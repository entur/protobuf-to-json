package no.entur.protobuf.json.accessor;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Message;
import no.entur.protobuf.json.accessor.MessageAccessors.Field;
import no.entur.protobuf.json.accessor.MessageAccessors.Kind;
import no.entur.protobuf.json.plan.FilteredJsonValues;
import no.entur.protobuf.json.plan.MessagePlan.EnumNames;
import no.entur.protobuf.json.plan.MessagePlan.FieldInfo;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;

/**
 * Generates a writer for a message type with the ClassFile API: a hidden class whose code reads each field with its
 * accessor and writes it with the methods of {@linkplain AccessorJsonValues} (called out of line, see
 * {@linkplain OutOfLineJsonValues}), in field number order. The writers of the message types of the fields are linked with
 * invokedynamic when first called, see {@linkplain MessageWriters#bootstrap}.
 * <br><br>
 * The unfiltered writer has a static method
 * <pre>
 * void write(JsonGenerator generator, SerializableString name, M message, AccessorProtobufJsonWriter writer)
 * </pre>
 * and the filtering writer
 * <pre>
 * boolean write(FilteredJsonValues values, SerializableString name, int nameSize, M message, boolean anonymize, FilteringAccessorProtobufJsonWriter writer)
 * </pre>
 * Messages with many fields are written by several methods, so that no method is too large for the JIT to compile, or to
 * inline the accessors into.
 * <br><br>
 * The class is defined next to this class, if this class's class loader can see the classes of the messages; otherwise
 * in the package of the message.
 */

final class MessageWriterGenerator {

    /** fields per method */
    static final int FIELDS_PER_METHOD = 16;

    private static final ClassDesc CD_JsonGenerator = desc(JsonGenerator.class);
    private static final ClassDesc CD_SerializableString = desc(SerializableString.class);
    private static final ClassDesc CD_FilteredJsonValues = desc(FilteredJsonValues.class);
    private static final ClassDesc CD_FieldInfo = desc(FieldInfo.class);
    private static final ClassDesc CD_EnumNames = desc(EnumNames.class);
    // the methods of AccessorJsonValues, called out of line
    private static final ClassDesc CD_Values = ClassDesc.of(OutOfLineJsonValues.NAME);
    private static final ClassDesc CD_Writer = desc(AccessorProtobufJsonWriter.class);
    private static final ClassDesc CD_FilteringWriter = desc(FilteringAccessorProtobufJsonWriter.class);
    private static final ClassDesc CD_MessageWriter = desc(MessageWriter.class);
    private static final ClassDesc CD_FilteringMessageWriter = desc(FilteringMessageWriter.class);
    private static final ClassDesc CD_Message = desc(Message.class);
    private static final ClassDesc CD_ByteString = desc(ByteString.class);
    private static final ClassDesc CD_Map = desc(Map.class);
    private static final ClassDesc CD_Set = desc(Set.class);
    private static final ClassDesc CD_Iterator = desc(Iterator.class);
    private static final ClassDesc CD_Entry = desc(Map.Entry.class);

    private static final DirectMethodHandleDesc BOOTSTRAP = ConstantDescs.ofCallsiteBootstrap(desc(MessageWriters.class), "bootstrap", ConstantDescs.CD_CallSite);

    /** the classes the generated code refers to, besides those of the messages */
    private static final List<Class<?>> LIBRARY_CLASSES = List.of(JsonGenerator.class, SerializableString.class, FilteredJsonValues.class,
            FieldInfo.class, EnumNames.class, OutOfLineJsonValues.CLASS, AccessorProtobufJsonWriter.class, FilteringAccessorProtobufJsonWriter.class,
            MessageWriter.class, FilteringMessageWriter.class, MessageWriters.class, Message.class, ByteString.class);

    private final MessageAccessors accessors;
    private final boolean filtering;
    private final ClassDesc messageType;

    // class data: constants loaded with ldc
    private final List<Object> constants = new ArrayList<>();
    private final Map<Object, Integer> constantIndexes = new IdentityHashMap<>();

    private ClassDesc self;

    private MessageWriterGenerator(MessageAccessors accessors, boolean filtering) {
        this.accessors = accessors;
        this.filtering = filtering;
        this.messageType = desc(accessors.type);
    }

    /**
     * Generate and define a writer.
     *
     * @param accessors accessors of the message type
     * @param filtering whether to generate a filtering writer
     * @return the writer
     * @throws ReflectiveOperationException if the writer could not be defined
     */

    static MessageWriters.Generated generate(MessageAccessors accessors, boolean filtering) throws ReflectiveOperationException {
        return new MessageWriterGenerator(accessors, filtering).define();
    }

    private MessageWriters.Generated define() throws ReflectiveOperationException {
        Class<?> type = accessors.type;

        // the classes the generated code refers to must be the same for the class loader it is defined in
        Set<Class<?>> messageClasses = new LinkedHashSet<>();
        messageClasses.add(type);
        List<Class<? extends Message>> nested = new ArrayList<>();
        for (Field field : accessors.fields) {
            if (field.kind == Kind.MESSAGE || field.kind == Kind.CLOSED_ENUM || field.kind == Kind.WELL_KNOWN) {
                messageClasses.add(field.type);
            }
            if (field.kind == Kind.MESSAGE) {
                nested.add(field.type.asSubclass(Message.class));
            }
        }
        ClassLoader library = MessageWriterGenerator.class.getClassLoader();
        MethodHandles.Lookup lookup;
        if (isVisible(library, messageClasses)) {
            lookup = MethodHandles.lookup();
        } else if (isVisible(type.getClassLoader(), LIBRARY_CLASSES) && isVisible(type.getClassLoader(), messageClasses)) {
            lookup = getLookup(type);
        } else {
            throw new IllegalStateException("The class loaders of " + type.getName() + " and of this library do not see each other's classes");
        }

        String suffix = filtering ? "$$FilteringJsonWriter" : "$$JsonWriter";
        self = ClassDesc.of(lookup.lookupClass().getPackageName() + "." + type.getSimpleName() + suffix);

        ClassHierarchyResolver resolver = ClassHierarchyResolver.ofClassLoading(library);
        if (type.getClassLoader() != null && type.getClassLoader() != library) {
            resolver = resolver.orElse(ClassHierarchyResolver.ofClassLoading(type.getClassLoader()));
        }
        resolver = resolver.orElse(ClassHierarchyResolver.defaultResolver());
        byte[] bytes = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(resolver)).build(self, this::generate);

        MethodHandles.Lookup hidden = lookup.defineHiddenClassWithClassData(bytes, List.copyOf(constants), true);
        Class<?> hiddenClass = hidden.lookupClass();
        MethodType writeType = filtering
                ? MethodType.methodType(boolean.class, FilteredJsonValues.class, SerializableString.class, int.class, type, boolean.class, FilteringAccessorProtobufJsonWriter.class)
                : MethodType.methodType(void.class, JsonGenerator.class, SerializableString.class, type, AccessorProtobufJsonWriter.class);
        MethodHandle handle = hidden.findStatic(hiddenClass, "write", writeType);
        Object writer;
        try {
            writer = hidden.findConstructor(hiddenClass, MethodType.methodType(void.class)).invoke();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
        return new MessageWriters.Generated(writer, handle, nested, false, null);
    }

    /**
     * Get a lookup with full privilege access in the package of a message class, for defining a hidden class there.
     * If the class is in another module than this library (i.e. the unnamed module of another class loader), a class which
     * provides one is defined in the package first.
     */

    private static MethodHandles.Lookup getLookup(Class<?> type) throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
        if (lookup.hasFullPrivilegeAccess()) {
            return lookup;
        }
        String name = type.getName() + "$$JsonWriterLookup";
        Class<?> provider;
        try {
            provider = Class.forName(name, false, type.getClassLoader());
        } catch (ClassNotFoundException e) {
            // package-private, so it only provides lookups to code which already has access to the package
            ClassDesc lookupDesc = desc(MethodHandles.Lookup.class);
            byte[] bytes = ClassFile.of().build(ClassDesc.of(name), builder -> builder
                    .withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SUPER)
                    .withMethodBody("lookup", MethodTypeDesc.of(lookupDesc), ClassFile.ACC_STATIC, code -> code
                            .invokestatic(desc(MethodHandles.class), "lookup", MethodTypeDesc.of(lookupDesc))
                            .areturn()));
            try {
                provider = lookup.defineClass(bytes);
            } catch (LinkageError duplicate) {
                // defined by another thread
                provider = Class.forName(name, false, type.getClassLoader());
            }
        }
        try {
            return (MethodHandles.Lookup) lookup.findStatic(provider, "lookup", MethodType.methodType(MethodHandles.Lookup.class)).invoke();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isVisible(ClassLoader loader, Iterable<Class<?>> classes) {
        for (Class<?> type : classes) {
            try {
                if (Class.forName(type.getName(), false, loader) != type) {
                    return false;
                }
            } catch (ClassNotFoundException | LinkageError e) {
                return false;
            }
        }
        return true;
    }

    // ---- the class ----

    private MethodTypeDesc writeType(ClassDesc message) {
        if (filtering) {
            return MethodTypeDesc.of(CD_boolean, CD_FilteredJsonValues, CD_SerializableString, CD_int, message, CD_boolean, CD_FilteringWriter);
        }
        return MethodTypeDesc.of(CD_void, CD_JsonGenerator, CD_SerializableString, message, CD_Writer);
    }

    private MethodTypeDesc fieldsType() {
        if (filtering) {
            return MethodTypeDesc.of(CD_boolean, CD_FilteredJsonValues, messageType, CD_boolean, CD_FilteringWriter);
        }
        return MethodTypeDesc.of(CD_void, CD_JsonGenerator, messageType, CD_Writer);
    }

    private void generate(ClassBuilder builder) {
        builder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
        builder.withInterfaceSymbols(filtering ? CD_FilteringMessageWriter : CD_MessageWriter);

        builder.withMethodBody(INIT_NAME, MTD_void, ClassFile.ACC_PUBLIC, code -> code
                .aload(0)
                .invokespecial(CD_Object, INIT_NAME, MTD_void)
                .return_());

        // the interface method, which calls the static method
        MethodTypeDesc bridgeType = writeType(CD_Message);
        builder.withMethodBody("write", bridgeType, ClassFile.ACC_PUBLIC, code -> {
            if (filtering) {
                code.aload(1).aload(2).iload(3).aload(4).checkcast(messageType).iload(5).aload(6)
                        .invokestatic(self, "write", writeType(messageType))
                        .ireturn();
            } else {
                code.aload(1).aload(2).aload(3).checkcast(messageType).aload(4)
                        .invokestatic(self, "write", writeType(messageType))
                        .return_();
            }
        });

        List<List<Field>> chunks = new ArrayList<>();
        List<Field> fields = accessors.fields;
        for (int i = 0; i < fields.size(); i += FIELDS_PER_METHOD) {
            chunks.add(fields.subList(i, Math.min(fields.size(), i + FIELDS_PER_METHOD)));
        }
        boolean inline = chunks.size() <= 1;

        builder.withMethodBody("write", writeType(messageType), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
            if (filtering) {
                // values, name, nameSize, message, anonymize, writer
                Label started = code.newLabel();
                code.aload(0).aload(1).iload(2)
                        .invokevirtual(CD_FilteredJsonValues, "startObject", MethodTypeDesc.of(CD_boolean, CD_SerializableString, CD_int))
                        .ifne(started)
                        .iconst_0()
                        .ireturn()
                        .labelBinding(started);
                Label stop = code.newLabel();
                if (inline) {
                    if (!chunks.isEmpty()) {
                        emitFields(new Emitter(code, 3, 0, 5, 4, stop), chunks.get(0));
                    }
                } else {
                    for (int i = 0; i < chunks.size(); i++) {
                        code.aload(0).aload(3).iload(4).aload(5)
                                .invokestatic(self, "fields" + i, fieldsType())
                                .ifeq(stop);
                    }
                }
                code.aload(0)
                        .invokevirtual(CD_FilteredJsonValues, "endObject", MTD_void)
                        .iconst_1()
                        .ireturn()
                        .labelBinding(stop)
                        .aload(0)
                        .invokevirtual(CD_FilteredJsonValues, "endObject", MTD_void)
                        .iconst_0()
                        .ireturn();
            } else {
                // generator, name, message, writer
                code.aload(0).aload(1).invokestatic(CD_Values, "startObject", MethodTypeDesc.of(CD_void, CD_JsonGenerator, CD_SerializableString));
                if (inline) {
                    if (!chunks.isEmpty()) {
                        emitFields(new Emitter(code, 2, 0, 3, -1, null), chunks.get(0));
                    }
                } else {
                    for (int i = 0; i < chunks.size(); i++) {
                        code.aload(0).aload(2).aload(3).invokestatic(self, "fields" + i, fieldsType());
                    }
                }
                code.aload(0)
                        .invokestatic(CD_Values, "endObject", MethodTypeDesc.of(CD_void, CD_JsonGenerator))
                        .return_();
            }
        });

        if (!inline) {
            for (int i = 0; i < chunks.size(); i++) {
                List<Field> chunk = chunks.get(i);
                builder.withMethodBody("fields" + i, fieldsType(), ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC, code -> {
                    if (filtering) {
                        // values, message, anonymize, writer
                        Label stop = code.newLabel();
                        emitFields(new Emitter(code, 1, 0, 3, 2, stop), chunk);
                        code.iconst_1()
                                .ireturn()
                                .labelBinding(stop)
                                .iconst_0()
                                .ireturn();
                    } else {
                        // generator, message, writer
                        emitFields(new Emitter(code, 1, 0, 2, -1, null), chunk);
                        code.return_();
                    }
                });
            }
        }
    }

    private void emitFields(Emitter emitter, List<Field> fields) {
        for (Field field : fields) {
            if (field.isMap()) {
                emitter.map(field);
            } else if (field.isRepeated()) {
                emitter.repeated(field);
            } else {
                emitter.singular(field);
            }
        }
    }

    /**
     * Load a constant from the class data.
     */

    private void loadConstant(CodeBuilder code, Object value, ClassDesc type) {
        Integer index = constantIndexes.get(value);
        if (index == null) {
            index = constants.size();
            constants.add(value);
            constantIndexes.put(value, index);
        }
        code.ldc(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_CLASS_DATA_AT, ConstantDescs.DEFAULT_NAME, type, index));
    }

    /**
     * Emits the code for fields, within one method.
     */

    private final class Emitter {

        private final CodeBuilder code;
        private final int message;
        private final int out;       // generator or values
        private final int writer;
        private final int anonymize; // filtering only
        private final Label stop;    // filtering only: stopped because of max size

        // locals, allocated when first used; each always holds the same type
        private int count = -1;
        private int index = -1;
        private int action = -1;
        private int entryAction = -1;
        private int iterator = -1;
        private int entry = -1;
        private int key = -1;
        private final Map<TypeKind, Integer> values = new IdentityHashMap<>();
        private final Map<ClassDesc, Integer> references = new HashMap<>();

        Emitter(CodeBuilder code, int message, int out, int writer, int anonymize, Label stop) {
            this.code = code;
            this.message = message;
            this.out = out;
            this.writer = writer;
            this.anonymize = anonymize;
            this.stop = stop;
        }

        private int local(int slot, TypeKind kind) {
            return slot != -1 ? slot : code.allocateLocal(kind);
        }

        // ---- singular fields ----

        void singular(Field field) {
            Label skip = code.newLabel();
            Runnable value;
            if (field.has != null) {
                code.aload(message);
                invoke(field.has);
                code.ifeq(skip);
                value = () -> {
                    code.aload(message);
                    invoke(field.get);
                    toEnumNumber(field);
                };
            } else {
                // implicit presence: written if not the default value
                code.aload(message);
                invoke(field.get);
                toEnumNumber(field);
                ClassDesc type = valueDesc(field);
                TypeKind kind = TypeKind.from(type).asLoadable();
                int slot = kind == TypeKind.REFERENCE ? references.computeIfAbsent(type, t -> code.allocateLocal(TypeKind.REFERENCE)) : values.computeIfAbsent(kind, code::allocateLocal);
                code.storeLocal(kind, slot);
                skipDefault(field, kind, slot, skip);
                value = () -> code.loadLocal(kind, slot);
            }

            if (!filtering) {
                writeValue(field, () -> loadConstant(code, field.info.name, CD_SerializableString), value);
            } else if (field.kind == Kind.MESSAGE || field.kind == Kind.WELL_KNOWN) {
                // anonymized messages keep their structure
                action = local(action, TypeKind.INT);
                filterStructure(field, skip);
                writeFilteredValue(field, () -> loadConstant(code, field.info.name, CD_SerializableString), () -> loadNameSize(field), value, () -> code.iload(action));
                code.ifeq(stop);
            } else {
                action = local(action, TypeKind.INT);
                Label write = code.newLabel();
                code.aload(out);
                loadConstant(code, field.info, CD_FieldInfo);
                code.iload(anonymize)
                        .invokestatic(CD_Values, "filterScalar", MethodTypeDesc.of(CD_int, CD_FilteredJsonValues, CD_FieldInfo, CD_boolean))
                        .istore(action)
                        .iload(action)
                        .ifeq(write)
                        .iload(action)
                        .loadConstant(AccessorJsonValues.DONE)
                        .if_icmpeq(skip)
                        .goto_(stop)
                        .labelBinding(write);
                writeFilteredValue(field, () -> loadConstant(code, field.info.name, CD_SerializableString), () -> loadNameSize(field), value, code::iconst_0);
                code.ifeq(stop);
            }
            code.labelBinding(skip);
        }

        private void loadNameSize(Field field) {
            code.aload(out);
            loadConstant(code, field.info, CD_FieldInfo);
            code.invokevirtual(CD_FilteredJsonValues, "getNameSize", MethodTypeDesc.of(CD_int, CD_FieldInfo));
        }

        /**
         * Jump to skip if the value in the slot is the default value.
         */

        private void skipDefault(Field field, TypeKind kind, int slot, Label skip) {
            code.loadLocal(kind, slot);
            switch (field.kind) {
                case INT32, UINT32, BOOL, ENUM -> code.ifeq(skip);
                case INT64, UINT64 -> code.lconst_0().lcmp().ifeq(skip);
                case FLOAT -> code.invokestatic(desc(Float.class), "floatToRawIntBits", MethodTypeDesc.of(CD_int, ConstantDescs.CD_float)).ifeq(skip);
                case DOUBLE -> code.invokestatic(desc(Double.class), "doubleToRawLongBits", MethodTypeDesc.of(CD_long, ConstantDescs.CD_double)).lconst_0().lcmp().ifeq(skip);
                case STRING -> code.invokevirtual(CD_String, "isEmpty", MethodTypeDesc.of(CD_boolean)).ifne(skip);
                case BYTES -> code.invokevirtual(CD_ByteString, "isEmpty", MethodTypeDesc.of(CD_boolean)).ifne(skip);
                case CLOSED_ENUM -> code.loadConstant(((EnumValueDescriptor) field.info.descriptor.getDefaultValue()).getNumber()).if_icmpeq(skip);
                default -> throw new IllegalStateException("Unexpected field without presence " + field.info.descriptor.getFullName());
            }
        }

        // ---- repeated fields ----

        void repeated(Field field) {
            count = local(count, TypeKind.INT);
            index = local(index, TypeKind.INT);

            Label skip = code.newLabel();
            Label loop = code.newLabel();
            Label end = code.newLabel();

            code.aload(message);
            invoke(field.count);
            code.istore(count)
                    .iload(count)
                    .ifeq(skip);

            Runnable element = () -> {
                code.aload(message).iload(index);
                invoke(field.get);
                toEnumNumber(field);
            };

            if (!filtering) {
                code.aload(out);
                loadConstant(code, field.info.name, CD_SerializableString);
                code.invokestatic(CD_Values, "startArray", MethodTypeDesc.of(CD_void, CD_JsonGenerator, CD_SerializableString))
                        .iconst_0()
                        .istore(index)
                        .labelBinding(loop)
                        .iload(index)
                        .iload(count)
                        .if_icmpge(end);
                writeValue(field, code::aconst_null, element);
                code.iinc(index, 1)
                        .goto_(loop)
                        .labelBinding(end)
                        .aload(out)
                        .invokestatic(CD_Values, "endArray", MethodTypeDesc.of(CD_void, CD_JsonGenerator))
                        .labelBinding(skip);
                return;
            }

            action = local(action, TypeKind.INT);
            Label stopInArray = code.newLabel();
            filterStructure(field, skip);
            code.aload(out);
            loadConstant(code, field.info.name, CD_SerializableString);
            loadNameSize(field);
            code.invokevirtual(CD_FilteredJsonValues, "startArray", MethodTypeDesc.of(CD_boolean, CD_SerializableString, CD_int))
                    .ifeq(stop)
                    .iconst_0()
                    .istore(index)
                    .labelBinding(loop)
                    .iload(index)
                    .iload(count)
                    .if_icmpge(end);
            writeFilteredValue(field, code::aconst_null, code::iconst_0, element, () -> code.iload(action));
            code.ifeq(stopInArray)
                    .iinc(index, 1)
                    .goto_(loop)
                    .labelBinding(stopInArray)
                    .aload(out)
                    .invokevirtual(CD_FilteredJsonValues, "endArray", MTD_void)
                    .goto_(stop)
                    .labelBinding(end)
                    .aload(out)
                    .invokevirtual(CD_FilteredJsonValues, "endArray", MTD_void)
                    .labelBinding(skip);
        }

        // ---- map fields ----

        void map(Field field) {
            iterator = local(iterator, TypeKind.REFERENCE);
            entry = local(entry, TypeKind.REFERENCE);

            Label skip = code.newLabel();
            Label loop = code.newLabel();
            Label end = code.newLabel();

            code.aload(message);
            invoke(field.count);
            code.ifeq(skip);

            Runnable value = () -> {
                code.aload(entry).invokeinterface(CD_Entry, "getValue", MethodTypeDesc.of(CD_Object));
                unbox(field);
                toEnumNumber(field);
            };

            if (!filtering) {
                code.aload(out);
                loadConstant(code, field.info.name, CD_SerializableString);
                code.invokestatic(CD_Values, "startObject", MethodTypeDesc.of(CD_void, CD_JsonGenerator, CD_SerializableString));
                startIteration(field, loop, end);
                code.aload(out);
                loadKey(field);
                code.invokestatic(CD_Values, "writeKey", MethodTypeDesc.of(CD_void, CD_JsonGenerator, CD_String));
                writeValue(field, code::aconst_null, value);
                code.goto_(loop)
                        .labelBinding(end)
                        .aload(out)
                        .invokestatic(CD_Values, "endObject", MethodTypeDesc.of(CD_void, CD_JsonGenerator))
                        .labelBinding(skip);
                return;
            }

            action = local(action, TypeKind.INT);
            entryAction = local(entryAction, TypeKind.INT);
            key = local(key, TypeKind.REFERENCE);
            Label stopInObject = code.newLabel();

            filterStructure(field, skip);
            code.aload(out);
            loadConstant(code, field.info.name, CD_SerializableString);
            loadNameSize(field);
            code.invokevirtual(CD_FilteredJsonValues, "startObject", MethodTypeDesc.of(CD_boolean, CD_SerializableString, CD_int))
                    .ifeq(stop);
            startIteration(field, loop, end);
            loadKey(field);
            code.invokestatic(CD_Values, "keyName", MethodTypeDesc.of(CD_SerializableString, CD_String))
                    .astore(key)
                    .aload(out)
                    .aload(key)
                    .iload(action)
                    .invokestatic(CD_Values, "filterEntry", MethodTypeDesc.of(CD_int, CD_FilteredJsonValues, CD_SerializableString, CD_int))
                    .istore(entryAction)
                    .iload(entryAction)
                    .loadConstant(AccessorJsonValues.DONE)
                    .if_icmpeq(loop)
                    .iload(entryAction)
                    .loadConstant(AccessorJsonValues.STOPPED)
                    .if_icmpeq(stopInObject);
            writeFilteredValue(field, () -> code.aload(key), () -> code
                    .aload(out)
                    .aload(key)
                    .invokevirtual(CD_FilteredJsonValues, "getNameSize", MethodTypeDesc.of(CD_int, CD_SerializableString)), value, () -> code.iload(entryAction));
            code.ifeq(stopInObject)
                    .goto_(loop)
                    .labelBinding(stopInObject)
                    .aload(out)
                    .invokevirtual(CD_FilteredJsonValues, "endObject", MTD_void)
                    .goto_(stop)
                    .labelBinding(end)
                    .aload(out)
                    .invokevirtual(CD_FilteredJsonValues, "endObject", MTD_void)
                    .labelBinding(skip);
        }

        /**
         * Start iterating over the entries of a map; at the loop label, jump to the end label when done, otherwise store the
         * next entry.
         */

        private void startIteration(Field field, Label loop, Label end) {
            code.aload(message);
            invoke(field.get);
            code.invokeinterface(CD_Map, "entrySet", MethodTypeDesc.of(CD_Set))
                    .invokeinterface(CD_Set, "iterator", MethodTypeDesc.of(CD_Iterator))
                    .astore(iterator)
                    .labelBinding(loop)
                    .aload(iterator)
                    .invokeinterface(CD_Iterator, "hasNext", MethodTypeDesc.of(CD_boolean))
                    .ifeq(end)
                    .aload(iterator)
                    .invokeinterface(CD_Iterator, "next", MethodTypeDesc.of(CD_Object))
                    .checkcast(CD_Entry)
                    .astore(entry);
        }

        /**
         * Load the key of the current entry, as a string.
         */

        private void loadKey(Field field) {
            String method = switch (field.keyKind) {
                case STRING -> "keyString";
                case INT32 -> "keyInt32";
                case UINT32 -> "keyUInt32";
                case INT64 -> "keyInt64";
                case UINT64 -> "keyUInt64";
                case BOOL -> "keyBool";
                default -> throw new IllegalStateException("Unexpected map key " + field.keyKind);
            };
            code.aload(entry)
                    .invokeinterface(CD_Entry, "getKey", MethodTypeDesc.of(CD_Object))
                    .invokestatic(CD_Values, method, MethodTypeDesc.of(CD_String, CD_Object));
        }

        /**
         * Unbox a map value.
         */

        private void unbox(Field field) {
            switch (field.kind) {
                case INT32, UINT32, ENUM -> unbox(Integer.class, "intValue", CD_int);
                case INT64, UINT64 -> unbox(Long.class, "longValue", CD_long);
                case BOOL -> unbox(Boolean.class, "booleanValue", CD_boolean);
                case FLOAT -> unbox(Float.class, "floatValue", ConstantDescs.CD_float);
                case DOUBLE -> unbox(Double.class, "doubleValue", ConstantDescs.CD_double);
                default -> code.checkcast(desc(field.type));
            }
        }

        private void unbox(Class<?> type, String method, ClassDesc primitive) {
            ClassDesc boxed = desc(type);
            code.checkcast(boxed).invokevirtual(boxed, method, MethodTypeDesc.of(primitive));
        }

        // ---- filtering ----

        /**
         * Filter a message, repeated or map field by path. Jumps to skip if the field was pruned, and to stop if writing stopped.
         * Otherwise the action is {@linkplain AccessorJsonValues#WRITE} or {@linkplain AccessorJsonValues#ANONYMIZE}, which is the
         * value of the anonymize parameter for the field's values.
         */

        private void filterStructure(Field field, Label skip) {
            code.aload(out);
            loadConstant(code, field.info, CD_FieldInfo);
            code.iload(anonymize)
                    .invokestatic(CD_Values, "filterStructure", MethodTypeDesc.of(CD_int, CD_FilteredJsonValues, CD_FieldInfo, CD_boolean))
                    .istore(action)
                    .iload(action)
                    .loadConstant(AccessorJsonValues.DONE)
                    .if_icmpeq(skip)
                    .iload(action)
                    .loadConstant(AccessorJsonValues.STOPPED)
                    .if_icmpeq(stop);
        }

        // ---- values ----

        /**
         * Convert a closed enum constant to its number.
         */

        private void toEnumNumber(Field field) {
            if (field.kind == Kind.CLOSED_ENUM) {
                code.invokevirtual(desc(field.type), "getNumber", MethodTypeDesc.of(CD_int));
            }
        }

        /**
         * Write a value: generator, name, value (and writer).
         */

        private void writeValue(Field field, Runnable name, Runnable value) {
            code.aload(out);
            name.run();
            switch (field.kind) {
                case MESSAGE -> {
                    value.run();
                    code.aload(writer)
                            .invokedynamic(DynamicCallSiteDesc.of(BOOTSTRAP, "write", writeType(desc(field.type))));
                }
                case WELL_KNOWN -> {
                    value.run();
                    code.aload(writer)
                            .invokestatic(CD_Values, AccessorJsonValues.getWriteMethodName(field.wellKnownType),
                                    MethodTypeDesc.of(CD_void, CD_JsonGenerator, CD_SerializableString, desc(field.type), CD_Writer));
                }
                case ENUM, CLOSED_ENUM -> {
                    loadConstant(code, enumNames(field), CD_EnumNames);
                    value.run();
                    code.invokestatic(CD_Values, "writeEnum", MethodTypeDesc.of(CD_void, CD_JsonGenerator, CD_SerializableString, CD_EnumNames, CD_int));
                }
                default -> {
                    value.run();
                    code.invokestatic(CD_Values, scalarMethod(field.kind), MethodTypeDesc.of(CD_void, CD_JsonGenerator, CD_SerializableString, valueDesc(field)));
                }
            }
        }

        /**
         * Write a filtered value: values, name, name size, value, anonymize (and writer). Leaves false on the stack if writing stopped.
         */

        private void writeFilteredValue(Field field, Runnable name, Runnable nameSize, Runnable value, Runnable anonymize) {
            code.aload(out);
            name.run();
            nameSize.run();
            switch (field.kind) {
                case MESSAGE -> {
                    value.run();
                    anonymize.run();
                    code.aload(writer)
                            .invokedynamic(DynamicCallSiteDesc.of(BOOTSTRAP, "write", writeType(desc(field.type))));
                }
                case WELL_KNOWN -> {
                    value.run();
                    anonymize.run();
                    code.aload(writer)
                            .invokestatic(CD_Values, AccessorJsonValues.getWriteMethodName(field.wellKnownType),
                                    MethodTypeDesc.of(CD_boolean, CD_FilteredJsonValues, CD_SerializableString, CD_int, desc(field.type), CD_boolean, CD_FilteringWriter));
                }
                case ENUM, CLOSED_ENUM -> {
                    loadConstant(code, enumNames(field), CD_EnumNames);
                    value.run();
                    anonymize.run();
                    code.invokestatic(CD_Values, "writeEnum",
                            MethodTypeDesc.of(CD_boolean, CD_FilteredJsonValues, CD_SerializableString, CD_int, CD_EnumNames, CD_int, CD_boolean));
                }
                default -> {
                    value.run();
                    anonymize.run();
                    code.invokestatic(CD_Values, scalarMethod(field.kind),
                            MethodTypeDesc.of(CD_boolean, CD_FilteredJsonValues, CD_SerializableString, CD_int, valueDesc(field), CD_boolean));
                }
            }
        }

        private void invoke(Method method) {
            code.invokevirtual(messageType, method.getName(), MethodTypeDesc.of(desc(method.getReturnType()), descs(method.getParameterTypes())));
        }
    }

    private static EnumNames enumNames(Field field) {
        return field.isMap() ? field.info.mapValue.enumNames : field.info.enumNames;
    }

    private static String scalarMethod(Kind kind) {
        return switch (kind) {
            case INT32 -> "writeInt32";
            case UINT32 -> "writeUInt32";
            case INT64 -> "writeInt64";
            case UINT64 -> "writeUInt64";
            case BOOL -> "writeBool";
            case FLOAT -> "writeFloat";
            case DOUBLE -> "writeDouble";
            case STRING -> "writeString";
            case BYTES -> "writeBytes";
            default -> throw new IllegalStateException("Not a scalar: " + kind);
        };
    }

    /**
     * @return type of a value as passed to the write methods; enums as their number
     */

    private static ClassDesc valueDesc(Field field) {
        return switch (field.kind) {
            case ENUM, CLOSED_ENUM -> CD_int;
            default -> desc(field.type);
        };
    }

    private static ClassDesc desc(Class<?> type) {
        return type.describeConstable().orElseThrow(() -> new IllegalArgumentException("No descriptor for " + type));
    }

    private static ClassDesc[] descs(Class<?>[] types) {
        ClassDesc[] descs = new ClassDesc[types.length];
        for (int i = 0; i < types.length; i++) {
            descs[i] = desc(types[i]);
        }
        return descs;
    }
}

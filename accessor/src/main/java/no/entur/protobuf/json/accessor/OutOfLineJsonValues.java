package no.entur.protobuf.json.accessor;

import java.lang.classfile.ClassFile;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Defines a class with the same static methods as {@linkplain AccessorJsonValues}, which the generated writers call, so that
 * the JIT compiles the writing of each kind of value once, rather than inlining it into the code of every message type.
 * <br><br>
 * Inlined into the generated code, the writing of values (i.e. Jackson's string encoding) makes the compiled code of a
 * message type with many fields so large that the JIT stops inlining before reaching the end, leaving even the accessors
 * as calls, and optimizes the rest less well. The methods of the defined class forward to {@linkplain AccessorJsonValues},
 * and are padded with no-op instructions to more than the size of methods the JIT inlines ({@code -XX:FreqInlineSize},
 * 325 bytes by default; with a larger value they are inlined again).
 */

final class OutOfLineJsonValues {

    static final String NAME = AccessorJsonValues.class.getPackageName() + ".OutOfLineAccessorJsonValues";

    /** in bytes, more than -XX:FreqInlineSize */
    private static final int PADDING = 400;

    /** the defined class */
    static final Class<?> CLASS = define();

    private OutOfLineJsonValues() {
    }

    private static Class<?> define() {
        ClassDesc self = ClassDesc.of(NAME);
        ClassDesc target = desc(AccessorJsonValues.class);
        byte[] bytes = ClassFile.of().build(self, builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            for (Method method : AccessorJsonValues.class.getDeclaredMethods()) {
                int modifiers = method.getModifiers();
                if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
                    continue;
                }
                MethodType methodType = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
                MethodTypeDesc type = MethodTypeDesc.ofDescriptor(methodType.toMethodDescriptorString());
                builder.withMethodBody(method.getName(), type, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                    for (int i = 0; i < PADDING; i++) {
                        code.nop();
                    }
                    int slot = 0;
                    for (Class<?> parameterType : method.getParameterTypes()) {
                        TypeKind kind = TypeKind.from(parameterType);
                        code.loadLocal(kind, slot);
                        slot += kind.slotSize();
                    }
                    code.invokestatic(target, method.getName(), type);
                    code.return_(TypeKind.from(method.getReturnType()));
                });
            }
        });
        try {
            return MethodHandles.lookup().defineClass(bytes);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ClassDesc desc(Class<?> type) {
        return type.describeConstable().orElseThrow();
    }
}

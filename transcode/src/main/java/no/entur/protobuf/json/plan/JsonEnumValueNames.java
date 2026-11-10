package no.entur.protobuf.json.plan;

import com.google.protobuf.DescriptorProtos.EnumValueOptions;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.ExtensionLite;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;

/**
 * Custom JSON names of enum values: the {@code json} option of {@code google/protobuf/json_enumvalue_options.proto}, which
 * is in protobuf-java-util 4.36 and later (and can only be set in edition 2026 files). The option is looked up by
 * reflection, as older protobuf-java-util versions do not have it, and its generated code fails to initialize with a
 * protobuf-java older than protobuf-java-util. Without the option, enum values are written with their names, as no
 * custom names can be set.
 * <br><br>
 * The option is only looked up for enum values which have options, so that the option and the reflection-based access
 * to it are only loaded if an enum value could have a custom name.
 */

final class JsonEnumValueNames {

    private JsonEnumValueNames() {
    }

    /**
     * @return whether custom JSON names are looked up, i.e. whether this protobuf-java-util version has the option
     */

    static boolean isAvailable() {
        return Option.OPTION != null;
    }

    /**
     * @param value enum value
     * @return the custom JSON name of the value if it has one, otherwise its name
     */

    static String getJsonName(EnumValueDescriptor value) {
        if (value.toProto().hasOptions() && Option.OPTION != null) {
            String name = Option.getCustomName(Option.OPTION, Option.NAME, value);
            if (name != null) {
                return name;
            }
        }
        return value.getName();
    }

    private static final class Option {

        private static final String OPTIONS_PROTO = "com.google.protobuf.util.JsonEnumvalueOptionsProto";

        private static final ExtensionLite<EnumValueOptions, ?> OPTION;
        private static final FieldDescriptor NAME;

        static {
            ExtensionLite<EnumValueOptions, ?> option = null;
            FieldDescriptor name = null;
            try {
                option = getOption();
                name = ((Message) option.getMessageDefaultInstance()).getDescriptorForType().findFieldByName("string");
                // the generated code of the option checks the protobuf-java version when it is first used
                getCustomName(option, name, NullValue.NULL_VALUE.getValueDescriptor());
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                option = null;
                name = null;
            }
            OPTION = option;
            NAME = name;
        }

        private Option() {
        }

        @SuppressWarnings("unchecked")
        private static ExtensionLite<EnumValueOptions, ?> getOption() throws ReflectiveOperationException {
            Class<?> type = Class.forName(OPTIONS_PROTO, true, JsonEnumValueNames.class.getClassLoader());
            return (ExtensionLite<EnumValueOptions, ?>) type.getField("json").get(null);
        }

        private static String getCustomName(ExtensionLite<EnumValueOptions, ?> option, FieldDescriptor name, EnumValueDescriptor value) {
            Message options = (Message) value.getOptions().getExtension(option);
            if (name != null && options.hasField(name)) {
                return (String) options.getField(name);
            }
            return null;
        }
    }
}

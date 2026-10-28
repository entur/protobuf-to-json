package no.entur.protobuf.json.benchmark;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.protobuf.MessageOrBuilder;
import com.hubspot.jackson3.datatype.protobuf.ProtobufJacksonConfig;
import com.hubspot.jackson3.datatype.protobuf.ProtobufModule;
import no.entur.protobuf.json.ProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.json.JsonMapper;

/**
 * <a href="https://github.com/HubSpot/jackson-datatype-protobuf">HubSpot jackson-datatype-protobuf</a> (the Jackson 3 version),
 * a Jackson databind module, configured to write as close to JsonFormat as it can: its canonical serialization (64-bit integers
 * as strings, unsigned integers as unsigned), with default values left out. It has no type registry, so google.protobuf.Any
 * values are written as regular messages. See HubSpotJacksonDatatypeProtobufTest for how the output differs from JsonFormat.
 */

public final class HubSpotJacksonDatatypeProtobuf {

    private HubSpotJacksonDatatypeProtobuf() {
    }

    public static JsonMapper createMapper() {
        return JsonMapper.builder()
                .addModule(new ProtobufModule(ProtobufJacksonConfig.builder().useCanonicalSerialization().build()))
                // omit default values and empty repeated fields and maps, like JsonFormat
                .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_DEFAULT))
                .build();
    }

    /**
     * @param mapper mapper with the module
     * @return writer, i.e. for comparing with JsonFormat
     */

    public static ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> createWriter(JsonMapper mapper) {
        return new ProtobufJsonWriter<>() {
            @Override
            public boolean write(MessageOrBuilder message, JsonGenerator generator) {
                mapper.writeValue(generator, message);
                return true;
            }

            @Override
            public String writeAsString(MessageOrBuilder message) {
                return mapper.writeValueAsString(message);
            }
        };
    }
}

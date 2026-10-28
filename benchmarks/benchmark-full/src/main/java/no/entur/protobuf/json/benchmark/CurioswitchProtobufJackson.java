package no.entur.protobuf.json.benchmark;

import com.google.protobuf.Any;
import com.google.protobuf.Duration;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Int32Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Type;
import com.google.protobuf.Value;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.test.protobufjackson.GithubApi.SearchResponse;
import org.curioswitch.common.protobuf.json.MessageMarshaller;
import tools.jackson.core.JsonGenerator;

import java.io.IOException;

/**
 * <a href="https://github.com/curioswitch/protobuf-jackson">curioswitch protobuf-jackson</a>, which generates a marshaller per
 * message type. Uses Jackson 2, with its own generator. The types of the payloads, and of the google.protobuf.Any values of
 * the random test messages, are registered. See CurioswitchProtobufJacksonTest for how the output differs from JsonFormat.
 */

public final class CurioswitchProtobufJackson {

    private CurioswitchProtobufJackson() {
    }

    /**
     * @return marshaller writing compact JSON
     */

    public static MessageMarshaller createMarshaller() {
        return builder().build();
    }

    /**
     * @return builder of a marshaller writing compact JSON, with the types registered
     */

    public static MessageMarshaller.Builder builder() {
        return MessageMarshaller.builder()
                // the payloads
                .register(TestAllTypesProto3.getDefaultInstance())
                .register(SearchResponse.getDefaultInstance())
                .register(Type.getDefaultInstance())
                // the types of google.protobuf.Any values, like the JsonFormat type registry of the test messages
                .register(Int32Value.getDefaultInstance())
                .register(StringValue.getDefaultInstance())
                .register(Timestamp.getDefaultInstance())
                .register(Duration.getDefaultInstance())
                .register(Struct.getDefaultInstance())
                .register(Value.getDefaultInstance())
                .register(ListValue.getDefaultInstance())
                .register(FieldMask.getDefaultInstance())
                .register(Any.getDefaultInstance())
                .omittingInsignificantWhitespace(true);
    }

    /**
     * @param marshaller marshaller
     * @return writer, i.e. for comparing with JsonFormat
     */

    public static ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> createWriter(MessageMarshaller marshaller) {
        return new ProtobufJsonWriter<>() {
            @Override
            public boolean write(MessageOrBuilder message, JsonGenerator generator) throws IOException {
                // writes with its own (Jackson 2) generator
                generator.writeRawValue(writeAsString(message));
                return true;
            }

            @Override
            public String writeAsString(MessageOrBuilder message) throws IOException {
                return marshaller.writeValueAsString((Message) message);
            }
        };
    }
}

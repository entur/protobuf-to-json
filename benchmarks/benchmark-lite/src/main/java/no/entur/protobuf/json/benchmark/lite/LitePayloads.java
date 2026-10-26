package no.entur.protobuf.json.benchmark.lite;

import com.google.protobuf.MessageLite;
import com.google.protobuf.Type;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import no.entur.protobuf.json.lite.LiteProtobufJsonWriter;
import no.entur.protobuf.json.test.protobufjackson.GithubApi.SearchResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * The benchmark payloads of the full protobuf benchmarks, parsed as lite messages from the files written during the build
 * (see PayloadFiles in the test-support module).
 */

public final class LitePayloads {

    private LitePayloads() {
    }

    /**
     * @param name small, medium, large, xlarge or xxlarge
     * @return message
     */

    public static MessageLite load(String name) {
        try (InputStream in = LitePayloads.class.getResourceAsStream("/payloads/" + name + ".binpb")) {
            if (in == null) {
                throw new IllegalArgumentException("No payload " + name);
            }
            byte[] bytes = in.readAllBytes();
            switch (name) {
                case "small":
                    return TestAllTypesProto3.parseFrom(bytes);
                case "medium":
                case "xlarge":
                case "xxlarge":
                    return SearchResponse.parseFrom(bytes);
                case "large":
                    return Type.parseFrom(bytes);
                default:
                    throw new IllegalArgumentException(name);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @return builder with the types of the google.protobuf.Any values in the test messages
     */

    public static LiteProtobufJsonWriter.Builder newBuilder() {
        return LiteProtobufJsonWriter.newBuilder()
                .withAnyType("type.googleapis.com/protobuf_test_messages.proto3.TestAllTypesProto3", TestAllTypesProto3.getDefaultInstance());
    }

    static int checkSize(int size) {
        if (size <= 2) {
            throw new IllegalStateException("Expected output");
        }
        return size;
    }
}

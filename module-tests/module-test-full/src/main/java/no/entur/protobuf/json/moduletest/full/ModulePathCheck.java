package no.entur.protobuf.json.moduletest.full;

import com.google.protobuf.Timestamp;
import com.google.protobuf.util.JsonFormat;
import no.entur.protobuf.json.AbstractProtobufJsonWriterBuilder;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.accessor.AccessorProtobufJsonWriter;
import no.entur.protobuf.json.jsonformat.JsonFormatProtobufJsonWriter;
import no.entur.protobuf.json.moduletest.full.messages.Address;
import no.entur.protobuf.json.moduletest.full.messages.Customer;
import no.entur.protobuf.json.moduletest.full.messages.Status;
import no.entur.protobuf.json.stream.CodedOutputStreamProtobufJsonWriter;
import no.entur.protobuf.json.transcode.TranscodingProtobufJsonWriter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Writes a message with every full protobuf writer, from a named module on the module path, and exits with status 1 if any
 * output is not as expected.
 */

public final class ModulePathCheck {

    private static final Customer CUSTOMER = Customer.newBuilder()
            .setName("Ada")
            .setEmail("ada@example.com")
            .setId(42)
            .addTags("a")
            .addTags("b")
            .putScores("x", 1)
            .setAddress(Address.newBuilder().setStreet("Main").setCity("Oslo"))
            .setCreated(Timestamp.newBuilder().setSeconds(1793793600L))
            .setStatus(Status.ACTIVE)
            .build();

    private static final String EXPECTED = "{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"id\":\"42\",\"tags\":[\"a\",\"b\"],"
            + "\"scores\":{\"x\":1},\"address\":{\"street\":\"Main\",\"city\":\"Oslo\"},\"created\":\"2026-11-04T12:00:00Z\",\"status\":\"ACTIVE\"}";

    private static final String EXPECTED_FILTERED = "{\"name\":\"Ada\",\"email\":\"*\",\"id\":\"42\",\"tags\":[\"a\",\"b\"],"
            + "\"scores\":{\"x\":1},\"address\":\"[removed]\",\"created\":\"2026-11-04T12:00:00Z\",\"status\":\"ACTIVE\"}";

    private final List<String> failures = new ArrayList<>();
    private int checks;

    public static void main(String[] args) {
        ModulePathCheck check = new ModulePathCheck();
        check.run();
        if (!check.failures.isEmpty()) {
            for (String failure : check.failures) {
                System.err.println(failure);
            }
            System.err.println(check.failures.size() + " of " + check.checks + " module path checks failed");
            System.exit(1);
        }
        System.out.println("All " + check.checks + " module path checks passed");
    }

    private void run() {
        checkModule(ModulePathCheck.class, "no.entur.protobuf.json.moduletest.full");
        checkModule(TranscodingProtobufJsonWriter.class, "no.entur.protobuf.json.transcode");
        checkModule(CodedOutputStreamProtobufJsonWriter.class, "no.entur.protobuf.json.stream");
        checkModule(AccessorProtobufJsonWriter.class, "no.entur.protobuf.json.accessor");
        checkModule(JsonFormatProtobufJsonWriter.class, "no.entur.protobuf.json.utility");

        check("transcode", EXPECTED, () -> TranscodingProtobufJsonWriter.newBuilder().build());
        check("filtering transcode", EXPECTED_FILTERED, () -> filtered(TranscodingProtobufJsonWriter.newBuilder()).build());

        check("stream", EXPECTED, () -> CodedOutputStreamProtobufJsonWriter.newBuilder().build());
        check("filtering stream", EXPECTED_FILTERED, () -> filtered(CodedOutputStreamProtobufJsonWriter.newBuilder()).build());

        // registering the message types fails if no writer can be generated, rather than transcoding instead
        check("accessor", EXPECTED, () -> AccessorProtobufJsonWriter.newBuilder().withMessageTypes(Customer.class).build());
        check("filtering accessor", EXPECTED_FILTERED, () -> filtered(AccessorProtobufJsonWriter.newBuilder()).withMessageTypes(Customer.class).build());
        check("accessor with warmup", EXPECTED, () -> AccessorProtobufJsonWriter.newBuilder().withWarmup(CUSTOMER).withWarmupIterations(10).build());

        check("JsonFormat writer", EXPECTED, () -> JsonFormatProtobufJsonWriter.create(JsonFormat.TypeRegistry.getEmptyTypeRegistry()));
    }

    private static <B extends AbstractProtobufJsonWriterBuilder<B>> B filtered(B builder) {
        return builder.withAnonymizePaths("$.email").withPrunePaths("$.address");
    }

    private void checkModule(Class<?> type, String expected) {
        checks++;
        String name = type.getModule().getName();
        if (!expected.equals(name)) {
            failures.add(type.getName() + ": expected to be in module " + expected + " on the module path, but was in " + type.getModule());
        }
    }

    private void check(String name, String expected, Callable<ProtobufJsonWriter<? super Customer, ?>> writer) {
        checks++;
        try {
            String json = writer.call().writeAsString(CUSTOMER);
            if (!expected.equals(json)) {
                failures.add(name + ": expected " + expected + " but was " + json);
            }
        } catch (Throwable e) {
            StringWriter stackTrace = new StringWriter();
            e.printStackTrace(new PrintWriter(stackTrace));
            failures.add(name + ": " + stackTrace);
        }
    }
}

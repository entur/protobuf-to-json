package no.entur.protobuf.json.moduletest.lite;

import com.google.protobuf.Timestamp;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.lite.LiteProtobufJsonWriter;
import no.entur.protobuf.json.moduletest.lite.internal.Secret;
import no.entur.protobuf.json.moduletest.lite.messages.Address;
import no.entur.protobuf.json.moduletest.lite.messages.Customer;
import no.entur.protobuf.json.moduletest.lite.messages.Status;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Writes a message with the lite writer, from a named module on the module path, and exits with status 1 if any output is
 * not as expected.
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
        checkModule(ModulePathCheck.class, "no.entur.protobuf.json.moduletest.lite");
        checkModule(LiteProtobufJsonWriter.class, "no.entur.protobuf.json.lite");

        check("lite", EXPECTED, () -> LiteProtobufJsonWriter.newBuilder().build());
        check("filtering lite", EXPECTED_FILTERED, () -> LiteProtobufJsonWriter.newBuilder().withAnonymizePaths("$.email").withPrunePaths("$.address").build());

        // the fields of a message in a package which is not exported cannot be read: fail rather than write {}
        checks++;
        try {
            String json = LiteProtobufJsonWriter.newBuilder().build().writeAsString(Secret.newBuilder().setName("x").build());
            failures.add("message in a package which is not exported: expected an exception, but was " + json);
        } catch (IllegalStateException e) {
            if (e.getMessage() == null || !e.getMessage().contains("export")) {
                fail("message in a package which is not exported: expected a message about exports", e);
            }
        } catch (Throwable e) {
            fail("message in a package which is not exported", e);
        }
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
            fail(name, e);
        }
    }

    private void fail(String name, Throwable e) {
        StringWriter stackTrace = new StringWriter();
        e.printStackTrace(new PrintWriter(stackTrace));
        failures.add(name + ": " + stackTrace);
    }
}

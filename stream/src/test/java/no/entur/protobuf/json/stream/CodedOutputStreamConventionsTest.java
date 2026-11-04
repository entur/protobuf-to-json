package no.entur.protobuf.json.stream;

import no.entur.protobuf.json.stream.CodedOutputStreamConventions.Call;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CodedOutputStreamConventionsTest {

    @Test
    public void acceptsTheCallingConventionOfThisProtobufVersion() throws Throwable {
        assertDoesNotThrow(() -> CodedOutputStreamConventions.verify(JsonCodedOutputStreamFactory.defineConstructor()));
        JsonCodedOutputStreamFactory.verify();
    }

    @Test
    public void rejectsFieldDataWrittenByIgnoredMethods() {
        List<Call> dropped = List.of(new Call("write", new byte[] {1, 2}, 0, 2));
        assertThrows(IllegalStateException.class, () -> CodedOutputStreamConventions.verifyPacked(dropped));
        assertThrows(IllegalStateException.class, () -> CodedOutputStreamConventions.verifyMap(List.of(new Call("writeLazy", new byte[1], 0, 1))));
        assertThrows(IllegalStateException.class, () -> CodedOutputStreamConventions.verifyBytes(List.of(new Call("write", ByteBuffer.allocate(1)))));
    }

    @Test
    public void rejectsAPackedFieldWhoseValuesAreNotWrittenUntagged() {
        List<Call> calls = List.of(
                new Call("writeUInt32NoTag", 10),
                new Call("writeUInt32NoTag", 2),
                new Call("writeString", 3, "x"));
        assertThrows(IllegalStateException.class, () -> CodedOutputStreamConventions.verifyPacked(calls));
    }
}

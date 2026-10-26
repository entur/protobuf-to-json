package no.entur.protobuf.json.stream;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class JsonCodedOutputStreamFactoryTest {

    @Test
    public void definesConstructor() throws Throwable {
        MethodHandle constructor = JsonCodedOutputStreamFactory.defineConstructor();
        assertThat(constructor.type().returnType()).isEqualTo(CodedOutputStream.class);
        assertThat(constructor.type().parameterList()).containsExactly(CodedOutputStreamSink.class);

        JsonCodedOutputStreamFactory.verify();
    }

    @Test
    public void forwardsWritesToTheSink() throws Exception {
        List<String> calls = new ArrayList<>();
        CodedOutputStreamSink sink = (CodedOutputStreamSink) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {CodedOutputStreamSink.class},
                (proxy, method, args) -> {
                    calls.add(method.getName() + (args != null ? List.of(args) : List.of()));
                    return method.getReturnType() == int.class ? 0 : null;
                });

        CodedOutputStream output = JsonCodedOutputStreamFactory.create(sink);
        Timestamp.newBuilder().setSeconds(1).setNanos(2).build().writeTo(output);

        // writeInt64 is final, and calls writeUInt64
        assertThat(calls).containsExactly("writeUInt64[1, 1]", "writeInt32[2, 2]").inOrder();
    }
}

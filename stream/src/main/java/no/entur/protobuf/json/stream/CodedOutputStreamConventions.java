package no.entur.protobuf.json.stream;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo.Location;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.WireFormat;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Fails if generated {@linkplain CodedOutputStream} calls are not a convention {@linkplain CodedOutputStreamJsonSink}
 * handles. A protobuf upgrade which starts writing field data through the sink's no-op methods, or which stops writing
 * packed fields and maps as a length-delimited tag, a length and then the values, would otherwise drop fields silently.
 */

final class CodedOutputStreamConventions {

    private CodedOutputStreamConventions() {
    }

    /**
     * Write messages from protobuf-java, whose generated code uses the packed, map, string and bytes conventions, and
     * check the calls.
     *
     * @param constructor constructor of a JSON-writing {@linkplain CodedOutputStream}, as returned by
     *                    {@linkplain JsonCodedOutputStreamFactory#defineConstructor()}
     * @throws IllegalStateException if a call sequence is not one the sink handles
     */

    static void verify(MethodHandle constructor) throws Throwable {
        verifyPacked(record(constructor, Location.newBuilder().addPath(1).addPath(128).addSpan(3).setLeadingComments("x").build()));
        verifyMap(record(constructor, Struct.newBuilder().putFields("k", Value.newBuilder().setNumberValue(1).build()).build()));
        verifyBytes(record(constructor, BytesValue.newBuilder().setValue(ByteString.copyFromUtf8("ab")).build()));
    }

    static void verifyPacked(List<Call> calls) {
        rejectDropped(calls, "packed field");
        int tag = indexOfLengthDelimitedTag(calls, 1, 0);
        if (tag < 0 || !isUInt32NoTag(calls, tag + 1) || !seenInt32NoTag(calls, tag + 2, 1) || !seenInt32NoTag(calls, tag + 2, 128)) {
            throw fail("packed field", calls);
        }
        if (!seenString(calls, "x")) {
            throw fail("string field", calls);
        }
    }

    static void verifyMap(List<Call> calls) {
        rejectDropped(calls, "map field");
        int tag = indexOfLengthDelimitedTag(calls, 1, 0);
        if (tag < 0 || !isUInt32NoTag(calls, tag + 1) || !seenString(calls, "k") || (!seen(calls, "writeMessage") && !seen(calls, "writeMessageNoTag"))) {
            throw fail("map field", calls);
        }
    }

    static void verifyBytes(List<Call> calls) {
        rejectDropped(calls, "bytes field");
        if (!seen(calls, "writeBytes") && !seen(calls, "writeBytesNoTag") && !seen(calls, "writeByteArray") && !seen(calls, "writeByteArrayNoTag")) {
            throw fail("bytes field", calls);
        }
    }

    private static List<Call> record(MethodHandle constructor, MessageLite message) throws Throwable {
        List<Call> calls = new ArrayList<>();
        CodedOutputStreamSink sink = (CodedOutputStreamSink) Proxy.newProxyInstance(CodedOutputStreamSink.class.getClassLoader(), new Class<?>[] {CodedOutputStreamSink.class},
                (proxy, method, args) -> {
                    if (!method.getDeclaringClass().equals(Object.class)) {
                        calls.add(new Call(method.getName(), args == null ? new Object[0] : args));
                    }
                    if (method.getReturnType() == int.class) {
                        return method.getName().equals("spaceLeft") ? Integer.MAX_VALUE : 0;
                    }
                    return null;
                });
        CodedOutputStream output = (CodedOutputStream) constructor.invokeExact(sink);
        message.writeTo(output);
        return calls;
    }

    private static void rejectDropped(List<Call> calls, String what) {
        for (Call call : calls) {
            if (isDropped(call)) {
                throw fail(what + " written by " + call + ", which the sink ignores", calls);
            }
        }
    }

    private static boolean isDropped(Call call) {
        if (call.name.equals("writeLazy") || call.name.equals("writeRawBytes")) {
            return true;
        }
        if (!call.name.equals("write")) {
            return false;
        }
        // write(byte) is writeBoolNoTag; write(byte[]) and write(ByteBuffer) are ignored
        return call.args.length != 1 || call.args[0] instanceof ByteBuffer;
    }

    private static int indexOfLengthDelimitedTag(List<Call> calls, int fieldNumber, int from) {
        int encoded = (fieldNumber << 3) | WireFormat.WIRETYPE_LENGTH_DELIMITED;
        for (int i = from; i < calls.size(); i++) {
            Call call = calls.get(i);
            if (call.name.equals("writeUInt32NoTag") && call.argInt(0) == encoded) {
                return i;
            }
            if (call.name.equals("writeTag") && call.argInt(0) == fieldNumber && call.argInt(1) == WireFormat.WIRETYPE_LENGTH_DELIMITED) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isUInt32NoTag(List<Call> calls, int index) {
        return index < calls.size() && calls.get(index).name.equals("writeUInt32NoTag");
    }

    private static boolean seenInt32NoTag(List<Call> calls, int from, int value) {
        for (int i = from; i < calls.size(); i++) {
            Call call = calls.get(i);
            if (call.name.equals("writeInt32NoTag") && call.argInt(0) == value) {
                return true;
            }
        }
        return false;
    }

    private static boolean seenString(List<Call> calls, String value) {
        for (Call call : calls) {
            if ((call.name.equals("writeString") || call.name.equals("writeStringNoTag")) && value.equals(call.args[call.args.length - 1])) {
                return true;
            }
        }
        return false;
    }

    private static boolean seen(List<Call> calls, String name) {
        for (Call call : calls) {
            if (call.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static IllegalStateException fail(String what, List<Call> calls) {
        return new IllegalStateException("CodedOutputStream calling convention is not one this writer handles (" + what + "): " + calls);
    }

    static final class Call {
        final String name;
        final Object[] args;

        Call(String name, Object... args) {
            this.name = name;
            this.args = args;
        }

        int argInt(int index) {
            return ((Number) args[index]).intValue();
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(name);
            for (Object arg : args) {
                builder.append(' ');
                if (arg instanceof byte[] bytes) {
                    builder.append("byte[").append(bytes.length).append(']');
                } else if (arg instanceof MessageLite || arg instanceof ByteString || arg instanceof ByteBuffer) {
                    builder.append(arg.getClass().getSimpleName());
                } else {
                    builder.append(arg);
                }
            }
            return builder.toString();
        }
    }
}

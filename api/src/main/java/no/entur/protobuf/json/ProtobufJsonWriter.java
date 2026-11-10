package no.entur.protobuf.json;

import java.io.UncheckedIOException;

/**
 * Writes protobuf messages as JSON, using the <a href="https://protobuf.dev/programming-guides/json/">ProtoJSON format</a>
 * with insignificant whitespace omitted, optionally filtered by path (anonymize and prune), max string length and max size.
 * <br><br>
 * The full protobuf implementations write to a Jackson 3 generator, the lite implementation to a Jackson 2 generator.
 * <br><br>
 * No checked exceptions are thrown. An {@linkplain java.io.IOException} is wrapped in an {@linkplain UncheckedIOException}:
 * a {@code com.google.protobuf.InvalidProtocolBufferException} for a {@code google.protobuf.Any} with an invalid type URL
 * (also when unresolved Any values are written as such, see {@linkplain UnresolvedAny}), whose type is not registered
 * (unless unresolved Any values are written as such), or whose content cannot be parsed, and the exceptions of Jackson 2
 * generators. Jackson 3 generators throw their own unchecked exceptions
 * ({@code tools.jackson.core.JacksonException}), which are not wrapped.
 * <br><br>
 * Implementations are thread-safe.
 *
 * @param <M> message type
 * @param <G> JSON generator type
 */

public interface ProtobufJsonWriter<M, G> {

    /**
     * Write a message as a JSON value.
     *
     * @param message   message or builder
     * @param generator generator positioned where a value can be written
     * @return true if the whole message was written, false if writing stopped because of the max size (the output is
     * then truncated, but still valid JSON)
     * @throws UncheckedIOException if the message could not be written, see above
     */

    boolean write(M message, G generator);

    /**
     * Write a message as a JSON string.
     *
     * @param message message or builder
     * @return JSON
     * @throws UncheckedIOException if the message could not be written, see above
     */

    String writeAsString(M message);
}

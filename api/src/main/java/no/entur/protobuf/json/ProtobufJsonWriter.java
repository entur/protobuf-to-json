package no.entur.protobuf.json;

import java.io.IOException;

/**
 * Writes protobuf messages as JSON, using the <a href="https://protobuf.dev/programming-guides/json/">ProtoJSON format</a>
 * with insignificant whitespace omitted, optionally filtered by path (anonymize and prune), max string length and max size.
 * <br><br>
 * The full protobuf implementations write to a Jackson 3 generator, the lite implementation to a Jackson 2 generator.
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
     * @throws IOException if the message could not be written
     */

    void write(M message, G generator) throws IOException;

    /**
     * Write a message as a JSON string.
     *
     * @param message message or builder
     * @return JSON
     * @throws IOException if the message could not be written
     */

    String writeAsString(M message) throws IOException;
}

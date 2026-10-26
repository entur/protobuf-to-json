package no.entur.protobuf.json.accessor;

import com.google.protobuf.Message;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;

import java.io.IOException;

/**
 * Writes messages of one type as JSON objects; implemented by the generated writers.
 * <br><br>
 * Internal; public only because the generated writers may be defined in the packages of the messages.
 */

public interface MessageWriter {

    /**
     * Write a message as a JSON object.
     *
     * @param generator generator
     * @param name      field name, or null
     * @param message   message of the type of this writer
     * @param writer    the writer, for google.protobuf.Any
     * @throws IOException if writing fails
     */

    void write(JsonGenerator generator, SerializableString name, Message message, AccessorProtobufJsonWriter writer) throws IOException;
}

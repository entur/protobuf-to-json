package no.entur.protobuf.json.accessor;

import com.google.protobuf.Message;
import no.entur.protobuf.json.plan.FilteredJsonValues;
import tools.jackson.core.SerializableString;

import java.io.IOException;

/**
 * Writes messages of one type as filtered JSON objects; implemented by the generated writers.
 * <br><br>
 * Internal; public only because the generated writers may be defined in the packages of the messages.
 */

public interface FilteringMessageWriter {

    /**
     * Write a message as a JSON object.
     *
     * @param values    state of the write
     * @param name      field name, or null
     * @param nameSize  size of the field name
     * @param message   message of the type of this writer
     * @param anonymize whether to anonymize all values
     * @param writer    the writer, for google.protobuf.Any
     * @return false if writing stopped because of max size
     * @throws IOException if writing fails
     */

    boolean write(FilteredJsonValues values, SerializableString name, int nameSize, Message message, boolean anonymize, FilteringAccessorProtobufJsonWriter writer) throws IOException;
}

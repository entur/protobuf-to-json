/**
 * Writes full protobuf messages as JSON with code generated per message type.
 */
module no.entur.protobuf.json.accessor {
    requires transitive no.entur.protobuf.json.transcode;

    // MessageWriters logs when generation falls back to transcoding
    requires java.logging;

    exports no.entur.protobuf.json.accessor;
}

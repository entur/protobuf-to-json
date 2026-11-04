/**
 * Writes full protobuf messages as JSON with code generated per message type.
 */
module no.entur.protobuf.json.accessor {
    requires transitive no.entur.protobuf.json.transcode;

    exports no.entur.protobuf.json.accessor;
}

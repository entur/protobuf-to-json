/**
 * Writes full protobuf messages as JSON through the generated serialization code.
 */
module no.entur.protobuf.json.stream {
    requires transitive no.entur.protobuf.json.transcode;

    exports no.entur.protobuf.json.stream;
}

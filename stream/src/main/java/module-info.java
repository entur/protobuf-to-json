/**
 * Writes full protobuf messages as JSON through the generated serialization code.
 */
module no.entur.protobuf.json.stream {
    requires transitive no.entur.protobuf.json.transcode;

    // JsonCodedOutputStreamFactory logs when the writers fall back to transcoding (#13)
    requires java.logging;

    exports no.entur.protobuf.json.stream;
}

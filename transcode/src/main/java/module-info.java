/**
 * Writes full protobuf messages as JSON by transcoding their binary form.
 */
module no.entur.protobuf.json.transcode {
    requires transitive no.entur.protobuf.json.api;
    requires transitive com.google.protobuf;
    requires transitive com.google.protobuf.util;
    requires transitive tools.jackson.core;

    exports no.entur.protobuf.json.transcode;

    // generated writers and the other writer modules call these types
    exports no.entur.protobuf.json.plan;
}

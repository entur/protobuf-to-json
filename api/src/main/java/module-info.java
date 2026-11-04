/**
 * Writer API and the filter logic shared by the full and lite implementations.
 */
module no.entur.protobuf.json.api {
    requires transitive no.entur.protobuf.json.path;

    exports no.entur.protobuf.json;

    // the writer modules; not application API
    exports no.entur.protobuf.json.filter to
            no.entur.protobuf.json.transcode,
            no.entur.protobuf.json.stream,
            no.entur.protobuf.json.accessor,
            no.entur.protobuf.json.lite;
}

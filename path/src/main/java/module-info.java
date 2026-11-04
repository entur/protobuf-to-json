/**
 * Path matching for anonymize and prune filters.
 */
module no.entur.protobuf.json.path {
    exports no.entur.protobuf.json.path;

    // the any-path lookup is only used by the api module
    exports no.entur.protobuf.json.path.any to no.entur.protobuf.json.api;
}

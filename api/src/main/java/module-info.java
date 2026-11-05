/**
 * Writer API and the filter logic shared by the full and lite implementations.
 */
module no.entur.protobuf.json.api {
    requires transitive no.entur.protobuf.json.path;

    exports no.entur.protobuf.json;

    // the builders and the filtering writers take and return JsonFilter; the rest supports the writer modules
    exports no.entur.protobuf.json.filter;
}

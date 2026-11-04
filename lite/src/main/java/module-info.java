/**
 * Writes protobuf lite messages as JSON.
 */
module no.entur.protobuf.json.lite {
    requires transitive no.entur.protobuf.json.api;
    requires transitive com.google.protobuf;
    requires transitive com.fasterxml.jackson.core;

    exports no.entur.protobuf.json.lite;
}

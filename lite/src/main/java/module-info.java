/**
 * Writes protobuf lite messages as JSON.
 */
module no.entur.protobuf.json.lite {
    requires transitive no.entur.protobuf.json.api;
    requires transitive com.google.protobuf;
    requires transitive com.fasterxml.jackson.core;

    // protobuf lite messages need sun.misc.Unsafe; protobuf-javalite is an automatic module, so it does not declare that
    requires jdk.unsupported;

    exports no.entur.protobuf.json.lite;
}

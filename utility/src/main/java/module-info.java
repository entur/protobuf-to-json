/**
 * JsonFormat comparison and a writer which uses JsonFormat.
 */
module no.entur.protobuf.json.utility {
    requires transitive no.entur.protobuf.json.api;
    requires transitive com.google.protobuf;
    requires transitive com.google.protobuf.util;
    requires transitive tools.jackson.core;

    // JsonFormat writes with Gson; protobuf-java-util is an automatic module, so it does not declare that it needs it
    requires com.google.gson;

    exports no.entur.protobuf.json.jsonformat;
}

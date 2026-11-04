/**
 * JsonFormat comparison and a writer which uses JsonFormat.
 */
module no.entur.protobuf.json.utility {
    requires transitive no.entur.protobuf.json.api;
    requires transitive com.google.protobuf;
    requires transitive com.google.protobuf.util;
    requires transitive tools.jackson.core;

    exports no.entur.protobuf.json.jsonformat;
}

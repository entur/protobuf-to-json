/**
 * An application module which requires the full protobuf writer modules.
 */
module no.entur.protobuf.json.moduletest.full {
    requires no.entur.protobuf.json.accessor;
    requires no.entur.protobuf.json.stream;
    requires no.entur.protobuf.json.utility;

    // for protobuf's reflection and the accessor writer
    exports no.entur.protobuf.json.moduletest.full.messages;
}

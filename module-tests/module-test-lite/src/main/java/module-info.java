/**
 * An application module which requires the lite writer module.
 */
module no.entur.protobuf.json.moduletest.lite {
    requires no.entur.protobuf.json.lite;

    // for the lite writer, which reads the fields with reflection; no.entur.protobuf.json.moduletest.lite.internal is not
    // exported, so its messages cannot be written
    exports no.entur.protobuf.json.moduletest.lite.messages;
}

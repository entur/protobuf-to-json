/**
 * Writer which reads fields with generated code and the message accessors.
 * <br><br>
 * {@link MessageWriters}, {@link MessageWriter} and {@link FilteringMessageWriter} are public only because the generated
 * writers may be defined in the packages of the messages. They are internal, not application API.
 * <br><br>
 * On the module path, the generated writers read the fields of the messages through their public accessors, so an
 * application module must export its message packages (at least to {@code no.entur.protobuf.json.accessor}). If the
 * message classes are loaded by another class loader than this library, the writers are defined in the packages of the
 * messages, so a named module must open those packages to {@code no.entur.protobuf.json.accessor}. Message types a writer
 * cannot be generated for are transcoded instead.
 */
package no.entur.protobuf.json.accessor;

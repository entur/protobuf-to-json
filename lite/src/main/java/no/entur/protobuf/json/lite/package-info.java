/**
 * Writer for protobuf lite messages.
 * <br><br>
 * The writers read the fields of the messages with reflection over their public accessors and field number constants.
 * On the module path, an application module must therefore export its message packages (at least to
 * {@code no.entur.protobuf.json.lite}), although protobuf lite itself does not need that; otherwise writing fails.
 */
package no.entur.protobuf.json.lite;

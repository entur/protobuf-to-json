package no.entur.protobuf.json;

/**
 * What to do when a {@code google.protobuf.Any} type is not registered.
 * A type URL without a {@code /} is invalid and always fails the write, as does JsonFormat.
 * An empty Any (type URL and value both empty) is written as <code>{}</code> either way.
 */

public enum UnresolvedAny {

    /**
     * Fail the write, as JsonFormat does. This is the default.
     */
    FAIL,

    /**
     * Write <code>{"@type": url, "value": base64}</code> and do not parse the payload.
     * Filtering matches {@code @type} and {@code value} as for a well-known Any.
     * Max string length applies to both.
     */
    VALUE
}

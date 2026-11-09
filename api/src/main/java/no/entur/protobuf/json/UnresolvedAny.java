package no.entur.protobuf.json;

/**
 * What to do when a {@code google.protobuf.Any} type is not registered.
 * A type URL without a {@code /} (other than trailing slashes) is invalid and always fails the write, as does JsonFormat.
 * An empty Any (type URL and value both empty) is written as <code>{}</code> either way.
 */

public enum UnresolvedAny {

    /**
     * Fail the write, as JsonFormat does. This is the default.
     */
    FAIL,

    /**
     * Write <code>{"@type": url, "@value": base64}</code> and do not parse the payload. No protobuf JSON field is
     * named {@code @value}, so parsers reject this rather than read the payload as a field.
     * <br><br>
     * The well-known types (other than google.protobuf.Empty) are always resolved, as by the lite writer, even if they are
     * not in the type registry of a full protobuf writer.
     * <br><br>
     * Filtering: paths match {@code @type} and {@code @value} as usual, and max string length applies to both. Paths
     * can not match within the payload, so if a path could have matched there, the payload is pruned (if such a path
     * prunes) or anonymized: an any-depth path ({@code $..name}, also from anonymize and prune keys), or an absolute
     * path which goes on into the Any other than through {@code @type} or {@code @value}, including through a wildcard.
     * A path below a field which a shorter absolute path filters as a whole does not count.
     */
    VALUE
}

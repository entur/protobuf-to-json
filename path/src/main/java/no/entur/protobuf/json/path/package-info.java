/**
 * Path expressions ({@code $.a.b}, {@code $.a.*.b}, {@code $..b}), compiled to a {@link PathFilter} and matched with a
 * {@link PathMatcher}.
 * <br><br>
 * Applications pass expressions to the writer builders; {@link PathExpressions} documents the syntax. The state machine
 * behind {@link PathFilter} is package-private.
 */
package no.entur.protobuf.json.path;

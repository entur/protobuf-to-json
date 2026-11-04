/**
 * Compiled filter options and the filter state of one write: paths, max string length and max size.
 * <br><br>
 * {@link JsonFilter} is what {@link no.entur.protobuf.json.AbstractProtobufJsonWriterBuilder#buildFilter()} returns and
 * what the filtering writers take and return. The other types are public because the writer modules and the generated
 * writers use them; they are not application API and may change between releases.
 */
package no.entur.protobuf.json.filter;

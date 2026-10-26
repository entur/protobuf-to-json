package no.entur.protobuf.json.filter;

import no.entur.protobuf.json.path.FilterType;
import no.entur.protobuf.json.path.PathFilter;
import no.entur.protobuf.json.path.PathItem;
import no.entur.protobuf.json.path.any.AnyPathFilters;

/**
 * State of a single filtered write, shared by the full and lite writers. Not thread-safe.
 * <br><br>
 * The writers report every JSON token before writing it, and only write it if it is accepted:
 * <ul>
 *     <li>{@linkplain #startObject(int)}, {@linkplain #startArray(int)}, {@linkplain #endObject()}, {@linkplain #endArray()}</li>
 *     <li>{@linkplain #scalar(int, long)} for anything else, including anonymized and pruned values</li>
 * </ul>
 * with the size of the field name (see {@linkplain JsonOutputSize#getNameSize(CharSequence, boolean)}), or 0 if there is none.
 * <br><br>
 * Paths: {@linkplain #matchName(String)} matches a field name in the current object; levels count objects only, so arrays are transparent.
 * <br><br>
 * Max size, as in json-log-filter: a field name is written only together with its value, each open object or array
 * reserves room for its closing bracket, and writing stops at the first token which does not fit. The writers then close the open
 * objects and arrays, which always fit. A scalar root value, and the root object or array, are always written.
 */

public final class FilterContext {

    private final JsonFilter filter;
    private final boolean utf8;
    private final boolean escapeSurrogates;

    // paths
    private final boolean absolutePaths;
    private final AnyPathFilters anyPathFilters;
    private PathItem pathItem;
    private int level;

    // max size
    private final boolean sizing;
    private long limit;
    private long written;
    private boolean stopped;
    private boolean[] elements; // per open object or array, whether it has elements, for the separator
    private int depth;

    FilterContext(JsonFilter filter, boolean utf8, boolean escapeSurrogates) {
        this.filter = filter;
        this.utf8 = utf8;
        this.escapeSurrogates = utf8 && escapeSurrogates;

        PathFilter pathFilter = filter.getPathFilter();
        if (pathFilter != null) {
            this.absolutePaths = pathFilter.hasAbsolutePaths();
            this.anyPathFilters = pathFilter.getAnyPathFilters();
            this.pathItem = pathFilter.getPathItem();
        } else {
            this.absolutePaths = false;
            this.anyPathFilters = null;
        }

        this.sizing = filter.hasMaxSize();
        if (sizing) {
            this.limit = filter.getMaxSize();
            this.elements = new boolean[16];
        }
    }

    public JsonFilter getFilter() {
        return filter;
    }

    public boolean isUtf8() {
        return utf8;
    }

    /**
     * Whether strings written as characters have their surrogates escaped, like Jackson 2 generators which write UTF-8 do by default.
     *
     * @return true if surrogates are escaped
     */

    public boolean isEscapeSurrogates() {
        return escapeSurrogates;
    }

    /**
     * Whether output size is limited, so that tokens must be reported with their size.
     *
     * @return true if max size applies
     */

    public boolean isSizing() {
        return sizing;
    }

    /**
     * Whether writing stopped because of max size; only closing brackets may be written.
     *
     * @return true if stopped
     */

    public boolean isStopped() {
        return stopped;
    }

    /**
     * Size written so far, when sizing.
     *
     * @return size in characters or bytes
     */

    public long getWritten() {
        return written;
    }

    /**
     * Match a field name in the current object. Only call it when not within an anonymized value.
     *
     * @param name field name
     * @return {@linkplain FilterType#PRUNE}, {@linkplain FilterType#ANON}, or null if no path matched
     */

    public FilterType matchName(String name) {
        FilterType type = null;
        if (absolutePaths) {
            PathItem item = pathItem.constrain(level).matchPath(level, name);
            if (item.hasType()) {
                type = item.getType();
                item = item.constrain(level);
            }
            pathItem = item;
        }
        if (anyPathFilters != null) {
            type = FilterType.strongest(type, anyPathFilters.matchPath(name));
        }
        return type;
    }

    public boolean hasPaths() {
        return absolutePaths || anyPathFilters != null;
    }

    /**
     * Report the start of an object.
     *
     * @param nameSize size of the field name and colon, or 0
     * @return true if the object should be written, false if writing stopped
     */

    public boolean startObject(int nameSize) {
        if (sizing && !start(nameSize)) {
            return false;
        }
        level++;
        return true;
    }

    /**
     * Report the start of an array.
     *
     * @param nameSize size of the field name and colon, or 0
     * @return true if the array should be written, false if writing stopped
     */

    public boolean startArray(int nameSize) {
        return !sizing || start(nameSize);
    }

    private boolean start(int nameSize) {
        if (stopped) {
            return false;
        }
        if (depth == 0) {
            // root is always written
            limit--;
            written++;
            push();
            return true;
        }
        long size = (elements[depth - 1] ? 1 : 0) + nameSize + 1;
        // the bracket, and room for the closing bracket
        if (written + size > limit - 1) {
            stopped = true;
            return false;
        }
        limit--;
        written += size;
        elements[depth - 1] = true;
        push();
        return true;
    }

    private void push() {
        if (depth == elements.length) {
            boolean[] next = new boolean[elements.length * 2];
            System.arraycopy(elements, 0, next, 0, elements.length);
            elements = next;
        }
        elements[depth++] = false;
    }

    public void endObject() {
        if (sizing) {
            end();
        }
        level--;
    }

    public void endArray() {
        if (sizing) {
            end();
        }
    }

    private void end() {
        // uses the reserved room
        limit++;
        written++;
        depth--;
    }

    /**
     * Report a scalar value: string, number, boolean, null, or an anonymized or pruned value.
     *
     * @param nameSize  size of the field name and colon, or 0
     * @param valueSize size of the value
     * @return true if the value should be written, false if writing stopped
     */

    public boolean scalar(int nameSize, long valueSize) {
        if (!sizing) {
            return true;
        }
        if (stopped) {
            return false;
        }
        if (depth == 0) {
            // a root scalar is always written
            written += valueSize;
            return true;
        }
        long size = (elements[depth - 1] ? 1 : 0) + nameSize + valueSize;
        if (written + size > limit) {
            stopped = true;
            return false;
        }
        written += size;
        elements[depth - 1] = true;
        return true;
    }

    // convenience methods for sizes in the current output unit

    /**
     * Size of a field name written as a string (not as a pre-encoded name).
     *
     * @param name field name
     * @return size of the name and the colon
     */

    public int getNameSize(CharSequence name) {
        return JsonOutputSize.getNameSize(name, utf8, escapeSurrogates);
    }

    /**
     * Size of a string value written as characters.
     *
     * @param value string
     * @return size
     */

    public int getQuotedSize(CharSequence value) {
        return JsonOutputSize.getQuotedSize(value, utf8, escapeSurrogates);
    }

    public int getQuotedUtf8Size(byte[] bytes, int offset, int length) {
        return JsonOutputSize.getQuotedUtf8Size(bytes, offset, length, utf8);
    }

    public int getAnonymizeSize() {
        return filter.getAnonymizeSize(utf8);
    }

    public int getPruneSize() {
        return filter.getPruneSize(utf8);
    }

    /**
     * Size of a truncated string.
     *
     * @param value   string
     * @param keep    number of characters kept
     * @param removed number of characters removed
     * @return size
     */

    public int getTruncatedSize(CharSequence value, int keep, int removed) {
        return JsonOutputSize.getQuotedSize(value, 0, keep, utf8, escapeSurrogates) + filter.getTruncateMessageSize(utf8) + MaxStringLength.lengthToDigits(removed);
    }

    /**
     * Size of a truncated UTF-8 string.
     *
     * @param bytes     UTF-8 bytes
     * @param offset    offset
     * @param keepBytes number of bytes kept
     * @param removed   number of characters removed
     * @return size
     */

    public int getTruncatedUtf8Size(byte[] bytes, int offset, int keepBytes, int removed) {
        return JsonOutputSize.getQuotedUtf8Size(bytes, offset, keepBytes, utf8) + filter.getTruncateMessageSize(utf8) + MaxStringLength.lengthToDigits(removed);
    }
}

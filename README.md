# protobuf-to-json

High-performance protobuf-to-JSON conversion. With or without filtering. Regular or lite messages. Multiple writers.

## Usage
Add a writer module, here the `accessor` writer (see [Choosing a writer](#choosing-a-writer) and
[Maven coordinates](#maven-coordinates) for the others):

```xml
<dependency>
    <groupId>no.entur.protobuf-to-json</groupId>
    <artifactId>protobuf-to-json-accessor</artifactId>
    <version>${protobuf-to-json.version}</version>
</dependency>
```

The `accessor` and `stream` writers need Java 24 or later. On Java 17 to 23, use the transcoding writer
(`TranscodingProtobufJsonWriter` in `protobuf-to-json-transcode`), which has the same builder.

Writers are thread-safe; create them once and reuse them:

```java
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.accessor.AccessorProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;

// the types of google.protobuf.Any values; add(..) also adds the other types of their .proto files, and of the
// files those import
JsonFormat.TypeRegistry typeRegistry = JsonFormat.TypeRegistry.newBuilder()
        .add(MyMessage.getDescriptor())
        .build();

ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = AccessorProtobufJsonWriter.newBuilder()
        .withTypeRegistry(typeRegistry)
        .withAnonymizeKeys("password", "token")
        .withPrunePaths("$.customer.photo")
        .withMaxStringLength(1024)
        .withMaxSize(64 * 1024)
        .build();

String json = writer.writeAsString(message);
```

The full protobuf writers write to Jackson 3 generators (`tools.jackson.core`), the lite writer to Jackson 2 generators
(`com.fasterxml.jackson.core`). To write a message as part of a document of your own:

```java
JsonFactory factory = new JsonFactory(); // tools.jackson.core.json.JsonFactory; create it once
try (JsonGenerator generator = factory.createGenerator(ObjectWriteContext.empty(), outputStream)) {
    boolean complete = writer.write(message, generator); // false if cut short by the max size
}
```

With a library which writes JSON with another Jackson version, use `writeAsString(..)`, and write the result as a raw
value.

`write(..)` and `writeAsString(..)` throw no checked exceptions. If a message cannot be written, they throw an
`UncheckedIOException`, whose cause is an `InvalidProtocolBufferException` for a `google.protobuf.Any` whose type is not
registered (see [Unresolved Any](#unresolved-any)) or whose content cannot be parsed, or the `IOException` of a Jackson 2
generator. Jackson 3 generators throw their own unchecked `JacksonException`, which is not wrapped.

## Protobuf (regular) message writers
Alternative writers, with different trade-offs between speed, memory usage and JIT compilation:

 * **Accessor**: code generated per message type reads the fields with their accessors (getters). 
   * The fastest, but uses more memory per message type than the other writers
 * **Stream**: custom `CodedOutputStream` which outputs JSON rather than the binary format.
   * Reflection limited to the initialization of the writer itself.
   * Shares the compiled serialization code with binary serialization, which makes binary serialization [somewhat slower](PERFORMANCE.md#the-stream-writer-and-the-jit).
 * **Transcoding**: the message is serialized to bytes, which are then read field by field and written as JSON.
   * Uses only public protobuf APIs.

See [PERFORMANCE.md](PERFORMANCE.md).

### Choosing a writer
Use the `accessor writer`, unless:

 * the application runs on Java 17 to 23: use the `transcoding` writer, as the `accessor` and `stream` writers need
   Java 24
 * the application writes very many message types: the `stream` writer uses less memory per message type, about half a
   KB of metaspace per field written against about 2 KB, and about 1 MB less in all (see
   [memory per message type](PERFORMANCE.md#memory-per-message-type))
 * many message types are written only rarely, and the first message of each type must be fast
   * the `accessor` writer generates the code for a message type when the first message of the type is written, alternatively
   * warming up (`withWarmup(..)`, `withWarmupTypes(..)`) moves the code generation to when the writer is built
 * the messages are mostly `DynamicMessage`s: the `stream` writer writes them directly, while the `accessor` writer
   transcodes them, which is slower
 * the JIT is not HotSpot's C2 with its default inlining: the `accessor` writer relies on the JIT not inlining the writing of
   values into the generated code (see [writing values out of line](accessor/README.md#writing-values-out-of-line)); with
   another JIT, or a larger `-XX:FreqInlineSize`, it could lose about 30%

The `stream` writer, in turn, depends on protobuf internals: the private constructor of `CodedOutputStream`, and which of
its methods the generated code calls. It needs protobuf 4.32 or later, and a protobuf upgrade can break it; otherwise it
logs a warning and delegates to the transcoding writer, which writes the same JSON more slowly. The `accessor` writer
only depends on the names of the accessors, which are part of protobuf's generated API. The transcoding writer only uses
public protobuf APIs and runs on Java 17, but is the slowest of the three.

<details>
<summary>Differences from JsonFormat</summary>

* extensions are not written
* out of range `google.protobuf.Timestamp` and `google.protobuf.Duration` values are written as objects, rather than failing
* NaN and infinity `google.protobuf.Value` numbers are written as strings, rather than failing
* escaping is left to the generator, so e.g. `=` and `<` are not written as unicode escapes
* an unregistered `google.protobuf.Any` fails the write, as JsonFormat does, unless
  `withUnresolvedAny(UnresolvedAny.VALUE)` is set. That writes `{"@type": url, "@value": base64}` and does not parse
  the payload, see [Unresolved Any](#unresolved-any). A type URL without a `/` still fails. An empty Any is still `{}`
* where `JsonFormat` throws `InvalidProtocolBufferException`, the writers throw `UncheckedIOException` with it as cause

No JSON key is written twice:

* fields are expected in increasing field number order, as protobuf serializes them; a field number seen again after
  a higher one (e.g. in the unknown fields) is skipped.
* map entries with a key already written are skipped, keeping the first entry (protobuf keeps the last when
  parsing). Generated messages never have duplicate keys, but e.g. a `DynamicMessage` can; `JsonFormat` then writes
  the key twice.

### Checking the output
Use `JsonFormatComparison` (`no.entur.protobuf.json.jsonformat`, in the `utility` module) to check that a writer writes the same as `JsonFormat` for your
messages, e.g. in a unit test:

```java
JsonFormatComparison comparison = JsonFormatComparison.newBuilder()
        .withTypeRegistry(typeRegistry)
        .withWriter(writer)
        .build();

comparison.verify(myMessage); // throws IllegalStateException with the differences
```

Outputs are compared as JSON values, so escaping and number notation do not matter (`JsonFormat` escapes e.g. `=`
as `\u003d`). Duplicate properties are reported.


</details>

## Protobuf lite message writer

```java
ProtobufJsonWriter<MessageLiteOrBuilder, com.fasterxml.jackson.core.JsonGenerator> writer = LiteProtobufJsonWriter.newBuilder()
        .withAnyType("type.googleapis.com/my.package.MyMessage", MyMessage.getDefaultInstance()) // for google.protobuf.Any
        .withAnonymizeKeys("nonce")
        .withMaxStringLength(256)
        .build();
```

Lite messages have no descriptors, so the lite writer uses reflection.

<details>
<summary>Differences from JsonFormat</summary>

Lite messages have no descriptors, so the lite writer finds the fields via the generated code, once per message class:
the public `*_FIELD_NUMBER` constants give the field numbers and names, and the public accessors (`getX()`, `hasX()`,
`getXList()`, `getXMap()`, `getXValue()` ...) the values. The output follows JsonFormat as closely as this allows (field
order, presence, quoted 64-bit integers, enum names, base64 bytes, well-known types, `google.protobuf.Any` via
`withAnyType(..)`), so that logs look the same and the same filter paths work for full and lite messages. Differences
from JsonFormat:

 * unsigned values (`uint32`, `fixed32`, `uint64` and `fixed64` fields and map keys) of 2^31 / 2^63 and higher are
   written as negative numbers, as the generated code does not tell them apart from signed values
   (`UInt32Value` and `UInt64Value` are correct)
 * custom `json_name` options and custom enum value JSON names are not available. Field names are derived like protoc
   does (`foo_bar` → `fooBar`), with upper case letters restored from the accessor names, but names starting with an
   upper case letter (`FieldName`) get a lower case first letter, and proto2 groups are named after their type
   (`multiWordGroupField` rather than `multiwordgroupfield`)
 * extensions are not written
 * as for the full protobuf writers: out of range `Timestamp`/`Duration` values are written as objects, NaN `Value`
   numbers as strings, and escaping is left to the generator. An unregistered `google.protobuf.Any` fails the write
   unless `withUnresolvedAny(UnresolvedAny.VALUE)` is set, which writes `{"@type": url, "@value": base64}`

`writeAsString` writes characters, so max size then counts characters. Jackson 2 generators which write UTF-8 escape
surrogate pairs (e.g. emojis) as `\uXXXX\uXXXX` unless `COMBINE_UNICODE_SURROGATES_IN_UTF8` is enabled; max size takes this
into account.

</details>

<details>
<summary>Obfuscation configuration</summary>
When shrinking on Android, keep the accessors and the field number constants (and preferably the generic signatures,
otherwise the writer checks the runtime type of each value):

```
-keepattributes Signature
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    public static final int *_FIELD_NUMBER;
    public *** get*();
    public *** has*();
}
```
</details>

## Filtering

Filtering options:

* **anonymize** and/or **prune** by field name (JSON path subset)
* **max string length**: truncate long string values
* **max size**: maximum document size (still valid JSON)

Build a writer with the filters you want:

| Option | Effect |
|---|---|
| `withAnonymizePaths(...)`, `withAnonymizeKeys(...)` | keep the structure, but replace every scalar value (strings, numbers, booleans and null) with `"*"` |
| `withPrunePaths(...)`, `withPruneKeys(...)` | replace the whole value with `"[removed]"`; the value is not read |
| `withMaxStringLength(n)` | truncate text and bytes values longer than `n` characters to `"<first n characters>... + <number of removed characters>"` |
| `withMaxSize(n)` | stop writing at the first value which does not fit within `n` bytes (characters, for character-based generators), then close the open objects and arrays |
| `withAnonymizeMessage(...)`, `withPruneMessage(...)`, `withTruncateMessage(...)` | replacement texts |

### Paths:

 * absolute paths, `$.a.b.c`, refer to JSON field names
 * wildcard (`*`) matches any field name
 * any-paths match at any depth
 * arrays are transparent: 
   * `$.items.price` matches `price` in every element of `items`
 * prune takes precedence over anonymize, and no paths are matched within an anonymized value
 * field names containing a dot cannot be matched

### Max string length
Max string length applies to text and bytes: string fields, base64-encoded bytes, and other text like `google.protobuf.Struct`
strings, field masks and the `@type` of `google.protobuf.Any`. 

It does not apply to field names and map keys, or to strings
which are not text: 64-bit integers, enum names, timestamps, durations, and NaN and infinity. 

Lengths are counted in UTF-16
characters, a surrogate pair is never split, and a string is only truncated if that makes it shorter.

### Max (output) size
Max size counts the JSON as written by a Jackson generator with default settings and no pretty printer: bytes for
generators which write UTF-8 bytes, characters otherwise. `writeAsString(..)` writes UTF-8 bytes with the full protobuf
writers, and characters with the lite writer.

A field name is
only written together with its value, and room for the closing brackets is reserved, so the output is always valid
JSON. `write(..)` returns `false` if writing stopped because of the max size, i.e. the output is truncated:

```java
boolean complete = writer.write(message, generator);
```

Only the max size makes `write(..)` return `false`; shortened strings (max string length), and pruned or anonymized values, do not.

A root value which is not an object or array (e.g. a `google.protobuf.Timestamp`) is always written, as is the root
object or array.

### Unresolved Any
With `withUnresolvedAny(UnresolvedAny.VALUE)`, a `google.protobuf.Any` whose type is not registered is written as
`{"@type": url, "@value": base64}`, without parsing the payload. No protobuf JSON field is named `@value`, so a parser
rejects it rather than reading the payload as a field. Well-known types are still written as such, also when they are
not in the type registry. With the default, `UnresolvedAny.FAIL`, the full protobuf writers fail for a well-known type
which is not in the type registry, as `JsonFormat` does; the lite writer always knows the well-known types.

Paths match `@type` and `@value` as usual, and max string length applies to both. Paths cannot match within the payload,
so it is pruned (if such a path prunes) or anonymized if a path could have matched within it:

 * any-paths, including anonymize and prune keys
 * absolute paths which go on into the Any, other than through `@type` or `@value`: for an Any at `$.event.payload`,
   `$.event.payload.password` and `$.event.*.password`, but not `$.event.payload.@type`

A path below a field which a shorter absolute path filters as a whole does not count.

## Performance
By message size as JSON, without filtering; the best value of each column in **bold**. See [PERFORMANCE.md](PERFORMANCE.md)
for more, including filtering, and [benchmarks](benchmarks/README.md) for the messages.

### CPU
Time per message in microseconds:

| Writer | small (0.2 KB) | medium (14 KB) | large (38 KB) | xlarge (127 KB) | xxlarge (254 KB) |
|---|---|---|---|---|---|
| `JsonFormat` | 4.48 | 42.9 | 127 | 368 | 757 |
| HubSpot jackson-datatype-protobuf | 4.20 | 39.9 | 109 | 252 | 528 |
| curioswitch protobuf-jackson | 0.911 | 14.8 | 50.0 | 141 | 283 |
| transcode | 0.860 | 17.3 | 43.9 | 159 | 319 |
| stream | 0.799 | **9.46** | 29.4 | 82.8 | 172 |
| accessor | **0.456** | 9.51 | **24.2** | **71.7** | **142** |
| `MessageLiteToJson` (lite messages) | 49.6 | 83.7 | 582 | 758 | 1480 |
| lite | 2.00 | 23.2 | 54.0 | 233 | 468 |

Serialized to binary (`toByteArray()`) and then written as JSON, like an application which sends a message and logs it, the
total time per message in microseconds:

| Writer | small (0.2 KB) | medium (14 KB) | large (38 KB) | xlarge (127 KB) | xxlarge (254 KB) |
|---|---|---|---|---|---|
| `JsonFormat` | 5.19 | 50.3 | 145 | 464 | 929 |
| HubSpot jackson-datatype-protobuf | 4.78 | 48.5 | 130 | 348 | 718 |
| curioswitch protobuf-jackson | 1.34 | 24.1 | 66.4 | 235 | 471 |
| transcode | 1.28 | 25.5 | 65.2 | 260 | 520 |
| stream | 1.23 | 20.1 | 47.9 | **165** | 365 |
| accessor | **0.991** | **18.2** | **41.6** | 166 | **330** |
| binary only (`toByteArray()`) | 0.389 | 8.43 | 14.0 | 94.7 | 189 |

### Memory
Kilobytes allocated per message:

| Writer | small (0.2 KB) | medium (14 KB) | large (38 KB) | xlarge (127 KB) | xxlarge (254 KB) |
|---|---|---|---|---|---|
| `JsonFormat` | 7.38 | 100 | 353 | 901 | 1809 |
| HubSpot jackson-datatype-protobuf | 4.22 | 8.69 | 19.6 | 70.9 | 143 |
| curioswitch protobuf-jackson | 2.95 | 7.93 | 0.899 | 61.5 | 122 |
| transcode | 2.15 | 26.0 | 48.8 | 234 | 471 |
| stream | 3.29 | 1.70 | 0.649 | 8.88 | 17.0 |
| accessor | 1.80 | **1.39** | **0.406** | **8.58** | **16.7** |
| `MessageLiteToJson` (lite messages) | 225 | 303 | 3076 | 2579 | 5154 |
| lite | **1.32** | 2.05 | 0.524 | 14.7 | 29.8 |

The accessor writer also uses about 1 MB of metaspace for generating code, and about 2 KB per field of the message types
it writes, see [memory per message type](PERFORMANCE.md#memory-per-message-type).

## Maven coordinates
Group id is `no.entur.protobuf-to-json`. 

| Module | Artifact | Description | Jackson | Java |
|---|---|---|---|---|
| `path` | `protobuf-to-json-path` | path matching for anonymize and prune | - | 11 (Android) |
| `api` | `protobuf-to-json-api` | `ProtobufJsonWriter`, the builder options, and the filter logic shared by all writers | - | 11 (Android) |
| `transcode` | `protobuf-to-json-transcode` | full protobuf: transcodes the binary form to JSON | 3 | 17 |
| `stream` | `protobuf-to-json-stream` | full protobuf: the generated serialization code writes JSON directly | 3 | 24 |
| `accessor` | `protobuf-to-json-accessor` | full protobuf: code generated per message type reads the fields with their accessors | 3 | 24 |
| `lite` | `protobuf-to-json-lite` | protobuf lite messages, via reflection over the generated accessors | 2 | 11 (Android) |
| `utility` | `protobuf-to-json-utility` | `JsonFormatComparison` for checking the output against JsonFormat, and a writer which uses JsonFormat | 3 | 17 |
| `test-support` | not published | the test messages, random messages and the benchmark payloads, also as files for the lite modules | - | 17 |
| `benchmarks` | not published | JMH benchmarks, see [benchmarks](benchmarks/README.md) | 2 and 3 | 25 |
| `module-tests` | not published | named application modules which write messages with the writers on the module path | 2 and 3 | 25 |

The published modules need protobuf 4.27 or later: protobuf-java and protobuf-java-util (of the same version) for the
full protobuf modules, protobuf-javalite for `lite`. Protobuf 3 is not supported. Protobuf 4.26 works too, except for
messages from edition files, which it supports only experimentally. With protobuf versions before 4.32 the `stream`
writer delegates to the transcoding writer. Custom JSON names of enum values (an option for edition 2026 files) need
protobuf-java-util 4.36 or later; with older versions, enum values are written with their names.

## Supported API

Use the writer classes and their builders (`AbstractProtobufJsonWriterBuilder`). Some other types are public only because
the writer modules and the generated writers use them, so they are not application API and may change between releases:
the `plan` package, the `no.entur.protobuf.json.filter` package (except `JsonFilter`, which the builders return and the
filtering writers take), `MessageWriters`, `MessageWriter` and `FilteringMessageWriter` in the accessor module, and
`CodedOutputStreamSink` in the stream module. The same goes for the public constructors of the transcoding writers
(which take a `Plans`), and their methods other than those of `ProtobufJsonWriter`: `writeMessage(..)`,
`writeDefault(..)`, `writeWellKnown(..)`, and the `write(..)` methods which take a `Message` or `FilteredJsonValues`. The
path matcher implementation is not public.

### Module path

Require the writer module: `no.entur.protobuf.json.accessor`, `no.entur.protobuf.json.stream`,
`no.entur.protobuf.json.transcode`, `no.entur.protobuf.json.lite` or `no.entur.protobuf.json.utility`. Then:

* export the packages of the messages: protobuf's own reflection (as in `JsonFormat`), the accessor writer and the lite
  writer read the messages through their public methods and fields. Without the export, the accessor writer transcodes
  instead, which is slower, and the lite writer fails. The accessor writer logs such fallbacks at `FINE` with
  `java.util.logging`, once per message class (logger `no.entur.protobuf.json.accessor.MessageWriters`).
* if the message classes are loaded by another class loader than this library, the accessor writer defines its writers
  in the packages of the messages: open those packages to `no.entur.protobuf.json.accessor`.

The stream and accessor writers generate classes at runtime, which needs no further options on the module path. The
`path`, `api` and `lite` jars have their module descriptor in `META-INF/versions/9` rather than at the root, which older
Android tools fail on. The `module-tests` modules run named application modules with the writers on the module path.

## Building
The build needs Java 25, as the `stream` and `accessor` modules target Java 24 and the benchmarks Java 25. To compile,
test and package all modules, including the module path tests and the benchmark jars (about two minutes):

```bash
./mvnw verify
```

See [AGENTS.md](AGENTS.md) for the repository layout, more build commands and the code conventions, and
[benchmarks](benchmarks/README.md) for running the benchmarks.

## License
[Apache-2.0](LICENSE.txt).

## Third-party code:

License texts are in [NOTICE](NOTICE), also packaged as `META-INF/NOTICE`.

 * the path matching (`path` module), and the size and truncation logic (`api` module), are ported from
   [json-log-filter](https://github.com/skjolber/json-log-filter) (Apache-2.0)
 * the protobuf conformance test messages (`support/protobuf/conformance`) are from
   [protobuf](https://github.com/protocolbuffers/protobuf) (BSD-3-Clause)
 * the lite writer is derived from protobuf's `MessageLiteToString` (BSD-3-Clause), as is the lite benchmark baseline
 * inspiration + the test messages in `support/protobuf/protobuf_jackson`, and the tests ported from them, are from
   [curioswitch protobuf-jackson](https://github.com/curioswitch/protobuf-jackson) (MIT)

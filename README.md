# protobuf-to-json

High-performance protobuf-to-JSON conversion. With or without filtering. Regular or lite messages. Multiple writers.

## Usage
Writers are thread-safe; create them once and reuse them:

```java
ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = AccessorProtobufJsonWriter.newBuilder()
        .withTypeRegistry(typeRegistry) // for google.protobuf.Any
        .withAnonymizeKeys("password", "token")
        .withPrunePaths("$.customer.photo")
        .withMaxStringLength(1024)
        .withMaxSize(64 * 1024)
        .build();

String json = writer.writeAsString(message);
```

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
its methods the generated code calls. So a protobuf upgrade can break it (it then fails when created). The `accessor` writer
only depends on the names of the accessors, which are part of protobuf's generated API. The transcoding writer only uses
public protobuf APIs and runs on Java 17, but is the slowest of the three.

<details>
<summary>Differences from JsonFormat</summary>

### Comparison from JsonFormat

* extensions are not written
* out of range `google.protobuf.Timestamp` and `google.protobuf.Duration` values are written as objects, rather than failing
* NaN and infinity `google.protobuf.Value` numbers are written as strings, rather than failing
* escaping is left to the generator, so i.e. `=` and `<` are not written as unicode escapes

No JSON key is written twice:

* fields are expected in increasing field number order, as protobuf serializes them; a field number seen again after
  a higher one (i.e. in the unknown fields) is skipped.
* map entries with a key already written are skipped, keeping the first entry (protobuf keeps the last when
  parsing). Generated messages never have duplicate keys, but i.e. a `DynamicMessage` can; `JsonFormat` then writes
  the key twice.

### Checking the output
Use `JsonFormatComparison` (in the `utility` module) to check that a writer writes the same as `JsonFormat` for your
messages, i.e. in a unit test:

```java
JsonFormatComparison comparison = JsonFormatComparison.newBuilder()
        .withTypeRegistry(typeRegistry)
        .withWriter(writer)
        .build();

comparison.verify(myMessage); // throws IllegalStateException with the differences
```

Outputs are compared as JSON values, so escaping and number notation do not matter (`JsonFormat` escapes i.e. `=`
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
   numbers as strings, and escaping is left to the generator

`writeAsString` writes characters, so max size then counts characters. Jackson 2 generators which write UTF-8 escape
surrogate pairs (i.e. emojis) as `\uXXXX\uXXXX` unless `COMBINE_UNICODE_SURROGATES_IN_UTF8` is enabled; max size takes this
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
Max size counts the JSON as written by a Jackson generator with default settings and no pretty printer. 

A field name is
only written together with its value, and room for the closing brackets is reserved, so the output is always valid
JSON. 

A root value which is not an object or array (i.e. a `google.protobuf.Timestamp`) is always written, as is the root
object or array.

## Performance
By message size as JSON, without filtering; the best value of each column in **bold**. See [PERFORMANCE.md](PERFORMANCE.md)
for more, i.e. filtering, and [benchmarks](benchmarks/README.md) for the messages.

### CPU
Time per message in microseconds:

| Writer | small (0.2 KB) | medium (14 KB) | large (38 KB) | xlarge (127 KB) | xxlarge (254 KB) |
|---|---|---|---|---|---|
| `JsonFormat` | 4.48 | 42.3 | 127 | 369 | 755 |
| HubSpot jackson-datatype-protobuf | 4.20 | 39.9 | 108 | 254 | 537 |
| curioswitch protobuf-jackson | 0.910 | 14.9 | 50.3 | 140 | 285 |
| transcode | 0.853 | 17.8 | 45.8 | 162 | 320 |
| stream | 0.673 | **9.58** | 29.6 | 85.5 | 172 |
| accessor | **0.449** | 9.68 | **24.3** | **72.2** | **143** |
| `MessageLiteToJson` (lite messages) | 48.7 | 85.5 | 584 | 761 | 1494 |
| lite | 2.01 | 22.3 | 54.5 | 234 | 478 |

Serialized to binary (`toByteArray()`) and then written as JSON, like an application which sends a message and logs it, the
total time per message in microseconds:

| Writer | small (0.2 KB) | medium (14 KB) | large (38 KB) | xlarge (127 KB) | xxlarge (254 KB) |
|---|---|---|---|---|---|
| `JsonFormat` | 5.06 | 50.2 | 146 | 486 | 929 |
| HubSpot jackson-datatype-protobuf | 4.82 | 49.0 | 125 | 350 | 721 |
| curioswitch protobuf-jackson | 1.35 | 24.4 | 65.2 | 237 | 474 |
| transcode | 1.29 | 26.2 | 66.5 | 264 | 521 |
| stream | 1.22 | 18.9 | 48.6 | 169 | 363 |
| accessor | **1.01** | **18.5** | **41.8** | **168** | **333** |
| binary only (`toByteArray()`) | 0.379 | 8.49 | 14.1 | 95.1 | 191 |

### Memory
Kilobytes allocated per message:

| Writer | small (0.2 KB) | medium (14 KB) | large (38 KB) | xlarge (127 KB) | xxlarge (254 KB) |
|---|---|---|---|---|---|
| `JsonFormat` | 7.38 | 100 | 353 | 901 | 1809 |
| HubSpot jackson-datatype-protobuf | 4.22 | 8.69 | 19.6 | 70.9 | 143 |
| curioswitch protobuf-jackson | 2.95 | 7.93 | 0.899 | 61.5 | 122 |
| transcode | 2.14 | 26.0 | 48.8 | 234 | 471 |
| stream | 2.07 | 1.70 | 0.649 | 8.88 | 17.0 |
| accessor | 1.80 | **1.39** | **0.406** | **8.58** | **16.7** |
| `MessageLiteToJson` (lite messages) | 225 | 302 | 3076 | 2579 | 5151 |
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


## License
[Apache-2.0](LICENSE.txt).

## Third-party code:

 * the path matching (`path` module), and the size and truncation logic (`api` module), are ported from
   [json-log-filter](https://github.com/skjolber/json-log-filter) (Apache-2.0)
 * the protobuf conformance test messages (`support/protobuf/conformance`) are from
   [protobuf](https://github.com/protocolbuffers/protobuf) (BSD-3-Clause)
 * the lite writer is derived from protobuf's `MessageLiteToString` (BSD-3-Clause), as is the lite benchmark baseline
 * inspiration + the test messages in `support/protobuf/protobuf_jackson`, and the tests ported from them, are from
   [curioswitch protobuf-jackson](https://github.com/curioswitch/protobuf-jackson) (MIT)

# Benchmarks

[JMH](https://github.com/openjdk/jmh) benchmarks, compiled for Java 25. For the results, see [PERFORMANCE.md](../PERFORMANCE.md).

| Module | Benchmarks |
|---|---|
| `benchmark-full` | the full protobuf writers, `JsonFormat` and other libraries |
| `benchmark-lite` | the lite writer and a reflection-based baseline |

## Running
Each module builds a self-contained `target/benchmarks.jar`. From the root of the project:

```
./mvnw verify -DskipTests
java -jar benchmarks/benchmark-full/target/benchmarks.jar -prof gc
java -jar benchmarks/benchmark-lite/target/benchmarks.jar -prof gc
```

Add a regular expression to run some of the benchmarks only, and JMH options such as `-f 1 -wi 2 -i 3` for shorter
runs, or `-rf csv -rff results.csv` to save the results. The results in [PERFORMANCE.md](../PERFORMANCE.md) were measured with

```
java -jar benchmarks/benchmark-full/target/benchmarks.jar '^no.entur.protobuf.json.benchmark.((Filtered)?ProtobufJsonWriter|ProtobufJsonString|BinaryAndJson)Benchmark' -f 2 -wi 3 -i 5 -prof gc
java -jar benchmarks/benchmark-lite/target/benchmarks.jar -f 2 -wi 3 -i 5 -prof gc
java -jar benchmarks/benchmark-full/target/benchmarks.jar '^no.entur.protobuf.json.benchmark.StreamWriterJitBenchmark' -f 2 -wi 3 -i 5
java -jar benchmarks/benchmark-full/target/benchmarks.jar '^no.entur.protobuf.json.benchmark.BinarySerializationBenchmark' -f 5 -wi 3 -i 5 -rf json
java -cp benchmarks/benchmark-full/target/benchmarks.jar no.entur.protobuf.json.benchmark.MemoryFootprint
```

## Payloads
The same messages for all benchmarks, built by `BenchmarkPayloads` in the `test-support` module. The lite modules get
them as files, written by `PayloadFiles` during their build.

| Payload | Message | Binary | JSON |
|---|---|---|---|
| small | a typical small request: a `TestAllTypesProto3` with a string, numbers, an enum, a timestamp and a nested message | 39 bytes | 185 bytes |
| medium | a typical API response: a GitHub search response (`GithubApi.proto` from curioswitch protobuf-jackson) with three repositories | 10.6 KB | 13.8 KB |
| large | a `google.protobuf.Type` describing the messages of four well-known type files: many strings, enums and nested messages | 19.6 KB | 37.8 KB |
| xlarge | a GitHub search response, like medium, with 26 repositories | 98.4 KB | 126.5 KB |
| xxlarge | a GitHub search response, like medium, with 52 repositories | 197.9 KB | 254.2 KB |

## Full protobuf messages (`benchmark-full`)
Each benchmark writes a message as UTF-8 to an output stream, the JSON writers through a JSON generator, like a JSON log
encoder does. The setup serializes the message to binary, like an application which sends it does, as this affects how the
JIT compiles the generated serialization code, which the stream writer uses.

`ProtobufJsonWriterBenchmark`, without filtering:

| Benchmark | Writes with |
|---|---|
| `jsonFormat` | `JsonFormat` (`JsonFormatProtobufJsonWriter`) |
| `hubspot` | [HubSpot jackson-datatype-protobuf](https://github.com/HubSpot/jackson-datatype-protobuf) 1.0.0 (the Jackson 3 version), with its canonical serialization |
| `curioswitch` | [curioswitch protobuf-jackson](https://github.com/curioswitch/protobuf-jackson) 2.8.1, which uses Jackson 2, with its own generator writing to the output stream |
| `curioswitchRawValue` | the same, but within a Jackson 3 generator, like a JSON log encoder: as a raw value, which must be a `String` |
| `transcode` | `TranscodingProtobufJsonWriter` |
| `stream` | `CodedOutputStreamProtobufJsonWriter` |
| `accessor` | `AccessorProtobufJsonWriter` |
| `binary` | `toByteArray()`, for reference |

The other libraries write the payloads like `JsonFormat` does, but not all messages: see `HubSpotJacksonDatatypeProtobufTest`
and `CurioswitchProtobufJacksonTest`.

`BinaryAndJsonBenchmark`: the same libraries and writers, but serializing a message to binary with `toByteArray()` and
then writing it as JSON, in sequence, like an application which sends a message and logs it: the total time per message.

`ProtobufJsonStringBenchmark`: the same libraries and writers, but writing a message as a `String`: `writeAsString()` of
the writers of this project (which write UTF-8, then decode it), JsonFormat's printer, and `writeValueAsString()` of the
other libraries (which write characters).

`FilteredProtobufJsonWriterBenchmark`: the `transcode`, `stream` and `accessor` writers with the filters below.

The JIT compiles the generated serialization code, which the stream writer uses, for the `CodedOutputStream` types it has
seen in use:

 * `StreamWriterJitBenchmark` measures the stream writer (and the transcoding and accessor writers and curioswitch
   protobuf-jackson, for comparison) depending on whether the message types are also serialized to binary in the same JVM:
   never, once, with `toByteArray()`, or with `toByteArray()` and `writeTo(OutputStream)`
 * `BinarySerializationBenchmark` measures binary serialization (`toByteArray()` or `writeTo(OutputStream)`) when the
   messages are also written as JSON, with the stream, transcoding or accessor writer, or curioswitch protobuf-jackson, in
   the same JVM; or, for reference, when the JVM runs unrelated code (regular expressions, formatting and a `TreeMap`).
   Binary serialization of the large payload is compiled in one of two ways (about 14 or 17 microseconds) depending on
   what else the JVM does, so the results are the medians of five JVMs
 * `LiteWriterJitBenchmark`, in `benchmark-lite`, measures the lite writer depending on whether it has also written to a
   character-based generator (i.e. with `writeAsString`)

`MemoryFootprint` (not JMH) measures the memory a writer uses besides what it allocates per message: the classes it loads
or generates (metaspace), its JIT-compiled code (code cache) and the heap it keeps, after writing a set of messages for five
seconds, relative to a JVM which only serializes them to binary. Each writer runs in its own JVM, with the serial garbage
collector. The message sets are the payloads; `descriptors`: the descriptors of protobuf's descriptor, type, API and
struct files and of the test messages, as `FileDescriptorProto` messages, which have many message types; and `all`: those,
the protobuf-jackson test messages and random protobuf conformance test messages.

## Lite messages (`benchmark-lite`)

`LiteProtobufJsonWriterBenchmark`, without filtering:

| Benchmark | Writes with |
|---|---|
| `messageLiteToJson` | `MessageLiteToJson`, the baseline: a reflection-based writer, which looks up the accessors of every message while writing it, like protobuf's `MessageLiteToString` |
| `messageLiteToString` | `MessageLite.toString()`, protobuf's reflection-based text format (not JSON), the usual alternative for logging lite messages |
| `lite` | `LiteProtobufJsonWriter`, which finds the fields once per message class |
| `binary` | `toByteArray()`, for reference |

The output of `MessageLiteToJson` differs from JsonFormat: fields are in alphabetical order, 64-bit integers are numbers,
and empty lists and maps are written.

`FilteredLiteProtobufJsonWriterBenchmark`: the lite writer with the filters below.

## Filters
Like in the benchmarks of [json-log-filter](https://github.com/skjolber/json-log-filter), with paths of fields in each
payload (`BenchmarkFilters`; `BenchmarkFiltersTest` checks that every path matches):

| Filter | Options |
|---|---|
| `none` | no filtering, the plain writers |
| `maxStringLength` | text and bytes longer than 32 characters are truncated |
| `maxSize` | half the size of the unfiltered output |
| `anyPaths3` | anonymize three field names, at any depth |
| `mixedPaths5` | anonymize an absolute path and an any-path, and prune an absolute path, a path with a wildcard and an any-path |
| `all` | `mixedPaths5`, `maxStringLength` and `maxSize` |

| Payload | `anyPaths3` | `mixedPaths5`: anonymize | `mixedPaths5`: prune |
|---|---|---|---|
| small | `$..optionalString`, `$..optionalInt64`, `$..a` | `$.optionalString`, `$..optionalInt32` | `$.optionalTimestamp`, `$.*.a`, `$..optionalInt64` |
| medium, xlarge, xxlarge | `$..login`, `$..fullName`, `$..description` | `$.items.owner.login`, `$..description` | `$.items.cloneUrl`, `$.items.*.avatarUrl`, `$..htmlUrl` |
| large | `$..name`, `$..jsonName`, `$..typeUrl` | `$.fields.name`, `$..jsonName` | `$.oneofs`, `$.*.fileName`, `$..typeUrl` |

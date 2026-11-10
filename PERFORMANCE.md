# Performance

How fast the writers are (CPU) and how much memory they use, writing a message as UTF-8 JSON. 

JMH on OpenJDK 25 on Fedora Linux + AMD Ryzen 9 9950X with CPU boost off, two JVMs for each benchmark.

The small, medium, large, xlarge and xxlarge messages are 0.2 KB, 14 KB, 38 KB, 127 KB and 254 KB as JSON. 

The best value of each column is in **bold**; binary serialization is only for reference.

## CPU

Time per message in microseconds.

### Full protobuf messages

| Writer | small | medium | large | xlarge | xxlarge |
|---|---|---|---|---|---|
| `JsonFormat` | 4.48 | 42.9 | 127 | 368 | 757 |
| HubSpot jackson-datatype-protobuf | 4.20 | 39.9 | 109 | 252 | 528 |
| curioswitch protobuf-jackson | 0.911 | 14.8 | 50.0 | 141 | 283 |
| transcode | 0.860 | 17.3 | 43.9 | 159 | 319 |
| stream | 0.799 | **9.46** | 29.4 | 82.8 | 172 |
| accessor | **0.456** | 9.51 | **24.2** | **71.7** | **142** |
| binary (`toByteArray()`) | 0.403 | 8.65 | 13.9 | 88.7 | 177 |

curioswitch protobuf-jackson uses Jackson 2, the others Jackson 3. The payloads are also serialized to binary before
measuring, as in an application which sends them, see [the stream writer and the JIT](#the-stream-writer-and-the-jit).

### Binary and JSON

Serializing a message to binary with `toByteArray()` and then writing it as JSON, in sequence, like an application which
sends a message and logs it: the total time per message (`BinaryAndJsonBenchmark`).

| Writer | small | medium | large | xlarge | xxlarge |
|---|---|---|---|---|---|
| `JsonFormat` | 5.19 | 50.3 | 145 | 464 | 929 |
| HubSpot jackson-datatype-protobuf | 4.78 | 48.5 | 130 | 348 | 718 |
| curioswitch protobuf-jackson | 1.34 | 24.1 | 66.4 | 235 | 471 |
| transcode | 1.28 | 25.5 | 65.2 | 260 | 520 |
| stream | 1.23 | 20.1 | 47.9 | **165** | 365 |
| accessor | **0.991** | **18.2** | **41.6** | 166 | **330** |
| binary only (`toByteArray()`) | 0.389 | 8.43 | 14.0 | 94.7 | 189 |

### Writing to a string

`writeAsString()` of the writers of this project, which write UTF-8 and decode it, `JsonFormat`'s printer, and
`writeValueAsString()` of the other libraries, which write characters:

| Writer | small | medium | large | xlarge | xxlarge |
|---|---|---|---|---|---|
| `JsonFormat` | 4.55 | 35.1 | 109 | 303 | 614 |
| HubSpot jackson-datatype-protobuf | 4.17 | 22.9 | 93.1 | 227 | 450 |
| curioswitch protobuf-jackson | 0.862 | 12.1 | 35.2 | 126 | 255 |
| transcode | 0.902 | 19.2 | 51.3 | 175 | 344 |
| stream | 0.856 | **11.1** | 33.3 | 97.6 | 197 |
| accessor | **0.523** | 11.3 | **28.2** | **86.6** | **170** |

### Lite messages

| Writer | small | medium | large | xlarge | xxlarge |
|---|---|---|---|---|---|
| `MessageLiteToJson` | 49.6 | 83.7 | 582 | 758 | 1480 |
| `toString()` | 39.5 | 84.7 | 539 | 742 | 1494 |
| lite | **2.00** | **23.2** | **54.0** | **233** | **468** |
| binary (`toByteArray()`) | 0.719 | 11.3 | 24.8 | 98.9 | 188 |

`MessageLiteToJson` is a reflection-based writer, which looks up the accessors of every message while writing it.
`toString()` is protobuf's text format, not JSON.

### Filtering

With the filters of the [benchmarks](benchmarks/README.md#filters) (`FilteredProtobufJsonWriterBenchmark` and
`FilteredLiteProtobufJsonWriterBenchmark`): `none` is the writer without filtering, `maxSize` is half the size of the
unfiltered output, and `all` is `mixedPaths5`, `maxStringLength` and `maxSize`.

Transcoding writer:

| Filter | small | medium | large | xlarge | xxlarge |
|---|---|---|---|---|---|
| `none` | 0.904 | 19.1 | 47.8 | 168 | 331 |
| `maxStringLength` | 1.00 | 26.7 | 59.1 | 239 | 476 |
| `maxSize` | 0.852 | 16.6 | 41.7 | 151 | 302 |
| `anyPaths3` | 0.975 | 19.2 | 45.3 | 171 | 344 |
| `mixedPaths5` | 0.942 | 20.9 | 51.7 | 187 | 372 |
| `all` | 0.851 | 25.0 | 56.4 | 227 | 451 |

Stream writer:

| Filter | small | medium | large | xlarge | xxlarge |
|---|---|---|---|---|---|
| `none` | 0.811 | 9.61 | 29.5 | 78.6 | 161 |
| `maxStringLength` | 0.847 | 9.58 | 33.6 | 77.6 | 157 |
| `maxSize` | 0.841 | 17.2 | 34.8 | 129 | 204 |
| `anyPaths3` | 0.852 | 10.0 | 27.3 | 88.7 | 176 |
| `mixedPaths5` | 0.889 | 11.5 | 34.9 | 106 | 208 |
| `all` | 0.802 | 14.9 | 36.2 | 106 | 214 |

Accessor writer:

| Filter | small | medium | large | xlarge | xxlarge |
|---|---|---|---|---|---|
| `none` | 0.473 | 9.35 | 24.1 | 70.7 | 148 |
| `maxStringLength` | 0.523 | 8.31 | 27.1 | 76.2 | 147 |
| `maxSize` | 0.208 | 8.68 | 22.7 | 72.7 | 153 |
| `anyPaths3` | 0.515 | 10.0 | 19.7 | 83.9 | 168 |
| `mixedPaths5` | 0.539 | 11.5 | 26.2 | 99.0 | 207 |
| `all` | 0.205 | 11.6 | 27.5 | 120 | 186 |

Lite writer, lite messages:

| Filter | small | medium | large | xlarge | xxlarge |
|---|---|---|---|---|---|
| `none` | 2.00 | 23.8 | 55.4 | 236 | 480 |
| `maxStringLength` | 2.17 | 13.1 | 44.4 | 121 | 235 |
| `maxSize` | 0.281 | 21.3 | 40.4 | 191 | 381 |
| `anyPaths3` | 2.08 | 18.1 | 35.9 | 265 | 531 |
| `mixedPaths5` | 2.08 | 18.4 | 42.4 | 270 | 542 |
| `all` | 0.280 | 14.7 | 42.2 | 136 | 263 |

With max size, the writers stop when the output is full, but until then they count the size of every value they write: the
transcoding writer from the UTF-8 bytes, and the stream and accessor writers from the `String`s, character by character,
which takes longer. With `maxSize`, the accessor writer then takes about as long for half the output as it takes without
filtering for all of it, and the stream writer takes longer, except for the small payload.

## Memory

### Allocation per message

Kilobytes allocated per message, measured with JMH's `-prof gc`.

Full protobuf messages:

| Writer | small | medium | large | xlarge | xxlarge |
|---|---|---|---|---|---|
| `JsonFormat` | 7.38 | 100 | 353 | 901 | 1809 |
| HubSpot jackson-datatype-protobuf | 4.22 | 8.69 | 19.6 | 70.9 | 143 |
| curioswitch protobuf-jackson | 2.95 | 7.93 | 0.899 | 61.5 | 122 |
| transcode | 2.15 | 26.0 | 48.8 | 234 | 471 |
| stream | 3.29 | 1.70 | 0.649 | 8.88 | 17.0 |
| accessor | **1.80** | **1.39** | **0.406** | **8.58** | **16.7** |
| binary (`toByteArray()`) | 1.53 | 24.0 | 48.3 | 221 | 444 |

Writing to a string:

| Writer | small | medium | large | xlarge | xxlarge |
|---|---|---|---|---|---|
| `JsonFormat` | 7.11 | 100 | 353 | 901 | 1809 |
| HubSpot jackson-datatype-protobuf | 4.45 | 22.5 | 57.4 | 452 | 1035 |
| curioswitch protobuf-jackson | 3.25 | **21.9** | **38.8** | 443 | 1015 |
| transcode | 2.68 | 61.1 | 126 | 558 | 1121 |
| stream | 3.84 | 36.8 | 77.8 | **332** | **667** |
| accessor | **2.35** | 36.5 | 77.6 | **332** | **667** |

Lite messages:

| Writer | small | medium | large | xlarge | xxlarge |
|---|---|---|---|---|---|
| `MessageLiteToJson` | 225 | 303 | 3076 | 2579 | 5154 |
| `toString()` | 186 | 384 | 3230 | 3275 | 6546 |
| lite | **1.32** | **2.05** | **0.524** | **14.7** | **29.8** |
| binary (`toByteArray()`) | 0.141 | 24.0 | 48.3 | 221 | 444 |

### Memory per message type

Besides what they allocate per message, the writers use memory for the classes they load or generate (metaspace) and for
caches per message type (heap). The accessor writer generates a class per message type (and the filtering accessor writer
another one), curioswitch protobuf-jackson a marshaller class per message type, and the stream writer one class in all.
Measured with `MemoryFootprint`, after writing the messages for five seconds, relative to a JVM which only serializes them
to binary.

`payloads`: 9 message types with 265 fields:

| Writer | Loaded classes | Metaspace (KB) | Heap (KB) |
|---|---|---|---|
| `JsonFormat` | 609 | 2963 | 2832 |
| HubSpot jackson-datatype-protobuf | 1194 | 6547 | 3278 |
| curioswitch protobuf-jackson | 1237 | 7288 | 3959 |
| transcode | **28** | **278** | 1414 |
| stream | 114 | 856 | **1172** |
| accessor | 222 | 1431 | 1358 |
| accessor, filtering | 239 | 1646 | 1608 |

`descriptors`: 15 message types with 108 fields:

| Writer | Loaded classes | Metaspace (KB) | Heap (KB) |
|---|---|---|---|
| `JsonFormat` | 424 | 2403 | 2046 |
| HubSpot jackson-datatype-protobuf | 1098 | 6325 | 2933 |
| curioswitch protobuf-jackson | not supported | | |
| transcode | **27** | **249** | 1455 |
| stream | 127 | 919 | 1390 |
| accessor | 193 | 1146 | 1364 |
| accessor, filtering | 208 | 1328 | **1315** |

`all`: 60 message types with 630 fields:

| Writer | Loaded classes | Metaspace (KB) | Heap (KB) |
|---|---|---|---|
| `JsonFormat` | 955 | 4407 | 2600 |
| HubSpot jackson-datatype-protobuf | 1554 | 7988 | 3141 |
| curioswitch protobuf-jackson | not supported | | |
| transcode | **39** | **482** | **1262** |
| stream | 97 | 877 | 1315 |
| accessor | 244 | 2105 | 1678 |
| accessor, filtering | 261 | 2503 | 1770 |

curioswitch protobuf-jackson cannot write the descriptors (`FileDescriptorProto`), see `CurioswitchProtobufJacksonTest`.
The compiled code (code cache) is left out, as it varied too much with when the JIT compiled what.

The accessor writer uses about 1 MB more metaspace than the transcoding writer for generating code (the ClassFile API and
method handles), and about 2 KB per field of the message types it has written (the generated code and its profiling data),
against about half a KB for the transcoding writer. The stream writer uses about half a MB more than the transcoding writer,
most of it for [checking how protobuf writes messages](#the-convention-check) when the first stream writer is created. The
heap is about the same.

## The stream writer and the JIT

The stream writer runs the generated serialization code of the messages (`writeTo(CodedOutputStream)`) with its own
`CodedOutputStream`, so the JIT compiles that code both for binary serialization and for JSON, and the two could affect each
other. For comparison: the transcoding writer serializes to binary itself, and the accessor writer and curioswitch
protobuf-jackson read the fields with their accessors.

### Binary serialization, by writing JSON

The stream writer slows down binary serialization with `toByteArray()` of the same message types, by 28% for the medium
payload (10.8 instead of 8.5 microseconds, the median of five JVMs; 9.0 in some), as the JIT then compiles the
serialization code for more kinds of `CodedOutputStream` and messages. More than half of it comes from
[the convention check](#the-convention-check). The transcoding and accessor writers and curioswitch protobuf-jackson do
not have this effect, and `writeTo(OutputStream)` is not slowed down.

### The convention check

When the first stream writer is created, it checks that protobuf writes messages to a `CodedOutputStream` as the stream
writer expects (`CodedOutputStreamConventions`): it writes a few probe messages, among them a `Struct`, which is a map, and
compares the output with the transcoding writer's. If the JIT has already profiled protobuf's serialization code by then, as
in the benchmarks, which serialize the payloads to binary first, the probe messages widen those profiles, and the JIT
compiles the code for more cases. The small payload has 19 empty map fields: writing it, the stream writer then allocates
3.3 instead of 2.1 KB and takes 0.80 instead of 0.67 microseconds. An application which serializes messages with non-empty
maps gets much the same, with or without the check.


# Performance

How fast the writers are (CPU) and how much memory they use, writing a message as UTF-8 JSON. 

JMH on OpenJDK 25 on Fedora Linx + AMD Ryzen 9 9950X, two JVMs for each benchmark.

The small, medium, large, xlarge and xxlarge messages are 0.2 KB, 14 KB, 38 KB, 127 KB and 254 KB as JSON. 

The best value of each column is in **bold**; binary serialization is only for reference.

## CPU

Time per message in microseconds.

### Full protobuf messages

| Writer | small | medium | large | xlarge | xxlarge |
|---|---|---|---|---|---|
| `JsonFormat` | 4.48 | 42.3 | 127 | 369 | 755 |
| HubSpot jackson-datatype-protobuf | 4.20 | 39.9 | 108 | 254 | 537 |
| curioswitch protobuf-jackson | 0.910 | 14.9 | 50.3 | 140 | 285 |
| transcode | 0.853 | 17.8 | 45.8 | 162 | 320 |
| stream | 0.673 | **9.58** | 29.6 | 85.5 | 172 |
| accessor | **0.449** | 9.68 | **24.3** | **72.2** | **143** |
| binary (`toByteArray()`) | 0.392 | 9.00 | 15.7 | 88.9 | 179 |

curioswitch protobuf-jackson uses Jackson 2, the others Jackson 3. The payloads are also serialized to binary before
measuring, as in an application which sends them, see [the stream writer and the JIT](#the-stream-writer-and-the-jit).

### Binary and JSON

Serializing a message to binary with `toByteArray()` and then writing it as JSON, in sequence, like an application which
sends a message and logs it: the total time per message (`BinaryAndJsonBenchmark`).

| Writer | small | medium | large | xlarge | xxlarge |
|---|---|---|---|---|---|
| `JsonFormat` | 5.06 | 50.2 | 146 | 486 | 929 |
| HubSpot jackson-datatype-protobuf | 4.82 | 49.0 | 125 | 350 | 721 |
| curioswitch protobuf-jackson | 1.35 | 24.4 | 65.2 | 237 | 474 |
| transcode | 1.29 | 26.2 | 66.5 | 264 | 521 |
| stream | 1.22 | 18.9 | 48.6 | 169 | 363 |
| accessor | **1.01** | **18.5** | **41.8** | **168** | **333** |
| binary only (`toByteArray()`) | 0.379 | 8.49 | 14.1 | 95.1 | 191 |

### Writing to a string

`writeAsString()` of the writers of this project, which write UTF-8 and decode it, `JsonFormat`'s printer, and
`writeValueAsString()` of the other libraries, which write characters:

| Writer | small | medium | large | xlarge | xxlarge |
|---|---|---|---|---|---|
| `JsonFormat` | 4.47 | 34.8 | 109 | 306 | 622 |
| HubSpot jackson-datatype-protobuf | 4.14 | 23.2 | 92.9 | 232 | 455 |
| curioswitch protobuf-jackson | 0.891 | 12.1 | 31.7 | 127 | 255 |
| transcode | 0.913 | 19.4 | 47.9 | 175 | 349 |
| stream | 0.716 | 11.3 | 33.8 | 100 | 199 |
| accessor | **0.508** | **11.2** | **28.6** | **87.3** | **172** |

### Lite messages

| Writer | small | medium | large | xlarge | xxlarge |
|---|---|---|---|---|---|
| `MessageLiteToJson` | 48.7 | 85.5 | 584 | 761 | 1494 |
| `toString()` | 39.8 | 87.0 | 553 | 742 | 1508 |
| lite | **2.01** | **22.3** | **54.5** | **234** | **478** |
| binary (`toByteArray()`) | 0.716 | 11.4 | 24.9 | 98.9 | 188 |

`MessageLiteToJson` is a reflection-based writer, which looks up the accessors of every message while writing it.
`toString()` is protobuf's text format, not JSON.

## Memory

### Allocation per message

Kilobytes allocated per message, measured with JMH's `-prof gc`.

Full protobuf messages:

| Writer | small | medium | large | xlarge | xxlarge |
|---|---|---|---|---|---|
| `JsonFormat` | 7.38 | 100 | 353 | 901 | 1809 |
| HubSpot jackson-datatype-protobuf | 4.22 | 8.69 | 19.6 | 70.9 | 143 |
| curioswitch protobuf-jackson | 2.95 | 7.93 | 0.899 | 61.5 | 122 |
| transcode | 2.14 | 26.0 | 48.8 | 234 | 471 |
| stream | 2.07 | 1.70 | 0.649 | 8.88 | 17.0 |
| accessor | **1.80** | **1.39** | **0.406** | **8.58** | **16.7** |
| binary (`toByteArray()`) | 1.53 | 24.0 | 48.3 | 221 | 444 |

Writing to a string:

| Writer | small | medium | large | xlarge | xxlarge |
|---|---|---|---|---|---|
| `JsonFormat` | 7.10 | 100 | 353 | 901 | 1809 |
| HubSpot jackson-datatype-protobuf | 4.45 | 22.5 | 57.4 | 452 | 1035 |
| curioswitch protobuf-jackson | 3.25 | **21.9** | **38.8** | 443 | 1015 |
| transcode | 2.67 | 61.1 | 126 | 558 | 1121 |
| stream | 2.62 | 36.8 | 77.8 | **332** | **667** |
| accessor | **2.35** | 36.5 | 77.6 | **332** | **667** |

Lite messages:

| Writer | small | medium | large | xlarge | xxlarge |
|---|---|---|---|---|---|
| `MessageLiteToJson` | 225 | 302 | 3076 | 2579 | 5151 |
| `toString()` | 186 | 384 | 3230 | 3274 | 6548 |
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
| `JsonFormat` | 608 | 2961 | 2880 |
| HubSpot jackson-datatype-protobuf | 1194 | 6539 | 3294 |
| curioswitch protobuf-jackson | 1237 | 7294 | 3980 |
| transcode | **31** | **326** | 1346 |
| stream | 60 | 443 | **1121** |
| accessor | 225 | 1465 | 1375 |
| accessor, filtering | 242 | 1674 | 1673 |

`descriptors`: 15 message types with 108 fields:

| Writer | Loaded classes | Metaspace (KB) | Heap (KB) |
|---|---|---|---|
| `JsonFormat` | 423 | 2382 | 1785 |
| HubSpot jackson-datatype-protobuf | 1098 | 6290 | 2668 |
| curioswitch protobuf-jackson | not supported | | |
| transcode | **30** | **264** | 1149 |
| stream | 62 | 413 | 1126 |
| accessor | 195 | 1142 | **1122** |
| accessor, filtering | 210 | 1340 | 1134 |

`all`: 60 message types with 630 fields:

| Writer | Loaded classes | Metaspace (KB) | Heap (KB) |
|---|---|---|---|
| `JsonFormat` | 954 | 4409 | 2607 |
| HubSpot jackson-datatype-protobuf | 1554 | 7990 | 3137 |
| curioswitch protobuf-jackson | not supported | | |
| transcode | **42** | **513** | **1288** |
| stream | 70 | 648 | 1304 |
| accessor | 246 | 2153 | 1769 |
| accessor, filtering | 262 | 2513 | 1811 |

curioswitch protobuf-jackson cannot write the descriptors (`FileDescriptorProto`), see `CurioswitchProtobufJacksonTest`.
The compiled code (code cache) is left out, as it varied too much with when the JIT compiled what.

The accessor writer uses about 1 MB more metaspace than the stream writer for generating code (the ClassFile API and method
handles), and about 2 KB per field of the message types it has written (the generated code and its profiling data), against
about half a KB for the stream writer. The heap is about the same.

## The stream writer and the JIT

The stream writer runs the generated serialization code of the messages (`writeTo(CodedOutputStream)`) with its own
`CodedOutputStream`, so the JIT compiles that code both for binary serialization and for JSON, and the two could affect each
other. For comparison: the transcoding writer serializes to binary itself, and the accessor writer and curioswitch
protobuf-jackson read the fields with their accessors.

### Binary serialization, by writing JSON

The stream writer slows down binary serialization with `toByteArray()` of the same message types somewhat, by
8% for the medium payload, as the JIT then compiles the serialization code for two kinds of `CodedOutputStream`.
The transcoding and accessor writers and curioswitch protobuf-jackson do not have this effect.


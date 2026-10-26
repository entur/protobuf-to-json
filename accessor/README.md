# protobuf-to-json-accessor

The fastest writer for full protobuf messages: code generated at runtime, per message type, reads the fields with their
accessors (the getters of the generated message classes) and writes them to a Jackson 3 generator. The other writers
serialize the message to get at its fields (the transcoding and stream writers), or use protobuf's reflection (`JsonFormat`).

The output is the same as for the other full protobuf writers (the ProtoJSON format, like `JsonFormat`, with the
differences listed in the [README](../README.md)), and so is the filtering. Requires Java 24 or later (for the ClassFile API).

## Usage

```java
ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = AccessorProtobufJsonWriter.newBuilder()
        .withTypeRegistry(typeRegistry) // for google.protobuf.Any
        .withAnonymizeKeys("password")
        .withMaxSize(64 * 1024)
        .withWarmup(typicalRequest, typicalResponse) // optional
        .build();

try (JsonGenerator generator = jsonFactory.createGenerator(outputStream)) {
    writer.write(message, generator);
}
```

Writers are thread-safe; create them once and reuse them. The generated code is shared by all writers.

### Code generation up front, and warmup

The code for a message type is generated when the first message of the type is written. In a new JVM, the first message
took about 40-50 ms to write (generating the code, and running it interpreted), and the next hundred messages about 40
(small) to 100 (medium) microseconds each, until the JIT had compiled the code. To move this to when the writer is built:

 * `withMessageTypes(MyRequest.class, MyResponse.class)` generates the code for these message types, and for the message
   types of their fields. Fails if the code cannot be generated for a type.
 * `withWarmup(typicalRequest, typicalResponse)` writes these messages (to a generator which discards the output)
   `withWarmupIterations(n)` times, 10,000 by default, about as many as the JIT needs to compile the code of a message type
   with its optimizing compiler. This generates the code, links it, and lets the JIT profile and compile it. The JIT optimizes
   for what it has seen, so the messages should be like the messages the writer will write. With the medium payload of the
   benchmarks, `build()` then took about 0.2 s, and the first hundred writes after it about 15 microseconds each, close to
   full speed.
 * `withWarmupTypes(MyRequest.class, MyResponse.class)` does the same for message types, when there are no typical
   messages at hand: with messages of the types with every field set, the fields of message types filled the same way, and
   one message per member of the largest oneof. Also registers the types. With the medium payload, the first hundred writes
   after it took about 32 microseconds each, as the JIT had to adapt the code to the real messages.

## How it works

### Finding the accessors

The fields of a message type are those of its descriptor. Their accessors are found by the names protoc gives them, and
verified with reflection:

 * the field name in upper camel case: `foo_bar` gets `getFooBar()`; group fields are named after their type
 * an underscore appended if the name would collide with a method of the base classes: `class` gets `getClass_()`
 * the field number appended if the accessors of two fields would collide, i.e. a repeated field `foo` (with
   `getFooCount()`) and a field `foo_count`

| Field | Present if | Value |
|---|---|---|
| with presence: messages, proto2 and `optional` fields, oneofs | `hasFoo()` | `getFoo()` |
| without presence: proto3 scalars | not the default value: 0, false, an empty string or bytes (floats by their bits, so -0.0 is written) | `getFoo()` |
| open enum | as above | `getFooValue()`: the number, also for unknown values |
| closed enum | as above | `getFoo().getNumber()` |
| repeated | `getFooCount() > 0` | `getFoo(int)`, or `getFooValue(int)` for open enums: no boxing |
| map | `getFooCount() > 0` | `getFooMap()`, or `getFooValueMap()` for open enum values |

If an accessor is not found as expected, messages of the type are transcoded instead (see [fallbacks](#fallbacks)).

### The generated code

For each message type, a hidden class is generated with the ClassFile API. Its static method writes a message as an
object, with the fields in field number order, like `JsonFormat`. For the `Item` of the GitHub search response of the
benchmarks, as Java:

```java
static void write(JsonGenerator generator, SerializableString name, Item message, AccessorProtobufJsonWriter writer) {
    startObject(generator, name);
    int id = message.getId(); // uint32, without presence
    if (id != 0) {
        writeUInt32(generator, ID, id); // ID: the field name, pre-encoded
    }
    String fullName = message.getFullName();
    if (!fullName.isEmpty()) {
        writeString(generator, FULL_NAME, fullName);
    }
    if (message.hasOwner()) {
        write(generator, OWNER, message.getOwner(), writer); // the code generated for User
    }
    ...
    if (message.hasCreatedAt()) {
        writeTimestamp(generator, CREATED_AT, message.getCreatedAt(), writer);
    }
    ...
    endObject(generator);
}
```

 * The constants (field names as pre-encoded `SerializableString`s, enum names, and the field information for filtering)
   are class data of the hidden class, loaded with `ldc` of dynamically-computed constants, so that the JIT treats them as
   constants.
 * Messages with many fields are written by several methods of 16 fields each, called by the `write` method, so that no
   method is too large for the JIT to compile (8000 bytes of bytecode), or to inline the accessors into.
 * The `write(...)` call for a field with a message type is an `invokedynamic` instruction. When it is first run, its
   bootstrap method (`MessageWriters.bootstrap`) generates or finds the code for the message type of the field, and links
   the call to it for good (a constant call site), so that the JIT can inline it. So code is only generated for the message
   types which are written, and recursive message types are no problem.
 * Well-known types have methods of their own (`writeTimestamp`, `writeInt64Value`, ...), which i.e. format timestamps
   without allocating a string. The contents of `google.protobuf.Any` values are transcoded from their bytes, with the
   type registry.

### Writing values out of line

The generated code only reads fields and calls the methods of `AccessorJsonValues` (`writeString`, `writeUInt32`, ...),
which write the values with Jackson. It calls them through a class with the same methods, which forward to
`AccessorJsonValues` and are padded with no-op instructions to more than the size of methods the JIT inlines
(`-XX:FreqInlineSize`, 325 bytes by default).

Inlined into the code of every message type, writing the values (i.e. Jackson's string encoding) made the compiled code of
a message type with many fields so large that the JIT stopped inlining before the end of it, so that even the accessors
were calls, and optimized the rest less well: about 30% slower. Called out of line, the writing of each kind of value is
compiled once.

Strings are copied to a (per thread) `char[]` before they are written: Jackson 3 writes short `String`s one `charAt` at a
time, which was up to a third slower than writing the characters of an array. The stream writer and the filtering
writers do the same.

### Filtering

When the writer filters, another class is generated per message type, with the same structure, and for each field:

 * paths: the field name is matched against the paths. A pruned field is replaced (`"[removed]"`) without calling its
   getter. An anonymized scalar is replaced (`"*"`), while anonymized messages, arrays and objects keep their structure,
   with their values replaced
 * max string length: strings and bytes are truncated while written
 * max size: each write returns false once the output is full, and the generated code then closes the open objects and
   arrays and returns false

The values are written with the same `FilteredJsonValues` and `FilterContext` as the other filtering writers, so the output
is the same: the tests compare it with filtering the unfiltered output with a reference implementation of the filters.

### Where the code is defined

The hidden classes are defined in this library's package, if its class loader can see the classes of the messages.
Otherwise (i.e. messages of an application whose libraries are loaded by a parent class loader), they are defined in the
package of the message. A package-private class is defined there first, to get a lookup with full privilege access to the
package; in a named module, the package must be open to this library.

### Fallbacks

These messages are transcoded instead, with `TranscodingProtobufJsonWriter`, which writes the same:

 * messages which are not generated, i.e. `DynamicMessage`
 * well-known types as the root message
 * message types for which no code can be generated: if an accessor is not found as expected, or if the class loaders of
   the messages and of this library cannot see each other's classes

Message types registered with `withMessageTypes(..)` fail instead.

## Performance and memory

From [PERFORMANCE.md](../PERFORMANCE.md), time per message in microseconds:

| Writer | small (0.2 KB) | medium (14 KB) | large (38 KB) | xlarge (127 KB) | xxlarge (254 KB) |
|---|---|---|---|---|---|
| `JsonFormat` | 4.48 | 42.3 | 127 | 369 | 755 |
| curioswitch protobuf-jackson | 0.910 | 14.9 | 50.3 | 140 | 285 |
| stream | 0.673 | **9.58** | 29.6 | 85.5 | 172 |
| accessor | **0.449** | 9.68 | **24.3** | **72.2** | **143** |

 * CPU: the fastest of the writers in most cases, also with filtering.
 * It does not run protobuf's serialization code, so it does not slow down binary serialization of the same message types,
   and its speed does not depend on it either; see [the stream writer and the JIT](../PERFORMANCE.md#the-stream-writer-and-the-jit).
 * Memory: it allocates the least per message, but uses more memory per message type than the other writers of this
   project: about 1 MB of metaspace for generating code, and about 2 KB per field of the message types it has written, against
   about half a KB for the stream writer; see [memory per message type](../PERFORMANCE.md#memory-per-message-type).

## Tests

Besides the tests which all full protobuf writers share (the same as `JsonFormat` for random conformance test messages and
the messages of curioswitch protobuf-jackson, and filtering the same as the reference filter), the tests of this module
check that code is generated (not transcoded) for every test message type, the accessors of messages whose names protoc
changes (`src/test/protobuf`, also with editions), messages loaded by another class loader, and the warmup, also with the
messages it creates for message types.

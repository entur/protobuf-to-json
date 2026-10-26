package no.entur.protobuf.json.accessor;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.accessor.names.AccessorNamesProto3.AccessorNames3;
import no.entur.protobuf.json.test.RandomMessages;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonGenerator;

import java.io.IOException;
import java.io.InputStream;

import static com.google.common.truth.Truth.assertThat;

/**
 * Messages loaded by a class loader which this library's class loader cannot see, like messages of an application whose
 * libraries are loaded by a parent class loader: the writers are defined in the packages of the messages.
 */

public class ClassLoaderTest {

    private static final String PACKAGE = AccessorNames3.class.getPackageName() + ".";

    /**
     * Loads the classes of a package itself, from the parent's class files, and everything else from the parent.
     */

    private static class ChildFirstClassLoader extends ClassLoader {

        ChildFirstClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(PACKAGE)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    try (InputStream input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (input == null) {
                            throw new ClassNotFoundException(name);
                        }
                        byte[] bytes = input.readAllBytes();
                        type = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
        }
    }

    @Test
    public void definesWritersInThePackageOfTheMessages() throws Exception {
        Class<?> type = new ChildFirstClassLoader(getClass().getClassLoader()).loadClass(AccessorNames3.class.getName());
        assertThat(type).isNotSameInstanceAs(AccessorNames3.class);

        ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = AccessorProtobufJsonWriter.newBuilder().build();
        ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> filteringWriter = AccessorProtobufJsonWriter.newBuilder().withPruneKeys("data").build();
        for (int seed = 0; seed < 20; seed++) {
            Message original = new RandomMessages(seed).fill(AccessorNames3.newBuilder());
            Message message = (Message) type.getMethod("parseFrom", byte[].class).invoke(null, (Object) original.toByteArray());

            assertThat(writer.writeAsString(message)).isEqualTo(writer.writeAsString(original));
            assertThat(filteringWriter.writeAsString(message)).isEqualTo(filteringWriter.writeAsString(original));
        }

        for (boolean filtering : new boolean[]{false, true}) {
            MessageWriters.Generated generated = MessageWriters.get(type, filtering);
            if (generated.failure() != null) {
                throw new AssertionError("Expected a generated writer", generated.failure());
            }
            assertThat(generated.transcoding()).isFalse();
            assertThat(generated.writer().getClass().getPackageName()).isEqualTo(type.getPackageName());
            assertThat(generated.writer().getClass().getClassLoader()).isSameInstanceAs(type.getClassLoader());
        }
    }
}

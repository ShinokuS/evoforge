package io.github.evoforge.simulation.world.definition;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefinitionFileReaderTest {

    @TempDir
    Path directory;

    @Test
    void readsJsonFilesRecursively() throws IOException {
        Path nested = directory.resolve("nature");
        Files.createDirectories(nested);

        Files.writeString(
                directory.resolve("apple.json"),
                """
                        {
                            "key": "core:apple",
                            "aspects": {}
                        }
                        """,
                UTF_8);

        Files.writeString(
                nested.resolve("oak.json"),
                """
                        {
                            "key": "core:oak",
                            "aspects": {}
                        }
                        """,
                UTF_8);

        DefinitionFileReader reader = new DefinitionFileReader();

        List<JsonObject> documents = reader.read(directory);

        assertEquals(2, documents.size());
    }

    @Test
    void ignoresNonJsonFiles() throws IOException {
        Files.writeString(
                directory.resolve("apple.json"),
                """
                        {
                            "key": "core:apple",
                            "aspects": {}
                        }
                        """,
                UTF_8);

        Files.writeString(
                directory.resolve("notes.txt"),
                "ignored",
                UTF_8);

        DefinitionFileReader reader = new DefinitionFileReader();

        List<JsonObject> documents = reader.read(directory);

        assertEquals(1, documents.size());
    }

    @Test
    void readsFilesInDeterministicOrder() throws IOException {
        Files.writeString(
                directory.resolve("z.json"),
                """
                        {
                            "key": "core:z",
                            "aspects": {}
                        }
                        """,
                UTF_8);

        Files.writeString(
                directory.resolve("a.json"),
                """
                        {
                            "key": "core:a",
                            "aspects": {}
                        }
                        """,
                UTF_8);

        DefinitionFileReader reader = new DefinitionFileReader();

        List<JsonObject> documents = reader.read(directory);

        assertEquals(
                "core:a",
                documents.get(0).get("key").getAsString());

        assertEquals(
                "core:z",
                documents.get(1).get("key").getAsString());
    }

    @Test
    void sortsNestedPathsDeterministically() throws IOException {
        Path alpha = directory.resolve("alpha");
        Path beta = directory.resolve("beta");

        Files.createDirectories(alpha);
        Files.createDirectories(beta);

        Files.writeString(
                beta.resolve("item.json"),
                """
                        {
                            "key": "core:beta",
                            "aspects": {}
                        }
                        """,
                UTF_8);

        Files.writeString(
                alpha.resolve("item.json"),
                """
                        {
                            "key": "core:alpha",
                            "aspects": {}
                        }
                        """,
                UTF_8);

        DefinitionFileReader reader = new DefinitionFileReader();

        List<JsonObject> documents = reader.read(directory);

        assertEquals(
                "core:alpha",
                documents.get(0).get("key").getAsString());

        assertEquals(
                "core:beta",
                documents.get(1).get("key").getAsString());
    }

    @Test
    void rejectsInvalidJson() throws IOException {
        Files.writeString(
                directory.resolve("broken.json"),
                "{ broken",
                UTF_8);

        DefinitionFileReader reader = new DefinitionFileReader();

        assertThrows(
                IllegalArgumentException.class,
                () -> reader.read(directory));
    }

    @Test
    void rejectsNonObjectJson() throws IOException {
        Files.writeString(
                directory.resolve("array.json"),
                """
                        [
                            "core:test"
                        ]
                        """,
                UTF_8);

        DefinitionFileReader reader = new DefinitionFileReader();

        assertThrows(
                IllegalArgumentException.class,
                () -> reader.read(directory));
    }

    @Test
    void rejectsMissingDirectory() {
        DefinitionFileReader reader = new DefinitionFileReader();

        assertThrows(
                IllegalArgumentException.class,
                () -> reader.read(
                        directory.resolve("missing")));
    }

    @Test
    void rejectsNullRoot() {
        DefinitionFileReader reader = new DefinitionFileReader();

        assertThrows(
                IllegalArgumentException.class,
                () -> reader.read(null));
    }
}
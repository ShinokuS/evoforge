package io.github.evoforge.simulation.definition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class DefinitionFileReader {

    public List<JsonObject> read(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }

        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException(
                    "definition root must be a directory: " + root);
        }

        List<Path> files = findFiles(root);
        List<JsonObject> documents = new ArrayList<>(files.size());

        for (Path file : files) {
            documents.add(readFile(file));
        }

        return documents;
    }

    private List<Path> findFiles(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(this::isJson)
                    .sorted(
                            Comparator.comparing(
                                    path -> normalizedRelativePath(root, path)))
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "failed to scan definition directory: " + root,
                    exception);
        }
    }

    private JsonObject readFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file, UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);

            if (!element.isJsonObject()) {
                throw new IllegalArgumentException(
                        "definition must be a JSON object: " + file);
            }

            return element.getAsJsonObject();
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException(
                    "invalid definition JSON: " + file,
                    exception);
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "failed to read definition file: " + file,
                    exception);
        }
    }

    private boolean isJson(Path path) {
        return path
                .getFileName()
                .toString()
                .toLowerCase(Locale.ROOT)
                .endsWith(".json");
    }

    private String normalizedRelativePath(
            Path root,
            Path path) {
        return root
                .relativize(path)
                .toString()
                .replace('\\', '/');
    }
}

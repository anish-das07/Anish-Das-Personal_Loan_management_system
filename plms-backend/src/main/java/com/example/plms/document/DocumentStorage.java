package com.example.plms.document;

import com.example.plms.common.PlmsProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Private file store on local disk, outside any web-served directory. Object keys are random UUIDs,
 * never derived from user input. Swap for an object store (with server-side encryption) in production.
 */
@Component
public class DocumentStorage {

    private static final Pattern KEY = Pattern.compile("[0-9a-f-]{36}");
    private final Path root;

    public DocumentStorage(PlmsProperties properties) {
        this.root = Path.of(properties.documents().storageDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create document storage directory", e);
        }
    }

    public String store(byte[] content) {
        String key = UUID.randomUUID().toString();
        try {
            Files.write(resolve(key), content, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store document", e);
        }
        return key;
    }

    public byte[] read(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read document", e);
        }
    }

    private Path resolve(String key) {
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid object key");
        }
        Path p = root.resolve(key + ".bin").normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Invalid object key");
        }
        return p;
    }
}

package com.docflow.storage;

import com.docflow.config.DocFlowProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Files on local disk. Default for running on your own machine. */
@Component
@ConditionalOnProperty(prefix = "docflow.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(DocFlowProperties properties) {
        this.root = Path.of(properties.storage().root()).toAbsolutePath().normalize();
    }

    @Override
    public String store(String tenantId, String sha256, String extension, byte[] content) {
        // The file name is the content hash, never the user's file name: no path traversal possible.
        Path target = root.resolve(tenantId).resolve(sha256 + "." + extension).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        try {
            Files.createDirectories(target.getParent());
            if (!Files.exists(target)) {
                Files.write(target, content, StandardOpenOption.CREATE_NEW);
            }
            return root.relativize(target).toString().replace('\\', '/');
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store upload", e);
        }
    }

    @Override
    public byte[] read(String storagePath) {
        Path target = root.resolve(storagePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Stored file not found: " + storagePath, e);
        }
    }
}

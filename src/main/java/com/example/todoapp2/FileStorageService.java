package com.example.todoapp2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final Path directory;

    public FileStorageService(@Value("${todo.attachments.directory}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    String sanitizeFilename(String name) {
        if (name == null) {
            return "attachment";
        }
        String normalized = name.replace('\\', '/');
        String basename = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cc}\\p{Cf}<>:\"|?*]", "_").strip();
        basename = basename.replaceAll("^[. ]+|[. ]+$", "");
        if (basename.isBlank()) {
            return "attachment";
        }
        return basename.substring(0, basename.offsetByCodePoints(0,
                Math.min(200, basename.codePointCount(0, basename.length()))));
    }

    String store(MultipartFile file) {
        String name = UUID.randomUUID().toString();
        Path destination = path(name);
        try {
            Files.createDirectories(directory);
            Files.createFile(destination);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        remove(destination);
                    }
                }
            });
            try (var input = file.getInputStream();
                 var output = Files.newOutputStream(destination, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                input.transferTo(output);
            }
            return name;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    byte[] read(String name) {
        try (var input = Files.newInputStream(path(name), LinkOption.NOFOLLOW_LINKS)) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    void deleteAfterCommit(String name) {
        Path destination = path(name);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                remove(destination);
            }
        });
    }

    private Path path(String name) {
        if (!UUID.fromString(name).toString().equals(name)) {
            throw new IllegalArgumentException("Invalid attachment storage name");
        }
        return directory.resolve(name);
    }

    private void remove(Path destination) {
        try {
            Files.deleteIfExists(destination);
        } catch (IOException exception) {
            log.warn("Attachment cleanup failed storageName={} exception={}",
                    destination.getFileName(), exception.getClass().getSimpleName());
        }
    }
}

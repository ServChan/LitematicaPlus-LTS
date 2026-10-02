package org.lts.litematicaplus.util;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AtomicFilePersistence {
    private static final int MOVE_ATTEMPTS = 5;
    private static final long MOVE_BACKOFF_MILLIS = 40L;

    private AtomicFilePersistence() {
    }

    public static void write(Path target, Path backup, String contents) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            if (backup != null && Files.isRegularFile(target)) {
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            replace(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static void replace(Path source, Path target) throws IOException {
        int attempts = Workers.onWorkerThread() ? MOVE_ATTEMPTS : 1;
        for (int attempt = 1; ; attempt++) {
            try {
                move(source, target);
                return;
            } catch (FileSystemException exception) {
                if (attempt >= attempts || exception instanceof NoSuchFileException) {
                    throw exception;
                }
            }
            try {
                Thread.sleep(MOVE_BACKOFF_MILLIS * attempt);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted while replacing " + target);
            }
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void restoreBackup(Path target, Path backup) throws IOException {
        quarantine(target);
        Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void quarantine(Path target) throws IOException {
        if (Files.isRegularFile(target)) {
            Files.move(target, target.resolveSibling(target.getFileName() + ".corrupt"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

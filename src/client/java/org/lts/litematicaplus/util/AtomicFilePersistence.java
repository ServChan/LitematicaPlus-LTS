package org.lts.litematicaplus.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AtomicFilePersistence {
    private AtomicFilePersistence() {
    }

    public static void write(Path target, Path backup, String contents) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, contents, StandardCharsets.UTF_8);
        if (backup != null && Files.isRegularFile(target)) {
            Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void restoreBackup(Path target, Path backup) throws IOException {
        if (Files.isRegularFile(target)) {
            Files.move(target, target.resolveSibling(target.getFileName() + ".corrupt"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
    }
}

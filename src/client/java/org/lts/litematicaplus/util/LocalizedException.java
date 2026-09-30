package org.lts.litematicaplus.util;

import java.io.IOException;

public final class LocalizedException extends IOException {
    private final String key;
    private final Object[] args;

    public LocalizedException(String key, String message, Object... args) {
        super(message);
        this.key = key;
        this.args = args;
    }

    public LocalizedException(String key, String message, Throwable cause) {
        super(message, cause);
        this.key = key;
        this.args = new Object[0];
    }

    public String key() {
        return this.key;
    }

    public Object[] args() {
        return this.args;
    }
}

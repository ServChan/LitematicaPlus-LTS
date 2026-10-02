package org.lts.litematicaplus.gui;

import net.minecraft.locale.Language;

public final class TextMemo {
    private Language language;
    private Object ref;
    private long a;
    private long b;
    private long c;
    private String text;

    public boolean stale(Object ref, long a, long b, long c) {
        Language current = Language.getInstance();
        if (this.text != null && this.language == current && this.ref == ref && this.a == a && this.b == b
                && this.c == c) {
            return false;
        }
        this.language = current;
        this.ref = ref;
        this.a = a;
        this.b = b;
        this.c = c;
        this.text = null;
        return true;
    }

    public boolean stale(Object ref) {
        return stale(ref, 0L, 0L, 0L);
    }

    public String set(String value) {
        this.text = value;
        return value;
    }

    public String text() {
        return this.text;
    }
}

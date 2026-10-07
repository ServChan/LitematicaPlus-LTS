package org.lts.litematicaplus.share;

import java.util.EnumSet;
import java.util.Set;
import java.util.StringJoiner;

public enum SyncFeature {
    CORE,
    FEATURE,
    MODIFY,
    MESSAGE,
    QUOTA,
    DEBUG,
    CORE_EX,
    VERSION,
    DISPLAY_NAME;

    public static EnumSet<SyncFeature> parse(String text) {
        EnumSet<SyncFeature> features = EnumSet.noneOf(SyncFeature.class);
        if (text == null) {
            return features;
        }
        for (String part : text.split("\n")) {
            for (SyncFeature feature : values()) {
                if (feature.name().equals(part.strip())) {
                    features.add(feature);
                }
            }
        }
        return features;
    }

    public static String join(Set<SyncFeature> features) {
        StringJoiner joiner = new StringJoiner("\n");
        for (SyncFeature feature : features) {
            joiner.add(feature.name());
        }
        return joiner.toString();
    }
}

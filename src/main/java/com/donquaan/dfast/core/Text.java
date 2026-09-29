package com.donquaan.dfast.core;

import java.util.Locale;

public final class Text {
    private Text() {
    }

    public static String fixed(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }
}

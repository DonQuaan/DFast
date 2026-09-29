package com.donquaan.dfast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class TextTest {
    @Test
    void usesADotWhateverTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("6.94", Text.fixed(6.944, 2));
            Locale.setDefault(Locale.forLanguageTag("vi-VN"));
            assertEquals("1234.5", Text.fixed(1234.5, 1));
            assertEquals("60", Text.fixed(59.6, 0));
        } finally {
            Locale.setDefault(previous);
        }
    }
}

package com.donquaan.dfast.latency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.donquaan.dfast.core.RenderAheadPolicy;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LangKeysTest {
    private static final Path LANG = Path.of(System.getProperty("dfast.resources"), "assets", "dfast", "lang");
    private static final Set<String> USED = used();

    private static Set<String> used() {
        Set<String> keys = new HashSet<>(List.of(
                LatencyClientModule.KEY_TITLE,
                LatencyClientModule.KEY_FPS,
                LatencyClientModule.KEY_FRAME,
                LatencyClientModule.KEY_LOW,
                LatencyClientModule.KEY_OWNER,
                LatencyClientModule.KEY_GPU_WAIT,
                LatencyClientModule.KEY_TOGGLE,
                LatencyClientModule.KEY_CATEGORY));
        for (RenderAheadPolicy.Owner owner : RenderAheadPolicy.Owner.values()) {
            keys.add(LatencyClientModule.ownerKey(owner));
        }
        return keys;
    }

    private static Set<String> keys(String file) throws Exception {
        return JsonParser.parseString(Files.readString(LANG.resolve(file))).getAsJsonObject().keySet();
    }

    @Test
    void everyLanguageHasExactlyTheKeysTheCodeUses() throws Exception {
        assertEquals(USED, keys("en_us.json"));
        assertEquals(USED, keys("vi_vn.json"));
    }

    @Test
    void percentSignsAreEscapedForTranslatableText() throws Exception {
        for (String file : Set.of("en_us.json", "vi_vn.json")) {
            String low = JsonParser.parseString(Files.readString(LANG.resolve(file)))
                    .getAsJsonObject().get(LatencyClientModule.KEY_LOW).getAsString();
            assertTrue(low.contains("%%") && low.contains("%s"), file);
        }
    }
}

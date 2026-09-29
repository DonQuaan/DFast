package com.donquaan.dfast;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.junit.jupiter.api.Test;

class ModMetadataTest {
    private static final Path RESOURCES = Path.of(System.getProperty("dfast.resources"));
    private static final String MINECRAFT = System.getProperty("dfast.minecraftVersion");
    private static final String FABRIC_API = System.getProperty("dfast.fabricApiVersion");

    private static JsonObject metadata() throws Exception {
        return JsonParser.parseString(Files.readString(RESOURCES.resolve("fabric.mod.json"))).getAsJsonObject();
    }

    private static VersionPredicate depends(String id) throws Exception {
        return VersionPredicate.parse(metadata().getAsJsonObject("depends").get(id).getAsString());
    }

    @Test
    void acceptsExactlyTheMinecraftVersionItIsBuiltFor() throws Exception {
        VersionPredicate predicate = depends("minecraft");
        assertTrue(predicate.test(Version.parse(MINECRAFT)));
        assertFalse(predicate.test(Version.parse("1.21.2")));
        assertFalse(predicate.test(Version.parse("1.21.11")));
        assertFalse(predicate.test(Version.parse("1.21")));
    }

    @Test
    void requiresTheFabricApiBuildItIsCompiledAgainst() throws Exception {
        VersionPredicate predicate = depends("fabric-api");
        assertTrue(predicate.test(Version.parse(FABRIC_API)));
        assertFalse(predicate.test(Version.parse("0.116.12+" + MINECRAFT)));
    }

    @Test
    void declaresNoMissingResourcesAndLoadableEntrypoints() throws Exception {
        JsonObject json = metadata();
        assertFalse(json.has("icon") && !Files.exists(RESOURCES.resolve(json.get("icon").getAsString())));
        assertFalse(json.has("mixins"));
        ClassLoader loader = getClass().getClassLoader();
        json.getAsJsonObject("entrypoints").entrySet().forEach(entry ->
                entry.getValue().getAsJsonArray().forEach(name ->
                        assertDoesNotThrow(() -> Class.forName(name.getAsString(), false, loader))));
    }
}

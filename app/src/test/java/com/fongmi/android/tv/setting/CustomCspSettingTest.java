package com.fongmi.android.tv.setting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

public class CustomCspSettingTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void migratesRegistryAndAssetsOnceAndKeepsTheLegacyDirectory() throws Exception {
        Path legacy = folder.newFolder("legacy").toPath();
        Path target = folder.getRoot().toPath().resolve("private");
        Path script = Files.createDirectories(legacy.resolve("site")).resolve("script.js");
        Files.writeString(script, "original");
        Files.writeString(legacy.resolve("registry.json"), "{\"items\":[]}");

        CustomCspSetting.migrateLegacy(legacy.toFile(), target.toFile());
        assertEquals("{\"items\":[]}", Files.readString(target.resolve("registry.json")));
        assertEquals("original", Files.readString(target.resolve("site/script.js")));
        assertEquals("original", Files.readString(script));

        Files.writeString(target.resolve("site/script.js"), "edited");
        CustomCspSetting.migrateLegacy(legacy.toFile(), target.toFile());
        assertEquals("edited", Files.readString(target.resolve("site/script.js")));
    }

    @Test
    public void missingLegacyRegistryLeavesMigrationAvailableForLater() throws Exception {
        Path legacy = folder.newFolder("legacy").toPath();
        Path target = folder.getRoot().toPath().resolve("private");

        CustomCspSetting.migrateLegacy(legacy.toFile(), target.toFile());

        assertFalse(Files.exists(target));
    }

    @Test
    public void lateMigrationKeepsNewAndEditedSitesAndRunsOnlyOnce() throws Exception {
        Path legacy = folder.newFolder("legacy").toPath();
        Path target = folder.newFolder("private").toPath();
        Path edited = Files.createDirectories(target.resolve("shared")).resolve("script.js");
        Files.writeString(edited, "edited");
        Files.writeString(target.resolve("registry.json"), "{\"enabled\":false,\"items\":[{\"id\":\"new\"},{\"id\":\"shared\",\"name\":\"edited\"}]}");
        CustomCspSetting.migrateLegacy(legacy.toFile(), target.toFile());

        Files.writeString(Files.createDirectories(legacy.resolve("old")).resolve("script.js"), "old");
        Files.writeString(Files.createDirectories(legacy.resolve("shared")).resolve("script.js"), "original");
        Files.writeString(legacy.resolve("registry.json"), "{\"enabled\":true,\"items\":[{\"id\":\"old\"},{\"id\":\"shared\",\"name\":\"original\"}]}");
        CustomCspSetting.migrateLegacy(legacy.toFile(), target.toFile());

        assertEquals("{\"enabled\":false,\"items\":[{\"id\":\"new\"},{\"id\":\"shared\",\"name\":\"edited\"},{\"id\":\"old\"}]}",
                Files.readString(target.resolve("registry.json")));
        assertEquals("edited", Files.readString(edited));
        assertEquals("old", Files.readString(target.resolve("old/script.js")));

        Files.writeString(target.resolve("registry.json"), "{\"items\":[]}");
        CustomCspSetting.migrateLegacy(legacy.toFile(), target.toFile());
        assertEquals("{\"items\":[]}", Files.readString(target.resolve("registry.json")));
    }

    @Test
    public void blankSearchMatchesEveryItem() {
        assertTrue(CustomCspSetting.matchesSearch(null, "星河影视", "source-key", "https://example.com/api"));
        assertTrue(CustomCspSetting.matchesSearch("  ", "星河影视", "source-key", "https://example.com/api"));
    }

    @Test
    public void searchMatchesNameKeyAndUrlsIgnoringCase() {
        String[] fields = {"影视仓", "movie-source", "https://Example.com/api", "https://example.com/home"};

        assertTrue(CustomCspSetting.matchesSearch("影视", fields));
        assertTrue(CustomCspSetting.matchesSearch("MOVIE-SOURCE", fields));
        assertTrue(CustomCspSetting.matchesSearch("example.com/api", fields));
        assertTrue(CustomCspSetting.matchesSearch("EXAMPLE.COM/HOME", fields));
        assertFalse(CustomCspSetting.matchesSearch("not-found", fields));
    }

    @Test
    public void searchMatchesLiveUrlAndOtherField() {
        assertTrue(CustomCspSetting.matchesSearch("live.m3u8", "新闻直播", "https://tv.example.com/live.m3u8"));
        assertTrue(CustomCspSetting.matchesSearch("HEADERS", "headers", "token"));
    }
}

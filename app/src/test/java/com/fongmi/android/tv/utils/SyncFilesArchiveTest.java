package com.fongmi.android.tv.utils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SyncFilesArchiveTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void privateCspUsesTheLegacyEntryNamesAndRestoresIntoPrivateStorage() throws Exception {
        Path root = folder.newFolder("root").toPath();
        Path cache = folder.newFolder("cache").toPath();
        Path privateCsp = folder.newFolder("private").toPath();
        Path restored = folder.newFolder("restored").toPath();
        Path legacy = Files.createDirectories(root.resolve("TV/CustomCsp"));
        Files.writeString(legacy.resolve("registry.json"), "legacy");
        Files.writeString(legacy.resolve("obsolete.js"), "obsolete");
        Files.writeString(privateCsp.resolve("registry.json"), "private");
        SyncFiles.Archive archive = SyncFiles.createArchive(root.toFile(), cache.toFile(), privateCsp.toFile(), List.of("TV"), null, null);

        assertEquals(1, Collections.frequency(entries(archive.getFile()), "TV/CustomCsp/registry.json"));
        assertFalse(entries(archive.getFile()).contains("TV/CustomCsp/obsolete.js"));
        assertEquals(1, SyncFiles.restoreArchive(archive.getFile(), root.toFile(), restored.toFile()));
        assertEquals("private", Files.readString(restored.resolve("registry.json")));
        assertEquals("legacy", Files.readString(legacy.resolve("registry.json")));

        SyncFiles.Archive legacyArchive = SyncFiles.createArchive(root.toFile(), cache.toFile(), List.of("TV/CustomCsp"));
        assertEquals(2, SyncFiles.restoreArchive(legacyArchive.getFile(), root.toFile(), restored.toFile()));
        assertEquals("legacy", Files.readString(restored.resolve("registry.json")));
        assertEquals("obsolete", Files.readString(restored.resolve("obsolete.js")));
    }

    @Test
    public void overlappingTargetsProduceOneValidArchiveWithoutRuntimeTrees() throws Exception {
        Path workspace = Files.createTempDirectory("sync-files-archive-");
        Path root = Files.createDirectories(workspace.resolve("root"));
        Path cache = Files.createDirectories(workspace.resolve("cache"));
        Path customCsp = Files.createDirectories(root.resolve("TV/CustomCsp"));
        Path log = Files.createDirectories(root.resolve("TV/log"));
        Files.writeString(root.resolve("TV/data.json"), "data", StandardCharsets.UTF_8);
        Files.writeString(customCsp.resolve("config.json"), "config", StandardCharsets.UTF_8);
        Files.writeString(log.resolve("ignored.log"), "ignored", StandardCharsets.UTF_8);
        SyncFiles.Archive archive = null;
        try {
            archive = SyncFiles.createArchive(root.toFile(), cache.toFile(), List.of("TV", "TV/CustomCsp", "TV/log"));

            assertTrue(archive != null && archive.getFile().isFile());
            assertEquals(2, archive.getCount());
            List<String> entries = entries(archive.getFile());
            assertEquals(1, Collections.frequency(entries, "TV/"));
            assertEquals(1, Collections.frequency(entries, "TV/CustomCsp/"));
            assertEquals(1, Collections.frequency(entries, "TV/CustomCsp/config.json"));
            assertEquals(1, Collections.frequency(entries, "TV/data.json"));
            assertFalse(entries.stream().anyMatch(name -> name.equals("TV/log/") || name.startsWith("TV/log/")));
        } finally {
            if (archive != null) archive.delete();
            deleteTree(workspace);
        }
    }

    private static List<String> entries(File archive) throws Exception {
        List<String> entries = new ArrayList<>();
        try (ZipInputStream input = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                entries.add(entry.getName());
                input.closeEntry();
            }
        }
        return entries;
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            Path[] items = paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
            for (Path item : items) Files.deleteIfExists(item);
        }
    }
}

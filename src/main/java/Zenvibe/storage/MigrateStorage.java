package Zenvibe.storage;

import java.nio.file.Path;

/** Optional offline importer; normal bot startup performs the same migration automatically. */
public final class MigrateStorage {
    public static void main(String[] args) {
        if (args.length > 1) throw new IllegalArgumentException("Usage: MigrateStorage [config-directory]");
        Path directory = Path.of(args.length == 1 ? args[0] : "config");
        Path database = directory.resolve("zenvibe.db");
        try (var ignored = new SqliteStore(database, directory)) {
            System.out.println("SQLite storage ready: " + database.toAbsolutePath());
            System.out.println("Legacy JSON files have been left unchanged. Completed migrations are not repeated.");
        }
    }
}

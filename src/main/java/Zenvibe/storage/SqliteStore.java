package Zenvibe.storage;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Embedded, transactional storage. SQL is loaded exclusively from classpath .sql resources. */
public final class SqliteStore implements AutoCloseable {
    private static final Set<String> CATEGORIES = Set.of("BlockedChannels", "DJRoles", "DJUsers");
    private static final String LEGACY_MIGRATION = "legacy-json-v1";
    private final Connection connection;
    private final Map<String, String> statements = new java.util.HashMap<>();

    public SqliteStore(Path databaseFile, Path legacyDirectory) {
        try {
            Path absolute = databaseFile.toAbsolutePath();
            Files.createDirectories(absolute.getParent());
            // Explicit loading also supports running from the shaded application JAR.
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + absolute);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to open local SQLite database: " + databaseFile, exception);
        }
        try {
            script("connection");
            transaction(() -> { script("schema"); return null; });
            migrateLegacy(legacyDirectory);
        } catch (RuntimeException | SQLException exception) {
            try { connection.close(); } catch (SQLException suppressed) { exception.addSuppressed(suppressed); }
            throw new IllegalStateException("Unable to initialize SQLite storage", exception);
        }
    }

    private String sql(String name) {
        return statements.computeIfAbsent(name, key -> {
            try (var stream = SqliteStore.class.getResourceAsStream("/sql/" + key + ".sql")) {
                if (stream == null) throw new IOException("Missing SQL resource: " + key);
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to load SQL resource " + key, exception);
            }
        });
    }

    private void script(String name) throws SQLException {
        // These resources contain simple DDL/PRAGMA statements, without SQL triggers or string semicolons.
        try (var statement = connection.createStatement()) {
            for (String command : sql(name).split(";")) {
                if (!command.isBlank()) statement.execute(command);
            }
        }
    }

    private PreparedStatement prepare(String name, Object... parameters) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql(name));
        try {
            for (int index = 0; index < parameters.length; index++) statement.setObject(index + 1, parameters[index]);
            return statement;
        } catch (SQLException exception) {
            statement.close();
            throw exception;
        }
    }

    private void execute(String name, Object... parameters) throws SQLException {
        try (var statement = prepare(name, parameters)) { statement.executeUpdate(); }
    }

    private interface Work<T> { T run() throws Exception; }

    private <T> T transaction(Work<T> work) {
        try {
            connection.setAutoCommit(false);
            try {
                T result = work.run();
                connection.commit();
                return result;
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("SQLite transaction failed", exception);
        }
    }

    public synchronized JSONObject guild(long guildId) {
        return transaction(() -> {
            execute("guild-create", Long.toString(guildId));
            JSONObject result = new JSONObject();
            for (String category : CATEGORIES) result.put(category, new JSONArray());
            try (var statement = prepare("guild-read", Long.toString(guildId)); var rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("Missing guild after creation");
                result.put("Locale", rows.getString("locale"));
            }
            try (var statement = prepare("memberships-read", Long.toString(guildId)); var rows = statement.executeQuery()) {
                while (rows.next()) ((JSONArray) result.get(rows.getString("category"))).add(rows.getString("subject_id"));
            }
            return result;
        });
    }

    public synchronized void setLocale(long guildId, String locale) {
        if (locale == null || locale.isBlank()) throw new IllegalArgumentException("Locale must not be blank");
        transaction(() -> {
            execute("guild-create", Long.toString(guildId));
            execute("guild-locale", locale, Long.toString(guildId));
            return null;
        });
    }

    /** One transaction for all role/user updates from a command. */
    public synchronized void changeMemberships(long guildId, Map<String, ? extends Collection<?>> changes, boolean add) {
        transaction(() -> {
            execute("guild-create", Long.toString(guildId));
            for (var entry : changes.entrySet()) {
                if (!CATEGORIES.contains(entry.getKey())) throw new IllegalArgumentException("Unknown membership category");
                for (Object value : entry.getValue()) {
                    execute(add ? "membership-add" : "membership-remove", Long.toString(guildId), entry.getKey(), snowflake(value));
                }
            }
            return null;
        });
    }

    public synchronized void deleteGuild(long guildId) {
        transaction(() -> { execute("guild-delete", Long.toString(guildId)); return null; });
    }

    public synchronized String lastFmSession(String userId) {
        return transaction(() -> {
            try (var statement = prepare("lastfm-read", userId); var rows = statement.executeQuery()) {
                return rows.next() ? rows.getString("session_key") : null;
            }
        });
    }

    public synchronized long lastFmUserCount() {
        return transaction(() -> {
            try (var statement = prepare("lastfm-count"); var rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("Missing session count");
                return rows.getLong(1);
            }
        });
    }

    public synchronized void saveLastFmSession(String userId, String sessionKey) {
        java.util.Objects.requireNonNull(sessionKey);
        transaction(() -> { execute("lastfm-save", snowflake(userId), sessionKey); return null; });
    }

    public synchronized void removeLastFmSession(String userId) {
        transaction(() -> { execute("lastfm-remove", userId); return null; });
    }

    public synchronized Map<String, Long> usage() {
        return transaction(() -> {
            Map<String, Long> result = new LinkedHashMap<>();
            try (var statement = prepare("usage-read"); var rows = statement.executeQuery()) {
                while (rows.next()) result.put(rows.getString("metric"), rows.getLong("value"));
            }
            return result;
        });
    }

    public synchronized void ensureUsageMetric(String metric) {
        transaction(() -> { execute("usage-create", metric, 0L); return null; });
    }

    public synchronized void recordCommand(String command, String source) {
        transaction(() -> {
            execute("usage-increment", command);
            execute("usage-increment", source);
            return null;
        });
    }

    public synchronized void saveBytesSent(long bytes) {
        transaction(() -> { execute("usage-save", "totalBytesSent", bytes); return null; });
    }

    private void migrateLegacy(Path directory) {
        transaction(() -> {
            try (var statement = prepare("migration-exists", LEGACY_MIGRATION); var rows = statement.executeQuery()) {
                if (rows.next()) return null;
            }
            List<Path> files;
            if (Files.exists(directory)) {
                try (var listing = Files.list(directory)) {
                    files = listing.filter(path -> path.getFileName().toString().endsWith(".json"))
                            .sorted().toList();
                }
            } else files = List.of();
            for (Path file : files) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    Object parsed = new JSONParser().parse(reader);
                    if (!(parsed instanceof JSONObject object)) throw new IllegalArgumentException("Expected an object");
                    String name = file.getFileName().toString().replaceFirst("\\.json$", "");
                    importConfig(name, object);
                } catch (Exception exception) {
                    // Do not include token values or parser exceptions in logs.
                    throw new IOException("Cannot migrate " + file.getFileName() + "; no legacy data was committed. Fix this file and restart.");
                }
            }
            execute("migration-record", LEGACY_MIGRATION);
            return null;
        });
    }

    private void importConfig(String name, JSONObject config) throws SQLException {
        if (name.matches("[0-9]+")) {
            String id = snowflake(name);
            boolean exists;
            try (var statement = prepare("guild-read", id); var rows = statement.executeQuery()) { exists = rows.next(); }
            if (exists) return; // Existing SQLite data always wins over an old file.
            for (Object key : config.keySet()) {
                // Removed feature left empty arrays in some live guild files.
                if ("announcementChannels".equals(key)) continue;
                if (!key.equals("Locale") && !CATEGORIES.contains(key)) throw new IllegalArgumentException("Unknown guild setting");
            }
            execute("guild-create", id);
            Object locale = config.getOrDefault("Locale", "english");
            if (!(locale instanceof String language) || language.isBlank()) throw new IllegalArgumentException("Invalid locale");
            execute("guild-locale", locale, id);
            for (String category : CATEGORIES) {
                Object values = config.getOrDefault(category, new JSONArray());
                if (!(values instanceof JSONArray array)) throw new IllegalArgumentException("Invalid membership list");
                for (Object value : array) execute("membership-add", id, category, snowflake(value));
            }
        } else if (name.equals("lastfm")) {
            for (Object key : config.keySet()) {
                Object value = config.get(key);
                if (!(value instanceof String)) throw new IllegalArgumentException("Invalid Last.fm session");
                execute("lastfm-import", snowflake(key), value);
            }
        } else if (name.equals("usage-stats")) {
            for (Object key : config.keySet()) {
                if (!(key instanceof String) || !(config.get(key) instanceof Long)) throw new IllegalArgumentException("Invalid usage counter");
                execute("usage-create", key, config.get(key));
            }
        } else {
            throw new IllegalArgumentException("Unknown legacy config");
        }
    }

    private static String snowflake(Object value) {
        if (!(value instanceof String) && !(value instanceof Long)) throw new IllegalArgumentException("Invalid Discord ID");
        String id = value.toString();
        if (!id.matches("[0-9]+") || Long.parseLong(id) <= 0) throw new IllegalArgumentException("Invalid Discord ID");
        return Long.toString(Long.parseLong(id));
    }

    @Override public synchronized void close() {
        try { connection.close(); } catch (SQLException exception) { throw new IllegalStateException("Cannot close SQLite database", exception); }
    }
}

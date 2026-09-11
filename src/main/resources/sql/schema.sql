CREATE TABLE IF NOT EXISTS guild_settings (guild_id TEXT PRIMARY KEY, locale TEXT NOT NULL DEFAULT 'english');
CREATE TABLE IF NOT EXISTS guild_memberships (guild_id TEXT NOT NULL REFERENCES guild_settings(guild_id) ON DELETE CASCADE, category TEXT NOT NULL CHECK(category IN ('BlockedChannels','DJRoles','DJUsers')), subject_id TEXT NOT NULL, PRIMARY KEY(guild_id, category, subject_id));
CREATE TABLE IF NOT EXISTS lastfm_sessions (user_id TEXT PRIMARY KEY, session_key TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS usage_stats (metric TEXT PRIMARY KEY, value INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS storage_migrations (name TEXT PRIMARY KEY, completed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);

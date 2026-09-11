INSERT INTO guild_settings(guild_id) VALUES (?) ON CONFLICT(guild_id) DO NOTHING;

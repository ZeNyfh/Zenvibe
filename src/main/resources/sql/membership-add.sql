INSERT INTO guild_memberships(guild_id, category, subject_id) VALUES (?, ?, ?) ON CONFLICT DO NOTHING;

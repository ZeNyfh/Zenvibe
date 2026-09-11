INSERT INTO lastfm_sessions(user_id, session_key) VALUES (?, ?) ON CONFLICT DO NOTHING;

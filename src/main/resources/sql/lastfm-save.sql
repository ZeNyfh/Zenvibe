INSERT INTO lastfm_sessions(user_id, session_key) VALUES (?, ?) ON CONFLICT(user_id) DO UPDATE SET session_key = excluded.session_key;

INSERT INTO usage_stats(metric, value) VALUES (?, ?) ON CONFLICT DO NOTHING;

INSERT INTO usage_stats(metric, value) VALUES (?, ?) ON CONFLICT(metric) DO UPDATE SET value = excluded.value;

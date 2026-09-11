INSERT INTO usage_stats(metric, value) VALUES (?, 1) ON CONFLICT(metric) DO UPDATE SET value = value + 1;

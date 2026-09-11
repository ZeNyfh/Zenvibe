package Zenvibe.lavaplayer;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static Zenvibe.Main.botVersion;

final class MetaBrainzClient {
    private static final Object RATE_LOCK = new Object();
    private static long lastReq;
    private static volatile long mbCooldownUntil;
    private static int consecutiveMbFailures;

    private MetaBrainzClient() {
    }

    static JsonBrowser postJson(String url, String body) {
        String raw = http("POST", url, body);
        try {
            return raw == null ? null : JsonBrowser.parse(raw);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    static JsonBrowser getJson(String url) {
        String raw = http("GET", url, null);
        try {
            return raw == null ? null : JsonBrowser.parse(raw);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** False while MusicBrainz is in a cooldown after 503/429 overload. */
    static boolean musicBrainzAvailable() {
        return System.currentTimeMillis() >= mbCooldownUntil;
    }

    static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static String text(JsonBrowser node, String key) {
        if (node == null) return "";
        String v = node.get(key).text();
        return v == null ? "" : v.trim();
    }

    private static String http(String method, String url, String body) {
        boolean musicBrainz = url.contains("musicbrainz.org");
        if (musicBrainz && !musicBrainzAvailable()) {
            return null;
        }

        for (int attempt = 0; attempt < 3; attempt++) {
            throttle(musicBrainz ? 1500 : 1100);
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) URI.create(url).toURL().openConnection();
                c.setRequestMethod(method);
                c.setConnectTimeout(10_000);
                c.setReadTimeout(15_000);
                c.setRequestProperty("User-Agent", "Zenvibe/" + botVersion + " (https://github.com/ZeNyfh/Zenvibe)");
                c.setRequestProperty("Accept", "application/json");
                if (body != null) {
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                    try (OutputStream out = c.getOutputStream()) {
                        out.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                }
                int code = c.getResponseCode();
                if (code == 503 || code == 429) {
                    noteMbFailure(musicBrainz, code, url);
                    if (!musicBrainzAvailable()) {
                        return null;
                    }
                    sleep(1500L * (attempt + 1));
                    continue;
                }
                if (code / 100 != 2) {
                    System.err.println("MetaBrainz " + method + " " + url + " -> HTTP " + code);
                    return null;
                }
                noteMbSuccess(musicBrainz);
                try (BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    for (String line; (line = in.readLine()) != null; ) sb.append(line);
                    return sb.toString();
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (attempt == 2) return null;
                sleep(1000L * (attempt + 1));
            } finally {
                if (c != null) c.disconnect();
            }
        }
        return null;
    }

    private static void noteMbFailure(boolean musicBrainz, int code, String url) {
        if (!musicBrainz) {
            System.err.println("MetaBrainz GET " + url + " -> HTTP " + code);
            return;
        }
        consecutiveMbFailures++;
        System.err.println("MusicBrainz HTTP " + code + " (failure " + consecutiveMbFailures + ")");
        if (consecutiveMbFailures >= 2) {
            mbCooldownUntil = System.currentTimeMillis() + 60_000L;
            consecutiveMbFailures = 0;
            System.err.println("MusicBrainz overloaded; skipping further MB requests for 60s (ytsearch fallback).");
        }
    }

    private static void noteMbSuccess(boolean musicBrainz) {
        if (musicBrainz) {
            consecutiveMbFailures = 0;
        }
    }

    private static void throttle(long minGapMs) {
        synchronized (RATE_LOCK) {
            long wait = minGapMs - (System.currentTimeMillis() - lastReq);
            if (wait > 0) {
                sleep(wait);
            }
            lastReq = System.currentTimeMillis();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

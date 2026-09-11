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
        throttle();
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
            if (c.getResponseCode() / 100 != 2) {
                System.err.println("MetaBrainz " + method + " " + url + " -> HTTP " + c.getResponseCode());
                return null;
            }
            try (BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                for (String line; (line = in.readLine()) != null; ) sb.append(line);
                return sb.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static void throttle() {
        synchronized (RATE_LOCK) {
            long wait = 1100 - (System.currentTimeMillis() - lastReq);
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastReq = System.currentTimeMillis();
        }
    }
}

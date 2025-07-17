package Zenvibe.lavaplayer;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static Zenvibe.lavaplayer.LastFMManager.filterMetadata;

public class LRCLIBManager {
    public static String getLyrics(AudioTrack track) {
        if (track.getInfo().title == null || track.getInfo().title.equalsIgnoreCase("unknown title")) {
            return "";
        }
        String url = createURL(track);
        if (url.isEmpty()) {
            return "";
        }

        try {
            URL requestURL = URI.create(url).toURL();
            HttpURLConnection connection = (HttpURLConnection) requestURL.openConnection();
            connection.setRequestMethod("GET");

            StringBuilder responseBuilder = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line);
            }
            String response = responseBuilder.toString();
            if (response.equals("[]")) {
                return "";
            }

            String lyrics = parseLyrics(response);
            if (lyrics == null || lyrics.equalsIgnoreCase("null")) {
                return "";
            }
            return lyrics;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private static String createURL(AudioTrack track) {
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append("https://lrclib.net/api/search?q=");

        String title = track.getInfo().title;
        if (track.getInfo().isStream && Objects.equals(track.getSourceManager().getSourceName(), "http")) {
            title = RadioDataFetcher.getStreamSongNow(track.getInfo().uri)[0];
        }

        title = filterMetadata(title);

        String artist = track.getInfo().author;
        if (track.getInfo().isStream && Objects.equals(track.getSourceManager().getSourceName(), "http")) {
            artist = "";
        }
        // add stream author/artist here.

        urlBuilder.append(URLEncoder.encode(artist + " " + title, StandardCharsets.UTF_8).trim());
        System.out.println(urlBuilder);
        return urlBuilder.toString();
    }

    private static String parseLyrics(String rawJson) {
        JsonBrowser parsedJson;
        try {
            parsedJson = JsonBrowser.parse(rawJson);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }

        JsonBrowser trackDetailsBrowser = null;
        try {
            trackDetailsBrowser = parsedJson.values().get(1);
        } catch (Exception ignored) {
            System.err.println("No lyrics were found for this track.");
        }
        if (trackDetailsBrowser == null) {
            return "";
        }
        return trackDetailsBrowser.get("plainLyrics").safeText();
    }
}

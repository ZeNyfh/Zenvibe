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
            if (lyrics == null || lyrics.equalsIgnoreCase("null") || lyrics.isBlank()) {
                return "";
            }
            return lyrics;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /** Catalogue artist - title when MusicBrainz resolves; otherwise filtered lavaplayer metadata. */
    public static String displayName(AudioTrack track) {
        String title = track.getInfo().title;
        String artist = track.getInfo().author == null ? "" : track.getInfo().author;
        if (track.getInfo().isStream && Objects.equals(track.getSourceManager().getSourceName(), "http")) {
            String[] now = RadioDataFetcher.getStreamSongNow(track.getInfo().uri);
            if (now != null && now[0] != null && !now[0].isBlank()) {
                title = now[0];
            }
            artist = "";
        }
        ResolvedRecording resolved = ListenBrainzManager.resolve(artist, title);
        if (resolved != null) {
            return resolved.artist() + " - " + resolved.title();
        }
        title = filterMetadata(title);
        if (artist.isBlank()) {
            return title;
        }
        return artist + " - " + title;
    }

    private static String createURL(AudioTrack track) {
        String title = track.getInfo().title;
        String artist = track.getInfo().author == null ? "" : track.getInfo().author;
        if (track.getInfo().isStream && Objects.equals(track.getSourceManager().getSourceName(), "http")) {
            String[] now = RadioDataFetcher.getStreamSongNow(track.getInfo().uri);
            if (now != null && now[0] != null && !now[0].isBlank()) {
                title = now[0];
            }
            artist = "";
        }

        ResolvedRecording resolved = ListenBrainzManager.resolve(artist, title);
        if (resolved != null) {
            artist = resolved.artist();
            title = resolved.title();
        } else {
            title = filterMetadata(title);
        }

        if (title.isBlank()) {
            return "";
        }

        if (!artist.isBlank()) {
            return "https://lrclib.net/api/search?artist_name="
                    + URLEncoder.encode(artist, StandardCharsets.UTF_8)
                    + "&track_name="
                    + URLEncoder.encode(title, StandardCharsets.UTF_8);
        }
        return "https://lrclib.net/api/search?q="
                + URLEncoder.encode(title, StandardCharsets.UTF_8);
    }

    private static String parseLyrics(String rawJson) {
        JsonBrowser parsedJson;
        try {
            parsedJson = JsonBrowser.parse(rawJson);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }

        if (!parsedJson.isList()) {
            return "";
        }
        for (JsonBrowser row : parsedJson.values()) {
            String lyrics = row.get("plainLyrics").safeText();
            if (lyrics != null && !lyrics.isBlank() && !lyrics.equalsIgnoreCase("null")) {
                return lyrics;
            }
        }
        System.err.println("No lyrics were found for this track.");
        return "";
    }
}

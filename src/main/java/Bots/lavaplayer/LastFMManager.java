package Bots.lavaplayer;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static Bots.Main.autoPlayedTracks;
import static Bots.Main.botVersion;

// Last.fm wish for their API to be used sensibly, I have outlined with comments how it is being used sensibly with attention to their note found at: https://www.last.fm/api/intro
public class LastFMManager {
    public static boolean hasAPI = false;
    private static String APIKEY = null;

    public static void Init() {
        Dotenv dotenv = Dotenv.load();
        String key = dotenv.get("LASTFMTOKEN");
        if (key == null) {
            System.err.println("LASTFMTOKEN is not set in " + new File(".env").getAbsolutePath());
        } else {
            System.out.println("LastFM manager initialised");
            hasAPI = true;
        }
        APIKEY = key;
    }

    public static String getSimilarSongs(AudioTrack track, Long guildID) {
        if (APIKEY == null) {
            return "noapi";
        }

        String songName;
        if (track.getInfo().title.contains("-")) {
            songName = filterMetadata(track.getInfo().title.toLowerCase());
        } else {
            songName = filterMetadata(track.getInfo().title.toLowerCase());
        }
        // TODO: should be replaced with actual logic checking if last.fm has either the author or the artist name in the title.
        String artistName = (track.getInfo().author.isEmpty() || track.getInfo().author == null || track.getInfo().title.contains("-"))
                ? filterMetadata((track.getInfo().title).toLowerCase())
                : (track.getInfo().author).toLowerCase();

        songName = URLEncoder.encode(songName, StandardCharsets.UTF_8);
        artistName = URLEncoder.encode(artistName, StandardCharsets.UTF_8);

        StringBuilder urlStringBuilder = new StringBuilder();
        urlStringBuilder.append("http://ws.audioscrobbler.com/2.0/?method=track.getSimilar&limit=5&autocorrect=1&artist=").append(artistName).append("&track=").append(songName);
        System.out.println(urlStringBuilder); // debug printing but removing the API key from the print.
        urlStringBuilder.append("&api_key=").append(APIKEY).append("&format=json");
        String urlString = urlStringBuilder.toString();

        StringBuilder response = new StringBuilder();
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Zenvibe/" + botVersion); // identifiable User-Agent header as requested by last.fm

            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            }
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }

        if (response.toString().startsWith("{\"error\":6,\"message\":\"Track not found\"") || response.toString().startsWith("{}")) {
            return "notfound";
        }

        String trackToSearch = extractTracks(response.toString(), guildID);
        return trackToSearch.isEmpty() ? "none" : trackToSearch;
    }


    private static final String[] titleFilters = {
        // yt
        "Official Video", "Music Video", "Lyric Video", "Visualizer", "Audio", "Official Audio", "Album Audio",
        "Live", "Live Performance", "HD", "HQ", "4K", "360°", "VR",
        // spotify
        "Official Spotify", "Spotify Singles", "Spotify Session", "Recorded at Spotify Studios",
        "Spotify Exclusive", "Podcast", "Episode", "B-Side", "Session",
        // flags
        "Explicit", "Clean", "Unedited", "Remastered", "Remaster", "Deluxe", "Extended", "Bonus Track", "Cover",
        "Acoustic", "Instrumental", "Radio Edit", "Reissue", "Anniversary Edition",
        // tags
        "VEVO", "YouTube", "YT", "Streaming", "Stream",
        // decorators
        "With Lyrics", "Lyrics", "ft.", "feat.", "featuring", "vs.", "x", "Official", "Original", "Version",
        "Edit", "Mix", "Mashup",
        // release
        "Album Version", "Single Version", "EP Version",
        // misc
        "||", "▶", "❌", "●", "...", "---", "•••", "FREE DOWNLOAD", "OUT NOW", "NEW"
    };

    private static final String[] rawTitleFilters = {
        // yt
        "OFFICIAL LYRIC VIDEO", "Music Video", "Lyric Video", "Official Audio", "Album Audio", "Live Performance",
        "HD", "HQ", "4K", "360°", "VR",
        // spotify
        "Official Spotify", "Spotify Singles", "Spotify Session", "Recorded at Spotify Studios",
        "Spotify Exclusive",
        // flags
        "Explicit", "Unedited", "Remastered", "Remaster", "Extended", "Bonus Track", "Acoustic", "Instrumental",
        "Radio Edit", "Reissue", "Anniversary Edition",
        // tags
        "VEVO", "YouTube", "YT", "Streaming", "Stream",
        // decorators
        "With Lyrics", "Lyrics", "ft.", "feat.", "featuring", "vs.", "x", "Official", "Original", "Version",
        "Edit", "Mix", "Mashup",
        // release
        "Album Version", "Single Version", "EP Version",
        // misc
        "||", "▶", "❌", "●", "...", "---", "•••", "FREE DOWNLOAD", "OUT NOW", "NEW"
    };

    private static final Map<String, String> equivalentChars = new HashMap<>() {{
        put("—", "-");
        put("–", "-");
        put("‐", "-");
        put("⁃", "-");
        put("⸺", "-");
        put("…", "...");
        put("･", ".");
        put("•", ".");
        put("․", ".");
        put("⋅", ".");
        put("∙", ".");
    }};


    public static String filterMetadata(String track) {
        Pattern bracketContent = Pattern.compile("(?i)[(\\[{<«【《『„](.*)[)\\]}>»】》』“]");
        Matcher matcher = bracketContent.matcher(track);
        System.out.println(track);

        for (Map.Entry<String, String> entry : equivalentChars.entrySet()) {
            track = track.replace(entry.getKey(), entry.getValue());
        }

        if (matcher.find()) {
            String bracketContentString = matcher.group(1).toLowerCase();
            for (String filter : titleFilters) {
                if (bracketContentString.contains(filter.toLowerCase())) {
                    track = matcher.replaceAll("");
                }
            }
        }

        for (String filter : rawTitleFilters) {
            if (track.toLowerCase().contains(filter.toLowerCase())) {
                track = track.replace(filter, "");
            }
        }

        System.out.println(track);
        return track.trim();
    }

    private static String extractTracks(String rawJson, long guildID) {
        JsonBrowser parsedJson;
        try {
            parsedJson = JsonBrowser.parse(rawJson);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }

        JsonBrowser trackInfoArray = parsedJson.get("similartracks").get("track").index(0);
        String artistName = trackInfoArray.get("artist").get("name").text();
        String songName = trackInfoArray.get("name").text();

        StringBuilder builder = new StringBuilder();
        builder.append(artistName).append(" - ").append(songName);

        if (autoPlayedTracks.get(guildID).contains(builder.toString())) {
            builder.setLength(0);
        } else {
            List<String> list = autoPlayedTracks.get(guildID);
            list.add(builder.toString().toLowerCase());
            autoPlayedTracks.put(guildID, list);
        }
        return builder.toString();
    }
}

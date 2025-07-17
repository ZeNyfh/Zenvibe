package Zenvibe.lavaplayer;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.entities.Guild;
import org.json.simple.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static Zenvibe.Main.*;
import static Zenvibe.lavaplayer.RadioDataFetcher.getStreamSongNow;
import static Zenvibe.managers.GuildDataManager.GetConfig;

// Last.fm wish for their API to be used sensibly; I have outlined with comments how it is being used sensibly with attention to their note found at: https://www.last.fm/api/intro
public class LastFMManager {
    private static final String APIURL = "https://ws.audioscrobbler.com/2.0/";
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
    public static boolean hasAPI = false;
    public static JSONObject sessionKeys = GetConfig("lastfm");
    private static String APIKEY = null;
    private static String LASTFMSECRET = null;

    public static void Init() {
        Dotenv dotenv = Dotenv.load();
        String key = dotenv.get("LASTFMTOKEN");
        String secret = dotenv.get("LASTFMSECRET");
        if (key == null) {
            System.err.println("LASTFMTOKEN is not set in " + new File(".env").getAbsolutePath());
        } else {
            System.out.println("LastFM manager initialised");
            hasAPI = true;
        }
        if (secret == null) {
            System.err.println("LASTFMSECRET is not set in " + new File(".env").getAbsolutePath());
        }
        LASTFMSECRET = secret;
        APIKEY = key;
    }

    public static String getSimilarSongs(AudioTrack track, Long guildID) {
        if (APIKEY == null) {
            return "noapi";
        }

        String songName = filterMetadata(track.getInfo().title.toLowerCase());
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
            URL url = URI.create(urlString).toURL();
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

    public static String filterMetadata(String track) {
        Pattern bracketContent = Pattern.compile("(?i)[(\\[{<«【《『„](.*)[)\\]}>»】》』“]");
        Matcher matcher = bracketContent.matcher(track);

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

        return track.trim();
    }

    public static void scrobble(AudioTrack track, String userID) throws Exception {
        if (APIKEY == null) {
            throw new IllegalStateException("APIKEY is null, cannot scrobble.");
        }
        String chosenByUser = "0"; // not chosen by the user

        PlayerManager.TrackData trackData = (PlayerManager.TrackData) track.getUserData();
        Guild guild = getGuildChannelFromID(trackData.channelId).getGuild();
        if (((PlayerManager.TrackData) track.getUserData()).username.equalsIgnoreCase(Objects.requireNonNull(guild.getMemberById(userID)).getEffectiveName())) {
            chosenByUser = "1"; // chosen by the user
        }

        String artistName = track.getInfo().author;
        String songName = filterMetadata(track.getInfo().title);

        if (track.getInfo().isStream && songName.isEmpty()) {
            songName = getStreamSongNow(track.getInfo().uri)[0];
        }

        if (songName.isEmpty() || artistName.isEmpty()) {
            return;
        }

        if (songName.contains("-")) {
            songName = songName.split("-", 2)[1].trim();
        }

        String method = "track.scrobble";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String duration = String.valueOf(track.getDuration() / 1000);
        String format = "json";

        String sessionKey = sessionKeys.get(userID).toString();

        TreeMap<String, String> params = new TreeMap<>();
        params.put("api_key", APIKEY);
        params.put("artist", artistName);
        params.put("chosenByUser", chosenByUser);
        params.put("duration", duration);
        params.put("method", method);
        params.put("sk", sessionKey);
        params.put("timestamp", timestamp);
        params.put("track", songName);

        // Generate API signature
        StringBuilder sigBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            sigBuilder.append(entry.getKey()).append(entry.getValue());
        }
        sigBuilder.append(LASTFMSECRET);

        String apiSignature = getMD5Hash(sigBuilder.toString());

        // Prepare POST data
        StringBuilder postData = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!postData.isEmpty()) postData.append("&");
            postData.append(entry.getKey()).append("=").append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        postData.append("&api_sig=").append(apiSignature);
        postData.append("&format=").append(format);

        HttpURLConnection conn = (HttpURLConnection) URI.create(APIURL).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("User-Agent", "Zenvibe/" + botVersion);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Content-Length", String.valueOf(postData.length()));
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(postData.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code != 200) {
            StringBuilder errorResponse = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    errorResponse.append(line);
                }
            }
            throw new Exception("Scrobble failed, HTTP code: " + code + ", Response: " + errorResponse);
        }
    }

    private static String getMD5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getMD5Sign(String... strings) {
        TreeMap<String, String> params = new TreeMap<>();
        for (int i = 0; i < strings.length; i += 2) {
            params.put(strings[i], strings[i + 1]);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            sb.append(entry.getKey()).append(entry.getValue());
        }
        sb.append(LASTFMSECRET);

        return getMD5Hash(sb.toString());
    }


    public static String fetchRequestToken() throws Exception { // creates unauthorised session token
        String method = "auth.getToken";
        String url = APIURL + "?method=" + method
                + "&api_key=" + APIKEY
                + "&api_sig=" + getMD5Sign("api_key", APIKEY, "method", method)
                + "&format=json";

        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Zenvibe/" + botVersion);

        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder resp = new StringBuilder();
            String line;

            while ((line = in.readLine()) != null) resp.append(line);
            JsonBrowser browser = JsonBrowser.parse(resp.toString());
            return browser.get("token").safeText();
        }
    }

    public static String fetchUserAuthorisation(String unauthToken) { // url for prompting user to authorise token
        return "http://www.last.fm/api/auth/?api_key=" + APIKEY + "&token=" + unauthToken;
    }

    public static String fetchWebServiceSession(String token) throws Exception { //
        String method = "auth.getSession";

        String url = APIURL + "?method=" + method
                + "&api_key=" + APIKEY
                + "&api_sig=" + getMD5Sign("api_key", APIKEY, "method", method, "token", token)
                + "&token=" + token
                + "&format=json";

        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Zenvibe/" + botVersion);

        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder resp = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) resp.append(line);
            JsonBrowser browser = JsonBrowser.parse(resp.toString());
            return browser.get("session").get("key").safeText();
        }
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

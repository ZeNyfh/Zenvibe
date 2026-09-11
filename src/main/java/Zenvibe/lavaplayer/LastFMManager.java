package Zenvibe.lavaplayer;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static Zenvibe.Main.*;
import static Zenvibe.lavaplayer.RadioDataFetcher.getStreamSongNow;
import Zenvibe.managers.GuildDataManager;

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
    private static String APIKEY = null;
    private static String LASTFMSECRET = null;
    private static final Set<String> pendingAuthPolls = ConcurrentHashMap.newKeySet();
    private static final long AUTH_POLL_MS = 5_000L;
    private static final long AUTH_TIMEOUT_MS = 10 * 60_000L;

    public static void Init() {
        Dotenv dotenv = loadEnvironment();
        String key = getEnvironmentValue(dotenv, "LASTFMTOKEN");
        String secret = getEnvironmentValue(dotenv, "LASTFMSECRET");
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

    public static void vcScrobble(AudioChannelUnion channel, AudioTrack track) {
        forEachScrobblingMember(channel, (userId) -> scrobble(track, userId));
    }

    public static void vcUpdateNowPlaying(AudioChannelUnion channel, AudioTrack track) {
        forEachScrobblingMember(channel, (userId) -> updateNowPlaying(track, userId));
    }

    private static void forEachScrobblingMember(AudioChannelUnion channel, LastFmTrackAction action) {
        for (Member member : Objects.requireNonNull(channel).getMembers()) {
            String session = GuildDataManager.database().lastFmSession(member.getId());
            if (session != null && !session.startsWith("REQUEST")) {
                try {
                    action.run(member.getId());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @FunctionalInterface
    private interface LastFmTrackAction {
        void run(String userId) throws Exception;
    }

    public static void scrobble(AudioTrack track, String userID) throws Exception {
        TreeMap<String, String> params = buildAuthenticatedTrackParams(track, userID, "track.scrobble");
        if (params == null) {
            return;
        }

        String chosenByUser = "0";
        PlayerManager.TrackData trackData = (PlayerManager.TrackData) track.getUserData();
        Guild guild = getGuildChannelFromID(trackData.channelId).getGuild();
        if (trackData.username.equalsIgnoreCase(Objects.requireNonNull(guild.getMemberById(userID)).getEffectiveName())) {
            chosenByUser = "1";
        }

        params.put("chosenByUser", chosenByUser);
        params.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));
        postAuthenticatedMethod(params, "Scrobble");
    }

    public static void updateNowPlaying(AudioTrack track, String userID) throws Exception {
        TreeMap<String, String> params = buildAuthenticatedTrackParams(track, userID, "track.updateNowPlaying");
        if (params == null) {
            return;
        }
        postAuthenticatedMethod(params, "NowPlaying");
    }

    private static TreeMap<String, String> buildAuthenticatedTrackParams(AudioTrack track, String userID, String method) {
        if (APIKEY == null) {
            throw new IllegalStateException("APIKEY is null, cannot call " + method + ".");
        }

        String sessionKey = GuildDataManager.database().lastFmSession(userID);
        if (sessionKey == null || sessionKey.startsWith("REQUEST")) {
            return null;
        }

        String rawArtist = track.getInfo().author == null ? "" : track.getInfo().author;
        String rawTitle = track.getInfo().title == null ? "" : track.getInfo().title;
        if (track.getInfo().isStream) {
            String[] nowPlaying = getStreamSongNow(track.getInfo().uri);
            if (nowPlaying != null && nowPlaying[0] != null && !nowPlaying[0].isBlank()) {
                rawTitle = nowPlaying[0];
            }
        }

        String artistName;
        String songName;
        ResolvedRecording resolved = ListenBrainzManager.resolve(rawArtist, rawTitle);
        if (resolved != null) {
            artistName = resolved.artist();
            songName = resolved.title();
        } else {
            List<TrackMetadataCandidate> candidates = TrackMetadataParser.candidates(rawArtist, rawTitle);
            if (!candidates.isEmpty()) {
                TrackMetadataCandidate best = candidates.getFirst();
                artistName = best.artist();
                songName = best.title();
            } else {
                artistName = rawArtist;
                songName = filterMetadata(rawTitle);
            }
        }

        if (songName.isEmpty() || artistName == null || artistName.isEmpty()) {
            return null;
        }

        TreeMap<String, String> params = new TreeMap<>();
        params.put("api_key", APIKEY);
        params.put("artist", artistName);
        params.put("method", method);
        params.put("sk", sessionKey);
        params.put("track", songName);

        // Generate API signature
        long durationSeconds = track.getDuration() / 1000;
        if (durationSeconds > 0 && durationSeconds < 86400) {
            params.put("duration", String.valueOf(durationSeconds));
        }
        return params;
    }

    private static void postAuthenticatedMethod(TreeMap<String, String> params, String actionLabel) throws Exception {
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
        postData.append("&format=json");

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
            throw new Exception(actionLabel + " failed, HTTP code: " + code + ", Response: " + errorResponse);
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

    /**
     * Polls {@code auth.getSession} until the user finishes the Last.fm auth page (or timeout).
     * On success the REQUEST token is replaced with a real session key.
     */
    public static void pollAuthorisation(String userId, String token, Runnable onSuccess, Runnable onTimeout) {
        if (!pendingAuthPolls.add(userId)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                long deadline = System.currentTimeMillis() + AUTH_TIMEOUT_MS;
                while (System.currentTimeMillis() < deadline) {
                    Thread.sleep(AUTH_POLL_MS);
                    String saved = GuildDataManager.database().lastFmSession(userId);
                    if (saved == null || !saved.equals("REQUEST" + token)) {
                        return;
                    }
                    try {
                        String session = tryFetchWebServiceSession(token);
                        if (session != null) {
                            GuildDataManager.database().saveLastFmSession(userId, session);
                            onSuccess.run();
                            return;
                        }
                    } catch (Exception e) {
                        System.err.println("Last.fm auth poll error for " + userId + ": " + e.getMessage());
                    }
                }
                String saved = GuildDataManager.database().lastFmSession(userId);
                if (saved != null && saved.equals("REQUEST" + token)) {
                    GuildDataManager.database().removeLastFmSession(userId);
                }
                onTimeout.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                pendingAuthPolls.remove(userId);
            }
        });
    }

    /** @return session key, or {@code null} if the token is not authorised yet */
    public static String tryFetchWebServiceSession(String token) throws Exception {
        String method = "auth.getSession";

        String url = APIURL + "?method=" + method
                + "&api_key=" + APIKEY
                + "&api_sig=" + getMD5Sign("api_key", APIKEY, "method", method, "token", token)
                + "&token=" + token
                + "&format=json";

        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Zenvibe/" + botVersion);

        StringBuilder resp = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) resp.append(line);
        }
        JsonBrowser browser = JsonBrowser.parse(resp.toString());
        if (!browser.get("error").isNull()) {
            long code = browser.get("error").asLong(0);
            if (code == 14) { // token not authorised yet
                return null;
            }
            throw new Exception("auth.getSession error " + code + ": " + browser.get("message").safeText());
        }
        String key = browser.get("session").get("key").safeText();
        return key == null || key.isBlank() ? null : key;
    }

    public static String fetchWebServiceSession(String token) throws Exception {
        String key = tryFetchWebServiceSession(token);
        if (key == null) {
            throw new Exception("This token has not been authorized");
        }
        return key;
    }
}

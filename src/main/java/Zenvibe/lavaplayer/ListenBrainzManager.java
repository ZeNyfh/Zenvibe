package Zenvibe.lavaplayer;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static Zenvibe.Main.autoPlayedTracks;
import static Zenvibe.lavaplayer.MetaBrainzClient.enc;
import static Zenvibe.lavaplayer.MetaBrainzClient.getJson;
import static Zenvibe.lavaplayer.MetaBrainzClient.jsonString;
import static Zenvibe.lavaplayer.MetaBrainzClient.postJson;
import static Zenvibe.lavaplayer.MetaBrainzClient.text;

public final class ListenBrainzManager {
    public static final int AUTOPLAY_BATCH = 10;

    private static final String LABS = "https://labs.api.listenbrainz.org";
    private static final String MB = "https://musicbrainz.org/ws/2";
    private static final String SIMILAR_ALGO =
            "session_based_days_7500_session_300_contribution_5_threshold_15_limit_50_skip_30";

    private static final Map<String, ResolvedRecording> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> URL_CACHE = new ConcurrentHashMap<>();

    private ListenBrainzManager() {
    }

    /** @return load query, or {@code "notfound"} / {@code "none"} / {@code ""} */
    public static String getSimilarSongs(AudioTrack track, long guildID) {
        ResolvedRecording seed = resolve(track);
        if (seed == null) return "notfound";

        List<AutoplayTarget> songs = collectSimilar(seed, guildID, 1);
        if (songs == null) return "";
        if (songs.isEmpty()) return "none";
        return songs.getFirst().toLoadQuery(PlayerManager.hasSpotify());
    }

    public static List<AutoplayTarget> getSimilarTargets(AudioTrack track, long guildID, int limit) {
        ResolvedRecording seed = resolve(track);
        if (seed == null) return List.of();
        List<AutoplayTarget> songs = collectSimilar(seed, guildID, limit);
        return songs == null ? List.of() : songs;
    }

    private static List<AutoplayTarget> collectSimilar(ResolvedRecording seed, long guildID, int limit) {
        remember(guildID, seed.mbid());

        JsonBrowser similar = getJson(LABS + "/similar-recordings/json?recording_mbids="
                + enc(seed.mbid()) + "&algorithm=" + enc(SIMILAR_ALGO));
        if (similar == null || !similar.isList()) return null;

        List<String> played = autoPlayedTracks.get(guildID);
        List<AutoplayTarget> out = new ArrayList<>(Math.max(1, limit));
        for (JsonBrowser row : similar.values()) {
            if (out.size() >= limit) break;
            String mbid = text(row, "recording_mbid");
            String artist = text(row, "artist_credit_name");
            String title = text(row, "recording_name");
            if (mbid.isEmpty() || artist.isEmpty() || title.isEmpty()) continue;
            if (played != null && played.contains(mbid)) continue;
            remember(guildID, mbid);
            out.add(new AutoplayTarget(artist, title, mbid, fetchExternalUrls(mbid)));
        }
        return out;
    }

    /** MusicBrainz recording URL relations (Spotify / YouTube / SoundCloud / …), cached per MBID. */
    static List<String> fetchExternalUrls(String mbid) {
        if (mbid == null || mbid.isBlank()) return List.of();
        List<String> cached = URL_CACHE.get(mbid);
        if (cached != null) return cached;
        if (!MetaBrainzClient.musicBrainzAvailable()) return List.of();

        // UUID path segment — do not form-encode (and skip entirely while MB is cooling down).
        JsonBrowser root = getJson(MB + "/recording/" + mbid + "?inc=url-rels&fmt=json");
        if (root == null) {
            return List.of(); // 503/error — do not cache empty as "no urls"
        }

        List<String> urls = new ArrayList<>();
        JsonBrowser relations = root.get("relations");
        if (relations.isList()) {
            for (JsonBrowser rel : relations.values()) {
                String type = text(rel, "type").toLowerCase(Locale.ROOT);
                if (!(type.contains("stream") || type.contains("download") || type.equals("youtube"))) {
                    continue;
                }
                String resource = text(rel.get("url"), "resource");
                if (resource.isEmpty()) continue;
                String lower = resource.toLowerCase(Locale.ROOT);
                if (lower.contains("spotify.com/")
                        || lower.contains("youtube.com/")
                        || lower.contains("youtu.be/")
                        || lower.contains("soundcloud.com/")
                        || lower.contains("bandcamp.com/")) {
                    urls.add(resource);
                }
            }
        }
        List<String> frozen = AutoplayTarget.mergeUrls(urls);
        URL_CACHE.put(mbid, frozen);
        return frozen;
    }

    public static String resolveRecordingMbid(AudioTrack track) {
        ResolvedRecording r = resolve(track);
        return r == null ? null : r.mbid();
    }

    public static ResolvedRecording resolveTrack(AudioTrack track) {
        return resolve(track);
    }

    static ResolvedRecording resolve(AudioTrack track) {
        return resolve(nz(track.getInfo().author), nz(track.getInfo().title));
    }

    static ResolvedRecording resolve(String rawArtist, String rawTitle) {
        String key = cacheKey(rawArtist, rawTitle);
        ResolvedRecording hit = CACHE.get(key);
        if (hit != null) return hit;

        List<TrackMetadataCandidate> top = TrackMetadataParser.candidates(rawArtist, rawTitle).stream()
                .limit(3)
                .toList();

        for (TrackMetadataCandidate c : top) {
            ResolvedRecording r = CACHE.get(c.key());
            if (r != null) {
                CACHE.put(key, r);
                return r;
            }
            r = acrLookup(c);
            if (r != null) {
                cache(key, c, r);
                return r;
            }
        }
        for (TrackMetadataCandidate c : top) {
            ResolvedRecording r = recordingSearch(c);
            if (r != null) {
                cache(key, c, r);
                return r;
            }
        }
        for (TrackMetadataCandidate c : top) {
            ResolvedRecording r = musicBrainzSearch(c);
            if (r != null) {
                cache(key, c, r);
                return r;
            }
        }
        return null;
    }

    private static ResolvedRecording acrLookup(TrackMetadataCandidate c) {
        String body = "[{\"artist_credit_name\":" + jsonString(c.artist())
                + ",\"recording_name\":" + jsonString(c.title()) + "}]";
        return firstResolved(postJson(LABS + "/acr-lookup/json", body));
    }

    private static ResolvedRecording recordingSearch(TrackMetadataCandidate c) {
        JsonBrowser list = postJson(LABS + "/recording-search/json",
                "[{\"query\":" + jsonString(c.artist() + " " + c.title()) + "}]");
        if (list == null || !list.isList()) return null;

        ResolvedRecording best = null;
        double bestScore = 0;
        for (JsonBrowser row : list.values()) {
            String artist = text(row, "artist_credit_name");
            String title = text(row, "recording_name");
            String mbid = text(row, "recording_mbid");
            if (mbid.isEmpty()) continue;
            double score = TrackMetadataParser.similarity(c.artist(), artist) * 0.45
                    + TrackMetadataParser.similarity(c.title(), title) * 0.55;
            if (score > bestScore) {
                bestScore = score;
                best = new ResolvedRecording(artist, title, mbid);
            }
        }
        return bestScore >= 0.85 ? best : null;
    }

    private static ResolvedRecording musicBrainzSearch(TrackMetadataCandidate c) {
        String q = "recording:\"" + c.title().replace("\"", "\\\"") + "\" AND artist:\""
                + c.artist().replace("\"", "\\\"") + "\"";
        JsonBrowser root = getJson(MB + "/recording/?query=" + enc(q) + "&fmt=json&limit=5");
        if (root == null) return null;
        JsonBrowser recs = root.get("recordings");
        if (!recs.isList()) return null;

        ResolvedRecording best = null;
        double bestScore = 0;
        for (JsonBrowser rec : recs.values()) {
            String mbid = text(rec, "id");
            String title = text(rec, "title");
            String artist = artistCredit(rec.get("artist-credit"));
            if (mbid.isEmpty() || title.isEmpty() || artist.isEmpty()) continue;
            double mb = 0;
            try {
                mb = Double.parseDouble(text(rec, "score")) / 100.0;
            } catch (Exception ignored) {
            }
            double local = TrackMetadataParser.similarity(c.artist(), artist) * 0.45
                    + TrackMetadataParser.similarity(c.title(), title) * 0.55;
            double score = mb * 0.55 + local * 0.45;
            if (score > bestScore) {
                bestScore = score;
                best = new ResolvedRecording(artist, title, mbid);
            }
        }
        return bestScore >= 0.8 ? best : null;
    }

    private static ResolvedRecording firstResolved(JsonBrowser list) {
        if (list == null || !list.isList() || list.values().isEmpty()) return null;
        JsonBrowser row = list.index(0);
        String mbid = text(row, "recording_mbid");
        String artist = text(row, "artist_credit_name");
        String title = text(row, "recording_name");
        if (mbid.isEmpty() || artist.isEmpty() || title.isEmpty()) return null;
        return new ResolvedRecording(artist, title, mbid);
    }

    private static String artistCredit(JsonBrowser credits) {
        if (credits == null || !credits.isList()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonBrowser c : credits.values()) {
            String name = text(c, "name");
            if (name.isEmpty()) name = text(c.get("artist"), "name");
            sb.append(name);
            String join = c.get("joinphrase").text();
            if (join != null) sb.append(join);
        }
        return sb.toString().trim();
    }

    private static void cache(String inputKey, TrackMetadataCandidate c, ResolvedRecording r) {
        CACHE.put(inputKey, r);
        CACHE.put(c.key(), r);
        CACHE.put(cacheKey(r.artist(), r.title()), r);
    }

    private static void remember(long guildID, String mbid) {
        List<String> list = autoPlayedTracks.get(guildID);
        if (list != null && !list.contains(mbid)) list.add(mbid);
    }

    private static String cacheKey(String artist, String title) {
        return TrackMetadataParser.normalize(artist).toLowerCase(Locale.ROOT)
                + '\0'
                + TrackMetadataParser.normalize(title).toLowerCase(Locale.ROOT);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}

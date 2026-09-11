package Zenvibe.lavaplayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A similar-recording recommendation ready to load into Lavaplayer.
 * Prefer catalogue URLs from MusicBrainz when present; otherwise ytsearch.
 */
public record AutoplayTarget(String artist, String title, String mbid, List<String> externalUrls) {

    public String toLoadQuery(boolean allowSpotify) {
        String url = bestExternalUrl(allowSpotify);
        if (url != null) {
            return url;
        }
        return "ytsearch:" + artist + " - " + title + " official audio";
    }

    public String bestExternalUrl(boolean allowSpotify) {
        if (externalUrls == null || externalUrls.isEmpty()) {
            return null;
        }
        return externalUrls.stream()
                .map(AutoplayTarget::normalizeUrl)
                .filter(u -> u != null && !u.isBlank())
                .filter(u -> allowSpotify || !isSpotify(u))
                .min(Comparator.comparingInt(AutoplayTarget::urlRank))
                .orElse(null);
    }

    public String displayName() {
        return artist + " - " + title;
    }

    private static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String u = url.trim();
        if (u.contains("music.youtube.com/")) {
            u = u.replace("music.youtube.com/", "www.youtube.com/");
        }
        return u;
    }

    private static boolean isSpotify(String url) {
        return url.toLowerCase(Locale.ROOT).contains("spotify.com/");
    }

    private static int urlRank(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        if (u.contains("open.spotify.com/track/")) return 0;
        if (u.contains("youtube.com/watch") || u.contains("youtu.be/")) return 1;
        if (u.contains("soundcloud.com/")) return 2;
        if (u.contains("bandcamp.com/")) return 3;
        if (u.contains("spotify.com/")) return 9;
        return 5;
    }

    static List<String> mergeUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(urls.size());
        for (String u : urls) {
            String n = normalizeUrl(u);
            if (n != null && !n.isBlank() && !out.contains(n)) {
                out.add(n);
            }
        }
        return List.copyOf(out);
    }
}

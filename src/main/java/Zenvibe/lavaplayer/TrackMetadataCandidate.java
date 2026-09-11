package Zenvibe.lavaplayer;

import java.util.Locale;

public record TrackMetadataCandidate(String artist, String title, double score) {
    public TrackMetadataCandidate {
        artist = artist == null ? "" : artist.trim();
        title = title == null ? "" : title.trim();
    }

    String key() {
        return artist.toLowerCase(Locale.ROOT) + '\0' + title.toLowerCase(Locale.ROOT);
    }
}

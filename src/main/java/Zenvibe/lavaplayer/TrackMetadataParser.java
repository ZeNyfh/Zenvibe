package Zenvibe.lavaplayer;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TrackMetadataParser {
    private static final Pattern PRESENTATION = Pattern.compile(
            "(?i)[\\[({]*\\s*(?:official(?:\\s+(?:music|lyric))?\\s+(?:audio|video|visualizer)|official\\s+(?:audio|video)|lyric(?:s)?(?:\\s+video)?|visualizer|audio|hq|hd|4k)\\s*[\\])]*\\s*$");
    private static final Pattern FEAT = Pattern.compile(
            "(?i)\\s*[\\[(]?\\s*(?:feat\\.?|ft\\.?|featuring)\\s+([^\\])]+)\\s*[\\])]?\\s*$");
    private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B-\\u200D\\uFEFF]");
    private static final Pattern SPLIT = Pattern.compile("\\s+(-|\\|)\\s+");

    private TrackMetadataParser() {
    }

    public static List<TrackMetadataCandidate> candidates(String rawArtist, String rawTitle) {
        String artist = normalize(rawArtist);
        String title = normalize(rawTitle);
        String clean = stripPresentation(title);
        double penalty = artistPenalty(artist);

        Map<String, TrackMetadataCandidate> best = new LinkedHashMap<>();
        add(best, artist, clean, 0.55 - penalty);
        add(best, artist, title, 0.45 - penalty);

        String splitSrc = clean.isBlank() ? title : clean;
        Matcher m = SPLIT.matcher(splitSrc);
        int n = 0;
        while (m.find()) {
            String left = splitSrc.substring(0, m.start()).trim();
            String right = splitSrc.substring(m.end()).trim();
            if (left.isEmpty() || right.isEmpty()) continue;
            double prior = m.group(1).equals("-") ? 0.95 : 0.7;
            add(best, left, right, prior - n * 0.08 + penalty * 0.2);
            n++;
        }

        for (TrackMetadataCandidate c : List.copyOf(best.values())) {
            Matcher feat = FEAT.matcher(c.title());
            if (!feat.find()) continue;
            String who = feat.group(1).trim();
            String bare = c.title().substring(0, feat.start()).trim();
            if (who.isEmpty() || bare.isEmpty()) continue;
            add(best, c.artist() + " feat. " + who, bare, c.score() - 0.05);
            add(best, c.artist(), bare, c.score() - 0.1);
        }

        return best.values().stream()
                .sorted(Comparator.comparingDouble(TrackMetadataCandidate::score).reversed())
                .toList();
    }

    public static String normalize(String s) {
        if (s == null || s.isBlank()) return "";
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);
        s = ZERO_WIDTH.matcher(s).replaceAll("");
        s = s.replace('–', '-').replace('—', '-').replace('−', '-').replace('‐', '-');
        s = s.replace('•', '|').replace('･', '|');
        s = s.replaceAll("[\"“”„«»『』「」]", "");
        s = s.replaceAll("\\s*-{1,2}\\s*", " - ");
        s = s.replaceAll("\\s*\\|\\s*", " | ");
        return s.replaceAll("\\s+", " ").trim();
    }

    public static String stripPresentation(String title) {
        String cur = title, prev;
        do {
            prev = cur;
            cur = PRESENTATION.matcher(cur).replaceFirst("").trim();
            cur = cur.replaceAll("[!¡\\[({]+\\s*$", "").trim();
        } while (!cur.equals(prev));
        return cur;
    }

    public static double artistPenalty(String artist) {
        if (artist.isBlank()) return 0.4;
        double p = 0;
        if (artist.matches("(?i).*\\bRecords?\\s*$")) p += 0.7;
        if (artist.matches("(?i).*\\bRecordings\\s*$")) p += 0.7;
        if (artist.matches("(?i).*\\bEntertainment\\s*$")) p += 0.5;
        if (artist.matches("(?i).*\\bOfficial\\s*$")) p += 0.15;
        if (artist.matches("(?i).*VEVO\\s*$")) p += 0.2;
        if (artist.matches("(?i).*-\\s*Topic\\s*$")) p += 0.3;
        if (artist.equalsIgnoreCase("Various Artists")) p += 0.6;
        return Math.min(0.9, p);
    }

    public static double similarity(String a, String b) {
        String x = cmp(a), y = cmp(b);
        if (x.isEmpty() || y.isEmpty()) return 0;
        if (x.equals(y)) return 1;
        if (x.contains(y) || y.contains(x)) return 0.92;
        String[] xs = x.split(" "), ys = y.split(" ");
        int hits = 0;
        for (String t : xs) {
            for (String u : ys) {
                if (t.equals(u)) {
                    hits++;
                    break;
                }
            }
        }
        return (double) hits / Math.max(xs.length, ys.length);
    }

    private static String cmp(String s) {
        return normalize(s).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static void add(Map<String, TrackMetadataCandidate> best, String artist, String title, double score) {
        if (artist == null || title == null) return;
        artist = artist.trim();
        title = title.trim();
        if (artist.isEmpty() || title.isEmpty()) return;
        TrackMetadataCandidate c = new TrackMetadataCandidate(artist, title, Math.max(0.05, Math.min(0.99, score)));
        best.merge(c.key(), c, (a, b) -> a.score() >= b.score() ? a : b);
    }

    /** Local candidate check — no network. */
    public static void main(String[] args) {
        List<TrackMetadataCandidate> cs = candidates(
                "Atlantic Records", "Skillet - Awake and Alive (Official Audio)");
        assert !cs.isEmpty() : "expected candidates";
        assert cs.getFirst().artist().equalsIgnoreCase("Skillet") : cs.getFirst();
        assert cs.getFirst().title().toLowerCase(Locale.ROOT).contains("awake and alive") : cs.getFirst();
        assert stripPresentation("Awake and Alive (Official Audio)").equals("Awake and Alive");
        System.out.println("ok: " + cs.getFirst());
    }
}

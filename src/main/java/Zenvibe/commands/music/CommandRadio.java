package Zenvibe.commands.music;

import Zenvibe.BaseCommand;
import Zenvibe.CommandEvent;
import Zenvibe.CommandStateChecker.Check;
import Zenvibe.CommandStateChecker.CheckResult;
import Zenvibe.lavaplayer.PlayerManager;
import Zenvibe.lavaplayer.RadioDataFetcher;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static Zenvibe.CommandStateChecker.PerformChecks;
import static Zenvibe.Main.botColour;
import static Zenvibe.Main.readableBotPrefix;
import static Zenvibe.managers.EmbedManager.createQuickEmbed;
import static Zenvibe.managers.EmbedManager.sanitise;

public class CommandRadio extends BaseCommand {
    // internet-radio.com lists .pls/.m3u via playlistgenerator, with the station title in the following <h4>.
    private static final Pattern STATION_PATTERN = Pattern.compile(
            "playlistgenerator/\\?u=([^\"&]+)&(?:amp;)?t=\\.(?:m3u|pls)[\\s\\S]*?<h4 class=\"text-danger\"[^>]*>\\s*(?:<a[^>]*>)?([^<]+)",
            Pattern.CASE_INSENSITIVE);

    Map<String, String> radioLists = new HashMap<>() {{
        put("Heart", "https://media-ssl.musicradio.com/HeartLondon");
        put("1Mix Trance", "http://fr3.1mix.co.uk:8060/320");
        put("1Mix EDM", "http://fr1.1mix.co.uk:8060/320h");
        put("Beats n Breaks", "http://83.137.145.141:14280/;");
        put("Hardcore", "http://cc5.beheerstream.com:8022/stream");
        put("USA Country", "https://ais-sa2.cdnstream1.com/1976_128.mp3");
        put("USA Classic Rock", "https://hdradioclassicrock-rfritschka.radioca.st/stream");
        put("Nova DK", "https://live-bauerdk.sharp-stream.com/nova_dk_mp3");
        put("Pro FM", "https://player.profm.nl/proxy/profm?mp=/stream");
        put("Radio Comercial", "https://media3.mcr.iol.pt/livefm/comercial.mp3/icecast.audio");
        put("RMF FM", "https://rs6-krk2-cyfronet.rmfstream.pl/RMFFM48");
        put("M1 Plius", "https://radio.m-1.fm/m1plius/aacp64");
        put("NRK Jazz", "http://lyd.nrk.no:80/nrk_radio_jazz_aac_h");
        put("XS Manchester", "https://media-ice.musicradio.com/RealXSManchesterMP3");
    }};

    public static String getRadio(String search) throws IOException {
        String query = search == null ? "" : search.trim().replace('+', ' ').replaceAll("\\s+", " ");
        if (query.isEmpty()) {
            return "None";
        }

        String[] tokens = query.toLowerCase(Locale.ROOT).split(" ");
        LinkedHashMap<String, String> stations = scrapeStations(query); // url -> name

        if (stations.isEmpty() && tokens.length > 1) {
            for (String token : tokens) {
                if (token.isBlank()) continue;
                mergeStations(stations, scrapeStations(token));
                if (bestMatchScore(stations, tokens) == tokens.length) {
                    break; // already have a station matching every term
                }
            }
        }

        if (stations.isEmpty()) {
            return "None";
        }

        return bestMatchUrl(stations, tokens);
    }

    private static LinkedHashMap<String, String> scrapeStations(String query) throws IOException {
        LinkedHashMap<String, String> stations = new LinkedHashMap<>();
        URL url;
        try {
            url = URI.create("https://www.internet-radio.com/search/?radio=" + URLEncoder.encode(query, StandardCharsets.UTF_8)).toURL();
        } catch (Exception e) {
            e.printStackTrace();
            return stations;
        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream();
        if (stream == null || status == 404) {
            connection.disconnect();
            return stations;
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                builder.append(line);
            }
        } catch (Exception ignored) {
            return stations;
        } finally {
            connection.disconnect();
        }

        Matcher matcher = STATION_PATTERN.matcher(builder.toString());
        while (matcher.find()) {
            String streamUrl = URLDecoder.decode(matcher.group(1).replace("&amp;", "&"), StandardCharsets.UTF_8).trim();
            String name = unescapeHtml(matcher.group(2)).trim();
            if (!streamUrl.isEmpty() && !name.isEmpty()) {
                stations.putIfAbsent(streamUrl, name);
            }
        }
        return stations;
    }

    private static void mergeStations(LinkedHashMap<String, String> into, LinkedHashMap<String, String> from) {
        for (Map.Entry<String, String> entry : from.entrySet()) {
            into.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    private static int matchScore(String name, String[] tokens) {
        String lower = name.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : tokens) {
            if (lower.contains(token)) {
                score++;
            }
        }
        return score;
    }

    private static int bestMatchScore(Map<String, String> stations, String[] tokens) {
        int best = 0;
        for (String name : stations.values()) {
            best = Math.max(best, matchScore(name, tokens));
        }
        return best;
    }

    private static String bestMatchUrl(LinkedHashMap<String, String> stations, String[] tokens) {
        String bestUrl = null;
        int bestScore = -1;
        for (Map.Entry<String, String> entry : stations.entrySet()) {
            int score = matchScore(entry.getValue(), tokens);
            if (score > bestScore) {
                bestScore = score;
                bestUrl = entry.getKey();
            }
        }
        return bestUrl != null ? bestUrl : "None";
    }

    private static String unescapeHtml(String input) {
        return input
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
    }

    public Map<String, String> getRadios() {
        return radioLists;
    }

    @Override
    public Check[] getChecks() {
        return new Check[]{Check.IS_CHANNEL_BLOCKED};
    }

    @Override
    public void execute(CommandEvent event) throws IOException {
        if (event.getArgs().length == 1 || event.getArgs()[1].equalsIgnoreCase("list")) {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(botColour);
            eb.appendDescription(event.localise("cmd.radio.list"));
            getRadios().forEach((key, value) -> eb.appendDescription("**[" + key + "](" + value + ")**\n"));
            eb.appendDescription("\n");
            eb.appendDescription(event.localise("cmd.radio.alternativeList", readableBotPrefix, "radio search"));
            eb.setFooter(event.localise("cmd.radio.useForPlay", readableBotPrefix, "radio"));
            if (event.getArgs().length == 1) {
                event.replyEmbeds(event.createQuickError(event.localise("cmd.radio.noArgsList")), eb.build());
            } else {
                event.replyEmbeds(eb.build());
            }
            return;
        }

        // We have to do this later manually since a 1-arg version (see above) shouldn't invoke VC joining
        CheckResult checkResult = PerformChecks(event, Check.TRY_JOIN_VC);
        if (!checkResult.succeeded()) {
            event.replyEmbeds(createQuickEmbed(event.localise("statecheck.notAllowed"), checkResult.getMessage()));
            return;
        }

        event.deferReply(); //Give us time to think
        String radioURL = null;
        StringBuilder radioSearchTerm = new StringBuilder();
        if (event.getArgs()[1].equalsIgnoreCase("search")) {
            if (event.getArgs().length == 2) {
                event.replyEmbeds(event.createQuickError(event.localise("cmd.radio.noSearchTerm")));
                return;
            }
            List<String> otherArgs = new ArrayList<>(List.of(event.getArgs()));
            otherArgs.remove(0);
            otherArgs.remove(0);
            int i = 0;
            for (String string : otherArgs) {
                i++;
                if (otherArgs.size() > i) {
                    radioSearchTerm.append(string).append("+");
                } else {
                    radioSearchTerm.append(string);
                }
            }
            radioURL = getRadio(radioSearchTerm.toString());
        }
        if (radioURL != null) {
            if (radioURL.equals("None")) {
                event.replyEmbeds(event.createQuickError(event.localise("cmd.radio.notFound")));
            } else {
                PlayerManager.getInstance().loadAndPlay(event, radioURL, false);
                event.replyEmbeds(createQuickEmbed(event.localise("cmd.radio.queued"), "**[" + sanitise(RadioDataFetcher.getStreamTitle(radioURL)) + "](" + radioURL + ")**"));
            }
        } else {
            String wantedRadio = event.getContentRaw().split(" ", 2)[1].toLowerCase();
            for (Map.Entry<String, String> tempMap : getRadios().entrySet()) {
                if (tempMap.getKey().equalsIgnoreCase(wantedRadio)) {
                    PlayerManager.getInstance().loadAndPlay(event, tempMap.getValue(), false);
                    event.replyEmbeds(createQuickEmbed(event.localise("cmd.radio.queued"), "**[" + tempMap.getKey() + "](" + tempMap.getValue() + ")**"));
                    return;
                }
            }
            event.replyEmbeds(event.createQuickError(event.localise("cmd.radio.invalid")));
        }
    }

    @Override
    public void ProvideOptions(SlashCommandData slashCommand) {
        slashCommand.addOption(OptionType.STRING, "source", "Play a radio station. Prefix with \"search\" to search for a custom station", false);
    }

    @Override
    public Category getCategory() {
        return Category.Music;
    }

    @Override
    public String getOptions() {
        return "<list OR name> OR <search> <Radio_Name>";
    }

    @Override
    public String[] getNames() {
        return new String[]{"radio", "radios"};
    }

    @Override
    public String getDescription() {
        return "Plays a radio station. Specify nothing for a list of some available radio stations";
    }

    @Override
    public long getRatelimit() {
        return 2500;
    }
}

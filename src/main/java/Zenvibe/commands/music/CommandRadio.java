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
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static Zenvibe.CommandStateChecker.PerformChecks;
import static Zenvibe.managers.EmbedManager.createQuickEmbed;
import static Zenvibe.managers.EmbedManager.sanitise;
import static Zenvibe.Main.*;

public class CommandRadio extends BaseCommand {
    private static final Pattern pattern = Pattern.compile("ga\\('send', 'event', 'tunein', 'playm3u', '([^']+)'\\);");
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
        URL url;
        try {
            url = URI.create("https://www.internet-radio.com/search/?radio=" + search).toURL();
        } catch (Exception e) {
            e.printStackTrace();
            return "None";
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            for (String line; (line = reader.readLine()) != null; ) {
                builder.append(line);
            }
        } catch (Exception ignored) {
            return "None";
        }
        Matcher matcher = pattern.matcher(builder.toString());
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return "None";
        }
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

package Zenvibe.lavaplayer;

import Zenvibe.CommandEvent;
import com.github.natanbc.lavadsp.timescale.TimescalePcmAudioFilter;
import com.github.natanbc.lavadsp.vibrato.VibratoPcmAudioFilter;
import com.github.topi314.lavasrc.spotify.SpotifySourceManager;
import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.*;
import dev.lavalink.youtube.clients.skeleton.Client;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static Zenvibe.CommandEvent.createQuickError;
import static Zenvibe.Main.*;
import static Zenvibe.managers.EmbedManager.sanitise;
import static Zenvibe.managers.EmbedManager.toTimestamp;
import static Zenvibe.managers.LocaleManager.managerLocalise;

/**
 * Handles audio player functionality and user interfacing of the bot for those actions, E.g. playing song(s), and outputting embed messages
 * and system logs where necessary.
 */
public class PlayerManager {
    private static final int FALLBACKCOUNT = 1;

    private static final Map<String, Pattern> patterns = new HashMap<>() {{
        put("Spotify", Pattern.compile("<img src=\"([^\"]+)\" width=\""));
        put("SoundCloud", Pattern.compile("\"thumbnail_url\":\"([^\"]+)\",\""));
    }};
    private static PlayerManager INSTANCE;
    private static boolean hasSpotify;

    public static boolean hasSpotify() {
        return hasSpotify;
    }
    private final Map<Long, GuildMusicManager> musicManagers;
    private final AudioPlayerManager audioPlayerManager;

    /**
     * Constructs a new <code>PlayerManager</code>, initialising various <code>AudioSourceManager</code>s.
     */
    public PlayerManager() {
        this.musicManagers = new HashMap<>();
        this.audioPlayerManager = new DefaultAudioPlayerManager();
        Dotenv dotenv = loadEnvironment();

        Client[] youtubeClients = new Client[]{
                new MusicWithThumbnail(),
                new Tv(),
                new AndroidVrWithThumbnail(),
                new WebWithThumbnail(),
                new WebEmbeddedWithThumbnail()
        };
        YoutubeSourceOptions youtubeOptions = new YoutubeSourceOptions().setAllowSearch(true);
        String remoteCipherUrl = getOptionalEnv(dotenv, "YTCIPHERSERVERURL", "http://127.0.0.1:8001");
        if (!isBlank(remoteCipherUrl)) {
            String remoteCipherPassword = getOptionalEnv(dotenv, "YTCIPHERSERVERPASSWORD", "");
            String remoteCipherUserAgent = getOptionalEnv(dotenv, "YTCIPHERSERVERUSERAGENT", "Zenvibe");
            youtubeOptions.setRemoteCipher(remoteCipherUrl, remoteCipherPassword, remoteCipherUserAgent);
            System.out.println("YouTube remote cipher server enabled: " + remoteCipherUrl);
        }
        YoutubeAudioSourceManager youtubeAudioSourceManager = new YoutubeAudioSourceManager(youtubeOptions, youtubeClients);
        youtubeAudioSourceManager.useOauth2(ytRefreshToken, false);
        this.audioPlayerManager.registerSourceManager(youtubeAudioSourceManager);

        String spotifyClientID = getEnvironmentValue(dotenv, "SPOTIFYCLIENTID");
        String spotifyClientSecret = getEnvironmentValue(dotenv, "SPOTIFYCLIENTSECRET");
        String spotifyTokenerEndpoint = getEnvironmentValue(dotenv, "SPOTIFYTOKENERENDPOINT");
        String spotifySpDc = getEnvironmentValue(dotenv, "SPOTIFYSPDC");
        String spotifyCountryCode = getOptionalEnv(dotenv, "SPOTIFYCOUNTRYCODE", "gb");
        boolean spotifyUseClientCredentials = Boolean.parseBoolean(getOptionalEnv(dotenv, "SPOTIFYUSECLIENTCREDENTIALS", "false"));
        boolean spotifyPreferPartnerApi = Boolean.parseBoolean(getOptionalEnv(dotenv, "SPOTIFYPREFERPARTNERAPI", String.valueOf(!isBlank(spotifyTokenerEndpoint))));

        if (!spotifyUseClientCredentials && !isBlank(spotifyTokenerEndpoint)) {
            spotifyClientID = null;
            spotifyClientSecret = null;
        }

        try {
            SpotifySourceManager spotifySourceManager = new SpotifySourceManager(null, spotifyClientID, spotifyClientSecret, spotifyCountryCode, audioPlayerManager);
            if (!isBlank(spotifyTokenerEndpoint)) {
                spotifySourceManager.setCustomTokenEndpoint(spotifyTokenerEndpoint);
            }
            if (!isBlank(spotifySpDc)) {
                spotifySourceManager.setSpDc(spotifySpDc);
            }
            spotifySourceManager.setPreferPartnerApi(spotifyPreferPartnerApi);
            this.audioPlayerManager.registerSourceManager(spotifySourceManager);
            hasSpotify = true;
        } catch (Exception exception) {
            System.err.println("Spotify manager was unable to load due to a complication. Continuing without it...\nError: " + exception);
            hasSpotify = false;
        }

        this.audioPlayerManager.registerSourceManager(new TwitchStreamAudioSourceManager());
        this.audioPlayerManager.registerSourceManager(new BandcampAudioSourceManager(true));

        AudioSourceManagers.registerRemoteSources(
                this.audioPlayerManager,
                com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class
        );
        AudioSourceManagers.registerLocalSource(this.audioPlayerManager);
    }

    private static String getOptionalEnv(Dotenv dotenv, String key, String defaultValue) {
        String value = getEnvironmentValue(dotenv, key);
        return isBlank(value) ? defaultValue : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Returns a <code>PlayerManager</code> instance if one exists. Otherwise it will instantiate and return one.
     * Prevents multiple instances of <code>PlayerManager</code>.
     *
     * @return A <code>PlayerManager</code> instace.
     */
    public synchronized static PlayerManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new PlayerManager();
        }
        return INSTANCE;
    }

    private static boolean isTimeoutFailure(FriendlyException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("read timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String getSearchFallback(String trackUrl) {
        if (trackUrl.startsWith(YoutubeAudioSourceManager.SEARCH_PREFIX)) {
            return YoutubeAudioSourceManager.MUSIC_SEARCH_PREFIX + trackUrl.substring(YoutubeAudioSourceManager.SEARCH_PREFIX.length());
        }
        return null;
    }

    private static boolean isSearchLoad(String trackUrl) {
        return trackUrl.startsWith(YoutubeAudioSourceManager.SEARCH_PREFIX)
                || trackUrl.startsWith(YoutubeAudioSourceManager.MUSIC_SEARCH_PREFIX);
    }

    /**
     * Returns a <code>GuildMusicManager</code> of the <code>guild</code> specified, if it has been assigned one.
     * Otherwise, a new <code>GuildMusicManager</code> will be instantiated, added to the <code>musicManagers</code> map and returned.
     *
     * @param guild A <code>Guild</code> object.
     * @return A <code>GuildMusicManager</code> object.
     */
    public synchronized GuildMusicManager getMusicManager(Guild guild) {
        return this.musicManagers.computeIfAbsent(guild.getIdLong(), (guildId) -> {
            final GuildMusicManager guildMusicManager = new GuildMusicManager(this.audioPlayerManager);
            // create filters for the guild player manager.
            guildMusicManager.audioPlayer.setFilterFactory((track, format, output) -> {
                VibratoPcmAudioFilter vibrato = new VibratoPcmAudioFilter(output, format.channelCount, format.sampleRate);
                TimescalePcmAudioFilter timescale = new TimescalePcmAudioFilter(vibrato, format.channelCount, format.sampleRate);
                guildMusicManager.filters.put(AudioFilters.Vibrato, vibrato);
                guildMusicManager.filters.put(AudioFilters.Timescale, timescale);
                //Just make sure the items are in the reverse order they were made and all will be good
                return Arrays.asList(new AudioFilter[]{timescale, vibrato});
            });

            guild.getAudioManager().setSendingHandler(guildMusicManager.getSendHandler());
            return guildMusicManager;
        });
    }

    /**
     * Creates an <code>EmbedBuilder</code> object using the <code>audioTrack</code>'s information.
     * The embed's duration in the description might be "Unknown" if the track's <code>length</code> is too high (5+ days) or low.
     *
     * @param audioTrack An <code>AudioTrack</code> object.
     * @return An <code>EmbedBuilder</code> object.
     */
    public EmbedBuilder createTrackEmbed(AudioTrack audioTrack) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(botColour);
        embed.setThumbnail(getThumbURL(audioTrack));
        if (audioTrack.getInfo().title.isEmpty()) { // Local file
            String[] trackNameArray = audioTrack.getInfo().identifier.split("/");
            String trackName = trackNameArray[trackNameArray.length - 1];
            embed.setTitle(trackName, audioTrack.getInfo().uri);
        } else {
            embed.setTitle(sanitise(audioTrack.getInfo().title), audioTrack.getInfo().uri);
        }
        String length;
        long guildId = ((TrackData) audioTrack.getUserData()).guildId;
        Map<String, String> lang = guildLocales.get(guildId);
        if (audioTrack.getInfo().length > 432000000 || audioTrack.getInfo().length <= 1) {
            length = managerLocalise("main.unknown", lang);
        } else {
            length = toTimestamp(audioTrack.getInfo().length, guildId);
        }
        embed.setDescription(managerLocalise("pmanager.duration", lang, length) + managerLocalise("pmanager.channel", lang, audioTrack.getInfo().author));
        return embed;
    }

    public MessageEmbed createQueuedTracksEmbed(String title, List<AudioTrack> tracks, long guildId) {
        Map<String, String> locale = guildLocales.get(guildId);
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(botColour);
        embed.setTitle(title);
        long lengthSeconds = 0;
        for (AudioTrack audioTrack : tracks) {
            lengthSeconds += audioTrack.getInfo().length;
        }
        embed.appendDescription(managerLocalise("pmanager.playlistQueued", locale, tracks.size(), toTimestamp(lengthSeconds, guildId)));
        for (int i = 0; i < tracks.size() && i < 5; i++) {
            AudioTrackInfo info = tracks.get(i).getInfo();
            if (info.title == null) {
                embed.appendDescription(i + 1 + ". [" + info.identifier + "](" + info.uri + ")\n");
            } else {
                embed.appendDescription(i + 1 + ". [" + sanitise(info.title) + "](" + info.uri + ")\n");
            }
        }
        if (tracks.size() > 5) {
            embed.appendDescription("...");
        }
        if (!tracks.isEmpty() && getThumbURL(tracks.getFirst()) != null) {
            embed.setThumbnail(getThumbURL(tracks.getFirst()));
        }
        return embed.build();
    }

    public void loadAutoplayBatch(Object eventOrChannel, List<AutoplayTarget> targets, long guildId) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        Guild guild = getBot().getGuildById(guildId);
        if (guild == null) {
            return;
        }
        GuildMusicManager musicManager = getMusicManager(guild);
        int queueBefore = musicManager.scheduler.queue.size();
        boolean idleBefore = musicManager.audioPlayer.getPlayingTrack() == null;

        List<CompletableFuture<LoadResult>> loads = new ArrayList<>(targets.size());
        for (AutoplayTarget target : targets) {
            loads.add(loadAndPlay(eventOrChannel, target.toLoadQuery(hasSpotify), false, true,
                    target.artist(), target.title()));
        }
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> {
            if (error != null) {
                error.printStackTrace();
            }
            List<AudioTrack> added = new ArrayList<>();
            if (idleBefore && musicManager.audioPlayer.getPlayingTrack() != null) {
                added.add(musicManager.audioPlayer.getPlayingTrack());
            }
            List<AudioTrack> queued = new ArrayList<>(musicManager.scheduler.queue);
            if (queued.size() > queueBefore) {
                added.addAll(queued.subList(queueBefore, queued.size()));
            }
            if (added.isEmpty()) {
                return;
            }
            Map<String, String> locale = guildLocales.get(guildId);
            MessageEmbed embed = createQueuedTracksEmbed(managerLocalise("tsched.queued", locale), added, guildId);
            replyWithEmbed(eventOrChannel, embed, true);
        });
    }

    /**
     * Sends an embed into a given event or channel, either replying to the <code>CommandEvent</code> or simply sending
     * the embed without replying to anything.
     *
     * @param eventOrChannel   The event or channel in which the embed is to be sent.
     * @param embed            The embed to be sent in the <code>eventOrChannel</code>.
     * @param forceSendChannel Whether to force a send without replying to the <code>CommandEvent</code>.
     */
    private void replyWithEmbed(Object eventOrChannel, MessageEmbed embed, boolean forceSendChannel) {
        if (eventOrChannel instanceof CommandEvent) {
            if (forceSendChannel) {
                ((CommandEvent) eventOrChannel).getChannel().sendMessageEmbeds(embed).queue();
            } else {
                ((CommandEvent) eventOrChannel).replyEmbeds(embed);
            }
        } else {
            ((GuildMessageChannelUnion) eventOrChannel).sendMessageEmbeds(embed).queue();
        }
    }

    /**
     * Attempts to load, queue and play a track or playlist from a given URL, and (optionally) sends an embed in the given
     * <code>eventOrChannel</code> based on the results of the method.
     *
     * @param eventOrChannel The event or channel of the <code>CommandEvent</code>.
     * @param trackUrl       The URL of the track(s) to be played.
     * @param sendEmbed      Whether to create and send an embed in the <code>eventOrChannel</code> about the song(s) queued (or the error if one occurred).
     * @return A <code>CompletableFuture</code> that contains the <code>LoadResult</code> of the method.
     * @throws AssertionError If <code>eventOrChannel</code> is not an event or channel (mind blown).
     */
    public CompletableFuture<LoadResult> loadAndPlay(Object eventOrChannel, String trackUrl, boolean sendEmbed) {
        return loadAndPlay(eventOrChannel, trackUrl, sendEmbed, false, null, null);
    }

    public CompletableFuture<LoadResult> loadAndPlay(Object eventOrChannel, String trackUrl, boolean sendEmbed, boolean autoplayFill) {
        return loadAndPlay(eventOrChannel, trackUrl, sendEmbed, autoplayFill, null, null);
    }

    public CompletableFuture<LoadResult> loadAndPlay(Object eventOrChannel, String trackUrl, boolean sendEmbed,
                                                     boolean autoplayFill, String expectedArtist, String expectedTitle) {
        assert (eventOrChannel instanceof CommandEvent || eventOrChannel instanceof GuildMessageChannelUnion);
        CompletableFuture<LoadResult> loadResultFuture = new CompletableFuture<>();
        Guild commandGuild;
        if (eventOrChannel instanceof CommandEvent) {
            commandGuild = ((CommandEvent) eventOrChannel).getGuild();
        } else {
            commandGuild = ((GuildMessageChannelUnion) eventOrChannel).getGuild();
        }
        Map<String, String> locale = guildLocales.get(commandGuild.getIdLong());

        if (trackUrl.toLowerCase().contains("spotify")) {
            if (!hasSpotify) {
                if (sendEmbed) {
                    MessageEmbed errorEmbed = createQuickError(managerLocalise("pmanager.noSpotify", locale), locale);
                    replyWithEmbed(eventOrChannel, errorEmbed, false);
                }
                loadResultFuture.complete(LoadResult.NO_MATCHES);
                return loadResultFuture;
            }
        }

        if (sendEmbed) {
            EmbedBuilder loadingEmbed = new EmbedBuilder();
            loadingEmbed.setColor(botColour);
            loadingEmbed.setTitle(managerLocalise("main.loading", locale));

            if (eventOrChannel instanceof CommandEvent event) {
                event.replyEmbeds(response -> {
                    loadAndPlayWithResponse(eventOrChannel, trackUrl, commandGuild, locale, loadResultFuture, response,
                            autoplayFill, expectedArtist, expectedTitle, 0);
                }, loadingEmbed.build());
            } else {
                ((GuildMessageChannelUnion) eventOrChannel).sendMessageEmbeds(loadingEmbed.build()).queue();
                loadAndPlayWithResponse(eventOrChannel, trackUrl, commandGuild, locale, loadResultFuture, null,
                        autoplayFill, expectedArtist, expectedTitle, 0);
            }
        } else {
            loadAndPlayWithResponse(eventOrChannel, trackUrl, commandGuild, locale, loadResultFuture, null,
                    autoplayFill, expectedArtist, expectedTitle, 0);
        }

        return loadResultFuture;
    }

    private void loadAndPlayWithResponse(Object eventOrChannel, String trackUrl, Guild commandGuild,
                                         Map<String, String> locale, CompletableFuture<LoadResult> loadResultFuture,
                                         CommandEvent.Response loadingResponse, boolean autoplayFill,
                                         String expectedArtist, String expectedTitle, int searchFallbacks) {
        final GuildMusicManager musicManager = this.getMusicManager(commandGuild);

        this.audioPlayerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                audioTrack.setUserData(new TrackData(eventOrChannel));
                musicManager.scheduler.queue(audioTrack);
                if (loadingResponse != null) {
                    loadingResponse.editMessageEmbeds(createTrackEmbed(audioTrack).build());
                }
                loadResultFuture.complete(LoadResult.TRACK_LOADED);
            }

            @Override
            public void playlistLoaded(AudioPlaylist audioPlaylist) {
                Map<String, String> locale = guildLocales.get(commandGuild.getIdLong());
                final List<AudioTrack> tracks = audioPlaylist.getTracks();
                for (AudioTrack audioTrack : tracks) {
                    audioTrack.setUserData(new TrackData(eventOrChannel));
                }
                if (!tracks.isEmpty()) {
                    AudioTrack track = audioPlaylist.getSelectedTrack() == null ? tracks.getFirst() : audioPlaylist.getSelectedTrack();
                    if (autoplayFill) {
                        track = pickAutoplaySearchResult(tracks, expectedArtist, expectedTitle);
                    }
                    if (tracks.size() == 1 || audioPlaylist.isSearchResult() || isSearchLoad(trackUrl) || autoplayFill) {
                        musicManager.scheduler.queue(track);
                        if (loadingResponse != null) {
                            track.setUserData(new TrackData(eventOrChannel));
                            loadingResponse.editMessageEmbeds(createTrackEmbed(track).build());
                        }
                    } else {
                        EmbedBuilder embed = new EmbedBuilder();
                        embed.setColor(botColour);
                        long lengthSeconds = 0;
                        for (AudioTrack audioTrack : tracks) {
                            lengthSeconds = (lengthSeconds + audioTrack.getInfo().length);
                            musicManager.scheduler.queue(audioTrack);
                        }
                        embed.setTitle(audioPlaylist.getName().replaceAll("&amp;", "&").replaceAll("&gt;", ">").replaceAll("&lt;", "<").replaceAll("\\\\", "\\\\\\\\"));
                        embed.appendDescription(managerLocalise("pmanager.playlistQueued", locale, tracks.size(), toTimestamp(lengthSeconds, commandGuild.getIdLong())));
                        for (int i = 0; i < tracks.size() && i < 5; i++) {
                            if (tracks.get(i).getInfo().title == null) {
                                embed.appendDescription(i + 1 + ". [" + tracks.get(i).getInfo().identifier + "](" + tracks.get(i).getInfo().uri + ")\n");
                            } else {
                                embed.appendDescription(i + 1 + ". [" + sanitise(tracks.get(i).getInfo().title) + "](" + tracks.get(i).getInfo().uri + ")\n");
                            }
                        }
                        if (tracks.size() > 5) {
                            embed.appendDescription("...");
                        }
                        embed.setThumbnail(getThumbURL(tracks.getFirst()));
                        if (loadingResponse != null) {
                            loadingResponse.editMessageEmbeds(embed.build());
                        }
                    }
                }
                loadResultFuture.complete(LoadResult.PLAYLIST_LOADED);
            }

            @Override
            public void noMatches() {
                if (loadingResponse != null) {
                    loadingResponse.editMessageEmbeds(createQuickError(managerLocalise("pmanager.noMatches", locale), locale));
                }
                System.err.println("No match found for the track.\nURL:\"" + trackUrl + "\"");
                loadResultFuture.complete(LoadResult.NO_MATCHES);
            }

            @Override
            public void loadFailed(FriendlyException e) {
                System.err.println("Track failed to load.\nURL: \"" + trackUrl + "\"\nReason: " + e.getMessage());
                skipCountGuilds.remove(commandGuild.getIdLong());
                String fallbackTrackUrl = getSearchFallback(trackUrl);
                if (isTimeoutFailure(e) && fallbackTrackUrl != null && searchFallbacks < FALLBACKCOUNT) {
                    System.err.println("Retrying YouTube search through YouTube Music after timeout.\nURL: \"" + trackUrl + "\"");
                    loadAndPlayWithResponse(eventOrChannel, fallbackTrackUrl, commandGuild, locale, loadResultFuture,
                            loadingResponse, autoplayFill, expectedArtist, expectedTitle, searchFallbacks + 1);
                    return;
                }

                final StringBuilder loadFailedBuilder = new StringBuilder();
                if (e.getMessage().toLowerCase().contains("search response: 400")) {
                    loadFailedBuilder.append(managerLocalise("pmanager.APIError", locale)).append(" ");
                }
                loadFailedBuilder.append(e.getMessage());
                if (loadingResponse != null) {
                    loadingResponse.editMessageEmbeds(createQuickError(managerLocalise("pmanager.loadFailed", locale, loadFailedBuilder), locale));
                }
                loadResultFuture.complete(LoadResult.LOAD_FAILED);
            }
        });
    }

    private static AudioTrack pickAutoplaySearchResult(List<AudioTrack> tracks, String expectedArtist, String expectedTitle) {
        AudioTrack best = tracks.getFirst();
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < tracks.size(); i++) {
            AudioTrack t = tracks.get(i);
            double score = scoreAutoplayCandidate(t, expectedArtist, expectedTitle) - i * 0.02;
            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return best;
    }

    private static double scoreAutoplayCandidate(AudioTrack track, String expectedArtist, String expectedTitle) {
        String title = track.getInfo().title == null ? "" : track.getInfo().title;
        String author = track.getInfo().author == null ? "" : track.getInfo().author;
        String hay = (title + " " + author).toLowerCase(Locale.ROOT);
        double score = 0;
        if (expectedTitle != null && !expectedTitle.isBlank()) {
            score += TrackMetadataParser.similarity(expectedTitle, title) * 3.0;
        }
        if (expectedArtist != null && !expectedArtist.isBlank()) {
            score += TrackMetadataParser.similarity(expectedArtist, author) * 1.5;
            score += TrackMetadataParser.similarity(expectedArtist, title) * 0.4;
        }
        if (hay.contains("official audio") || hay.contains("official video") || hay.contains("topic")) score += 1.2;
        if (hay.contains("lyric")) score += 0.2;
        if (hay.contains("live") || hay.contains("concert") || hay.contains("tour") || hay.contains("session at")) score -= 3.5;
        if (hay.contains("cover") || hay.contains("karaoke") || hay.contains("nightcore") || hay.contains("sped up")
                || hay.contains("slowed") || hay.contains("remix") || hay.contains("mashup")) score -= 2.0;
        return score;
    }

    /**
     * Returns the URL of the thumbnail for the current <code>AudioTrack</code>, checking if it is a valid image URL.
     *
     * @param track The <code>AudioTrack</code> object whose information will be used to search for a thumbnail.
     * @return A string URL of the thumbnail image (or <code>null</code> when one was not found for any reason).
     */
    @Nullable
    public String getThumbURL(AudioTrack track) {
        URL url = null;
        Pattern pattern = null;
        String site = "";
        try {
            if (track.getInfo().artworkUrl != null) {
                return track.getInfo().artworkUrl;
            }
            if (track.getInfo().uri.toLowerCase().contains("youtube")) {
                return "https://img.youtube.com/vi/" + track.getIdentifier() + "/0.jpg";
            } else if (track.getInfo().uri.toLowerCase().contains("spotify")) {
                site = "Spotify";
                url = URI.create("https://embed.spotify.com/oembed?url=" + track.getInfo().uri).toURL();
            } else if (track.getInfo().uri.toLowerCase().contains("soundcloud")) {
                site = "SoundCloud";
                url = URI.create(track.getInfo().uri).toURL();
            } else {
                return null;
            }
            pattern = patterns.get(site);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Thumb URL Fail : " + site + " |" + url);
        }

        if (url != null && pattern != null) {
            try {
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
                reader.close();
                Matcher matcher = pattern.matcher(output.toString());
                if (matcher.find()) {
                    return matcher.group(1);
                } else {
                    return null;
                }
            } catch (Exception ignored) {
                System.err.println("Thumb Matcher Fail : " + site + " |" + url);
            } // ignore because floods console if image url invalid
            return null;
        }
        return null;
    }

    /**
     * Possible results of an attempt to load an <code>AudioTrack</code> or <code>AudioPlaylist</code>.
     */
    public enum LoadResult {
        /**
         * A single <code>AudioTrack</code> has been loaded.
         */
        TRACK_LOADED(true),

        /**
         * An <code>AudioPlaylist</code> has been loaded.
         */
        PLAYLIST_LOADED(true),

        /**
         * There are no matching <code>AudioTrack</code>'s to the load query.
         */
        NO_MATCHES(false),

        /**
         * The load attempt has failed due to some error(s).
         */
        LOAD_FAILED(false);


        /**
         * Whether this entry of <code>LoadResult</code> will cause a song to be played.
         */
        public final boolean songWasPlayed;

        /**
         * Sets the current <code>LoadResult</code> instance's <code>songWasPlayed</code> property to the given value.
         *
         * @param songWasPlayed Whether this entry of <code>LoadResult</code> will cause a song to be played.
         */
        LoadResult(boolean songWasPlayed) {
            this.songWasPlayed = songWasPlayed;
        }
    }

    /**
     * Holds necessary information on user, guild and channel with regards to a specific <code>AudioTrack</code> or
     * <code>AudioPlaylist</code> for later use.
     */
    public static class TrackData {
        public final Object eventOrChannel;
        public final Long channelId;
        public final Long guildId;
        public final String username;
        public Boolean wasSkipped;

        /**
         * Constructs a new <code>TrackUserData</code> object.
         *
         * @param eventOrChannel The event or channel where the <code>CommandEvent</code> was called.
         */
        public TrackData(Object eventOrChannel) {
            this.eventOrChannel = eventOrChannel;
            GuildMessageChannelUnion channel;
            if (eventOrChannel instanceof CommandEvent) {
                channel = ((CommandEvent) eventOrChannel).getChannel();
                username = ((CommandEvent) eventOrChannel).getUser().getEffectiveName();
            } else {
                channel = (GuildMessageChannelUnion) eventOrChannel;
                username = "";
            }
            this.wasSkipped = false;
            this.channelId = channel.getIdLong();
            this.guildId = channel.getGuild().getIdLong();
        }
    }
}

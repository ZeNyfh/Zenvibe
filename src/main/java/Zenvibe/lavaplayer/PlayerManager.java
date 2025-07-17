package Zenvibe.lavaplayer;

import Zenvibe.CommandEvent;
import com.github.natanbc.lavadsp.timescale.TimescalePcmAudioFilter;
import com.github.natanbc.lavadsp.vibrato.VibratoPcmAudioFilter;
import com.github.topi314.lavasrc.spotify.SpotifySourceManager;
import com.github.topi314.lavasrc.ytdlp.YtdlpAudioSourceManager;
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
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static Zenvibe.CommandEvent.createQuickError;
import static Zenvibe.managers.EmbedManager.sanitise;
import static Zenvibe.managers.EmbedManager.toTimestamp;
import static Zenvibe.managers.LocaleManager.managerLocalise;
import static Zenvibe.Main.*;

/**
 * Handles audio player functionality and user interfacing of the bot for those actions, E.g. playing song(s), and outputting embed messages
 * and system logs where necessary.
 */
public class PlayerManager {
    private static final Map<String, Pattern> patterns = new HashMap<>() {{
        put("Spotify", Pattern.compile("<img src=\"([^\"]+)\" width=\""));
        put("SoundCloud", Pattern.compile("\"thumbnail_url\":\"([^\"]+)\",\""));
    }};
    private static PlayerManager INSTANCE;
    private static boolean hasSpotify;
    private final Map<Long, GuildMusicManager> musicManagers;
    private final AudioPlayerManager audioPlayerManager;

    /**
     * Constructs a new <code>PlayerManager</code>, initialising various <code>AudioSourceManager</code>s.
     */
    public PlayerManager() {
        this.musicManagers = new HashMap<>();
        this.audioPlayerManager = new DefaultAudioPlayerManager();

        String ytdlpName = "yt-dlp.exe";
        if (System.getProperty("os.name").toLowerCase().contains("linux")) {
            ytdlpName = "yt-dlp_linux";
        }
        File ytdlpFile = new File(ytdlpName);
        if (!ytdlpFile.exists()) {
           System.err.println("File not found, stopping the bot for now because youtube is crucial, please add: " + ytdlpName);
           System.err.println("https://github.com/yt-dlp/yt-dlp");
           System.exit(1);
        }

        YtdlpAudioSourceManager ytdlpSource = new YtdlpAudioSourceManager(ytdlpFile.getAbsolutePath());
        ytdlpSource.setSearchLimit(1);

        this.audioPlayerManager.registerSourceManager(ytdlpSource);

        String spotifyClientID = Dotenv.load().get("SPOTIFYCLIENTID");
        String spotifyClientSecret = Dotenv.load().get("SPOTIFYCLIENTSECRET");

        try {
            this.audioPlayerManager.registerSourceManager(new SpotifySourceManager(null, spotifyClientID, spotifyClientSecret, "gb", audioPlayerManager));
            hasSpotify = true;
        } catch (Exception exception) {
            System.err.println("Spotify manager was unable to load due to a complication. Continuing without it...\nError: " + exception);
            hasSpotify = false;
        }

        this.audioPlayerManager.registerSourceManager(new TwitchStreamAudioSourceManager());
        this.audioPlayerManager.registerSourceManager(new BandcampAudioSourceManager(true));

        AudioSourceManagers.registerRemoteSources(this.audioPlayerManager);
        AudioSourceManagers.registerLocalSource(this.audioPlayerManager);
    }

    /**
     * Returns a <code>PlayerManager</code> instance if one exists. Otherwise it will instantiate and return one.
     * Prevents multiple instances of <code>PlayerManager</code>.
     *
     * @return  A <code>PlayerManager</code> instace.
     */
    public synchronized static PlayerManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new PlayerManager();
        }
        return INSTANCE;
    }

    /**
     * Returns a <code>GuildMusicManager</code> of the <code>guild</code> specified, if it has been assigned one.
     * Otherwise, a new <code>GuildMusicManager</code> will be instantiated, added to the <code>musicManagers</code> map and returned.
     *
     * @param guild     A <code>Guild</code> object.
     * @return          A <code>GuildMusicManager</code> object.
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
     * @param audioTrack    An <code>AudioTrack</code> object.
     * @return              An <code>EmbedBuilder</code> object.
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

    /**
     * Sends an embed into a given event or channel, either replying to the <code>CommandEvent</code> or simply sending
     * the embed without replying to anything.
     *
     * @param eventOrChannel    The event or channel in which the embed is to be sent.
     * @param embed             The embed to be sent in the <code>eventOrChannel</code>.
     * @param forceSendChannel  Whether to force a send without replying to the <code>CommandEvent</code>.
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
     * @param eventOrChannel    The event or channel of the <code>CommandEvent</code>.
     * @param trackUrl          The URL of the track(s) to be played.
     * @param sendEmbed         Whether to create and send an embed in the <code>eventOrChannel</code> about the song(s) queued (or the error if one occurred).
     * @return                  A <code>CompletableFuture</code> that contains the <code>LoadResult</code> of the method.
     * @throws AssertionError   If <code>eventOrChannel</code> is not an event or channel (mind blown).
     */
    public CompletableFuture<LoadResult> loadAndPlay(Object eventOrChannel, String trackUrl, boolean sendEmbed) {
        assert (eventOrChannel instanceof CommandEvent || eventOrChannel instanceof GuildMessageChannelUnion);
        CompletableFuture<LoadResult> loadResultFuture = new CompletableFuture<>();
        Guild commandGuild;
        if (eventOrChannel instanceof CommandEvent) {
            commandGuild = ((CommandEvent) eventOrChannel).getGuild();
        } else {
            commandGuild = ((GuildMessageChannelUnion) eventOrChannel).getGuild();
        }
        Map<String, String> locale = guildLocales.get(commandGuild.getIdLong());

        // If the trackURL is for spotify but the manager can't use spotify, return NO_MATCHES (and embed if sendEmbed)
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

        // Send loading embed and handle response asynchronously
        if (sendEmbed) {
            EmbedBuilder loadingEmbed = new EmbedBuilder();
            loadingEmbed.setColor(botColour);
            loadingEmbed.setTitle(managerLocalise("main.loading", locale));

            if (eventOrChannel instanceof CommandEvent event) {
                event.replyEmbeds(response -> {
                    loadAndPlayWithResponse(eventOrChannel, trackUrl, commandGuild, locale, loadResultFuture, response);
                }, loadingEmbed.build());
            } else {
                ((GuildMessageChannelUnion) eventOrChannel).sendMessageEmbeds(loadingEmbed.build()).queue();
                loadAndPlayWithResponse(eventOrChannel, trackUrl, commandGuild, locale, loadResultFuture, null);
            }
        } else {
            loadAndPlayWithResponse(eventOrChannel, trackUrl, commandGuild, locale, loadResultFuture, null);
        }

        return loadResultFuture;
    }

    /**
     * Helper method that continues the loading process after the initial loading embed has been sent.
     */
    private void loadAndPlayWithResponse(Object eventOrChannel, String trackUrl, Guild commandGuild,
                                         Map<String, String> locale, CompletableFuture<LoadResult> loadResultFuture,
                                         CommandEvent.Response loadingResponse) {
        final GuildMusicManager musicManager = this.getMusicManager(commandGuild);

        this.audioPlayerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            /**
             * Sets user data for <code>audioTrack</code>, queues it on the guild's scheduler and (optionally)
             * sends an embed.
             *
             * @param audioTrack    The <code>AudioTrack</code> which was loaded.
             */
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                audioTrack.setUserData(new TrackData(eventOrChannel));
                musicManager.scheduler.queue(audioTrack);
                if (loadingResponse != null) {
                    loadingResponse.editMessageEmbeds(createTrackEmbed(audioTrack).build());
                }
                loadResultFuture.complete(LoadResult.TRACK_LOADED);
            }

            /**
             * Handles queueing and embed building for playlists when loaded (assuming the <code>audioPlaylist</code>
             * instance contains tracks).
             *
             * @param audioPlaylist The <code>AudioPlaylist</code> which was loaded.
             */
            @Override
            public void playlistLoaded(AudioPlaylist audioPlaylist) {
                boolean autoplaying = AutoplayGuilds.contains(commandGuild.getIdLong());
                Map<String, String> locale = guildLocales.get(commandGuild.getIdLong());
                final List<AudioTrack> tracks = audioPlaylist.getTracks();
                for (AudioTrack audioTrack : tracks) {
                    audioTrack.setUserData(new TrackData(eventOrChannel));
                }
                if (!tracks.isEmpty()) {
                    AudioTrack track = tracks.getFirst();
                    if (autoplaying)
                        track = tracks.get(ThreadLocalRandom.current().nextInt(2, 4)); // this is to prevent looping tracks
                    if (tracks.size() == 1 || audioPlaylist.getName().contains("Search results for:") || autoplaying) {
                        musicManager.scheduler.queue(track);
                        if (loadingResponse != null && !autoplaying) {
                            track.setUserData(new TrackData(eventOrChannel));
                            loadingResponse.editMessageEmbeds(createTrackEmbed(track).build());
                        } else if (autoplaying) {
                            track.setUserData(new TrackData(eventOrChannel));
                            replyWithEmbed(eventOrChannel, createTrackEmbed(track).build(), autoplaying);
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

            /**
             * Sends an embed to notify the user that the load query had no matching <code>AudioTrack</code> or
             * <code>AudioPlaylist</code>.
             */
            @Override
            public void noMatches() {
                if (loadingResponse != null) {
                    loadingResponse.editMessageEmbeds(createQuickError(managerLocalise("pmanager.noMatches", locale), locale));
                }
                System.err.println("No match found for the track.\nURL:\"" + trackUrl + "\"");
                loadResultFuture.complete(LoadResult.NO_MATCHES);
            }

            /**
             * Sends output to <code>System.err</code> and an embed in <code>eventOrChannel</code> of <code>CommandEvent</code> to notify bot host
             * and user of an error occuring during the loading process.
             *
             * @param e The error that occurred during the loading process.
             */
            @Override
            public void loadFailed(FriendlyException e) {
                System.err.println("Track failed to load.\nURL: \"" + trackUrl + "\"\nReason: " + e.getMessage());
                skipCountGuilds.remove(commandGuild.getIdLong());

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



    /**
     * Returns the URL of the thumbnail for the current <code>AudioTrack</code>, checking if it is a valid image URL.
     *
     * @param track The <code>AudioTrack</code> object whose information will be used to search for a thumbnail.
     * @return      A string URL of the thumbnail image (or <code>null</code> when one was not found for any reason).
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

        /**
         * Constructs a new <code>TrackUserData</code> object.
         *
         * @param eventOrChannel    The event or channel where the <code>CommandEvent</code> was called.
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
            this.channelId = channel.getIdLong();
            this.guildId = channel.getGuild().getIdLong();
        }
    }
}

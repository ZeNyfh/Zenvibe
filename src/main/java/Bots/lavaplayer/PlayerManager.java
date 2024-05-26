package Bots.lavaplayer;

import Bots.MessageEvent;
import com.github.natanbc.lavadsp.timescale.TimescalePcmAudioFilter;
import com.github.natanbc.lavadsp.vibrato.VibratoPcmAudioFilter;
import com.github.topi314.lavasrc.spotify.SpotifySourceManager;
import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static Bots.Main.*;

public class PlayerManager {
    private static final HashMap<String, Pattern> patterns = new HashMap<>() {{
        put("Spotify", Pattern.compile("<img src=\"([^\"]+)\" width=\""));
        put("SoundCloud", Pattern.compile("\"thumbnail_url\":\"([^\"]+)\",\""));
    }};
    private static PlayerManager INSTANCE;
    private static boolean hasSpotify;
    private final Map<Long, GuildMusicManager> musicManagers;
    private final AudioPlayerManager audioPlayerManager;

    public PlayerManager() {
        this.musicManagers = new HashMap<>();
        this.audioPlayerManager = new DefaultAudioPlayerManager();

        String spotifyClientID = Dotenv.load().get("SPOTIFYCLIENTID");
        String spotifyClientSecret = Dotenv.load().get("SPOTIFYCLIENTSECRET");

        try {
            this.audioPlayerManager.registerSourceManager(new SpotifySourceManager(null, spotifyClientID, spotifyClientSecret, "gb", audioPlayerManager));
            hasSpotify = true;
        } catch (Exception exception) {
            System.err.println("Spotify manager was unable to load due to a complication. Continuing without it...\nError: " + exception);
            hasSpotify = false;
        }

        AudioSourceManagers.registerRemoteSources(this.audioPlayerManager);
        AudioSourceManagers.registerLocalSource(this.audioPlayerManager);
    }

    public static PlayerManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new PlayerManager();
        }
        return INSTANCE;
    }

    public GuildMusicManager getMusicManager(Guild guild) {
        return this.musicManagers.computeIfAbsent(guild.getIdLong(), (guildId) -> {
            final GuildMusicManager guildMusicManager = new GuildMusicManager(this.audioPlayerManager);
            guildMusicManager.audioPlayer.setFilterFactory((track, format, output) -> {
                VibratoPcmAudioFilter vibrato = new VibratoPcmAudioFilter(output, format.channelCount, format.sampleRate);
                TimescalePcmAudioFilter timescale = new TimescalePcmAudioFilter(vibrato, format.channelCount, format.sampleRate);
                guildMusicManager.filters.put(audioFilters.Vibrato, vibrato);
                guildMusicManager.filters.put(audioFilters.Timescale, timescale);
                //Just make sure the items are in the reverse order they were made and all will be good
                return Arrays.asList(new AudioFilter[]{timescale, vibrato});
            });
            guild.getAudioManager().setSendingHandler(guildMusicManager.getSendHandler());
            return guildMusicManager;
        });
    }

    public EmbedBuilder createTrackEmbed(AudioTrack audioTrack) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(botColour);
        embed.setThumbnail(getThumbURL(audioTrack));
        if (audioTrack.getInfo().title.isEmpty()) { // Local file
            String[] trackNameArray = audioTrack.getInfo().identifier.split("/");
            String trackName = trackNameArray[trackNameArray.length - 1];
            embed.setTitle(trackName, audioTrack.getInfo().uri);
        } else {
            embed.setTitle(audioTrack.getInfo().title, audioTrack.getInfo().uri);
        }
        String length;
        if (audioTrack.getInfo().length > 432000000 || audioTrack.getInfo().length <= 1) {
            length = "Unknown";
        } else {
            length = toTimestamp((audioTrack.getInfo().length));
        }
        embed.setDescription("Duration: `" + length + "`\n" + "Channel: `" + audioTrack.getInfo().author + "`");
        return embed;
    }

    // TODO: That's a lot of seemingly random arguments, this could use some cleanup
    public void loadAndPlay(MessageEvent event, String trackUrl, Boolean sendEmbed, Runnable OnCompletion, GuildMessageChannelUnion channel) {
        GuildMessageChannelUnion commandChannel;
        if (event == null) {
            commandChannel = channel;
        } else {
            commandChannel = event.getChannel();
        }
        if (trackUrl.toLowerCase().contains("spotify")) {
            if (!hasSpotify) {
                commandChannel.sendMessageEmbeds(createQuickError("The bot had complications during initialisation and is unable to play spotify tracks")).queue();
                return;
            }
        }
        final GuildMusicManager musicManager = this.getMusicManager(commandChannel.getGuild());
        this.audioPlayerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack audioTrack) {
                audioTrack.setUserData(new Object[]{event, commandChannel.getId()});
                musicManager.scheduler.queue(audioTrack);
                if (sendEmbed) {
                    EmbedBuilder embed = createTrackEmbed(audioTrack);
                    if (event != null) {
                        event.replyEmbeds(embed.build());
                    } else {
                        commandChannel.sendMessageEmbeds(embed.build()).queue();
                    }
                }
                OnCompletion.run();
            }

            @Override
            public void playlistLoaded(AudioPlaylist audioPlaylist) {
                boolean autoplaying = AutoplayHelper.includes(commandChannel.getGuild().getIdLong());
                final List<AudioTrack> tracks = audioPlaylist.getTracks();
                if (!tracks.isEmpty()) {
                    AudioTrack track = tracks.get(0);
                    if (autoplaying)
                        track = tracks.get(ThreadLocalRandom.current().nextInt(2, 4)); // this is to prevent looping tracks
                    if (tracks.size() == 1 || audioPlaylist.getName().contains("Search results for:") || autoplaying) {
                        musicManager.scheduler.queue(track);
                        if (sendEmbed) {
                            EmbedBuilder embed = createTrackEmbed(track);
                            if (!autoplaying && event != null) {
                                event.replyEmbeds(embed.build());
                            } else {
                                commandChannel.sendMessageEmbeds(embed.build()).queue();
                            }
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
                        embed.appendDescription("Size: **" + tracks.size() + "** tracks.\nLength: **" + toTimestamp(lengthSeconds) + "**\n\n");

                        for (int i = 0; i < tracks.size() && i < 5; i++) {
                            if (tracks.get(i).getInfo().title == null) {
                                embed.appendDescription(i + 1 + ". [" + tracks.get(i).getInfo().identifier + "](" + tracks.get(i).getInfo().uri + ")\n");
                            } else {
                                embed.appendDescription(i + 1 + ". [" + tracks.get(i).getInfo().title + "](" + tracks.get(i).getInfo().uri + ")\n");
                            }
                        }
                        if (tracks.size() > 5) {
                            embed.appendDescription("...");
                        }
                        embed.setThumbnail(getThumbURL(tracks.get(0)));
                        if (event != null) {
                            event.replyEmbeds(embed.build());
                        } else {
                            commandChannel.sendMessageEmbeds(embed.build()).queue();
                        }
                    }
                    for (AudioTrack audioTrack : tracks) {
                        audioTrack.setUserData(new Object[]{event, commandChannel.getId()});
                    }
                }
                OnCompletion.run();
            }

            @Override
            public void noMatches() {
                event.replyEmbeds(createQuickError("No matches found for the track."));
                System.err.println("No match found for the track.\nURL:\"" + trackUrl + "\"");
                OnCompletion.run();
            }

            @Override
            public void loadFailed(FriendlyException e) {
                System.err.println("Track failed to load.\nURL: \"" + trackUrl + "\"\nReason: " + e.getMessage());
                clearVotes(commandChannel.getGuild().getIdLong());

                final StringBuilder loadFailedBuilder = new StringBuilder();
                if (e.getMessage().toLowerCase().contains("search response: 400")) {
                    loadFailedBuilder.append("An error with the youtube search API has occurred. ");
                }
                loadFailedBuilder.append(e.getMessage());
                event.replyEmbeds(createQuickError("The track failed to load: " + loadFailedBuilder));
                OnCompletion.run();
            }
        });
    }

    public void loadAndPlay(MessageEvent event, String trackUrl, Boolean sendEmbed, Runnable OnCompletion) {
        loadAndPlay(event, trackUrl, sendEmbed, OnCompletion, null);
    }

    public void loadAndPlay(MessageEvent event, String trackUrl, Boolean sendEmbed) {
        loadAndPlay(event, trackUrl, sendEmbed, () -> {
        }, null);
    }

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
                url = new URL("https://embed.spotify.com/oembed?url=" + track.getInfo().uri);
            } else if (track.getInfo().uri.toLowerCase().contains("soundcloud")) {
                site = "SoundCloud";
                url = new URL(track.getInfo().uri);
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
}

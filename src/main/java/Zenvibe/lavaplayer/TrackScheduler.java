package Zenvibe.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static Zenvibe.CommandEvent.createQuickError;
import static Zenvibe.Main.*;
import static Zenvibe.lavaplayer.LastFMManager.vcScrobble;
import static Zenvibe.lavaplayer.LastFMManager.vcUpdateNowPlaying;
import static Zenvibe.managers.EmbedManager.createQuickEmbed;
import static Zenvibe.managers.EmbedManager.toSimpleTimestamp;
import static Zenvibe.managers.LocaleManager.managerLocalise;

public class TrackScheduler extends AudioEventAdapter {

    public final AudioPlayer player;
    public final BlockingQueue<AudioTrack> queue;
    private final Map<Long, Integer> guildFailCount = new HashMap<>();

    public TrackScheduler(AudioPlayer player) {
        this.player = player;
        this.queue = new LinkedBlockingQueue<>();
    }

    public void queue(AudioTrack track) {
        if (this.player.startTrack(track, true)) {
            this.player.setPaused(false);
        } else {
            this.queue.offer(track);
        }
    }

    public void nextTrack() {
        AudioTrack nextTrack = this.queue.poll();
        if (nextTrack == null) {
            this.player.stopTrack();
            return;
        }
        this.player.startTrack(nextTrack, false);
        this.player.setPaused(false);
    }

    public boolean startNextTrackIfIdle() {
        if (this.player.getPlayingTrack() != null || this.queue.isEmpty()) {
            return false;
        }
        nextTrack();
        return this.player.getPlayingTrack() != null;
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        updateNowPlayingForTrack(track);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        logEarlyTrackFinish(track, endReason);

        if (!endReason.mayStartNext) {
            return;
        }

        PlayerManager.TrackData trackData = (PlayerManager.TrackData) track.getUserData();
        GuildMessageChannelUnion originalEventChannel = (GuildMessageChannelUnion) getGuildChannelFromID(trackData.channelId);
        long guildID = trackData.guildId;
        Map<String, String> lang = guildLocales.get(guildID);

        if (endReason == AudioTrackEndReason.LOAD_FAILED) {
            handleTrackFailure(originalEventChannel, player, track);
            return;
        }

        guildFailCount.remove(guildID);

        if (endReason.mayStartNext) {
            if (!Boolean.TRUE.equals(trackData.wasSkipped)) {
                scrobbleFinishedTrack(track, guildID);
            }
            if (LoopGuilds.contains(guildID)) { // track is looping
                AudioTrack loopTrack = track.makeClone();
                this.player.startTrack(loopTrack, false);
                trackLoops.put(guildID, trackLoops.getOrDefault(guildID, 0) + 1);
                return;
            }
            if (LoopQueueGuilds.contains(guildID)) { // queue is looping
                AudioTrack loopTrack = track.makeClone();
                nextTrack();
                queue(loopTrack);
                return;
            }

            if (AutoplayGuilds.contains(guildID) && queue.isEmpty()) { // autoplay only after queued tracks
                Object eventOrChannel = trackData.eventOrChannel;
                CompletableFuture.runAsync(() -> {
                    List<AutoplayTarget> songs = ListenBrainzManager.getSimilarTargets(track, guildID, ListenBrainzManager.AUTOPLAY_BATCH);
                    if (songs.isEmpty()) {
                        StringBuilder errorBuilder = new StringBuilder("❌ **")
                                .append(managerLocalise("main.error", lang))
                                .append(":**\n");
                        if (ListenBrainzManager.resolveRecordingMbid(track) == null) {
                            errorBuilder.append(managerLocalise("tsched.autoplay.notFound", lang, track.getInfo().title)).append("\n");
                        } else {
                            errorBuilder.append(managerLocalise("tsched.autoplay.noSimilar", lang));
                        }
                        try {
                            originalEventChannel.sendMessageEmbeds(createQuickError(errorBuilder.toString(), lang)).queue();
                        } catch (InsufficientPermissionException ignored) {
                        }
                    } else {
                        PlayerManager.getInstance().loadAutoplayBatch(eventOrChannel, songs, guildID);
                    }
                });
            } else { // is not autoplaying
                playNextTrack(player, originalEventChannel);
            }
        }
    }


    private static void logEarlyTrackFinish(AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason != AudioTrackEndReason.FINISHED) {
            return;
        }
        long pos = track.getPosition();
        long duration = track.getDuration();
        long metaLength = track.getInfo().length;
        if (duration <= 0 || duration > 432000000L) {
            return;
        }
        long shortfallMs = duration - pos;
        if (shortfallMs <= 500) {
            return;
        }
        System.err.println("Track finished early: " + track.getInfo().uri
                + " pos=" + pos
                + " duration=" + duration
                + " metaLength=" + metaLength
                + " shortfallMs=" + shortfallMs);
    }

    private void updateNowPlayingForTrack(AudioTrack track) {
        PlayerManager.TrackData trackData = (PlayerManager.TrackData) track.getUserData();
        if (trackData == null) {
            return;
        }
        long guildID = trackData.guildId;
        CompletableFuture.runAsync(() -> {
            try {
                if (!LastFMManager.hasAPI) {
                    return;
                }
                Guild guild = getBot().getGuildById(guildID);
                if (guild == null || guild.getSelfMember().getVoiceState() == null) {
                    return;
                }
                AudioChannelUnion channel = guild.getSelfMember().getVoiceState().getChannel();
                if (channel != null) {
                    vcUpdateNowPlaying(channel, track);
                }
            } catch (RuntimeException exception) {
                System.err.println("Could not update Last.fm now playing in guild " + guildID);
                exception.printStackTrace();
            }
        });
    }

    private void scrobbleFinishedTrack(AudioTrack track, long guildID) {
        CompletableFuture.runAsync(() -> {
            try {
                if (!LastFMManager.hasAPI) {
                    return;
                }
                Guild guild = getBot().getGuildById(guildID);
                if (guild == null || guild.getSelfMember().getVoiceState() == null) {
                    return;
                }
                AudioChannelUnion channel = guild.getSelfMember().getVoiceState().getChannel();
                if (channel != null) {
                    vcScrobble(channel, track);
                }
            } catch (RuntimeException exception) {
                System.err.println("Could not scrobble finished track in guild " + guildID);
                exception.printStackTrace();
            }
        });
    }

    private void handleTrackFailure(GuildMessageChannelUnion originalEventChannel, AudioPlayer player, AudioTrack track) {
        long guildID = originalEventChannel.getGuild().getIdLong();

        int failCount = guildFailCount.getOrDefault(guildID, 0) + 1;
        guildFailCount.put(guildID, failCount);

        System.err.println("Failed to load track " + track.getInfo().uri + " | fail count: " + failCount);
        if (failCount == 1) { // if fails once, retry the track.
            retryTrack(track);
        } else if (failCount < 3) { // if it is less than 3 but not 1, handle a regular failure.
            handleRegularFailure(originalEventChannel, player);
        } else { // if it is more than 3, worry (network issue usually).
            handleCriticalFailure(originalEventChannel);
        }
    }

    private void handleRegularFailure(GuildMessageChannelUnion originalEventChannel, AudioPlayer player) {
        long guildID = originalEventChannel.getGuild().getIdLong();
        Map<String, String> lang = guildLocales.get(originalEventChannel.getGuild().getIdLong());

        try {
            originalEventChannel.sendMessageEmbeds(createQuickError(managerLocalise("tsched.regfail", lang), lang)).queue();
        } catch (InsufficientPermissionException ignored) {
            // This should not be logged.
        }

        if (queue.isEmpty()) {
            guildFailCount.put(guildID, 0);
        } else {
            playNextTrack(player, originalEventChannel);
        }
    }

    private void handleCriticalFailure(GuildMessageChannelUnion originalEventChannel) {
        long guildID = originalEventChannel.getGuild().getIdLong();
        guildFailCount.put(guildID, 0);
        Map<String, String> lang = guildLocales.get(originalEventChannel.getGuild().getIdLong());

        MessageEmbed failureEmbed = createQuickEmbed(
                managerLocalise("tsched.critfail.title", lang),
                managerLocalise("tsched.critfail.description", lang),
                managerLocalise("tsched.critfail.footer", lang)
        );

        try {
            originalEventChannel.sendMessageEmbeds(failureEmbed).queue();
        } catch (InsufficientPermissionException ignored) {
            // This should not be logged.
        }
    }

    private void retryTrack(AudioTrack track) {
        this.player.startTrack(track.makeClone(), false);
    }

    private void playNextTrack(AudioPlayer player, GuildMessageChannelUnion originalEventChannel) {
        long guildID = originalEventChannel.getGuild().getIdLong();
        Map<String, String> lang = guildLocales.get(guildID);

        trackLoops.put(guildID, 0);
        nextTrack();
        AudioTrack nextTrack = player.getPlayingTrack();
        if (nextTrack == null) {
            return;
        }

        EmbedBuilder eb = new EmbedBuilder();

        eb.setTitle(managerLocalise("tsched.playnext.nowPlaying", lang, (nextTrack.getInfo().title.isEmpty() ?
                nextTrack.getInfo().uri :
                nextTrack.getInfo().title)), nextTrack.getInfo().uri
        );

        eb.appendDescription(managerLocalise("main.channel", lang));
        eb.appendDescription((nextTrack.getInfo().author.isEmpty() ?
                lang.get("main.unknown") :
                nextTrack.getInfo().author));

        eb.addField(managerLocalise("main.duration", lang),
                nextTrack.getInfo().length > 432000000 ? lang.get("main.unknown") :
                        toSimpleTimestamp(nextTrack.getInfo().length),
                true
        );

        String name;
        name = ((PlayerManager.TrackData) nextTrack.getUserData()).username;
        if (!name.isEmpty()) {
            eb.setFooter(managerLocalise("tsched.playnext.playedBy", lang, name));
        }

        if (PlayerManager.getInstance().getThumbURL(nextTrack) != null) {
            eb.setThumbnail(PlayerManager.getInstance().getThumbURL(nextTrack));
        }
        eb.setColor(botColour);
        try {
            originalEventChannel.sendMessageEmbeds(eb.build()).queue();
        } catch (InsufficientPermissionException ignored) {
            // this should not be logged.
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        PlayerManager.TrackData trackData = (PlayerManager.TrackData) track.getUserData();
        Guild guild = getGuildChannelFromID(trackData.channelId).getGuild();

        System.err.println("AudioPlayer in " + guild.getIdLong() + " (" + guild.getName() + ") threw friendly exception on track " + track.getInfo().uri);
        System.err.println(exception.getMessage());
    }
}

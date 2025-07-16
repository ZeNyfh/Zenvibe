package Bots.lavaplayer;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static Bots.CommandEvent.createQuickError;
import static Bots.EmbedHelper.createQuickEmbed;
import static Bots.EmbedHelper.toSimpleTimestamp;
import static Bots.managers.LocaleManager.managerLocalise;
import static Bots.Main.*;
import static Bots.lavaplayer.LastFMManager.filterMetadata;

public class TrackScheduler extends AudioEventAdapter {

    public final AudioPlayer player;
    public final BlockingQueue<AudioTrack> queue;
    private final Map<Long, Integer> guildFailCount = new HashMap<>();

    public TrackScheduler(AudioPlayer player) {
        this.player = player;
        this.queue = new LinkedBlockingQueue<>();
    }

    public void queue(AudioTrack track) {
        if (!this.player.startTrack(track, true)) {
            this.queue.offer(track);
        }
    }

    public void nextTrack() {
        this.player.startTrack(this.queue.poll(), false);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {

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
            if (LoopGuilds.contains(guildID)) { // track is looping
                AudioTrack loopTrack = track.makeClone();
                this.player.startTrack(loopTrack, false);
                trackLoops.put(guildID, trackLoops.get(guildID) + 1);
                return;
            }
            if (LoopQueueGuilds.contains(guildID)) { // queue is looping
                AudioTrack loopTrack = track.makeClone();
                nextTrack();
                queue(loopTrack);
                return;
            }

            if (AutoplayGuilds.contains(guildID)) { // is autoplaying
                StringBuilder errorBuilder = new StringBuilder();
                String searchTerm = LastFMManager.getSimilarSongs(track, guildID);
                Map<String, String> locale = guildLocales.get(guildID);
                boolean canAutoPlay = true;

                if (searchTerm.equals("notfound")) {
                    errorBuilder.append("❌ **")
                            .append(managerLocalise("main.error", lang))
                            .append(":**\n")
                            .append(managerLocalise("tsched.autoplay.notFound", lang, track.getInfo().title))
                            .append("\n");
                    canAutoPlay = false;
                }
                if (searchTerm.equals("none")) {
                    errorBuilder.append("❌ **")
                            .append(managerLocalise("main.error", lang))
                            .append(":**\n")
                            .append(managerLocalise("tsched.autoplay.noSimilar", lang));
                    canAutoPlay = false;
                }
                if (searchTerm.isEmpty()) {
                    errorBuilder.append("❌ **")
                            .append(managerLocalise("main.error", lang))
                            .append(":**\n")
                            .append(managerLocalise("tsched.autoplay.unknownError", lang));
                    canAutoPlay = false;
                }

                if (canAutoPlay) {
                    // TODO: will be replaced by https://github.com/ZeNyfh/Zenvibe/pull/166
                    String artistName = (track.getInfo().author.isEmpty() || track.getInfo().author == null)
                            ? filterMetadata(track.getInfo().title.toLowerCase())
                            : filterMetadata(track.getInfo().author.toLowerCase());
                    String title = filterMetadata(track.getInfo().title.toLowerCase());
                    PlayerManager.getInstance().loadAndPlay(trackData.eventOrChannel, "ytsearch:" + artistName + " " + title, true);
                    createQuickEmbed(managerLocalise("tsched.autoplay.queued", lang), artistName + " - " + title);
                } else { // cannot autoplay
                    originalEventChannel.sendMessageEmbeds(createQuickError(errorBuilder.toString(), lang)).queue();
                    // go to next track in the queue
                    playNextTrack(player, originalEventChannel);
                }
            } else { // is not autoplaying
                playNextTrack(player, originalEventChannel);
            }
        }
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

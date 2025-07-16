package Bots.commands.music;

import Bots.BaseCommand;
import Bots.CommandEvent;
import Bots.CommandStateChecker.Check;
import Bots.lavaplayer.GuildMusicManager;
import Bots.lavaplayer.LastFMManager;
import Bots.lavaplayer.PlayerManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static Bots.EmbedHelper.createQuickEmbed;
import static Bots.EmbedHelper.toSimpleTimestamp;
import static Bots.Main.*;
import static Bots.lavaplayer.LastFMManager.filterMetadata;

public class CommandSkip extends BaseCommand {
    @Override
    public Check[] getChecks() {
        return new Check[]{Check.IS_IN_SAME_VC, Check.IS_PLAYING};
    }

    @Override
    public void execute(CommandEvent event) {
        final Member self = event.getGuild().getSelfMember();
        final GuildVoiceState selfVoiceState = Objects.requireNonNull(self.getVoiceState());
        final GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        final AudioPlayer audioPlayer = musicManager.audioPlayer;

        skipCountGuilds.putIfAbsent(event.getGuild().getIdLong(), new ArrayList<>()); // required, otherwise there is a nullPointerException

        List<Member> votes = skipCountGuilds.get(event.getGuild().getIdLong());
        if (votes.contains(event.getMember())) {
            event.replyEmbeds(event.createQuickError(event.localise("cmd.skip.alreadyVoted")));
            return;
        } else {
            votes.add(event.getMember());
            skipCountGuilds.put(event.getGuild().getIdLong(), votes);
        }

        int effectiveMemberCount = 0;
        int votedMemberCount = 0;
        for (Member member : Objects.requireNonNull(selfVoiceState.getChannel()).getMembers()) {
            if (!member.getUser().isBot()) {
                effectiveMemberCount++;
            }
            if (votes.contains(member)) {
                votedMemberCount++;
            }
        }

        if (votedMemberCount >= effectiveMemberCount / 2) {
            StringBuilder messageBuilder = new StringBuilder();
            if (AutoplayGuilds.contains(event.getGuild().getIdLong())) {
                String searchTerm = LastFMManager.getSimilarSongs(audioPlayer.getPlayingTrack(), event.getGuild().getIdLong());
                boolean canPlay = true;
                if (searchTerm.equals("notfound")) {
                    messageBuilder.append("❌ **")
                            .append(event.localise("main.error"))
                            .append(":**\n")
                            .append(event.localise("cmd.skip.failedToFind", audioPlayer.getPlayingTrack().getInfo().title));
                    canPlay = false;
                }
                if (searchTerm.equals("none")) {
                    messageBuilder.append("❌ **")
                            .append(event.localise("main.error"))
                            .append(":**\n")
                            .append(event.localise("cmd.skip.couldNotFind"));
                    canPlay = false;
                }
                if (searchTerm.isEmpty()) {
                    messageBuilder.append("❌ **")
                            .append(event.localise("main.error"))
                            .append(":**\n")
                            .append(event.localise("cmd.skip.noSearchTerm"));
                    canPlay = false;
                }
                if (canPlay) {
                    AudioTrack track = audioPlayer.getPlayingTrack();
                    // TODO: should be replaced with actual logic checking if last.fm has either the author or the artist name in the title.
                    String artistName = (track.getInfo().author.isEmpty() || track.getInfo().author == null)
                            ? filterMetadata((track.getInfo().title).toLowerCase())
                            : filterMetadata(track.getInfo().author.toLowerCase());
                    String title = filterMetadata(track.getInfo().title.toLowerCase());
                    PlayerManager.getInstance().loadAndPlay(event, "ytsearch:" + artistName + " - " + title, false);
                    messageBuilder.append("♾️ ")
                            .append(event.localise("cmd.skip.autoplayQueued", artistName, title));
                }
            }
            musicManager.scheduler.nextTrack();
            skipCountGuilds.remove(event.getGuild().getIdLong());
            if (musicManager.audioPlayer.getPlayingTrack() == null) { // if there is nothing playing after the skip command
                event.replyEmbeds(createQuickEmbed(" ", event.localise("cmd.skip.skippedTheTrack")));
            } else { // if there is something playing after the skip command
                EmbedBuilder eb = new EmbedBuilder();
                eb.setColor(botColour);
                if (musicManager.audioPlayer.getPlayingTrack().getInfo().title != null) {
                    eb.setTitle(event.localise("cmd.skip.skippedTo", musicManager.audioPlayer.getPlayingTrack().getInfo().title), musicManager.audioPlayer.getPlayingTrack().getInfo().uri);
                } else {
                    eb.setTitle(event.localise("cmd.skip.skippedTo.unknown"));
                    eb.appendDescription(event.localise("cmd.skip.nowPlaying", musicManager.audioPlayer.getPlayingTrack().getInfo().uri));
                }
                if (musicManager.audioPlayer.getPlayingTrack().getInfo().author != null) {
                    eb.appendDescription(event.localise("cmd.skip.channel", musicManager.audioPlayer.getPlayingTrack().getInfo().author));
                }
                eb.appendDescription(event.localise("cmd.skip.duration", toSimpleTimestamp(musicManager.audioPlayer.getPlayingTrack().getInfo().length)));
                eb.appendDescription(messageBuilder);
                event.replyEmbeds(eb.build());
            }
        } else {
            event.replyEmbeds(createQuickEmbed(event.localise("cmd.skip.voted.title"),
                    event.localise("cmd.skip.voted.description", votedMemberCount, effectiveMemberCount / 2)));
        }
    }

    @Override
    public String[] getNames() {
        return new String[]{"skip", "s", "voteskip", "vs"};
    }

    @Override
    public Category getCategory() {
        return Category.Music;
    }

    @Override
    public String getDescription() {
        return "Casts a vote or skips the current song.";
    }

    @Override
    public long getRatelimit() {
        return 2500;
    }
}
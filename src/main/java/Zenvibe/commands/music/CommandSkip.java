package Zenvibe.commands.music;

import Zenvibe.BaseCommand;
import Zenvibe.CommandEvent;
import Zenvibe.CommandStateChecker.Check;
import Zenvibe.lavaplayer.AutoplayTarget;
import Zenvibe.lavaplayer.GuildMusicManager;
import Zenvibe.lavaplayer.ListenBrainzManager;
import Zenvibe.lavaplayer.PlayerManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static Zenvibe.Main.*;
import static Zenvibe.lavaplayer.LastFMManager.vcScrobble;
import static Zenvibe.managers.EmbedManager.createQuickEmbed;
import static Zenvibe.managers.EmbedManager.toSimpleTimestamp;

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

        skipCountGuilds.putIfAbsent(event.getGuild().getIdLong(), new ArrayList<>());

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
            PlayerManager.TrackData trackData = (PlayerManager.TrackData) audioPlayer.getPlayingTrack().getUserData();
            trackData.wasSkipped = true;
            audioPlayer.getPlayingTrack().setUserData(trackData);
            if ((double) audioPlayer.getPlayingTrack().getPosition() / audioPlayer.getPlayingTrack().getDuration() >= 0.5) {
                vcScrobble(selfVoiceState.getChannel(), audioPlayer.getPlayingTrack());
            }

            AudioTrack finishing = audioPlayer.getPlayingTrack();
            boolean autoplaying = AutoplayGuilds.contains(event.getGuild().getIdLong());
            musicManager.scheduler.nextTrack();
            skipCountGuilds.remove(event.getGuild().getIdLong());

            MessageEmbed skipEmbed = buildSkipEmbed(event, musicManager, autoplaying);
            if (autoplaying && finishing != null) {
                event.replyEmbeds(response -> CompletableFuture.runAsync(() -> {
                    List<AutoplayTarget> targets = ListenBrainzManager.getSimilarTargets(finishing, event.getGuild().getIdLong(), 1);
                    if (targets.isEmpty()) {
                        response.editMessageEmbeds(buildSkipEmbed(event, musicManager, false));
                        return;
                    }
                    AutoplayTarget target = targets.getFirst();
                    PlayerManager.getInstance()
                            .loadAndPlay(event, target.toLoadQuery(PlayerManager.hasSpotify()), false, true,
                                    target.artist(), target.title())
                            .whenComplete((ignored, error) -> {
                                if (error != null) {
                                    error.printStackTrace();
                                    response.editMessageEmbeds(buildSkipEmbed(event, musicManager, false));
                                    return;
                                }
                                response.editMessageEmbeds(buildSkipEmbed(event, musicManager, true,
                                        event.localise("cmd.skip.autoplayQueued", target.artist(), target.title())));
                            });
                }), skipEmbed);
            } else {
                event.replyEmbeds(skipEmbed);
            }
        } else {
            event.replyEmbeds(createQuickEmbed(event.localise("cmd.skip.voted.title"),
                    event.localise("cmd.skip.voted.description", votedMemberCount, effectiveMemberCount / 2)));
        }
    }

    private static MessageEmbed buildSkipEmbed(CommandEvent event, GuildMusicManager musicManager, boolean autoplayLoading) {
        return buildSkipEmbed(event, musicManager, autoplayLoading, null);
    }

    private static MessageEmbed buildSkipEmbed(CommandEvent event, GuildMusicManager musicManager,
                                               boolean showAutoplay, String autoplayLine) {
        AudioTrack playing = musicManager.audioPlayer.getPlayingTrack();
        if (playing == null) {
            return createQuickEmbed(" ", event.localise("cmd.skip.skippedTheTrack"));
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(botColour);
        if (playing.getInfo().title != null) {
            eb.setTitle(event.localise("cmd.skip.skippedTo", playing.getInfo().title), playing.getInfo().uri);
        } else {
            eb.setTitle(event.localise("cmd.skip.skippedTo.unknown"));
            eb.appendDescription(event.localise("cmd.skip.nowPlaying", playing.getInfo().uri));
        }
        if (playing.getInfo().author != null) {
            eb.appendDescription(event.localise("cmd.skip.channel", playing.getInfo().author));
        }
        eb.appendDescription(event.localise("cmd.skip.duration", toSimpleTimestamp(playing.getInfo().length)));
        if (showAutoplay) {
            if (autoplayLine != null && !autoplayLine.isBlank()) {
                eb.appendDescription("\n♾️ " + autoplayLine);
            } else {
                eb.appendDescription("\n♾️ " + event.localise("cmd.ap.loadingTracks"));
            }
        }
        return eb.build();
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

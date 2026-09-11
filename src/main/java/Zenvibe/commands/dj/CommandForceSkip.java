package Zenvibe.commands.dj;

import Zenvibe.BaseCommand;
import Zenvibe.CommandEvent;
import Zenvibe.CommandStateChecker.Check;
import Zenvibe.lavaplayer.AutoplayTarget;
import Zenvibe.lavaplayer.GuildMusicManager;
import Zenvibe.lavaplayer.ListenBrainzManager;
import Zenvibe.lavaplayer.PlayerManager;
import Zenvibe.lavaplayer.RadioDataFetcher;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static Zenvibe.Main.AutoplayGuilds;
import static Zenvibe.Main.skipCountGuilds;
import static Zenvibe.lavaplayer.LastFMManager.vcScrobble;
import static Zenvibe.managers.EmbedManager.createQuickEmbed;
import static Zenvibe.managers.EmbedManager.sanitise;

public class CommandForceSkip extends BaseCommand {
    @Override
    public Check[] getChecks() {
        return new Check[]{Check.IS_DJ, Check.IS_IN_SAME_VC, Check.IS_PLAYING};
    }

    @Override
    public void execute(CommandEvent event) {
        final GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        final AudioPlayer audioPlayer = musicManager.audioPlayer;
        AudioTrack finishing = audioPlayer.getPlayingTrack();
        boolean autoplaying = AutoplayGuilds.contains(event.getGuild().getIdLong());

        if (event.getArgs().length > 1 && event.getArgs()[1].matches("^\\d+$")) {
            markSkipped(audioPlayer, event);
            int givenPosition = Integer.parseInt(event.getArgs()[1]);
            if (givenPosition - 1 >= musicManager.scheduler.queue.size()) {
                musicManager.scheduler.queue.clear();
                musicManager.scheduler.nextTrack();
                event.replyEmbeds(createQuickEmbed(" ", "⏩ " + event.localise("cmd.fs.skippedQueue")));
            } else {
                List<AudioTrack> list = new ArrayList<>(musicManager.scheduler.queue);
                musicManager.scheduler.queue.clear();
                musicManager.scheduler.queue.addAll(list.subList(Math.max(0, Math.min(givenPosition, list.size()) - 1), list.size()));
                musicManager.scheduler.nextTrack();
                AudioTrackInfo trackInfo = musicManager.audioPlayer.getPlayingTrack().getInfo();
                String title = trackInfo.title;
                if (trackInfo.isStream) {
                    String streamTitle = RadioDataFetcher.getStreamTitle(trackInfo.uri);
                    if (streamTitle != null) {
                        title = streamTitle;
                    }
                }
                String trackHyperLink = "__**[" + sanitise(title) + "](" + trackInfo.uri + ")**__";
                event.replyEmbeds(createQuickEmbed(" ", "⏩ " + event.localise("cmd.fs.skippedToPos",
                        event.getArgs()[1], trackHyperLink)));
            }
            if (autoplaying && finishing != null) {
                queueAutoplaySilently(event, finishing);
            }
        } else {
            markSkipped(audioPlayer, event);
            MessageEmbed initial;
            if (!musicManager.scheduler.queue.isEmpty()) {
                musicManager.scheduler.nextTrack();
                initial = buildForceSkipEmbed(event, musicManager, autoplaying, null);
            } else {
                musicManager.scheduler.nextTrack();
                initial = buildForceSkipEmbed(event, musicManager, autoplaying, null);
            }

            if (autoplaying && finishing != null) {
                event.replyEmbeds(response -> CompletableFuture.runAsync(() -> {
                    List<AutoplayTarget> targets = ListenBrainzManager.getSimilarTargets(finishing, event.getGuild().getIdLong(), 1);
                    if (targets.isEmpty()) {
                        response.editMessageEmbeds(buildForceSkipEmbed(event, musicManager, false, null));
                        return;
                    }
                    AutoplayTarget target = targets.getFirst();
                    PlayerManager.getInstance()
                            .loadAndPlay(event, target.toLoadQuery(PlayerManager.hasSpotify()), false, true,
                                    target.artist(), target.title())
                            .whenComplete((ignored, error) -> {
                                if (error != null) {
                                    error.printStackTrace();
                                    response.editMessageEmbeds(buildForceSkipEmbed(event, musicManager, false, null));
                                    return;
                                }
                                response.editMessageEmbeds(buildForceSkipEmbed(event, musicManager, true,
                                        event.localise("cmd.fs.autoplayQueued", target.artist(), target.title())));
                            });
                }), initial);
            } else {
                event.replyEmbeds(initial);
            }
        }
        skipCountGuilds.remove(event.getGuild().getIdLong());
    }

    private static void markSkipped(AudioPlayer audioPlayer, CommandEvent event) {
        PlayerManager.TrackData trackData = (PlayerManager.TrackData) audioPlayer.getPlayingTrack().getUserData();
        trackData.wasSkipped = true;
        audioPlayer.getPlayingTrack().setUserData(trackData);
        if ((double) audioPlayer.getPlayingTrack().getPosition() / audioPlayer.getPlayingTrack().getDuration() >= 0.5) {
            vcScrobble(Objects.requireNonNull(event.getGuild().getSelfMember().getVoiceState()).getChannel(), audioPlayer.getPlayingTrack());
        }
    }

    private static void queueAutoplaySilently(CommandEvent event, AudioTrack finishing) {
        long guildId = event.getGuild().getIdLong();
        CompletableFuture.runAsync(() -> {
            List<AutoplayTarget> targets = ListenBrainzManager.getSimilarTargets(finishing, guildId, 1);
            if (targets.isEmpty()) {
                return;
            }
            AutoplayTarget target = targets.getFirst();
            PlayerManager.getInstance().loadAndPlay(event, target.toLoadQuery(PlayerManager.hasSpotify()), false, true,
                    target.artist(), target.title());
        });
    }

    private static MessageEmbed buildForceSkipEmbed(CommandEvent event, GuildMusicManager musicManager,
                                                    boolean showAutoplay, String autoplayLine) {
        AudioTrack playing = musicManager.audioPlayer.getPlayingTrack();
        if (playing == null) {
            String body = "⏩ " + event.localise("cmd.fs.skipped");
            if (showAutoplay) {
                body += "\n\n♾️ " + (autoplayLine != null ? autoplayLine : event.localise("cmd.ap.loadingTracks"));
            }
            return createQuickEmbed(" ", body.trim());
        }

        AudioTrackInfo trackInfo = playing.getInfo();
        String title = trackInfo.title;
        boolean isHTTP = (trackInfo.uri.contains("youtube") || trackInfo.uri.contains("soundcloud")
                || trackInfo.uri.contains("twitch") || trackInfo.uri.contains("bandcamp") || trackInfo.uri.contains("spotify"));
        if (trackInfo.isStream && !isHTTP) {
            String streamTitle = RadioDataFetcher.getStreamTitle(trackInfo.uri);
            if (streamTitle != null) {
                title = streamTitle;
            }
        }
        String trackHyperLink = "__**[" + title + "](" + trackInfo.uri + ")**__";
        String body = "⏩ " + event.localise("cmd.fs.skippedToTrack", trackHyperLink);
        if (showAutoplay) {
            body += "\n\n♾️ " + (autoplayLine != null ? autoplayLine : event.localise("cmd.ap.loadingTracks"));
        }
        return createQuickEmbed(" ", body.trim());
    }

    @Override
    public Category getCategory() {
        return Category.DJ;
    }

    @Override
    public String[] getNames() {
        return new String[]{"forceskip", "fs"};
    }

    @Override
    public void ProvideOptions(SlashCommandData slashCommand) {
        slashCommand.addOption(OptionType.INTEGER, "amount", "Amount of tracks to skip from the queue.", false);
    }

    @Override
    public String getOptions() {
        return "[Number]";
    }

    @Override
    public String getDescription() {
        return "Skips the song forcefully.";
    }

    @Override
    public long getRatelimit() {
        return 1000;
    }
}

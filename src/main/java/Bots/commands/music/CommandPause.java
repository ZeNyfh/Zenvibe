package Bots.commands.music;

import Bots.BaseCommand;
import Bots.CommandEvent;
import Bots.CommandStateChecker;
import Bots.CommandStateChecker.Check;
import Bots.lavaplayer.GuildMusicManager;
import Bots.lavaplayer.PlayerManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;

import static Bots.CommandStateChecker.PerformChecks;
import static Bots.EmbedHelper.createQuickEmbed;

public class CommandPause extends BaseCommand {
    @Override
    public Check[] getChecks() {
        return new Check[]{Check.IS_IN_SAME_VC};
    }

    @Override
    public void execute(CommandEvent event) {
        final GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        final AudioPlayer audioPlayer = musicManager.audioPlayer;

        if (audioPlayer.getPlayingTrack() == null && !musicManager.scheduler.queue.isEmpty()) {
            musicManager.scheduler.nextTrack(); // No embed data :( (playNextTrack is private, dunno if its worth publicising)
            event.replyEmbeds(createQuickEmbed("\uD83C\uDFB5 ▶", event.localise("cmd.pause.resumed")));
        } else {
            CommandStateChecker.CheckResult checkResult = PerformChecks(event, Check.IS_DJ, Check.IS_PLAYING);
            if (!checkResult.succeeded()) {
                event.replyEmbeds(createQuickEmbed(event.localise("statecheck.notAllowed"), checkResult.getMessage()));
                return;
            }
            if (audioPlayer.isPaused()) {
                audioPlayer.setPaused(false);
                event.replyEmbeds(createQuickEmbed("\uD83C\uDFB5 ▶", event.localise("cmd.pause.resumed")));
            } else {
                audioPlayer.setPaused(true);
                event.replyEmbeds(createQuickEmbed("\uD83C\uDFB5 ⏸", event.localise("cmd.pause.paused")));
            }
        }
    }

    @Override
    public Category getCategory() {
        return Category.DJ;
    }

    @Override
    public String[] getNames() {
        return new String[]{"pause", "resume", "continue", "unpause", "stop", "start"};
    }

    @Override
    public String getDescription() {
        return "pauses or resumes the current track.";
    }

    @Override
    public long getRatelimit() {
        return 2500;
    }
}
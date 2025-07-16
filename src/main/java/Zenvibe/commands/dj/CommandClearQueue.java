package Zenvibe.commands.dj;

import Zenvibe.BaseCommand;
import Zenvibe.CommandEvent;
import Zenvibe.CommandStateChecker.Check;
import Zenvibe.lavaplayer.GuildMusicManager;
import Zenvibe.lavaplayer.PlayerManager;

import static Zenvibe.Main.skipCountGuilds;

public class CommandClearQueue extends BaseCommand {
    @Override
    public Check[] getChecks() {
        return new Check[]{Check.IS_DJ, Check.IS_IN_SAME_VC};
    }

    @Override
    public void execute(CommandEvent event) {
        final GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        if (musicManager.scheduler.queue.isEmpty() && musicManager.audioPlayer.getPlayingTrack() == null) {
            event.replyEmbeds(event.createQuickError(event.localise("cmd.cq.empty")));
            return;
        }

        skipCountGuilds.remove(event.getGuild().getIdLong());
        musicManager.scheduler.queue.clear();
        musicManager.scheduler.nextTrack();
        musicManager.audioPlayer.destroy();
        event.replyEmbeds(event.createQuickSuccess(event.localise("cmd.cq.cleared")));
    }

    @Override
    public String[] getNames() {
        return new String[]{"clearqueue", "clear queue", "queueclear", "queue clear", "clearq", "clear q"};
    }

    @Override
    public Category getCategory() {
        return Category.DJ;
    }

    @Override
    public String getDescription() {
        return "Clears the current queue.";
    }

    @Override
    public long getRatelimit() {
        return 5000;
    }
}

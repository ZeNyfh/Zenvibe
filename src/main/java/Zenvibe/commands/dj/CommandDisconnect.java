package Zenvibe.commands.dj;

import Zenvibe.BaseCommand;
import Zenvibe.CommandEvent;
import Zenvibe.CommandStateChecker.Check;
import Zenvibe.lavaplayer.GuildMusicManager;
import Zenvibe.lavaplayer.PlayerManager;

import static Zenvibe.Main.skipCountGuilds;

public class CommandDisconnect extends BaseCommand {
    @Override
    public Check[] getChecks() {
        return new Check[]{Check.IS_DJ, Check.IS_BOT_IN_ANY_VC};
    }

    @Override
    public void execute(CommandEvent event) {
        final GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        musicManager.scheduler.queue.clear();
        event.getGuild().getAudioManager().closeAudioConnection();
        musicManager.scheduler.nextTrack();
        skipCountGuilds.remove(event.getGuild().getIdLong());
        event.replyEmbeds(event.createQuickSuccess(event.localise("cmd.dc.disconnected")));
    }

    @Override
    public String[] getNames() {
        return new String[]{"disconnect", "dc", "leave", "fu" + "ckoff", "fu" + "ck off", "shutup", "shut up"};
    }

    @Override
    public Category getCategory() {
        return Category.DJ;
    }

    @Override
    public String getDescription() {
        return "Makes the bot forcefully leave the vc.";
    }

    @Override
    public long getRatelimit() {
        return 5000;
    }
}

package Zenvibe.commands.music;

import Zenvibe.BaseCommand;
import Zenvibe.CommandEvent;
import Zenvibe.CommandStateChecker.Check;

import static Zenvibe.managers.EmbedManager.createQuickEmbed;
import static Zenvibe.Main.LoopGuilds;

public class CommandLoop extends BaseCommand {
    @Override
    public Check[] getChecks() {
        return new Check[]{Check.IS_IN_SAME_VC, Check.IS_PLAYING};
    }

    @Override
    public void execute(CommandEvent event) {
        if (LoopGuilds.contains(event.getGuild().getIdLong())) {
            event.replyEmbeds(createQuickEmbed("❌ \uD83D\uDD01", event.localise("cmd.loop.notLooping")));
            LoopGuilds.remove(event.getGuild().getIdLong());
        } else {
            event.replyEmbeds(createQuickEmbed("✅ \uD83D\uDD01", event.localise("cmd.loop.looping")));
            LoopGuilds.add(event.getGuild().getIdLong());
        }
    }

    @Override
    public Category getCategory() {
        return Category.Music;
    }

    @Override
    public String[] getNames() {
        return new String[]{"loop"};
    }

    @Override
    public String getDescription() {
        return "Loops the current track.";
    }

    @Override
    public long getRatelimit() {
        return 2500;
    }
}

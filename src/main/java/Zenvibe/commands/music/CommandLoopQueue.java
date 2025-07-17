package Zenvibe.commands.music;

import Zenvibe.BaseCommand;
import Zenvibe.CommandEvent;
import Zenvibe.CommandStateChecker.Check;

import static Zenvibe.managers.EmbedManager.createQuickEmbed;
import static Zenvibe.Main.LoopQueueGuilds;

public class CommandLoopQueue extends BaseCommand {
    @Override
    public Check[] getChecks() {
        return new Check[]{Check.IS_IN_SAME_VC, Check.IS_PLAYING};
    }

    @Override
    public void execute(CommandEvent event) {
        if (LoopQueueGuilds.contains(event.getGuild().getIdLong())) {
            event.replyEmbeds(createQuickEmbed("❌ \uD83D\uDD01", event.localise("cmd.lq.notLooping")));
            LoopQueueGuilds.remove(event.getGuild().getIdLong());
        } else {
            event.replyEmbeds(createQuickEmbed("✅ \uD83D\uDD01", event.localise("cmd.lq.looping")));
            LoopQueueGuilds.add(event.getGuild().getIdLong());
        }
    }

    @Override
    public String[] getNames() {
        return new String[]{"loopqueue", "loopq"};
    }

    @Override
    public Category getCategory() {
        return Category.Music;
    }

    @Override
    public String getDescription() {
        return "Loops the current queue.";
    }

    @Override
    public long getRatelimit() {
        return 2500;
    }
}

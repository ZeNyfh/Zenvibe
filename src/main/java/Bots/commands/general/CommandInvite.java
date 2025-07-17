package Bots.commands.general;

import Bots.BaseCommand;
import Bots.CommandEvent;

import static Bots.managers.EmbedManager.createQuickEmbed;

public class CommandInvite extends BaseCommand {
    @Override
    public void execute(CommandEvent event) {
        event.replyEmbeds(createQuickEmbed(event.localise("cmd.inv.haveFun"), "http://zenvibe.ddns.net/"));
    }

    @Override
    public Category getCategory() {
        return Category.General;
    }

    @Override
    public String[] getNames() {
        return new String[]{"invite"};
    }

    @Override
    public String getDescription() {
        return "Sends an invite to the bot.";
    }

    @Override
    public long getRatelimit() {
        return 5000;
    }
}

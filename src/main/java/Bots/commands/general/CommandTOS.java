package Bots.commands.general;

import Bots.BaseCommand;
import Bots.CommandEvent;

import static Bots.managers.EmbedManager.createQuickEmbed;

public class CommandTOS extends BaseCommand {

    @Override
    public void execute(CommandEvent event) {
        event.replyEmbeds(createQuickEmbed(event.localise("cmd.tos.TermsOfService"), "https://github.com/ZeNyfh/Zenvibe/blob/main/TOS.md"));
    }

    @Override
    public String[] getNames() {
        return new String[]{"tos", "terms of service", "termsofservice"};
    }

    @Override
    public Category getCategory() {
        return Category.General;
    }

    @Override
    public String getDescription() {
        return "Sends a link to the terms of service.";
    }

    @Override
    public long getRatelimit() {
        return 10000;
    }
}

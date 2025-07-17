package Bots.commands.general;

import Bots.BaseCommand;
import Bots.CommandEvent;

import static Bots.managers.EmbedManager.createQuickEmbed;

public class CommandGithub extends BaseCommand {
    @Override
    public void execute(CommandEvent event) {
        event.replyEmbeds(createQuickEmbed(" ", event.localise("cmd.git.message")));
    }

    @Override
    public Category getCategory() {
        return Category.General;
    }

    @Override
    public String[] getNames() {
        return new String[]{"github"};
    }

    @Override
    public String getDescription() {
        return "Sends github URL and some info.";
    }

    @Override
    public long getRatelimit() {
        return 5000;
    }
}

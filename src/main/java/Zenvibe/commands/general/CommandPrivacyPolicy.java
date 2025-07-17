package Zenvibe.commands.general;

import Zenvibe.BaseCommand;
import Zenvibe.CommandEvent;

import static Zenvibe.managers.EmbedManager.createQuickEmbed;

public class CommandPrivacyPolicy extends BaseCommand {

    @Override
    public void execute(CommandEvent event) {
        event.replyEmbeds(createQuickEmbed(event.localise("cmd.pp.privacyPolicy"), "https://github.com/ZeNyfh/Zenvibe/blob/main/PRIVACY_POLICY.md"));
    }

    @Override
    public String[] getNames() {
        return new String[]{"privacypolicy", "privacy policy", "pp"};
    }

    @Override
    public Category getCategory() {
        return Category.General;
    }

    @Override
    public String getDescription() {
        return "Sends a link to the privacy policy.";
    }

    @Override
    public long getRatelimit() {
        return 10000;
    }
}

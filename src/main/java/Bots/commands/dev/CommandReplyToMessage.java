package Bots.commands.dev;

import Bots.BaseCommand;
import Bots.CommandEvent;
import Bots.CommandStateChecker;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.Objects;

public class CommandReplyToMessage extends BaseCommand {
    @Override
    public CommandStateChecker.Check[] getChecks() {
        return new CommandStateChecker.Check[]{CommandStateChecker.Check.IS_DEV};
    }

    @Override
    public void execute(CommandEvent event) throws Exception {
        if (event.getArgs().length < 2) {
            event.replyEmbeds(event.createQuickError("Message not provided."));
            return;
        }
        String messageContent = event.getContentRaw().split(" ", 3)[2];
        try {
            Objects.requireNonNull(event.getJDA().getUserById(Long.parseLong(event.getArgs()[1]))).openPrivateChannel().queue(a -> a.sendMessage(messageContent).queue());
        } catch (Exception e) {
            e.printStackTrace();
            event.reply("Something went wrong when trying to send the message, check the console.");
            return;
        }
        event.reply("\uD83D\uDC4D");
    }

    @Override
    public void ProvideOptions(SlashCommandData slashCommand) {
        slashCommand.addOption(OptionType.STRING, "userid", "The UserID of the user to reply to.", true);
        slashCommand.addOption(OptionType.STRING, "message", "The message to reply to the user with.", true);
    }

    @Override
    public String[] getNames() {
        return new String[]{"reply"};
    }

    @Override
    public String getOptions() {
        return "<UserID> <String>";
    }

    @Override
    public Category getCategory() {
        return Category.Dev;
    }

    @Override
    public String getDescription() {
        return "Replies to a message. Intended for bug command replies.";
    }
}

package Zenvibe.commands.dev;

import Zenvibe.BaseCommand;
import Zenvibe.CommandEvent;
import Zenvibe.CommandStateChecker;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public class CommandReplyToMessage extends BaseCommand {
    @Override
    public CommandStateChecker.Check[] getChecks() {
        return new CommandStateChecker.Check[]{CommandStateChecker.Check.IS_DEV};
    }

    @Override
    public void execute(CommandEvent event) throws Exception {
        if (event.getArgs().length < 3) {
            event.replyEmbeds(event.createQuickError("Message not provided."));
            return;
        }
        String messageContent = event.getContentRaw().split(" ", 3)[2];
        long userId;
        try {
            userId = Long.parseLong(event.getArgs()[1]);
        } catch (NumberFormatException e) {
            event.replyEmbeds(event.createQuickError("Invalid UserID."));
            return;
        }
        event.getJDA().retrieveUserById(userId).queue(
                user -> user.openPrivateChannel().queue(
                        channel -> channel.sendMessage(messageContent).queue(
                                success -> event.reply("\uD83D\uDC4D"),
                                err -> fail(event, err)
                        ),
                        err -> fail(event, err)
                ),
                err -> fail(event, err)
        );
    }

    private static void fail(CommandEvent event, Throwable err) {
        err.printStackTrace();
        event.reply("Something went wrong when trying to send the message, check the console.");
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

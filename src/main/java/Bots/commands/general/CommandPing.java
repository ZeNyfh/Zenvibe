package Bots.commands.general;

import Bots.BaseCommand;
import Bots.CommandEvent;

import static java.lang.System.currentTimeMillis;

public class CommandPing extends BaseCommand {

    @Override
    public void execute(CommandEvent event) {
        long time = currentTimeMillis();
        event.reply(response -> response.editMessageFormat(
                event.localise("cmd.ping.ping", currentTimeMillis() - time) + "\n" +
                        event.localise("cmd.ping.gatewayPing", event.getJDA().getGatewayPing())), ".");
    }

    @Override
    public Category getCategory() {
        return Category.General;
    }

    @Override
    public String[] getNames() {
        return new String[]{"ping"};
    }

    @Override
    public String getDescription() {
        return "Shows you the bot's ping.";
    }

    @Override
    public long getRatelimit() {
        return 1000;
    }
}

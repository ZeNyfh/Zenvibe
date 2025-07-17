package Bots.commands.dev;

import Bots.BaseCommand;
import Bots.CommandEvent;
import Bots.CommandStateChecker.Check;
import net.dv8tion.jda.api.EmbedBuilder;

import java.util.*;

import static Bots.Main.botColour;
import static Bots.Main.commandUsageTracker;

public class CommandSendUsage extends BaseCommand {
    @Override
    public Check[] getChecks() {
        return new Check[]{Check.IS_DEV};
    }

    @Override
    public void execute(CommandEvent event) {
        Long[] values = (Long[]) commandUsageTracker.values().toArray(new Long[0]);
        Arrays.sort(values);
        Map<Long, List<String>> InverseReference = new HashMap<>();
        for (Object name : commandUsageTracker.keySet()) {
            Object value = commandUsageTracker.get(name);
            InverseReference.putIfAbsent((Long) value, new ArrayList<>());
            InverseReference.get((Long) value).add((String) name);
        }
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(botColour);
        eb.setTitle("**Usage Logs**");
        eb.appendDescription("```js\n");
        for (int i = values.length - 1; i >= 0; i--) {
            String reference = InverseReference.get(values[i]).remove(0);
            if (reference.endsWith("command")) continue;
            eb.appendDescription(reference + ": " + values[i] + "\n");
        }
        eb.appendDescription("\nslashcommand: "+ commandUsageTracker.get("slashcommand"));
        eb.appendDescription("\nprefixcommand: "+ commandUsageTracker.get("prefixcommand"));

        eb.appendDescription("```");
        event.replyEmbeds(eb.build());
    }

    @Override
    public String[] getNames() {
        return new String[]{"sendusage", "usage", "getusage", "get usage"};
    }

    @Override
    public Category getCategory() {
        return Category.Dev;
    }

    @Override
    public String getDescription() {
        return "Sends the usage log";
    }

    @Override
    public long getRatelimit() {
        return 10000;
    }
}

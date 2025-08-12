package Zenvibe.commands.dev;

import Zenvibe.BaseCommand;
import Zenvibe.CommandEvent;
import Zenvibe.CommandStateChecker.Check;
import net.dv8tion.jda.api.EmbedBuilder;

import java.util.*;

import static Zenvibe.Main.botColour;
import static Zenvibe.Main.commandUsageTracker;
import static Zenvibe.lavaplayer.AudioPlayerSendHandler.totalBytesSent;

public class CommandSendUsage extends BaseCommand {
    @Override
    public Check[] getChecks() {
        return new Check[]{Check.IS_DEV};
    }

    private static String formatDataUsage() {
        long totalBytes = totalBytesSent.get();
        long bytes = totalBytes % 1024;
        long kb = (totalBytes / 1024) % 1024;
        long mb = (totalBytes / (1024 * 1024)) % 1024;
        long gb = totalBytes / (1024 * 1024 * 1024);

        StringBuilder result = new StringBuilder();
        if (gb > 0) {
            result.append(gb).append("GB, ");
        }
        if (mb > 0 || gb > 0) {
            result.append(mb).append("MB, ");
        }
        if (kb > 0 || mb > 0 || gb > 0) {
            result.append(kb).append("KB");
        } else {
            result.append(bytes).append(" bytes");
        }

        return result.toString();
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
        eb.appendDescription("\nAudio data sent: "+formatDataUsage());

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

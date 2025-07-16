package Bots;

import Bots.managers.LocaleManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.ArrayList;
import java.util.Map;

import static Bots.managers.LocaleManager.getLocalisedTimeUnits;
import static Bots.Main.botColour;

public class EmbedHelper {

    public static MessageEmbed createQuickEmbed(String title, String description, String footer) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(title);
        eb.setColor(botColour);
        eb.setDescription(description);
        eb.setFooter(footer);
        return eb.build();
    }

    public static MessageEmbed createQuickEmbed(String title, String description) {
        return createQuickEmbed(title, description, null);
    }

    public static String toTimestamp(long seconds, long guildID) {
        Map<String, String> lang = Main.guildLocales.get(guildID);
        seconds /= 1000;
        if (seconds <= 0) {
            return String.format(getLocalisedTimeUnits(true, LocaleManager.TimeUnits.second.ordinal(), lang), "0");
        } else {
            long days = seconds / 86400;
            seconds %= 86400;
            long hours = seconds / 3600;
            seconds %= 3600;
            long minutes = seconds / 60;
            seconds %= 60;
            ArrayList<String> totalSet = new ArrayList<>();

            if (days != 0) {
                String dayLabel = days == 1 ? getLocalisedTimeUnits(false, LocaleManager.TimeUnits.day.ordinal(), lang) : getLocalisedTimeUnits(true, LocaleManager.TimeUnits.day.ordinal(), lang);
                totalSet.add(String.format(dayLabel, days));
            }

            if (hours != 0) {
                String hourLabel = hours == 1 ? getLocalisedTimeUnits(false, LocaleManager.TimeUnits.hour.ordinal(), lang) : getLocalisedTimeUnits(true, LocaleManager.TimeUnits.hour.ordinal(), lang);
                totalSet.add(String.format(hourLabel, hours));
            }

            if (minutes != 0) {
                String minuteLabel = minutes == 1 ? getLocalisedTimeUnits(false, LocaleManager.TimeUnits.minute.ordinal(), lang) : getLocalisedTimeUnits(true, LocaleManager.TimeUnits.minute.ordinal(), lang);
                totalSet.add(String.format(minuteLabel, minutes));
            }

            if (seconds != 0) {
                String secondLabel = seconds == 1 ? getLocalisedTimeUnits(false, LocaleManager.TimeUnits.second.ordinal(), lang) : getLocalisedTimeUnits(true, LocaleManager.TimeUnits.second.ordinal(), lang);
                totalSet.add(String.format(secondLabel, seconds));
            }
            return String.join(", ", totalSet);
        }
    }

    public static String toSimpleTimestamp(long seconds) {
        ArrayList<String> totalSet = new ArrayList<>();
        seconds /= 1000;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        String finalMinutes;
        String finalHours;

        if (hours != 0) {
            if (hours < 10) {
                finalHours = ("0" + hours);
                totalSet.add(finalHours + ":");
            } else {
                totalSet.add(hours + ":");
            }
        }
        if (minutes < 10) {
            finalMinutes = ("0" + minutes);
        } else {
            finalMinutes = String.valueOf(minutes);
        }
        totalSet.add(finalMinutes + ":");
        String finalSeconds;
        if (seconds < 10) {
            finalSeconds = ("0" + seconds);
        } else {
            finalSeconds = String.valueOf(seconds);
        }
        totalSet.add(finalSeconds);
        return String.join("", totalSet);
    }

    public static String replaceAllNoRegex(String input, String toReplace, String replacement) {
        StringBuilder result = new StringBuilder();
        int i = 0;

        while (i < input.length()) {
            if (i + toReplace.length() <= input.length() && input.startsWith(toReplace, i)) {
                result.append(replacement);
                i += toReplace.length();
            } else {
                result.append(input.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    public static String sanitise(String str) {
        String[] chars = new String[]{"_", "`", "#", "(", ")", "~"};

        for (String c : chars) {
            if (str.contains(c)) {
                str = replaceAllNoRegex(str, c, String.format("\\%s", c));
            }
        }
        return str;
    }
}

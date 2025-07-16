package Zenvibe.managers;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static Zenvibe.Main.guildLocales;
import static Zenvibe.managers.GuildDataManager.GetGuildConfig;

public class LocaleManager {
    public static Map<String, Map<String, String>> languages = new HashMap<>();
    static Pattern serializePattern = Pattern.compile("\\{(\\d+)}");

    public static void init(JDA bot) {
        if (bot.getSelfUser().getIdLong() == 920435768726532107L) { // only sync on init for the main bot, to update locales on another bot run the dev command "synclocales".
            syncLocaleFiles();
        }
        languages.put("english", readLocale("locales/en.txt")); // english needs to always be loaded first as it is used for comparison.
        for (File file : Objects.requireNonNull(Path.of("locales").toFile().listFiles())) {
            if (file.getName().equals("en.txt")) continue;
            Map<String, String> locale = readLocale(file.getAbsolutePath());
            languages.put(locale.get("main.languageName"), locale);
        }
        for (Guild g : bot.getGuilds()) {
            JSONObject config = GetGuildConfig(g.getIdLong());
            guildLocales.put(g.getIdLong(), languages.get(config.get("Locale")));
            guildLocales.putIfAbsent(g.getIdLong(), languages.get("english"));
        }
    }

    public static void syncLocaleFiles() { // this is public because of usage in CommandDev.
        String apiUrl = "https://api.github.com/repos/ZeNyfh/Zenvibe/contents/locales";
        String rawUrl = "https://raw.githubusercontent.com/ZeNyfh/Zenvibe/main/locales/";
        System.out.println("Syncing locales from git.");
        try {
            URL url = URI.create(apiUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (connection.getResponseCode() != 200) {
                System.err.println("Could not retrieve locales from github, update them manually from https://github.com/ZeNyfh/Zenvibe/tree/main/locales");
                return;
            }

            InputStream stream = connection.getInputStream();
            Scanner scanner = new Scanner(stream);
            StringBuilder jsonBuilder = new StringBuilder();
            while (scanner.hasNext()) jsonBuilder.append(scanner.nextLine());
            scanner.close();
            stream.close();


            JSONArray fileArray = (JSONArray) new JSONParser().parse(jsonBuilder.toString());
            for (Object obj : fileArray) {
                JSONObject fileObject = (JSONObject) obj;
                String filename = (String) fileObject.get("name");
                String fileURL = rawUrl + filename;
                System.out.println("updating locale from: " + fileURL);

                URL downloadURL = URI.create(fileURL).toURL();
                Path localePath = Path.of("locales", filename);

                Files.createDirectories(localePath.getParent()); // creation for first time setup cases.

                try (InputStream downloadStream = downloadURL.openStream()) {
                    Files.copy(downloadStream, localePath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static HashMap<String, String> readLocale(String localeFile) {
        File file = new File(localeFile);
        List<String> lines = new ArrayList<>();
        try {
            lines = Files.readAllLines(Path.of(file.getAbsolutePath()));
        } catch (Exception e) {
            e.printStackTrace();
        }

        HashMap<String, String> localeMap = new HashMap<>();

        for (String line : lines) {
            if (line.equals("\n") || line.startsWith("/") || !line.contains("=")) continue;

            String[] lineSplit = line.split("=", 2);
            if (lineSplit.length > 1) {
                localeMap.put(lineSplit[0], serializeString(lineSplit[1]));
            } else {
                System.err.println("Problematic locale: " + line);
            }
        }

        // missing key handler
        if (!localeFile.equals("locales/en.txt")) {
            boolean isMissing = false;
            for (String k : languages.get("english").keySet()) {
                if (!localeMap.containsKey(k)) {
                    if (!isMissing) {
                        String[] fileSplit = localeFile.split("/");
                        String langName = fileSplit[fileSplit.length - 1];
                        String flag = ":flag_" + langName.split("\\.", 2)[0] + ":";
                        System.err.println("## " + flag + " " + fileSplit[fileSplit.length - 1] + " is missing keys.");
                        isMissing = true;
                    }
                    System.err.println("- ENGLISH KEY: " + languages.get("english").get(k).replaceAll("%(.)\\$s", "{$1}"));
                    System.err.println("  - MISSING KEY: " + k);
                    localeMap.put(k, languages.get("english").getOrDefault(k, k)); // if the language is missing anything, fallback to english.
                }
            }
        }
        return localeMap;
    }

    // for use in CommandEvent.localise or when the lang map has to be passed in manually.
    public static String managerLocalise(String key, Map<String, String> lang, Object... args) {
        for (String locale : languages.keySet()) {
            if (languages.get(locale) == lang) {
                if (lang.get(key) == null) {
                    System.err.println(locale.toUpperCase() + " IS MISSING A KEY: " + key);
                    return key;
                }
            }
        }

        String localisedString = lang.get(key);
        localisedString = localisedString.replaceAll("\\\\n", "\n");
        if (args.length != 0) return String.format(localisedString, args);
        return localisedString;
    }

    private static String serializeString(String localeInput) {
        String[] localeStrings = localeInput.split("//");
        localeInput = localeInput.replace("//" + localeStrings[localeStrings.length - 1], "").trim();
        localeInput = localeInput.replaceAll("\\\\n", "\n");

        Matcher matcher = serializePattern.matcher(localeInput);

        while (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                try {
                    String match = matcher.group(i);
                    localeInput = localeInput.replace("{" + match + "}", "%" + match + "$s");
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("Issue: " + localeInput);
                }
            }
        }
        return localeInput;
    }

    public static String getLocalisedTimeUnits(boolean plural, int unit, Map<String, String> lang) {
        String[] unitKeys = {"second", "minute", "hour", "day"};
        String key = "main." + unitKeys[unit] + (plural ? ".plural" : "");
        return lang.get(key);
    }

    public enum TimeUnits {
        second, minute, hour, day
    }

}

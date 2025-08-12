package Zenvibe.managers;

import Zenvibe.lavaplayer.GuildMusicManager;
import Zenvibe.lavaplayer.PlayerManager;
import com.github.natanbc.lavadsp.timescale.TimescalePcmAudioFilter;
import com.github.natanbc.lavadsp.vibrato.VibratoPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static Zenvibe.Main.*;

/**
 * Central manager used to handle the reading, saving, and management of JSON files
 */
public class GuildDataManager {
    public final static String configFolder = "config";
    public final static Map<Object, JSONObject> Configs = new HashMap<>();

    public static void Init() {
        boolean madeFolder = Paths.get(configFolder).toFile().mkdir();
        if (madeFolder) {
            System.out.println("Created new config folder");
        }
        madeFolder = Paths.get(configFolder + "/failed").toFile().mkdir();
        if (madeFolder) {
            System.out.println("Created new config failure folder");
        }
        madeFolder = Paths.get(configFolder + "/queues").toFile().mkdir();
        if (madeFolder) {
            System.out.println("Created new guild queue recovery folder");
        }
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                SaveConfigs();
            }
        };
        timer.scheduleAtFixedRate(task, 60000, 120000); //Actively save every 2 minutes
        System.out.println("Guild Data manager initialised");
    }

    private static JSONObject CreateGuildObject() { //Base guild config
        JSONObject defaultConfig = new JSONObject();
        defaultConfig.put("BlockedChannels", new JSONArray());
        defaultConfig.put("DJRoles", new JSONArray());
        defaultConfig.put("DJUsers", new JSONArray());
        defaultConfig.put("Locale", "english");
        return defaultConfig;
    }

    private static boolean IsConfigDirty(JSONObject config) {
        try {
            new JSONParser().parse(config.toJSONString());
        } catch (ParseException ignored) {
            return true;
        }
        return false;
    }

    private static void WriteConfig(Object Filename, JSONObject config) throws IOException { //So I don't have to write it multiple times
        String filePath = configFolder + "/" + Filename + ".json";
        FileWriter writer = new FileWriter(filePath);
        writer.write(config.toString());
        writer.close();
    }

    private static JSONObject CreateConfig(String Filename, JSONObject Content) throws IOException { //Creates the actual file behind a config
        String filePath = configFolder + "/" + Filename + ".json";
        boolean madeFile = Paths.get(filePath).toFile().createNewFile();
        if (!madeFile) {
            System.err.println("Warning: Potentially overwriting an existing config (" + Filename + ")");
        }
        WriteConfig(Filename, Content);
        return Content;
    }

    private static JSONObject CreateConfig(String Filename) throws IOException {
        return CreateConfig(Filename, new JSONObject());
    }

    public static JSONObject CreateGuildConfig(long GuildID) throws IOException { //Guild-based config, ensures it has the normal guild content
        return CreateConfig(String.valueOf(GuildID), CreateGuildObject());
    }

    private static JSONObject ReadConfig(String Filename) throws IOException { //Helper to GetConfig, safely fetches a config
        String filePath = configFolder + "/" + Filename + ".json";
        JSONParser parser = new JSONParser();
        FileReader reader = new FileReader(filePath);
        JSONObject config;
        try {
            config = (JSONObject) parser.parse(reader);
        } catch (ParseException exception) { //Useless config, just discard it and start fresh
            System.err.println("Usurping config for " + Filename + " with a generic one and moving old to /failed due to bad formatting");
            reader.close();
            Files.move(Paths.get(filePath), Paths.get(configFolder + "/failed/" + Filename + "_" + System.currentTimeMillis() + ".json"));
            return CreateConfig(Filename);
        }
        reader.close();
        Configs.put(Filename, config);
        return config;
    }

    private static JSONObject ReadGuildConfig(long GuildID) throws IOException { //Helper to GetGuildConfig, safely fetches a guild's config
        //Get the config
        String filePath = configFolder + "/" + GuildID + ".json";
        JSONParser parser = new JSONParser();
        FileReader reader = new FileReader(filePath);
        JSONObject config;
        try {
            config = (JSONObject) parser.parse(reader);
        } catch (ParseException exception) { //Useless config, just discard it and start fresh
            System.err.println("Usurping config for " + GuildID + " with a generic one and moving old to /failed due to bad formatting");
            reader.close();
            Files.move(Paths.get(filePath), Paths.get(configFolder + "/failed/" + GuildID + "_" + System.currentTimeMillis() + ".json"));
            return CreateGuildConfig(GuildID);
        }
        reader.close();

        //Check the config to ensure it follows the format of any changes
        JSONObject baseConfig = CreateGuildObject();
        for (Object key : baseConfig.keySet()) {
            if (!config.containsKey(key)) {
                System.err.println("Config " + GuildID + " missing key " + key);
                config.put(key, baseConfig.get(key)); //Overwrite
            } else {
                if (config.get(key).getClass() != baseConfig.get(key).getClass()) {
                    System.err.println("Config " + GuildID + " has invalid value type for key " + key);
                    config.put(key, baseConfig.get(key)); //Overwrite
                }
            }
        }
        for (Iterator it = config.keySet().iterator(); it.hasNext(); ) {
            Object key = it.next();
            if (!baseConfig.containsKey(key)) {
                System.err.println("Config " + GuildID + " has unrecognised key " + key);
                it.remove(); //Remove
            }
        }

        //Done, store and send away
        Configs.put(GuildID, config);
        return config;
    }

    public static JSONObject GetConfig(String Filename) {
        if (Configs.containsKey(Filename)) { //Already loaded
            return Configs.get(Filename);
        } else { //Need to load it
            String filePath = configFolder + "/" + Filename + ".json";
            Path existingConfig = Paths.get(filePath);
            if (existingConfig.toFile().exists()) { //It exists
                try {
                    return ReadConfig(Filename);
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            } else { //It doesn't exist
                try {
                    return CreateConfig(Filename);
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            }
        }
        throw new NullPointerException(); // I guess?
    }

    public static JSONObject GetGuildConfig(long GuildID) { //Gets the config for the requested GuildID
        if (Configs.containsKey(GuildID)) { //Already loaded
            return Configs.get(GuildID);
        } else { //Need to load it
            String filePath = configFolder + "/" + GuildID + ".json";
            Path existingConfig = Paths.get(filePath);
            if (existingConfig.toFile().exists()) { //It exists
                try {
                    return ReadGuildConfig(GuildID);
                } catch (IOException exception) {
                    System.err.println("Failed to read the guild config for " + GuildID);
                    exception.printStackTrace();
                }
            } else { //It doesn't exist
                try {
                    return CreateGuildConfig(GuildID);
                } catch (IOException exception) {
                    System.err.println("Failed to create a guild config for " + GuildID);
                    exception.printStackTrace();
                }
            }
        }
        throw new NullPointerException();
    }

    public static void RemoveConfig(Object identifier) {
        Configs.remove(identifier);
        File file = new File(configFolder + "/" + identifier + ".json");
        if (file.exists()) {
            if (!file.delete()) {
                System.err.println("Unable to delete the config file for " + identifier);
            }
        } else {
            System.err.println("Attempted to delete non-existent config " + identifier);
        }
    }

    public static void SaveQueues(JDA bot) { // queue restoration can only occur once because this here does NOT give the tracks their data.
        for (Guild guild : bot.getGuilds()) {
            GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(guild);
            AudioPlayer player = musicManager.audioPlayer;
            AudioTrack playingTrack = player.getPlayingTrack();
            if (playingTrack == null) { // the track being null means there is no queue 99% of the time.
                continue;
            }
            String fileName = guild.getId() + ".txt";
            File guildQueueFile = new File(configFolder + "/queues/" + fileName);
            try {
                if (guildQueueFile.exists()) {
                    guildQueueFile.delete();
                    guildQueueFile.createNewFile();
                }
                FileWriter writer = new FileWriter(guildQueueFile);
                writer.write(System.currentTimeMillis() + "\n"); // time now
                PlayerManager.TrackData trackData = (PlayerManager.TrackData) playingTrack.getUserData();
                GuildChannel channel = bot.getGuildChannelById(trackData.channelId);
                writer.write(Objects.requireNonNull(channel).getGuild().getId() + "\n"); // guild id
                writer.write(channel.getId() + "\n"); // channel id
                writer.write(Objects.requireNonNull(Objects.requireNonNull(guild.getSelfMember().getVoiceState()).getChannel()).getId() + "\n"); // vc id
                writer.write(playingTrack.getPosition() + "\n"); // track now position
                // track states
                writer.write(player.isPaused() + "\n"); // is paused
                writer.write(LoopGuilds.contains(guild.getIdLong()) + "\n"); // is looping
                writer.write(LoopQueueGuilds.contains(guild.getIdLong()) + "\n"); // is queue looping
                writer.write(AutoplayGuilds.contains(guild.getIdLong()) + "\n"); // is autoplaying
                // track modifiers
                writer.write(player.getVolume() + "\n"); // volume
                writer.write(((TimescalePcmAudioFilter) musicManager.filters.get(AudioFilters.Timescale)).getSpeed() + "\n"); // speed
                writer.write(((TimescalePcmAudioFilter) musicManager.filters.get(AudioFilters.Timescale)).getPitch() + "\n"); // pitch
                writer.write(((VibratoPcmAudioFilter) musicManager.filters.get(AudioFilters.Vibrato)).getFrequency() + "\n"); // vibrato freq
                writer.write(((VibratoPcmAudioFilter) musicManager.filters.get(AudioFilters.Vibrato)).getDepth() + "\n"); // vibrato depth
                writer.write(playingTrack.getInfo().uri + "\n"); // track now url
                if (!musicManager.scheduler.queue.isEmpty()) {
                    for (AudioTrack track : musicManager.scheduler.queue)
                        writer.write(track.getInfo().uri + "\n"); // queue urls
                }
                writer.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void SaveConfigs() { //Saves all configs. Should ideally be run just before shutdown
        for (Object Filename : Configs.keySet()) {
            JSONObject config = Configs.get(Filename);
            if (IsConfigDirty(config)) {
                System.err.println("Refusing to save " + Filename + " as it isn't legal json");
                try {
                    WriteConfig("failed/" + Filename + "_" + System.currentTimeMillis(), config);
                } catch (IOException exception) {
                    System.err.println("Unable to save failed config for Config " + Filename + " (unsurprisingly)");
                    exception.printStackTrace();
                }
                try { //Force a fresh fetch
                    if (Filename.getClass() == String.class) {
                        ReadConfig((String) Filename);
                    } else {
                        ReadGuildConfig((long) Filename);
                    }
                } catch (IOException exception) {
                    System.err.println("Unable to load the existing non-screwed version for Config " + Filename);
                    exception.printStackTrace();
                }
            } else {
                try {
                    WriteConfig(Filename, config);
                } catch (IOException exception) {
                    System.err.println("Unable to save config for Config " + Filename);
                    exception.printStackTrace();
                }
            }
        }
    }
}

package Zenvibe.managers;

import Zenvibe.storage.SqliteStore;
import Zenvibe.lavaplayer.GuildMusicManager;
import Zenvibe.lavaplayer.PlayerManager;
import com.github.natanbc.lavadsp.timescale.TimescalePcmAudioFilter;
import com.github.natanbc.lavadsp.vibrato.VibratoPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static Zenvibe.Main.*;
import static Zenvibe.lavaplayer.AudioPlayerSendHandler.totalBytesSent;

/** Local SQLite configuration storage; queue recovery retains its existing text format. */
public class GuildDataManager {
    public static final String configFolder = "config";
    private static SqliteStore database;
    private static Timer saveTimer;
    private static boolean closed;

    public static synchronized SqliteStore database() {
        if (closed) throw new IllegalStateException("Storage has been closed");
        if (database == null) {
            database = new SqliteStore(Path.of(configFolder, "zenvibe.db"), Path.of(configFolder));
        }
        return database;
    }

    public static synchronized void Init() {
        database();
        try {
            Files.createDirectories(Path.of(configFolder, "queues"));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create queue recovery directory", exception);
        }
        if (saveTimer == null) {
            saveTimer = new Timer("storage-save", true);
            saveTimer.scheduleAtFixedRate(new TimerTask() {
                @Override public void run() {
                    try { SaveConfigs(); }
                    catch (RuntimeException exception) { System.err.println("Cannot save usage statistics: " + exception.getMessage()); }
                }
            }, 60000, 120000);
        }
        System.out.println("SQLite storage initialized: " + Path.of(configFolder, "zenvibe.db").toAbsolutePath());
    }

    /** Returned JSON objects are detached in-memory views, never files or database JSON blobs. */
    public static JSONObject GetGuildConfig(long guildId) {
        return database().guild(guildId);
    }

    public static JSONObject CreateGuildConfig(long guildId) throws IOException {
        return GetGuildConfig(guildId);
    }

    public static void RemoveConfig(Object identifier) {
        database().deleteGuild(Long.parseLong(identifier.toString()));
    }

    public static synchronized void SaveConfigs() {
        database().saveBytesSent(totalBytesSent.get());
    }

    public static synchronized void Close() {
        closed = true;
        if (saveTimer != null) { saveTimer.cancel(); saveTimer = null; }
        if (database != null) { database.close(); database = null; }
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

}

package Zenvibe.commands.general;

import Zenvibe.BaseCommand;
import Zenvibe.CommandEvent;
import Zenvibe.Main;
import Zenvibe.lavaplayer.PlayerManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Objects;

import static Zenvibe.managers.EmbedManager.toTimestamp;
import static Zenvibe.Main.*;
import static java.lang.System.currentTimeMillis;

public class CommandInfo extends BaseCommand {
    final int CommandCount = commandNames.size();

    @Override
    public void execute(CommandEvent event) {
        int vcCount = 0;
        int memberCount = 0;
        int playingCount = 0;

        for (Guild guild : event.getJDA().getGuilds()) {
            if (Objects.requireNonNull(guild.getSelfMember().getVoiceState()).inAudioChannel()) {
                vcCount++;
            }
            if (PlayerManager.getInstance().getMusicManager(guild).audioPlayer.getPlayingTrack() != null) {
                playingCount++;
            }
            memberCount += guild.getMemberCount();
        }

        String ytdlpVersion;
        try {
            Process process = new ProcessBuilder(new File("yt-dlp_linux").getAbsolutePath(), "--version").start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            ytdlpVersion = reader.readLine();
            ytdlpVersion = ytdlpVersion.substring(2, ytdlpVersion.length()-1);
            reader.close();
            process.waitFor();
        } catch (Exception e) {
            ytdlpVersion = "Unknown";
        }

        long memoryUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(event.localise("cmd.info.info", event.getJDA().getSelfUser().getName()), null);
        eb.setColor(botColour);
        eb.appendDescription(event.localise("cmd.info.ramUsage", String.format("%,d", memoryUsed / 1024 / 1024)));
        long finalUptime = currentTimeMillis() - Main.startupTime;
        String finalTime = toTimestamp(finalUptime, event.getGuild().getIdLong());
        eb.appendDescription(event.localise("cmd.info.upTime", finalTime));
        eb.appendDescription(event.localise("cmd.info.discordServers", String.format("%,d", event.getJDA().getGuilds().size())));
        eb.appendDescription(event.localise("cmd.info.discordMembers", String.format("%,d", memberCount)));
        eb.appendDescription(event.localise("cmd.info.registeredCommands", CommandCount));
        eb.appendDescription(event.localise("cmd.info.voiceChannels", vcCount));
        eb.appendDescription(event.localise("cmd.info.playingCount", playingCount));
        eb.appendDescription(event.localise("cmd.info.gatewayPing", event.getJDA().getGatewayPing()));
        long time = currentTimeMillis();
        String finalYtdlpVersion = ytdlpVersion;
        event.replyEmbeds(response -> {
            eb.appendDescription("⏱️  " + event.localise("cmd.info.ping", currentTimeMillis() - time));
            eb.appendDescription("-# " + "yt-dlp" + " " + event.localise("cmd.info.version", finalYtdlpVersion));
            eb.appendDescription("\n-# " + event.getJDA().getSelfUser().getName() + " " + event.localise("cmd.info.version", botVersion));
            response.editMessageEmbeds(eb.build());
        }, eb.build());
    }


    @Override
    public String[] getNames() {
        return new String[]{"info"};
    }

    @Override
    public Category getCategory() {
        return Category.General;
    }

    @Override
    public String getDescription() {
        return "Tells you bot info.";
    }
}

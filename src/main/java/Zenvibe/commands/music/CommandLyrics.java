package Zenvibe.commands.music;

import Zenvibe.BaseCommand;
import Zenvibe.CommandEvent;
import Zenvibe.CommandStateChecker.Check;
import Zenvibe.lavaplayer.GuildMusicManager;
import Zenvibe.lavaplayer.LRCLIBManager;
import Zenvibe.lavaplayer.PlayerManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.utils.FileUpload;

import java.nio.charset.StandardCharsets;

import static Zenvibe.Main.botColour;
import static Zenvibe.managers.EmbedManager.sanitise;

public class CommandLyrics extends BaseCommand {
    @Override
    public Check[] getChecks() {
        return new Check[]{Check.IS_BOT_IN_ANY_VC, Check.IS_PLAYING};
    }

    @Override
    public void execute(CommandEvent event) {
        final GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        final AudioPlayer audioPlayer = musicManager.audioPlayer;

        EmbedBuilder loadingEmbed = new EmbedBuilder();
        loadingEmbed.setColor(botColour);
        loadingEmbed.setDescription(event.localise("main.loading"));

        event.replyEmbeds(response -> {
            String lyrics = LRCLIBManager.getLyrics(audioPlayer.getPlayingTrack()).trim();

            if (lyrics.isEmpty()) {
                response.editMessageEmbeds(event.createQuickError(event.localise("cmd.lyr.notFound")));
                return;
            }

            EmbedBuilder builder = new EmbedBuilder().setColor(botColour).setFooter(event.localise("cmd.lyr.source"));
            String title = LRCLIBManager.displayName(audioPlayer.getPlayingTrack());

            String embedTitle = event.localise("cmd.lyr.lyricsForTrack", sanitise(title));
            if (embedTitle.length() > 256) {
                embedTitle = embedTitle.substring(0, 253) + "...";
            }

            if (lyrics.length() <= 2000) {
                builder.setDescription(lyrics);
                builder.setTitle(embedTitle);
                response.editMessageEmbeds(builder.build());
            } else {
                builder.setDescription(event.localise("cmd.lyr.tooLong"));
                response.editMessageEmbeds(builder.build());
                String fileName = event.localise("cmd.lyr.lyricsForTrack", title);
                if (fileName.length() > 200) {
                    fileName = fileName.substring(0, 197) + "...";
                }
                event.getChannel().sendFiles(FileUpload.fromData(lyrics.getBytes(StandardCharsets.UTF_8), fileName + ".txt")).queue();
            }
        }, loadingEmbed.build());
    }


    @Override
    public Category getCategory() {
        return Category.Music;
    }

    @Override
    public String[] getNames() {
        return new String[]{"lyrics"};
    }

    @Override
    public String getDescription() {
        return "Gets the lyrics from the current song.";
    }

    @Override
    public long getRatelimit() {
        return 10000;
    }
}

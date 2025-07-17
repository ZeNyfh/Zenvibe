package Zenvibe.commands.music;

import Zenvibe.BaseCommand;
import Zenvibe.CommandEvent;
import Zenvibe.CommandStateChecker.Check;
import Zenvibe.lavaplayer.GuildMusicManager;
import Zenvibe.lavaplayer.LRCLIBManager;
import Zenvibe.lavaplayer.PlayerManager;
import Zenvibe.lavaplayer.RadioDataFetcher;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.utils.FileUpload;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static Zenvibe.Main.botColour;

public class CommandLyrics extends BaseCommand {
    @Override
    public Check[] getChecks() {
        return new Check[]{Check.IS_BOT_IN_ANY_VC, Check.IS_PLAYING};
    }

    @Override
    public void execute(CommandEvent event) {
        final GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        final AudioPlayer audioPlayer = musicManager.audioPlayer;

        String lyrics = LRCLIBManager.getLyrics(audioPlayer.getPlayingTrack()).trim();
        if (lyrics.isEmpty()) {
            event.replyEmbeds(event.createQuickError(event.localise("cmd.lyr.notFound")));
            return;
        }
        EmbedBuilder builder = new EmbedBuilder().setColor(botColour).setFooter(event.localise("cmd.lyr.source"));
        String title = audioPlayer.getPlayingTrack().getInfo().title;
        if (audioPlayer.getPlayingTrack().getInfo().isStream && Objects.equals(audioPlayer.getPlayingTrack().getSourceManager().getSourceName(), "http")) {
            title = RadioDataFetcher.getStreamSongNow(audioPlayer.getPlayingTrack().getInfo().uri)[0];
        }

        title = event.localise("cmd.lyr.lyricsForTrack", title);
        if (title.length() > 256) {
            title = title.substring(0, 253) + "...";
        }
        if (lyrics.length() <= 2000) {
            builder.setDescription(lyrics);
            builder.setTitle(title);
            event.replyEmbeds(builder.build());
        } else {
            builder.setDescription(event.localise("cmd.lyr.tooLong"));
            event.replyEmbeds(builder.build());
            event.getChannel().sendFiles(FileUpload.fromData(lyrics.getBytes(StandardCharsets.UTF_8), title + ".txt")).queue();
        }
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
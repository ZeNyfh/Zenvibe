package Zenvibe.commands.dj;

import Zenvibe.BaseCommand;
import Zenvibe.CommandEvent;
import Zenvibe.CommandStateChecker.Check;
import Zenvibe.lavaplayer.ListenBrainzManager;
import Zenvibe.lavaplayer.PlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static Zenvibe.Main.AutoplayGuilds;
import static Zenvibe.managers.EmbedManager.createQuickEmbed;

public class CommandAutoplay extends BaseCommand {
    @Override
    public Check[] getChecks() {
        return new Check[]{Check.IS_DEV, Check.IS_DJ, Check.IS_IN_SAME_VC, Check.IS_PLAYING};
    }

    @Override
    public void execute(CommandEvent event) {
        if (AutoplayGuilds.contains(event.getGuild().getIdLong())) {
            event.replyEmbeds(createQuickEmbed("❌ ♾\uFE0F", event.localise("cmd.ap.notAutoplaying")));
            AutoplayGuilds.remove(event.getGuild().getIdLong());
            return;
        }

        AutoplayGuilds.add(event.getGuild().getIdLong());
        AudioTrack track = PlayerManager.getInstance().getMusicManager(event.getGuild()).audioPlayer.getPlayingTrack();
        event.replyEmbeds(createQuickEmbed("✅ ♾\uFE0F", event.localise("cmd.ap.isAutoplaying")));

        if (track == null) {
            return;
        }

        long guildId = event.getGuild().getIdLong();
        CompletableFuture.runAsync(() -> {
            List<String> songs = ListenBrainzManager.getSimilarSongs(track, guildId, ListenBrainzManager.AUTOPLAY_BATCH);
            PlayerManager.getInstance().loadAutoplayBatch(event, songs, guildId);
        });
    }

    @Override
    public String[] getNames() {
        return new String[]{"autoplay", "ap"};
    }

    @Override
    public Category getCategory() {
        return Category.Dev;
    }

    @Override
    public String getDescription() {
        return "Toggles autoplay.";
    }

    @Override
    public long getRatelimit() {
        return 2500;
    }
}

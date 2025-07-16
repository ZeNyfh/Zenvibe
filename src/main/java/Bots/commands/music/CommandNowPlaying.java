package Bots.commands.music;

import Bots.BaseCommand;
import Bots.CommandEvent;
import Bots.CommandStateChecker.Check;
import Bots.lavaplayer.GuildMusicManager;
import Bots.lavaplayer.PlayerManager;
import Bots.lavaplayer.RadioDataFetcher;
import com.github.natanbc.lavadsp.timescale.TimescalePcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;

import java.util.Objects;

import static Bots.EmbedHelper.toSimpleTimestamp;
import static Bots.Main.*;

public class CommandNowPlaying extends BaseCommand {
    @Override
    public Check[] getChecks() {
        return new Check[]{Check.IS_BOT_IN_ANY_VC, Check.IS_PLAYING};
    }

    @Override
    public void execute(CommandEvent event) {
        final GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        final AudioPlayer audioPlayer = musicManager.audioPlayer;
        final AudioTrack track = audioPlayer.getPlayingTrack();
        EmbedBuilder embed = new EmbedBuilder();

        long trackPos = track.getPosition();
        if (!track.getInfo().isStream) {
            double totalTime = track.getDuration();
            TimescalePcmAudioFilter timescale = (TimescalePcmAudioFilter) musicManager.filters.get(AudioFilters.Timescale);
            if (timescale != null) {
                trackPos = (long) (trackPos / timescale.getSpeed());
                totalTime = totalTime / timescale.getSpeed();
            }
            String totalTimeText;
            if (totalTime > 432000000) { // 5 days
                totalTimeText = event.localise("main.unknown"); //Assume malformed
            } else {
                totalTimeText = toSimpleTimestamp((long) totalTime);
            }
            int trackLocation = Math.toIntExact(Math.round((totalTime - trackPos) / totalTime * 20d));
            String barText = "";
            try {
                barText = new String(new char[20 - trackLocation]).replace("\0", "━") + "\uD83D\uDD18" + new String(new char[trackLocation]).replace("\0", "━");
            } catch (Exception ignored) {
            }
            if (PlayerManager.getInstance().getThumbURL(track) != null) {
                embed.setThumbnail(PlayerManager.getInstance().getThumbURL(track));
            }
            embed.setDescription("```" + barText + " " + toSimpleTimestamp(trackPos) + " / " + totalTimeText + "```");
        } else {
            embed.setDescription(event.localise("cmd.np.livestreamDuration", toSimpleTimestamp(trackPos)));
        }
        try {
            embed.setTitle((track.getInfo().title), (track.getInfo().uri));
            if (track.getInfo().isStream && Objects.equals(audioPlayer.getPlayingTrack().getSourceManager().getSourceName(), "http")) {
                String streamTitle = RadioDataFetcher.getStreamSongNow(track.getInfo().uri)[0];
                embed.setTitle(streamTitle, track.getInfo().uri);
            }
        } catch (Exception ignored) {
            embed.setTitle(event.localise("main.unknown"));
        }
        embed.addField(event.localise("cmd.np.channel"), track.getInfo().author, true);
        if (getTrackFromQueue(event.getGuild(), 0) != null) {
            AudioTrack trackQueue0 = getTrackFromQueue(event.getGuild(), 0);
            if (PlayerManager.getInstance().getThumbURL(track) != null) {
                embed.setThumbnail(PlayerManager.getInstance().getThumbURL(track));
            }
            String title = Objects.requireNonNull(trackQueue0).getInfo().title;
            if (Objects.requireNonNull(trackQueue0).getInfo().isStream) {
                String streamTitle = RadioDataFetcher.getStreamTitle(trackQueue0.getInfo().uri);
                if (streamTitle != null) {
                    title = streamTitle;
                }
            }
            embed.addField(event.localise("cmd.np.next"), "[" + title + "](" + trackQueue0.getInfo().uri + ")", true);
        } else {
            embed.addField(" ", " ", true);
        }
        embed.addField(" ", " ", true);
        if (audioPlayer.isPaused()) {
            embed.addField(event.localise("cmd.np.paused"), event.localise("cmd.np.true"), true);
        } else {
            embed.addField(event.localise("cmd.np.paused"), event.localise("cmd.np.false"), true);
        }
        if (LoopGuilds.contains(event.getGuild().getIdLong())) {
            embed.addField(event.localise("cmd.np.trackLooping"), event.localise("cmd.np.true"), true);
            embed.setFooter("Loop Count: " + trackLoops.get(event.getGuild().getIdLong()));
        } else {
            embed.addField(event.localise("cmd.np.trackLooping"), event.localise("cmd.np.false"), true);
        }
        if (LoopQueueGuilds.contains(event.getGuild().getIdLong())) {
            embed.addField(event.localise("cmd.np.queueLooping"), event.localise("cmd.np.true"), true);
        } else {
            embed.addField(event.localise("cmd.np.queueLooping"), event.localise("cmd.np.false"), true);
        }
        if (AutoplayGuilds.contains(event.getGuild().getIdLong())) {
            embed.addField(event.localise("cmd.np.autoplaying"), event.localise("cmd.np.true"), true);
        } else {
            embed.addField(event.localise("cmd.np.autoplaying"), event.localise("cmd.np.false"), true);
        }
        embed.addField(" ", " ", true);
        embed.setColor(botColour);
        event.replyEmbeds(embed.build());
    }

    @Override
    public Category getCategory() {
        return Category.Music;
    }

    @Override
    public String[] getNames() {
        return new String[]{"np", "nowplaying"};
    }

    @Override
    public String getDescription() {
        return "Shows you the track that is currently playing.";
    }

    @Override
    public long getRatelimit() {
        return 2500;
    }
}

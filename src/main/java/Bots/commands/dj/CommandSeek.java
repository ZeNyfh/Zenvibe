package Bots.commands.dj;

import Bots.BaseCommand;
import Bots.CommandEvent;
import Bots.CommandStateChecker.Check;
import Bots.lavaplayer.GuildMusicManager;
import Bots.lavaplayer.PlayerManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import static Bots.managers.EmbedManager.toSimpleTimestamp;

public class CommandSeek extends BaseCommand {
    @Override
    public Check[] getChecks() {
        return new Check[]{Check.IS_DJ, Check.IS_IN_SAME_VC, Check.IS_PLAYING};
    }

    @Override
    public void execute(CommandEvent event) {
        final GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        final AudioPlayer audioPlayer = musicManager.audioPlayer;

        String[] args = event.getArgs();
        if (args.length > 1) {
            if (audioPlayer.getPlayingTrack().isSeekable()) {
                String[] times = args[1].split(":", 3);
                for (String time : times) {
                    if (!time.matches("^\\d+$")) {
                        event.replyEmbeds(event.createQuickError(String.format(event.localise("cmd.seek.invalidArg"))));
                        return;
                    }
                }
                long position = 0;
                if (times.length == 3) {
                    position = (Long.parseLong(times[0]) * 60 * 60) + (Long.parseLong(times[1]) * 60) + (Long.parseLong(times[2]));
                } else if (times.length == 2) {
                    position = (Long.parseLong(times[0]) * 60) + (Long.parseLong(times[1]));
                } else if (times.length == 1) {
                    position = Long.parseLong(times[0]);
                }
                position = position * 1000;
                if (position <= 0) {
                    event.replyEmbeds(event.createQuickError(event.localise("cmd.seek.timeTooLow")));
                    return;
                }
                audioPlayer.getPlayingTrack().setPosition(position);
                event.replyEmbeds(event.createQuickSuccess(event.localise("cmd.seek.setPos", toSimpleTimestamp(position))));
            } else {
                event.replyEmbeds(event.createQuickError(event.localise("cmd.seek.cannotSeek")));
            }
        } else {
            event.replyEmbeds(event.createQuickError(event.localise("cmd.seek.noArg")));
        }
    }

    @Override
    public void ProvideOptions(SlashCommandData slashCommand) {
        slashCommand.addOption(OptionType.STRING, "timestamp", "Timestamp to seek to, E.g: 12 | 1:54 | 1:09:34.", true);
    }

    @Override
    public Category getCategory() {
        return Category.DJ;
    }


    @Override
    public String getOptions() {
        return "[HH:][MM:]<SS>";
    }

    @Override
    public String[] getNames() {
        return new String[]{"seek"};
    }

    @Override
    public String getDescription() {
        return "Seeks to a certain position in the track.";
    }

    @Override
    public long getRatelimit() {
        return 1000;
    }
}

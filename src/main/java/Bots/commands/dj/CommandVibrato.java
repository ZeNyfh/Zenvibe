package Bots.commands.dj;

import Bots.BaseCommand;
import Bots.CommandEvent;
import Bots.CommandStateChecker.Check;
import Bots.lavaplayer.GuildMusicManager;
import Bots.lavaplayer.PlayerManager;
import com.github.natanbc.lavadsp.vibrato.VibratoPcmAudioFilter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import static Bots.EmbedHelper.createQuickEmbed;
import static Bots.Main.AudioFilters;

public class CommandVibrato extends BaseCommand {
    @Override
    public Check[] getChecks() {
        return new Check[]{Check.IS_DJ, Check.IS_IN_SAME_VC, Check.IS_PLAYING};
    }

    @Override
    public void execute(CommandEvent event) {
        final GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        VibratoPcmAudioFilter vibrato = (VibratoPcmAudioFilter) musicManager.filters.get(AudioFilters.Vibrato);

        if (event.getArgs().length == 1) {
            vibrato.setFrequency(2);
            vibrato.setDepth(0.5f);
            event.replyEmbeds(event.createQuickSuccess(event.localise("cmd.vib.defaulted")));
            return;
        }

        if (event.getArgs().length != 3) {
            event.replyEmbeds(createQuickEmbed(event.localise("cmd.vib.invalidArgs"), event.localise("cmd.vib.incorrectArgs", "vibrato")));
            return;
        }

        float value = Float.parseFloat(String.format("%.3f %n", Float.parseFloat(event.getArgs()[1])));
        float power = Float.parseFloat(String.format("%.3f %n", Float.parseFloat(event.getArgs()[2])));

        if (!(value <= 14 && value >= 0.1)) {
            event.replyEmbeds(event.createQuickError(event.localise("cmd.vib.range.frequency")));
            return;
        }

        if (!(power <= 1 && power >= 0.05)) {
            event.replyEmbeds(event.createQuickError(event.localise("cmd.vib.range.depth")));
            return;
        }

        vibrato.setFrequency(value);
        vibrato.setDepth(power);
        event.replyEmbeds(event.createQuickSuccess(event.localise("cmd.vib.success", value, power)));
    }

    @Override
    public void ProvideOptions(SlashCommandData slashCommand) {
        slashCommand.addOption(OptionType.NUMBER, "frequency", "The frequency at which the track \"bounces\", supports decimals.", false);
        slashCommand.addOption(OptionType.NUMBER, "depth", "How powerful the \"bounce\" will be, supports decimals.", false);
    }

    @Override
    public String getOptions() {
        return "[frequency] [depth]";
    }

    @Override
    public Category getCategory() {
        return Category.DJ;
    }

    @Override
    public String[] getNames() {
        return new String[]{"vibrato", "vib"};
    }

    @Override
    public String getDescription() {
        return "Changes the vibrato of the audio. If no arguments are provided, resets the parameters.";
    }

    @Override
    public long getRatelimit() {
        return 2000;
    }
}

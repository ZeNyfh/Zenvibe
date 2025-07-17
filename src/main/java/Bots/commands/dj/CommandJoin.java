package Bots.commands.dj;

import Bots.BaseCommand;
import Bots.CommandEvent;
import Bots.CommandStateChecker.Check;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import java.util.Objects;

public class CommandJoin extends BaseCommand {
    @Override
    public Check[] getChecks() {
        return new Check[]{Check.IS_DJ, Check.IS_USER_IN_ANY_VC};
    }

    @Override
    public void execute(CommandEvent event) {
        // Don't use the check system since we want to specifically allow this even if the bot is active in another VC

        // Validate if the bot is already in the VC
        AudioChannelUnion memberChannel = Objects.requireNonNull(event.getMember().getVoiceState()).getChannel();
        AudioChannelUnion selfChannel = Objects.requireNonNull(event.getGuild().getSelfMember().getVoiceState()).getChannel();
        if (memberChannel == selfChannel) {
            event.replyEmbeds(event.createQuickError(event.localise("cmd.join.botAlreadyInVC")));
            return;
        }
        try {
            event.getGuild().getAudioManager().openAudioConnection(memberChannel);
        } catch (InsufficientPermissionException e) {
            event.replyEmbeds(event.createQuickError(event.localise("cmd.join.noAccess")));
            return;
        }
        event.replyEmbeds(event.createQuickSuccess(event.localise("cmd.join.joined")));
    }

    @Override
    public String[] getNames() {
        return new String[]{"connect", "join"};
    }

    @Override
    public Category getCategory() {
        return Category.DJ;
    }

    @Override
    public String getDescription() {
        return "Makes the bot forcefully join your vc.";
    }

    @Override
    public long getRatelimit() {
        return 5000;
    }
}

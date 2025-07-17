package Zenvibe.commands.dj;

import Zenvibe.BaseCommand;
import Zenvibe.CommandEvent;
import Zenvibe.CommandStateChecker.Check;
import Zenvibe.lavaplayer.GuildMusicManager;
import Zenvibe.lavaplayer.PlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandShuffle extends BaseCommand {
    @Override
    public Check[] getChecks() {
        return new Check[]{Check.IS_DJ, Check.IS_IN_SAME_VC};
    }

    @Override
    public void execute(CommandEvent event) {
        final GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        final List<AudioTrack> queue = new ArrayList<>(musicManager.scheduler.queue);
        if (queue.isEmpty()) {
            event.replyEmbeds(event.createQuickError(event.localise("cmd.shuffle.emptyQueue")));
            return;
        }

        Collections.shuffle(queue);
        musicManager.scheduler.queue.clear();
        for (AudioTrack audioTrack : queue) {
            musicManager.scheduler.queue(audioTrack.makeClone());
        }
        event.replyEmbeds(event.createQuickSuccess(event.localise("cmd.shuffle.shuffled")));
    }

    @Override
    public String[] getNames() {
        return new String[]{"shuffle"};
    }

    @Override
    public Category getCategory() {
        return Category.DJ;
    }

    @Override
    public String getDescription() {
        return "Shuffles the current queue.";
    }

    @Override
    public long getRatelimit() {
        return 5000;
    }
}

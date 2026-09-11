package Zenvibe.commands.music;

import Zenvibe.BaseCommand;
import Zenvibe.CommandEvent;
import Zenvibe.managers.GuildDataManager;
import net.dv8tion.jda.api.entities.MessageEmbed;

import static Zenvibe.lavaplayer.LastFMManager.*;

public class CommandScrobble extends BaseCommand {

    @Override
    public void execute(CommandEvent event) throws Exception {
        var storage = GuildDataManager.database();
        String userId = event.getUser().getId();
        String savedSession = storage.lastFmSession(userId);
        if (savedSession == null) {
            String requestToken = fetchRequestToken();
            storage.saveLastFmSession(userId, "REQUEST" + requestToken);
            String authUrl = fetchUserAuthorisation(requestToken);
            MessageEmbed pending = event.createQuickSuccess(event.localise("cmd.scrobble.authPending", authUrl));
            if (event.isSlash()) {
                event.deferReply(true);
                event.replyEmbeds(pending);
            } else {
                event.getUser().openPrivateChannel().queue(dm ->
                        dm.sendMessageEmbeds(pending).queue(
                                ok -> {
                                },
                                err -> event.replyEmbeds(event.createQuickError(event.localise("cmd.scrobble.cannotDM")))
                        ), err -> event.replyEmbeds(event.createQuickError(event.localise("cmd.scrobble.cannotDM"))));
            }
            startAuthPoll(event, userId, requestToken);
            return;
        }

        if (savedSession.startsWith("REQUEST")) {
            String token = savedSession.substring("REQUEST".length());
            try {
                String sessionKey = fetchWebServiceSession(token);
                storage.saveLastFmSession(userId, sessionKey);
                event.replyEmbeds(event.createQuickSuccess(event.localise("cmd.scrobble.startedScrobbling")));
            } catch (Exception e) {
                MessageEmbed pending = event.createQuickSuccess(event.localise(
                        "cmd.scrobble.authPending", fetchUserAuthorisation(token)));
                event.replyEmbeds(pending);
                startAuthPoll(event, userId, token);
            }
            return;
        }

        storage.removeLastFmSession(userId);
        event.replyEmbeds(event.createQuickSuccess(event.localise("cmd.scrobble.stoppedScrobbling")));
    }

    private static void startAuthPoll(CommandEvent event, String userId, String requestToken) {
        pollAuthorisation(userId, requestToken,
                () -> notifyUser(event, event.createQuickSuccess(event.localise("cmd.scrobble.startedScrobbling"))),
                () -> notifyUser(event, event.createQuickError(event.localise("cmd.scrobble.authTimedOut"))));
    }

    private static void notifyUser(CommandEvent event, MessageEmbed embed) {
        event.getUser().openPrivateChannel().queue(
                dm -> dm.sendMessageEmbeds(embed).queue(),
                err -> {
                });
    }

    @Override
    public Category getCategory() {
        return Category.Music;
    }

    @Override
    public String[] getNames() {
        return new String[]{"scrobble", "lastfm"};
    }

    @Override
    public String getDescription() {
        return "Toggles scrobbling to last.fm.";
    }

    @Override
    public long getRatelimit() {
        return 2500;
    }
}

package Zenvibe.commands.music;

import Zenvibe.BaseCommand;
import Zenvibe.CommandEvent;

import Zenvibe.managers.GuildDataManager;

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
            if (event.isSlash()) {
                event.deferReply(true);
                event.replyEmbeds(event.createQuickSuccess(event.localise("cmd.scrobble.authPending", fetchUserAuthorisation(requestToken))));
            } else {
                event.getUser().openPrivateChannel().queue(dm -> {
                    try {
                        dm.sendMessageEmbeds(event.createQuickSuccess(event.localise("cmd.scrobble.authPending", fetchUserAuthorisation(requestToken)))).queue();
                    } catch (Exception e) {
                        event.replyEmbeds(event.createQuickError(event.localise("cmd.scrobble.cannotDM")));
                    }
                });
            }
        } else {
            if (savedSession.startsWith("REQUEST")) {
                String sessionKey = fetchWebServiceSession(savedSession.substring("REQUEST".length()));
                storage.saveLastFmSession(userId, sessionKey);
                event.replyEmbeds(event.createQuickSuccess(event.localise("cmd.scrobble.startedScrobbling")));
                return;
            }
            storage.removeLastFmSession(userId);
            event.replyEmbeds(event.createQuickSuccess(event.localise("cmd.scrobble.stoppedScrobbling")));
        }
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

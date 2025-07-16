package Bots.commands.music;

import Bots.BaseCommand;
import Bots.CommandEvent;

import java.util.HashMap;

import static Bots.lavaplayer.LastFMManager.*;

public class CommandScrobble extends BaseCommand {
    public static HashMap<String, String> scrobbleUsers = new HashMap<>();

    @Override
    public void execute(CommandEvent event) throws Exception {
        scrobbleUsers = sessionKeys;
        scrobbleUsers.get(event.getUser().getId());

        if (!scrobbleUsers.containsKey(event.getUser().getId())) {
            scrobbleUsers.put(event.getUser().getId(), "REQUEST" + fetchRequestToken());
            sessionKeys = (org.json.simple.JSONObject) scrobbleUsers;
            if (event.isSlash()) {
                event.deferReply(true);
                event.replyEmbeds(event.createQuickSuccess(event.localise("cmd.scrobble.authPending", fetchUserAuthorisation(scrobbleUsers.get(event.getUser().getId()).replace("REQUEST", "")))));
            } else {
                event.getUser().openPrivateChannel().queue(dm -> {
                    try {
                        dm.sendMessageEmbeds(event.createQuickSuccess(event.localise("cmd.scrobble.authPending", fetchUserAuthorisation(scrobbleUsers.get(event.getUser().getId()).replace("REQUEST", ""))))).queue();
                    } catch (Exception e) {
                        event.replyEmbeds(event.createQuickError(event.localise("cmd.scrobble.cannotDM")));
                    }
                });
            }
        } else {
            if (scrobbleUsers.get(event.getUser().getId()).contains("REQUEST")) {
                String sessionKey = fetchWebServiceSession(scrobbleUsers.get(event.getUser().getId()).replace("REQUEST", ""));
                scrobbleUsers.put(event.getUser().getId(), sessionKey);
                System.out.println(sessionKey);
                event.replyEmbeds(event.createQuickSuccess(event.localise("cmd.scrobble.startedScrobbling")));
                sessionKeys = (org.json.simple.JSONObject) scrobbleUsers;
                return;
            }
            event.replyEmbeds(event.createQuickSuccess(event.localise("cmd.scrobble.stoppedScrobbling")));

            scrobbleUsers.remove(event.getUser().getId());
            sessionKeys = (org.json.simple.JSONObject) scrobbleUsers;
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

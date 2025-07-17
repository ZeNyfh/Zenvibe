package Bots.commands.dev;

import Bots.BaseCommand;
import Bots.CommandEvent;
import Bots.CommandStateChecker.Check;
import Bots.managers.LocaleManager;
import Bots.lavaplayer.GuildMusicManager;
import Bots.lavaplayer.LastFMManager;
import Bots.lavaplayer.PlayerManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

import static Bots.Main.*;
import static Bots.managers.EventManager.registerButtonInteraction;

public class CommandDevTests extends BaseCommand {
    private static void writeGuilds(CommandEvent event) {
        JDA bot = event.getJDA();
        List<Guild> guilds = bot.getGuilds();
        StringBuilder builder = new StringBuilder();
        for (Guild g : guilds) {
            builder.append(g.getName()).append(",").append(g.getMemberCount()).append("\n");
        }
        File file = new File("guilds.csv");
        try {
            file.delete();
            file.createNewFile();
            FileWriter writer = new FileWriter(file);
            writer.write(String.valueOf(builder));
            writer.flush();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(builder);
    }

    private void HandleButtonEvent(ButtonInteractionEvent event) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(botColour);
        eb.setDescription("*new* description text");
        event.getInteraction().editMessageEmbeds(eb.build()).queue();
    }

    @Override
    public void Init() {
        registerButtonInteraction("dev-button", this::HandleButtonEvent);
    }

    @Override
    public Check[] getChecks() {
        return new Check[]{Check.IS_DEV};
    }

    @Override
    public void execute(CommandEvent event) throws Exception {
        String[] args = event.getArgs();
        if (args.length == 1) {
            event.reply("No dev command provided");
        } else {
            String command = args[1];
            if (command.equalsIgnoreCase("dirty-config")) { //Adds an illegal object to the json to invalidate it
                event.getConfig().put("bad-value", new Exception());
                event.reply("Added something nonsensical to the config");
            } else if (command.equalsIgnoreCase("test-buttons")) { //Testing for the button registration system
                EmbedBuilder eb = new EmbedBuilder();
                eb.setColor(botColour);
                eb.setDescription("description text");
                event.getChannel().sendMessageEmbeds(eb.build()).queue(
                        message -> message.editMessageComponents().setActionRow(
                                Button.secondary("dev-button", "Me"), Button.secondary("not-dev-button", "Not Me")
                        ).queue()
                );
            } else if (command.equalsIgnoreCase("threads")) {
                event.reply("Command thread count: " + commandThreads.getPoolSize() + " threads (" + commandThreads.getActiveCount() + " active)");
            } else if (command.equalsIgnoreCase("sleep")) {
                final long sleepTime = args.length > 2 ? Long.parseLong(args[2]) : 5000;
                event.reply("Sleeping for " + sleepTime + "ms...");
                Thread.sleep(sleepTime);
                event.reply("Finished sleeping");
            } else if (command.equalsIgnoreCase("guilds")) {
                writeGuilds(event);
                event.reply("File made guilds.csv.");
            } else if (command.equalsIgnoreCase("reloadlocales")) {
                LocaleManager.init(event.getJDA());
                event.reply("reinitialised all locales.");
            } else if (command.equalsIgnoreCase("synclocales")) {
                LocaleManager.syncLocaleFiles();
                LocaleManager.init(event.getJDA());
                event.reply("synced all locales and reinitialised them.");
            } else if (command.equalsIgnoreCase("scrobble")) {
                GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
                AudioPlayer audioPlayer = musicManager.audioPlayer;
                AudioTrack track = audioPlayer.getPlayingTrack();
                LastFMManager.scrobble(track, event.getUser().getId());
                event.reply("Supposedly scrobbled " + track.getInfo().title + " by " + track.getInfo().author + " to last.fm");
            } else {
                event.reply("Unrecognised dev command " + command);
            }
        }
    }

    @Override
    public String[] getNames() {
        return new String[]{"devtools", "dev"};
    }

    @Override
    public Category getCategory() {
        return Category.Dev;
    }

    @Override
    public String getDescription() {
        return "A suite of testing tools for making sure features work";
    }

    @Override
    public String getOptions() {
        return "(dirty-config | test-buttons | threads | sleep | guilds | reloadlocales | synclocales)"; //(Command1 | Command2 | Command3) - add them here once they exist
    }

    @Override
    public void ProvideOptions(SlashCommandData slashCommand) {
        slashCommand.addOption(OptionType.STRING, "query", "The query", true);
    }
}

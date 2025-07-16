package Bots.managers;

import Bots.BaseCommand;
import Bots.CommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static Bots.CommandEvent.createQuickError;
import static Bots.Main.*;
import static Bots.managers.LocaleManager.languages;
import static Bots.managers.LocaleManager.managerLocalise;

public class EventManager extends ListenerAdapter {
    private static final Map<String, Consumer<ButtonInteractionEvent>> ButtonInteractionMappings = new HashMap<>();
    private static final Map<String, Consumer<StringSelectInteractionEvent>> SelectionInteractionMappings = new HashMap<>();

    public static void registerSelectionInteraction(String[] names, Consumer<StringSelectInteractionEvent> func) {
        for (String name : names) {
            registerSelectionInteraction(name, func);
        }
    }

    public static void registerSelectionInteraction(String name, Consumer<StringSelectInteractionEvent> func) {
        if (SelectionInteractionMappings.containsKey(name)) {
            System.err.println("Attempting to override the selection interaction manager for id " + name);
        }
        SelectionInteractionMappings.put(name, func);
    }

    public static void registerButtonInteraction(String[] names, Consumer<ButtonInteractionEvent> func) {
        for (String name : names) {
            registerButtonInteraction(name, func);
        }
    }

    public static void registerButtonInteraction(String name, Consumer<ButtonInteractionEvent> func) {
        if (ButtonInteractionMappings.containsKey(name)) {
            System.err.println("Attempting to override the button manager for id " + name);
        }
        ButtonInteractionMappings.put(name, func);
    }

    private float handleRateLimit(BaseCommand Command, Member member) {
        long ratelimit = Command.getRatelimit();
        long lastRatelimit = ratelimitTracker.get(Command).getOrDefault(member.getIdLong(), 0L);
        long curTime = System.currentTimeMillis();
        float timeLeft = (ratelimit - (curTime - lastRatelimit)) / 1000F;
        if (timeLeft > 0f) {
            return timeLeft;
        } else {
            ratelimitTracker.get(Command).put(member.getIdLong(), curTime);
            return -1F;
        }
    }

    private boolean processSlashCommand(BaseCommand Command, SlashCommandInteractionEvent event) {
        if (event.getInteraction().getName().equalsIgnoreCase(Command.getNames()[0])) {
            float ratelimitTime = handleRateLimit(Command, Objects.requireNonNull(event.getInteraction().getMember()));
            Map<String, String> lang = guildLocales.get(Objects.requireNonNull(event.getGuild()).getIdLong());
            if (ratelimitTime > 0) {
                event.replyEmbeds(createQuickError(managerLocalise("main.ratelimit", lang, ratelimitTime), lang)).setEphemeral(true).queue();
            } else {
                //run command
                String primaryName = Command.getNames()[0];
                commandUsageTracker.put(primaryName, Long.parseLong(String.valueOf(commandUsageTracker.get(primaryName))) + 1); //Nightmarish type conversion but I'm not seeing better
                commandUsageTracker.put("slashcommand", Long.parseLong(String.valueOf(commandUsageTracker.get("slashcommand"))) + 1);
                commandThreads.submit(() -> {
                    try {
                        Command.executeWithChecks(new CommandEvent(event));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
            return true;
        }
        return false;
    }

    private boolean processCommand(String matchTerm, BaseCommand Command, MessageReceivedEvent event) {
        String commandLower = event.getMessage().getContentRaw().toLowerCase();
        commandLower = commandLower.replaceFirst(botPrefix, "").trim().replaceAll(" +", " ");
        if (commandLower.startsWith(matchTerm)) {
            if (commandLower.length() != matchTerm.length()) { //Makes sure we aren't misinterpreting
                String afterChar = commandLower.substring(matchTerm.length(), matchTerm.length() + 1);
                if (!afterChar.equals(" ") && !afterChar.equals("\n")) { //Ensure there's whitespace afterwards
                    return false;
                }
            }
            //ratelimit code. ratelimit is per-user per-guild
            float ratelimitTime = handleRateLimit(Command, Objects.requireNonNull(event.getMember()));
            Map<String, String> lang = guildLocales.get(Objects.requireNonNull(event.getGuild()).getIdLong());
            if (ratelimitTime > 0) {
                event.getMessage().replyEmbeds(createQuickError(managerLocalise("main.ratelimit", lang, ratelimitTime), lang)).queue(message -> message.delete().queueAfter((long) ratelimitTime, TimeUnit.SECONDS));
            } else {
                //run command
                String primaryName = Command.getNames()[0];
                commandUsageTracker.put(primaryName, Long.parseLong(String.valueOf(commandUsageTracker.get(primaryName))) + 1); //Nightmarish type conversion but I'm not seeing better
                commandUsageTracker.put("prefixcommand", Long.parseLong(String.valueOf(commandUsageTracker.get("prefixcommand"))) + 1);
                commandThreads.submit(() -> {
                    try {
                        Command.executeWithChecks(new CommandEvent(event));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
            return true;
        }
        return false;
    }

    @Override
    public void onShutdown(@NotNull ShutdownEvent event) {
        event.getJDA().getAudioManagers().clear();
    }

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        // nothing to do
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(createQuickError("I currently do not work outside of discord servers.", LocaleManager.languages.get("english"))).queue(); // this cannot be localised because it isn't in a guild.
            return;
        }
        Map<String, String> lang = guildLocales.get(Objects.requireNonNull(event.getGuild()).getIdLong());
        if (!event.getGuild().getSelfMember().hasPermission(event.getGuildChannel(), Permission.VIEW_CHANNEL)) {
            event.replyEmbeds(createQuickError(managerLocalise("main.botNoPermission", lang), lang)).setEphemeral(true).queue();
            return;
        }
        for (BaseCommand Command : commands) {
            if (processSlashCommand(Command, event)) {
                return;
            }
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String messageContent = event.getMessage().getContentRaw();
        if (messageContent.isEmpty()) {
            return;
        }

        if (event.getMessage().getMentions().getUsers().contains(event.getJDA().getSelfUser())) {
            for (BaseCommand Command : commands) {
                for (String alias : Command.getNames()) {
                    if (processCommand(alias, Command, event)) {
                        return; //Command executed, stop checking
                    }
                }
            }
        }
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        trackLoops.put(event.getGuild().getIdLong(), 0);
        event.getJDA().getPresence().setActivity(Activity.playing("use /language to change the language! | playing music for " + getBot().getGuilds().size() + " servers!"));
        //event.getJDA().getPresence().setActivity(Activity.playing(String.format("music for %,d servers! | " + readableBotPrefix + " help", event.getJDA().getGuilds().size())));
        try {
            GuildDataManager.CreateGuildConfig(event.getGuild().getIdLong());
        } catch (IOException e) {
            e.printStackTrace();
        }
        guildLocales.putIfAbsent(event.getGuild().getIdLong(), languages.get("english"));
    }

    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        trackLoops.remove(event.getGuild().getIdLong());
        event.getJDA().getPresence().setActivity(Activity.playing("use /language to change the language! | playing music for " + getBot().getGuilds().size() + " servers!"));
        //event.getJDA().getPresence().setActivity(Activity.playing("music for " + event.getJDA().getGuilds().size() + " servers! | " + readableBotPrefix + " help"));
        GuildDataManager.RemoveConfig(event.getGuild().getIdLong());
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonID = Objects.requireNonNull(event.getInteraction().getButton().getId());
        for (String name : ButtonInteractionMappings.keySet()) {
            if (name.equalsIgnoreCase(buttonID)) {
                try {
                    ButtonInteractionMappings.get(name).accept(event);
                } catch (Exception e) {
                    System.err.println("Issue handling button interaction for " + name);
                    e.printStackTrace();
                }
                return;
            }
        }
        System.err.println("Button of ID " + buttonID + " has gone ignored - missing listener?");
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String selectionID = event.getInteraction().getComponent().getId();
        for (String name : SelectionInteractionMappings.keySet()) {
            if (name.equalsIgnoreCase(selectionID)) {
                try {
                    SelectionInteractionMappings.get(name).accept(event);
                } catch (Exception e) {
                    System.err.println("Issue handling selection interaction for " + name);
                    e.printStackTrace();
                }
                return;
            }
        }
        System.err.println("Selection interaction of ID " + selectionID + " has gone ignored - missing listener?");
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        if (event.getChannelLeft() == null) {
            // GuildVoiceJoinEvent
            return;
        } else if (event.getChannelJoined() == null) {
            // GuildVoiceLeaveEvent
            if (event.getMember() == event.getGuild().getSelfMember()) { //we left
                cleanUpAudioPlayer(event.getGuild());
                return;
            } else { //someone else left
                assert true;
            }
        } else {
            assert true;
            // GuildVoiceMoveEvent
        }
        GuildVoiceState voiceState = Objects.requireNonNull(event.getGuild().getSelfMember().getVoiceState());
        if (voiceState.getChannel() != null) {
            int members = 0;
            for (Member member : voiceState.getChannel().getMembers()) {
                if (!member.getUser().isBot()) {
                    members++;
                }
            }
            if (members == 0) { // If alone
                cleanUpAudioPlayer(event.getGuild());
            }
        }
    }

}

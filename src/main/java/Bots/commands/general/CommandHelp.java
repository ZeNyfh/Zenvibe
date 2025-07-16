package Bots.commands.general;

import Bots.BaseCommand;
import Bots.CommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static Bots.managers.EventManager.registerButtonInteraction;
import static Bots.managers.LocaleManager.managerLocalise;
import static Bots.Main.*;

public class CommandHelp extends BaseCommand {
    final List<ItemComponent> CategoryButtons = new ArrayList<>();

    public String getCommands(Category category) {
        StringBuilder Commands = new StringBuilder();
        for (BaseCommand Command : commands) {
            if (Command.getCategory() == category) {
                Commands.append(" `").append(Command.getNames()[0]).append("`");
            }
        }
        if (Commands.isEmpty()) {
            return "none";
        } else {
            return Commands.toString();
        }
    }

    private void BuildEmbedFromCategory(EmbedBuilder embed, Category category, long guildID) {
        Map<String, String> lang = guildLocales.get(guildID);
        switch (category) { //Note: This generates an anonymous reference (CommandHelp$1). I do not know why nor how, nor does it matter, but I'm still confused. -9382
            case General -> embed.setTitle(managerLocalise("cmd.help.cat.general", lang)); // General
            case Music -> embed.setTitle(managerLocalise("cmd.help.cat.music", lang)); // Music
            case DJ -> embed.setTitle(managerLocalise("cmd.help.cat.DJ", lang)); // DJ
            case Admin -> embed.setTitle(managerLocalise("cmd.help.cat.admin", lang)); // Admin
            case Dev -> embed.setTitle(managerLocalise("cmd.help.cat.dev", lang)); // Dev
            default -> System.err.println("Unrecognised category for help title: " + category);
        }
        int i = 0;
        for (BaseCommand Command : commands) {
            if (Command.getCategory() == category) {
                i++;
                StringBuilder aliases = new StringBuilder();
                if (Command.getNames().length == 2) {
                    aliases.append(managerLocalise("cmd.help.alias", lang));
                } else if (Command.getNames().length > 2) {
                    aliases.append(managerLocalise("cmd.help.alias.plural", lang));
                }
                aliases.append(" ");
                int j = 0;
                for (String name : Command.getNames()) {
                    j++;
                    if (j == 1) {
                        continue;
                    }
                    if (j != Command.getNames().length) {
                        aliases.append("**(").append(name).append(")**, ");
                    } else {
                        aliases.append("**(").append(name).append(")**");
                    }
                }
                embed.appendDescription("`" + i + ")` **" + Command.getNames()[0] + " " + Command.getOptions() + "** - " + Command.getDescription() + aliases + "\n\n");
            }
        }
    }

    private void HandleButtonEvent(ButtonInteractionEvent event) {
        EmbedBuilder eb = new EmbedBuilder();
        Map<String, String> lang = guildLocales.get(Objects.requireNonNull(event.getGuild()).getIdLong());
        eb.setColor(botColour);
        eb.setFooter(lang.get("cmd.help.footer")); // Syntax: "<>" is a required argument, "[]" is an optional argument. "()" is an alternate word for the command.
        Category ButtonCategory = Category.valueOf(Objects.requireNonNull(event.getButton().getId()).split("help-")[1]);
        BuildEmbedFromCategory(eb, ButtonCategory, Objects.requireNonNull(event.getGuild()).getIdLong());
        event.getInteraction().editMessageEmbeds().setEmbeds(eb.build()).queue();
    }

    @Override
    public void Init() {
        String[] ExpectedButtonIDs = new String[Category.values().length];
        for (int i = 0; i < Category.values().length; i++) {
            Category category = Category.values()[i];
            ExpectedButtonIDs[i] = "help-" + category.name();
            if (category != Category.Dev)
                CategoryButtons.add(Button.secondary("help-" + category.name(), category.name()));
        }
        registerButtonInteraction(ExpectedButtonIDs, this::HandleButtonEvent);
    }

    @Override
    public void execute(CommandEvent event) {
        Category userCategory = null;
        if (event.getArgs().length > 1) {
            for (Category category : Category.values()) {
                if (category.name().equalsIgnoreCase(event.getArgs()[1])) {
                    userCategory = category;
                    break;
                }
            }
        }
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(botColour);
        embed.setFooter(event.localise("cmd.help.footer"));
        if (userCategory != null) {
            BuildEmbedFromCategory(embed, userCategory, event.getGuild().getIdLong());
        } else {
            embed.setTitle(event.localise("cmd.help.embedTitle"));
            for (Category category : Category.values()) {
                if (category != Category.Dev)
                    embed.appendDescription("**" + category.name() + "**\n" + getCommands(category) + "\n\n");
            }
            embed.setFooter(event.localise("cmd.help.originalFooter"));
        }
        event.replyEmbeds(a -> a.setActionRow(CategoryButtons), embed.build());
    }

    @Override
    public Category getCategory() {
        return Category.General;
    }

    @Override
    public String[] getNames() {
        return new String[]{"help"};
    }

    @Override
    public String getDescription() {
        return "Shows you a list of commands.";
    }

    @Override
    public String getOptions() {
        return "[Category]";
    }

    @Override
    public void ProvideOptions(SlashCommandData slashCommand) {
        slashCommand.addOption(OptionType.STRING, "category", "Subcategory of commands to get information on", false);
    }

    @Override
    public long getRatelimit() {
        return 1000;
    }
}

package Zenvibe.commands.admin;

import Zenvibe.BaseCommand;
import Zenvibe.CommandEvent;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.json.simple.JSONObject;

import java.util.Map;
import java.util.Objects;

import static Zenvibe.CommandEvent.createQuickError;
import static Zenvibe.CommandEvent.createQuickSuccess;
import static Zenvibe.managers.EmbedManager.createQuickEmbed;
import static Zenvibe.managers.GuildDataManager.GetGuildConfig;
import static Zenvibe.managers.LocaleManager.languages;
import static Zenvibe.managers.LocaleManager.managerLocalise;
import static Zenvibe.Main.*;

public class CommandLocale extends BaseCommand {

    private void HandleSelectionEvent(StringSelectInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(createQuickError("I currently do not work outside of discord servers.", null)).queue();
            return;
        }
        JSONObject config = GetGuildConfig(Objects.requireNonNull(event.getGuild()).getIdLong());
        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.MESSAGE_MANAGE)) {
            Map<String, String> curLang = languages.get(config.get("Locale"));
            event.replyEmbeds(createQuickError(managerLocalise("main.noPermission", curLang), curLang)).setEphemeral(true).queue();
            return;
        }

        String selectionValue = event.getInteraction().getSelectedOptions().getFirst().getValue();
        config.put("Locale", selectionValue);
        Map<String, String> locale = languages.get(selectionValue);
        guildLocales.put(event.getGuild().getIdLong(), locale);
        event.replyEmbeds(createQuickSuccess(managerLocalise("cmd.loc.languageChanged", locale, Character.toUpperCase(selectionValue.charAt(0)) + selectionValue.substring(1)), locale)).queue();
    }

    @Override
    public void execute(CommandEvent event) throws Exception {
        StringBuilder builder = new StringBuilder();
        StringSelectMenu.Builder menu = StringSelectMenu.create("langlist");
        for (String langName : languages.keySet().stream().sorted().toList()) {
            String capitalisedLangName = Character.toUpperCase(langName.charAt(0)) + langName.substring(1);
            menu.addOption(languages.get(langName).get("main.flag") + " " +  capitalisedLangName, langName);
            builder.append(String.format("- %s %s\n", languages.get(langName).get("main.flag"), capitalisedLangName));
        }
        String languagesString = builder.toString().trim();
        event.replyEmbeds(response -> response.setActionRow(
                menu.build()), createQuickEmbed(event.localise("cmd.loc.list"), languagesString));
    }

    @Override
    public String[] getNames() {
        return new String[]{"language", "locale", "lang"}; // language is more user-friendly.
    }

    @Override
    public Category getCategory() {
        return Category.Admin;
    }

    @Override
    public String getDescription() {
        return "Changes the language of Zenvibe for the server";
    }

    @Override
    public void Init() {
        registerSelectionInteraction(new String[]{"langlist"}, this::HandleSelectionEvent);
    }

    @Override
    public long getRatelimit() {
        return 10000;
    }
}

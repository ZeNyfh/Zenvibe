package Zenvibe.commands.admin;

import Zenvibe.BaseCommand;
import Zenvibe.CommandEvent;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static Zenvibe.Main.botColour;
import static Zenvibe.managers.JsonBrowserManager.asStringList;

public class CommandDJ extends BaseCommand {
    private static final Pattern MENTION_REGEX = Pattern.compile("(?:<@&?)?(\\d+)>?");

    @Override
    public void execute(CommandEvent event) throws IOException {
        String[] args = event.getArgs();
        
        if (args.length == 1 || "list".equalsIgnoreCase(args[1])) {
            handleListDJs(event);
        } else if ("add".equalsIgnoreCase(args[1])) {
            handleAddRemoveDJs(event, true);
        } else if ("remove".equalsIgnoreCase(args[1])) {
            handleAddRemoveDJs(event, false);
        } else {
            event.replyEmbeds(event.createQuickError(event.localise("cmd.dj.invalidArgs")));
        }
    }

    private void handleListDJs(CommandEvent event) throws IOException {
        JsonBrowser config = JsonBrowser.parse(event.getConfig().toJSONString()); // slowly moving towards the JsonBrowser...
        List<String> djRoles = asStringList(config.get("DJRoles"));
        List<String> djUsers = asStringList(config.get("DJUsers"));
        
        EmbedBuilder eb = new EmbedBuilder();
        StringBuilder builder = new StringBuilder();
        
        builder.append(event.localise("cmd.dj.roleList"));
        if (djRoles.isEmpty()) {
            builder.append(event.localise("cmd.dj.roleListEmpty"));
        } else {
            formatRoleList(event, builder, djRoles);
        }
        
        builder.append(event.localise("cmd.dj.userList"));
        if (djUsers.isEmpty()) {
            builder.append(event.localise("cmd.dj.roleListEmpty"));
        } else {
            formatUserList(builder, djUsers);
        }
        
        eb.setColor(botColour);
        eb.setTitle(String.format(event.localise("cmd.dj.guildDJs"), event.getGuild().getName()));
        eb.appendDescription(builder);
        event.replyEmbeds(eb.build());
    }

    private void formatRoleList(CommandEvent event, StringBuilder builder, List<String> roles) {
        int count = 0;
        for (Object roleObj : roles) {
            count++;
            long roleId = (long) roleObj;
            
            if (roleId == event.getGuild().getIdLong()) {
                builder.append("@everyone");
            } else {
                builder.append("<@&").append(roleId).append(">");
            }
            
            if (count != roles.size()) {
                builder.append(", ");
            }
        }
    }

    private void formatUserList(StringBuilder builder, List<String> users) {
        int count = 0;
        for (Object userObj : users) {
            count++;
            builder.append("<@").append(userObj).append(">");
            
            if (count != users.size()) {
                builder.append(", ");
            }
        }
    }

    private void handleAddRemoveDJs(CommandEvent event, boolean isAdding) {
        if (!event.getMember().hasPermission(Permission.MESSAGE_MANAGE)) {
            event.replyEmbeds(event.createQuickError(event.localise("main.noPermission")));
            return;
        }
        
        DJTargets targets = parseTargets(event);
        if (targets.isEmpty()) {
            event.replyEmbeds(event.createQuickError(event.localise("cmd.dj.notGiven")));
            return;
        }
        
        applyChanges(event.getConfig(), targets, isAdding);
        String responseMessage = buildResponseMessage(event, targets);
        if (isAdding) {
            event.replyEmbeds(event.createQuickSuccess(event.localise("cmd.dj.added", responseMessage)));
        } else {
            event.replyEmbeds(event.createQuickSuccess(event.localise("cmd.dj.removed", responseMessage)));
        }
    }

    private DJTargets parseTargets(CommandEvent event) {
        List<Long> memberIds = new ArrayList<>();
        List<Long> roleIds = new ArrayList<>();
        
        for (int i = 2; i < event.getArgs().length; i++) {
            String arg = event.getArgs()[i];
            Matcher matcher = MENTION_REGEX.matcher(arg);
            
            if (matcher.matches()) {
                long id = Long.parseLong(matcher.group(1));
                
                if (event.getGuild().getMemberById(id) != null) {
                    memberIds.add(id);
                } else if (event.getGuild().getRoleById(id) != null) {
                    roleIds.add(id);
                }
            }
        }
        
        return new DJTargets(memberIds, roleIds);
    }

    private void applyChanges(JSONObject config, DJTargets targets, boolean isAdding) {
        JSONArray djRoles = (JSONArray) config.get("DJRoles");
        JSONArray djUsers = (JSONArray) config.get("DJUsers");

        for (long memberId : targets.memberIds()) {
            if (isAdding && !djUsers.contains(memberId)) {
                djUsers.add(memberId);
            } else if (!isAdding) {
                djUsers.remove(memberId);
            }
        }
        
        for (long roleId : targets.roleIds()) {
            if (isAdding && !djRoles.contains(roleId)) {
                djRoles.add(roleId);
            } else if (!isAdding) {
                djRoles.remove(roleId);
            }
        }
    }

    private String buildResponseMessage(CommandEvent event, DJTargets targets) {
        int memberCount = targets.memberIds().size();
        int roleCount = targets.roleIds().size();
        
        String memberText = memberCount == 1 
                ? event.localise("cmd.dj.member") 
                : event.localise("cmd.dj.member.plural");

        String roleText = roleCount == 1
                ? event.localise("cmd.dj.role")
                : event.localise("cmd.dj.role.plural");

        if (memberCount > 0) {
            if (roleCount > 0) {
                return event.localise("cmd.dj.membersAndRoles", memberCount, memberText, roleCount, roleText);
            } else {
                return String.format("%d %s", memberCount, memberText);
            }
        } else {
            return String.format("%d %s", roleCount, roleText);
        }
    }

    private record DJTargets(List<Long> memberIds, List<Long> roleIds) {
        public boolean isEmpty() {
            return memberIds.isEmpty() && roleIds.isEmpty();
        }
    }

    @Override
    public Category getCategory() {
        return Category.Admin;
    }

    @Override
    public String[] getNames() {
        return new String[]{"dj"};
    }

    @Override
    public String getDescription() {
        return "Adds or removes a user or role from the DJ list. Alternatively, shows you the DJ roles/users.";
    }

    @Override
    public String getOptions() {
        return "add <User/Role> | remove <User/Role>";
    }

    @Override
    public void ProvideOptions(SlashCommandData slashCommand) {
        slashCommand.addSubcommands(
                new SubcommandData("add", "Adds someone/a role from DJ.").addOptions(
                        new OptionData(OptionType.USER, "user", "Gives DJ to the user.", false),
                        new OptionData(OptionType.ROLE, "role", "Gives DJ to the role.", false)
                ),
                new SubcommandData("remove", "Removes someone/a role from DJ.").addOptions(
                        new OptionData(OptionType.USER, "user", "Removes DJ from the user.", false),
                        new OptionData(OptionType.ROLE, "role", "Removes DJ from the role.", false)
                ),
                new SubcommandData("list", "Lists the DJs.")
        );
    }

    @Override
    public long getRatelimit() {
        return 1000;
    }
}
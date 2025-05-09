package Bots.commands.dev;

import Bots.BaseCommand;
import Bots.CommandEvent;
import Bots.CommandStateChecker.Check;
import net.dv8tion.jda.api.utils.FileUpload;

import java.io.*;
import java.util.Objects;

public class CommandGetDump extends BaseCommand {
    @Override
    public Check[] getChecks() {
        return new Check[]{Check.IS_DEV};
    }

    @Override
    public void execute(CommandEvent event) {
        new File("temp/dump.txt").delete();
        event.deferReply();
        try {
            Process p = Runtime.getRuntime().exec("jps");
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String line;
            String PID = "";
            while ((line = reader.readLine()) != null) {
                String[] jpsOutputLine = line.split(" ");
                if (jpsOutputLine.length >= 2 && (jpsOutputLine[1].equals("bot") || jpsOutputLine[1].equals("bot.jar") || jpsOutputLine[1].equals("Main"))) {
                    PID = jpsOutputLine[0];
                }
            }
            reader.close();
            if (Objects.equals(PID, "")) {
                event.replyEmbeds(event.createQuickError("Could not get dump as the process ID was not found."));
                return;
            }
            p = Runtime.getRuntime().exec("jstack " + PID);
            reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new FileWriter("temp/dump.txt"));

            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
            }

            writer.close();
            reader.close();
            p.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
            event.replyEmbeds(event.createQuickError("Could not get dump.\n```\n" + e.getMessage() + "\n```"));
        }
        event.replyFiles(FileUpload.fromData(new File("temp/dump.txt")));
    }

    @Override
    public Category getCategory() {
        return Category.Dev;
    }

    @Override
    public String[] getNames() {
        return new String[]{"getdump", "dump"};
    }

    @Override
    public String getDescription() {
        return "Returns a dump of jstack.";
    }

}

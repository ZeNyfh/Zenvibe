package Zenvibe;

import Zenvibe.lavaplayer.GuildMusicManager;
import Zenvibe.lavaplayer.LastFMManager;
import Zenvibe.lavaplayer.PlayerManager;
import Zenvibe.managers.GuildDataManager;
import Zenvibe.managers.LocaleManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
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
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;

import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static Zenvibe.CommandEvent.createQuickError;
import static Zenvibe.CommandEvent.createQuickSuccess;
import static Zenvibe.managers.GuildDataManager.GetConfig;
import static Zenvibe.managers.GuildDataManager.SaveConfigs;
import static Zenvibe.managers.LocaleManager.languages;
import static Zenvibe.managers.LocaleManager.managerLocalise;
import static java.lang.System.currentTimeMillis;

public class Main extends ListenerAdapter {
    // constants*
    public static final long startupTime = currentTimeMillis();
    public static final GatewayIntent[] INTENTS = {GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_VOICE_STATES};
    // command management
    public static final List<BaseCommand> commands = new ArrayList<>();
    public static final List<SlashCommandData> slashCommands = new ArrayList<>();
    public static final List<String> commandNames = new ArrayList<>(); // Purely for conflict detection
    public static final Map<BaseCommand, Map<Long, Long>> ratelimitTracker = new HashMap<>();
    public static final ThreadPoolExecutor commandThreads = (ThreadPoolExecutor) Executors.newCachedThreadPool();
    // guild management
    public static final Map<Long, List<Member>> skipCountGuilds = new HashMap<>();
    public static final List<Long> AutoplayGuilds = new ArrayList<>();
    public static final List<Long> LoopGuilds = new ArrayList<>();
    public static final List<Long> LoopQueueGuilds = new ArrayList<>();
    public static final Map<Long, Integer> trackLoops = new HashMap<>();
    public static final Map<Long, List<String>> autoPlayedTracks = new HashMap<>();
    public static final Map<Long, Map<String, String>> guildLocales = new HashMap<>();

    private static final Map<String, Consumer<ButtonInteractionEvent>> ButtonInteractionMappings = new HashMap<>();
    private static final Map<String, Consumer<StringSelectInteractionEvent>> SelectionInteractionMappings = new HashMap<>();
    // config
    public static Color botColour = new Color(0, 0, 0);
    public static String botVersion = ""; // YY.MM.DD
    public static String botPrefix = "";
    public static String readableBotPrefix = "";
    public static boolean ignoreFiles = false;
    public static JSONObject commandUsageTracker;
    private static JDA bot;

    public static void main(String[] args) throws Exception {
        OutputLogger.Init("log.log");

        prepareEnvironment();
        Dotenv dotenv = Dotenv.load();
        String botToken = dotenv.get("TOKEN");
        if (botToken == null) {
            throw new NullPointerException("TOKEN is not set in the .env file");
        }
        GuildDataManager.Init();
        commandUsageTracker = GetConfig("usage-stats");
        LastFMManager.Init();
        PlayerManager.getInstance();
        loadCommandClasses();

        Message.suppressContentIntentWarning();
        bot = JDABuilder.create(botToken, Arrays.asList(INTENTS))
                .enableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE)
                .disableCache(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS)
                .addEventListeners(new Main())
                .build();
        bot.awaitReady();
        LocaleManager.init(bot);
        bot.updateCommands().addCommands(slashCommands).queue();
        System.out.println("bot is now running, have fun ig");
        botPrefix = "<@" + bot.getSelfUser().getId() + ">";
        readableBotPrefix = "@" + bot.getSelfUser().getName();
        bot.getPresence().setActivity(Activity.playing("use /language to change the language! | playing music for " + bot.getGuilds().size() + " servers!"));
        //bot.getPresence().setActivity(Activity.playing(String.format("music for %,d servers! | " + readableBotPrefix + " help", bot.getGuilds().size())));
        for (Guild guild : bot.getGuilds()) {
            trackLoops.put(guild.getIdLong(), 0);
            autoPlayedTracks.put(guild.getIdLong(), new ArrayList<>());
        }
        setupTasks();
        recoverQueues();
    }

    // Ensure all folders and files exist, or create them where applicable, and load most .env settings
    private static void prepareEnvironment() throws IOException, URISyntaxException {
        ignoreFiles = new File("config/").mkdir();
        ignoreFiles = new File("update/").mkdir();
        ignoreFiles = new File("temp/").mkdir();
        Path fullDir = Paths.get("temp/").toAbsolutePath();
        if (Files.list(fullDir).findAny().isPresent()) {
            deleteFiles(fullDir.toAbsolutePath().toString());
        }
        File logDir = new File("logs/");
        if (logDir.isDirectory()) {
            File[] logs = logDir.listFiles();
            if (logs != null) {
                int files = logs.length;
                for (File log : logs) {
                    if (files <= 20) {
                        break;
                    }
                    if (System.currentTimeMillis() - log.lastModified() >= 2419200000L) {
                        ignoreFiles = log.delete();
                        files -= 1;
                    }
                }
            }
        }
        botVersion = new SimpleDateFormat("yy.MM.dd").format(new Date(new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).lastModified()));
        File env = new File(".env");
        if (!env.exists()) {
            System.out.println(env.getName() + " doesn't exist, creating now.");
            ignoreFiles = env.createNewFile();
            FileWriter writer = new FileWriter(".env");
            writer.write("# This is the bot token, it needs to be set.\nTOKEN=\n# This is the hex value for the bot colour\nCOLOUR=\n# These 2 are required for spotify support with the bot.\nSPOTIFYCLIENTID=\nSPOTIFYCLIENTSECRET=\n# This is the last.fm API key for some functions of zenvibe\nLASTFMTOKEN=# This is the last.fm API Secret, only used for scrobbling.\nLASTFMSECRET=");
            writer.close();
        }
        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            File startsh = new File("start.sh");
            if (!startsh.exists()) {
                ignoreFiles = startsh.createNewFile();
                FileWriter writer = new FileWriter("start.sh");
                writer.write("#!/bin/bash\nwhile [ True ]; do\n\tjava -jar bot.jar\n\tsleep 2\ndone");
                writer.flush();
                writer.close();
            }
        }
        Dotenv dotenv = Dotenv.load();
        if (dotenv.get("COLOUR") == null) {
            System.err.println("Hex value COLOUR is not set in " + new File(".env").getAbsolutePath() + " example: #FFCCEE");
            throw new NullPointerException("COLOUR is not set in the .env file");
        }
        try {
            botColour = Color.decode(dotenv.get("COLOUR"));
        } catch (NumberFormatException exception) {
            throw new NumberFormatException("Unable to successfully parse the COLOUR from the .env as a colour"); // Provide a more descriptive message
        }
    }

    // Load all the command classes and register them
    private static void loadCommandClasses() throws IOException, URISyntaxException, ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        String tempJarPath = String.valueOf(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        JarFile jarFile = null;
        try {
            jarFile = new JarFile(tempJarPath.substring(5));
        } catch (FileNotFoundException ignored) {
            System.out.println("detected process in IDE, registering commands in a different way...");
            Enumeration<URL> resources = ClassLoader.getSystemClassLoader().getResources("");
            while (resources.hasMoreElements()) {
                URL folder = resources.nextElement();
                if (folder.getPath().contains("classes")) {
                    folder = URI.create("file:" + folder.getPath() + "Zenvibe/commands/").toURL();
                }
                try {
                    for (File subfolder : Objects.requireNonNull(new File(folder.getFile()).listFiles())) {
                        if (subfolder.isDirectory()) {
                            for (File classFile : Objects.requireNonNull(subfolder.listFiles())) {
                                if (classFile.getName().endsWith(".class") && !classFile.getName().contains("$")) {
                                    classes.add(ClassLoader.getSystemClassLoader().loadClass("Zenvibe.commands." + subfolder.getName() + "." + classFile.getName().substring(0, classFile.getName().length() - 6)));
                                }
                            }
                        }
                    }
                    break;
                } catch (Exception ignored1) {
                }
            }
        }
        if (jarFile != null) {
            Enumeration<JarEntry> resources = jarFile.entries();
            while (resources.hasMoreElements()) {
                JarEntry url = resources.nextElement();
                if (url.toString().endsWith(".class") && url.toString().startsWith("Zenvibe/commands/") && !url.toString().contains("$")) {
                    classes.add(ClassLoader.getSystemClassLoader().loadClass(url.getName().substring(0, url.getName().length() - 6).replaceAll("/", ".")));
                }
            }
            jarFile.close();
        }

        // registering all the commands
        for (Class<?> commandClass : classes) {
            try {
                registerCommand((BaseCommand) commandClass.getDeclaredConstructor().newInstance());
                System.out.println("loaded command: " + commandClass.getSimpleName().substring(7));
            } catch (Exception e) {
                System.err.println("Unable to load command: " + commandClass);
                e.printStackTrace();
            }
        }
    }

    // Register hooks and timers as required
    private static void setupTasks() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> GuildDataManager.SaveQueues(bot)));
        Runtime.getRuntime().addShutdownHook(new Thread(GuildDataManager::SaveConfigs));
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            final File updateFile = new File("update/bot.jar");
            final File tempDir = new File("temp/");

            int cleanUpTime = 300; // 5 minutes
            int ytdlpUpdateTime = 7200; // 2 hours

            @Override
            public void run() {
                // temp directory cleanup
                cleanUpTime--;
                // we shouldn't need to check this often or if the directory is empty
                if (cleanUpTime <= 0) {
                    File[] contents = tempDir.listFiles();
                    if (contents != null && contents.length != 0) {
                        // we don't want to delete something as it is being written to.
                        Path fullDir = Paths.get("temp/").toAbsolutePath();
                        if (System.currentTimeMillis() - tempDir.lastModified() > 2000)
                            deleteFiles(fullDir.toAbsolutePath().toString());
                        cleanUpTime = 300;
                    }
                }

                // yt-dlp_linux updater and cleanup
                ytdlpUpdateTime--;
                File ytdlpFile = new File("yt-dlp_linux");
                if (ytdlpUpdateTime <= 0) {
                    try {
                        ytdlpUpdateTime = 21600;
                        new ProcessBuilder(ytdlpFile.getAbsolutePath() + " --update").start();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                // updater code
                if (updateFile.exists() && !System.getProperty("os.name").toLowerCase().contains("windows")) { // auto-updater only works on linux
                    // leeway for upload past the time limit
                    if (System.currentTimeMillis() - updateFile.lastModified() >= 10000) {
                        System.out.println("It's update time!");
                        File botJar = new File("bot.jar");
                        ignoreFiles = botJar.delete();
                        ignoreFiles = updateFile.renameTo(botJar);
                        killMain();
                    }
                }
            }
        };
        timer.scheduleAtFixedRate(task, 0, 1000);
    }

    private static void registerCommand(BaseCommand command) {
        command.Init();
        ratelimitTracker.put(command, new HashMap<>());
        commandUsageTracker.putIfAbsent(command.getNames()[0], 0L);
        commandUsageTracker.putIfAbsent("slashcommand", 0L);
        commandUsageTracker.putIfAbsent("prefixcommand", 0L);
        commands.add(command);
        SlashCommandData slashCommand = Commands.slash(command.getNames()[0], command.getDescription());
        command.ProvideOptions(slashCommand);
        command.slashCommand = slashCommand;
        slashCommands.add(slashCommand);
        for (String name : command.getNames()) {
            if (commandNames.contains(name)) {
                System.err.println("Command conflict - 2 commands are attempting to use the name " + name);
            } else {
                commandNames.add(name);
            }
        }
    }

    public static AudioTrack getTrackFromQueue(Guild guild, int queuePos) {
        final GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(guild);
        List<AudioTrack> queue = new ArrayList<>(musicManager.scheduler.queue);
        if (queue.isEmpty()) {
            return null;
        } else {
            return queue.get(queuePos);
        }
    }

    public static GuildChannel getGuildChannelFromID(Long ID) {
        return bot.getGuildChannelById(ID);
    }

    public static void deleteFiles(String filePrefix) { // ONLY USE THIS IF YOU KNOW WHAT YOU ARE DOING
        File directory = new File(filePrefix);
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null && files.length == 0) {
                return;
            }
        }
        // I use cmd here as the normal java method for this would throw an exception if a file is being accessed (such as the bot.jar file)
        try {
            if (filePrefix.isEmpty()) {
                System.err.println("Tried to delete empty string, bad idea.");
                return;
            }
            if (!System.getProperty("os.name").toLowerCase().contains("windows") && directory != null) {
                for (File file : Objects.requireNonNull(directory.listFiles())) {
                    ignoreFiles = file.delete();
                }
                return;
            }
            String[] command = new String[]{"cmd", "/c", "del", "/Q", "\"" + filePrefix + "\\*\""};
            Process process = new ProcessBuilder(command).start();
            int exitCode = process.waitFor();
            String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (exitCode != 0) {
                System.err.println("Error deleting file, Exit code: " + exitCode + " | Error:" + error);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void cleanUpAudioPlayer(Guild guild) {
        Long id = guild.getIdLong();
        GuildMusicManager manager = PlayerManager.getInstance().getMusicManager(guild);
        LoopGuilds.remove(id);
        LoopQueueGuilds.remove(id);
        AutoplayGuilds.remove(id);
        manager.audioPlayer.setVolume(100);
        manager.scheduler.queue.clear();
        manager.audioPlayer.destroy();
        manager.audioPlayer.setPaused(false);
        manager.audioPlayer.checkCleanup(0);
        guild.getAudioManager().closeAudioConnection();
        skipCountGuilds.remove(guild.getIdLong());
    }

    public static void killMain() {
        SaveConfigs();
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    private static void recoverQueues() throws FileNotFoundException {
        File queueDir = new File(GuildDataManager.configFolder + "/queues/");
        if (Objects.requireNonNull(queueDir.listFiles()).length == 0) { // can be safely ignored and the files can be deleted.
            for (File file : Objects.requireNonNull(queueDir.listFiles())) {
                file.delete();
            }
        } else {
            System.out.println("restoring queues");
            for (File file : Objects.requireNonNull(queueDir.listFiles())) {
                Scanner scanner = new Scanner(file);
                String time;
                try {
                    time = scanner.nextLine();
                } catch (Exception e) {
                    scanner.close();
                    file.delete();
                    e.printStackTrace();
                    continue;
                }
                if (System.currentTimeMillis() - Long.parseLong(time) > 60000) { // 60 seconds feels more reasonable for not restoring a queue
                    scanner.close();
                    ignoreFiles = file.delete();
                    continue;
                }

                String guildID = scanner.nextLine();
                String channelID = scanner.nextLine();
                String vcID = scanner.nextLine();
                String trackPos = scanner.nextLine();
                // track states
                boolean paused = Boolean.parseBoolean(scanner.nextLine());
                boolean looping = Boolean.parseBoolean(scanner.nextLine());
                boolean queueLooping = Boolean.parseBoolean(scanner.nextLine());
                boolean autoplaying = Boolean.parseBoolean(scanner.nextLine());
                // track modifiers
                int volume = Integer.parseInt(scanner.nextLine());
                double speed = Double.parseDouble(scanner.nextLine());
                double pitch = Double.parseDouble(scanner.nextLine());
                float frequency = Float.parseFloat(scanner.nextLine());
                float depth = Float.parseFloat(scanner.nextLine());
                try {
                    Guild guild = bot.getGuildById(guildID);
                    GuildMessageChannelUnion channelUnion = (GuildMessageChannelUnion) Objects.requireNonNull(guild).getGuildChannelById(channelID);
                    Map<String, String> lang = guildLocales.get(guild.getIdLong());
                    VoiceChannel vc = guild.getVoiceChannelById(vcID);
                    if (Objects.requireNonNull(vc).getMembers().isEmpty()) {
                        continue;
                    }
                    guild.getAudioManager().openAudioConnection(guild.getVoiceChannelById(vcID));
                    boolean first = true;
                    GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(guild);
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        if (first) {
                            PlayerManager.getInstance().loadAndPlay(channelUnion, line, false).whenComplete((loadResult, throwable) -> {
                                if (loadResult.songWasPlayed) {
                                    PlayerManager.getInstance().getMusicManager(guild).audioPlayer.getPlayingTrack().setPosition(Long.parseLong(trackPos));
                                } else {
                                    System.err.println("Track " + line + " from a restored queue was unable to be loaded: " + loadResult.name());
                                }
                            });
                            first = false;
                        } else {
                            PlayerManager.getInstance().loadAndPlay(channelUnion, line, false);
                        }
                    }
                    AudioPlayer player = musicManager.audioPlayer;
                    // setting player states
                    player.setPaused(paused);
                    if (looping) LoopGuilds.add(Long.valueOf(guildID));
                    if (queueLooping) LoopQueueGuilds.add(Long.valueOf(guildID));
                    if (autoplaying) AutoplayGuilds.add(Long.valueOf(guildID));
                    // setting track modifiers
                    player.setVolume(volume);
                    // TODO: audio filters to be added here.

                    Objects.requireNonNull(channelUnion).sendMessageEmbeds(createQuickSuccess(managerLocalise("main.update", lang), lang)).queue();
                    scanner.close();
                    ignoreFiles = file.delete();
                } catch (Exception e) {
                    scanner.close();
                    ignoreFiles = file.delete();
                    e.printStackTrace();
                }
                scanner.close();
                ignoreFiles = file.delete();
            }
        }
    }

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

    public static JDA getBot() {
        return bot;
    }

    public enum AudioFilters {
        Vibrato, Timescale
    }
}

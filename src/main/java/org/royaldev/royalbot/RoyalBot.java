package org.royaldev.royalbot;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.CharOptionHandler;
import org.kohsuke.args4j.spi.IntOptionHandler;
import org.kohsuke.args4j.spi.LongOptionHandler;
import org.kohsuke.args4j.spi.StringArrayOptionHandler;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.managers.ThreadedListenerManager;
import org.royaldev.royalbot.commands.ChannelCommand;
import org.royaldev.royalbot.commands.impl.AdminCommand;
import org.royaldev.royalbot.commands.impl.BaxFaxCommand;
import org.royaldev.royalbot.commands.impl.ChooseCommand;
import org.royaldev.royalbot.commands.impl.ChuckCommand;
import org.royaldev.royalbot.commands.impl.DefineCommand;
import org.royaldev.royalbot.commands.impl.GoogleCommand;
import org.royaldev.royalbot.commands.impl.HelpCommand;
import org.royaldev.royalbot.commands.impl.IgnoreCommand;
import org.royaldev.royalbot.commands.impl.JoinCommand;
import org.royaldev.royalbot.commands.impl.MCAccountCommand;
import org.royaldev.royalbot.commands.impl.MCPingCommand;
import org.royaldev.royalbot.commands.impl.MessageCommand;
import org.royaldev.royalbot.commands.impl.NumberFactCommand;
import org.royaldev.royalbot.commands.impl.PartCommand;
import org.royaldev.royalbot.commands.impl.PingCommand;
import org.royaldev.royalbot.commands.impl.QuitCommand;
import org.royaldev.royalbot.commands.impl.RepositoryCommand;
import org.royaldev.royalbot.commands.impl.RollCommand;
import org.royaldev.royalbot.commands.impl.RoyalBotCommand;
import org.royaldev.royalbot.commands.impl.ShakespeareInsultCommand;
import org.royaldev.royalbot.commands.impl.ShortenCommand;
import org.royaldev.royalbot.commands.impl.UrbanDictionaryCommand;
import org.royaldev.royalbot.commands.impl.WeatherCommand;
import org.royaldev.royalbot.commands.impl.WolframAlphaCommand;
import org.royaldev.royalbot.commands.impl.channelmanagement.ChannelManagementCommand;
import org.royaldev.royalbot.configuration.Config;
import org.royaldev.royalbot.configuration.ConfigurationSection;
import org.royaldev.royalbot.listeners.YouTubeListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * The main bot.
 */
public class RoyalBot {

    public static void main(String[] args) {
        new RoyalBot(args);
    }

    private final PircBotX bot;
    private final Logger logger = Logger.getLogger("org.royaldev.royalbot.RoyalBot");
    @SuppressWarnings("FieldCanBeLocal")
    private final String botVersion = this.getClass().getPackage().getImplementationVersion();
    private final CommandHandler ch = new CommandHandler();
    private final ListenerHandler lh = new ListenerHandler();
    private final Config c;
    private final Random random = new Random();
    private static RoyalBot instance;

    @Option(name = "-n", usage = "Define the nickname of the bot", aliases = {"--nick"})
    private String botNick = "RoyalBot";
    @Option(name = "-r", usage = "Define the real name of the bot", aliases = {"--real-name"})
    private String botRealname = "RoyalBot";
    @Option(name = "-f", usage = "Define the response the a CTCP FINGER query", aliases = {"--finger"})
    private String botFinger = "RoyalDev's IRC Management Bot";
    @Option(name = "-l", usage = "Define the bot's login to the server", aliases = {"--login"})
    private String botLogin = "RoyalBot";
    @Option(name = "-s", usage = "Set the server to connect to", aliases = {"--server"}, required = true)
    private String serverHostname;
    @Option(name = "-P", usage = "Set the password of the server", aliases = {"--server-password"})
    private String serverPassword = "";
    @Option(name = "-A", usage = "Set the NickServ password to use", aliases = {"--nickserv-password"})
    private String nickServPassword = "";
    @Option(name = "-z", usage = "Sets the path to the configuration file", aliases = {"--config"})
    private String configPath = null;
    @Option(name = "-C", usage = "Sets the command prefix (one character) to use for the bot", aliases = {"--command-prefix"}, handler = CharOptionHandler.class)
    private char commandPrefix = ':';
    @SuppressWarnings("MismatchedReadAndWriteOfArray")
    @Option(name = "-c", usage = "List of channels to join (e.g. \"#chan #chan2\")", aliases = {"--channels"}, handler = StringArrayOptionHandler.class)
    private String[] channels = new String[0];
    @Option(name = "-p", usage = "Set the port of the server to connect to", aliases = {"--port"}, handler = IntOptionHandler.class)
    private int serverPort = 6667;
    @Option(name = "-d", usage = "Sets the delay between queued messages", aliases = {"--message-delay"}, handler = LongOptionHandler.class)
    private long messageDelay = 1000L;

    private RoyalBot(String[] args) {
        final ConsoleHandler ch = new ConsoleHandler();
        ch.setFormatter(new Formatter() {
            private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            @Override
            public String format(LogRecord logRecord) {
                return sdf.format(new Date()) + " [" + logRecord.getLevel().getLocalizedName() + "] " + logRecord.getMessage() + System.getProperty("line.separator");
            }
        });
        getLogger().setUseParentHandlers(false);
        getLogger().addHandler(ch);
        // Set up log format before logging
        getLogger().info("Starting.");
        instance = this;
        final CmdLineParser clp = new CmdLineParser(this);
        try {
            clp.parseArgument(args);
        } catch (CmdLineException e) {
            System.out.println(e.getMessage());
            clp.printUsage(System.out);
            System.exit(1);
        }
        saveDefaultConfig();
        c = new Config(configPath);
        addCommands();
        addChannelCommands();
        final Configuration.Builder<PircBotX> cb = new Configuration.Builder<>();
        cb.setServer(serverHostname, serverPort)
                .setName(botNick)
                .setRealName(botRealname)
                .setLogin(botLogin)
                .setFinger(botFinger)
                .setVersion("RoyalBot " + botVersion)
                .setListenerManager(new ThreadedListenerManager<>())
                .addListener(new BaseListeners(this))
                .setMessageDelay(messageDelay)
                .setAutoNickChange(true);
        for (String channel : channels) cb.addAutoJoinChannel(channel);
        for (String channel : c.getChannels()) cb.addAutoJoinChannel(channel);
        if (!serverPassword.isEmpty()) cb.setServerPassword(serverPassword);
        if (!nickServPassword.isEmpty()) cb.setNickservPassword(nickServPassword);
        bot = new PircBotX(cb.buildConfiguration());
        addListeners();
        getLogger().info("Connecting.");
        new Thread(new Runnable() {
            public void run() {
                try {
                    bot.startBot();
                } catch (Exception e) {
                    e.printStackTrace();
                    getLogger().severe("Could not start bot: " + e.getClass().getSimpleName() + " (" + e.getMessage() + ")");
                    System.exit(1);
                }
            }
        }).start();
    }

    private void saveDefaultConfig() {
        final File f;
        try {
            f = (configPath == null) ? new File(URLDecoder.decode(RoyalBot.class.getProtectionDomain().getCodeSource().getLocation().toURI().resolve(".").getPath(), "UTF-8"), "config.yml") : new File(configPath);
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }
        if (f.exists()) return;
        getLogger().info("Saving default config.");
        try {
            if (!f.createNewFile()) return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        try (InputStream file = RoyalBot.class.getResourceAsStream("/config.yml"); OutputStream os = new FileOutputStream(f)) {
            int read;
            while ((read = file.read()) != -1) os.write(read);
            os.flush();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        getLogger().info("Saved!");
    }

    private void addCommands() {
        ch.registerCommand(new AdminCommand());
        ch.registerCommand(new BaxFaxCommand());
        ch.registerCommand(new ChannelManagementCommand());
        ch.registerCommand(new ChooseCommand());
        ch.registerCommand(new ChuckCommand());
        ch.registerCommand(new DefineCommand());
        ch.registerCommand(new GoogleCommand());
        ch.registerCommand(new HelpCommand());
        ch.registerCommand(new IgnoreCommand());
        ch.registerCommand(new JoinCommand());
        ch.registerCommand(new MCAccountCommand());
        ch.registerCommand(new MCPingCommand());
        ch.registerCommand(new MessageCommand());
        ch.registerCommand(new NumberFactCommand());
        ch.registerCommand(new PartCommand());
        ch.registerCommand(new PingCommand());
        ch.registerCommand(new QuitCommand());
        ch.registerCommand(new RepositoryCommand());
        ch.registerCommand(new RollCommand());
        ch.registerCommand(new RoyalBotCommand());
        ch.registerCommand(new ShakespeareInsultCommand());
        ch.registerCommand(new ShortenCommand());
        ch.registerCommand(new UrbanDictionaryCommand());
        ch.registerCommand(new WeatherCommand());
        ch.registerCommand(new WolframAlphaCommand());
    }

    private void addListeners() {
        lh.registerListener(new YouTubeListener());
    }

    private void addChannelCommands() {
        ConfigurationSection cs = getConfig().getChannelCommands();
        for (final String channel : cs.getKeys(false)) {
            ConfigurationSection channelCommands = cs.getConfigurationSection(channel);
            for (final String command : channelCommands.getKeys(false)) {
                final ChannelCommand cc;
                try {
                    cc = BotUtils.createChannelCommand(channelCommands.getString(command, ""), channel);
                } catch (Exception e) {
                    continue;
                }
                ch.registerCommand(cc);
            }
        }
    }

    public PircBotX getBot() {
        return bot;
    }

    public Logger getLogger() {
        return logger;
    }

    public Config getConfig() {
        return c;
    }

    public CommandHandler getCommandHandler() {
        return ch;
    }

    public ListenerHandler getListenerHandler() {
        return lh;
    }

    public char getCommandPrefix() {
        return commandPrefix;
    }

    public Random getRandom() {
        return random;
    }

    public static RoyalBot getInstance() {
        return instance;
    }
}

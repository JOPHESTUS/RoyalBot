package org.royaldev.royalbot;

import org.apache.commons.lang3.ArrayUtils;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.Event;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.ConnectEvent;
import org.pircbotx.hooks.events.DisconnectEvent;
import org.pircbotx.hooks.events.InviteEvent;
import org.pircbotx.hooks.events.JoinEvent;
import org.pircbotx.hooks.events.KickEvent;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.events.PartEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;
import org.pircbotx.hooks.types.GenericChannelEvent;
import org.pircbotx.hooks.types.GenericChannelUserEvent;
import org.pircbotx.hooks.types.GenericMessageEvent;
import org.pircbotx.hooks.types.GenericUserEvent;
import org.royaldev.royalbot.auth.Auth;
import org.royaldev.royalbot.commands.CallInfo;
import org.royaldev.royalbot.commands.ChannelCommand;
import org.royaldev.royalbot.commands.IRCCommand;
import org.royaldev.royalbot.configuration.ChannelPreferences;
import org.royaldev.royalbot.listeners.IRCListener;
import org.royaldev.royalbot.listeners.Listener;

import java.lang.reflect.Method;
import java.util.List;

/**
 * The basic listeners of the bot that allow it to function.
 */
final class BaseListeners extends ListenerAdapter<PircBotX> {

    private final RoyalBot rb;

    BaseListeners(final RoyalBot instance) {
        rb = instance;
    }

    @Override
    public void onEvent(final Event<PircBotX> event) {
        try {
            super.onEvent(event);
        } catch (final Exception ex) {
            ex.printStackTrace();
        }
        for (final IRCListener il : rb.getListenerHandler().getAll()) {
            if (event instanceof GenericChannelEvent) {
                final GenericChannelEvent gce = (GenericChannelEvent) event;
                final ChannelPreferences cp = new ChannelPreferences(gce.getChannel().getName());
                if (cp.getDisabledListeners().contains(il.getName())) continue;
            }
            if (event instanceof GenericUserEvent) {
                final GenericUserEvent gue = (GenericUserEvent) event;
                if (BotUtils.isIgnored(BotUtils.generateHostmask(gue.getUser()))) continue;
            }
            if (event instanceof GenericChannelUserEvent) {
                final GenericChannelUserEvent gcue = (GenericChannelUserEvent) event;
                final ChannelPreferences cp = new ChannelPreferences(gcue.getChannel().getName());
                if (BotUtils.isIgnored(BotUtils.generateHostmask(gcue.getUser()), cp.getIgnores())) continue;
            }
            for (final Method m : il.getClass().getDeclaredMethods()) {
                if (m.getAnnotation(Listener.class) == null) continue;
                final Class<?>[] params = m.getParameterTypes();
                if (params.length != 1) continue;
                final Class clazz = params[0];
                if (!Event.class.isAssignableFrom(clazz)) continue;
                if (event.getClass() != clazz) continue;
                try {
                    m.invoke(il, event);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onGenericChannel(final GenericChannelEvent<PircBotX> e) {
        if (e.getChannel().getUsers().size() <= 1) {
            e.getChannel().send().part("Alone.");
        }
    }

    @Override
    public void onConnect(final ConnectEvent<PircBotX> e) {
        rb.getLogger().info("Connected!");
        rb.getPluginLoader().enablePlugins();
    }

    @Override
    public void onDisconnect(final DisconnectEvent<PircBotX> e) {
        rb.getPluginLoader().disablePlugins();
    }

    @Override
    public void onInvite(final InviteEvent<PircBotX> e) {
        e.getBot().sendIRC().joinChannel(e.getChannel());
        rb.getLogger().info("Invited to " + e.getChannel() + " by " + e.getUser() + ".");
    }

    @Override
    public void onJoin(final JoinEvent<PircBotX> e) {
        if (!e.getUser().getNick().equals(rb.getBot().getUserBot().getNick())) return;
        List<String> channels = rb.getConfig().getChannels();
        if (channels.contains(e.getChannel().getName())) return;
        channels.add(e.getChannel().getName());
        rb.getConfig().setChannels(channels);
        rb.getLogger().info("Joined " + e.getChannel().getName() + ".");
    }

    @Override
    public void onPart(final PartEvent<PircBotX> e) {
        if (!e.getUser().getNick().equals(rb.getBot().getUserBot().getNick())) return;
        List<String> channels = rb.getConfig().getChannels();
        if (channels.contains(e.getChannel().getName())) channels.remove(e.getChannel().getName());
        rb.getConfig().setChannels(channels);
        rb.getLogger().info("Parted from " + e.getChannel().getName() + ".");
    }

    @Override
    public void onKick(final KickEvent<PircBotX> e) {
        if (!e.getUser().getNick().equals(rb.getBot().getUserBot().getNick())) return;
        List<String> channels = rb.getConfig().getChannels();
        if (channels.contains(e.getChannel().getName())) channels.remove(e.getChannel().getName());
        rb.getConfig().setChannels(channels);
        rb.getLogger().info("Kicked from " + e.getChannel().getName() + ".");
    }

    @Override
    public void onGenericMessage(final GenericMessageEvent<PircBotX> e) {
        if (BotUtils.isIgnored(BotUtils.generateHostmask(e.getUser()))) return;
        if (!(e instanceof MessageEvent) && !(e instanceof PrivateMessageEvent)) return;
        final boolean isPrivateMessage = e instanceof PrivateMessageEvent;
        String message = e.getMessage();
        if (message.isEmpty()) return;
        if (message.startsWith(e.getBot().getNick()) && !isPrivateMessage) {
            message = e.getMessage().substring(e.getBot().getNick().length());
            if (message.charAt(0) != '.') return;
            message = message.substring(1);
            int parenIndex = message.indexOf('(');
            String command = message.substring(0, parenIndex);
            message = message.substring(parenIndex);
            if (!message.startsWith("(") || !message.endsWith(");")) return;
            message = message.substring(1, message.length() - 2);
            message = rb.getCommandPrefix() + command + " " + message;
        }
        if (message.charAt(0) != rb.getCommandPrefix() && !isPrivateMessage) return;
        final String[] split = message.trim().split(" ");
        final String commandString = (!isPrivateMessage) ? split[0].substring(1, split[0].length()) : split[0];
        IRCCommand command = rb.getCommandHandler().get(commandString);
        if (command == null && !isPrivateMessage) // search for channel-specific commands
            command = rb.getCommandHandler().get(commandString + ":" + ((MessageEvent) e).getChannel().getName());
        if (command == null) {
            if (isPrivateMessage) e.respond("No such command.");
            return;
        }
        if (command instanceof ChannelCommand && !isPrivateMessage) {
            MessageEvent me = (MessageEvent) e;
            final String[] names = command.getName().split(":");
            if (names.length < 2) return; // invalid command name
            if (!me.getChannel().getName().equalsIgnoreCase(names[names.length - 1])) return; // wrong channel
        }
        if (!isPrivateMessage) {
            MessageEvent me = (MessageEvent) e;
            ChannelPreferences cp = new ChannelPreferences(me.getChannel().getName());
            if (BotUtils.isIgnored(BotUtils.generateHostmask(me.getUser()), cp.getIgnores())) return;
            if (cp.getDisabledCommands().contains(command.getName())) return;
        }
        final IRCCommand.CommandType commandType = command.getCommandType();
        if (!isPrivateMessage && commandType != IRCCommand.CommandType.MESSAGE && commandType != IRCCommand.CommandType.BOTH)
            return;
        else if (isPrivateMessage && commandType != IRCCommand.CommandType.PRIVATE && commandType != IRCCommand.CommandType.BOTH)
            return;
        final IRCCommand.AuthLevel authLevel = command.getAuthLevel();
        if (authLevel == IRCCommand.AuthLevel.ADMIN && !Auth.checkAuth(e.getUser()).isAuthed()) {
            e.getUser().send().notice("You are not an admin!");
            return;
        }
        if (authLevel == IRCCommand.AuthLevel.SUPERADMIN && (!rb.getConfig().getSuperAdmin().equalsIgnoreCase(e.getUser().getNick()) || !Auth.checkAuth(e.getUser()).isAuthed())) {
            e.getUser().send().notice("You are not a superadmin!");
            return;
        }
        rb.getLogger().info(((isPrivateMessage) ? "" : ((MessageEvent) e).getChannel().getName() + "/") + e.getUser().getNick() + ": " + e.getMessage());
        try {
            command.onCommand(e, new CallInfo(commandString, ((isPrivateMessage) ? CallInfo.UsageType.PRIVATE : CallInfo.UsageType.MESSAGE)), ArrayUtils.subarray(split, 1, split.length));
        } catch (Throwable t) {
            final StringBuilder sb = new StringBuilder("Unhandled command exception! ");
            sb.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
            String url = BotUtils.linkToStackTrace(t);
            if (url != null) sb.append(" (").append(url).append(")");
            e.getUser().send().notice(sb.toString());
            rb.getLogger().warning(sb.toString());
        }
    }

}

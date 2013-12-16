package org.royaldev.royalbot.commands.impl;

import org.pircbotx.hooks.types.GenericMessageEvent;
import org.royaldev.royalbot.commands.CallInfo;
import org.royaldev.royalbot.commands.NoticeableCommand;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class IsUpCommand extends NoticeableCommand {

    @Override
    public void onCommand(GenericMessageEvent event, CallInfo callInfo, String[] args) {
        if (args.length < 1) {
            notice(event, "Not enough arguments.");
            return;
        }
        final InetAddress ia;
        final boolean isReachable;
        try {
            ia = InetAddress.getByName(args[0]);
            isReachable = ia.isReachable(2500);
        } catch (UnknownHostException e) {
            notice(event, "Unknown host.");
            return;
        } catch (IOException e) {
            notice(event, "An input/output error occurred. Please try again.");
            return;
        }
        event.respond(ia.getHostName() + " is " + ((isReachable) ? "" : "not ") + "up.");
    }

    @Override
    public String getName() {
        return "isup";
    }

    @Override
    public String getUsage() {
        return "<command> [host]";
    }

    @Override
    public String getDescription() {
        return "Checks if a host is up";
    }

    @Override
    public String[] getAliases() {
        return new String[0];
    }

    @Override
    public CommandType getCommandType() {
        return CommandType.BOTH;
    }

    @Override
    public AuthLevel getAuthLevel() {
        return AuthLevel.PUBLIC;
    }
}
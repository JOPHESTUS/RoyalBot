package org.royaldev.royalbot.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WrapFactory;
import org.pircbotx.hooks.events.MessageEvent;
import org.pircbotx.hooks.types.GenericMessageEvent;
import org.royaldev.royalbot.BotUtils;
import org.royaldev.royalbot.RoyalBot;

import java.util.HashMap;
import java.util.Map;

public abstract class ChannelCommand extends NoticeableCommand {

    private final RoyalBot rb = RoyalBot.getInstance();
    private final ObjectMapper om = new ObjectMapper();

    static {
        ContextFactory.initGlobal(new SandboxContextFactory());
    }

    /**
     * Gets the name of the command without the channel appended.
     *
     * @return Name of the command
     */
    public abstract String getBaseName();

    /**
     * Gets the channel for this command to execute in.
     *
     * @return Channel to execute in
     */
    public abstract String getChannel();

    /**
     * Gets the JavaScript for the command.
     *
     * @return JavaScript in String
     */
    public abstract String getJavaScript();

    /**
     * This executes the command. In ChannelCommands, this will set up a Context for Rhino to use to execute the
     * command's JavaScript, which will be obtained from {@link #getJavaScript()}. Two global variables will be passed
     * to the script: <code>event</code> and <code>args</code>, the same that are used in this method.
     * <code>event</code> will always be a {@link MessageEvent}. If any exceptions occur while the JavaScript is being
     * processed, they will be caught and pasted.
     *
     * @param event    Event of receiving command
     * @param callInfo Information received at the calling of this command
     * @param args     Arguments passed to the command
     */
    @Override
    public final void onCommand(GenericMessageEvent event, CallInfo callInfo, String[] args) {
        if (!(event instanceof MessageEvent)) return; // these commands should only be channel messages
        final MessageEvent me = (MessageEvent) event;
        final Context c = ContextFactory.getGlobal().enterContext();
        c.setClassShutter(new ClassShutter() {
            @Override
            public boolean visibleToScripts(String className) {
                if (className.equals("org.royaldev.royalbot.BotUtils")) return true; // allow BotUtils
                else if (className.equals("org.pircbotx.PircBotX")) return false; // no bot access
                else if (className.startsWith("org.royaldev.royalbot")) return false; // no package access
                return true;
            }
        });
        final Scriptable s = c.initStandardObjects();
        ScriptableObject.putProperty(s, "event", Context.javaToJS(me, s)); // provide message event for ease
        ScriptableObject.putProperty(s, "args", Context.javaToJS(args, s)); // supply arguments
        try {
            c.evaluateString(s, getJavaScript(), getName(), 1, null);
        } catch (Throwable t) {
            if (t instanceof OutOfMemoryError) {
                rb.getLogger().warning("Channel command (\"" + getName() + "\") produced OutOfMemoryError! Removing.");
                rb.getCommandHandler().unregister(getName());
            }
            final String url = BotUtils.linkToStackTrace(t);
            notice(event, "Exception!" + ((url != null) ? " (" + url + ")" : ""));
        } finally {
            Context.exit();
        }
    }

    @Override
    public final CommandType getCommandType() {
        return CommandType.MESSAGE;
    }

    @Override
    public final String getName() {
        return getBaseName() + ":" + getChannel();
    }

    /**
     * Writes the command out in JSON, ready for use with a command maker.
     *
     * @return Command as a JSON string
     */
    @Override
    public final String toString() {
        final Map<String, Object> data = new HashMap<>();
        data.put("name", getBaseName());
        final StringBuilder aliases = new StringBuilder();
        for (String alias : getAliases()) {
            String[] split = alias.split(":#");
            aliases.append(StringUtils.join(split, ":#", 0, split.length - 1)).append(",");
        }
        if (aliases.length() > 0) data.put("aliases", aliases.substring(0, aliases.length() - 1));
        data.put("description", getDescription());
        data.put("usage", getUsage());
        data.put("auth", getAuthLevel().name());
        data.put("script", getJavaScript());
        try {
            return om.writeValueAsString(data);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static class SandboxContextFactory extends ContextFactory {
        @Override
        protected Context makeContext() {
            TimedContext tcx = new TimedContext(this);
            tcx.setOptimizationLevel(-1);
            tcx.setInstructionObserverThreshold(500);
            tcx.setWrapFactory(new SandboxWrapFactory());
            return tcx;
        }

        @Override
        protected void observeInstructionCount(Context cx, int instructionCount) {
            TimedContext tcx = (TimedContext) cx;
            long currentTime = System.currentTimeMillis();
            if (currentTime - tcx.startTime > 7500L)
                throw new Error("Command ran for too long (longer than 7.5 seconds)!");
        }

        @Override
        protected Object doTopCall(org.mozilla.javascript.Callable callable, Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
            TimedContext tcx = (TimedContext) cx;
            tcx.startTime = System.currentTimeMillis();
            return super.doTopCall(callable, cx, scope, thisObj, args);
        }

        private static class TimedContext extends Context {
            long startTime;

            private TimedContext(SandboxContextFactory scf) {
                super(scf);
            }
        }
    }

    private static class SandboxWrapFactory extends WrapFactory {
        @Override
        public Scriptable wrapAsJavaObject(Context cx, Scriptable scope, Object javaObject, Class staticType) {
            return new SandboxNativeJavaObject(scope, javaObject, staticType);
        }
    }

    private static class SandboxNativeJavaObject extends NativeJavaObject {
        public SandboxNativeJavaObject(Scriptable scope, Object javaObject, Class staticType) {
            super(scope, javaObject, staticType);
        }

        @Override
        public Object get(String name, Scriptable start) {
            if (name.equals("getClass")) return NativeJavaObject.NOT_FOUND; // no reflection
            else if (name.equals("getBot")) return NativeJavaObject.NOT_FOUND; // no bot access
            return super.get(name, start);
        }
    }
}

package de.jakomi1.voiceServer.command;

import de.jakomi1.voiceServer.util.DataUtils;
import de.jakomi1.voiceServer.util.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class VoiceServerCommand implements CommandExecutor, TabCompleter {

    public static final List<String> SUBCOMMANDS = List.of("reload", "config");
    public static final List<String> CONFIG_KEYS = List.of(
            "persistent-groups-should-survive-restart",
            "debug"
    );

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            MessageUtils.sendError(sender, "Usage: /vcserver reload | /vcserver config <persistent-groups-should-survive-restart|debug> <true/false>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (args.length != 1) {
                    MessageUtils.sendError(sender, "Usage: /vcserver reload");
                    return true;
                }

                DataUtils.reloadConfig();
                MessageUtils.sendSuccess(sender, "VoiceServer config was successfully reloaded");
            }

            case "config" -> {
                if (args.length != 3) {
                    MessageUtils.sendError(sender, "Usage: /vcserver config <persistent-groups-should-survive-restart|debug> <true/false>");
                    return true;
                }

                String key = args[1].toLowerCase();
                Boolean value = parseBoolean(args[2]);

                if (value == null) {
                    MessageUtils.sendError(sender, "Value must be true or false.");
                    return true;
                }

                switch (key) {
                    case "debug" -> {
                        DataUtils.setDebug(value);
                        MessageUtils.sendSuccess(sender, "debug was set to " + value);
                    }

                    case "persistent-groups-should-survive-restart" -> {
                        DataUtils.setPersistentGroupsShouldSurviveRestart(value);
                        MessageUtils.sendSuccess(sender, "persistent-groups-should-survive-restart was set to " + value);
                    }

                    default -> MessageUtils.sendError(sender, "Unknown config key: " + args[1]);
                }
            }

            default -> MessageUtils.sendError(sender, "Usage: /vcserver reload | /vcserver config <persistent-groups-should-survive-restart|debug> <true/false>");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            SUBCOMMANDS.stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .forEach(suggestions::add);
            return suggestions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("config")) {
            CONFIG_KEYS.stream()
                    .filter(key -> key.startsWith(args[1].toLowerCase()))
                    .forEach(suggestions::add);
            return suggestions;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("config")) {
            if ("true".startsWith(args[2].toLowerCase())) {
                suggestions.add("true");
            }
            if ("false".startsWith(args[2].toLowerCase())) {
                suggestions.add("false");
            }
        }

        return suggestions;
    }

    private static Boolean parseBoolean(String input) {
        if (input == null) return null;
        if (input.equalsIgnoreCase("true")) return true;
        if (input.equalsIgnoreCase("false")) return false;
        return null;
    }
}
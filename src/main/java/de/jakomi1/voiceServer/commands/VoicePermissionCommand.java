package de.jakomi1.voiceServer.commands;

import de.jakomi1.voiceServer.utils.DataUtils;
import de.jakomi1.voiceServer.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VoicePermissionCommand implements CommandExecutor, TabCompleter {
    public static final List<String> SUBCOMMANDS = List.of("give", "remove", "default", "reset");

    public static final List<String> PERMISSIONS = List.of("listen", "speak", "groups");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            MessageUtils.showVoicePermissionCommandUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        switch (subCommand) {
            case "default" -> {
                // /vcpermission default <permission> <true|false>
                if (args.length != 3) {
                    MessageUtils.showVoicePermissionCommandUsage(sender);
                    return true;
                }

                String permission = args[1].toLowerCase(Locale.ROOT);
                String boolStr = args[2].toLowerCase(Locale.ROOT);

                if (!PERMISSIONS.contains(permission)) {
                    MessageUtils.sendError(sender, "Invalid permission category. Valid: listen, speak, groups");
                    return true;
                }

                if (!("true".equals(boolStr) || "false".equals(boolStr))) {
                    MessageUtils.sendError(sender, "Invalid value. Use true or false.");
                    return true;
                }

                boolean value = Boolean.parseBoolean(boolStr);
                boolean changed = DataUtils.setPermissionDefault(permission, value);
                if (changed) {
                    MessageUtils.sendSuccess(sender, "Set default for '" + permission + "' to " + value + ".");
                } else {
                    MessageUtils.sendError(sender, "No change: default for '" + permission + "' was already " + value + ".");
                }
            }

            case "give" -> {
                // /vcpermission give <player> <permission>
                if (args.length != 3) {
                    MessageUtils.showVoicePermissionCommandUsage(sender);
                    return true;
                }

                String playerName = args[1];
                String permission = args[2].toLowerCase(Locale.ROOT);

                if (!PERMISSIONS.contains(permission)) {
                    MessageUtils.sendError(sender, "Invalid permission category. Valid: listen, speak, groups");
                    return true;
                }

                if (DataUtils.isPlayerAllowed(permission, playerName)) {
                    MessageUtils.sendError(sender, "Player '" + playerName + "' already has permission '" + permission + "'.");
                    return true;
                }

                if (DataUtils.addPermissionPlayer(permission, playerName)) {
                    MessageUtils.sendSuccess(sender, "Given permission '" + permission + "' to player '" + playerName + "'.");
                } else {
                    MessageUtils.sendError(sender, "Failed to give permission '" + permission + "' to player '" + playerName + "'.");
                }
            }

            case "remove" -> {
                // /vcpermission remove <player> <permission>
                if (args.length != 3) {
                    MessageUtils.showVoicePermissionCommandUsage(sender);
                    return true;
                }

                String playerName = args[1];
                String permission = args[2].toLowerCase(Locale.ROOT);

                if (!PERMISSIONS.contains(permission)) {
                    MessageUtils.sendError(sender, "Invalid permission category. Valid: listen, speak, groups");
                    return true;
                }

                if (!DataUtils.isPlayerAllowed(permission, playerName)) {
                    MessageUtils.sendError(sender, "Player '" + playerName + "' does not have permission '" + permission + "'.");
                    return true;
                }

                if (DataUtils.removePermissionPlayer(permission, playerName)) {
                    MessageUtils.sendSuccess(sender, "Removed permission '" + permission + "' from player '" + playerName + "'.");
                } else {
                    MessageUtils.sendError(sender, "Failed to remove permission '" + permission + "' from player '" + playerName + "'.");
                }
            }
            case "reset" -> {
                // /vcpermission reset            -> reset ALL (wie vorher)
                // /vcpermission reset <player>   -> reset only that player (remove explicit allow/disallow)
                if (args.length == 1) {
                    DataUtils.resetAllPermissions();
                    MessageUtils.sendSuccess(sender, "All voice permissions were reset to defaults.");
                    return true;
                } else if (args.length == 2) {
                    String playerName = args[1];
                    if (playerName == null || playerName.isBlank()) {
                        MessageUtils.sendError(sender, "Invalid player name.");
                        return true;
                    }

                    boolean changed = DataUtils.resetPlayerPermissions(playerName);
                    if (changed) {
                        MessageUtils.sendSuccess(sender, "Permissions for player '" + playerName + "' were reset (removed explicit entries).");
                    } else {
                        MessageUtils.sendError(sender, "No permission entries found for player '" + playerName + "'.");
                    }
                    return true;
                } else {
                    MessageUtils.showVoicePermissionCommandUsage(sender);
                    return true;
                }
            }

            default -> MessageUtils.showVoicePermissionCommandUsage(sender);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            SUBCOMMANDS.stream()
                    .filter(sc -> sc.startsWith(partial))
                    .forEach(suggestions::add);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String partial = args[1].toLowerCase(Locale.ROOT);

            if (sub.equals("default")) {
                // suggest permission categories
                PERMISSIONS.stream()
                        .filter(p -> p.startsWith(partial))
                        .forEach(suggestions::add);
            } else {
                // suggest online players
                Bukkit.getOnlinePlayers().forEach(p -> {
                    String name = p.getName();
                    if (name.toLowerCase(Locale.ROOT).startsWith(partial)) suggestions.add(name);
                });
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String partial = args[2].toLowerCase(Locale.ROOT);

            if (sub.equals("default")) {
                // Only suggest the opposite of the current default value
                String permission = args[1].toLowerCase(Locale.ROOT);

                if (PERMISSIONS.contains(permission)) {
                    boolean current = DataUtils.getPermissionDefault(permission);
                    String opposite = current ? "false" : "true";

                    if (opposite.startsWith(partial)) {
                        suggestions.add(opposite);
                    }
                }
            } else {
                // suggest relevant permission categories for give/remove
                String playerName = args[1];
                for (String cat : PERMISSIONS) {
                    boolean allowed = DataUtils.isPlayerAllowed(cat, playerName);
                    boolean shouldSuggest = false;
                    if (sub.equals("give")) shouldSuggest = !allowed;
                    if (sub.equals("remove")) shouldSuggest = allowed;
                    if (shouldSuggest && cat.startsWith(partial)) suggestions.add(cat);
                }
            }
        }

        return suggestions;
    }
}

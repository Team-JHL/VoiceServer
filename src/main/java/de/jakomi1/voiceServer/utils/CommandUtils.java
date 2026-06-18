package de.jakomi1.voiceServer.utils;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;


import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.jakomi1.voiceServer.VoiceServer.plugin;


public class CommandUtils {

    private static CommandMap getCommandMap() {
        try {
            Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            return (CommandMap) f.get(Bukkit.getServer());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Komfort-Überladung ohne Permission
    public static void registerDynamicCommand(String name,
                                              CommandExecutor executor,
                                              TabCompleter tabCompleter,
                                              String permission) {
        registerDynamicCommand(name, executor, tabCompleter, List.of(),permission);
    }

    public static void registerDynamicCommand(String name,
                                              CommandExecutor executor,
                                              TabCompleter tabCompleter) {
        registerDynamicCommand(name, executor, tabCompleter, List.of(), null);
    }


    public static void registerDynamicCommand(String name,
                                              CommandExecutor executor,
                                              TabCompleter tabCompleter,
                                              List<String> aliases,
                                              String permission) {

        CommandMap commandMap = getCommandMap();
        if (commandMap == null) {
            plugin.getLogger().warning("CommandMap konnte nicht gefunden werden!");
            return;
        }

        if (aliases == null) aliases = Collections.emptyList();
        if (tabCompleter == null) tabCompleter = (sender, command, alias, args) -> Collections.emptyList();

        final TabCompleter finalTabCompleter = tabCompleter;
        final String perm = plugin+permission;

        Command dynamicCommand = new Command(name, name + "-command", "", aliases) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!perm.isEmpty() && !sender.hasPermission(perm)) {
                    return true;
                }
                return executor.onCommand(sender, this, label, args);
            }

            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                List<String> result = finalTabCompleter.onTabComplete(sender, this, alias, args);
                return result != null ? result : Collections.emptyList();
            }
        };
        if(!perm.equals(plugin.toString())) {
            dynamicCommand.setPermission(perm);
        }

        commandMap.register(plugin.getName(), dynamicCommand);
        //plugin.getLogger().info("Dynamischer Command /" + name + " registriert!" + (!aliases.isEmpty() ? " Aliase: " + String.join(", ", aliases) : ""));
    }



    public static void registerDynamicPermission(String name, PermissionDefault defaultValue) {

        String full = "voiceserver." + name;

        if (Bukkit.getPluginManager().getPermission(full) != null) return;

        Permission permission = new Permission(
                full,
                defaultValue
        );

        Bukkit.getPluginManager().addPermission(permission);
    }
}

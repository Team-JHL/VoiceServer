package de.jakomi1.voiceServer.commands;

import de.jakomi1.voiceServer.utils.MessageUtils;
import de.jakomi1.voiceServer.utils.TextUtils;
import de.maxhenkel.voicechat.api.Group;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


import static de.jakomi1.voiceServer.VoiceServer.serverApi;
import static de.jakomi1.voiceServer.utils.GroupUtils.*;
import static de.jakomi1.voiceServer.utils.TextUtils.mergeQuoted;

public class VoiceGroupCommand implements CommandExecutor, TabCompleter {
    public static final List<String> SUBCOMMANDS = List.of("list", "create", "join", "kick", "remove","info");
    public static final List<String> GROUP_TYPES = List.of("normal", "open", "isolated");

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            MessageUtils.showVoiceGroupCommandUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "list"   -> listGroups(sender);
            case "create" -> createGroup(sender, args);
            case "join"   -> joinPlayers(sender, args);
            case "kick"   -> kickPlayers(sender, args);
            case "remove" -> removeGroup(sender, args);
            case "info"   -> commandInfo(sender, args);
            default       -> MessageUtils.showVoiceGroupCommandUsage(sender);
        }
        return true;
    }



    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> merged = mergeQuoted(args);
        List<String> suggestions = new ArrayList<>();
        if (merged.isEmpty()) return suggestions;
        String sub = merged.get(0).toLowerCase();
        // Vorschläge für Gruppen immer in ""!
        List<String> quotedGroups = serverApi.getGroups().stream()
                .map(Group::getName)
                .map(TextUtils::quote)
                .toList();

        switch (merged.size()) {
            case 1 -> SUBCOMMANDS.stream()
                    .filter(sc -> sc.startsWith(sub)).forEach(suggestions::add);
            case 2 -> {
                if (List.of("create", "join", "kick").contains(sub)) {
                    suggestions.addAll(List.of("@a", "@s", "@r", "@p"));
                    Bukkit.getOnlinePlayers().forEach(p -> suggestions.add(p.getName()));
                    suggestions.removeIf(n -> !n.toLowerCase().startsWith(merged.get(1).toLowerCase()));
                } else if (sub.equals("remove") || sub.equals("info")) {
                    suggestions.addAll(quotedGroups);
                }
            }
            case 3 -> {
                if (sub.equals("create")) {
                    for (String type : GROUP_TYPES) {
                        if (type.startsWith(merged.get(2).toLowerCase())) suggestions.add(type);
                    }
                } else if (sub.equals("join")) {
                    suggestions.addAll(quotedGroups);
                }
            }
            case 4 -> {
                if (sub.equals("create")) {
                    suggestions.add("\"<group name>\"");
                }
            }
            case 5 -> {
                if (sub.equals("create")) {
                    String groupNameArg = merged.get(3);
                    if (groupNameArg.startsWith("\"") && groupNameArg.endsWith("\"")) {
                        for (String ptype : PERSISTENCE_TYPES) {
                            if (ptype.startsWith(merged.get(4).toLowerCase())) suggestions.add(ptype);
                        }
                    }
                }
            }
            case 6 -> {
                if (sub.equals("create")) {
                    String persistArg = merged.get(4).toLowerCase();
                    if (persistArg.equals("persistent") || persistArg.equals("not-persistent")) {
                        suggestions.add("\"<password>\"");
                    }
                }
            }
            default -> { }
        }
        return suggestions;
    }

}

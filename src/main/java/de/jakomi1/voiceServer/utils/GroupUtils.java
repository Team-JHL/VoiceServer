package de.jakomi1.voiceServer.utils;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

import static de.jakomi1.voiceServer.VoiceServer.plugin;
import static de.jakomi1.voiceServer.VoiceServer.serverApi;
import static de.jakomi1.voiceServer.utils.DataUtils.removePersistentGroupByName;
import static de.jakomi1.voiceServer.utils.MessageUtils.*;
import static de.jakomi1.voiceServer.utils.TextUtils.*;

public class GroupUtils {
    public static final Map<UUID, String> groupPasswords = new HashMap<>();
    public static final Map<UUID, Group> playerGroupMap = new HashMap<>();

    public static final List<String> PERSISTENCE_TYPES = List.of("persistent", "not-persistent");

    public static void joinPlayers(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendError(sender, "Usage: /vcgroup join <player|@a|@s> \"<group name>\"");
            return;
        }
        List<Player> targets = parseTargets(sender, args[1]);
        String groupName = getQuotedArg(args, 2);
        if (groupName == null) {
            sendError(sender, "Group name must be in quotes!");
            return;
        }
        Group newGroup = findGroup(groupName);
        if (newGroup == null) {
            sendError(sender, "Group not found.");
            return;
        }

        for (Player p : targets) {
            VoicechatConnection conn = serverApi.getConnectionOf(p.getUniqueId());
            if (conn == null) {
                sendError(sender, "Player not connected to voice chat: " + p.getName());
                continue;
            }
            Group oldGroup = conn.getGroup();
            conn.setGroup(newGroup);

            if (oldGroup != null && !oldGroup.getId().equals(newGroup.getId())) {
                long remaining = Bukkit.getOnlinePlayers().stream()
                        .map(pm -> serverApi.getConnectionOf(pm.getUniqueId()))
                        .filter(Objects::nonNull)
                        .map(VoicechatConnection::getGroup)
                        .filter(oldGroup::equals)
                        .count();

                if (remaining == 0 && !oldGroup.isPersistent()) {
                    serverApi.removeGroup(oldGroup.getId());
                    groupPasswords.remove(oldGroup.getId());
                }
            }
        }
    }

    /**
     * Erstellt eine Gruppe ohne Spieler (für persistente Gruppen beim Reload)
     */
    public static void createGroupWithoutPlayers(Group group, String password) {
        if (group == null) return;

        // Nur erstellen, keine Spieler setzen
        Group built = serverApi.groupBuilder()
                .setName(group.getName())
                .setType(group.getType())
                .setPersistent(true)
                .setPassword(group.hasPassword() ? password : null)
                .build();

        if (group.hasPassword()) {
            groupPasswords.put(built.getId(), password);
        }

        System.out.println("Persistent group created without players: " + built.getName());
    }


    public static void kickPlayers(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendError(sender, "Usage: /vcgroup kick <player|@a|@s|@r|@p>");
            return;
        }
        List<Player> targets = parseTargets(sender, args[1]);
        if (targets.isEmpty()) {
            sendError(sender, "No valid players specified.");
            return;
        }
        for (Player p : targets) {
            var conn = serverApi.getConnectionOf(p.getUniqueId());
            if (conn != null && conn.getGroup() != null) {
                Group group = conn.getGroup();
                conn.setGroup(null);

                sendSuccess(sender, "Kicked player: " + p.getName() + " from group: " + group.getName());
                long remaining = Bukkit.getOnlinePlayers().stream()
                        .map(pm -> serverApi.getConnectionOf(pm.getUniqueId()))
                        .filter(Objects::nonNull)
                        .map(VoicechatConnection::getGroup)
                        .filter(group::equals)
                        .count();
                if (remaining == 0 && !group.isPersistent()) {
                    serverApi.removeGroup(group.getId());
                    groupPasswords.remove(group.getId());
                    sendSuccess(sender, "Old group removed (was empty): " + group.getName());
                }
            }
        }
    }

    public static void removeGroup(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendError(sender, "Usage: /vcgroup remove \"<group name>\"");
            return;
        }
        String groupName = getQuotedArg(args, 1);
        if (groupName == null) {
            sendError(sender, "Group name must be in quotes!");
            return;
        }
        Group group = findGroup(groupName);
        if (group == null) {
            sendError(sender, "Group not found.");
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            var conn = serverApi.getConnectionOf(p.getUniqueId());
            if (conn != null && conn.getGroup() != null
                    && conn.getGroup().getId().equals(group.getId())) {
                conn.setGroup(null);
            }
        }

        serverApi.removeGroup(group.getId());
        groupPasswords.remove(group.getId());
        if(group.isPersistent()) {
            removePersistentGroupByName(group.getName());
        }
        sendSuccess(sender, "Group removed: " + group.getName());
    }

    public static Group findGroup(String name) {
        return serverApi.getGroups().stream()
                .filter(g -> g.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public static void listGroups(CommandSender sender) {
        List<Group> groups = serverApi.getGroups().stream().toList();
        if (groups.isEmpty()) {
            sendSuccess(sender, "No voice chat groups available.");
            return;
        }
        sendSuccess(sender, "Voice chat groups:");
        for (Group g : groups) {
            boolean isLocked = groupPasswords.containsKey(g.getId());
            String displayName = quote(g.getName());
            String msg = ">> " + ChatColor.AQUA + g.getName() +
                    (isLocked ? ChatColor.RED + " [locked]" : "") +
                    ChatColor.GRAY + " (" + typeToString(g.getType()) + ")";
            sender.sendMessage(getChatPrefix() + msg);
        }
    }

    public static void commandInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendError(sender, "Usage: /vcgroup info \"<group name>\"");
            return;
        }

        String groupName = getQuotedArg(args, 1);
        if (groupName == null) {
            sendError(sender, "Group name must be in quotes!");
            return;
        }

        Group group = findGroup(groupName);
        if (group == null) {
            sendError(sender, "Group not found: " + groupName);
            return;
        }

        boolean hasPw = group.hasPassword() || groupPasswords.containsKey(group.getId());
        String typeStr = typeToString(group.getType());
        String persistence = group.isPersistent() ? "Yes" : "No";

        List<String> members = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            VoicechatConnection conn = serverApi.getConnectionOf(p.getUniqueId());
            if (conn != null && conn.getGroup() != null && conn.getGroup().getId().equals(group.getId())) {
                members.add(p.getName());
            }
        }

        sendPlain(sender, "Group Info for " + group.getName());
        sendPlain(sender, "Type: " + typeStr);
        sendPlain(sender, "Persistent: " + persistence);
        sendPlain(sender, "Locked: " + (hasPw ? "Yes" : "No"));
        sendPlain(sender, "Members (" + members.size() + "): " + (members.isEmpty() ? "None" : String.join(", ", members)));
    }

    public static void createGroup(CommandSender sender, String[] args) {
        int argLen = args.length;
        if (argLen < 4) {
            sendError(sender, "Usage: /vcgroup create <player|@a|@s|@null> <type> \"<group name>\" [persistent] [\"password\"]");
            return;
        }

        List<Player> targets;
        if (args[1].equalsIgnoreCase("@null")) {
            targets = new ArrayList<>(); // keine Spieler
        } else {
            targets = parseTargets(sender, args[1]);
            if (targets.isEmpty()) {
                sendError(sender, "No valid players specified.");
                return;
            }
        }

        String typeArg = args[2].toLowerCase(Locale.ROOT);
        Group.Type type = typeFromString(typeArg);

        int nameIdx = 3;
        String groupName = getQuotedArg(args, nameIdx);
        if (groupName == null) {
            sendError(sender, "Group name must be in quotes!");
            return;
        }
        int nextArgIdx = nameIdx + countQuotedArgs(args, nameIdx);

        boolean persistent = false;
        String password = null;

        if (argLen > nextArgIdx) {
            String arg4 = args[nextArgIdx];
            if (arg4.equalsIgnoreCase("persistent")) {
                persistent = true;
                if (argLen > nextArgIdx + 1) {
                    password = getQuotedArg(args, nextArgIdx + 1);
                }
            } else if (arg4.equalsIgnoreCase("not-persistent")) {
                persistent = false;
                if (argLen > nextArgIdx + 1) {
                    password = getQuotedArg(args, nextArgIdx + 1);
                }
            } else {
                password = getQuotedArg(args, nextArgIdx);
            }
        }

        if (findGroup(groupName) != null) {
            sendError(sender, "Group already exists: " + groupName);
            return;
        }

        Group group;
        if (password != null && !password.isEmpty()) {
            group = serverApi.groupBuilder()
                    .setName(groupName)
                    .setPersistent(persistent)
                    .setPassword(password)
                    .setType(type)
                    .build();
            groupPasswords.put(group.getId(), password);
        } else {
            group = serverApi.groupBuilder()
                    .setName(groupName)
                    .setPersistent(persistent)
                    .setType(type)
                    .build();
        }

        // Serialisieren nur bei persistenten Gruppen
        if (persistent) {
            DataUtils.savePersistentGroup(serializePersistentGroup(group, password));
        }
        /*
        // Spieler in die Gruppe setzen, nur wenn nicht @null
        for (Player p : targets) {
            VoicechatConnection conn = serverApi.getConnectionOf(p.getUniqueId());
            if (conn == null) {
                sendError(sender, "Player not connected to voice chat: " + p.getName());
                continue;
            }
            conn.setConnected(false);

            Group oldGroup = conn.getGroup();
            conn.setGroup(group);
            sendSuccess(sender, "Moved player: " + p.getName() + " to group: " + group.getName());

            if (oldGroup != null && !oldGroup.getId().equals(group.getId())) {
                long remaining = Bukkit.getOnlinePlayers().stream()
                        .map(pm -> serverApi.getConnectionOf(pm.getUniqueId()))
                        .filter(Objects::nonNull)
                        .map(VoicechatConnection::getGroup)
                        .filter(oldGroup::equals)
                        .count();

                if (remaining == 0 && !oldGroup.isPersistent()) {
                    serverApi.removeGroup(oldGroup.getId());
                    groupPasswords.remove(oldGroup.getId());
                    sendSuccess(sender, "Old group removed (was empty): " + oldGroup.getName());
                }
            }
        }*/
    }


    public static String serializePersistentGroup(Group group, String password) {
        if (group == null || !group.isPersistent()) return null;

        StringBuilder sb = new StringBuilder();
        sb.append(group.getName())
                .append("+")
                .append(typeToString(group.getType()));

        // Passwort immer mitgeben, auch wenn null
        sb.append("+[").append(password).append("]");

        return sb.toString();
    }


    /**
     * Erstellt eine persistente Gruppe ohne Spieler direkt aus einem String.
     * Intern wird einfach der createGroup-Command mit @null als Spieler aufgerufen.
     * Format: <name>+<type>+[password] (password kann null sein)
     */
    public static void createGroupWithoutPlayersFromString(String str) {
        if (str == null || str.isBlank()) return;

        try {
            String[] parts = str.split("\\+");
            if (parts.length < 2) return;

            String name = parts[0];
            String typeStr = parts[1];
            Group.Type type = typeFromString(typeStr);

            String password = null;
            if (parts.length > 2) {
                String pwPart = parts[2];
                if (pwPart.startsWith("[") && pwPart.endsWith("]")) {
                    password = pwPart.substring(1, pwPart.length() - 1);
                    if ("null".equals(password)) password = null;
                }
            }

            // Intern die createGroup-Methode nutzen, @null als Spieler, persistent = true
            String[] commandArgs;
            if (password != null) {
                commandArgs = new String[]{"create", "@null", typeStr, "\"" + name + "\"", "persistent", "\"" + password + "\""};
            } else {
                commandArgs = new String[]{"create", "@null", typeStr, "\"" + name + "\"", "persistent"};
            }

            // CommandSender kann null sein, weil es intern ist
            createGroup(Bukkit.getConsoleSender(), commandArgs);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



}

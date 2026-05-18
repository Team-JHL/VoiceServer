package de.jakomi1.voiceServer.utils;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static de.jakomi1.voiceServer.VoiceServer.serverApi;
import static de.jakomi1.voiceServer.utils.DataUtils.clearDefaultGroupIfMatches;
import static de.jakomi1.voiceServer.utils.DataUtils.removePersistentGroupByName;
import static de.jakomi1.voiceServer.utils.MessageUtils.*;
import static de.jakomi1.voiceServer.utils.TextUtils.*;

public class GroupUtils {
    public static final Map<UUID, String> groupPasswords = new HashMap<>();
    public static final Map<UUID, Group> playerGroupMap = new HashMap<>();

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
            moveConnectionToGroup(conn, newGroup);
        }
    }

    public static void joinDefaultGroup(Player player) {
        if (player == null || serverApi == null) return;

        String defaultGroupName = DataUtils.getDefaultGroupName();
        if (defaultGroupName == null || defaultGroupName.isBlank()) return;

        Group defaultGroup = findGroup(defaultGroupName);
        if (defaultGroup == null) return;

        VoicechatConnection conn = serverApi.getConnectionOf(player.getUniqueId());
        if (conn == null) return;

        moveConnectionToGroup(conn, defaultGroup);
    }

    public static void applyDefaultGroupToOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            joinDefaultGroup(player);
        }
    }

    public static void setDefaultGroup(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendError(sender, "Usage: /vcgroup default \"<group name>\"");
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

        if (args.length > 2) {
            sendError(sender, "Usage: /vcgroup default \"<group name>\"");
            return;
        }

        DataUtils.setDefaultGroup(group.getName(), group.isPersistent());
        sendSuccess(sender, "Default group saved: " + group.getName());
    }

    private static void moveConnectionToGroup(VoicechatConnection conn, Group newGroup) {
        Group oldGroup = conn.getGroup();
        if (oldGroup != null && oldGroup.getId().equals(newGroup.getId())) return;

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
        clearDefaultGroupIfMatches(group.getName());
        if (group.isPersistent()) {
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
            sendError(sender, "Usage: /vcgroup create <player|@a|@s|@null> <type> \"<group name>\" [not-persistent] [\"password\"]");
            return;
        }

        List<Player> targets;
        if (args[1].equalsIgnoreCase("@null")) {
            targets = new ArrayList<>();
        } else {
            targets = parseTargets(sender, args[1]);
            if (targets.isEmpty()) {
                sendError(sender, "No valid players specified.");
                return;
            }
        }

        String typeArg = args[2].toLowerCase(Locale.ROOT);
        Group.Type type = typeFromString(typeArg);
        if (type == null) {
            sendError(sender, "Unknown group type: " + args[2]);
            return;
        }

        int nameIdx = 3;
        String groupName = getQuotedArg(args, nameIdx);
        if (groupName == null) {
            sendError(sender, "Group name must be in quotes!");
            return;
        }

        int nextArgIdx = nameIdx + countQuotedArgs(args, nameIdx);
        boolean persistent = true;
        String password = null;

        if (argLen > nextArgIdx) {
            String mode = args[nextArgIdx].toLowerCase(Locale.ROOT);

            if (mode.equals("not-persistent")) {
                persistent = false;
                nextArgIdx++;
            } else if (mode.equals("persistent")) {
                persistent = true;
                nextArgIdx++;
            }
        }

        if (argLen > nextArgIdx) {
            password = getQuotedArg(args, nextArgIdx);
            if (password == null) {
                sendError(sender, "Password must be in quotes!");
                return;
            }
            if (argLen > nextArgIdx + countQuotedArgs(args, nextArgIdx)) {
                sendError(sender, "Too many arguments.");
                return;
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

        if (persistent) {
            DataUtils.savePersistentGroup(serializePersistentGroup(group, password));
        }

        for (Player p : targets) {
            var conn = serverApi.getConnectionOf(p.getUniqueId());
            if (conn != null) {
                moveConnectionToGroup(conn, group);
            }
        }

        sendSuccess(sender, "Group created: " + group.getName() + (persistent ? " [persistent]" : " [not-persistent]"));
    }

    public static String serializePersistentGroup(Group group, String password) {
        if (group == null || !group.isPersistent()) return null;

        return group.getName() +
                "+" +
                typeToString(group.getType()) +
                "+[" + password + "]";
    }

    public static void createGroupWithoutPlayersFromString(String str) {
        if (str == null || str.isBlank()) return;

        try {
            String[] parts = str.split("\\+");
            if (parts.length < 2) return;

            String name = parts[0];
            String typeStr = parts[1];
            Group.Type type = typeFromString(typeStr);
            if (type == null) return;

            String password = null;
            if (parts.length > 2) {
                String pwPart = parts[2];
                if (pwPart.startsWith("[") && pwPart.endsWith("]")) {
                    password = pwPart.substring(1, pwPart.length() - 1);
                    if ("null".equals(password)) password = null;
                }
            }

            String[] commandArgs;
            if (password != null) {
                commandArgs = new String[]{"create", "@null", typeStr, "\"" + name + "\"", "persistent", "\"" + password + "\""};
            } else {
                commandArgs = new String[]{"create", "@null", typeStr, "\"" + name + "\"", "persistent"};
            }

            createGroup(Bukkit.getConsoleSender(), commandArgs);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
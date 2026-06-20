package de.jakomi1.voiceServer.utils;

import de.maxhenkel.voicechat.api.Group;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class TextUtils {
    public static List<String> mergeQuoted(String[] args) {
        List<String> merged = new ArrayList<>();
        StringBuilder current = null;
        for (String arg : args) {
            if (current == null) {
                if (arg.startsWith("\"") && !arg.endsWith("\"")) {
                    current = new StringBuilder(arg);
                } else {
                    merged.add(arg);
                }
            } else {
                current.append(" ").append(arg);
                if (arg.endsWith("\"")) {
                    merged.add(current.toString());
                    current = null;
                }
            }
        }
        if (current != null) {
            merged.add(current.toString());
        }
        return merged;
    }

    public static String quote(String groupName) {
        return "\"" + groupName + "\"";
    }


    public static Group.Type typeFromString(String type) {
        if (type == null) return Group.Type.NORMAL;
        return switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "open" -> Group.Type.OPEN;
            case "isolated" -> Group.Type.ISOLATED;
            default -> Group.Type.NORMAL;
        };
    }

    public static String typeToString(Group.Type type) {
        if (type == Group.Type.OPEN) return "open";
        if (type == Group.Type.ISOLATED) return "isolated";
        return "normal";
    }

    public static List<Player> parseTargets(CommandSender sender, String token) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        return switch (token) {
            case "@a" -> online;
            case "@s" -> sender instanceof Player p ? List.of(p) : List.of();
            case "@r" -> online.isEmpty() ? List.of() : List.of(online.get(new Random().nextInt(online.size())));
            case "@p" -> {
                if (!(sender instanceof Player ps)) yield List.of();
                Player closest = null;
                double minDist = Double.MAX_VALUE;
                for (Player pl : online) {
                    if (!pl.equals(ps)) {
                        double dist = ps.getLocation().distance(pl.getLocation());
                        if (dist < minDist) {
                            minDist = dist;
                            closest = pl;
                        }
                    }
                }
                yield closest != null ? List.of(closest) : List.of();
            }
            default -> {
                Player p = Bukkit.getPlayerExact(token);
                yield p == null ? List.of() : List.of(p);
            }
        };
    }

    public static String getQuotedArg(String[] args, int startIdx) {
        if (startIdx >= args.length) return null;
        StringBuilder sb = new StringBuilder();
        boolean started = false, ended = false;
        for (int i = startIdx; i < args.length; i++) {
            String arg = args[i];
            if (!started && arg.startsWith("\"")) {
                sb.append(arg.substring(1));
                started = true;
                if (arg.endsWith("\"") && arg.length() > 1) {
                    sb.setLength(sb.length() - 1);
                    ended = true;
                    break;
                }
            } else if (started) {
                sb.append(" ");
                if (arg.endsWith("\"")) {
                    sb.append(arg, 0, arg.length() - 1);
                    ended = true;
                    break;
                } else {
                    sb.append(arg);
                }
            }
        }
        return (started && ended) ? sb.toString() : null;
    }
    public static int countQuotedArgs(String[] args, int startIdx) {
        if (startIdx >= args.length) return 0;
        int count = 0;
        boolean started = false, ended = false;
        for (int i = startIdx; i < args.length; i++) {
            String arg = args[i];
            count++;
            if (!started && arg.startsWith("\"")) {
                started = true;
                if (arg.endsWith("\"") && arg.length() > 1) {
                    ended = true;
                    break;
                }
            } else if (started && arg.endsWith("\"")) {
                ended = true;
                break;
            }
        }
        return (started && ended) ? count : 1;
    }
    public static String getChatPrefix() {
        return DataUtils.getChatPrefix();
    }
}

package de.jakomi1.voiceServer.commands;

import de.jakomi1.voiceServer.VoiceServer;
import de.jakomi1.voiceServer.utils.MessageUtils;
import de.jakomi1.voiceServer.utils.SoundboardUtils;
import de.jakomi1.voiceServer.utils.TextUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static de.jakomi1.voiceServer.utils.TextUtils.mergeQuoted;

public class VoiceSoundboardCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> m = mergeQuoted(args);

        if (m.isEmpty()) {
            MessageUtils.sendError(sender, "/vcsoundboard play|stop ...");
            return true;
        }

        String sub = m.get(0).toLowerCase();

        if (sub.equals("play")) {
            if (m.size() < 4) {
                MessageUtils.sendError(sender, "/vcsoundboard play <target|@a|@s|@p|name> <recordings|sounds> \"file\" [volume]");
                return true;
            }

            int vol = 100;
            if (m.size() >= 5) {
                try {
                    vol = Integer.parseInt(m.get(4).replace("%", ""));
                } catch (Exception ignored) {
                    vol = 100;
                }
            }

            SoundboardUtils.play(sender, m.get(1), m.get(2), m.get(3), vol);
            return true;
        }

        if (sub.equals("stop")) {
            if (m.size() < 2) {
                MessageUtils.sendError(sender, "/vcsoundboard stop <target|@a|@s|@p|name> [sound]");
                return true;
            }

            String target = m.get(1);

            if (m.size() == 2) {
                SoundboardUtils.stop(sender, target, null);
                MessageUtils.sendSuccess(sender, "Stopped all sounds for target: " + target);
                return true;
            }

            String soundName = m.get(2);
            SoundboardUtils.stop(sender, target, soundName);
            MessageUtils.sendSuccess(sender, "Stopped: " + soundName + " for target: " + target);
            return true;
        }

        MessageUtils.sendError(sender, "/vcsoundboard play|stop ...");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> m = mergeQuoted(args);

        if (m.isEmpty()) return List.of("play", "stop");
        if (m.size() == 1) return List.of("play", "stop");

        if (m.get(0).equalsIgnoreCase("play")) {
            if (m.size() == 2) {
                List<String> out = new ArrayList<>(List.of("@a", "@s", "@p"));
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
                return filterByPrefix(out, m.get(1));
            }

            if (m.size() == 3) {
                return filterByPrefix(List.of("recordings", "sounds"), m.get(2));
            }

            if (m.size() == 4) {
                File f = new File(VoiceServer.plugin.getDataFolder(), m.get(2));
                if (!f.exists() || !f.isDirectory()) return List.of();
                return listPlayableBaseNames(f, m.get(3));
            }

            if (m.size() == 5) {
                return filterByPrefix(List.of("50", "75", "100", "125", "150"), m.get(4));
            }
        }

        if (m.get(0).equalsIgnoreCase("stop")) {
            if (m.size() == 2) {
                List<String> out = new ArrayList<>(List.of("@a", "@s", "@p"));
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
                return filterByPrefix(out, m.get(1));
            }

            if (m.size() == 3) {
                return filterByPrefix(SoundboardUtils.getActiveSoundNames(sender, m.get(1)), m.get(2));
            }
        }

        return List.of();
    }

    private static List<String> listPlayableBaseNames(File folder, String prefix) {
        Set<String> out = new LinkedHashSet<>();
        File[] files = folder.listFiles();
        if (files == null) return List.of();

        for (File file : files) {
            if (!file.isFile()) continue;

            String name = file.getName();
            String lower = name.toLowerCase();

            if (!lower.endsWith(".wav") && !lower.endsWith(".opus")) continue;

            String base = stripExtension(name);
            if (!prefix.isBlank() && !base.toLowerCase().startsWith(prefix.toLowerCase())) continue;

            out.add(TextUtils.quote(base));
        }

        return new ArrayList<>(out);
    }

    private static List<String> filterByPrefix(List<String> input, String prefix) {
        if (prefix == null || prefix.isBlank()) return input;

        List<String> out = new ArrayList<>();
        String p = prefix.toLowerCase();

        for (String s : input) {
            if (s == null) continue;
            if (s.toLowerCase().startsWith(p)) out.add(s);
        }

        return out;
    }

    private static String stripExtension(String name) {
        if (name == null) return "";
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }
}
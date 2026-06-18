package de.jakomi1.voiceServer.commands;

import de.jakomi1.voiceServer.utils.MessageUtils;
import de.jakomi1.voiceServer.utils.TextUtils;
import de.jakomi1.voiceServer.utils.StreamUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static de.jakomi1.voiceServer.utils.TextUtils.mergeQuoted;

public class VoiceStreamCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        List<String> m = mergeQuoted(args);

        if (m.isEmpty()) {
            MessageUtils.sendError(sender, "/vcstream start <from player> <to player> | /vcstream stop \"player1-player2\"");
            return true;
        }

        String sub = m.get(0).toLowerCase();

        if (sub.equals("start")) {
            if (m.size() < 3) {
                MessageUtils.sendError(sender, "/vcstream start <from player> <to player>");
                return true;
            }

            Player from = Bukkit.getPlayerExact(m.get(1));
            Player to = Bukkit.getPlayerExact(m.get(2));

            if (from == null) {
                MessageUtils.sendError(sender, "Spieler nicht gefunden: " + m.get(1));
                return true;
            }

            if (to == null) {
                MessageUtils.sendError(sender, "Spieler nicht gefunden: " + m.get(2));
                return true;
            }

            StreamUtils.startStream(from, to, sender);
            return true;
        }

        if (sub.equals("stop")) {
            if (m.size() < 2) {
                MessageUtils.sendError(sender, "/vcstream stop \"player1-player2\"");
                return true;
            }

            StreamUtils.stopStream(m.get(1));
            MessageUtils.sendSuccess(sender, "Stop-Befehl ausgeführt: " + m.get(1));
            return true;
        }

        MessageUtils.sendError(sender, "/vcstream start <from player> <to player> | /vcstream stop \"player1-player2\"");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        List<String> m = mergeQuoted(args);

        if (m.isEmpty()) {
            return List.of("start", "stop");
        }

        if (m.size() == 1) {
            return List.of("start", "stop");
        }

        if (m.get(0).equalsIgnoreCase("start")) {
            if (m.size() == 2 || m.size() == 3) {
                List<String> out = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    out.add(p.getName());
                }
                return out;
            }
        }

        if (m.get(0).equalsIgnoreCase("stop")) {
            List<String> out = new ArrayList<>();
            for (String key : StreamUtils.getActiveKeys()) {
                out.add(TextUtils.quote(key));
            }
            return out;
        }

        return List.of();
    }
}
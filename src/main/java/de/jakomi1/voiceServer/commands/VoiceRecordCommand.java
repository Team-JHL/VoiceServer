package de.jakomi1.voiceServer.commands;

import de.jakomi1.voiceServer.utils.MessageUtils;
import de.jakomi1.voiceServer.utils.RecorderUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VoiceRecordCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length < 1) {
            MessageUtils.sendError(sender, "Usage: /vcrecord <player> [seconds]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);

        if (target == null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().equalsIgnoreCase(args[0])) {
                    target = p;
                    break;
                }
            }
        }

        if (target == null) {
            MessageUtils.sendError(sender, "Player not found.");
            return true;
        }

        int seconds = 10;

        if (args.length >= 2) {
            try {
                seconds = Integer.parseInt(args[1]);
            } catch (Exception e) {
                MessageUtils.sendError(sender, "Invalid seconds.");
                return true;
            }
        }

        if (seconds <= 0) {
            MessageUtils.sendError(sender, "Seconds must be > 0.");
            return true;
        }

        RecorderUtils.startRecording(target, seconds, sender);
        MessageUtils.sendSuccess(sender,
                "Recording " + target.getName() + " for " + seconds + "s");

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    suggestions.add(p.getName());
                }
            }
        }

        if (args.length == 2) {
            for (String s : List.of("10", "15", "30", "60")) {
                suggestions.add(s);
            }
        }

        return suggestions;
    }
}
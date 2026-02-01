package de.jakomi1.voiceServer.commands;

import de.jakomi1.voiceServer.utils.DataUtils;
import de.jakomi1.voiceServer.utils.MessageUtils;
import de.jakomi1.voiceServer.utils.TextUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static de.jakomi1.voiceServer.VoiceServer.plugin;
import static de.jakomi1.voiceServer.utils.TextUtils.mergeQuoted;

public class VoiceServerCommand implements CommandExecutor, TabCompleter {

    public static final List<String> SUBCOMMANDS = List.of("reload");

    @Override
    public boolean onCommand(CommandSender sender, Command cmd,  String label, String[] args) {
        if (args.length != 1) {
            MessageUtils.showVoiceServerCommandUsage(sender);

            return true;


        }

        if (args[0].equalsIgnoreCase("reload")) {
            DataUtils.reloadConfig();
            MessageUtils.sendSuccess(sender, "VoiceServer config was successfully reloaded");
        } else {
            MessageUtils.showVoiceServerCommandUsage(sender);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender,  Command command,  String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length != 1) return suggestions;

        if (args.length == 1) {
            SUBCOMMANDS.stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .forEach(suggestions::add);
        }

        return suggestions;
    }
}

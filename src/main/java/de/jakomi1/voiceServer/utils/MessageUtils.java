package de.jakomi1.voiceServer.utils;

import de.jakomi1.voiceServer.commands.VoicePermissionCommand;
import de.jakomi1.voiceServer.commands.VoiceServerCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import static de.jakomi1.voiceServer.commands.VoiceGroupCommand.SUBCOMMANDS;
import static de.jakomi1.voiceServer.utils.TextUtils.getChatPrefix;

public class MessageUtils {


    public static void showVoiceGroupCommandUsage(CommandSender sender) {
        sendError(sender, "Usage: /vcgroup <" + String.join("|", SUBCOMMANDS) + "> [...]");
    }

    public static void showVoiceServerCommandUsage(CommandSender sender) {
        sendError(sender, "Usage: /vcserver <" + String.join("|", VoiceServerCommand.SUBCOMMANDS) + ">");
    }
    public static void showVoicePermissionCommandUsage(CommandSender sender) {
        sendError(sender, "Usage: /vcpermission <" + String.join("|", VoicePermissionCommand.SUBCOMMANDS) +"> [...]");
    }
    public static void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(getChatPrefix() + ChatColor.GRAY + message);
    }

    public static void sendError(CommandSender sender, String message) {
        sender.sendMessage(getChatPrefix() + ChatColor.RED + message);
    }

    public static void sendPlain(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GRAY + message);
    }
}

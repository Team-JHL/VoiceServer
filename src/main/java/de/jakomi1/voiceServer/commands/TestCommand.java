package de.jakomi1.voiceServer.commands;

import de.jakomi1.voiceServer.utils.DataUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

public class TestCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] strings) {
        if(sender instanceof Player player) {
            DataUtils.updatePermissions(player);
            for (PermissionAttachmentInfo permInfo : player.getEffectivePermissions()) {
                String permission = permInfo.getPermission();
                boolean value = permInfo.getValue();
                player.sendMessage(permission + " = " + value);
            }

        }
        return true;
    }
}

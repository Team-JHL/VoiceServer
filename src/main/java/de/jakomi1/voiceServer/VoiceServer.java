package de.jakomi1.voiceServer;

import de.jakomi1.voiceServer.commands.*;
import de.jakomi1.voiceServer.listener.CreateGroupListener;
import de.jakomi1.voiceServer.listener.JoinGroupListener;
import de.jakomi1.voiceServer.listener.JoinListener;
import de.jakomi1.voiceServer.listener.LeaveGroupListener;
import de.jakomi1.voiceServer.utils.DataUtils;
import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.nio.channels.NetworkChannel;
import java.util.*;

import static de.jakomi1.voiceServer.utils.DataUtils.*;

public class VoiceServer extends JavaPlugin implements VoicechatPlugin, CommandExecutor, TabCompleter {
    public static VoicechatServerApi serverApi;
    public static JavaPlugin plugin;

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(CreateGroupEvent.class, CreateGroupListener::onGroupCreatedEvent);
        registration.registerEvent(JoinGroupEvent.class, JoinGroupListener::onGroupJoinEvent);
        registration.registerEvent(LeaveGroupEvent.class, LeaveGroupListener::onGroupLeaveEvent);
    }


    @Override
    public void onEnable() {
        plugin = this;
        BukkitVoicechatService service = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            getLogger().warning("[VoiceServer] Voice chat service not found. Plugin disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        service.registerPlugin(this);

        registerAllCommands();
        registerAllListener();
    }

    private void registerAllListener() {
        List<Listener> listeners = List.of(
                new JoinListener());
        listeners.forEach(this::registerListener);

    }
    private void registerListener(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }
    private void registerAllCommands() {
        registerCommand("vcserver",new VoiceServerCommand(),new VoiceServerCommand());
        registerCommand("vcgroup",new VoiceGroupCommand(),new VoiceGroupCommand());
        registerCommand("vcpermission",new VoicePermissionCommand(),new VoicePermissionCommand());
        registerCommand("testcommand",new TestCommand(),new EmptyTabCompleter());
    }

    private void registerCommand(String command, CommandExecutor executor, TabCompleter completer) {
        PluginCommand cmd = getServer().getPluginCommand(command);
        if(cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(Objects.requireNonNullElseGet(completer, EmptyTabCompleter::new));
        } else {
            getLogger().warning("Cannot register the \"/" + command + "\" command");
        }
    }

   @Override
    public String getPluginId() {
        return "voiceserver";
    }

    @Override
    public void initialize(VoicechatApi api) {
        if (api instanceof VoicechatServerApi s) {
            serverApi = s;
        } else {
            throw new IllegalStateException("Expected VoicechatServerApi");
        }
        loadConfig();
        Bukkit.getOnlinePlayers().forEach(DataUtils::updatePermissions);
    }

}

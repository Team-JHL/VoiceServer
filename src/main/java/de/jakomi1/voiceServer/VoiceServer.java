package de.jakomi1.voiceServer;

import de.jakomi1.voiceServer.commands.*;
import de.jakomi1.voiceServer.listener.*;
import de.jakomi1.voiceServer.utils.DataUtils;
import de.jakomi1.voiceServer.utils.GroupUtils;
import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.event.Listener;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;

import static de.jakomi1.voiceServer.utils.CommandUtils.registerDynamicCommand;
import static de.jakomi1.voiceServer.utils.CommandUtils.registerDynamicPermission;
import static de.jakomi1.voiceServer.utils.DataUtils.*;

public class VoiceServer extends JavaPlugin implements VoicechatPlugin, CommandExecutor, TabCompleter {
    public static VoicechatServerApi serverApi;
    public static JavaPlugin plugin;

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(CreateGroupEvent.class, CreateGroupListener::onGroupCreatedEvent);
        registration.registerEvent(JoinGroupEvent.class, JoinGroupListener::onGroupJoinEvent);
        registration.registerEvent(LeaveGroupEvent.class, LeaveGroupListener::onGroupLeaveEvent);
        registration.registerEvent(MicrophonePacketEvent.class, MicrophonePacketListener::onPacketEvent);
    }
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
                new JoinListener()
        );
        listeners.forEach(this::registerListener);
    }

    private void registerListener(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    private void registerAllCommands() {

        registerDynamicPermission("vcgroup", PermissionDefault.FALSE);
        registerDynamicPermission("vcserver", PermissionDefault.FALSE);
        registerDynamicPermission("vcpermission", PermissionDefault.FALSE);
        registerDynamicPermission("vcrecord", PermissionDefault.FALSE);
        registerDynamicPermission("vcsoundboard", PermissionDefault.FALSE);
        registerDynamicPermission("vcstream", PermissionDefault.FALSE);
        registerDynamicCommand("vcstream", new VoiceStreamCommand(), new VoiceStreamCommand(), List.of("voicestream"), "voiceserver.vcstream");
        registerDynamicCommand("vcserver", new VoiceServerCommand(), new VoiceServerCommand(), List.of("voiceserver"), "voiceserver.vcserver");
        registerDynamicCommand("vcgroup", new VoiceGroupCommand(), new VoiceGroupCommand(), List.of("voicegroup"), "voiceserver.vcgroup");
        registerDynamicCommand("vcsoundboard", new VoiceSoundboardCommand(), new VoiceSoundboardCommand(), List.of("voicesoundboard"), "voiceserver.vcsoundboard");
        registerDynamicCommand("vcpermission", new VoicePermissionCommand(), new VoicePermissionCommand(), List.of("voicepermission"), "voiceserver.vcpermission");
        registerDynamicCommand("vcrecord", new VoiceRecordCommand(), new VoiceRecordCommand(), List.of("voicerecord"), "voiceserver.vcrecord");
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
        Scheduler.runLater(() -> Bukkit.getOnlinePlayers().forEach(GroupUtils::joinDefaultGroup), 40L);
    }
}
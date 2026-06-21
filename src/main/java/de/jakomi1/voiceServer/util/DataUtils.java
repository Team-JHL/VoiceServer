package de.jakomi1.voiceServer.util;

import de.jakomi1.voiceServer.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static de.jakomi1.voiceServer.VoiceServer.plugin;

public class DataUtils {

    public static final String CONFIG_NAME = "config.yml";
    public static final String DEFAULT_PREFIX = "§7[§9VS§7]";
    private static FileConfiguration config;
    private static boolean loaded = false;
    private static final Method UPDATE_COMMANDS_METHOD;

    static {
        Method m = null;
        try {
            m = Player.class.getMethod("updateCommands");
        } catch (NoSuchMethodException ignored) {
        }
        UPDATE_COMMANDS_METHOD = m;
    }

    private static final String[] PERMISSION_CATEGORIES = new String[]{"listen", "speak", "groups"};
    private static final Map<UUID, PermissionAttachment> permissionAttachments = new ConcurrentHashMap<>();
    private static volatile boolean DEBUG = false;
    private static volatile boolean PERSISTENT_GROUPS_SHOULD_SURVIVE_RESTART = false;
    private static final String PERSISTENT_GROUPS_PATH = "persistent-groups";
    private static final String DEFAULT_GROUP_PATH = "default-group";

    private static volatile String DEFAULT_GROUP_NAME = null;
    private static volatile boolean DEFAULT_GROUP_PERSISTENT = false;

    public static void loadConfig() {
        internalLoad();
        loadPersistentGroups();
    }

    public static void reloadConfig() {
        internalLoad();
        loadPersistentGroups();
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static void saveConfigFile(File configFile) {
        if (config == null) return;
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save config.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void savePersistentGroup(String serializedGroup) {
        if (config == null || serializedGroup == null) return;

        if (!config.contains(PERSISTENT_GROUPS_PATH)) {
            config.set(PERSISTENT_GROUPS_PATH, new ArrayList<String>());
        }

        List<String> existing = new ArrayList<>(config.getStringList(PERSISTENT_GROUPS_PATH));
        if (!existing.contains(serializedGroup)) {
            existing.add(serializedGroup);
            config.set(PERSISTENT_GROUPS_PATH, existing);
            saveConfigFile(new File(plugin.getDataFolder(), CONFIG_NAME));
            plugin.getLogger().info("Saved persistent group: " + serializedGroup);
        }
    }

    public static void setDebug(boolean debug) {
        DEBUG = debug;

        if (config != null) {
            config.set("debug", debug);
            saveConfigFile(new File(plugin.getDataFolder(), CONFIG_NAME));
        }

        plugin.getLogger().info("VoiceServer debug " + (DEBUG ? "ENABLED" : "DISABLED"));
    }

    public static boolean isDebug() {
        return DEBUG;
    }

    public static boolean getPersistentGroupsShouldSurviveRestart() {
        return PERSISTENT_GROUPS_SHOULD_SURVIVE_RESTART;
    }

    public static void setPersistentGroupsShouldSurviveRestart(boolean survive) {
        PERSISTENT_GROUPS_SHOULD_SURVIVE_RESTART = survive;

        if (config != null) {
            config.set("persistent-groups-should-survive-restart", survive);
            saveConfigFile(new File(plugin.getDataFolder(), CONFIG_NAME));
        }

        plugin.getLogger().info("persistent-groups-should-survive-restart " + (survive ? "ENABLED" : "DISABLED"));

        if (survive) {
            loadPersistentGroups();
        }
    }

    public static String getDefaultGroupName() {
        return DEFAULT_GROUP_NAME;
    }

    public static boolean isDefaultGroupPersistent() {
        return DEFAULT_GROUP_PERSISTENT;
    }

    public static void setDefaultGroup(String groupName, boolean persistent) {
        String normalized = groupName == null ? null : groupName.trim();
        if (normalized != null && normalized.isBlank()) normalized = null;

        DEFAULT_GROUP_NAME = normalized;
        DEFAULT_GROUP_PERSISTENT = persistent;

        if (config == null) return;

        if (normalized != null) {
            config.set(DEFAULT_GROUP_PATH + ".name", normalized);
            config.set(DEFAULT_GROUP_PATH + ".persistent", persistent);
        } else {
            config.set(DEFAULT_GROUP_PATH + ".name", null);
            config.set(DEFAULT_GROUP_PATH + ".persistent", null);
        }

        saveConfigFile(new File(plugin.getDataFolder(), CONFIG_NAME));
    }

    public static void clearDefaultGroupIfMatches(String groupName) {
        if (groupName == null || groupName.isBlank()) return;

        if (DEFAULT_GROUP_NAME != null && DEFAULT_GROUP_NAME.equalsIgnoreCase(groupName)) {
            DEFAULT_GROUP_NAME = null;
            DEFAULT_GROUP_PERSISTENT = false;
        }

        if (config == null) return;

        String storedName = config.getString(DEFAULT_GROUP_PATH + ".name");
        if (storedName != null && storedName.equalsIgnoreCase(groupName)) {
            config.set(DEFAULT_GROUP_PATH + ".name", null);
            config.set(DEFAULT_GROUP_PATH + ".persistent", null);
            saveConfigFile(new File(plugin.getDataFolder(), CONFIG_NAME));
        }
    }

    private static void debugLog(String msg) {
        if (DEBUG) {
            plugin.getLogger().info("[DEBUG] " + msg);
        }
    }

    public static void loadPersistentGroups() {
        if (config == null) return;

        if (!PERSISTENT_GROUPS_SHOULD_SURVIVE_RESTART) {
            plugin.getLogger().info("Persistent groups are configured not to survive restart; skipping automatic restore.");
            return;
        }

        List<String> storedGroups = new ArrayList<>(config.getStringList(PERSISTENT_GROUPS_PATH));
        if (storedGroups.isEmpty()) return;

        int created = 0;
        for (String serialized : storedGroups) {
            Scheduler.run(() -> GroupUtils.createGroupWithoutPlayersFromString(serialized));
            created++;
        }

        plugin.getLogger().info("Will load " + created + " persistent groups from config.");
    }

    public static void resetAllPermissions() {
        if (config == null) return;

        File configFile = new File(plugin.getDataFolder(), CONFIG_NAME);

        for (String cat : PERMISSION_CATEGORIES) {
            String path = "permissions." + cat;

            config.set(path + ".default", true);
            config.set(path + ".allowed-players", new ArrayList<String>());
            config.set(path + ".disallowed-players", new ArrayList<String>());
        }

        saveConfigFile(configFile);
        reloadPermissions();
        applyPermissionsToAllOnline();

        plugin.getLogger().info("ALL permissions reset to defaults.");
    }

    public static String getChatPrefix() {
        if (config == null) return DEFAULT_PREFIX;

        String prefix = config.getString("chat-prefix", DEFAULT_PREFIX);
        if (prefix == null || prefix.trim().isEmpty()) {
            prefix = DEFAULT_PREFIX;
            config.set("chat-prefix", DEFAULT_PREFIX);
            saveConfigFile(new File(plugin.getDataFolder(), CONFIG_NAME));
        }

        return prefix + " ";
    }

    public static boolean isPermissionDefault(String category) {
        if (config == null) return true;
        if (!isValidCategory(category)) return true;
        return config.getBoolean(getPermissionPath(category) + ".default", true);
    }

    public static boolean getPermissionDefault(String category) {
        return isPermissionDefault(category);
    }

    public static List<String> getAllowedPlayers(String category) {
        if (config == null) return new ArrayList<>();
        if (!isValidCategory(category)) return new ArrayList<>();
        return new ArrayList<>(config.getStringList(getPermissionPath(category) + ".allowed-players"));
    }

    public static List<String> getDisallowedPlayers(String category) {
        if (config == null) return new ArrayList<>();
        if (!isValidCategory(category)) return new ArrayList<>();
        return new ArrayList<>(config.getStringList(getPermissionPath(category) + ".disallowed-players"));
    }

    public static boolean isPlayerAllowed(String category, String playerName) {
        if (config == null) return true;
        if (!isValidCategory(category) || playerName == null || playerName.isBlank()) return false;

        boolean def = isPermissionDefault(category);
        List<String> allowed = getAllowedPlayers(category);
        List<String> disallowed = getDisallowedPlayers(category);

        if (def) {
            boolean result = !disallowed.contains(playerName);
            debugLog("isPlayerAllowed(category=" + category + ", player=" + playerName + ") => default=true -> " + result);
            return result;
        } else {
            boolean result = allowed.contains(playerName);
            debugLog("isPlayerAllowed(category=" + category + ", player=" + playerName + ") => default=false -> " + result);
            return result;
        }
    }

    public static void removeAttachmentFor(Player player) {
        if (player == null) return;
        PermissionAttachment att = permissionAttachments.remove(player.getUniqueId());
        if (att != null) {
            try {
                player.removeAttachment(att);
                debugLog("Removed permission attachment for " + player.getName());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to remove permission attachment for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    public static void clearAllAttachments() {
        List<UUID> keys = new ArrayList<>(permissionAttachments.keySet());
        for (UUID id : keys) {
            PermissionAttachment att = permissionAttachments.remove(id);
            if (att != null) {
                try {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline()) {
                        p.removeAttachment(att);
                        debugLog("Removed attachment for online player " + p.getName());
                    } else {
                        debugLog("Attachment removed for offline UUID " + id);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error while removing attachment for " + id + ": " + e.getMessage());
                }
            }
        }
        permissionAttachments.clear();
    }

    public static boolean setPermissionDefault(String category, boolean def) {
        if (config == null) return false;
        if (!isValidCategory(category)) return false;

        String base = getPermissionPath(category);
        String defaultPath = base + ".default";
        boolean old = config.getBoolean(defaultPath, true);
        if (old == def) return false;

        config.set(defaultPath, def);
        saveConfigFile(new File(plugin.getDataFolder(), CONFIG_NAME));

        reloadPermissions();
        applyPermissionsToAllOnline();

        plugin.getLogger().info("Permission default for '" + category + "' changed to " + def);
        return true;
    }

    public static void updatePermissions(Player player) {
        if (player == null) return;

        if (!Bukkit.isPrimaryThread()) {
            plugin.getLogger().fine("updatePermissions called asynchronously for " + player.getName() + " — scheduling sync task.");
            Scheduler.run(() -> updatePermissions(player));
            return;
        }

        String playerName = player.getName();
        UUID uuid = player.getUniqueId();

        reloadPermissions();

        removeAttachmentFor(player);

        PermissionAttachment att;
        try {
            att = player.addAttachment(plugin);
            permissionAttachments.put(uuid, att);
            debugLog("Recreated PermissionAttachment for " + playerName + " (force-refresh).");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create permission attachment for " + playerName + ": " + e.getMessage());
            e.printStackTrace();
            return;
        }

        for (String category : PERMISSION_CATEGORIES) {
            String defaultPath = getPermissionPath(category) + ".default";
            if (config == null) continue;
            if (!config.contains(defaultPath) || config.get(defaultPath) == null) {
                config.set(defaultPath, true);
                saveConfigFile(new File(plugin.getDataFolder(), CONFIG_NAME));
                plugin.getLogger().warning("Permission default for '" + category + "' was missing or null — set to true.");
            }

            boolean desired = isPlayerAllowed(category, playerName);
            String permKey = "voicechat." + category;

            try {
                att.setPermission(permKey, desired);
                debugLog("Set permission " + permKey + "=" + desired + " for " + playerName);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to set permission '" + permKey + "' for " + playerName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        if (UPDATE_COMMANDS_METHOD != null) {
            try {
                UPDATE_COMMANDS_METHOD.invoke(player);
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean addPermissionPlayer(String category, String playerName) {
        if (config == null) return false;
        if (!isValidCategory(category) || playerName == null || playerName.isBlank()) return false;

        File configFile = new File(plugin.getDataFolder(), CONFIG_NAME);
        String base = getPermissionPath(category);

        String allowPath = base + ".allowed-players";
        String disPath = base + ".disallowed-players";

        List<String> allow = new ArrayList<>(config.getStringList(allowPath));
        List<String> dis = new ArrayList<>(config.getStringList(disPath));

        boolean changed = false;

        if (!allow.contains(playerName)) {
            allow.add(playerName);
            config.set(allowPath, allow);
            changed = true;
            debugLog("Added " + playerName + " to " + allowPath);
        }

        if (dis.remove(playerName)) {
            config.set(disPath, dis);
            changed = true;
            debugLog("Removed " + playerName + " from " + disPath);
        }

        if (changed) {
            saveConfigFile(configFile);
            Player p = Bukkit.getPlayerExact(playerName);
            if (p != null && p.isOnline()) {
                updatePermissions(p);
            }
        }

        return changed;
    }

    public static boolean removePermissionPlayer(String category, String playerName) {
        if (config == null) return false;
        if (!isValidCategory(category) || playerName == null || playerName.isBlank()) return false;

        File configFile = new File(plugin.getDataFolder(), CONFIG_NAME);
        String base = getPermissionPath(category);

        String allowPath = base + ".allowed-players";
        String disPath = base + ".disallowed-players";

        List<String> allow = new ArrayList<>(config.getStringList(allowPath));
        List<String> dis = new ArrayList<>(config.getStringList(disPath));

        boolean changed = false;

        if (allow.remove(playerName)) {
            config.set(allowPath, allow);
            changed = true;
            debugLog("Removed " + playerName + " from " + allowPath);
        }

        if (!dis.contains(playerName)) {
            dis.add(playerName);
            config.set(disPath, dis);
            changed = true;
            debugLog("Added " + playerName + " to " + disPath);
        }

        if (changed) {
            saveConfigFile(configFile);
            Player p = Bukkit.getPlayerExact(playerName);
            if (p != null && p.isOnline()) {
                updatePermissions(p);
            }
        }
        return changed;
    }

    public static boolean resetPlayerPermissions(String playerName) {
        if (config == null) return false;
        if (playerName == null || playerName.isBlank()) return false;

        File configFile = new File(plugin.getDataFolder(), CONFIG_NAME);
        boolean changed = false;

        for (String cat : PERMISSION_CATEGORIES) {
            String base = getPermissionPath(cat);
            String allowPath = base + ".allowed-players";
            String disPath = base + ".disallowed-players";

            List<String> allow = new ArrayList<>(config.getStringList(allowPath));
            List<String> dis = new ArrayList<>(config.getStringList(disPath));

            if (allow.remove(playerName)) {
                config.set(allowPath, allow);
                changed = true;
                debugLog("Removed " + playerName + " from " + allowPath);
            }
            if (dis.remove(playerName)) {
                config.set(disPath, dis);
                changed = true;
                debugLog("Removed " + playerName + " from " + disPath);
            }
        }

        if (changed) {
            saveConfigFile(configFile);
            reloadPermissions();
            Player p = Bukkit.getPlayerExact(playerName);
            if (p != null && p.isOnline()) {
                updatePermissions(p);
            }
            plugin.getLogger().info("Permissions for player '" + playerName + "' were reset (explicit entries removed).");
        }

        return changed;
    }

    public static boolean addAllowedPlayer(String category, String playerName) {
        if (config == null) return false;
        if (!isValidCategory(category) || playerName == null || playerName.isBlank()) return false;
        String path = getPermissionPath(category) + ".allowed-players";
        List<String> allow = config.getStringList(path);
        if (allow.contains(playerName)) return false;
        allow.add(playerName);
        config.set(path, allow);
        saveConfigFile(new File(plugin.getDataFolder(), CONFIG_NAME));
        return true;
    }

    public static boolean removeAllowedPlayer(String category, String playerName) {
        if (config == null) return false;
        if (!isValidCategory(category) || playerName == null || playerName.isBlank()) return false;
        String path = getPermissionPath(category) + ".allowed-players";
        List<String> allow = config.getStringList(path);
        boolean removed = allow.remove(playerName);
        if (removed) {
            config.set(path, allow);
            saveConfigFile(new File(plugin.getDataFolder(), CONFIG_NAME));
        }
        return removed;
    }

    public static boolean addDisallowedPlayer(String category, String playerName) {
        if (config == null) return false;
        if (!isValidCategory(category) || playerName == null || playerName.isBlank()) return false;
        String path = getPermissionPath(category) + ".disallowed-players";
        List<String> dis = config.getStringList(path);
        if (dis.contains(playerName)) return false;
        dis.add(playerName);
        config.set(path, dis);
        saveConfigFile(new File(plugin.getDataFolder(), CONFIG_NAME));
        return true;
    }

    public static boolean removeDisallowedPlayer(String category, String playerName) {
        if (config == null) return false;
        if (!isValidCategory(category) || playerName == null || playerName.isBlank()) return false;
        String path = getPermissionPath(category) + ".disallowed-players";
        List<String> dis = config.getStringList(path);
        boolean removed = dis.remove(playerName);
        if (removed) {
            config.set(path, dis);
            saveConfigFile(new File(plugin.getDataFolder(), CONFIG_NAME));
        }
        return removed;
    }

    private static void ensureDataFolder(File folder) throws IOException {
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Could not create plugin data folder: " + folder.getAbsolutePath());
        }
    }

    private static boolean ensureConfigFile(File configFile) {
        boolean created = false;

        if (!configFile.exists()) {
            if (plugin.getResource(CONFIG_NAME) != null) {
                try {
                    plugin.saveResource(CONFIG_NAME, false);
                    plugin.getLogger().info("Default config.yml extracted from plugin jar.");
                } catch (Exception e) {
                    plugin.getLogger().warning("Couldn't extract config.yml from jar: " + e.getMessage());
                }
            }

            if (!configFile.exists()) {
                YamlConfiguration minimal = new YamlConfiguration();
                minimal.set("chat-prefix", DEFAULT_PREFIX);
                minimal.set("debug", false);
                minimal.set("persistent-groups-should-survive-restart", false);
                minimal.set(PERSISTENT_GROUPS_PATH, new ArrayList<String>());
                minimal.set(DEFAULT_GROUP_PATH + ".name", null);
                minimal.set(DEFAULT_GROUP_PATH + ".persistent", null);
                try {
                    minimal.save(configFile);
                    plugin.getLogger().info("Created new minimal config.yml with default prefix.");
                    created = true;
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to create minimal config.yml: " + e.getMessage());
                }
            }
        }
        return created;
    }

    private static void ensurePersistentGroupsStructure(FileConfiguration cfg, File configFile) {
        if (cfg == null) return;
        boolean changed = false;

        if (!cfg.contains("persistent-groups-should-survive-restart")) {
            cfg.set("persistent-groups-should-survive-restart", false);
            changed = true;
            debugLog("Created missing 'persistent-groups-should-survive-restart' -> false");
        }

        if (!cfg.contains(PERSISTENT_GROUPS_PATH)) {
            cfg.set(PERSISTENT_GROUPS_PATH, new ArrayList<String>());
            changed = true;
            debugLog("Created missing '" + PERSISTENT_GROUPS_PATH + "' (empty list)");
        }

        if (!cfg.contains(DEFAULT_GROUP_PATH + ".name")) {
            cfg.set(DEFAULT_GROUP_PATH + ".name", null);
            changed = true;
        }

        if (!cfg.contains(DEFAULT_GROUP_PATH + ".persistent")) {
            cfg.set(DEFAULT_GROUP_PATH + ".persistent", null);
            changed = true;
        }

        if (changed) {
            saveConfigFile(configFile);
        }
    }

    private static void validatePrefixAndFixIfNeeded(FileConfiguration cfg, File configFile) {
        String prefix = cfg.getString("chat-prefix");
        if (prefix == null || prefix.trim().isEmpty()) {
            cfg.set("chat-prefix", DEFAULT_PREFIX);
            saveConfigFile(configFile);
            plugin.getLogger().warning("chat-prefix was missing or empty – set to default value.");
        }
    }

    private static void internalLoad() {
        File dataFolder = plugin.getDataFolder();
        File configFile = new File(dataFolder, CONFIG_NAME);

        try {
            ensureDataFolder(dataFolder);
            boolean created = ensureConfigFile(configFile);

            config = YamlConfiguration.loadConfiguration(configFile);

            ensurePersistentGroupsStructure(config, configFile);

            boolean cfgDebug = config.getBoolean("debug", false);
            DEBUG = cfgDebug;

            PERSISTENT_GROUPS_SHOULD_SURVIVE_RESTART = config.getBoolean("persistent-groups-should-survive-restart", false);
            debugLog("persistent-groups-should-survive-restart=" + PERSISTENT_GROUPS_SHOULD_SURVIVE_RESTART);

            String storedDefaultName = config.getString(DEFAULT_GROUP_PATH + ".name", null);
            boolean storedDefaultPersistent = config.getBoolean(DEFAULT_GROUP_PATH + ".persistent", false);
            if (storedDefaultName != null && !storedDefaultName.isBlank()) {
                DEFAULT_GROUP_NAME = storedDefaultName.trim();
                DEFAULT_GROUP_PERSISTENT = storedDefaultPersistent;
            } else {
                DEFAULT_GROUP_NAME = null;
                DEFAULT_GROUP_PERSISTENT = false;
            }

            validatePrefixAndFixIfNeeded(config, configFile);
            ensurePermissionsStructure(config, configFile);

            loaded = true;

            String msg = "VoiceServer config.yml " + (created ? "created and " : "") + "loaded successfully.";
            plugin.getLogger().info(msg);

            applyPermissionsToAllOnline();

        } catch (Exception e) {
            loaded = false;
            plugin.getLogger().severe("Error while loading config.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void removePersistentGroupByName(String groupName) {
        if (config == null || groupName == null || groupName.isBlank()) return;

        if (!config.contains(PERSISTENT_GROUPS_PATH)) return;

        List<String> storedGroups = new ArrayList<>(config.getStringList(PERSISTENT_GROUPS_PATH));
        boolean removed = storedGroups.removeIf(serialized -> {
            if (serialized == null || serialized.isBlank()) return false;
            String[] parts = serialized.split("\\+");
            return parts.length >= 1 && groupName.equalsIgnoreCase(parts[0]);
        });

        if (removed) {
            config.set(PERSISTENT_GROUPS_PATH, storedGroups);
            saveConfigFile(new File(plugin.getDataFolder(), CONFIG_NAME));
            clearDefaultGroupIfMatches(groupName);
        }
    }

    public static void reloadPermissions() {
        if (config == null) {
            plugin.getLogger().warning("Config not loaded — cannot reload permissions.");
            return;
        }

        File configFile = new File(plugin.getDataFolder(), CONFIG_NAME);

        FileConfiguration fresh = YamlConfiguration.loadConfiguration(configFile);

        if (!fresh.contains("permissions")) {
            plugin.getLogger().warning("permissions section missing in config.yml — recreating default structure.");
        }

        ensurePermissionsStructure(fresh, configFile);

        for (String cat : PERMISSION_CATEGORIES) {
            String path = "permissions." + cat;

            config.set(path + ".default", fresh.getBoolean(path + ".default", true));
            config.set(path + ".allowed-players", fresh.getStringList(path + ".allowed-players"));
            config.set(path + ".disallowed-players", fresh.getStringList(path + ".disallowed-players"));
        }

        ensurePersistentGroupsStructure(fresh, configFile);
        PERSISTENT_GROUPS_SHOULD_SURVIVE_RESTART = fresh.getBoolean("persistent-groups-should-survive-restart", PERSISTENT_GROUPS_SHOULD_SURVIVE_RESTART);
        debugLog("Reloaded persistent-groups-should-survive-restart=" + PERSISTENT_GROUPS_SHOULD_SURVIVE_RESTART);

        String storedDefaultName = fresh.getString(DEFAULT_GROUP_PATH + ".name", null);
        boolean storedDefaultPersistent = fresh.getBoolean(DEFAULT_GROUP_PATH + ".persistent", false);
        if (storedDefaultName != null && !storedDefaultName.isBlank()) {
            DEFAULT_GROUP_NAME = storedDefaultName.trim();
            DEFAULT_GROUP_PERSISTENT = storedDefaultPersistent;
        } else {
            DEFAULT_GROUP_NAME = null;
            DEFAULT_GROUP_PERSISTENT = false;
        }

        saveConfigFile(configFile);

        plugin.getLogger().info("Permissions were successfully reloaded from config.yml.");
        debugLog("Reloaded permission data: " + Arrays.toString(PERMISSION_CATEGORIES));
    }

    private static void ensurePermissionsStructure(FileConfiguration cfg, File configFile) {
        if (cfg == null) return;

        String base = "permissions";
        boolean changed = false;

        if (!cfg.contains(base)) {
            cfg.set(base, null);
            changed = true;
        }

        for (String cat : PERMISSION_CATEGORIES) {
            String catPath = base + "." + cat;
            String defaultPath = catPath + ".default";
            String playersOldPath = catPath + ".players";
            String allowPath = catPath + ".allowed-players";
            String disPath = catPath + ".disallowed-players";

            if (!cfg.contains(defaultPath)) {
                cfg.set(defaultPath, true);
                changed = true;
                debugLog("Set missing default for " + cat + " -> true");
            }

            if (cfg.contains(playersOldPath) && !cfg.contains(allowPath) && !cfg.contains(disPath)) {
                List<String> oldPlayers = cfg.getStringList(playersOldPath);
                cfg.set(allowPath, oldPlayers != null ? oldPlayers : new ArrayList<String>());
                cfg.set(disPath, new ArrayList<String>());
                cfg.set(playersOldPath, null);
                changed = true;
                debugLog("Migrated deprecated '" + playersOldPath + "' to '" + allowPath + "'.");
            } else {
                if (!cfg.contains(allowPath)) {
                    cfg.set(allowPath, new ArrayList<String>());
                    changed = true;
                    debugLog("Created missing " + allowPath);
                }
                if (!cfg.contains(disPath)) {
                    cfg.set(disPath, new ArrayList<String>());
                    changed = true;
                    debugLog("Created missing " + disPath);
                }
            }
        }

        if (changed) {
            saveConfigFile(configFile);
        }
    }

    private static boolean isValidCategory(String category) {
        if (category == null) return false;
        for (String c : PERMISSION_CATEGORIES) {
            if (c.equalsIgnoreCase(category)) return true;
        }
        return false;
    }

    private static String getPermissionPath(String category) {
        return "permissions." + category.toLowerCase();
    }

    private static void applyPermissionsToAllOnline() {
        debugLog("Applying permissions to all online players (" + Bukkit.getOnlinePlayers().size() + ")");
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                updatePermissions(p);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to update permissions for online player " + p.getName() + ": " + e.getMessage());
            }
        }
    }
}
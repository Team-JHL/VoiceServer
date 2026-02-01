package de.jakomi1.voiceServer.utils;

import de.maxhenkel.voicechat.api.Group;
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
            // 1.8 → Methode existiert nicht
        }
        UPDATE_COMMANDS_METHOD = m;
    }

    // permission category names used in config under "permissions"
    private static final String[] PERMISSION_CATEGORIES = new String[]{"listen", "speak", "groups"};

    // runtime permission attachments (keine mehrfachen attachments pro Spieler!)
    private static final Map<UUID, PermissionAttachment> permissionAttachments = new ConcurrentHashMap<>();

    // debug flag (kann über config.yml unter 'debug: true' gesetzt werden)
    private static volatile boolean DEBUG = false;

    // whether persistent groups should survive a restart (config key: "persistent-groups-should-survive-restart")
    // optional boolean; default = false
    private static volatile boolean PERSISTENT_GROUPS_SHOULD_SURVIVE_RESTART = false;

    private static final String PERSISTENT_GROUPS_PATH = "persistent-groups";

    // -------------------- Öffentliche API --------------------

    public static void loadConfig() {
        internalLoad();
        loadPersistentGroups();
    }

    /**
     * Lädt die Konfiguration komplett neu, exakt wie der erste Load
     */
    public static void reloadConfig() {
        internalLoad();
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

    /**
     * Speichert eine persistente Gruppe als String in der config.
     * @param serializedGroup Ergebnis von GroupUtils.serializePersistentGroup(group, password)
     */
    public static void savePersistentGroup(String serializedGroup) {
        if (config == null || serializedGroup == null) return;

        // ensure list exists
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

    /**
     * Setzt das Debug-Flag zur Laufzeit (wird auch beim Laden aus config.yml gesetzt).
     */
    public static void setDebug(boolean debug) {
        DEBUG = debug;
        plugin.getLogger().info("VoiceServer debug " + (DEBUG ? "ENABLED" : "DISABLED"));
    }

    public static boolean isDebug() {
        return DEBUG;
    }

    /**
     * Getter für das neue Feature-Flag: persistent-groups-should-survive-restart
     * Default ist false. Wird beim Laden aus config.yml gesetzt.
     */
    public static boolean getPersistentGroupsShouldSurviveRestart() {
        // Always read current runtime value (keeps a single access point)
        return PERSISTENT_GROUPS_SHOULD_SURVIVE_RESTART;
    }

    private static void debugLog(String msg) {
        if (DEBUG) {
            plugin.getLogger().info("[DEBUG] " + msg);
        }
    }


    /**
     * Lädt beim Init / Reload alle persistenten Gruppen aus der config
     * und erstellt sie über GroupUtils.
     * Wenn das Flag persistent-groups-should-survive-restart=false ist,
     * wird die Liste geleert.
     */
    public static void loadPersistentGroups() {
        if (config == null) return;

        // ensure the persistent-groups list exists and flag exists
        if (!config.contains(PERSISTENT_GROUPS_PATH)) {
            config.set(PERSISTENT_GROUPS_PATH, new ArrayList<String>());
            saveConfigFile(new File(plugin.getDataFolder(), CONFIG_NAME));
        }

        List<String> storedGroups = new ArrayList<>(config.getStringList(PERSISTENT_GROUPS_PATH));

        if (!getPersistentGroupsShouldSurviveRestart()) {
            // ensure config has an empty list
            config.set(PERSISTENT_GROUPS_PATH, new ArrayList<String>());
            saveConfigFile(new File(plugin.getDataFolder(), CONFIG_NAME));
            plugin.getLogger().info("Persistent groups cleared (survive-restart=false).");
            return;
        }

        if (storedGroups.isEmpty()) return;

        int created = 0;
        for (String serialized : storedGroups) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        GroupUtils.createGroupWithoutPlayersFromString(serialized);
                    }, 20*5);
            created++;
        }
        plugin.getLogger().info("Will load " + created + " persistent groups from config.");
    }
    /**
     * Resets ALL permission categories back to their default state:
     * - default = true
     * - allowed-players = []
     * - disallowed-players = []
     *
     * Also saves config.yml und live-updates all online players.
     */
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

        // Permissions neu einlesen
        reloadPermissions();

        // Permissions live für alle Spieler aktualisieren (force)
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

    // -------------------- Permission-Config API --------------------

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

    /** Entfernt und entfernt das Attachment eines Spielers (z.B. bei Quit / Plugin-Disable). */
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

    /** Entfernt alle Attachments (z.B. beim Plugin-Disable). */
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

        // Force update all online players
        applyPermissionsToAllOnline();

        plugin.getLogger().info("Permission default for '" + category + "' changed to " + def);
        return true;
    }

    /**
     * Erzeugt zwingend ein neues Attachment für den Spieler und setzt
     * explizit *jedes* einzelne voicechat.<category> Permission (kein wildcard).
     * -> Force-Verhalten: Sauberer Neustart des Attachments + explizite Setzung
     */
    public static void updatePermissions(Player player) {
        if (player == null) return;

        // Ensure execution on main thread
        if (!Bukkit.isPrimaryThread()) {
            plugin.getLogger().fine("updatePermissions called asynchronously for " + player.getName() + " — scheduling sync task.");
            Bukkit.getScheduler().runTask(plugin, () -> updatePermissions(player));
            return;
        }

        String playerName = player.getName();
        UUID uuid = player.getUniqueId();

        // ensure config is up to date
        reloadPermissions();

        // REMOVE existing attachment we created (force clean state)
        removeAttachmentFor(player);

        // create a fresh attachment (guarantees no stale values)
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

        // compute desired values and set them explicitly for each sub-permission (no wildcard)
        for (String category : PERMISSION_CATEGORIES) {
            // ensure default exists
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
                // set each sub-permission explicitly (true or false)
                att.setPermission(permKey, desired);
                debugLog("Set permission " + permKey + "=" + desired + " for " + playerName);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to set permission '" + permKey + "' for " + playerName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Trigger commands update if available
        if (UPDATE_COMMANDS_METHOD != null) {
            try {
                UPDATE_COMMANDS_METHOD.invoke(player);
            } catch (Throwable ignored) {
                // ignore multi-version failures
            }
        }
    }


    // Give permission to a player (used for /vcpermission give ...)
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
                // force update for that player
                updatePermissions(p);
            }
        }

        return changed;
    }

    // Remove permission from a player (used for /vcpermission remove ...)
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

    // direct helpers

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

    // -------------------- Private Hilfsmethoden --------------------

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
                // new optional flag: persistent-groups-should-survive-restart (default: false)
                minimal.set("persistent-groups-should-survive-restart", false);
                // ensure an empty list for persistent groups is present by default
                minimal.set(PERSISTENT_GROUPS_PATH, new ArrayList<String>());
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

    /**
     * Ensure persistent-groups flag and list exist in the given config object.
     * If they are missing, set sensible defaults and persist the file.
     */
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

            // ensure persistent groups flag/list exist before reading
            ensurePersistentGroupsStructure(config, configFile);

            boolean cfgDebug = config.getBoolean("debug", false);
            setDebug(cfgDebug);

            // read new optional flag from config (default false)
            PERSISTENT_GROUPS_SHOULD_SURVIVE_RESTART = config.getBoolean("persistent-groups-should-survive-restart", false);
            debugLog("persistent-groups-should-survive-restart=" + PERSISTENT_GROUPS_SHOULD_SURVIVE_RESTART);

            validatePrefixAndFixIfNeeded(config, configFile);

            ensurePermissionsStructure(config, configFile);

            loaded = true;

            String msg = "VoiceServer config.yml " + (created ? "created and " : "") + "loaded successfully.";
            plugin.getLogger().info(msg);

            // Force apply permissions for all online players after load
            applyPermissionsToAllOnline();

            // ⚡ Hier die persistenten Gruppen laden

        } catch (Exception e) {
            loaded = false;
            plugin.getLogger().severe("Error while loading config.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }
    /**
     * Entfernt alle persistente Gruppen mit dem gegebenen Namen aus der Config.
     * Das Passwort wird dabei ignoriert.
     * @param groupName Der Name der Gruppe, die entfernt werden soll
     */
    public static void removePersistentGroupByName(String groupName) {
        if (config == null || groupName == null || groupName.isBlank()) return;

        // Prüfen, ob die Liste existiert
        if (!config.contains(PERSISTENT_GROUPS_PATH)) return;

        List<String> storedGroups = new ArrayList<>(config.getStringList(PERSISTENT_GROUPS_PATH));
        boolean removed = storedGroups.removeIf(serialized -> {
            if (serialized == null || serialized.isBlank()) return false;
            String[] parts = serialized.split("\\+");
            return parts.length >= 1 && groupName.equals(parts[0]);
        });

        if (removed) {
            config.set(PERSISTENT_GROUPS_PATH, storedGroups);
            saveConfigFile(new File(plugin.getDataFolder(), CONFIG_NAME));
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

        // also refresh the persistent-groups flag/list from disk in case someone edited config manually
        ensurePersistentGroupsStructure(fresh, configFile);
        PERSISTENT_GROUPS_SHOULD_SURVIVE_RESTART = fresh.getBoolean("persistent-groups-should-survive-restart", PERSISTENT_GROUPS_SHOULD_SURVIVE_RESTART);
        debugLog("Reloaded persistent-groups-should-survive-restart=" + PERSISTENT_GROUPS_SHOULD_SURVIVE_RESTART);

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

    /**
     * Wendet updatePermissions(Player) für alle online Spieler an (force).
     */
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

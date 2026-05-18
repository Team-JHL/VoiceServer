package de.jakomi1.voiceServer;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public final class Scheduler {

    private static final boolean FOLIA;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        FOLIA = folia;
    }

    private Scheduler() {
    }

    private static Plugin plugin() {
        return VoiceServer.plugin;
    }

    public static void run(Runnable runnable) {
        if (!plugin().isEnabled()) {
            return;
        }

        if (FOLIA) {
            executeFolia(runnable);
            return;
        }

        Bukkit.getScheduler().runTask(plugin(), runnable);
    }

    public static Task runLater(Runnable runnable, long delayTicks) {
        if (!plugin().isEnabled()) {
            return null;
        }

        if (FOLIA) {
            Object task = runDelayedFolia(runnable, delayTicks);
            return new Task(task);
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(
                plugin(),
                runnable,
                delayTicks
        );

        return new Task(task);
    }

    public static void runAsync(Runnable runnable) {
        if (!plugin().isEnabled()) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin(), runnable);
    }

    private static void executeFolia(Runnable runnable) {
        try {
            Method method = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Object scheduler = method.invoke(Bukkit.getServer());

            Method execute = scheduler.getClass().getMethod(
                    "execute",
                    Plugin.class,
                    Runnable.class
            );

            execute.invoke(scheduler, plugin(), runnable);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Object runDelayedFolia(Runnable runnable, long delay) {
        try {
            Method method = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Object scheduler = method.invoke(Bukkit.getServer());

            Method runDelayed = scheduler.getClass().getMethod(
                    "runDelayed",
                    Plugin.class,
                    Consumer.class,
                    long.class
            );

            return runDelayed.invoke(
                    scheduler,
                    plugin(),
                    (Consumer<Object>) task -> runnable.run(),
                    delay
            );

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static final class Task {

        private final Object foliaTask;
        private final BukkitTask bukkitTask;

        public Task(Object foliaTask) {
            this.foliaTask = foliaTask;
            this.bukkitTask = null;
        }

        public Task(BukkitTask bukkitTask) {
            this.bukkitTask = bukkitTask;
            this.foliaTask = null;
        }

        public void cancel() {
            try {
                if (foliaTask != null) {
                    foliaTask.getClass().getMethod("cancel").invoke(foliaTask);
                }

                if (bukkitTask != null) {
                    bukkitTask.cancel();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
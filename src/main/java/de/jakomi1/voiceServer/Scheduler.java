package de.jakomi1.voiceServer;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
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

    public static boolean isFolia() {
        return FOLIA;
    }

    /*
     * =========================
     * MAIN THREAD
     * =========================
     */

    public static void run(Runnable runnable) {
        if (!plugin().isEnabled()) {
            return;
        }

        if (FOLIA) {
            executeFolia(runnable);
            return;
        }

        Bukkit.getScheduler().runTask(
                plugin(),
                runnable
        );
    }

    public static Task runLater(
            Runnable runnable,
            long delayTicks
    ) {
        if (!plugin().isEnabled()) {
            return null;
        }

        if (FOLIA) {
            Object task = runDelayedFolia(
                    runnable,
                    delayTicks
            );

            return new Task(task);
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(
                plugin(),
                runnable,
                delayTicks
        );

        return new Task(task);
    }

    public static Task runTimer(
            Runnable runnable,
            long delayTicks,
            long periodTicks
    ) {
        if (!plugin().isEnabled()) {
            return null;
        }

        if (FOLIA) {
            Object task = runRepeatingFolia(
                    runnable,
                    delayTicks,
                    periodTicks
            );

            return new Task(task);
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(
                plugin(),
                runnable,
                delayTicks,
                periodTicks
        );

        return new Task(task);
    }

    /*
     * =========================
     * ASYNC
     * =========================
     */

    public static void runAsync(Runnable runnable) {
        if (!plugin().isEnabled()) {
            return;
        }

        if (FOLIA) {
            runAsyncFolia(runnable);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(
                plugin(),
                runnable
        );
    }

    public static Task runAsyncLater(
            Runnable runnable,
            long delayTicks
    ) {
        if (!plugin().isEnabled()) {
            return null;
        }

        if (FOLIA) {
            Object task = runDelayedAsyncFolia(
                    runnable,
                    delayTicks
            );

            return new Task(task);
        }

        BukkitTask task = Bukkit.getScheduler()
                .runTaskLaterAsynchronously(
                        plugin(),
                        runnable,
                        delayTicks
                );

        return new Task(task);
    }

    public static Task runAsyncTimer(
            Runnable runnable,
            long delayTicks,
            long periodTicks
    ) {
        if (!plugin().isEnabled()) {
            return null;
        }

        if (FOLIA) {
            Object task = runRepeatingAsyncFolia(
                    runnable,
                    delayTicks,
                    periodTicks
            );

            return new Task(task);
        }

        BukkitTask task = Bukkit.getScheduler()
                .runTaskTimerAsynchronously(
                        plugin(),
                        runnable,
                        delayTicks,
                        periodTicks
                );

        return new Task(task);
    }

    /*
     * =========================
     * FOLIA METHODS
     * =========================
     */

    private static void executeFolia(Runnable runnable) {
        try {
            Object scheduler = Bukkit.class
                    .getMethod("getGlobalRegionScheduler")
                    .invoke(Bukkit.getServer());

            Method execute = scheduler.getClass().getMethod(
                    "execute",
                    Plugin.class,
                    Runnable.class
            );

            execute.invoke(
                    scheduler,
                    plugin(),
                    runnable
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Object runDelayedFolia(
            Runnable runnable,
            long delay
    ) {
        try {
            Object scheduler = Bukkit.class
                    .getMethod("getGlobalRegionScheduler")
                    .invoke(Bukkit.getServer());

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

    private static Object runRepeatingFolia(
            Runnable runnable,
            long delay,
            long period
    ) {
        try {
            Object scheduler = Bukkit.class
                    .getMethod("getGlobalRegionScheduler")
                    .invoke(Bukkit.getServer());

            Method runAtFixedRate = scheduler.getClass().getMethod(
                    "runAtFixedRate",
                    Plugin.class,
                    Consumer.class,
                    long.class,
                    long.class
            );

            return runAtFixedRate.invoke(
                    scheduler,
                    plugin(),
                    (Consumer<Object>) task -> runnable.run(),
                    delay,
                    period
            );

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void runAsyncFolia(Runnable runnable) {
        try {
            Object scheduler = Bukkit.class
                    .getMethod("getAsyncScheduler")
                    .invoke(Bukkit.getServer());

            Method runNow = scheduler.getClass().getMethod(
                    "runNow",
                    Plugin.class,
                    Consumer.class
            );

            runNow.invoke(
                    scheduler,
                    plugin(),
                    (Consumer<Object>) task -> runnable.run()
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Object runDelayedAsyncFolia(
            Runnable runnable,
            long delay
    ) {
        try {
            Object scheduler = Bukkit.class
                    .getMethod("getAsyncScheduler")
                    .invoke(Bukkit.getServer());

            Method runDelayed = scheduler.getClass().getMethod(
                    "runDelayed",
                    Plugin.class,
                    Consumer.class,
                    long.class,
                    TimeUnit.class
            );

            return runDelayed.invoke(
                    scheduler,
                    plugin(),
                    (Consumer<Object>) task -> runnable.run(),
                    delay * 50L,
                    TimeUnit.MILLISECONDS
            );

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Object runRepeatingAsyncFolia(
            Runnable runnable,
            long delay,
            long period
    ) {
        try {
            Object scheduler = Bukkit.class
                    .getMethod("getAsyncScheduler")
                    .invoke(Bukkit.getServer());

            Method runAtFixedRate = scheduler.getClass().getMethod(
                    "runAtFixedRate",
                    Plugin.class,
                    Consumer.class,
                    long.class,
                    long.class,
                    TimeUnit.class
            );

            return runAtFixedRate.invoke(
                    scheduler,
                    plugin(),
                    (Consumer<Object>) task -> runnable.run(),
                    delay * 50L,
                    period * 50L,
                    TimeUnit.MILLISECONDS
            );

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /*
     * =========================
     * TASK WRAPPER
     * =========================
     */

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
                    Method cancel = foliaTask
                            .getClass()
                            .getDeclaredMethod("cancel");

                    cancel.setAccessible(true);
                    cancel.invoke(foliaTask);
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
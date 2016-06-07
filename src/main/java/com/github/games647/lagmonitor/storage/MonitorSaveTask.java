package com.github.games647.lagmonitor.storage;

import com.github.games647.lagmonitor.LagMonitor;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class MonitorSaveTask implements Runnable {

    protected final LagMonitor plugin;

    public MonitorSaveTask(LagMonitor plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        try {
            Storage storage = plugin.getStorage();
            int monitorId = onMonitorSave(storage);
            if (monitorId == -1) {
                //error occurred
                return;
            }

            Map<UUID, WorldData> worldsData = getWorldData();
            if (!storage.saveWorlds(monitorId, worldsData.values())) {
                //error occurred
                return;
            }

            List<PlayerData> playerData = getPlayerData(worldsData);
            storage.savePlayers(monitorId, playerData);
        } catch (ExecutionException | InterruptedException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error saving monitoring data", ex);
        }
    }

    private List<PlayerData> getPlayerData(final Map<UUID, WorldData> worldsData)
            throws InterruptedException, ExecutionException {
        Future<List<PlayerData>> playerFuture = Bukkit.getScheduler()
                .callSyncMethod(plugin, new Callable<List<PlayerData>>() {
                    @Override
                    public List<PlayerData> call() throws Exception {
                        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
                        List<PlayerData> playerData = Lists.newArrayListWithCapacity(onlinePlayers.size());
                        for (Player player : onlinePlayers) {
                            UUID worldId = player.getWorld().getUID();

                            int worldRowId = 0;
                            WorldData worldData = worldsData.get(worldId);
                            if (worldData != null) {
                                worldRowId = worldData.getRowId();
                            }

                            int lastPing = (int) plugin.getPingHistoryTask().getHistory(player).getLastSample();
                            String playerName = player.getName();
                            UUID playerId = player.getUniqueId();
                            playerData.add(new PlayerData(worldRowId, playerId, playerName, lastPing));
                        }

                        return playerData;
                    }
                });
        
        return playerFuture.get();
    }

    private Map<UUID, WorldData> getWorldData()
            throws ExecutionException, InterruptedException {
        //this is not thread-safe and have to run sync
        Future<Map<UUID, WorldData>> worldFuture = Bukkit.getScheduler()
                .callSyncMethod(plugin, new Callable<Map<UUID, WorldData>>() {
                    @Override
                    public Map<UUID, WorldData> call() throws Exception {
                        List<World> worlds = Bukkit.getWorlds();
                        Map<UUID, WorldData> worldsData = Maps.newHashMapWithExpectedSize(worlds.size());
                        for (World world : worlds) {
                            UUID worldId = world.getUID();
                            String worldName = world.getName();
                            int tileEntities = 0;
                            for (Chunk loadedChunk : world.getLoadedChunks()) {
                                tileEntities += loadedChunk.getTileEntities().length;
                            }

                            int entities = world.getEntities().size();
                            int chunks = world.getLoadedChunks().length;

                            WorldData worldData = new WorldData(worldName, chunks, tileEntities, entities);
                            worldsData.put(worldId, worldData);
                        }

                        return worldsData;
                    }
                });

        Map<UUID, WorldData> worldsData = worldFuture.get();
        //this can run async because it's thread-safe
        for (Entry<UUID, WorldData> entry : worldsData.entrySet()) {
            UUID worldId = entry.getKey();
            WorldData worldData = entry.getValue();
            File worldFolder = Bukkit.getWorld(worldId).getWorldFolder();
            worldData.setWorldSize(byteToMega(getFolderSize(worldFolder)));
        }
        
        return worldsData;
    }

    private int onMonitorSave(Storage storage) {
        Runtime runtime = Runtime.getRuntime();
        int maxMemory = byteToMega(runtime.maxMemory());
        int freeRam = byteToMega(runtime.freeMemory());
        float freeRamPct = round(freeRam / maxMemory);

        float systemUsage = 0;
        float procUsage = 0;

        int totalOsMemory = 0;
        int freeOsRam = 0;

        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        float loadAvg = round(osBean.getSystemLoadAverage());
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOsBean = (com.sun.management.OperatingSystemMXBean) osBean;
            systemUsage = round(sunOsBean.getSystemCpuLoad());
            procUsage = round(sunOsBean.getProcessCpuLoad());

            totalOsMemory = byteToMega(sunOsBean.getTotalPhysicalMemorySize());
            freeOsRam = byteToMega(sunOsBean.getFreePhysicalMemorySize());
        }

        float freeOsRamPct = round(freeOsRam / totalOsMemory);
        return storage.saveMonitor(procUsage, systemUsage, freeRam, freeRamPct, freeOsRam, freeOsRamPct, loadAvg);
    }

    private long getFolderSize(File folder) {
        long size = 0;

        for (File file : folder.listFiles()) {
            if (file == null) {
                continue;
            }

            if (file.isFile()) {
                size += file.length();
            } else {
                size += getFolderSize(file);
            }
        }

        return size;
    }

    private int byteToMega(long bytes) {
        return (int) (bytes / (1024 * 1024));
    }

    private float round(double value) {
        //round to 4 decimals -> example: 0.2456
        return Math.round(value * 10000) / 10000;
    }
}
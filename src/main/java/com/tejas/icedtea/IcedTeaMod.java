package com.tejas.icedtea;

import com.tejas.icedtea.config.IcedTeaConfig;
import com.tejas.icedtea.client.IcedTeaHudOverlay;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import com.tejas.icedtea.culling.OcclusionCullingSystem;
import com.tejas.icedtea.util.ThreadPoolManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import com.mojang.brigadier.arguments.StringArgumentType;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class IcedTeaMod implements ClientModInitializer {
    public static final String MOD_ID = "icedtea";
    public static final String MOD_NAME = "IcedTea";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static IcedTeaConfig config;
    private static OcclusionCullingSystem cullingSystem;
    private static boolean debugMode = false;
    private static boolean modEnabled = true;
    private static int preloadTickCounter = 0;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing {}", MOD_NAME);

        config = IcedTeaConfig.load();

        ThreadPoolManager.initialize(config.getThreadCount());

        cullingSystem = new OcclusionCullingSystem(config);

        HudRenderCallback.EVENT.register(new IcedTeaHudOverlay());

        registerCommands();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
        if (!modEnabled) return;
        preloadTickCounter++;
        if (preloadTickCounter >= 20) {
            preloadTickCounter = 0;
            preloadChunks(config.getEntityCullingDistance() > 128 ? 4 : 2);
        }
    });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down {} systems...", MOD_NAME);
            ThreadPoolManager.shutdown();
            config.save();
        }));

        LOGGER.info("{} initialized successfully", MOD_NAME);
    }

    public static void preloadChunks(int radius) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) return;
    ChunkPos center = new ChunkPos(mc.player.blockPosition());
    for (int x = -radius; x <= radius; x++) {
        for (int z = -radius; z <= radius; z++) {
            ChunkPos pos = new ChunkPos(center.x + x, center.z + z);
            mc.level.getChunk(pos.x, pos.z);
        }
    }
}

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("icedtea")
                .then(ClientCommandManager.literal("enable")
                    .executes(context -> {
                        modEnabled = true;
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(
                                Component.literal("[IcedTea] Mod enabled"));
                        }
                        return 1;
                    }))
                .then(ClientCommandManager.literal("disable")
                    .executes(context -> {
                        modEnabled = false;
                        if (cullingSystem != null) {
                            cullingSystem.clearCache();
                        }
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(
                                Component.literal("[IcedTea] Mod disabled"));
                        }
                        return 1;
                    }))
                .then(ClientCommandManager.literal("stats")
                    .executes(context -> {
                        IcedTeaHudOverlay.showStats = !IcedTeaHudOverlay.showStats;
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            String msg = IcedTeaHudOverlay.showStats
                                ? "[IcedTea] Stats HUD enabled (toggle with /icedtea stats)"
                                : "[IcedTea] Stats HUD disabled";
                            mc.player.sendSystemMessage(Component.literal(msg));
                        }
                        return 1;
                    }))
                .then(ClientCommandManager.literal("debug")
                    .executes(context -> {
                        debugMode = !debugMode;
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            String status = debugMode ? "enabled" : "disabled";
                            mc.player.sendSystemMessage(
                                Component.literal("[IcedTea] Debug visualization " + status));
                        }
                        return 1;
                    }))
                    
                .then(ClientCommandManager.literal("help")
                    .executes(context -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(
                "[IcedTea] Usage:\n" +
                "/icedtea enable|disable|reload|stats|debug\n" +
                "/icedtea set <option> <value>\n" +
                "Options:\n" +
                "  occlusionCullingEnabled [true|false]\n" +
                "  undergroundCullingEnabled [true|false]\n" +
                "  occlusionAggressiveness [float]\n" +
                "  occlusionCacheSize [int]\n" +
                "  entityCullingEnabled [true|false]\n" +
                "  entityCullingDistance [double]\n" +
                "  entityLODEnabled [true|false]\n" +
                "  particleCullingEnabled [true|false]\n" +
                "  particleCullingDistance [double]\n" +
                "  maxParticles [int]\n" +
                "  blockEntityCullingEnabled [true|false]\n" +
                "  blockEntityCachingEnabled [true|false]\n" +
                "  threadCount [int]\n" +
                "  performanceOverlayEnabled [true|false]\n" +
                "  showDetailedStats [true|false]\n" +
                "  enhancedFrustumCulling [true|false]\n" +
                "  portalDetection [true|false]\n" +
                "  cacheExpirationTimeMs [long]\n" +
                "  maxRaycastDistance [int]\n" +
                "  maxCacheSize [int]\n" +
                "  cacheValidityMs [long]\n" +
                "  importantParticles [comma,separated,list]\n"
            ));
        }
        return 1;
    }))
                .then(ClientCommandManager.literal("reload")
                    .executes(context -> {
                        config = IcedTeaConfig.load();
                        cullingSystem.updateConfig(config);
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(
                                Component.literal("[IcedTea] Configuration reloaded"));
                        }
                        return 1;
                    }))
.then(ClientCommandManager.literal("set")
    .then(ClientCommandManager.argument("option", StringArgumentType.word())
        .then(ClientCommandManager.argument("value", StringArgumentType.word())
            .executes(context -> {
                String option = StringArgumentType.getString(context, "option");
                String value = StringArgumentType.getString(context, "value");
                IcedTeaConfig cfg = getConfig();
                Minecraft mc = Minecraft.getInstance();
                boolean success = false;
                String msg = "";

                try {
                    switch (option.toLowerCase()) {
                        case "occlusioncullingenabled":
                            cfg.setOcclusionCullingEnabled(Boolean.parseBoolean(value));
                            success = true; break;
                        case "undergroundcullingenabled":
                            cfg.setUndergroundCullingEnabled(Boolean.parseBoolean(value));
                            success = true; break;
                        case "occlusionaggressiveness":
                            cfg.setOcclusionAggressiveness(Float.parseFloat(value));
                            success = true; break;
                        case "occlusioncachesize":
                            cfg.setOcclusionCacheSize(Integer.parseInt(value));
                            success = true; break;
                        case "entitycullingenabled":
                            cfg.setEntityCullingEnabled(Boolean.parseBoolean(value));
                            success = true; break;
                        case "entitycullingdistance":
                            cfg.setEntityCullingDistance(Double.parseDouble(value));
                            success = true; break;
                        case "entitylodenabled":
                            cfg.setEntityLODEnabled(Boolean.parseBoolean(value));
                            success = true; break;
                        case "particlecullingenabled":
                            cfg.setParticleCullingEnabled(Boolean.parseBoolean(value));
                            success = true; break;
                        case "particlecullingdistance":
                            cfg.setParticleCullingDistance(Double.parseDouble(value));
                            success = true; break;
                        case "maxparticles":
                            cfg.setMaxParticles(Integer.parseInt(value));
                            success = true; break;
                        case "blockentitycullingenabled":
                            cfg.setBlockEntityCullingEnabled(Boolean.parseBoolean(value));
                            success = true; break;
                        case "blockentitycachingenabled":
                            cfg.setBlockEntityCachingEnabled(Boolean.parseBoolean(value));
                            success = true; break;
                        case "threadcount":
                            cfg.setThreadCount(Integer.parseInt(value));
                            success = true; break;
                        case "performanceoverlayenabled":
                            cfg.setPerformanceOverlayEnabled(Boolean.parseBoolean(value));
                            success = true; break;
                        case "showdetailedstats":
                            cfg.setShowDetailedStats(Boolean.parseBoolean(value));
                            success = true; break;
                        case "enhancedfrustumculling":
                            cfg.setEnhancedFrustumCulling(Boolean.parseBoolean(value));
                            success = true; break;
                        case "portaldetection":
                            cfg.setPortalDetection(Boolean.parseBoolean(value));
                            success = true; break;
                        case "cacheexpirationtimems":
                            cfg.setCacheExpirationTimeMs(Long.parseLong(value));
                            success = true; break;
                        case "maxraycastdistance":
                            cfg.setMaxRaycastDistance(Integer.parseInt(value));
                            success = true; break;
                        case "maxcachesize":
                            cfg.setMaxCacheSize(Integer.parseInt(value));
                            success = true; break;
                        case "importantparticles":
                            List<String> particles = Arrays.asList(value.split(","));
                            cfg.setImportantParticles(particles);
                            success = true; break;
                        case "cachevalidityms":
                            cfg.setCacheValidityMs(Long.parseLong(value));
                            success = true; break;
                        default:
                            msg = "[IcedTea] Unknown config option: " + option;
                    }
                    if (success) {
                        cfg.save();
                        msg = "[IcedTea] Set " + option + " to " + value + ". Use /icedtea reload to apply.";
                    }
                } catch (Exception e) {
                    msg = "[IcedTea] Error setting " + option + ": " + e.getMessage();
                }
                if (mc.player != null) mc.player.sendSystemMessage(Component.literal(msg));
                return success ? 1 : 0;
            })
        )
    )
));
        });
    }

    public static IcedTeaConfig getConfig() {
        return config;
    }

    public static OcclusionCullingSystem getCullingSystem() {
        return cullingSystem;
    }

    public static boolean isDebugMode() {
        return debugMode;
    }

    public static boolean isModEnabled() {
        return modEnabled;
    }
}
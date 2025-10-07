package com.tejas.icedtea.client;

import com.tejas.icedtea.IcedTeaMod;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class IcedTeaHudOverlay implements HudRenderCallback {
    public static boolean showStats = false;

    private static final AtomicInteger totalChunksLoaded = new AtomicInteger(0);
    private static final AtomicInteger chunksRendered = new AtomicInteger(0);
    private static final AtomicInteger totalEntities = new AtomicInteger(0);
    private static final AtomicInteger entitiesRendered = new AtomicInteger(0);
    private static final AtomicInteger totalParticles = new AtomicInteger(0);
    private static final AtomicInteger particlesRendered = new AtomicInteger(0);

    private static final AtomicLong frameTimeSum = new AtomicLong(0);
    private static final AtomicInteger frameCount = new AtomicInteger(0);

    public static void updateChunkStats(int total, int rendered) {
        totalChunksLoaded.set(total);
        chunksRendered.set(rendered);
    }

    public static void updateEntityStats(int total, int rendered) {
        totalEntities.set(total);
        entitiesRendered.set(rendered);
    }

    public static void updateParticleStats(int total, int rendered) {
        totalParticles.set(total);
        particlesRendered.set(rendered);
    }

    public static void recordFrameTime(long nanos) {
        frameTimeSum.addAndGet(nanos);
        frameCount.incrementAndGet();
    }

    public static void reset() {
        frameTimeSum.set(0);
        frameCount.set(0);
    }

    private static int getCurrentFPS() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getFps();
    }

    private static double getAverageFrameTime() {
        int count = frameCount.get();
        if (count == 0) return 0.0;
        return (frameTimeSum.get() / (double) count) / 1_000_000.0;
    }

    private static double getChunkCullingEfficiency() {
        int total = totalChunksLoaded.get();
        if (total == 0) return 0.0;
        int culled = total - chunksRendered.get();
        return (culled * 100.0) / total;
    }

    private static double getEntityCullingEfficiency() {
        int total = totalEntities.get();
        if (total == 0) return 0.0;
        int culled = total - entitiesRendered.get();
        return (culled * 100.0) / total;
    }

    private static double getParticleCullingEfficiency() {
        int total = totalParticles.get();
        if (total == 0) return 0.0;
        int culled = total - particlesRendered.get();
        return (culled * 100.0) / total;
    }

    private static String getStatsString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("FPS: %d\n", getCurrentFPS()));
        sb.append(String.format("Avg Frame Time: %.2f ms\n", getAverageFrameTime()));
        sb.append(String.format("Chunks: %d / %d (%.1f%% culled)\n",
                chunksRendered.get(), totalChunksLoaded.get(), getChunkCullingEfficiency()));
        sb.append(String.format("Entities: %d / %d (%.1f%% culled)\n",
                entitiesRendered.get(), totalEntities.get(), getEntityCullingEfficiency()));
        sb.append(String.format("Particles: %d / %d (%.1f%% culled)\n",
                particlesRendered.get(), totalParticles.get(), getParticleCullingEfficiency()));
        return sb.toString();
    }
    @Override
    public void onHudRender(GuiGraphics graphics, float tickDelta) {
        if (!showStats) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        String[] lines = getStatsString().split("\n");
        int x = 10;
        int y = 10;
        int padding = 6;

        String watermark = "IcedTea";
        int half = watermark.length() / 2;
        String firstHalf = watermark.substring(0, half);
        String secondHalf = watermark.substring(half);

        int watermarkX = x;
        int watermarkY = y - font.lineHeight;

        graphics.drawString(font, firstHalf, watermarkX, watermarkY, 0xFFFF00, true);

        int firstHalfWidth = font.width(firstHalf);

        graphics.drawString(font, secondHalf, watermarkX + firstHalfWidth, watermarkY, 0xFFFFFF, true);

        int yOffset = y + padding;
        for (String line : lines) {
            graphics.drawString(font, line, x + padding, yOffset, 0xFFFFFF, false);
            yOffset += font.lineHeight;
        }
    }
}
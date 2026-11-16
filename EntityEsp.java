package dev.lumen.visuals;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Entity ESP «заливка»: сквозь стены, у людей собирается из частей модели (голова, торс, руки, ноги, с шагом),
 * у остальных — из хитбокса. Видимые грани заливаются полупрозрачным цветом, рёбра светятся.
 * Всё уходит в один пакет треугольников.
 */
public final class EntityEsp {
    private static final int MAX = 64;
    private static final int[][] FACES = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};
    private static final int[][] EDGES = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    private static final class Item {
        LivingEntity e;
        double px, py, pz, distSq;
        int color;
    }

    private static final List<Item> LIST = new ArrayList<>();
    private static final double[] WX = new double[8], WY = new double[8], WZ = new double[8];
    private static final float[] SX = new float[8], SY = new float[8];

    private EntityEsp() {}

    static void begin() {
        LIST.clear();
    }

    static void add(LivingEntity e, double px, double py, double pz, double distSq, int color) {
        Item it = new Item();
        it.e = e;
        it.px = px;
        it.py = py;
        it.pz = pz;
        it.distSq = distSq;
        it.color = color;
        LIST.add(it);
    }

    static void flush(DrawContext c, LumenConfig cfg, int sw, int sh) {
        if (LIST.isEmpty()) return;
        if (LIST.size() > MAX) {
            LIST.sort(Comparator.comparingDouble((Item i) -> i.distSq));
            while (LIST.size() > MAX) LIST.remove(LIST.size() - 1);
        }
        LIST.sort(Comparator.comparingDouble((Item i) -> -i.distSq)); // дальние первыми, ближние поверх

        Matrix4f m = c.getMatrices().peek().getPositionMatrix();
        Draw2D.beginTris(c);
        for (Item it : LIST) draw(m, it, cfg, sw, sh);
        Draw2D.endTris();
    }

    private static void draw(Matrix4f m, Item it, LumenConfig cfg, int sw, int sh) {
        LivingEntity e = it.e;
        float td = VisualsRenderer.tickDelta;
        int rgb = it.color & 0xFFFFFF;
        float dist = (float) Math.sqrt(it.distSq);
        float fade = MathHelper.clamp(1.2f - dist / Math.max(16f, cfg.range), 0.3f, 1f);
        int fill = (Math.min(255, (int) (255 * cfg.espFillAlpha / 100f * fade)) << 24) | rgb;
        int line = (Math.min(255, (int) (255 * 0.95f * fade)) << 24) | rgb;
        int glow = (Math.min(255, (int) (255 * 0.16f * fade)) << 24) | rgb;
        float lw = cfg.espLine * 0.5f;
        boolean doGlow = cfg.espGlow;

        double bodyYaw = Math.toRadians(MathHelper.lerp(td, e.prevBodyYaw, e.bodyYaw));
        boolean humanoid = e instanceof PlayerEntity || e instanceof ZombieEntity || e instanceof AbstractSkeletonEntity;

        if (!humanoid) {
            float w = e.getWidth(), h = e.getHeight();
            box(m, it, bodyYaw, 1f, 0f, h / 2f, 0f, w / 2f, h / 2f, w / 2f, 0f, 0f, 0f, fill, line, glow, lw, doGlow, sw, sh);
            return;
        }

        float k = e.getHeight() / 1.8f;
        float limbPos = e.limbAnimator.getPos(td) * 0.6662f;
        float limbSpd = Math.min(1f, e.limbAnimator.getSpeed(td));
        float legA = MathHelper.cos(limbPos) * 1.4f * limbSpd;
        float armA = MathHelper.cos(limbPos + (float) Math.PI) * limbSpd;
        double headYaw = Math.toRadians(MathHelper.lerp(td, e.prevHeadYaw, e.headYaw));

        // единицы модели: 1.8 блока = 32 «пикселя» (1px = 0.05625)
        box(m, it, bodyYaw, k, 0f, 1.0125f, 0f, 0.225f, 0.3375f, 0.1125f, 0f, 0f, 0f, fill, line, glow, lw, doGlow, sw, sh);          // торс
        box(m, it, headYaw, k, 0f, 1.575f, 0f, 0.225f, 0.225f, 0.225f, 0f, 0f, 0f, fill, line, glow, lw, doGlow, sw, sh);             // голова
        box(m, it, bodyYaw, k, 0.3375f, 1.0125f, 0f, 0.1125f, 0.3375f, 0.1125f, 1.35f, 0f, armA, fill, line, glow, lw, doGlow, sw, sh);   // рука
        box(m, it, bodyYaw, k, -0.3375f, 1.0125f, 0f, 0.1125f, 0.3375f, 0.1125f, 1.35f, 0f, -armA, fill, line, glow, lw, doGlow, sw, sh); // рука
        box(m, it, bodyYaw, k, 0.1125f, 0.3375f, 0f, 0.1125f, 0.3375f, 0.1125f, 0.675f, 0f, legA, fill, line, glow, lw, doGlow, sw, sh);  // нога
        box(m, it, bodyYaw, k, -0.1125f, 0.3375f, 0f, 0.1125f, 0.3375f, 0.1125f, 0.675f, 0f, -legA, fill, line, glow, lw, doGlow, sw, sh); // нога
    }

    private static void box(Matrix4f m, Item it, double yaw, float k,
                            float cx, float cy, float cz, float hx, float hy, float hz,
                            float pivY, float pivZ, float swing,
                            int fill, int line, int glow, float lw, boolean doGlow, int sw, int sh) {
        double cs = Math.cos(yaw), sn = Math.sin(yaw);
        float sc = 1f, ss = 0f;
        if (swing != 0f) {
            sc = MathHelper.cos(swing);
            ss = MathHelper.sin(swing);
        }
        double mx = 0, my = 0, mz = 0;
        for (int i = 0; i < 8; i++) {
            float lx = cx + ((i & 1) == 0 ? -hx : hx);
            float ly = cy + ((i & 2) == 0 ? -hy : hy);
            float lz = cz + ((i & 4) == 0 ? -hz : hz);
            if (swing != 0f) {
                float dy = ly - pivY, dz = lz - pivZ;
                ly = pivY + dy * sc - dz * ss;
                lz = pivZ + dy * ss + dz * sc;
            }
            lx *= k;
            ly *= k;
            lz *= k;
            WX[i] = it.px + lx * cs - lz * sn;
            WY[i] = it.py + ly;
            WZ[i] = it.pz + lx * sn + lz * cs;
            if (!VisualsRenderer.project(WX[i], WY[i], WZ[i], sw, sh)) return;
            SX[i] = VisualsRenderer.SCR[0];
            SY[i] = VisualsRenderer.SCR[1];
            mx += WX[i];
            my += WY[i];
            mz += WZ[i];
        }
        mx /= 8;
        my /= 8;
        mz /= 8;

        // заливка: только грани, повёрнутые к камере
        for (int f = 0; f < 6; f++) {
            int[] q = FACES[f];
            double fx = (WX[q[0]] + WX[q[1]] + WX[q[2]] + WX[q[3]]) / 4;
            double fy = (WY[q[0]] + WY[q[1]] + WY[q[2]] + WY[q[3]]) / 4;
            double fz = (WZ[q[0]] + WZ[q[1]] + WZ[q[2]] + WZ[q[3]]) / 4;
            double dot = (fx - mx) * (VisualsRenderer.camX - fx) + (fy - my) * (VisualsRenderer.camY - fy) + (fz - mz) * (VisualsRenderer.camZ - fz);
            if (dot > 0) {
                Draw2D.quad(m, SX[q[0]], SY[q[0]], SX[q[1]], SY[q[1]], SX[q[2]], SY[q[2]], SX[q[3]], SY[q[3]], fill);
            }
        }
        // рёбра
        for (int[] ed : EDGES) {
            if (doGlow) Draw2D.line(m, SX[ed[0]], SY[ed[0]], SX[ed[1]], SY[ed[1]], lw * 3f, glow);
            Draw2D.line(m, SX[ed[0]], SY[ed[0]], SX[ed[1]], SY[ed[1]], lw, line);
        }
    }
}

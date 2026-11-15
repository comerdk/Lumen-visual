package dev.lumen.visuals;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Собственные частицы (не из Minecraft): плавные звёзды, круглый снег, дым.
 * Живут в мировых координатах вокруг камеры, проецируются на экран и рисуются одним пакетом на тип.
 */
public final class FxParticles {
    public static final Identifier TEX_STAR = Identifier.of("lumenvisuals", "textures/particle/star.png");
    public static final Identifier TEX_SNOW = Identifier.of("lumenvisuals", "textures/particle/snow.png");
    public static final Identifier TEX_GLOW = Identifier.of("lumenvisuals", "textures/particle/glow.png");

    static final byte SNOW = 0, STAR = 1, SMOKE = 2, TRAIL = 3;
    private static final int CAP = 1100;
    private static final double[] X = new double[CAP], Y = new double[CAP], Z = new double[CAP];
    private static final float[] VX = new float[CAP], VY = new float[CAP], VZ = new float[CAP];
    private static final float[] AGE = new float[CAP], LIFE = new float[CAP], SIZE = new float[CAP];
    private static final float[] ROT = new float[CAP], ROTV = new float[CAP], SEED = new float[CAP];
    private static final int[] COL = new int[CAP];
    private static final byte[] TYPE = new byte[CAP];
    private static int n;
    private static final Random R = new Random();

    private FxParticles() {}

    private static float rnd(float a, float b) {
        return a + R.nextFloat() * (b - a);
    }

    private static void copy(int from, int to) {
        X[to] = X[from]; Y[to] = Y[from]; Z[to] = Z[from];
        VX[to] = VX[from]; VY[to] = VY[from]; VZ[to] = VZ[from];
        AGE[to] = AGE[from]; LIFE[to] = LIFE[from]; SIZE[to] = SIZE[from];
        ROT[to] = ROT[from]; ROTV[to] = ROTV[from]; SEED[to] = SEED[from];
        COL[to] = COL[from]; TYPE[to] = TYPE[from];
    }

    private static int add(byte type, double x, double y, double z, float vx, float vy, float vz, float size, float life, int rgb) {
        if (n >= CAP) return -1;
        int i = n++;
        TYPE[i] = type;
        X[i] = x; Y[i] = y; Z[i] = z;
        VX[i] = vx; VY[i] = vy; VZ[i] = vz;
        SIZE[i] = size; LIFE[i] = life; AGE[i] = 0f;
        ROT[i] = rnd(0f, 6.28f); ROTV[i] = rnd(-0.8f, 0.8f); SEED[i] = rnd(0f, 100f);
        COL[i] = rgb;
        return i;
    }

    /** Для косметики: шлейф из звёзд. */
    public static void spawnTrail(double x, double y, double z, int rgb, float size) {
        add(TRAIL, x, y, z, rnd(-0.12f, 0.12f), rnd(0.02f, 0.2f), rnd(-0.12f, 0.12f), size, rnd(0.7f, 1.1f), rgb);
    }

    public static void clear() {
        n = 0;
    }

    // ---------- обновление ----------
    public static void update(float dt, LumenConfig cfg) {
        double cx = VisualsRenderer.camX, cy = VisualsRenderer.camY, cz = VisualsRenderer.camZ;
        float spd = cfg.particleSpeed / 100f;
        float step = dt * spd;
        int snow = 0, star = 0, smoke = 0;

        for (int i = 0; i < n; ) {
            byte t = TYPE[i];
            AGE[i] += dt;
            ROT[i] += ROTV[i] * dt;
            boolean dead = false;
            double dx = X[i] - cx, dz = Z[i] - cz;
            switch (t) {
                case SNOW -> {
                    X[i] += (VX[i] + Math.sin(AGE[i] * 1.3 + SEED[i]) * 0.28) * step;
                    Z[i] += (VZ[i] + Math.cos(AGE[i] * 1.1 + SEED[i]) * 0.28) * step;
                    Y[i] += VY[i] * step;
                    dead = Y[i] < cy - 7.0 || dx * dx + dz * dz > 30.0 * 30.0;
                    if (!dead) snow++;
                }
                case STAR -> {
                    X[i] += (VX[i] + Math.sin(AGE[i] * 0.7 + SEED[i]) * 0.12) * step;
                    Y[i] += (VY[i] + Math.cos(AGE[i] * 0.6 + SEED[i]) * 0.1) * step;
                    Z[i] += VZ[i] * step;
                    dead = AGE[i] > LIFE[i] || dx * dx + dz * dz > 30.0 * 30.0;
                    if (!dead) star++;
                }
                case SMOKE -> {
                    X[i] += VX[i] * step;
                    Y[i] += VY[i] * step;
                    Z[i] += VZ[i] * step;
                    dead = AGE[i] > LIFE[i];
                    if (!dead) smoke++;
                }
                default -> {
                    X[i] += VX[i] * dt;
                    Y[i] += VY[i] * dt;
                    Z[i] += VZ[i] * dt;
                    dead = AGE[i] > LIFE[i];
                }
            }
            if (dead) {
                n--;
                if (i != n) copy(n, i);
            } else {
                i++;
            }
        }

        float sz = cfg.snowSize / 100f;
        boolean bigDeficit = cfg.snowCount - snow > 12;
        for (int k = snow; k < cfg.snowCount; k++) {
            double ang = R.nextDouble() * Math.PI * 2, rad = Math.sqrt(R.nextDouble()) * 22.0;
            double y = bigDeficit ? cy + rnd(-6f, 11f) : cy + rnd(8f, 12f);
            if (add(SNOW, cx + Math.cos(ang) * rad, y, cz + Math.sin(ang) * rad,
                    rnd(-0.08f, 0.08f), -rnd(0.7f, 1.5f), rnd(-0.08f, 0.08f), rnd(0.035f, 0.075f) * sz, 1e9f, 0xFFFFFF) < 0) break;
        }

        float[] hsv = {cfg.themeH / 360f};
        for (int k = star; k < cfg.starCount; k++) {
            double ang = R.nextDouble() * Math.PI * 2, rad = 3.0 + R.nextDouble() * 16.0;
            float hue = hsv[0] + rnd(-0.10f, 0.10f);
            int rgb = MathHelper.hsvToRgb(hue < 0 ? hue + 1f : hue % 1f, rnd(0.25f, 0.7f), 1f) & 0xFFFFFF;
            if (add(STAR, cx + Math.cos(ang) * rad, cy + rnd(-2f, 7f), cz + Math.sin(ang) * rad,
                    rnd(-0.15f, 0.15f), rnd(-0.05f, 0.1f), rnd(-0.15f, 0.15f), rnd(0.09f, 0.22f), rnd(5f, 12f), rgb) < 0) break;
        }

        int rgbSmoke = Gfx.mixRgb(cfg.themeDark, 0xFFFFFF, 0.35f);
        for (int k = smoke; k < cfg.smokeCount * 3; k++) {
            double ang = R.nextDouble() * Math.PI * 2, rad = Math.sqrt(R.nextDouble()) * 14.0;
            if (add(SMOKE, cx + Math.cos(ang) * rad, cy - 1.6 + rnd(0f, 1.6f), cz + Math.sin(ang) * rad,
                    rnd(-0.05f, 0.05f), rnd(0.12f, 0.3f), rnd(-0.05f, 0.05f), rnd(0.9f, 1.8f), rnd(5f, 9f), rgbSmoke) < 0) break;
        }
    }

    // ---------- отрисовка ----------
    public static void render(DrawContext c, LumenConfig cfg, int sw, int sh) {
        if (n == 0 || !VisualsRenderer.ready) return;
        float focal = VisualsRenderer.focalPx(sw);
        Matrix4f m = c.getMatrices().peek().getPositionMatrix();

        pass(c, m, TEX_GLOW, false, SMOKE, focal, sw, sh);
        pass(c, m, TEX_SNOW, false, SNOW, focal, sw, sh);
        pass(c, m, TEX_STAR, true, STAR, focal, sw, sh);
        pass(c, m, TEX_STAR, true, TRAIL, focal, sw, sh);
    }

    private static void pass(DrawContext c, Matrix4f m, Identifier tex, boolean additive, byte type, float focal, int sw, int sh) {
        boolean any = false;
        for (int i = 0; i < n; i++) if (TYPE[i] == type) { any = true; break; }
        if (!any) return;

        Draw2D.beginTex(c, tex, additive);
        for (int i = 0; i < n; i++) {
            if (TYPE[i] != type) continue;
            if (!VisualsRenderer.project(X[i], Y[i], Z[i], sw, sh)) continue;
            float depth = VisualsRenderer.lastDepth;
            if (depth < 0.4f) continue;
            float sx = VisualsRenderer.SCR[0], sy = VisualsRenderer.SCR[1];
            if (sx < -40 || sy < -40 || sx > sw + 40 || sy > sh + 40) continue;

            float r = focal * SIZE[i] / depth;
            float a;
            switch (type) {
                case SNOW -> {
                    r = MathHelper.clamp(r, 0.7f, 9f);
                    a = 0.85f * MathHelper.clamp(1.25f - depth / 26f, 0.15f, 1f);
                }
                case STAR -> {
                    r = MathHelper.clamp(r * 2.4f, 2f, 28f);
                    float life = AGE[i] / LIFE[i];
                    float fade = Math.min(1f, Math.min(life * 5f, (1f - life) * 5f));
                    a = Math.max(0f, fade) * (0.55f + 0.45f * (float) Math.sin(AGE[i] * 5.0 + SEED[i])) * 0.95f;
                }
                case SMOKE -> {
                    float life = AGE[i] / LIFE[i];
                    r = MathHelper.clamp(r * (1f + life), 6f, 260f);
                    a = Math.min(1f, Math.min(life * 4f, (1f - life) * 2f)) * 0.10f;
                }
                default -> {
                    float life = AGE[i] / LIFE[i];
                    r = MathHelper.clamp(r * 2.2f * (1f - life * 0.5f), 1.5f, 22f);
                    a = (1f - life) * 0.9f;
                }
            }
            if (a <= 0.01f) continue;
            int argb = (Math.min(255, (int) (a * 255)) << 24) | (COL[i] & 0xFFFFFF);
            Draw2D.sprite(m, sx, sy, r, ROT[i], argb);
        }
        Draw2D.endTex();
    }
}

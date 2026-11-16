package dev.lumen.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

/**
 * Графика «liquid glass»: сглаженные скруглённые прямоугольники, кольца-обводки, свечение.
 * Фигуры рисуются в физических пикселях (ps = пикселей экрана на 1 локальную единицу), с антиалиасингом углов.
 */
public final class Gfx {
    private Gfx() {}

    public static double sf() {
        return MinecraftClient.getInstance().getWindow().getScaleFactor();
    }

    public static int ringPx(double ps) {
        return Math.max(1, (int) Math.round(ps * 0.5));
    }

    // ---------- цвета ----------
    public static int alpha(int argb, float k) {
        int a = (int) (((argb >>> 24) & 0xFF) * k);
        a = Math.max(0, Math.min(255, a));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int withA(int rgb, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    private static int ch(int a, int b, int shift, float t) {
        int x = (a >>> shift) & 255, y = (b >>> shift) & 255;
        return Math.max(0, Math.min(255, Math.round(x + (y - x) * t)));
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return (ch(a, b, 24, t) << 24) | (ch(a, b, 16, t) << 16) | (ch(a, b, 8, t) << 8) | ch(a, b, 0, t);
    }

    /** Смешивание RGB (без альфы). */
    public static int mixRgb(int a, int b, float t) {
        return lerp(0xFF000000 | a, 0xFF000000 | b, t) & 0xFFFFFF;
    }

    public static int mulRgb(int rgb, float k) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 255) * k));
        int g = Math.min(255, Math.round(((rgb >> 8) & 255) * k));
        int b = Math.min(255, Math.round((rgb & 255) * k));
        return (r << 16) | (g << 8) | b;
    }

    public static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0f) h = 0f;
        else if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h /= 6f;
        if (h < 0f) h += 1f;
        return new float[]{h, max == 0f ? 0f : d / max, max};
    }

    /** Меняет насыщенность цвета в factor раз (1 = без изменений). */
    public static int saturate(int rgb, float factor) {
        if (Math.abs(factor - 1f) < 0.01f) return rgb & 0xFFFFFF;
        float[] hsv = rgbToHsv(rgb);
        float s = Math.max(0f, Math.min(1f, hsv[1] * factor));
        return MathHelper.hsvToRgb(hsv[0], s, hsv[2]) & 0xFFFFFF;
    }

    // ---------- скруглённый прямоугольник ----------
    private static double inset(int i, int R) {
        if (i >= R) return 0.0;
        double dy = R - i - 0.5;
        return R - Math.sqrt(Math.max(0.0, (double) R * R - dy * dy));
    }

    public static void rrect(DrawContext c, float x, float y, float w, float h, float r, int top, int bottom, double ps) {
        int X = Math.round((float) (x * ps)), Y = Math.round((float) (y * ps));
        int W = Math.round((float) (w * ps)), H = Math.round((float) (h * ps));
        if (W <= 0 || H <= 0) return;
        int R = Math.min(Math.round((float) (r * ps)), Math.min(W, H) / 2);
        MatrixStack m = c.getMatrices();
        m.push();
        m.scale((float) (1.0 / ps), (float) (1.0 / ps), 1f);
        if (R <= 0) {
            c.fillGradient(X, Y, X + W, Y + H, top, bottom);
        } else {
            if (H - 2 * R > 0) {
                c.fillGradient(X, Y + R, X + W, Y + H - R,
                        lerp(top, bottom, (float) R / H), lerp(top, bottom, (float) (H - R) / H));
            }
            for (int i = 0; i < R; i++) {
                double in = inset(i, R);
                int il = (int) Math.ceil(in);
                float cov = (float) (il - in);
                span(c, X, Y + i, W, il, cov, lerp(top, bottom, (i + 0.5f) / H));
                span(c, X, Y + H - 1 - i, W, il, cov, lerp(top, bottom, (H - i - 0.5f) / H));
            }
        }
        m.pop();
    }

    private static void span(DrawContext c, int X, int y, int W, int il, float cov, int col) {
        if (X + il < X + W - il) c.fill(X + il, y, X + W - il, y + 1, col);
        if (cov > 0.03f && il >= 1) {
            int ec = alpha(col, cov);
            if ((ec >>> 24) > 0) {
                c.fill(X + il - 1, y, X + il, y + 1, ec);
                c.fill(X + W - il, y, X + W - il + 1, y + 1, ec);
            }
        }
    }

    // ---------- обводка (кольцо) ----------
    public static void ring(DrawContext c, float x, float y, float w, float h, float r, int t, int color, double ps) {
        int X = Math.round((float) (x * ps)), Y = Math.round((float) (y * ps));
        int W = Math.round((float) (w * ps)), H = Math.round((float) (h * ps));
        if (W <= 0 || H <= 0 || (color >>> 24) == 0) return;
        int R = Math.min(Math.round((float) (r * ps)), Math.min(W, H) / 2);
        t = Math.max(1, t);
        MatrixStack m = c.getMatrices();
        m.push();
        m.scale((float) (1.0 / ps), (float) (1.0 / ps), 1f);
        if (R <= 0) {
            c.fill(X, Y, X + W, Y + t, color);
            c.fill(X, Y + H - t, X + W, Y + H, color);
            c.fill(X, Y + t, X + t, Y + H - t, color);
            c.fill(X + W - t, Y + t, X + W, Y + H - t, color);
        } else {
            int ri = R - t;
            for (int i = 0; i < R; i++) {
                double out = inset(i, R);
                double inn = (i < t) ? -1.0 : t + inset(i - t, ri);
                ringRow(c, X, Y + i, W, out, inn, color);
                ringRow(c, X, Y + H - 1 - i, W, out, inn, color);
            }
            if (H - 2 * R > 0) {
                c.fill(X, Y + R, X + t, Y + H - R, color);
                c.fill(X + W - t, Y + R, X + W, Y + H - R, color);
            }
        }
        m.pop();
    }

    private static void ringRow(DrawContext c, int X, int y, int W, double out, double inn, int color) {
        int lo = (int) Math.ceil(out);
        float cov = (float) (lo - out);
        if (inn < 0) {
            if (X + lo < X + W - lo) c.fill(X + lo, y, X + W - lo, y + 1, color);
        } else {
            int li = (int) Math.floor(inn);
            if (li > lo) {
                c.fill(X + lo, y, X + li, y + 1, color);
                c.fill(X + W - li, y, X + W - lo, y + 1, color);
            }
        }
        if (cov > 0.03f && lo >= 1) {
            int ec = alpha(color, cov);
            if ((ec >>> 24) > 0) {
                c.fill(X + lo - 1, y, X + lo, y + 1, ec);
                c.fill(X + W - lo, y, X + W - lo + 1, y + 1, ec);
            }
        }
    }

    // ---------- мягкое свечение (круги в локальных единицах) ----------
    public static void disc(DrawContext c, int cx, int cy, int r, int color) {
        for (int dy = -r; dy < r; dy++) {
            double yy = dy + 0.5;
            int dx = (int) Math.sqrt(Math.max(0.0, (double) r * r - yy * yy));
            if (dx > 0) c.fill(cx - dx, cy + dy, cx + dx, cy + dy + 1, color);
        }
    }

    public static void glow(DrawContext c, int cx, int cy, int r, int rgb, int a) {
        for (int i = 0; i < 4; i++) {
            disc(c, cx, cy, Math.max(1, (int) (r * (1f - i * 0.23f))), withA(rgb, a));
        }
    }
}

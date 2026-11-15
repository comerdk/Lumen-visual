package dev.lumen.visuals;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * Быстрая пакетная отрисовка в HUD: цветные треугольники (заливка ESP, линии) и текстурные квады (частицы).
 * Один draw call на пакет, буфер создаётся лениво (только если что-то нарисовано).
 */
public final class Draw2D {
    private static DrawContext ctx;
    private static BufferBuilder buf;
    private static boolean texMode;
    private static Identifier texId;
    private static boolean additive;
    private static boolean open;

    private Draw2D() {}

    // ---------- цветные треугольники ----------
    public static void beginTris(DrawContext c) {
        ctx = c;
        texMode = false;
        open = true;
        buf = null;
    }

    public static void endTris() {
        flush();
    }

    // ---------- текстурные квады ----------
    public static void beginTex(DrawContext c, Identifier tex, boolean add) {
        ctx = c;
        texMode = true;
        texId = tex;
        additive = add;
        open = true;
        buf = null;
    }

    public static void endTex() {
        flush();
    }

    private static void ensure() {
        if (buf != null) return;
        ctx.draw();
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        if (texMode) {
            if (additive) RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            else RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
            RenderSystem.setShaderTexture(0, texId);
            buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        } else {
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        }
    }

    private static void flush() {
        if (!open) return;
        open = false;
        if (buf != null) {
            BufferRenderer.drawWithGlobalProgram(buf.end());
            buf = null;
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableCull();
        }
    }

    // ---------- примитивы ----------
    public static void tri(Matrix4f m, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        ensure();
        buf.vertex(m, x1, y1, 0f).color(argb);
        buf.vertex(m, x2, y2, 0f).color(argb);
        buf.vertex(m, x3, y3, 0f).color(argb);
    }

    public static void quad(Matrix4f m, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int argb) {
        tri(m, x1, y1, x2, y2, x3, y3, argb);
        tri(m, x1, y1, x3, y3, x4, y4, argb);
    }

    /** Линия толщиной w (в единицах GUI) как повёрнутый прямоугольник. */
    public static void line(Matrix4f m, float x1, float y1, float x2, float y2, float w, int argb) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) return;
        float nx = -dy / len * w * 0.5f, ny = dx / len * w * 0.5f;
        quad(m, x1 + nx, y1 + ny, x2 + nx, y2 + ny, x2 - nx, y2 - ny, x1 - nx, y1 - ny, argb);
    }

    /** Текстурный квад с центром (cx, cy), «радиусом» r и поворотом rot (рад). Нужно вызывать между beginTex/endTex. */
    public static void sprite(Matrix4f m, float cx, float cy, float r, float rot, int argb) {
        ensure();
        float c = (float) Math.cos(rot) * r, s = (float) Math.sin(rot) * r;
        buf.vertex(m, cx - c + s, cy - s - c, 0f).texture(0f, 0f).color(argb);
        buf.vertex(m, cx + c + s, cy + s - c, 0f).texture(1f, 0f).color(argb);
        buf.vertex(m, cx + c - s, cy + s + c, 0f).texture(1f, 1f).color(argb);
        buf.vertex(m, cx - c - s, cy - s + c, 0f).texture(0f, 1f).color(argb);
    }
}

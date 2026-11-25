package dev.lumen.visuals;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;

/** Procedural sky Moonlit Night — базис камеры из rotation камеры MC. */
public final class MoonlitSky {
    private static final Identifier VERT = Identifier.of("lumenvisuals", "shaders/sky/moonlit.vert");
    private static final Identifier FRAG = Identifier.of("lumenvisuals", "shaders/sky/moonlit.frag");

    private static int program;
    private static int vao;
    private static boolean failed;
    private static long startMs = System.currentTimeMillis();

    private static int locRes, locTime, locSpeed, locBright, locStars, locFov, locQuality;
    private static int locNeb, locClouds, locRight, locUp, locFwd, locDepth, locHasDepth, locTint;

    private static final Vector3f RIGHT = new Vector3f();
    private static final Vector3f UP = new Vector3f();
    private static final Vector3f FWD = new Vector3f();
    private static final Quaternionf TMPQ = new Quaternionf();

    private MoonlitSky() {}

    public static boolean active(LumenConfig c) {
        return c.world && "moonlit".equals(c.plasma) && c.plasmaIntensity > 0;
    }

    public static void render(MinecraftClient mc, LumenConfig cfg) {
        if (!active(cfg) || mc.world == null || mc.player == null) return;
        if (failed) return;
        if (program == 0 && !init(mc)) return;
        if (!VisualsRenderer.ready) return;

        Framebuffer fb = mc.getFramebuffer();
        int w = fb.textureWidth;
        int h = fb.textureHeight;
        if (w <= 0 || h <= 0) return;

        TMPQ.set(mc.gameRenderer.getCamera().getRotation());
        FWD.set(0, 0, -1).rotate(TMPQ).normalize();
        RIGHT.set(1, 0, 0).rotate(TMPQ).normalize();
        UP.set(0, 1, 0).rotate(TMPQ).normalize();

        float fov = 70f;
        try { fov = (float) mc.options.getFov().getValue(); } catch (Throwable ignored) {}

        float timeSec = (System.currentTimeMillis() - startMs) / 1000.0f;
        float speed = cfg.plasmaSpeed / 100.0f;
        float bright = MathHelper.clamp(cfg.plasmaIntensity / 50.0f, 0.25f, 3.5f);
        float stars = MathHelper.clamp(cfg.plasmaIntensity / 65.0f, 0.35f, 2.2f);
        float neb = 0.55f + cfg.plasmaIntensity / 220.0f;
        float clouds = 0.4f + cfg.plasmaIntensity / 280.0f;

        RenderSystem.assertOnRenderThread();
        int prevProgram = GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

        int depthTex = 0;
        try { depthTex = fb.getDepthAttachment(); } catch (Throwable ignored) {}

        GL20.glUseProgram(program);
        if (vao == 0) vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        GL20.glUniform2f(locRes, (float) w, (float) h);
        GL20.glUniform1f(locTime, timeSec);
        GL20.glUniform1f(locSpeed, speed);
        GL20.glUniform1f(locBright, bright);
        GL20.glUniform1f(locStars, stars);
        GL20.glUniform1f(locFov, fov);
        GL20.glUniform1f(locQuality, 1.0f);
        GL20.glUniform1f(locNeb, neb);
        GL20.glUniform1f(locClouds, clouds);
        GL20.glUniform3f(locTint, ((cfg.plasmaRgb >> 16) & 255) / 255f, ((cfg.plasmaRgb >> 8) & 255) / 255f, (cfg.plasmaRgb & 255) / 255f);
        GL20.glUniform3f(locRight, RIGHT.x, RIGHT.y, RIGHT.z);
        GL20.glUniform3f(locUp, UP.x, UP.y, UP.z);
        GL20.glUniform3f(locFwd, FWD.x, FWD.y, FWD.z);

        boolean hasDepth = depthTex > 0;
        GL20.glUniform1f(locHasDepth, hasDepth ? 1f : 0f);
        if (hasDepth && locDepth >= 0) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(depthTex);
            GL20.glUniform1i(locDepth, 0);
        }

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        GL20.glDrawArrays(GL20.GL_TRIANGLES, 0, 3);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();

        GL30.glBindVertexArray(prevVao);
        GL20.glUseProgram(prevProgram);
    }

    private static boolean init(MinecraftClient mc) {
        try {
            String vsrc = readShader(mc, VERT);
            String fsrc = readShader(mc, FRAG);
            if (vsrc == null || fsrc == null) { failed = true; return false; }
            int vs = compile(GL20.GL_VERTEX_SHADER, vsrc);
            int fs = compile(GL20.GL_FRAGMENT_SHADER, fsrc);
            if (vs == 0 || fs == 0) { failed = true; return false; }
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vs);
            GL20.glAttachShader(program, fs);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                System.err.println("[Lumen] moonlit link: " + GL20.glGetProgramInfoLog(program));
                failed = true;
                return false;
            }
            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);

            locRes = u("uResolution");
            locTime = u("uTime");
            locSpeed = u("uSpeed");
            locBright = u("uBrightness");
            locStars = u("uStars");
            locFov = u("uFov");
            locQuality = u("uQuality");
            locNeb = u("uNebula");
            locClouds = u("uClouds");
            locTint = u("uTint");
            locRight = u("uCamRight");
            locUp = u("uCamUp");
            locFwd = u("uCamForward");
            locDepth = u("uDepth");
            locHasDepth = u("uHasDepth");
            vao = GL30.glGenVertexArrays();
            System.out.println("[Lumen] moonlit sky ready");
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            failed = true;
            return false;
        }
    }

    private static int u(String name) { return GL20.glGetUniformLocation(program, name); }

    private static int compile(int type, String src) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, src);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == 0) {
            System.err.println("[Lumen] moonlit compile: " + GL20.glGetShaderInfoLog(id));
            GL20.glDeleteShader(id);
            return 0;
        }
        return id;
    }

    private static String readShader(MinecraftClient mc, Identifier id) {
        try {
            Optional<Resource> res = mc.getResourceManager().getResource(id);
            if (res.isEmpty()) {
                System.err.println("[Lumen] missing " + id);
                return null;
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(res.get().getInputStream(), StandardCharsets.UTF_8))) {
                return r.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

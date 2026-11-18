package dev.lumen.visuals;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Полноэкранный procedural sky «Moonlit Night».
 * Рисуется поверх ванильного неба, только по пикселям с depth ≈ far (блоки не перекрывает).
 */
public final class MoonlitSky {
    private static final Identifier VERT = Identifier.of("lumenvisuals", "shaders/sky/moonlit.vert");
    private static final Identifier FRAG = Identifier.of("lumenvisuals", "shaders/sky/moonlit.frag");

    private static int program;
    private static int vao;
    private static boolean failed;
    private static long startMs = System.currentTimeMillis();

    private static int locRes, locTime, locSpeed, locBright, locStars, locFov, locQuality, locNeb, locClouds, locQuat, locDepth;

    private MoonlitSky() {}

    public static boolean active(LumenConfig c) {
        return c.world && "moonlit".equals(c.plasma) && c.plasmaIntensity > 0;
    }

    public static void render(MinecraftClient mc, LumenConfig cfg) {
        if (!active(cfg) || mc.world == null || mc.player == null || mc.options.hudHidden) return;
        if (failed) return;
        if (program == 0 && !init(mc)) return;

        Framebuffer fb = mc.getFramebuffer();
        int w = fb.textureWidth;
        int h = fb.textureHeight;
        if (w <= 0 || h <= 0) return;

        // camera quaternion from yaw/pitch (matches shader rot * ray)
        var cam = mc.gameRenderer.getCamera();
        float yaw = cam.getYaw();
        float pitch = cam.getPitch();
        // порядок как у ванильной камеры: yaw вокруг Y, затем pitch вокруг X
        Quaternionf q = new Quaternionf();
        q.rotateY((float) Math.toRadians(-yaw));
        q.rotateX((float) Math.toRadians(pitch));

        float fov = (float) mc.options.getFov().getValue();
        float timeSec = (System.currentTimeMillis() - startMs) / 1000.0f;
        float speed = cfg.plasmaSpeed / 100.0f;
        float bright = MathHelper.clamp(cfg.plasmaIntensity / 55.0f, 0.2f, 4.0f);
        float stars = MathHelper.clamp(cfg.plasmaIntensity / 70.0f, 0.3f, 2.5f);
        float neb = 0.7f + cfg.plasmaIntensity / 200.0f;
        float clouds = 0.55f + cfg.plasmaIntensity / 250.0f;
        float quality = 1.0f; // 0 low / 1 mid / 2 high

        RenderSystem.assertOnRenderThread();
        int prevProgram = GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

        // depth texture from main FBO (attachment)
        int depthTex = fb.getDepthAttachment();

        GL20.glUseProgram(program);
        GL30.glBindVertexArray(vao == 0 ? createVao() : vao);

        GL20.glUniform2f(locRes, (float) w, (float) h);
        GL20.glUniform1f(locTime, timeSec);
        GL20.glUniform1f(locSpeed, speed);
        GL20.glUniform1f(locBright, bright);
        GL20.glUniform1f(locStars, stars);
        GL20.glUniform1f(locFov, fov);
        GL20.glUniform1f(locQuality, quality);
        GL20.glUniform1f(locNeb, neb);
        GL20.glUniform1f(locClouds, clouds);
        GL20.glUniform4f(locQuat, q.x, q.y, q.z, q.w);

        if (depthTex > 0 && locDepth >= 0) {
            RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(depthTex);
            GL20.glUniform1i(locDepth, 0);
        }

        // не писать в depth, только цвет; depth-test off — маска в шейдере
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

    private static int createVao() {
        vao = GL30.glGenVertexArrays();
        return vao;
    }

    private static boolean init(MinecraftClient mc) {
        try {
            String vsrc = readShader(mc, VERT);
            String fsrc = readShader(mc, FRAG);
            if (vsrc == null || fsrc == null) {
                failed = true;
                return false;
            }
            int vs = compile(GL20.GL_VERTEX_SHADER, vsrc);
            int fs = compile(GL20.GL_FRAGMENT_SHADER, fsrc);
            if (vs == 0 || fs == 0) {
                failed = true;
                return false;
            }
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

            locRes = GL20.glGetUniformLocation(program, "uResolution");
            locTime = GL20.glGetUniformLocation(program, "uTime");
            locSpeed = GL20.glGetUniformLocation(program, "uSpeed");
            locBright = GL20.glGetUniformLocation(program, "uBrightness");
            locStars = GL20.glGetUniformLocation(program, "uStars");
            locFov = GL20.glGetUniformLocation(program, "uFov");
            locQuality = GL20.glGetUniformLocation(program, "uQuality");
            locNeb = GL20.glGetUniformLocation(program, "uNebula");
            locClouds = GL20.glGetUniformLocation(program, "uClouds");
            locQuat = GL20.glGetUniformLocation(program, "uCameraQuat");
            locDepth = GL20.glGetUniformLocation(program, "uDepth");
            createVao();
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            failed = true;
            return false;
        }
    }

    private static int compile(int type, String src) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, src);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == 0) {
            System.err.println("[Lumen] shader compile: " + GL20.glGetShaderInfoLog(id));
            GL20.glDeleteShader(id);
            return 0;
        }
        return id;
    }

    private static String readShader(MinecraftClient mc, Identifier id) {
        try {
            Optional<Resource> res = mc.getResourceManager().getResource(id);
            if (res.isEmpty()) {
                System.err.println("[Lumen] missing shader " + id);
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

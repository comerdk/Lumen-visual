package dev.lumen.visuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class LumenVisualsClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("lumenvisuals");
    private static final String CATEGORY = "key.categories.lumenvisuals";
    private static final Set<String> REPORTED = new HashSet<>();

    private static volatile boolean openMenu;
    private static boolean greeted;
    private static Object lastPlayer;

    private KeyBinding kEsp, kTracers, kNametags, kFullbright, kMenu;

    @Override
    public void onInitializeClient() {
        ConfigProfiles.ensureDefault();
        LumenConfig.load();
        TargetTracker.init();
        LumenCommands.register();

        kEsp = key("esp", GLFW.GLFW_KEY_J);
        kTracers = key("tracers", GLFW.GLFW_KEY_K);
        kFullbright = key("fullbright", GLFW.GLFW_KEY_L);
        kNametags = key("nametags", GLFW.GLFW_KEY_U);
        kMenu = key("menu", GLFW.GLFW_KEY_RIGHT_SHIFT);

        WorldRenderEvents.START.register(ctx -> {
            try {
                VisualsRenderer.capture(ctx);
                WorldEffects.frame(MinecraftClient.getInstance());
            } catch (Throwable t) {
                report("world", t);
            }
        });
        HudRenderCallback.EVENT.register(VisualsRenderer::render);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);

        LOGGER.info("Lumen Visuals loaded (menu: RShift or chat command .gui)");
    }

    /** Открыть меню (из чата): откроется на ближайшем тике, когда закроется экран чата. */
    public static void requestMenu() {
        openMenu = true;
    }

    private static KeyBinding key(String id, int code) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.lumenvisuals." + id, InputUtil.Type.KEYSYM, code, CATEGORY));
    }

    private void tick(MinecraftClient mc) {
        try {
            tickInner(mc);
        } catch (Throwable t) {
            report("tick", t);
        }
    }

    private void tickInner(MinecraftClient mc) {
        // один раз за запуск: подтверждение, что мод вообще загрузился
        if (mc.player != lastPlayer) {
            lastPlayer = mc.player;
            if (mc.player != null && !greeted) {
                greeted = true;
                mc.player.sendMessage(Text.literal("[Lumen] Мод загружен. Меню: .gui (или RShift). Помощь: .lumen help")
                        .formatted(Formatting.LIGHT_PURPLE), false);
            }
        }

        if (openMenu && mc.currentScreen == null && mc.player != null) {
            openMenu = false;
            mc.setScreen(new LumenScreen());
        }

        TargetTracker.tick(mc);
        WorldEffects.tick(mc);

        LumenConfig c = LumenConfig.get();
        while (kMenu.wasPressed()) {
            if (mc.currentScreen == null) mc.setScreen(new LumenScreen());
        }
        while (kEsp.wasPressed()) { c.esp = !c.esp; notify(mc, "ESP", c.esp, c); }
        while (kTracers.wasPressed()) { c.tracers = !c.tracers; notify(mc, "Tracers", c.tracers, c); }
        while (kNametags.wasPressed()) { c.nametags = !c.nametags; notify(mc, "Nametags", c.nametags, c); }
        while (kFullbright.wasPressed()) { c.fullbright = !c.fullbright; notify(mc, "Fullbright", c.fullbright, c); }
    }

    private static void notify(MinecraftClient mc, String name, boolean on, LumenConfig c) {
        c.save();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(name + ": " + (on ? "ON" : "OFF"))
                    .formatted(on ? Formatting.LIGHT_PURPLE : Formatting.GRAY), true);
        }
    }

    /** Ошибки не роняют игру: пишем в лог и один раз в чат. */
    public static void report(String where, Throwable t) {
        String key = where + ":" + t.getClass().getName();
        if (!REPORTED.add(key)) return;
        LOGGER.error("Lumen Visuals error in " + where, t);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null) {
            String msg = String.valueOf(t);
            if (msg.length() > 160) msg = msg.substring(0, 160);
            mc.player.sendMessage(Text.literal("[Lumen] Ошибка (" + where + "): " + msg).formatted(Formatting.RED), false);
        }
    }
}

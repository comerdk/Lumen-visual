package dev.lumen.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

/**
 * Click Actions: 3 режима, каждый срабатывает по своему бинду.
 * 1) Pearl  — бросок эндер-перла (из хотбара/левой руки), слот возвращается обратно
 * 2) Spec   — команда в чат (по умолчанию /spec)
 * 3) Hub    — команда в чат (по умолчанию /hub)
 *
 * Бинд хранится числом: 0 = не задан, 32.. = клавиша клавиатуры (GLFW),
 * 1000 + N = кнопка мыши N (ЛКМ = 0 и ПКМ = 1 запрещены).
 */
public final class ClickActions {
    public static final int MOUSE_BASE = 1000;
    private static final boolean[] PREV = new boolean[3];

    private ClickActions() {}

    // ---------- бинды ----------

    /** Код для кнопки мыши. */
    public static int mouseCode(int button) {
        return MOUSE_BASE + button;
    }

    /** Mouse 1 и 2 (ЛКМ/ПКМ) биндить нельзя. */
    public static boolean mouseAllowed(int button) {
        return button >= 2 && button <= 7;
    }

    /** Приводит значение из конфига к допустимому (ЛКМ/ПКМ и мусор -> 0). */
    public static int clean(int bind) {
        if (bind < 0) return 0;
        if (bind == MOUSE_BASE || bind == MOUSE_BASE + 1) return 0;
        if (bind > MOUSE_BASE + 7) return 0;
        if (bind > 0 && bind < 32) return 0;
        if (bind > 348 && bind < MOUSE_BASE) return 0;
        return bind;
    }

    public static String keyName(int bind) {
        if (bind <= 0) return "Нет";
        if (bind >= MOUSE_BASE) return "Mouse " + (bind - MOUSE_BASE + 1);
        try {
            InputUtil.Key k = InputUtil.fromKeyCode(bind, GLFW.glfwGetKeyScancode(bind));
            String s = k.getLocalizedText().getString();
            return s.isEmpty() ? "Key " + bind : s;
        } catch (Throwable t) {
            return "Key " + bind;
        }
    }

    private static boolean isDown(long window, int bind) {
        if (bind <= 0) return false;
        if (bind >= MOUSE_BASE) {
            return GLFW.glfwGetMouseButton(window, bind - MOUSE_BASE) == GLFW.GLFW_PRESS;
        }
        return InputUtil.isKeyPressed(window, bind);
    }

    // ---------- тик ----------

    public static void tick(MinecraftClient mc) {
        LumenConfig c = LumenConfig.get();
        long window = mc.getWindow().getHandle();
        int[] binds = {c.bindPearl, c.bindSpec, c.bindHub};
        // в меню/чате/без игрока бинды не срабатывают (и «залипшая» клавиша после закрытия меню не стреляет)
        boolean idle = mc.player == null || mc.currentScreen != null;

        for (int i = 0; i < 3; i++) {
            boolean down = isDown(window, binds[i]);
            boolean fire = down && !PREV[i] && !idle;
            PREV[i] = down;
            if (!fire) continue;
            switch (i) {
                case 0 -> throwPearl(mc);
                case 1 -> command(mc, c.clickSpecCmd);
                default -> command(mc, c.clickHubCmd);
            }
        }
    }

    // ---------- действия ----------

    private static void throwPearl(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) return;

        PlayerInventory inv = p.getInventory();
        int prev = inv.selectedSlot;
        int slot = -1;
        if (inv.getStack(prev).isOf(Items.ENDER_PEARL)) {
            slot = prev;
        } else {
            for (int i = 0; i < 9; i++) {
                if (inv.getStack(i).isOf(Items.ENDER_PEARL)) {
                    slot = i;
                    break;
                }
            }
        }

        if (slot >= 0) {
            inv.selectedSlot = slot;
            mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
            p.swingHand(Hand.MAIN_HAND);
            inv.selectedSlot = prev; // клиент синхронизирует слот на следующем тике
        } else if (p.getOffHandStack().isOf(Items.ENDER_PEARL)) {
            mc.interactionManager.interactItem(p, Hand.OFF_HAND);
            p.swingHand(Hand.OFF_HAND);
        } else {
            p.sendMessage(Text.literal("[Lumen] Нет эндер-перла в хотбаре").formatted(Formatting.GRAY), true);
        }
    }

    private static void command(MinecraftClient mc, String raw) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        String cmd = raw == null ? "" : raw.trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (cmd.isEmpty()) return;
        mc.player.networkHandler.sendChatCommand(cmd);
    }
}

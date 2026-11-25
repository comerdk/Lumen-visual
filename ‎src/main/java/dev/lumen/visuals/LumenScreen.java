package dev.lumen.visuals;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Click GUI в стиле liquid glass. Разделы Combat / Render / Player / Misc, у каждого модуля
 * сворачиваемые настройки (ПКМ по модулю), прокрутка колесом мыши. Открывается по RShift.
 */
public class LumenScreen extends Screen {
    private static final int PW = 580, PH = 280, CW = 172, CGAP = 12, CX0 = 20, CY0 = 64;
    /** Видимая область модулей (локальные координаты панели). */
    private static final int VIEW_T = 60, VIEW_B = PH - 22;
    private static final String[] TABS = {"Combat", "Render", "Player", "Misc"};
    private static final int TAB_W = 130, TAB_GAP = 6;
    private static final String[] SWATCHES = {"B388FF", "FF6EC7", "5E8BFF", "44D7F5", "5DE6B0", "A6E34F", "FFD24D",
            "FF9A4D", "FF5A5A", "FFFFFF", "8E5BFF", "C77DFF", "00E5FF", "FF2E93"};
    private static final String[] ESP_COLORS = {"39FF6B", "FF3B4D", "55D6FF", "B388FF", "FF6EC7", "FFD24D", "FF9A4D",
            "5DE6B0", "FFFFFF", "00E5FF", "A6E34F", "FF2E93", "5E8BFF", "C77DFF"};
    private static int tab = 0;
    /** Развёрнутые модули (по имени). Изначально пусто — все свёрнуты. */
    private static final Set<String> OPEN = new HashSet<>();

    private float us = 1f, px, py, dt;
    private double ps = 1.0;
    private long lastNs = System.nanoTime();
    private List<W> ws = new ArrayList<>();
    private final List<Mod> mods = new ArrayList<>();
    private final List<float[]> cards = new ArrayList<>();
    private int contentH;
    private float scrollCur, scrollTarget;
    private W drag;
    private Bind listening; // кнопка бинда, ждущая нажатия

    public LumenScreen() {
        super(Text.literal("Lumen Visuals"));
    }

    @Override
    protected void init() {
        build();
    }

    // ================= модули и раскладка =================
    private static final class Mod {
        final String name;
        final int col;
        final BooleanSupplier on;     // null — у модуля нет переключателя
        final Consumer<Boolean> setOn;
        final List<W> kids = new ArrayList<>();

        Mod(String name, int col, BooleanSupplier on, Consumer<Boolean> setOn) {
            this.name = name;
            this.col = col;
            this.on = on;
            this.setOn = setOn;
        }

        Mod add(W w) {
            kids.add(w);
            return this;
        }
    }

    private Mod mod(String name, int col, BooleanSupplier on, Consumer<Boolean> set) {
        Mod m = new Mod(name, col, on, set);
        mods.add(m);
        return m;
    }

    private Mod mod(String name, int col) {
        return mod(name, col, null, null);
    }

    /** Расставляет заголовки модулей и (у развёрнутых) их настройки по трём колонкам. */
    private void layout() {
        List<W> out = new ArrayList<>();
        cards.clear();
        int[] y = {CY0, CY0, CY0};
        for (Mod m : mods) {
            int col = m.col;
            float x = CX0 + col * (CW + CGAP);
            boolean open = !m.kids.isEmpty() && OPEN.contains(m.name);
            ModHdr hd = new ModHdr(m, open);
            hd.x = x;
            hd.y = y[col];
            hd.w = CW;
            out.add(hd);
            float top = y[col];
            float cy = top + hd.h;
            if (open) {
                cy += 2;
                for (W k : m.kids) {
                    k.x = x + 4;
                    k.y = cy;
                    k.w = CW - 8;
                    cy += k.h;
                    out.add(k);
                }
                cy += 4;
            }
            cards.add(new float[]{x, top, CW, cy - top});
            y[col] = (int) Math.ceil(cy) + 6;
        }
        ws = out;
        int bottom = Math.max(y[0], Math.max(y[1], y[2])) - 6;
        contentH = Math.max(0, bottom - CY0);
        scrollTarget = MathHelper.clamp(scrollTarget, 0f, maxScroll());
        scrollCur = MathHelper.clamp(scrollCur, 0f, maxScroll());
    }

    private float maxScroll() {
        return Math.max(0, contentH - (VIEW_B - CY0) + 2);
    }

    private void build() {
        LumenConfig c = LumenConfig.get();
        mods.clear();
        drag = null;
        listening = null;
        switch (tab) {
            case 0 -> buildCombat(c);
            case 1 -> buildRender(c);
            case 2 -> buildPlayer(c);
            default -> buildMisc(c);
        }
        layout();
    }

    // ---------- Combat ----------
    private void buildCombat(LumenConfig c) {
        mod("Target ESP", 0, () -> c.tesp, v -> c.tesp = v)
                .add(new Cyc("Стиль", new String[]{"crystal", "ring", "box", "both"}, () -> c.tespStyle, v -> c.tespStyle = v))
                .add(new Sld("Кристаллы", 3, 12, 1, () -> c.crystalCount, v -> c.crystalCount = v, String::valueOf))
                .add(new Sld("Размер кристаллов", 40, 200, 5, () -> c.crystalSize, v -> c.crystalSize = v, v -> v + "%"))
                .add(new Sld("Скорость вращения", 20, 300, 10, () -> c.crystalSpeed, v -> c.crystalSpeed = v, v -> v + "%"));

        mod("Target HUD", 1, () -> c.thud, v -> c.thud = v)
                .add(new Cyc("Стиль", new String[]{"glass", "flat"}, () -> c.thudStyle, v -> c.thudStyle = v))
                .add(new Tog("Голова игрока", () -> c.thudSkin, v -> c.thudSkin = v))
                .add(new Sld("Размер", 50, 200, 5, () -> Math.round(c.thudScale * 100), v -> c.thudScale = v / 100f, v -> v + "%"))
                .add(new Sld("Смещение X", -250, 250, 5, () -> c.thudX, v -> c.thudX = v, String::valueOf))
                .add(new Sld("Смещение Y", -200, 200, 5, () -> c.thudY, v -> c.thudY = v, String::valueOf))
                .add(new Sld("Держать цель", 1, 10, 1, () -> Math.round(c.targetHold), v -> c.targetHold = v, v -> v + " с"));

        mod("Prediction", 2, () -> c.predict, v -> c.predict = v)
                .add(new Note("Траектории: лук, арбалет,"))
                .add(new Note("трезубец, зелья, пёрлы"));
    }

    // ---------- Render ----------
    private void buildRender(LumenConfig c) {
        // колонка 1
        mod("ESP", 0, () -> c.esp, v -> c.esp = v)
                .add(new Cyc("Режим", new String[]{"fill", "box"}, () -> c.espMode, v -> c.espMode = v))
                .add(new Sld("Заливка", 10, 80, 2, () -> c.espFillAlpha, v -> c.espFillAlpha = v, v -> v + "%"))
                .add(new Sld("Толщина линий", 1, 6, 1, () -> c.espLine, v -> c.espLine = v, v -> String.valueOf(v / 2f)))
                .add(new Tog("Свечение рёбер", () -> c.espGlow, v -> c.espGlow = v))
                .add(new Hdr("Цели"))
                .add(new Tog("Игроки", () -> c.espPlayers, v -> c.espPlayers = v))
                .add(new Tog("Враждебные", () -> c.espHostile, v -> c.espHostile = v))
                .add(new Tog("Мирные", () -> c.espPassive, v -> c.espPassive = v))
                .add(new Tog("Невидимые", () -> c.showInvisible, v -> c.showInvisible = v))
                .add(new Sld("Дальность", 16, 256, 8, () -> c.range, v -> c.range = v, v -> v + " м"))
                .add(new Hdr("Цвет: игроки"))
                .add(new Swatches(ESP_COLORS, hx -> { c.playerColor = hx; }, () -> c.playerArgb))
                .add(new Hdr("Цвет: враждебные"))
                .add(new Swatches(ESP_COLORS, hx -> { c.hostileColor = hx; }, () -> c.hostileArgb))
                .add(new Hdr("Цвет: мирные"))
                .add(new Swatches(ESP_COLORS, hx -> { c.passiveColor = hx; }, () -> c.passiveArgb));

        mod("Tracers", 0, () -> c.tracers, v -> c.tracers = v)
                .add(new Tog("Из прицела", () -> c.tracersFromCrosshair, v -> c.tracersFromCrosshair = v));

        mod("Nametags", 0, () -> c.nametags, v -> c.nametags = v)
                .add(new Tog("Дистанция в нике", () -> c.showDistance, v -> c.showDistance = v));

        mod("Fullbright", 0, () -> c.fullbright, v -> c.fullbright = v)
                .add(new Sld("Яркость", 2, 100, 2, () -> (int) c.fullbrightGamma, v -> c.fullbrightGamma = v, String::valueOf));

        // колонка 2
        mod("World", 1, () -> c.world, v -> c.world = v)
                .add(new Btns(new String[]{"Newyear", "Sunset", "Aurora"},
                        new Runnable[]{() -> c.applyPreset("newyear"), () -> c.applyPreset("sunset"), () -> c.applyPreset("aurora")}))
                .add(new Btns(new String[]{"Sakura", "Neon"},
                        new Runnable[]{() -> c.applyPreset("sakura"), () -> c.applyPreset("neon")}))
                .add(new Sld("Насыщенность", 0, 200, 5, () -> c.worldSat, v -> c.worldSat = v, v -> v + "%"))
                .add(new Sld("Затемнение", 0, 70, 2, () -> c.worldDark, v -> c.worldDark = v, v -> v + "%"))
                .add(new Sld("Цветной оверлей", 0, 80, 2, () -> c.tintAlpha, v -> c.tintAlpha = v, String::valueOf))
                .add(new Tog("Туман", () -> c.worldFog, v -> c.worldFog = v))
                .add(new Sld("Дальность тумана", 30, 100, 5, () -> c.fogEnd, v -> c.fogEnd = v, v -> v + "%"))
                .add(new Tog("Своё время суток", () -> c.worldTime, v -> c.worldTime = v))
                .add(new Sld("Время", 0, 24000, 500, () -> c.worldTimeTicks, v -> c.worldTimeTicks = v,
                        v -> String.format("%02d:00", ((v / 1000) + 6) % 24)));

        mod("Particles", 1)
                .add(new Sld("Снег", 0, 400, 10, () -> c.snowCount, v -> c.snowCount = v, String::valueOf))
                .add(new Sld("Звёзды", 0, 150, 5, () -> c.starCount, v -> c.starCount = v, String::valueOf))
                .add(new Sld("Дым", 0, 30, 1, () -> c.smokeCount, v -> c.smokeCount = v, String::valueOf))
                .add(new Sld("Скорость частиц", 20, 300, 10, () -> c.particleSpeed, v -> c.particleSpeed = v, v -> v + "%"));

        mod("Sky Plasma", 1)
                .add(new Cyc("Вариант", new String[]{"off", "aurora", "nebula", "solar", "toxic", "moonlit"}, () -> c.plasma, v -> c.plasma = v))
                .add(new Sld("Яркость", 0, 100, 5, () -> c.plasmaIntensity, v -> c.plasmaIntensity = v, v -> v + "%"))
                .add(new Sld("Скорость", 20, 300, 10, () -> c.plasmaSpeed, v -> c.plasmaSpeed = v, v -> v + "%"));

        // колонка 3
        mod("View Model", 2, () -> c.vm, v -> c.vm = v)
                .add(new Sld("Позиция X", -100, 100, 1, () -> c.vmX, v -> c.vmX = v, String::valueOf))
                .add(new Sld("Позиция Y", -100, 100, 1, () -> c.vmY, v -> c.vmY = v, String::valueOf))
                .add(new Sld("Позиция Z", -100, 100, 1, () -> c.vmZ, v -> c.vmZ = v, String::valueOf))
                .add(new Sld("Размер", 30, 200, 5, () -> c.vmScale, v -> c.vmScale = v, v -> v + "%"))
                .add(new Hdr("Поворот"))
                .add(new Sld("Вокруг X", -180, 180, 5, () -> c.vmRotX, v -> c.vmRotX = v, v -> v + "°"))
                .add(new Sld("Вокруг Y", -180, 180, 5, () -> c.vmRotY, v -> c.vmRotY = v, v -> v + "°"))
                .add(new Sld("Вокруг Z", -180, 180, 5, () -> c.vmRotZ, v -> c.vmRotZ = v, v -> v + "°"))
                .add(new Tog("Без анимации подъёма", () -> c.vmNoEquip, v -> c.vmNoEquip = v))
                .add(new Btns(new String[]{"Сбросить view model"}, new Runnable[]{() -> {
                    c.vmX = 0; c.vmY = 0; c.vmZ = 0; c.vmScale = 100;
                    c.vmRotX = 0; c.vmRotY = 0; c.vmRotZ = 0;
                    c.vmSwing = "smooth"; c.vmSwingSpeed = 100; c.vmAnimation = true; c.vmRotate = false; c.vmRotateSpeed = 1; c.vmNoEquip = false;
                }}));

        mod("Swing Animation", 2, () -> c.vmAnimation, v -> c.vmAnimation = v)
                .add(new Cyc("Стиль", new String[]{"smooth", "mode2", "mode3", "mode4", "mode5", "mode6", "mode7", "vanilla", "none"},
                        () -> c.vmSwing, v -> c.vmSwing = v))
                .add(new Sld("Скорость взмаха", 50, 300, 10, () -> c.vmSwingSpeed, v -> c.vmSwingSpeed = v, v -> v + "%"))
                .add(new Tog("Вращение при ударе", () -> c.vmRotate, v -> c.vmRotate = v))
                .add(new Sld("Скорость вращения", 1, 5, 1, () -> c.vmRotateSpeed, v -> c.vmRotateSpeed = v, String::valueOf));

        mod("Module List", 2, () -> c.hud, v -> c.hud = v)
                .add(new Note("Список включённых модулей"))
                .add(new Note("на экране"));
    }

    // ---------- Player ----------
    private void buildPlayer(LumenConfig c) {
        mod("Click Actions", 0)
                .add(new Bind("Pearl", () -> c.bindPearl, v -> c.bindPearl = v))
                .add(new Bind("Spec в чат", () -> c.bindSpec, v -> c.bindSpec = v))
                .add(new Bind("/hub в чат", () -> c.bindHub, v -> c.bindHub = v))
                .add(new Note("Клик, затем нажми клавишу"))
                .add(new Note("Esc при выборе — стереть"))
                .add(new Note("ЛКМ и ПКМ не биндятся"));

        mod("Halo", 1, () -> c.cosHalo, v -> c.cosHalo = v)
                .add(new Note("Виден от 3-го лица (F5)"));
        mod("Star Aura", 1, () -> c.cosAura, v -> c.cosAura = v)
                .add(new Note("Виден от 3-го лица (F5)"));
        mod("Star Trail", 1, () -> c.cosTrail, v -> c.cosTrail = v)
                .add(new Note("Всегда, пока ты двигаешься"));

        mod("Cosmetics Style", 2)
                .add(new Sld("Размер", 50, 200, 10, () -> c.cosSize, v -> c.cosSize = v, v -> v + "%"))
                .add(new Sld("Скорость", 20, 300, 10, () -> c.cosSpeed, v -> c.cosSpeed = v, v -> v + "%"))
                .add(new Tog("Цвет темы", () -> c.cosFollowTheme, v -> c.cosFollowTheme = v))
                .add(new Swatches(SWATCHES, hx -> { c.cosColor = hx; c.cosFollowTheme = false; }, () -> c.cosRgb));
    }

    // ---------- Misc ----------
    private void buildMisc(LumenConfig c) {
        Sld hue = new Sld("Оттенок", 0, 359, 1, () -> c.themeH, v -> c.themeH = v, v -> v + "°");
        hue.rainbow = true;
        mod("Theme", 0)
                .add(new Swatches(SWATCHES, c::setThemeHex, () -> c.themeRgb))
                .add(hue)
                .add(new Sld("Насыщенность", 15, 100, 1, () -> c.themeS, v -> c.themeS = v, v -> v + "%"))
                .add(new Sld("Яркость", 35, 100, 1, () -> c.themeV, v -> c.themeV = v, v -> v + "%"));

        mod("Glass", 1)
                .add(new Sld("Прозрачность панели", 20, 95, 5, () -> c.glassOpacity, v -> c.glassOpacity = v, v -> v + "%"))
                .add(new Tog("Жидкие блики", () -> c.guiAnim, v -> c.guiAnim = v))
                .add(new Tog("Тема для Target/HUD", () -> c.followTheme, v -> c.followTheme = v));

        mod("HUD", 1, () -> c.customHud, v -> c.customHud = v)
                .add(new Swatches(ESP_COLORS, hx -> { c.hudColor = hx; c.recolor(); }, () -> c.hudRgb))
                .add(new Sld("Позиция X", 0, 300, 2, () -> c.hudX, v -> c.hudX = v, String::valueOf))
                .add(new Sld("Позиция Y", 0, 300, 2, () -> c.hudY, v -> c.hudY = v, String::valueOf))
                .add(new Sld("Размер", 50, 150, 5, () -> c.hudScale, v -> c.hudScale = v, v -> v + "%"))
                .add(new Hdr("Отображать"))
                .add(new Tog("Название Lumen", () -> c.hudLogo, v -> c.hudLogo = v))
                .add(new Tog("Координаты", () -> c.hudCoords, v -> c.hudCoords = v))
                .add(new Tog("FPS", () -> c.hudFps, v -> c.hudFps = v))
                .add(new Tog("Сервер", () -> c.hudServer, v -> c.hudServer = v))
                .add(new Tog("Время", () -> c.hudClock, v -> c.hudClock = v))
                .add(new Tog("Скорость", () -> c.hudSpeed, v -> c.hudSpeed = v));

        mod("Preview", 2).add(new Preview());

        mod("Info", 2)
                .add(new Note("Меню: RShift или чат .gui"))
                .add(new Note("Помощь: .lumen help"))
                .add(new Note("Клавиши: J ESP, K Tracers,"))
                .add(new Note("U Nametags, L Fullbright"))
                .add(new Note("ПКМ по модулю — настройки"))
                .add(new Note("Колесо мыши — прокрутка"));
    }

    // ================= рендер =================
    private boolean broken;
    private String brokenMsg = "";

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        if (broken) {
            renderBackground(ctx, mx, my, delta);
            ctx.drawCenteredTextWithShadow(textRenderer, "Lumen: ошибка отрисовки меню", width / 2, height / 2 - 12, 0xFFFF6B8A);
            ctx.drawCenteredTextWithShadow(textRenderer, brokenMsg, width / 2, height / 2, 0xFFFFFFFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "Закрой (Esc) и пришли лог игры", width / 2, height / 2 + 14, 0xFFD8CCF2);
            return;
        }
        try {
            renderInner(ctx, mx, my, delta);
        } catch (Throwable t) {
            broken = true;
            brokenMsg = String.valueOf(t);
            if (brokenMsg.length() > 90) brokenMsg = brokenMsg.substring(0, 90);
            LumenVisualsClient.report("GUI", t);
        }
    }

    private void renderInner(DrawContext ctx, int mx, int my, float delta) {
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;

        renderBackground(ctx, mx, my, delta);

        LumenConfig cfg = LumenConfig.get();
        us = Math.max(0.5f, Math.min(1f, Math.min((width - 12f) / PW, (height - 12f) / PH)));
        px = (width - PW * us) / 2f;
        py = (height - PH * us) / 2f;
        ps = Gfx.sf() * us;
        double lx = (mx - px) / us, ly = (my - py) / us;

        // плавная прокрутка
        scrollTarget = MathHelper.clamp(scrollTarget, 0f, maxScroll());
        scrollCur += (scrollTarget - scrollCur) * Math.min(1f, dt * 16f);
        if (Math.abs(scrollTarget - scrollCur) < 0.05f) scrollCur = scrollTarget;

        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(px, py, 0f);
        m.scale(us, us, 1f);

        drawPanel(ctx, cfg, now);

        // заголовок и кнопка закрытия
        ctx.drawText(textRenderer, Text.literal("LUMEN").formatted(Formatting.BOLD), 20, 14, 0xFFFFFFFF, true);
        ctx.drawText(textRenderer, "Visuals", 62, 14, 0xFF000000 | cfg.themeLight, false);
        boolean closeHover = in(lx, ly, PW - 34, 12, 20, 16);
        Gfx.rrect(ctx, PW - 34, 12, 20, 16, 8, closeHover ? 0x55FF6B8A : 0x26FFFFFF, closeHover ? 0x33FF6B8A : 0x14FFFFFF, ps);
        ctx.drawCenteredTextWithShadow(textRenderer, "×", PW - 24, 16, 0xFFFFFFFF);

        // разделы
        for (int i = 0; i < TABS.length; i++) {
            float tx = 20 + i * (TAB_W + TAB_GAP), ty = 34;
            boolean active = i == tab, hover = in(lx, ly, tx, ty, TAB_W, 20);
            if (active) {
                Gfx.rrect(ctx, tx, ty, TAB_W, 20, 10, Gfx.withA(cfg.themeLight, 0xC8), Gfx.withA(cfg.themeRgb, 0xC8), ps);
                Gfx.ring(ctx, tx, ty, TAB_W, 20, 10, Gfx.ringPx(ps), 0x88FFFFFF, ps);
            } else {
                Gfx.rrect(ctx, tx, ty, TAB_W, 20, 10, hover ? 0x30FFFFFF : 0x1AFFFFFF, hover ? 0x20FFFFFF : 0x0EFFFFFF, ps);
            }
            ctx.drawCenteredTextWithShadow(textRenderer, TABS[i], (int) (tx + TAB_W / 2f), (int) ty + 6, active ? 0xFFFFFFFF : 0xFFD8CCF2);
        }

        // полоса прокрутки
        float ms = maxScroll();
        if (ms > 0) {
            float trackY = VIEW_T + 4, trackH = VIEW_B - VIEW_T - 8;
            float viewH = VIEW_B - CY0;
            float thumbH = Math.max(18f, trackH * viewH / (viewH + ms));
            float thumbY = trackY + (trackH - thumbH) * (scrollCur / ms);
            Gfx.rrect(ctx, PW - 11, trackY, 3, trackH, 1.5f, 0x22FFFFFF, 0x12FFFFFF, ps);
            Gfx.rrect(ctx, PW - 11, thumbY, 3, thumbH, 1.5f, Gfx.withA(cfg.themeLight, 0xE0), Gfx.withA(cfg.themeRgb, 0xE0), ps);
        }

        String foot = "RShift / ×  — закрыть   •   ПКМ по модулю — настройки   •   колесо — прокрутка";
        ctx.drawText(textRenderer, foot, PW / 2 - textRenderer.getWidth(foot) / 2, PH - 14, 0x88FFFFFF, false);

        m.pop();

        // модули: обрезаем по видимой области и сдвигаем на величину прокрутки.
        // Scissor включаем при единичной матрице, чтобы координаты были экранными.
        int x1 = (int) Math.floor(px + 12 * us), y1 = (int) Math.floor(py + VIEW_T * us);
        int x2 = (int) Math.ceil(px + (PW - 12) * us), y2 = (int) Math.ceil(py + VIEW_B * us);
        ctx.enableScissor(x1, y1, x2, y2);
        try {
            int sc = Math.round(scrollCur);
            m.push();
            m.translate(px, py, 0f);
            m.scale(us, us, 1f);
            m.translate(0f, -sc, 0f);
            double hy = (ly >= VIEW_T && ly < VIEW_B && lx >= 0 && lx < PW) ? ly + sc : -10000;
            for (float[] k : cards) {
                if (k[1] + k[3] < sc + VIEW_T || k[1] > sc + VIEW_B) continue;
                Gfx.rrect(ctx, k[0], k[1], k[2], k[3], 8, 0x1AFFFFFF, 0x0AFFFFFF, ps);
            }
            for (W w : ws) {
                if (w.y + w.h < sc + VIEW_T || w.y > sc + VIEW_B) continue;
                w.draw(this, ctx, lx, hy);
            }
            m.pop();
        } finally {
            ctx.disableScissor();
        }
    }

    private void drawPanel(DrawContext c, LumenConfig cfg, long now) {
        // мягкая тень
        for (int i = 3; i >= 1; i--) {
            Gfx.rrect(c, -i * 3, -i * 3 + 7, PW + i * 6, PH + i * 6, 22 + i * 3, 0x0E000000, 0x0E000000, ps);
        }
        float op = cfg.glassOpacity / 100f;
        int top = Gfx.withA(Gfx.mulRgb(cfg.themeRgb, 0.62f), (int) (255 * op * 0.60f));
        int bot = Gfx.withA(cfg.themeDark, (int) (255 * op * 0.90f));
        Gfx.rrect(c, 0, 0, PW, PH, 22, top, bot, ps);

        if (cfg.guiAnim) {
            double t = now / 1_000_000_000.0 * 0.35;
            Gfx.glow(c, (int) (PW * 0.28 + Math.sin(t) * 60), (int) (PH * 0.35 + Math.cos(t * 1.3) * 40), 50, cfg.themeLight, 14);
            Gfx.glow(c, (int) (PW * 0.72 + Math.cos(t * 0.9) * 70), (int) (PH * 0.60 + Math.sin(t * 1.1) * 50), 56,
                    Gfx.mixRgb(cfg.themeRgb, 0xFFFFFF, 0.2f), 12);
        }

        // блик сверху и обводка
        Gfx.rrect(c, 1.5f, 1.5f, PW - 3, PH * 0.42f, 21, 0x3CFFFFFF, 0x05FFFFFF, ps);
        Gfx.ring(c, 0, 0, PW, PH, 22, Gfx.ringPx(ps), 0x78FFFFFF, ps);
        Gfx.ring(c, 1.2f, 1.2f, PW - 2.4f, PH - 2.4f, 21, 1, Gfx.withA(cfg.themeLight, 0x40), ps);
    }

    private static boolean in(double lx, double ly, float x, float y, float w, float h) {
        return lx >= x && lx < x + w && ly >= y && ly < y + h;
    }

    // ================= ввод =================
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double lx = (mx - px) / us, ly = (my - py) / us;
        LumenConfig cfg = LumenConfig.get();
        if (listening != null) {
            if (ClickActions.mouseAllowed(button)) {
                listening.set.accept(ClickActions.mouseCode(button));
                listening = null;
                cfg.save();
            }
            return true; // ЛКМ/ПКМ (mouse 1 и 2) не биндятся и ничего не делают
        }
        if (lx >= 0 && ly >= 0 && lx < PW && ly < PH) {
            if (button == 0 && in(lx, ly, PW - 34, 12, 20, 16)) {
                close();
                return true;
            }
            for (int i = 0; i < TABS.length; i++) {
                if (in(lx, ly, 20 + i * (TAB_W + TAB_GAP), 34, TAB_W, 20)) {
                    if (tab != i) {
                        tab = i;
                        scrollCur = scrollTarget = 0f;
                        build();
                    }
                    return true;
                }
            }
            if (ly >= VIEW_T && ly < VIEW_B) {
                double cy = ly + Math.round(scrollCur);
                for (W w : ws) {
                    if (w.hit(lx, cy) && w.down(this, lx, cy, button)) {
                        if (w.draggable) drag = w;
                        cfg.recolor();
                        cfg.save();
                        return true;
                    }
                }
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        if (listening != null) return true;
        scrollTarget = MathHelper.clamp(scrollTarget - (float) verticalAmount * 28f, 0f, maxScroll());
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (drag != null) {
            drag.move((mx - px) / us);
            LumenConfig.get().recolor();
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (drag != null) {
            drag = null;
            LumenConfig.get().recolor();
            LumenConfig.get().save();
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                listening.set.accept(0); // Esc — стереть бинд
            } else if (keyCode != GLFW.GLFW_KEY_UNKNOWN) {
                listening.set.accept(keyCode);
            } else {
                return true;
            }
            listening = null;
            LumenConfig.get().recolor();
            LumenConfig.get().save();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        listening = null;
        LumenConfig c = LumenConfig.get();
        c.recolor();
        c.save();
    }

    // ================= виджеты =================
    private abstract static class W {
        float x, y, w, h;
        boolean draggable;

        abstract void draw(LumenScreen s, DrawContext c, double mx, double my);

        boolean down(LumenScreen s, double mx, double my, int b) {
            return false;
        }

        void move(double mx) {
        }

        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** Заголовок модуля: ЛКМ — вкл/выкл, ПКМ — развернуть/свернуть настройки. */
    private static final class ModHdr extends W {
        final Mod m;
        final boolean open;
        float anim;

        ModHdr(Mod m, boolean open) {
            this.m = m;
            this.open = open;
            this.h = 22;
            this.anim = m.on != null && m.on.getAsBoolean() ? 1f : 0f;
        }

        @Override
        void draw(LumenScreen s, DrawContext c, double mx, double my) {
            LumenConfig cfg = LumenConfig.get();
            boolean on = m.on == null || m.on.getAsBoolean();
            if (hit(mx, my)) Gfx.rrect(c, x, y, w, h, 8, 0x28FFFFFF, 0x14FFFFFF, s.ps);
            float tx = x + 8;
            if (!m.kids.isEmpty()) {
                c.drawText(s.textRenderer, open ? "v" : ">", (int) x + 8, (int) y + 7, 0xBBFFFFFF, false);
                tx = x + 19;
            }
            c.drawText(s.textRenderer, m.name, (int) tx, (int) y + 7, on ? 0xFFFFFFFF : 0xFFC9BEE0, true);
            if (m.on != null) {
                anim += ((on ? 1f : 0f) - anim) * Math.min(1f, s.dt * 14f);
                float sx = x + w - 32, sy = y + 5;
                Gfx.rrect(c, sx, sy, 26, 12, 6,
                        Gfx.lerp(0x40FFFFFF, Gfx.withA(cfg.themeLight, 0xE6), anim),
                        Gfx.lerp(0x26FFFFFF, Gfx.withA(cfg.themeRgb, 0xE6), anim), s.ps);
                Gfx.ring(c, sx, sy, 26, 12, 6, Gfx.ringPx(s.ps), 0x55FFFFFF, s.ps);
                float kx = sx + 1 + 13 * anim;
                Gfx.rrect(c, kx, sy + 1.5f, 10, 10, 5, 0xFFFFFFFF, 0xFFE4DAFF, s.ps);
            }
        }

        @Override
        boolean down(LumenScreen s, double mx, double my, int b) {
            boolean hasKids = !m.kids.isEmpty();
            if (b == 1 || (b == 0 && m.on == null)) {
                if (!hasKids) return true;
                if (!OPEN.remove(m.name)) OPEN.add(m.name);
                s.layout();
                return true;
            }
            if (b == 0) {
                m.setOn.accept(!m.on.getAsBoolean());
                return true;
            }
            return false;
        }
    }

    private static final class Hdr extends W {
        final String text;

        Hdr(String t) {
            text = t;
            h = 16;
        }

        @Override
        void draw(LumenScreen s, DrawContext c, double mx, double my) {
            LumenConfig cfg = LumenConfig.get();
            c.drawText(s.textRenderer, text, (int) x + 2, (int) y + 3, 0xFF000000 | cfg.themeLight, false);
            Gfx.rrect(c, x, y + 13, w, 1f, 0.5f, 0x55FFFFFF, 0x10FFFFFF, s.ps);
        }
    }

    private static final class Note extends W {
        final String text;

        Note(String t) {
            text = t;
            h = 12;
        }

        @Override
        void draw(LumenScreen s, DrawContext c, double mx, double my) {
            c.drawText(s.textRenderer, text, (int) x + 2, (int) y + 2, 0x99FFFFFF, false);
        }
    }

    private static final class Tog extends W {
        final String label;
        final BooleanSupplier get;
        final Consumer<Boolean> set;
        float anim;

        Tog(String label, BooleanSupplier get, Consumer<Boolean> set) {
            this.label = label;
            this.get = get;
            this.set = set;
            this.h = 20;
            this.anim = get.getAsBoolean() ? 1f : 0f;
        }

        @Override
        void draw(LumenScreen s, DrawContext c, double mx, double my) {
            LumenConfig cfg = LumenConfig.get();
            boolean on = get.getAsBoolean();
            anim += ((on ? 1f : 0f) - anim) * Math.min(1f, s.dt * 14f);
            if (hit(mx, my)) Gfx.rrect(c, x, y, w, h - 1, 7, 0x24FFFFFF, 0x12FFFFFF, s.ps);
            c.drawText(s.textRenderer, label, (int) x + 6, (int) y + 6, 0xFFFFFFFF, true);
            float sx = x + w - 32, sy = y + 4;
            Gfx.rrect(c, sx, sy, 26, 12, 6,
                    Gfx.lerp(0x40FFFFFF, Gfx.withA(cfg.themeLight, 0xE6), anim),
                    Gfx.lerp(0x26FFFFFF, Gfx.withA(cfg.themeRgb, 0xE6), anim), s.ps);
            Gfx.ring(c, sx, sy, 26, 12, 6, Gfx.ringPx(s.ps), 0x55FFFFFF, s.ps);
            float kx = sx + 1 + 13 * anim;
            Gfx.rrect(c, kx, sy + 1.5f, 10, 10, 5, 0xFFFFFFFF, 0xFFE4DAFF, s.ps);
        }

        @Override
        boolean down(LumenScreen s, double mx, double my, int b) {
            if (b != 0) return false;
            set.accept(!get.getAsBoolean());
            return true;
        }
    }

    private static final class Bind extends W {
        final String label;
        final IntSupplier get;
        final IntConsumer set;

        Bind(String label, IntSupplier get, IntConsumer set) {
            this.label = label;
            this.get = get;
            this.set = set;
            this.h = 20;
        }

        @Override
        void draw(LumenScreen s, DrawContext c, double mx, double my) {
            LumenConfig cfg = LumenConfig.get();
            boolean wait = s.listening == this;
            if (hit(mx, my) || wait) Gfx.rrect(c, x, y, w, h - 1, 7, 0x24FFFFFF, 0x12FFFFFF, s.ps);
            c.drawText(s.textRenderer, label, (int) x + 6, (int) y + 6, 0xFFFFFFFF, true);
            float bw = 72, bx = x + w - bw - 2;
            int a = wait ? 0xE0 : 0x99, b = wait ? 0xF0 : 0xB0;
            Gfx.rrect(c, bx, y + 2, bw, 16, 8, Gfx.withA(cfg.themeLight, a), Gfx.withA(cfg.themeRgb, b), s.ps);
            Gfx.ring(c, bx, y + 2, bw, 16, 8, Gfx.ringPx(s.ps), wait ? 0xCCFFFFFF : 0x55FFFFFF, s.ps);
            String t = wait ? "..." : s.textRenderer.trimToWidth(ClickActions.keyName(get.getAsInt()), 64);
            c.drawCenteredTextWithShadow(s.textRenderer, t, (int) (bx + bw / 2), (int) y + 6, 0xFFFFFFFF);
        }

        @Override
        boolean down(LumenScreen s, double mx, double my, int b) {
            if (b != 0) return false;
            s.listening = this;
            return true;
        }
    }

    private static final class Cyc extends W {
        final String label;
        final String[] opts;
        final Supplier<String> get;
        final Consumer<String> set;

        Cyc(String label, String[] opts, Supplier<String> get, Consumer<String> set) {
            this.label = label;
            this.opts = opts;
            this.get = get;
            this.set = set;
            this.h = 20;
        }

        @Override
        void draw(LumenScreen s, DrawContext c, double mx, double my) {
            LumenConfig cfg = LumenConfig.get();
            if (hit(mx, my)) Gfx.rrect(c, x, y, w, h - 1, 7, 0x24FFFFFF, 0x12FFFFFF, s.ps);
            c.drawText(s.textRenderer, label, (int) x + 6, (int) y + 6, 0xFFFFFFFF, true);
            float bw = 72, bx = x + w - bw - 2;
            Gfx.rrect(c, bx, y + 2, bw, 16, 8, Gfx.withA(cfg.themeLight, 0x99), Gfx.withA(cfg.themeRgb, 0xB0), s.ps);
            Gfx.ring(c, bx, y + 2, bw, 16, 8, Gfx.ringPx(s.ps), 0x55FFFFFF, s.ps);
            c.drawCenteredTextWithShadow(s.textRenderer, get.get(), (int) (bx + bw / 2), (int) y + 6, 0xFFFFFFFF);
        }

        @Override
        boolean down(LumenScreen s, double mx, double my, int b) {
            String cur = get.get();
            int i = 0;
            for (int k = 0; k < opts.length; k++) if (opts[k].equals(cur)) i = k;
            i += (b == 1) ? -1 : 1;
            set.accept(opts[(i + opts.length) % opts.length]);
            return true;
        }
    }

    private static final class Sld extends W {
        final String label;
        final int min, max, step;
        final IntSupplier get;
        final IntConsumer set;
        final IntFunction<String> fmt;
        boolean rainbow;

        Sld(String label, int min, int max, int step, IntSupplier get, IntConsumer set, IntFunction<String> fmt) {
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.get = get;
            this.set = set;
            this.fmt = fmt;
            this.h = 30;
            this.draggable = true;
        }

        @Override
        void draw(LumenScreen s, DrawContext c, double mx, double my) {
            LumenConfig cfg = LumenConfig.get();
            int v = get.getAsInt();
            float f = MathHelper.clamp((v - min) / (float) (max - min), 0f, 1f);
            c.drawText(s.textRenderer, label, (int) x + 6, (int) y + 3, 0xFFFFFFFF, true);
            String t = fmt.apply(v);
            c.drawText(s.textRenderer, t, (int) (x + w - 6 - s.textRenderer.getWidth(t)), (int) y + 3, 0xFF000000 | cfg.themeLight, true);

            float tx = x + 8, tw = w - 16, ty = y + 19;
            if (rainbow) {
                int n = (int) tw;
                int[] ins = {3, 2, 1, 1};
                for (int i = 0; i < n; i++) {
                    int d = Math.min(i, n - 1 - i);
                    int in = d >= 4 ? 0 : ins[d];
                    int col = 0xFF000000 | MathHelper.hsvToRgb(i / (float) n, 0.75f, 1f);
                    c.fill((int) tx + i, (int) ty + in, (int) tx + i + 1, (int) ty + 8 - in, col);
                }
                ty += 2;
            } else {
                Gfx.rrect(c, tx, ty, tw, 4, 2, 0x3CFFFFFF, 0x26FFFFFF, s.ps);
                Gfx.rrect(c, tx, ty, Math.max(4f, tw * f), 4, 2, Gfx.withA(cfg.themeLight, 0xEE), Gfx.withA(cfg.themeRgb, 0xEE), s.ps);
            }
            float kx = tx + tw * f - 5;
            Gfx.rrect(c, kx, ty - 3, 10, 10, 5, 0xFFFFFFFF, 0xFFE4DAFF, s.ps);
            Gfx.ring(c, kx, ty - 3, 10, 10, 5, 1, 0x66000000, s.ps);
        }

        @Override
        boolean down(LumenScreen s, double mx, double my, int b) {
            if (b != 0) return false;
            move(mx);
            return true;
        }

        @Override
        void move(double mx) {
            double f = MathHelper.clamp((mx - (x + 8)) / (w - 16), 0.0, 1.0);
            int steps = (int) Math.round(f * (max - min) / step);
            set.accept(MathHelper.clamp(min + steps * step, min, max));
        }
    }

    private static final class Btns extends W {
        final String[] labels;
        final Runnable[] acts;

        Btns(String[] labels, Runnable[] acts) {
            this.labels = labels;
            this.acts = acts;
            this.h = 22;
        }

        @Override
        void draw(LumenScreen s, DrawContext c, double mx, double my) {
            LumenConfig cfg = LumenConfig.get();
            int n = labels.length;
            float gap = 4, bw = (w - gap * (n - 1)) / n;
            for (int i = 0; i < n; i++) {
                float bx = x + i * (bw + gap);
                boolean hv = mx >= bx && mx < bx + bw && my >= y && my < y + h;
                Gfx.rrect(c, bx, y + 1, bw, 20, 10,
                        Gfx.withA(cfg.themeLight, hv ? 0xCC : 0x80), Gfx.withA(cfg.themeRgb, hv ? 0xCC : 0x90), s.ps);
                Gfx.ring(c, bx, y + 1, bw, 20, 10, Gfx.ringPx(s.ps), 0x55FFFFFF, s.ps);
                c.drawCenteredTextWithShadow(s.textRenderer, labels[i], (int) (bx + bw / 2), (int) y + 7, 0xFFFFFFFF);
            }
        }

        @Override
        boolean down(LumenScreen s, double mx, double my, int b) {
            int n = labels.length;
            float gap = 4, bw = (w - gap * (n - 1)) / n;
            int i = (int) ((mx - x) / (bw + gap));
            if (i >= 0 && i < n && mx - x - i * (bw + gap) <= bw) {
                acts[i].run();
                return true;
            }
            return false;
        }
    }

    private static final class Swatches extends W {
        static final int COLS = 7, SZ = 16, GAP = 7;
        final String[] hex;
        final Consumer<String> pick;
        final IntSupplier cur;

        Swatches(String[] hex, Consumer<String> pick, IntSupplier cur) {
            this.hex = hex;
            this.pick = pick;
            this.cur = cur;
            this.h = ((hex.length + COLS - 1) / COLS) * 22;
        }

        private float sx(int i) {
            return x + 3 + (i % COLS) * (SZ + GAP);
        }

        private float sy(int i) {
            return y + 2 + (i / COLS) * 22;
        }

        @Override
        void draw(LumenScreen s, DrawContext c, double mx, double my) {
            LumenConfig cfg = LumenConfig.get();
            for (int i = 0; i < hex.length; i++) {
                int rgb = LumenConfig.parseHex(hex[i]);
                float cx = sx(i), cyy = sy(i);
                boolean hv = mx >= cx && mx < cx + SZ && my >= cyy && my < cyy + SZ;
                int now = cur.getAsInt();
                int diff = Math.abs(((rgb >> 16) & 255) - ((now >> 16) & 255))
                        + Math.abs(((rgb >> 8) & 255) - ((now >> 8) & 255))
                        + Math.abs((rgb & 255) - (now & 255));
                Gfx.rrect(c, cx, cyy, SZ, SZ, SZ / 2f, 0xFF000000 | Gfx.mixRgb(rgb, 0xFFFFFF, 0.3f), 0xFF000000 | rgb, s.ps);
                if (diff < 24) Gfx.ring(c, cx - 2, cyy - 2, SZ + 4, SZ + 4, (SZ + 4) / 2f, Gfx.ringPx(s.ps) + 1, 0xFFFFFFFF, s.ps);
                else if (hv) Gfx.ring(c, cx - 1.5f, cyy - 1.5f, SZ + 3, SZ + 3, (SZ + 3) / 2f, Gfx.ringPx(s.ps), 0xAAFFFFFF, s.ps);
            }
        }

        @Override
        boolean down(LumenScreen s, double mx, double my, int b) {
            for (int i = 0; i < hex.length; i++) {
                if (mx >= sx(i) - 2 && mx < sx(i) + SZ + 2 && my >= sy(i) - 2 && my < sy(i) + SZ + 2) {
                    pick.accept(hex[i]);
                    return true;
                }
            }
            return false;
        }
    }

    private static final class Preview extends W {
        Preview() {
            h = 100;
        }

        @Override
        void draw(LumenScreen s, DrawContext c, double mx, double my) {
            LumenConfig cfg = LumenConfig.get();
            int top = Gfx.withA(cfg.themeLight, 0xA0), bot = Gfx.withA(cfg.themeDark, 0xD0);
            // «Lumen» плашка как в HUD
            float pw = s.textRenderer.getWidth("Lumen") + 16, ph = s.textRenderer.fontHeight + 8;
            Gfx.rrect(c, x + 2, y + 2, pw, ph, ph / 2f, top, bot, s.ps);
            Gfx.ring(c, x + 2, y + 2, pw, ph, ph / 2f, Gfx.ringPx(s.ps), 0x66FFFFFF, s.ps);
            c.drawText(s.textRenderer, "Lumen", (int) x + 10, (int) y + 6, 0xFFFFFFFF, true);
            // мини-Target HUD
            float cx = x + 2, cyy = y + 28, cw = w - 4, ch = 44;
            Gfx.rrect(c, cx, cyy, cw, ch, 10, top, bot, s.ps);
            Gfx.rrect(c, cx + 1, cyy + 1, cw - 2, ch * 0.45f, 9, 0x38FFFFFF, 0x04FFFFFF, s.ps);
            Gfx.ring(c, cx, cyy, cw, ch, 10, Gfx.ringPx(s.ps), 0x77FFFFFF, s.ps);
            Gfx.rrect(c, cx + 6, cyy + 7, 30, 30, 5, 0xFF000000 | cfg.themeRgb, 0xFF000000 | cfg.themeRgb, s.ps);
            c.drawText(s.textRenderer, "Target", (int) cx + 42, (int) cyy + 8, 0xFFFFFFFF, true);
            Gfx.rrect(c, cx + 42, cyy + 22, cw - 50, 6, 3, 0x66000000, 0x44000000, s.ps);
            Gfx.rrect(c, cx + 42, cyy + 22, (cw - 50) * 0.7f, 6, 3, 0xFF9CFFB0, 0xFF3FD97A, s.ps);
            c.drawText(s.textRenderer, "14/20", (int) cx + 42, (int) cyy + 31, 0xFFE6D2FF, false);
            // «выключатель»
            Gfx.rrect(c, x + 2, y + 80, 26, 12, 6, Gfx.withA(cfg.themeLight, 0xE6), Gfx.withA(cfg.themeRgb, 0xE6), s.ps);
            Gfx.rrect(c, x + 2 + 14, y + 81.5f, 10, 10, 5, 0xFFFFFFFF, 0xFFE4DAFF, s.ps);
        }
    }
}

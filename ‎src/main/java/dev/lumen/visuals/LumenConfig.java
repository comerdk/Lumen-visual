package dev.lumen.visuals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Настройки хранятся в config/lumenvisuals.json */
public final class LumenConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static LumenConfig instance = new LumenConfig();

    // модули
    public boolean esp = true;
    public boolean tracers = true;
    public boolean nametags = true;
    public boolean fullbright = true;

    // фильтры
    public boolean espPlayers = true;
    public boolean espHostile = true;
    public boolean espPassive = false;
    public boolean showInvisible = true;
    public boolean showDistance = true;
    public boolean hud = true;
    public boolean tracersFromCrosshair = false;

    // параметры
    public int range = 128;
    public double fullbrightGamma = 100.0;

    // цвета (hex RRGGBB)
    public String playerColor = "39FF6B";
    public String hostileColor = "FF3B4D";
    public String passiveColor = "55D6FF";

    // target ESP / target HUD
    public boolean tesp = true;
    public String tespStyle = "ring"; // ring | box | both
    public String tespColor = "B388FF";
    public boolean thud = true;
    public int thudX = 60;
    public int thudY = 40;
    public float thudScale = 1.0f;
    public String thudStyle = "glass"; // glass | flat
    public String thudColor = "B388FF";
    public boolean thudSkin = true;
    public float targetHold = 4.0f;

    // world visuals
    public boolean world = true;
    public boolean worldFog = true;
    public int fogStart = 8;
    public int fogEnd = 70;
    public String fogColor = "3B1580";
    public boolean worldTime = true;
    public int worldTimeTicks = 16500;
    public String particleType = "snow";
    public int particleCount = 8;
    public String tintColor = "7A3FE0";
    public int tintAlpha = 24;
    public boolean vignette = true;
    public int worldSat = 125;            // 0..200 %, насыщенность атмосферы (туман, небо, плазма, оверлей)
    public int smokeCount = 0;            // 0..30
    public String smokeType = "smoke";    // smoke | large | cozy
    public String plasma = "nebula";      // off | aurora | nebula | solar | toxic
    public int plasmaIntensity = 55;      // 0..100
    public int plasmaSpeed = 100;         // 20..300 %

    // тема интерфейса (HSV)
    public int themeH = 268;
    public int themeS = 62;
    public int themeV = 100;
    public boolean followTheme = true;    // цвет Target ESP / Target HUD берётся из темы
    public int glassOpacity = 70;         // 20..95
    public boolean guiAnim = true;        // «жидкие» блики в меню

    public int cfgVer = 0;

    // собственные частицы
    public int snowCount = 120;           // 0..400
    public int starCount = 40;            // 0..150
    public int snowSize = 100;            // 50..200 %
    public int particleSpeed = 100;       // 20..300 %
    public int worldDark = 0;             // 0..70 % затемнение мира

    // Entity ESP
    public String espMode = "fill";       // fill | box
    public int espFillAlpha = 45;         // 10..80 %
    public int espLine = 2;               // 1..6 (x0.5 GUI px)
    public boolean espGlow = true;

    // View model
    public boolean vm = true;
    public int vmX = 0, vmY = 0, vmZ = 0;       // -100..100 (x0.01 блока)
    public int vmScale = 100;                   // 30..200 %
    public int vmRotX = 0, vmRotY = 0, vmRotZ = 0; // -180..180
    public String vmSwing = "smooth";          // vanilla|smooth|slow|snap|stab|slash|spin|bounce|none
    public int vmSwingSpeed = 100;              // 100..300 %
    public boolean vmNoEquip = false;

    // косметика
    public boolean cosHalo = true;
    public boolean cosAura = true;
    public boolean cosTrail = false;
    public int cosSize = 100;             // 50..200 %
    public int cosSpeed = 100;            // 20..300 %
    public boolean cosFollowTheme = true;
    public String cosColor = "FFD6F5";

    public transient int playerArgb, hostileArgb, passiveArgb, tespArgb, thudArgb, fogRgb, tintRgb;
    public transient int themeRgb, themeLight, themeDark, cosRgb;

    public static int parseHex(String s) {
        try {
            String h = s.replace("#", "");
            if (h.length() != 6) return -1;
            return Integer.parseInt(h, 16);
        } catch (Exception e) {
            return -1;
        }
    }

    public void resetThud() {
        thud = true; thudX = 60; thudY = 40; thudScale = 1.0f;
        thudStyle = "glass"; thudColor = "B388FF"; thudSkin = true; targetHold = 4.0f;
    }

    public void setThemeHex(String hex) {
        int rgb = parseHex(hex);
        if (rgb < 0) return;
        float[] hsv = Gfx.rgbToHsv(rgb);
        themeH = Math.round(hsv[0] * 360f) % 360;
        themeS = Math.round(hsv[1] * 100f);
        themeV = Math.round(hsv[2] * 100f);
        recolor();
    }

    public boolean applyPreset(String n) {
        switch (n) {
            case "newyear" -> { world = true; worldFog = true; fogStart = 8; fogEnd = 70; fogColor = "3B1580";
                worldTime = true; worldTimeTicks = 16500; snowCount = 140; starCount = 45; smokeCount = 0;
                tintColor = "7A3FE0"; tintAlpha = 24; vignette = true; worldSat = 110; worldDark = 0;
                plasma = "nebula"; plasmaIntensity = 55; }
            case "sunset" -> { world = true; worldFog = true; fogStart = 20; fogEnd = 88; fogColor = "C55BD6";
                worldTime = true; worldTimeTicks = 12800; snowCount = 0; starCount = 60; smokeCount = 3;
                tintColor = "FF8AD8"; tintAlpha = 18; vignette = true; worldSat = 105; worldDark = 0;
                plasma = "solar"; plasmaIntensity = 50; }
            case "aurora" -> { world = true; worldFog = true; fogStart = 15; fogEnd = 80; fogColor = "1B2A6B";
                worldTime = true; worldTimeTicks = 18000; snowCount = 40; starCount = 70; smokeCount = 0;
                tintColor = "3AC7A0"; tintAlpha = 14; vignette = true; worldSat = 115; worldDark = 0;
                plasma = "aurora"; plasmaIntensity = 70; }
            case "sakura" -> { world = true; worldFog = true; fogStart = 25; fogEnd = 95; fogColor = "F2B5D4";
                worldTime = true; worldTimeTicks = 6000; snowCount = 60; starCount = 20; smokeCount = 0;
                tintColor = "FFC2E2"; tintAlpha = 14; vignette = true; worldSat = 100; worldDark = 0;
                plasma = "off"; }
            case "neon" -> { world = true; worldFog = true; fogStart = 10; fogEnd = 75; fogColor = "06120C";
                worldTime = true; worldTimeTicks = 15000; snowCount = 0; starCount = 70; smokeCount = 0;
                tintColor = "00FF6A"; tintAlpha = 8; vignette = true; worldSat = 65; worldDark = 28;
                plasma = "off"; }
            case "off" -> world = false;
            default -> { return false; }
        }
        recolor();
        return true;
    }

    public static LumenConfig get() {
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("lumenvisuals.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                LumenConfig c = GSON.fromJson(Files.readString(p), LumenConfig.class);
                if (c != null) instance = c;
            }
        } catch (Exception ignored) {
        }
        if (instance.cfgVer < 2) {
            instance.cfgVer = 2;
            instance.playerColor = "39FF6B";
            instance.hostileColor = "FF3B4D";
            instance.passiveColor = "55D6FF";
            instance.espMode = "fill";
        }
        instance.recolor();
        instance.save();
    }

    public void save() {
        try {
            Files.writeString(path(), GSON.toJson(this));
        } catch (IOException ignored) {
        }
    }

    public void recolor() {
        playerArgb = parse(playerColor, 0xB388FF);
        hostileArgb = parse(hostileColor, 0xFF5555);
        passiveArgb = parse(passiveColor, 0x55FF99);
        if (range < 8) range = 8;
        tespArgb = parse(tespColor, 0xB388FF);
        thudArgb = parse(thudColor, 0xB388FF);
        fogRgb = parse(fogColor, 0x3B1580) & 0xFFFFFF;
        tintRgb = parse(tintColor, 0x7A3FE0) & 0xFFFFFF;
        thudScale = Math.max(0.5f, Math.min(3f, thudScale));
        fogStart = Math.max(0, Math.min(98, fogStart));
        fogEnd = Math.max(fogStart + 1, Math.min(100, fogEnd));
        tintAlpha = Math.max(0, Math.min(120, tintAlpha));
        particleCount = Math.max(0, Math.min(60, particleCount));
        targetHold = Math.max(0.5f, Math.min(30f, targetHold));
        worldSat = Math.max(0, Math.min(200, worldSat));
        smokeCount = Math.max(0, Math.min(30, smokeCount));
        plasmaIntensity = Math.max(0, Math.min(100, plasmaIntensity));
        plasmaSpeed = Math.max(20, Math.min(300, plasmaSpeed));
        glassOpacity = Math.max(20, Math.min(95, glassOpacity));
        themeH = ((themeH % 360) + 360) % 360;
        themeS = Math.max(0, Math.min(100, themeS));
        themeV = Math.max(20, Math.min(100, themeV));
        themeRgb = net.minecraft.util.math.MathHelper.hsvToRgb(themeH / 360f, themeS / 100f, themeV / 100f) & 0xFFFFFF;
        themeLight = Gfx.mixRgb(themeRgb, 0xFFFFFF, 0.45f);
        themeDark = Gfx.mulRgb(themeRgb, 0.28f);
        snowCount = Math.max(0, Math.min(400, snowCount));
        starCount = Math.max(0, Math.min(150, starCount));
        snowSize = Math.max(50, Math.min(200, snowSize));
        particleSpeed = Math.max(20, Math.min(300, particleSpeed));
        worldDark = Math.max(0, Math.min(70, worldDark));
        espFillAlpha = Math.max(10, Math.min(80, espFillAlpha));
        espLine = Math.max(1, Math.min(6, espLine));
        vmScale = Math.max(30, Math.min(200, vmScale));
        vmSwingSpeed = Math.max(100, Math.min(300, vmSwingSpeed));
        cosSize = Math.max(50, Math.min(200, cosSize));
        cosSpeed = Math.max(20, Math.min(300, cosSpeed));
        cosRgb = cosFollowTheme ? themeLight : (parse(cosColor, 0xFFD6F5) & 0xFFFFFF);
        if (followTheme) {
            tespArgb = 0xFF000000 | themeRgb;
            thudArgb = 0xFF000000 | themeRgb;
        }
    }

    private static int parse(String s, int def) {
        try {
            return 0xFF000000 | (Integer.parseInt(s.replace("#", ""), 16) & 0xFFFFFF);
        } catch (Exception e) {
            return 0xFF000000 | def;
        }
    }
}

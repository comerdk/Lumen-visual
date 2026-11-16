
package dev.lumen.visuals;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.Set;

/**
 * Настройка через чат.
 * /lumen thud x 80
 * .thud x 80
 * .lumen thud x 80
 */
public final class LumenCommands {
    private static final Set<String> ROOTS = Set.of(
            "lumen", "thud", "tesp", "world",
            "theme", "gui", "menu", "cfg"
    );

    private LumenCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                dispatcher.register(ClientCommandManager.literal("lumen")
                        .executes(ctx -> {
                            handle("");
                            return 1;
                        })
                        .then(ClientCommandManager.argument(
                                        "args",
                                        StringArgumentType.greedyString()
                                )
                                .executes(ctx -> {
                                    handle(StringArgumentType.getString(ctx, "args"));
                                    return 1;
                                }))));

        ClientSendMessageEvents.ALLOW_CHAT.register(msg -> {
            if (msg.length() > 1 && msg.charAt(0) == '.') {
                String body = msg.substring(1).trim();
                String root = body.split("\\s+")[0].toLowerCase();

                if (ROOTS.contains(root)) {
                    handle(body);
                    return false;
                }
            }
            return true;
        });
    }

    static void handle(String line) {
        String trimmed = line.trim();
        String[] t = trimmed.isEmpty()
                ? new String[0]
                : trimmed.split("\\s+");

        int i = (t.length > 0 && t[0].equalsIgnoreCase("lumen")) ? 1 : 0;

        if (i >= t.length) {
            help();
            return;
        }

        String[] a = Arrays.copyOfRange(t, i + 1, t.length);

        switch (t[i].toLowerCase()) {
            case "cfg" -> cfg(a);
            case "thud" -> thud(a);
            case "tesp" -> tesp(a);
            case "world" -> world(a);
            case "theme" -> theme(a);

            case "gui", "menu" -> {
                LumenVisualsClient.requestMenu();
                say("Открываю меню…");
            }

            case "help" -> help();
            default -> say("Неизвестная команда. Напиши .lumen help");
        }

        LumenConfig c = LumenConfig.get();
        c.recolor();
        c.save();
    }

    // ---------- config profiles ----------

    private static void cfg(String[] a) {
        if (a.length == 0) {
            say("Использование: .cfg save|load|list|dir");
            return;
        }

        switch (a[0].toLowerCase()) {
            case "save" -> {
                if (a.length < 2) {
                    say("Использование: .cfg save имя");
                } else {
                    say(ConfigProfiles.save(a[1])
                            ? "Профиль сохранён: " + a[1]
                            : "Не удалось сохранить профиль");
                }
            }

            case "load" -> {
                if (a.length < 2) {
                    say("Использование: .cfg load имя");
                } else {
                    say(ConfigProfiles.load(a[1])
                            ? "Профиль загружен: " + a[1]
                            : "Не удалось загрузить профиль");
                }
            }

            case "list" ->
                    say("Профили: " + String.join(", ", ConfigProfiles.list()));

            case "dir" ->
                    say("Папка профилей: " + ConfigProfiles.directoryPath());

            default ->
                    say("Команды: .cfg save имя | load имя | list | dir");
        }
    }

    // ---------- thud ----------

    private static void thud(String[] a) {
        LumenConfig c = LumenConfig.get();

        if (a.length == 0) {
            say("thud " + onOff(c.thud)
                    + " x=" + c.thudX
                    + " y=" + c.thudY
                    + " scale=" + c.thudScale
                    + " style=" + c.thudStyle
                    + " skin=" + onOff(c.thudSkin)
                    + " hold=" + c.targetHold + "s");
            return;
        }

        switch (a[0].toLowerCase()) {
            case "on" -> {
                c.thud = true;
                say("Target HUD: ON");
            }

            case "off" -> {
                c.thud = false;
                say("Target HUD: OFF");
            }

            case "x" -> {
                c.thudX = (int) num(a, 1, c.thudX);
                say("x = " + c.thudX);
            }

            case "y" -> {
                c.thudY = (int) num(a, 1, c.thudY);
                say("y = " + c.thudY);
            }

            case "scale" -> {
                c.thudScale = (float) num(a, 1, c.thudScale);
                c.recolor();
                say("scale = " + c.thudScale);
            }

            case "hold" -> {
                c.targetHold = (float) num(a, 1, c.targetHold);
                c.recolor();
                say("hold = " + c.targetHold + "s");
            }

            case "skin" -> {
                c.thudSkin = flag(a, 1, c.thudSkin);
                say("skin = " + onOff(c.thudSkin));
            }

            case "style" -> {
                if (a.length > 1 &&
                        (a[1].equalsIgnoreCase("glass")
                                || a[1].equalsIgnoreCase("flat"))) {
                    c.thudStyle = a[1].toLowerCase();
                    say("style = " + c.thudStyle);
                } else {
                    say("style: glass | flat");
                }
            }

            case "color" -> {
                String h = hex(a, 1);
                if (h != null) {
                    c.thudColor = h;
                    c.followTheme = false;
                    say("color = #" + h + " (тема для Target отключена)");
                }
            }

            case "reset" -> {
                c.resetThud();
                say("Target HUD сброшен");
            }

            default -> say("thud: on|off|x N|y N|scale F|style glass|flat|color HEX|skin on|off|hold SEC|reset");
        }
    }

    // ---------- tesp ----------

    private static void tesp(String[] a) {
        LumenConfig c = LumenConfig.get();

        if (a.length == 0) {
            say("tesp " + onOff(c.tesp)
                    + " style=" + c.tespStyle
                    + " crystals=" + c.crystalCount
                    + " size=" + c.crystalSize + "%"
                    + " speed=" + c.crystalSpeed + "%"
                    + " color=#" + c.tespColor);
            return;
        }

        switch (a[0].toLowerCase()) {
            case "on" -> {
                c.tesp = true;
                say("Target ESP: ON");
            }

            case "off" -> {
                c.tesp = false;
                say("Target ESP: OFF");
            }

            case "style" -> {
                if (a.length > 1 &&
                        Set.of("crystal", "ring", "box", "both").contains(a[1].toLowerCase())) {
                    c.tespStyle = a[1].toLowerCase();
                    say("style = " + c.tespStyle);
                } else {
                    say("style: crystal | ring | box | both");
                }
            }

            case "count" -> {
                if (a.length > 1) try {
                    c.crystalCount = Integer.parseInt(a[1]);
                    say("crystals = " + c.crystalCount);
                } catch (Exception e) { say("count 3..12"); }
            }
            case "size" -> {
                if (a.length > 1) try {
                    c.crystalSize = Integer.parseInt(a[1]);
                    say("crystal size = " + c.crystalSize + "%");
                } catch (Exception e) { say("size 40..200"); }
            }
            case "speed" -> {
                if (a.length > 1) try {
                    c.crystalSpeed = Integer.parseInt(a[1]);
                    say("crystal speed = " + c.crystalSpeed + "%");
                } catch (Exception e) { say("speed 20..300"); }
            }

            case "color" -> {
                String h = hex(a, 1);
                if (h != null) {
                    c.tespColor = h;
                    c.followTheme = false;
                    say("color = #" + h + " (тема для Target отключена)");
                }
            }

            default -> say("tesp: on|off|style crystal|ring|box|both|count|size|speed|color HEX");
        }
    }

    // ---------- world ----------

    private static void world(String[] a) {
        LumenConfig c = LumenConfig.get();

        if (a.length == 0) {
            say("world " + onOff(c.world)
                    + " fog=" + onOff(c.worldFog)
                    + " " + c.fogStart + "-" + c.fogEnd + "%"
                    + " time=" + (c.worldTime ? c.worldTimeTicks : "off")
                    + " snow=" + c.snowCount
                    + " stars=" + c.starCount
                    + " tint=#" + c.tintColor + "/" + c.tintAlpha);
            return;
        }

        switch (a[0].toLowerCase()) {
            case "on" -> {
                c.world = true;
                say("World visuals: ON");
            }

            case "off" -> {
                c.world = false;
                say("World visuals: OFF");
            }

            case "preset" -> {
                if (a.length > 1 && c.applyPreset(a[1].toLowerCase())) {
                    say("Пресет: " + a[1].toLowerCase());
                } else {
                    say("Пресеты: newyear, sunset, aurora, sakura, neon, off");
                }
            }

            case "fog" -> {
                if (a.length >= 3) {
                    c.fogStart = (int) num(a, 1, c.fogStart);
                    c.fogEnd = (int) num(a, 2, c.fogEnd);
                    c.worldFog = true;
                    c.recolor();
                    say("fog " + c.fogStart + "% - " + c.fogEnd + "%");
                } else if (a.length == 2) {
                    c.worldFog = flag(a, 1, c.worldFog);
                    say("fog = " + onOff(c.worldFog));
                } else {
                    say("fog: on|off|<старт%> <конец%>");
                }
            }

            case "fogcolor" -> {
                String h = hex(a, 1);
                if (h != null) {
                    c.fogColor = h;
                    say("fogcolor = #" + h);
                }
            }

            case "time" -> {
                if (a.length < 2) {
                    say("time: off | day | noon | sunset | night | dawn | 0-24000");
                    break;
                }

                if (a[1].equalsIgnoreCase("off")) {
                    c.worldTime = false;
                    say("time: ваше время сервера");
                    break;
                }

                Integer v = timeValue(a[1]);

                if (v == null) {
                    say("time: off | day | noon | sunset | night | dawn | 0-24000");
                } else {
                    c.worldTime = true;
                    c.worldTimeTicks = v;
                    say("time = " + v);
                }
            }

            case "snow" -> {
                c.snowCount = (int) num(a, 1, c.snowCount);
                c.recolor();
                say("snow = " + c.snowCount);
            }

            case "stars" -> {
                c.starCount = (int) num(a, 1, c.starCount);
                c.recolor();
                say("stars = " + c.starCount);
            }

            case "dark" -> {
                c.worldDark = (int) num(a, 1, c.worldDark);
                c.recolor();
                say("dark = " + c.worldDark + "%");
            }

            case "tint" -> {
                String h = hex(a, 1);
                if (h != null) {
                    c.tintColor = h;

                    if (a.length > 2) {
                        c.tintAlpha = (int) num(a, 2, c.tintAlpha);
                    }

                    c.recolor();
                    say("tint = #" + h + " alpha " + c.tintAlpha);
                }
            }

            case "vignette" -> {
                c.vignette = flag(a, 1, c.vignette);
                say("vignette = " + onOff(c.vignette));
            }

            case "sat" -> {
                c.worldSat = (int) num(a, 1, c.worldSat);
                c.recolor();
                say("sat = " + c.worldSat + "%");
            }

            case "smoke" -> {
                if (a.length < 2) {
                    say("smoke: smoke|large|cozy|off [кол-во]");
                    break;
                }

                String sm = a[1].toLowerCase();

                if (sm.equals("off")) {
                    c.smokeCount = 0;
                    say("smoke: off");
                } else if (Set.of("smoke", "large", "cozy").contains(sm)) {
                    c.smokeType = sm;
                    c.smokeCount = a.length > 2
                            ? (int) num(a, 2, c.smokeCount)
                            : Math.max(c.smokeCount, 6);
                    c.recolor();
                    say("smoke = " + sm + " x" + c.smokeCount);
                } else {
                    say("smoke: smoke|large|cozy|off [кол-во]");
                }
            }

            case "plasma" -> {
                if (a.length < 2) {
                    say("plasma: aurora|nebula|solar|toxic|off [яркость 0-100]");
                    break;
                }

                String pv = a[1].toLowerCase();

                if (Set.of("off", "aurora", "nebula", "solar", "toxic").contains(pv)) {
                    c.plasma = pv;

                    if (a.length > 2) {
                        c.plasmaIntensity = (int) num(a, 2, c.plasmaIntensity);
                    }

                    c.recolor();
                    say("plasma = " + pv + " " + c.plasmaIntensity + "%");
                } else {
                    say("plasma: aurora|nebula|solar|toxic|off [яркость 0-100]");
                }
            }

            case "plasmaspeed" -> {
                c.plasmaSpeed = (int) num(a, 1, c.plasmaSpeed);
                c.recolor();
                say("plasmaspeed = " + c.plasmaSpeed + "%");
            }

            default -> say("world: on|off|preset N|sat N|fog ..|fogcolor HEX|time ..|snow N|stars N|dark N|smoke ..|plasma ..|tint HEX [A]|vignette on|off");
        }
    }

    // ---------- theme ----------

    private static void theme(String[] a) {
        LumenConfig c = LumenConfig.get();

        if (a.length == 0) {
            say("theme #" + String.format("%06X", c.themeRgb)
                    + " (h=" + c.themeH
                    + " s=" + c.themeS
                    + " v=" + c.themeV + ")");
            return;
        }

        if (a[0].equalsIgnoreCase("reset")) {
            c.themeH = 268;
            c.themeS = 62;
            c.themeV = 100;
            c.recolor();
            say("theme: сброшена");
            return;
        }

        if (a[0].equalsIgnoreCase("hsv") && a.length >= 4) {
            c.themeH = (int) num(a, 1, c.themeH);
            c.themeS = (int) num(a, 2, c.themeS);
            c.themeV = (int) num(a, 3, c.themeV);
            c.recolor();
            say("theme: h=" + c.themeH
                    + " s=" + c.themeS
                    + " v=" + c.themeV);
            return;
        }

        String h = hex(a, 0);

        if (h != null) {
            c.setThemeHex(h);
            say("theme = #" + h);
        }
    }

    // ---------- help ----------

    private static void help() {
        say("Меню: .gui (или RShift). Команды можно с точкой: .thud x 80 или /lumen thud x 80");
        say("thud on|off|x N|y N|scale F|style glass|flat|color HEX|skin on|off|hold SEC|reset");
        say("tesp on|off|style crystal|ring|box|both|count N|size N|speed N|color HEX");
        say("theme HEX | theme hsv H S V | theme reset");
        say("world on|off|preset newyear|sunset|aurora|sakura|off | sat 0-200");
        say("world fog <старт%> <конец%> | fogcolor HEX | time day|sunset|night|N | snow N | stars N | dark N | tint HEX [A]");
        say("world smoke smoke|large|cozy|off [N] | plasma aurora|nebula|solar|toxic|off [яркость] | plasmaspeed N");
    }

    // ---------- утилиты ----------

    private static String onOff(boolean b) {
        return b ? "ON" : "OFF";
    }

    private static boolean flag(String[] a, int i, boolean def) {
        if (i >= a.length) return !def;

        String s = a[i].toLowerCase();

        if (s.equals("on") || s.equals("true") || s.equals("1")) return true;
        if (s.equals("off") || s.equals("false") || s.equals("0")) return false;

        say("Нужно on или off");
        return def;
    }

    private static double num(String[] a, int i, double def) {
        if (i >= a.length) {
            say("Не хватает числа");
            return def;
        }

        try {
            return Double.parseDouble(a[i].replace(',', '.'));
        } catch (NumberFormatException e) {
            say("Не число: " + a[i]);
            return def;
        }
    }

    private static String hex(String[] a, int i) {
        if (i >= a.length || LumenConfig.parseHex(a[i]) < 0) {
            say("Нужен цвет в формате RRGGBB, например B388FF");
            return null;
        }

        return a[i].replace("#", "").toUpperCase();
    }

    private static Integer timeValue(String s) {
        switch (s.toLowerCase()) {
            case "day": return 1000;
            case "noon": return 6000;
            case "sunset": return 12800;
            case "night": return 16500;
            case "midnight": return 18000;
            case "dawn": return 23000;

            default:
                try {
                    return Math.floorMod(Integer.parseInt(s), 24000);
                } catch (NumberFormatException e) {
                    return null;
                }
        }
    }

    private static void say(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player != null) {
            mc.player.sendMessage(
                    Text.literal("[Lumen] " + s)
                            .formatted(Formatting.LIGHT_PURPLE),
                    false
            );
        }
    }
}

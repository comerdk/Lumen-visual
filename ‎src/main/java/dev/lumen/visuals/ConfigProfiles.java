package dev.lumen.visuals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ConfigProfiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR =
            FabricLoader.getInstance().getConfigDir().resolve("Lumen");

    private ConfigProfiles() {}

    private static Path file(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException(
                    "Имя: только латинские буквы, цифры, _ и - (до 32 символов)"
            );
        }
        return DIR.resolve(name + ".cfg");
    }

    public static void ensureDefault() {
        try {
            Files.createDirectories(DIR);
            Path path = file("default");

            if (!Files.exists(path)) {
                LumenConfig defaults = new LumenConfig();

                // Все переключатели выключены в стартовом конфиге.
                for (var field : LumenConfig.class.getFields()) {
                    if (field.getType() == boolean.class) {
                        field.setBoolean(defaults, false);
                    }
                }

                defaults.recolor();
                Files.writeString(path, GSON.toJson(defaults));
            }
        } catch (Exception e) {
            LumenVisualsClient.LOGGER.error("Не удалось создать default.cfg", e);
        }
    }

    public static void save(String name) throws IOException {
        Files.createDirectories(DIR);
        Files.writeString(file(name), GSON.toJson(LumenConfig.get()));
    }

    public static void load(String name) throws IOException {
        LumenConfig loaded = GSON.fromJson(
                Files.readString(file(name)), LumenConfig.class
        );

        if (loaded == null) {
            throw new IOException("Конфиг пустой или повреждён");
        }

        LumenConfig current = LumenConfig.get();

        // Копируем все настройки, не заменяя сам объект конфига.
        for (var field : LumenConfig.class.getFields()) {
            int modifiers = field.getModifiers();

            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                continue;
            }

            try {
                field.set(current, field.get(loaded));
            } catch (IllegalAccessException e) {
                throw new IOException("Не удалось загрузить настройку: " + field.getName(), e);
            }
        }

        current.recolor();
        current.save();
    }

    public static List<String> list() throws IOException {
        Files.createDirectories(DIR);

        try (var files = Files.list(DIR)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.toLowerCase().endsWith(".cfg"))
                    .map(n -> n.substring(0, n.length() - 4))
                    .sorted()
                    .toList();
        }
    }

    public static String directory() {
        return DIR.toAbsolutePath().toString();
    }
              }

package dev.lumen.visuals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class ConfigProfiles {
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();

    private static Path directory() {
        return FabricLoader.getInstance()
                .getConfigDir().resolve("Lumen");
    }

    private static Path profile(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("Invalid profile name");
        }
        return directory().resolve(name + ".cfg");
    }

    public static void ensureDefault() {
        try {
            Files.createDirectories(directory());
            Path path = profile("default");

            if (!Files.exists(path)) {
                LumenConfig config = new LumenConfig();

                for (Field field : LumenConfig.class.getFields()) {
                    if (field.getType() == boolean.class
                            && !Modifier.isStatic(field.getModifiers())) {
                        field.setBoolean(config, false);
                    }
                }

                config.save();
                Files.writeString(path, GSON.toJson(config));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean save(String name) {
        try {
            Files.createDirectories(directory());
            Files.writeString(profile(name),
                    GSON.toJson(LumenConfig.get()));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean load(String name) {
        try {
            Path path = profile(name);
            if (!Files.exists(path)) return false;

            LumenConfig loaded =
                    GSON.fromJson(Files.readString(path), LumenConfig.class);

            if (loaded == null) return false;

            LumenConfig current = LumenConfig.get();

            for (Field field : LumenConfig.class.getFields()) {
                int modifiers = field.getModifiers();

                if (Modifier.isStatic(modifiers)
                        || Modifier.isTransient(modifiers)) continue;

                field.set(current, field.get(loaded));
            }

            current.recolor();
            current.save();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static List<String> list() {
        try {
            Files.createDirectories(directory());

            return Files.list(directory())
                    .filter(p -> p.toString().endsWith(".cfg"))
                    .map(p -> p.getFileName().toString()
                            .replaceFirst("\\.cfg$", ""))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }

    public static String directoryPath() {
        return directory().toString();
    }

    private ConfigProfiles() {}
                                }

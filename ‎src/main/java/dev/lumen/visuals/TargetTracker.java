package dev.lumen.visuals;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.ActionResult;

/** Цель = существо под прицелом или то, по которому ты бил. Держится несколько секунд. */
public final class TargetTracker {
    private static LivingEntity target;
    private static long lastNanos;

    private TargetTracker() {}

    public static void init() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient() && entity instanceof LivingEntity le && le != MinecraftClient.getInstance().player) {
                set(le);
            }
            return ActionResult.PASS;
        });
    }

    public static void set(LivingEntity e) {
        target = e;
        lastNanos = System.nanoTime();
    }

    public static LivingEntity get() {
        return target;
    }

    public static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            target = null;
            return;
        }
        Entity look = mc.targetedEntity;
        if (look instanceof LivingEntity le && !(le instanceof ArmorStandEntity)) set(le);

        if (target != null) {
            float hold = LumenConfig.get().targetHold;
            if (target.isRemoved() || !target.isAlive() || (System.nanoTime() - lastNanos) / 1.0e9 > hold) {
                target = null;
            }
        }
    }
}

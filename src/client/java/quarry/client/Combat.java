package quarry.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/** Статистика ударов и эффекты при ударе. Ничего не автоматизирует: только считает и рисует. */
public class Combat {
    public static int combo = 0;
    public static double reach = 0;
    public static LivingEntity target = null;

    /** Когда цель последний раз была под прицелом (мс), нужно для плавного затухания ESP. */
    public static long seenMs() {
        return targetSeen;
    }

    private static long lastHitMs = 0, lastAttackMs = 0, targetSeen = 0;
    private static boolean prevAttack = false;
    private static int prevSelfHurt = 0;
    private static final Map<Integer, Integer> PREV = new HashMap<>();
    private static final ParticleOptions[] FX = {
            ParticleTypes.CRIT, ParticleTypes.ELECTRIC_SPARK, ParticleTypes.HEART,
            ParticleTypes.FLAME, ParticleTypes.ENCHANTED_HIT};

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            combo = 0;
            target = null;
            return;
        }
        long now = System.currentTimeMillis();
        boolean attack = mc.options.keyAttack.isDown();
        if (attack && !prevAttack) lastAttackMs = now;
        prevAttack = attack;

        Entity pick = mc.crosshairPickEntity;
        if (pick instanceof LivingEntity le && le != p) {
            target = le;
            targetSeen = now;
            int prev = PREV.getOrDefault(le.getId(), 0);
            if (le.hurtTime > 0 && prev == 0 && now - lastAttackMs < 400) onHit(mc, p, le, now);
            PREV.put(le.getId(), le.hurtTime);
            if (PREV.size() > 64) PREV.clear();
        } else if (now - targetSeen > 1500) {
            target = null;
        }

        if (p.hurtTime > 0 && prevSelfHurt == 0) combo = 0;
        prevSelfHurt = p.hurtTime;
        if (now - lastHitMs > 3000) combo = 0;
    }

    private static void onHit(Minecraft mc, LocalPlayer p, LivingEntity e, long now) {
        combo++;
        lastHitMs = now;
        Fx.hitFlash(e);
        Vec3 eye = p.getEyePosition();
        AABB b = e.getBoundingBox();
        double dx = Math.max(Math.max(b.minX - eye.x, 0), eye.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - eye.y, 0), eye.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - eye.z, 0), eye.z - b.maxZ);
        reach = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (Config.get("hitsound")) {
            SoundEvent[] sounds = {SoundEvents.EXPERIENCE_ORB_PICKUP, SoundEvents.ARROW_HIT_PLAYER,
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundEvents.PLAYER_LEVELUP, SoundEvents.ITEM_PICKUP};
            float vol = Config.setting("hitsound", "volume") / 5f, pitch = Config.setting("hitsound", "pitch") / 10f;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sounds[Config.setting("hitsound", "type")], pitch, vol));
        }

        if (Config.get("hit_fx")) {
            ParticleOptions type = FX[Config.setting("hit_fx", "type")];
            int n = Config.setting("hit_fx", "count");
            for (int i = 0; i < n; i++) {
                double rx = (mc.level.getRandom().nextDouble() - 0.5) * e.getBbWidth();
                double ry = mc.level.getRandom().nextDouble() * e.getBbHeight();
                double rz = (mc.level.getRandom().nextDouble() - 0.5) * e.getBbWidth();
                mc.levelRenderer.addParticle(type, true, e.getX() + rx, e.getY() + ry, e.getZ() + rz,
                        (mc.level.getRandom().nextDouble() - 0.5) * 0.2, 0.1, (mc.level.getRandom().nextDouble() - 0.5) * 0.2);
            }
        }
    }
}

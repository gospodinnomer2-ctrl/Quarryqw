package quarry.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Красивые ники и цифры урона. Всё рисуется на экране (а не в мире): мировые координаты
 * переводятся в экранные с теми же матрицами, которыми игра рисует сцену.
 */
public class Nametags {
    private static final Matrix4f MV = new Matrix4f(), PRJ = new Matrix4f();
    private static Vec3 cam = Vec3.ZERO;
    private static float partial = 0f;
    private static boolean ok = false;

    private static final Map<Integer, Smooth> HP = new HashMap<>();
    private static final Map<Integer, Float> LAST_HP = new HashMap<>();
    private static final List<Floater> FLOATERS = new ArrayList<>();
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private record Floater(String text, double x, double y, double z, double dx, long at, int color) {
    }

    private record Tag(Player p, double sx, double sy, double dist) {
    }

    public static void init() {
        // запоминаем матрицы камеры текущего кадра: HUD рисуется сразу после мира
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            MV.set(RenderSystem.getModelViewMatrix());
            PRJ.set(RenderSystem.getProjectionMatrix());
            cam = ctx.camera().getPosition();
            partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            ok = true;
        });
    }

    /** Точка мира -> координаты экрана в GUI: {x, y, глубина}. Если точка за спиной, вернёт null. */
    public static float[] project(double x, double y, double z, int guiW, int guiH) {
        if (!ok) return null;
        Vector4f v = new Vector4f((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z), 1f);
        MV.transform(v);
        PRJ.transform(v);
        if (v.w <= 0.05f) return null;
        float nx = v.x / v.w, ny = v.y / v.w;
        if (Math.abs(nx) > 1.6f || Math.abs(ny) > 1.6f) return null;
        return new float[]{(nx * 0.5f + 0.5f) * guiW, (0.5f - ny * 0.5f) * guiH, v.w};
    }

    /** Позиция сущности с учётом сглаживания между тиками. */
    public static double[] pos(LivingEntity e) {
        return new double[]{Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()), Mth.lerp(partial, e.zo, e.getZ())};
    }

    /** Следим за здоровьем соседних существ: когда оно падает, появляется цифра урона. */
    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            FLOATERS.clear();
            LAST_HP.clear();
            return;
        }
        if (!Config.get("dmgnum")) {
            LAST_HP.clear();
            return;
        }
        double range = Config.setting("dmgnum", "range");
        Set<Integer> seen = new HashSet<>();
        for (net.minecraft.world.entity.Entity en : mc.level.entitiesForRendering()) {
            if (!(en instanceof LivingEntity le) || le == mc.player || !le.isAlive()) continue;
            if (le.distanceToSqr(mc.player) > range * range) continue;
            seen.add(le.getId());
            float hp = le.getHealth();
            Float prev = LAST_HP.put(le.getId(), hp);
            if (prev != null && prev - hp >= 0.5f && FLOATERS.size() < 30) {
                String t = (prev - hp) % 1f == 0f ? "-" + (int) (prev - hp) : String.format("-%.1f", prev - hp);
                double r = mc.level.getRandom().nextDouble() - 0.5;
                FLOATERS.add(new Floater(t, le.getX(), le.getY() + le.getBbHeight() * 0.85, le.getZ(), r * 0.8,
                        System.currentTimeMillis(), 0xFFFF5C5C));
            }
        }
        LAST_HP.keySet().retainAll(seen);
        HP.keySet().retainAll(seen);
    }

    public static void render(GuiGraphics ctx, Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        Font font = mc.font;
        int sw = ctx.guiWidth(), sh = ctx.guiHeight();
        if (Config.get("nametags")) tags(ctx, mc, font, sw, sh);
        if (Config.get("dmgnum")) floaters(ctx, font, sw, sh);
    }

    private static void tags(GuiGraphics ctx, Minecraft mc, Font font, int sw, int sh) {
        double range = Config.setting("nametags", "range");
        boolean own = Config.setting("nametags", "self") == 1 && !mc.options.getCameraType().isFirstPerson();
        List<Tag> list = new ArrayList<>();
        for (Player p : mc.level.players()) {
            if (p == mc.player && !own) continue;
            if (p.isSpectator() || p.isInvisibleTo(mc.player)) continue;
            double d = Math.sqrt(p.distanceToSqr(mc.player));
            if (d > range) continue;
            double[] q = pos(p);
            float[] s = project(q[0], q[1] + p.getBbHeight() + 0.42, q[2], sw, sh);
            if (s == null) continue;
            list.add(new Tag(p, s[0], s[1], d));
        }
        list.sort((a, b) -> Double.compare(b.dist(), a.dist()));
        for (Tag t : list) drawTag(ctx, font, t);
    }

    private static void drawTag(GuiGraphics ctx, Font font, Tag t) {
        Player p = t.p();
        float size = Config.setting("nametags", "size") / 100f;
        if (Config.setting("nametags", "scaling") == 1) {
            size *= Math.max(0.55f, Math.min(1.1f, 1.2f - (float) t.dist() * 0.011f));
        }
        boolean bar = Config.setting("nametags", "health") == 1;
        boolean items = Config.setting("nametags", "items") == 1;
        int acc = Config.accent();

        String name = Ui.trim(font, p.getDisplayName().getString(), 190);
        float hp = p.getHealth(), max = Math.max(1f, p.getMaxHealth());
        float frac = Math.max(0f, Math.min(1f, hp / max));
        float shown = HP.computeIfAbsent(p.getId(), k -> {
            Smooth s = new Smooth();
            s.snap(frac);
            return s;
        }).to(frac, 9f);
        String hs = hp % 1f == 0f ? String.valueOf((int) hp) : String.format("%.1f", hp);
        int hc = frac > 0.5f ? 0xFF7CE08A : frac > 0.25f ? 0xFFFFC857 : 0xFFFF6B6B;

        boolean quarry = Config.get("quarryusers") && Presence.isUser(p.getName().getString());
        int bw = quarry ? Ui.w(font, "\u25C6") + 5 : 0;
        int nw = Ui.width(font, name, true), hw = Ui.w(font, hs);
        int w = Math.max(64, nw + hw + 30 + bw);
        int h = bar ? 25 : 18;

        ItemStack[] stacks = new ItemStack[6];
        int ni = 0;
        if (items) {
            stacks[ni++] = p.getMainHandItem();
            for (EquipmentSlot s : SLOTS) stacks[ni++] = p.getItemBySlot(s);
            stacks[ni++] = p.getOffhandItem();
        }

        float fade = p.isDiscrete() ? 0.6f : 1f;
        Ui.GA = fade;
        ctx.pose().pushPose();
        ctx.pose().translate((float) t.sx(), (float) t.sy(), 0f);
        ctx.pose().scale(size, size, 1f);
        int x = -w / 2, y = -h - (items ? 15 : 3);

        Ui.panel(ctx, x, y, w, h, 8, 0xD0120D0B, (acc & 0x00FFFFFF) | 0x50000000);
        Ui.rrect(ctx, x + 6, y + 6, 2, 7, 1, acc);
        if (quarry) Ui.draw(ctx, font, "\u25C6", x + 12, y + 5, acc, false);
        Ui.text(ctx, font, name, x + 12 + bw, y + 5, 0xFFFFFFFF, 1f, true, false);
        Ui.draw(ctx, font, hs, x + w - 8 - hw, y + 5, hc, false);
        if (bar) {
            Ui.rrect(ctx, x + 8, y + 17, w - 16, 3, 1, 0xFF2A2420);
            Ui.rrect(ctx, x + 8, y + 17, Math.max(2, Math.round((w - 16) * shown)), 3, 1, hc);
        }
        if (items && ni > 0) {
            int rw = ni * 12 + 6;
            int rx = -rw / 2, ry = y + h + 3;
            Ui.rrect(ctx, rx, ry, rw, 13, 6, 0xC0120D0B);
            for (int i = 0; i < ni; i++) {
                if (stacks[i].isEmpty()) continue;
                ctx.pose().pushPose();
                ctx.pose().translate((float) (rx + 4 + i * 12), (float) (ry + 2), 0f);
                ctx.pose().scale(0.56f, 0.56f, 1f);
                ctx.renderItem(stacks[i], 0, 0);
                ctx.pose().popPose();
            }
        }
        ctx.pose().popPose();
        Ui.GA = 1f;
    }

    private static void floaters(GuiGraphics ctx, Font font, int sw, int sh) {
        long now = System.currentTimeMillis();
        FLOATERS.removeIf(f -> now - f.at() > 1100);
        float size = Config.setting("dmgnum", "size") / 100f;
        for (Floater f : FLOATERS) {
            float age = (now - f.at()) / 1100f;
            float rise = 1f - (1f - age) * (1f - age);
            float[] s = project(f.x() + f.dx() * rise, f.y() + 0.2 + rise * 0.9, f.z(), sw, sh);
            if (s == null) continue;
            float pop = age < 0.12f ? 1.5f - age / 0.12f * 0.5f : 1f;
            Ui.GA = Math.max(0f, 1f - Math.max(0f, age - 0.55f) / 0.45f);
            Ui.text(ctx, font, f.text(), Math.round(s[0] - Ui.width(font, f.text(), true) * size * pop / 2f), Math.round(s[1]),
                    f.color(), size * pop, true, true);
            Ui.GA = 1f;
        }
    }
}

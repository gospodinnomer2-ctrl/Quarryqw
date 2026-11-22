package quarry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Контуры хитбоксов и «таргет ESP».
 * Всё рисуется с проверкой глубины: за стенами ничего не видно.
 */
public class Hitbox {
    // стили таргет ESP (порядок совпадает с Config)
    private static final int GHOSTS = 0, DIAMOND = 1, DIAMONDS = 2, HALO = 3, ORBS = 4, DASHED = 5, SOLID = 6;

    private interface Emit {
        void run(VertexConsumer vc, Matrix4f m);
    }

    private record Item(double dist, Emit e) {
    }

    private static final List<Item> ITEMS = new ArrayList<>();
    // «вправо» и «вверх» камеры: нужны, чтобы свечения всегда были повёрнуты к игроку
    private static double RX, RY, RZ, UX, UY, UZ;
    private static int lastId = -1;
    private static long since = 0;

    public static void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(Hitbox::render);
    }

    private static int colorOf(int choice) {
        switch (choice) {
            case 1:
                return 0xFFFFFFFF;
            case 2:
                return 0xFFFF4B4B;
            case 3:
                return 0xFF4BE07C;
            default:
                return Config.accent();
        }
    }

    /** Цвет ESP: 0 — текущая тема, дальше цвета тем по порядку, потом белый и радуга. */
    private static int espColor(int choice, double phase) {
        int n = Config.THEME_COLORS.length;
        if (choice <= 0) return Config.accent();
        if (choice <= n) return Config.THEME_COLORS[choice - 1];
        if (choice == n + 1) return 0xFFFFFFFF;
        float h = (float) (((System.currentTimeMillis() / 5000.0) + phase) % 1.0);
        return hsv(h, 0.62f, 1f);
    }

    private static int hsv(float h, float s, float v) {
        float hh = (h - (float) Math.floor(h)) * 6f;
        int i = (int) Math.floor(hh);
        float f = hh - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0:
                r = v; g = t; b = p;
                break;
            case 1:
                r = q; g = v; b = p;
                break;
            case 2:
                r = p; g = v; b = t;
                break;
            case 3:
                r = p; g = q; b = v;
                break;
            case 4:
                r = t; g = p; b = v;
                break;
            default:
                r = v; g = p; b = q;
                break;
        }
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    private static void render(WorldRenderContext ctx) {
        boolean boxes = Config.get("hitbox");
        boolean esp = Config.get("target_esp");
        if (!boxes && !esp) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        MultiBufferSource consumers = ctx.consumers();
        PoseStack ps = ctx.matrixStack();
        if (consumers == null || ps == null) return;

        Vec3 cam = ctx.camera().getPosition();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        // цель берём из боевой статистики: она держится ещё секунду после того, как вы отвели прицел
        LivingEntity target = null;
        float fade = 0f;
        if (esp) {
            LivingEntity c = Combat.target;
            long now = System.currentTimeMillis();
            if (c != null && c.isAlive() && c != mc.player) {
                if (c.getId() != lastId) {
                    lastId = c.getId();
                    since = now;
                }
                long gone = now - Combat.seenMs();
                float fadeOut = gone < 900 ? 1f : Math.max(0f, 1f - (gone - 900) / 600f);
                float fadeIn = Math.min(1f, (now - since) / 250f);
                fade = fadeOut * fadeIn;
                target = c;
            } else {
                lastId = -1;
            }
        }
        int style = Config.setting("target_esp", "style");
        boolean boxStyle = style == DASHED || style == SOLID;

        if (boxes || (target != null && boxStyle)) {
            VertexConsumer vc = consumers.getBuffer(RenderType.lines());
            ps.pushPose();
            ps.translate(-cam.x, -cam.y, -cam.z);
            if (boxes) {
                int which = Config.setting("hitbox", "target");
                double range = Config.setting("hitbox", "range");
                float alpha = Config.setting("hitbox", "alpha") / 100f;
                int col = colorOf(Config.setting("hitbox", "color"));
                float r = ((col >> 16) & 255) / 255f, g = ((col >> 8) & 255) / 255f, b = (col & 255) / 255f;
                for (Entity e : mc.level.entitiesForRendering()) {
                    if (e == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
                    if (e.distanceToSqr(mc.player) > range * range) continue;
                    boolean isPlayer = e instanceof Player;
                    if (which == 0 && !isPlayer) continue;
                    if (which == 1 && !(e instanceof LivingEntity && !isPlayer)) continue;
                    ShapeRenderer.renderLineBox(ps, vc, movedBox(e, pt), r, g, b, alpha);
                }
            }
            if (target != null && boxStyle && fade > 0.01f) boxEsp(ps, vc, target, pt, style == DASHED, fade);
            ps.popPose();
        }

        if (target != null && !boxStyle && fade > 0.01f) glowEsp(ctx, ps, consumers, cam, target, pt, style, fade);
    }

    // ───────────────────────── рамки (старые стили) ─────────────────────────

    private static void boxEsp(PoseStack ps, VertexConsumer vc, LivingEntity t, float pt, boolean dashed, float fade) {
        int col = espColor(Config.setting("target_esp", "color"), 0);
        float r = ((col >> 16) & 255) / 255f, g = ((col >> 8) & 255) / 255f, b = (col & 255) / 255f;
        AABB box = movedBox(t, pt).inflate(0.05);
        double dash = Config.setting("target_esp", "dash") / 100.0;
        double phase = (System.currentTimeMillis() % 100000L) / 1000.0 * Config.setting("target_esp", "speed") * 0.05;
        if (dashed) {
            dashedBox(ps, vc, box.inflate(0.06), r, g, b, 0.30f * fade, dash * 1.6, dash * 0.8, phase);
            dashedBox(ps, vc, box, r, g, b, fade, dash, dash * 0.7, phase);
            dashedBox(ps, vc, box.inflate(0.012), r, g, b, fade, dash, dash * 0.7, phase);
        } else {
            ShapeRenderer.renderLineBox(ps, vc, box.inflate(0.06), r, g, b, 0.30f * fade);
            ShapeRenderer.renderLineBox(ps, vc, box, r, g, b, fade);
            ShapeRenderer.renderLineBox(ps, vc, box.inflate(0.012), r, g, b, fade);
        }
    }

    private static AABB movedBox(Entity e, float pt) {
        double dx = Mth.lerp(pt, e.xo, e.getX()) - e.getX();
        double dy = Mth.lerp(pt, e.yo, e.getY()) - e.getY();
        double dz = Mth.lerp(pt, e.zo, e.getZ()) - e.getZ();
        return e.getBoundingBox().move(dx, dy, dz);
    }

    private static void dashedBox(PoseStack ps, VertexConsumer vc, AABB b, float r, float g, float bl, float a,
                                  double dash, double gap, double phase) {
        double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
        for (double y : ys) for (double z : zs) dashLine(ps, vc, b.minX, y, z, b.maxX, y, z, r, g, bl, a, dash, gap, phase);
        for (double x : xs) for (double z : zs) dashLine(ps, vc, x, b.minY, z, x, b.maxY, z, r, g, bl, a, dash, gap, phase);
        for (double x : xs) for (double y : ys) dashLine(ps, vc, x, y, b.minZ, x, y, b.maxZ, r, g, bl, a, dash, gap, phase);
    }

    /** Одно ребро рамки пунктиром: короткие отрезки (вырожденные коробки) с зазорами. */
    private static void dashLine(PoseStack ps, VertexConsumer vc, double x1, double y1, double z1,
                                 double x2, double y2, double z2, float r, float g, float b, float a,
                                 double dash, double gap, double phase) {
        double len = Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1)));
        if (len <= 0) return;
        double period = Math.max(0.02, dash + gap);
        for (double s = -period + (phase % period); s < len; s += period) {
            double a0 = Math.max(0, s), a1 = Math.min(len, s + dash);
            if (a1 <= a0) continue;
            double f0 = a0 / len, f1 = a1 / len;
            AABB seg = new AABB(x1 + (x2 - x1) * f0, y1 + (y2 - y1) * f0, z1 + (z2 - z1) * f0,
                    x1 + (x2 - x1) * f1, y1 + (y2 - y1) * f1, z1 + (z2 - z1) * f1);
            ShapeRenderer.renderLineBox(ps, vc, seg, r, g, b, a);
        }
    }

    // ───────────────────────── свечения (призраки, ромбы, кольцо, шары) ─────────────────────────

    private static void glowEsp(WorldRenderContext ctx, PoseStack ps, MultiBufferSource consumers, Vec3 cam,
                                LivingEntity t, float pt, int style, float fade) {
        int choice = Config.setting("target_esp", "color");
        double speed = Config.setting("target_esp", "speed") / 8.0;
        double sc = Config.setting("target_esp", "size") / 10.0;
        boolean dust = Config.setting("target_esp", "dust") == 1;

        // «вправо» и «вверх» камеры из её поворота
        double yaw = Math.toRadians(ctx.camera().getYRot()), pitch = Math.toRadians(ctx.camera().getXRot());
        double lx = -Math.sin(yaw) * Math.cos(pitch), ly = -Math.sin(pitch), lz = Math.cos(yaw) * Math.cos(pitch);
        RX = -Math.cos(yaw);
        RY = 0;
        RZ = -Math.sin(yaw);
        UX = RY * lz - RZ * ly;
        UY = RZ * lx - RX * lz;
        UZ = RX * ly - RY * lx;

        double x = Mth.lerp(pt, t.xo, t.getX()) - cam.x;
        double y = Mth.lerp(pt, t.yo, t.getY()) - cam.y;
        double z = Mth.lerp(pt, t.zo, t.getZ()) - cam.z;
        double w = t.getBbWidth(), h = t.getBbHeight();
        double ts = (System.currentTimeMillis() % 1_000_000L) / 1000.0 * speed;

        ITEMS.clear();
        switch (style) {
            case GHOSTS:
                ghosts(x, y, z, w, h, ts, choice, fade, sc);
                break;
            case DIAMOND:
                diamond(x, y, z, h, ts, espColor(choice, 0), fade, sc);
                break;
            case DIAMONDS:
                diamonds(x, y, z, w, h, ts, choice, fade, sc);
                break;
            case HALO:
                halo(x, y, z, w, h, ts, espColor(choice, 0), fade, sc);
                break;
            default:
                orbs(x, y, z, w, h, ts, choice, fade, sc);
                break;
        }
        if (dust) dust(x, y, z, w, h, ts, espColor(choice, 0.5), fade, sc);

        // дальние элементы рисуем первыми, чтобы ближние не обрезали их прозрачные края
        ITEMS.sort((a, b) -> Double.compare(b.dist(), a.dist()));
        ps.pushPose();
        Matrix4f m = ps.last().pose();
        VertexConsumer gv = consumers.getBuffer(RenderType.lightning());
        for (Item it : ITEMS) it.e().run(gv, m);
        ps.popPose();
        ITEMS.clear();
    }

    /** Призраки: три светящихся серпа с хвостом летают вокруг цели на разной высоте. */
    private static void ghosts(double x, double y, double z, double w, double h, double ts, int choice, float fade, double sc) {
        int n = 26;
        double radius = (w * 0.5 + 0.42) * sc;
        for (int g = 0; g < 3; g++) {
            double phase = g * 2.0944;
            int col = espColor(choice, g / 3.0);
            double head = ts * 2.3 + phase;
            for (int k = n - 1; k >= 0; k--) {
                double th = head - k * 0.05;
                double px = x + Math.cos(th) * radius;
                double pz = z + Math.sin(th) * radius;
                double py = y + h * (0.5 + 0.32 * Math.sin(th * 0.8 + phase * 1.7));
                double f = k / (double) (n - 1);
                double rad = (0.12 - 0.095 * Math.pow(f, 0.7)) * sc;
                float al = (float) (Math.pow(1 - f, 1.5) * 0.55) * fade;
                disc(px, py, pz, rad, col, al);
            }
            double px = x + Math.cos(head) * radius, pz = z + Math.sin(head) * radius;
            double py = y + h * (0.5 + 0.32 * Math.sin(head * 0.8 + phase * 1.7));
            disc(px, py, pz, 0.045 * sc, Ui.lerp(col, 0xFFFFFFFF, 0.7f), 0.9f * fade);
        }
    }

    /** Один крутящийся ромб над головой. */
    private static void diamond(double x, double y, double z, double h, double ts, int col, float fade, double sc) {
        double cy = y + h + (0.42 + 0.06 * Math.sin(ts * 2.0)) * sc;
        octa(x, cy, z, 0.13 * sc, 0.23 * sc, ts * 2.2, col, 0.85f * fade);
        disc(x, cy, z, 0.42 * sc, col, 0.30f * fade);
        int white = Ui.lerp(col, 0xFFFFFFFF, 0.6f);
        for (int k = 0; k < 3; k++) {
            double th = ts * 2.5 + k * 2.0944;
            disc(x + Math.cos(th) * 0.26 * sc, cy + Math.sin(th * 1.3) * 0.08 * sc, z + Math.sin(th) * 0.26 * sc,
                    0.05 * sc, white, 0.7f * fade);
        }
    }

    /** Четыре маленьких ромба летают вокруг цели. */
    private static void diamonds(double x, double y, double z, double w, double h, double ts, int choice, float fade, double sc) {
        double radius = (w * 0.5 + 0.5) * sc;
        for (int k = 0; k < 4; k++) {
            int col = espColor(choice, k / 4.0);
            double th = ts * 1.8 + k * Math.PI / 2;
            double bob = h * 0.2;
            double px = x + Math.cos(th) * radius, pz = z + Math.sin(th) * radius;
            double py = y + h * 0.5 + Math.sin(ts * 2.0 + k * 1.7) * bob;
            octa(px, py, pz, 0.07 * sc, 0.13 * sc, ts * 3.0 + k, col, 0.9f * fade);
            disc(px, py, pz, 0.22 * sc, col, 0.28f * fade);
            for (int j = 1; j <= 7; j++) {
                double t2 = th - j * 0.11;
                disc(x + Math.cos(t2) * radius, py, z + Math.sin(t2) * radius,
                        (0.06 - 0.005 * j) * sc, col, 0.4f * (1f - j / 8f) * fade);
            }
        }
    }

    /** Светящееся кольцо ходит снизу вверх по цели. */
    private static void halo(double x, double y, double z, double w, double h, double ts, int col, float fade, double sc) {
        double radius = (w * 0.5 + 0.3) * sc;
        double sweep = 0.5 + 0.5 * Math.sin(ts * 1.6);
        double sweep2 = 0.5 + 0.5 * Math.sin(ts * 1.6 - 0.4);
        ring(x, y + h * sweep, z, radius, 0.07 * sc, col, 0.85f * fade);
        ring(x, y + h * sweep2, z, radius, 0.05 * sc, col, 0.35f * fade);
        ring(x, y + 0.03, z, radius * 1.25, 0.05 * sc, col, 0.30f * fade);
    }

    /** Пять светящихся шаров облетают цель по разным орбитам. */
    private static void orbs(double x, double y, double z, double w, double h, double ts, int choice, float fade, double sc) {
        double radius = (w * 0.5 + 0.5) * sc;
        for (int k = 0; k < 5; k++) {
            int col = espColor(choice, k / 5.0);
            double dir = k % 2 == 0 ? 1 : -1;
            double sp = (1.5 + 0.22 * k) * dir;
            double tilt = 0.35 + k * 0.28, az = k * 1.2566;
            double head = ts * sp + k * 1.7;
            for (int j = 9; j >= 0; j--) {
                double th = head - j * 0.075 * dir;
                double lx = radius * Math.cos(th);
                double ly = radius * Math.sin(th) * Math.sin(tilt);
                double lz = radius * Math.sin(th) * Math.cos(tilt);
                double gx = lx * Math.cos(az) - lz * Math.sin(az);
                double gz = lx * Math.sin(az) + lz * Math.cos(az);
                double f = j / 9.0;
                disc(x + gx, y + h * 0.5 + ly * 0.75, z + gz, (0.085 - 0.06 * f) * sc, col,
                        (float) (Math.pow(1 - f, 1.3) * 0.6) * fade);
                if (j == 0) {
                    disc(x + gx, y + h * 0.5 + ly * 0.75, z + gz, 0.035 * sc, Ui.lerp(col, 0xFFFFFFFF, 0.7f), 0.9f * fade);
                }
            }
        }
    }

    /** Плавающие светящиеся точки вокруг цели. */
    private static void dust(double x, double y, double z, double w, double h, double ts, int col, float fade, double sc) {
        for (int i = 0; i < 14; i++) {
            double ang = rnd(i, 0) * 6.2832 + ts * (0.3 + 0.4 * rnd(i, 1));
            double rad = (w * 0.5 + 0.3 + rnd(i, 2) * 1.1) * sc;
            double life = (ts * (0.10 + 0.12 * rnd(i, 3)) + rnd(i, 4)) % 1.0;
            double py = y - 0.1 + life * (h + 0.9);
            float al = (float) (Math.sin(Math.PI * life) * 0.55) * fade;
            disc(x + Math.cos(ang) * rad, py, z + Math.sin(ang) * rad, (0.035 + 0.05 * rnd(i, 5)) * sc, col, al);
        }
    }

    private static double rnd(int i, int k) {
        double s = Math.sin(i * 12.9898 + k * 78.233) * 43758.5453;
        return s - Math.floor(s);
    }

    // ───────────────────────── примитивы ─────────────────────────

    private static void vtx(VertexConsumer vc, Matrix4f m, double x, double y, double z, int rgb, int a) {
        vc.addVertex(m, (float) x, (float) y, (float) z).setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a);
    }

    /** Четырёхугольник, нарисованный с обеих сторон (не зависит от порядка обхода). */
    private static void quad(VertexConsumer vc, Matrix4f m, int rgb,
                             double x0, double y0, double z0, int a0, double x1, double y1, double z1, int a1,
                             double x2, double y2, double z2, int a2, double x3, double y3, double z3, int a3) {
        vtx(vc, m, x0, y0, z0, rgb, a0);
        vtx(vc, m, x1, y1, z1, rgb, a1);
        vtx(vc, m, x2, y2, z2, rgb, a2);
        vtx(vc, m, x3, y3, z3, rgb, a3);
        vtx(vc, m, x3, y3, z3, rgb, a3);
        vtx(vc, m, x2, y2, z2, rgb, a2);
        vtx(vc, m, x1, y1, z1, rgb, a1);
        vtx(vc, m, x0, y0, z0, rgb, a0);
    }

    /** Мягкий светящийся кружок, повёрнутый к камере: яркий в центре, прозрачный по краю. */
    private static void disc(double x, double y, double z, double rad, int rgb, float alpha) {
        if (alpha < 0.004f || rad <= 0) return;
        final int a = Math.round(Math.min(1f, alpha) * 255f);
        ITEMS.add(new Item(x * x + y * y + z * z, (vc, m) -> {
            double[] px = new double[8], py = new double[8], pz = new double[8];
            for (int k = 0; k < 8; k++) {
                double ang = k * Math.PI / 4.0, cs = Math.cos(ang) * rad, sn = Math.sin(ang) * rad;
                px[k] = x + RX * cs + UX * sn;
                py[k] = y + RY * cs + UY * sn;
                pz[k] = z + RZ * cs + UZ * sn;
            }
            for (int i = 0; i < 4; i++) {
                int i0 = 2 * i, i1 = 2 * i + 1, i2 = (2 * i + 2) % 8;
                quad(vc, m, rgb, x, y, z, a,
                        px[i0], py[i0], pz[i0], 0, px[i1], py[i1], pz[i1], 0, px[i2], py[i2], pz[i2], 0);
            }
        }));
    }

    /** Ромб (октаэдр), вращающийся вокруг вертикальной оси. */
    private static void octa(double x, double y, double z, double hw, double hh, double rot, int rgb, float alpha) {
        if (alpha < 0.004f) return;
        final int a = Math.round(Math.min(1f, alpha) * 255f), am = a / 2;
        ITEMS.add(new Item(x * x + y * y + z * z, (vc, m) -> {
            double[] rx = new double[4], rz = new double[4];
            for (int k = 0; k < 4; k++) {
                double ang = rot + k * Math.PI / 2 + Math.PI / 4;
                rx[k] = x + Math.cos(ang) * hw;
                rz[k] = z + Math.sin(ang) * hw;
            }
            for (int k = 0; k < 4; k++) {
                int n = (k + 1) % 4;
                quad(vc, m, rgb, x, y + hh, z, a, rx[k], y, rz[k], am, rx[n], y, rz[n], am, rx[n], y, rz[n], am);
                quad(vc, m, rgb, x, y - hh, z, a, rx[n], y, rz[n], am, rx[k], y, rz[k], am, rx[k], y, rz[k], am);
            }
        }));
    }

    /** Светящееся кольцо: плоская лента и вертикальная полоса, чтобы было видно с любого угла. */
    private static void ring(double x, double y, double z, double rad, double half, int rgb, float alpha) {
        if (alpha < 0.004f) return;
        final int a = Math.round(Math.min(1f, alpha) * 255f);
        ITEMS.add(new Item(x * x + y * y + z * z, (vc, m) -> {
            int n = 40;
            for (int k = 0; k < n; k++) {
                double a0 = 2 * Math.PI * k / n, a1 = 2 * Math.PI * (k + 1) / n;
                double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
                double ri = rad - half, ro = rad + half;
                quad(vc, m, rgb, x + c0 * ri, y, z + s0 * ri, 0, x + c0 * rad, y, z + s0 * rad, a,
                        x + c1 * rad, y, z + s1 * rad, a, x + c1 * ri, y, z + s1 * ri, 0);
                quad(vc, m, rgb, x + c0 * rad, y, z + s0 * rad, a, x + c0 * ro, y, z + s0 * ro, 0,
                        x + c1 * ro, y, z + s1 * ro, 0, x + c1 * rad, y, z + s1 * rad, a);
                double vh = half * 0.9;
                quad(vc, m, rgb, x + c0 * rad, y - vh, z + s0 * rad, 0, x + c0 * rad, y, z + s0 * rad, a,
                        x + c1 * rad, y, z + s1 * rad, a, x + c1 * rad, y - vh, z + s1 * rad, 0);
                quad(vc, m, rgb, x + c0 * rad, y, z + s0 * rad, a, x + c0 * rad, y + vh, z + s0 * rad, 0,
                        x + c1 * rad, y + vh, z + s1 * rad, 0, x + c1 * rad, y, z + s1 * rad, a);
            }
        }));
    }
}

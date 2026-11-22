package quarry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Эффекты в мире: круги при прыжке, светящаяся лента-трейл, подсветка блока. Всё с проверкой глубины. */
public class Fx {
    private interface Emit {
        void run(VertexConsumer vc, Matrix4f m);
    }

    private record Item(double dist, Emit e) {
    }

    private record Flash(LivingEntity e, long at) {
    }

    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final List<Item> ITEMS = new ArrayList<>();
    private static final ArrayDeque<double[]> TRAIL = new ArrayDeque<>();
    private static final List<double[]> CIRCLES = new ArrayList<>();
    private static double RX, RZ, UX, UY, UZ;
    private static boolean wasGround = true;

    public static void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(Fx::render);
    }

    public static int hsv(float h, float s, float v) {
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

    /** Вспышка цвета на цели в момент вашего удара. */
    public static void hitFlash(LivingEntity e) {
        if (!Config.get("hitcolor")) return;
        FLASHES.add(new Flash(e, System.currentTimeMillis()));
        if (FLASHES.size() > 6) FLASHES.remove(0);
    }

    /** Цвет эффекта: 0 — цвета темы с переливом, 1 — радуга. */
    private static int mix(int mode, double u, double time) {
        if (mode == 1) return hsv((float) (u + time * 0.15), 0.6f, 1f);
        return Ui.lerp(Config.accent(), Config.accent2(), (float) (0.5 + 0.5 * Math.sin(u * 12.566 + time * 2.0)));
    }

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            TRAIL.clear();
            CIRCLES.clear();
            return;
        }
        boolean ground = p.onGround();
        if (Config.get("jumpcircles") && wasGround && !ground && p.getDeltaMovement().y > 0.2 && !p.isInWater()) {
            CIRCLES.add(new double[]{p.getX(), p.getY() + 0.03, p.getZ(), System.currentTimeMillis()});
            if (CIRCLES.size() > 8) CIRCLES.remove(0);
        }
        wasGround = ground;

        if (Config.get("trail") && Config.setting("trail", "type") >= 7) {
            double[] last = TRAIL.peekLast();
            boolean moved = last == null || (p.getX() - last[0]) * (p.getX() - last[0]) + (p.getY() - last[1]) * (p.getY() - last[1])
                    + (p.getZ() - last[2]) * (p.getZ() - last[2]) > 0.0004;
            if (moved) TRAIL.addLast(new double[]{p.getX(), p.getY(), p.getZ()});
            else if (!TRAIL.isEmpty()) TRAIL.pollFirst();
            int max = Config.setting("trail", "length");
            while (TRAIL.size() > max) TRAIL.pollFirst();
        } else {
            TRAIL.clear();
        }
    }

    private static void render(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        MultiBufferSource consumers = ctx.consumers();
        PoseStack ps = ctx.matrixStack();
        if (consumers == null || ps == null) return;

        boolean circles = !CIRCLES.isEmpty() && Config.get("jumpcircles");
        boolean ribbon = Config.get("trail") && Config.setting("trail", "type") >= 7 && !TRAIL.isEmpty()
                && !mc.options.getCameraType().isFirstPerson();
        BlockHitResult bhr = mc.hitResult instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK ? b : null;
        boolean outline = bhr != null && Config.get("blockoutline");
        boolean flashes = !FLASHES.isEmpty() && Config.get("hitcolor");
        if (!circles && !ribbon && !outline && !flashes) return;

        Vec3 cam = ctx.camera().getPosition();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double time = (System.currentTimeMillis() % 1_000_000L) / 1000.0;

        // «вправо» и «вверх» камеры: светящиеся кружки всегда повёрнуты к игроку
        double yaw = Math.toRadians(ctx.camera().getYRot()), pitch = Math.toRadians(ctx.camera().getXRot());
        double lx = -Math.sin(yaw) * Math.cos(pitch), ly = -Math.sin(pitch), lz = Math.cos(yaw) * Math.cos(pitch);
        RX = -Math.cos(yaw);
        RZ = -Math.sin(yaw);
        UX = -RZ * ly;
        UY = RZ * lx - RX * lz;
        UZ = RX * ly;

        ITEMS.clear();
        if (circles) {
            int mode = Config.setting("jumpcircles", "color");
            double size = Config.setting("jumpcircles", "size") / 10.0;
            long now = System.currentTimeMillis();
            Iterator<double[]> it = CIRCLES.iterator();
            while (it.hasNext()) {
                double[] c = it.next();
                float age = (now - (long) c[3]) / 1000f;
                if (age > 1.0f) {
                    it.remove();
                    continue;
                }
                float e = 1f - (1f - age) * (1f - age) * (1f - age);
                float alpha = (float) Math.pow(1f - age, 1.4) * 0.9f;
                double rad = 0.3 + size * 0.75 * e;
                circle(c[0] - cam.x, c[1] - cam.y, c[2] - cam.z, rad, 0.06 + 0.05 * (1f - age), alpha, mode, time);
                circle(c[0] - cam.x, c[1] - cam.y, c[2] - cam.z, rad * 0.62, 0.04, alpha * 0.6f, mode, time + 0.5);
            }
        }
        if (ribbon) ribbon(mc, cam, pt, time);
        if (flashes) {
            long now = System.currentTimeMillis();
            int dur = Config.setting("hitcolor", "duration");
            float strength = Config.setting("hitcolor", "strength") / 100f;
            int cm = Config.setting("hitcolor", "color");
            Iterator<Flash> fi = FLASHES.iterator();
            while (fi.hasNext()) {
                Flash f = fi.next();
                float age = (now - f.at()) / (float) dur;
                LivingEntity le = f.e();
                if (age >= 1f || !le.isAlive()) {
                    fi.remove();
                    continue;
                }
                double dx = Mth.lerp(pt, le.xo, le.getX()) - le.getX();
                double dy = Mth.lerp(pt, le.yo, le.getY()) - le.getY();
                double dz = Mth.lerp(pt, le.zo, le.getZ()) - le.getZ();
                AABB fb = le.getBoundingBox().move(dx, dy, dz).inflate(0.05).move(-cam.x, -cam.y, -cam.z);
                int fc = cm == 0 ? 0xFFFF3030 : cm == 1 ? Config.accent() : cm == 2 ? 0xFFFFFFFF : hsv((float) (time * 0.3), 0.6f, 1f);
                fillBox(fb, fc, (int) (Math.pow(1f - age, 1.6) * strength * 0.7 * 255));
            }
        }

        if (outline) {
            BlockPos pos = bhr.getBlockPos();
            BlockState st = mc.level.getBlockState(pos);
            VoxelShape shape = st.getShape(mc.level, pos);
            if (!shape.isEmpty()) {
                AABB box = shape.bounds().move(pos).inflate(0.004);
                int col = mix(Config.setting("blockoutline", "color"), 0.0, time);
                float r = ((col >> 16) & 255) / 255f, g = ((col >> 8) & 255) / 255f, bl = (col & 255) / 255f;
                VertexConsumer lv = consumers.getBuffer(RenderType.lines());
                ps.pushPose();
                ps.translate(-cam.x, -cam.y, -cam.z);
                ShapeRenderer.renderLineBox(ps, lv, box, r, g, bl, 1f);
                ShapeRenderer.renderLineBox(ps, lv, box.inflate(0.003), r, g, bl, 0.5f);
                ps.popPose();
                if (Config.setting("blockoutline", "fill") == 1) {
                    int a = (int) ((0.12 + 0.05 * Math.sin(time * 3.0)) * 255);
                    fillBox(box.move(-cam.x, -cam.y, -cam.z), col, a);
                }
            }
        }

        if (ITEMS.isEmpty()) return;
        // дальние элементы рисуем первыми, чтобы ближние не обрезали их прозрачные края
        ITEMS.sort((x, y) -> Double.compare(y.dist(), x.dist()));
        ps.pushPose();
        Matrix4f m = ps.last().pose();
        VertexConsumer gv = consumers.getBuffer(RenderType.lightning());
        for (Item it : ITEMS) it.e().run(gv, m);
        ps.popPose();
        ITEMS.clear();
    }

    // ───────────────────────── лента-трейл ─────────────────────────

    private static void ribbon(Minecraft mc, Vec3 cam, float pt, double time) {
        int mode = Config.setting("trail", "type") == 8 ? 1 : 0;
        List<double[]> pts = new ArrayList<>(TRAIL);
        pts.add(new double[]{Mth.lerp(pt, mc.player.xo, mc.player.getX()), Mth.lerp(pt, mc.player.yo, mc.player.getY()),
                Mth.lerp(pt, mc.player.zo, mc.player.getZ())});
        int n = pts.size() - 1;
        if (n < 1) return;
        int steps = 3;
        for (int i = 0; i < n; i++) {
            double[] a = pts.get(i), b = pts.get(i + 1);
            for (int s = 0; s < steps; s++) {
                double u = s / (double) steps;
                double f = 1.0 - (i + u + 1.0) / n;   // 0 у головы, 1 у хвоста
                f = Math.max(0, Math.min(1, f));
                double x = a[0] + (b[0] - a[0]) * u - cam.x;
                double y = a[1] + (b[1] - a[1]) * u + 0.12 - cam.y;
                double z = a[2] + (b[2] - a[2]) * u - cam.z;
                int col = mode == 1 ? hsv((float) (f * 0.8 + time * 0.12), 0.62f, 1f)
                        : Ui.lerp(Config.accent(), Config.accent2(), (float) f);
                disc(x, y, z, 0.17 - 0.14 * f, col, (float) (Math.pow(1.0 - f, 1.1) * 0.8));
            }
        }
    }

    // ───────────────────────── примитивы ─────────────────────────

    private static void v(VertexConsumer vc, Matrix4f m, double x, double y, double z, int rgb, int a) {
        vc.addVertex(m, (float) x, (float) y, (float) z).setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a);
    }

    /** Четырёхугольник с цветом и прозрачностью в каждой вершине, рисуется с обеих сторон. */
    private static void quad(VertexConsumer vc, Matrix4f m,
                             double x0, double y0, double z0, double x1, double y1, double z1,
                             double x2, double y2, double z2, double x3, double y3, double z3,
                             int c0, int c1, int c2, int c3, int a0, int a1, int a2, int a3) {
        v(vc, m, x0, y0, z0, c0, a0);
        v(vc, m, x1, y1, z1, c1, a1);
        v(vc, m, x2, y2, z2, c2, a2);
        v(vc, m, x3, y3, z3, c3, a3);
        v(vc, m, x3, y3, z3, c3, a3);
        v(vc, m, x2, y2, z2, c2, a2);
        v(vc, m, x1, y1, z1, c1, a1);
        v(vc, m, x0, y0, z0, c0, a0);
    }

    /** Мягкий светящийся кружок, повёрнутый к камере. */
    private static void disc(double x, double y, double z, double rad, int rgb, float alpha) {
        if (alpha < 0.004f || rad <= 0) return;
        final int a = Math.round(Math.min(1f, alpha) * 255f);
        ITEMS.add(new Item(x * x + y * y + z * z, (vc, m) -> {
            double[] px = new double[8], py = new double[8], pz = new double[8];
            for (int k = 0; k < 8; k++) {
                double ang = k * Math.PI / 4.0, cs = Math.cos(ang) * rad, sn = Math.sin(ang) * rad;
                px[k] = x + RX * cs + UX * sn;
                py[k] = y + UY * sn;
                pz[k] = z + RZ * cs + UZ * sn;
            }
            for (int i = 0; i < 4; i++) {
                int i0 = 2 * i, i1 = 2 * i + 1, i2 = (2 * i + 2) % 8;
                quad(vc, m, x, y, z, px[i0], py[i0], pz[i0], px[i1], py[i1], pz[i1], px[i2], py[i2], pz[i2],
                        rgb, rgb, rgb, rgb, a, 0, 0, 0);
            }
        }));
    }

    /** Плоское светящееся кольцо на земле, цвет плавно меняется по кругу. */
    private static void circle(double x, double y, double z, double rad, double half, float alpha, int mode, double time) {
        if (alpha < 0.004f) return;
        final int a = Math.round(Math.min(1f, alpha) * 255f);
        ITEMS.add(new Item(x * x + y * y + z * z, (vc, m) -> {
            int n = 56;
            for (int k = 0; k < n; k++) {
                double u0 = k / (double) n, u1 = (k + 1) / (double) n;
                double a0 = u0 * 2 * Math.PI, a1 = u1 * 2 * Math.PI;
                double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
                int k0 = mix(mode, u0, time), k1 = mix(mode, u1, time);
                double ri = rad - half, ro = rad + half;
                quad(vc, m, x + c0 * ri, y, z + s0 * ri, x + c0 * rad, y, z + s0 * rad,
                        x + c1 * rad, y, z + s1 * rad, x + c1 * ri, y, z + s1 * ri, k0, k0, k1, k1, 0, a, a, 0);
                quad(vc, m, x + c0 * rad, y, z + s0 * rad, x + c0 * ro, y, z + s0 * ro,
                        x + c1 * ro, y, z + s1 * ro, x + c1 * rad, y, z + s1 * rad, k0, k0, k1, k1, a, 0, 0, a);
                double vh = half * 1.4;
                quad(vc, m, x + c0 * rad, y, z + s0 * rad, x + c0 * rad, y + vh, z + s0 * rad,
                        x + c1 * rad, y + vh, z + s1 * rad, x + c1 * rad, y, z + s1 * rad, k0, k0, k1, k1, a, 0, 0, a);
            }
        }));
    }

    /** Полупрозрачная заливка граней блока. */
    private static void fillBox(AABB b, int rgb, int a) {
        double cx = (b.minX + b.maxX) / 2, cy = (b.minY + b.maxY) / 2, cz = (b.minZ + b.maxZ) / 2;
        ITEMS.add(new Item(cx * cx + cy * cy + cz * cz, (vc, m) -> {
            double x0 = b.minX, y0 = b.minY, z0 = b.minZ, x1 = b.maxX, y1 = b.maxY, z1 = b.maxZ;
            quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, rgb, rgb, rgb, rgb, a, a, a, a);
            quad(vc, m, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, rgb, rgb, rgb, rgb, a, a, a, a);
            quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, rgb, rgb, rgb, rgb, a, a, a, a);
            quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, rgb, rgb, rgb, rgb, a, a, a, a);
            quad(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, rgb, rgb, rgb, rgb, a, a, a, a);
            quad(vc, m, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, rgb, rgb, rgb, rgb, a, a, a, a);
        }));
    }
}

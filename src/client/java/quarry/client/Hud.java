package quarry.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Hud {
    private static final int BG = Ui.HUD_BG;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    // состояния анимаций: у каждого элемента худа своё плавное значение 0..1
    private static final Map<String, Smooth> SM = new HashMap<>();
    // последние показанные тексты, чтобы элемент мог красиво исчезнуть
    private static final Map<String, String[]> LAST = new HashMap<>();
    private static final Map<String, Boolean> PREV = new HashMap<>();
    private static final List<Toast> TOASTS = new ArrayList<>();

    private record Toast(String name, boolean on, long at) {
    }

    /** Прямоугольник элемента худа на экране: нужен, чтобы его можно было схватить мышью в чате. */
    private record Box(String id, int x, int y, int w, int h) {
    }

    private static List<Box> hits = new ArrayList<>();
    private static List<Box> prevHits = new ArrayList<>();
    private static boolean edit = false, wasDown = false, wasRight = false;
    private static String dragId = null;
    private static double lastMx, lastMy;
    private static int SH;
    // 0 — ничего, 1 — двигаем руки (ЛКМ), 2 — меняем размер рук (ПКМ)
    private static int handsMode = 0;
    private static float hx, hy, hs;

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(Math.max(lo, hi), v));
    }

    private static Box boxOf(String id) {
        for (Box b : prevHits) if (b.id().equals(id)) return b;
        return null;
    }

    /** В открытом чате элементы худа можно таскать левой кнопкой мыши, правая кнопка возвращает на место. */
    private static void editMouse(Minecraft mc, GuiGraphics ctx) {
        edit = mc.screen instanceof ChatScreen;
        if (!edit) {
            if (dragId != null) {
                dragId = null;
                Config.save();
            }
            if (handsMode != 0) {
                handsMode = 0;
                Config.save();
            }
            wasDown = false;
            wasRight = false;
            return;
        }
        double mx = mc.mouseHandler.xpos() * ctx.guiWidth() / (double) mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * ctx.guiHeight() / (double) mc.getWindow().getScreenHeight();
        long win = mc.getWindow().getWindow();
        boolean down = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rdown = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        Box over = null;
        for (int i = prevHits.size() - 1; i >= 0; i--) {
            Box b = prevHits.get(i);
            if (mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h()) {
                over = b;
                break;
            }
        }
        if (down && !wasDown && over != null) {
            dragId = over.id();
            if (!Config.HUD_POS.containsKey(dragId)) {
                // список справа привязан к правому краю, остальные элементы к левому верхнему углу
                Config.HUD_POS.put(dragId, new double[]{dragId.equals("al") ? over.x() + over.w() : over.x(), over.y()});
            }
        }
        if (down && dragId != null) {
            double[] f = Config.HUD_POS.get(dragId);
            Box b = boxOf(dragId);
            if (f != null) {
                f[0] += mx - lastMx;
                f[1] += my - lastMy;
                if (b != null) {
                    boolean al = dragId.equals("al");
                    f[0] = Math.max(al ? b.w() : 0, Math.min(al ? ctx.guiWidth() : ctx.guiWidth() - b.w(), f[0]));
                    f[1] = Math.max(0, Math.min(ctx.guiHeight() - b.h(), f[1]));
                }
            }
        }
        if (!down && wasDown && dragId != null) {
            dragId = null;
            Config.save();
        }

        // руки от первого лица: зажмите ЛКМ на свободном месте и тащите, ПКМ вверх-вниз меняет размер
        if (Config.get("hands") && over == null && dragId == null && handsMode == 0) {
            if (down && !wasDown) {
                handsMode = 1;
                hx = Config.setting("hands", "x");
                hy = Config.setting("hands", "y");
            } else if (rdown && !wasRight) {
                handsMode = 2;
                hs = Config.setting("hands", "scale");
            }
        }
        if (handsMode == 1 && down) {
            hx += (float) (mx - lastMx) * 0.35f;
            hy -= (float) (my - lastMy) * 0.35f;
            Config.setSettingQuiet("hands", "x", clamp(Math.round(hx), -50, 50));
            Config.setSettingQuiet("hands", "y", clamp(Math.round(hy), -50, 50));
        } else if (handsMode == 2 && rdown) {
            hs -= (float) (my - lastMy) * 0.5f;
            Config.setSettingQuiet("hands", "scale", clamp(Math.round(hs), 50, 150));
        }
        if ((handsMode == 1 && !down) || (handsMode == 2 && !rdown)) {
            handsMode = 0;
            Config.save();
        }
        if (rdown && !wasRight && over != null && Config.HUD_POS.remove(over.id()) != null) Config.save();
        lastMx = mx;
        lastMy = my;
        wasDown = down;
        wasRight = rdown;
    }

    /** Рамки вокруг всех элементов, пока открыт чат. */
    private static void editOverlay() {
        if (!edit) return;
        for (Box b : hits) {
            boolean d = b.id().equals(dragId);
            int c = d ? ACC : (ACC & 0x00FFFFFF) | 0x99000000;
            if (d) Ui.fill(G, b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), (ACC & 0x00FFFFFF) | 0x30000000);
            Ui.fill(G, b.x() - 1, b.y() - 1, b.x() + b.w() + 1, b.y(), c);
            Ui.fill(G, b.x() - 1, b.y() + b.h(), b.x() + b.w() + 1, b.y() + b.h() + 1, c);
            Ui.fill(G, b.x() - 1, b.y(), b.x(), b.y() + b.h(), c);
            Ui.fill(G, b.x() + b.w(), b.y(), b.x() + b.w() + 1, b.y() + b.h(), c);
        }
        String hint = "Drag to move   ·   Right click to reset";
        int w = Ui.w(TR, hint) + 22;
        Ui.rrect(G, SW / 2 - w / 2, 28, w, 19, 9, BG);
        Ui.draw(G, TR, hint, SW / 2 - w / 2 + 11, 34, 0xFFFFFFFF, false);
        if (Config.get("hands")) {
            String hh = "Hands: hold LMB on empty space and drag   ·   hold RMB and drag up / down = size";
            int hw = Ui.w(TR, hh) + 22;
            Ui.rrect(G, SW / 2 - hw / 2, SH - 48, hw, 19, 9, BG);
            Ui.draw(G, TR, hh, SW / 2 - hw / 2 + 11, SH - 42, handsMode != 0 ? ACC : 0xFFFFFFFF, false);
        }
    }

    // данные кадра
    private static GuiGraphics G;
    private static Font TR;
    private static int ACC, ACC2, SW;

    // последняя цель (для плавного исчезновения карточки)
    private static int tgtId = -1;
    private static String tgtName = "";
    private static double tgtDist = 0;
    private static float tgtHp = 0, tgtMax = 1;
    private static double tgtX, tgtY, tgtZ, tgtH = 1.8;

    private static Smooth sm(String k) {
        return SM.computeIfAbsent(k, x -> new Smooth());
    }

    /** Колонка плашек: каждая плавно выезжает с края и гаснет, остальные плавно сдвигаются. */
    private static final class Stack {
        float y;
        final boolean right;

        Stack(float y, boolean right) {
            this.y = y;
            this.right = right;
        }

        void pill(String id, boolean show, String label, String value) {
            float p = sm("p_" + id).to(show ? 1f : 0f, 13f);
            if (show) LAST.put(id, new String[]{label, value});
            if (p <= 0.01f) return;
            String[] t = LAST.get(id);
            if (t == null) return;
            int w = Ui.w(TR, t[0]) + (t[1].isEmpty() ? 0 : Ui.w(TR, t[1]) + 7) + 20;
            double[] f = Config.HUD_POS.get(id);
            int px, py;
            if (f != null) {
                px = (int) Math.round(f[0]);
                py = (int) Math.round(f[1]);
            } else {
                int slide = Math.round((1f - p) * 16f);
                px = right ? SW - w - 6 + slide : 6 - slide;
                py = Math.round(y);
            }
            px = clamp(px, 0, SW - w);
            py = clamp(py, 0, SH - 17);
            hits.add(new Box(id, px, py, w, 17));
            Ui.GA = p;
            Ui.rrect(G, px, py, w, 17, 8, BG);
            Ui.rrect(G, px + 5, py + 5, 2, 7, 1, ACC);
            Ui.draw(G, TR, t[0], px + 11, py + 5, ACC, false);
            if (!t[1].isEmpty()) Ui.draw(G, TR, t[1], px + 11 + Ui.w(TR, t[0]) + 7, py + 5, 0xFFFFFFFF, false);
            Ui.GA = 1f;
            if (f == null) y += 20f * p;
        }

        void watermark(Minecraft mc) {
            float p = sm("p_wm").to(Config.get("hud_watermark") ? 1f : 0f, 13f);
            if (p <= 0.01f) return;
            String name = "Quarry";
            String user = mc.player.getName().getString();
            boolean showName = Config.setting("hud_watermark", "name") == 1;
            int nw = Ui.w(TR, name);
            int w = nw + (showName ? Ui.w(TR, user) + 14 : 0) + 22;
            double[] f = Config.HUD_POS.get("wm");
            int px = f != null ? (int) Math.round(f[0]) : 6 - Math.round((1f - p) * 16f);
            int py = f != null ? (int) Math.round(f[1]) : Math.round(y);
            px = clamp(px, 0, SW - w);
            py = clamp(py, 0, SH - 19);
            hits.add(new Box("wm", px, py, w, 19));
            Ui.GA = p;
            Ui.rrect(G, px, py, w, 19, 9, BG);
            for (int i = 0; i < name.length(); i++) {
                float u = i / (float) Math.max(1, name.length() - 1);
                Ui.draw(G, TR, name.substring(i, i + 1), px + 11 + Ui.w(TR, name.substring(0, i)), py + 6, Ui.lerp(ACC, ACC2, u), false);
            }
            if (showName) {
                Ui.rrect(G, px + 11 + nw + 6, py + 5, 1, 9, 0, 0x50FFFFFF);
                Ui.draw(G, TR, user, px + 11 + nw + 13, py + 6, 0xFFFFFFFF, false);
            }
            Ui.GA = 1f;
            if (f == null) y += 23f * p;
        }
    }

    public static void render(GuiGraphics ctx, DeltaTracker tick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Font tr = mc.font;
        G = ctx;
        TR = tr;
        int acc = Config.accent();
        ACC = acc;
        ACC2 = Config.accent2();
        int accDim = (acc & 0x00FFFFFF);
        int sw = ctx.guiWidth();
        int sh = ctx.guiHeight();
        SW = sw;
        SH = sh;
        List<Box> tmp = prevHits;
        prevHits = hits;
        hits = tmp;
        hits.clear();
        editMouse(mc, ctx);

        pollToggles();
        // пока открыто меню, худ скрыт, но уведомления остаются; при закрытии меню худ проявляется под ним
        if (mc.screen instanceof MenuScreen menu && !menu.isClosing()) {
            toasts();
            return;
        }

        if (Config.get("edge_glow")) {
            int al = Math.min(255, Config.setting("edge_glow", "power") * 2) << 24;
            ctx.fillGradient(0, 0, sw, 30, accDim | al, accDim);
            ctx.fillGradient(0, sh - 30, sw, sh, accDim, accDim | al);
        }
        if (Config.get("lowhp") && mc.player.getHealth() <= 6f) {
            int pulse = 40 + (int) (30 * Math.sin(System.currentTimeMillis() / 220.0));
            ctx.fillGradient(0, 0, sw, 50, (pulse << 24) | 0xFF2020, 0x00FF2020);
            ctx.fillGradient(0, sh - 50, sw, sh, 0x00FF2020, (pulse << 24) | 0xFF2020);
        }
        if (Config.get("ring")) {
            int rad = Config.setting("ring", "radius");
            for (int a = 0; a < 360; a += 5) {
                double r = Math.toRadians(a);
                int px = sw / 2 + (int) Math.round(Math.cos(r) * rad);
                int py = sh / 2 + (int) Math.round(Math.sin(r) * rad);
                ctx.fill(px, py, px + 1, py + 1, acc);
            }
        }

        Nametags.render(ctx, mc);
        Crosshair.render(ctx, mc);

        // FPS по центру сверху
        float pf = sm("fps").to(Config.get("hud_fps") ? 1f : 0f, 13f);
        if (pf > 0.01f) {
            boolean wm = Config.get("hud_watermark");
            String lab = wm ? "FPS" : "Quarry";
            String val = wm ? String.valueOf(mc.getFps()) : mc.getFps() + " FPS";
            int w = Ui.w(tr, lab + "  " + val) + 18;
            double[] ff = Config.HUD_POS.get("fps");
            int ox = ff != null ? (int) Math.round(ff[0]) : sw / 2 - w / 2;
            int oy = ff != null ? (int) Math.round(ff[1]) : 4 - Math.round((1f - pf) * 10f);
            ox = clamp(ox, 0, sw - w);
            oy = clamp(oy, 0, sh - 17);
            hits.add(new Box("fps", ox, oy, w, 17));
            Ui.GA = pf;
            Ui.rrect(ctx, ox, oy, w, 17, 8, BG);
            Ui.draw(ctx, tr, lab, ox + 9, oy + 5, acc, false);
            Ui.draw(ctx, tr, val, ox + 9 + Ui.w(tr, lab + "  "), oy + 5, 0xFFFFFFFF, false);
            Ui.GA = 1f;
        }

        // слева сверху
        Stack left = new Stack(6f, false);
        left.watermark(mc);
        boolean on = Config.get("hud_coords");
        left.pill("coords", on, "XYZ", !on ? "" : (int) Math.floor(mc.player.getX()) + "  " + (int) Math.floor(mc.player.getY())
                + "  " + (int) Math.floor(mc.player.getZ()) + "  " + mc.player.getDirection().getName());
        on = Config.get("hud_speed");
        String spv = "";
        if (on) {
            Vec3 v = mc.player.getDeltaMovement();
            double sp = Math.sqrt(v.x * v.x + v.z * v.z) * 20;
            spv = Config.setting("hud_speed", "unit") == 1 ? String.format("%.0f km/h", sp * 3.6) : String.format("%.1f b/s", sp);
        }
        left.pill("speed", on, "Speed", spv);
        on = Config.get("hud_ping");
        String ping = "—";
        if (on) {
            ClientPacketListener conn = mc.getConnection();
            if (conn != null) {
                PlayerInfo info = conn.getPlayerInfo(mc.player.getUUID());
                if (info != null) ping = info.getLatency() + " ms";
            }
        }
        left.pill("ping", on, "Ping", ping);
        on = Config.get("hud_clock");
        left.pill("clock", on, "Time", !on ? "" : LocalTime.now().format(DateTimeFormatter.ofPattern(Config.setting("hud_clock", "fmt") == 1 ? "hh:mm a" : "HH:mm")));
        on = Config.get("hud_world") && mc.level != null;
        String wt = "";
        if (on) {
            long dt = mc.level.getDayTime() % 24000L;
            wt = String.format("%02d:%02d", (dt / 1000 + 6) % 24, (dt % 1000) * 60 / 1000);
        }
        left.pill("world", on, "World", wt);
        on = Config.get("hud_memory");
        String mem = "";
        if (on) {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
            mem = used + " / " + (rt.maxMemory() >> 20) + " MB";
        }
        left.pill("memory", on, "RAM", mem);

        // справа сверху
        Stack right = new Stack(6f, true);
        on = Config.get("hud_cps");
        right.pill("cps", on, "CPS", String.valueOf(QuarryClient.cps()));
        on = Config.get("hud_combo");
        right.pill("combo", on, "Combo", "x" + Combat.combo);
        on = Config.get("hud_reach");
        right.pill("reach", on, "Reach", Combat.reach > 0 ? String.format("%.2f", Combat.reach) : "—");
        boolean cdOn = Config.get("hud_cooldowns");
        Item[] its = {Items.ENDER_PEARL, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.CHORUS_FRUIT, Items.SHIELD, Items.WIND_CHARGE};
        String[] nms = {"Pearls", "Apple", "G-Apple", "Chorus", "Shield", "Wind"};
        for (int i = 0; i < its.length; i++) {
            float cd = cdOn ? mc.player.getCooldowns().getCooldownPercent(new ItemStack(its[i]), 0f) : 0f;
            right.pill("cd_" + nms[i], cdOn && cd > 0f, nms[i], Math.round(cd * 100) + "%");
        }
        boolean itOn = Config.get("hud_items");
        int[] counts = new int[4];
        String[] names = {"Totems", "Apples", "Pearls", "Arrows"};
        if (itOn) {
            Inventory inv = mc.player.getInventory();
            int apples = inv.countItem(Items.GOLDEN_APPLE) + inv.countItem(Items.ENCHANTED_GOLDEN_APPLE);
            counts = new int[]{inv.countItem(Items.TOTEM_OF_UNDYING), apples, inv.countItem(Items.ENDER_PEARL), inv.countItem(Items.ARROW)};
        }
        for (int i = 0; i < counts.length; i++) {
            right.pill("it_" + names[i], itOn && counts[i] > 0, names[i], "x" + counts[i]);
        }
        arrayList(right.y + 4f);

        // цель под прицелом: карточка плавно выезжает, полоска здоровья плавно меняется
        LivingEntity t = Combat.target;
        boolean showT = Config.get("hud_target") && t != null && t.isAlive();
        float pt = sm("tgt").to(showT ? 1f : 0f, 12f);
        if (showT) {
            if (t.getId() != tgtId) {
                tgtId = t.getId();
                sm("hp").snap(Math.min(1f, t.getHealth() / Math.max(1f, t.getMaxHealth())));
            }
            tgtName = t.getName().getString();
            tgtDist = Math.sqrt(mc.player.distanceToSqr(t));
            tgtHp = t.getHealth();
            tgtMax = Math.max(1f, t.getMaxHealth());
            double[] tp = Nametags.pos(t);
            tgtX = tp[0];
            tgtY = tp[1];
            tgtZ = tp[2];
            tgtH = t.getBbHeight();
        }
        if (pt > 0.01f) {
            String hs = String.format("%.1f / %.0f", tgtHp, tgtMax);
            String ds = String.format("%.1f m", tgtDist);
            int w = Math.max(130, Math.max(Ui.w(tr, tgtName), Ui.w(tr, hs)) + Ui.w(tr, ds) + 34);
            double[] tf = Config.HUD_POS.get("tgt");
            int tx = tf != null ? (int) Math.round(tf[0]) : sw / 2 + 28 + Math.round((1f - pt) * 14f);
            int ty = tf != null ? (int) Math.round(tf[1]) : sh / 2 + 20;
            // режим «Follow»: карточка плавно едет рядом с целью на экране
            if (tf == null && Config.setting("hud_target", "mode") == 1) {
                float[] sp = Nametags.project(tgtX, tgtY + tgtH * 0.55, tgtZ, sw, sh);
                if (sp != null) {
                    Smooth fx = sm("tgt_x"), fy = sm("tgt_y");
                    if (pt < 0.2f) {
                        fx.snap(sp[0] + 38f);
                        fy.snap(sp[1] - 19f);
                    }
                    tx = Math.round(fx.to(sp[0] + 38f, 24f));
                    ty = Math.round(fy.to(sp[1] - 19f, 24f));
                }
            }
            tx = clamp(tx, 0, sw - w);
            ty = clamp(ty, 0, sh - 52);
            hits.add(new Box("tgt", tx, ty, w, 52));
            float frac = sm("hp").to(Math.min(1f, tgtHp / tgtMax), 9f);
            int hc = frac > 0.5f ? 0xFF7CE08A : frac > 0.25f ? 0xFFFFC857 : 0xFFFF6B6B;
            Ui.GA = pt;
            Ui.rrect(ctx, tx, ty, w, 38, 10, BG);
            Ui.rrect(ctx, tx + 6, ty + 8, 2, 8, 1, acc);
            Ui.draw(ctx, tr, tgtName, tx + 13, ty + 8, 0xFFFFFFFF, false);
            Ui.draw(ctx, tr, ds, tx + w - 10 - Ui.w(tr, ds), ty + 8, acc, false);
            Ui.rrect(ctx, tx + 10, ty + 22, w - 20, 8, 4, 0xFF262B3A);
            Ui.rrect(ctx, tx + 10, ty + 22, Math.max(4, Math.round((w - 20) * frac)), 8, 4, hc);
            Ui.draw(ctx, tr, hs, tx + 13, ty + 40, 0xFF9AA8B8, false);
            Ui.GA = 1f;
        }

        // эффекты слева по центру
        Stack eff = new Stack(sh / 2f - 20f, false);
        boolean effOn = Config.get("hud_effects");
        for (MobEffectInstance e : mc.player.getActiveEffects()) {
            if (Config.get("fullbright") && e.getEffect().equals(MobEffects.NIGHT_VISION)) continue;
            int secs = e.getDuration() / 20;
            String time = e.isInfiniteDuration() ? "∞" : String.format("%d:%02d", secs / 60, secs % 60);
            String name = e.getEffect().value().getDisplayName().getString() + " " + (e.getAmplifier() + 1);
            eff.pill("ef_" + name, effOn, name, time);
        }

        // броня справа снизу
        int minPct = 100;
        double[] af = Config.HUD_POS.get("armor");
        int agx = af != null ? (int) Math.round(af[0]) : sw - 72;
        int agy = af != null ? (int) Math.round(af[1]) : sh - 114;
        agx = clamp(agx, 0, sw - 66);
        agy = clamp(agy, 0, sh - 20);
        int slots = 0;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack st = mc.player.getItemBySlot(slot);
            if (st.isEmpty() || !st.isDamageableItem()) continue;
            int pct = (st.getMaxDamage() - st.getDamageValue()) * 100 / Math.max(1, st.getMaxDamage());
            minPct = Math.min(minPct, pct);
            if (!Config.get("hud_armor")) continue;
            int sy = agy + 22 * slots;
            Ui.rrect(ctx, agx, sy, 66, 20, 8, BG);
            ctx.renderItem(st, agx + 3, sy + 2);
            int col = pct > 50 ? 0xFF7CE08A : pct > 20 ? 0xFFFFC857 : 0xFFFF6B6B;
            Ui.draw(ctx, tr, pct + "%", agx + 25, sy + 6, col, false);
            slots++;
        }
        if (slots > 0) hits.add(new Box("armor", agx, agy, 66, 22 * slots - 2));
        float pw = sm("warn").to(Config.get("hud_warn") && minPct < 10 ? 1f : 0f, 12f);
        if (pw > 0.01f) {
            String s = "Armor is breaking!";
            int w = Ui.w(tr, s) + 18;
            int wy = 26 - Math.round((1f - pw) * 8f);
            Ui.GA = pw;
            Ui.rrect(ctx, sw / 2 - w / 2, wy, w, 17, 8, 0xD0501418);
            Ui.draw(ctx, tr, s, sw / 2 - w / 2 + 9, wy + 5, 0xFFFF8A8A, false);
            Ui.GA = 1f;
        }

        keystrokes(mc, sh);
        editOverlay();
        toasts();
    }

    /** Клавиши WASD, мышь и пробел: при нажатии плавно заливаются цветом темы. */
    private static void keystrokes(Minecraft mc, int sh) {
        float p = sm("keys").to(Config.get("hud_keys") ? 1f : 0f, 12f);
        if (p <= 0.01f) return;
        boolean extra = Config.setting("hud_keys", "extra") == 1;
        int kh = extra ? 86 : 46;
        double[] kf = Config.HUD_POS.get("keys");
        int bx = kf != null ? (int) Math.round(kf[0]) : 8 - Math.round((1f - p) * 24f);
        int by = kf != null ? (int) Math.round(kf[1]) : sh - 24 - kh;
        bx = clamp(bx, 0, SW - 70);
        by = clamp(by, 0, sh - kh);
        hits.add(new Box("keys", bx, by, 70, kh));
        Ui.GA = p;
        key("W", "W", bx + 24, by, 22, 22, mc.options.keyUp.isDown());
        key("A", "A", bx, by + 24, 22, 22, mc.options.keyLeft.isDown());
        key("S", "S", bx + 24, by + 24, 22, 22, mc.options.keyDown.isDown());
        key("D", "D", bx + 48, by + 24, 22, 22, mc.options.keyRight.isDown());
        if (extra) {
            key("LMB", "LMB", bx, by + 48, 34, 22, mc.options.keyAttack.isDown());
            key("RMB", "RMB", bx + 36, by + 48, 34, 22, mc.options.keyUse.isDown());
            key("SPC", "", bx, by + 72, 70, 14, mc.options.keyJump.isDown());
        }
        Ui.GA = 1f;
    }

    private static void key(String id, String s, int x, int y, int w, int h, boolean down) {
        float p = sm("k_" + id).to(down ? 1f : 0f, 26f);
        Ui.rrect(G, x, y, w, h, 7, Ui.lerp(BG, ACC, p));
        int tc = Ui.lerp(0xFFFFFFFF, 0xFF04232B, p);
        if (s.isEmpty()) {
            Ui.rrect(G, x + w / 2 - 12, y + h / 2 - 1, 24, 2, 1, tc);
        } else {
            Ui.draw(G, TR, s, x + w / 2 - Ui.w(TR, s) / 2, y + h / 2 - 4, tc, false);
        }
    }

    /** Список включённых функций справа: появляются и исчезают плавно, цвет переливается между цветами темы. */
    private static void arrayList(float startY) {
        boolean master = Config.get("hud_arraylist");
        boolean grad = Config.setting("hud_arraylist", "gradient") == 1;
        boolean bg = Config.setting("hud_arraylist", "bg") == 1;
        List<String[]> mods = new ArrayList<>();
        for (String[] f : Config.FEATURES) {
            if (!f[0].equals("Interface")) mods.add(f);
        }
        mods.sort((a, b) -> Ui.w(TR, b[2]) - Ui.w(TR, a[2]));
        double[] lf = Config.HUD_POS.get("al");
        int rightEdge = lf != null ? clamp((int) Math.round(lf[0]), 40, SW) : SW - 4;
        float y = lf != null ? clamp((int) Math.round(lf[1]), 0, SH - 16) : startY;
        float topY = y;
        int minX = rightEdge;
        int i = 0;
        double time = System.currentTimeMillis() / 1000.0;
        for (String[] f : mods) {
            float p = sm("al_" + f[1]).to(master && Config.get(f[1]) ? 1f : 0f, 12f);
            if (p <= 0.01f) continue;
            int tw = Ui.w(TR, f[2]);
            int w = tw + 16;
            int px = rightEdge - w + Math.round((1f - p) * (w + 6));
            int py = Math.round(y);
            minX = Math.min(minX, rightEdge - w);
            float u = grad ? (float) (0.5 + 0.5 * Math.sin(time * 1.3 + i * 0.55)) : 0f;
            int col = Ui.lerp(ACC, ACC2, u);
            Ui.GA = p;
            if (bg) Ui.rrect(G, px, py, w, 14, 6, BG);
            Ui.draw(G, TR, f[2], px + 7, py + 3, col, false);
            Ui.rrect(G, px + w - 4, py + 3, 2, 8, 1, col);
            Ui.GA = 1f;
            y += 16f * p;
            i++;
        }
        if (i > 0) hits.add(new Box("al", minX, Math.round(topY), rightEdge - minX, Math.max(14, Math.round(y - topY) - 2)));
    }

    /** Следим за переключателями функций и показываем уведомления. */
    private static void pollToggles() {
        boolean first = PREV.isEmpty();
        boolean notify = Config.get("hud_notify");
        for (String[] f : Config.FEATURES) {
            boolean now = Config.get(f[1]);
            Boolean was = PREV.put(f[1], now);
            if (!first && was != null && was != now && notify && !f[1].equals("hud_notify")) {
                TOASTS.add(new Toast(f[2], now, System.currentTimeMillis()));
            }
        }
        while (TOASTS.size() > 4) TOASTS.remove(0);
    }

    private static void toasts() {
        long now = System.currentTimeMillis();
        TOASTS.removeIf(t -> now - t.at() > 2300);
        float y = 28f;
        for (Toast t : TOASTS) {
            long age = now - t.at();
            float in = Math.min(1f, age / 220f);
            float out = age > 1900 ? Math.max(0f, 1f - (age - 1900) / 400f) : 1f;
            float p = (1f - (1f - in) * (1f - in)) * out;
            String val = t.on() ? "Enabled" : "Disabled";
            int vcol = t.on() ? 0xFF7CE08A : 0xFFFF8A8A;
            int w = Ui.w(TR, t.name()) + Ui.w(TR, val) + 38;
            int px = SW / 2 - w / 2;
            int py = Math.round(y) - Math.round((1f - in) * 8f);
            Ui.GA = p;
            Ui.rrect(G, px, py, w, 19, 9, BG);
            Ui.rrect(G, px + 7, py + 7, 5, 5, 2, vcol);
            Ui.draw(G, TR, t.name(), px + 18, py + 6, 0xFFFFFFFF, false);
            Ui.draw(G, TR, val, px + 18 + Ui.w(TR, t.name()) + 8, py + 6, vcol, false);
            Ui.GA = 1f;
            y += 23f * p;
        }
    }
}

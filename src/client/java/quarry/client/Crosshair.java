package quarry.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Свой прицел. Рисуется в физических пикселях экрана, поэтому линии тонкие и чёткие при любом масштабе интерфейса. */
public class Crosshair {
    private static final Smooth GAP = new Smooth(), RED = new Smooth();

    public static void render(GuiGraphics ctx, Minecraft mc) {
        if (!Config.get("crosshair") || mc.player == null || mc.screen != null) return;
        if (!mc.options.getCameraType().isFirstPerson() || mc.player.isSpectator()) return;

        int k = (int) Math.max(1, Math.round(mc.getWindow().getGuiScale()));
        int cx = ctx.guiWidth() * k / 2, cy = ctx.guiHeight() * k / 2;
        int style = Config.setting("crosshair", "style");
        int len = Config.setting("crosshair", "size") * k / 2 + 2;
        int gap = Config.setting("crosshair", "gap") * k / 2;
        int th = Config.setting("crosshair", "thickness");
        boolean outline = Config.setting("crosshair", "outline") == 1;

        // при движении прицел «раскрывается»
        float move = 0f;
        if (Config.setting("crosshair", "dynamic") == 1) {
            Vec3 v = mc.player.getDeltaMovement();
            move = (float) Math.min(1.0, Math.sqrt(v.x * v.x + v.z * v.z) * 6.0);
        }
        gap += Math.round(GAP.to(move, 10f) * 3f * k);

        // цвет: тема, белый, красный или радуга; при наведении на существо плавно краснеет
        int c = Config.setting("crosshair", "color");
        int col = c == 1 ? 0xFFFFFFFF : c == 2 ? 0xFFFF5C5C : c == 3
                ? Fx.hsv((float) ((System.currentTimeMillis() % 6000L) / 6000.0), 0.6f, 1f) : Config.accent();
        boolean over = Config.setting("crosshair", "target") == 1 && mc.crosshairPickEntity instanceof LivingEntity;
        col = Ui.lerp(col, 0xFFFF5C5C, RED.to(over ? 1f : 0f, 14f));

        ctx.pose().pushPose();
        ctx.pose().scale(1f / k, 1f / k, 1f);
        if (outline) shape(ctx, cx, cy, style, len, gap, th, 0xB0000000, 1);
        shape(ctx, cx, cy, style, len, gap, th, col, 0);
        ctx.pose().popPose();
    }

    private static void r(GuiGraphics g, int x1, int y1, int x2, int y2, int col, int pad) {
        Ui.fill(g, x1 - pad, y1 - pad, x2 + pad, y2 + pad, col);
    }

    private static void shape(GuiGraphics g, int cx, int cy, int style, int len, int gap, int t, int col, int pad) {
        int h = t / 2, h2 = t - h;
        switch (style) {
            case 1: // точка
                r(g, cx - len / 3 - h, cy - len / 3 - h, cx + len / 3 + h2, cy + len / 3 + h2, col, pad);
                break;
            case 2: { // кольцо
                int rad = len + gap;
                for (int i = 0; i < 72; i++) {
                    double a = i * Math.PI / 36.0;
                    int px = cx + (int) Math.round(Math.cos(a) * rad), py = cy + (int) Math.round(Math.sin(a) * rad);
                    r(g, px - h, py - h, px + h2, py + h2, col, pad);
                }
                break;
            }
            case 3: { // квадрат
                int rad = len + gap;
                r(g, cx - rad, cy - rad - h, cx + rad + h2, cy - rad + h2, col, pad);
                r(g, cx - rad, cy + rad - h, cx + rad + h2, cy + rad + h2, col, pad);
                r(g, cx - rad - h, cy - rad, cx - rad + h2, cy + rad, col, pad);
                r(g, cx + rad - h, cy - rad, cx + rad + h2, cy + rad, col, pad);
                break;
            }
            case 4: { // ромб
                int rad = len + gap;
                for (int i = 0; i <= rad; i++) {
                    r(g, cx + i - h, cy - rad + i - h, cx + i + h2, cy - rad + i + h2, col, pad);
                    r(g, cx - i - h, cy - rad + i - h, cx - i + h2, cy - rad + i + h2, col, pad);
                    r(g, cx + i - h, cy + rad - i - h, cx + i + h2, cy + rad - i + h2, col, pad);
                    r(g, cx - i - h, cy + rad - i - h, cx - i + h2, cy + rad - i + h2, col, pad);
                }
                break;
            }
            default: // крест с зазором
                r(g, cx - gap - len, cy - h, cx - gap, cy + h2, col, pad);
                r(g, cx + gap, cy - h, cx + gap + len, cy + h2, col, pad);
                r(g, cx - h, cy - gap - len, cx + h2, cy - gap, col, pad);
                r(g, cx - h, cy + gap, cx + h2, cy + gap + len, col, pad);
                break;
        }
    }
}

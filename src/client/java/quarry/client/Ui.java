package quarry.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public class Ui {
    // тёплая палитра
    public static final int PANEL = 0xF01A1210, PANEL_BORDER = 0xFF3B2B23, SIDE = 0xFF120C0A;
    public static final int CARD = 0xFF241914, CARD_HOV = 0xFF2E201A, CARD_BORDER = 0xFF372921;
    public static final int TEXT = 0xFFF6ECE4, DIM = 0xFFB09D90, OFF = 0xFF8D7B70, TRACK = 0xFF3A2B23, PILL = 0xFF1C1411;
    public static final int HUD_BG = 0xC01B1210;

    /** Общая прозрачность: все, что рисует Ui, умножается на неё (плавное появление и исчезновение). */
    public static float GA = 1f;

    public static int fade(int col) {
        if (GA >= 0.999f) return col;
        int a = (int) (((col >>> 24) & 255) * Math.max(0f, GA));
        return (a << 24) | (col & 0x00FFFFFF);
    }

    public static void fill(GuiGraphics c, int x1, int y1, int x2, int y2, int col) {
        col = fade(col);
        if ((col >>> 24) == 0) return;
        c.fill(x1, y1, x2, y2, col);
    }

    private static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("quarry_client", "ui");
    private static final ResourceLocation FONT_BOLD = ResourceLocation.fromNamespaceAndPath("quarry_client", "ui_bold");

    public static final Map<String, String[]> ICONS = Map.of(
            "layers", new String[]{"...##...", "..####..", ".######.", "........", ".######.", "........", ".######.", "........"},
            "spark", new String[]{"...#....", "...#....", "..###...", "#######.", "..###...", "...#....", "...#....", "........"},
            "search", new String[]{"..###...", ".#...#..", ".#...#..", ".#...#..", "..###...", "....##..", ".....##.", "......#."},
            "gear", new String[]{"..#..#..", ".######.", ".##..##.", "##....##", "##....##", ".##..##.", ".######.", "..#..#.."},
            "drop", new String[]{"...#....", "..###...", ".#####..", ".#####..", "#######.", "#######.", ".#####..", "..###..."},
            "flake", new String[]{"#..#..#.", ".#.#.#..", "..###...", "#######.", "..###...", ".#.#.#..", "#..#..#.", "........"});

    /** Текст нашим шрифтом (Inter). */
    public static Component comp(String s, boolean bold) {
        return Component.literal(s).withStyle(Style.EMPTY.withFont(bold ? FONT_BOLD : FONT));
    }

    public static void draw(GuiGraphics c, Font f, String s, int x, int y, int col, boolean shadow) {
        col = fade(col);
        if ((col >>> 24) < 5) return;
        c.drawString(f, comp(s, false), x, y, col, shadow);
    }

    public static int w(Font f, String s) {
        return f.width(comp(s, false));
    }

    public static int width(Font f, String s, boolean bold) {
        return f.width(comp(s, bold));
    }

    public static String trim(Font f, String s, int maxW) {
        if (w(f, s) <= maxW) return s;
        int n = s.length();
        while (n > 0 && w(f, s.substring(0, n) + "…") > maxW) n--;
        return s.substring(0, n) + "…";
    }

    public static void text(GuiGraphics c, Font f, String s, int x, int y, int col, float scale, boolean bold, boolean shadow) {
        col = fade(col);
        if ((col >>> 24) < 5) return;
        c.pose().pushPose();
        c.pose().translate((float) x, (float) y, 0f);
        c.pose().scale(scale, scale, 1f);
        c.drawString(f, comp(s, bold), 0, 0, col, shadow);
        c.pose().popPose();
    }

    /** Скруглённый прямоугольник со сглаженными краями; при r = высота/2 получается «таблетка». */
    /**
     * Скруглённый прямоугольник. Рисуется в «физических» пикселях экрана (а не в крупных пикселях интерфейса),
     * поэтому края получаются гладкими при любом масштабе GUI.
     */
    public static void rrect(GuiGraphics c, int x, int y, int w, int h, int r, int col) {
        col = fade(col);
        if ((col >>> 24) == 0 || w <= 0 || h <= 0) return;
        int k = (int) Math.max(1, Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
        if (k > 1) {
            c.pose().pushPose();
            c.pose().scale(1f / k, 1f / k, 1f);
            rr(c, x * k, y * k, w * k, h * k, r * k, col);
            c.pose().popPose();
        } else {
            rr(c, x, y, w, h, r, col);
        }
    }

    /** Как rrect, но без повышенной точности: для мягких теней и свечений (там края всё равно не видны). */
    public static void rrectLow(GuiGraphics c, int x, int y, int w, int h, int r, int col) {
        col = fade(col);
        if ((col >>> 24) == 0 || w <= 0 || h <= 0) return;
        rr(c, x, y, w, h, r, col);
    }

    private static void rr(GuiGraphics c, int x, int y, int w, int h, int r, int col) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        c.fill(x, y + r, x + w, y + h - r, col);
        int a0 = (col >>> 24) & 255;
        for (int i = 0; i < r; i++) {
            double dy = r - (i + 0.5);
            double inset = r - Math.sqrt(Math.max(0, r * r - dy * dy));
            int whole = (int) Math.ceil(inset);
            c.fill(x + whole, y + i, x + w - whole, y + i + 1, col);
            c.fill(x + whole, y + h - 1 - i, x + w - whole, y + h - i, col);
            if (whole > 0) {
                int ac = (col & 0x00FFFFFF) | (((int) (a0 * (whole - inset))) << 24);
                c.fill(x + whole - 1, y + i, x + whole, y + i + 1, ac);
                c.fill(x + w - whole, y + i, x + w - whole + 1, y + i + 1, ac);
                c.fill(x + whole - 1, y + h - 1 - i, x + whole, y + h - i, ac);
                c.fill(x + w - whole, y + h - 1 - i, x + w - whole + 1, y + h - i, ac);
            }
        }
    }

    public static void panel(GuiGraphics c, int x, int y, int w, int h, int r, int fill, int border) {
        rrect(c, x, y, w, h, r, border);
        rrect(c, x + 1, y + 1, w - 2, h - 2, r - 1, fill);
    }

    public static void shadow(GuiGraphics c, int x, int y, int w, int h, int r) {
        for (int k = 9; k >= 1; k--) {
            int a = (int) (26 * (1 - k / 10.0));
            rrectLow(c, x - k, y - k + 3, w + 2 * k, h + 2 * k, r + k, a << 24);
        }
    }

    public static void icon(GuiGraphics c, String name, int x, int y, int s, int col) {
        String[] m = ICONS.get(name);
        if (m == null) return;
        for (int r = 0; r < m.length; r++) {
            for (int k = 0; k < m[r].length(); k++) {
                if (m[r].charAt(k) == '#') fill(c, x + k * s, y + r * s, x + k * s + s, y + r * s + s, col);
            }
        }
    }

    /** Иконка из масок Glyphs: рисуется в физических пикселях, поэтому гладкая при любом масштабе интерфейса. */
    public static void glyph(GuiGraphics c, String name, int x, int y, int size, int col) {
        int k = (int) Math.max(1, Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
        int p = size * k;
        int[] a = Glyphs.sample(name, p);
        if (a == null) return;
        int ca = (col >>> 24) & 255;
        c.pose().pushPose();
        c.pose().scale(1f / k, 1f / k, 1f);
        for (int py = 0; py < p; py++) {
            int px = 0;
            while (px < p) {
                int v = a[py * p + px];
                if (v == 0) {
                    px++;
                    continue;
                }
                int e = px;
                while (e < p && a[py * p + e] == v) e++;
                int al = Math.round(ca * v / 15f);
                fill(c, x * k + px, y * k + py, x * k + e, y * k + py + 1, (al << 24) | (col & 0x00FFFFFF));
                px = e;
            }
        }
        c.pose().popPose();
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int s = 24; s >= 0; s -= 8) {
            int ca = (a >> s) & 255, cb = (b >> s) & 255;
            out |= ((int) (ca + (cb - ca) * t) & 255) << s;
        }
        return out;
    }
}

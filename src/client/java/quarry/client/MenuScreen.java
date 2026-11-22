package quarry.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MenuScreen extends Screen {
    private static final String[] TAB_CAT = {"Visuals", "Interface", "Helper"};
    private static final String[] TAB_RU = {"Visuals", "HUD", "Utilities"};
    private static final int W = 480, H = 300, SIDE_W = 214;

    private static int tab = 0;
    private static int page = 0; // 0 — функции, 1 — темы
    private int scroll = 0, maxScroll = 0, clipTop = 0, clipBottom = 0;
    private String query = "";
    private boolean searching = false;
    private String settingsFor = null;
    private int sx, sy, sw, sh;
    private boolean modal = false;
    private String tip = null;
    private Row dragRow = null;
    private final long opened = System.currentTimeMillis();
    private boolean closing = false;
    private long closeStart = 0;
    private String shownFor = null;
    private final Smooth settingsAnim = new Smooth();
    private final Map<String, Float> anim = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();

    private record Hit(int x, int y, int w, int h, Runnable action, Runnable alt, boolean scrolls) {
        boolean in(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record Row(Config.Setting s, int x, int y, int w, int h, int trackX, int trackW) {
    }

    public MenuScreen() {
        super(Component.literal("Quarry"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Отключаем стандартное размытие фона: затемнение рисуем сами в render(). */
    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
    }

    public boolean isClosing() {
        return closing;
    }

    /** Плавное появление (замедляется к концу) и исчезновение (ускоряется) всего меню. */
    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        float p;
        if (closing) {
            float c = Math.min(1f, (now - closeStart) / 190f);
            p = 1f - c * c;
        } else {
            float o = Math.min(1f, (now - opened) / 230f);
            p = 1f - (1f - o) * (1f - o) * (1f - o);
        }
        Ui.GA = p;
        try {
            renderMenu(ctx, mouseX, mouseY, delta, p);
        } finally {
            Ui.GA = 1f;
        }
    }

    private void renderMenu(GuiGraphics ctx, int mouseX, int mouseY, float delta, float prog) {
        hits.clear();
        rows.clear();
        tip = null;
        int acc = Config.accent();
        Ui.fill(ctx, 0, 0, width, height, 0x88000000);
        boolean xmas = Config.get("christmas");
        if (xmas && Config.setting("christmas", "menu_snow") == 1) Snow.render(ctx, width, height, Config.setting("christmas", "density"));

        float sc = 0.92f + 0.08f * prog;
        ctx.pose().pushPose();
        ctx.pose().translate(0f, (1f - prog) * 14f, 0f);
        ctx.pose().translate(width / 2f, height / 2f, 0f);
        ctx.pose().scale(sc, sc, 1f);
        ctx.pose().translate(-width / 2f, -height / 2f, 0f);

        int px = (width - W) / 2, py = (height - H) / 2 - 24;
        Ui.shadow(ctx, px, py, W, H, 18);
        Ui.panel(ctx, px, py, W, H, 18, Ui.PANEL, Ui.PANEL_BORDER);
        if (xmas && Config.setting("christmas", "garland") == 1) garland(ctx, px, py);

        // вкладки сверху: Визуалы / HUD / Утилиты
        int tx = px + 22;
        for (int i = 0; i < TAB_RU.length; i++) {
            boolean sel = tab == i && page == 0 && query.isEmpty();
            String nm = TAB_RU[i];
            int w = Ui.width(font, nm, sel);
            Ui.text(ctx, font, nm, tx, py + 20, sel ? 0xFFFFFFFF : Ui.DIM, 1f, sel, false);
            if (sel) Ui.rrect(ctx, tx, py + 32, w, 2, 1, acc);
            final int idx = i;
            hits.add(new Hit(tx - 4, py + 12, w + 8, 24, () -> {
                tab = idx;
                page = 0;
                query = "";
                scroll = 0;
            }, null, false));
            tx += w + 10;
            if (i < TAB_RU.length - 1) {
                Ui.draw(ctx, font, "/", tx, py + 20, 0xFF6B584C, false);
                tx += Ui.w(font, "/") + 10;
            }
        }

        // поиск справа
        int sbx = px + W - 176, sby = py + 11, sbw = 156;
        Ui.panel(ctx, sbx, sby, sbw, 24, 12, Ui.CARD, searching ? acc : Ui.PANEL_BORDER);
        Ui.glyph(ctx, "search", sbx + 9, sby + 7, 10, Ui.DIM);
        boolean caret = searching && (System.currentTimeMillis() / 500) % 2 == 0;
        if (query.isEmpty() && !searching) Ui.draw(ctx, font, "Search", sbx + 24, sby + 8, Ui.OFF, false);
        else Ui.draw(ctx, font, query + (caret ? "|" : ""), sbx + 24, sby + 8, 0xFFFFFFFF, false);
        hits.add(new Hit(sbx, sby, sbw, 24, () -> searching = true, null, false));

        Ui.fill(ctx, px + 16, py + 46, px + W - 16, py + 47, Ui.PANEL_BORDER);

        clipTop = py + 56;
        clipBottom = py + H - 12;
        ctx.enableScissor(px + 8, clipTop, px + W - 8, clipBottom);
        boolean themes = page == 1 && query.isEmpty();
        if (themes) {
            int cardW = (W - 32 - 24) / 4;
            for (int i = 0; i < Config.THEME_COLORS.length; i++) {
                int bx = px + 16 + (i % 4) * (cardW + 8), by = clipTop + (i / 4) * 76;
                boolean sel = i == Config.theme();
                int c1 = Config.THEME_COLORS[i], c2 = Config.THEME_COLORS2[i];
                Ui.panel(ctx, bx, by, cardW, 68, 16, sel ? 0xFF2D211B : Ui.CARD, sel ? c1 : Ui.CARD_BORDER);
                Ui.rrect(ctx, bx + cardW / 2 - 14, by + 9, 28, 28, 14, c1);
                int gx = bx + cardW / 2 - 18;
                for (int k = 0; k < 36; k++) Ui.fill(ctx, gx + k, by + 43, gx + k + 1, by + 46, Ui.lerp(c1, c2, k / 35f));
                String nm = Config.THEME_NAMES[i];
                Ui.draw(ctx, font, nm, bx + cardW / 2 - Ui.w(font, nm) / 2, by + 52, sel ? 0xFFFFFFFF : Ui.DIM, false);
                final int idx = i;
                hits.add(new Hit(bx, by, cardW, 68, () -> Config.setTheme(idx), null, false));
            }
            maxScroll = 0;
        } else {
            List<String[]> shown = new ArrayList<>();
            String q = query.toLowerCase();
            for (String[] f : Config.FEATURES) {
                if (q.isEmpty() ? f[0].equals(TAB_CAT[tab])
                        : (f[2].toLowerCase().contains(q) || f[3].toLowerCase().contains(q))) shown.add(f);
            }
            int cardW = (W - 32 - 8) / 2;
            int rowsN = (shown.size() + 1) / 2;
            maxScroll = Math.max(0, rowsN * 44 - (clipBottom - clipTop) + 4);
            scroll = Math.max(0, Math.min(maxScroll, scroll));
            if (shown.isEmpty()) Ui.draw(ctx, font, "Nothing found", px + 18, clipTop + 8, Ui.OFF, false);
            for (int n = 0; n < shown.size(); n++) {
                int bx = px + 16 + (n % 2) * (cardW + 8), by = clipTop + (n / 2) * 44 - scroll;
                if (by + 36 < clipTop || by > clipBottom) continue;
                card(ctx, shown.get(n), bx, by, cardW, mouseX, mouseY, delta, acc);
            }
        }
        ctx.disableScissor();
        if (maxScroll > 0) {
            int trackH = clipBottom - clipTop;
            int barH = Math.max(24, trackH * trackH / (trackH + maxScroll));
            Ui.rrect(ctx, px + W - 12, clipTop + (trackH - barH) * scroll / maxScroll, 3, barH, 1, 0xFF4F3B30);
        }

        // нижняя панель-«док»
        int dw = 106, dh = 46, dx = (width - dw) / 2, dy = py + H + 14;
        Ui.shadow(ctx, dx, dy, dw, dh, 18);
        Ui.panel(ctx, dx, dy, dw, dh, 18, Ui.PANEL, Ui.PANEL_BORDER);
        String[] dockIcons = {"gear", "palette"};
        for (int i = 0; i < 2; i++) {
            int bx = dx + 12 + i * 46;
            boolean sel = page == i;
            Ui.rrect(ctx, bx, dy + 6, 34, 34, 17, sel ? acc : Ui.PILL);
            Ui.glyph(ctx, dockIcons[i], bx + 8, dy + 14, 18, sel ? 0xFF0B1116 : Ui.DIM);
            final int idx = i;
            hits.add(new Hit(bx, dy + 6, 34, 34, () -> {
                page = idx;
                query = "";
                scroll = 0;
            }, null, false));
        }
        String hint = "LMB toggle   ·   RMB settings   ·   Right Shift close";
        Ui.draw(ctx, font, hint, (width - Ui.w(font, hint)) / 2, dy + dh + 10, 0xFF8D7B70, false);

        float sa = settingsAnim.to(settingsFor != null ? 1f : 0f, 16f);
        if (settingsFor != null) shownFor = settingsFor;
        else if (sa < 0.02f) shownFor = null;
        if (shownFor != null) {
            String keep = settingsFor;
            settingsFor = shownFor;
            float save = Ui.GA;
            Ui.GA = save * sa;
            ctx.pose().pushPose();
            ctx.pose().translate((1f - sa) * -12f, 0f, 0f);
            drawSettings(ctx, px, py, acc);
            ctx.pose().popPose();
            Ui.GA = save;
            settingsFor = keep;
        }

        if (tip != null && settingsFor == null) {
            int tw = Ui.w(font, tip) + 14;
            int tipX = Math.min(width - tw - 4, mouseX + 12), tipY = mouseY + 14;
            Ui.panel(ctx, tipX, tipY, tw, 18, 8, 0xF0241914, 0xFF4A362B);
            Ui.draw(ctx, font, tip, tipX + 7, tipY + 5, Ui.TEXT, false);
        }

        ctx.pose().popPose();
        super.render(ctx, mouseX, mouseY, delta);
    }

    /** Новогодняя гирлянда и снежная шапка по верхнему краю панели. */
    private void garland(GuiGraphics ctx, int px, int py) {
        for (int x = px + 14; x < px + W - 24; x += 11) Ui.rrect(ctx, x, py - 3, 15, 5 + (x * 7 % 3), 3, 0xFFF4F7FB);
        int[] cols = {0xFFE8454B, 0xFFF2C14E, 0xFF3FBF6A, 0xFF4B8BFF};
        long t = System.currentTimeMillis();
        for (int x = px + 24; x < px + W - 24; x += 2) {
            double u = (x - px - 24) / (double) (W - 48);
            Ui.fill(ctx, x, py + 5 + (int) Math.round(5 * Math.abs(Math.sin(u * Math.PI * 6))), x + 2, py + 6 + (int) Math.round(5 * Math.abs(Math.sin(u * Math.PI * 6))), 0xFF2E5B3A);
        }
        for (int i = 0; i < 10; i++) {
            double u = (i + 0.5) / 10.0;
            int bx = px + 24 + (int) (u * (W - 48));
            int by = py + 8 + (int) Math.round(5 * Math.abs(Math.sin(u * Math.PI * 6)));
            float tw = 0.6f + 0.4f * (float) Math.sin(t / 300.0 + i * 1.7);
            int c = cols[i % 4];
            Ui.rrect(ctx, bx - 4, by - 3, 10, 10, 5, (c & 0x00FFFFFF) | (((int) (70 * tw)) << 24));
            Ui.rrect(ctx, bx - 2, by - 1, 6, 6, 3, c);
        }
    }

    private void card(GuiGraphics ctx, String[] f, int bx, int by, int w, int mx, int my, float delta, int acc) {
        boolean hov = mx >= bx && mx < bx + w && my >= by && my < by + 36 && my >= clipTop && my < clipBottom;
        boolean on = Config.get(f[1]);
        float a = anim.getOrDefault(f[1], on ? 1f : 0f);
        a += ((on ? 1f : 0f) - a) * Math.min(1f, delta * 0.5f);
        anim.put(f[1], a);

        // свечение вокруг включённой функции
        if (a > 0.02f) {
            for (int k = 6; k >= 1; k--) {
                int al = (int) (46 * (1 - k / 7.0) * a);
                Ui.rrectLow(ctx, bx - k, by - k, w + 2 * k, 36 + 2 * k, 18 + k, (acc & 0x00FFFFFF) | (al << 24));
            }
        }
        Ui.panel(ctx, bx, by, w, 36, 18, hov ? Ui.CARD_HOV : Ui.CARD, Ui.lerp(hov ? 0xFF4A362B : Ui.CARD_BORDER, acc, a * 0.85f));
        if (a > 0.02f) Ui.rrect(ctx, bx + 7, by + 9, 3, 18, 1, (acc & 0x00FFFFFF) | (((int) (255 * a)) << 24));
        Ui.draw(ctx, font, f[2], bx + 16, by + 14, on ? 0xFFFFFFFF : Ui.OFF, false);

        int tx = bx + w - 46, ty = by + 10;
        Ui.rrect(ctx, tx, ty, 34, 16, 8, Ui.lerp(Ui.TRACK, acc, a));
        Ui.rrect(ctx, tx + 2 + Math.round(a * 18), ty + 2, 12, 12, 6, 0xFFFFFFFF);
        if (Config.SETTINGS.containsKey(f[1])) {
            for (int i = 0; i < 3; i++) Ui.fill(ctx, tx - 16 + i * 4, by + 17, tx - 14 + i * 4, by + 19, Ui.OFF);
        }
        if (hov) tip = f[3];
        hits.add(new Hit(bx, by, w, 36, () -> Config.toggle(f[1]),
                () -> settingsFor = f[1].equals(settingsFor) ? null : f[1], true));
    }

    /** Панель настроек выбранной функции (открывается правой кнопкой мыши). */
    private void drawSettings(GuiGraphics ctx, int px, int py, int acc) {
        String[] feat = null;
        for (String[] f : Config.FEATURES) if (f[1].equals(settingsFor)) feat = f;
        if (feat == null) {
            settingsFor = null;
            return;
        }
        List<Config.Setting> list = Config.SETTINGS.getOrDefault(settingsFor, List.of());
        int ph = 46;
        for (Config.Setting s : list) ph += s.choices() != null ? 36 : 52;
        if (list.isEmpty()) ph += 30;
        ph += 8;
        sw = SIDE_W;
        sh = ph;
        modal = px - sw - 12 < 4;
        if (modal) {
            sx = (width - sw) / 2;
            sy = (height - sh) / 2;
            Ui.rrect(ctx, px, py, W, H, 18, 0xA0000000);
        } else {
            sx = px - sw - 12;
            sy = py + 40;
        }
        Ui.shadow(ctx, sx, sy, sw, sh, 14);
        Ui.panel(ctx, sx, sy, sw, sh, 14, Ui.PANEL, Ui.PANEL_BORDER);
        Ui.text(ctx, font, feat[2], sx + 16, sy + 14, 0xFFFFFFFF, 1.1f, true, true);

        int ry = sy + 40;
        if (list.isEmpty()) {
            Ui.draw(ctx, font, "No extra settings", sx + 16, ry + 8, Ui.OFF, false);
            return;
        }
        for (Config.Setting s : list) {
            int v = Config.setting(settingsFor, s.key());
            if (s.choices() != null) {
                Ui.draw(ctx, font, s.label(), sx + 16, ry + 11, Ui.TEXT, false);
                int pw = 92, pxx = sx + sw - 16 - pw;
                Ui.panel(ctx, pxx, ry + 2, pw, 24, 12, Ui.PILL, Ui.TRACK);
                String val = s.choices()[v];
                Ui.draw(ctx, font, val, pxx + 10, ry + 10, acc, false);
                Ui.fill(ctx, pxx + pw - 16, ry + 12, pxx + pw - 9, ry + 13, Ui.DIM);
                Ui.fill(ctx, pxx + pw - 15, ry + 13, pxx + pw - 10, ry + 14, Ui.DIM);
                Ui.fill(ctx, pxx + pw - 14, ry + 14, pxx + pw - 11, ry + 15, Ui.DIM);
                rows.add(new Row(s, sx + 8, ry, sw - 16, 36, 0, 0));
                ry += 36;
            } else {
                Ui.draw(ctx, font, s.label(), sx + 16, ry + 4, Ui.TEXT, false);
                String val = String.valueOf(v);
                int bw = Ui.w(font, val) + 14;
                Ui.rrect(ctx, sx + sw - 16 - bw, ry, bw, 18, 9, Ui.PILL);
                Ui.draw(ctx, font, val, sx + sw - 16 - bw + 7, ry + 5, 0xFFFFFFFF, false);
                int trackX = sx + 16, trackW = sw - 32;
                Ui.rrect(ctx, trackX, ry + 31, trackW, 4, 2, Ui.TRACK);
                float frac = (v - s.min()) / (float) Math.max(1, s.max() - s.min());
                int fw = Math.round(trackW * frac);
                Ui.rrect(ctx, trackX, ry + 31, Math.max(4, fw), 4, 2, acc);
                Ui.rrect(ctx, trackX + fw - 6, ry + 27, 12, 12, 6, 0xFFFFFFFF);
                rows.add(new Row(s, sx + 8, ry, sw - 16, 52, trackX, trackW));
                ry += 52;
            }
        }
    }

    private void setSlider(Row r, double mx) {
        double f = Math.max(0, Math.min(1, (mx - r.trackX()) / Math.max(1, r.trackW())));
        int v = r.s().min() + (int) Math.round(f * (r.s().max() - r.s().min()));
        Config.setSettingQuiet(settingsFor, r.s().key(), v);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (closing) return true;
        if (settingsFor != null) {
            if (mx >= sx && mx < sx + sw && my >= sy && my < sy + sh) {
                for (Row r : rows) {
                    if (mx < r.x() || mx >= r.x() + r.w() || my < r.y() || my >= r.y() + r.h()) continue;
                    if (r.s().choices() != null) {
                        int n = r.s().choices().length;
                        int v = Config.setting(settingsFor, r.s().key());
                        Config.setSetting(settingsFor, r.s().key(), (v + (button == 1 ? n - 1 : 1)) % n);
                    } else if (button == 0) {
                        setSlider(r, mx);
                        dragRow = r;
                    }
                    return true;
                }
                return true;
            } else if (modal) {
                settingsFor = null;
                return true;
            }
        }
        searching = false;
        for (Hit h : hits) {
            if (!h.in(mx, my)) continue;
            if (h.scrolls() && (my < clipTop || my >= clipBottom)) continue;
            if (button == 0) {
                h.action().run();
                return true;
            }
            if (button == 1 && h.alt() != null) {
                h.alt().run();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragRow != null) {
            setSlider(dragRow, mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragRow != null) {
            dragRow = null;
            Config.save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (scrollY * 26)));
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (searching && c >= 32 && query.length() < 24) {
            query += c;
            scroll = 0;
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closing) return true;
        if (searching) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
                query = query.substring(0, query.length() - 1);
                scroll = 0;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                searching = false;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && settingsFor != null) {
            settingsFor = null;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Закрытие не мгновенное: меню плавно гаснет, а экран закрывается в tick() через долю секунды. */
    @Override
    public void onClose() {
        if (closing) return;
        closing = true;
        closeStart = System.currentTimeMillis();
        Config.save();
    }

    @Override
    public void tick() {
        super.tick();
        if (closing && System.currentTimeMillis() - closeStart >= 200) {
            super.onClose();
        }
    }
}

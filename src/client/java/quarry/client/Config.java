package quarry.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Config {
    /** Настройка функции: либо выбор из списка (choices), либо ползунок min..max. */
    public record Setting(String key, String label, String[] choices, int min, int max, int def) {
    }

    /** Темы: название, основной цвет и второй цвет для градиентов. */
    public static final String[] THEME_NAMES = {"Ember", "Christmas", "Pine", "Gold", "Rose", "Owl", "Violet", "Ocean",
            "Mint", "Lime", "Ice", "Sunset"};
    public static final int[] THEME_COLORS = {0xFFFF8A4B, 0xFFE8454B, 0xFF3FBF6A, 0xFFF2C14E,
            0xFFFF7BD1, 0xFF35D0E6, 0xFFA66BFF, 0xFF4B8BFF,
            0xFF5CE6C0, 0xFFB6F04A, 0xFF9ADCFF, 0xFFFF5E8A};
    public static final int[] THEME_COLORS2 = {0xFFFFD166, 0xFF3FBF6A, 0xFFA8F0B8, 0xFFFF8A4B,
            0xFFFF9A76, 0xFF4B8BFF, 0xFFFF7BD1, 0xFF35E6D0,
            0xFF4B8BFF, 0xFF3FBF6A, 0xFFC9B6FF, 0xFFFFB347};

    /** категория, id, название, описание */
    public static final String[][] FEATURES = {
        {"Interface", "hud_watermark", "Watermark", "Название клиента и ваш ник в углу экрана"},
        {"Interface", "hud_arraylist", "ArrayList", "Список включённых функций справа, с анимацией"},
        {"Interface", "hud_notify", "Notifications", "Всплывающие уведомления при включении и выключении функций"},
        {"Interface", "quarryusers", "Quarry Users", "Значок ◆ у ников тех, кто тоже играет с Quarry (в TAB и над головой). Отправляет ваш ник на публичный сервис ntfy.sh"},
        {"Interface", "hud_fps", "FPS", "Счётчик кадров сверху экрана"},
        {"Interface", "hud_ping", "Ping", "Задержка до сервера"},
        {"Interface", "hud_coords", "Coordinates", "Ваши координаты и сторона света"},
        {"Interface", "hud_speed", "Speed", "Скорость движения"},
        {"Interface", "hud_clock", "Clock", "Реальное время на вашем компьютере"},
        {"Interface", "hud_world", "World Time", "Время суток внутри игры"},
        {"Interface", "hud_cps", "CPS", "Клики левой кнопкой в секунду"},
        {"Interface", "hud_memory", "Memory", "Сколько ОЗУ использует игра"},
        {"Interface", "hud_armor", "Armor", "Прочность надетой брони"},
        {"Interface", "hud_effects", "Effects", "Активные эффекты зелий"},
        {"Interface", "hud_items", "Items", "Тотемы, яблоки, жемчуг, стрелы"},
        {"Interface", "hud_keys", "Keys", "Нажатые клавиши WASD"},
        {"Interface", "hud_warn", "Armor Alert", "Предупреждение, если броня на исходе"},
        {"Interface", "hud_target", "Target HUD", "Имя, дистанция и здоровье того, на кого вы смотрите"},
        {"Interface", "hud_combo", "Combo", "Сколько ударов подряд вы нанесли"},
        {"Interface", "hud_reach", "Reach", "С какого расстояния был последний удар"},
        {"Interface", "hud_cooldowns", "Cooldowns", "Перезарядка жемчуга, яблок, щита"},
        {"Visuals", "christmas", "Christmas", "Новый год: снег в мире и в меню, гирлянда, шапка Санты на игроках"},
        {"Visuals", "nametags", "NameTags", "Красивые ники над игроками: тёмная плашка, здоровье, предметы"},
        {"Visuals", "jumpcircles", "Jump Circles", "Светящиеся круги, которые расходятся при прыжке"},
        {"Visuals", "chinahat", "China Hat", "Светящаяся шляпа-конус на голове (видна в режиме F5)"},
        {"Visuals", "dmgnum", "Damage Numbers", "Всплывающие цифры урона над целью"},
        {"Visuals", "hitsound", "Hit Sound", "Звук, когда вы попадаете по цели"},
        {"Visuals", "blockoutline", "Block Outline", "Цветная подсветка блока, на который вы смотрите"},
        {"Visuals", "customtime", "Custom Time", "Своё время суток (видите только вы)"},
        {"Visuals", "customweather", "Custom Weather", "Своя погода (видите только вы)"},
        {"Visuals", "crosshair", "Custom Crosshair", "Свой прицел: крест, точка, кольцо, квадрат или ромб"},
        {"Visuals", "hands", "Custom Hands", "Положение и размер рук и предмета от первого лица"},
        {"Visuals", "hitcolor", "Hit Color", "Цветная вспышка на цели, когда вы по ней попадаете"},
        {"Visuals", "trail", "Trail", "Частицы за вашим персонажем (видны только в режиме F5)"},
        {"Visuals", "zoom", "Zoom", "Приближение по зажатой клавише C"},
        {"Visuals", "ring", "Crosshair Ring", "Цветное кольцо вокруг прицела"},
        {"Visuals", "edge_glow", "Edge Glow", "Подсветка сверху и снизу экрана"},
        {"Visuals", "hitbox", "Hitboxes", "Контуры существ, не видны сквозь стены"},
        {"Visuals", "target_esp", "Target ESP", "Эффект вокруг цели: призраки, ромбы, кольцо, шары или рамка"},
        {"Visuals", "hit_fx", "Hit Effects", "Частицы при вашем ударе по цели"},
        {"Helper", "sprint", "Auto Sprint", "Бег без зажатия клавиши, как «Переключение спринта»"},
        {"Helper", "norender", "NoRender", "Убирает огонь перед лицом, тряску и эффекты поля зрения"},
        {"Helper", "fullbright", "FullBright", "Максимальная яркость в тёмных местах (проверьте правила сервера)"},
        {"Helper", "lowhp", "Low Health", "Красная подсветка экрана при низком здоровье"},
        {"Helper", "optimize", "Optimization", "Прорисовка 4 чанка и ускорение игры"},
    };

    private static String[] onOff() {
        return new String[]{"Off", "On"};
    }

    private static String[] colors() {
        return new String[]{"Theme", "White", "Red", "Green"};
    }

    /** Цвет ESP: «Theme» (текущая тема), все цвета тем, белый и радуга. */
    private static String[] espColors() {
        int n = THEME_NAMES.length;
        String[] out = new String[n + 3];
        out[0] = "Theme";
        System.arraycopy(THEME_NAMES, 0, out, 1, n);
        out[n + 1] = "White";
        out[n + 2] = "Rainbow";
        return out;
    }

    private static String[] offKeep() {
        return new String[]{"Disable", "Keep"};
    }

    private static String[] hideKeep() {
        return new String[]{"Hide", "Keep"};
    }

    public static final Map<String, List<Setting>> SETTINGS = Map.ofEntries(
        Map.entry("christmas", List.of(
            new Setting("menu_snow", "Menu Snow", onOff(), 0, 0, 1),
            new Setting("inv_snow", "Inventory Snow", onOff(), 0, 0, 1),
            new Setting("world_snow", "World Snow", onOff(), 0, 0, 1),
            new Setting("hat_self", "Santa Hat (Me)", onOff(), 0, 0, 1),
            new Setting("hat_others", "Santa Hat (Others)", onOff(), 0, 0, 1),
            new Setting("garland", "Garland", onOff(), 0, 0, 1),
            new Setting("density", "Snow Amount", null, 20, 200, 90))),
        Map.entry("trail", List.of(
            new Setting("type", "Particle", new String[]{"Theme Color", "Stars", "Fire", "Souls", "Hearts", "Notes", "Enchant", "Ribbon", "Rainbow Ribbon"}, 0, 0, 0),
            new Setting("density", "Density", null, 1, 8, 3),
            new Setting("length", "Ribbon Length", null, 8, 60, 26),
            new Setting("still", "Show When Still", onOff(), 0, 0, 0))),
        Map.entry("crosshair", List.of(
            new Setting("style", "Style", new String[]{"Cross", "Dot", "Circle", "Square", "Diamond"}, 0, 0, 0),
            new Setting("color", "Color", new String[]{"Theme", "White", "Red", "Rainbow"}, 0, 0, 0),
            new Setting("target", "Red On Target", onOff(), 0, 0, 1),
            new Setting("outline", "Outline", onOff(), 0, 0, 1),
            new Setting("dynamic", "Spread On Move", onOff(), 0, 0, 1),
            new Setting("size", "Size", null, 2, 20, 8),
            new Setting("gap", "Gap", null, 0, 12, 4),
            new Setting("thickness", "Thickness", null, 1, 6, 2))),
        Map.entry("hands", List.of(
            new Setting("x", "Left / Right", null, -50, 50, 0),
            new Setting("y", "Down / Up", null, -50, 50, 0),
            new Setting("z", "Far / Close", null, -50, 50, 0),
            new Setting("scale", "Size", null, 50, 150, 100))),
        Map.entry("hitcolor", List.of(
            new Setting("color", "Color", new String[]{"Red", "Theme", "White", "Rainbow"}, 0, 0, 0),
            new Setting("strength", "Strength", null, 10, 100, 60),
            new Setting("duration", "Duration", null, 100, 800, 350))),
        Map.entry("nametags", List.of(
            new Setting("health", "Health Bar", onOff(), 0, 0, 1),
            new Setting("items", "Armor & Items", onOff(), 0, 0, 1),
            new Setting("scaling", "Distance Scale", onOff(), 0, 0, 1),
            new Setting("self", "Show Own (F5)", onOff(), 0, 0, 0),
            new Setting("size", "Size", null, 60, 160, 100),
            new Setting("range", "Range", null, 16, 128, 64))),
        Map.entry("jumpcircles", List.of(
            new Setting("color", "Color", new String[]{"Theme", "Rainbow"}, 0, 0, 0),
            new Setting("size", "Size", null, 5, 30, 14))),
        Map.entry("chinahat", List.of(
            new Setting("color", "Color", new String[]{"Theme", "Rainbow"}, 0, 0, 0),
            new Setting("self", "On Me", onOff(), 0, 0, 1),
            new Setting("others", "On Others", onOff(), 0, 0, 1),
            new Setting("size", "Size", null, 6, 16, 10))),
        Map.entry("dmgnum", List.of(
            new Setting("range", "Range", null, 8, 48, 24),
            new Setting("size", "Size", null, 60, 160, 100))),
        Map.entry("hitsound", List.of(
            new Setting("type", "Sound", new String[]{"Ding", "Arrow", "Crystal", "Level", "Pop"}, 0, 0, 0),
            new Setting("volume", "Volume", null, 1, 10, 6),
            new Setting("pitch", "Pitch", null, 5, 20, 12))),
        Map.entry("blockoutline", List.of(
            new Setting("color", "Color", new String[]{"Theme", "Rainbow"}, 0, 0, 0),
            new Setting("fill", "Fill", onOff(), 0, 0, 1))),
        Map.entry("customtime", List.of(new Setting("hour", "Hour", null, 0, 23, 12))),
        Map.entry("customweather", List.of(new Setting("type", "Weather", new String[]{"Clear", "Rain", "Thunder"}, 0, 0, 0))),
        Map.entry("hud_target", List.of(new Setting("mode", "Position", new String[]{"Fixed", "Follow"}, 0, 0, 0))),
        Map.entry("zoom", List.of(new Setting("fov", "Zoom FOV", null, 30, 60, 30))),
        Map.entry("ring", List.of(new Setting("radius", "Ring Radius", null, 4, 16, 8))),
        Map.entry("edge_glow", List.of(new Setting("power", "Intensity", null, 10, 100, 50))),
        Map.entry("hitbox", List.of(
            new Setting("target", "Show", new String[]{"Players", "Mobs", "All"}, 0, 0, 0),
            new Setting("color", "Color", colors(), 0, 0, 0),
            new Setting("alpha", "Opacity", null, 20, 100, 100),
            new Setting("range", "Range", null, 8, 64, 32))),
        Map.entry("target_esp", List.of(
            new Setting("style", "Style", new String[]{"Ghosts", "Diamond", "Diamonds", "Halo", "Orbs", "Dashed Box", "Solid Box"}, 0, 0, 0),
            new Setting("color", "Color", espColors(), 0, 0, 0),
            new Setting("dust", "Floating Dots", onOff(), 0, 0, 1),
            new Setting("size", "Size", null, 5, 20, 10),
            new Setting("speed", "Speed", null, 0, 20, 8),
            new Setting("dash", "Box Dash Length", null, 4, 30, 14))),
        Map.entry("hit_fx", List.of(
            new Setting("type", "Particle", new String[]{"Crit", "Sparks", "Hearts", "Fire", "Enchant"}, 0, 0, 0),
            new Setting("count", "Amount", null, 1, 12, 6))),
        Map.entry("sprint", List.of(new Setting("water", "Sprint In Water", onOff(), 0, 0, 0))),
        Map.entry("norender", List.of(
            new Setting("fire", "Fire Overlay", hideKeep(), 0, 0, 0),
            new Setting("hurt", "Hurt Shake", hideKeep(), 0, 0, 0),
            new Setting("fov", "FOV Effects", hideKeep(), 0, 0, 0),
            new Setting("nausea", "Nausea / Portal", hideKeep(), 0, 0, 0),
            new Setting("bob", "View Bobbing", hideKeep(), 0, 0, 1))),
        Map.entry("hud_keys", List.of(new Setting("extra", "Mouse & Space", onOff(), 0, 0, 1))),
        Map.entry("hud_watermark", List.of(new Setting("name", "Show Nickname", onOff(), 0, 0, 1))),
        Map.entry("hud_arraylist", List.of(
            new Setting("gradient", "Gradient", onOff(), 0, 0, 1),
            new Setting("bg", "Background", onOff(), 0, 0, 1))),
        Map.entry("hud_clock", List.of(new Setting("fmt", "Format", new String[]{"24h", "12h"}, 0, 0, 0))),
        Map.entry("hud_speed", List.of(new Setting("unit", "Units", new String[]{"blocks/s", "km/h"}, 0, 0, 0))),
        Map.entry("optimize", List.of(
            new Setting("rd", "Render Distance", null, 2, 16, 4),
            new Setting("sim", "Simulation Dist.", null, 5, 12, 6),
            new Setting("particles", "Particles", new String[]{"Keep", "Fewer", "Minimal"}, 0, 0, 0),
            new Setting("clouds", "Clouds", offKeep(), 0, 0, 0),
            new Setting("shadows", "Entity Shadows", offKeep(), 0, 0, 0),
            new Setting("blend", "Biome Blend", offKeep(), 0, 0, 0),
            new Setting("gfx", "Graphics", new String[]{"Fast", "Keep"}, 0, 0, 0),
            new Setting("fps", "FPS Limit", new String[]{"Unlimited", "Keep"}, 0, 0, 0),
            new Setting("vsync", "VSync", offKeep(), 0, 0, 0))));

    private static final Set<String> DEFAULT_ON = Set.of("hud_fps", "hud_coords", "hud_armor", "hud_effects", "hud_keys", "christmas", "hud_watermark", "hud_arraylist", "hud_notify", "nametags", "jumpcircles", "dmgnum");
    private static final Map<String, Boolean> ON = new HashMap<>();
    private static final Map<String, Integer> VAL = new HashMap<>();
    /** Свободные позиции элементов худа (левый верхний угол в координатах GUI); если записи нет, элемент стоит на своём месте. */
    public static final Map<String, double[]> HUD_POS = new HashMap<>();
    private static int theme = 0;
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("quarry_client.json");

    public static boolean get(String id) {
        return ON.getOrDefault(id, DEFAULT_ON.contains(id));
    }

    public static void toggle(String id) {
        ON.put(id, !get(id));
        save();
    }

    /** Текущее значение настройки функции. */
    public static int setting(String feature, String key) {
        List<Setting> list = SETTINGS.get(feature);
        if (list == null) return 0;
        for (Setting s : list) {
            if (!s.key().equals(key)) continue;
            int v = VAL.getOrDefault(feature + "." + key, s.def());
            if (s.choices() != null) return Math.max(0, Math.min(s.choices().length - 1, v));
            return Math.max(s.min(), Math.min(s.max(), v));
        }
        return 0;
    }

    public static void setSettingQuiet(String feature, String key, int v) {
        VAL.put(feature + "." + key, v);
    }

    public static void setSetting(String feature, String key, int v) {
        setSettingQuiet(feature, key, v);
        save();
    }

    public static int accent() {
        return THEME_COLORS[theme];
    }

    public static int accent2() {
        return THEME_COLORS2[theme];
    }

    public static int theme() {
        return theme;
    }

    public static void setTheme(int t) {
        theme = Math.max(0, Math.min(THEME_COLORS.length - 1, t));
        save();
    }

    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            JsonObject o = new Gson().fromJson(Files.readString(FILE), JsonObject.class);
            for (String[] f : FEATURES) {
                if (o.has(f[1])) ON.put(f[1], o.get(f[1]).getAsBoolean());
            }
            for (Map.Entry<String, List<Setting>> e : SETTINGS.entrySet()) {
                for (Setting s : e.getValue()) {
                    String k = "s_" + e.getKey() + "." + s.key();
                    if (o.has(k)) VAL.put(e.getKey() + "." + s.key(), o.get(k).getAsInt());
                }
            }
            HUD_POS.clear();
            for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                String k = en.getKey();
                if (!k.startsWith("pos.") || !k.endsWith(".x")) continue;
                String id = k.substring(4, k.length() - 2);
                String ky = "pos." + id + ".y";
                if (o.has(ky)) HUD_POS.put(id, new double[]{en.getValue().getAsDouble(), o.get(ky).getAsDouble()});
            }
            if (o.has("theme")) theme = Math.max(0, Math.min(THEME_COLORS.length - 1, o.get("theme").getAsInt()));
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            for (String[] f : FEATURES) o.addProperty(f[1], get(f[1]));
            for (Map.Entry<String, List<Setting>> e : SETTINGS.entrySet()) {
                for (Setting s : e.getValue()) o.addProperty("s_" + e.getKey() + "." + s.key(), setting(e.getKey(), s.key()));
            }
            o.addProperty("theme", theme);
            for (Map.Entry<String, double[]> e : HUD_POS.entrySet()) {
                o.addProperty("pos." + e.getKey() + ".x", e.getValue()[0]);
                o.addProperty("pos." + e.getKey() + ".y", e.getValue()[1]);
            }
            Files.writeString(FILE, new Gson().toJson(o));
        } catch (Exception ignored) {
        }
    }
}

package quarry.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/** Убирает «тряску» и лишние эффекты на экране. Всё возвращается назад при выключении. */
public class NoRender {
    private static boolean applied = false;
    private static double oFov, oScreen;
    private static boolean oBob;

    public static boolean on() {
        return Config.get("norender");
    }

    public static boolean hideFire() {
        return on() && Config.setting("norender", "fire") == 0;
    }

    public static boolean hideHurt() {
        return on() && Config.setting("norender", "hurt") == 0;
    }

    public static void tick(Minecraft mc) {
        Options o = mc.options;
        if (o == null) return;
        if (on()) {
            if (!applied) {
                oFov = o.fovEffectScale().get();
                oScreen = o.screenEffectScale().get();
                oBob = o.bobView().get();
                applied = true;
            }
            o.fovEffectScale().set(Config.setting("norender", "fov") == 0 ? 0.0 : oFov);
            o.screenEffectScale().set(Config.setting("norender", "nausea") == 0 ? 0.0 : oScreen);
            o.bobView().set(Config.setting("norender", "bob") == 0 ? false : oBob);
            if (hideHurt() && mc.player != null) mc.player.hurtTime = 0;
        } else if (applied) {
            restore(mc);
        }
    }

    public static void restore(Minecraft mc) {
        if (!applied || mc.options == null) return;
        mc.options.fovEffectScale().set(oFov);
        mc.options.screenEffectScale().set(oScreen);
        mc.options.bobView().set(oBob);
        applied = false;
    }
}

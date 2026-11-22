package quarry.client;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.level.ParticleStatus;

/** Ставит быстрые настройки графики. При выключении возвращает прежние значения. */
public class Optimizer {
    private static boolean applied = false;
    private static int sig = 0;

    private static int oRd, oSim, oBlend, oFps;
    private static boolean oShadows, oVsync;
    private static ParticleStatus oParticles;
    private static CloudStatus oClouds;
    private static GraphicsStatus oGfx;

    private static int signature() {
        int h = 7;
        for (String k : new String[]{"rd", "sim", "particles", "clouds", "shadows", "blend", "gfx", "fps", "vsync"}) {
            h = h * 31 + Config.setting("optimize", k);
        }
        return h;
    }

    public static void tick(Minecraft mc) {
        if (mc.options == null) return;
        if (Config.get("optimize")) {
            int s = signature();
            if (!applied) {
                save(mc.options);
                applied = true;
                apply(mc.options);
                sig = s;
            } else if (s != sig) {
                apply(mc.options);
                sig = s;
            }
        } else if (applied) {
            restore(mc);
        }
    }

    public static void restore(Minecraft mc) {
        if (!applied || mc.options == null) return;
        Options o = mc.options;
        o.renderDistance().set(oRd);
        o.simulationDistance().set(oSim);
        o.biomeBlendRadius().set(oBlend);
        o.framerateLimit().set(oFps);
        o.entityShadows().set(oShadows);
        o.enableVsync().set(oVsync);
        o.particles().set(oParticles);
        o.cloudStatus().set(oClouds);
        o.graphicsMode().set(oGfx);
        applied = false;
    }

    private static void save(Options o) {
        oRd = o.renderDistance().get();
        oSim = o.simulationDistance().get();
        oBlend = o.biomeBlendRadius().get();
        oFps = o.framerateLimit().get();
        oShadows = o.entityShadows().get();
        oVsync = o.enableVsync().get();
        oParticles = o.particles().get();
        oClouds = o.cloudStatus().get();
        oGfx = o.graphicsMode().get();
    }

    private static void apply(Options o) {
        o.renderDistance().set(Config.setting("optimize", "rd"));
        o.simulationDistance().set(Config.setting("optimize", "sim"));
        int p = Config.setting("optimize", "particles");
        if (p == 1) o.particles().set(ParticleStatus.DECREASED);
        if (p == 2) o.particles().set(ParticleStatus.MINIMAL);
        if (Config.setting("optimize", "clouds") == 0) o.cloudStatus().set(CloudStatus.OFF);
        if (Config.setting("optimize", "shadows") == 0) o.entityShadows().set(false);
        if (Config.setting("optimize", "blend") == 0) o.biomeBlendRadius().set(0);
        if (Config.setting("optimize", "gfx") == 0) o.graphicsMode().set(GraphicsStatus.FAST);
        if (Config.setting("optimize", "fps") == 0) o.framerateLimit().set(260);
        if (Config.setting("optimize", "vsync") == 0) o.enableVsync().set(false);
    }
}

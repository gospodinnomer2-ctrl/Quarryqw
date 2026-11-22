package quarry.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

public class QuarryClient implements ClientModInitializer {
    private static final ArrayDeque<Long> CLICKS = new ArrayDeque<>();

    private static KeyMapping menuKey;
    private static KeyMapping zoomKey;
    private static boolean zoomed = false;
    private static boolean prevAttack = false;
    private static int savedFov = 70;
    private static boolean fbOn = false;

    public static int cps() {
        return CLICKS.size();
    }

    @Override
    public void onInitializeClient() {
        Config.load();
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.quarry.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.quarry"));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.quarry.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "category.quarry"));
        ClientTickEvents.END_CLIENT_TICK.register(QuarryClient::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(Optimizer::restore);
        HudRenderCallback.EVENT.register(Hud::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(NoRender::restore);
        Hitbox.init();
        Fx.init();
        Nametags.init();
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof PlayerRenderer pr) helper.register(new HatLayer(pr));
        });
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof AbstractContainerScreen<?>) {
                ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
                    if (Config.get("christmas") && Config.setting("christmas", "inv_snow") == 1) {
                        Snow.render(ctx, s.width, s.height, Config.setting("christmas", "density"));
                    }
                });
            }
        });
    }

    private static ParticleOptions trailType(int kind) {
        switch (kind) {
            case 0:
                return new DustParticleOptions(Config.accent() & 0x00FFFFFF, 1.1f);
            case 1:
                return ParticleTypes.END_ROD;
            case 2:
                return ParticleTypes.FLAME;
            case 3:
                return ParticleTypes.SOUL_FIRE_FLAME;
            case 4:
                return ParticleTypes.HEART;
            case 5:
                return ParticleTypes.NOTE;
            default:
                return ParticleTypes.ENCHANT;
        }
    }

    private static void tick(Minecraft mc) {
        while (menuKey.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new MenuScreen());
        }
        Optimizer.tick(mc);
        Combat.tick(mc);
        Fx.tick(mc);
        Nametags.tick(mc);
        Presence.tick(mc);
        NoRender.tick(mc);

        long now = System.currentTimeMillis();
        boolean attack = mc.options.keyAttack.isDown();
        if (attack && !prevAttack && mc.screen == null) CLICKS.addLast(now);
        prevAttack = attack;
        while (!CLICKS.isEmpty() && now - CLICKS.peekFirst() > 1000) CLICKS.pollFirst();

        // зум: поле зрения в игре не может быть меньше 30
        boolean wantZoom = Config.get("zoom") && mc.screen == null && zoomKey.isDown();
        if (wantZoom && !zoomed) {
            savedFov = mc.options.fov().get();
            zoomed = true;
        } else if (!wantZoom && zoomed) {
            mc.options.fov().set(savedFov);
            zoomed = false;
        }
        if (zoomed) mc.options.fov().set(Config.setting("zoom", "fov"));

        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;

        // FullBright: локальный эффект ночного зрения (на сервер не отправляется)
        if (Config.get("fullbright")) {
            MobEffectInstance cur = p.getEffect(MobEffects.NIGHT_VISION);
            if (cur == null || cur.getDuration() < 600) {
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 2400, 0, false, false));
            }
            fbOn = true;
        } else if (fbOn) {
            p.removeEffectNoUpdate(MobEffects.NIGHT_VISION);
            fbOn = false;
        }

        // авто-спринт: те же условия, что и у обычного бега
        if (Config.get("sprint") && mc.screen == null && mc.options.keyUp.isDown()
                && !p.isShiftKeyDown() && !p.isUsingItem() && !p.horizontalCollision
                && p.getFoodData().getFoodLevel() > 6
                && (Config.setting("sprint", "water") == 1 || !p.isInWater())) {
            p.setSprinting(true);
        }

        // след из частиц; частицы создаются «принудительно», поэтому видны и при минимуме частиц в настройках игры
        boolean moving = p.getDeltaMovement().horizontalDistanceSqr() > 0.0004;
        // трейл виден только от третьего лица (F5), от первого лица частицы закрывали бы обзор
        if (Config.get("trail") && !mc.isPaused() && !mc.options.getCameraType().isFirstPerson()
                && (moving || Config.setting("trail", "still") == 1)) {
            int kind = Config.setting("trail", "type");
            if (kind >= 7) kind = -1;   // ленты рисуются в мире (Fx), частицы для них не нужны
            ParticleOptions type = trailType(kind);
            int n = kind < 0 ? 0 : Config.setting("trail", "density");
            for (int i = 0; i < n; i++) {
                double rx = (mc.level.getRandom().nextDouble() - 0.5) * 0.6;
                double ry = 0.05 + mc.level.getRandom().nextDouble() * 1.6;
                double rz = (mc.level.getRandom().nextDouble() - 0.5) * 0.6;
                mc.levelRenderer.addParticle(type, true, p.getX() + rx, p.getY() + ry, p.getZ() + rz,
                        kind == 5 ? mc.level.getRandom().nextDouble() : 0.0, kind == 0 ? 0.0 : 0.01, 0.0);
            }
        }

        // своё время суток и погода: меняется только то, что видите вы
        if (Config.get("customtime")) {
            long ticks = ((Config.setting("customtime", "hour") - 6 + 24) % 24) * 1000L;
            mc.level.setTimeFromServer(mc.level.getGameTime(), ticks, false);
        }
        if (Config.get("customweather")) {
            int w = Config.setting("customweather", "type");
            mc.level.setRainLevel(w == 0 ? 0f : 1f);
            mc.level.setThunderLevel(w == 2 ? 1f : 0f);
        }

        // снег в мире: хлопья падают вокруг игрока, но только там, где над головой открытое небо
        if (Config.get("christmas") && Config.setting("christmas", "world_snow") == 1 && !mc.isPaused()) {
            int flakes = Math.max(1, Config.setting("christmas", "density") / 9);
            for (int i = 0; i < flakes; i++) {
                double sx = p.getX() + (mc.level.getRandom().nextDouble() - 0.5) * 36.0;
                double sz = p.getZ() + (mc.level.getRandom().nextDouble() - 0.5) * 36.0;
                double sy = p.getY() - 2.0 + mc.level.getRandom().nextDouble() * 10.0;
                if (!mc.level.canSeeSky(BlockPos.containing(sx, sy, sz))) continue;
                mc.levelRenderer.addParticle(ParticleTypes.SNOWFLAKE, true, sx, sy, sz, 0.0, 0.0, 0.0);
            }
        }
    }
}

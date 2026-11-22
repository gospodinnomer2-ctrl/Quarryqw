package quarry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.joml.Matrix4f;

/** Новогодняя шляпа Санты на голове игроков (рисуется цветными кубиками, без текстур). */
public class HatLayer extends RenderLayer<PlayerRenderState, PlayerModel> {
    private static final int RED = 0xC8222B, WHITE = 0xF5F5F5;

    public HatLayer(RenderLayerParent<PlayerRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, PlayerRenderState state, float yRot, float xRot) {
        if (state.isInvisible) return;
        Minecraft mc = Minecraft.getInstance();
        boolean self = mc.player != null && mc.player.distanceToSqr(state.x, state.y, state.z) < 0.3;
        boolean santa = Config.get("christmas") && Config.setting("christmas", self ? "hat_self" : "hat_others") == 1;
        boolean china = !santa && Config.get("chinahat") && Config.setting("chinahat", self ? "self" : "others") == 1;
        if (!santa && !china) return;

        ps.pushPose();
        this.getParentModel().head.translateAndRotate(ps);
        VertexConsumer vc = buffers.getBuffer(RenderType.leash());
        Matrix4f m = ps.last().pose();
        if (santa) {
            box(m, vc, -4.7f, -9.3f, -4.7f, 4.7f, -7.9f, 4.7f, WHITE, light);      // белая оторочка
            box(m, vc, -3.9f, -12.3f, -3.9f, 3.9f, -9.3f, 3.9f, RED, light);        // основа
            box(m, vc, -2.9f, -14.6f, -3.0f, 2.9f, -12.3f, 3.3f, RED, light);       // загиб назад
            box(m, vc, -2.0f, -16.2f, -1.0f, 2.0f, -14.6f, 4.6f, RED, light);
            box(m, vc, -1.5f, -16.0f, 4.2f, 1.5f, -13.2f, 7.2f, WHITE, light);      // помпон
        } else {
            cone(m, vc);
        }
        ps.popPose();
    }

    /** Китайская шляпа: широкий конус, цвета плавно переливаются по кругу. */
    private static void cone(Matrix4f m, VertexConsumer vc) {
        int mode = Config.setting("chinahat", "color");
        float sc = Config.setting("chinahat", "size") / 10f;
        double time = (System.currentTimeMillis() % 1_000_000L) / 1000.0;
        int n = 28;
        float rad = 9.2f / 16f * sc, yb = -8.5f / 16f, ya = yb - 5.8f / 16f * sc;
        int full = 15728880;
        int top = Ui.lerp(Config.accent(), 0xFFFFFFFF, 0.55f);
        for (int k = 0; k < n; k++) {
            double u0 = k / (double) n, u1 = (k + 1) / (double) n;
            int c0 = hat(mode, u0, time), c1 = hat(mode, u1, time);
            float x0 = (float) Math.cos(u0 * 2 * Math.PI) * rad, z0 = (float) Math.sin(u0 * 2 * Math.PI) * rad;
            float x1 = (float) Math.cos(u1 * 2 * Math.PI) * rad, z1 = (float) Math.sin(u1 * 2 * Math.PI) * rad;
            vert(m, vc, 0f, ya, 0f, top, full);
            vert(m, vc, x0, yb, z0, c0, full);
            vert(m, vc, x1, yb, z1, c1, full);
            vert(m, vc, x1, yb, z1, c1, full);
        }
    }

    private static int hat(int mode, double u, double time) {
        if (mode == 1) return Fx.hsv((float) (u + time * 0.15), 0.55f, 1f);
        return Ui.lerp(Config.accent(), Config.accent2(), (float) (0.5 + 0.5 * Math.sin(u * 12.566 + time * 2.0)));
    }

    private static void vert(Matrix4f m, VertexConsumer vc, float x, float y, float z, int rgb, int light) {
        vc.addVertex(m, x, y, z).setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255).setLight(light);
    }

    private static void box(Matrix4f m, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                            int rgb, int light) {
        x0 /= 16f; y0 /= 16f; z0 /= 16f; x1 /= 16f; y1 /= 16f; z1 /= 16f;
        face(m, vc, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, rgb, 1.0f, light);   // верх (в пространстве модели y вверх отрицательный)
        face(m, vc, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, rgb, 0.55f, light);
        face(m, vc, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, rgb, 0.85f, light);
        face(m, vc, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, rgb, 0.80f, light);
        face(m, vc, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, rgb, 0.70f, light);
        face(m, vc, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, rgb, 0.70f, light);
    }

    private static void face(Matrix4f m, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int rgb, float shade, int light) {
        int r = (int) (((rgb >> 16) & 255) * shade), g = (int) (((rgb >> 8) & 255) * shade), b = (int) ((rgb & 255) * shade);
        vc.addVertex(m, ax, ay, az).setColor(r, g, b, 255).setLight(light);
        vc.addVertex(m, bx, by, bz).setColor(r, g, b, 255).setLight(light);
        vc.addVertex(m, cx, cy, cz).setColor(r, g, b, 255).setLight(light);
        vc.addVertex(m, dx, dy, dz).setColor(r, g, b, 255).setLight(light);
    }
}

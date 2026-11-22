package quarry.client;

import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/** Падающий снег для экранов (меню мода и инвентарь). */
public class Snow {
    private static final int MAX = 220;
    private static final float[] X = new float[MAX], Y = new float[MAX], S = new float[MAX], Z = new float[MAX], D = new float[MAX];
    private static boolean init = false;
    private static long last = System.nanoTime();

    public static void render(GuiGraphics ctx, int w, int h, int count) {
        if (!init) {
            Random r = new Random(7);
            for (int i = 0; i < MAX; i++) {
                X[i] = r.nextFloat();
                Y[i] = r.nextFloat();
                S[i] = r.nextFloat();
                Z[i] = r.nextFloat();
                D[i] = r.nextFloat() * 6.28f;
            }
            init = true;
        }
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
        last = now;
        double t = System.currentTimeMillis() / 1000.0;
        int n = Math.max(0, Math.min(MAX, count));
        for (int i = 0; i < n; i++) {
            Y[i] += dt * (0.05f + S[i] * 0.09f);
            X[i] += (float) Math.sin(t * 0.9 + D[i]) * dt * 0.018f;
            if (Y[i] > 1.02f) {
                Y[i] = -0.02f;
                X[i] = (float) Math.random();
            }
            if (X[i] < 0f) X[i] += 1f;
            if (X[i] > 1f) X[i] -= 1f;
            int size = 1 + (int) (Z[i] * 2.6f);
            int a = 110 + (int) (Z[i] * 120);
            int px = (int) (X[i] * w), py = (int) (Y[i] * h);
            Ui.fill(ctx, px, py, px + size, py + size, (a << 24) | 0xFFFFFF);
        }
    }
}

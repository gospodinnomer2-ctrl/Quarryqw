package quarry.client;

/** Плавное приближение числа к цели: скорость не зависит от FPS, получается мягкое «замедление» к концу. */
public final class Smooth {
    public float v;
    private long t = 0;

    public Smooth() {
    }

    public Smooth(float start) {
        this.v = start;
    }

    public float to(float target, float speed) {
        long n = System.nanoTime();
        float dt = t == 0 ? 1f / 60f : Math.min(0.1f, (n - t) / 1_000_000_000f);
        t = n;
        v += (target - v) * (1f - (float) Math.exp(-speed * dt));
        if (Math.abs(target - v) < 0.003f) v = target;
        return v;
    }

    /** Мгновенно поставить значение (без анимации). */
    public void snap(float x) {
        v = x;
        t = 0;
    }
}

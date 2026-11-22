package quarry.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кто играет с Quarry. Клиент раз в несколько минут отправляет свой ник в публичный канал ntfy.sh
 * и раз в минуту читает список ников за последние минуты. Больше ничего не передаётся.
 * Любая ошибка сети молча игнорируется.
 */
public class Presence {
    private static final String TOPIC = "quarry-visuals-presence-7f3a9c";
    private static final Set<String> USERS = ConcurrentHashMap.newKeySet();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static long lastPost = 0, lastPoll = 0;
    private static String lastName = "";
    private static volatile boolean busy = false;
    private static volatile String me = "";

    public static boolean isUser(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.equals(me) || USERS.contains(n);
    }

    public static void tick(Minecraft mc) {
        if (!Config.get("quarryusers") || mc.player == null || mc.level == null) return;
        String nick = mc.player.getName().getString();
        me = nick.toLowerCase(Locale.ROOT);
        if (busy) return;
        long now = System.currentTimeMillis();
        boolean post = now - lastPost > 5 * 60_000L || !nick.equals(lastName);
        boolean poll = now - lastPoll > 60_000L;
        if (!post && !poll) return;
        busy = true;
        if (post) {
            lastPost = now;
            lastName = nick;
        }
        if (poll) lastPoll = now;
        final boolean doPost = post, doPoll = poll;
        Thread t = new Thread(() -> {
            try {
                if (doPost) post(nick);
                if (doPoll) poll();
            } catch (Exception ignored) {
                // нет сети или сервис недоступен: просто пробуем позже
            } finally {
                busy = false;
            }
        }, "quarry-presence");
        t.setDaemon(true);
        t.start();
    }

    private static void post(String nick) throws Exception {
        HttpRequest r = HttpRequest.newBuilder(URI.create("https://ntfy.sh/" + TOPIC))
                .timeout(Duration.ofSeconds(6)).POST(HttpRequest.BodyPublishers.ofString(nick)).build();
        HTTP.send(r, HttpResponse.BodyHandlers.discarding());
    }

    private static void poll() throws Exception {
        HttpRequest r = HttpRequest.newBuilder(URI.create("https://ntfy.sh/" + TOPIC + "/json?poll=1&since=7m"))
                .timeout(Duration.ofSeconds(6)).GET().build();
        HttpResponse<String> resp = HTTP.send(r, HttpResponse.BodyHandlers.ofString());
        Set<String> found = new HashSet<>();
        for (String line : resp.body().split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                JsonObject o = JsonParser.parseString(line).getAsJsonObject();
                if (!o.has("message")) continue;
                String n = o.get("message").getAsString().trim();
                if (n.matches("[A-Za-z0-9_]{3,16}")) found.add(n.toLowerCase(Locale.ROOT));
            } catch (Exception ignored) {
                // строка не похожа на сообщение, пропускаем
            }
        }
        USERS.clear();
        USERS.addAll(found);
    }
}

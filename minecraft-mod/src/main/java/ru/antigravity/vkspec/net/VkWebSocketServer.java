package ru.antigravity.vkspec.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import ru.antigravity.vkspec.FunSpecMod;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class VkWebSocketServer extends WebSocketServer {

    private static final int MAX_CACHE_SIZE = 500;
    private static final int MAX_MESSAGE_SIZE = 4096;

    // LRU-кеш для дедупликации заявок (автоочистка старых записей при переполнении)
    private final Map<String, Long> processedTickets = Collections.synchronizedMap(
        new LinkedHashMap<String, Long>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        }
    );

    // LRU-кеш для дедупликации клеймов
    private final Map<String, Long> processedClaims = Collections.synchronizedMap(
        new LinkedHashMap<String, Long>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        }
    );

    // Счётчик подключённых браузеров (потокобезопасный)
    private final AtomicInteger connectedBrowsers = new AtomicInteger(0);

    // Callback для автоперезапуска при фатальной ошибке сервера
    private Runnable onFatalError;

    public VkWebSocketServer(int port) {
        // БЕЗОПАСНОСТЬ: слушаем ТОЛЬКО на 127.0.0.1 (localhost)
        // Никто из локальной сети не сможет подключиться
        super(new InetSocketAddress("127.0.0.1", port));
        // Таймаут для обнаружения мёртвых соединений (секунды)
        setConnectionLostTimeout(60);
    }

    public void setOnFatalError(Runnable callback) {
        this.onFatalError = callback;
    }

    public int getConnectedBrowsers() {
        return connectedBrowsers.get();
    }

    // ==================== Жизненный цикл соединений ====================

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        int count = connectedBrowsers.incrementAndGet();
        System.out.println("[FunSpec] Browser connected (" + count + " total): " + conn.getRemoteSocketAddress());

        // Отправляем handshake — браузер ждёт этот пакет для запуска сканера
        JsonObject configJson = new JsonObject();
        configJson.addProperty("type", "config");
        conn.send(configJson.toString());

        // Уведомляем игрока в чате
        notifyPlayer("§a[FunSpec] ✓ Браузер подключен!" + (count > 1 ? " (" + count + " вкладок)" : ""));
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        int count = connectedBrowsers.decrementAndGet();
        if (count < 0) connectedBrowsers.set(0); // Защита от отрицательных значений
        System.out.println("[FunSpec] Browser disconnected (" + count + " remaining). Code: " + code);

        if (count == 0) {
            notifyPlayer("§e[FunSpec] Браузер отключен. Откройте VK для работы мода.");
        }
    }

    // ==================== Обработка входящих сообщений ====================

    @Override
    public void onMessage(WebSocket conn, String message) {
        FunSpecMod mod = FunSpecMod.getInstance();
        if (mod == null || !mod.getConfig().modEnabled) {
            return;
        }

        // Защита от слишком длинных сообщений (спам/атака)
        if (message == null || message.length() > MAX_MESSAGE_SIZE) {
            System.err.println("[FunSpec] Rejected message: " + (message == null ? "null" : message.length() + " bytes"));
            return;
        }

        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();

            if (!json.has("type") || json.get("type").isJsonNull()) {
                return;
            }

            String type = json.get("type").getAsString();

            switch (type) {
                case "new_spec":
                    handleNewSpec(json);
                    break;
                case "claimed":
                    handleClaimed(json);
                    break;
                case "click_result":
                    handleClickResult(json);
                    break;
                case "logs_result":
                    handleLogsResult(json);
                    break;
                case "pong":
                    // Heartbeat ответ от браузера — всё ок, ничего не делаем
                    break;
                default:
                    // Неизвестный тип — игнорируем
                    break;
            }
        } catch (Exception e) {
            System.err.println("[FunSpec] Error parsing message: " + e.getMessage());
        }
    }

    /** Новая заявка на спек */
    private void handleNewSpec(JsonObject json) {
        if (!hasFields(json, "nickname", "server", "reason", "peer_id", "conversation_message_id")) {
            return;
        }

        String nickname = json.get("nickname").getAsString();
        String server = json.get("server").getAsString();
        String reason = json.get("reason").getAsString();
        long peerId = json.get("peer_id").getAsLong();
        long convMsgId = json.get("conversation_message_id").getAsLong();

        // Валидация: защита от мусорных данных
        if (nickname.isEmpty() || nickname.length() > 32 || server.isEmpty() || server.length() > 64) {
            return;
        }

        String ticketKey = peerId + "_" + convMsgId;
        if (!processedTickets.containsKey(ticketKey)) {
            processedTickets.put(ticketKey, System.currentTimeMillis());
            FunSpecMod.getInstance().addNotification(nickname, server, reason, peerId, convMsgId);
        }
    }

    /** Заявка взята другим сотрудником */
    private void handleClaimed(JsonObject json) {
        if (!hasFields(json, "nickname", "claimer", "conversation_message_id")) {
            return;
        }

        String nickname = json.get("nickname").getAsString();
        long convMsgId = json.get("conversation_message_id").getAsLong();
        String claimerRaw = json.get("claimer").getAsString();

        // Очищаем VK-упоминание: [id123|Name] → Name
        String claimer = claimerRaw.replaceAll("\\[id\\d+\\|(.*?)\\]", "$1");

        if (claimer.isEmpty() || claimer.length() > 32) return;

        String claimKey = nickname + "_" + convMsgId + "_" + claimer;
        if (!processedClaims.containsKey(claimKey)) {
            processedClaims.put(claimKey, System.currentTimeMillis());
            boolean isMe = false;
            if (json.has("isMe")) {
                isMe = json.get("isMe").getAsBoolean();
            }
            FunSpecMod.getInstance().markAsClaimed(nickname, claimer, isMe);
        }
    }

    /** Результат нажатия кнопки «Взять» в браузере */
    private void handleClickResult(JsonObject json) {
        if (!json.has("success")) return;

        boolean success = json.get("success").getAsBoolean();
        String error = json.has("error") && !json.get("error").isJsonNull()
                ? json.get("error").getAsString() : null;
        String nickname = json.has("nickname") && !json.get("nickname").isJsonNull()
                ? json.get("nickname").getAsString() : null;

        // Делегируем в основной мод — он управляет 3-секундным подтверждением
        FunSpecMod mod = FunSpecMod.getInstance();
        if (mod != null) {
            mod.handleClickResult(success, error, nickname);
        }
    }

    // ==================== Утилиты ====================

    /** Проверяет наличие обязательных полей в JSON */
    private boolean hasFields(JsonObject json, String... fields) {
        for (String field : fields) {
            if (!json.has(field) || json.get(field).isJsonNull()) {
                return false;
            }
        }
        return true;
    }

    /** Отправить сообщение игроку в чат (потокобезопасно) */
    private void notifyPlayer(String message) {
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(Text.literal(message), false);
            }
        });
    }

    // ==================== Ошибки ====================

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[FunSpec] WebSocket error: " + ex.getMessage());

        // conn == null → фатальная ошибка самого сервера (не отдельного соединения)
        if (conn == null && onFatalError != null) {
            System.err.println("[FunSpec] FATAL server error! Triggering auto-restart...");
            onFatalError.run();
        }
    }

    @Override
    public void onStart() {
        System.out.println("[FunSpec] ✓ WebSocket Server listening on 127.0.0.1:" + getPort());
    }

    // ==================== Исходящие команды ====================

    /** Отправить команду браузеру: нажать кнопку «Взять» */
    public void requestBrowserToClickClaim(String nickname) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "click_button");
        json.addProperty("nickname", nickname);

        String msg = json.toString();
        boolean sent = false;

        for (WebSocket conn : getConnections()) {
            if (conn.isOpen()) {
                try {
                    conn.send(msg);
                    sent = true;
                } catch (Exception e) {
                    System.err.println("[FunSpec] Failed to send to browser: " + e.getMessage());
                }
            }
        }

        if (!sent) {
            notifyPlayer("§c[FunSpec] Браузер не подключен! Откройте VK чат.");
        }
    }

    /** Отправить ping всем браузерам (проверка живости) */
    public void pingAll() {
        JsonObject ping = new JsonObject();
        ping.addProperty("type", "ping");
        String msg = ping.toString();

        for (WebSocket conn : getConnections()) {
            if (conn.isOpen()) {
                try {
                    conn.send(msg);
                } catch (Exception ignored) {
                    // Мёртвое соединение — будет закрыто по таймауту
                }
            }
        }
    }

    // ==================== Логи ====================

    /** Отправить команду браузеру: проверить логи через VK бота */
    public void requestLogsCheck(String nicknames) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "check_logs");
        json.addProperty("nicknames", nicknames);

        String msg = json.toString();
        boolean sent = false;

        for (WebSocket conn : getConnections()) {
            if (conn.isOpen()) {
                try {
                    conn.send(msg);
                    sent = true;
                } catch (Exception e) {
                    System.err.println("[FunSpec] Failed to send logs request to browser: " + e.getMessage());
                }
            }
        }

        if (!sent) {
            FunSpecMod mod = FunSpecMod.getInstance();
            if (mod != null) {
                mod.setLogsResult("⚠ Браузер не подключен! Откройте VK.");
            }
            notifyPlayer("§c[FunSpec] Браузер не подключен! Откройте VK чат.");
        }
    }

    /** Обработка результата проверки логов от браузера */
    private void handleLogsResult(JsonObject json) {
        String text = json.has("text") && !json.get("text").isJsonNull()
                ? json.get("text").getAsString() : "⚠ Пустой ответ от бота.";
        boolean success = !json.has("error") || json.get("error").isJsonNull();

        FunSpecMod mod = FunSpecMod.getInstance();
        if (mod != null) {
            if (success) {
                mod.setLogsResult(text);
                notifyPlayer("§a[FunSpec] ✓ Результат логов получен! Откройте вкладку «Логи» для просмотра.");
            } else {
                String error = json.get("error").getAsString();
                mod.setLogsResult("⚠ Ошибка: " + error);
                notifyPlayer("§c[FunSpec] ✗ Ошибка логов: " + error);
            }
        }
    }
}

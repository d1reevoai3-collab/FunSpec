package ru.antigravity.vkspec.net;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Enumeration;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HwidManager {

    private static String hwid = null;
    private static boolean authorized = false;
    private static boolean checked = false;
    private static String blockReason = null; // "not_registered", "banned", "network_error"

    // HMAC secret key (obfuscated — split into parts)
    private static final String HMAC_KEY;
    static {
        // Obfuscated key assembly — harder to find via simple string search
        char[] k = new char[32];
        k[0]='V'; k[1]='k'; k[2]='S'; k[3]='p';
        k[4]='3'; k[5]='c'; k[6]='_'; k[7]='H';
        k[8]='m'; k[9]='a'; k[10]='C'; k[11]='_';
        k[12]='S'; k[13]='3'; k[14]='c'; k[15]='R';
        k[16]='3'; k[17]='t'; k[18]='_'; k[19]='K';
        k[20]='3'; k[21]='y'; k[22]='_'; k[23]='2';
        k[24]='0'; k[25]='2'; k[26]='6'; k[27]='!';
        k[28]='x'; k[29]='9'; k[30]='Z'; k[31]='q';
        HMAC_KEY = new String(k);
    }

    // Salt for HWID generation (obfuscated)
    private static final String HWID_SALT;
    static {
        char[] s = new char[24];
        s[0]='V'; s[1]='K'; s[2]='_'; s[3]='S';
        s[4]='P'; s[5]='E'; s[6]='C'; s[7]='_';
        s[8]='H'; s[9]='W'; s[10]='I'; s[11]='D';
        s[12]='_'; s[13]='S'; s[14]='A'; s[15]='L';
        s[16]='T'; s[17]='_'; s[18]='v'; s[19]='2';
        s[20]='_'; s[21]='9'; s[22]='9'; s[23]='1';
        HWID_SALT = new String(s);
    }

    /**
     * Генерирует уникальный аппаратный идентификатор из железа ПК.
     * Использует: CPU ID, серийник материнки, BIOS UUID, серийник диска, MAC-адрес.
     * НЕ использует: имя пользователя, имя ПК (они меняются).
     */
    public static String getHwid() {
        if (hwid != null) return hwid;

        try {
            StringBuilder rawData = new StringBuilder();
            int componentsFound = 0;

            // 1. CPU Processor ID — уникальный ID процессора, не меняется
            String cpuId = runWmic("cpu get ProcessorId");
            if (cpuId != null && !cpuId.isEmpty()) {
                rawData.append("CPU:").append(cpuId).append("|");
                componentsFound++;
            }

            // 2. Motherboard Serial Number
            String mbSerial = runWmic("baseboard get SerialNumber");
            if (mbSerial != null && !mbSerial.isEmpty() && !mbSerial.equals("To be filled by O.E.M.")) {
                rawData.append("MB:").append(mbSerial).append("|");
                componentsFound++;
            }

            // 3. BIOS/System UUID — зашит в BIOS, уникален
            String biosUuid = runWmic("csproduct get UUID");
            if (biosUuid != null && !biosUuid.isEmpty() && !biosUuid.equals("FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF")) {
                rawData.append("BIOS:").append(biosUuid).append("|");
                componentsFound++;
            }

            // 4. Disk Drive Serial Number — серийник основного диска
            String diskSerial = runWmic("diskdrive get SerialNumber");
            if (diskSerial != null && !diskSerial.isEmpty()) {
                rawData.append("DISK:").append(diskSerial).append("|");
                componentsFound++;
            }

            // 5. MAC Address — физический MAC первого Ethernet/Wi-Fi адаптера
            String mac = getPhysicalMacAddress();
            if (mac != null && !mac.isEmpty()) {
                rawData.append("MAC:").append(mac).append("|");
                componentsFound++;
            }

            // 6. Количество ядер CPU (бонусный параметр, есть всегда)
            String cores = String.valueOf(Runtime.getRuntime().availableProcessors());
            rawData.append("CORES:").append(cores).append("|");

            // 7. OS arch (amd64 vs x86 — стабильный параметр)
            rawData.append("ARCH:").append(System.getProperty("os.arch")).append("|");

            // Проверяем что получили хотя бы 2 аппаратных компонента
            if (componentsFound < 2) {
                System.err.println("[VK-Spec HWID] WARNING: Only " + componentsFound + " hardware components found!");
                // Добавляем fallback — имя ОС + версия (лучше чем ничего)
                rawData.append("OS:").append(System.getProperty("os.name")).append("|");
                rawData.append("OSVER:").append(System.getProperty("os.version")).append("|");
            }

            // Добавляем соль
            rawData.append(HWID_SALT);

            // SHA-256 хэш
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawData.toString().getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            // Форматируем: A8F9-2B3C-D1E2-F345
            String fullHash = hexString.toString().toUpperCase();
            hwid = fullHash.substring(0, 4) + "-" + fullHash.substring(4, 8) + "-"
                 + fullHash.substring(8, 12) + "-" + fullHash.substring(12, 16);

            System.out.println("[VK-Spec HWID] Generated HWID from " + componentsFound + " hardware components");
            return hwid;
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR-HWID-GEN";
        }
    }

    /**
     * Выполняет WMIC-команду и возвращает первую непустую строку результата.
     * Возвращает null если команда не доступна или не на Windows.
     */
    private static String runWmic(String wmicArgs) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (!os.contains("win")) return null;

            Process process = Runtime.getRuntime().exec("wmic " + wmicArgs);
            process.waitFor();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    // Пропускаем заголовок и пустые строки
                    if (!line.isEmpty() && !line.toLowerCase().contains("processorid")
                            && !line.toLowerCase().contains("serialnumber")
                            && !line.toLowerCase().contains("uuid")) {
                        return line.trim();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[VK-Spec HWID] WMIC failed for: " + wmicArgs + " — " + e.getMessage());
        }
        return null;
    }

    /**
     * Получает физический MAC-адрес первого не-виртуального сетевого адаптера.
     */
    private static String getPhysicalMacAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();

                // Пропускаем loopback, виртуальные, отключенные
                if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) continue;

                // Пропускаем известные виртуальные адаптеры
                String name = ni.getDisplayName().toLowerCase();
                if (name.contains("virtual") || name.contains("vmware") || name.contains("vbox")
                        || name.contains("hyper-v") || name.contains("docker")) continue;

                byte[] mac = ni.getHardwareAddress();
                if (mac != null && mac.length == 6) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X", mac[i]));
                        if (i < mac.length - 1) sb.append(':');
                    }
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            System.err.println("[VK-Spec HWID] MAC address detection failed: " + e.getMessage());
        }
        return null;
    }

    public static boolean isAuthorized() {
        return authorized;
    }

    public static boolean isChecked() {
        return checked;
    }

    public static String getBlockReason() {
        return blockReason;
    }

    /**
     * Проверяет HWID на сервере с проверкой HMAC-подписи ответа.
     * Вызывается при запуске и каждые 5 минут.
     */
    public static void checkAuthorization(String apiUrl) {
        String currentHwid = getHwid();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .build();

            long requestTime = System.currentTimeMillis() / 1000;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/api/check?hwid=" + currentHwid))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            // Парсим JSON вручную (без библиотеки)
            boolean isAuthorized = body.contains("\"authorized\":true");
            String signature = extractJsonField(body, "signature");
            String timestampStr = extractJsonField(body, "timestamp");

            if (signature != null && timestampStr != null) {
                // Проверяем HMAC-подпись
                long serverTimestamp = Long.parseLong(timestampStr);
                long now = System.currentTimeMillis() / 1000;

                // Проверяем что ответ не старше 60 секунд
                if (Math.abs(now - serverTimestamp) > 60) {
                    System.err.println("[FunSpec] HWID response too old! Possible replay attack.");
                    authorized = false;
                    blockReason = "tamper_detected";
                    checked = true;
                    return;
                }

                // Проверяем HMAC
                String dataToSign = currentHwid + ":" + isAuthorized + ":" + serverTimestamp;
                String expectedSig = computeHmac(dataToSign);

                if (!signature.equals(expectedSig)) {
                    System.err.println("[FunSpec] HWID signature mismatch! Possible tampering.");
                    authorized = false;
                    blockReason = "tamper_detected";
                    checked = true;
                    return;
                }

                // Подпись верна
                authorized = isAuthorized;
                if (!isAuthorized) {
                    // Определяем причину
                    if (body.contains("\"status\":\"BANNED\"")) {
                        blockReason = "banned";
                    } else {
                        blockReason = "not_registered";
                    }
                } else {
                    blockReason = null;
                }
            } else {
                // Сервер без подписи (старая версия?) — принимаем как есть но логируем
                System.out.println("[FunSpec] Warning: server response without signature");
                authorized = isAuthorized;
                blockReason = isAuthorized ? null : "not_registered";
            }

        } catch (Exception e) {
            System.err.println("[FunSpec] HWID Check Failed: " + e.getMessage());
            authorized = false;
            blockReason = "network_error";
        } finally {
            checked = true;
        }
    }

    /**
     * Вычисляет HMAC-SHA256
     */
    private static String computeHmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(HMAC_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : rawHmac) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Простой парсер JSON-поля (без библиотеки).
     */
    private static String extractJsonField(String json, String field) {
        String key = "\"" + field + "\":";
        int idx = json.indexOf(key);
        if (idx == -1) return null;

        int valueStart = idx + key.length();
        // Пропускаем пробелы
        while (valueStart < json.length() && json.charAt(valueStart) == ' ') valueStart++;

        if (valueStart >= json.length()) return null;

        char first = json.charAt(valueStart);
        if (first == '"') {
            // Строковое значение
            int end = json.indexOf('"', valueStart + 1);
            if (end == -1) return null;
            return json.substring(valueStart + 1, end);
        } else {
            // Числовое/boolean значение
            int end = valueStart;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            return json.substring(valueStart, end).trim();
        }
    }

    /**
     * Отправляет на сервер информацию о времени, проведённом в игре.
     */
    public static void sendPlaytimeHeartbeat(String apiUrl, int seconds) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                String currentHwid = getHwid();
                String json = "{\"hwid\":\"" + currentHwid + "\",\"seconds\":" + seconds + "}";

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl + "/api/playtime"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                client.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {
                // Игнорируем ошибки
            }
        });
    }
}

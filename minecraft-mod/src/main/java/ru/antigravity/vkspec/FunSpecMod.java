package ru.antigravity.vkspec;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.antigravity.vkspec.config.ModConfig;

import ru.antigravity.vkspec.ui.HistoryEntry;
import ru.antigravity.vkspec.ui.NotificationRenderer;
import ru.antigravity.vkspec.ui.SpecNotification;

import ru.antigravity.vkspec.net.VkWebSocketServer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FunSpecMod implements ClientModInitializer {
    private static FunSpecMod instance;
    private ModConfig config;
    private VkWebSocketServer wsServer;
    
    // Потокобезопасный список для активных уведомлений на экране
    private final List<SpecNotification> activeNotifications = new CopyOnWriteArrayList<>();
    
    // История всех заявок (последние 50)
    private final List<HistoryEntry> ticketHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 50;
    
    // Статистика сессии
    private int totalTickets = 0;
    private int myClaimedTickets = 0;
    
    // ==================== Двухфазное подтверждение ====================
    // Фаза 1: ждём 3 сек после клика. Если таймаут — молча заходим на анку.
    // Фаза 2: ждём ещё 3 сек после захода на анку. Если таймаут — ничего не делаем.
    // На любой фазе: мой ник → спек ✓, чужой ник → отмена ✗.
    private String awaitingConfirmationNickname = null;    // Ник нарушителя, чью заявку мы пытаемся взять
    private SpecNotification awaitingConfirmationNotif = null; // Уведомление, на которое нажали
    private int confirmationTimeoutTicks = 0;               // Обратный отсчёт (60 тиков = 3 сек)
    private int confirmationPhase = 0;                      // 0=нет, 1=первая фаза, 2=вторая фаза (после захода на анку)
    private boolean clickSucceeded = false;                 // Получили ли click_result success от браузера
    
    // Очередь для отложенного выполнения команд в игре
    private String pendingSpecCommand = null;
    private String pendingSpecNickname = null;
    private String pendingSpecDisplayNick = null;
    private String pendingSpecReason = null;
    private int commandDelayTicks = 0;
    private boolean waitingForWorldLoad = false;
    
    // Текущий спек (кого сейчас спекаем)
    private String currentlySpectating = null;
    private String spectatingReason = null;

    // Логи (проверка через VK бота)
    private volatile String logsResult = null;
    private volatile boolean logsSearching = false;

    // Переход между серверами / анархиями
    private net.minecraft.client.world.ClientWorld lastWorld = null;
    private int autoKeyDelayTicks = -1;
    private boolean expectingServerTransition = false;
    private int transitionTimeoutTicks = 0;
    
    // Задержка перед сервером
    private String pendingServerCommand = null;
    private int serverCommandDelayTicks = 0;

    // Автоперезапуск сервера
    private int serverRestartTicks = -1;
    private boolean notifiedConnected = false;
    
    // Счетчик отыгранного времени
    private int playtimeTicks = 0;

    // ==================== HWID Protection ====================
    private boolean hwidBlocked = false;        // true = мод полностью заблокирован
    private int hwidRecheckTicks = 0;           // Обратный отсчёт до следующей ре-проверки
    private static final int HWID_RECHECK_INTERVAL = 6000; // 5 минут = 6000 тиков

    // Горячие клавиши
    private static KeyBinding claimKeyBinding;
    private static KeyBinding settingsKeyBinding;
    private static KeyBinding copyNickKeyBinding;

    // Smart Spec Fallback
    private java.util.List<String> fallbackSpecQueue = new java.util.ArrayList<>();
    private int fallbackSpecTicks = 0;

    public static FunSpecMod getInstance() {
        return instance;
    }

    public static KeyBinding getCopyNickKeyBinding() {
        return copyNickKeyBinding;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        config = ModConfig.load();

        // 1. Проверяем HWID синхронно (пока загружается игра)
        ru.antigravity.vkspec.net.HwidManager.checkAuthorization("https://funspec-production.up.railway.app");
        
        // Устанавливаем флаг блокировки
        if (!ru.antigravity.vkspec.net.HwidManager.isAuthorized()) {
            hwidBlocked = true;
        }
        
        // Инициализируем таймер ре-проверки
        hwidRecheckTicks = HWID_RECHECK_INTERVAL;

        // 2. Блокировка главного меню, если нет доступа (первая линия защиты)
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (hwidBlocked && !(screen instanceof ru.antigravity.vkspec.ui.HwidAuthScreen)) {
                // Перенаправляем на экран ошибки HWID — для ЛЮБОГО экрана, не только TitleScreen
                client.execute(() -> client.setScreen(new ru.antigravity.vkspec.ui.HwidAuthScreen(ru.antigravity.vkspec.net.HwidManager.getHwid())));
            }
        });

        System.out.println("[FunSpec] Initializing FunSpec Mod v3.0...");

        // 1. Загрузка конфигурации
        this.config = ModConfig.load();

        // 2. Инициализация и запуск внутреннего WebSocket-сервера
        startServer();

        // Завершение работы сервера при закрытии игры
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> stopServer());

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!ru.night.Night.isRendererInitialized()) {
                ru.night.Night.ensureRendererInitialized();
            }
        });

        // 3. Горячая клавиша: принять заявку (Y)
        claimKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.vkspec.claim",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Y,
                "category.vkspec.general"
        ));

        // 4. Горячая клавиша: открыть настройки (Right Shift)
        settingsKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.vkspec.settings",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.vkspec.general"
        ));

        // 4b. Горячая клавиша: копировать ник (Middle Mouse Button)
        copyNickKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.vkspec.copy_nick",
                InputUtil.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                "category.vkspec.general"
        ));

        // 5. Регистрация HUD-рендеринга
        HudRenderCallback.EVENT.register(NotificationRenderer::render);

        // 6. Тик клиента
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        // 7. Перехват исходящих команд (для сброса "Спекаю:")
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND.register((command) -> {
            String cmd = command.toLowerCase().trim();
            
            // /an305, /server ..., /srv ... — игрок меняет сервер, сбрасываем спек
            if (cmd.startsWith("an") || cmd.startsWith("server ") || cmd.startsWith("srv ")) {
                currentlySpectating = null;
                expectingServerTransition = true;
            }
            
            // /spec NickName — игрок начинает спек вручную
            if (cmd.startsWith("spec ") && cmd.length() > 5) {
                String nick = command.substring(5).trim();
                if (!nick.isEmpty()) {
                    currentlySpectating = nick;
                }
            }
            
            // /unspec, /deop... — конец спека
            if (cmd.equals("spec") || cmd.startsWith("unspec")) {
                currentlySpectating = null;
            }
            
            // /key — ввод ключа (ручной), отложить спек на 3 секунды
            if (cmd.startsWith("key ")) {
                if (pendingSpecCommand != null) {
                    commandDelayTicks = 60; // 3 секунды после ввода ключа
                    waitingForWorldLoad = false;
                }
            }
            
            return true; // Не блокируем команду
        });

        // Перехват входящих сообщений чата (для fallback спека)
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!fallbackSpecQueue.isEmpty() && fallbackSpecTicks > 0 && !overlay) {
                String text = message.getString().toLowerCase();
                // Защита от бесконечного цикла: игнорируем собственные сообщения мода
                if (text.contains("[funspec]")) return true;
                
                if (text.contains("игрок не найден") || text.contains("не найден") || text.contains("не в сети") || text.contains("оффлайн") || text.contains("цель не найдена") || text.contains("не существует") || text.contains("ошибка")) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.player != null && mc.player.networkHandler != null && !fallbackSpecQueue.isEmpty()) {
                        String nextTarget = fallbackSpecQueue.remove(0);
                        if (fallbackSpecQueue.isEmpty()) {
                            fallbackSpecTicks = 0;
                            mc.player.sendMessage(Text.literal("§e[FunSpec] Игрок не найден. Переход к автору заявки: " + nextTarget), false);
                        } else {
                            fallbackSpecTicks = 100;
                            mc.player.sendMessage(Text.literal("§e[FunSpec] Игрок не найден. Пробую следующий ник: " + nextTarget), false);
                        }
                        mc.player.networkHandler.sendCommand("spec " + nextTarget);
                        currentlySpectating = nextTarget;
                    }
                }
            }
            return true;
        });
    }

    private void onClientTick(MinecraftClient client) {
        // ==================== HWID Tick Guard (вторая линия защиты) ====================
        // Работает ДАЖЕ если другой мод подменил экран
        if (hwidBlocked) {
            net.minecraft.client.gui.screen.Screen current = client.currentScreen;
            if (!(current instanceof ru.antigravity.vkspec.ui.HwidAuthScreen)) {
                client.setScreen(new ru.antigravity.vkspec.ui.HwidAuthScreen(
                    ru.antigravity.vkspec.net.HwidManager.getHwid()));
            }
            return; // Блокируем ВСЮ логику мода
        }
        
        // ==================== Периодическая ре-проверка HWID (третья линия защиты) ====================
        hwidRecheckTicks--;
        if (hwidRecheckTicks <= 0) {
            hwidRecheckTicks = HWID_RECHECK_INTERVAL;
            // Проверяем в фоновом потоке чтобы не лагать игру
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                ru.antigravity.vkspec.net.HwidManager.checkAuthorization("https://funspec-production.up.railway.app");
                if (!ru.antigravity.vkspec.net.HwidManager.isAuthorized()) {
                    // HWID отозван/забанен — блокируем мод
                    hwidBlocked = true;
                    client.execute(() -> {
                        if (client.player != null) {
                            client.player.sendMessage(
                                net.minecraft.text.Text.literal("§c[FunSpec] Доступ отозван. Обратитесь к администратору."),
                                false
                            );
                        }
                        client.setScreen(new ru.antigravity.vkspec.ui.HwidAuthScreen(
                            ru.antigravity.vkspec.net.HwidManager.getHwid()));
                    });
                }
            });
        }

        if (client.world == null || client.player == null) {
            lastWorld = null;
            return;
        }

        // Обновляем счетчик отыгранного времени
        playtimeTicks++;
        if (playtimeTicks >= 1200) { // 1200 тиков = 60 секунд
            playtimeTicks = 0;
            ru.antigravity.vkspec.net.HwidManager.sendPlaytimeHeartbeat("https://funspec-production.up.railway.app", 60);
        }

        // Таймер для fallback спека
        if (fallbackSpecTicks > 0) {
            fallbackSpecTicks--;
            if (fallbackSpecTicks == 0) {
                fallbackSpecQueue.clear();
            }
        }

        // Обнаружение перехода на другой сервер/анархию
        if (client.world != lastWorld) {
            lastWorld = client.world;
            notifiedConnected = false;
            // Авто-ключ ТОЛЬКО при переходе между серверами (не при выходе из спека)
            if (expectingServerTransition) {
                expectingServerTransition = false;
                transitionTimeoutTicks = 0;
                if (config.modEnabled && config.autoKey && config.autoKeyValue != null && !config.autoKeyValue.trim().isEmpty()) {
                    autoKeyDelayTicks = 10; // 0.5 секунды (10 тиков)
                }
            }
        }
        
        // Таймаут ожидания перехода на сервер (если мы уже на нем, мир не перезагрузится)
        if (expectingServerTransition && transitionTimeoutTicks > 0) {
            transitionTimeoutTicks--;
            if (transitionTimeoutTicks == 0) {
                System.out.println("[FunSpec] Server transition timeout (already on server?). Proceeding to spec.");
                expectingServerTransition = false;
                waitingForWorldLoad = false; // Разрешаем продолжать выполнение команд
            }
        }

        // Уведомление о статусе при первом заходе
        if (!notifiedConnected && client.player != null) {
            notifiedConnected = true;
            if (wsServer != null) {
                int count = wsServer.getConnectedBrowsers();
                if (count > 0) {
                    client.player.sendMessage(Text.literal("§a[FunSpec] ✓ Мод готов! Подключено вкладок VK: " + count), false);
                } else {
                    client.player.sendMessage(Text.literal("§e[FunSpec] ⚠ Откройте VK чтобы начать принимать заявки."), false);
                }
            }
        }

        // Авто-перезапуск внутреннего сервера
        if (serverRestartTicks > 0) {
            serverRestartTicks--;
            if (serverRestartTicks == 0) {
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§e[FunSpec] Перезапуск внутреннего сервера..."), false);
                }
                startServer();
            }
        }

        if (autoKeyDelayTicks > 0) {
            autoKeyDelayTicks--;
            if (autoKeyDelayTicks == 0) {
                if (client.player != null && client.player.networkHandler != null) {
                    String cmd = "key " + config.autoKeyValue.trim();
                    client.player.networkHandler.sendCommand(cmd);
                    System.out.println("[FunSpec] Executed auto key command: /" + cmd);
                } else {
                    autoKeyDelayTicks = 20; // попробовать снова через 1 секунду
                }
            }
        }

        // Обновляем таймер жизни уведомлений
        for (SpecNotification notification : activeNotifications) {
            notification.tick();
            if (notification.isExpired()) {
                activeNotifications.remove(notification);
            }
        }

        // ==================== Двухфазный таймаут подтверждения ====================
        if (awaitingConfirmationNickname != null && confirmationTimeoutTicks > 0) {
            confirmationTimeoutTicks--;
            if (confirmationTimeoutTicks <= 0) {
                if (confirmationPhase == 1) {
                    // Фаза 1 истекла — молча заходим на анку, переходим в фазу 2
                    System.out.println("[FunSpec] Phase 1 timeout, silently joining server...");
                    SpecNotification target = awaitingConfirmationNotif;
                    if (target != null && config.autoSpec) {
                        String serverCmd = getServerCommand(target.server);
                        if (serverCmd != null) {
                            this.pendingServerCommand = serverCmd;
                            this.serverCommandDelayTicks = 20; // 1 сек задержка
                        }
                    }
                    // Переходим в фазу 2 — будем ждать ещё 3 сек после захода
                    confirmationPhase = 2;
                    // Таймер фазы 2 запустится после загрузки мира (см. ниже)
                    // Пока ставим большой таймер, чтобы не сбросить раньше времени
                    confirmationTimeoutTicks = 200; // 10 сек запас на загрузку мира
                } else if (confirmationPhase == 2) {
                    // Фаза 2 истекла — подтверждение так и не пришло, просто ничего не делаем
                    System.out.println("[FunSpec] Phase 2 timeout, no confirmation received. Doing nothing.");
                    cancelAwaitingConfirmation();
                }
            }
        }
        
        // Фаза 2: после загрузки мира на новом сервере перезапускаем 3-секундный таймер
        if (confirmationPhase == 2 && !expectingServerTransition && client.world != null && confirmationTimeoutTicks > 100) {
            // Мир загрузился, запускаем точный 3-секундный таймер
            confirmationTimeoutTicks = 60; // 3 секунды
            System.out.println("[FunSpec] Phase 2: world loaded, starting 3-second recheck timer...");
        }

        // Обработка перехода на сервер с задержкой (1 секунда)
        if (pendingServerCommand != null) {
            if (serverCommandDelayTicks > 0) {
                serverCommandDelayTicks--;
            } else {
                expectingServerTransition = true;
                transitionTimeoutTicks = 40; // 2 секунды максимум на смену мира
                if (client.player != null && client.player.networkHandler != null) {
                    client.player.networkHandler.sendCommand(pendingServerCommand);
                }
                waitingForWorldLoad = true;
                pendingServerCommand = null;
            }
        }

        // Обработка очереди отложенных команд (авто-спек)
        if (pendingSpecCommand != null) {
            // Если мы еще даже не отправили команду на переход сервера, то и спекать рано
            if (pendingServerCommand != null) return;
            
            // Ждём загрузки мира после смены сервера
            if (waitingForWorldLoad) {
                if (client.world != null && client.player != null && client.player.networkHandler != null) {
                    // Но мир может быть старым! Поэтому проверим, что expectingServerTransition == false
                    if (!expectingServerTransition) {
                        waitingForWorldLoad = false;
                        boolean willSendKey = config.autoKey && config.autoKeyValue != null && !config.autoKeyValue.trim().isEmpty();
                        if (willSendKey) {
                            // Ключ через ~10 тиков, спек через 3 сек после ключа
                            commandDelayTicks = 70; // 10 + 60 = 3.5с после загрузки
                        } else {
                            // Без ключа — 3 секунды (60 тиков)
                            commandDelayTicks = 60;
                        }
                    } else {
                        return; // Ждем пока мир поменяется
                    }
                } else {
                    return; // Мир ещё не загрузился
                }
            }
            
            if (commandDelayTicks > 0) {
                commandDelayTicks--;
            } else {
                if (client.player.networkHandler != null) {
                    client.player.networkHandler.sendCommand(pendingSpecCommand);
                    System.out.println("[FunSpec] Executed spec command: /" + pendingSpecCommand);
                    if (!fallbackSpecQueue.isEmpty()) {
                        fallbackSpecTicks = 100; // Начинаем отсчет 5 секунд ТОЛЬКО ПОСЛЕ отправки команды
                    }
                    if (pendingSpecDisplayNick != null) {
                        currentlySpectating = pendingSpecDisplayNick;
                        spectatingReason = pendingSpecReason;
                    }
                } else {
                    System.out.println("[FunSpec] networkHandler is null, retrying...");
                    commandDelayTicks = 20; // Retry in 1 sec
                    return;
                }
                
                pendingSpecNickname = null;
                pendingSpecDisplayNick = null;
                pendingSpecReason = null;
                pendingSpecCommand = null;
            }
        }

        // Горячая клавиша: принять заявку
        while (claimKeyBinding.wasPressed()) {
            handleClaimKeyPressed(client);
        }

        // Горячая клавиша: открыть настройки
        while (settingsKeyBinding.wasPressed()) {
            if (client.currentScreen == null) {
                System.out.println("[FunSpec] Settings keybind pressed — opening GUI");
                // Создаём новый экземпляр вместо повторного использования синглтона,
                // чтобы гарантировать чистое состояние init()
                ru.night.Night.get.guiClient = new ru.night.ui.gui.GuiClient();
                client.setScreen(ru.night.Night.get.guiClient);
            } else if (client.currentScreen instanceof ru.night.ui.gui.GuiClient) {
                // Если GUI уже открыт, обрабатываем как toggle (закрытие)
                System.out.println("[FunSpec] Settings keybind pressed — GUI already open, triggering close");
                ru.night.ui.gui.component.main.GuiShouldCloseOnEsc.shouldCloseOnEsc();
            }
        }

        // Горячая клавиша: копировать ник
        while (copyNickKeyBinding.wasPressed()) {
            if (client.targetedEntity != null) {
                String targetName = client.targetedEntity.getName().getString();
                client.keyboard.setClipboard(targetName);
                client.player.sendMessage(Text.literal("§a[FunSpec] Ник скопирован: §f" + targetName), true);
            }
        }
    }

    /**
     * Генерирует правильную команду перехода на сервер.
     * anarchy229 → "an229"
     * duels2 → "srv duels2"
     */
    private String getServerCommand(String server) {
        if (server == null || server.isEmpty()) return null;
        
        String lower = server.toLowerCase();
        
        // anarchy229 → /an229
        if (lower.startsWith("anarchy")) {
            String number = server.substring("anarchy".length());
            return "an" + number;
        }
        
        // duels2 → /srv duels2
        if (lower.startsWith("duels")) {
            return "srv " + server;
        }
        
        // Фоллбэк для неизвестных серверов
        return "server " + server;
    }

    private void handleClaimKeyPressed(MinecraftClient client) {
        // Если уже ждём подтверждения предыдущей заявки — блокируем повторный клик
        if (awaitingConfirmationNickname != null) {
            client.player.sendMessage(
                Text.literal("§e[FunSpec] ⏳ Ожидание подтверждения предыдущей заявки..."), 
                false
            );
            return;
        }
        
        // Находим ПЕРВУЮ (самую старую из неподтвержденных) свободную заявку
        // Обрабатываем очередь по правилу FIFO (старые заявки важнее)
        SpecNotification target = null;
        for (int i = 0; i < activeNotifications.size(); i++) {
            SpecNotification notification = activeNotifications.get(i);
            if (!notification.claimed) {
                target = notification;
                break;
            }
        }

        if (target == null) {
            client.player.sendMessage(Text.literal("§e[FunSpec] Нет активных свободных заявок на спек!"), false);
            return;
        }

        claimTicket(client, target);
    }

    public void claimTicket(MinecraftClient client, SpecNotification target) {
        // 1. Отправляем сигнал в браузер, чтобы он нажал кнопку "Взять"
        if (wsServer != null) {
            wsServer.requestBrowserToClickClaim(target.nickname);
        }
        
        // 2. Входим в Фазу 1 ожидания подтверждения (5 секунд = 100 тиков)
        awaitingConfirmationNickname = target.nickname;
        awaitingConfirmationNotif = target;
        confirmationTimeoutTicks = 100; // 5 секунд (редактирование сообщения может задержаться)
        confirmationPhase = 1;
        clickSucceeded = false;
        
        // 3. Показываем сообщение в чате
        client.player.sendMessage(
            Text.literal("§e[FunSpec] ⏳ Нажимаю кнопку «Взять» для " + target.nickname + "..."), 
            false
        );
        
        System.out.println("[FunSpec] Phase 1: awaiting confirmation for: " + target.nickname);
    }

    /**
     * Вызывается из VkWebSocketServer, когда браузер сообщает о результате клика.
     */
    public void handleClickResult(boolean success, String error, String nickname) {
        // Дедупликация: если пришел click_result для ника, который мы не ожидаем — игнорируем
        if (nickname != null && awaitingConfirmationNickname != null && !nickname.equalsIgnoreCase(awaitingConfirmationNickname)) {
            System.out.println("[FunSpec] Ignoring click_result for " + nickname + " (awaiting " + awaitingConfirmationNickname + ")");
            return;
        }
        // Если мы ничего не ждём — игнорируем
        if (awaitingConfirmationNickname == null) {
            System.out.println("[FunSpec] Ignoring click_result: not awaiting any confirmation");
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        
        if (success) {
            clickSucceeded = true;
            System.out.println("[FunSpec] Browser click succeeded, waiting for VK confirmation...");
            // Не начинаем спек — ждём сообщения "обработана" из VK
        } else {
            // Клик не удался → сразу отменяем ожидание
            client.execute(() -> {
                if (client.player != null) {
                    client.player.sendMessage(
                        Text.literal("§c[FunSpec] ✗ Не удалось нажать кнопку: " + (error != null ? error : "неизвестная ошибка")), 
                        false
                    );
                }
            });
            cancelAwaitingConfirmation();
        }
    }
    
    /**
     * Сброс ожидания подтверждения (вызывается при таймауте, ошибке или чужом клейме).
     */
    public void cancelAwaitingConfirmation() {
        awaitingConfirmationNickname = null;
        awaitingConfirmationNotif = null;
        confirmationTimeoutTicks = 0;
        confirmationPhase = 0;
        clickSucceeded = false;
    }
    
    /**
     * Запуск авто-спека после успешного подтверждения.
     */
    private void executeAutoSpec(MinecraftClient client, SpecNotification target) {
        if (!config.autoSpec) {
            client.player.sendMessage(
                Text.literal("§6[FunSpec] §aВы приняли заявку на §f" + target.nickname + " §7(авто-спек выключен)"), 
                false
            );
            return;
        }
        
        String serverCmd = getServerCommand(target.server);
        
        // Если мы уже спекаем этого же игрока
        if (currentlySpectating != null && currentlySpectating.equals(target.nickname)) {
            return; // предотвратить спам
        }
        
        // Сбрасываем текущий спек
        currentlySpectating = null;
        spectatingReason = null;
        
        // Переходим на нужный сервер с задержкой 1 секунда (20 тиков)
        if (serverCmd != null) {
            this.pendingServerCommand = serverCmd;
            this.serverCommandDelayTicks = 20;
        }
        
        java.util.List<String> candidates = new java.util.ArrayList<>();
        if (config.smartSpec && target.reason != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b([a-zA-Z0-9_]{3,16})\\b").matcher(target.reason);
            while (m.find()) {
                String word = m.group(1);
                if (word.equalsIgnoreCase("spec") || word.equalsIgnoreCase("spek") || word.equalsIgnoreCase("msg") || word.equalsIgnoreCase("hvh") || word.equalsIgnoreCase("tim") || word.equalsIgnoreCase(target.nickname)) continue;
                if (!candidates.contains(word)) candidates.add(word);
            }
        }
        
        if (!candidates.contains(target.nickname)) {
            candidates.add(target.nickname); // Последним всегда идет автор заявки
        }
        
        String specTarget = candidates.remove(0); // Берем первого кандидата
        
        if (!candidates.isEmpty()) {
            this.fallbackSpecQueue.clear();
            this.fallbackSpecQueue.addAll(candidates);
        } else {
            this.fallbackSpecQueue.clear();
            this.fallbackSpecTicks = 0;
        }
        
        // Планируем /spec через задержку
        this.pendingSpecCommand = "spec " + specTarget;
        this.pendingSpecNickname = target.nickname;
        this.pendingSpecDisplayNick = specTarget;
        this.pendingSpecReason = target.reason;
        
        if (serverCmd == null) {
            // Если сервер не менялся — ждем 3 секунды (60 тиков)
            this.commandDelayTicks = 60;
        }
    }
    
    /**
     * Запуск только /spec без перехода на сервер (для Фазы 2 — мы уже на анке).
     */
    private void executeSpecOnly(MinecraftClient client, SpecNotification target) {
        if (!config.autoSpec) {
            client.player.sendMessage(
                Text.literal("§6[FunSpec] §aВы приняли заявку на §f" + target.nickname + " §7(авто-спек выключен)"), 
                false
            );
            return;
        }
        
        // Если мы уже спекаем этого же игрока
        if (currentlySpectating != null && currentlySpectating.equals(target.nickname)) {
            return;
        }
        
        currentlySpectating = null;
        spectatingReason = null;
        
        java.util.List<String> candidates = new java.util.ArrayList<>();
        if (config.smartSpec && target.reason != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b([a-zA-Z0-9_]{3,16})\\b").matcher(target.reason);
            while (m.find()) {
                String word = m.group(1);
                if (word.equalsIgnoreCase("spec") || word.equalsIgnoreCase("spek") || word.equalsIgnoreCase("msg") || word.equalsIgnoreCase("hvh") || word.equalsIgnoreCase("tim") || word.equalsIgnoreCase(target.nickname)) continue;
                if (!candidates.contains(word)) candidates.add(word);
            }
        }
        
        if (!candidates.contains(target.nickname)) {
            candidates.add(target.nickname); // Последним всегда идет автор заявки
        }
        
        String specTarget = candidates.remove(0); // Берем первого кандидата
        
        if (!candidates.isEmpty()) {
            this.fallbackSpecQueue.clear();
            this.fallbackSpecQueue.addAll(candidates);
        } else {
            this.fallbackSpecQueue.clear();
            this.fallbackSpecTicks = 0;
        }
        
        // Мы уже на нужном сервере, просто делаем /spec с задержкой на ключ
        this.pendingSpecCommand = "spec " + specTarget;
        this.pendingSpecNickname = target.nickname;
        this.pendingSpecDisplayNick = specTarget;
        this.pendingSpecReason = target.reason;
        
        boolean willSendKey = config.autoKey && config.autoKeyValue != null && !config.autoKeyValue.trim().isEmpty();
        if (willSendKey) {
            // Ключ уже отправляется автоматически при смене мира, ждём 3 сек после
            this.commandDelayTicks = 70;
        } else {
            this.commandDelayTicks = 40; // 2 секунды
        }
    }

    public void addNotification(String nickname, String server, String reason, long peerId, long convMsgId) {
        // Добавляем новое уведомление в список
        SpecNotification notification = new SpecNotification(
                nickname, server, reason, peerId, convMsgId, this.config.notificationDurationSeconds * 20
        );
        activeNotifications.add(notification);

        // Добавляем в историю
        totalTickets++;
        HistoryEntry entry = new HistoryEntry(nickname, server, reason, peerId, convMsgId);
        ticketHistory.add(0, entry); // Новые сверху
        if (ticketHistory.size() > MAX_HISTORY) {
            ticketHistory.remove(ticketHistory.size() - 1);
        }

        // Воспроизводим звук
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.player != null && client.world != null && this.config.enableSound) {
                ru.night.module.impl.vkspec.SoundNotifModule sm = ru.night.Night.get.manager.get(ru.night.module.impl.vkspec.SoundNotifModule.class);
                if (sm != null) {
                    if (sm.soundType.is("Пинг")) this.config.notificationSound = "entity.arrow.hit_player";
                    else if (sm.soundType.is("Уровень")) this.config.notificationSound = "entity.player.levelup";
                    else this.config.notificationSound = "entity.experience_orb.pickup";
                }

                if (this.config.notificationSound != null && !"none".equals(this.config.notificationSound)) {
                    net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(this.config.notificationSound);
                    if (id != null) {
                        net.minecraft.sound.SoundEvent event = net.minecraft.registry.Registries.SOUND_EVENT.get(id);
                        if (event != null) {
                            client.player.playSound(event, 1.0f, 1.0f);
                        }
                    }
                }
                
                // Резервное сообщение в чат
                client.player.sendMessage(
                    Text.literal("§6[FunSpec] §fНовый запрос на §a" + nickname + " §f(§e" + server + "§f) §7" + reason), 
                    false
                );
            }
        });
    }

    public void markAsClaimed(String nickname, String claimedBy, boolean isMe) {
        MinecraftClient client = MinecraftClient.getInstance();
        String myName = client.player != null ? client.player.getName().getString() : "";
        
        // ==================== Проверка двухфазного подтверждения ====================
        System.out.println("[FunSpec] markAsClaimed called: nickname=" + nickname + ", claimedBy=" + claimedBy + ", isMe=" + isMe + ", myName=" + myName + ", awaitingNick=" + awaitingConfirmationNickname);
        if (awaitingConfirmationNickname != null && awaitingConfirmationNickname.equalsIgnoreCase(nickname)) {
            int currentPhase = confirmationPhase;
            
            if (isMe || claimedBy.equalsIgnoreCase(myName)) {
                // ✅ Заявку взяли МЫ! Запускаем спек.
                System.out.println("[FunSpec] ✅ Confirmation received (phase " + currentPhase + "): WE claimed " + nickname);
                
                SpecNotification confirmedNotif = awaitingConfirmationNotif;
                
                // Обновляем плашку
                if (confirmedNotif != null) {
                    confirmedNotif.claimed = true;
                    confirmedNotif.claimedBy = claimedBy;
                    if (confirmedNotif.ticksLeft > 40) {
                        confirmedNotif.ticksLeft = 40;
                    }
                }
                
                // Обновляем историю
                for (HistoryEntry entry : ticketHistory) {
                    if (entry.nickname.equalsIgnoreCase(nickname) && entry.claimedBy == null) {
                        entry.claimedBy = claimedBy;
                        break;
                    }
                }
                
                myClaimedTickets++;
                
                // Сброс ожидания
                SpecNotification targetForSpec = confirmedNotif;
                cancelAwaitingConfirmation();
                
                // Сообщение в чат и запуск авто-спека
                client.execute(() -> {
                    if (client.player != null) {
                        client.player.sendMessage(
                            Text.literal("§a[FunSpec] ✓ Вы успешно взяли заявку на " + nickname + "!"), 
                            false
                        );
                        if (targetForSpec != null) {
                            if (currentPhase == 1) {
                                // Фаза 1: полный авто-спек (переход на сервер + /spec)
                                executeAutoSpec(client, targetForSpec);
                            } else {
                                // Фаза 2: мы уже на анке, нужен только /spec
                                executeSpecOnly(client, targetForSpec);
                            }
                        }
                    }
                });
                
                return; // Не продолжаем в общую логику
                
            } else {
                // ❌ Заявку забрал кто-то другой — мы не успели
                System.out.println("[FunSpec] ❌ Someone else claimed " + nickname + ": " + claimedBy + " (phase " + currentPhase + ")");
                
                // Если мы на фазе 2 и уже зашли на анку — отменяем все pending команды
                pendingSpecCommand = null;
                pendingSpecNickname = null;
                pendingSpecDisplayNick = null;
                pendingSpecReason = null;
                pendingServerCommand = null;
                
                cancelAwaitingConfirmation();
                
                client.execute(() -> {
                    if (client.player != null) {
                        client.player.sendMessage(
                            Text.literal("§c[FunSpec] ✗ Вы не успели взять заявку (забрал " + claimedBy + ")"), 
                            false
                        );
                    }
                });
                
                // Продолжаем обновлять плашку и историю ниже
            }
        }
        
        // ==================== Общая логика обновления UI ====================
        
        // Обновляем уведомление на экране
        for (SpecNotification notification : activeNotifications) {
            if (notification.nickname.equalsIgnoreCase(nickname)) {
                notification.claimed = true;
                notification.claimedBy = claimedBy;
                
                // Быстро убираем плашку (2 секунды)
                if (notification.ticksLeft > 40) {
                    notification.ticksLeft = 40;
                }
                
                // Звук "кто-то забрал"
                client.execute(() -> {
                    if (client.player != null && client.world != null && this.config.enableSound) {
                        client.player.playSound(
                            SoundEvents.UI_BUTTON_CLICK.value(), 
                            0.5f, 0.8f
                        );
                    }
                });
                break;
            }
        }

        // Обновляем историю (ищем по нику, так как ID сообщения может отличаться)
        for (HistoryEntry entry : ticketHistory) {
            if (entry.nickname.equalsIgnoreCase(nickname) && entry.claimedBy == null) {
                entry.claimedBy = claimedBy;
                break;
            }
        }
    }

    public List<SpecNotification> getActiveNotifications() {
        return activeNotifications;
    }

    public List<HistoryEntry> getTicketHistory() {
        return ticketHistory;
    }

    public int getTotalTickets() {
        return totalTickets;
    }

    public int getMyClaimedTickets() {
        return myClaimedTickets;
    }

    public ModConfig getConfig() {
        return config;
    }

    public void startServer() {
        if (wsServer != null) {
            stopServer();
        }

        int boundPort = config.serverPort;

        try {
            // Проверяем доступность порта перед запуском
            try (java.net.ServerSocket testSocket = new java.net.ServerSocket(boundPort)) {
                testSocket.setReuseAddress(true);
            }
        } catch (java.io.IOException e) {
            System.err.println("[FunSpec] Port " + boundPort + " is busy!");
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§c[FunSpec] ОШИБКА: Порт " + boundPort + " занят! Закройте другие окна Майнкрафта."), false);
                }
            });
            return;
        }

        try {
            wsServer = new VkWebSocketServer(boundPort);
            wsServer.setReuseAddr(true);
            
            // Настройка автоперезапуска
            wsServer.setOnFatalError(() -> {
                // Если сервер упал (например, отвалился интерфейс), перезапускаем через 3 секунды
                this.serverRestartTicks = 60;
                this.wsServer = null;
            });
            
            wsServer.start();
            System.out.println("[FunSpec] WebSocket server started on port " + boundPort);
        } catch (Exception e) {
            System.err.println("[FunSpec] Failed to start WebSocket server:");
            e.printStackTrace();
        }
    }

    public void stopServer() {
        if (wsServer != null) {
            try {
                wsServer.stop(1000); // 1s timeout
            } catch (Exception e) {
                e.printStackTrace();
            }
            wsServer = null;
        }
    }


    public VkWebSocketServer getWsServer() {
        return wsServer;
    }

    public String getCurrentlySpectating() {
        return currentlySpectating;
    }
    
    public String getSpectatingReason() {
        return spectatingReason;
    }

    // ==================== Logs Check ====================

    public void requestLogsCheck(String nicknames) {
        if (wsServer == null) {
            logsResult = "⚠ Браузер не подключен. Откройте VK.";
            logsSearching = false;
            return;
        }
        logsResult = null;
        logsSearching = true;
        wsServer.requestLogsCheck(nicknames);
    }

    public void setLogsResult(String result) {
        this.logsResult = result;
        this.logsSearching = false;
    }

    public String getLogsResult() {
        return logsResult;
    }

    public boolean isLogsSearching() {
        return logsSearching;
    }

    public void clearLogsResult() {
        this.logsResult = null;
        this.logsSearching = false;
    }
}

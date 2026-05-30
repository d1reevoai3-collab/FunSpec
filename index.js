require('dotenv').config();
const express = require('express');
const { VK } = require('vk-io');
const { Telegraf, Markup } = require('telegraf');
const cors = require('cors');
const cookieParser = require('cookie-parser');
const crypto = require('crypto');
const path = require('path');
const rateLimit = require('express-rate-limit');

// Единый PrismaClient для всего проекта
const prisma = require('./lib/prisma');

const authRoutes = require('./routes/auth');
const apiRoutes = require('./routes/api');
const updatesRoutes = require('./routes/updates');
const { requireAuth, requireVkAuth } = require('./middleware/auth');

const app = express();

// --- Настройки из .env ---
const PORT = process.env.PORT || 3000;
const VK_TOKEN = process.env.VK_TOKEN;
const TG_TOKEN = process.env.TG_TOKEN;
const TG_ADMIN_ID = process.env.TG_ADMIN_ID;
const HMAC_SECRET = process.env.HMAC_SECRET || 'FALLBACK_HMAC_SECRET_CHANGE_ME';

// ==========================================
// SECURITY: CORS — только наш домен
// ==========================================
const ALLOWED_ORIGINS = [
    'https://funspec-production.up.railway.app',
    'http://localhost:3000',
    'http://localhost:8080'
];

app.use(cors({
    origin: function (origin, callback) {
        // Разрешаем запросы без origin (curl, мод Minecraft, Postman)
        if (!origin) return callback(null, true);
        if (ALLOWED_ORIGINS.includes(origin)) {
            return callback(null, true);
        }
        return callback(new Error('Not allowed by CORS'));
    },
    credentials: true
}));

app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));
app.use(cookieParser());

// ==========================================
// SECURITY: Rate Limiters
// ==========================================

// Общий лимитер для всех запросов: 200 в минуту
const globalLimiter = rateLimit({
    windowMs: 60 * 1000,
    max: 200,
    standardHeaders: true,
    legacyHeaders: false,
    message: { error: 'Слишком много запросов, попробуйте позже.' }
});
app.use(globalLimiter);

// Жёсткий лимитер для логина: 5 попыток в 15 минут (защита от brute-force)
const loginLimiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 5,
    message: { error: 'Слишком много попыток входа. Подождите 15 минут.' },
    standardHeaders: true,
    legacyHeaders: false
});

// Лимитер для API check: 30 запросов в минуту (защита от перебора HWID)
const checkLimiter = rateLimit({
    windowMs: 60 * 1000,
    max: 30,
    message: { error: 'Rate limit exceeded.' },
    standardHeaders: true,
    legacyHeaders: false
});

// Лимитер для playtime: 10 запросов в минуту на IP
const playtimeLimiter = rateLimit({
    windowMs: 60 * 1000,
    max: 10,
    message: { error: 'Rate limit exceeded.' },
    standardHeaders: true,
    legacyHeaders: false
});

// ==========================================
// SECURITY: Валидация HWID
// ==========================================

// HWID должен быть: буквы A-F, цифры 0-9, дефисы, длина от 8 до 128
const HWID_REGEX = /^[A-Fa-f0-9\-]{8,128}$/;

function isValidHwid(hwid) {
    return typeof hwid === 'string' && HWID_REGEX.test(hwid);
}

// ==========================================
// 1. EXPRESS API (Для мода Майнкрафта)
// ==========================================

// Мод проверяет авторизацию при запуске
app.get('/api/check', checkLimiter, async (req, res) => {
    const { hwid } = req.query;

    if (!hwid) {
        return res.json({ authorized: false, error: 'HWID is required' });
    }

    if (!isValidHwid(hwid)) {
        return res.json({ authorized: false, error: 'Invalid HWID format' });
    }

    try {
        const record = await prisma.hwidRequest.findUnique({
            where: { hwid: hwid }
        });

        const isAuthorized = record && record.status === 'ACTIVE';
        const timestamp = Math.floor(Date.now() / 1000);

        // HMAC-подпись: hwid + ":" + authorized + ":" + timestamp
        const dataToSign = hwid + ':' + isAuthorized + ':' + timestamp;
        const signature = crypto.createHmac('sha256', HMAC_SECRET)
            .update(dataToSign)
            .digest('hex');

        const response = {
            authorized: isAuthorized,
            timestamp: timestamp,
            signature: signature
        };

        // Статус для отображения причины блокировки
        if (record) {
            response.status = record.status;
        }

        return res.json(response);
    } catch (e) {
        console.error('[API Check]', e.message);
        return res.json({ authorized: false, error: 'Server error' });
    }
});

// Мод отправляет обновления playtime (с HMAC-проверкой)
app.post('/api/playtime', playtimeLimiter, async (req, res) => {
    const { hwid, seconds, timestamp, signature } = req.body;

    // Базовая валидация
    if (!hwid || seconds === undefined) {
        return res.status(400).json({ error: 'Missing parameters' });
    }

    if (!isValidHwid(hwid)) {
        return res.status(400).json({ error: 'Invalid HWID format' });
    }

    // Ограничение: максимум 120 секунд (2 минуты) за один запрос
    const sec = parseInt(seconds);
    if (isNaN(sec) || sec < 1 || sec > 120) {
        return res.status(400).json({ error: 'Invalid seconds value (1-120)' });
    }

    // HMAC-проверка (если мод поддерживает подпись)
    if (signature && timestamp) {
        const dataToSign = hwid + ':' + sec + ':' + timestamp;
        const expectedSignature = crypto.createHmac('sha256', HMAC_SECRET)
            .update(dataToSign)
            .digest('hex');

        if (signature !== expectedSignature) {
            return res.status(403).json({ error: 'Invalid signature' });
        }

        // Проверяем, что timestamp не старше 5 минут
        const now = Math.floor(Date.now() / 1000);
        if (Math.abs(now - parseInt(timestamp)) > 300) {
            return res.status(403).json({ error: 'Request expired' });
        }
    }

    try {
        // Проверяем, что HWID активен
        const record = await prisma.hwidRequest.findUnique({ where: { hwid } });
        if (!record || record.status !== 'ACTIVE') {
            return res.status(403).json({ error: 'HWID not active' });
        }

        await prisma.hwidRequest.update({
            where: { hwid: hwid },
            data: { playtimeSeconds: { increment: sec } }
        });
        return res.json({ success: true });
    } catch (e) {
        console.error('[API Playtime]', e.message);
        return res.status(404).json({ error: 'HWID not found' });
    }
});

// ==========================================
// 1.5 FRONTEND ROUTES
// ==========================================

// Защищенные страницы для VK-авторизованных пользователей
app.get('/download.html', requireVkAuth, (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'download.html'));
});
app.get('/download', requireVkAuth, (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'download.html'));
});
app.get('/stats.html', requireVkAuth, (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'stats.html'));
});

// Статика (без auto-serving index.html, чтобы работала авторизация)
app.use(express.static(path.join(__dirname, 'public'), { index: false }));

// Админские страницы
app.get('/admin', requireAuth, (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'index.html'));
});
app.get('/', requireAuth, (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'index.html'));
});
app.get('/index.html', requireAuth, (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// API маршруты (с лимитером для логина)
app.use('/api/auth/login', loginLimiter);
app.use('/api/auth', authRoutes);
app.use('/api', requireAuth, apiRoutes);
app.use('/api/updates', updatesRoutes);

// ==========================================
// 2. VK BOT (Для приема заявок от игроков)
// ==========================================

const vk = new VK({
    token: VK_TOKEN || 'DUMMY_TOKEN',
    pollingGroupId: 239085439,
    pollingWait: 25
});

if (VK_TOKEN) {
    vk.updates.on('message_new', async (context) => {
        if (!context.text) return;

        const text = context.text.trim();
        const cmd = text.toLowerCase();

        // ==========================================
        // Команда: /hwid — привязка HWID
        // ==========================================
        if (cmd.startsWith('/hwid') || cmd.startsWith('hwid')) {
            const parts = text.split(/\s+/);
            if (parts.length < 2) {
                return context.send('❌ Пожалуйста, укажите ваш HWID. Пример: /hwid A8F9-2B3C...');
            }

            const hwid = parts[1].trim();

            // ✅ Валидация HWID
            if (!isValidHwid(hwid)) {
                return context.send('❌ Неверный формат HWID. HWID должен содержать только символы A-F, 0-9 и дефисы, длиной от 8 до 128 символов.\n\nПример: /hwid A8F9-2B3C-D4E5-F6A7');
            }

            try {
                // Проверяем, есть ли уже такой HWID
                let existing = await prisma.hwidRequest.findUnique({ where: { hwid } });

                if (existing) {
                    if (existing.status === 'ACTIVE') {
                        return context.send('✅ Ваш HWID уже зарегистрирован и активен. Приятной игры!');
                    } else if (existing.status === 'BANNED') {
                        return context.send('🚫 Ваш HWID заблокирован администратором.');
                    } else {
                        return context.send('⏳ Ваша заявка уже находится на рассмотрении. Ожидайте ответа.');
                    }
                }

                // Создаем новую заявку
                const request = await prisma.hwidRequest.create({
                    data: {
                        vkId: context.senderId.toString(),
                        hwid: hwid,
                        status: 'PENDING'
                    }
                });

                await context.send('⏳ Ваша заявка принята и отправлена администратору. Ожидайте подтверждения.');

                // Уведомление админу в Telegram
                if (tgBot && TG_ADMIN_ID) {
                    const message = `🔔 <b>Новая заявка на доступ!</b>\n\n👤 VK ID: <a href="https://vk.com/id${context.senderId}">id${context.senderId}</a>\n🔑 HWID: <code>${hwid}</code>`;

                    await tgBot.telegram.sendMessage(TG_ADMIN_ID, message, {
                        parse_mode: 'HTML',
                        ...Markup.inlineKeyboard([
                            Markup.button.callback('✅ Принять', `approve_${request.id}`),
                            Markup.button.callback('❌ Отклонить', `reject_${request.id}`)
                        ])
                    });
                }

            } catch (error) {
                console.error('[VK Bot /hwid]', error.message);
                return context.send('❌ Произошла ошибка при отправке заявки. Попробуйте позже.');
            }
        }
        // ==========================================
        // Команда: /stats — статистика
        // ==========================================
        else if (cmd === '/stats' || cmd === 'stats' || cmd === '/стата' || cmd === 'стата') {
            const vkId = context.senderId.toString();
            try {
                const records = await prisma.hwidRequest.findMany({
                    where: { vkId: vkId }
                });

                if (records.length === 0) {
                    return context.send('❌ Вы еще не зарегистрировали свой HWID в боте.\nИспользуйте команду:\n👉 /hwid [ваш_hwid]\n\nПример: /hwid A8F9-2B3C...');
                }

                let message = '📊 Ваша статистика в FunSpec:\n\n';
                records.forEach((rec, idx) => {
                    const playtimeHours = (rec.playtimeSeconds / 3600).toFixed(1);
                    const statusEmoji = rec.status === 'ACTIVE' ? '✅ Активен' :
                                        rec.status === 'PENDING' ? '⏳ На рассмотрении' :
                                        rec.status === 'BANNED' ? '🚫 Заблокирован' : '❌ Отклонен';

                    message += `${idx + 1}. 🔑 HWID: ${rec.hwid}\n`;
                    message += `   🔹 Статус: ${statusEmoji}\n`;
                    message += `   ⏱ Время игры: ${playtimeHours} ч. (${Math.floor(rec.playtimeSeconds / 60)} мин.)\n`;
                    message += `   📅 Регистрация: ${new Date(rec.createdAt).toLocaleDateString('ru-RU')}\n\n`;
                });

                return context.send(message);
            } catch (error) {
                console.error('[VK Bot /stats]', error.message);
                return context.send('❌ Произошла ошибка при получении статистики. Попробуйте позже.');
            }
        }
        // ==========================================
        // Команда: /mod — информация о моде
        // ==========================================
        else if (cmd === '/mod' || cmd === 'mod' || cmd === '/мод' || cmd === 'мод') {
            try {
                const latestUpdate = await prisma.modUpdate.findFirst({
                    orderBy: { createdAt: 'desc' },
                    include: { files: true }
                });

                let updateInfo = 'ℹ️ Информация о моде:\n';
                updateInfo += '🔹 Название: FunSpec (Fabric 1.21.4)\n';
                updateInfo += '🔹 Назначение: Интеграция ВК-спеков в игру + логи.\n';
                updateInfo += '🌐 Сайт: https://funspec-production.up.railway.app/\n\n';

                if (latestUpdate) {
                    const formattedDate = new Date(latestUpdate.createdAt).toLocaleDateString('ru-RU');
                    updateInfo += `🆕 Последнее обновление (${formattedDate}):\n`;
                    updateInfo += `📝 ${latestUpdate.description}\n`;
                    if (latestUpdate.files && latestUpdate.files.length > 0) {
                        updateInfo += `📁 Файл: ${latestUpdate.files[0].originalName}\n`;
                    }
                    updateInfo += '\n👉 Скачать мод можно в личном кабинете на сайте.';
                    updateInfo += '\n\n📖 Полная памятка и инструкция по установке:';
                    updateInfo += '\nhttps://vk.com/@funspec-pamyatka-dlya-raboty-ustanovka-i-ispolzovanie-funspec';
                    updateInfo += '\n\n⚡ Функционал и возможности мода:';
                    updateInfo += '\nhttps://vk.com/@funspec-funkcional-i-vozmozhnosti-funspec';
                    return context.send(updateInfo);
                } else {
                    updateInfo += '⚠️ Информация о версиях пока отсутствует. Скачайте мод в личном кабинете на сайте.';
                }

                return context.send(updateInfo);
            } catch (error) {
                console.error('[VK Bot /mod]', error.message);
                return context.send('❌ Произошла ошибка при получении информации о моде. Попробуйте позже.');
            }
        }
        // ==========================================
        // Команда: /help — список команд
        // ==========================================
        else if (cmd === '/help' || cmd === 'help' || cmd === '/помощь' || cmd === 'помощь') {
            const helpText = `🛠 Помощник FunSpec Bot\n\n` +
                             `Доступные команды:\n` +
                             `👉 /hwid [ключ] — привязать HWID для доступа к моду\n` +
                             `👉 /stats (или стата) — ваш статус, привязанные HWID и время игры\n` +
                             `👉 /mod (или мод) — скачать мод, узнать текущую версию и изменения\n` +
                             `👉 /help (или помощь) — показать это меню\n\n` +
                             `Все команды работают как с косой чертой (/), так и без нее.`;
            return context.send(helpText);
        }
    });
}

// ==========================================
// 3. TELEGRAM BOT (Для админа)
// ==========================================

const tgBot = TG_TOKEN ? new Telegraf(TG_TOKEN) : null;

// Передаём экземпляры в маршруты через app.locals
app.locals.vk = vk;
app.locals.tgBot = tgBot;
app.locals.VK_TOKEN = VK_TOKEN;
app.locals.TG_ADMIN_ID = TG_ADMIN_ID;

if (tgBot) {
    tgBot.on('callback_query', async (ctx) => {
        const action = ctx.callbackQuery.data;

        if (action.startsWith('approve_') || action.startsWith('reject_')) {
            const isApprove = action.startsWith('approve_');
            const reqId = parseInt(action.split('_')[1]);

            try {
                const record = await prisma.hwidRequest.findUnique({ where: { id: reqId } });

                if (!record) {
                    return ctx.answerCbQuery('Заявка не найдена в базе.');
                }

                if (record.status !== 'PENDING') {
                    return ctx.answerCbQuery(`Заявка уже имеет статус: ${record.status}`);
                }

                const newStatus = isApprove ? 'ACTIVE' : 'REJECTED';
                await prisma.hwidRequest.update({
                    where: { id: reqId },
                    data: { status: newStatus }
                });

                // Уведомляем игрока в ВК
                if (VK_TOKEN) {
                    try {
                        const messageToPlayer = isApprove
                            ? '✅ Ваша заявка одобрена! Ваш HWID привязан, перезапустите Minecraft.'
                            : '❌ К сожалению, ваша заявка была отклонена администратором.';

                        await vk.api.messages.send({
                            peer_id: parseInt(record.vkId),
                            message: messageToPlayer,
                            random_id: Math.floor(Math.random() * 1000000000)
                        });
                    } catch (e) {
                        console.error('[TG->VK] Не удалось отправить сообщение:', e.message);
                    }
                }

                // Обновляем сообщение в Telegram
                await ctx.editMessageText(
                    `🔔 <b>Заявка обработана</b>\n\n👤 VK ID: <a href="https://vk.com/id${record.vkId}">id${record.vkId}</a>\n🔑 HWID: <code>${record.hwid}</code>\n\nСтатус: <b>${isApprove ? '✅ ПРИНЯТО' : '❌ ОТКЛОНЕНО'}</b>`,
                    { parse_mode: 'HTML' }
                );

            } catch (error) {
                console.error('[TG Callback]', error.message);
                ctx.answerCbQuery('Произошла ошибка базы данных.');
            }
        }
    });

    // Команда /ban для админа
    tgBot.command('ban', async (ctx) => {
        if (ctx.from.id.toString() !== TG_ADMIN_ID) return;

        const hwid = ctx.message.text.split(' ')[1];
        if (!hwid) return ctx.reply('Использование: /ban <hwid>');

        if (!isValidHwid(hwid)) {
            return ctx.reply('❌ Неверный формат HWID.');
        }

        try {
            await prisma.hwidRequest.update({
                where: { hwid },
                data: { status: 'BANNED' }
            });
            ctx.reply(`✅ HWID ${hwid} успешно заблокирован.`);
        } catch (e) {
            ctx.reply(`❌ Ошибка: HWID не найден.`);
        }
    });

    tgBot.launch();
    console.log('[TG Bot] Started');
}

// ==========================================
// 4. VK LONG POLL (Исправленная версия)
// ==========================================

const axios = require('axios');

async function startVkBot() {
    try {
        console.log('[VK Bot] Fetching Long Poll Server...');
        const serverRes = await axios.get(`https://api.vk.com/method/groups.getLongPollServer?group_id=239085439&access_token=${VK_TOKEN}&v=5.199`);
        const serverData = serverRes.data;
        if (serverData.error) {
            console.error('[VK Bot] Auth error:', serverData.error);
            return;
        }
        let { key, server, ts } = serverData.response;
        console.log('[VK Bot] Successfully connected to Long Poll!');

        while (true) {
            try {
                const pollRes = await axios.get(`${server}?act=a_check&key=${key}&ts=${ts}&wait=25`, { timeout: 30000 });
                const pollData = pollRes.data;

                if (pollData.failed) {
                    // ✅ Правильная обработка кодов ошибок Long Poll
                    if (pollData.failed === 1) {
                        // Просто обновляем ts, не переподключаемся
                        ts = pollData.ts;
                        continue;
                    } else if (pollData.failed === 2) {
                        // Нужен новый key
                        console.log('[VK Bot] Key expired, fetching new key...');
                        const newServer = await axios.get(`https://api.vk.com/method/groups.getLongPollServer?group_id=239085439&access_token=${VK_TOKEN}&v=5.199`);
                        if (newServer.data.response) {
                            key = newServer.data.response.key;
                        }
                        continue;
                    } else {
                        // failed: 3 — нужно полное переподключение
                        console.log('[VK Bot] Session expired (failed: 3), full reconnect...');
                        break;
                    }
                }

                ts = pollData.ts;
                if (pollData.updates) {
                    for (const update of pollData.updates) {
                        vk.updates.handleWebhookUpdate(update);
                    }
                }
            } catch (e) {
                console.error("[VK Bot] Network timeout, retrying in 2s...");
                await new Promise(r => setTimeout(r, 2000));
            }
        }
    } catch (e) {
        console.error('[VK Bot] Fatal connection error:', e.message);
    }
    setTimeout(startVkBot, 3000);
}

// ==========================================
// 5. ЗАПУСК СЕРВЕРА
// ==========================================

let httpServer;

app.listen(PORT, async () => {
    console.log(`[Express] Server is running on port ${PORT}`);
    console.log(`[Security] CORS origins: ${ALLOWED_ORIGINS.join(', ')}`);
    console.log(`[Security] Rate limiting enabled`);
    console.log(`[Security] HWID validation enabled`);

    if (VK_TOKEN) {
        vk.updates.on('error', (err) => console.error('[VK Bot] Background polling error:', err.message));
        startVkBot();
    } else {
        console.log('[VK Bot] Missing VK_TOKEN! Bot not started.');
    }
});

// ==========================================
// 6. GRACEFUL SHUTDOWN
// ==========================================

async function gracefulShutdown(signal) {
    console.log(`\n[${signal}] Shutting down gracefully...`);

    // Останавливаем Telegram бота
    if (tgBot) {
        try { tgBot.stop(signal); } catch (e) { /* ignore */ }
    }

    // Закрываем соединения с БД
    try {
        await prisma.$disconnect();
        console.log('[Prisma] Disconnected from database.');
    } catch (e) { /* ignore */ }

    process.exit(0);
}

process.once('SIGINT', () => gracefulShutdown('SIGINT'));
process.once('SIGTERM', () => gracefulShutdown('SIGTERM'));

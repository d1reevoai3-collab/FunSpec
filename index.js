require('dotenv').config();
const express = require('express');
const { PrismaClient } = require('@prisma/client');
const { VK } = require('vk-io');
const { Telegraf, Markup } = require('telegraf');
const cors = require('cors');

const prisma = new PrismaClient({});
const app = express();

// --- Настройки из .env ---
const PORT = process.env.PORT || 3000;
const VK_TOKEN = process.env.VK_TOKEN;
const TG_TOKEN = process.env.TG_TOKEN;
const TG_ADMIN_ID = process.env.TG_ADMIN_ID; // Твой ID в телеграме

app.use(cors());
app.use(express.json());

// ==========================================
// 1. EXPRESS API (Для мода Майнкрафта)
// ==========================================

// Мод будет стучаться сюда при запуске
app.get('/api/check', async (req, res) => {
    const { hwid } = req.query;
    
    if (!hwid) {
        return res.json({ authorized: false, error: 'HWID is required' });
    }

    try {
        const record = await prisma.hwidRequest.findUnique({
            where: { hwid: hwid }
        });

        // Если запись найдена и её статус ACTIVE — доступ разрешен
        if (record && record.status === 'ACTIVE') {
            return res.json({ authorized: true });
        }

        return res.json({ authorized: false });
    } catch (e) {
        console.error(e);
        return res.json({ authorized: false, error: 'Server error' });
    }
});

// Простая админка
app.get('/admin', (req, res) => {
    res.send('<h1>Админка скоро будет тут! Управление перенесено в Telegram.</h1>');
});

// ==========================================
// 2. VK BOT (Для приема заявок от игроков)
// ==========================================

const vk = new VK({ 
    token: VK_TOKEN || 'DUMMY_TOKEN',
    pollingGroupId: 239085439, // ID группы пользователя
    pollingWait: 25
});

if (VK_TOKEN) {
    vk.updates.on('message_new', async (context) => {
        if (!context.text) return;
        
        const text = context.text.trim();
        
        if (text.startsWith('/hwid')) {
            const parts = text.split(' ');
            if (parts.length < 2) {
                return context.send('❌ Пожалуйста, укажите ваш HWID. Пример: /hwid A8F9-2B3C...');
            }

            const hwid = parts[1].trim();

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

                // Отвечаем игроку в ВК
                await context.send('⏳ Ваша заявка принята и отправлена администратору. Ожидайте подтверждения.');

                // Отправляем уведомление админу в Телеграм
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
                console.error(error);
                return context.send('❌ Произошла ошибка при отправке заявки. Попробуйте позже.');
            }
        }
    });
}

// ==========================================
// 3. TELEGRAM BOT (Для админа)
// ==========================================

const tgBot = TG_TOKEN ? new Telegraf(TG_TOKEN) : null;

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

                // Обновляем статус в базе
                const newStatus = isApprove ? 'ACTIVE' : 'REJECTED';
                await prisma.hwidRequest.update({
                    where: { id: reqId },
                    data: { status: newStatus }
                });

                // Отправляем уведомление игроку в ВК
                if (VK_TOKEN) {
                    try {
                        const messageToPlayer = isApprove 
                            ? '✅ Ваша заявка одобрена! Ваш HWID привязан, перезапустите Minecraft.'
                            : '❌ К сожалению, ваша заявка была отклонена администратором.';
                        
                        await vk.api.messages.send({
                            peer_id: parseInt(record.vkId),
                            message: messageToPlayer,
                            random_id: Math.floor(Math.random() * 10000)
                        });
                    } catch (e) {
                        console.error('Не удалось отправить сообщение в ВК', e);
                    }
                }

                // Меняем сообщение в телеграме
                await ctx.editMessageText(
                    `🔔 <b>Заявка обработана</b>\n\n👤 VK ID: <a href="https://vk.com/id${record.vkId}">id${record.vkId}</a>\n🔑 HWID: <code>${record.hwid}</code>\n\nСтатус: <b>${isApprove ? '✅ ПРИНЯТО' : '❌ ОТКЛОНЕНО'}</b>`,
                    { parse_mode: 'HTML' }
                );
                
            } catch (error) {
                console.error(error);
                ctx.answerCbQuery('Произошла ошибка базы данных.');
            }
        }
    });

    // Удобная команда для админа, чтобы заблокировать HWID
    tgBot.command('ban', async (ctx) => {
        if (ctx.from.id.toString() !== TG_ADMIN_ID) return;

        const hwid = ctx.message.text.split(' ')[1];
        if (!hwid) return ctx.reply('Использование: /ban <hwid>');

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
        
        while(true) {
            try {
                const pollRes = await axios.get(`${server}?act=a_check&key=${key}&ts=${ts}&wait=25`, { timeout: 30000 });
                const pollData = pollRes.data;
                if (pollData.failed) {
                    console.log('[VK Bot] Long poll session expired, restarting...');
                    break; 
                }
                ts = pollData.ts;
                if (pollData.updates) {
                    for (const update of pollData.updates) {
                        vk.updates.handleWebhookUpdate(update);
                    }
                }
            } catch(e) {
                console.error("[VK Bot] Network timeout, restarting poll...");
                await new Promise(r => setTimeout(r, 2000));
            }
        }
    } catch (e) {
        console.error('[VK Bot] Fatal connection error:', e.message);
    }
    setTimeout(startVkBot, 3000);
}

app.listen(PORT, async () => {
    console.log(`[Express] Server is running on port ${PORT}`);
    if (VK_TOKEN) {
        vk.updates.on('error', (err) => console.error('[VK Bot] Background polling error:', err.message));
        startVkBot();
    } else {
        console.log('[VK Bot] Missing VK_TOKEN! Bot not started.');
    }
});

// Грейсфул стоппер
process.once('SIGINT', () => {
    if(tgBot) tgBot.stop('SIGINT');
});
process.once('SIGTERM', () => {
    if(tgBot) tgBot.stop('SIGTERM');
});

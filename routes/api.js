const express = require('express');
const router = express.Router();
const prisma = require('../lib/prisma');

// ==========================================
// Демо-режим (fallback при недоступности БД)
// ==========================================
let isDemoMode = false;
let mockHwids = [
  { id: 1, vkId: "10550302", hwid: "D3B07384-9D60-4E8D-B992-E2B5AF70E102", status: "PENDING", playtimeSeconds: 0, createdAt: new Date(Date.now() - 3600000 * 2), updatedAt: new Date(Date.now() - 3600000 * 2) },
  { id: 2, vkId: "48202958", hwid: "A1C2E3F4-5A6B-7C8D-9E0F-1A2B3C4D5E6F", status: "ACTIVE", playtimeSeconds: 7200, createdAt: new Date(Date.now() - 3600000 * 24), updatedAt: new Date(Date.now() - 3600000 * 23) },
  { id: 3, vkId: "99887766", hwid: "B8C16790-2E3F-485A-9D8B-3C4D5E6F7A8B", status: "REJECTED", playtimeSeconds: 300, createdAt: new Date(Date.now() - 3600000 * 48), updatedAt: new Date(Date.now() - 3600000 * 47) },
  { id: 4, vkId: "99104820", hwid: "F2E1D0C9-B8A7-6D5C-4B3A-2910F8E7D6C5", status: "BANNED", playtimeSeconds: 14400, createdAt: new Date(Date.now() - 3600000 * 72), updatedAt: new Date(Date.now() - 3600000 * 71) }
];

async function executeWithDbFallback(req, res, dbOperation, mockOperation) {
  try {
    const result = await dbOperation();
    return res.json({ success: true, ...result });
  } catch (error) {
    console.warn(`⚠️ Prisma error, enabling Demo Fallback: ${error.message}`);
    isDemoMode = true;
    const result = mockOperation();
    return res.json({
      success: true,
      isDemo: true,
      warning: 'Демо-режим: База данных оффлайн. Отображаются демонстрационные данные.',
      ...result
    });
  }
}

// GET /api/hwids — Получить все заявки с фильтрацией и поиском
router.get('/hwids', async (req, res) => {
  const { status, search } = req.query;

  const dbOp = async () => {
    const where = {};
    if (status && status !== 'ALL') {
      where.status = status;
    }
    if (search && search.trim() !== '') {
      where.OR = [
        { vkId: { contains: search.trim(), mode: 'insensitive' } },
        { hwid: { contains: search.trim(), mode: 'insensitive' } }
      ];
    }
    const data = await prisma.hwidRequest.findMany({
      where,
      orderBy: { updatedAt: 'desc' }
    });
    return { data };
  };

  const mockOp = () => {
    let data = [...mockHwids];
    if (status && status !== 'ALL') {
      data = data.filter(item => item.status === status);
    }
    if (search && search.trim() !== '') {
      const q = search.trim().toLowerCase();
      data = data.filter(item =>
        item.vkId.toLowerCase().includes(q) ||
        item.hwid.toLowerCase().includes(q)
      );
    }
    data.sort((a, b) => b.updatedAt - a.updatedAt);
    return { data };
  };

  await executeWithDbFallback(req, res, dbOp, mockOp);
});

// GET /api/stats — Статистика дашборда
router.get('/stats', async (req, res) => {
  const dbOp = async () => {
    const [total, pending, active, banned] = await Promise.all([
      prisma.hwidRequest.count(),
      prisma.hwidRequest.count({ where: { status: 'PENDING' } }),
      prisma.hwidRequest.count({ where: { status: 'ACTIVE' } }),
      prisma.hwidRequest.count({ where: { status: 'BANNED' } })
    ]);
    return { stats: { total, pending, active, banned } };
  };

  const mockOp = () => {
    return {
      stats: {
        total: mockHwids.length,
        pending: mockHwids.filter(i => i.status === 'PENDING').length,
        active: mockHwids.filter(i => i.status === 'ACTIVE').length,
        banned: mockHwids.filter(i => i.status === 'BANNED').length
      }
    };
  };

  await executeWithDbFallback(req, res, dbOp, mockOp);
});

// GET /api/analytics — Аналитика и графики
router.get('/analytics', async (req, res) => {
  const dbOp = async () => {
    const [active, banned, pending, rejected] = await Promise.all([
      prisma.hwidRequest.count({ where: { status: 'ACTIVE' } }),
      prisma.hwidRequest.count({ where: { status: 'BANNED' } }),
      prisma.hwidRequest.count({ where: { status: 'PENDING' } }),
      prisma.hwidRequest.count({ where: { status: 'REJECTED' } })
    ]);

    const sevenDaysAgo = new Date();
    sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
    sevenDaysAgo.setHours(0, 0, 0, 0);

    const recentRequests = await prisma.hwidRequest.findMany({
      where: { createdAt: { gte: sevenDaysAgo } },
      select: { createdAt: true }
    });

    const registrationsPerDay = getRegistrationsMap(recentRequests);

    const topPlaytime = await prisma.hwidRequest.findMany({
      take: 5,
      orderBy: { playtimeSeconds: 'desc' },
      select: { vkId: true, playtimeSeconds: true }
    });

    return {
      analytics: {
        statusDistribution: { ACTIVE: active, BANNED: banned, PENDING: pending, REJECTED: rejected },
        registrationsPerDay,
        topPlaytime
      }
    };
  };

  const mockOp = () => {
    const active = mockHwids.filter(i => i.status === 'ACTIVE').length;
    const banned = mockHwids.filter(i => i.status === 'BANNED').length;
    const pending = mockHwids.filter(i => i.status === 'PENDING').length;
    const rejected = mockHwids.filter(i => i.status === 'REJECTED').length;

    const sevenDaysAgo = new Date();
    sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 7);
    sevenDaysAgo.setHours(0, 0, 0, 0);
    const recentRequests = mockHwids.filter(item => item.createdAt >= sevenDaysAgo);
    const registrationsPerDay = getRegistrationsMap(recentRequests);

    const sorted = [...mockHwids]
      .sort((a, b) => (b.playtimeSeconds || 0) - (a.playtimeSeconds || 0))
      .slice(0, 5)
      .map(item => ({ vkId: item.vkId, playtimeSeconds: item.playtimeSeconds || 0 }));

    return {
      analytics: {
        statusDistribution: { ACTIVE: active, BANNED: banned, PENDING: pending, REJECTED: rejected },
        registrationsPerDay,
        topPlaytime: sorted
      }
    };
  };

  await executeWithDbFallback(req, res, dbOp, mockOp);
});

// Вспомогательная функция: подсчёт регистраций за 7 дней
function getRegistrationsMap(requests) {
  const map = {};
  for (let i = 6; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const dateStr = d.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
    map[dateStr] = 0;
  }
  requests.forEach(r => {
    const dateStr = new Date(r.createdAt).toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
    if (map[dateStr] !== undefined) {
      map[dateStr]++;
    }
  });
  return map;
}

// PATCH /api/hwids/:id — Изменить статус заявки
router.patch('/hwids/:id', async (req, res) => {
  const id = parseInt(req.params.id);
  const { status } = req.body;

  if (isNaN(id)) return res.status(400).json({ success: false, error: 'Неверный ID запроса' });
  const validStatuses = ['PENDING', 'ACTIVE', 'REJECTED', 'BANNED'];
  if (!status || !validStatuses.includes(status)) return res.status(400).json({ success: false, error: 'Неверный статус запроса' });

  try {
    const updatedRequest = await prisma.hwidRequest.update({
      where: { id },
      data: { status }
    });

    res.json({ success: true, data: updatedRequest });

    // Fire-and-forget уведомления
    sendStatusNotifications(req, updatedRequest, status).catch(err =>
      console.error('[Notifications] Error:', err.message)
    );
  } catch (err) {
    if (isDemoMode) {
      const index = mockHwids.findIndex(item => item.id === id);
      if (index === -1) return res.status(404).json({ success: false, error: 'Запрос HWID не найден' });
      mockHwids[index].status = status;
      mockHwids[index].updatedAt = new Date();
      return res.json({ success: true, data: mockHwids[index] });
    }
    console.error('[PATCH /hwids/:id]', err.message);
    return res.status(404).json({ success: false, error: 'Запрос HWID не найден' });
  }
});

async function sendStatusNotifications(req, record, newStatus) {
  const vk = req.app.locals.vk;
  const tgBot = req.app.locals.tgBot;
  const VK_TOKEN = req.app.locals.VK_TOKEN;
  const TG_ADMIN_ID = req.app.locals.TG_ADMIN_ID;

  const statusEmoji = { 'ACTIVE': '✅', 'REJECTED': '❌', 'BANNED': '🚫', 'PENDING': '⏳' };
  const statusMessages = {
    'ACTIVE': '✅ Ваша заявка одобрена! Ваш HWID привязан, перезапустите Minecraft.',
    'REJECTED': '❌ К сожалению, ваша заявка была отклонена администратором.',
    'BANNED': '🚫 Ваш HWID был заблокирован администратором.'
  };

  if (VK_TOKEN && vk && statusMessages[newStatus] && /^\d+$/.test(record.vkId)) {
    try {
      await vk.api.messages.send({
        peer_id: parseInt(record.vkId),
        message: statusMessages[newStatus],
        random_id: Math.floor(Math.random() * 1000000000)
      });
      console.log(`[VK Bot] Notification sent to ${record.vkId} about status ${newStatus}`);
    } catch (e) {
      console.error('[VK Bot] Failed to send notification:', e.message);
    }
  }

  if (tgBot && TG_ADMIN_ID) {
    try {
      const emoji = statusEmoji[newStatus] || '❓';
      const message = `🖥 <b>Статус изменён через веб-панель</b>\n\n👤 VK: <a href="https://vk.com/id${record.vkId}">id${record.vkId}</a>\n🔑 HWID: <code>${record.hwid}</code>\n\nСтатус: <b>${emoji} ${newStatus}</b>`;
      await tgBot.telegram.sendMessage(TG_ADMIN_ID, message, { parse_mode: 'HTML' });
    } catch (e) {
      console.error('[TG Bot] Failed to send admin notification:', e.message);
    }
  }
}

// DELETE /api/hwids/:id — Удалить заявку
router.delete('/hwids/:id', async (req, res) => {
  const id = parseInt(req.params.id);
  if (isNaN(id)) {
    return res.status(400).json({ success: false, error: 'Неверный ID запроса' });
  }

  try {
    if (isDemoMode) {
      const index = mockHwids.findIndex(item => item.id === id);
      if (index === -1) return res.status(404).json({ success: false, error: 'Запрос HWID не найден' });
      mockHwids.splice(index, 1);
      return res.json({ success: true, message: 'Запрос успешно удален' });
    }

    await prisma.hwidRequest.delete({ where: { id } });
    return res.json({ success: true, message: 'Запрос успешно удален' });
  } catch (err) {
    console.error('[DELETE /hwids/:id]', err.message);
    return res.status(404).json({ success: false, error: 'Запрос HWID не найден' });
  }
});

module.exports = router;

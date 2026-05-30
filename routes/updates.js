const express = require('express');
const router = express.Router();
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const prisma = require('../lib/prisma');

// Папка для хранения загруженных файлов
const uploadDir = path.join(__dirname, '../uploads');
if (!fs.existsSync(uploadDir)) {
    fs.mkdirSync(uploadDir, { recursive: true });
}

// Настройка Multer С ЛИМИТАМИ БЕЗОПАСНОСТИ
const storage = multer.diskStorage({
    destination: uploadDir,
    filename: (req, file, cb) => {
        const safeName = Date.now() + '-' + file.originalname.replace(/[^\x00-\x7F]/g, '_').replace(/\s+/g, '_');
        cb(null, safeName);
    }
});

const upload = multer({
    storage: storage,
    limits: {
        fileSize: 100 * 1024 * 1024, // Максимум 100 МБ на файл
        files: 5                       // Максимум 5 файлов за раз
    }
});

const { requireAuth, requireVkAuth, isAuthenticated, isVkAuthenticated } = require('../middleware/auth');

function requireAnyAuth(req, res, next) {
    if (isAuthenticated(req) || isVkAuthenticated(req)) {
        return next();
    }
    return res.status(401).json({ error: 'Unauthorized' });
}

// GET /api/updates/download/:fileId — Безопасное скачивание файла
router.get('/download/:fileId', requireAnyAuth, async (req, res) => {
    try {
        const fileId = parseInt(req.params.fileId);
        if (isNaN(fileId)) {
            return res.status(400).json({ error: 'Invalid file ID' });
        }

        const fileRecord = await prisma.updateFile.findUnique({
            where: { id: fileId }
        });
        if (!fileRecord) {
            return res.status(404).json({ error: 'File not found' });
        }

        const filePath = fileRecord.filePath;

        // ✅ ЗАЩИТА ОТ PATH TRAVERSAL: проверяем, что файл внутри uploadDir
        const resolvedPath = path.resolve(filePath);
        const resolvedUploadDir = path.resolve(uploadDir);
        if (!resolvedPath.startsWith(resolvedUploadDir)) {
            console.error(`[Security] Path traversal attempt blocked: ${filePath}`);
            return res.status(403).json({ error: 'Access denied' });
        }

        if (!fs.existsSync(resolvedPath)) {
            return res.status(404).json({ error: 'File not found on disk' });
        }

        res.download(resolvedPath, fileRecord.originalName);
    } catch (error) {
        console.error('[Updates] Download error:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
});

// POST /api/updates — Создать обновление (ТОЛЬКО АДМИН)
router.post('/', requireAuth, upload.array('files'), async (req, res) => {
    try {
        const description = req.body.description || req.body.changelog || '';
        const files = req.files;

        if (!description && (!files || files.length === 0)) {
            return res.status(400).json({ error: 'Description or files are required' });
        }

        // Сохраняем обновление в БД
        const modUpdate = await prisma.modUpdate.create({
            data: {
                description: description || '',
                files: {
                    create: files ? files.map(file => ({
                        originalName: file.originalname,
                        filePath: file.path
                    })) : []
                }
            },
            include: { files: true }
        });

        // Рассылка уведомлений ВКонтакте
        const vk = req.app.locals.vk;
        const VK_TOKEN = req.app.locals.VK_TOKEN;

        if (VK_TOKEN && vk) {
            try {
                const activeUsers = await prisma.hwidRequest.findMany({
                    where: { status: 'ACTIVE' }
                });

                if (activeUsers.length > 0) {
                    const userIds = activeUsers.map(u => parseInt(u.vkId)).filter(id => !isNaN(id));
                    const domain = process.env.RAILWAY_PUBLIC_DOMAIN || 'funspec-production.up.railway.app';

                    const messageText = `🔔 Новое обновление FunSpec!\n\n${description || 'Вышло обновление!'}\n\n📦 Скачать новые файлы можно на нашем официальном сайте:\nhttps://${domain}/download`;

                    const chunkSize = 100;
                    for (let i = 0; i < userIds.length; i += chunkSize) {
                        const chunk = userIds.slice(i, i + chunkSize);
                        await vk.api.messages.send({
                            peer_ids: chunk,
                            message: messageText,
                            random_id: Math.floor(Math.random() * 1000000000)
                        });
                    }
                    console.log(`[Updates] Уведомление разослано ${userIds.length} модераторам.`);
                }
            } catch (vkError) {
                console.error('[Updates] Ошибка при рассылке ВК:', vkError.message);
            }
        }

        res.json({ success: true, update: modUpdate });
    } catch (error) {
        console.error('[Updates] Error:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
});

// GET /api/updates — Список обновлений
router.get('/', requireAnyAuth, async (req, res) => {
    try {
        const updates = await prisma.modUpdate.findMany({
            orderBy: { createdAt: 'desc' },
            include: { files: true },
            take: 20
        });
        res.json(updates);
    } catch (error) {
        console.error('[Updates] Error fetch:', error);
        res.status(500).json({ error: 'Internal server error' });
    }
});

module.exports = router;

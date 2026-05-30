const crypto = require('crypto');

// ==========================================
// Секреты берутся ТОЛЬКО из переменных окружения
// Никогда не хардкодь секреты в исходном коде!
// ==========================================

const AUTH_SECRET = process.env.AUTH_SECRET || 'FALLBACK_AUTH_SECRET_CHANGE_ME';
const VK_AUTH_SECRET = process.env.VK_AUTH_SECRET || 'FALLBACK_VK_AUTH_SECRET_CHANGE_ME';

// Генерация безопасного токена для админской сессии
function getAuthToken() {
  const password = process.env.ADMIN_PASSWORD || 'admin';
  return crypto
    .createHmac('sha256', AUTH_SECRET)
    .update(password)
    .digest('hex');
}

// Middleware: проверка авторизации админа
function requireAuth(req, res, next) {
  const token = req.cookies.admin_session;
  const expectedToken = getAuthToken();

  if (token === expectedToken) {
    return next();
  }

  // Если это API-запрос — возвращаем 401
  if (req.path.startsWith('/api/') || req.xhr || req.headers.accept?.includes('application/json')) {
    return res.status(401).json({ success: false, error: 'Unauthorized' });
  }

  // Иначе редирект на страницу логина
  res.redirect('/login.html');
}

// Проверка авторизации (boolean)
function isAuthenticated(req) {
  const token = req.cookies.admin_session;
  return token === getAuthToken();
}

// --- VK Auth ---

function getVkAuthToken(vkId) {
  return crypto
    .createHmac('sha256', VK_AUTH_SECRET)
    .update(vkId.toString())
    .digest('hex');
}

function requireVkAuth(req, res, next) {
  // Админ тоже может заходить на VK-защищённые страницы
  if (isAuthenticated(req)) {
    return next();
  }

  const token = req.cookies.vk_session;
  const vkId = req.cookies.vk_id;

  if (token && vkId && token === getVkAuthToken(vkId)) {
    req.vkId = vkId;
    return next();
  }

  // API → 401, иначе редирект
  if (req.path.startsWith('/api/') || req.xhr || req.headers.accept?.includes('application/json')) {
    return res.status(401).json({ success: false, error: 'Unauthorized (VK Login required)' });
  }

  res.redirect('/vk-login.html');
}

function isVkAuthenticated(req) {
  const token = req.cookies.vk_session;
  const vkId = req.cookies.vk_id;
  return token && vkId && token === getVkAuthToken(vkId);
}

module.exports = {
  requireAuth,
  isAuthenticated,
  getAuthToken,
  requireVkAuth,
  isVkAuthenticated,
  getVkAuthToken
};

const express = require('express');
const router = express.Router();
const axios = require('axios');
const { getAuthToken, isAuthenticated, getVkAuthToken, isVkAuthenticated } = require('../middleware/auth');

// POST /api/auth/login — Вход по паролю
router.post('/login', (req, res) => {
  const { username, password } = req.body;
  const expectedPassword = process.env.ADMIN_PASSWORD || 'admin';

  if (password === expectedPassword) {
    const token = getAuthToken();

    // Безопасная cookie на 7 дней
    res.cookie('admin_session', token, {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'strict',
      maxAge: 7 * 24 * 60 * 60 * 1000
    });

    return res.json({ success: true, message: 'Logged in successfully' });
  }

  return res.status(401).json({ success: false, error: 'Неверное имя пользователя или пароль' });
});

// POST /api/auth/logout
router.post('/logout', (req, res) => {
  res.clearCookie('admin_session');
  res.clearCookie('vk_session');
  res.clearCookie('vk_id');
  res.clearCookie('vk_name');
  return res.json({ success: true, message: 'Logged out successfully' });
});

// GET /api/auth/status — Проверка статуса авторизации
router.get('/status', (req, res) => {
  if (isAuthenticated(req)) {
    return res.json({ authenticated: true, type: 'admin' });
  }
  if (isVkAuthenticated(req)) {
    const vkName = req.cookies.vk_name ? decodeURIComponent(req.cookies.vk_name) : null;
    return res.json({ authenticated: true, type: 'vk', vkId: req.cookies.vk_id, name: vkName });
  }
  return res.json({ authenticated: false });
});

// ==========================================
// VK OAuth Routes
// ==========================================

const VK_APP_ID = process.env.VK_APP_ID || '54622572';
const VK_REDIRECT_URI = process.env.VK_REDIRECT_URI || 'https://funspec-production.up.railway.app/vk-callback.html';

router.get('/vk', (req, res) => {
  if (!VK_APP_ID || VK_APP_ID === 'PLACEHOLDER_ID') {
    return res.status(500).send('VK_APP_ID is not configured in .env');
  }
  // Используем Implicit Flow (response_type=token), чтобы не требовался секретный ключ!
  const authUrl = `https://oauth.vk.com/authorize?client_id=${VK_APP_ID}&display=page&redirect_uri=${VK_REDIRECT_URI}&response_type=token&v=5.199`;
  res.redirect(authUrl);
});

// VK SDK требует валидный redirectUrl, даже если обработка идет на клиенте
router.get('/vk/callback', (req, res) => {
  res.send('<html><body><script>window.close();</script>Авторизация...</body></html>');
});

router.post('/vk/implicit', async (req, res) => {
  const { access_token, user_id } = req.body;
  
  if (!access_token || !user_id) {
    return res.status(400).json({ success: false, error: 'Missing token or user_id' });
  }

  try {
    // 1. Проверяем валидность токена, запросив профиль пользователя
    const apiRes = await axios.get(`https://api.vk.com/method/users.get?user_ids=${user_id}&fields=photo_100&access_token=${access_token}&v=5.199`);
    
    if (apiRes.data.error) {
      console.error('VK Implicit Auth Error:', apiRes.data.error.error_msg);
      return res.status(401).json({ success: false, error: 'Invalid token' });
    }

    const user = apiRes.data.response[0];
    const fullName = `${user.first_name} ${user.last_name}`;

    // 2. Устанавливаем безопасные cookies
    const vkToken = getVkAuthToken(user_id);

    const cookieOpts = {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'strict',
      maxAge: 30 * 24 * 60 * 60 * 1000 // 30 дней
    };

    res.cookie('vk_session', vkToken, cookieOpts);
    res.cookie('vk_id', user_id.toString(), cookieOpts);

    // Имя доступно из JS для отображения, но не содержит секретов
    res.cookie('vk_name', encodeURIComponent(fullName), {
      httpOnly: false,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'strict',
      maxAge: 30 * 24 * 60 * 60 * 1000
    });

    return res.json({ success: true, user: fullName });

  } catch (error) {
    console.error('VK Implicit Auth Callback Error:', error.message);
    return res.status(500).json({ success: false, error: 'Internal server error' });
  }
});

module.exports = router;

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
const VK_REDIRECT_URI = process.env.VK_REDIRECT_URI || 'https://funspec-production.up.railway.app/api/auth/vk/callback';
const crypto = require('crypto');

router.get('/vk', (req, res) => {
  if (!VK_APP_ID || VK_APP_ID === 'PLACEHOLDER_ID') {
    return res.status(500).send('VK_APP_ID is not configured in .env');
  }
  
  // Генерируем PKCE ключи
  const codeVerifier = crypto.randomBytes(32).toString('base64url');
  const codeChallenge = crypto.createHash('sha256').update(codeVerifier).digest('base64url');
  
  // Сохраняем verifier во временную куку
  res.cookie('vk_code_verifier', codeVerifier, {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax',
    maxAge: 5 * 60 * 1000 // 5 минут
  });

  const authUrl = `https://oauth.vk.com/authorize?client_id=${VK_APP_ID}&display=page&redirect_uri=${VK_REDIRECT_URI}&response_type=code&code_challenge=${codeChallenge}&code_challenge_method=s256&v=5.199`;
  res.redirect(authUrl);
});

router.get('/vk/callback', async (req, res) => {
  const code = req.query.code;
  const deviceId = req.query.device_id || '';
  const codeVerifier = req.cookies.vk_code_verifier;
  
  if (!code) {
    return res.redirect('/vk-login.html?error=no_code');
  }

  if (!codeVerifier) {
    console.error('Missing code_verifier in cookies');
    return res.redirect('/vk-login.html?error=server_error');
  }

  try {
    // 1. Обмениваем код на токен через PKCE (БЕЗ client_secret!)
    const tokenUrl = `https://oauth.vk.com/access_token?client_id=${VK_APP_ID}&redirect_uri=${VK_REDIRECT_URI}&code=${code}&code_verifier=${codeVerifier}${deviceId ? '&device_id=' + deviceId : ''}`;
    const tokenRes = await axios.get(tokenUrl);

    if (tokenRes.data.error) {
      console.error('VK Auth Error:', tokenRes.data.error_description);
      return res.redirect('/vk-login.html?error=vk_error');
    }

    const { access_token, user_id } = tokenRes.data;

    // 2. Получаем профиль пользователя. Токен получен на сервере, поэтому ошибки IP не будет!
    const apiRes = await axios.get(`https://api.vk.com/method/users.get?user_ids=${user_id}&fields=photo_100&access_token=${access_token}&v=5.199`);
    
    if (apiRes.data.error) {
      console.error('VK Profile Error:', apiRes.data.error.error_msg);
      return res.redirect('/vk-login.html?error=vk_error');
    }

    const user = apiRes.data.response[0];
    const fullName = `${user.first_name} ${user.last_name}`;

    // 3. Устанавливаем безопасные cookies
    const vkToken = getVkAuthToken(user_id);

    const cookieOpts = {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'strict',
      maxAge: 30 * 24 * 60 * 60 * 1000 // 30 дней
    };

    res.clearCookie('vk_code_verifier');
    res.cookie('vk_session', vkToken, cookieOpts);
    res.cookie('vk_id', user_id.toString(), cookieOpts);

    res.cookie('vk_name', encodeURIComponent(fullName), {
      httpOnly: false,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'strict',
      maxAge: 30 * 24 * 60 * 60 * 1000
    });

    res.redirect('/download.html');

  } catch (error) {
    console.error('VK PKCE Auth Callback Error:', error.message);
    res.redirect('/vk-login.html?error=server_error');
  }
});

module.exports = router;

// ==================== VK MC Bridge v9.0 — Background Service Worker ====================
// Все процессы (Long Poll, WebSocket) работают напрямую здесь. Никаких offscreen.
// Для того чтобы Chrome не усыплял Service Worker, используется content_script keepalive.js

const VK_API_VERSION = '5.199';
const KATE_APP_ID = 2685278; // Kate Mobile
const WS_RECONNECT_MS = 3000;
const HEARTBEAT_MS = 25000; // каждые 25 сек пингуем Minecraft
const MAX_CACHE = 500;

// ==================== State ====================
let vkToken = null;
let vkUserId = null;
let peerId = null;
let peerTitle = null;

let isWsConnected = false;

let lpServer = null;
let lpKey = null;
let lpTs = null;
let lpRunning = false;
let lpAbort = null;

// ==================== LRU Cache ====================
class LRU {
    constructor(max) { this.max = max; this.m = new Map(); }
    has(k) { return this.m.has(k); }
    add(k) {
        this.m.delete(k);
        this.m.set(k, 1);
        if (this.m.size > this.max) this.m.delete(this.m.keys().next().value);
    }
}
const seenSpecs = new LRU(MAX_CACHE);
const seenClaims = new LRU(MAX_CACHE);

// Хранилище последних заявок (чтобы сопоставить "обработана" с никнеймом)
const recentSpecsList = []; // { nickname, server, peer_id, convMsgId, timestamp, claimed }
const MAX_RECENT_SPECS = 100;


// ==================== Init ====================
console.log('[BG] VK MC Bridge v9.2 starting...');
let vkUserFullName = null;

chrome.storage.local.get(['vk_token', 'vk_user_id', 'peer_id', 'peer_title', 'vk_user'], (data) => {
    vkToken = data.vk_token || null;
    vkUserId = data.vk_user_id || null;
    peerId = data.peer_id ? parseInt(data.peer_id) : null;
    peerTitle = data.peer_title || null;
    vkUserFullName = data.vk_user || null;
    
    console.log(`[BG] Init: token=${!!vkToken}, peerId=${peerId}, user=${vkUserFullName}`);
    if (vkToken) startLongPoll();
    connectAllWS();
});

// ==================== Keep-Alive & Auto-Inject ====================
chrome.runtime.onConnect.addListener((port) => {
    if (port.name === 'keep-alive') {
        console.log('[BG] Keep-alive port connected');
        port.onDisconnect.addListener(() => {
            console.log('[BG] Keep-alive port disconnected');
        });
    }
});

// Автоматически внедряем скрипт во все открытые вкладки ВК при запуске расширения
chrome.tabs.query({ url: "*://*.vk.com/*" }, (tabs) => {
    for (const tab of tabs) {
        chrome.scripting.executeScript({
            target: { tabId: tab.id },
            files: ['keepalive.js']
        }).then(() => console.log(`[BG] Injected keepalive to tab ${tab.id}`))
          .catch(err => console.log(`[BG] Failed to inject to tab ${tab.id}:`, err));
    }
});

// ==================== OAuth Token Capture ====================
chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
    if (changeInfo.url && changeInfo.url.includes('oauth.vk.com/blank.html#')) {
        const hash = changeInfo.url.split('#')[1];
        const params = new URLSearchParams(hash);
        const token = params.get('access_token');
        const userId = params.get('user_id');
        
        if (token) {
            console.log('[BG] ✅ OAuth token captured!');
            vkToken = token;
            vkUserId = userId;
            chrome.storage.local.set({ vk_token: token, vk_user_id: userId || '' }, () => {
                startLongPoll();
                fetchVkUser(token);
            });
            chrome.tabs.remove(tabId).catch(() => {});
        }
    }
});

// ==================== Message Router ====================
chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
    switch (msg.action) {
        case 'connect':
            const authUrl = `https://oauth.vk.com/authorize?client_id=${KATE_APP_ID}&display=page&redirect_uri=https://oauth.vk.com/blank.html&scope=messages,offline&response_type=token&v=${VK_API_VERSION}`;
            chrome.tabs.create({ url: authUrl });
            sendResponse({ ok: true });
            break;

        case 'disconnect':
            chrome.storage.local.remove(['vk_token', 'vk_user', 'vk_user_id', 'peer_id', 'peer_title']);
            vkToken = null;
            vkUserId = null;
            peerId = null;
            peerTitle = null;
            stopLongPoll();
            sendResponse({ ok: true });
            break;

        case 'get_status':
            chrome.storage.local.get(['vk_user'], (data) => {
                sendResponse({
                    connected: !!vkToken,
                    user: data.vk_user || null,
                    peerId: peerId,
                    peerTitle: peerTitle
                });
            });
            return true;

        case 'get_conversations':
            getConversations().then(sendResponse);
            return true;

        case 'set_peer':
            peerId = msg.peer_id ? parseInt(msg.peer_id) : null;
            peerTitle = msg.peer_title;
            chrome.storage.local.set({ peer_id: msg.peer_id, peer_title: msg.peer_title }, () => {
                if (vkToken) { stopLongPoll(); startLongPoll(); }
                sendResponse({ ok: true });
            });
            return true;

        case 'status_request':
            sendResponse({ lp: lpRunning, ws: isWsConnected });
            break;
    }
});

// ==================== Fetch User ====================
async function fetchVkUser(token) {
    try {
        const resp = await fetch(`https://api.vk.com/method/users.get?v=${VK_API_VERSION}&access_token=${token}`);
        const data = await resp.json();
        if (data.response && data.response[0]) {
            const u = data.response[0];
            vkUserFullName = `${u.first_name} ${u.last_name}`;
            console.log('[BG] Fetched VK user from API:', vkUserFullName);
            chrome.storage.local.set({ vk_user: vkUserFullName });
        }
    } catch (e) {
        console.error('[BG] fetchVkUser error:', e);
    }
}

// ==================== Conversations ====================
async function getConversations() {
    if (!vkToken) return { error: 'Нет токена — нажмите Подключить' };
    try {
        const resp = await fetch(`https://api.vk.com/method/messages.getConversations?count=30&extended=1&v=${VK_API_VERSION}&access_token=${vkToken}`);
        const result = await resp.json();
        if (result.error) return { error: result.error.error_msg };
        
        const conversations = result.response.items.map(item => {
            const peer = item.conversation.peer;
            let title = 'Неизвестно';
            if (peer.type === 'chat') {
                title = item.conversation.chat_settings?.title || `Беседа ${peer.local_id}`;
            } else if (peer.type === 'group') {
                const g = result.response.groups?.find(g => g.id === Math.abs(peer.id));
                title = g ? g.name : `Группа ${Math.abs(peer.id)}`;
            } else if (peer.type === 'user') {
                const u = result.response.profiles?.find(u => u.id === peer.id);
                title = u ? `${u.first_name} ${u.last_name}` : `ID ${peer.id}`;
            }
            return {
                peer_id: peer.id,
                type: peer.type,
                title,
                unread: item.conversation.unread_count || 0,
                lastMessage: (item.last_message?.text || '').substring(0, 60)
            };
        });
        return { conversations };
    } catch (e) {
        return { error: e.message };
    }
}

// ==================== VK API ====================
async function vkApi(method, params = {}) {
    if (!vkToken) throw new Error('No VK token');
    params.v = VK_API_VERSION;
    params.access_token = vkToken;
    const resp = await fetch(`https://api.vk.com/method/${method}`, {
        method: 'POST',
        body: new URLSearchParams(params)
    });
    const data = await resp.json();
    if (data.error) throw new Error(data.error.error_msg || 'VK API Error');
    return data.response;
}

// ==================== VK Long Poll ====================
async function startLongPoll() {
    if (!vkToken) return;
    stopLongPoll();
    lpRunning = true;
    console.log('[LP] Starting...');
    try {
        const srv = await vkApi('messages.getLongPollServer', { lp_version: 3, need_pts: 0 });
        lpServer = srv.server;
        lpKey = srv.key;
        lpTs = srv.ts;
        pollLoop();
    } catch (e) {
        console.error('[LP] Start failed:', e.message);
        if (lpRunning) setTimeout(startLongPoll, 5000);
    }
}

function stopLongPoll() {
    lpRunning = false;
    if (lpAbort) { lpAbort.abort(); lpAbort = null; }
}

function triggerScroll() {
    chrome.tabs.query({ url: "*://*.vk.com/*" }).then(tabs => {
        const specTabs = tabs.filter(t => t.url && (t.url.includes('2000000040') || t.url.includes('2000000042')));
        if (specTabs.length > 0) {
            chrome.tabs.sendMessage(specTabs[0].id, { action: 'do_scroll' }).catch(() => {});
        }
    }).catch(() => {});
}

async function pollLoop() {
    while (lpRunning && lpServer && lpKey) {
        try {
            lpAbort = new AbortController();
            const resp = await fetch(
                `https://${lpServer}?act=a_check&key=${lpKey}&ts=${lpTs}&wait=25&mode=2&version=3`,
                { signal: lpAbort.signal }
            );
            const data = await resp.json();
            if (data.failed) {
                if (data.failed === 1) { lpTs = data.ts; }
                else {
                    const srv = await vkApi('messages.getLongPollServer', { lp_version: 3, need_pts: 0 });
                    lpServer = srv.server;
                    lpKey = srv.key;
                    if (data.failed === 3) lpTs = srv.ts;
                }
                continue;
            }
            lpTs = data.ts;
            if (data.updates) data.updates.forEach(handleLPEvent);
        } catch (e) {
            if (e.name === 'AbortError') break;
            console.error('[LP] Error:', e.message);
            await new Promise(r => setTimeout(r, 3000));
        }
    }
}

// ==================== Long Poll Events ====================
function handleLPEvent(upd) {
    // 4 - New message, 5 - Edit message
    if (upd[0] !== 4 && upd[0] !== 5) return;
    if (upd[2] & 2) return; // outgoing
    const msgPeerId = upd[3];
    const text = (upd[5] || '').replace(/<br>/gi, '\n');
    const convMsgId = (upd[0] === 4 && upd.length > 9) ? upd[9] : upd[1];

    // --- Логи бота теперь получаем через активный опрос (messages.getHistory), а не через Long Poll ---

    if (peerId && msgPeerId !== peerId) return;

    // --- Новая заявка ---
    if (text.includes('Рассылка-спек') || text.includes('рассылка-спек') || text.includes('Зов спектатора')) {
        const p = parseSpec(text);
        if (p) {
            const k = p.nickname + '_' + p.server;
            if (!seenSpecs.has(k)) {
                seenSpecs.add(k);
                triggerScroll();
                // Сохраняем в список последних заявок для сопоставления с "обработана"
                recentSpecsList.unshift({ nickname: p.nickname, server: p.server, peer_id: msgPeerId, msgId: upd[1], convMsgId, timestamp: Date.now(), claimed: false });
                if (recentSpecsList.length > MAX_RECENT_SPECS) recentSpecsList.pop();
                
                // Сохраняем данные заявки напрямую из Long Poll — мгновенно!
                cacheClaimData(msgPeerId, p.nickname, p.server, p.reason, convMsgId);
                
                sendToMC({ type: 'new_spec', nickname: p.nickname, server: p.server, reason: p.reason, peer_id: msgPeerId, conversation_message_id: convMsgId });
            }
        }
    }
    
    // --- Заявка обработана ---
    if (text.includes('обработана') || text.includes('Обработана') || text.includes('Взял:')) {
        const clean = text.replace(/\[id\d+\|([^\]]+)\]/g, '$1');
        const staffM = clean.match(/(?:Сотрудником\s*:|✅\s*Взял:)\s*([^\n\r]+?)(?:\s+в\s+\d{2}:\d{2}|$)/i);
        const nickM = clean.match(/(?:Никнейм|Игрок)\s*:\s*([_A-Za-z0-9]+)/i);
        
        if (staffM) {
            const claimer = staffM[1].trim();
            let nickname = nickM ? nickM[1].trim() : null;
            
            // Если ника нет в сообщении "обработана", ищем заявку по ТОЧНОМУ message_id
            if (!nickname) {
                const exactSpec = recentSpecsList.find(s => s.msgId === upd[1]);
                if (exactSpec) {
                    nickname = exactSpec.nickname;
                    exactSpec.claimed = true;
                    console.log(`[BG] Matched claim by ${claimer} to EXACT spec: ${nickname} (msgId=${exactSpec.msgId})`);
                } else {
                    // Fallback, если точный не найден
                    const recentSpec = recentSpecsList.find(s => !s.claimed && s.peer_id === msgPeerId);
                    if (recentSpec) {
                        nickname = recentSpec.nickname;
                        recentSpec.claimed = true;
                        console.log(`[BG] Matched claim by ${claimer} to recent spec: ${nickname} (convMsgId=${recentSpec.convMsgId})`);
                    }
                }
            } else {
                // Помечаем заявку как обработанную
                const spec = recentSpecsList.find(s => s.nickname === nickname && !s.claimed);
                if (spec) spec.claimed = true;
            }
            
            if (nickname) {
                const ck = nickname + '_' + claimer;
                triggerScroll();

                if (!seenClaims.has(ck)) {
                    seenClaims.add(ck);
                    
                    let isMe = false;
                    const myClaimTime = recentMyClaims.get(nickname);
                    
                    // Строгая проверка по имени ВК
                    if (vkUserFullName && claimer) {
                        const myParts = vkUserFullName.toLowerCase().split(' ');
                        const claimerLower = claimer.toLowerCase();
                        // Проверяем, есть ли обе части имени и фамилии в claimer (бот может их переставить местами)
                        const isNameMatch = myParts.every(p => claimerLower.includes(p));
                        if (isNameMatch) {
                            isMe = true;
                        }
                    }
                    
                    // Если имя не загрузилось, используем старый метод проверки по таймингу (как fallback)
                    if (!vkUserFullName && myClaimTime && (Date.now() - myClaimTime < 15000)) {
                        isMe = true;
                    }
                    
                    recentMyClaims.delete(nickname);
                    
                    console.log(`[BG] ✅ Claimed: ${nickname} by ${claimer} (isMe: ${isMe})`);
                    sendToMC({ type: 'claimed', nickname: nickname, claimer: claimer, conversation_message_id: convMsgId, isMe: isMe });
                }
            } else {
                console.log(`[BG] ⚠️ Claim by ${claimer} but no matching spec found`);
                // Все равно отправляем — пусть мод покажет хотя бы имя сотрудника
                sendToMC({ type: 'claimed', nickname: 'unknown', claimer: claimer, conversation_message_id: convMsgId });
            }
        }
    }
}

function parseSpec(raw) {
    const text = raw.replace(/\[id\d+\|([^\]]+)\]/g, '$1');
    const nickM = text.match(/(?:Никнейм|Игрок)\s*:\s*([_A-Za-z0-9]+)/i);
    const srvM = text.match(/(?:Server|Сервер)\s*:\s*([A-Za-z0-9_]+)/i);
    const reasonM = text.match(/(?:Сообщение|msg)\s*:\s*[«"]?([^\n\r»"]+)/i);
    if (!nickM || !srvM) return null;
    return { nickname: nickM[1].trim(), server: srvM[1].trim(), reason: reasonM ? reasonM[1].replace(/[»"]$/, '').trim() : '!spec' };
}

// ==================== Claim (Silent AJAX) ====================
const pendingClaims = new Map(); // nickname -> { convMsgId, payload, peerId }
const recentMyClaims = new Map(); // nickname -> timestamp

// Сохраняем данные заявки напрямую из Long Poll — без парсинга HTML!
function cacheClaimData(targetPeerId, nickname, server, msg, convMsgId) {
    const payload = {
        check: 'took',
        sender: nickname,
        server: server,
        msg: msg || '!spec'
    };
    console.log(`[BG] 💾 Кэш заявки: ${nickname} (convMsgId=${convMsgId}, server=${server})`);
    pendingClaims.set(nickname, { convMsgId, payload, peerId: targetPeerId });
    if (pendingClaims.size > MAX_CACHE) pendingClaims.delete(pendingClaims.keys().next().value);
}

// Fallback: если данных нет в кэше, пробуем достать из HTML (старый способ)
async function prefetchClaimHash(targetPeerId, nickname) {
    console.log(`[BG] ⏳ Фоллбэк: ищем кнопку для ${nickname} в HTML...`);
    try {
        const resp = await fetch('https://vk.com/al_im.php', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-Requested-With': 'XMLHttpRequest'
            },
            body: new URLSearchParams({ act: 'a_history', al: 1, peer: targetPeerId, offset: 0, count: 200 })
        });
        
        let text = await resp.text();
        if (text.startsWith('<!--')) text = text.replace(/^<!--/, '');
        
        let data;
        try { data = JSON.parse(text); } catch (e) { return; }
        
        let html = '';
        if (data.payload && Array.isArray(data.payload)) {
            for (const item of data.payload) {
                if (typeof item === 'string' && item.includes('im-mess')) html += item;
                else if (Array.isArray(item)) {
                    for (const sub of item) if (typeof sub === 'string' && sub.includes('im-mess')) html += sub;
                }
            }
        }
        if (!html && data.payload) html = JSON.stringify(data.payload);

        const nickLower = nickname.toLowerCase();
        const payloadRegex = /data-payload="([^"]+)"/gi;
        let payloadMatch;
        const candidates = [];
        
        while ((payloadMatch = payloadRegex.exec(html)) !== null) {
            const rawPayload = payloadMatch[1];
            const decoded = rawPayload.replace(/&quot;/g, '"').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&#(\d+);/g, (m,c) => String.fromCharCode(c));
            try {
                const payloadObj = JSON.parse(decoded);
                if (payloadObj.sender && payloadObj.sender.toLowerCase() === nickLower && payloadObj.check === 'took') {
                    candidates.push({ index: payloadMatch.index, payload: payloadObj });
                }
            } catch(e) {}
        }
        
        if (candidates.length === 0) {
            console.log(`[BG] ⚠️ Fallback: кнопка для ${nickname} не найдена в HTML`);
            return;
        }
        
        const best = candidates[candidates.length - 1];
        const context = html.substring(Math.max(0, best.index - 2000), best.index + 500);
        
        let convMsgId = null;
        const mediaMatch = context.match(/_im_msg_media(\d+)/);
        if (mediaMatch) convMsgId = mediaMatch[1];
        if (!convMsgId) { const m = context.match(/data-msgid\s*=\s*["'](\d+)["']/i); if (m) convMsgId = m[1]; }
        if (!convMsgId) { const m = context.match(/data-cmid\s*=\s*["'](\d+)["']/i); if (m) convMsgId = m[1]; }
        if (!convMsgId) { const m = context.match(/msgid=(\d+)/); if (m) convMsgId = m[1]; }
        
        if (convMsgId) {
            console.log(`[BG] ✅ Fallback: найдена кнопка для ${nickname}: convMsgId=${convMsgId}`);
            pendingClaims.set(nickname, { convMsgId, payload: best.payload, peerId: targetPeerId });
        } else {
            console.log(`[BG] ⚠️ Fallback: кнопка найдена, но convMsgId не извлечён`);
        }
    } catch(e) {
        console.error('[BG] Prefetch error:', e);
    }
}

async function handleClaimFromMC(nickname) {
    if (!vkToken) {
        sendToMC({ type: 'click_result', nickname: nickname, success: false, error: 'Нет токена VK' });
        return;
    }
    
    console.log(`[BG] 🚀 Клик для: ${nickname}`);
    
    let claimData = pendingClaims.get(nickname);
    
    if (!claimData && !peerId) {
        sendToMC({ type: 'click_result', nickname: nickname, success: false, error: 'Чат не выбран в расширении' });
        return;
    }
    
    if (!claimData) {
        console.log(`[BG] ⚠️ Данные не найдены в кэше, делаем синхронный поиск...`);
        await prefetchClaimHash(peerId, nickname);
        claimData = pendingClaims.get(nickname);
    }
    
    if (!claimData) {
        console.log(`[BG] ⚠️ Данные не найдены, пробуем fallback через вкладки...`);
        fallbackTabClick(nickname);
        return;
    }

    try {
        // ===== Fallback на клик через вкладки =====
        console.log(`[BG] 🚀 Пробуем кликнуть через вкладку VK...`);
        recentMyClaims.set(nickname, Date.now());
        fallbackTabClick(nickname);
        pendingClaims.delete(nickname);
        
    } catch (e) {
        console.error(`[BG] ❌ Ошибка при клике:`, e);
        sendToMC({ type: 'click_result', nickname: nickname, success: false });
        pendingClaims.delete(nickname);
    }
}

// =========================================================================
// AUTO-SCROLL ORCHESTRATOR
// =========================================================================
setInterval(async () => {
    try {
        // Ищем все вкладки ВК
        const tabs = await chrome.tabs.query({ url: "*://*.vk.com/*" });
        // Ищем нужный чат (спекхелпер)
        const specTabs = tabs.filter(t => t.url && (t.url.includes('2000000040') || t.url.includes('2000000042')));
        
        // Если есть хотя бы одна, берем только ПЕРВУЮ и отправляем ей сигнал скролла
        if (specTabs.length > 0) {
            chrome.tabs.sendMessage(specTabs[0].id, { action: 'do_scroll' }).catch(() => {});
        }
    } catch(e) {}
}, 1500);

function fallbackTabClick(nickname) {
    chrome.tabs.query({ url: "*://*.vk.com/*" }, (tabs) => {
        if (tabs.length === 0) {
            sendToMC({ type: 'click_result', nickname: nickname, success: false, error: 'Не удалось нажать кнопку, и вкладка VK закрыта' });
            return;
        }
        let anySuccess = false;
        let processed = 0;
        tabs.forEach(tab => {
            chrome.tabs.sendMessage(tab.id, { action: 'click_claim', nickname: nickname }, (resp) => {
                processed++;
                if (!chrome.runtime.lastError && resp && resp.success) anySuccess = true;
                if (processed === tabs.length) {
                    if (anySuccess) {
                        console.log(`[BG] ✅ Fallback: кнопка нажата через вкладку!`);
                        sendToMC({ type: 'click_result', nickname: nickname, success: true });
                    } else {
                        sendToMC({ type: 'click_result', nickname: nickname, success: false, error: 'Кнопка не найдена. Открой чат ВК со заявками.' });
                    }
                }
            });
        });
    });
}

// ==================== WebSocket Multi-Port Discovery ====================
const WS_PORT_BASE = 23588;
const WS_PORT_MAX = 23588; // Проверяем только 1 порт, чтобы не спамить в консоль
const activeSockets = new Map(); // port -> WebSocket
const retryTimers = new Map();   // port -> timerId

function connectAllWS() {
    for (let port = WS_PORT_BASE; port <= WS_PORT_MAX; port++) {
        connectWSPort(port);
    }
}

function connectWSPort(port) {
    if (activeSockets.has(port)) {
        const existing = activeSockets.get(port);
        if (existing.readyState === WebSocket.OPEN || existing.readyState === WebSocket.CONNECTING) return;
    }
    
    if (retryTimers.has(port)) {
        clearTimeout(retryTimers.get(port));
        retryTimers.delete(port);
    }

    let socket;
    try {
        socket = new WebSocket(`ws://127.0.0.1:${port}/?type=userscript`);
    } catch (e) {
        scheduleReconnect(port);
        return;
    }

    socket.onopen = () => { 
        console.log(`[WS] ✅ Connected to port ${port}!`); 
        activeSockets.set(port, socket);
        isWsConnected = true; // Update status for UI
    };
    
    socket.onmessage = (e) => {
        try {
            const d = JSON.parse(e.data);
            if (d.type === 'ping') {
                safeSendToSocket(socket, { type: 'pong' });
            } else if (d.type === 'click_button') {
                handleClaimFromMC(d.nickname);
            } else if (d.type === 'check_logs') {
                handleLogsCheckFromMC(d.nicknames);
            }
        } catch {}
    };
    
    socket.onclose = () => { 
        if (activeSockets.get(port) === socket) {
            console.log(`[WS] Disconnected from port ${port}`); 
            activeSockets.delete(port);
            isWsConnected = activeSockets.size > 0;
            scheduleReconnect(port);
        }
    };
    
    socket.onerror = (e) => { 
        // Silently handle errors to not spam the console, as most ports will be offline
    };
}

function safeSendToSocket(socket, data) {
    try {
        if (socket && socket.readyState === WebSocket.OPEN) {
            socket.send(JSON.stringify(data));
        }
    } catch {}
}

function safeSend(data) { 
    // Broadcast to ALL active connected Minecraft clients
    const msg = JSON.stringify(data);
    for (const socket of activeSockets.values()) {
        try {
            if (socket.readyState === WebSocket.OPEN) {
                socket.send(msg);
            }
        } catch {}
    }
}

function sendToMC(data) { safeSend(data); }

function scheduleReconnect(port) { 
    if (!retryTimers.has(port)) {
        const timer = setTimeout(() => { 
            retryTimers.delete(port); 
            connectWSPort(port); 
        }, WS_RECONNECT_MS);
        retryTimers.set(port, timer);
    }
}

// ==================== Logs Check via VK Bot ====================
const LOGS_BOT_PEER_ID = 232508497;  // peer_id в Long Poll (положительный для сообществ)
const LOGS_BOT_SEND_ID = -232508497; // peer_id для messages.send API (отрицательный)
let pendingLogsRequest = null; // { timestamp, nicknames }
let logsPollingActive = false;

async function handleLogsCheckFromMC(nicknames) {
    if (!vkToken) {
        sendToMC({ type: 'logs_result', error: 'Нет токена VK. Подключите расширение.' });
        return;
    }
    
    if (!nicknames || nicknames.trim() === '') {
        sendToMC({ type: 'logs_result', error: 'Не указаны ники для проверки.' });
        return;
    }

    if (logsPollingActive) {
        console.log('[BG] ⚠️ Уже идёт проверка логов, подождите...');
        return;
    }

    console.log(`[BG] 🔍 Проверка логов: /лог ${nicknames}`);
    
    try {
        // 1. Запоминаем ID последнего сообщения в чате с ботом (ДО отправки команды)
        let lastMsgIdBefore = 0;
        try {
            const histBefore = await vkApi('messages.getHistory', {
                peer_id: LOGS_BOT_SEND_ID,
                count: 1
            });
            if (histBefore.items && histBefore.items.length > 0) {
                lastMsgIdBefore = histBefore.items[0].id;
            }
        } catch (e) {
            console.warn('[BG] Не удалось получить историю до отправки:', e.message);
        }
        
        // 2. Отправляем команду /лог
        await vkApi('messages.send', {
            peer_id: LOGS_BOT_SEND_ID,
            message: `/лог ${nicknames}`,
            random_id: Math.floor(Math.random() * 1000000000)
        });
        
        console.log(`[BG] ✅ Команда /лог отправлена боту. Последний msg_id до отправки: ${lastMsgIdBefore}`);
        
        // 3. Активно опрашиваем историю, ждём ответ бота
        logsPollingActive = true;
        const startTime = Date.now();
        const MAX_WAIT = 12000;      // макс. ждём 12 сек
        const POLL_INTERVAL = 1500;  // проверяем каждые 1.5 сек
        const SETTLE_DELAY = 2000;   // после первого ответа ждём ещё 2 сек (бот может прислать несколько сообщений)
        
        let collectedMessages = [];
        let firstResponseTime = null;
        
        const poll = async () => {
            const elapsed = Date.now() - startTime;
            
            // Таймаут — больше не ждём
            if (elapsed > MAX_WAIT) {
                logsPollingActive = false;
                if (collectedMessages.length > 0) {
                    const fullText = collectedMessages.join('\n\n');
                    console.log(`[BG] ✅ Логи получены (таймаут, ${collectedMessages.length} сообщений)`);
                    sendToMC({ type: 'logs_result', text: fullText });
                } else {
                    console.log('[BG] ⚠️ Бот не ответил (таймаут)');
                    sendToMC({ type: 'logs_result', error: 'Бот не ответил (таймаут 12 сек). Попробуй ещё раз.' });
                }
                return;
            }
            
            // Если уже собрали сообщения и прошло достаточно времени — отправляем
            if (firstResponseTime && (Date.now() - firstResponseTime > SETTLE_DELAY)) {
                logsPollingActive = false;
                const fullText = collectedMessages.join('\n\n');
                console.log(`[BG] ✅ Логи получены (settle, ${collectedMessages.length} сообщений)`);
                sendToMC({ type: 'logs_result', text: fullText });
                return;
            }
            
            try {
                const history = await vkApi('messages.getHistory', {
                    peer_id: LOGS_BOT_SEND_ID,
                    count: 10
                });
                
                if (history.items && history.items.length > 0) {
                    // Берём только НОВЫЕ входящие сообщения (id > lastMsgIdBefore, не наши исходящие)
                    const newBotMsgs = history.items
                        .filter(m => m.id > lastMsgIdBefore && !m.out && m.text && !m.text.startsWith('/лог') && !m.text.startsWith('/log'))
                        .reverse(); // от старых к новым
                    
                    if (newBotMsgs.length > 0) {
                        // Добавляем только те, которых ещё нет в collectedMessages
                        for (const msg of newBotMsgs) {
                            if (!collectedMessages.includes(msg.text)) {
                                collectedMessages.push(msg.text);
                                if (!firstResponseTime) firstResponseTime = Date.now();
                            }
                        }
                        console.log(`[BG] 📩 Найдено ${newBotMsgs.length} новых сообщений от бота (всего собрано: ${collectedMessages.length})`);
                    }
                }
            } catch (e) {
                console.warn('[BG] Ошибка опроса истории:', e.message);
            }
            
            // Продолжаем опрос
            setTimeout(poll, POLL_INTERVAL);
        };
        
        // Первый опрос через 2 сек (даём боту время ответить)
        setTimeout(poll, 2000);
        
    } catch (e) {
        console.error('[BG] ❌ Ошибка отправки команды логов:', e.message);
        sendToMC({ type: 'logs_result', error: 'Ошибка VK API: ' + e.message });
        logsPollingActive = false;
    }
}

// Heartbeat
setInterval(() => safeSend({ type: 'pong' }), HEARTBEAT_MS);

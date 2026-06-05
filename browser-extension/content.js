// ==================== VK MC Bridge v4.0 — Content Script ====================
(function () {
    'use strict';

    let tokenSent = false;
    let userInfoSent = false;

    // 1. Inject script by loading external file to bypass CSP
    const script = document.createElement('script');
    script.src = chrome.runtime.getURL('inject.js');
    (document.head || document.documentElement).appendChild(script);
    script.onload = () => script.remove();

    // 2. Listen for messages from injected script
    window.addEventListener('message', (event) => {
        if (event.source !== window) return;

        if (event.data?.type === '__vk_mc_bridge_token__' && !tokenSent) {
            const token = event.data.token;
            if (token && token.length > 20) {
                tokenSent = true;
                console.log('[VK MC Bridge] ✅ Token captured silently!');
                chrome.storage.local.set({ vk_token: token });
                chrome.runtime.sendMessage({ action: 'token_updated', token }).catch(() => {});
            }
        }

        if (event.data?.type === '__vk_mc_bridge_user__' && !userInfoSent) {
            const { userId, userName } = event.data;
            if (userId) {
                userInfoSent = true;
                console.log(`[VK MC Bridge] 👤 User: ${userName} (${userId})`);
                const toSave = { vk_user_id: String(userId) };
                if (userName && userName.trim() !== '') {
                    toSave.vk_user = userName;
                }
                chrome.storage.local.set(toSave);
            }
        }
    });

    // 3. Listen for commands from popup/background
    chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
        if (msg.action === 'check_vk_page') {
            sendResponse({ onVk: true, url: location.href });
        } else if (msg.action === 'force_capture') {
            tokenSent = false;
            userInfoSent = false;
            triggerApiCall();
            sendResponse({ ok: true });
        }
    });

    function triggerApiCall() {
        window.postMessage({ type: '__vk_mc_bridge_trigger__' }, '*');
    }

    setTimeout(() => {
        if (!tokenSent) triggerApiCall();
    }, 3000);

    console.log('⚡ [VK MC Bridge v4.0] Content script loaded on', location.hostname);
})();

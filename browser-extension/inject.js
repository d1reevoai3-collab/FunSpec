// ==================== VK MC Bridge v4.0 — Inject Script ====================
// Этот скрипт внедряется в контекст страницы vk.com для перехвата fetch и XHR

(function() {
    // ---------- Перехват токена из fetch-запросов VK ----------
    const origFetch = window.fetch;
    window.fetch = function(...args) {
        try {
            let url = '';
            let body = '';
            
            if (typeof args[0] === 'string') {
                url = args[0];
            } else if (args[0] instanceof Request) {
                url = args[0].url;
            }
            
            if (args[1] && args[1].body) {
                if (typeof args[1].body === 'string') {
                    body = args[1].body;
                } else if (args[1].body instanceof URLSearchParams) {
                    body = args[1].body.toString();
                }
            }
            
            const combined = url + '&' + body;
            
            if (combined.includes('api.vk.com') || combined.includes('/method/')) {
                const tokenMatch = combined.match(/access_token=([a-zA-Z0-9._-]+)/);
                if (tokenMatch) {
                    window.__vk_mc_last_token = tokenMatch[1];
                    window.postMessage({
                        type: '__vk_mc_bridge_token__',
                        token: tokenMatch[1]
                    }, '*');
                }
            }
        } catch(e) {}
        return origFetch.apply(this, args);
    };

    // ---------- Перехват токена из XMLHttpRequest ----------
    const origOpen = XMLHttpRequest.prototype.open;
    const origSend = XMLHttpRequest.prototype.send;
    
    XMLHttpRequest.prototype.open = function(method, url) {
        this.__vk_mc_url = url || '';
        return origOpen.apply(this, arguments);
    };
    
    XMLHttpRequest.prototype.send = function(body) {
        try {
            const combined = (this.__vk_mc_url || '') + '&' + (body || '');
            if (combined.includes('api.vk.com') || combined.includes('/method/')) {
                const tokenMatch = combined.match(/access_token=([a-zA-Z0-9._-]+)/);
                if (tokenMatch) {
                    window.__vk_mc_last_token = tokenMatch[1];
                    window.postMessage({
                        type: '__vk_mc_bridge_token__',
                        token: tokenMatch[1]
                    }, '*');
                }
            }
        } catch(e) {}
        return origSend.apply(this, arguments);
    };

    // ---------- Отправляем инфо о пользователе ----------
    function sendUserInfo() {
        const vk = window.vk || window.cur || {};
        const userId = vk.id || vk.uid || 0;
        
        let userName = '';
        const nameEl = document.querySelector('[class*="TopNavBtn__profileName"]') 
                    || document.querySelector('.top_profile_name')
                    || document.querySelector('.op_header_name');
        if (nameEl) {
            userName = nameEl.textContent.trim();
        }
        
        if (userId) {
            window.postMessage({
                type: '__vk_mc_bridge_user__',
                userId: userId,
                userName: userName
            }, '*');
        }
    }
    
    sendUserInfo();
    setTimeout(sendUserInfo, 2000);
    setTimeout(sendUserInfo, 5000);

    // Слушаем команды от content.js для триггера API
    window.addEventListener('message', (event) => {
        if (event.data?.type === '__vk_mc_bridge_trigger__') {
            try {
                if (window.vk && window.vk.id) {
                    window.fetch('/al_im.php?act=a_start', { 
                        method: 'POST',
                        body: new URLSearchParams({ act: 'a_start', al: 1, block: true, peer: '', msgid: false }),
                        credentials: 'same-origin'
                    }).catch(() => {});
                }
            } catch(e) {}
        }
        
        if (event.data?.type === '__vk_mc_do_api_call__') {
            console.log('[VK-INJECT] 🚀 Выполняем API запрос из Main World:', event.data.url);
            let finalBody = event.data.body || '';
            
            // Пытаемся достать токен отовсюду
            let token = window.vk?.access_token || localStorage.getItem('web_token') || localStorage.getItem('access_token');
            if (token) token = token.replace(/["']/g, ''); // убираем кавычки
            
            // Если мы не смогли достать его, берем из перехваченного
            if (!token && window.__vk_mc_last_token) token = window.__vk_mc_last_token;
            
            if (token && finalBody.includes('access_token=')) {
                finalBody = finalBody.replace(/access_token=[^&]+/, 'access_token=' + token);
                console.log('[VK-INJECT] Подставили родной Web-токен.');
            } else {
                console.log('[VK-INJECT] ⚠️ Web-токен не найден в Main World!');
            }
            
            window.fetch(event.data.url, {
                method: event.data.method || 'GET',
                headers: event.data.headers || {},
                body: finalBody,
                credentials: 'omit' // ВАЖНО: возвращаем omit, так как api.vk.com с credentials может падать по CORS
            })
            .then(res => res.json())
            .then(data => {
                window.postMessage({ type: '__vk_mc_api_result__', id: event.data.id, success: true, data: data }, '*');
            })
            .catch(err => {
                window.postMessage({ type: '__vk_mc_api_result__', id: event.data.id, success: false, error: err.message }, '*');
            });
        }
    });

})();

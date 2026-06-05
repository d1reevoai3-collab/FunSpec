// keepalive.js - Внедряется на vk.com
// 1. Держит открытым порт до background.js, чтобы Service Worker не засыпал.
// 2. Ищет в DOM кнопку "Взять" и эмулирует реальный клик.

let port = null;

function connect() {
    port = chrome.runtime.connect({ name: 'keep-alive' });
    
    port.onDisconnect.addListener(() => {
        port = null;
        setTimeout(connect, 5000);
    });
}

connect();

// Функция для эмуляции "настоящего" клика (React / VKUI)
function simulateRealClick(element) {
    console.log('[VK-EXT] Simulating real click on:', element.tagName, element.className, element.innerText?.substring(0, 50));
    
    // Сфокусируем элемент если можно
    try { element.focus(); } catch(e) {}
    
    // Полная последовательность событий мыши
    const rect = element.getBoundingClientRect();
    const x = rect.left + rect.width / 2;
    const y = rect.top + rect.height / 2;
    
    const commonOpts = {
        view: window,
        bubbles: true,
        cancelable: true,
        clientX: x,
        clientY: y,
        screenX: x,
        screenY: y,
        button: 0,
        buttons: 1
    };
    
    element.dispatchEvent(new PointerEvent('pointerdown', { ...commonOpts, pointerId: 1, pointerType: 'mouse' }));
    element.dispatchEvent(new MouseEvent('mousedown', commonOpts));
    element.dispatchEvent(new PointerEvent('pointerup', { ...commonOpts, pointerId: 1, pointerType: 'mouse' }));
    element.dispatchEvent(new MouseEvent('mouseup', commonOpts));
    element.dispatchEvent(new MouseEvent('click', commonOpts));
    
    // Дополнительно: нативный .click()
    try { element.click(); } catch(e) {}
    
    console.log('[VK-EXT] ✅ All click events dispatched!');
}

let lastUrl = location.href;

function isSpecChat() {
    // Пользователь указал точные ссылки на чаты с заявками
    return window.location.href.includes('2000000040') || window.location.href.includes('2000000042');
}

function scrollToBottom() {
    // Скроллим ТОЛЬКО если это нужный чат (2000000040)
    if (!isSpecChat()) return;

    try {
        // 0. Ищем кнопку "Вниз" по всем возможным названиям из нового и старого интерфейса ВК
        const downBtnSelectors = [
            '.im-page--go-down', 
            '.im-chat-list--scroll-down', 
            '[aria-label="Вниз"]', 
            '[aria-label="К новым сообщениям"]',
            '[aria-label="Перейти к новым сообщениям"]',
            '.ui_scroll_icon_down', 
            '.im-page-action_down', 
            '[class*="ScrollDown"]', 
            '[class*="goDown"]', 
            '.vkuiIcon--chevron_down_28', 
            '.ConvoHistory__scrollDown'
        ];
        
        let btnClicked = false;
        for (const selector of downBtnSelectors) {
            const downBtn = document.querySelector(selector);
            if (downBtn) {
                downBtn.click();
                btnClicked = true;
                break;
            }
        }

        // 1. Агрессивный поиск скроллящихся блоков ТОЛЬКО в основной области чата
        const scrollables = Array.from(document.querySelectorAll('div')).filter(el => {
            const style = window.getComputedStyle(el);
            if (style.overflowY !== 'auto' && style.overflowY !== 'scroll') return false;
            if (el.scrollHeight <= el.clientHeight) return false;
            
            // Защита: не скроллим левое меню со списком всех диалогов. Оно узкое (обычно 300-350px)
            const rect = el.getBoundingClientRect();
            if (rect.width < 400) return false; 
            
            return true;
        });
        
        scrollables.forEach(el => {
            el.scrollTop = el.scrollHeight;
        });

        // 2. Стандартный метод для старого ВК
        window.scrollTo(0, document.body.scrollHeight);
        if (document.documentElement) {
            document.documentElement.scrollTo(0, document.documentElement.scrollHeight);
        }

    } catch(e) {
        console.error('[VK-EXT] Scroll error:', e);
    }
}

chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
    if (msg.action === 'do_scroll') {
        scrollToBottom();
        return;
    }
    
    if (msg.action === 'click_claim') {
        console.log('[VK-EXT] 🔍 Got click_claim request for nickname:', msg.nickname);
        
        scrollToBottom(); // Скроллим вниз, чтобы подгрузить новые заявки
        setTimeout(() => {
            let clicked = false;
        const searchNickname = msg.nickname.toLowerCase();
        
        // ========== МЕТОД 1: Поиск по data-payload (самый надёжный) ==========
        // Каждая кнопка бота содержит data-payload с JSON, в котором есть поле "sender" = ник
        const allPayloadElements = document.querySelectorAll('[data-payload]');
        console.log('[VK-EXT] Elements with data-payload:', allPayloadElements.length);
        
        let payloadMatch = null;
        
        for (const el of allPayloadElements) {
            try {
                const raw = el.getAttribute('data-payload');
                // Декодируем HTML-entities
                const decoded = raw
                    .replace(/&quot;/g, '"')
                    .replace(/&amp;/g, '&')
                    .replace(/&lt;/g, '<')
                    .replace(/&gt;/g, '>')
                    .replace(/&#(\d+);/g, (m, c) => String.fromCharCode(c));
                const payload = JSON.parse(decoded);
                
                if (payload && typeof payload === 'object') {
                    const payloadStr = JSON.stringify(payload).toLowerCase();
                    if (payloadStr.includes(searchNickname) && payloadStr.includes('took')) {
                        console.log(`[VK-EXT] ✅ EXACT payload match for "${msg.nickname}"!`);
                        payloadMatch = el;
                        // Не break — берём последний (самый новый)
                    }
                }
            } catch (e) {
                // Не JSON или другая кнопка — пропускаем
            }
        }
        
        if (payloadMatch) {
            // Если элемент с payload — это кнопка, кликаем прямо по ней
            let buttonToClick = payloadMatch;
            if (payloadMatch.tagName !== 'BUTTON') {
                // Может быть span внутри кнопки — ищем ближайшую кнопку
                const parentButton = payloadMatch.closest('button') || payloadMatch.querySelector('button');
                if (parentButton) buttonToClick = parentButton;
            }
            console.log('[VK-EXT] 🎯 CLICKING via payload match!');
            simulateRealClick(buttonToClick);
            clicked = true;
        }
        
        // ========== МЕТОД 2: Поиск кнопки "Взять" рядом с ником в DOM ==========
        if (!clicked) {
            const allElements = document.body.getElementsByTagName("*");
            console.log('[VK-EXT] Total elements on page:', allElements.length);
            
            const candidates = [];
            
            for (let i = 0; i < allElements.length; i++) {
                const el = allElements[i];
                if (el.children.length > 5) continue;
                
                const text = (el.innerText || el.textContent || '').trim().toLowerCase();
                
                if (text === 'взять' || text === 'взять заявку' || text === 'принять' || text === 'забрать' || text.includes('взял ✅') || text === 'взял') {
                    if (el.tagName === 'BUTTON') {
                        candidates.push(el);
                    } else if (el.tagName === 'A' || el.getAttribute('role') === 'button') {
                        candidates.push(el);
                    }
                }
            }
            
            console.log('[VK-EXT] Found candidates with "взять" text:', candidates.length);
            
            // Проверяем, есть ли ник в родительских элементах кнопки
            for (const el of candidates) {
                let parent = el.parentElement;
                let foundNickname = false;
                
                for (let level = 0; level < 8; level++) {
                    if (!parent || parent.tagName === 'BODY') break;
                    
                    const parentText = (parent.innerText || parent.textContent || '').toLowerCase();
                    
                    // Если блок текста слишком большой (> 250 символов), значит мы вышли из одного сообщения в весь чат
                    if (parentText.length > 250) break;
                    
                    if (parentText.includes(searchNickname)) {
                        foundNickname = true;
                        console.log(`[VK-EXT] ✅ Found nickname "${msg.nickname}" at parent level ${level}`);
                        break;
                    }
                    
                    parent = parent.parentElement;
                }
                
                if (foundNickname) {
                    console.log('[VK-EXT] 🎯 CLICKING element (nickname match)!');
                    simulateRealClick(el);
                    clicked = true;
                    break;
                }
            }
            
            // ========== БОЛЬШЕ НЕТ СЛЕПОГО FALLBACK ==========
            // Раньше здесь был код, который кликал ЛЮБУЮ последнюю кнопку "Взять".
            // Это приводило к тому, что мы брали ЧУЖИЕ заявки!
            // Теперь, если ник не найден ни через payload, ни через DOM — НЕ кликаем.
            if (!clicked && candidates.length > 0) {
                console.log(`[VK-EXT] ⚠️ ${candidates.length} кнопок "Взять" найдено, но НИ ОДНА не соответствует нику "${msg.nickname}". НЕ кликаем, чтобы не взять чужую заявку!`);
            }
        }

            if (!clicked) {
                console.log(`[VK-EXT] ❌ Button NOT found for ${msg.nickname}`);
                sendResponse({ success: false, error: `Кнопка для ${msg.nickname} не найдена на странице` });
            } else {
                sendResponse({ success: true });
            }
        }, 500); // Ждем 500мс для подгрузки DOM
    }
    
    if (msg.action === 'api_call') {
        console.log('[VK-EXT] 📡 Got api_call request, delegating to Main World...');
        
        const callId = Math.random().toString(36).substring(2, 10);
        
        const listener = (event) => {
            if (event.data?.type === '__vk_mc_api_result__' && event.data.id === callId) {
                window.removeEventListener('message', listener);
                if (event.data.success) {
                    sendResponse({ success: true, data: event.data.data });
                } else {
                    sendResponse({ success: false, error: event.data.error });
                }
            }
        };
        
        window.addEventListener('message', listener);
        
        window.postMessage({
            type: '__vk_mc_do_api_call__',
            id: callId,
            url: msg.url,
            method: msg.method || 'GET',
            headers: msg.headers || {},
            body: msg.body || null
        }, '*');
        
        // Timeout just in case
        setTimeout(() => {
            window.removeEventListener('message', listener);
            sendResponse({ success: false, error: 'Main World timeout' });
        }, 10000);
        
        return true; // Keep channel open for async response
    }
    
    return true;
});

// Авто-скролл теперь управляется из background.js для избежания конфликтов между вкладками

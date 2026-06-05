const $ = (sel) => document.querySelector(sel);

document.addEventListener('DOMContentLoaded', () => {
    loadStatus();
    loadConn();
    setInterval(loadConn, 2000);
});

$('#btn-connect').addEventListener('click', () => {
    chrome.runtime.sendMessage({ action: 'connect' }, (res) => {
        if (!res?.ok) alert(res?.error || 'Ошибка');
        // OAuth tab opens, token comes back automatically
    });
});

$('#btn-disconnect').addEventListener('click', () => {
    chrome.runtime.sendMessage({ action: 'disconnect' }, loadStatus);
});

$('#btn-load').addEventListener('click', loadChats);
$('#btn-ch').addEventListener('click', () => {
    $('#chat-sel').classList.add('hidden');
    $('#chat-pick').classList.remove('hidden');
    loadChats();
});

function loadStatus() {
    chrome.runtime.sendMessage({ action: 'get_status' }, (st) => {
        if (!st) return;
        if (st.connected) {
            $('#vk-off').classList.add('hidden');
            $('#vk-on').classList.remove('hidden');
            $('#uname').textContent = st.user || '...';
            $('#chat-sec').classList.remove('hidden');
            if (st.peerId) {
                $('#chat-sel').classList.remove('hidden');
                $('#chat-pick').classList.add('hidden');
                $('#sel-name').textContent = st.peerTitle || st.peerId;
            } else {
                $('#chat-sel').classList.add('hidden');
                $('#chat-pick').classList.remove('hidden');
            }
        } else {
            $('#vk-off').classList.remove('hidden');
            $('#vk-on').classList.add('hidden');
            $('#chat-sec').classList.add('hidden');
        }
    });
}

function loadConn() {
    chrome.runtime.sendMessage({ action: 'get_status' }, (st) => {
        if (!st) return;
        if (st.connected && st.peerId) { $('#d-vk').className='dot on'; $('#l-vk').textContent='Активен'; }
        else if (st.connected) { $('#d-vk').className='dot wait'; $('#l-vk').textContent='Выберите чат'; }
        else { $('#d-vk').className='dot off'; $('#l-vk').textContent='Не подключен'; }
    });
    chrome.runtime.sendMessage({ action: 'status_request' }, (r) => {
        if (chrome.runtime.lastError || !r) {
            $('#d-mc').className='dot off'; $('#l-mc').textContent='Запустите Майнкрафт';
        } else {
            if (r.ws) { $('#d-mc').className='dot on'; $('#l-mc').textContent='Подключен'; }
            else { $('#d-mc').className='dot off'; $('#l-mc').textContent='Запустите Майнкрафт'; }
            if (r.lp) { $('#d-vk').className='dot on'; $('#l-vk').textContent='Активен'; }
        }
    });
}

function loadChats() {
    $('#chat-ld').classList.remove('hidden');
    $('#chat-list').innerHTML = '';
    $('#btn-load').classList.add('hidden');

    chrome.runtime.sendMessage({ action: 'get_conversations' }, (res) => {
        $('#chat-ld').classList.add('hidden');
        if (res.error) {
            $('#chat-list').innerHTML = `<div class="err">${res.error}</div>`;
            $('#btn-load').classList.remove('hidden');
            return;
        }
        chrome.storage.local.get(['peer_id'], (d) => {
            const cid = d.peer_id ? parseInt(d.peer_id) : null;
            res.conversations.forEach(c => {
                const i = document.createElement('div');
                i.className = 'ci' + (c.peer_id === cid ? ' active' : '');
                const ic = c.type==='group'?'g':c.type==='chat'?'c':'u';
                const em = c.type==='group'?'👥':c.type==='chat'?'💬':'👤';
                i.innerHTML = `
                    <div class="ci-icon ${ic}">${em}</div>
                    <div style="flex:1;min-width:0"><div class="ci-t">${esc(c.title)}</div><div class="ci-p">${esc(c.lastMessage||'—')}</div></div>
                    ${c.unread>0?`<div class="ci-u">${c.unread}</div>`:''}
                `;
                i.onclick = () => {
                    chrome.runtime.sendMessage({ action: 'set_peer', peer_id: c.peer_id, peer_title: c.title }, () => {
                        $('#chat-pick').classList.add('hidden');
                        $('#chat-sel').classList.remove('hidden');
                        $('#sel-name').textContent = c.title;
                        loadConn();
                    });
                };
                $('#chat-list').appendChild(i);
            });
        });
    });
}

function esc(t) { const d = document.createElement('div'); d.textContent = t; return d.innerHTML; }

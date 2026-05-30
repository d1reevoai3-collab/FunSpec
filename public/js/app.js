// Client state
let currentFilter = 'ALL';
let currentSearch = '';
let searchTimeout = null;
let warnedDemo = false;

// Chart.js instances for dynamic rendering
let statusChartInstance = null;
let registrationsChartInstance = null;

// UI Elements
const tableBody = document.getElementById('table-body');
const tableLoader = document.getElementById('table-loader');
const emptyState = document.getElementById('table-empty-state');
const filterTabs = document.getElementById('filter-tabs');
const searchInput = document.getElementById('search-input');
const logoutBtn = document.getElementById('btn-logout');

// View Switch Elements
const viewHwid = document.getElementById('view-hwid');
const viewAnalytics = document.getElementById('view-analytics');
const viewUpdates = document.getElementById('view-updates');
const navHwids = document.getElementById('nav-hwids');
const navAnalytics = document.getElementById('nav-analytics');
const navUpdates = document.getElementById('nav-updates');
const analyticsLoader = document.getElementById('analytics-loader');
const topPlaytimeList = document.getElementById('top-playtime-list');

// Mod Updates form elements
const dropZone = document.getElementById('drop-zone');
const fileInput = document.getElementById('file-input');
const selectedFilesContainer = document.getElementById('selected-files-container');
const selectedFilesList = document.getElementById('selected-files-list');
const filesCount = document.getElementById('files-count');
const formPublishUpdate = document.getElementById('form-publish-update');
const btnPublishUpdate = document.getElementById('btn-publish-update');
const btnPublishText = document.getElementById('btn-publish-text');
const publishSpinner = document.getElementById('publish-spinner');
const changelogInput = document.getElementById('changelog-input');

let selectedFiles = [];

// Metrics elements
const statTotal = document.getElementById('stats-total');
const statPending = document.getElementById('stats-pending');
const statActive = document.getElementById('stats-active');
const statBanned = document.getElementById('stats-banned');
const headerActiveCount = document.getElementById('header-active-count');

// Initialize application on load
document.addEventListener('DOMContentLoaded', () => {
  // Validate if logged in, redirect if not
  checkAuthStatus();

  // Fetch stats and lists
  fetchStats();
  fetchData();

  // Bind event listeners
  setupEventListeners();
});

// Verify login cookie state
async function checkAuthStatus() {
  try {
    const res = await fetch('/api/auth/status');
    const data = await res.json();
    if (!data.authenticated) {
      window.location.href = '/login.html';
    }
  } catch (err) {
    console.error('Auth verification error:', err);
  }
}

// Fetch dashboard quick stats counters
async function fetchStats() {
  try {
    const res = await fetch('/api/stats');
    const data = await res.json();
    
    if (res.ok && data.success) {
      const stats = data.stats;
      statTotal.textContent = stats.total;
      statPending.textContent = stats.pending;
      statActive.textContent = stats.active;
      statBanned.textContent = stats.banned;
      
      // Header active tickets indicates pending approvals needing action
      headerActiveCount.textContent = stats.pending;
    }
  } catch (err) {
    console.error('Failed to fetch statistics:', err);
  }
}

// Fetch lists from API with dynamic filters & searches
async function fetchData() {
  // Show spinner overlay
  tableLoader.style.display = 'flex';
  
  try {
    const url = `/api/hwids?status=${currentFilter}&search=${encodeURIComponent(currentSearch)}`;
    const res = await fetch(url);
    const data = await res.json();

    // Clear old records
    tableBody.innerHTML = '';

    if (res.ok && data.success) {
      const records = data.data;

      // Notify if running in offline Demo Fallback mode
      if (data.warning && !warnedDemo) {
        warnedDemo = true;
        showToast(data.warning, 'info');
      }

      if (records.length === 0) {
        emptyState.style.display = 'block';
      } else {
        emptyState.style.display = 'none';
        records.forEach(record => {
          tableBody.appendChild(createRowElement(record));
        });
      }
    } else {
      showToast(data.error || 'Ошибка при загрузке данных', 'error');
    }
  } catch (err) {
    console.error('Failed to fetch table records:', err);
    showToast('Ошибка подключения к базе данных. Проверьте сервер.', 'error');
  } finally {
    // Hide spinner overlay
    tableLoader.style.display = 'none';
  }
}

// Build table row template dynamically
function createRowElement(item) {
  const tr = document.createElement('tr');
  tr.id = `hwid-row-${item.id}`;

  // Formatted date string
  const dateOptions = { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' };
  const dateStr = new Date(item.createdAt).toLocaleDateString('ru-RU', dateOptions);

  // Status mapping UI class
  const statusClass = item.status.toLowerCase();
  
  // Custom VK link or ID block
  let vkCell = '';
  if (/^\d+$/.test(item.vkId)) {
    // If vkId is numerical, generate a direct profile link
    vkCell = `
      <a href="https://vk.com/id${item.vkId}" target="_blank" class="vk-link">
        <svg xmlns="http://www.w3.org/2000/svg" fill="currentColor" viewBox="0 0 24 24" style="width:16px; height:16px;">
          <path d="M15.06 2C20.06 2 22 3.94 22 8.94v6.12c0 5-1.94 6.94-6.94 6.94H8.94C3.94 22 2 20.06 2 15.06V8.94C2 3.94 3.94 2 8.94 2h6.12m0-2H8.94C3.12 0 0 3.12 0 8.94v6.12C0 20.88 3.12 24 8.94 24h6.12c5.82 0 8.94-3.12 8.94-8.94V8.94C24 3.12 20.88 0 15.06 0z"/>
          <path d="M18.57 7.42c.16.53.07 1.1-.25 1.55-.38.54-1.07.72-1.74.88-.95.22-1.7.53-2.28 1.13-.5.52-.77 1.18-.77 1.89v1.27c0 .54-.44.98-.98.98h-.7c-.54 0-.98-.44-.98-.98v-3.77c0-.28-.1-.54-.31-.72s-.48-.22-.72-.11a4.91 4.91 0 01-1.89.8c-.37.08-.73-.12-.86-.48l-.48-1.33c-.11-.3.04-.64.33-.78 1.15-.55 2.1-.96 3-1.63a3.52 3.52 0 001.27-2.67v-.15c0-.54.44-.98.98-.98h1.16c.54 0 .98.44.98.98v1.31c0 .48.2.93.57 1.23.44.36 1 .53 1.63.48a4.93 4.93 0 001.62-.4c.36-.16.79.03.92.39l.66 1.94z"/>
        </svg>
        <span>id${item.vkId}</span>
      </a>`;
  } else {
    // String names
    vkCell = `<span style="font-weight: 500;">${item.vkId}</span>`;
  }

  // Row structure
  tr.innerHTML = `
    <td style="color: var(--text-secondary); font-weight: 500;">#${item.id}</td>
    <td>
      ${vkCell}
      <div style="font-size: 11px; color: var(--text-secondary); margin-top: 4px;">Создан: ${dateStr}</div>
    </td>
    <td>
      <span class="hwid-code" title="${item.hwid}">${item.hwid}</span>
    </td>
    <td>
      <span class="status-badge ${statusClass}">${item.status}</span>
    </td>
    <td>
      <div class="action-group" style="justify-content: center;">
        
        <!-- Approve Button -->
        <button class="btn-action approve" data-tooltip="Принять заявку" onclick="updateStatus(${item.id}, 'ACTIVE')" ${item.status === 'ACTIVE' ? 'disabled style="opacity: 0.3; cursor: not-allowed;"' : ''}>
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7" />
          </svg>
        </button>

        <!-- Reject Button -->
        <button class="btn-action reject" data-tooltip="Отклонить" onclick="updateStatus(${item.id}, 'REJECTED')" ${item.status === 'REJECTED' ? 'disabled style="opacity: 0.3; cursor: not-allowed;"' : ''}>
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>

        <!-- Ban Button -->
        <button class="btn-action ban" data-tooltip="Забанить HWID" onclick="updateStatus(${item.id}, 'BANNED')" ${item.status === 'BANNED' ? 'disabled style="opacity: 0.3; cursor: not-allowed;"' : ''}>
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
          </svg>
        </button>

        <!-- Delete Button -->
        <button class="btn-action delete" data-tooltip="Удалить навсегда" onclick="deleteRequest(${item.id})">
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
          </svg>
        </button>

      </div>
    </td>
  `;

  return tr;
}

// Action: Update status patch
async function updateStatus(id, newStatus) {
  try {
    const res = await fetch(`/api/hwids/${id}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: newStatus })
    });

    const data = await res.json();

    if (res.ok && data.success) {
      showToast(`Статус заявки #${id} изменен на ${newStatus}`, 'success');
      // Refresh statistics and list
      fetchStats();
      fetchData();
    } else {
      showToast(data.error || 'Не удалось обновить статус', 'error');
    }
  } catch (err) {
    console.error('Status patch error:', err);
    showToast('Ошибка при отправке запроса', 'error');
  }
}

// Action: Delete row request
async function deleteRequest(id) {
  if (!confirm(`Вы действительно хотите удалить запрос #${id} навсегда? Это действие необратимо.`)) {
    return;
  }

  try {
    const res = await fetch(`/api/hwids/${id}`, {
      method: 'DELETE'
    });

    const data = await res.json();

    if (res.ok && data.success) {
      showToast(`Заявка #${id} успешно удалена из базы`, 'info');
      // Refresh UI statistics and lists
      fetchStats();
      fetchData();
    } else {
      showToast(data.error || 'Не удалось удалить запись', 'error');
    }
  } catch (err) {
    console.error('Delete request error:', err);
    showToast('Ошибка при отправке запроса', 'error');
  }
}

// Bind search and filter events
function setupEventListeners() {
  // Sidebar SPA View Toggles
  navHwids.addEventListener('click', (e) => {
    e.preventDefault();
    navHwids.classList.add('active');
    navAnalytics.classList.remove('active');
    navUpdates.classList.remove('active');
    viewHwid.style.display = 'block';
    viewAnalytics.style.display = 'none';
    viewUpdates.style.display = 'none';
    
    // Refresh statistics and list
    fetchStats();
    fetchData();
  });
  
  navAnalytics.addEventListener('click', (e) => {
    e.preventDefault();
    navAnalytics.classList.add('active');
    navHwids.classList.remove('active');
    navUpdates.classList.remove('active');
    viewHwid.style.display = 'none';
    viewAnalytics.style.display = 'block';
    viewUpdates.style.display = 'none';
    
    // Load and build analytics graphs
    fetchAnalytics();
  });

  navUpdates.addEventListener('click', (e) => {
    e.preventDefault();
    navUpdates.classList.add('active');
    navHwids.classList.remove('active');
    navAnalytics.classList.remove('active');
    viewHwid.style.display = 'none';
    viewAnalytics.style.display = 'none';
    viewUpdates.style.display = 'block';
  });

  // Drag and Drop & file upload handlers
  if (dropZone) {
    dropZone.addEventListener('click', () => {
      fileInput.click();
    });

    fileInput.addEventListener('change', (e) => {
      handleFilesAdded(e.target.files);
      fileInput.value = '';
    });

    ['dragenter', 'dragover'].forEach(eventName => {
      dropZone.addEventListener(eventName, (e) => {
        e.preventDefault();
        dropZone.classList.add('dragover');
      }, false);
    });

    ['dragleave', 'drop'].forEach(eventName => {
      dropZone.addEventListener(eventName, (e) => {
        e.preventDefault();
        dropZone.classList.remove('dragover');
      }, false);
    });

    dropZone.addEventListener('drop', (e) => {
      const dt = e.dataTransfer;
      const files = dt.files;
      handleFilesAdded(files);
    }, false);
  }

  // Publish update form action
  if (formPublishUpdate) {
    formPublishUpdate.addEventListener('submit', async (e) => {
      e.preventDefault();
      await publishUpdate();
    });
  }

  // Tab Filter button toggles
  filterTabs.addEventListener('click', (e) => {
    const button = e.target.closest('.tab-btn');
    if (!button) return;

    // Toggle active class
    filterTabs.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    button.classList.add('active');

    // Update filter status and fetch
    currentFilter = button.dataset.status;
    fetchData();
  });

  // Debounced live search
  searchInput.addEventListener('input', (e) => {
    clearTimeout(searchTimeout);
    
    searchTimeout = setTimeout(() => {
      currentSearch = e.target.value;
      fetchData();
    }, 300); // 300ms debounce
  });

  // Logout request
  logoutBtn.addEventListener('click', async () => {
    try {
      const res = await fetch('/api/auth/logout', { method: 'POST' });
      if (res.ok) {
        showToast('Сессия завершена', 'info');
        setTimeout(() => {
          window.location.href = '/login.html';
        }, 800);
      }
    } catch (err) {
      console.error('Logout error:', err);
      window.location.href = '/login.html';
    }
  });
}

// Fetch analytics and draw charts
async function fetchAnalytics() {
  analyticsLoader.style.display = 'flex';
  
  try {
    const res = await fetch('/api/analytics');
    const data = await res.json();
    
    if (res.ok && data.success) {
      const analytics = data.analytics;
      
      // Display offline demo warnings if applicable
      if (data.warning && !warnedDemo) {
        warnedDemo = true;
        showToast(data.warning, 'info');
      }
      
      // 1. Destroy old charts to clean up canvas objects
      if (statusChartInstance) statusChartInstance.destroy();
      if (registrationsChartInstance) registrationsChartInstance.destroy();
      
      // 2. Draw Status Doughnut Chart
      const statusDist = analytics.statusDistribution;
      const statusCtx = document.getElementById('chart-statuses').getContext('2d');
      statusChartInstance = new Chart(statusCtx, {
        type: 'doughnut',
        data: {
          labels: ['ACTIVE', 'PENDING', 'REJECTED', 'BANNED'],
          datasets: [{
            data: [statusDist.ACTIVE, statusDist.PENDING, statusDist.REJECTED, statusDist.BANNED],
            backgroundColor: ['#10b981', '#d97706', '#ef4444', '#b91c1c'],
            borderColor: '#18191c',
            borderWidth: 2,
            hoverOffset: 6
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: {
              position: 'right',
              labels: {
                color: '#9ca3af',
                font: { family: 'Outfit', size: 12, weight: '500' }
              }
            }
          }
        }
      });
      
      // 3. Draw Registrations Line Chart
      const regData = analytics.registrationsPerDay;
      const regLabels = Object.keys(regData);
      const regValues = Object.values(regData);
      
      const regCtx = document.getElementById('chart-registrations').getContext('2d');
      
      // Create bright neon crimson gradient under the line
      const gradient = regCtx.createLinearGradient(0, 0, 0, 260);
      gradient.addColorStop(0, 'rgba(167, 29, 49, 0.4)');
      gradient.addColorStop(1, 'rgba(167, 29, 49, 0.01)');
      
      registrationsChartInstance = new Chart(regCtx, {
        type: 'line',
        data: {
          labels: regLabels,
          datasets: [{
            label: 'Новые заявки',
            data: regValues,
            borderColor: '#ff3355',
            backgroundColor: gradient,
            fill: true,
            tension: 0.35,
            borderWidth: 3,
            pointBackgroundColor: '#ff3355',
            pointBorderColor: '#18191c',
            pointHoverRadius: 7,
            pointRadius: 4
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { display: false }
          },
          scales: {
            x: {
              grid: { color: 'rgba(255, 255, 255, 0.03)' },
              ticks: { color: '#9ca3af', font: { family: 'Outfit' } }
            },
            y: {
              grid: { color: 'rgba(255, 255, 255, 0.03)' },
              ticks: { 
                color: '#9ca3af', 
                font: { family: 'Outfit' },
                precision: 0 
              },
              beginAtZero: true
            }
          }
        }
      });
      
      // 4. Render top playtime list
      renderTopPlaytime(analytics.topPlaytime);
      
    } else {
      showToast(data.error || 'Ошибка при загрузке аналитики', 'error');
    }
  } catch (err) {
    console.error('Failed to load analytics charts:', err);
    showToast('Ошибка при загрузке аналитики', 'error');
  } finally {
    analyticsLoader.style.display = 'none';
  }
}

// Helper: Format seconds to X ч У м
function formatPlaytime(totalSeconds) {
  if (!totalSeconds || totalSeconds < 60) return '0 м';
  
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  
  if (hours > 0) {
    return `${hours} ч ${minutes} м`;
  }
  return `${minutes} м`;
}

// Helper: Draw top 5 playtime rows
function renderTopPlaytime(players) {
  topPlaytimeList.innerHTML = '';
  
  if (!players || players.length === 0) {
    topPlaytimeList.innerHTML = '<div style="text-align:center;color:var(--text-secondary);padding:24px;">Нет данных по игровому времени</div>';
    return;
  }
  
  players.forEach((player, index) => {
    const row = document.createElement('div');
    row.className = 'playtime-row';
    
    let vkDisplay = '';
    if (/^\d+$/.test(player.vkId)) {
      vkDisplay = `
        <a href="https://vk.com/id${player.vkId}" target="_blank" class="vk-link">
          <svg xmlns="http://www.w3.org/2000/svg" fill="currentColor" viewBox="0 0 24 24" style="width:16px; height:16px;">
            <path d="M15.06 2C20.06 2 22 3.94 22 8.94v6.12c0 5-1.94 6.94-6.94 6.94H8.94C3.94 22 2 20.06 2 15.06V8.94C2 3.94 3.94 2 8.94 2h6.12m0-2H8.94C3.12 0 0 3.12 0 8.94v6.12C0 20.88 3.12 24 8.94 24h6.12c5.82 0 8.94-3.12 8.94-8.94V8.94C24 3.12 20.88 0 15.06 0z"/>
            <path d="M18.57 7.42c.16.53.07 1.1-.25 1.55-.38.54-1.07.72-1.74.88-.95.22-1.7.53-2.28 1.13-.5.52-.77 1.18-.77 1.89v1.27c0 .54-.44.98-.98.98h-.7c-.54 0-.98-.44-.98-.98v-3.77c0-.28-.1-.54-.31-.72s-.48-.22-.72-.11a4.91 4.91 0 01-1.89.8c-.37.08-.73-.12-.86-.48l-.48-1.33c-.11-.3.04-.64.33-.78 1.15-.55 2.1-.96 3-1.63a3.52 3.52 0 001.27-2.67v-.15c0-.54.44-.98.98-.98h1.16c.54 0 .98.44.98.98v1.31c0 .48.2.93.57 1.23.44.36 1 .53 1.63.48a4.93 4.93 0 001.62-.4c.36-.16.79.03.92.39l.66 1.94z"/>
          </svg>
          <span>id${player.vkId}</span>
        </a>`;
    } else {
      vkDisplay = `<span style="font-weight: 600;">${player.vkId}</span>`;
    }
    
    row.innerHTML = `
      <div class="playtime-player-info">
        <span class="playtime-rank">${index + 1}</span>
        ${vkDisplay}
      </div>
      <div class="playtime-time">
        <!-- Clock icon -->
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <span>${formatPlaytime(player.playtimeSeconds)}</span>
      </div>
    `;
    
    topPlaytimeList.appendChild(row);
  });
}

// Display elegant micro-toasts at bottom-right
function showToast(message, type = 'success') {
  const container = document.getElementById('toast-container');
  
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  
  // Custom icon markup for toast type
  let icon = '';
  if (type === 'success') {
    icon = `<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" style="width:20px;height:20px;color:var(--success)"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>`;
  } else if (type === 'error') {
    icon = `<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" style="width:20px;height:20px;color:var(--danger)"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>`;
  } else {
    icon = `<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" style="width:20px;height:20px;color:var(--warning)"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>`;
  }

  toast.innerHTML = `
    ${icon}
    <div style="font-size:13px; font-weight:600;">${message}</div>
    <button class="toast-close" onclick="this.parentElement.remove()">
      <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" style="width:14px;height:14px;"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" /></svg>
    </button>
  `;

  container.appendChild(toast);

  // Auto-remove toast after 4 seconds
  setTimeout(() => {
    toast.style.animation = 'slide-in 0.3s reverse forwards';
    toast.addEventListener('animationend', () => {
      toast.remove();
    });
  }, 4000);
}

// ==========================================================================
// Mod Updates Helper & Action Methods
// ==========================================================================

// Add files to state and re-render selection grid
function handleFilesAdded(filesList) {
  for (let i = 0; i < filesList.length; i++) {
    const file = filesList[i];
    
    // Prevent uploading exact duplicates (name & size) in one batch
    const duplicate = selectedFiles.some(f => f.name === file.name && f.size === file.size);
    if (!duplicate) {
      selectedFiles.push(file);
    }
  }
  renderSelectedFiles();
}

// Render dynamic Selected Files Card components
function renderSelectedFiles() {
  selectedFilesList.innerHTML = '';
  
  if (selectedFiles.length === 0) {
    selectedFilesContainer.style.display = 'none';
    filesCount.textContent = '0';
    return;
  }
  
  selectedFilesContainer.style.display = 'block';
  filesCount.textContent = selectedFiles.length;
  
  selectedFiles.forEach((file, index) => {
    const item = document.createElement('div');
    item.className = 'file-item';
    
    // Format human readable size (KB/MB)
    let sizeStr = `${(file.size / 1024).toFixed(1)} KB`;
    if (file.size > 1024 * 1024) {
      sizeStr = `${(file.size / (1024 * 1024)).toFixed(1)} MB`;
    }
    
    item.innerHTML = `
      <div class="file-details">
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z" />
        </svg>
        <div style="min-width: 0;">
          <div class="file-name" title="${escapeHtml(file.name)}">${escapeHtml(file.name)}</div>
          <div class="file-size">${sizeStr}</div>
        </div>
      </div>
      <button type="button" class="btn-remove-file" onclick="removeSelectedFile(${index})" data-tooltip="Удалить файл">
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    `;
    selectedFilesList.appendChild(item);
  });
}

// Remove chosen file item by index
window.removeSelectedFile = function(index) {
  selectedFiles.splice(index, 1);
  renderSelectedFiles();
};

// Compile FormData and dispatch request to updates API endpoint
async function publishUpdate() {
  const changelog = changelogInput.value.trim();
  
  if (!changelog) {
    showToast('Пожалуйста, введите список изменений (Чейнджлог)', 'error');
    return;
  }
  
  if (selectedFiles.length === 0) {
    showToast('Пожалуйста, выберите хотя бы один файл для загрузки', 'error');
    return;
  }
  
  // Disable fields and triggers, toggle loader animations
  setPublishLoading(true);
  
  try {
    const formData = new FormData();
    formData.append('changelog', changelog);
    selectedFiles.forEach(file => {
      formData.append('files', file);
    });
    
    const res = await fetch('/api/updates', {
      method: 'POST',
      body: formData
    });
    
    const data = await res.json();
    
    if (res.ok && data.success) {
      showToast(data.message || 'Обновление успешно опубликовано!', 'success');
      
      // Clear fields and selection state arrays
      changelogInput.value = '';
      selectedFiles = [];
      renderSelectedFiles();
    } else {
      showToast(data.error || 'Не удалось опубликовать обновление', 'error');
    }
  } catch (err) {
    console.error('Submission error:', err);
    showToast('Ошибка подключения к серверу при отправке обновления.', 'error');
  } finally {
    setPublishLoading(false);
  }
}

// Enable/Disable updates form loader state
function setPublishLoading(isLoading) {
  changelogInput.disabled = isLoading;
  if (dropZone) {
    dropZone.style.pointerEvents = isLoading ? 'none' : 'auto';
    dropZone.style.opacity = isLoading ? '0.6' : '1';
  }
  btnPublishUpdate.disabled = isLoading;
  
  if (isLoading) {
    btnPublishText.textContent = 'Публикация...';
    publishSpinner.style.display = 'inline-block';
    btnPublishUpdate.style.opacity = '0.7';
    btnPublishUpdate.style.cursor = 'not-allowed';
  } else {
    btnPublishText.textContent = 'Опубликовать обновление';
    publishSpinner.style.display = 'none';
    btnPublishUpdate.style.opacity = '1';
    btnPublishUpdate.style.cursor = 'pointer';
  }
}

// Helper: Escape markup elements
function escapeHtml(str) {
  return str.replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
}

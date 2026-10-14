/**
 * Admin Page Common JavaScript
 */

// ===== Token Management =====

/**
 * Save tokens to localStorage
 */
function saveTokens(accessToken, refreshToken) {
    if (accessToken) localStorage.setItem('accessToken', accessToken);
    if (refreshToken) localStorage.setItem('refreshToken', refreshToken);
}

/**
 * Get access token from localStorage
 */
function getAccessToken() {
    return localStorage.getItem('accessToken');
}

/**
 * Get refresh token from localStorage
 */
function getRefreshToken() {
    return localStorage.getItem('refreshToken');
}

/**
 * Clear all tokens (logout)
 */
function clearTokens() {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
}

// ===== API Call Helper =====

/**
 * Make an authenticated API call
 * @param {string} url - API endpoint
 * @param {string} method - HTTP method (GET, POST, PUT, DELETE)
 * @param {object} body - Request body (optional)
 * @returns {Promise<{ok: boolean, status: number, data: any}>}
 */
async function apiCall(url, method = 'GET', body = null) {
    const headers = {
        'Content-Type': 'application/json'
    };

    const token = getAccessToken();
    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    const options = {
        method: method,
        headers: headers
    };

    if (body && method !== 'GET') {
        options.body = JSON.stringify(body);
    }

    try {
        const response = await fetch(url, options);
        let data = null;

        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
            data = await response.json();
        }

        // Handle 401 Unauthorized - token might be expired
        if (response.status === 401) {
            // Try to refresh token
            const refreshed = await refreshAccessToken();
            if (refreshed) {
                // Retry the original request
                return apiCall(url, method, body);
            } else {
                // Redirect to login
                showNotification('세션이 만료되었습니다. 다시 로그인해주세요.', 'warning');
                setTimeout(() => {
                    window.location.href = '/demo/auth/login';
                }, 2000);
            }
        }

        return {
            ok: response.ok,
            status: response.status,
            data: data
        };
    } catch (error) {
        console.error('API call error:', error);
        return {
            ok: false,
            status: 0,
            data: { message: error.message }
        };
    }
}

/**
 * Attempt to refresh the access token
 */
async function refreshAccessToken() {
    const refreshToken = getRefreshToken();
    if (!refreshToken) {
        return false;
    }

    try {
        const response = await fetch('/auth/refresh', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ refreshToken: refreshToken })
        });

        if (response.ok) {
            const data = await response.json();
            saveTokens(data.accessToken, data.refreshToken);
            return true;
        }
    } catch (error) {
        console.error('Token refresh error:', error);
    }

    clearTokens();
    return false;
}

// ===== UI Helpers =====

/**
 * Show result in result area
 */
function showResult(response) {
    const resultArea = document.getElementById('resultArea');
    if (!resultArea) return;

    const resultStatus = document.getElementById('resultStatus');
    const resultBody = document.getElementById('resultBody');

    resultArea.style.display = 'block';
    resultStatus.textContent = `Status: ${response.status}`;
    resultStatus.className = `result-status ${response.ok ? 'success' : 'error'}`;
    resultBody.textContent = JSON.stringify(response.data, null, 2);
}

/**
 * Show error in result area
 */
function showError(message) {
    const resultArea = document.getElementById('resultArea');
    if (!resultArea) return;

    const resultStatus = document.getElementById('resultStatus');
    const resultBody = document.getElementById('resultBody');

    resultArea.style.display = 'block';
    resultStatus.textContent = 'Error';
    resultStatus.className = 'result-status error';
    resultBody.textContent = message;
}

/**
 * Show notification toast
 * @param {string} message - Notification message
 * @param {string} type - Type: 'info', 'success', 'error', 'warning'
 */
function showNotification(message, type = 'info') {
    const notification = document.createElement('div');
    notification.className = `notification ${type}`;
    notification.textContent = message;
    document.body.appendChild(notification);

    setTimeout(() => {
        notification.classList.add('fade-out');
        setTimeout(() => notification.remove(), 300);
    }, 3000);
}

// ===== Modal Helpers =====

/**
 * Open a modal by ID
 */
function openModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.style.display = 'flex';
    }
}

/**
 * Close a modal by ID
 */
function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.style.display = 'none';
    }
}

// Close modal when clicking outside
document.addEventListener('click', function(event) {
    if (event.target.classList.contains('modal')) {
        event.target.style.display = 'none';
    }
});

// Close modal with Escape key
document.addEventListener('keydown', function(event) {
    if (event.key === 'Escape') {
        const modals = document.querySelectorAll('.modal');
        modals.forEach(modal => {
            if (modal.style.display === 'flex') {
                modal.style.display = 'none';
            }
        });
    }
});

// ===== Sidebar Toggle =====

function toggleSidebar() {
    const sidebar = document.querySelector('.admin-sidebar');
    const content = document.querySelector('.admin-content');

    if (sidebar) {
        sidebar.classList.toggle('collapsed');
        sidebar.classList.toggle('open');
    }
    if (content) {
        content.classList.toggle('expanded');
    }
}

// ===== Confirmation Helpers =====

/**
 * Confirm delete action
 */
function confirmDelete(itemName, deleteCallback) {
    if (confirm(`'${itemName}'을(를) 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.`)) {
        deleteCallback();
    }
}

// ===== Utility Functions =====

/**
 * Format date to Korean locale
 */
function formatDate(dateString) {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleString('ko-KR');
}

/**
 * Debounce function for search inputs
 */
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

/**
 * Copy text to clipboard
 */
function copyToClipboard(elementId) {
    const element = document.getElementById(elementId);
    if (!element) return;

    const text = element.textContent || element.value;
    navigator.clipboard.writeText(text).then(() => {
        showNotification('클립보드에 복사되었습니다.', 'success');
    }).catch(err => {
        console.error('Copy failed:', err);
        showNotification('복사에 실패했습니다.', 'error');
    });
}

// ===== Form Helpers =====

/**
 * Serialize form data to object
 */
function serializeForm(formId) {
    const form = document.getElementById(formId);
    if (!form) return {};

    const formData = new FormData(form);
    const data = {};

    for (const [key, value] of formData.entries()) {
        if (data[key]) {
            // Handle multiple values (checkboxes, multi-select)
            if (!Array.isArray(data[key])) {
                data[key] = [data[key]];
            }
            data[key].push(value);
        } else {
            data[key] = value;
        }
    }

    return data;
}

/**
 * Reset form by ID
 */
function resetForm(formId) {
    const form = document.getElementById(formId);
    if (form) {
        form.reset();
    }
}

// ===== Pagination Helper =====

/**
 * Render pagination controls
 * @param {string} containerId - Container element ID
 * @param {number} totalPages - Total number of pages
 * @param {number} currentPage - Current page (0-indexed)
 * @param {function} loadFunction - Function to call when page changes
 */
function renderPagination(containerId, totalPages, currentPage, loadFunction) {
    const container = document.getElementById(containerId);
    if (!container || totalPages <= 1) {
        if (container) container.innerHTML = '';
        return;
    }

    let html = '';

    // Previous button
    if (currentPage > 0) {
        html += `<button onclick="${loadFunction.name}(${currentPage - 1})">&laquo; 이전</button>`;
    }

    // Page numbers
    const startPage = Math.max(0, currentPage - 2);
    const endPage = Math.min(totalPages - 1, currentPage + 2);

    if (startPage > 0) {
        html += `<button onclick="${loadFunction.name}(0)">1</button>`;
        if (startPage > 1) {
            html += '<span class="ellipsis">...</span>';
        }
    }

    for (let i = startPage; i <= endPage; i++) {
        const activeClass = i === currentPage ? 'active' : '';
        html += `<button onclick="${loadFunction.name}(${i})" class="${activeClass}">${i + 1}</button>`;
    }

    if (endPage < totalPages - 1) {
        if (endPage < totalPages - 2) {
            html += '<span class="ellipsis">...</span>';
        }
        html += `<button onclick="${loadFunction.name}(${totalPages - 1})">${totalPages}</button>`;
    }

    // Next button
    if (currentPage < totalPages - 1) {
        html += `<button onclick="${loadFunction.name}(${currentPage + 1})">다음 &raquo;</button>`;
    }

    container.innerHTML = html;
}

// ===== Initialization =====

document.addEventListener('DOMContentLoaded', function() {
    // Check authentication status
    const token = getAccessToken();
    const userInfo = document.getElementById('userInfo');

    if (!token && window.location.pathname.startsWith('/demo/admin')) {
        showNotification('로그인이 필요합니다. 먼저 로그인 페이지에서 인증해주세요.', 'warning');
    }

    // Parse JWT to get user info (if available)
    if (token && userInfo) {
        try {
            const payload = JSON.parse(atob(token.split('.')[1]));
            if (payload.sub) {
                userInfo.textContent = payload.sub;
            }
        } catch (e) {
            // Invalid token format
        }
    }
});

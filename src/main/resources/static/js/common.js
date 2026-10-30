/**
 * 화면 공통 유틸. 서버 설정(window.SEAT_CONFIG)을 읽어 상태 메타데이터·그리드 크기를 제공한다.
 */
const SeatApp = (() => {
    const config = window.SEAT_CONFIG || {};
    const statusList = config.statuses || [];
    const statusMap = Object.fromEntries(statusList.map(s => [s.code, s]));

    // 서버와 브라우저 시계 차이 보정 (타이머가 서버 기준 statusChangedAt 과 비교되므로)
    const clockOffset = config.serverNow ? new Date(config.serverNow).getTime() - Date.now() : 0;

    function now() {
        return Date.now() + clockOffset;
    }

    function status(code) {
        return statusMap[code] || {code, label: code, color: '#94a3b8', icon: 'fa-question', timed: false};
    }

    // ===== API =====
    /**
     * 모든 서버 요청은 이 함수 하나로 보낸다. (인증 도입 시 CSRF 헤더 등을 여기에 추가)
     * 실패하면 서버의 {message, details} 를 담은 Error 를 던진다.
     */
    async function api(method, url, body) {
        const options = {method, headers: {'Accept': 'application/json'}};
        if (body !== undefined) {
            options.headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(body);
        }
        let response;
        try {
            response = await fetch(url, options);
        } catch (e) {
            throw Object.assign(new Error('서버에 연결할 수 없습니다. 네트워크를 확인해주세요.'), {details: []});
        }
        const text = await response.text();
        const data = text ? safeJson(text) : null;
        if (!response.ok) {
            const message = (data && data.message) || `요청을 처리하지 못했습니다. (${response.status})`;
            throw Object.assign(new Error(message), {status: response.status, details: (data && data.details) || []});
        }
        return data;
    }

    function safeJson(text) {
        try {
            return JSON.parse(text);
        } catch (e) {
            return null;
        }
    }

    function query(params) {
        const search = new URLSearchParams();
        Object.entries(params).forEach(([k, v]) => {
            if (v !== null && v !== undefined && v !== '') search.append(k, v);
        });
        const s = search.toString();
        return s ? `?${s}` : '';
    }

    // ===== 토스트 =====
    const TOAST_ICONS = {
        success: 'fa-check-circle', error: 'fa-exclamation-circle', warning: 'fa-exclamation-triangle',
        info: 'fa-info-circle', loading: 'fa-spinner fa-spin'
    };

    /** 우측 상단 토스트. loading 이 아니면 3초 후 자동 제거. 반환값의 close()로 직접 닫을 수 있다. */
    function toast(message, type = 'info') {
        const container = document.getElementById('toastContainer');
        const el = document.createElement('div');
        el.className = `toast ${type}`;
        el.innerHTML = `<i class="fas ${TOAST_ICONS[type] || TOAST_ICONS.info}"></i><span></span>`;
        el.querySelector('span').textContent = message;
        container.appendChild(el);
        const close = () => {
            el.classList.add('leaving');
            setTimeout(() => el.remove(), 200);
        };
        if (type !== 'loading') setTimeout(close, 3000);
        return {close};
    }

    // ===== 모달 =====
    const openModals = [];

    function openModal(el) {
        el.hidden = false;
        if (!openModals.includes(el)) openModals.push(el);
        const focusTarget = el.querySelector('[autofocus], input:not([disabled]), textarea, select');
        if (focusTarget) setTimeout(() => focusTarget.focus(), 30);
    }

    function closeModal(el) {
        el.hidden = true;
        const i = openModals.indexOf(el);
        if (i >= 0) openModals.splice(i, 1);
        el.dispatchEvent(new CustomEvent('modal:closed'));
    }

    function topModal() {
        return openModals[openModals.length - 1] || null;
    }

    function isAnyModalOpen() {
        return openModals.length > 0;
    }

    // 바깥 클릭 / [data-close] 버튼으로 닫기
    document.addEventListener('click', e => {
        const backdrop = e.target.closest('.modal-backdrop');
        if (!backdrop) return;
        if (e.target === backdrop || e.target.closest('[data-close]')) closeModal(backdrop);
    });

    document.addEventListener('keydown', e => {
        if (e.key === 'Escape' && topModal()) {
            e.preventDefault();
            closeModal(topModal());
        }
    });

    /** 확인 모달. Promise<boolean> */
    function confirmDialog({title = '확인', message, confirmText = '확인', danger = false}) {
        const modal = document.getElementById('confirmModal');
        const ok = document.getElementById('confirmOk');
        document.getElementById('confirmTitle').textContent = title;
        document.getElementById('confirmMessage').textContent = message;
        ok.textContent = confirmText;
        ok.className = `btn ${danger ? 'btn-danger' : 'btn-primary'}`;
        return new Promise(resolve => {
            let result = false;
            const onOk = () => {
                result = true;
                closeModal(modal);
            };
            const onClosed = () => {
                ok.removeEventListener('click', onOk);
                modal.removeEventListener('modal:closed', onClosed);
                resolve(result);
            };
            ok.addEventListener('click', onOk);
            modal.addEventListener('modal:closed', onClosed);
            openModal(modal);
            setTimeout(() => ok.focus(), 30);
        });
    }

    // ===== 포맷 =====
    const tz = config.timeZone || undefined;
    const fullFormat = new Intl.DateTimeFormat('ko-KR', {
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true, timeZone: tz
    });
    const timeFormat = new Intl.DateTimeFormat('ko-KR', {
        hour: 'numeric', minute: '2-digit', second: '2-digit', hour12: true, timeZone: tz
    });
    const shortParts = new Intl.DateTimeFormat('ko-KR', {
        month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false, timeZone: tz
    });

    /** 2026. 09. 02. 오후 12:06:44 */
    function formatDateTime(value) {
        return value ? fullFormat.format(new Date(value)) : '-';
    }

    /** 오전 9:11:54 */
    function formatTime(value) {
        return timeFormat.format(new Date(value));
    }

    /** MM/DD HH:mm */
    function formatShort(value) {
        if (!value) return '-';
        const p = Object.fromEntries(shortParts.formatToParts(new Date(value)).map(x => [x.type, x.value]));
        const hour = p.hour === '24' ? '00' : p.hour;
        return `${p.month}/${p.day} ${hour}:${p.minute}`;
    }

    /** 경과 시간 mm:ss (1시간 이상이면 h:mm:ss) */
    function formatElapsed(ms) {
        const total = Math.max(0, Math.floor(ms / 1000));
        const h = Math.floor(total / 3600);
        const m = Math.floor((total % 3600) / 60);
        const s = total % 60;
        const mmss = `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
        return h > 0 ? `${h}:${mmss}` : mmss;
    }

    function elapsedMs(seat) {
        return seat.statusChangedAt ? now() - new Date(seat.statusChangedAt).getTime() : 0;
    }

    /** 타이머 대상 상태의 위험도: 'danger' | 'warning' | null */
    function dangerLevel(seat) {
        const meta = status(seat.status);
        if (!meta.timed) return null;
        const minutes = elapsedMs(seat) / 60000;
        if (minutes >= meta.dangerMinutes) return 'danger';
        if (meta.warningMinutes && minutes >= meta.warningMinutes) return 'warning';
        return null;
    }

    function escapeHtml(value) {
        return String(value ?? '').replace(/[&<>"']/g, c => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        })[c]);
    }

    function compareName(a, b) {
        return a.name.localeCompare(b.name, 'ko');
    }

    function debounce(fn, wait) {
        let timer;
        return (...args) => {
            clearTimeout(timer);
            timer = setTimeout(() => fn(...args), wait);
        };
    }

    /** WebSocket 메시지(seatId/seatName) → 좌석 객체(id/name) */
    function seatFromMessage(msg) {
        return {
            id: msg.seatId,
            name: msg.seatName,
            rowIndex: msg.rowIndex,
            colIndex: msg.colIndex,
            status: msg.status,
            statusChangedAt: msg.statusChangedAt,
            meetingRoom: msg.meetingRoom
        };
    }

    // ===== WebSocket =====
    /**
     * /topic/batch/{batchName} 구독. 끊기면 3초 간격으로 자동 재연결한다.
     * onConnectionChange(connected: boolean, isReconnect: boolean)
     */
    function connectBatch(batchName, onMessage, onConnectionChange) {
        let connectedOnce = false;
        const client = new StompJs.Client({
            webSocketFactory: () => new SockJS('/ws'),
            reconnectDelay: 3000,
            heartbeatIncoming: 10000,
            heartbeatOutgoing: 10000,
            debug: () => {}
        });
        client.onConnect = () => {
            client.subscribe(`/topic/batch/${batchName}`, frame => {
                const msg = safeJson(frame.body);
                if (msg) onMessage(msg);
            });
            onConnectionChange(true, connectedOnce);
            connectedOnce = true;
        };
        client.onWebSocketClose = () => onConnectionChange(false, connectedOnce);
        client.onStompError = () => onConnectionChange(false, connectedOnce);
        client.activate();
        return client;
    }

    function setLiveIndicator(connected) {
        const el = document.getElementById('liveIndicator');
        if (!el) return;
        el.classList.toggle('connected', connected);
        el.title = connected ? '실시간 연결됨' : '연결 끊김 - 재연결 중';
    }

    return {
        config, statusList, status, now, api, query, toast,
        openModal, closeModal, isAnyModalOpen, confirmDialog,
        formatDateTime, formatTime, formatShort, formatElapsed, elapsedMs, dangerLevel,
        escapeHtml, compareName, debounce, seatFromMessage, connectBatch, setLiveIndicator
    };
})();

/**
 * 좌석관리 메인 화면 (/)
 */
(() => {
    const {config} = SeatApp;
    const batch = config.currentBatch;
    const $ = id => document.getElementById(id);

    $('batchSelect').addEventListener('change', e => {
        location.href = '/' + SeatApp.query({batchName: e.target.value});
    });
    if (!batch) return;

    // 메뉴에 노출하는 8개 상태 (점심시간은 일괄 처리, 중도포기는 수강생 관리에서만)
    const MENU_STATUSES = ['IN', 'OUT', 'WC', 'SMOKE', 'STORE', 'ABSENT', 'LATE', 'OUT_OFFICE'];

    /** id → seat */
    const seats = new Map((config.seats || []).map(s => [s.id, s]));
    /** id → 좌석 카드 element */
    const cards = new Map();

    const grid = $('seatGrid');
    let menuSeatId = null;
    let alphaVisible = false;

    // ===== 범례 =====
    $('legend').innerHTML = SeatApp.statusList
        .filter(s => s.code !== 'GIVEUP')
        .map(s => `<li><span class="legend-dot" style="background:${s.color}"></span>${s.label}</li>`)
        .join('');
    $('legendToggle').addEventListener('click', e => {
        const open = $('legend').classList.toggle('open');
        e.currentTarget.setAttribute('aria-expanded', String(open));
    });

    // ===== 배치도 =====
    grid.style.gridTemplateColumns = `repeat(${config.gridCols}, 1fr)`;
    grid.style.gridTemplateRows = `repeat(${config.gridRows}, 55px)`;

    // 드롭 가이드용 빈 칸 (드래그 중에만 보임)
    for (let r = 1; r <= config.gridRows; r++) {
        for (let c = 1; c <= config.gridCols; c++) {
            const cell = document.createElement('div');
            cell.className = 'drop-cell';
            cell.dataset.row = r;
            cell.dataset.col = c;
            cell.style.gridRow = r;
            cell.style.gridColumn = c;
            grid.appendChild(cell);
        }
    }

    function onSeatMap(seat) {
        return seat.status !== 'GIVEUP' && seat.rowIndex > 0 && seat.colIndex > 0;
    }

    function createCard(seat) {
        const el = document.createElement('div');
        el.className = 'seat';
        el.draggable = true;
        el.dataset.id = seat.id;
        el.tabIndex = 0;
        el.innerHTML = `
            <span class="seat-name"></span>
            <span class="seat-timer"></span>
            <i class="fas seat-icon"></i>
            <span class="seat-room" hidden></span>`;
        grid.appendChild(el);
        cards.set(seat.id, el);
        return el;
    }

    /** 카드 하나를 seat 값에 맞게 갱신. 위치가 바뀌면 FLIP 애니메이션으로 이동한다. */
    function renderCard(seat, {animateMove = false} = {}) {
        let el = cards.get(seat.id);
        if (!onSeatMap(seat)) {
            if (el) {
                el.remove();
                cards.delete(seat.id);
            }
            return;
        }
        if (!el) el = createCard(seat);

        const meta = SeatApp.status(seat.status);
        const moved = el.style.gridRow !== String(seat.rowIndex) || el.style.gridColumn !== String(seat.colIndex);
        const before = animateMove && moved ? el.getBoundingClientRect() : null;

        el.style.gridRow = seat.rowIndex;
        el.style.gridColumn = seat.colIndex;
        el.style.setProperty('--seat-color', meta.color);
        el.dataset.tooltip = `${seat.name} - ${meta.label}${seat.meetingRoom ? ` (회의실 ${seat.meetingRoom})` : ''}`;
        el.setAttribute('aria-label', el.dataset.tooltip);
        el.querySelector('.seat-name').textContent = seat.name;
        el.querySelector('.seat-icon').className = `fas seat-icon ${meta.icon}`;
        const room = el.querySelector('.seat-room');
        room.hidden = !seat.meetingRoom;
        room.textContent = seat.meetingRoom ? `회의실 ${seat.meetingRoom}` : '';
        updateTimer(seat, el);
        updateDanger(seat, el);

        if (before) {
            const after = el.getBoundingClientRect();
            el.animate([
                {transform: `translate(${before.left - after.left}px, ${before.top - after.top}px)`},
                {transform: 'none'}
            ], {duration: 450, easing: 'cubic-bezier(.2,.8,.2,1)'});
        }
    }

    function renderAll() {
        // 사라진 좌석 정리
        for (const id of [...cards.keys()]) {
            if (!seats.has(id)) {
                cards.get(id).remove();
                cards.delete(id);
            }
        }
        seats.forEach(seat => renderCard(seat));
        $('seatEmpty').hidden = [...seats.values()].some(onSeatMap);
        renderRoomCount();
        if (alphaVisible) renderAlpha();
        if (!$('roomStatusModal').hidden) renderRoomCards();
    }

    function pulse(id) {
        const el = cards.get(id);
        if (!el) return;
        el.classList.remove('pulse');
        void el.offsetWidth; // 애니메이션 재시작
        el.classList.add('pulse');
    }

    // ===== 타이머 / 위험 상태 =====
    function updateTimer(seat, el = cards.get(seat.id)) {
        if (!el) return;
        const timed = SeatApp.status(seat.status).timed;
        el.querySelector('.seat-timer').textContent = timed ? SeatApp.formatElapsed(SeatApp.elapsedMs(seat)) : '';
    }

    const notified = new Set();

    function updateDanger(seat, el = cards.get(seat.id)) {
        if (!el) return;
        const level = SeatApp.dangerLevel(seat);
        el.classList.toggle('danger', level === 'danger');
        el.classList.toggle('warning', level === 'warning');
        const key = `${seat.id}:${seat.statusChangedAt}`;
        if (level === 'danger' && !notified.has(key)) {
            notified.add(key);
            notify(seat);
        }
    }

    function notify(seat) {
        if (!('Notification' in window) || Notification.permission !== 'granted') return;
        const meta = SeatApp.status(seat.status);
        new Notification('좌석관리 시스템', {
            body: `${seat.name} 수강생이 ${meta.label} 상태로 ${SeatApp.formatElapsed(SeatApp.elapsedMs(seat))} 경과했습니다.`,
            tag: `seat-${seat.id}`
        });
    }

    // 브라우저 알림 권한은 사용자 동작이 있을 때 한 번만 요청
    document.addEventListener('click', () => {
        if ('Notification' in window && Notification.permission === 'default') Notification.requestPermission();
    }, {once: true});

    setInterval(() => seats.forEach(s => updateTimer(s)), 1000);
    setInterval(() => seats.forEach(s => updateDanger(s)), 10000);

    // ===== 마지막 업데이트 =====
    function touchLastUpdated() {
        $('lastUpdated').textContent = SeatApp.formatTime(SeatApp.now());
    }

    setInterval(touchLastUpdated, 30000);
    touchLastUpdated();

    // ===== 상태 변경 =====
    async function changeStatus(id, status) {
        const el = cards.get(id);
        const icon = el && el.querySelector('.seat-icon');
        if (el) {
            el.classList.add('loading');
            icon.className = 'fas seat-icon fa-spinner fa-spin';
        }
        try {
            const updated = await SeatApp.api('POST', '/seats/status', {seatId: id, status});
            seats.set(id, updated);
            renderCard(updated);
            pulse(id);
            renderAll();
        } catch (err) {
            SeatApp.toast(err.message, 'error');
            const seat = seats.get(id);
            if (seat) renderCard(seat);
        } finally {
            if (el) el.classList.remove('loading');
        }
    }

    // ===== 좌석 클릭 → 메뉴 =====
    let suppressClick = false;

    grid.addEventListener('click', e => {
        const el = e.target.closest('.seat');
        if (!el || suppressClick) return;
        const seat = seats.get(Number(el.dataset.id));
        if (!seat || seat.status === 'GIVEUP') return;
        if (seat.status === 'LUNCH') {
            changeStatus(seat.id, 'IN');
            return;
        }
        openMenu(seat);
    });

    grid.addEventListener('keydown', e => {
        if (e.key !== 'Enter') return;
        const el = e.target.closest('.seat');
        if (el) el.click();
    });

    $('statusMenu').innerHTML = MENU_STATUSES.map(code => {
        const meta = SeatApp.status(code);
        return `<button type="button" class="status-btn" data-status="${code}" style="--btn-color:${meta.color}">
                    <i class="fas ${meta.icon}"></i> ${meta.label}</button>`;
    }).join('');

    function openMenu(seat) {
        menuSeatId = seat.id;
        renderMenu();
        SeatApp.openModal($('seatMenuModal'));
    }

    function renderMenu() {
        const seat = seats.get(menuSeatId);
        if (!seat) return;
        $('seatMenuName').textContent = seat.name;
        $('statusMenu').querySelectorAll('.status-btn').forEach(btn => {
            btn.classList.toggle('current', btn.dataset.status === seat.status);
        });
        const inRoom = !!seat.meetingRoom;
        $('meetingCurrent').textContent = inRoom ? `현재 회의실 ${seat.meetingRoom}에 있습니다.` : '회의실에 있지 않습니다.';
        $('meetingEnterBtn').hidden = inRoom;
        $('meetingExitBtn').hidden = !inRoom;
    }

    $('seatMenuModal').addEventListener('modal:closed', () => {
        if ($('roomSelectModal').hidden) menuSeatId = null;
    });

    $('statusMenu').addEventListener('click', e => {
        const btn = e.target.closest('.status-btn');
        if (!btn || menuSeatId == null) return;
        const id = menuSeatId;
        SeatApp.closeModal($('seatMenuModal'));
        changeStatus(id, btn.dataset.status);
    });

    // 메뉴가 열려 있을 때 스페이스바 = 닫기
    document.addEventListener('keydown', e => {
        if (e.code === 'Space' && !$('seatMenuModal').hidden && $('roomSelectModal').hidden) {
            e.preventDefault();
            SeatApp.closeModal($('seatMenuModal'));
        }
    });

    // ===== 회의실 =====
    $('roomSelect').innerHTML = Array.from({length: config.meetingRoomCount}, (_, i) => i + 1)
        .map(n => `<button type="button" class="btn btn-ghost" data-room="${n}">
                       <span><i class="fas fa-door-open"></i> 회의실 ${n}</span><span class="badge" data-room-count="${n}"></span>
                   </button>`).join('');

    function roomMembers(room) {
        return [...seats.values()].filter(s => s.meetingRoom === room).sort(SeatApp.compareName);
    }

    $('meetingEnterBtn').addEventListener('click', () => {
        $('roomSelect').querySelectorAll('[data-room-count]').forEach(badge => {
            badge.textContent = `${roomMembers(Number(badge.dataset.roomCount)).length}명`;
        });
        const seatId = menuSeatId;
        SeatApp.closeModal($('seatMenuModal'));
        menuSeatId = seatId;
        SeatApp.openModal($('roomSelectModal'));
    });

    $('roomSelectModal').addEventListener('modal:closed', () => {
        menuSeatId = null;
    });

    $('roomSelect').addEventListener('click', async e => {
        const btn = e.target.closest('[data-room]');
        if (!btn || menuSeatId == null) return;
        const seatId = menuSeatId;
        SeatApp.closeModal($('roomSelectModal'));
        try {
            const updated = await SeatApp.api('POST', '/api/meeting-room/enter',
                {seatId, meetingRoom: Number(btn.dataset.room)});
            applySeat(updated);
            pulse(seatId);
            SeatApp.toast(`${updated.name}: 회의실 ${updated.meetingRoom} 입장`, 'success');
        } catch (err) {
            SeatApp.toast(err.message, 'error');
        }
    });

    async function exitRoom(seatId) {
        try {
            const updated = await SeatApp.api('POST', '/api/meeting-room/exit' + SeatApp.query({seatId}));
            applySeat(updated);
            SeatApp.toast(`${updated.name}: 회의실 퇴장`, 'success');
        } catch (err) {
            SeatApp.toast(err.message, 'error');
        }
    }

    $('meetingExitBtn').addEventListener('click', () => {
        const seatId = menuSeatId;
        SeatApp.closeModal($('seatMenuModal'));
        exitRoom(seatId);
    });

    function renderRoomCount() {
        $('roomCount').textContent = [...seats.values()].filter(s => s.meetingRoom).length;
    }

    function renderRoomCards() {
        $('roomCards').innerHTML = Array.from({length: config.meetingRoomCount}, (_, i) => i + 1).map(room => {
            const members = roomMembers(room);
            const list = members.length
                ? `<ul>${members.map(s => `
                    <li><span>${SeatApp.escapeHtml(s.name)}</span>
                        <button type="button" class="btn btn-ghost btn-sm" data-exit="${s.id}">퇴장</button></li>`).join('')}</ul>`
                : '<p class="empty">비어있음</p>';
            return `<div class="room-card">
                        <h4><span><i class="fas fa-door-open"></i> 회의실 ${room}</span>
                            <span class="badge badge-primary">${members.length}명</span></h4>
                        ${list}
                    </div>`;
        }).join('');
    }

    $('roomStatusBtn').addEventListener('click', () => {
        renderRoomCards();
        SeatApp.openModal($('roomStatusModal'));
    });

    $('roomCards').addEventListener('click', e => {
        const btn = e.target.closest('[data-exit]');
        if (btn) exitRoom(Number(btn.dataset.exit));
    });

    $('exitAllRoomsBtn').addEventListener('click', async () => {
        const ok = await SeatApp.confirmDialog({
            title: '전체 회의실 퇴장',
            message: '모든 회의실의 수강생을 퇴장 처리하시겠습니까?',
            confirmText: '전체 퇴장',
            danger: true
        });
        if (!ok) return;
        try {
            const result = await SeatApp.api('POST', '/api/meeting-room/exit-all' + SeatApp.query({batchName: batch.name}));
            await resync();
            SeatApp.toast(`${result.changed}명을 회의실에서 퇴장 처리했습니다.`, 'success');
        } catch (err) {
            SeatApp.toast(err.message, 'error');
        }
    });

    // ===== 점심시간 / 전체 퇴실 =====
    async function bulkAction({title, message, confirmText, url, done, danger}) {
        const ok = await SeatApp.confirmDialog({title, message, confirmText, danger});
        if (!ok) return;
        const loading = SeatApp.toast('처리 중입니다...', 'loading');
        try {
            const result = await SeatApp.api('POST', url, {batchName: batch.name});
            await resync();
            SeatApp.toast(done(result.changed), 'success');
        } catch (err) {
            SeatApp.toast(err.message, 'error');
        } finally {
            loading.close();
        }
    }

    $('lunchBtn').addEventListener('click', () => bulkAction({
        title: '점심시간',
        message: `${batch.name} 수강생 전체를 점심시간으로 변경하시겠습니까?\n(결석·퇴실·중도포기 수강생은 제외)`,
        confirmText: '점심시간 시작',
        url: '/seats/lunch-time',
        done: n => `${n}명을 점심시간으로 변경했습니다.`
    }));

    $('bulkExitBtn').addEventListener('click', () => bulkAction({
        title: '전체 퇴실',
        message: `${batch.name} 수강생 전체를 퇴실 처리하시겠습니까?\n(결석·중도포기 수강생은 제외, 회의실에서도 모두 퇴장)`,
        confirmText: '전체 퇴실',
        url: '/seats/bulk-exit',
        danger: true,
        done: n => `${n}명을 퇴실 처리했습니다.`
    }));

    // ===== 드래그 앤 드롭 =====
    let dragId = null;

    function clearDropTargets() {
        grid.querySelectorAll('.drop-target').forEach(el => el.classList.remove('drop-target'));
    }

    grid.addEventListener('dragstart', e => {
        const el = e.target.closest('.seat');
        if (!el || SeatApp.isAnyModalOpen()) {
            e.preventDefault();
            return;
        }
        dragId = Number(el.dataset.id);
        e.dataTransfer.effectAllowed = 'move';
        e.dataTransfer.setData('text/plain', String(dragId));
        // 브라우저가 드래그 이미지를 캡처한 뒤에 스타일을 바꾼다
        requestAnimationFrame(() => {
            grid.classList.add('dragging');
            el.classList.add('drag-source');
        });
    });

    grid.addEventListener('dragend', () => {
        grid.classList.remove('dragging');
        grid.querySelectorAll('.drag-source').forEach(el => el.classList.remove('drag-source'));
        clearDropTargets();
        dragId = null;
        // 드롭 직후 발생하는 click 이 메뉴를 열지 않도록
        suppressClick = true;
        setTimeout(() => (suppressClick = false), 50);
    });

    function dropTargetOf(e) {
        const target = e.target.closest('.seat, .drop-cell');
        if (!target) return null;
        if (target.classList.contains('seat') && Number(target.dataset.id) === dragId) return null;
        return target;
    }

    grid.addEventListener('dragover', e => {
        if (dragId == null) return;
        const target = dropTargetOf(e);
        if (!target) return;
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';
        if (!target.classList.contains('drop-target')) {
            clearDropTargets();
            target.classList.add('drop-target');
        }
    });

    grid.addEventListener('dragleave', e => {
        const target = e.target.closest('.seat, .drop-cell');
        if (target && !target.contains(e.relatedTarget)) target.classList.remove('drop-target');
    });

    grid.addEventListener('drop', async e => {
        const target = dropTargetOf(e);
        const sourceId = dragId;
        if (!target || sourceId == null) return;
        e.preventDefault();
        clearDropTargets();
        const source = seats.get(sourceId);

        if (target.classList.contains('seat')) {
            const other = seats.get(Number(target.dataset.id));
            const ok = await SeatApp.confirmDialog({
                title: '자리 교환',
                message: `${source.name}(${source.rowIndex}행 ${source.colIndex}열)과 ${other.name}(${other.rowIndex}행 ${other.colIndex}열)의 자리를 교환하시겠습니까?`,
                confirmText: '교환'
            });
            if (!ok) return;
            try {
                const updated = await SeatApp.api('POST', '/students/swap', {seatId1: source.id, seatId2: other.id});
                updated.forEach(s => applySeat(s, true));
                SeatApp.toast(`${source.name} ↔ ${other.name} 자리를 교환했습니다.`, 'success');
            } catch (err) {
                SeatApp.toast(err.message, 'error');
            }
            return;
        }

        const row = Number(target.dataset.row);
        const col = Number(target.dataset.col);
        try {
            const updated = await SeatApp.api('PUT', `/students/${source.id}`,
                {name: source.name, rowIndex: row, colIndex: col});
            applySeat(updated, true);
        } catch (err) {
            SeatApp.toast(err.message, 'error');
        }
    });

    // ===== 가나다순 보기 =====
    $('alphaToggleBtn').addEventListener('click', () => {
        alphaVisible = !alphaVisible;
        $('alphaSection').hidden = !alphaVisible;
        $('alphaToggleBtn').querySelector('span').textContent = alphaVisible ? '가나다순 숨기기' : '가나다순 보기';
        if (alphaVisible) {
            renderAlpha();
            $('alphaSection').scrollIntoView({behavior: 'smooth', block: 'start'});
        }
    });

    function renderAlpha() {
        const list = [...seats.values()].sort(SeatApp.compareName);
        $('alphaTitle').textContent = `가나다순 정렬 보기 (${list.length}명)`;
        $('alphaGrid').innerHTML = list.map(s => {
            const meta = SeatApp.status(s.status);
            const cls = s.status === 'ABSENT' ? 'absent' : s.status === 'GIVEUP' ? 'giveup' : '';
            return `<button type="button" class="alpha-card ${cls}" data-id="${s.id}">
                        <span class="name">${SeatApp.escapeHtml(s.name)}</span>
                        <span class="badge status-badge" style="background:${meta.color}">${meta.label}</span>
                    </button>`;
        }).join('');
    }

    $('alphaGrid').addEventListener('click', e => {
        const card = e.target.closest('.alpha-card');
        if (!card) return;
        const el = cards.get(Number(card.dataset.id));
        if (!el) {
            SeatApp.toast('중도포기 수강생은 배치도에 없습니다.', 'info');
            return;
        }
        el.scrollIntoView({behavior: 'smooth', block: 'center', inline: 'center'});
        el.classList.remove('highlight');
        void el.offsetWidth;
        el.classList.add('highlight');
        setTimeout(() => el.classList.remove('highlight'), 3000);
    });

    // ===== 출입 히스토리 =====
    const history = {page: 0, totalPages: 0};

    $('hBatch').innerHTML = '<option value="">전체</option>' + config.batches
        .map(b => `<option value="${SeatApp.escapeHtml(b.name)}">${SeatApp.escapeHtml(b.name)}</option>`).join('');

    function historyFilters() {
        return {
            batchName: $('hBatch').value,
            studentName: $('hName').value.trim(),
            startDate: $('hStart').value,
            endDate: $('hEnd').value
        };
    }

    function resetHistoryFilters() {
        $('hBatch').value = batch.name;
        $('hName').value = '';
        $('hStart').value = '';
        $('hEnd').value = '';
    }

    async function loadHistory(page = 0) {
        const tbody = $('historyBody');
        tbody.innerHTML = '<tr><td colspan="5" class="empty-state"><i class="fas fa-spinner fa-spin"></i></td></tr>';
        try {
            const data = await SeatApp.api('GET', '/api/history' + SeatApp.query({...historyFilters(), page, size: 10}));
            history.page = data.currentPage;
            history.totalPages = data.totalPages;
            const offset = data.currentPage * 10;
            tbody.innerHTML = data.histories.length
                ? data.histories.map((h, i) => {
                    const meta = SeatApp.status(h.toStatus);
                    return `<tr>
                        <td>${data.totalElements - offset - i}</td>
                        <td>${SeatApp.escapeHtml(h.studentName)}</td>
                        <td>${SeatApp.escapeHtml(h.batchName)}</td>
                        <td><span class="badge status-badge" style="background:${meta.color}">
                            <i class="fas ${meta.icon}"></i> ${meta.label}</span></td>
                        <td class="time">${SeatApp.formatDateTime(h.changedAt)}</td>
                    </tr>`;
                }).join('')
                : '<tr><td colspan="5" class="empty-state">기록이 없습니다.</td></tr>';
            $('historyPageInfo').textContent =
                `${data.totalPages === 0 ? 0 : data.currentPage + 1} / ${data.totalPages} 페이지 (총 ${data.totalElements}건)`;
            $('historyPrev').disabled = !data.hasPrevious;
            $('historyNext').disabled = !data.hasNext;
        } catch (err) {
            tbody.innerHTML = `<tr><td colspan="5" class="empty-state">${SeatApp.escapeHtml(err.message)}</td></tr>`;
        }
    }

    $('historyBtn').addEventListener('click', () => {
        resetHistoryFilters();
        SeatApp.openModal($('historyModal'));
        loadHistory(0);
    });

    $('historyForm').addEventListener('submit', e => {
        e.preventDefault();
        loadHistory(0);
    });

    $('historyReset').addEventListener('click', () => {
        resetHistoryFilters();
        loadHistory(0);
    });

    $('historyPrev').addEventListener('click', () => loadHistory(history.page - 1));
    $('historyNext').addEventListener('click', () => loadHistory(history.page + 1));

    $('historyExport').addEventListener('click', async () => {
        const loading = SeatApp.toast('엑셀 파일을 만드는 중입니다...', 'loading');
        try {
            await SeatApp.download('/api/history/export' + SeatApp.query(historyFilters()), '출입히스토리.xlsx');
        } catch (err) {
            SeatApp.toast(err.message, 'error');
        } finally {
            loading.close();
        }
    });

    // ===== 실시간 동기화 =====
    /** 서버에서 받은 좌석 하나를 반영 */
    function applySeat(seat, animateMove = false) {
        seats.set(seat.id, seat);
        renderCard(seat, {animateMove});
        if (menuSeatId === seat.id && !$('seatMenuModal').hidden) {
            if (seat.status === 'GIVEUP') SeatApp.closeModal($('seatMenuModal'));
            else renderMenu();
        }
        $('seatEmpty').hidden = [...seats.values()].some(onSeatMap);
        renderRoomCount();
        if (alphaVisible) renderAlpha();
        if (!$('roomStatusModal').hidden) renderRoomCards();
    }

    function onMessage(msg) {
        touchLastUpdated();
        if (msg.type === 'DELETED') {
            seats.delete(msg.seatId);
            renderAll();
            if (menuSeatId === msg.seatId) SeatApp.closeModal($('seatMenuModal'));
            return;
        }
        const seat = SeatApp.seatFromMessage(msg);
        const prev = seats.get(seat.id);
        applySeat(seat, true);
        if (!prev || prev.status !== seat.status) pulse(seat.id);
    }

    /** 전체 좌석을 다시 받아 반영 (탭 복귀·재연결·일괄 작업 후) */
    async function resync() {
        try {
            const list = await SeatApp.api('GET', `/api/seats/batch/${encodeURIComponent(batch.name)}`);
            const fresh = new Map(list.map(s => [s.id, s]));
            seats.clear();
            fresh.forEach((s, id) => seats.set(id, s));
            for (const id of [...cards.keys()]) {
                if (!fresh.has(id)) {
                    cards.get(id).remove();
                    cards.delete(id);
                }
            }
            seats.forEach(s => renderCard(s, {animateMove: true}));
            renderAll();
            touchLastUpdated();
        } catch (err) {
            SeatApp.toast(err.message, 'error');
        }
    }

    SeatApp.connectBatch(batch.name, onMessage, (connected, isReconnect) => {
        SeatApp.setLiveIndicator(connected);
        if (connected && isReconnect) {
            SeatApp.toast('실시간 연결이 복구되었습니다.', 'success');
            resync();
        }
    });

    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') resync();
    });
    window.addEventListener('focus', SeatApp.debounce(resync, 300));

    renderAll();
})();

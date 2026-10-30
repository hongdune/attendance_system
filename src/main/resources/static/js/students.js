/**
 * 수강생 관리 화면 (/students)
 */
(() => {
    const {config} = SeatApp;
    const batch = config.currentBatch;
    const $ = id => document.getElementById(id);

    /** id → seat */
    const seats = new Map((config.seats || []).map(s => [s.id, s]));

    // 상태 셀렉트에 노출하는 상태 (점심시간은 일괄 처리 전용이라 제외)
    const SELECTABLE = SeatApp.statusList.filter(s => s.code !== 'LUNCH');

    // ===== 기수 선택 / 추가 / 삭제 =====
    $('batchSelect').addEventListener('change', e => {
        location.href = '/students' + SeatApp.query({batchName: e.target.value});
    });

    $('addBatchBtn').addEventListener('click', () => {
        $('addBatchForm').reset();
        setHint($('batchNameHint'), `${config.batchNameMin}~${config.batchNameMax}자, 기존 기수와 중복될 수 없습니다.`);
        $('batchName').classList.remove('is-invalid');
        SeatApp.openModal($('addBatchModal'));
    });

    $('addBatchForm').addEventListener('submit', async e => {
        e.preventDefault();
        const name = $('batchName').value.trim();
        if (name.length < config.batchNameMin || name.length > config.batchNameMax) {
            $('batchName').classList.add('is-invalid');
            setHint($('batchNameHint'), `기수명은 ${config.batchNameMin}~${config.batchNameMax}자로 입력해주세요.`, 'error');
            return;
        }
        if (config.batches.some(b => b.name === name)) {
            $('batchName').classList.add('is-invalid');
            setHint($('batchNameHint'), '이미 존재하는 기수명입니다.', 'error');
            return;
        }
        try {
            const created = await SeatApp.api('POST', '/batches', {name});
            location.href = '/students' + SeatApp.query({batchName: created.name});
        } catch (err) {
            $('batchName').classList.add('is-invalid');
            setHint($('batchNameHint'), err.message, 'error');
        }
    });

    $('deleteBatchBtn').addEventListener('click', () => {
        if (!batch) return;
        const hasStudents = seats.size > 0;
        $('deleteBatchMessage').textContent =
            `정말로 '${batch.name}' 기수를 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.`;
        $('deleteBatchBlocked').hidden = !hasStudents;
        $('deleteBatchConfirm').disabled = hasStudents;
        SeatApp.openModal($('deleteBatchModal'));
    });

    $('deleteBatchConfirm').addEventListener('click', async () => {
        try {
            await SeatApp.api('DELETE', `/batches/${batch.id}`);
            location.href = '/students';
        } catch (err) {
            SeatApp.toast(err.message, 'error');
        }
    });

    if (!batch) return;

    // ===== 목록 렌더링 =====
    const tbody = $('studentTableBody');

    function render() {
        const list = [...seats.values()].sort(SeatApp.compareName);
        $('studentCount').textContent = `총 ${list.length}명`;
        $('studentEmpty').hidden = list.length > 0;
        tbody.innerHTML = list.map(rowHtml).join('');
    }

    function rowHtml(s) {
        const meta = SeatApp.status(s.status);
        const giveUp = s.status === 'GIVEUP';
        const options = SELECTABLE.map(o =>
            `<option value="${o.code}" ${o.code === s.status ? 'selected' : ''}>${o.label}</option>`).join('');
        // 점심시간 상태인 학생은 셀렉트에 현재 값으로만 표시
        const lunchOption = s.status === 'LUNCH' ? `<option value="LUNCH" selected disabled>${meta.label}</option>` : '';
        const position = giveUp
            ? '<span class="badge">중도포기</span>'
            : `<span class="badge badge-primary">${s.rowIndex}행 ${s.colIndex}열</span>`;
        return `
            <tr data-id="${s.id}" class="${giveUp ? 'giveup' : ''}">
                <td class="student-name">${SeatApp.escapeHtml(s.name)}</td>
                <td>${position}</td>
                <td>
                    <select class="status-select" data-action="status" style="--status-color: ${meta.color}"
                            aria-label="${SeatApp.escapeHtml(s.name)} 상태">${lunchOption}${options}</select>
                </td>
                <td class="time-cell">${SeatApp.formatShort(s.statusChangedAt)}</td>
                <td class="actions">
                    <button type="button" class="icon-btn edit" data-action="edit" title="수정"><i class="fas fa-pen"></i></button>
                    <button type="button" class="icon-btn danger" data-action="delete" title="삭제"><i class="fas fa-trash"></i></button>
                </td>
            </tr>`;
    }

    function flashRow(id) {
        const row = tbody.querySelector(`tr[data-id="${id}"]`);
        if (row) row.classList.add('flash');
    }

    // ===== 상태 변경 / 수정 / 삭제 =====
    tbody.addEventListener('change', async e => {
        const select = e.target.closest('[data-action="status"]');
        if (!select) return;
        const id = Number(select.closest('tr').dataset.id);
        const seat = seats.get(id);
        const next = select.value;

        if (next === 'GIVEUP' && !confirm(`${seat.name} 수강생을 중도포기 처리하시겠습니까?\n\n좌석 위치가 자동으로 제거됩니다.`)) {
            select.value = seat.status;
            return;
        }
        select.disabled = true;
        try {
            const updated = await SeatApp.api('POST', '/seats/status', {seatId: id, status: next});
            seats.set(id, updated);
            render();
            flashRow(id);
            SeatApp.toast(`${updated.name}: ${SeatApp.status(updated.status).label}`, 'success');
        } catch (err) {
            select.value = seat.status;
            select.disabled = false;
            SeatApp.toast(err.message, 'error');
        }
    });

    tbody.addEventListener('click', async e => {
        const button = e.target.closest('button[data-action]');
        if (!button) return;
        const id = Number(button.closest('tr').dataset.id);
        const seat = seats.get(id);
        if (button.dataset.action === 'edit') {
            openEdit(seat);
        } else if (button.dataset.action === 'delete') {
            if (!confirm(`${seat.name} 수강생을 정말 삭제하시겠습니까?\n\n이 작업은 되돌릴 수 없습니다.`)) return;
            try {
                await SeatApp.api('DELETE', `/students/${id}`);
                seats.delete(id);
                render();
                SeatApp.toast(`${seat.name} 수강생을 삭제했습니다.`, 'success');
            } catch (err) {
                SeatApp.toast(err.message, 'error');
            }
        }
    });

    // ===== 위치 실시간 중복 체크 =====
    /** 위치 입력 검증. 문제 없으면 null, 있으면 메시지 */
    function positionError(row, col, selfId) {
        if (!Number.isInteger(row) || !Number.isInteger(col)) return '행과 열을 입력해주세요.';
        if (row < 1 || row > config.gridRows || col < 1 || col > config.gridCols) {
            return `행은 1~${config.gridRows}, 열은 1~${config.gridCols} 사이로 입력해주세요.`;
        }
        const occupant = [...seats.values()].find(s =>
            s.id !== selfId && s.status !== 'GIVEUP' && s.rowIndex === row && s.colIndex === col);
        return occupant ? `${occupant.name}이(가) 배치되어 있습니다.` : null;
    }

    function bindPositionCheck(rowInput, colInput, hint, selfIdFn) {
        rowInput.max = config.gridRows;
        colInput.max = config.gridCols;
        const check = SeatApp.debounce(() => {
            if (rowInput.disabled) return;
            if (rowInput.value === '' || colInput.value === '') {
                markPosition(rowInput, colInput, null);
                setHint(hint, `행 1~${config.gridRows}, 열 1~${config.gridCols}`);
                return;
            }
            const error = positionError(Number(rowInput.value), Number(colInput.value), selfIdFn());
            markPosition(rowInput, colInput, !error);
            setHint(hint, error || '사용 가능한 위치입니다.', error ? 'error' : 'ok');
        }, 300);
        rowInput.addEventListener('input', check);
        colInput.addEventListener('input', check);
        return check;
    }

    function markPosition(rowInput, colInput, valid) {
        [rowInput, colInput].forEach(input => {
            input.classList.toggle('is-valid', valid === true);
            input.classList.toggle('is-invalid', valid === false);
        });
    }

    function setHint(el, text, type = '') {
        el.textContent = text;
        el.className = `form-hint ${type}`;
    }

    // ===== 수강생 추가 =====
    const addCheck = bindPositionCheck($('addRow'), $('addCol'), $('addPositionHint'), () => null);
    $('addName').maxLength = config.studentNameMax;

    $('addStudentBtn').addEventListener('click', () => {
        $('addStudentForm').reset();
        markPosition($('addRow'), $('addCol'), null);
        addCheck();
        SeatApp.openModal($('addStudentModal'));
    });

    $('addStudentForm').addEventListener('submit', async e => {
        e.preventDefault();
        const name = $('addName').value.trim();
        const row = Number($('addRow').value);
        const col = Number($('addCol').value);
        if (!name) {
            $('addName').classList.add('is-invalid');
            $('addName').focus();
            return;
        }
        $('addName').classList.remove('is-invalid');
        const error = positionError(row, col, null);
        if (error) {
            markPosition($('addRow'), $('addCol'), false);
            setHint($('addPositionHint'), error, 'error');
            return;
        }
        try {
            const created = await SeatApp.api('POST', '/students',
                {name, batchName: batch.name, rowIndex: row, colIndex: col});
            seats.set(created.id, created);
            render();
            flashRow(created.id);
            SeatApp.closeModal($('addStudentModal'));
            SeatApp.toast(`${created.name} 수강생을 추가했습니다.`, 'success');
        } catch (err) {
            setHint($('addPositionHint'), err.message, 'error');
        }
    });

    // ===== 수강생 수정 =====
    let editingId = null;
    const editCheck = bindPositionCheck($('editRow'), $('editCol'), $('editPositionHint'), () => editingId);
    $('editName').maxLength = config.studentNameMax;

    function openEdit(seat) {
        editingId = seat.id;
        const giveUp = seat.status === 'GIVEUP';
        $('editName').value = seat.name;
        $('editName').classList.remove('is-invalid');
        $('editRow').value = giveUp ? '' : seat.rowIndex;
        $('editCol').value = giveUp ? '' : seat.colIndex;
        $('editRow').disabled = giveUp;
        $('editCol').disabled = giveUp;
        $('editRow').placeholder = giveUp ? '중도포기' : '';
        $('editCol').placeholder = giveUp ? '중도포기' : '';
        markPosition($('editRow'), $('editCol'), null);
        if (giveUp) {
            setHint($('editPositionHint'), '중도포기 (자동 설정) - 위치를 지정할 수 없습니다.');
        } else {
            setHint($('editPositionHint'), '');
        }
        SeatApp.openModal($('editStudentModal'));
    }

    $('editStudentForm').addEventListener('submit', async e => {
        e.preventDefault();
        const seat = seats.get(editingId);
        if (!seat) return;
        const name = $('editName').value.trim();
        if (!name) {
            $('editName').classList.add('is-invalid');
            return;
        }
        const body = {name};
        if (seat.status !== 'GIVEUP') {
            body.rowIndex = Number($('editRow').value);
            body.colIndex = Number($('editCol').value);
            const error = positionError(body.rowIndex, body.colIndex, seat.id);
            if (error) {
                markPosition($('editRow'), $('editCol'), false);
                setHint($('editPositionHint'), error, 'error');
                return;
            }
        }
        try {
            const updated = await SeatApp.api('PUT', `/students/${seat.id}`, body);
            seats.set(updated.id, updated);
            render();
            flashRow(updated.id);
            SeatApp.closeModal($('editStudentModal'));
            SeatApp.toast(`${updated.name} 수강생 정보를 수정했습니다.`, 'success');
        } catch (err) {
            setHint($('editPositionHint'), err.message, 'error');
        }
    });

    // ===== 일괄 등록 =====
    $('bulkMax').textContent = config.bulkMax;
    const DIRECT_LINE = /^\s*[^,]+?\s*,\s*\d+\s*,\s*\d+\s*$/;

    function updateBulkPreview() {
        const text = $('bulkText').value;
        const firstLine = text.split(/\r?\n/).find(l => l.trim() !== '');
        if (!firstLine) {
            setHint($('bulkPreview'), '');
            return;
        }
        const direct = DIRECT_LINE.test(firstLine);
        const count = direct
            ? text.split(/\r?\n/).filter(l => l.trim() !== '').length
            : text.split(/[,\r\n]/).filter(t => t.trim() !== '').length;
        setHint($('bulkPreview'), `${direct ? '방법 2 (직접 지정)' : '방법 1 (자동 배치)'} · ${count}명`,
            count > config.bulkMax ? 'error' : '');
    }

    $('bulkText').addEventListener('input', SeatApp.debounce(updateBulkPreview, 200));

    $('bulkBtn').addEventListener('click', () => {
        $('bulkText').value = '';
        $('bulkErrors').hidden = true;
        $('bulkSkipped').hidden = true;
        updateBulkPreview();
        SeatApp.openModal($('bulkModal'));
    });

    function showList(el, items) {
        el.innerHTML = items.map(t => `<li>${SeatApp.escapeHtml(t)}</li>`).join('');
        el.hidden = items.length === 0;
    }

    $('bulkForm').addEventListener('submit', async e => {
        e.preventDefault();
        const text = $('bulkText').value;
        showList($('bulkErrors'), []);
        showList($('bulkSkipped'), []);
        if (!text.trim()) {
            showList($('bulkErrors'), ['등록할 수강생을 입력해주세요.']);
            return;
        }
        $('bulkSubmit').disabled = true;
        try {
            const result = await SeatApp.api('POST', '/students/bulk', {batchName: batch.name, text});
            result.created.forEach(s => seats.set(s.id, s));
            render();
            SeatApp.toast(`${result.createdCount}명을 등록했습니다.`, 'success');
            if (result.skipped.length > 0) {
                showList($('bulkSkipped'), [
                    `${result.skipped.length}명은 건너뛰었습니다:`,
                    ...result.skipped.map(s => `${s.line}번째 줄 ${s.name} - ${s.reason}`)
                ]);
                $('bulkText').value = '';
                updateBulkPreview();
            } else {
                SeatApp.closeModal($('bulkModal'));
            }
        } catch (err) {
            showList($('bulkErrors'), err.details.length ? err.details : [err.message]);
        } finally {
            $('bulkSubmit').disabled = false;
        }
    });

    // ===== 실시간 동기화 =====
    SeatApp.connectBatch(batch.name, msg => {
        if (msg.type === 'DELETED') {
            seats.delete(msg.seatId);
        } else {
            seats.set(msg.seatId, SeatApp.seatFromMessage(msg));
        }
        render();
        if (msg.type !== 'DELETED') flashRow(msg.seatId);
    }, async (connected, isReconnect) => {
        SeatApp.setLiveIndicator(connected);
        if (connected && isReconnect) await resync();
    });

    /** 끊겼던 동안의 변경을 놓치지 않도록 전체 목록을 다시 받는다 */
    async function resync() {
        try {
            const list = await SeatApp.api('GET', `/api/seats/batch/${encodeURIComponent(batch.name)}`);
            seats.clear();
            list.forEach(s => seats.set(s.id, s));
            render();
        } catch (err) {
            SeatApp.toast(err.message, 'error');
        }
    }

    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') resync();
    });

    render();
})();

/* ── WorkMate Frontend — app.js ─────────────────────────────────────────────
   Vanilla JS SPA. No frameworks. All DOM manipulation is explicit.
   Token stored in sessionStorage so it clears on tab close.
───────────────────────────────────────────────────────────────────────────── */

const App = (() => {
  // ── state ──────────────────────────────────────────────────────────────────
  let token       = sessionStorage.getItem('wm_token')  || null;
  let role        = sessionStorage.getItem('wm_role')   || null;
  let userName    = sessionStorage.getItem('wm_name')   || null;
  let employeeId  = parseInt(sessionStorage.getItem('wm_empid') || '0');
  let employees   = [];
  let selectedEmpId = null;
  let cfg         = {};
  let demoMode    = false;
  let activeTab   = 0;

  // Avatar colour palette
  const AVATAR_COLORS = ['#6366f1','#8b5cf6','#ec4899','#0ea5e9','#10b981','#f59e0b','#ef4444','#14b8a6'];

  // ── helpers ────────────────────────────────────────────────────────────────
  const $ = id => document.getElementById(id);
  const show  = id => $(id).classList.remove('hidden');
  const hide  = id => $(id).classList.add('hidden');
  const esc   = s  => (s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  const fv    = v  => parseFloat(v).toFixed(1);

  function avatarColor(name) {
    let h = 0;
    for (let i = 0; i < (name||'').length; i++) h = Math.imul(31, h) + name.charCodeAt(i) | 0;
    return AVATAR_COLORS[Math.abs(h) % AVATAR_COLORS.length];
  }

  function initials(name) {
    return (name || '?').split(' ').map(p => p[0]).join('').substring(0, 2).toUpperCase();
  }

  function formatDate() {
    return new Date().toLocaleDateString('en-US', { weekday:'long', month:'long', day:'numeric', year:'numeric' });
  }

  function cellClass(v, inverted) {
    const n = parseFloat(v);
    if (inverted) return n <= 4 ? 'cv-g' : n <= 6 ? 'cv-y' : 'cv-r';
    return n >= 7 ? 'cv-g' : n >= 5 ? 'cv-y' : 'cv-r';
  }

  // ── API wrapper ────────────────────────────────────────────────────────────
  async function api(method, path, body) {
    const opts = {
      method,
      headers: { 'Content-Type': 'application/json' }
    };
    if (token) opts.headers['Authorization'] = 'Bearer ' + token;
    if (body)  opts.body = JSON.stringify(body);
    const res = await fetch('/api' + path, opts);
    if (res.status === 204) return {};
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw Object.assign(new Error(data.message || res.statusText), { code: data.code, status: res.status });
    return data;
  }

  // ── init ───────────────────────────────────────────────────────────────────
  async function init() {
    cfg = await api('GET', '/config').catch(() => ({}));

    if (!cfg.passwordSet) {
      showSetupPage();
      return;
    }

    // load employee list for login
    employees = await api('GET', '/employees').catch(() => []);

    // if we have a stored session, try to restore it
    if (token) {
      try {
        const me = await api('GET', '/auth/me');
        role = me.role; userName = me.name; employeeId = me.employeeId;
        enterApp();
        return;
      } catch {
        clearSession();
      }
    }

    showLoginPage();
  }

  // ── setup ──────────────────────────────────────────────────────────────────
  function showSetupPage() { hide('page-login'); hide('page-app'); show('page-setup'); }

  async function submitSetup() {
    const p1 = $('setupPwd').value;
    const p2 = $('setupPwd2').value;
    const err = $('setup-err');
    err.classList.add('hidden');
    if (!p1) { err.textContent = 'Password required.'; err.classList.remove('hidden'); return; }
    if (p1 !== p2) { err.textContent = 'Passwords do not match.'; err.classList.remove('hidden'); return; }
    try {
      await api('POST', '/auth/setup', { password: p1 });
      cfg.passwordSet = true;
      employees = await api('GET', '/employees').catch(() => []);
      showLoginPage();
    } catch(e) {
      err.textContent = e.message || 'Setup failed.';
      err.classList.remove('hidden');
    }
  }

  // ── login page ─────────────────────────────────────────────────────────────
  function showLoginPage() {
    hide('page-setup'); hide('page-app'); show('page-login');
    renderEmpList();
  }

  function renderEmpList() {
    const el = $('empList');
    if (!employees.length) {
      el.innerHTML = '<div style="padding:16px;text-align:center;color:var(--text-muted)">No employees found.</div>';
      return;
    }
    el.innerHTML = employees.map(e => `
      <div class="emp-list-item${selectedEmpId===e.id?' selected':''}" onclick="App.selectEmp(${e.id})">
        <span class="avatar avatar-sm" style="background:${avatarColor(e.name)}">${initials(e.name)}</span>
        <div>
          <div class="emp-list-name">${esc(e.name)}</div>
          <div class="emp-list-pos">${esc(e.position)} · ${esc(e.department)}</div>
        </div>
      </div>`).join('');
  }

  function selectEmp(id) {
    selectedEmpId = id;
    renderEmpList();
  }

  function loginEmployee() {
    if (!selectedEmpId) { alert('Please select your name first.'); return; }
    const emp = employees.find(e => e.id === selectedEmpId);
    openModal(`
      <h2 class="modal-title">🔒 Enter PIN</h2>
      <p style="color:var(--text-secondary);font-size:0.85rem;margin-bottom:16px">
        ${esc(emp?.name)} — enter your 4-digit PIN
      </p>
      <div class="field-group">
        <label class="field-label">PIN</label>
        <input class="field-input mono" type="password" id="empPin" maxlength="4"
               placeholder="• • • •" style="letter-spacing:0.3em;font-size:1.2rem;text-align:center"
               onkeydown="if(event.key==='Enter')App.submitEmployeePin()">
      </div>
      <div id="pin-err" class="hidden" style="color:var(--accent-danger);font-size:0.82rem;margin-bottom:8px"></div>
      <div class="modal-footer">
        <button class="btn btn-ghost" onclick="App.closeModal()">Back</button>
        <button class="btn btn-primary" onclick="App.submitEmployeePin()">Login →</button>
      </div>`);
    setTimeout(() => $('empPin')?.focus(), 50);
  }

  async function submitEmployeePin() {
    const pin = $('empPin')?.value || '';
    const err = $('pin-err');
    err.classList.add('hidden');
    try {
      const res = await api('POST', '/auth/login', { type: 'employee', employeeId: selectedEmpId, pin });
      closeModal();
      saveSession(res.token, res.role, res.name, res.employeeId);
      enterApp();
    } catch(e) {
      err.textContent = e.code === 'WRONG_PIN' ? 'Wrong PIN. Try again.' : e.message;
      err.classList.remove('hidden');
      $('empPin').value = '';
      $('empPin').focus();
    }
  }

  function showManagerModal() {
    openModal(`
      <h2 class="modal-title">🔐 Manager Login</h2>
      <div class="field-group">
        <label class="field-label">Password</label>
        <input class="field-input" type="password" id="mgrPwd" placeholder="Manager password" onkeydown="if(event.key==='Enter')App.loginManager()">
      </div>
      <div id="mgr-err" class="hidden" style="color:var(--accent-danger);font-size:0.82rem;margin-bottom:8px"></div>
      <div class="modal-footer">
        <button class="btn btn-ghost" onclick="App.closeModal()">Cancel</button>
        <button class="btn btn-primary" onclick="App.loginManager()">Login →</button>
      </div>`);
    setTimeout(() => $('mgrPwd')?.focus(), 50);
  }

  async function loginManager() {
    const pwd = $('mgrPwd')?.value || '';
    const err = $('mgr-err');
    try {
      const res = await api('POST', '/auth/login', { type: 'manager', password: pwd });
      closeModal();
      saveSession(res.token, res.role, res.name, -1);
      enterApp();
    } catch(e) {
      err.textContent = 'Wrong password.';
      err.classList.remove('hidden');
    }
  }

  // ── session helpers ────────────────────────────────────────────────────────
  function saveSession(t, r, n, eid) {
    token = t; role = r; userName = n; employeeId = eid;
    sessionStorage.setItem('wm_token',  t);
    sessionStorage.setItem('wm_role',   r);
    sessionStorage.setItem('wm_name',   n);
    sessionStorage.setItem('wm_empid',  eid);
  }

  function clearSession() {
    token = role = userName = null; employeeId = 0;
    sessionStorage.clear();
  }

  async function logout() {
    await api('POST', '/auth/logout').catch(() => {});
    clearSession();
    showLoginPage();
  }

  // ── app shell ──────────────────────────────────────────────────────────────
  function enterApp() {
    hide('page-login'); hide('page-setup');
    const app = $('page-app');
    app.classList.remove('hidden');
    app.style.display = 'flex';

    // topbar
    $('topbarRole').textContent = role === 'MANAGER' ? 'Manager Portal' : 'Employee Portal';
    $('topbarName').textContent = userName;
    const av = $('topbarAvatar');
    av.textContent = initials(userName);
    av.style.background = avatarColor(userName);

    // tabs
    const tabs = role === 'MANAGER'
      ? [['📊','Dashboard'], ['👥','Team Directory'], ['📋','Survey Data']]
      : [['🗒','Directory'],  ['✓','Weekly Check-in'], ['👤','My Profile']];

    $('tabBar').innerHTML = tabs.map((t, i) => `
      <div class="tab${i===0?' active':''}" data-idx="${i}" onclick="App.switchTab(${i})">
        <span class="tab-icon">${t[0]}</span> ${t[1]}
      </div>`).join('');

    switchTab(0);
  }

  function switchTab(idx) {
    activeTab = idx;
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    document.querySelector(`.tab[data-idx="${idx}"]`)?.classList.add('active');

    if (role === 'MANAGER') {
      if (idx === 0) renderDashboard();
      else if (idx === 1) renderManagerDirectory();
      else renderSurveyData();
    } else {
      if (idx === 0) renderEmployeeDirectory();
      else if (idx === 1) renderCheckin();
      else renderProfile();
    }
  }

  function setTabContent(html) { $('tabContent').innerHTML = html; }

  // ── Employee Directory ─────────────────────────────────────────────────────
  let eDirFilter = 'All';

  function renderEmployeeDirectory() {
    setTabContent(`
      <div class="dir-toolbar stagger">
        <div class="search-box">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="color:var(--text-muted)">
            <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
          </svg>
          <input type="text" id="eSearch" placeholder="Search name or position…" oninput="App.refilterDir()">
        </div>
        <div class="filter-pills" id="deptFilters">
          ${getDepts().map(d=>`<button class="filter-pill${d===eDirFilter?' active':''}" onclick="App.setDirFilter('${d}')">${d}</button>`).join('')}
        </div>
      </div>
      <div class="emp-grid stagger" id="eGrid"></div>`);
    refilterDir();
  }

  function getDepts() {
    const set = new Set(['All']);
    employees.forEach(e => set.add(e.department));
    return [...set];
  }

  function setDirFilter(f) {
    eDirFilter = f;
    document.querySelectorAll('.filter-pill').forEach(p => p.classList.remove('active'));
    document.querySelectorAll('.filter-pill').forEach(p => {
      if (p.textContent.trim() === f) p.classList.add('active');
    });
    refilterDir();
  }

  function refilterDir() {
    const q = ($('eSearch')?.value || '').toLowerCase();
    const filtered = employees.filter(e =>
      (eDirFilter === 'All' || e.department === eDirFilter) &&
      (q === '' || e.name.toLowerCase().includes(q) || e.position.toLowerCase().includes(q)));
    $('eGrid').innerHTML = filtered.map(e => `
      <div class="emp-card fade-up" onclick="App.showEmpDetail(${e.id})">
        <span class="avatar avatar-md" style="background:${avatarColor(e.name)}">${initials(e.name)}</span>
        <div>
          <div class="emp-name">${esc(e.name)}</div>
          <div class="emp-pos">${esc(e.position)}</div>
          <span class="badge badge-dept mt-4">${esc(e.department)}</span>
        </div>
      </div>`).join('') || '<div class="text-muted" style="text-align:center;padding:40px">No results.</div>';
  }

  function showEmpDetail(id) {
    const e = employees.find(x => x.id === id);
    if (!e) return;
    const resetPinBtn = role === 'MANAGER'
      ? `<button class="btn btn-ghost btn-sm" onclick="App.showChangePinModal(${e.id},'${esc(e.name)}')">🔑 Reset PIN</button>`
      : '';
    openModal(`
      <h2 class="modal-title">${esc(e.name)}</h2>
      ${[['📧','Email',e.email],['📱','Phone',e.phone],['🏢','Department',e.department],['💼','Position',e.position]]
        .map(([ic,l,v])=>`<div class="profile-row"><span class="profile-icon">${ic}</span>
          <span class="profile-label">${l}</span><span class="profile-value">${esc(v)}</span></div>`).join('')}
      <div class="modal-footer">${resetPinBtn}<button class="btn btn-ghost" onclick="App.closeModal()">Close</button></div>`);
  }

  // ── Weekly Check-in ────────────────────────────────────────────────────────
  function renderCheckin() {
    const now  = new Date();
    const day  = now.getDay();                 // 0=Sun,1=Mon…
    const isoDay = day === 0 ? 7 : day;        // ISO: 1=Mon…7=Sun
    const checkinDays = (cfg.checkinDays || '4,5').split(',').map(Number);
    const isOpen = checkinDays.includes(isoDay) || demoMode;

    if (!isOpen) {
      const names = ['Sunday','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday'];
      const nextOpen = checkinDays[0];
      const daysLeft = ((nextOpen - isoDay + 7) % 7) || 7;
      setTabContent(`
        <div class="checkin-wrap">
          <div class="card checkin-card fade-up">
            <div class="locked-wrap">
              <div class="locked-icon">🗓</div>
              <div class="locked-title">Check-in opens Thursday</div>
              <p class="locked-sub">The weekly wellbeing check-in is available <strong>Thursday and Friday</strong> only.<br>Today is <strong>${names[day]}</strong>.</p>
              <div class="locked-badge">Opens in ${daysLeft} day${daysLeft!==1?'s':''}</div><br>
              <span class="text-muted">Running a demo?</span><br>
              <button class="btn btn-ghost btn-sm mt-8" onclick="App.enableDemo()">Open in demo mode</button>
            </div>
          </div>
        </div>`);
      return;
    }
    renderCheckinForm();
  }

  function enableDemo() { demoMode = true; renderCheckin(); }

  function renderCheckinForm() {
    setTabContent(`
      <div class="checkin-wrap">
        <div class="card checkin-card fade-up" id="ciCard">
          <div class="checkin-title">Weekly Wellbeing Check-in</div>
          <div class="checkin-date">${formatDate()}</div>

          <div class="section-title">How are you feeling?</div>
          <div class="slider-grid stagger">
            ${[
              ['Mood',         'Overall emotional state',            '😔','😊','sl-mood'],
              ['Energy',       'Physical & mental energy',           '🪴','⚡','sl-energy'],
              ['Motivation',   'Drive to engage with work',          '😴','🚀','sl-motiv'],
              ['Stress',       'Current pressure level',             '😌','😰','sl-stress']
            ].map(([l,hint,lo,hi,id])=>sliderItem(l,hint,lo,hi,id)).join('')}
          </div>
          <div class="slider-grid stagger" style="margin-top:14px">
            ${[
              ['Work-Life Balance','Balance between work & life',   '⚖️','🏡','sl-wlb'],
              ['Team Connection',  'Feeling supported by the team', '🧱','🤝','sl-team'],
              ['Accomplishment',   'Progress on meaningful goals',  '😐','🎯','sl-acc']
            ].map(([l,hint,lo,hi,id])=>sliderItem(l,hint,lo,hi,id)).join('')}
            <div></div>
          </div>

          <!-- real-time score -->
          <div class="score-bar mt-16">
            <div>
              <div class="score-label">Your wellbeing score</div>
              <div class="text-muted">Updates as you move the sliders</div>
            </div>
            <div style="text-align:right">
              <div class="score-value mono" id="liveScore" style="color:var(--accent-success)">—</div>
              <div class="text-muted">/ 10</div>
            </div>
          </div>

          <div class="section-title">Work context</div>
          <div class="grid-2">
            <div class="field-group">
              <label class="field-label">Workload this week</label>
              <select class="field-select" id="ci-workload">
                <option value="light">Light — had capacity to spare</option>
                <option value="manageable" selected>Manageable — just right</option>
                <option value="heavy">Heavy — struggled to keep up</option>
                <option value="overwhelming">Overwhelming — could not cope</option>
              </select>
            </div>
            <div class="field-group">
              <label class="field-label">Team communication</label>
              <select class="field-select" id="ci-teamcomm">
                <option value="excellent">Excellent — clear &amp; supportive</option>
                <option value="good" selected>Good — mostly worked well</option>
                <option value="average">Average — some gaps</option>
                <option value="poor">Poor — frequent misalignment</option>
              </select>
            </div>
          </div>

          <div class="section-title">Open reflection (anonymous)</div>
          <div class="field-group">
            <label class="field-label">What went well for you this week?</label>
            <textarea class="field-textarea" id="ci-wentwell" placeholder="e.g. Finished the project milestone…"></textarea>
          </div>
          <div class="field-group">
            <label class="field-label">What was your biggest challenge?</label>
            <textarea class="field-textarea" id="ci-challenges" placeholder="e.g. Too many meetings…"></textarea>
          </div>
          <div class="field-group">
            <label class="field-label">What support would help you most?</label>
            <textarea class="field-textarea" id="ci-support" placeholder="e.g. Clearer expectations…"></textarea>
          </div>

          <div class="section-title">Anything specific to flag?</div>
          <div class="issues-grid stagger">
            ${['Workload too heavy','Unclear expectations','Team conflicts','Lack of recognition',
               'Technical blockers','Personal challenges','Poor work-life balance','Feeling isolated']
              .map((o,i)=>`<label class="checkbox-item" id="cbl${i}">
                <input type="checkbox" id="cb${i}" onchange="App.toggleCb(${i})"> ${o}</label>`).join('')}
          </div>

          <button class="btn btn-primary btn-full btn-lg mt-20" onclick="App.submitCheckin()">
            Submit Wellbeing Check-in →
          </button>
        </div>
      </div>`);

    // wire sliders for live score
    ['sl-mood','sl-energy','sl-motiv','sl-stress','sl-wlb','sl-team','sl-acc']
      .forEach(id => {
        const el = $(id);
        if (!el) return;
        el.addEventListener('input', () => { updateSliderTrack(el); updateLiveScore(); });
        updateSliderTrack(el);
      });
    updateLiveScore();
  }

  function sliderItem(label, hint, lo, hi, id) {
    return `<div class="slider-item">
      <div class="slider-label">${label} <span class="slider-hint">${hint}</span></div>
      <div class="slider-row">
        <span class="slider-emoji">${lo}</span>
        <input type="range" min="1" max="10" value="5" id="${id}"
               oninput="document.getElementById('v${id}').textContent=this.value;App.updateSliderTrack(this);App.updateLiveScore()">
        <span class="slider-emoji">${hi}</span>
        <span class="slider-value mono" id="v${id}">5</span>
      </div>
    </div>`;
  }

  function updateSliderTrack(el) {
    const pct = ((el.value - el.min) / (el.max - el.min)) * 100;
    el.style.setProperty('--val', pct + '%');
  }

  function updateLiveScore() {
    const ids = ['sl-mood','sl-energy','sl-motiv','sl-stress','sl-wlb','sl-team','sl-acc'];
    const vals = ids.map(id => parseFloat($(id)?.value || 5));
    const stress = vals[3];
    const positive = vals[0]+vals[1]+vals[2]+vals[4]+vals[5]+vals[6];
    const score = (positive + (11 - stress)) / 7;
    const el = $('liveScore');
    if (!el) return;
    el.textContent = score.toFixed(1);
    el.style.color = score >= 7 ? 'var(--accent-success)'
                   : score >= 5 ? 'var(--accent-warning)'
                   : 'var(--accent-danger)';
  }

  function toggleCb(i) {
    const lbl = $('cbl' + i);
    if (lbl) lbl.classList.toggle('checked', !!$('cb'+i)?.checked);
  }

  async function submitCheckin() {
    const issues = ['Workload too heavy','Unclear expectations','Team conflicts','Lack of recognition',
      'Technical blockers','Personal challenges','Poor work-life balance','Feeling isolated']
      .filter((_,i) => $('cb'+i)?.checked).join(', ');

    try {
      const res = await api('POST', '/survey/responses', {
        employeeId:       employeeId,
        mood:             +$('sl-mood').value,
        energy:           +$('sl-energy').value,
        motivation:       +$('sl-motiv').value,
        stress:           +$('sl-stress').value,
        workLifeBalance:  +$('sl-wlb').value,
        teamConnection:   +$('sl-team').value,
        accomplishment:   +$('sl-acc').value,
        workloadFeel:     $('ci-workload').value,
        teamCommunication:$('ci-teamcomm').value,
        wentWell:         $('ci-wentwell').value,
        challenges:       $('ci-challenges').value,
        support:          $('ci-support').value,
        issues
      });
      $('ciCard').innerHTML = `
        <div class="thankyou fade-in">
          <div class="ty-ring">
            <svg width="36" height="36" viewBox="0 0 36 36" fill="none">
              <polyline points="7,18 14,25 29,10" stroke="white" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <h2 style="font-size:1.4rem;font-weight:800;margin-bottom:8px">Thank you for checking in!</h2>
          <p style="color:var(--text-secondary)">Your response has been recorded anonymously.<br>
          Risk level: <strong class="risk-${res.risk}">${res.risk}</strong> &nbsp; Your wellbeing matters.</p>
        </div>`;
    } catch(e) { alert('Submission failed: ' + e.message); }
  }

  // ── My Profile ─────────────────────────────────────────────────────────────
  function renderProfile() {
    const emp = employees.find(e => e.id === employeeId) || {};
    setTabContent(`
      <div class="profile-wrap">
        <div class="card profile-card fade-up" style="padding:36px 40px">
          <span class="avatar avatar-xl" style="background:${avatarColor(emp.name||'?')};margin:0 auto">
            ${initials(emp.name)}
          </span>
          <div class="profile-name">${esc(emp.name)}</div>
          <div class="profile-pos">${esc(emp.position)}</div>
          <span class="badge badge-dept mt-8">${esc(emp.department)}</span>
          <div class="profile-divider"></div>
          ${[['📧','Email',emp.email],['📱','Phone',emp.phone],['🏢','Department',emp.department],['🆔','Employee ID','#'+emp.id]]
            .map(([ic,l,v])=>`<div class="profile-row">
              <span class="profile-icon">${ic}</span>
              <span class="profile-label">${l}</span>
              <span class="profile-value">${esc(v)}</span>
            </div>`).join('')}
          <button class="btn btn-ghost btn-full mt-20"
            onclick="alert('HR Department\\nAnna Bērziņa\\nEmail: anna.berzina@company.lv\\nPhone: +371 20000001')">
            📞 Contact HR
          </button>
          <button class="btn btn-ghost btn-full mt-8" onclick="App.showChangePinModal()">
            🔑 Change my PIN
          </button>
        </div>
      </div>`);
  }

  // ── Manager Dashboard ──────────────────────────────────────────────────────
  async function renderDashboard() {
    setTabContent('<div style="color:var(--text-muted);text-align:center;padding:40px">Loading dashboard…</div>');
    try {
      const s = await api('GET', '/survey/summary');
      const scoreColor = v => parseFloat(v) >= 7 ? 'var(--accent-success)'
                             : parseFloat(v) >= 5 ? 'var(--accent-warning)'
                             : 'var(--accent-danger)';
      const stressColor = v => parseFloat(v) <= 4 ? 'var(--accent-success)'
                              : parseFloat(v) <= 6 ? 'var(--accent-warning)'
                              : 'var(--accent-danger)';
      const rateColor = v => parseFloat(v) > 70 ? 'var(--accent-success)' : 'var(--accent-warning)';

      setTabContent(`
        <div class="metrics-grid stagger">
          ${[
            ['Avg Mood',       s.avgMood,       scoreColor(s.avgMood),    '😊','primary'],
            ['Avg Energy',     s.avgEnergy,     scoreColor(s.avgEnergy),  '⚡','primary'],
            ['Avg Stress',     s.avgStress,     stressColor(s.avgStress), '😰','warning'],
            ['Response Rate',  s.responseRate,  rateColor(s.responseRate),'📊','success'],
          ].map(([l,v,c,ic,cls])=>`
          <div class="metric-card ${cls} fade-up">
            <div class="metric-trend">${ic}</div>
            <div class="metric-value mono" style="color:${c}">${l.includes('Rate')?parseFloat(v).toFixed(0)+'%':parseFloat(v).toFixed(1)}</div>
            <div class="metric-label">${l}</div>
          </div>`).join('')}
        </div>

        <div class="card mb-12" style="margin-bottom:16px">
          <div class="flex-between mb-12">
            <h3>Team Overview by Week</h3>
            <div style="display:flex;gap:14px;flex-wrap:wrap">
              ${[['#1d4ed8','Mood'],['#7c3aed','Motivation'],['#dc2626','Stress'],['#0891b2','Energy'],['#16a34a','WLB']]
                .map(([c,l])=>`<span style="font-size:0.75rem;color:var(--text-muted);display:flex;align-items:center;gap:4px">
                  <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${c}"></span>${l}</span>`).join('')}
            </div>
          </div>
          <div class="chart-wrap">
            <canvas id="weekChart" width="800" height="160"></canvas>
          </div>
        </div>

        <div class="dash-grid-3">
          <div class="card">
            <div class="card-title">Burnout Risk Alerts</div>
            ${s.highRisk && s.highRisk.length
              ? s.highRisk.map(e=>`<div class="alert-row">
                  <span style="color:var(--accent-danger);font-size:1.1rem">⚠</span>
                  <div>
                    <div class="alert-name">${esc(e.name)}</div>
                    <div class="alert-dept">${esc(e.department)} · Score ${parseFloat(e.score).toFixed(1)}</div>
                  </div>
                </div>`).join('')
              : '<div style="color:var(--accent-success);font-size:0.85rem">✔ No high-risk employees this week</div>'}
          </div>
          <div class="card">
            <div class="card-title">Quick Actions</div>
            <button class="btn btn-ghost btn-full" style="margin-bottom:8px" onclick="App.switchTab(1)">👥 View Team Directory</button>
            <button class="btn btn-ghost btn-full" onclick="App.switchTab(2)">📋 View Survey Data</button>
          </div>
        </div>`);

      // draw canvas bar chart
      drawWeekChart('weekChart', s.weeklyStats || []);
    } catch(e) {
      setTabContent(`<div style="color:var(--accent-danger);text-align:center;padding:40px">Error loading dashboard: ${esc(e.message)}</div>`);
    }
  }

  function drawWeekChart(canvasId, weeks) {
    const canvas = $(canvasId);
    if (!canvas || !weeks.length) return;
    const ctx = canvas.getContext('2d');
    const W = canvas.parentElement.clientWidth || 800;
    const H = 160;
    canvas.width = W; canvas.height = H;

    const dims = ['mood','motivation','stress','energy','wlb'];
    const colors = ['#1d4ed8','#7c3aed','#dc2626','#0891b2','#16a34a'];
    const barW = 12; const gap = 4;
    const groupW = dims.length * (barW + gap) - gap;
    const weekGap = (W - 60) / weeks.length;
    const maxV = 10; const chartH = H - 30;

    ctx.clearRect(0, 0, W, H);
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, W, H);

    // horizontal grid lines
    for (let y = 2; y <= 10; y += 2) {
      const yPx = chartH - (y / maxV) * chartH;
      ctx.beginPath(); ctx.moveTo(40, yPx); ctx.lineTo(W - 10, yPx);
      ctx.strokeStyle = '#e5e7eb'; ctx.lineWidth = 1; ctx.stroke();
      ctx.fillStyle = '#9ca3af'; ctx.font = '10px Roboto Mono, monospace';
      ctx.fillText(y, 10, yPx + 4);
    }

    weeks.forEach((wk, wi) => {
      const cx = 50 + wi * weekGap + weekGap / 2 - groupW / 2;
      dims.forEach((d, di) => {
        const v = parseFloat(wk[d]) || 0;
        const bh = Math.max(4, (v / maxV) * chartH);
        const x = cx + di * (barW + gap);
        const y = chartH - bh;

        // bar gradient
        const grad = ctx.createLinearGradient(x, y, x, chartH);
        grad.addColorStop(0, colors[di]);
        grad.addColorStop(1, colors[di] + '44');
        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.roundRect ? ctx.roundRect(x, y, barW, bh, [3, 3, 0, 0])
                      : ctx.rect(x, y, barW, bh);
        ctx.fill();
      });
      // week label
      ctx.fillStyle = '#6b7280';
      ctx.font = '10px Inter, sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText(wk.week || ('Wk ' + (wi+1)), 50 + wi * weekGap + weekGap/2, H - 4);
    });
    ctx.textAlign = 'left';
  }

  // ── Manager Directory ──────────────────────────────────────────────────────
  let mDirFilter = 'All';

  function renderManagerDirectory() {
    setTabContent(`
      <div class="dir-toolbar stagger">
        <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
          <h2 style="font-size:1rem;font-weight:700">${employees.length} Team Members</h2>
          <button class="btn btn-primary btn-sm" onclick="App.showAddEmployee()">+ Add Employee</button>
        </div>
        <div class="search-box">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="color:var(--text-muted)">
            <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
          </svg>
          <input type="text" id="mSearch" placeholder="Search…" oninput="App.refilterMDir()">
        </div>
      </div>
      <div class="filter-pills mb-12" id="mDeptFilters">
        ${getDepts().map(d=>`<button class="filter-pill${d===mDirFilter?' active':''}" onclick="App.setMDirFilter('${d}')">${d}</button>`).join('')}
      </div>
      <div class="emp-grid stagger" id="mGrid"></div>`);
    refilterMDir();
  }

  function setMDirFilter(f) {
    mDirFilter = f;
    document.querySelectorAll('#mDeptFilters .filter-pill').forEach(p => {
      p.classList.toggle('active', p.textContent.trim() === f);
    });
    refilterMDir();
  }

  function refilterMDir() {
    const q = ($('mSearch')?.value || '').toLowerCase();
    const filtered = employees.filter(e =>
      (mDirFilter === 'All' || e.department === mDirFilter) &&
      (q === '' || e.name.toLowerCase().includes(q) || e.position.toLowerCase().includes(q)));
    const grid = $('mGrid');
    if (!grid) return;
    grid.innerHTML = filtered.map(e => `
      <div class="emp-card fade-up" onclick="App.showEmpDetail(${e.id})">
        <span class="avatar avatar-md" style="background:${avatarColor(e.name)}">${initials(e.name)}</span>
        <div>
          <div class="emp-name">${esc(e.name)}</div>
          <div class="emp-pos">${esc(e.position)}</div>
          <span class="badge badge-dept mt-4">${esc(e.department)}</span>
          ${e.isManager ? '<span class="badge badge-success mt-4" style="margin-left:4px">Manager</span>' : ''}
        </div>
      </div>`).join('');
  }

  function showAddEmployee() {
    openModal(`
      <h2 class="modal-title">Add Employee</h2>
      ${[['Full Name','name'],['Position','position'],['Department','department'],['Email','email'],['Phone','phone']]
        .map(([l,k])=>`<div class="field-group">
          <label class="field-label">${l}</label>
          <input class="field-input" type="text" id="ef-${k}" placeholder="${l}">
        </div>`).join('')}
      <div id="add-err" class="hidden" style="color:var(--accent-danger);font-size:0.82rem;margin-bottom:8px"></div>
      <div class="modal-footer">
        <button class="btn btn-ghost" onclick="App.closeModal()">Cancel</button>
        <button class="btn btn-primary" onclick="App.addEmployee()">Add Employee</button>
      </div>`);
  }

  async function addEmployee() {
    const name = $('ef-name')?.value.trim();
    const err  = $('add-err');
    if (!name) { err.textContent = 'Name is required.'; err.classList.remove('hidden'); return; }
    try {
      const e = await api('POST', '/employees', {
        name, position:   $('ef-position')?.value.trim(),
        department:        $('ef-department')?.value.trim(),
        email:             $('ef-email')?.value.trim(),
        phone:             $('ef-phone')?.value.trim()
      });
      employees.push(e);
      closeModal();
      renderManagerDirectory();
    } catch(ex) {
      err.textContent = ex.message;
      err.classList.remove('hidden');
    }
  }

  // ── Survey Data ────────────────────────────────────────────────────────────
  let svWeek = '';

  async function renderSurveyData() {
    setTabContent('<div style="color:var(--text-muted);text-align:center;padding:40px">Loading…</div>');
    try {
      const params = svWeek ? `?week=${encodeURIComponent(svWeek)}` : '';
      const rows   = await api('GET', '/survey/responses' + params);

      // group by week
      const byWeek = {};
      rows.forEach(r => { const k = r.weekStart; if (!byWeek[k]) byWeek[k] = []; byWeek[k].push(r); });
      const sortedWeeks = Object.keys(byWeek).sort();
      const allWeeks = [...new Set(rows.map(r => r.weekStart))].sort();

      function avgArr(arr, key) {
        if (!arr.length) return 0;
        return (arr.reduce((s,r) => s + (r[key]||0), 0) / arr.length).toFixed(1);
      }
      function cv(v, inv) { return `<span class="cell-val ${cellClass(v,inv)}">${v}</span>`; }
      function wlBadge(w) { return `<span class="badge wl-${w}">${w}</span>`; }
      function riskBadge(r) { return `<span class="risk-${r}" style="font-size:0.75rem;font-weight:700">${r}</span>`; }

      const weeksHtml = sortedWeeks.map(wk => {
        const rs = byWeek[wk];
        const avg = {
          mood: avgArr(rs,'mood'), energy: avgArr(rs,'energy'), motiv: avgArr(rs,'motivation'),
          stress: avgArr(rs,'stress'), wlb: avgArr(rs,'workLifeBalance'),
          team: avgArr(rs,'teamConnection'), acc: avgArr(rs,'accomplishment')
        };
        const high = rs.filter(r=>r.risk==='HIGH').length;
        const med  = rs.filter(r=>r.risk==='MEDIUM').length;
        return `<div class="week-block fade-up">
          <div class="week-header">
            <div>
              <div class="week-header-title">Week ${wk}</div>
              <div style="font-size:0.75rem;color:var(--text-muted)">${rs.length} response${rs.length!==1?'s':''}</div>
            </div>
            <div class="week-header-meta">
              ${high ? `<span class="badge badge-danger">${high} HIGH</span>` : ''}
              ${med  ? `<span class="badge badge-warning">${med} MED</span>`  : ''}
              <span class="badge badge-muted">Mood ${avg.mood} · Stress ${avg.stress}</span>
            </div>
          </div>
          <div class="sv-table-wrap">
            <table class="sv-table">
              <thead><tr>
                <th>Employee</th><th>Dept</th>
                <th title="Mood">😊</th><th title="Energy">⚡</th><th title="Motivation">🚀</th>
                <th title="Stress">😰</th><th title="WLB">⚖</th><th title="Team">🤝</th><th title="Accomplish">🎯</th>
                <th>Workload</th><th>Risk</th><th>Highlights</th>
              </tr></thead>
              <tbody>
                ${rs.map(r=>`<tr>
                  <td style="font-weight:600;white-space:nowrap">${esc(r.name)}</td>
                  <td><span class="badge badge-dept">${esc(r.department)}</span></td>
                  <td>${cv(r.mood,false)}</td><td>${cv(r.energy,false)}</td><td>${cv(r.motivation,false)}</td>
                  <td>${cv(r.stress,true)}</td><td>${cv(r.workLifeBalance,false)}</td>
                  <td>${cv(r.teamConnection,false)}</td><td>${cv(r.accomplishment,false)}</td>
                  <td>${wlBadge(r.workloadFeel)}</td>
                  <td>${riskBadge(r.risk)}</td>
                  <td style="max-width:160px;font-size:0.78rem;color:var(--text-muted)">
                    ${r.wentWell ? '✓ ' + esc(r.wentWell.substring(0,50)) + (r.wentWell.length>50?'…':'') : '—'}
                  </td></tr>`).join('')}
                <tr class="avg-row">
                  <td colspan="2">TEAM AVERAGES</td>
                  <td>${avg.mood}</td><td>${avg.energy}</td><td>${avg.motiv}</td>
                  <td>${avg.stress}</td><td>${avg.wlb}</td><td>${avg.team}</td><td>${avg.acc}</td>
                  <td colspan="3">${rs.length} responses</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>`;
      }).join('') || '<div class="text-muted" style="text-align:center;padding:40px">No responses found.</div>';

      setTabContent(`
        <div class="sv-toolbar stagger">
          <h2 style="font-size:1rem;font-weight:700">Survey Responses</h2>
          <button class="btn btn-ghost btn-sm" onclick="App.exportCsv()">⬇ Export CSV</button>
        </div>
        <div class="week-tabs">
          <button class="week-tab${svWeek===''?' active':''}" onclick="App.setSvWeek('')">All Weeks</button>
          ${allWeeks.map(w=>`<button class="week-tab${svWeek===w?' active':''}" onclick="App.setSvWeek('${w}')">${w}</button>`).join('')}
        </div>
        ${weeksHtml}`);
    } catch(e) {
      setTabContent(`<div style="color:var(--accent-danger);text-align:center;padding:40px">Error: ${esc(e.message)}</div>`);
    }
  }

  function setSvWeek(w) { svWeek = w; renderSurveyData(); }

  async function exportCsv() {
    const a = document.createElement('a');
    const res = await fetch('/api/survey/export/csv', {
      headers: { 'Authorization': 'Bearer ' + token }
    });
    const blob = await res.blob();
    a.href = URL.createObjectURL(blob);
    a.download = 'workmate_export.csv';
    a.click();
  }

  // ── PIN management ─────────────────────────────────────────────────────────
  function showChangePinModal(empId, empName) {
    const targetId   = empId   || employeeId;
    const targetName = empName || userName;
    openModal(`
      <h2 class="modal-title">🔑 Change PIN</h2>
      <p style="color:var(--text-secondary);font-size:0.85rem;margin-bottom:16px">${esc(targetName)}</p>
      <div class="field-group">
        <label class="field-label">New PIN (4 digits)</label>
        <input class="field-input mono" type="password" id="newPin" maxlength="4"
               placeholder="• • • •" style="letter-spacing:0.3em;font-size:1.2rem;text-align:center">
      </div>
      <div class="field-group">
        <label class="field-label">Confirm PIN</label>
        <input class="field-input mono" type="password" id="newPin2" maxlength="4"
               placeholder="• • • •" style="letter-spacing:0.3em;font-size:1.2rem;text-align:center"
               onkeydown="if(event.key==='Enter')App.submitChangePin(${targetId})">
      </div>
      <div id="chpin-err" class="hidden" style="color:var(--accent-danger);font-size:0.82rem;margin-bottom:8px"></div>
      <div class="modal-footer">
        <button class="btn btn-ghost" onclick="App.closeModal()">Cancel</button>
        <button class="btn btn-primary" onclick="App.submitChangePin(${targetId})">Save PIN</button>
      </div>`);
    setTimeout(() => $('newPin')?.focus(), 50);
  }

  async function submitChangePin(targetId) {
    const p1  = $('newPin')?.value  || '';
    const p2  = $('newPin2')?.value || '';
    const err = $('chpin-err');
    err.classList.add('hidden');
    if (p1.length < 4) { err.textContent = 'PIN must be 4 digits.'; err.classList.remove('hidden'); return; }
    if (p1 !== p2)     { err.textContent = 'PINs do not match.';     err.classList.remove('hidden'); return; }
    try {
      const body = role === 'MANAGER'
        ? { newPin: p1, employeeId: targetId }
        : { newPin: p1 };
      await api('POST', '/auth/pin', body);
      closeModal();
      alert('PIN changed successfully!');
    } catch(e) {
      err.textContent = e.message;
      err.classList.remove('hidden');
    }
  }

  // ── Modal ──────────────────────────────────────────────────────────────────
  function openModal(html) {
    $('modalBody').innerHTML = html;
    $('overlay').classList.remove('hidden');
  }
  function closeModal() { $('overlay').classList.add('hidden'); }

  // ── public API ─────────────────────────────────────────────────────────────
  return {
    init, submitSetup, showLoginPage, selectEmp, loginEmployee, submitEmployeePin,
    showManagerModal, loginManager, logout,
    switchTab, setDirFilter, refilterDir, showEmpDetail,
    setMDirFilter, refilterMDir, showAddEmployee, addEmployee,
    renderCheckin, enableDemo, updateSliderTrack, updateLiveScore,
    submitCheckin, toggleCb,
    setSvWeek, exportCsv,
    showChangePinModal, submitChangePin,
    openModal, closeModal
  };
})();

document.addEventListener('DOMContentLoaded', App.init);

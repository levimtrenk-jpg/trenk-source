// רכיבים משותפים לכל האתרים: כפתור ⬇, חלון ההורדה, ומעקב אחרי שינויים בדף.
// הכול בתוך Shadow DOM כדי שה-CSS של האתר לא ישבור את העיצוב ולהפך.
(() => {
  if (window.UD) return;

  // מחרוזות מ-_locales (עברית ברירת מחדל, אנגלית לפי שפת כרום) וכיוון בהתאם
  const t = (key, ...args) => chrome.i18n.getMessage(key, args.map(String)) || key;
  const DIR = chrome.i18n.getMessage("dir") || "rtl";

  const ICON = `<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
    <path fill="currentColor" d="M11 3h2v10.17l3.59-3.58L18 11l-6 6-6-6 1.41-1.41L11 13.17V3zM5 19h14v2H5z"/></svg>`;

  // ---------- תקשורת עם background ----------
  async function send(msg) {
    try {
      const res = await chrome.runtime.sendMessage(msg);
      return res || { ok: false, error: t("noReply") };
    } catch (e) {
      // אחרי עדכון/טעינה מחדש של התוסף, הסקריפט הישן בדף מנותק
      return { ok: false, error: t("extUpdated") };
    }
  }

  // ---------- עזרים ----------
  const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  function fmtDuration(sec) {
    if (!sec) return "";
    sec = Math.round(sec);
    const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
    const pad = (n) => String(n).padStart(2, "0");
    return h ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`;
  }

  function fmtBytes(n) {
    if (!n) return "";
    if (n >= 1024 ** 3) return (n / 1024 ** 3).toFixed(1) + " GB";
    if (n >= 1024 ** 2) return (n / 1024 ** 2).toFixed(n >= 100 * 1024 ** 2 ? 0 : 1) + " MB";
    return Math.max(1, Math.round(n / 1024)) + " KB";
  }

  // ---------- כפתור (עיצוב TRENK) ----------
  // עמודה: המילה TRENK זעירה מעל עיגול עם מסגרת גרדיאנט מסתובבת וחץ לבן באמצע.
  const BUTTON_ICON = `<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true"><path d="M12 4v11M7 10l5 5 5-5M5 20h14"/></svg>`;

  const BUTTON_CSS = `
    :host { all: initial; display: inline-flex; --s: 50px; }
    /* יוטיוב בדף צפייה: שורה של כפתורים בגובה 36px, אז העיגול קטן כדי שהגובה הכולל יישאר 36 */
    :host([data-ud-button="inline"]) { --s: 28px; }
    button { all: unset; box-sizing: border-box; cursor: pointer; display: inline-flex; flex-direction: column;
      align-items: center; gap: 2px; -webkit-tap-highlight-color: transparent; }
    button:focus-visible .c { outline: 2px solid #8b6cff; outline-offset: 2px; }
    .t { font-family: "JetBrains Mono", ui-monospace, monospace; font-size: 6.5px; line-height: 1; letter-spacing: .35em;
      font-weight: 700; padding-left: .35em; background: linear-gradient(90deg, #ffb84d, #ff5e8a, #8b6cff);
      -webkit-background-clip: text; background-clip: text; color: transparent; }
    /* מעל סרטון: צל קטן כדי שהמילה תיקרא גם על תמונה בהירה */
    :host([data-ud-button="overlay"]) .t { filter: drop-shadow(0 1px 1px rgba(0,0,0,.7)); }
    .c { box-sizing: border-box; width: var(--s); height: var(--s); border-radius: 50%; padding: 2px;
      background: conic-gradient(from var(--a, 0deg), #ffb84d, #ff5e8a, #8b6cff, #ffb84d);
      animation: spin 3s linear infinite; transition: transform .15s; }
    .c i { display: grid; place-items: center; width: 100%; height: 100%; border-radius: 50%; background: #111319; color: #fff; }
    :host([data-ud-button="inline"]) svg { width: 15px; height: 15px; }
    button:hover .c { transform: scale(1.06); }
    button:active .c { transform: scale(.94); }
    @property --a { syntax: "<angle>"; inherits: false; initial-value: 0deg; }
    @keyframes spin { to { --a: 360deg; } }
    @media (prefers-reduced-motion: reduce) { .c { animation: none; } }
  `;

  // @property לא נרשם מתוך Shadow DOM, רק מה-document הראשי. בלי זה העיגול לא מסתובב.
  function registerSpinProperty() {
    if (document.getElementById("ud-spin-property")) return;
    const style = document.createElement("style");
    style.id = "ud-spin-property";
    style.textContent = '@property --a { syntax: "<angle>"; inherits: false; initial-value: 0deg; }';
    (document.head || document.documentElement).appendChild(style);
  }

  /**
   * יוצר כפתור הורדה.
   * getUrl: פונקציה שמחזירה את הקישור של הסרטון הספציפי ברגע הלחיצה.
   * variant: inline (שורת כפתורים ביוטיוב) | column (עמודת פעולות) | overlay (פינת סרטון)
   */
  function makeButton(getUrl, variant = "overlay") {
    registerSpinProperty();
    const host = document.createElement("div");
    host.setAttribute("data-ud-button", variant);
    const root = host.attachShadow({ mode: "open" });
    root.innerHTML = `<style>${BUTTON_CSS}</style><button title="${t("buttonTitle")}" aria-label="${t("buttonLabel")}">` +
      `<span class="t" aria-hidden="true">TRENK</span><span class="c"><i>${BUTTON_ICON}</i></span></button>`;
    const btn = root.querySelector("button");
    // לא לתת לאתר "לבלוע" את הלחיצה (טיקטוק/אינסטגרם פותחים את הסרטון בלחיצה)
    for (const ev of ["pointerdown", "mousedown", "pointerup", "mouseup", "dblclick"]) {
      btn.addEventListener(ev, (e) => e.stopPropagation());
    }
    // לחיצה רגילה וגם מקלדת (Enter/רווח). לחיצת עכבר על כפתור overlay נתפסת קודם ב-interceptOverlayClicks.
    btn.addEventListener("click", (e) => {
      e.preventDefault();
      e.stopPropagation();
      console.debug("[UD] button click", variant);
      panel.toggle(host, getUrl());
    });
    host._udGetUrl = getUrl;
    return host;
  }

  // ---------- כפתורים מעל סרטון ----------
  // באינסטגרם יש DIV שקוף מעל הסרטון שמקבל את הלחיצה (elementFromPoint מחזיר אותו, לא את הכפתור),
  // ולכן z-index לבד לא מספיק. תופסים את הלחיצה ב-capture על window, לפי המיקום, לפני שהאתר רואה אותה.
  const overlays = new Set();

  function overlayAt(x, y) {
    for (const host of overlays) {
      if (!host.isConnected) { overlays.delete(host); continue; }
      const r = host.getBoundingClientRect();
      if (r.width && x >= r.left && x <= r.right && y >= r.top && y <= r.bottom) return host;
    }
    return null;
  }

  function interceptOverlayClicks(e) {
    if (e.clientX === 0 && e.clientY === 0 && e.detail === 0) return; // הפעלה ממקלדת: מטופלת בכפתור עצמו
    if (panel.host && e.composedPath().includes(panel.host)) return; // לחיצות בתוך החלון
    const host = overlayAt(e.clientX, e.clientY);
    if (!host) return;
    e.preventDefault();
    e.stopImmediatePropagation();
    if (e.type === "click") {
      console.debug("[UD] overlay click intercepted", e.target?.tagName);
      panel.toggle(host, host._udGetUrl());
    }
  }

  for (const type of ["pointerdown", "mousedown", "touchstart", "pointerup", "mouseup", "touchend", "click", "dblclick"]) {
    window.addEventListener(type, interceptOverlayClicks, { capture: true, passive: false });
  }

  /** שם כפתור overlay בפינה של אלמנט (בדרך כלל ההורה של ה-video). */
  function attachOverlay(container, getUrl) {
    if (!container || container.querySelector(":scope > [data-ud-button]")) return null;
    if (getComputedStyle(container).position === "static") container.style.position = "relative";
    const host = makeButton(getUrl, "overlay");
    Object.assign(host.style, { position: "absolute", top: "10px", left: "10px", zIndex: "2147483646" });
    container.appendChild(host);
    overlays.add(host);
    return host;
  }

  // ---------- חלון ההורדה ----------
  const PANEL_CSS = `
    :host { all: initial; position: fixed; z-index: 2147483647; }
    * { box-sizing: border-box; }
    .card { width: 320px; max-height: min(520px, calc(100vh - 24px)); overflow: auto; direction: ${DIR};
      font: 14px/1.4 system-ui, "Segoe UI", Arial, sans-serif; color: #f1f1f1; background: #212121;
      border: 1px solid #3a3a3a; border-radius: 14px; box-shadow: 0 10px 30px rgba(0,0,0,.45); padding: 12px; }
    .top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
    .brand { font-size: 12px; color: #aaa; display: flex; align-items: center; gap: 6px; }
    .brand svg { width: 14px; height: 14px; color: #3b82f6; }
    .x { all: unset; cursor: pointer; width: 26px; height: 26px; display: grid; place-items: center; border-radius: 50%; color: #aaa; font-size: 18px; }
    .x:hover { background: #333; color: #fff; }
    .media { display: flex; gap: 10px; margin-bottom: 10px; }
    .thumb { width: 96px; height: 64px; border-radius: 8px; object-fit: cover; background: #333; flex: none; }
    .meta { min-width: 0; }
    .title { font-weight: 600; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
    .sub { color: #aaa; font-size: 12px; margin-top: 2px; }
    .opts { display: grid; gap: 6px; }
    .opt { all: unset; cursor: pointer; display: flex; justify-content: space-between; align-items: center;
      padding: 9px 12px; border-radius: 10px; background: #2d2d2d; }
    .opt:hover, .opt:focus-visible { background: #3b82f6; }
    .opt .size { color: #bbb; font-size: 12px; direction: ltr; }
    .opt:hover .size { color: #e5edff; }
    .msg { padding: 10px 2px; }
    .msg.err { color: #ff8a80; }
    .hint { color: #aaa; font-size: 12px; margin-top: 4px; }
    .bar { height: 8px; background: #333; border-radius: 4px; overflow: hidden; margin: 8px 0 6px; }
    .fill { height: 100%; background: #3b82f6; width: 0; transition: width .4s; }
    .fill.busy { width: 100% !important; background: linear-gradient(90deg,#3b82f6 0%,#93c5fd 50%,#3b82f6 100%);
      background-size: 200% 100%; animation: slide 1.2s linear infinite; }
    @keyframes slide { from { background-position: 200% 0; } to { background-position: 0 0; } }
    .stats { display: flex; justify-content: space-between; color: #bbb; font-size: 12px; }
    .stats span { direction: ltr; }
    .actions { display: flex; gap: 8px; margin-top: 10px; }
    .btn { all: unset; cursor: pointer; padding: 8px 14px; border-radius: 10px; background: #3b82f6; color: #fff; font-weight: 600; }
    .btn.ghost { background: #2d2d2d; color: #ddd; }
    .btn:hover { filter: brightness(1.1); }
    .spin { width: 18px; height: 18px; border: 2px solid #555; border-top-color: #3b82f6; border-radius: 50%;
      animation: r .8s linear infinite; display: inline-block; vertical-align: middle; margin-inline-end: 8px; }
    @keyframes r { to { transform: rotate(360deg); } }
    .file { font-size: 12px; color: #bbb; word-break: break-all; margin-top: 4px; }
  `;

  const activeJobs = new Map(); // url → job_id, כדי לחזור להורדה שרצה אם סוגרים ופותחים שוב

  const panel = {
    host: null, root: null, anchor: null, url: null, pollTimer: null, token: 0,

    ensure() {
      if (this.host?.isConnected) return;
      this.host = document.createElement("div");
      this.host.setAttribute("data-ud-panel", "");
      this.root = this.host.attachShadow({ mode: "open" });
      document.documentElement.appendChild(this.host);
      document.addEventListener("pointerdown", (e) => {
        if (!this.anchor) return;
        const path = e.composedPath();
        if (!path.includes(this.host) && !path.includes(this.anchor)) this.close("outside click");
      }, true);
      document.addEventListener("keydown", (e) => { if (e.key === "Escape") this.close("escape"); }, true);
      window.addEventListener("resize", () => this.position());
      window.addEventListener("scroll", () => this.position(), true);
    },

    toggle(anchor, url) {
      if (this.anchor === anchor) return this.close("toggle");
      this.open(anchor, url);
    },

    close(reason = "") {
      if (this.anchor) console.debug("[UD] panel close", reason);
      clearTimeout(this.pollTimer);
      this.token++;
      this.anchor = null;
      if (this.host) this.host.style.display = "none";
    },

    render(body) {
      this.root.innerHTML = `<style>${PANEL_CSS}</style>
        <div class="card" role="dialog" aria-label="${t("dialogLabel")}">
          <div class="top"><span class="brand">${ICON} Universal Downloader</span><button class="x" aria-label="${t("close")}">×</button></div>
          ${body}
        </div>`;
      this.root.querySelector(".x").onclick = () => this.close("x button");
      this.position();
    },

    position() {
      if (!this.anchor || !this.host || this.host.style.display === "none") return;
      if (!this.anchor.isConnected) return this.close("anchor removed");
      const r = this.anchor.getBoundingClientRect();
      const card = this.root.querySelector(".card");
      const w = card?.offsetWidth || 320, h = card?.offsetHeight || 200;
      let left = r.left + r.width / 2 - w / 2;
      left = Math.max(8, Math.min(left, innerWidth - w - 8));
      let top = r.top - h - 8; // מעל הכפתור
      if (top < 8) top = Math.min(r.bottom + 8, innerHeight - h - 8); // אין מקום: מתחת
      Object.assign(this.host.style, { left: left + "px", top: Math.max(8, top) + "px" });
    },

    async open(anchor, url) {
      this.ensure();
      clearTimeout(this.pollTimer);
      const token = ++this.token;
      this.anchor = anchor;
      this.url = url;
      this.host.style.display = "block";
      console.debug("[UD] panel open", url);
      const alive = () => token === this.token;

      if (!url) return this.render(`<div class="msg err">${t("noVideoLink")}</div>`);
      if (activeJobs.has(url)) return this.track(activeJobs.get(url), null, token);

      this.render(`<div class="msg"><span class="spin"></span>${t("checkingEngine")}</div>`);
      const health = await send({ type: "health" });
      if (!alive()) return;
      if (!health.ok) return this.showError(health, () => this.open(anchor, url));

      this.render(`<div class="msg"><span class="spin"></span>${t("loadingDetails")}</div>`);
      const res = await send({ type: "formats", url });
      if (!alive()) return;
      if (!res.ok) return this.showError(res, () => this.open(anchor, url));
      this.showFormats(res.data, token);
    },

    showError(res, retry) {
      const body = res.down
        ? `<div class="msg err"><b>${t("engineOff")}</b></div>
           <div class="hint">${esc(t("engineOffHint"))}</div>`
        : `<div class="msg err">${esc(res.error)}</div>`;
      this.render(`${body}<div class="actions"><button class="btn retry">${t("retry")}</button></div>`);
      this.root.querySelector(".retry").onclick = retry;
    },

    mediaHtml(info) {
      const sub = [info.uploader, fmtDuration(info.duration)].filter(Boolean).map(esc).join(" · ");
      const thumb = info.thumbnail
        ? `<img class="thumb" src="${esc(info.thumbnail)}" referrerpolicy="no-referrer" alt="">`
        : `<div class="thumb"></div>`;
      return `<div class="media">${thumb}<div class="meta"><div class="title">${esc(info.title || t("untitled"))}</div>
        <div class="sub">${sub}</div></div></div>`;
    },

    showFormats(info, token) {
      this.info = info;
      const opts = info.options.map((o) => `<button class="opt" data-id="${esc(o.format_id)}">
          <span>${esc(o.label)}</span><span class="size">${o.filesize_approx ? "~" + fmtBytes(o.filesize_approx) : ""}</span></button>`).join("");
      this.render(`${this.mediaHtml(info)}<div class="opts">${opts}</div>`);
      this.root.querySelector(".thumb")?.addEventListener("error", (e) => (e.target.style.visibility = "hidden"));
      this.root.querySelectorAll(".opt").forEach((b) => (b.onclick = () => this.start(b.dataset.id, token)));
    },

    async start(formatId, token) {
      const url = this.url;
      this.render(`${this.mediaHtml(this.info)}<div class="msg"><span class="spin"></span>${t("starting")}</div>`);
      const res = await send({ type: "download", url, format_id: formatId });
      if (token !== this.token) return;
      if (!res.ok) return this.showError(res, () => this.showFormats(this.info, token));
      activeJobs.set(url, res.data.job_id);
      this.track(res.data.job_id, this.info, token);
    },

    async track(jobId, info, token) {
      const res = await send({ type: "job", id: jobId });
      if (token !== this.token) return;
      if (!res.ok) {
        activeJobs.delete(this.url);
        return this.showError(res, () => this.open(this.anchor, this.url));
      }
      const job = res.data;
      const head = info ? this.mediaHtml(info) : `<div class="title">${esc(job.title || t("downloading"))}</div>`;

      if (job.status === "done") {
        activeJobs.delete(job.url);
        const name = (job.file_path || "").split(/[\\/]/).pop();
        this.render(`${head}<div class="msg">${t("done")}</div><div class="file">${esc(name)}</div>
          <div class="actions"><button class="btn open">${t("openFolder")}</button><button class="btn ghost more">${t("downloadAgain")}</button></div>`);
        this.root.querySelector(".open").onclick = () => send({ type: "open", job_id: jobId });
        this.root.querySelector(".more").onclick = () => this.open(this.anchor, this.url);
        return;
      }
      if (job.status === "error" && job.code === "restarting" && !this.restarted) {
        // תיקון עצמי: המנוע עדכן את yt-dlp ומופעל מחדש. מחכים לו ומתחילים את ההורדה שוב, פעם אחת.
        this.restarted = true;
        activeJobs.delete(job.url);
        this.render(`${head}<div class="msg"><span class="spin"></span>${t("repairing")}</div>`);
        const back = await send({ type: "waitEngine" });
        if (token !== this.token) return;
        if (!back.ok) return this.showError(back, () => this.open(this.anchor, this.url));
        return info ? this.start(job.format_id, token) : this.open(this.anchor, this.url);
      }
      if (job.status === "error") {
        activeJobs.delete(job.url);
        return this.showError({ error: job.error || t("failed") }, () => this.open(this.anchor, this.url));
      }

      const processing = job.status === "processing";
      const label = t(job.status === "queued" ? "queued" : job.status === "repairing" ? "repairing"
        : processing ? "processing" : "downloading");
      const speed = job.speed ? fmtBytes(job.speed) + "/s" : "";
      const eta = job.eta ? t("etaLeft", fmtDuration(job.eta)) : "";
      this.render(`${head}<div class="msg">${label}</div>
        <div class="bar"><div class="fill ${processing ? "busy" : ""}" style="width:${job.percent || 0}%"></div></div>
        <div class="stats"><span>${processing ? "" : Math.round(job.percent || 0) + "%"}</span><span>${esc([speed, eta].filter(Boolean).join(" · "))}</span></div>`);
      this.pollTimer = setTimeout(() => this.track(jobId, info, token), 700);
    },
  };

  // ---------- מעקב אחרי הדף ----------
  // האתרים מחליפים תוכן בלי טעינה, אז סורקים מחדש בכל שינוי (מקובץ ל-פעם ב-250ms)
  function observe(scan) {
    let scheduled = false;
    const run = () => {
      scheduled = false;
      try { scan(); } catch (e) { console.debug("[Universal Downloader]", e); }
    };
    const schedule = () => {
      if (scheduled) return;
      scheduled = true;
      setTimeout(run, 250);
    };
    new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
    run();
    return schedule;
  }

  window.UD = { makeButton, attachOverlay, observe, panel };
})();

// כל התקשורת עם המנוע עוברת כאן. המנוע מקבל רק בקשות עם Origin של התוסף,
// ולכן ה-content scripts (שרצים עם ה-Origin של האתר) לא פונים אליו ישירות.
const ENGINE = "http://127.0.0.1:5123";

// formats ארוך: בכישלון המנוע מעדכן את yt-dlp ומנסה שוב (תיקון עצמי)
const t = (key, ...args) => chrome.i18n.getMessage(key, args.map(String)) || key;

const TIMEOUTS = { health: 4000, formats: 300000, download: 15000, job: 10000, open: 10000, cookies: 10000 };

async function call(type, path, { method = "GET", body } = {}) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), TIMEOUTS[type] || 15000);
  try {
    const res = await fetch(ENGINE + path, {
      method,
      signal: ctrl.signal,
      headers: body ? { "Content-Type": "application/json" } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    });
    const data = await res.json().catch(() => ({}));
    if (res.status === 403) {
      return { ok: false, error: t("notAllowed", chrome.runtime.id) };
    }
    if (!res.ok) return { ok: false, error: data.detail || t("errorCode", res.status), code: data.code };
    return { ok: true, data };
  } catch (e) {
    if (e.name === "AbortError" && type !== "health") {
      return { ok: false, error: t("timeout") };
    }
    // fetch נכשל = אין מי שמאזין בפורט
    return { ok: false, down: true, error: t("engineOff") };
  } finally {
    clearTimeout(timer);
  }
}

// אינסטגרם דורש התחברות: מייצאים את עוגיות instagram.com מכרום (cookies.txt בפורמט Netscape) ושולחים למנוע.
// המשתמש לא עושה כלום – מספיק שהוא מחובר לאינסטגרם בכרום.
async function syncInstagramCookies() {
  const cookies = await chrome.cookies.getAll({ domain: "instagram.com" });
  if (!cookies.some((c) => c.name === "sessionid")) return false; // לא מחובר בכרום
  const lines = cookies.map((c) => {
    const domain = (c.httpOnly ? "#HttpOnly_" : "") + (c.hostOnly ? c.domain : "." + c.domain.replace(/^\./, ""));
    return [domain, c.hostOnly ? "FALSE" : "TRUE", c.path, c.secure ? "TRUE" : "FALSE",
      Math.floor(c.expirationDate || 0), c.name, c.value].join("\t");
  });
  const text = "# Netscape HTTP Cookie File\n" + lines.join("\n") + "\n";
  const res = await call("cookies", "/cookies", { method: "POST", body: { text } });
  return res.ok;
}

// אחרי עדכון yt-dlp המנוע מופעל מחדש לבד. עלייה במחשב הזה יכולה לקחת יותר מדקה.
async function waitEngine() {
  await new Promise((r) => setTimeout(r, 6000)); // שהתהליך הישן ייסגר
  for (let i = 0; i < 75; i++) {
    if ((await call("health", "/health")).ok) return { ok: true };
    await new Promise((r) => setTimeout(r, 2000));
  }
  return { ok: false, error: t("engineNoReturn") };
}

async function formats(url) {
  let res = await call("formats", "/formats?url=" + encodeURIComponent(url));
  if (res.code === "restarting") {
    // תיקון עצמי: המנוע עדכן את yt-dlp ומופעל מחדש – מחכים ומנסים שוב פעם אחת
    const back = await waitEngine();
    if (!back.ok) return back;
    res = await call("formats", "/formats?url=" + encodeURIComponent(url));
  }
  if (res.ok || res.code !== "login_required") return res;
  if (!(await syncInstagramCookies())) {
    return { ok: false, error: t("igLogin") };
  }
  return call("formats", "/formats?url=" + encodeURIComponent(url));
}

const handlers = {
  // ניסיון שני לפני שמכריזים שהמנוע כבוי (תקלה רגעית לא צריכה להציג הודעה מפחידה)
  health: async () => {
    const first = await call("health", "/health");
    if (first.ok || !first.down) return first;
    await new Promise((r) => setTimeout(r, 700));
    return call("health", "/health");
  },
  formats: (m) => formats(m.url),
  waitEngine: () => waitEngine(),
  download: (m) => call("download", "/download", { method: "POST", body: { url: m.url, format_id: m.format_id } }),
  job: (m) => call("job", "/jobs/" + encodeURIComponent(m.id)),
  open: (m) => call("open", "/open", { method: "POST", body: { job_id: m.job_id || null } }),
};

chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  const handler = handlers[msg?.type];
  if (!handler) return false;
  handler(msg).then(sendResponse);
  return true; // תשובה אסינכרונית
});

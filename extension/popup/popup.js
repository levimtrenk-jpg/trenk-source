// חלון התוסף: סטטוס המנוע, תיקיית היעד ופתיחת התיקייה. yt-dlp מתעדכן לבד במנוע.
const $ = (id) => document.getElementById(id);
const send = (msg) => chrome.runtime.sendMessage(msg);
const t = (key, ...args) => chrome.i18n.getMessage(key, args.map(String)) || key;

// שפה וכיוון לפי כרום (עברית ברירת מחדל), טקסטים מ-_locales
// ("dir" מתוך הקובץ שנבחר, לא @@bidi_dir: בכרום בשפה שלישית הטקסט בעברית אבל @@bidi_dir הוא ltr)
document.documentElement.lang = t("lang");
document.documentElement.dir = t("dir");
document.querySelectorAll("[data-i18n]").forEach((el) => (el.textContent = t(el.dataset.i18n)));
const site = t("siteBase");
$("privacy").href = site + "privacy.html";
$("terms").href = site + "terms.html";
// כתובת יצירת קשר מגיעה מ-version.json (config/site.json); עד אז – דף התנאים
$("contact").href = site + "terms.html";

$("extId").textContent = chrome.runtime.id;
$("extVersion").textContent = chrome.runtime.getManifest().version;

async function refresh() {
  const res = await send({ type: "health" });
  const ok = res?.ok;
  $("dot").className = "dot " + (ok ? "ok" : "bad");
  $("state").textContent = ok ? t("engineRunning") : res?.down ? t("engineOff") : res?.error || t("error");
  $("versions").textContent = ok ? t("autoUpdates", res.data.ytdlp_version) : "";
  $("hint").hidden = !res?.down;
  $("dirBox").hidden = !ok;
  $("actions").hidden = !ok;
  if (ok) $("dir").textContent = res.data.download_dir || "";
  return ok;
}

$("openBtn").onclick = () => send({ type: "open" });

refresh();

// קישור הטיפ מגיע מדף ההורדה הציבורי (config/tip.json בריפו). ריק או לא https = לא מוצג.
fetch("https://levimtrenk-jpg.github.io/trenk/version.json", { cache: "no-store" })
  .then((r) => r.json())
  .then((v) => {
    if (typeof v.contact === "string" && /^[^\s@]+@[^\s@]+\.[a-z]+$/i.test(v.contact)) $("contact").href = "mailto:" + v.contact;
    if (typeof v.tip === "string" && /^https:\/\/\S+$/.test(v.tip)) {
      $("tip").href = v.tip;
      $("tip").hidden = false;
    }
  })
  .catch(() => {});

// טיקטוק: כפתור בעמודת הפעולות (מעל הלייק) של כל סרטון בפיד, עם הקישור של אותו סרטון.
// מזהה הסרטון לקוח מה-id של נגן הווידאו: xgwrapper-<n>-<videoId>.
// מאפייני data-e2e לא תלויים בשפת הממשק, ולכן נשענים עליהם ולא על טקסט.
(() => {
  const { makeButton, attachOverlay, observe } = window.UD;

  function authorOf(item) {
    const fromPage = location.pathname.match(/^\/@([\w.-]+)\/video\//);
    const avatar = item?.querySelector('[data-e2e="video-author-avatar"]')?.closest("a[href^='/@']");
    const link = avatar || item?.querySelector("a[href^='/@']:not([href*='/video/'])");
    const m = link?.getAttribute("href")?.match(/^\/@([\w.-]+)/);
    return m?.[1] || fromPage?.[1] || ""; // yt-dlp מקבל גם @/video/<id> בלי שם משתמש
  }

  function placeInActionBar(item, btn) {
    // like-icon הוא DIV (לא button) בתוך section של עמודת הפעולות
    const like = item.querySelector('[data-e2e="like-icon"]');
    if (!like) return false;
    const section = like.closest("section") || like.parentElement?.parentElement;
    if (!section) return false;
    let child = like;
    while (child.parentElement && child.parentElement !== section) child = child.parentElement;
    section.insertBefore(btn, child);
    return true;
  }

  function scan() {
    for (const player of document.querySelectorAll("[id^='xgwrapper-']")) {
      const videoId = player.id.match(/(\d{6,})$/)?.[1];
      if (!videoId) continue;
      const item = player.closest('[data-e2e="recommend-list-item-container"], article') || player.parentElement;
      const existing = item.querySelector(`[data-ud-button][data-ud-video="${videoId}"]`);
      // הנגן נטען לפני עמודת הפעולות: כפתור זמני מעל הסרטון עובר לעמודה כשהיא מופיעה
      const barReady = !!item.querySelector('[data-e2e="like-icon"]');
      if (existing && (existing.getAttribute("data-ud-button") !== "overlay" || !barReady)) continue;
      // פריט שמוחזר לשימוש עם סרטון אחר: מסירים כפתור ישן
      item.querySelectorAll("[data-ud-button][data-ud-video]").forEach((b) => b.remove());

      const getUrl = () => `https://www.tiktok.com/@${authorOf(item)}/video/${videoId}`;
      const btn = makeButton(getUrl, "column");
      btn.dataset.udVideo = videoId;
      btn.style.margin = "0 0 8px";
      if (!placeInActionBar(item, btn)) {
        const overlay = attachOverlay(player.parentElement, getUrl);
        if (overlay) overlay.dataset.udVideo = videoId;
      }
    }

    // דף סרטון בלי נגן מוכר (מבנה אחר): כפתור מעל הווידאו עם כתובת הדף.
    // כשהנגן הרגיל מופיע (לפעמים רק אחרי רגע), כפתור הגיבוי מוסר.
    const page = location.pathname.match(/^\/@[\w.-]*\/video\/(\d+)/);
    if (document.querySelector("[id^='xgwrapper-']")) {
      document.querySelectorAll("[data-ud-fallback]").forEach((b) => b.remove());
    } else if (page) {
      const video = [...document.querySelectorAll("video")].find((v) => v.offsetWidth >= 120 && v.parentElement !== document.body);
      const btn = video && attachOverlay(video.parentElement, () => `https://www.tiktok.com${page[0]}`);
      if (btn) btn.dataset.udFallback = "";
    }
  }

  observe(scan);
})();

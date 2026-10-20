// אינסטגרם: כפתור בפינת כל סרטון (פיד, Reels, פוסט, סטורי) עם הקישור של אותו פוסט.
// הטקסטים של כפתורי הלייק באינסטגרם מתורגמים לפי שפת הממשק, ולכן הכפתור נצמד לסרטון עצמו.
(() => {
  const { attachOverlay, observe } = window.UD;
  const POST_RE = /\/(p|reel|reels|tv)\/([\w-]{5,})/;

  function normalize(path) {
    const m = path.match(POST_RE);
    if (!m) return null;
    const kind = m[1] === "p" || m[1] === "tv" ? "p" : "reel";
    return `https://www.instagram.com/${kind}/${m[2]}/`;
  }

  function postUrl(video) {
    // פוסט בפיד: הקישור הראשון לפוסט בתוך ה-article הוא חותמת הזמן של אותו פוסט
    const article = video.closest("article");
    if (article) {
      for (const a of article.querySelectorAll("a[href]")) {
        const url = normalize(a.getAttribute("href"));
        if (url) return url;
      }
    }
    // סטורי: לכתובת יש מזהה של הסטורי הספציפי
    if (location.pathname.startsWith("/stories/")) return location.href.split("?")[0];
    // דף פוסט / Reel בודד, וגם לשונית ה-Reels (הכתובת מתעדכנת לסרטון שגלוי)
    const fromPage = normalize(location.pathname);
    if (fromPage) return fromPage;
    // מבנה אחר: מחפשים קישור לפוסט בהורים הקרובים
    let el = video.parentElement;
    for (let i = 0; el && i < 10; i++, el = el.parentElement) {
      const a = [...el.querySelectorAll("a[href]")].find((x) => POST_RE.test(x.getAttribute("href")));
      if (a) return normalize(a.getAttribute("href"));
    }
    return null;
  }

  function scan() {
    for (const video of document.querySelectorAll("video")) {
      const box = video.parentElement;
      if (!box || box.querySelector(":scope > [data-ud-button]")) continue;
      if (video.offsetWidth < 120) continue; // תצוגות מקדימות זעירות
      attachOverlay(box, () => postUrl(video));
    }
  }

  observe(scan);
})();

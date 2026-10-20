// יוטיוב: כפתור ליד הלייק/שיתוף בדף צפייה, וכפתור בעמודת הפעולות של כל Short.
(() => {
  const { makeButton, observe } = window.UD;

  function watchUrl() {
    const id = new URL(location.href).searchParams.get("v");
    return id ? `https://www.youtube.com/watch?v=${id}` : null;
  }

  function shortsUrl(reel) {
    // ה-Short שגלוי כרגע הוא זה שבכתובת. reel נשמר למקרה שבעתיד יהיה לו מזהה משלו.
    const m = location.pathname.match(/^\/shorts\/([\w-]{11})/);
    return m ? `https://www.youtube.com/shorts/${m[1]}` : null;
  }

  function scan() {
    // דף צפייה רגיל: אחרי כפתורי הלייק/דיסלייק
    const bar = document.querySelector("ytd-watch-metadata #top-level-buttons-computed");
    if (bar && !bar.querySelector(":scope > [data-ud-button]")) {
      const btn = makeButton(watchUrl, "inline");
      btn.style.marginInlineStart = "8px";
      const like = bar.querySelector(":scope > segmented-like-dislike-button-view-model, :scope > like-button-view-model");
      bar.insertBefore(btn, like ? like.nextSibling : bar.firstChild);
    }

    // Shorts: בראש עמודת הפעולות (מעל הלייק) של כל Short שנטען
    for (const actions of document.querySelectorAll("ytd-reel-video-renderer reel-action-bar-view-model")) {
      if (actions.querySelector(":scope > [data-ud-button]")) continue;
      const reel = actions.closest("ytd-reel-video-renderer");
      const btn = makeButton(() => shortsUrl(reel), "column");
      btn.style.marginBottom = "16px";
      actions.insertBefore(btn, actions.firstChild);
    }
  }

  const rescan = observe(scan);
  document.addEventListener("yt-navigate-finish", rescan);
})();

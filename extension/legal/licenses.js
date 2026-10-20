// רישיונות וקרדיטים: THIRD_PARTY_NOTICES.md + הטקסטים המלאים (נוצרים מ-tools/sync_legal.py)
const t = (key) => chrome.i18n.getMessage(key) || key;
document.documentElement.lang = t("lang");
document.documentElement.dir = t("dir");
document.querySelectorAll("[data-i18n]").forEach((el) => (el.textContent = t(el.dataset.i18n)));

const esc = (s) => s.replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
// Markdown מינימלי: כותרות, **מודגש**, קישורים
const inline = (s) => esc(s).replace(/\*\*(.+?)\*\*/g, "<b>$1</b>").replace(/(https?:\/\/[^\s)<]+)/g, '<a href="$1" target="_blank" rel="noopener">$1</a>');

fetch("THIRD_PARTY_NOTICES.md").then((r) => r.text()).then((md) => {
  document.getElementById("notices").innerHTML = md.split(/\n\s*\n/).map((block) => {
    const m = block.match(/^(#+)\s+(.*)/);
    return m ? `<h${Math.min(m[1].length + 1, 4)}>${inline(m[2])}</h${Math.min(m[1].length + 1, 4)}>` : `<p>${inline(block.trim())}</p>`;
  }).join("");
});

fetch("index.json").then((r) => r.json()).then((files) => {
  const box = document.getElementById("files");
  for (const f of files) {
    const d = document.createElement("details");
    d.innerHTML = `<summary>${esc(f.replace(/\.txt$/, ""))}</summary><pre></pre>`;
    d.addEventListener("toggle", () => {
      const pre = d.querySelector("pre");
      if (d.open && !pre.textContent) fetch("licenses/" + f).then((r) => r.text()).then((x) => (pre.textContent = x));
    }, { once: true });
    box.appendChild(d);
  }
});

/* مكفوف بوت — all screens (tabs) in one file. */

import { t, getLang, setLang } from "./i18n.js";
import * as store from "./storage.js";
import * as sp from "./speech.js";

/* ============================================================
 * DOM helpers
 * ============================================================ */
const $main = () => document.getElementById("main");

export function el(tag, attrs = {}, ...kids) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k === "class") e.className = v;
    else if (k === "html") e.innerHTML = v;
    else if (k.startsWith("on") && typeof v === "function") e.addEventListener(k.slice(2).toLowerCase(), v);
    else if (k === "checked" || k === "disabled" || k === "hidden") { if (v) e.setAttribute(k, ""); }
    else e.setAttribute(k, v);
  }
  for (const k of kids.flat()) {
    if (k == null || k === false) continue;
    e.append(k.nodeType ? k : document.createTextNode(String(k)));
  }
  return e;
}

export function clear() {
  const m = $main();
  while (m.firstChild) m.removeChild(m.firstChild);
  m.scrollTop = 0; window.scrollTo(0, 0);
  m.focus({ preventScroll: true });
}
export function setTitle(title) { document.getElementById("appTitle").textContent = title; }
export function toast(text, ms = 2200) {
  const t = document.getElementById("toast");
  t.textContent = text;
  t.hidden = false;
  clearTimeout(toast._timer);
  toast._timer = setTimeout(() => { t.hidden = true; }, ms);
}

function section(text) { return el("h2", { class: "section-header" }, text); }
function info(label, value) {
  return el("div", { class: "info" },
    el("div", { class: "info-label" }, label),
    el("div", { class: "info-body" }, value));
}
function loading(label) {
  return el("div", { class: "loading", role: "status" },
    el("div", { class: "spinner", "aria-hidden": "true" }),
    el("span", null, label || t("loading")));
}

function segmented(ids, labels, subs, selected, onChange) {
  const row = el("div", { class: "segmented", role: "radiogroup" });
  const buttons = ids.map((id, i) => {
    const b = el("button", {
      class: "segment" + (id === selected ? " selected" : ""),
      type: "button", role: "radio",
      "aria-checked": id === selected ? "true" : "false",
      onClick: () => {
        selected = id;
        for (const btn of row.children) {
          btn.classList.remove("selected");
          btn.setAttribute("aria-checked", "false");
        }
        b.classList.add("selected");
        b.setAttribute("aria-checked", "true");
        onChange(id);
      }
    },
      el("span", null, labels[i]),
      subs && subs[i] ? el("span", { class: "segment-sub" }, subs[i]) : null);
    return b;
  });
  row.append(...buttons);
  return row;
}

function micButton(onText) {
  if (!sp.speechRecognitionAvailable()) return null;
  let r = null;
  const btn = el("button", { class: "btn btn-outline", type: "button", "aria-label": t("voice_dictation") },
    el("span", { class: "mic-icon", "aria-hidden": "true" }, "🎤"),
    el("span", null, t("voice_dictation")));
  btn.addEventListener("click", () => {
    if (r) { try { r.stop(); } catch {} r = null; btn.classList.remove("mic-active"); return; }
    r = sp.createDictation({
      onError: () => { btn.classList.remove("mic-active"); r = null; toast(t("voice_not_supported")); },
      onEnd: (finalText) => {
        btn.classList.remove("mic-active"); r = null;
        if (finalText) onText(finalText);
      }
    });
    if (!r) return toast(t("voice_not_supported"));
    try { r.start(); btn.classList.add("mic-active"); }
    catch { toast(t("voice_not_supported")); }
  });
  return btn;
}

/* ============================================================
 * Tabs
 * ============================================================ */
let currentTab = "home";

function tabBar() {
  const tabs = [
    { id: "home",     icon: "🏠", label: t("tab_home") },
    { id: "coords",   icon: "📍", label: t("tab_coords") },
    { id: "settings", icon: "⚙️", label: t("tab_settings") },
    { id: "guide",    icon: "🗺", label: t("tab_guide") }
  ];
  const bar = el("div", { class: "tabbar", role: "tablist" });
  for (const tab of tabs) {
    const selected = tab.id === currentTab;
    const btn = el("button", {
      class: "tab" + (selected ? " selected" : ""),
      type: "button", role: "tab",
      "aria-selected": selected ? "true" : "false",
      "aria-label": tab.label,
      onClick: () => { currentTab = tab.id; render(); }
    },
      el("span", { class: "tab-icon", "aria-hidden": "true" }, tab.icon),
      el("span", null, tab.label));
    bar.append(btn);
  }
  return bar;
}

export function render() {
  clear();
  setTitle(t("app_name"));
  const m = $main();
  m.append(tabBar());
  switch (currentTab) {
    case "coords":   renderCoords(m); break;
    case "settings": renderSettings(m); break;
    case "guide":    renderGuide(m); break;
    case "home":
    default:         renderHome(m); break;
  }
}

/* ============================================================
 * Home tab — status, save position, marital-status check
 * ============================================================ */
function renderHome(m) {
  const s = store.getSettings();
  const state = store.getState();
  const lastDisplay = state.lastMemberName || t("none");
  const refreshAt = (s.likesPerCycle || 80);
  const cycle = state.cycleLikes || 0;

  m.append(
    el("p", { class: "subtitle" }, t("home_subtitle")),

    section(t("status_title")),
    info(t("last_member"),  lastDisplay),
    info(t("processed"),    String(state.processedCount || 0)),
    info(t("cycle_likes"),  `${cycle} / ${refreshAt}`),
    info(t("next_refresh_at"), String(refreshAt)),

    section(t("save_pos"))
  );

  // Position save
  const posInput = el("input", { type: "text", placeholder: t("pos_label"), "aria-label": t("pos_label"), value: state.lastMemberName || "" });
  const micPos = micButton((text) => { posInput.value = text; posInput.focus(); });
  const saveBtn = el("button", { class: "btn", type: "button",
    onClick: () => {
      const name = (posInput.value || "").trim();
      if (!name) return;
      const cur = store.getState();
      store.setState({
        lastMemberName: name,
        processedCount: (cur.processedCount || 0) + 1,
        cycleLikes: (cur.cycleLikes || 0) + 1
      });
      store.pushLog(t("log_position"), name);
      toast(t("pos_saved"));
      sp.vibrate(30);
      render();
    }
  }, t("save_pos"));

  const bumpBtn = el("button", { class: "btn btn-outline", type: "button",
    onClick: () => {
      const cur = store.getState();
      const next = (cur.cycleLikes || 0) + 1;
      store.setState({ cycleLikes: next, processedCount: (cur.processedCount || 0) + 1 });
      store.pushLog(t("log_like"), `#${next}`);
      const max = (store.getSettings().likesPerCycle || 80);
      if (next >= max) {
        sp.vibrate([60, 50, 60]);
        sp.speak(t("guide_refresh").replace("$N", String(max)), { force: true });
      } else {
        sp.vibrate(20);
      }
      render();
    }
  }, "❤️ " + t("bump_like"));

  const resetCycleBtn = el("button", { class: "btn btn-ghost", type: "button",
    onClick: () => {
      store.setState({ cycleLikes: 0 });
      store.pushLog(t("log_cycle_reset"), "");
      toast(t("saved"));
      render();
    }
  }, t("reset_cycle"));

  const clearBtn = el("button", { class: "btn btn-ghost", type: "button",
    onClick: () => {
      store.clearState();
      toast(t("state_cleared"));
      render();
    }
  }, t("clear_state"));

  m.append(
    el("label", { class: "field" },
      el("span", { class: "field-label" }, t("pos_label")),
      posInput),
    el("div", { class: "btn-row" }, saveBtn, micPos ? micPos : null),
    el("div", { class: "btn-row" }, bumpBtn, resetCycleBtn, clearBtn)
  );

  // Marital status check (only useful in DW mode but shown always)
  m.append(section(t("check_title")));
  m.append(el("p", { class: "subtitle" }, t("check_hint")));
  m.append(el("div", { class: "callout callout-warn" }, t("profile_already")));

  const checkBox = el("div", { "aria-live": "polite" });
  const checkInput = el("textarea", { placeholder: t("check_placeholder"), "aria-label": t("check_placeholder") });
  const micCheck = micButton((text) => { checkInput.value = (checkInput.value ? checkInput.value + " " : "") + text; checkInput.focus(); });

  const runCheck = () => {
    const text = (checkInput.value || "").trim();
    if (!text) return;
    sp.vibrate(20);
    const result = checkMaritalStatusLocally(text);
    if (result === "yes") {
      checkBox.replaceChildren(el("div", { class: "callout callout-ok" }, t("check_yes")));
      sp.speak(t("check_yes"));
    } else if (result === "no") {
      checkBox.replaceChildren(el("div", { class: "callout callout-danger" }, t("check_no")));
      sp.speak(t("check_no"));
    } else {
      checkBox.replaceChildren(el("div", { class: "callout callout-warn" }, t("check_unknown")));
      sp.speak(t("check_unknown"));
    }
  };

  m.append(
    el("label", { class: "field" },
      el("span", { class: "field-label" }, t("check_title")),
      checkInput),
    el("div", { class: "btn-row" },
      el("button", { class: "btn btn-block", type: "button", onClick: runCheck }, t("check_run")),
      micCheck ? micCheck : null),
    checkBox
  );

  // Activity log
  const logItems = store.getLog().slice(0, 12);
  if (logItems.length) {
    m.append(section(t("log_title")));
    for (const it of logItems) {
      m.append(el("div", { class: "list-item" },
        el("div", { class: "meta" }, new Date(it.at).toLocaleString() + " · " + it.type),
        el("div", null, it.content || "")));
    }
    m.append(el("div", { class: "btn-row" },
      el("button", { class: "btn btn-ghost", type: "button",
        onClick: () => { store.clearLog(); toast(t("saved")); render(); }
      }, t("log_clear"))));
  }
}

function checkMaritalStatusLocally(text) {
  const clean = (text || "").replace(/\s+/g, " ");
  if (/مطلق[ةه]/.test(clean) || /أرمل[ةه]/.test(clean) || /divorced|widowed/i.test(clean)) return "yes";
  if (/متزوج[ةه]?|عزباء?|أعزب|single|married/i.test(clean)) return "no";
  return "unknown";
}

/* ============================================================
 * Coordinates tab — record, screenshot, Mawada presets, list
 * ============================================================ */
const MAWADA_BASE_WIDTH  = 1080;
const MAWADA_BASE_HEIGHT = 2340;
const MAWADA_PRESETS = [
  { name: "ثلاث نقاط (قائمة علوية)", x: 1004, y: 211 },
  { name: "بحث (أعلى يسار)",       x:   54, y: 211 },
  { name: "جرس الإشعارات",          x:  864, y: 211 },
  { name: "الأعضاء (تنقّل سفلي)",   x:  540, y: 2223 },
  { name: "بريدي الداخلي",          x:  184, y: 2223 },
  { name: "باقة التميز",            x:  896, y: 2223 },
  { name: "تقييم التطبيق",          x:  540, y:  760 },
  { name: "أيقونة الوصول (يسار)",   x:   72, y:  370 }
];

function scaleToScreen(presets) {
  const s = store.getSettings();
  const sx = (s.screenWidth  || MAWADA_BASE_WIDTH)  / MAWADA_BASE_WIDTH;
  const sy = (s.screenHeight || MAWADA_BASE_HEIGHT) / MAWADA_BASE_HEIGHT;
  return presets.map(p => ({
    name: p.name,
    x: Math.round(p.x * sx),
    y: Math.round(p.y * sy)
  }));
}

function startCoordRecording(onCapture) {
  const overlay = document.createElement("div");
  overlay.className = "coord-overlay";
  overlay.setAttribute("role", "button");
  overlay.setAttribute("tabindex", "0");
  overlay.setAttribute("aria-label", t("coord_recording"));
  const hint = document.createElement("div");
  hint.className = "coord-overlay-hint";
  hint.textContent = t("coord_recording");
  overlay.appendChild(hint);
  const capture = (x, y) => {
    document.body.removeChild(overlay);
    sp.vibrate(40);
    onCapture({ x: Math.round(x), y: Math.round(y) });
  };
  overlay.addEventListener("click", (e) => capture(e.clientX, e.clientY));
  overlay.addEventListener("touchend", (e) => {
    e.preventDefault();
    const touch = e.changedTouches[0];
    capture(touch.clientX, touch.clientY);
  }, { passive: false });
  document.body.appendChild(overlay);
  overlay.focus();
  sp.vibrate(20);
}

function startCoordFromScreenshot(onCapture) {
  const fi = document.createElement("input");
  fi.type = "file"; fi.accept = "image/*";
  fi.addEventListener("change", () => {
    const file = fi.files[0];
    if (!file) return;
    const url = URL.createObjectURL(file);
    const overlay = document.createElement("div");
    overlay.className = "coord-screenshot-wrap";

    const close = document.createElement("button");
    close.className = "coord-screenshot-close";
    close.textContent = t("cancel");
    close.addEventListener("click", () => {
      URL.revokeObjectURL(url);
      document.body.removeChild(overlay);
    });

    const hint = document.createElement("div");
    hint.className = "coord-screenshot-hint";
    hint.textContent = t("coord_tap_screenshot");

    const img = document.createElement("img");
    img.src = url;
    img.className = "coord-screenshot-img";

    const capture = (cx, cy) => {
      const r = img.getBoundingClientRect();
      const sx = img.naturalWidth  / r.width;
      const sy = img.naturalHeight / r.height;
      const x = Math.round((cx - r.left) * sx);
      const y = Math.round((cy - r.top)  * sy);
      URL.revokeObjectURL(url);
      document.body.removeChild(overlay);
      sp.vibrate(40);
      onCapture({ x, y });
    };
    img.addEventListener("click", (e) => capture(e.clientX, e.clientY));
    img.addEventListener("touchend", (e) => {
      e.preventDefault();
      const tch = e.changedTouches[0];
      capture(tch.clientX, tch.clientY);
    }, { passive: false });

    overlay.append(hint, close, img);
    document.body.appendChild(overlay);
    sp.vibrate(20);
  });
  fi.click();
}

function renderCoords(m) {
  const pendingBox = el("div");
  const listBox = el("div", { "aria-live": "polite" });

  function refreshList() {
    listBox.replaceChildren();
    const coords = store.getCoords();
    if (!coords.length) {
      listBox.append(el("p", { class: "info-label" }, t("coord_empty")));
      return;
    }
    for (const c of coords) {
      listBox.append(el("div", { class: "list-item" },
        el("div", { class: "meta" }, c.name || "—"),
        el("div", null, `${t("coord_x")}: ${c.x}   ${t("coord_y")}: ${c.y}`),
        el("div", { class: "btn-row" },
          el("button", { class: "btn btn-ghost", type: "button",
            onClick: () => promptRename(c)
          }, t("coord_rename")),
          el("button", { class: "btn btn-ghost", type: "button",
            onClick: () => {
              store.removeCoord(c.id);
              store.pushLog(t("log_coord_removed"), c.name || "");
              refreshList();
            }
          }, t("delete")))));
    }
  }

  function promptRename(c) {
    const dlg = document.getElementById("dialog");
    while (dlg.firstChild) dlg.removeChild(dlg.firstChild);
    const input = el("input", { type: "text", value: c.name || "", "aria-label": t("rename_to") });
    dlg.append(
      el("h2", null, t("coord_rename")),
      el("label", { class: "field" }, el("span", { class: "field-label" }, t("rename_to")), input),
      el("div", { class: "actions" },
        el("button", { class: "btn btn-ghost", type: "button", onClick: () => dlg.close() }, t("cancel")),
        el("button", { class: "btn", type: "button", onClick: () => {
          const name = (input.value || "").trim();
          if (name) store.updateCoord(c.id, { name });
          dlg.close();
          refreshList();
        }}, t("save"))));
    if (typeof dlg.showModal === "function") dlg.showModal(); else dlg.setAttribute("open", "");
    input.focus();
  }

  function nameAndSave(x, y) {
    pendingBox.replaceChildren();
    const nameInput = el("input", { type: "text", placeholder: t("coord_name_hint"), "aria-label": t("coord_name_label") });
    const micName = micButton((text) => { nameInput.value = text; nameInput.focus(); });
    pendingBox.append(
      el("div", { class: "callout" }, `${t("coord_x")}: ${x}   ${t("coord_y")}: ${y}`),
      el("label", { class: "field" }, el("span", { class: "field-label" }, t("coord_name_label")), nameInput),
      el("div", { class: "btn-row" },
        el("button", { class: "btn", type: "button", onClick: () => {
          const name = (nameInput.value || "").trim() || `${x},${y}`;
          store.addCoord({ name, x, y });
          store.pushLog(t("log_coord_added"), name);
          toast(t("coord_saved"));
          sp.vibrate(30);
          pendingBox.replaceChildren();
          refreshList();
        }}, t("save")),
        micName ? micName : null,
        el("button", { class: "btn btn-ghost", type: "button",
          onClick: () => pendingBox.replaceChildren()
        }, t("cancel"))));
    nameInput.focus();
  }

  const recordBtn = el("button", { class: "btn", type: "button",
    onClick: () => startCoordRecording(({ x, y }) => nameAndSave(x, y))
  }, "📍 " + t("coord_record"));

  const fromShotBtn = el("button", { class: "btn btn-outline", type: "button",
    onClick: () => startCoordFromScreenshot(({ x, y }) => nameAndSave(x, y))
  }, "🖼 " + t("coord_from_screenshot"));

  const loadMawadaBtn = el("button", { class: "btn btn-outline", type: "button",
    onClick: () => {
      const existing = new Set(store.getCoords().map(c => c.name));
      let added = 0;
      for (const p of scaleToScreen(MAWADA_PRESETS)) {
        if (existing.has(p.name)) continue;
        store.addCoord(p);
        added++;
      }
      toast(added ? `${t("coord_mawada_added")} (${added})` : t("coord_mawada_exists"));
      sp.vibrate(30);
      refreshList();
    }
  }, "📋 " + t("coord_load_mawada"));

  m.append(
    el("p", { class: "subtitle" }, t("coords_intro")),
    section(t("coords_title")),
    el("div", { class: "btn-row" }, recordBtn, fromShotBtn, loadMawadaBtn),
    pendingBox,
    listBox,
    el("div", { class: "btn-row" },
      el("button", { class: "btn btn-ghost", type: "button",
        onClick: () => {
          store.clearCoords();
          toast(t("saved"));
          refreshList();
        }
      }, t("coord_clear_all")))
  );
  refreshList();
}

/* ============================================================
 * Settings tab
 * ============================================================ */
function renderSettings(m) {
  const s = store.getSettings();

  m.append(
    el("p", { class: "subtitle" }, t("app_tagline")),

    section(t("set_like_mode")),
    segmented(
      ["normal", "divorced_widowed"],
      [t("mode_normal"), t("mode_dw")],
      [t("mode_normal_sub"), t("mode_dw_sub")],
      s.likeMode,
      (v) => store.setSettings({ likeMode: v })),

    section(t("set_after_refresh")),
    segmented(
      ["continue", "restart"],
      [t("refresh_continue"), t("refresh_restart")],
      [t("refresh_continue_sub"), t("refresh_restart_sub")],
      s.afterRefresh,
      (v) => store.setSettings({ afterRefresh: v })),

    section(t("set_nav")),
    segmented(
      ["online", "search"],
      [t("nav_online"), t("nav_search")],
      [t("nav_online_sub"), t("nav_search_sub")],
      s.navMode,
      (v) => store.setSettings({ navMode: v })),

    section(t("set_screen"))
  );

  // Screen size form
  const wIn = el("input", { type: "number", min: "200", step: "1", value: String(s.screenWidth || 1080), "aria-label": t("screen_w") });
  const hIn = el("input", { type: "number", min: "200", step: "1", value: String(s.screenHeight || 2340), "aria-label": t("screen_h") });
  const screenSaveBtn = el("button", { class: "btn", type: "button",
    onClick: () => {
      const w = parseInt(wIn.value, 10);
      const h = parseInt(hIn.value, 10);
      if (!w || !h || w < 200 || h < 200) return toast(t("screen_invalid"));
      store.setSettings({ screenWidth: w, screenHeight: h });
      toast(t("saved")); sp.vibrate(20);
    }
  }, t("save"));
  m.append(
    el("p", { class: "subtitle" }, t("set_screen_hint")),
    el("label", { class: "field" }, el("span", { class: "field-label" }, t("screen_w")), wIn),
    el("label", { class: "field" }, el("span", { class: "field-label" }, t("screen_h")), hIn),
    el("div", { class: "btn-row" }, screenSaveBtn)
  );

  // Likes per cycle
  const cycleIn = el("input", { type: "number", min: "1", step: "1", value: String(s.likesPerCycle || 80) });
  const cycleSaveBtn = el("button", { class: "btn", type: "button",
    onClick: () => {
      const v = parseInt(cycleIn.value, 10);
      if (!v || v < 1) return toast(t("screen_invalid"));
      store.setSettings({ likesPerCycle: v });
      toast(t("saved")); sp.vibrate(20);
    }
  }, t("save"));
  m.append(
    section(t("set_cycle")),
    el("p", { class: "subtitle" }, t("set_cycle_hint")),
    el("label", { class: "field" }, el("span", { class: "field-label" }, t("set_cycle")), cycleIn),
    el("div", { class: "btn-row" }, cycleSaveBtn)
  );

  // Voice settings
  const ttsRate = el("input", { type: "range", min: "0.5", max: "1.6", step: "0.1", value: String(s.ttsRate || 1.0) });
  ttsRate.addEventListener("change", () => store.setSettings({ ttsRate: parseFloat(ttsRate.value) }));
  const ttsCheckbox = el("input", { type: "checkbox", checked: !!s.tts });
  ttsCheckbox.addEventListener("change", () => store.setSettings({ tts: ttsCheckbox.checked }));
  m.append(
    section(t("set_voice")),
    el("label", { class: "field" },
      el("span", { class: "field-label" }, t("set_tts")),
      ttsCheckbox),
    el("label", { class: "field" },
      el("span", { class: "field-label" }, t("set_tts_rate")),
      ttsRate)
  );

  // Font size
  const fontInput = el("input", { type: "range", min: "1", max: "1.3", step: "0.1", value: String(s.fontStep || 1) });
  fontInput.addEventListener("change", () => {
    const v = parseFloat(fontInput.value);
    store.setSettings({ fontStep: v });
    document.documentElement.style.setProperty("--font-step", String(v));
  });
  m.append(
    el("label", { class: "field" },
      el("span", { class: "field-label" }, t("set_font")),
      fontInput)
  );

  // Language
  const langSel = el("select", null,
    el("option", { value: "ar" }, "العربية"),
    el("option", { value: "en" }, "English"));
  langSel.value = s.language;
  langSel.addEventListener("change", () => {
    store.setSettings({ language: langSel.value });
    setLang(langSel.value);
    render();
  });
  m.append(
    el("label", { class: "field" },
      el("span", { class: "field-label" }, "اللغة / Language"),
      langSel),
    el("p", { class: "subtitle" }, t("version")),
    el("p", { class: "callout" }, t("install_hint"))
  );
}

/* ============================================================
 * Guide tab — dynamic steps based on current settings
 * ============================================================ */
function renderGuide(m) {
  const s = store.getSettings();
  const state = store.getState();

  const navStep   = s.navMode === "search" ? t("guide_nav_search") : t("guide_nav_online");
  const likeStep  = s.likeMode === "divorced_widowed" ? t("guide_like_dw") : t("guide_like_normal");
  const refreshStep = t("guide_refresh").replace("$N", String(s.likesPerCycle || 80));
  const continueStep = s.afterRefresh === "continue"
    ? t("guide_continue").replace("$LAST", state.lastMemberName || t("none"))
    : t("guide_restart");

  const steps = [navStep, likeStep, t("guide_already_added"), refreshStep, continueStep];

  m.append(
    el("p", { class: "subtitle" }, t("guide_intro")),
    section(t("guide_title"))
  );

  steps.forEach((step, i) => {
    m.append(el("div", { class: "callout" },
      el("strong", null, `${t("guide_step")}${i + 1}`),
      el("br"),
      el("span", null, step)));
  });

  m.append(
    el("div", { class: "btn-row" },
      el("button", { class: "btn", type: "button",
        onClick: () => sp.speak(steps.join(". "), { force: true })
      }, "🔊 " + t("read_aloud")),
      el("button", { class: "btn btn-ghost", type: "button",
        onClick: () => sp.stopSpeak()
      }, t("stop_read")))
  );
}

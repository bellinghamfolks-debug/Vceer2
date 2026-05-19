/* Basir AI — bootstrapper + hash router. */

import { setLang, getLang, t } from "./i18n.js";
import * as store from "./storage.js";
import * as sp from "./speech.js";
import { Views, toast } from "./views.js";

// ---------- Apply persisted settings ----------
(function applySettings() {
  const s = store.getSettings();
  setLang(s.language);
  document.documentElement.style.setProperty("--font-step", String(s.fontStep || 1));
})();

// ---------- Service worker registration ----------
if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("sw.js").catch(err => console.warn("sw register failed", err));
  });
}

// ---------- Router ----------
function parseRoute() {
  const hash = (location.hash || "").replace(/^#\/?/, "");
  return hash || "home";
}

function navigate() {
  const route = parseRoute();
  const view = Views[route];
  const back = document.getElementById("backBtn");
  back.hidden = (route === "home");
  back.onclick = () => { history.length > 1 ? history.back() : (location.hash = "#/home"); };

  const ttsBtn = document.getElementById("ttsToggleBtn");
  ttsBtn.hidden = false;
  ttsBtn.setAttribute("aria-pressed", store.getSettings().tts ? "true" : "false");
  ttsBtn.onclick = () => {
    if (sp.isSpeaking()) { sp.stopSpeak(); toast(t("stop_read")); }
    const next = !store.getSettings().tts;
    store.setSettings({ tts: next });
    ttsBtn.setAttribute("aria-pressed", next ? "true" : "false");
    toast(next ? t("on") : t("off"));
  };

  if (view) view();
  else (Views.home)();
}

window.addEventListener("hashchange", navigate);
window.addEventListener("DOMContentLoaded", () => {
  if (!location.hash) location.hash = "#/home";
  else navigate();
});

// Stop TTS when navigating away
window.addEventListener("hashchange", () => { sp.stopSpeak(); });

// Catch unhandled errors gracefully
window.addEventListener("unhandledrejection", (e) => {
  console.error(e.reason);
});

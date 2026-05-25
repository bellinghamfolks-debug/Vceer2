/* مكفوف بوت — bootstrapper. */

import { setLang, t } from "./i18n.js";
import * as store from "./storage.js";
import * as sp from "./speech.js";
import { render } from "./views.js";

(function applySettings() {
  const s = store.getSettings();
  setLang(s.language || "ar");
  document.documentElement.style.setProperty("--font-step", String(s.fontStep || 1));
})();

if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("sw.js").catch(err => console.warn("sw register failed", err));
  });
}

// TTS toggle in the header
window.addEventListener("DOMContentLoaded", () => {
  const ttsBtn = document.getElementById("ttsBtn");
  const refreshTtsBtn = () => {
    const on = !!store.getSettings().tts;
    ttsBtn.setAttribute("aria-pressed", on ? "true" : "false");
    ttsBtn.textContent = on ? "🔊" : "🔇";
  };
  refreshTtsBtn();
  ttsBtn.addEventListener("click", () => {
    if (sp.isSpeaking()) sp.stopSpeak();
    const next = !store.getSettings().tts;
    store.setSettings({ tts: next });
    refreshTtsBtn();
  });

  // Back button hidden by default (single-page tab app)
  document.getElementById("backBtn").hidden = true;

  render();
});

window.addEventListener("unhandledrejection", (e) => console.error(e.reason));

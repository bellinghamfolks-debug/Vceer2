/* مكفوف بوت — speech (TTS) + voice dictation. */

import { getLang } from "./i18n.js";
import { getSettings } from "./storage.js";

/* ---------- TTS ---------- */
let _speakingUtter = null;

export function isSpeaking() {
  return !!_speakingUtter || (typeof speechSynthesis !== "undefined" && speechSynthesis.speaking);
}

export function stopSpeak() {
  try { if (typeof speechSynthesis !== "undefined") speechSynthesis.cancel(); } catch {}
  _speakingUtter = null;
}

export function speak(text, opts = {}) {
  if (!text) return;
  const s = getSettings();
  if (!s.tts && !opts.force) return;
  if (typeof speechSynthesis === "undefined") return;
  try {
    speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(String(text));
    const lang = getLang() === "en" ? "en-US" : "ar-SA";
    u.lang = lang;
    u.rate = Number(s.ttsRate || 1.0);
    u.pitch = 1.0;
    u.onend = () => { _speakingUtter = null; if (opts.onend) opts.onend(); };
    u.onerror = () => { _speakingUtter = null; };
    _speakingUtter = u;
    speechSynthesis.speak(u);
  } catch (e) {
    console.warn("speak failed", e);
  }
}

/* ---------- Vibration ---------- */
export function vibrate(pattern) {
  try { if (navigator.vibrate) navigator.vibrate(pattern); } catch {}
}

/* ---------- Dictation (speech recognition) ---------- */
const SR = window.SpeechRecognition || window.webkitSpeechRecognition;

export function speechRecognitionAvailable() { return !!SR; }

export function createDictation({ onResult, onError, onEnd } = {}) {
  if (!SR) return null;
  const r = new SR();
  r.lang = getLang() === "en" ? "en-US" : "ar-SA";
  r.continuous = false;
  r.interimResults = true;
  let finalText = "";
  r.onresult = (e) => {
    let interim = "";
    for (let i = e.resultIndex; i < e.results.length; i++) {
      const res = e.results[i];
      if (res.isFinal) finalText += res[0].transcript;
      else interim += res[0].transcript;
    }
    if (onResult) onResult({ finalText, interim });
  };
  r.onerror = (e) => { if (onError) onError(e); };
  r.onend = () => { if (onEnd) onEnd(finalText.trim()); };
  return r;
}

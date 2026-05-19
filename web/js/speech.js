/* Basir AI — speech I/O: TTS + speech recognition + vibration. */

import { getSettings } from "./storage.js";

// ---------- Text-to-speech ----------
let currentUtter = null;

export function ttsAvailable() {
  return typeof window !== "undefined" && "speechSynthesis" in window;
}

export function speak(text, opts = {}) {
  if (!ttsAvailable()) return false;
  const s = getSettings();
  if (!s.tts) return false;
  stopSpeak();
  const utter = new SpeechSynthesisUtterance(String(text || ""));
  utter.rate = Math.max(0.5, Math.min(2.0, opts.rate || s.ttsRate || 1.0));
  utter.lang = opts.lang || (s.language === "en" ? "en-US" : "ar-SA");
  utter.onend = () => { currentUtter = null; opts.onend && opts.onend(); };
  utter.onerror = () => { currentUtter = null; opts.onerror && opts.onerror(); };
  currentUtter = utter;
  try { window.speechSynthesis.speak(utter); return true; } catch { return false; }
}

export function stopSpeak() {
  if (!ttsAvailable()) return;
  try { window.speechSynthesis.cancel(); } catch {}
  currentUtter = null;
}

export function isSpeaking() {
  return ttsAvailable() && (window.speechSynthesis.speaking || !!currentUtter);
}

// ---------- Vibration ----------
export function vibrate(pattern) {
  const s = getSettings();
  if (!s.vibrate) return false;
  if (!navigator.vibrate) return false;
  try { return navigator.vibrate(pattern || 60); }
  catch { return false; }
}

// ---------- Speech recognition (dictation) ----------
const SR = window.SpeechRecognition || window.webkitSpeechRecognition;

export function speechRecognitionAvailable() {
  return !!SR;
}

export function createDictation({ lang, onResult, onError, onEnd, continuous = false }) {
  if (!SR) return null;
  const s = getSettings();
  const r = new SR();
  r.lang = lang || (s.language === "en" ? "en-US" : "ar-SA");
  r.interimResults = true;
  r.continuous = !!continuous;
  let finalText = "";
  r.onresult = (e) => {
    let interim = "";
    for (let i = e.resultIndex; i < e.results.length; i++) {
      const res = e.results[i];
      if (res.isFinal) finalText += res[0].transcript;
      else interim += res[0].transcript;
    }
    onResult && onResult({ finalText, interim });
  };
  r.onerror = (e) => onError && onError(e);
  r.onend = () => onEnd && onEnd(finalText);
  return r;
}

// ---------- Locator beep (Web Audio fallback) ----------
let beepCtx;
export function playLocatorBeep(durationMs = 4000) {
  try {
    beepCtx = beepCtx || new (window.AudioContext || window.webkitAudioContext)();
    const ctx = beepCtx;
    if (ctx.state === "suspended") ctx.resume();
    const now = ctx.currentTime;
    const stopAt = now + durationMs / 1000;
    let t = now;
    while (t < stopAt) {
      const o = ctx.createOscillator();
      const g = ctx.createGain();
      o.type = "sine";
      o.frequency.value = 880;
      g.gain.value = 0;
      g.gain.linearRampToValueAtTime(0.25, t + 0.02);
      g.gain.linearRampToValueAtTime(0, t + 0.3);
      o.connect(g).connect(ctx.destination);
      o.start(t);
      o.stop(t + 0.32);
      t += 0.5;
    }
    return true;
  } catch (e) {
    return false;
  }
}

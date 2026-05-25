/* مكفوف بوت — localStorage facade.
 * Standalone storage independent of Basir. */

const K = {
  settings: "mkfwf.settings.v1",
  state:    "mkfwf.state.v1",
  coords:   "mkfwf.coords.v1",
  log:      "mkfwf.log.v1"
};

const DEFAULT_SETTINGS = {
  language: "ar",
  tts: true,
  ttsRate: 1.0,
  fontStep: 1,
  likeMode: "normal",        // normal | divorced_widowed
  afterRefresh: "continue",  // continue | restart
  navMode: "online",         // online | search
  screenWidth: 1080,
  screenHeight: 2340,
  likesPerCycle: 80          // refresh after this many likes
};

function read(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    if (raw == null) return fallback;
    return JSON.parse(raw);
  } catch { return fallback; }
}
function write(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); }
  catch (e) { console.warn("storage write failed", key, e); }
}

/* ---------- Settings ---------- */
export function getSettings() {
  return { ...DEFAULT_SETTINGS, ...read(K.settings, {}) };
}
export function setSettings(patch) {
  const next = { ...getSettings(), ...patch };
  write(K.settings, next);
  return next;
}

/* ---------- Bot session state ---------- */
export function getState() {
  return read(K.state, { lastMemberName: "", processedCount: 0, cycleLikes: 0, at: null });
}
export function setState(patch) {
  write(K.state, { ...getState(), ...patch, at: Date.now() });
}
export function clearState() { localStorage.removeItem(K.state); }

/* ---------- Coordinates ---------- */
export function getCoords() { return read(K.coords, []); }
export function addCoord(coord) {
  const list = getCoords();
  list.push({ id: Date.now() + Math.random().toString(36).slice(2, 5), ...coord });
  write(K.coords, list);
}
export function updateCoord(id, patch) {
  write(K.coords, getCoords().map(c => c.id === id ? { ...c, ...patch } : c));
}
export function removeCoord(id) {
  write(K.coords, getCoords().filter(c => c.id !== id));
}
export function clearCoords() { localStorage.removeItem(K.coords); }

/* ---------- Activity log (text only) ---------- */
export function getLog() { return read(K.log, []); }
export function pushLog(type, content) {
  const list = getLog();
  list.unshift({ id: Date.now(), type, content: String(content || "").slice(0, 300), at: Date.now() });
  if (list.length > 200) list.length = 200;
  write(K.log, list);
}
export function clearLog() { localStorage.removeItem(K.log); }

/* ---------- Bulk reset ---------- */
export function resetAll() {
  localStorage.removeItem(K.state);
  localStorage.removeItem(K.coords);
  localStorage.removeItem(K.log);
  // keep settings (user-chosen modes + screen size)
}

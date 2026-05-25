/* Basir AI — localStorage facade.
 * Sections: settings, archive, history, memory, emergency.
 * Everything stays on the user's device. */

const K = {
  settings: "basir.settings.v1",
  archive: "basir.archive.v1",
  history: "basir.history.v1",
  memory_people: "basir.memory.people.v1",
  memory_products: "basir.memory.products.v1",
  memory_places: "basir.memory.places.v1",
  emergency: "basir.emergency.v1",
  qaDoc: "basir.qa.doc.v1",
  botState: "basir.bot.state.v1"
};

const DEFAULT_SETTINGS = {
  language: navigator.language && navigator.language.startsWith("ar") ? "ar" : "ar",
  tts: true,
  ttsRate: 1.0,
  fontStep: 1,
  privacy: false,
  autoSave: true,
  proxyUrl: "",
  appToken: "",
  quality: "balanced",
  likeMode: "normal",        // normal | divorced_widowed
  afterRefresh: "continue",  // continue | restart
  botNavMode: "online"       // online | search (navigation path in the app)
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

// ---------- Settings ----------
export function getSettings() {
  const stored = read(K.settings, {});
  return { ...DEFAULT_SETTINGS, ...stored };
}
export function setSettings(patch) {
  const next = { ...getSettings(), ...patch };
  write(K.settings, next);
  return next;
}

// ---------- Archive ----------
export function getArchive() { return read(K.archive, []); }
export function addToArchive(item) {
  const list = getArchive();
  list.unshift({ id: Date.now() + "-" + Math.random().toString(36).slice(2, 7), ...item });
  if (list.length > 200) list.length = 200;
  write(K.archive, list);
}
export function removeFromArchive(id) {
  write(K.archive, getArchive().filter(x => x.id !== id));
}
export function clearArchive() { write(K.archive, []); }

// ---------- History (text only) ----------
export function getHistory() { return read(K.history, []); }
export function pushHistory(type, content) {
  const list = getHistory();
  list.unshift({ id: Date.now(), type, content: String(content || "").slice(0, 500), at: Date.now() });
  if (list.length > 300) list.length = 300;
  write(K.history, list);
}
export function clearHistory() { write(K.history, []); }

// ---------- Memory ----------
export function getPeople()  { return read(K.memory_people, []); }
export function getProducts(){ return read(K.memory_products, []); }
export function getPlaces()  { return read(K.memory_places, []); }
export function addPerson(p) {
  const list = getPeople(); list.unshift({ id: Date.now(), ...p }); write(K.memory_people, list);
}
export function addProduct(p) {
  const list = getProducts(); list.unshift({ id: Date.now(), ...p }); write(K.memory_products, list);
}
export function addPlace(p) {
  const list = getPlaces(); list.unshift({ id: Date.now(), ...p }); write(K.memory_places, list);
}
export function removeMemory(kind, id) {
  const key = kind === "people" ? K.memory_people
    : kind === "products" ? K.memory_products
    : K.memory_places;
  write(key, read(key, []).filter(x => x.id !== id));
}

// ---------- Emergency contact ----------
export function getEmergency() { return read(K.emergency, { phone: "", name: "" }); }
export function setEmergency(v) { write(K.emergency, v || { phone: "", name: "" }); }

// ---------- Q&A doc ref (v2.0) ----------
export function getQaDoc() { return read(K.qaDoc, null); }
export function setQaDoc(v) {
  if (!v) { localStorage.removeItem(K.qaDoc); return; }
  write(K.qaDoc, { ...v, at: Date.now() });
}
export function clearQaDoc() { localStorage.removeItem(K.qaDoc); }
export function isQaDocFresh() {
  const d = getQaDoc();
  if (!d || !d.at) return false;
  // Gemini Files API auto-expires after ~48h. Use 47h to be safe.
  return Date.now() - d.at < 47 * 60 * 60 * 1000;
}

// ---------- Bot state (position tracking) ----------
export function getBotState() {
  return read(K.botState, { lastMemberName: "", processedCount: 0, at: null });
}
export function setBotState(patch) {
  write(K.botState, { ...getBotState(), ...patch, at: Date.now() });
}
export function clearBotState() {
  localStorage.removeItem(K.botState);
}

// ---------- Bulk reset ----------
export function deleteAllUserData() {
  write(K.archive, []);
  write(K.history, []);
  write(K.memory_people, []);
  write(K.memory_products, []);
  write(K.memory_places, []);
  clearQaDoc();
  // keep settings + emergency contact unless explicitly wiped
}

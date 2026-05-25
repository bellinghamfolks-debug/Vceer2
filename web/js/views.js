/* Basir AI — screen renderers.
 * Each view function paints into the #main element.
 * Navigation uses hash routing handled by app.js. */

import { t, getLang, setLang, TRANSLATE_LANGS } from "./i18n.js";
import * as store from "./storage.js";
import * as api from "./api.js";
import * as sp from "./speech.js";

// ---------- DOM helpers ----------
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
  m.scrollTop = 0;
  window.scrollTo(0, 0);
  m.focus({ preventScroll: true });
}

export function setTitle(title) {
  document.getElementById("appTitle").textContent = title;
}

export function toast(text, ms = 2200) {
  const t = document.getElementById("toast");
  t.textContent = text;
  t.hidden = false;
  clearTimeout(toast._timer);
  toast._timer = setTimeout(() => { t.hidden = true; }, ms);
}

function card(title, desc, onClick, ariaLabel) {
  return el("button", { class: "card", type: "button", onClick, "aria-label": ariaLabel || title },
    el("span", { class: "card-title" }, title),
    desc ? el("span", { class: "card-desc" }, desc) : null);
}

function backButton(href = "#/home") {
  return el("button", {
    class: "btn btn-ghost", type: "button",
    onClick: () => { history.length > 1 ? history.back() : (location.hash = href); }
  }, t("back"));
}

function loading(label) {
  return el("div", { class: "loading", role: "status" },
    el("div", { class: "spinner", "aria-hidden": "true" }),
    el("span", null, label || t("loading")));
}

function showDialog(title, body, buttons) {
  const dlg = document.getElementById("dialog");
  while (dlg.firstChild) dlg.removeChild(dlg.firstChild);
  dlg.append(el("h2", null, title));
  if (body) dlg.append(el("p", null, body));
  const actions = el("div", { class: "actions" });
  for (const b of buttons) {
    const btn = el("button", {
      class: "btn " + (b.style === "primary" ? "" : b.style === "danger" ? "btn-danger" : "btn-ghost"),
      type: "button",
      onClick: () => { dlg.close(); b.onClick && b.onClick(); }
    }, b.label);
    actions.append(btn);
  }
  dlg.append(actions);
  if (typeof dlg.showModal === "function") dlg.showModal();
  else dlg.setAttribute("open", "");
}

function ensureSetup(onMissing) {
  // For the same-origin case (proxyUrl empty), the in-page server handles requests.
  // For an external host, proxyUrl must be set. App token is optional.
  return true; // we let server respond; failure shows error toast
}

// ---------- Home (v2.1.2: 4 tabs with rich cards) ----------
let currentHomeTab = 0; // 0=talk, 1=vision, 2=documents, 3=more

function richCard(icon, title, desc, onClick) {
  return el("button", {
    class: "rcard", type: "button", onClick,
    "aria-label": title
  },
    el("span", { class: "rcard-icon", "aria-hidden": "true" }, icon),
    el("span", { class: "rcard-body" },
      el("span", { class: "rcard-title" }, title),
      desc ? el("span", { class: "rcard-desc" }, desc) : null
    )
  );
}

function sectionHeader(text) {
  return el("h2", { class: "section-header" }, text);
}

function tabBar() {
  const tabs = [
    { id: 0, icon: "💬", label: t("tab_talk") },
    { id: 1, icon: "👁",  label: t("tab_vision") },
    { id: 2, icon: "📄", label: t("tab_documents") },
    { id: 3, icon: "⋯",  label: t("tab_more") }
  ];
  const bar = el("div", { class: "tabbar", role: "tablist" });
  for (const tab of tabs) {
    const selected = tab.id === currentHomeTab;
    const btn = el("button", {
      class: "tab" + (selected ? " selected" : ""),
      type: "button", role: "tab",
      "aria-selected": selected ? "true" : "false",
      "aria-label": `${tab.label}, ${t("tab_talk") === t("tab_talk") ? "" : ""}`
    },
      el("span", { class: "tab-icon", "aria-hidden": "true" }, tab.icon),
      el("span", { class: "tab-label" }, tab.label)
    );
    btn.addEventListener("click", () => {
      currentHomeTab = tab.id;
      viewHome();
    });
    bar.append(btn);
  }
  return bar;
}

function renderTalkTab(m) {
  m.append(
    sectionHeader(t("section_talk")),
    richCard("💬", t("nav_ask"), t("nav_ask_desc"), () => location.hash = "#/ask"),
    richCard("🎙️", t("nav_voice_convo"), t("nav_voice_convo_desc"), () => location.hash = "#/voice-convo")
  );
}

function renderVisionTab(m) {
  m.append(
    sectionHeader(t("section_vision")),
    richCard("📷", t("nav_describe"), t("nav_describe_desc"), () => location.hash = "#/describe"),
    richCard("🚶", t("nav_walking"), t("nav_walking_desc"), () => location.hash = "#/walking"),
    richCard("💵", t("nav_currency"), t("nav_currency_desc"), () => imageTask(
      "currency_or_receipt", t("currency_title"),
      "You are Basir, an assistant for blind and low-vision users. The image contains either banknotes/coins OR a paid receipt/invoice. BANKNOTES/COINS: state the currency and denomination in the FIRST sentence, e.g. 'هذه ورقة من فئة 100 ريال سعودي' / 'This is a 100 Saudi Riyal banknote'. If multiple notes are visible, list each one. Mention the total at the end. RECEIPTS/INVOICES: state the grand total and the currency in the FIRST sentence. Then briefly list the merchant name, date, and 3-4 most expensive line items if they're legible. Keep the entire answer under 80 words, plain prose, no bullets or markdown — this is read aloud by TTS."
    ))
  );
}

function renderDocumentsTab(m) {
  m.append(sectionHeader(t("section_docs_analysis")));
  m.append(richCard("📄", t("nav_documents"), t("nav_documents_desc"), () => location.hash = "#/documents"));
  if (store.isQaDocFresh()) {
    const doc = store.getQaDoc();
    const intro = doc && doc.displayName ? t("docqa_intro") + doc.displayName : t("nav_doc_qa_desc");
    m.append(richCard("❓", t("nav_doc_qa"), intro, () => location.hash = "#/doc-qa"));
  }
  m.append(
    sectionHeader(t("section_language")),
    richCard("🌐", t("nav_translate"), t("nav_translate_desc"), () => location.hash = "#/translate")
  );
}

function renderMoreTab(m) {
  m.append(
    sectionHeader(t("section_quick_help")),
    richCard("🆘", t("nav_emergency"), t("nav_emergency_desc"), () => location.hash = "#/emergency"),
    sectionHeader(t("section_tools")),
    richCard("🤖", t("nav_bot"), t("nav_bot_desc"), () => location.hash = "#/bot"),
    richCard("🛠", t("nav_advanced"), t("nav_advanced_desc"), () => location.hash = "#/advanced"),
    richCard("🧠", t("nav_memory"), t("nav_memory_desc"), () => location.hash = "#/memory"),
    richCard("📚", t("nav_archive"), t("nav_archive_desc"), () => location.hash = "#/archive"),
    richCard("📜", t("nav_history"), t("nav_history_desc"), () => location.hash = "#/history"),
    sectionHeader(t("section_app")),
    richCard("⚙️", t("nav_settings"), t("nav_settings_desc"), () => location.hash = "#/settings"),
    richCard("ℹ️", t("nav_about"), t("nav_about_desc"), () => location.hash = "#/about"),
    el("div", { class: "btn-row" },
      el("button", { class: "btn btn-ghost", type: "button", onClick: () => location.hash = "#/status" }, t("nav_status")),
      sp.speechRecognitionAvailable()
        ? el("button", { class: "btn btn-ghost", type: "button", onClick: () => location.hash = "#/voice-convo" }, t("nav_voice"))
        : null
    )
  );
}

export function viewHome() {
  clear();
  setTitle(t("home_title"));
  const m = $main();
  m.append(
    el("p", { class: "subtitle" }, t("home_subtitle_tabs")),
    tabBar()
  );
  switch (currentHomeTab) {
    case 1: renderVisionTab(m); break;
    case 2: renderDocumentsTab(m); break;
    case 3: renderMoreTab(m); break;
    case 0:
    default: renderTalkTab(m); break;
  }
}

// ---------- More ----------
export function viewMore() {
  clear();
  setTitle(t("nav_more"));
  const m = $main();
  m.append(
    el("p", { class: "subtitle" }, t("more_subtitle")),
    el("nav", { class: "card-list" },
      card(t("nav_advanced"), t("nav_advanced_desc"), () => location.hash = "#/advanced"),
      card(t("nav_memory"), t("nav_memory_desc"), () => location.hash = "#/memory"),
      card(t("nav_archive"), t("nav_archive_desc"), () => location.hash = "#/archive"),
      card(t("nav_history"), t("nav_history_desc"), () => location.hash = "#/history"),
      card(t("nav_settings"), t("nav_settings_desc"), () => location.hash = "#/settings"),
      card(t("nav_about"), t("nav_about_desc"), () => location.hash = "#/about"),
      sp.speechRecognitionAvailable() ? card(t("nav_voice"), null, () => startVoiceCommand()) : null,
    )
  );
}

// ---------- Status ----------
export function viewStatus() {
  clear();
  setTitle(t("nav_status"));
  const m = $main();
  const s = store.getSettings();
  const proxy = s.proxyUrl ? s.proxyUrl : (location.origin + " (نفس الأصل)");

  m.append(
    el("p", { class: "subtitle" }, t("status_subtitle")),
    info(t("s_language"), s.language === "en" ? "English" : "العربية"),
    info(t("s_privacy"), s.privacy ? t("on") : t("off")),
    info("Proxy: ", proxy),
    info(t("s_tts"), s.tts ? t("s_tts_on") : t("s_tts_off")),
    info(t("s_autosave"), s.autoSave ? t("on") : t("off")),
    info(t("s_version"), t("about_version")),
    backButton()
  );
}

function info(label, value) {
  return el("div", { class: "info" },
    el("div", { class: "info-label" }, label),
    el("div", { class: "info-body" }, value));
}

// ---------- Ask ----------
export function viewAsk() {
  clear();
  setTitle(t("nav_ask"));
  const m = $main();
  let input;
  let mic;

  const resultBox = el("div", { id: "askResult", "aria-live": "polite" });

  const onSend = async () => {
    const text = (input.value || "").trim();
    if (!text) return toast(t("ask_first"));
    sp.vibrate(30);
    resultBox.replaceChildren(loading(t("loading")));
    try {
      const ans = await api.askBasir({ task: "ask", input: text });
      showResult(resultBox, t("ask_answer_title"), ans, { logType: t("log_question"), logContent: text });
    } catch (e) {
      resultBox.replaceChildren(errorBox(e));
    }
  };

  input = el("textarea", { id: "askInput", placeholder: t("ask_placeholder"), "aria-label": t("ask_placeholder") });
  mic = micButton((text) => { input.value = (input.value ? input.value + " " : "") + text; input.focus(); });

  m.append(
    el("p", { class: "subtitle" }, t("ask_subtitle")),
    el("label", { class: "field" },
      el("span", { class: "field-label" }, t("nav_ask")),
      input
    ),
    el("div", { class: "btn-row" },
      el("button", { class: "btn btn-block", type: "button", onClick: onSend }, t("send")),
      mic ? mic : null,
      el("button", { class: "btn btn-ghost", type: "button", onClick: () => { input.value = ""; resultBox.replaceChildren(); } }, t("clear"))
    ),
    resultBox,
    backButton()
  );
}

// ---------- Describe ----------
export function viewDescribe() {
  clear();
  setTitle(t("nav_describe"));
  const m = $main();
  m.append(
    el("p", { class: "subtitle" }, t("describe_subtitle")),
    el("nav", { class: "card-list" },
      card(t("describe_full"), t("describe_full_desc"), () => imageTask("describe", t("describe_full"),
        "Describe this image thoroughly for a blind user: layout, main elements, visible text, colors, and any risks or context.")),
      card(t("describe_alt"), t("describe_alt_desc"), () => imageTask("alt", t("describe_alt"),
        "Generate a short, precise alt text (1-2 sentences) suitable for screen-reader users.")),
      card(t("describe_screen"), t("describe_screen_desc"), () => imageTask("screenshot", t("describe_screen"),
        "Read this screenshot for a blind user. Describe each visible UI element, the screen's purpose, and suggest the next step.")),
      card(t("describe_scene"), t("describe_scene_desc"), () => sceneTextTask()),
    ),
    backButton()
  );
}

function imageTask(task, title, instruction) {
  clear();
  setTitle(title);
  const m = $main();
  let imgPreview = el("img", { class: "preview", alt: "", hidden: true });
  let chosenFile = null;

  const fileInputCamera = el("input", { type: "file", accept: "image/*", capture: "environment", class: "sr-only" });
  const fileInputGallery = el("input", { type: "file", accept: "image/*", class: "sr-only" });

  const onChosen = async (file) => {
    if (!file) return;
    if (file.size > 25 * 1024 * 1024) { toast(t("image_too_large")); return; }
    chosenFile = await api.compressImage(file);
    const url = URL.createObjectURL(chosenFile);
    imgPreview.src = url;
    imgPreview.hidden = false;
  };

  fileInputCamera.addEventListener("change", e => onChosen(e.target.files[0]));
  fileInputGallery.addEventListener("change", e => onChosen(e.target.files[0]));

  const resultBox = el("div", { "aria-live": "polite" });

  const onRun = async () => {
    if (!chosenFile) return toast(t("no_image_chosen"));
    sp.vibrate(30);
    resultBox.replaceChildren(loading(t("image_analyzing")));
    try {
      const base64 = await api.fileToBase64(chosenFile);
      const ans = await api.askBasir({
        task,
        input: "[image]",
        instruction,
        imageBase64: base64,
        mimeType: chosenFile.type || "image/jpeg"
      });
      const logType = task === "alt" ? t("log_alt")
                   : task === "screenshot" ? t("log_screenshot")
                   : t("log_image");
      showResult(resultBox, title, ans, { logType, logContent: title });
    } catch (e) {
      resultBox.replaceChildren(errorBox(e));
    }
  };

  m.append(
    el("p", { class: "subtitle" }, t("image_source")),
    el("div", { class: "btn-row" },
      el("button", { class: "btn", type: "button", onClick: () => fileInputCamera.click() }, t("take_photo")),
      el("button", { class: "btn btn-outline", type: "button", onClick: () => fileInputGallery.click() }, t("pick_gallery")),
    ),
    fileInputCamera, fileInputGallery,
    imgPreview,
    el("div", { class: "btn-row" },
      el("button", { class: "btn btn-block", type: "button", onClick: onRun }, t("analyze_image")),
    ),
    resultBox,
    backButton()
  );
}

function sceneTextTask() {
  textTask({
    title: t("describe_scene"),
    task: "scene",
    placeholder: t("paste_text"),
    instruction: "The user is blind. They describe their surroundings in free text. Turn it into 3-6 short, practical guidance lines (objects, layout, possible obstacles, suggested action). Be concise and screen-reader friendly.",
    logType: t("log_scene")
  });
}

// ---------- Documents ----------
export function viewDocuments() {
  clear();
  setTitle(t("nav_documents"));
  const m = $main();
  m.append(
    el("p", { class: "subtitle" }, t("documents_subtitle")),
    el("nav", { class: "card-list" },
      card(t("doc_general"), t("doc_general_desc"), () => textTask({
        title: t("doc_general"), task: "doc", placeholder: t("paste_text"),
        instruction: "Analyze this text for a blind user. Give a clear structured summary: title, main points, action items.",
        logType: t("log_document"),
        allowFile: true
      })),
      card(t("doc_invoice"), t("doc_invoice_desc"), () => textTask({
        title: t("doc_invoice"), task: "invoice", placeholder: t("paste_text"),
        instruction: "Treat this as an invoice. Extract: issuer, amount, currency, due date, account/IBAN, reference numbers, late-fee notes. Return structured bullets.",
        logType: t("log_invoice"),
        allowFile: true
      })),
      card(t("doc_legal"), t("doc_legal_desc"), () => textTask({
        title: t("doc_legal"), task: "legal", placeholder: t("paste_text"),
        instruction: "Explain this legal text for a layperson. List obligations, rights, durations, penalties, and risks. Add a note that this is educational and not legal advice.",
        logType: t("log_legal"),
        allowFile: true
      })),
      card(t("doc_medical"), t("doc_medical_desc"), () => textTask({
        title: t("doc_medical"), task: "medical", placeholder: t("paste_text"),
        instruction: "Explain this medical note safely for a layperson. Define unfamiliar terms. Do NOT give diagnosis or treatment; suggest consulting a doctor.",
        logType: t("log_medical"),
        allowFile: true
      })),
      card(t("doc_convert"), t("doc_convert_desc"), () => location.hash = "#/convert"),
    ),
    backButton()
  );
}

// ---------- Translate ----------
export function viewTranslate() {
  clear();
  setTitle(t("nav_translate"));
  const m = $main();
  let srcSel, tgtSel, input, resultBox;

  const onSwap = () => {
    if (srcSel.value === "auto") return toast(t("swap_disabled"));
    const a = srcSel.value, b = tgtSel.value;
    srcSel.value = b; tgtSel.value = a;
    toast(t("swap_done"));
  };

  const onRun = async () => {
    const text = (input.value || "").trim();
    if (!text) return toast(t("paste_first"));
    const src = srcSel.value;
    const tgt = tgtSel.value;
    sp.vibrate(30);
    resultBox.replaceChildren(loading(t("loading")));
    try {
      const ans = await api.askBasir({
        task: "translate",
        input: text,
        instruction: `Translate the text. Source language: ${src === "auto" ? "auto-detect" : src}. Target language: ${tgt}. Preserve meaning, tone, and proper nouns. Add a one-line note on tone or context if helpful. Output the translation followed by an optional brief note. No code fences.`,
        language: tgt === "ar" ? "ar" : "en"
      });
      showResult(resultBox, t("translation"), ans, { logType: t("log_translation"), logContent: text });
    } catch (e) {
      resultBox.replaceChildren(errorBox(e));
    }
  };

  srcSel = el("select", { "aria-label": t("source_lang") },
    ...TRANSLATE_LANGS.map(([code, key]) => el("option", { value: code }, t(key))));
  srcSel.value = "auto";
  tgtSel = el("select", { "aria-label": t("target_lang") },
    ...TRANSLATE_LANGS.filter(([c]) => c !== "auto").map(([code, key]) => el("option", { value: code }, t(key))));
  tgtSel.value = getLang() === "ar" ? "en" : "ar";

  input = el("textarea", { placeholder: t("paste_to_translate"), "aria-label": t("paste_to_translate") });
  resultBox = el("div", { "aria-live": "polite" });

  m.append(
    el("p", { class: "subtitle" }, t("translate_subtitle")),
    el("label", { class: "field" }, el("span", { class: "field-label" }, t("source_lang")), srcSel),
    el("label", { class: "field" }, el("span", { class: "field-label" }, t("target_lang")), tgtSel),
    el("div", { class: "btn-row" },
      el("button", { class: "btn btn-ghost", type: "button", onClick: onSwap }, t("swap_langs"))
    ),
    el("label", { class: "field" },
      el("span", { class: "field-label" }, t("paste_to_translate")),
      input
    ),
    el("div", { class: "btn-row" },
      el("button", { class: "btn btn-block", type: "button", onClick: onRun }, t("translate"))
    ),
    resultBox,
    backButton()
  );
}

// ---------- Advanced ----------
export function viewAdvanced() {
  clear();
  setTitle(t("nav_advanced"));
  const m = $main();
  m.append(
    el("p", { class: "subtitle" }, t("advanced_subtitle")),
    el("nav", { class: "card-list" },
      card(t("adv_cards"), t("adv_cards_desc"), () => textTask({
        title: t("adv_cards"), task: "study", placeholder: t("paste_text"),
        instruction: "Turn this text into 5-10 study cards. Format each as: Q: ... / A: ... Make them concise and screen-reader friendly.",
        logType: t("log_document")
      })),
      card(t("adv_reply"), t("adv_reply_desc"), () => textTask({
        title: t("adv_reply"), task: "reply", placeholder: t("paste_text"),
        instruction: "Draft a polite, context-appropriate reply to this message. Match the tone (formal/informal) of the input. Output the reply only.",
        logType: t("log_reply")
      })),
      card(t("adv_table"), t("adv_table_desc"), () => textTask({
        title: t("adv_table"), task: "table", placeholder: t("paste_text"),
        instruction: "Convert this table into clear, linear text suitable for a screen reader. State dimensions, headers, and read each row as: header: value, separated by commas. Add a 1-sentence summary at the end.",
        logType: t("log_table")
      })),
    ),
    backButton()
  );
}

// ---------- Generic text task screen ----------
function textTask({ title, task, placeholder, instruction, logType, allowFile = false }) {
  clear();
  setTitle(title);
  const m = $main();
  let input;
  let chosenFile = null;
  let chosenLabel;
  const resultBox = el("div", { "aria-live": "polite" });

  const fileInput = el("input", { type: "file", accept: ".pdf,image/*", class: "sr-only" });
  fileInput.addEventListener("change", async (e) => {
    chosenFile = e.target.files[0] || null;
    if (chosenFile && chosenFile.type.startsWith("image/")) chosenFile = await api.compressImage(chosenFile);
    chosenLabel.textContent = chosenFile ? chosenFile.name : "";
  });

  const onRun = async () => {
    const text = (input.value || "").trim();
    if (!text && !chosenFile) return toast(t("type_first"));
    sp.vibrate(30);
    resultBox.replaceChildren(loading(t("analyzing")));
    try {
      let imageBase64 = null, mimeType = null;
      if (chosenFile && chosenFile.type.startsWith("image/")) {
        imageBase64 = await api.fileToBase64(chosenFile);
        mimeType = chosenFile.type;
      }
      const ans = await api.askBasir({
        task, input: text || "[file]", instruction,
        imageBase64, mimeType
      });
      showResult(resultBox, title, ans, { logType: logType || title, logContent: (text || "[file]").slice(0, 200) });
    } catch (e) {
      resultBox.replaceChildren(errorBox(e));
    }
  };

  input = el("textarea", { placeholder, "aria-label": placeholder });
  chosenLabel = el("div", { class: "info-label" });

  const mic = micButton((text) => { input.value = (input.value ? input.value + " " : "") + text; input.focus(); });

  m.append(
    el("label", { class: "field" }, el("span", { class: "field-label" }, title), input),
    allowFile ? el("div", { class: "btn-row" },
      el("button", { class: "btn btn-ghost", type: "button", onClick: () => fileInput.click() }, t("attach_file")),
      chosenLabel
    ) : null,
    allowFile ? fileInput : null,
    el("div", { class: "btn-row" },
      el("button", { class: "btn btn-block", type: "button", onClick: onRun }, t("run")),
      mic ? mic : null
    ),
    resultBox,
    backButton()
  );
}

// ---------- Convert (PDF/PPTX → DOCX) ----------
export function viewConvert() {
  clear();
  setTitle(t("convert_title"));
  const m = $main();
  let quality = store.getSettings().quality || "balanced";
  let mode = "full";
  const fileInput = el("input", { type: "file", accept: ".pdf,.ppt,.pptx,application/pdf,application/vnd.openxmlformats-officedocument.presentationml.presentation", class: "sr-only" });

  const qualityRow = segmented(
    ["fast", "balanced", "best"],
    [t("quality_fast"), t("quality_balanced"), t("quality_best")],
    null,
    quality,
    (v) => { quality = v; }
  );
  const modeRow = segmented(
    ["full", "simple", "descriptions_only", "text_only"],
    [t("mode_full"), t("mode_simple"), t("mode_descriptions_only"), t("mode_text_only")],
    [t("mode_full_sub"), t("mode_simple_sub"), t("mode_descriptions_only_sub"), t("mode_text_only_sub")],
    mode,
    (v) => { mode = v; }
  );

  const progress = el("div", { "aria-live": "polite" });
  const result = el("div");

  fileInput.addEventListener("change", () => {
    const f = fileInput.files[0];
    if (!f) return;
    showDialog(t("convert_privacy_title"), t("convert_privacy_body"), [
      { label: t("cancel"), style: "ghost", onClick: () => { fileInput.value = ""; } },
      { label: t("continue"), style: "primary", onClick: () => runConvert(f) }
    ]);
  });

  async function runConvert(file) {
    progress.replaceChildren(loading(t("convert_uploading")));
    result.replaceChildren();
    // Kick off the Files API upload in parallel so Document Q&A is ready
    // by the time the user wants it. Best-effort; failures are silent.
    const qaUpload = (file.type === "application/pdf" || /\.pdf$/i.test(file.name))
      ? api.uploadForQa({ file }).then(r => {
          store.setQaDoc({
            fileUri: r.fileUri,
            fileName: r.fileName,
            mimeType: r.mimeType,
            displayName: r.displayName || file.name
          });
        }).catch(() => { /* ignore — Q&A simply won't be available */ })
      : Promise.resolve();
    try {
      const blob = await api.convertFile({
        file,
        language: getLang(),
        mode,
        quality
      });
      const baseName = (file.name || "basir").replace(/\.[^.]+$/, "");
      const url = URL.createObjectURL(blob);
      progress.replaceChildren();
      const dlLink = el("a", { class: "btn btn-block", href: url, download: baseName + ".docx" }, t("download"));
      const shareBtn = (navigator.canShare && navigator.canShare({ files: [new File([blob], baseName + ".docx", { type: blob.type })] }))
        ? el("button", { class: "btn btn-outline", type: "button", onClick: async () => {
            try {
              await navigator.share({ files: [new File([blob], baseName + ".docx", { type: blob.type })], title: baseName });
            } catch {}
          }}, t("convert_share"))
        : null;
      // Wait for upload (Q&A) to finish so we can show the "Ask about it" CTA.
      await qaUpload;
      const qaBtn = store.isQaDocFresh()
        ? el("button", { class: "btn btn-outline", type: "button", onClick: () => location.hash = "#/doc-qa" }, t("nav_doc_qa"))
        : null;
      result.append(
        el("div", { class: "callout" }, t("convert_done")),
        el("div", { class: "btn-row" }, dlLink, shareBtn, qaBtn)
      );
      store.pushHistory(t("log_conversion"), file.name);
      sp.vibrate([60, 60, 60]);
    } catch (e) {
      progress.replaceChildren();
      result.replaceChildren(errorBox(e, t("convert_failed")));
    }
  }

  m.append(
    el("p", { class: "subtitle" }, t("convert_intro")),
    el("p", null, t("convert_supported")),
    el("h3", null, t("convert_quality")),
    qualityRow,
    el("h3", null, t("convert_mode")),
    modeRow,
    el("div", { class: "btn-row" },
      el("button", { class: "btn btn-block", type: "button", onClick: () => fileInput.click() }, t("convert_pick"))
    ),
    fileInput,
    progress,
    result,
    backButton("#/documents")
  );
}

// ---------- Emergency ----------
export function viewEmergency() {
  clear();
  setTitle(t("nav_emergency"));
  const m = $main();
  const contact = store.getEmergency();
  const contactInfo = contact.phone
    ? el("div", { class: "info" }, el("div", { class: "info-label" }, t("emergency_saved_contact")),
        el("div", { class: "info-body" }, (contact.name ? contact.name + " · " : "") + contact.phone))
    : el("div", { class: "callout callout-danger" }, t("emergency_no_contact"));

  const onSend = async () => {
    const c = store.getEmergency();
    if (!c.phone) { location.hash = "#/emergency/contact"; return; }
    showDialog(t("emergency_confirm_title"), t("emergency_confirm_body"), [
      { label: t("cancel"), style: "ghost" },
      { label: t("emergency_send_now"), style: "danger", onClick: () => sendEmergency(c) }
    ]);
  };

  async function sendEmergency(c) {
    sp.vibrate([100, 80, 100, 80, 100]);
    let locPart = "";
    try {
      const pos = await getPosition({ timeout: 6000 });
      const { latitude, longitude } = pos.coords;
      locPart = `https://maps.google.com/?q=${latitude},${longitude}`;
    } catch (e) {
      // proceed without location
    }
    const body = t("emergency_msg_prefix") + (locPart || "(غير متاح)");
    const url = `sms:${encodeURIComponent(c.phone)}${navigator.userAgent.includes("iPhone") ? "&" : "?"}body=${encodeURIComponent(body)}`;
    store.pushHistory(t("log_emergency"), c.phone);
    location.href = url;
  }

  async function shareLocation() {
    try {
      const pos = await getPosition({ timeout: 8000 });
      const { latitude, longitude } = pos.coords;
      const link = `https://maps.google.com/?q=${latitude},${longitude}`;
      if (navigator.share) {
        try { await navigator.share({ title: "موقعي", text: t("emergency_msg_prefix") + link, url: link }); return; }
        catch {}
      }
      await navigator.clipboard.writeText(link);
      toast(link, 4000);
    } catch (e) {
      toast(t("emergency_no_loc"));
    }
  }

  m.append(
    el("p", { class: "subtitle" }, t("emergency_subtitle")),
    contactInfo,
    el("div", { class: "btn-row" },
      el("button", { class: "btn btn-danger btn-block", type: "button", onClick: onSend }, t("emergency_send"))
    ),
    el("div", { class: "btn-row" },
      el("button", { class: "btn btn-outline", type: "button", onClick: shareLocation }, t("emergency_share_loc")),
      el("button", { class: "btn btn-outline", type: "button", onClick: () => { store.pushHistory(t("log_locator"), ""); sp.playLocatorBeep(); } }, t("emergency_locator"))
    ),
    el("div", { class: "btn-row" },
      el("button", { class: "btn btn-ghost", type: "button", onClick: () => location.hash = "#/emergency/contact" }, t("emergency_add_change"))
    ),
    backButton()
  );
}

export function viewEmergencyContact() {
  clear();
  setTitle(t("emergency_contact"));
  const m = $main();
  const cur = store.getEmergency();
  const name = el("input", { type: "text", value: cur.name || "", placeholder: t("f_name") });
  const phone = el("input", { type: "tel", value: cur.phone || "", placeholder: t("emergency_contact_example") });
  m.append(
    el("label", { class: "field" }, el("span", { class: "field-label" }, t("f_name")), name),
    el("label", { class: "field" }, el("span", { class: "field-label" }, t("emergency_contact")), phone),
    el("div", { class: "btn-row" },
      el("button", { class: "btn", type: "button", onClick: () => {
        store.setEmergency({ name: name.value.trim(), phone: phone.value.trim() });
        toast(t("saved"));
        history.back();
      }}, t("save"))
    ),
    backButton("#/emergency")
  );
}

function getPosition(opts) {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) return reject(new Error("no geolocation"));
    navigator.geolocation.getCurrentPosition(resolve, reject, {
      enableHighAccuracy: false, timeout: 8000, maximumAge: 60000, ...opts
    });
  });
}

// ---------- Memory ----------
export function viewMemory() {
  clear();
  setTitle(t("nav_memory"));
  const m = $main();
  const renderList = (title, items, kind) => {
    if (!items.length) return null;
    return el("div", null,
      el("h3", null, title),
      ...items.map(it => el("div", { class: "list-item" },
        el("div", { class: "meta" }, new Date(it.id).toLocaleString()),
        el("div", null, [it.name, it.relation, it.barcode, it.description, it.notes, it.accessNotes].filter(Boolean).join(" · ")),
        el("button", { class: "btn btn-ghost", type: "button", onClick: () => { store.removeMemory(kind, it.id); viewMemory(); } }, t("delete"))
      ))
    );
  };
  m.append(
    el("p", { class: "subtitle" }, t("memory_intro")),
    el("div", { class: "btn-row" },
      el("button", { class: "btn", type: "button", onClick: () => addPersonDialog() }, t("mem_add_person")),
      el("button", { class: "btn btn-outline", type: "button", onClick: () => addProductDialog() }, t("mem_add_product")),
      el("button", { class: "btn btn-outline", type: "button", onClick: () => addPlaceDialog() }, t("mem_add_place")),
    ),
    renderList(t("mem_add_person"), store.getPeople(), "people")    || el("p", null, t("mem_empty") + " (" + t("mem_add_person") + ")"),
    renderList(t("mem_add_product"), store.getProducts(), "products") || el("p", null, t("mem_empty") + " (" + t("mem_add_product") + ")"),
    renderList(t("mem_add_place"), store.getPlaces(), "places")     || el("p", null, t("mem_empty") + " (" + t("mem_add_place") + ")"),
    backButton()
  );
}

function threeFieldDialog(title, labels, onSave) {
  const dlg = document.getElementById("dialog");
  while (dlg.firstChild) dlg.removeChild(dlg.firstChild);
  const inputs = labels.map(lab => el("input", { type: "text", placeholder: lab, "aria-label": lab }));
  dlg.append(el("h2", null, title));
  for (let i = 0; i < inputs.length; i++) {
    dlg.append(el("label", { class: "field" }, el("span", { class: "field-label" }, labels[i]), inputs[i]));
  }
  dlg.append(el("div", { class: "actions" },
    el("button", { class: "btn btn-ghost", type: "button", onClick: () => dlg.close() }, t("cancel")),
    el("button", { class: "btn", type: "button", onClick: () => {
      onSave(inputs.map(i => i.value.trim()));
      dlg.close();
    }}, t("save"))
  ));
  if (typeof dlg.showModal === "function") dlg.showModal(); else dlg.setAttribute("open", "");
}

function addPersonDialog() {
  threeFieldDialog(t("mem_add_person"), [t("f_name"), t("f_relation"), t("f_notes")], ([name, relation, notes]) => {
    if (!name) return;
    store.addPerson({ name, relation, notes });
    toast(t("saved"));
    viewMemory();
  });
}
function addProductDialog() {
  threeFieldDialog(t("mem_add_product"), [t("f_name"), t("f_barcode"), t("f_notes")], ([name, barcode, notes]) => {
    if (!name) return;
    store.addProduct({ name, barcode, notes });
    toast(t("saved"));
    viewMemory();
  });
}
function addPlaceDialog() {
  threeFieldDialog(t("mem_add_place"), [t("f_name"), t("f_description"), t("f_access_notes")], ([name, description, accessNotes]) => {
    if (!name) return;
    store.addPlace({ name, description, accessNotes });
    toast(t("saved"));
    viewMemory();
  });
}

// ---------- Archive ----------
export function viewArchive() {
  clear();
  setTitle(t("nav_archive"));
  const m = $main();
  const items = store.getArchive();
  m.append(el("p", { class: "subtitle" }, t("nav_archive_desc")));
  if (!items.length) {
    m.append(el("p", null, t("archive_empty")));
  } else {
    for (const it of items) {
      const when = it.at || (typeof it.id === "string" ? parseInt(it.id.split("-")[0], 10) : it.id) || Date.now();
      m.append(el("div", { class: "list-item" },
        el("div", { class: "meta" }, new Date(when).toLocaleString() + " · " + (it.title || "")),
        el("div", { class: "result" }, it.content || ""),
        el("div", { class: "btn-row" },
          el("button", { class: "btn btn-ghost", type: "button", onClick: () => {
            navigator.clipboard.writeText(it.content || ""); toast(t("copied"));
          }}, t("copy_result")),
          el("button", { class: "btn btn-ghost", type: "button", onClick: () => {
            store.removeFromArchive(it.id); viewArchive();
          }}, t("delete"))
        )
      ));
    }
  }
  m.append(backButton());
}

// ---------- History ----------
export function viewHistory() {
  clear();
  setTitle(t("nav_history"));
  const m = $main();
  const items = store.getHistory();
  m.append(el("p", { class: "subtitle" }, t("history_intro")));
  if (!items.length) {
    m.append(el("p", null, t("history_empty")));
  } else {
    for (const it of items) {
      m.append(el("div", { class: "list-item" },
        el("div", { class: "meta" }, new Date(it.at).toLocaleString() + " · " + it.type),
        el("div", null, it.content)
      ));
    }
    m.append(el("div", { class: "btn-row" },
      el("button", { class: "btn btn-ghost", type: "button", onClick: () => {
        store.clearHistory(); toast(t("history_cleared")); viewHistory();
      }}, t("history_clear"))
    ));
  }
  m.append(backButton());
}

// ---------- Settings ----------
export function viewSettings() {
  clear();
  setTitle(t("nav_settings"));
  const m = $main();
  const s = store.getSettings();

  const langSel = el("select", null,
    el("option", { value: "ar" }, "العربية"),
    el("option", { value: "en" }, "English"));
  langSel.value = s.language;
  langSel.addEventListener("change", () => {
    store.setSettings({ language: langSel.value });
    setLang(langSel.value);
    viewSettings();
  });

  const ttsRate = el("input", { type: "range", min: "0.5", max: "1.6", step: "0.1", value: String(s.ttsRate) });
  ttsRate.addEventListener("change", () => store.setSettings({ ttsRate: parseFloat(ttsRate.value) }));

  const fontSize = el("input", { type: "range", min: "1", max: "1.3", step: "0.15", value: String(s.fontStep) });
  fontSize.addEventListener("change", () => {
    const v = parseFloat(fontSize.value);
    store.setSettings({ fontStep: v });
    document.documentElement.style.setProperty("--font-step", String(v));
  });

  const proxyUrl = el("input", { type: "url", value: s.proxyUrl || "", placeholder: location.origin });
  proxyUrl.addEventListener("change", () => store.setSettings({ proxyUrl: proxyUrl.value.trim() }));

  const appToken = el("input", { type: "password", value: s.appToken || "" });
  appToken.addEventListener("change", () => store.setSettings({ appToken: appToken.value.trim() }));

  const qualityRow = segmented(
    ["fast", "balanced", "best"],
    [t("quality_fast"), t("quality_balanced"), t("quality_best")],
    null, s.quality, (v) => store.setSettings({ quality: v })
  );

  const testStatus = el("div", { class: "info-label" });
  const onTest = async () => {
    testStatus.textContent = t("set_test_running");
    try { await api.testConnection(); testStatus.textContent = t("set_test_ok"); }
    catch (e) { testStatus.textContent = t("set_test_fail") + " — " + (e.message || e); }
  };

  m.append(
    el("p", { class: "subtitle" }, t("settings_subtitle")),

    el("h2", null, t("set_language")),
    el("label", { class: "field" }, el("span", { class: "field-label" }, t("set_current_lang")), langSel),

    el("h2", null, t("set_voice_section")),
    switchRow(t("set_tts"), s.tts, (v) => store.setSettings({ tts: v })),
    el("label", { class: "field" }, el("span", { class: "field-label" }, t("set_tts_rate")), ttsRate),

    el("h2", null, t("set_appearance")),
    el("label", { class: "field" }, el("span", { class: "field-label" }, t("set_font_size")), fontSize),
    el("p", null, t("set_dark_hint")),

    el("h2", null, t("set_privacy_section")),
    switchRow(t("set_privacy_mode"), s.privacy, (v) => store.setSettings({ privacy: v })),
    switchRow(t("set_autosave"), s.autoSave, (v) => store.setSettings({ autoSave: v })),

    el("h2", null, t("set_proxy_section")),
    el("p", null, t("set_proxy_url_hint")),
    el("label", { class: "field" }, el("span", { class: "field-label" }, t("set_proxy_url")), proxyUrl),
    el("label", { class: "field" }, el("span", { class: "field-label" }, t("set_app_token")), appToken),
    el("h3", null, t("set_quality")),
    qualityRow,
    el("div", { class: "btn-row" },
      el("button", { class: "btn", type: "button", onClick: onTest }, t("set_test"))
    ),
    testStatus,

    el("h2", null, t("set_emergency_section")),
    el("div", { class: "btn-row" },
      el("button", { class: "btn btn-ghost", type: "button", onClick: () => location.hash = "#/emergency/contact" }, t("emergency_add_change"))
    ),

    el("h2", null, t("set_bot_section")),
    el("h3", null, t("set_like_mode")),
    segmented(
      ["normal", "divorced_widowed"],
      [t("bot_mode_normal"), t("bot_mode_divorced_widowed")],
      [t("bot_mode_normal_sub"), t("bot_mode_divorced_widowed_sub")],
      s.likeMode,
      (v) => store.setSettings({ likeMode: v })
    ),
    el("h3", null, t("set_after_refresh")),
    segmented(
      ["continue", "restart"],
      [t("bot_refresh_continue"), t("bot_refresh_restart")],
      [t("bot_refresh_continue_sub"), t("bot_refresh_restart_sub")],
      s.afterRefresh,
      (v) => store.setSettings({ afterRefresh: v })
    ),
    el("h3", null, t("set_bot_nav")),
    segmented(
      ["online", "search"],
      [t("bot_nav_online"), t("bot_nav_search")],
      [t("bot_nav_online_sub"), t("bot_nav_search_sub")],
      s.botNavMode,
      (v) => store.setSettings({ botNavMode: v })
    ),
    el("div", { class: "btn-row" },
      el("button", { class: "btn btn-ghost", type: "button", onClick: () => location.hash = "#/bot" }, t("nav_bot"))
    ),

    el("h2", null, t("set_data_section")),
    el("div", { class: "btn-row" },
      el("button", { class: "btn btn-danger", type: "button", onClick: () => {
        showDialog(t("set_delete_confirm_title"), t("set_delete_confirm_body"), [
          { label: t("cancel"), style: "ghost" },
          { label: t("delete"), style: "danger", onClick: () => { store.deleteAllUserData(); toast(t("saved")); viewSettings(); } }
        ]);
      }}, t("set_delete_data"))
    ),

    backButton()
  );
}

function switchRow(label, value, onChange) {
  const checkbox = el("input", { type: "checkbox", checked: value, "aria-label": label });
  checkbox.addEventListener("change", () => onChange(checkbox.checked));
  return el("label", { class: "switch" },
    el("span", { class: "switch-label" }, label),
    el("span", null, checkbox, el("span", { class: "knob", "aria-hidden": "true" }))
  );
}

function segmented(ids, labels, subs, selected, onChange) {
  const row = el("div", { class: "segmented", role: "radiogroup" });
  const buttons = ids.map((id, i) => {
    const b = el("button", {
      class: "segment" + (id === selected ? " selected" : ""),
      type: "button",
      role: "radio",
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
      subs && subs[i] ? el("span", { class: "segment-sub" }, subs[i]) : null
    );
    return b;
  });
  row.append(...buttons);
  return row;
}

// ---------- About ----------
export function viewAbout() {
  clear();
  setTitle(t("nav_about"));
  const m = $main();
  m.append(
    el("p", null, t("about_text")),
    info(t("about_contact"), t("about_email_addr")),
    info(t("s_version"), t("about_version")),
    el("div", { class: "btn-row" },
      el("a", { class: "btn", href: "mailto:" + t("about_email_addr") }, t("about_email")),
      el("button", { class: "btn btn-outline", type: "button", onClick: async () => {
        const data = { title: "بصير AI", text: t("about_text"), url: location.origin + location.pathname };
        if (navigator.share) { try { await navigator.share(data); return; } catch {} }
        try { await navigator.clipboard.writeText(data.url); toast("URL copied"); } catch {}
      }}, t("about_share"))
    ),
    el("p", { class: "callout" }, t("install_hint")),
    backButton()
  );
}

// ---------- Result helper ----------
function showResult(target, title, text, { logType, logContent } = {}) {
  target.replaceChildren();
  const resultEl = el("div", { class: "result", tabindex: "0" }, text || "");
  target.append(
    el("h3", null, title),
    el("div", { class: "info-label" }, t("analysis_done")),
    resultEl,
    el("div", { class: "btn-row" },
      el("button", { class: "btn btn-outline", type: "button", onClick: () => {
        if (sp.isSpeaking()) { sp.stopSpeak(); return; }
        sp.speak(text);
      }}, t("read_aloud")),
      el("button", { class: "btn btn-ghost", type: "button", onClick: () => {
        navigator.clipboard.writeText(text || ""); toast(t("copied"));
      }}, t("copy_result")),
      el("button", { class: "btn btn-ghost", type: "button", onClick: () => {
        store.addToArchive({ title, content: text || "", at: Date.now() });
        toast(t("archive_saved"));
      }}, t("archive_save"))
    )
  );
  if (logType) store.pushHistory(logType, logContent || "");
  const s = store.getSettings();
  if (s.autoSave) store.addToArchive({ title, content: text || "", at: Date.now() });
  if (s.tts) sp.speak(text);
}

function errorBox(err, customTitle) {
  const msg = (err && err.message) ? err.message : String(err);
  return el("div", { class: "callout callout-danger" },
    el("strong", null, customTitle || t("error_ai")),
    el("br"),
    el("span", null, msg));
}

// ---------- Mic button (dictation) ----------
function micButton(onText) {
  if (!sp.speechRecognitionAvailable()) return null;
  let r = null;
  const btn = el("button", { class: "btn btn-outline", type: "button", "aria-label": t("voice_dictation") },
    el("span", { class: "mic-icon", "aria-hidden": "true" }, "🎤"),
    el("span", null, t("voice_dictation")));
  btn.addEventListener("click", () => {
    if (r) { try { r.stop(); } catch {} r = null; btn.classList.remove("mic-active"); return; }
    r = sp.createDictation({
      onResult: ({ finalText, interim }) => {
        // No-op for interim; we set the final once on end.
      },
      onError: () => { btn.classList.remove("mic-active"); r = null; toast(t("voice_not_supported")); },
      onEnd: (finalText) => {
        btn.classList.remove("mic-active");
        r = null;
        if (finalText) onText(finalText);
      }
    });
    if (!r) { toast(t("voice_not_supported")); return; }
    try { r.start(); btn.classList.add("mic-active"); }
    catch { toast(t("voice_not_supported")); }
  });
  return btn;
}

// ---------- Voice command (quick nav) ----------
function startVoiceCommand() {
  if (!sp.speechRecognitionAvailable()) return toast(t("voice_not_supported"));
  toast(t("voice_listening"));
  const r = sp.createDictation({
    onEnd: (text) => {
      const phrase = (text || "").toLowerCase();
      if (!phrase) return;
      const map = [
        { kws: ["اسأل", "سؤال", "ask", "question"], to: "#/ask" },
        { kws: ["وصف", "describe", "image", "صورة"], to: "#/describe" },
        { kws: ["مستند", "doc", "documents", "pdf"], to: "#/documents" },
        { kws: ["ترجم", "translate"], to: "#/translate" },
        { kws: ["طوارئ", "emergency", "help"], to: "#/emergency" },
        { kws: ["إعدادات", "settings"], to: "#/settings" },
        { kws: ["محفوظ", "memory"], to: "#/memory" },
        { kws: ["أرشيف", "archive"], to: "#/archive" },
        { kws: ["سجل", "history"], to: "#/history" },
        { kws: ["تحويل", "convert", "word"], to: "#/convert" },
      ];
      for (const m of map) if (m.kws.some(k => phrase.includes(k))) { location.hash = m.to; return; }
      location.hash = "#/ask";
      setTimeout(() => {
        const inp = document.getElementById("askInput");
        if (inp) inp.value = text;
      }, 50);
    },
    onError: () => toast(t("voice_not_supported"))
  });
  if (r) try { r.start(); } catch { toast(t("voice_not_supported")); }
}

// ============================================================
// v2.0 — Walking mode
//
// iOS Safari does not allow programmatic file-input clicks outside a user
// gesture, so the Android "auto-relaunch" toggle is removed for the web
// build — the user re-taps the big capture button after each description.
// ============================================================
let walkingBusy = false;
let walkingLast = "";

export function viewWalking() {
  clear();
  setTitle(t("walking_title"));
  const m = $main();

  const fileInput = el("input", { type: "file", accept: "image/*", capture: "environment", class: "sr-only" });
  fileInput.addEventListener("change", async (e) => {
    const f = e.target.files[0];
    if (!f) return;
    await runWalkingFrame(f);
    fileInput.value = "";
  });

  const captureBtn = el("button", {
    class: "btn btn-block walking-btn", type: "button",
    onClick: () => fileInput.click(),
    disabled: walkingBusy
  }, walkingBusy ? t("walking_processing") : t("walking_capture"));

  const status = el("div", { "aria-live": "polite" });

  async function runWalkingFrame(file) {
    walkingBusy = true;
    captureBtn.disabled = true;
    captureBtn.textContent = t("walking_processing");
    status.replaceChildren(loading(t("walking_processing")));
    try {
      const blob = await api.compressImage(file);
      const base64 = await api.fileToBase64(blob);
      const ans = await api.askBasir({
        task: "walking_scene",
        input: "Describe what's ahead of the blind user in this image.",
        instruction: "You are Basir helping a blind user walk safely. Describe the scene in 1-2 short sentences. LEAD with anything immediately important (obstacle, person, vehicle, stairs, door, road crossing). Then mention general surroundings if space allows. No markdown, no lists — read aloud by TTS.",
        imageBase64: base64,
        mimeType: blob.type || "image/jpeg"
      });
      walkingLast = (ans || "").trim();
      store.pushHistory(t("log_scene"), walkingLast.slice(0, 200));
      status.replaceChildren(
        el("div", { class: "result", tabindex: "0" }, walkingLast)
      );
      sp.speak(walkingLast);
    } catch (e) {
      status.replaceChildren(errorBox(e, t("walking_error")));
    } finally {
      walkingBusy = false;
      captureBtn.disabled = false;
      captureBtn.textContent = t("walking_capture");
    }
  }

  m.append(
    el("p", { class: "subtitle" }, t("walking_intro")),
    walkingLast ? el("div", { class: "info" },
      el("div", { class: "info-label" }, t("walking_last")),
      el("div", { class: "info-body" }, walkingLast)
    ) : null,
    captureBtn,
    fileInput,
    status,
    backButton("#/home")
  );
}

// ============================================================
// v2.0 — Continuous voice conversation
// ============================================================
let convoActive = false;
let convoHistory = []; // [{ q, a }]
let convoRecognizer = null;

function setConvoStatus(node, text) {
  if (node) node.textContent = text;
}

export function viewVoiceConvo() {
  clear();
  setTitle(t("convo_title"));
  const m = $main();

  if (!sp.speechRecognitionAvailable()) {
    m.append(
      el("p", { class: "subtitle" }, t("convo_intro")),
      el("div", { class: "callout callout-danger" }, t("convo_no_recognition")),
      backButton("#/home")
    );
    return;
  }

  const status = el("div", { class: "info-body", role: "status", "aria-live": "polite" }, t("convo_tap_start"));
  const transcript = el("div", { "aria-live": "polite" });

  function refreshTranscript() {
    transcript.replaceChildren();
    if (!convoHistory.length) return;
    const last = convoHistory[convoHistory.length - 1];
    transcript.append(
      el("div", { class: "info" },
        el("div", { class: "info-label" }, t("convo_prev_q")),
        el("div", null, last.q)
      ),
      el("div", { class: "info" },
        el("div", { class: "info-label" }, t("convo_prev_a")),
        el("div", null, last.a)
      )
    );
  }
  refreshTranscript();

  function stopRecognizer() {
    if (convoRecognizer) {
      try { convoRecognizer.stop(); } catch {}
      convoRecognizer = null;
    }
  }

  function startListenStep() {
    if (!convoActive) return;
    setConvoStatus(status, t("convo_listening"));
    convoRecognizer = sp.createDictation({
      onEnd: (text) => {
        convoRecognizer = null;
        if (!convoActive) return;
        if (!text || !text.trim()) {
          sp.speak(t("convo_no_speech"), { onend: () => startListenStep() });
          return;
        }
        handleConvoTurn(text.trim());
      },
      onError: () => {
        convoRecognizer = null;
        if (convoActive) sp.speak(t("convo_no_speech"), { onend: () => startListenStep() });
      }
    });
    if (!convoRecognizer) {
      setConvoStatus(status, t("convo_no_recognition"));
      convoActive = false;
      return;
    }
    try { convoRecognizer.start(); }
    catch {
      convoRecognizer = null;
      setConvoStatus(status, t("convo_no_recognition"));
      convoActive = false;
    }
  }

  async function handleConvoTurn(question) {
    setConvoStatus(status, t("convo_thinking"));
    try {
      const recent = convoHistory.slice(-4)
        .map(turn => `User: ${turn.q}\nAssistant: ${turn.a}`)
        .join("\n");
      const fullPrompt = (recent ? recent + "\n" : "") + "User: " + question;
      const instruction = getLang() === "en"
        ? "You are Basir, an assistant for blind and low-vision users having a spoken conversation. Answer in 1-3 short sentences of plain English, no markdown, no lists — this is read aloud by TTS."
        : "أنت بصير، مساعد للمستخدمين المكفوفين في محادثة صوتية مستمرة. أجب في جملة أو ثلاث جمل قصيرة بالعربية الفصيحة، بدون قوائم أو رموز Markdown، لأن الإجابة تُقرأ صوتيًا.";
      const answer = await api.askBasir({ task: "ask", input: fullPrompt, instruction });
      const a = (answer || "").trim();
      convoHistory.push({ q: question, a });
      if (convoHistory.length > 10) convoHistory.shift();
      store.pushHistory(t("log_question"), question.slice(0, 200));
      refreshTranscript();
      setConvoStatus(status, t("convo_prev_a") + a);
      sp.speak(a, { onend: () => { if (convoActive) startListenStep(); } });
    } catch (e) {
      setConvoStatus(status, t("docqa_failed"));
      const msg = (e && e.message) ? e.message : String(e);
      sp.speak(msg, { onend: () => { if (convoActive) startListenStep(); } });
    }
  }

  const toggleBtn = el("button", {
    class: "btn btn-block",
    type: "button"
  }, convoActive ? t("convo_end") : t("convo_start"));
  toggleBtn.addEventListener("click", () => {
    if (convoActive) {
      convoActive = false;
      stopRecognizer();
      sp.stopSpeak();
      setConvoStatus(status, t("convo_ended"));
      sp.speak(t("convo_ended"));
      toggleBtn.textContent = t("convo_start");
    } else {
      convoActive = true;
      toggleBtn.textContent = t("convo_end");
      setConvoStatus(status, t("convo_listening"));
      sp.speak(t("convo_speak_now"), { onend: () => setTimeout(startListenStep, 350) });
    }
  });

  m.append(
    el("p", { class: "subtitle" }, t("convo_intro")),
    el("div", { class: "info" }, status),
    transcript,
    toggleBtn,
    convoHistory.length
      ? el("button", { class: "btn btn-ghost btn-block", type: "button", onClick: () => {
          convoHistory = [];
          refreshTranscript();
          toast(t("history_cleared"));
        }}, t("convo_clear"))
      : null,
    backButton("#/home")
  );

  // Stop everything when leaving via hashchange — viewHome will rebuild.
  window.addEventListener("hashchange", function onLeave() {
    convoActive = false;
    stopRecognizer();
    sp.stopSpeak();
    window.removeEventListener("hashchange", onLeave);
  }, { once: true });
}

// ============================================================
// v2.0 — Document Q&A (after a PDF/PPTX was uploaded to Files API)
// ============================================================
let lastDocQa = { q: "", a: "" };

export function viewDocQa() {
  clear();
  setTitle(t("docqa_title"));
  const m = $main();
  const doc = store.getQaDoc();

  if (!doc || !store.isQaDocFresh()) {
    m.append(
      el("p", { class: "subtitle" }, t("docqa_no_doc")),
      el("div", { class: "btn-row" },
        el("button", { class: "btn", type: "button", onClick: () => location.hash = "#/convert" }, t("convert_title"))
      ),
      backButton("#/home")
    );
    return;
  }

  const input = el("textarea", {
    placeholder: t("docqa_placeholder"),
    "aria-label": t("docqa_placeholder")
  });
  const resultBox = el("div", { "aria-live": "polite" });
  const mic = micButton((text) => { input.value = (input.value ? input.value + " " : "") + text; input.focus(); });

  if (lastDocQa.q || lastDocQa.a) {
    if (lastDocQa.q) m.append(el("div", { class: "info" },
      el("div", { class: "info-label" }, t("convo_prev_q")),
      el("div", null, lastDocQa.q)));
    if (lastDocQa.a) m.append(el("div", { class: "info" },
      el("div", { class: "info-label" }, t("convo_prev_a")),
      el("div", null, lastDocQa.a)));
  }

  const onSend = async () => {
    const q = (input.value || "").trim();
    if (!q) return toast(t("ask_first"));
    lastDocQa = { q, a: "" };
    sp.vibrate(30);
    resultBox.replaceChildren(loading(t("docqa_searching")));
    try {
      const ans = await api.qaAboutFile({
        fileUri: doc.fileUri,
        mimeType: doc.mimeType,
        question: q,
        language: getLang()
      });
      lastDocQa.a = (ans || "").trim();
      store.pushHistory(t("log_document"), q.slice(0, 200));
      showResult(resultBox, t("docqa_title"), lastDocQa.a, {
        logType: t("log_document"),
        logContent: q.slice(0, 200)
      });
    } catch (e) {
      resultBox.replaceChildren(errorBox(e, t("docqa_failed")));
    }
  };

  m.append(
    el("p", { class: "subtitle" }, t("docqa_intro") + (doc.displayName || "")),
    el("label", { class: "field" },
      el("span", { class: "field-label" }, t("docqa_placeholder")),
      input
    ),
    el("div", { class: "btn-row" },
      el("button", { class: "btn btn-block", type: "button", onClick: onSend }, t("docqa_send")),
      mic ? mic : null
    ),
    resultBox,
    el("div", { class: "btn-row" },
      el("button", { class: "btn btn-ghost", type: "button", onClick: () => {
        lastDocQa = { q: "", a: "" };
        viewDocQa();
      }}, t("convo_clear")),
      el("button", { class: "btn btn-ghost", type: "button", onClick: () => {
        store.clearQaDoc();
        location.hash = "#/home";
      }}, t("delete"))
    ),
    backButton("#/home")
  );
}

// ============================================================
// Bot — Smart automation assistant
// ============================================================

function checkMaritalStatusLocally(text) {
  const clean = (text || "").replace(/\s+/g, " ");
  if (/مطلق[ةه]/.test(clean)) return "yes";
  if (/أرمل[ةه]/.test(clean)) return "yes";
  if (/divorced|widowed/i.test(clean)) return "yes";
  if (/متزوج[ةه]?|عزباء?|أعزب/.test(clean)) return "no";
  return "unknown";
}

// ---- Coordinate recording overlay ----
function startCoordRecording(onCapture) {
  const overlay = document.createElement("div");
  overlay.style.cssText = [
    "position:fixed", "inset:0", "background:rgba(0,0,0,0.55)",
    "z-index:9999", "display:flex", "flex-direction:column",
    "align-items:center", "justify-content:center",
    "cursor:crosshair", "touch-action:none"
  ].join(";");
  overlay.setAttribute("role", "button");
  overlay.setAttribute("tabindex", "0");

  const hint = document.createElement("div");
  hint.style.cssText = "color:#fff;font-size:1.3em;text-align:center;padding:24px;pointer-events:none;";
  hint.textContent = t("bot_coord_recording");
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

export function viewBot() {
  clear();
  setTitle(t("bot_title"));
  const m = $main();
  const s = store.getSettings();
  const state = store.getBotState();

  // ---- session status ----
  const lastMemberDisplay = state.lastMemberName || t("bot_none");
  const processedDisplay = state.processedCount ? String(state.processedCount) : "0";
  const afterRefresh = s.afterRefresh || "continue";
  const afterRefreshNote = afterRefresh === "continue"
    ? `${t("bot_refresh_continue")}: ${lastMemberDisplay}`
    : t("bot_refresh_restart");

  // ---- save position ----
  const posInput = el("input", {
    type: "text",
    placeholder: t("bot_pos_label"),
    "aria-label": t("bot_pos_label"),
    value: state.lastMemberName || ""
  });
  const micPos = micButton((text) => { posInput.value = text; posInput.focus(); });
  const savePosbtn = el("button", { class: "btn", type: "button",
    onClick: () => {
      const name = (posInput.value || "").trim();
      if (!name) return;
      const cur = store.getBotState();
      store.setBotState({ lastMemberName: name, processedCount: (cur.processedCount || 0) + 1 });
      toast(t("bot_pos_saved"));
      sp.vibrate(30);
    }
  }, t("bot_save_pos"));

  const clearBtn = el("button", { class: "btn btn-ghost", type: "button",
    onClick: () => {
      store.clearBotState();
      posInput.value = "";
      toast(t("bot_cleared"));
      viewBot();
    }
  }, t("bot_clear"));

  // ---- marital status check ----
  const checkResultBox = el("div", { "aria-live": "polite" });
  const checkInput = el("textarea", {
    placeholder: t("bot_profile_check_sub"),
    "aria-label": t("bot_profile_check_sub")
  });
  const micCheck = micButton((text) => { checkInput.value = (checkInput.value ? checkInput.value + " " : "") + text; checkInput.focus(); });

  const onCheck = async () => {
    const text = (checkInput.value || "").trim();
    if (!text) return toast(t("ask_first"));
    sp.vibrate(20);
    const local = checkMaritalStatusLocally(text);
    if (local === "yes") {
      checkResultBox.replaceChildren(el("div", { class: "callout" }, t("bot_profile_check_yes")));
      sp.speak(t("bot_profile_check_yes"));
      return;
    }
    if (local === "no") {
      checkResultBox.replaceChildren(el("div", { class: "callout callout-danger" }, t("bot_profile_check_no")));
      sp.speak(t("bot_profile_check_no"));
      return;
    }
    checkResultBox.replaceChildren(loading(t("loading")));
    try {
      const ans = await api.askBasir({
        task: "ask", input: text,
        instruction: "Quick marital-status classifier. Does the text indicate 'مطلقة' (divorced) or 'أرملة' (widowed)? Reply exactly: YES or NO."
      });
      const isYes = /^yes/i.test((ans || "").trim()) || /نعم/.test(ans);
      checkResultBox.replaceChildren(el("div", { class: isYes ? "callout" : "callout callout-danger" },
        isYes ? t("bot_profile_check_yes") : t("bot_profile_check_no")));
      sp.speak(isYes ? t("bot_profile_check_yes") : t("bot_profile_check_no"));
    } catch (e) {
      checkResultBox.replaceChildren(errorBox(e, t("bot_profile_check")));
    }
  };

  // ---- coordinate recording ----
  const coordsListEl = el("div", { "aria-live": "polite" });

  function renderCoordsList() {
    coordsListEl.replaceChildren();
    const coords = store.getBotCoords();
    if (!coords.length) {
      coordsListEl.append(el("p", { class: "info-label" }, t("bot_coord_empty")));
      return;
    }
    for (const c of coords) {
      coordsListEl.append(
        el("div", { class: "list-item" },
          el("div", { class: "meta" }, c.name || "—"),
          el("div", null,
            el("span", { class: "info-label" }, `${t("bot_coord_x")}: ${c.x}  ${t("bot_coord_y")}: ${c.y}`)
          ),
          el("button", { class: "btn btn-ghost", type: "button",
            onClick: () => { store.removeBotCoord(c.id); renderCoordsList(); }
          }, t("delete"))
        )
      );
    }
  }
  renderCoordsList();

  // Pending-capture state (name input shown after tap)
  const pendingBox = el("div");

  function showNameDialog(x, y) {
    pendingBox.replaceChildren();
    const nameInput = el("input", {
      type: "text",
      placeholder: t("bot_coord_name_hint"),
      "aria-label": t("bot_coord_name_label")
    });
    const micName = micButton((text) => { nameInput.value = text; nameInput.focus(); });
    const confirmBtn = el("button", { class: "btn", type: "button",
      onClick: () => {
        const name = (nameInput.value || "").trim() || `${x},${y}`;
        store.addBotCoord({ name, x, y });
        toast(t("bot_coord_saved"));
        sp.vibrate(30);
        pendingBox.replaceChildren();
        renderCoordsList();
      }
    }, t("save"));
    const cancelBtn = el("button", { class: "btn btn-ghost", type: "button",
      onClick: () => pendingBox.replaceChildren()
    }, t("cancel"));

    pendingBox.append(
      el("div", { class: "callout" },
        el("p", null, `${t("bot_coord_x")}: ${x}   ${t("bot_coord_y")}: ${y}`)
      ),
      el("label", { class: "field" },
        el("span", { class: "field-label" }, t("bot_coord_name_label")),
        nameInput
      ),
      el("div", { class: "btn-row" },
        confirmBtn,
        micName ? micName : null,
        cancelBtn
      )
    );
    nameInput.focus();
  }

  const recordBtn = el("button", { class: "btn btn-block", type: "button",
    onClick: () => {
      startCoordRecording(({ x, y }) => showNameDialog(x, y));
    }
  }, "📍 " + t("bot_coord_record"));

  // ---- navigation guide ----
  const navMode = s.botNavMode || "online";
  const likeMode = s.likeMode || "normal";
  const guideNav = navMode === "search" ? t("bot_guide_search") : t("bot_guide_online");
  const guideLike = likeMode === "divorced_widowed" ? t("bot_guide_like_dw") : t("bot_guide_like_normal");

  m.append(
    el("p", { class: "subtitle" }, t("bot_subtitle")),

    sectionHeader(t("bot_status_title")),
    info(t("bot_last_member"), lastMemberDisplay),
    info(t("bot_processed"), processedDisplay),
    info(t("set_after_refresh"), afterRefreshNote),

    sectionHeader(t("bot_save_pos")),
    el("label", { class: "field" },
      el("span", { class: "field-label" }, t("bot_pos_label")),
      posInput
    ),
    el("div", { class: "btn-row" },
      savePosbtn,
      micPos ? micPos : null,
      clearBtn
    ),

    s.likeMode === "divorced_widowed"
      ? el("div", null,
          sectionHeader(t("bot_profile_check")),
          el("p", { class: "subtitle" }, t("bot_profile_already")),
          el("label", { class: "field" },
            el("span", { class: "field-label" }, t("bot_profile_check")),
            checkInput
          ),
          el("div", { class: "btn-row" },
            el("button", { class: "btn btn-block", type: "button", onClick: onCheck }, t("bot_profile_check_run")),
            micCheck ? micCheck : null
          ),
          checkResultBox
        )
      : null,

    sectionHeader(t("bot_coords_section")),
    recordBtn,
    pendingBox,
    coordsListEl,
    store.getBotCoords().length
      ? el("div", { class: "btn-row" },
          el("button", { class: "btn btn-ghost", type: "button",
            onClick: () => { store.clearBotCoords(); renderCoordsList(); toast(t("bot_cleared")); }
          }, t("bot_coords_clear_all"))
        )
      : null,

    sectionHeader(t("bot_guide_title")),
    el("div", { class: "callout" },
      el("div", null, "🗺 " + guideNav),
      el("div", null, "❤️ " + guideLike)
    ),

    backButton()
  );
}

export const Views = {
  home: viewHome,
  more: viewMore,
  status: viewStatus,
  ask: viewAsk,
  describe: viewDescribe,
  documents: viewDocuments,
  translate: viewTranslate,
  advanced: viewAdvanced,
  convert: viewConvert,
  emergency: viewEmergency,
  "emergency/contact": viewEmergencyContact,
  memory: viewMemory,
  archive: viewArchive,
  history: viewHistory,
  settings: viewSettings,
  about: viewAbout,
  walking: viewWalking,
  "voice-convo": viewVoiceConvo,
  "doc-qa": viewDocQa,
  bot: viewBot
};

/* Basir AI — server API client.
 * Talks to the same proxy as the Android app: /api/basir, /api/convert. */

import { getSettings } from "./storage.js";

function baseUrl() {
  const s = getSettings();
  return (s.proxyUrl || "").replace(/\/+$/, "") || "";
}

function authHeaders() {
  const s = getSettings();
  const h = { "Content-Type": "application/json" };
  if (s.appToken) h["X-Basir-Client-Token"] = s.appToken;
  return h;
}

function multipartHeaders() {
  const s = getSettings();
  const h = {};
  if (s.appToken) h["X-Basir-Client-Token"] = s.appToken;
  return h;
}

export async function askBasir({ task = "ask", input, instruction, language, imageBase64, mimeType, signal }) {
  const s = getSettings();
  const body = {
    task,
    input: input || "",
    instruction: instruction || "",
    language: language || s.language || "ar",
    quality: s.quality || "balanced"
  };
  if (imageBase64) {
    body.image_base64 = imageBase64;
    body.mime_type = mimeType || "image/jpeg";
  }
  const res = await fetch(baseUrl() + "/api/basir", {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify(body),
    signal
  });
  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try { const j = await res.json(); if (j && j.error) msg = j.error; } catch {}
    throw new Error(msg);
  }
  const json = await res.json();
  return json.answer || "";
}

export async function convertFile({ file, language, mode, quality, signal, onProgress }) {
  const fd = new FormData();
  fd.append("file", file, file.name);
  fd.append("language", language || "ar");
  fd.append("mode", mode || "full");
  if (quality) fd.append("quality", quality);

  // Plain fetch — no upload progress event in fetch. Use XHR only if onProgress is wanted.
  if (onProgress && typeof XMLHttpRequest !== "undefined") {
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open("POST", baseUrl() + "/api/convert");
      Object.entries(multipartHeaders()).forEach(([k, v]) => xhr.setRequestHeader(k, v));
      xhr.responseType = "blob";
      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable && onProgress) onProgress("upload", e.loaded / e.total);
      };
      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve(xhr.response);
        } else {
          let msg = `HTTP ${xhr.status}`;
          try {
            const reader = new FileReader();
            reader.onload = () => {
              try { msg = JSON.parse(reader.result).error || msg; } catch {}
              reject(new Error(msg));
            };
            reader.readAsText(xhr.response);
            return;
          } catch {}
          reject(new Error(msg));
        }
      };
      xhr.onerror = () => reject(new Error("network"));
      xhr.onabort = () => reject(new Error("aborted"));
      if (signal) signal.addEventListener("abort", () => xhr.abort());
      xhr.send(fd);
    });
  }

  const res = await fetch(baseUrl() + "/api/convert", {
    method: "POST",
    headers: multipartHeaders(),
    body: fd,
    signal
  });
  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try { const j = await res.json(); if (j && j.error) msg = j.error; } catch {}
    throw new Error(msg);
  }
  return await res.blob();
}

export async function testConnection() {
  const url = baseUrl() + "/health";
  const res = await fetch(url, { method: "GET" });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const j = await res.json();
  if (!j || !j.ok) throw new Error("server did not return ok");
  return true;
}

// v2.0 — upload a file to the Gemini Files API via the proxy.
// Returns { fileUri, mimeType, displayName } that can later be passed to qaAboutFile.
export async function uploadForQa({ file, signal }) {
  const fd = new FormData();
  fd.append("file", file, file.name);
  const res = await fetch(baseUrl() + "/api/upload", {
    method: "POST",
    headers: multipartHeaders(),
    body: fd,
    signal
  });
  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try { const j = await res.json(); if (j && j.error) msg = j.error; } catch {}
    throw new Error(msg);
  }
  return await res.json();
}

// v2.0 — ask a follow-up question about an uploaded file.
export async function qaAboutFile({ fileUri, mimeType, question, language, signal }) {
  const s = getSettings();
  const res = await fetch(baseUrl() + "/api/qa", {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify({
      fileUri,
      mimeType: mimeType || "application/pdf",
      question,
      language: language || s.language || "ar",
      quality: s.quality || "best"
    }),
    signal
  });
  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try { const j = await res.json(); if (j && j.error) msg = j.error; } catch {}
    throw new Error(msg);
  }
  const j = await res.json();
  return j.answer || "";
}

// Read a File as base64 (without the data: prefix).
export function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const s = String(reader.result || "");
      const i = s.indexOf(",");
      resolve(i >= 0 ? s.slice(i + 1) : s);
    };
    reader.onerror = () => reject(reader.error || new Error("read failed"));
    reader.readAsDataURL(file);
  });
}

// Compress an image to JPEG <= ~1024px to stay under the 20mb JSON limit.
export async function compressImage(file, maxDim = 1600, quality = 0.85) {
  if (!file.type.startsWith("image/")) return file;
  if (typeof createImageBitmap !== "function") return file;
  let bitmap;
  try { bitmap = await createImageBitmap(file); } catch { return file; }
  const ratio = Math.min(1, maxDim / Math.max(bitmap.width, bitmap.height));
  const w = Math.round(bitmap.width * ratio);
  const h = Math.round(bitmap.height * ratio);
  const canvas = document.createElement("canvas");
  canvas.width = w; canvas.height = h;
  const ctx = canvas.getContext("2d");
  ctx.drawImage(bitmap, 0, 0, w, h);
  return await new Promise(r => canvas.toBlob(b => r(b || file), "image/jpeg", quality));
}

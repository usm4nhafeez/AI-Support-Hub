/* AI Support Hub — dashboard SPA (ES module; three.js scene is optional) */

const API = ""; // same origin — served by Spring Boot

/* ------------------------------------------------------------------ state */
const state = {
  token: sessionStorage.getItem("ash.jwt") || null,
  agentUsername: sessionStorage.getItem("ash.agentUser") || null,
  conversationId: null,
  conversations: [],
  clients: [],
  clientsLoaded: false,
  team: null,          // active agents of MY client (feeds the assign dropdown)
  teamLoaded: false,
  agents: [],          // roster shown in the Clients view (whatever project is picked)
};
let pendingCreds = null; // keys generated for the not-yet-registered client

const $ = (id) => document.getElementById(id);

/* ------------------------------------------------------------------ theme
   Three curated palettes. Values stay in localStorage under ash.theme, and the
   WebGL hub recolours itself from the tokens the new theme defines. */
const THEME_KEY = "ash.theme";
const THEMES = ["dark", "light", "blueprint"];
const THEME_NAMES = { dark: "Console", light: "Daylight", blueprint: "Blueprint" };

const hub = { full: null, mini: null };

function themeTokens() {
  const s = getComputedStyle(document.documentElement);
  return { bg: s.getPropertyValue("--bg").trim(), accent: s.getPropertyValue("--accent").trim() };
}

function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
  document.querySelectorAll("[data-set-theme]").forEach((b) => {
    b.setAttribute("aria-checked", String(b.dataset.setTheme === theme));
  });
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute("content", themeTokens().bg || "#0E1218");
  Object.values(hub).forEach((scene) => scene && scene.setTheme());
}

function setTheme(theme) {
  if (!THEMES.includes(theme)) return;
  localStorage.setItem(THEME_KEY, theme);
  applyTheme(theme);
  toast(`${THEME_NAMES[theme]} theme applied.`);
}

function initialTheme() {
  const saved = localStorage.getItem(THEME_KEY);
  if (THEMES.includes(saved)) return saved;
  return window.matchMedia && matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
}

document.querySelectorAll("[data-set-theme]").forEach((btn) => {
  btn.addEventListener("click", () => setTheme(btn.dataset.setTheme));
  // Arrow keys move through the group, like a real radiogroup.
  btn.addEventListener("keydown", (e) => {
    if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
    e.preventDefault();
    const i = THEMES.indexOf(btn.dataset.setTheme);
    const next = THEMES[(i + (e.key === "ArrowRight" ? 1 : THEMES.length - 1)) % THEMES.length];
    setTheme(next);
    document.querySelector(`[data-set-theme="${next}"]:focus, [data-set-theme="${next}"]`)?.focus();
  });
});

/* ---------------------------------------------------------------- helpers */
function esc(s) {
  const d = document.createElement("div");
  d.textContent = s == null ? "" : String(s);
  return d.innerHTML;
}

function toast(msg, kind = "info") {
  const t = $("toast");
  t.textContent = msg;
  t.className = `toast ${kind}`;
  clearTimeout(toast._timer);
  toast._timer = setTimeout(() => t.classList.add("hidden"), 3500);
}

async function parseError(res) {
  // Spring's ApiError body: { timestamp, status, error, code, message, path }
  try {
    const body = await res.json();
    if (body && body.message) return `${body.message}`;
  } catch { /* non-JSON error body */ }
  if (res.status === 401) return "Not authorised — check the credentials.";
  if (res.status === 403) return "Forbidden — wrong role or missing credentials.";
  return `Request failed (HTTP ${res.status}).`;
}

async function copyText(text, btn) {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    const ta = document.createElement("textarea");
    ta.value = text;
    ta.style.position = "fixed";
    ta.style.opacity = "0";
    document.body.appendChild(ta);
    ta.select();
    document.execCommand("copy");
    ta.remove();
  }
  if (btn) {
    const old = btn.textContent;
    btn.textContent = "Copied";
    btn.classList.add("copied");
    setTimeout(() => { btn.textContent = old; btn.classList.remove("copied"); }, 1500);
  }
}

/* Agent endpoints: JWT Bearer, never send client headers */
async function agentFetch(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (state.token) headers["Authorization"] = `Bearer ${state.token}`;
  if (options.body && !(options.body instanceof FormData)) headers["Content-Type"] = "application/json";
  const res = await fetch(`${API}${path}`, { ...options, headers });
  if (res.status === 401 || res.status === 403) {
    logout(true);
    throw new Error("Session expired — please sign in again.");
  }
  if (!res.ok) throw new Error(await parseError(res));
  if (res.status === 204) return null;
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

/* Client endpoints: X-Client-Key + X-API-Key, must NOT send Authorization
   (ApiKeyFilter ignores requests that carry one). */
function clientHeaders() {
  const clientKey = $("client-key").value.trim();
  const apiKey = $("api-key").value.trim();
  if (!clientKey || !apiKey) {
    throw new Error("Enter the client key and API key in the Client credentials panel first — or register a client and use “Use in Chat Playground”.");
  }
  sessionStorage.setItem("ash.clientKey", clientKey);
  sessionStorage.setItem("ash.apiKey", apiKey);
  return { "X-Client-Key": clientKey, "X-API-Key": apiKey };
}

async function clientFetch(path, options = {}) {
  const headers = { ...(options.headers || {}), ...clientHeaders() };
  const res = await fetch(`${API}${path}`, { ...options, headers });
  if (!res.ok) throw new Error(await parseError(res));
  return res.json();
}

function fmtDate(v) {
  if (!v) return "—";
  const d = new Date(typeof v === "string" && !v.endsWith("Z") ? v + "Z" : v);
  return isNaN(d) ? String(v) : d.toLocaleString();
}

const STATUS_BADGE = {
  OPEN: "badge-blue", AI_HANDLING: "badge-indigo",
  WAITING_FOR_CUSTOMER: "badge-amber", WAITING_FOR_AGENT: "badge-amber",
  ASSIGNED: "badge-blue", RESOLVED: "badge-green", CLOSED: "badge-gray",
};
const PRIORITY_BADGE = {
  LOW: "badge-gray", MEDIUM: "badge-blue", HIGH: "badge-amber", URGENT: "badge-red",
};
const badge = (text, cls) => `<span class="badge ${cls || "badge-gray"}">${esc(text)}</span>`;

applyTheme(initialTheme());

/* ------------------------------------------------------------------ auth */
/* Only the scene you can see animates: the login constellation stops the moment
   you sign in, and the sidebar core takes over. */
function setScenes(loggedIn) {
  if (hub.full) hub.full.setEnabled(!loggedIn);
  if (hub.mini) hub.mini.setEnabled(loggedIn);
}

function renderIdentity() {
  const name = $("user-name"), role = $("user-role");
  if (!name) return;
  name.textContent = state.agentUsername ? `@${state.agentUsername}` : "Signed out";
  const me = (state.team || []).find((a) => a.username === state.agentUsername);
  role.textContent = me ? me.role.toLowerCase()
    : (state.agentUsername ? "agent" : "sign in to route tickets");
}

function showLogin() {
  $("login-screen").classList.remove("hidden");
  $("app-shell").classList.add("hidden");
  setScenes(false);
  renderIdentity();
}

async function showApp() {
  $("login-screen").classList.add("hidden");
  $("app-shell").classList.remove("hidden");
  setScenes(true);
  renderIdentity();
  try { await loadTeam(); } catch { /* cells fall back to plain text */ }
  renderIdentity();
  applyView(viewFromHash());
}

async function loadTeam(force) {
  if (state.teamLoaded && !force) return;
  state.team = await agentFetch("/api/v1/agent/team");
  state.teamLoaded = true;
}

function logout(silent) {
  state.token = null;
  state.agentUsername = null;
  state.conversationId = null;
  state.team = null;
  state.teamLoaded = false;
  sessionStorage.removeItem("ash.jwt");
  sessionStorage.removeItem("ash.agentUser");
  showLogin();
  if (!silent) toast("Signed out.");
}

$("login-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const btn = $("login-btn");
  const errBox = $("login-error");
  errBox.classList.add("hidden");
  btn.disabled = true;
  try {
    const res = await fetch(`${API}/api/v1/agent/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        username: $("login-username").value.trim(),
        password: $("login-password").value,
      }),
    });
    if (!res.ok) throw new Error(await parseError(res));
    const { token, username } = await res.json();
    state.token = token;
    state.agentUsername = username || null;
    sessionStorage.setItem("ash.jwt", token);
    sessionStorage.setItem("ash.agentUser", username || "");
    errBox.classList.add("hidden");
    showApp();
    if (hub.mini) hub.mini.emit(6);
    toast(username ? `Welcome back, @${username}.` : "Welcome back.", "success");
  } catch (err) {
    errBox.textContent = err.message;
    errBox.classList.remove("hidden");
  } finally {
    btn.disabled = false;
  }
});

$("logout-btn").addEventListener("click", () => logout(false));

/* ------------------------------------------------------------------ nav */
const VIEW_TITLES = {
  conversations: "Conversations",
  chat: "Chat Playground",
  knowledge: "Knowledge Base",
  clients: "Clients",
  guide: "API Guide",
  about: "About this hub",
};
const VIEW_EYEBROWS = {
  conversations: "Queue",
  chat: "Test console",
  knowledge: "Training",
  clients: "Projects & logins",
  guide: "Integration",
  about: "Credits",
};

/* Views are deep-linkable: opening #about lands on About, and a reload stays
   where the agent was working. Unknown or missing hashes mean the queue. */
function viewFromHash() {
  const v = decodeURIComponent(location.hash || "").replace(/^#\/?/, "");
  return VIEW_TITLES[v] ? v : "conversations";
}

function applyView(view) {
  document.querySelectorAll(".nav-item").forEach((n) => n.classList.toggle("active", n.dataset.view === view));
  document.querySelectorAll(".view").forEach((v) => v.classList.add("hidden"));
  $(`view-${view}`).classList.remove("hidden");
  $("view-title").textContent = VIEW_TITLES[view];
  $("view-eyebrow").textContent = VIEW_EYEBROWS[view] || "";
  if (viewFromHash() !== view) history.replaceState(null, "", `#${view}`);
  if (view === "conversations") loadConversations();
  if (view === "knowledge") refreshProjects();
  if (view === "clients") loadClientList();
  if (view === "about") loadDeveloper();
}

document.querySelectorAll(".nav-item").forEach((item) => {
  item.addEventListener("click", () => applyView(item.dataset.view));
});
window.addEventListener("hashchange", () => {
  if (!$("app-shell").classList.contains("hidden")) applyView(viewFromHash());
});
document.querySelectorAll("[data-goto]").forEach((btn) => {
  btn.addEventListener("click", () => applyView(btn.dataset.goto));
});

document.querySelectorAll(".tab").forEach((tab) => {
  tab.addEventListener("click", () => {
    document.querySelectorAll(".tab").forEach((t) => t.classList.remove("active"));
    tab.classList.add("active");
    ["kn-text", "kn-file"].forEach((id) => $(id).classList.add("hidden"));
    $(tab.dataset.tab).classList.remove("hidden");
  });
});

/* -------------------------------------------------------- conversations */
async function loadConversations() {
  $("conv-loading").classList.remove("hidden");
  $("conv-empty").classList.add("hidden");
  $("conv-table").classList.add("hidden");
  try {
    state.conversations = await agentFetch("/api/v1/agent/conversations");
    renderConversations();
  } catch (err) {
    toast(err.message, "error");
  } finally {
    $("conv-loading").classList.add("hidden");
  }
}

function renderConversations() {
  const q = $("conv-search").value.trim().toLowerCase();
  const rows = state.conversations.filter((c) =>
    !q || [c.conversationKey, c.customerId, c.summary, c.assignedAgentName]
      .some((v) => (v || "").toLowerCase().includes(q))
  );
  $("conv-table").classList.toggle("hidden", rows.length === 0);
  $("conv-empty").classList.toggle("hidden", rows.length !== 0);
  $("conv-empty").querySelector("p").textContent = q && state.conversations.length
    ? `No conversations match “${q}”.`
    : "No conversations yet. Chat with the AI assistant to create one.";

  $("conv-body").innerHTML = rows.map((c) => `
    <tr>
      <td>
        <div class="conv-summary">${esc(c.summary || "No summary yet — it is written automatically after the first AI reply.")}</div>
        <div class="conv-key">${esc(c.conversationKey)}</div>
      </td>
      <td>${esc(c.customerId)}</td>
      <td>${badge((c.status || "").replace(/_/g, " "), STATUS_BADGE[c.status])}</td>
      <td>${badge((c.priority || "—").replace(/_/g, " "), PRIORITY_BADGE[c.priority])}</td>
      <td>${agentCell(c)}</td>
      <td class="muted small">${esc(fmtDate(c.updatedAt || c.createdAt))}</td>
    </tr>`).join("");
  bindConvActions();
}

/* Agent column: a dropdown over my tenant's active agents — pick a name to assign
   or reassign, Release to hand the ticket back to the queue. */
function agentCell(c) {
  const current = c.assignedAgentName;
  if (!state.teamLoaded) {
    const label = current ? `@${esc(current)}` : "unassigned";
    return `<div class="conv-agent-cell"><span class="conv-agent${current === state.agentUsername ? " me" : ""}">${label}</span></div>`;
  }
  const team = (state.team || []).filter((a) => a.active || a.username === current);
  const opts = [];
  if (!current) opts.push('<option value="" disabled selected>— assign… —</option>');
  for (const a of team) {
    const label = a.username + (a.username === state.agentUsername ? " (you)" : "");
    opts.push(`<option value="${esc(a.username)}"${current === a.username ? " selected" : ""}>${esc(label)}</option>`);
  }
  if (current && !team.some((a) => a.username === current)) {
    opts.push(`<option selected>${esc(current)} · removed</option>`);
  }
  const pick = `<select class="input agent-pick" data-conv="${c.id}" aria-label="Assigned agent">${opts.join("")}</select>`;
  const release = current
    ? `<button type="button" class="btn btn-ghost btn-small" data-release="${c.id}">Release</button>` : "";
  return `<div class="conv-agent-cell">${pick}${release}</div>`;
}

function bindConvActions() {
  $("conv-body").querySelectorAll(".agent-pick").forEach((sel) => {
    sel.addEventListener("change", () => assignConversation(sel.dataset.conv, sel.value, sel));
  });
  $("conv-body").querySelectorAll("[data-release]").forEach((btn) => {
    btn.addEventListener("click", () => releaseConversation(btn.dataset.release, btn));
  });
}

async function assignConversation(id, username, sel) {
  sel.disabled = true;
  try {
    await agentFetch(`/api/v1/agent/conversations/${id}/assign`,
      { method: "POST", body: JSON.stringify({ username }) });
    toast(username === state.agentUsername ? "Assigned to you." : `Assigned to @${username}.`, "success");
    await loadConversations();
  } catch (err) {
    toast(err.message, "error");
    renderConversations();
  }
}

async function releaseConversation(id, btn) {
  btn.disabled = true;
  try {
    await agentFetch(`/api/v1/agent/conversations/${id}/assign`, { method: "DELETE" });
    toast("Released back to the queue.", "success");
    await loadConversations();
  } catch (err) {
    toast(err.message, "error");
    renderConversations();
  }
}

$("conv-refresh").addEventListener("click", loadConversations);
$("conv-search").addEventListener("input", renderConversations);

/* ------------------------------------------------------------------ chat */
function addBubble(text, cls) {
  const b = document.createElement("div");
  b.className = `chat-msg ${cls}`;
  b.textContent = text;
  $("chat-transcript").appendChild(b);
  $("chat-transcript").scrollTop = $("chat-transcript").scrollHeight;
  return b;
}

$("api-key-toggle").addEventListener("click", () => {
  const input = $("api-key");
  const reveal = input.type === "password";
  input.type = reveal ? "text" : "password";
  $("api-key-toggle").classList.toggle("eye-on", reveal);
});

$("chat-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const input = $("chat-input");
  const message = input.value.trim();
  if (!message) return;
  const btn = $("chat-send");
  btn.disabled = true;
  addBubble(message, "chat-msg-customer");
  input.value = "";
  const thinking = addBubble("AI is thinking…", "chat-msg-ai");
  try {
    const data = await clientFetch("/api/v1/support/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        customerId: $("chat-customer").value.trim() || "cust-001",
        conversationId: state.conversationId,
        message,
      }),
    });
    thinking.textContent = data.response;
    const meta = document.createElement("span");
    meta.className = "chat-meta";
    meta.textContent = `conversation ${data.conversationId} · ${data.status}`;
    thinking.appendChild(meta);
    state.conversationId = data.conversationId;
    if (hub.mini) hub.mini.emit(5);   // a dispatch pulse on the sidebar core
    $("chat-status").textContent = (data.status || "").replace(/_/g, " ");
    $("chat-status").className = `badge ${STATUS_BADGE[data.status] || "badge-gray"}`;
  } catch (err) {
    thinking.className = "chat-msg chat-msg-error";
    thinking.textContent = err.message;
  } finally {
    btn.disabled = false;
    input.focus();
  }
});

$("chat-reset").addEventListener("click", () => {
  state.conversationId = null;
  $("chat-transcript").innerHTML =
    '<div class="chat-msg chat-msg-ai">New conversation started.</div>';
  $("chat-status").textContent = "new conversation";
  $("chat-status").className = "badge badge-gray";
});

/* ------------------------------------------------------------- knowledge
   Training data is uploaded per project: pick a client, ingest text or a
   file through the agent endpoints (/api/v1/clients/{id}/knowledge).
   Project names can repeat across tenants, so every label pairs the name
   with its unique client key. */
const KN_CLIENT_KEY = "ash.knClientId";
const knTextSubmit = $("kn-text-submit");
const knFileSubmit = $("kn-file-submit");

function knProjectById(id) {
  return state.clients.find((c) => String(c.id) === String(id)) || null;
}

function setKnFormsEnabled(hasProject) {
  knTextSubmit.disabled = !hasProject;
  knFileSubmit.disabled = !hasProject;
}

function updateKnTarget() {
  const c = knProjectById($("kn-client").value);
  $("kn-target-name").textContent = c ? c.name : "—";
  $("kn-target-key").textContent = c ? c.clientKey : "";
  $("kn-target-key-wrap").classList.toggle("hidden", !c);
  const label = c ? `${c.name} (${c.clientKey})` : "—";
  $("kn-text-project-name").textContent = label;
  $("kn-file-project-name").textContent = label;
  setKnFormsEnabled(!!c);
}

async function refreshProjects() {
  const sel = $("kn-client");
  sel.disabled = true;
  setKnFormsEnabled(false);
  if (!state.clientsLoaded) {
    sel.innerHTML = "";
    sel.appendChild(new Option("Loading projects…", ""));
    $("kn-docs").innerHTML = "";
  }
  try {
    if (!state.clientsLoaded) {
      state.clients = await agentFetch("/api/v1/clients");
      state.clientsLoaded = true;
    }
  } catch (err) {
    sel.innerHTML = "";
    sel.appendChild(new Option("Failed to load projects", ""));
    $("kn-docs").innerHTML = "";
    updateKnTarget();
    sel.disabled = false;
    toast(err.message, "error");
    return;
  }
  const previous = localStorage.getItem(KN_CLIENT_KEY);
  sel.innerHTML = "";
  if (!state.clients.length) {
    sel.appendChild(new Option("No projects yet — onboard one first", ""));
    $("kn-docs").innerHTML = "";
    updateKnTarget();
    sel.disabled = false;
    return;
  }
  for (const c of state.clients) sel.appendChild(new Option(`${c.name} · ${c.clientKey}`, c.id));
  sel.value = state.clients.some((c) => String(c.id) === previous) ? previous : state.clients[0].id;
  sel.disabled = false;
  updateKnTarget();
  loadKnDocs();
}

async function loadKnDocs() {
  const id = $("kn-client").value;
  const box = $("kn-docs");
  if (!id) { box.innerHTML = ""; return; }
  box.innerHTML = '<span class="kn-docs-label">Loading trained documents…</span>';
  try {
    const docs = await agentFetch(`/api/v1/clients/${id}/knowledge`);
    box.innerHTML = docs.length
      ? `<span class="kn-docs-label">Trained (${docs.length})</span>` +
        docs.slice(0, 12).map((d) =>
          `<span class="kn-chip" title="${esc([d.category, d.version && ("v" + d.version)].filter(Boolean).join(" · "))}">${esc(d.name)}</span>`
        ).join("") +
        (docs.length > 12 ? `<span class="kn-chip more">+${docs.length - 12} more</span>` : "")
      : '<span class="kn-docs-label">Nothing trained for this project yet.</span>';
  } catch (err) {
    box.innerHTML = '<span class="kn-docs-label">Could not load trained documents.</span>';
    toast(err.message, "error");
  }
}

$("kn-client").addEventListener("change", () => {
  localStorage.setItem(KN_CLIENT_KEY, $("kn-client").value);
  updateKnTarget();
  loadKnDocs();
});
$("kn-client-refresh").addEventListener("click", () => {
  state.clientsLoaded = false;
  refreshProjects();
});
$("kn-file-input").addEventListener("change", () => {
  const file = $("kn-file-input").files[0];
  const meta = $("kn-file-meta");
  if (!file) { meta.classList.add("hidden"); meta.textContent = ""; return; }
  const size = (file.size / 1024 / 1024).toFixed(2);
  const oversize = file.size > 25 * 1024 * 1024;
  meta.textContent = `${file.name} · ${size} MB${oversize ? " — over the 25 MB limit" : ""}`;
  meta.className = "kn-file-meta muted small" + (oversize ? " over-limit" : "");
});

function selectedProject() {
  const id = $("kn-client").value;
  if (!id) throw new Error("Pick a project to train first — onboard a client if none exist yet.");
  return id;
}

$("kn-text-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const box = $("kn-text-result");
  if (knTextSubmit.disabled) return;
  knTextSubmit.disabled = true;
  knTextSubmit.textContent = "Ingesting…";
  try {
    const id = selectedProject();
    const doc = await agentFetch(`/api/v1/clients/${id}/knowledge`, {
      method: "POST",
      body: JSON.stringify({
        name: $("kn-name").value.trim(),
        category: $("kn-category").value.trim() || null,
        version: $("kn-version").value.trim() || null,
        content: $("kn-content").value,
      }),
    });
    const target = knProjectById(id);
    box.className = "result-box";
    box.innerHTML = `Ingested <strong>${esc(doc.name)}</strong> into `
      + `<strong>${esc(target ? `${target.name} (${target.clientKey})` : "the selected project")}</strong>. `
      + "The AI can now cite it.";
    box.classList.remove("hidden");
    e.target.reset();
    loadKnDocs();
  } catch (err) {
    box.className = "result-box error";
    box.textContent = err.message;
    box.classList.remove("hidden");
  } finally {
    knTextSubmit.textContent = "Ingest document";
    setKnFormsEnabled(!!knProjectById($("kn-client").value));
  }
});

$("kn-file-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const box = $("kn-file-result");
  const file = $("kn-file-input").files[0];
  if (!file || knFileSubmit.disabled) return;
  if (!/\.(pdf|md|markdown)$/i.test(file.name)) {
    box.className = "result-box error";
    box.textContent = "Unsupported file type. Only PDF and Markdown (.pdf / .md) files can be ingested.";
    box.classList.remove("hidden");
    return;
  }
  if (file.size > 25 * 1024 * 1024) {
    box.className = "result-box error";
    box.textContent = `File is too large (${(file.size / 1024 / 1024).toFixed(1)} MB). Maximum upload size is 25 MB.`;
    box.classList.remove("hidden");
    return;
  }
  knFileSubmit.disabled = true;
  knFileSubmit.textContent = "Uploading…";
  $("kn-upload-progress").classList.remove("hidden");
  try {
    const id = selectedProject();
    const form = new FormData();
    form.append("file", file);
    const params = new URLSearchParams();
    for (const [k, el] of [["name", "knf-name"], ["category", "knf-category"], ["version", "knf-version"]]) {
      const v = $(el).value.trim();
      if (v) params.set(k, v);
    }
    const qs = params.toString();
    const doc = await agentFetch(`/api/v1/clients/${id}/knowledge/file${qs ? "?" + qs : ""}`, {
      method: "POST",
      body: form, // browser sets the multipart boundary
    });
    const target = knProjectById(id);
    box.className = "result-box";
    box.innerHTML = `Uploaded <strong>${esc(doc.name)}</strong> (${esc((file.size / 1024).toFixed(1))} KB) into `
      + `<strong>${esc(target ? `${target.name} (${target.clientKey})` : "the selected project")}</strong>.`;
    box.classList.remove("hidden");
    e.target.reset();
    $("kn-file-meta").classList.add("hidden");
    $("kn-file-meta").textContent = "";
    loadKnDocs();
  } catch (err) {
    box.className = "result-box error";
    // A TypeError from fetch means the connection dropped before we got a
    // response — usually an oversize upload aborted by the server.
    box.textContent = err instanceof TypeError
      ? "Upload failed: the server closed the connection mid-request. If the file is large, keep it under 25 MB and try again."
      : err.message;
    box.classList.remove("hidden");
  } finally {
    $("kn-upload-progress").classList.add("hidden");
    knFileSubmit.textContent = "Upload & ingest";
    setKnFormsEnabled(!!knProjectById($("kn-client").value));
  }
});

/* ---------------------------------------------------------------- clients
   Onboarding asks for one thing: the project name. The client key is derived
   from it (slug + random suffix) and the API key is a fresh 160-bit secret. */
function randomHex(bytes) {
  const a = new Uint8Array(bytes);
  if (window.crypto && crypto.getRandomValues) crypto.getRandomValues(a);
  else for (let i = 0; i < bytes; i++) a[i] = Math.floor(Math.random() * 256);
  return [...a].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function slugify(s) {
  return (s.toLowerCase().normalize("NFKD")
    .replace(/[^a-z0-9\s-]/g, "")
    .trim().replace(/[\s_]+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "")
    .slice(0, 24) || "");
}

function generateCreds() {
  const base = slugify($("cl-name").value.trim()) || "client";
  pendingCreds = { clientKey: `${base}-${randomHex(3)}`, apiKey: `ash_${randomHex(20)}` };
  renderGenPreview();
}

function renderGenPreview() {
  const p = $("gen-preview");
  if (!pendingCreds) { p.classList.add("hidden"); return; }
  p.classList.remove("hidden");
  $("gen-client-key").textContent = pendingCreds.clientKey;
  $("gen-api-key").textContent = pendingCreds.apiKey;
}

$("cl-name").addEventListener("input", () => {
  if (!pendingCreds && $("cl-name").value.trim().length >= 2) generateCreds();
});

$("gen-reroll").addEventListener("click", () => {
  if (!$("cl-name").value.trim()) { toast("Type a project name first."); return; }
  generateCreds();
});

/* Registered projects table: name, unique client key, status, created date. */
async function loadClientList() {
  const host = $("client-list");
  if (!state.clientsLoaded) {
    host.innerHTML = '<p class="muted small">Loading projects…</p>';
  }
  try {
    if (!state.clientsLoaded) {
      state.clients = await agentFetch("/api/v1/clients");
      state.clientsLoaded = true;
    }
  } catch (err) {
    host.innerHTML = `<p class="muted small">Could not load projects: ${esc(err.message)}</p>`;
    return;
  }
  if (!state.clients.length) {
    host.innerHTML = '<p class="muted small">No projects yet — onboard the first one above.</p>';
    $("ag-project").innerHTML = "";
    $("ag-list").innerHTML = '<p class="muted small">No projects yet — onboard one first.</p>';
    return;
  }
  host.innerHTML = `<table class="table client-table">
      <thead><tr><th>Project</th><th>Client key</th><th>Status</th><th>Created</th><th>ID</th><th>API key</th></tr></thead>
      <tbody>${state.clients.map((c) => `
        <tr>
          <td class="cl-td-name">${esc(c.name)}</td>
          <td><div class="cl-key-cell"><code class="kn-key" id="cl-key-${c.id}">${esc(c.clientKey)}</code>`
            + `<button type="button" class="btn btn-ghost btn-small copy-btn" data-copy="cl-key-${c.id}">Copy</button></div></td>
          <td><span class="badge ${c.active ? "badge-green" : "badge-gray"}">${c.active ? "Active" : "Disabled"}</span></td>
          <td class="cl-td-meta">${esc((c.createdAt || "").slice(0, 10))}</td>
          <td class="cl-td-meta">${c.id}</td>
          <td><button type="button" class="btn btn-ghost btn-small rotate-btn" data-rotate="${c.id}">Rotate key</button></td>
        </tr>`).join("")}</tbody>
    </table>
    <p class="client-list-count muted small">${state.clients.length} project${state.clients.length === 1 ? "" : "s"} total. Rotating issues a new API secret for the same project — client key, id and trained knowledge are kept.</p>`;
  host.querySelectorAll(".copy-btn[data-copy]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const src = $(btn.dataset.copy);
      if (src && src.textContent) copyText(src.textContent, btn);
    });
  });
  host.querySelectorAll("[data-rotate]").forEach((btn) => {
    btn.addEventListener("click", () => rotateApiKey(btn));
  });
  populateAgentProjects();
  loadAgentList();
}

async function rotateApiKey(btn) {
  const c = state.clients.find((x) => String(x.id) === btn.dataset.rotate);
  if (!c) return;
  const sure = window.confirm(
    `Issue a new API key for "${c.name}" (${c.clientKey})?\n\n`
    + "The old API key stops working immediately. The client key, tenant id and all trained knowledge stay unchanged.");
  if (!sure) return;
  btn.disabled = true;
  btn.textContent = "Rotating…";
  try {
    const r = await agentFetch(`/api/v1/clients/${c.id}/rotate-api-key`, { method: "POST" });
    showTicket({ name: r.name, id: r.id, clientKey: r.clientKey, apiKey: r.apiKey });
    toast(`New API key issued for ${r.name} — training untouched.`, "success");
  } catch (err) {
    toast(err.message, "error");
  } finally {
    btn.textContent = "Rotate key";
    btn.disabled = false;
  }
}

$("client-list-refresh").addEventListener("click", () => {
  state.clientsLoaded = false;
  loadClientList();
});

/* ------------------------------------------------- Support agents (Clients view) */
const AG_PROJECT_KEY = "ash.agProject";

function populateAgentProjects() {
  const sel = $("ag-project");
  if (!state.clientsLoaded) return;
  const prev = localStorage.getItem(AG_PROJECT_KEY) || sel.value;
  sel.innerHTML = state.clients.map((c) =>
    `<option value="${c.id}">${esc(c.name)} · ${esc(c.clientKey)}</option>`).join("");
  if (state.clients.some((c) => String(c.id) === String(prev))) sel.value = prev;
}

async function loadAgentList() {
  const host = $("ag-list");
  const id = $("ag-project").value;
  if (!id) {
    host.innerHTML = '<p class="muted small">No projects yet — onboard one first.</p>';
    return;
  }
  host.innerHTML = '<p class="muted small">Loading agents…</p>';
  try {
    const agents = await agentFetch(`/api/v1/clients/${id}/agents`);
    state.agents = agents;
    if (!agents.length) {
      host.innerHTML = '<p class="muted small">No agents on this project yet — add the first one above.</p>';
      return;
    }
    host.innerHTML = `<table class="table client-table">
        <thead><tr><th>Username</th><th>Role</th><th>Status</th><th>Added</th><th></th></tr></thead>
        <tbody>${agents.map((a) => {
          const mine = a.username === state.agentUsername;
          const admin = a.role === "ADMIN";
          /* Admin logins are listed but never removable from here — the API would
             refuse a project's last admin anyway, so offer no button to fail with. */
          const action = admin
            ? `<span class="ag-protected" role="note" title="Admin logins are not removed from the dashboard, so this project always has someone who can manage it.">
                 <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 10V7a4 4 0 0 1 8 0v3M6 10h12a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1z"/></svg>Protected</span>`
            : `<button type="button" class="btn btn-ghost btn-small ag-remove" data-remove="${a.id}"
                 data-user="${esc(a.username)}"${mine ? " disabled title=\"This is the account you are signed in with\"" : ""}>${mine ? "Signed in" : "Remove"}</button>`;
          return `
          <tr>
            <td class="cl-td-name"><code class="kn-key">${esc(a.username)}</code>${mine ? ' <span class="badge badge-indigo">you</span>' : ""}</td>
            <td>${badge(a.role, admin ? "badge-amber" : "badge-gray")}</td>
            <td><span class="badge ${a.active ? "badge-green" : "badge-gray"}">${a.active ? "Active" : "Disabled"}</span></td>
            <td class="cl-td-meta">${esc((a.createdAt || "").slice(0, 10))}</td>
            <td>${action}</td>
          </tr>`;
        }).join("")}</tbody>
      </table>
      <p class="client-list-count muted small">${agents.length} agent${agents.length === 1 ? "" : "s"} on this project. Removing one only takes away their login: their tickets go back to the queue, and the project, its API key and its trained knowledge stay. Admin rows are marked Protected and have no Remove button.</p>`;
    host.querySelectorAll("[data-remove]").forEach((btn) => {
      btn.addEventListener("click", () => removeAgent(btn));
    });
  } catch (err) {
    host.innerHTML = `<p class="muted small">Could not load agents: ${esc(err.message)}</p>`;
  }
}

async function removeAgent(btn) {
  const projectId = $("ag-project").value;
  const project = knProjectById(projectId);
  const username = btn.dataset.user;
  const sure = window.confirm(
    `Remove agent "@${username}"${project ? ` from "${project.name}"` : ""}?\n\n`
    + "They can no longer sign in, and any ticket still on their queue goes back to the pool. "
    + "The project, its client key, API key and trained knowledge are untouched.");
  if (!sure) return;
  btn.disabled = true;
  btn.textContent = "Removing…";
  try {
    const r = await agentFetch(`/api/v1/clients/${projectId}/agents/${btn.dataset.remove}`, { method: "DELETE" });
    toast(r.ticketsReleased
      ? `Agent @${r.username} removed — ${r.ticketsReleased} ticket${r.ticketsReleased === 1 ? "" : "s"} back in the queue.`
      : `Agent @${r.username} removed.`, "success");
    await loadAgentList();
    await loadTeam(true);
    loadConversations();
  } catch (err) {
    toast(err.message, "error");
    btn.textContent = "Remove";
    btn.disabled = false;
  }
}

function setAgAddEnabled() {
  $("ag-add").disabled = !(
    $("ag-username").value.trim().length >= 3 && $("ag-password").value.length >= 8);
}

$("ag-username").addEventListener("input", setAgAddEnabled);
$("ag-password").addEventListener("input", setAgAddEnabled);
$("ag-project").addEventListener("change", () => {
  localStorage.setItem(AG_PROJECT_KEY, $("ag-project").value);
  loadAgentList();
});
$("ag-refresh").addEventListener("click", loadAgentList);

$("ag-add").addEventListener("click", async () => {
  const btn = $("ag-add");
  const username = $("ag-username").value.trim();
  const client = knProjectById($("ag-project").value);
  btn.disabled = true;
  btn.textContent = "Adding…";
  try {
    const a = await agentFetch(`/api/v1/clients/${$("ag-project").value}/agents`,
      { method: "POST", body: JSON.stringify({ username, password: $("ag-password").value }) });
    toast(a.username === username
      ? `Agent @${a.username} added to ${client ? client.name : "the project"}.`
      : `Username taken — agent issued as @${a.username}.`
        + " That exact name is the login.", "success");
    $("ag-username").value = "";
    $("ag-password").value = "";
    loadAgentList();
    loadTeam(true); // keep the Conversations dropdown in sync
  } catch (err) {
    toast(err.message, "error");
  } finally {
    btn.textContent = "Add agent";
    setAgAddEnabled();
  }
});

/* copy buttons bound to a value element id */
document.querySelectorAll(".copy-btn[data-copy]").forEach((btn) => {
  btn.addEventListener("click", () => {
    const src = $(btn.dataset.copy);
    if (src && src.textContent) copyText(src.textContent, btn);
  });
});

$("guide-copy-curl").addEventListener("click", () => {
  const src = $("guide-curl");
  if (!src) return;
  const ck = $("client-key").value.trim() || sessionStorage.getItem("ash.clientKey") || "";
  const ak = $("api-key").value.trim() || sessionStorage.getItem("ash.apiKey") || "";
  let text = src.textContent;
  if (ck) text = text.replaceAll("&lt;your-client-key&gt;", ck).replaceAll("<your-client-key>", ck);
  if (ak) text = text.replaceAll("&lt;your-api-key&gt;", ak).replaceAll("<your-api-key>", ak);
  copyText(text, $("guide-copy-curl"));
});

function showTicket({ name, id, clientKey, apiKey }) {
  const slot = $("ticket-slot");
  slot.innerHTML = `
    <div class="ticket" role="status">
      <div class="ticket-head">
        <p class="ticket-eyebrow">Credentials issued</p>
        <h4 id="tk-name"></h4>
      </div>
      <div class="ticket-body">
        <span class="stamp">API key shown once</span>
        <div class="ticket-row">
          <span class="ticket-label">Client key</span>
          <div class="ticket-value-row">
            <code class="ticket-value" id="tk-client-key"></code>
            <button type="button" class="btn btn-ghost btn-small copy-btn" data-copy="tk-client-key">Copy</button>
          </div>
        </div>
        <div class="ticket-row">
          <span class="ticket-label">API key</span>
          <div class="ticket-value-row">
            <code class="ticket-value" id="tk-api-key"></code>
            <button type="button" class="btn btn-ghost btn-small copy-btn" data-copy="tk-api-key">Copy</button>
          </div>
        </div>
        <div class="ticket-row">
          <span class="ticket-label">Tenant id</span>
          <div class="ticket-value-row"><code class="ticket-value" id="tk-id"></code></div>
        </div>
        <div class="ticket-actions">
          <button type="button" class="btn btn-primary btn-small" id="ticket-use">Use in Chat Playground</button>
          <button type="button" class="btn btn-ghost btn-small" id="ticket-dismiss">Done</button>
        </div>
      </div>
    </div>`;
  $("tk-name").textContent = name;
  $("tk-client-key").textContent = clientKey;
  $("tk-api-key").textContent = apiKey;
  $("tk-id").textContent = id;
  slot.querySelectorAll(".copy-btn[data-copy]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const src = $(btn.dataset.copy);
      if (src && src.textContent) copyText(src.textContent, btn);
    });
  });
  $("ticket-use").addEventListener("click", () => {
    $("client-key").value = clientKey;
    $("api-key").value = apiKey;
    sessionStorage.setItem("ash.clientKey", clientKey);
    sessionStorage.setItem("ash.apiKey", apiKey);
    applyView("chat");
    toast("Credentials loaded — chat away.", "success");
  });
  $("ticket-dismiss").addEventListener("click", () => { slot.innerHTML = ""; });
}

$("client-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const box = $("client-result");
  const btn = $("client-create");
  const name = $("cl-name").value.trim();
  if (!name) return;
  if (!pendingCreds) generateCreds();
  const keyRequested = pendingCreds.clientKey;

  btn.disabled = true;
  box.classList.add("hidden");
  try {
    const c = await agentFetch("/api/v1/clients", {
      method: "POST",
      body: JSON.stringify({
        clientKey: pendingCreds.clientKey,
        name,
        apiKey: pendingCreds.apiKey,
      }),
    });
    /* The server guarantees a unique client key: if the generated one already
       exists it issues a suffixed variant, so read the final key off the response. */
    showTicket({ name: c.name, id: c.id, clientKey: c.clientKey, apiKey: pendingCreds.apiKey });
    e.target.reset();
    pendingCreds = null;
    renderGenPreview();
    state.clientsLoaded = false; // new project should appear in the Knowledge picker
    loadClientList();            // …and in the table on this screen
    toast(c.clientKey !== keyRequested
      ? `Client onboarded — key collision resolved, issued ${c.clientKey}`
      : "Client onboarded", "success");
  } catch (err) {
    box.className = "result-box error";
    box.textContent = err.message;
    box.classList.remove("hidden");
  } finally {
    btn.disabled = false;
  }
});

/* ------------------------------------------------------------------ about
   Credits only: who built this, and where to reach them. The card reads the
   public GitHub profile for a current name, bio and photo, and falls back to
   baked values when the API is unreachable, offline or rate-limited. No hub
   credentials go with it — a plain public GET — and it is cached per session. */
const GH_USER = "usm4nhafeez";
const GH_API = "https://api.github.com";
const DEV_CACHE_KEY = "ash.dev";

const DEV_FALLBACK = {
  name: "Muhammad Usman",
  bio: "Software Engineering Student at COMSATS",
  location: "Lahore, Pakistan",
  html_url: `https://github.com/${GH_USER}`,
  avatar_url: "https://avatars.githubusercontent.com/u/154317430?v=4",
};

async function loadDeveloper() {
  const cached = sessionStorage.getItem(DEV_CACHE_KEY);
  if (cached) {
    try {
      const data = JSON.parse(cached);
      if (data && data.name) { renderDeveloper(data); return; }
    } catch { /* unreadable cache: refetch below */ }
    sessionStorage.removeItem(DEV_CACHE_KEY);
  }
  try {
    const res = await fetch(`${GH_API}/users/${GH_USER}`, { headers: { Accept: "application/vnd.github+json" } });
    if (!res.ok) throw new Error(`GitHub answered HTTP ${res.status}`);
    const p = await res.json();
    const data = {
      name: p.name || p.login || GH_USER,
      bio: p.bio || "",
      location: p.location || "",
      html_url: p.html_url || DEV_FALLBACK.html_url,
      avatar_url: p.avatar_url || "",
    };
    try { sessionStorage.setItem(DEV_CACHE_KEY, JSON.stringify(data)); } catch { /* private mode */ }
    renderDeveloper(data);
  } catch {
    renderDeveloper(DEV_FALLBACK);
  }
}

function renderDeveloper(d) {
  const setText = (id, value) => { const el = $(id); if (el) el.textContent = value; };
  setText("dev-name", d.name || GH_USER);
  setText("dev-bio", [d.bio, d.location].filter(Boolean).join(" · "));
  const gh = $("dev-github");
  if (gh && d.html_url) gh.href = d.html_url;
  const avatar = $("dev-avatar");
  if (avatar && d.avatar_url && !avatar.src.endsWith(d.avatar_url.split("?")[0])) {
    avatar.onerror = () => { avatar.onerror = null; avatar.src = "/assets/avatar.png"; };
    avatar.alt = d.name || GH_USER;
    avatar.src = d.avatar_url;
  }
}

/* ---------------------------------------------------------------- health */
async function checkHealth() {
  const dot = $("api-dot");
  const text = $("api-status-text");
  try {
    const res = await fetch(`${API}/actuator/health`);
    const up = res.ok && (await res.json()).status === "UP";
    dot.className = `dot ${up ? "dot-up" : "dot-down"}`;
    text.textContent = up ? "API online" : "API degraded";
  } catch {
    dot.className = "dot dot-down";
    text.textContent = "API unreachable";
  }
}

/* ------------------------------------------------------------------ boot */
(function restoreCreds() {
  $("client-key").value = sessionStorage.getItem("ash.clientKey") || "demo-client";
  $("api-key").value = sessionStorage.getItem("ash.apiKey") || "";
})();

checkHealth();
setInterval(checkHealth, 30000);
state.token ? showApp() : showLogin();

/* three.js mounts last and never blocks the console: if the module or WebGL is
   unavailable, the flat CSS backdrop stands in and everything else still works. */
(async function mountHub() {
  try {
    const { createHubScene } = await import("./assets/hub-scene.js");
    hub.full = createHubScene($("login-scene"), "full");
    hub.mini = createHubScene($("hub-mini"), "mini");
    setScenes(!!state.token);
  } catch (err) {
    console.info("Routing core unavailable:", err.message);
  }
})();

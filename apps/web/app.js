const cloudImage = document.querySelector("#cloudImage");
const poemSlip = document.querySelector("#poemSlip");
const analysisPanel = document.querySelector("#analysisPanel");
const analysisPoem = document.querySelector("#analysisPoem");
const balanceMeter = document.querySelector("#balanceMeter");
const balancePanel = document.querySelector("#balancePanel");
const statusLine = document.querySelector("#statusLine");

const fallbackPoem = {
  title: "云图诗鉴",
  lines: ["云图正在判", "读完成后更", "新今日诗句", "请稍候片刻"],
  poemDateShanghai: "",
  analysis: {},
  evidence: {},
};

let poemData = fallbackPoem;
let balanceData = null;
let currentFont = localStorage.getItem("cloud-poem-font") === "kai" ? "kai" : "xing";
let dragState = null;

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function resolvedWindows(tool) {
  const windows = Array.isArray(tool?.windows) ? tool.windows.filter((item) => Number.isFinite(item.pct)) : [];
  if (windows.length > 0) {
    return windows;
  }
  return [
    Number.isFinite(tool?.p5) ? { label: "5时", pct: tool.p5, reset: tool.r5, hourScale: true } : null,
    Number.isFinite(tool?.p7) ? { label: "7天", pct: tool.p7, reset: tool.r7, hourScale: false } : null,
  ].filter(Boolean);
}

function tightestWindow(tool) {
  const windows = resolvedWindows(tool);
  if (windows.length === 0) {
    return null;
  }
  return windows.reduce((lowest, item) => item.pct < lowest.pct ? item : lowest, windows[0]);
}

function countdown(windowData) {
  if (!windowData?.reset) {
    return "--";
  }
  const remainingMinutes = Math.floor((windowData.reset * 1000 - Date.now()) / 60000);
  if (remainingMinutes <= 0) {
    return "即将刷新";
  }
  const hours = Math.floor(remainingMinutes / 60);
  const days = Math.floor(hours / 24);
  if (windowData.hourScale) {
    return hours > 0 ? `${hours}时${remainingMinutes % 60}分` : `${Math.max(1, remainingMinutes)}分`;
  }
  return days > 0 ? `${days}天${hours % 24}时` : `${hours}时`;
}

function updateMeter(prefix, tool) {
  const windowData = tightestWindow(tool);
  const percent = Number.isFinite(windowData?.pct) ? clamp(Math.round(windowData.pct), 0, 100) : null;
  document.querySelector(`#${prefix}Reset`).textContent = countdown(windowData);
  document.querySelector(`#${prefix}Value`).textContent = percent === null ? "--" : `${percent}%`;
  document.querySelector(`#${prefix}Fill`).style.width = percent === null ? "0" : `${percent}%`;
}

function compactTokens(value) {
  if (!Number.isFinite(value)) {
    return "--";
  }
  if (value >= 100_000_000) {
    return `${Number((value / 100_000_000).toFixed(value >= 1_000_000_000 ? 0 : 1))}亿`;
  }
  if (value >= 10_000) {
    return `${Number((value / 10_000).toFixed(value >= 1_000_000 ? 0 : 1))}万`;
  }
  return String(Math.round(value));
}

function renderToolDetail(prefix, tool) {
  const windows = resolvedWindows(tool);
  const bottleneck = tightestWindow(tool);
  const percent = Number.isFinite(bottleneck?.pct) ? Math.round(bottleneck.pct) : null;
  document.querySelector(`#${prefix}DetailPercent`).textContent = percent === null ? "--" : `${percent}%`;

  const windowList = document.querySelector(`#${prefix}Windows`);
  windowList.replaceChildren(...windows.map((item) => {
    const row = document.createElement("div");
    row.className = "window-row";

    const label = document.createElement("span");
    label.textContent = item.label || "额度窗口";
    const track = document.createElement("span");
    track.className = "detail-track";
    const fill = document.createElement("span");
    fill.style.width = `${clamp(item.pct, 0, 100)}%`;
    track.append(fill);
    const value = document.createElement("strong");
    value.textContent = `${Math.round(item.pct)}%`;
    const reset = document.createElement("time");
    reset.textContent = countdown(item);
    row.append(label, track, value, reset);
    return row;
  }));

  const stats = [
    ["今日", tool?.today],
    ["近 7 天", tool?.week],
    ["本月", tool?.month],
  ];
  document.querySelector(`#${prefix}Stats`).replaceChildren(...stats.map(([labelText, value]) => {
    const wrapper = document.createElement("div");
    wrapper.className = "usage-stat";
    const label = document.createElement("dt");
    label.textContent = labelText;
    const number = document.createElement("dd");
    number.textContent = compactTokens(value);
    wrapper.append(label, number);
    return wrapper;
  }));

  const trend = Array.isArray(tool?.trend) ? tool.trend.slice(-14) : [];
  const maxTokens = Math.max(1, ...trend.map((item) => Number(item.tokens) || 0));
  const trendChart = document.querySelector(`#${prefix}Trend`);
  trendChart.style.gridTemplateColumns = `repeat(${Math.max(1, trend.length)}, minmax(4px, 1fr))`;
  trendChart.replaceChildren(...trend.map((item) => {
    const bar = document.createElement("span");
    bar.className = "trend-bar";
    bar.title = `${item.label || ""} · ${compactTokens(Number(item.tokens) || 0)}`;
    const fill = document.createElement("span");
    fill.style.height = `${Math.max(2, ((Number(item.tokens) || 0) / maxTokens) * 100)}%`;
    bar.append(fill);
    return bar;
  }));

  const projects = Array.isArray(tool?.projects) ? tool.projects.slice(0, 4) : [];
  const projectList = document.querySelector(`#${prefix}Projects`);
  if (projects.length === 0) {
    const empty = document.createElement("span");
    empty.className = "empty-detail";
    empty.textContent = "暂无项目统计";
    projectList.replaceChildren(empty);
  } else {
    projectList.replaceChildren(...projects.map((item) => {
      const row = document.createElement("div");
      row.className = "project-row";
      const name = document.createElement("span");
      name.textContent = item.name || "未命名项目";
      const value = document.createElement("span");
      value.textContent = compactTokens(Number(item.tokens));
      row.append(name, value);
      return row;
    }));
  }
}

function renderBalance(data) {
  balanceData = data;
  renderToolDetail("codex", data?.codex || {});
  renderToolDetail("claude", data?.claude || {});
  const rawTime = Number(data?.updatedAt);
  const timestamp = rawTime > 0 ? (rawTime < 1_000_000_000_000 ? rawTime * 1000 : rawTime) : Date.now();
  document.querySelector("#balanceUpdatedAt").textContent = new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(timestamp));
}

function characterSpans(text) {
  return [...(text || "")].map((character) => {
    const span = document.createElement("span");
    span.className = "poem-character";
    span.textContent = character;
    return span;
  });
}

function poemSegment(text) {
  const segment = document.createElement("div");
  segment.className = "poem-segment";
  segment.append(...characterSpans(text));
  return segment;
}

function seal() {
  const image = document.createElement("img");
  image.className = "poem-seal";
  image.src = "/assets/poem-seal.png";
  image.alt = "诗鉴印章";
  return image;
}

function renderPoemColumns(leftElement, rightElement, lines) {
  const safeLines = Array.from({ length: 4 }, (_, index) => lines?.[index] || "");
  leftElement.replaceChildren(
    poemSegment(safeLines[2].slice(2)),
    poemSegment(safeLines[3]),
    seal(),
  );
  rightElement.replaceChildren(
    poemSegment(safeLines[0]),
    poemSegment(safeLines[1]),
    poemSegment(safeLines[2].slice(0, 2)),
  );
}

function formatEvidence(data) {
  const frames = data?.evidence?.frameTimes;
  const parts = [];
  if (Array.isArray(frames) && frames.length > 0) {
    parts.push(`连续 ${frames.length} 帧云图`);
    parts.push(`${frames[0]} 至 ${frames.at(-1)}`);
  } else {
    parts.push("卫星云图");
  }
  if (data?.evidence?.typhoon) {
    const typhoon = data.evidence.typhoon;
    parts.push(typhoon.name || typhoon.source || "权威台风快讯");
  }
  return parts.join(" · ");
}

function renderPoem(data) {
  poemData = data;
  renderPoemColumns(
    document.querySelector("#poemLeft"),
    document.querySelector("#poemRight"),
    data.lines,
  );
  renderPoemColumns(
    document.querySelector("#analysisPoemLeft"),
    document.querySelector("#analysisPoemRight"),
    data.lines,
  );

  const analysis = data.analysis || {};
  document.querySelector("#poemTitle").textContent = data.title || "云图诗鉴";
  document.querySelector("#analysisDate").textContent = (data.poemDateShanghai || "").replaceAll("-", ".");
  document.querySelector("#confidence").textContent = analysis.confidence || "--";
  document.querySelector("#phenomenon").textContent = analysis.phenomenon || "暂无判读";
  document.querySelector("#trend").textContent = analysis.trend || "暂无判读";
  document.querySelector("#localImpact").textContent = analysis.localImpact || "暂无判读";
  document.querySelector("#summary").textContent = analysis.summary || "暂无判读";
  document.querySelector("#worldEcho").textContent = analysis.worldEcho || "暂无判读";
  document.querySelector("#evidenceLine").textContent = formatEvidence(data);
}

function setFont(font) {
  currentFont = font === "kai" ? "kai" : "xing";
  localStorage.setItem("cloud-poem-font", currentFont);
  [poemSlip, analysisPoem].forEach((element) => {
    element.classList.toggle("font-xing", currentFont === "xing");
    element.classList.toggle("font-kai", currentFont === "kai");
  });
  document.querySelectorAll("[data-font]").forEach((button) => {
    button.setAttribute("aria-pressed", String(button.dataset.font === currentFont));
  });
}

function openAnalysis() {
  closeBalance(false);
  analysisPanel.classList.add("is-open");
  analysisPanel.setAttribute("aria-hidden", "false");
  document.querySelector("#closeAnalysis").focus({ preventScroll: true });
}

function openBalance() {
  if (balanceData) {
    renderBalance(balanceData);
  }
  analysisPanel.classList.remove("is-open");
  analysisPanel.setAttribute("aria-hidden", "true");
  balancePanel.classList.add("is-open");
  balancePanel.setAttribute("aria-hidden", "false");
  document.querySelector("#closeBalance").focus({ preventScroll: true });
}

function closeBalance(restoreFocus = true) {
  balancePanel.classList.remove("is-open");
  balancePanel.setAttribute("aria-hidden", "true");
  if (restoreFocus) {
    balanceMeter.focus({ preventScroll: true });
  }
}

function closeAnalysis() {
  analysisPanel.classList.remove("is-open");
  analysisPanel.setAttribute("aria-hidden", "true");
  poemSlip.focus({ preventScroll: true });
}

async function loadData({ quiet = false } = {}) {
  if (!quiet) {
    statusLine.textContent = "正在更新云图与今日诗鉴…";
  }
  cloudImage.src = `/api/cloud?t=${Date.now()}`;
  const results = await Promise.allSettled([
    fetch("/api/poem", { cache: "no-store" }).then((response) => {
      if (!response.ok) throw new Error("诗鉴数据读取失败");
      return response.json();
    }),
    fetch("/api/balance", { cache: "no-store" }).then((response) => {
      if (!response.ok) throw new Error("码表数据读取失败");
      return response.json();
    }),
  ]);

  if (results[0].status === "fulfilled") {
    renderPoem(results[0].value);
  }
  if (results[1].status === "fulfilled") {
    updateMeter("codex", results[1].value.codex);
    updateMeter("claude", results[1].value.claude);
    renderBalance(results[1].value);
  }

  const errors = results.filter((result) => result.status === "rejected");
  statusLine.textContent = errors.length > 0 ? errors.map((result) => result.reason.message).join("；") : "";
}

async function loadMeta() {
  try {
    const response = await fetch("/meta.json", { cache: "no-store" });
    if (!response.ok) return;
    const meta = await response.json();
    document.querySelector("#dataNotice").textContent = meta.mode === "demo" ? meta.notice : "";
  } catch {
    // Metadata is optional when the UI is served by another compatible backend.
  }
}

function restorePoemPosition() {
  try {
    const saved = JSON.parse(localStorage.getItem("cloud-poem-position"));
    if (!Number.isFinite(saved?.x) || !Number.isFinite(saved?.y)) {
      return;
    }
    const maxX = Math.max(0, window.innerWidth - poemSlip.offsetWidth);
    const maxY = Math.max(0, window.innerHeight - poemSlip.offsetHeight);
    poemSlip.style.left = `${clamp(saved.x, 0, maxX)}px`;
    poemSlip.style.top = `${clamp(saved.y, 0, maxY)}px`;
    poemSlip.style.right = "auto";
  } catch {
    localStorage.removeItem("cloud-poem-position");
  }
}

poemSlip.addEventListener("pointerdown", (event) => {
  const rect = poemSlip.getBoundingClientRect();
  dragState = {
    pointerId: event.pointerId,
    startX: event.clientX,
    startY: event.clientY,
    left: rect.left,
    top: rect.top,
    moved: false,
  };
  poemSlip.setPointerCapture(event.pointerId);
});

poemSlip.addEventListener("pointermove", (event) => {
  if (!dragState || event.pointerId !== dragState.pointerId) {
    return;
  }
  const deltaX = event.clientX - dragState.startX;
  const deltaY = event.clientY - dragState.startY;
  if (Math.hypot(deltaX, deltaY) > 4) {
    dragState.moved = true;
    poemSlip.classList.add("is-dragging");
  }
  if (!dragState.moved) {
    return;
  }
  const maxX = Math.max(0, window.innerWidth - poemSlip.offsetWidth);
  const maxY = Math.max(0, window.innerHeight - poemSlip.offsetHeight);
  poemSlip.style.left = `${clamp(dragState.left + deltaX, 0, maxX)}px`;
  poemSlip.style.top = `${clamp(dragState.top + deltaY, 0, maxY)}px`;
  poemSlip.style.right = "auto";
});

poemSlip.addEventListener("pointerup", (event) => {
  if (!dragState || event.pointerId !== dragState.pointerId) {
    return;
  }
  poemSlip.releasePointerCapture(event.pointerId);
  poemSlip.classList.remove("is-dragging");
  if (dragState.moved) {
    const rect = poemSlip.getBoundingClientRect();
    localStorage.setItem("cloud-poem-position", JSON.stringify({ x: rect.left, y: rect.top }));
  } else {
    openAnalysis();
  }
  dragState = null;
});

poemSlip.addEventListener("keydown", (event) => {
  if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    openAnalysis();
  }
});

document.querySelector("#closeAnalysis").addEventListener("click", closeAnalysis);
document.querySelector("#closeBalance").addEventListener("click", () => closeBalance());
document.querySelector("#refreshButton").addEventListener("click", () => loadData());
document.querySelector("#fullscreenButton").addEventListener("click", async () => {
  if (document.fullscreenElement) {
    await document.exitFullscreen();
  } else {
    await document.documentElement.requestFullscreen();
  }
});

document.querySelectorAll("[data-font]").forEach((button) => {
  button.addEventListener("click", () => setFont(button.dataset.font));
});

balanceMeter.addEventListener("click", openBalance);
balanceMeter.addEventListener("keydown", (event) => {
  if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    openBalance();
  }
});

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    if (balancePanel.classList.contains("is-open")) {
      closeBalance();
    } else if (analysisPanel.classList.contains("is-open")) {
      closeAnalysis();
    }
  }
});

window.addEventListener("resize", restorePoemPosition);
cloudImage.addEventListener("error", () => {
  statusLine.textContent = "云图读取失败，请确认本机云图服务正在运行。";
});

renderPoem(fallbackPoem);
setFont(currentFont);
requestAnimationFrame(restorePoemPosition);
loadData();
loadMeta();
setInterval(() => loadData({ quiet: true }), 15 * 60 * 1000);

import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { extname, isAbsolute, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const repositoryRoot = resolve(fileURLToPath(new URL("..", import.meta.url)));
const webRoot = resolve(repositoryRoot, "apps/web");
const demoRoot = resolve(repositoryRoot, "demo");
const upstream = process.env.YUNHAI_UPSTREAM_URL?.replace(/\/$/, "") || "";
const listenHost = process.env.HOST || "127.0.0.1";
const port = Number(process.env.PORT || 8790);

const contentTypes = {
  ".css": "text/css; charset=utf-8",
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".ttf": "font/ttf",
};

const assets = new Map([
  ["/assets/poem-seal.png", resolve(repositoryRoot, "apps/android/res/drawable-nodpi/poem_seal.png")],
  ["/assets/ma-shan-zheng.ttf", resolve(repositoryRoot, "apps/android/res/font/ma_shan_zheng_regular.ttf")],
  ["/assets/zhi-mang-xing.ttf", resolve(repositoryRoot, "apps/android/res/font/zhi_mang_xing_regular.ttf")],
]);

function localPath(value, fallback) {
  if (!value) return fallback;
  return isAbsolute(value) ? value : resolve(repositoryRoot, value);
}

function send(res, status, body, type, headOnly = false, cache = "no-store") {
  const content = Buffer.isBuffer(body) ? body : Buffer.from(String(body));
  res.writeHead(status, {
    "Content-Type": type,
    "Content-Length": content.length,
    "Cache-Control": cache,
    "X-Content-Type-Options": "nosniff",
    "X-Yunhai-Mode": upstream ? "upstream" : "demo",
  });
  res.end(headOnly ? undefined : content);
}

async function sendFile(res, path, headOnly = false, cache = "no-cache") {
  const body = await readFile(path);
  send(res, 200, body, contentTypes[extname(path)] || "application/octet-stream", headOnly, cache);
}

async function proxy(res, path, headOnly) {
  const response = await fetch(`${upstream}${path}`, {
    headers: { Accept: "*/*", "User-Agent": "YunhaiShijian/1.0" },
    signal: AbortSignal.timeout(15000),
  });
  if (!response.ok) throw new Error(`上游服务返回 ${response.status}`);
  const body = Buffer.from(await response.arrayBuffer());
  send(res, 200, body, response.headers.get("content-type") || "application/octet-stream", headOnly);
}

function normalizeDemoBalance(data) {
  const nowSeconds = Math.floor(Date.now() / 1000);
  const normalized = structuredClone(data);
  for (const tool of Object.values(normalized)) {
    if (!tool || typeof tool !== "object") continue;
    if (Array.isArray(tool.windows)) {
      tool.windows = tool.windows.map((windowData) => ({
        ...windowData,
        reset: nowSeconds + Number(windowData.resetAfterMinutes || 0) * 60,
        resetAfterMinutes: undefined,
      }));
      const first = tool.windows[0];
      const second = tool.windows[1];
      if (first) {
        tool.p5 = first.pct;
        tool.r5 = first.reset;
      }
      if (second) {
        tool.p7 = second.pct;
        tool.r7 = second.reset;
      }
    }
  }
  normalized.updatedAt = Date.now();
  return normalized;
}

async function sendDemoJson(res, path, headOnly, normalize = false) {
  const data = JSON.parse(await readFile(path, "utf8"));
  const body = JSON.stringify(normalize ? normalizeDemoBalance(data) : data);
  send(res, 200, body, "application/json; charset=utf-8", headOnly);
}

async function sendCloud(res, headOnly) {
  const configured = process.env.YUNHAI_CLOUD_IMAGE;
  if (configured && /^https?:\/\//i.test(configured)) {
    const response = await fetch(configured, { signal: AbortSignal.timeout(15000) });
    if (!response.ok) throw new Error(`云图地址返回 ${response.status}`);
    const body = Buffer.from(await response.arrayBuffer());
    send(res, 200, body, response.headers.get("content-type") || "image/jpeg", headOnly);
    return;
  }
  const file = localPath(configured, resolve(demoRoot, "cloud-synthetic.jpg"));
  await sendFile(res, file, headOnly, "no-cache");
}

const server = createServer(async (req, res) => {
  try {
    const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
    const headOnly = req.method === "HEAD";
    if (req.method !== "GET" && !headOnly) {
      send(res, 405, "Method not allowed", "text/plain; charset=utf-8", headOnly);
      return;
    }

    if (url.pathname === "/health") {
      send(res, 200, JSON.stringify({ ok: true, mode: upstream ? "upstream" : "demo" }), "application/json; charset=utf-8", headOnly);
      return;
    }
    if (url.pathname === "/meta.json") {
      send(res, 200, JSON.stringify({
        mode: upstream ? "upstream" : "demo",
        notice: upstream ? "数据由自定义上游提供" : "合成演示图，非实时气象资料",
      }), "application/json; charset=utf-8", headOnly);
      return;
    }

    if (url.pathname === "/api/cloud" || url.pathname === "/image.jpg") {
      if (upstream) await proxy(res, "/image.jpg", headOnly);
      else await sendCloud(res, headOnly);
      return;
    }
    if (url.pathname === "/api/poem" || url.pathname === "/cloud-poem.json") {
      if (upstream) await proxy(res, "/cloud-poem.json", headOnly);
      else await sendDemoJson(res, localPath(process.env.YUNHAI_POEM_FILE, resolve(demoRoot, "poem.json")), headOnly);
      return;
    }
    if (url.pathname === "/api/balance" || url.pathname === "/balance.json") {
      if (upstream) await proxy(res, "/balance.json", headOnly);
      else await sendDemoJson(res, localPath(process.env.YUNHAI_BALANCE_FILE, resolve(demoRoot, "balance.json")), headOnly, true);
      return;
    }
    if (assets.has(url.pathname)) {
      await sendFile(res, assets.get(url.pathname), headOnly, "public, max-age=86400");
      return;
    }

    const relative = url.pathname === "/" ? "index.html" : decodeURIComponent(url.pathname.slice(1));
    const file = resolve(webRoot, relative);
    if (file !== webRoot && !file.startsWith(`${webRoot}${sep}`)) {
      send(res, 403, "Forbidden", "text/plain; charset=utf-8", headOnly);
      return;
    }
    await sendFile(res, file, headOnly);
  } catch (error) {
    const status = error?.code === "ENOENT" ? 404 : 502;
    send(res, status, JSON.stringify({ error: error.message }), "application/json; charset=utf-8", req.method === "HEAD");
  }
});

server.listen(port, listenHost, () => {
  console.log(`云海诗鉴：http://${listenHost}:${port}`);
  console.log(upstream ? `上游数据：${upstream}` : "演示模式：合成云图与示例码表");
});

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => server.close(() => process.exit(0)));
}

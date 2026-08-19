import { mkdir, readFile, writeFile } from "node:fs/promises";
import { extname, resolve } from "node:path";

const apiKey = process.env.OPENAI_API_KEY;
const model = process.env.OPENAI_MODEL || "gpt-5-mini";
const frameInputs = process.argv.slice(2);

if (!apiKey) {
  throw new Error("请先设置 OPENAI_API_KEY");
}
if (frameInputs.length < 2) {
  throw new Error("至少提供两帧云图：node backend/generate-poem.mjs frame-1.jpg frame-2.jpg ...");
}

async function asImageUrl(input) {
  if (/^https?:\/\//i.test(input)) return input;
  const path = resolve(input);
  const body = await readFile(path);
  const mime = extname(path).toLowerCase() === ".png" ? "image/png" : "image/jpeg";
  return `data:${mime};base64,${body.toString("base64")}`;
}

function extractOutputText(response) {
  for (const item of response.output || []) {
    for (const content of item.content || []) {
      if (content.type === "output_text" && content.text) return content.text;
    }
  }
  throw new Error("模型响应中没有 output_text");
}

const context = process.env.YUNHAI_CONTEXT_FILE
  ? await readFile(resolve(process.env.YUNHAI_CONTEXT_FILE), "utf8")
  : "未提供权威台风或预警快讯。不得仅凭图像给热带气旋命名。";

const images = await Promise.all(frameInputs.map(asImageUrl));
const userContent = [
  {
    type: "input_text",
    text: `以下是按时间顺序排列的连续云图。请像审慎的气象分析员一样比较云系移动和组织变化，再写一首五言绝句。\n\n外部核对信息：\n${context}`,
  },
  ...images.map((imageUrl) => ({ type: "input_image", image_url: imageUrl, detail: "high" })),
];

const schema = {
  type: "object",
  additionalProperties: false,
  required: ["title", "lines", "analysis"],
  properties: {
    title: { type: "string" },
    lines: {
      type: "array",
      minItems: 4,
      maxItems: 4,
      items: { type: "string" },
    },
    analysis: {
      type: "object",
      additionalProperties: false,
      required: ["phenomenon", "trend", "summary", "localImpact", "worldEcho", "confidence"],
      properties: {
        phenomenon: { type: "string" },
        trend: { type: "string" },
        summary: { type: "string" },
        localImpact: { type: "string" },
        worldEcho: { type: "string" },
        confidence: { type: "string", enum: ["低", "中", "高"] },
      },
    },
  },
};

const response = await fetch("https://api.openai.com/v1/responses", {
  method: "POST",
  headers: {
    Authorization: `Bearer ${apiKey}`,
    "Content-Type": "application/json",
  },
  body: JSON.stringify({
    model,
    input: [
      {
        role: "system",
        content: [{
          type: "input_text",
          text: "你负责多帧云图判读与中文古诗创作。区分图像观察、外部权威事实和推测。台风名称必须由外部权威信息确认。每句诗必须恰好五个汉字，不加标点。不得把 AI 结论写成官方预报或预警。",
        }],
      },
      { role: "user", content: userContent },
    ],
    text: {
      format: {
        type: "json_schema",
        name: "cloud_poem",
        strict: true,
        schema,
      },
    },
  }),
  signal: AbortSignal.timeout(120000),
});

if (!response.ok) {
  throw new Error(`OpenAI API ${response.status}: ${await response.text()}`);
}

const result = JSON.parse(extractOutputText(await response.json()));
const fiveChineseCharacters = /^\p{Script=Han}{5}$/u;
if (!result.lines.every((line) => fiveChineseCharacters.test(line))) {
  throw new Error(`模型没有返回严格五言诗：${JSON.stringify(result.lines)}`);
}

const now = new Date();
const output = {
  ...result,
  source: "openai",
  evidence: {
    frameTimes: frameInputs.map((input, index) => `帧 ${index + 1} · ${input}`),
    typhoon: null,
  },
  poemDateShanghai: new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(now),
  generatedAt: now.toISOString(),
};

const outputPath = resolve(process.env.YUNHAI_POEM_OUTPUT || "runtime/poem.json");
await mkdir(resolve(outputPath, ".."), { recursive: true });
await writeFile(outputPath, `${JSON.stringify(output, null, 2)}\n`, "utf8");
console.log(outputPath);

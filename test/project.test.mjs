import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { spawnSync } from "node:child_process";
import { test } from "node:test";

const poem = JSON.parse(await readFile(new URL("../demo/poem.json", import.meta.url), "utf8"));
const balance = JSON.parse(await readFile(new URL("../demo/balance.json", import.meta.url), "utf8"));

test("demo poem is a strict four-line five-character poem", () => {
  assert.equal(poem.lines.length, 4);
  for (const line of poem.lines) {
    assert.match(line, /^\p{Script=Han}{5}$/u);
  }
});
test("demo balance exposes Codex and Claude windows", () => {
  for (const key of ["codex", "claude"]) {
    assert.ok(Array.isArray(balance[key].windows));
    assert.ok(balance[key].windows.length >= 2);
    assert.ok(balance[key].windows.every((item) => item.pct >= 0 && item.pct <= 100));
  }
});

test("browser and server JavaScript parse", () => {
  for (const path of ["apps/web/app.js", "backend/server.mjs", "backend/generate-poem.mjs"]) {
    const result = spawnSync(process.execPath, ["--check", path], { encoding: "utf8" });
    assert.equal(result.status, 0, `${path}: ${result.stderr}`);
  }
});

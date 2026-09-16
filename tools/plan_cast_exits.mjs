#!/usr/bin/env node
/**
 * plan_cast_exits.mjs —— 常驻立绘清理：算出「谁该在哪一行退场」，并生成干跑报告
 *
 * 背景（见 docs/ds-adventrue/常驻立绘清理-干跑报告.md）：
 *   剧本里 @enter 写了 242 次、@exit 只写了 16 次，而编译器换场景也不清场
 *   （build_story.mjs 的 case "scene" 只改背景/幕题/CG），于是立绘只进不出：
 *   平均同台从第1章 1.9 人一路涨到第8章 7.7 人，终章恒定 7 人，其中 5 人整章零台词。
 *
 * 规则（= 需求定稿里的 A3：段落边界清场 + 段内滚动窗口退场）
 *   1) 进入新 @label（剧情段落）：把上一段遗留的、在**本段没有台词**的角色清掉
 *   2) 段内：某角色连续 K 拍没说话就退场（默认 K=12）
 *   3) 前瞻保护：未来 P 拍内要说话的角色绝不清（默认 P=2，避免把下一句的说话人赶走）
 *   4) 显式 @enter/@exit 优先：剧本自己写了 exit 的地方不动
 *
 * 用法
 *   node tools/plan_cast_exits.mjs                    # 干跑报告（不写剧本）
 *   node tools/plan_cast_exits.mjs --k=8 --p=2        # 调窗口/前瞻
 *   node tools/plan_cast_exits.mjs --markdown=out.md  # 报告写成 markdown
 *   node tools/plan_cast_exits.mjs --apply            # 落盘（写入 @exit 行；git 可回滚）
 */

import fs from "node:fs";
import path from "node:path";
import url from "node:url";

const HERE = path.dirname(url.fileURLToPath(import.meta.url));
const ROOT = path.dirname(HERE);
const SCRIPT_DIR = path.join(ROOT, "docs", "ds-adventrue", "剧本");
const BUILDER = path.join(HERE, "build_story.mjs");

const arg = (name, def) => {
  const hit = process.argv.find((a) => a.startsWith(`--${name}=`));
  return hit ? hit.slice(name.length + 3) : def;
};
const has = (name) => process.argv.includes(`--${name}`);
const K = Number(arg("k", 12));
const P = Number(arg("p", 2));
const APPLY = has("apply");
const MD = arg("markdown", "");

/** 章节顺序直接从编译器里读，避免两边不一致 */
function chapters() {
  const src = fs.readFileSync(BUILDER, "utf8");
  const m = src.match(/const CHAPTERS = \[([\s\S]*?)\];/);
  if (!m) throw new Error("build_story.mjs 里找不到 CHAPTERS");
  return [...m[1].matchAll(/"([^"]+\.txt)"/g)].map((x) => x[1]);
}

const SPEAKER = /^([A-Za-z0-9_\u4e00-\u9fa5]+):\s*\S/;
const SKIP_SPEAKERS = new Set(["narr", "st", "goto"]);

/** 说话人：一行台词属于谁（旁白/横幅不算角色） */
const speakerOf = (line) => {
  const m = line.match(SPEAKER);
  return m && !SKIP_SPEAKERS.has(m[1]) ? m[1] : "";
};

/** 每条台词/指令推进"拍"的时刻：编译器里说话人一变就拆拍，指令也会 flush */
const beatsOn = (line) => {
  const t = line.trim();
  if (!t) return false;
  if (SPEAKER.test(t)) return true;                 // 台词行（换人即新拍）
  if (t.startsWith("@")) return true;               // 任何指令都会 flush 出本拍
  if (t.startsWith("st:") || t.startsWith("goto ") || t.startsWith("#")) return false;
  return false;
};

function analyze(K, P) {
  const files = chapters();
  const chaptersReport = [];
  const afterCurve = new Map();
  const cast = new Map();          // role -> 最后一次活跃的拍号
  let beat = 0;
  let lastSpeaker = "";
  let inserts = [];                // {file, line, text, role, why}

  for (const f of files) {
    const raw = fs.readFileSync(path.join(SCRIPT_DIR, f), "utf8");
    const eol = raw.includes("\r\n") ? "\r\n" : "\n";
    const lines = raw.split(/\r?\n/);
    const sizes = [];
    // 每个角色在未来哪几拍会说话（前瞻保护用）
    const speakBeats = new Map();
    {
      let b = beat, sp = lastSpeaker;
      for (const l of lines) {
        const t = l.trim();
        const s = speakerOf(t);
        if (beatsOn(t) && s !== sp) { b++; sp = s; }
        if (s) {
          if (!speakBeats.has(s)) speakBeats.set(s, []);
          speakBeats.get(s).push(b);
        }
      }
    }
    const speaksLater = (role, from, within) =>
      (speakBeats.get(role) || []).some((x) => x > from && x <= from + within);

    const labelRows = [];
    let curLabel = "(章首)";
    let inherited = new Map();

    lines.forEach((line, i) => {
      const t = line.trim();
      if (!t) return;

      if (t.startsWith("@label ")) {
        const next = t.slice(7).trim();
        // 段落边界：进入本段时，把"整段都不会说话"的遗留角色清掉
        const drop = [...cast.keys()].filter((r) => !speaksLater(r, beat, Number.MAX_SAFE_INTEGER));
        curLabel = next;
        inherited = new Map(cast);
        labelRows.push({
          label: next, inherited: [...cast.keys()],
          drop: drop.map((r) => ({ role: r, why: "本段无台词" })),
        });
        for (const r of drop) {
          inserts.push({ file: f, line: i, text: `@exit ${r}`, role: r, why: "label" });
          cast.delete(r);
        }
        beat++;
        return;
      }

      const s = speakerOf(t);
      if (beatsOn(t) && s !== lastSpeaker) { beat++; lastSpeaker = s; }
      if (s) cast.set(s, beat);
      if (SPEAKER.test(t)) sizes.push(cast.size);

      if (t.startsWith("@enter ")) {
        const role = t.slice(7).trim().split(/\s+/)[0];
        cast.set(role, beat);
        return;
      }
      if (t.startsWith("@exit ")) {
        cast.delete(t.slice(6).trim());
        return;
      }

      // 段内滚动窗口：连续 K 拍没说话 → 退场（前瞻保护）
      for (const [role, last] of [...cast.entries()]) {
        if (role === s) continue;
        if (beat - last <= K) continue;
        if (speaksLater(role, beat, P)) continue;      // 马上要说话，留着
        inserts.push({ file: f, line: i, text: `@exit ${role}`, role, why: "window" });
        cast.delete(role);
      }
    });

    labelRows.push({
      label: `${curLabel}（章末）`, inherited: [...cast.keys()],
      drop: [], final: [...cast.keys()],
    });
    chaptersReport.push({ file: f, rows: labelRows, eol });
    afterCurve.set(f, {
      mean: sizes.length ? sizes.reduce((a, b) => a + b, 0) / sizes.length : 0,
      peak: sizes.length ? Math.max(...sizes) : 0,
    });
  }
  return { chaptersReport, inserts, afterCurve };
}

/** 编译产物实测（现状基准）：按章节前缀统计每拍同台立绘数 */
function mapTruth() {
  const mapFile = path.join(ROOT, "maps", "story", "scenario.txt");
  const out = new Map();
  if (!fs.existsSync(mapFile)) return out;
  // 章节前缀 → 文件（取每章第一个 @label 的前缀）
  const prefixToFile = new Map();
  for (const f of chapters()) {
    const lines = fs.readFileSync(path.join(SCRIPT_DIR, f), "utf8").split(/\r?\n/);
    const lab = lines.map((l) => l.trim()).find((l) => l.startsWith("@label "));
    if (lab) prefixToFile.set(lab.slice(7).trim().replace(/_.*$/, ""), f);
  }
  const byFile = new Map();
  let cur = null;
  for (const l of fs.readFileSync(mapFile, "utf8").split(/\r?\n/)) {
    const sm = l.match(/^\[(.+)\]\s*$/);
    if (sm && sm[1] !== "option") {
      const prefix = sm[1].replace(/__.*$/, "").replace(/_.*$/, "");
      const file = prefixToFile.get(prefix);
      cur = file ? { file, n: 0 } : null;
      if (cur && !byFile.has(file)) byFile.set(file, []);
      if (cur) byFile.get(file).push(cur);
      continue;
    }
    if (cur && /^id = char_/.test(l)) cur.n++;
  }
  for (const [file, rows] of byFile) {
    const ns = rows.map((r) => r.n);
    out.set(file.replace(/\.txt$/, ""), {
      mean: ns.length ? ns.reduce((a, b) => a + b, 0) / ns.length : 0,
      peak: ns.length ? Math.max(...ns) : 0,
    });
  }
  return out;
}

/** 现状基线：立绘从不清场时，每章结尾的在场人数（用 @enter/@exit 原样模拟） */
function baselineCurve() {
  const files = chapters();
  const cast = new Set();
  const out = [];
  for (const f of files) {
    const lines = fs.readFileSync(path.join(SCRIPT_DIR, f), "utf8").split(/\r?\n/);
    let peak = 0, sum = 0, n = 0;
    for (const l of lines) {
      const t = l.trim();
      if (t.startsWith("@enter ")) cast.add(t.slice(7).trim().split(/\s+/)[0]);
      if (t.startsWith("@exit ")) cast.delete(t.slice(6).trim());
      if (SPEAKER.test(t)) { sum += cast.size; n++; peak = Math.max(peak, cast.size); }
    }
    out.push({ file: f, mean: n ? sum / n : 0, peak, end: cast.size });
  }
  return out;
}

const { chaptersReport, inserts, afterCurve } = analyze(K, P);
const base = baselineCurve();
const truth = mapTruth();

const fmt = (n) => (Math.round(n * 10) / 10).toFixed(1);
let md = [];
const say = (s) => { console.log(s); md.push(s); };

say(`# 常驻立绘清理 · 干跑报告`);
say("");
say(`规则：段落边界清场 + 段内 **K=${K} 拍**没说话退场 + 前瞻 **P=${P} 拍**保护；显式 @exit 优先。`);
say(`模式：${APPLY ? "**落盘**" : "干跑（不写剧本）"}`);
say("");
say(`## 一、现状（编译产物实测）vs 清理后（按同一规则推演）`);
say("");
say(`| 章 | 现状平均同台 | 现状峰值 | 清理后平均 | 清理后峰值 | 计划新增 @exit |`);
say(`|---|---|---|---|---|---|`);
for (let i = 0; i < base.length; i++) {
  const b = base[i];
  const t = truth.get(b.file.replace(/\.txt$/, "")) || { mean: 0, peak: 0 };
  const a = afterCurve.get(b.file) || { mean: 0, peak: 0 };
  const n = inserts.filter((x) => x.file === b.file).length;
  say(`| ${b.file.replace(/\.txt$/, "")} | ${fmt(t.mean)} | ${t.peak} | **${fmt(a.mean)}** | **${a.peak}** | ${n} |`);
}
const totalIns = inserts.length;
const byWhy = { label: inserts.filter((x) => x.why === "label").length, window: inserts.filter((x) => x.why === "window").length };
const tMean = [...truth.values()].reduce((a, b) => a + b.mean, 0) / Math.max(1, truth.size);
const aMean = [...afterCurve.values()].reduce((a, b) => a + b.mean, 0) / Math.max(1, afterCurve.size);
say("");
say(`**合计新增 @exit ${totalIns} 条**（段落边界 ${byWhy.label} 条 + 窗口超时 ${byWhy.window} 条）；`);
say(`全篇平均同台 **${fmt(tMean)} → ${fmt(aMean)}**。`);
say("");
say(`## 二、K 值（清场积极程度）敏感性`);
say("");
say(`| K（几拍没说话就退场） | 新增 @exit | 全篇平均同台 | 峰值最大章 |`);
say(`|---|---|---|---|`);
for (const k of [4, 6, 8, 12, 20, 999]) {
  const r = analyze(k, P);
  const m = [...r.afterCurve.values()].reduce((a, b) => a + b.mean, 0) / Math.max(1, r.afterCurve.size);
  const peak = Math.max(...[...r.afterCurve.values()].map((x) => x.peak));
  say(`| ${k === 999 ? "不清（只清段落遗留）" : k} | ${r.inserts.length} | ${fmt(m)} | ${peak} |`);
}
say("");
say(`## 二、逐段明细（每个 @label 的在场 / 清理）`);
for (const c of chaptersReport) {
  say("");
  say(`### ${c.file.replace(/\.txt$/, "")}`);
  say("");
  say(`| 段落 | 进入时在场 | 本段清掉 |`);
  say(`|---|---|---|`);
  for (const r of c.rows) {
    const drop = r.drop.map((d) => `${d.role}（${d.why}）`).join("、");
    say(`| ${r.label} | ${r.inherited.join("、") || "—"} | ${drop || "—"} |`);
  }
}
say("");
const roles = new Map();
for (const i of inserts) roles.set(i.role, (roles.get(i.role) || 0) + 1);
say(`## 三、按角色统计（被清次数）`);
say("");
say([...roles.entries()].sort((a, b) => b[1] - a[1]).map(([r, n]) => `${r}×${n}`).join("  "));

if (MD) fs.writeFileSync(path.join(ROOT, MD), md.join("\n") + "\n", "utf8");

if (APPLY) {
  // 按文件、按行号倒序插入，保证行号不位移
  const byFile = new Map();
  for (const ins of inserts) {
    if (!byFile.has(ins.file)) byFile.set(ins.file, []);
    byFile.get(ins.file).push(ins);
  }
  for (const [f, list] of byFile) {
    const p = path.join(SCRIPT_DIR, f);
    const raw = fs.readFileSync(p, "utf8");
    const eol = raw.includes("\r\n") ? "\r\n" : "\n";
    const lines = raw.split(/\r?\n/);
    const grouped = new Map();
    for (const ins of list) {
      if (!grouped.has(ins.line)) grouped.set(ins.line, []);
      grouped.get(ins.line).push(ins);
    }
    for (const line of [...grouped.keys()].sort((a, b) => b - a)) {
      const texts = grouped.get(line).map((x) => x.text);
      lines.splice(line + 1, 0, ...texts);
    }
    fs.writeFileSync(p, lines.join(eol), "utf8");
    console.log(`  [写入] ${f}：+${list.length} 条 @exit`);
  }
  console.log(`\n已写入 ${totalIns} 条 @exit。回滚：git checkout -- "docs/ds-adventrue/剧本"`);
}

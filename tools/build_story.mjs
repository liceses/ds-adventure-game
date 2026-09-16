#!/usr/bin/env node
/**
 * 剧本 DSL → scenario.txt 编译器（试验批次：序章 / 第1章 / 第2章）
 *
 * 用法：
 *   node tools/build_story.mjs           # 生成 maps/story/scenario.txt
 *   node tools/build_story.mjs --check   # 生成到内存并与现有产物比对（不写盘）
 *
 * 设计要点（对齐引擎既有惯用法，见 maps/prologue_404）：
 *  - 一个 DSL「拍点」＝一个场景（micro-scene），用 dialog 的 target 串联推进；
 *  - 立绘换表情＝复用同一节点 id（char_<role>）只改 path，绝不叠图；
 *  - 每个拍点都重新声明舞台状态（背景 + 当前可见立绘），因为引擎按场景重建节点；
 *  - @flag / @save / goto 编译成「逻辑拍点」：只写 `slot = 场景进入 | …` 再 goto 下一拍；
 *  - *choice 编译成按钮 + 每个选项一个逻辑拍点（写 flag → goto）；
 *  - @minigame 编译成 `event = <id>` + `mg.*` 场景属性，结果路由由引擎读取（mg.onWin/mg.onLose）。
 *
 * 本批次只覆盖这三章用到的 DSL；遇到 @if / @ending 会直接报错退出（避免静默丢语义）。
 */
import fs from "node:fs";
import path from "node:path";

const ROOT = process.cwd();
const SCRIPT_DIR = path.join(ROOT, "docs", "ds-adventrue", "剧本");
const OUT_FILE = path.join(ROOT, "maps", "story", "scenario.txt");
const CHECK = process.argv.includes("--check");

const CHAPTERS = [
  "序章_404之夜.txt",
  "第1章_思维链大暴走.txt",
  "第2章_幻想乡弹幕异变.txt",
  "第3章_合鳞礼.txt",
  "第4章_防火墙拆迁办.txt",
  "第5章_上下文溢出.txt",
  "第6章_歌姬的曲库灾难.txt",
  "第7章_流言沼公关战.txt",
  "第8章_无主之物仓.txt",
  "第9章_决战万秤楼.txt",
  "终章_深度求索.txt",
];

/** 剧本里的游戏 id → 引擎插件 id（剧本不改，编译期映射） */
const GAME_ALIAS = { brick: "breakout", minesweep: "minesweeper" };
/** 引擎已注册的游戏插件（缺失的会在编译末尾列出，不静默） */
const GAME_KNOWN = new Set([
  "snake", "plane", "2048", "breakout", "memory", "minesweeper",
  "sokoban", "gomoku", "link", "savepanel", "audio", "add", "select",
]);
/** @ending 的合法 id（来源：剧情侧 §三 CG 对照表） */
const ENDING_NAME = {
  true: "真结局",
  local: "温情结局",
  temp: "隐藏结局",
  busy: "Bad End · 服务器繁忙",
  bad_collapse: "Bad End · 人设崩塌",
};
/** @if 分支用的临时链路变量（写入 @var(...)，名字会出现在存档里） */
const FLOW_VAR = "__flow_next";
/** 谓词映射：@if 的运算符 → Expr 谓词函数 */
const PRED = { ">=": "ge", ">": "gt", "<=": "le", "<": "lt", "==": "eq", "!=": "ne" };

// =====================================================================
// BGM 分配（程序侧按"类别语义"自动分配；剧情侧在剧本里写 @bgm / 场景 bgm: 即覆盖）
// 类别与曲库来自美术侧 assets/sounds/bgm/bgm_map.json（同类多首由引擎运行时随机取）
// =====================================================================
const BGM_MAP_FILE = path.join(ROOT, "src", "main", "resources", "assets", "sounds", "bgm", "bgm_map.json");
let BGM_CATS = new Set();
try {
  BGM_CATS = new Set(Object.keys(JSON.parse(fs.readFileSync(BGM_MAP_FILE, "utf8"))));
} catch (e) {
  console.warn("⚠ 读不到 bgm_map.json（" + e.message + "），BGM 分配跳过");
}
/** 章节 → 默认类别 */
const CHAPTER_BGM = {
  "序章": "explore", "第1章": "daily", "第2章": "explore", "第3章": "tower", "第4章": "tension",
  "第5章": "memory", "第6章": "funny", "第7章": "tension", "第8章": "local", "第9章": "tower",
  "终章": "ending",
};
/** 场景/标签关键词 → 类别（自上而下先命中先赢） */
const BGM_RULES = [
  { key: "chaos", cat: "funny" }, { key: "taunt", cat: "funny" },
  { key: "mem", cat: "memory" }, { key: "scale", cat: "scale" },
  { key: "wanzheng", cat: "celebration" }, { key: "wancheng", cat: "celebration" },
  { key: "gift", cat: "celebration" }, { key: "win", cat: "celebration" },
  { key: "collapse", cat: "bad" }, { key: "bad", cat: "bad" },
  { key: "end_", cat: "ending" }, { key: "local", cat: "local" },
  { key: "top", cat: "tower" }, { key: "tower", cat: "tower" },
];
/** 结局 id → 类别（与引擎 Bgm.categoryForEnding 一致） */
const ENDING_BGM = { true: "ending", temp: "ending", local: "warm", busy: "bad", bad_collapse: "bad" };

/** 某一拍应有的 BGM 类别（"" = 不变；"__stop" = 停） */
function bgmCategoryOf(b) {
  if (b.bgmStop) return "__stop";
  if (b.bgmExplicit) return b.bgmExplicit;
  if (b.kind === "minigame") return BGM_CATS.has("battle") ? "battle" : "";
  if (b.kind === "ending") return ENDING_BGM[b.endingId] || "";
  const key = ((b.scene || "") + " " + (b.label || "")).toLowerCase();
  for (const r of BGM_RULES) {
    if (key.includes(r.key) && BGM_CATS.has(r.cat)) return r.cat;
  }
  // 章节判定：优先用文件名前缀（第1章_…）；文件名为空时退回标签前缀（ch0_ / ch1_ / finale_ / wancheng）
  const file = b.chapter || "";
  let chapter = "";
  for (const k of Object.keys(CHAPTER_BGM)) {
    if (file.startsWith(k) && k.length > chapter.length) chapter = k;
  }
  if (!chapter) {
    const l = (b.label || "").toLowerCase();
    const m = l.match(/^ch([0-9])/);
    if (m) chapter = m[1] === "0" ? "序章" : "第" + m[1] + "章";
    else if (l.includes("finale") || l.includes("wancheng")) chapter = "终章";
  }
  const cat = CHAPTER_BGM[chapter];
  return cat && BGM_CATS.has(cat) ? cat : "";
}

/** 编译期问题收集（缺失目标 / 未知游戏 / 未知结局），最后统一报告 */
const problems = { missingTargets: new Set(), unknownGames: new Set(), unknownEndings: new Set() };
const SPRITES = path.join(ROOT, "src", "main", "resources", "assets", "sprites");

// ---- 剧本 id → 素材目录 / 表情别名（依据《剧情素材需求总表》与 archive_assets 归档结果）----
const ROLE_DIR = { qianwen: "qwen", 灯官: "dengguan", 契官: "qiguan", 戏官: "xiguan", 怪力: "gelili",
  皮卡丘: "pikachu", 秤主: "scale" };   // 秤主：素材目录名是 scale（5 个表情与剧本一致）
const EXPR_ALIAS = { cute: "happy", cry: "sad", whale_cute: "defect_happy", whale_cry: "defect_sad" };
// 背景临时顶替表（仅当同场景 id 的背景图尚未出图时兜底；素材到位后自动改用真图）
const BG_ALIAS = { server_room: "bg_tech_serverroom" };

// ---- 取景参数表（S3）----
// 立绘已由 tools/normalize_sprites.py 归一化到统一画布 1280×1536
// （身高 1460 / 脚线 y=1500 / 水平中心 x=640），所以「节点框 = 取景窗口」对每个角色都成立：
// 头顶线、腰线、脚线全篇一致，同框不会一头高一头低。改取景就改这几行（编辑器暂不改）。
const CANVAS = { w: 1280, h: 1536, charH: 1460, feetY: 1500 };   // 必须与 normalize_sprites.py 一致
/** 三档站位的「角色中心线」（不是节点左上角；节点 x 由它和框宽反推） */
const STATIONS = { left: 250, center: 640, right: 1030 };

// 同一拍里人数越多，能分给每个人的宽度越小 —— 实测剧本里 87% 的拍是 4 人以上同台
// （6 人 116 拍 / 7 人 278 拍 / 8 人 85 拍 / 11 人 63 拍），所以"半身"只对 1~2 人的对手戏生效，
// 人多时必须成档回收尺寸，否则重叠成一锅粥。
const FRAME_BY_CAST = [
  { max: 2, pose: "bust" },    // 1~2 人：头到腰（可见带 0..516），比旧的 213 宽放大约 2.2×
  { max: 3, pose: "mid" },     // 3 人：头到大腿
  { max: 5, pose: "small" },   // 4~5 人：头到膝
  { max: 99, pose: "crowd" },  // 6 人以上：全身（等于旧尺寸略大），保证铺得开
];
// s = 画布缩放；y = 节点 y（画布顶边对齐屏幕 y=0；立绘的头顶线在画布 y=40）
const FRAMES = {
  bust: { s: 0.75, y: 0 },
  mid: { s: 0.55, y: 0 },
  small: { s: 0.46, y: 0 },
  crowd: { s: 0.38, y: 0 },    // 群像（6 人以上，由 FRAME_BY_CAST 选中）
  full: { s: 0.469, y: 0 },    // 全身进画面（@pose full / @debut 用；腿会被对话框压住，卡拍里才完全可见）
};
for (const f of Object.values(FRAMES)) {
  f.w = Math.round(CANVAS.w * f.s);
  f.h = Math.round(CANVAS.h * f.s);
}
/** 本拍默认取景档（@pose 可按角色覆盖） */
const poseForCast = (n) => (FRAME_BY_CAST.find((r) => n <= r.max) || FRAME_BY_CAST[FRAME_BY_CAST.length - 1]).pose;
/** 站位 → 节点 x：由「角色中心线」反推（站位认不出时按 center） */
const frameX = (fr, pos) => Math.round((STATIONS[pos] !== undefined ? STATIONS[pos] : STATIONS.center) - fr.w / 2);
// 对话框 / 名牌坐标与"UI 皮肤"层：按美术侧《UI与小游戏接线规格》§2.1 的表值
const DIALOG_BOX = { x: 96, y: 448, w: 1090, h: 190 };   // 引擎文字落在这块可读区里
const BANNER = { x: 240, y: 250, w: 800, h: 160 };
const NAME = { x: 140, y: 404, w: 260, h: 40 };
/** UI 皮肤（char 节点，垫在文字之下；美术侧 §2.1 / §2.5） */
const UI_DIALOG = { x: 64, y: 430, w: 1152, h: 230, path: "assets/sprites/ui/dialog_box.png" };
const UI_NAMEPLATE = { x: 96, y: 392, w: 300, h: 62, path: "assets/sprites/ui/name_plate.png" };
const UI_VIGNETTE = { x: 0, y: 0, w: 1280, h: 720, path: "assets/sprites/ui/vignette.png" };

/**
 * 发 UI 皮肤层（放在 dialog / name 节点之前 → 引擎文字压在图上）。
 * 这些是 char 节点，会被引擎的 stage=keep 复用逻辑按 id 复用，跨拍不重建。
 */
function emitUiSkin(out, withNameplate) {
  out.push("{", "type = char", "id = ui_vignette", `x = ${UI_VIGNETTE.x}`, `y = ${UI_VIGNETTE.y}`,
    `width = ${UI_VIGNETTE.w}`, `height = ${UI_VIGNETTE.h}`, `path = ${UI_VIGNETTE.path}`,
    "opacity = 0.35", "}");
  // 必须用 bg（拉伸铺满）：char 是等比缩放居中，920×240 的底板塞进 1152×230 只画 881 宽，
  // 对话框文字会左右各超出可见图案约 100px（PR #18 指出）
  out.push("{", "type = bg", "id = ui_dialog", `x = ${UI_DIALOG.x}`, `y = ${UI_DIALOG.y}`,
    `width = ${UI_DIALOG.w}`, `height = ${UI_DIALOG.h}`, `path = ${UI_DIALOG.path}`,
    "opacity = 1.0", "transition = opacity/scale:150ms", "}");
  if (withNameplate) {
    out.push("{", "type = bg", "id = ui_nameplate", `x = ${UI_NAMEPLATE.x}`, `y = ${UI_NAMEPLATE.y}`,
      `width = ${UI_NAMEPLATE.w}`, `height = ${UI_NAMEPLATE.h}`, `path = ${UI_NAMEPLATE.path}`,
      "opacity = 1.0", "transition = x:300ms", "}");
  }
}

/** 角色 id → 名牌显示名（剧本里只有 id，这里给玩家看的名字） */
const DISPLAY_NAME = {
  ds: "ds娘", glm: "GLM娘", qianwen: "千问酱", kimi: "Kimi娘", "灯官": "灯官",
  "契官": "契官", "戏官": "戏官", sclerk: "司秤吏", snake_expert: "守鳞人",
  reimu: "博丽灵梦", bugs: "报幕虫", miku: "初音未来", amiya: "阿米娅",
  paimon: "派蒙", pikachu: "皮卡丘", creeper: "苦力怕", sai: "藤原佐为", "怪力": "怪力"
};

// =====================================================================
// 1) 解析 DSL
// =====================================================================
function parseScriptFile(file) {
  const full = path.join(SCRIPT_DIR, file);
  const lines = fs.readFileSync(full, "utf8").split(/\r?\n/);
  const out = [];
  let choiceOpen = false;
  lines.forEach((raw, i) => {
    const no = i + 1;
    const line = raw.trim();
    if (!line || line.startsWith("#")) return;
    const push = (kind, data) => out.push({ kind, file, line: no, ...data });

    if (line === "*choice") { choiceOpen = true; push("choiceStart", {}); return; }
    if (line.startsWith("> ")) {
      if (!choiceOpen) throw new Error(`${file}:${no} 选项行出现在 *choice 之外`);
      // > 文本 | flag <名> <值> | goto <标签>
      const parts = line.slice(2).split("|").map((s) => s.trim());
      const opt = { text: parts[0], flag: null, value: null, goto: null };
      for (const p of parts.slice(1)) {
        if (p.startsWith("flag ")) {
          const m = p.slice(5).trim().split(/\s+/);
          opt.flag = m[0];
          opt.value = m.slice(1).join(" ") || "got";
        } else if (p.startsWith("goto ")) {
          opt.goto = p.slice(5).trim();
        }
      }
      push("choice", { opt });
      return;
    }
    choiceOpen = false;

    if (line.startsWith("@label ")) return push("label", { id: line.slice(7).trim() });
    if (line.startsWith("@scene ")) {
      const rest = line.slice(7).trim();
      const m = rest.match(/^(\S+)([\s\S]*)$/);
      const id = m[1];
      const kv = {};
      for (const seg of m[2].split(/\s+(?=[a-zA-Z]+:)/)) {
        const idx = seg.indexOf(":");
        if (idx > 0) kv[seg.slice(0, idx).trim()] = seg.slice(idx + 1).trim();
      }
      return push("scene", { id, bg: kv.bg || "", bgm: kv.bgm || "", title: kv.title || "" });
    }
    if (line.startsWith("@enter ")) {
      const t = line.slice(7).trim().split(/\s+/);
      const pos = (t[2] || "").replace("pos:", "") || "center";
      // 第三个参数之后可以写 hold：该角色跨段落保留（豁免段落边界的兜底清场）
      const hold = t.slice(3).some((x) => x.toLowerCase() === "hold");
      return push("enter", { role: t[0], expr: t[1], pos, hold });
    }
    if (line.startsWith("@exit ")) return push("exit", { role: line.slice(6).trim() });
    if (line.startsWith("@flag ")) {
      const t = line.slice(6).trim().split(/\s+/);
      return push("flag", { name: t[0], op: t.slice(1).join(" ") || "got" });
    }
    if (line === "@save") return push("save", {});
    if (line.startsWith("@minigame ")) {
      const t = line.slice(10).trim().split(/\s+/);
      const mg = { id: t[0], mode: "normal", onWin: "", onLose: "", loop: "", with: "" };
      for (const a of t.slice(1)) {
        const idx = a.indexOf(":");
        if (idx <= 0) continue;
        const k = a.slice(0, idx), v = a.slice(idx + 1);
        if (k === "with") mg.with = v;
        else if (k in mg) mg[k] = v;
      }
      return push("minigame", { mg });
    }
    // ---- 演出指令（剧情侧 v1.0 新增，见《素材交接-剧情侧答复》§二）----
    if (line.startsWith("@cg ")) {
      const t = line.slice(4).trim().split(/\s+/);
      const mode = (t[1] || "flash").toLowerCase();
      return push("cg", { id: t[0], mode: ["flash", "hold", "clear"].includes(mode) ? mode : "flash" });
    }
    if (line.startsWith("@pose ")) {
      // @pose <角色> <取景档>：持久切换该角色的取景（bust 半身 / mid / small / crowd 群像 / full 全身）
      const t = line.slice(6).trim().split(/\s+/);
      const pose = (t[1] || "").toLowerCase();
      if (!(pose in FRAMES)) {
        throw new Error(`${file}:${no} @pose 取景档无法识别（可选 ${Object.keys(FRAMES).join(" / ")}）：${line}`);
      }
      return push("pose", { role: t[0], pose });
    }
    if (line.startsWith("@debut ")) {
      // @debut <角色>：该角色"登场卡拍"——无对话框的一拍全身亮相，点一下继续，之后回常态取景
      return push("debut", { role: line.slice(7).trim() });
    }
    if (line.startsWith("@se ")) {
      const t = line.slice(4).trim().split(/\s+/);
      const se = { id: t[0], vol: "" };
      for (const a of t.slice(1)) {
        const i = a.indexOf(":");
        if (i > 0 && a.slice(0, i) === "vol") se.vol = a.slice(i + 1);
      }
      return push("se", se);
    }
    if (line.startsWith("@bgm ")) {
      const t = line.slice(5).trim().split(/\s+/);
      const mode = (t[1] || "loop").toLowerCase();
      return push("bgm", { id: t[0], mode: ["loop", "stop", "fade"].includes(mode) ? mode : "loop" });
    }
    if (line.startsWith("goto ")) return push("goto", { target: line.slice(5).trim() });
    if (line.startsWith("narr:")) return push("dialog", { text: line });
    if (line.startsWith("st:")) return push("banner", { text: line.slice(3).trim() });
    // @if <名> <谓词> <值> goto <label>（单行平坦式，无 @else/@endif；假则顺延下一行）
    if (line.startsWith("@if ")) {
      const m = line.slice(4).trim()
        .match(/^([A-Za-z0-9_\u4e00-\u9fa5]+)\s*(>=|<=|==|!=|>|<)\s*(-?\d+)\s+goto\s+(\S+)$/);
      if (!m) {
        throw new Error(`${file}:${no} @if 写法无法识别（应为：@if <名> >= <值> goto <label>）：${line}`);
      }
      return push("if", { name: m[1], op: m[2], value: m[3], label: m[4] });
    }
    if (line === "@ending" || line.startsWith("@ending ")) {
      const id = line.slice(7).trim();
      if (!id) throw new Error(`${file}:${no} @ending 缺少结局 id：${line}`);
      return push("ending", { id });
    }
    if (/^@/.test(line)) throw new Error(`${file}:${no} 未知指令：${line}`);
    const m = line.match(/^([A-Za-z0-9_\u4e00-\u9fa5]+):\s*([\s\S]*)$/);
    if (m) return push("dialog", { text: line, speaker: m[1] });
    throw new Error(`${file}:${no} 无法识别的行：${line}`);
  });
  return out;
}

// =====================================================================
// 2) 素材解析
// =====================================================================
const exists = (p) => fs.existsSync(p);
function roleDir(role) { return ROLE_DIR[role] || role; }

/** 归一化 flag 增量：+1 / got → 1；=N → N；其余原样 */
function flagDelta(op) {
  if (op === "+1" || op === "got" || op === "+" || op === "") return "1";
  if (op.startsWith("=")) return op.slice(1);
  return op;
}

/** 立绘：返回相对路径（引擎按 classpath 解析 assets/sprites/**） */
function spritePath(role, expr) {
  const dir = roleDir(role);
  const base = path.join(SPRITES, dir);
  const cands = [];
  const alias = EXPR_ALIAS[expr];
  cands.push(path.join(base, `${expr}.png`));
  if (alias) cands.push(path.join(base, `${alias}.png`));
  cands.push(path.join(base, "official", `${expr}.png`));
  if (alias) cands.push(path.join(base, "official", `${alias}.png`));
  cands.push(path.join(base, "base.png"));
  for (const c of cands) {
    if (exists(c)) return "assets/sprites/" + path.relative(SPRITES, c).split(path.sep).join("/");
  }
  // 找不到：仍给出期望路径，由引擎画占位图（占位文字带角色/表情，便于发现缺图）
  return `assets/sprites/${dir}/${expr}.png`;
}

/** 系统横幅（14 条）文案 → 图片映射；按「独特关键词」匹配，避免标点微调导致失配 */
const BANNER_RULES = [
  { key: "小声", file: "sys_busy_soft" },            // 【（小声）请稍后再试……】——放前面，避免被"服务器繁忙"优先命中
  { key: "服务器繁忙", file: "sys_busy" },
  { key: "叮——秤崩簧", file: "sys_scale_break" },
  { key: "崩簧——秤不肯认", file: "sys_scale_break2" },
  { key: "思考中", file: "sys_thinking" },
  { key: "觉醒", file: "sys_awaken_r1" },
  { key: "合鳞礼成", file: "sys_merge" },
  { key: "九鳞归位", file: "sys_nine" },
  { key: "旗舰之力", file: "sys_v4pro" },
  { key: "深度求索", file: "sys_flash" },
  { key: "无主之物仓", file: "sys_warehouse" }
];
/** 章节变体（剧情侧 §五：第 4 章同文案改用 sys_scale_break2，避免视觉重复） */
const BANNER_CHAPTER_OVERRIDE = { "第4章": { "秤崩簧": "sys_scale_break2" } };
const BANNER_DIR = path.join(ROOT, "src", "main", "resources", "assets", "sprites", "ui", "banners");

/** 读出 PNG 的宽高（只解析 IHDR，无需图像库） */
function pngSize(file) {
  try {
    const fd = fs.openSync(file, "r");
    const buf = Buffer.alloc(24);
    fs.readSync(fd, buf, 0, 24, 0);
    fs.closeSync(fd);
    return { w: buf.readUInt32BE(16), h: buf.readUInt32BE(20) };
  } catch (e) {
    return null;
  }
}

/** 文案 → 横幅文件名；找不到映射返回 null（调用方回退文字横幅） */
function bannerFile(text, chapterKey) {
  const chapter = (chapterKey || "").replace(/\.txt$/, "");
  const overrides = BANNER_CHAPTER_OVERRIDE[chapter];
  if (overrides) {
    for (const k of Object.keys(overrides)) {
      if (String(text).includes(k) && exists(path.join(BANNER_DIR, `${overrides[k]}.png`))) {
        return overrides[k];
      }
    }
  }
  for (const r of BANNER_RULES) {
    if (String(text).includes(r.key) && exists(path.join(BANNER_DIR, `${r.file}.png`))) {
      return r.file;
    }
  }
  return null;
}

/** 音效 / BGM 路径：按实际存在的扩展名解析（wav/mp3/ogg/m4a），缺图缺音都不崩 */
function soundPath(id) {
  const dir = path.join(ROOT, "src", "main", "resources", "assets", "sounds");
  for (const ext of ["wav", "mp3", "ogg", "m4a"]) {
    if (exists(path.join(dir, `${id}.${ext}`))) return `assets/sounds/${id}.${ext}`;
  }
  return `assets/sounds/${id}.ogg`;   // BGM 计划格式（美术侧到位后按实际扩展名自动生效）
}

/** CG 路径：PNG 优先，其次 JPEG（美术侧转 JPEG 后自动生效） */
function cgPath(id) {
  const dir = path.join(ROOT, "src", "main", "resources", "assets", "cg");
  // 美术侧约定 CG 走 JPEG（q88~90）；两种都在时优先 JPEG（PNG 未清理也能跑）
  if (exists(path.join(dir, `${id}.jpg`))) return `assets/cg/${id}.jpg`;
  if (exists(path.join(dir, `${id}.png`))) return `assets/cg/${id}.png`;
  return `assets/cg/${id}.jpg`;
}

/** @se → 音频插件槽；每个 SE 用独立通道（se_<id>）→ 多路并发互不打断 */
function seOps(se) {
  const ch = se.id;   // 每个 SE 独立通道（id 本身已唯一）→ 多路并发互不打断
  const ops = [];
  if (se.vol) ops.push(`@plugin(audio) | volume | ${se.vol} | ${ch}`);
  ops.push(`@plugin(audio) | play | ${soundPath(se.id)} | ${ch}`);
  return ops;
}

/** @bgm（类别语义）→ 音频插件槽：按类别运行时随机取曲（fade 先按 loop 处理） */
function bgmOps(bgm) {
  if (bgm.mode === "stop") return ["@plugin(audio) | stop | | bgm"];
  return [`@plugin(audio) | loopcat | ${bgm.id} | bgm`];
}

function bgPath(sceneId) {
  // 优先用与场景 id 同名的真实背景（素材到位后自动生效）；
  // 只有该场景图还没出时，才退回 BG_ALIAS 里的临时顶替图
  const exact = `bg_${sceneId}`;
  if (exists(path.join(SPRITES, "backgrounds", `${exact}.png`))) {
    return `assets/sprites/backgrounds/${exact}.png`;
  }
  const alias = BG_ALIAS[sceneId];
  if (alias) return `assets/sprites/backgrounds/${alias}.png`;
  return `assets/sprites/backgrounds/${exact}.png`;
}

// =====================================================================
// 3) 编译成拍点（beat）
// =====================================================================
const beats = [];
const labelFirstBeat = new Map();
const flags = new Set();
let stage = { bg: "", chars: new Map(), cg: null, pose: new Map(), hold: new Set() }; // chars: role -> {expr,pos}；cg: {id,hold}；pose: role -> bust|full；hold: 跨段保留的角色
const lastExpr = new Map();     // role -> 最近一次表情（说话人自动上场时沿用）
const lastPos = new Map();      // role -> 最近一次站位
const autoEntered = new Set();  // 剧本没 @enter 但说了话、被自动补上场的角色
const autoExited = new Set();   // 被段落边界兜底清场请下台的角色
let pendingOps = [];                                 // @se / @bgm：挂到下一个拍点（进入即执行）

function sceneName(label, n) { return n === 0 ? label : `${label}__${n + 1}`; }

function newBeat(label, kind, extra = {}) {
  // 逻辑拍点是「自动推进」的过渡幕，不承载画面；其余拍点才显示 CG
  const visual = kind === "dialog" || kind === "banner" || kind === "choice"
    || kind === "tail" || kind === "ending" || kind === "cgcard" || kind === "debut";
  const b = {
    label,
    kind,
    bg: stage.bg,
    title: stage.title,
    cg: visual ? stage.cg : null,
    chars: [...stage.chars.entries()].map(([role, v]) => ({ role, ...v })),
    poses: Object.fromEntries(stage.pose),   // 本拍各角色的取景（bust/full）
    dialog: [],
    ops: [],
    buttons: [],
    goto: null,
    target: null,
    mg: null,
    banner: null,
    ...extra,
  };
  // 剧本显式 @bgm 的锁挂到本拍
  if (pendingBgmLock) {
    if (pendingBgmLock.stop) b.bgmStop = true;
    else b.bgmExplicit = pendingBgmLock.cat;
    pendingBgmLock = null;
  }
  // @se 等「进入即执行」的槽挂到本拍
  if (pendingOps.length) {
    b.ops.push(...pendingOps);
    pendingOps = [];
  }
  beats.push(b);
  // flash = 点一下继续：只在承载它的这一拍显示，下一拍收起；hold = 保持到下一条 @cg/@scene/@ending
  if (visual && stage.cg && !stage.cg.hold) stage.cg = null;
  return b;
}

function flushDialog(label) {
  const last = beats[beats.length - 1];
  if (last && last.label === label && last.dialog.length && !last.dialogEmitted) return last;
  return null;
}

let dialogBuf = [];
let dialogSpeaker = "";
let currentLabel = null;
let currentChapter = "";   // 当前所在的章节文件（横幅章节变体用）
let pendingBgmLock = null; // 剧本显式 @bgm 的类别/停止：挂到下一拍

function flush(label) {
  if (!dialogBuf.length) return;
  const b = newBeat(label, "dialog");
  b.dialog = dialogBuf.slice();
  b.speaker = dialogSpeaker;
  dialogBuf = [];
  dialogSpeaker = "";
  return b;
}

function ensureLabel(id, file, line) {
  if (currentLabel === null) { currentLabel = id; }
  if (!labelFirstBeat.has(id)) labelFirstBeat.set(id, null); // 稍后回填
}

const directives = [];
for (const f of CHAPTERS) directives.push(...parseScriptFile(f));

// ---- 段落级"本段有台词的角色"（S5 兜底清场要用；编译器是单遍的，所以先扫一遍指令流）----
// 为什么需要：旧剧本 @enter 写了 242 次、@exit 只写了 16 次，而立绘在换场景/换段时都不清，
// 于是只进不出 —— 平均同台从第1章 1.9 人一路涨到第8章 7.6 人（详见
// docs/ds-adventrue/常驻立绘清理-干跑报告.md）。这里做的是"兜底"：
// 进入新段落时，把"本段整段没有台词"的立绘清掉；剧本里显式 @exit 仍然优先、@enter ... hold 可豁免。
const speakersByLabel = new Map();
{
  let lab = null;
  for (const d of directives) {
    if (d.kind === "label") lab = d.id;
    else if (d.kind === "dialog" && lab) {
      const m = String(d.text).match(/^([A-Za-z0-9_\u4e00-\u9fa5]+):/);
      const sp = m ? m[1] : "";
      if (sp && sp !== "narr") {
        if (!speakersByLabel.has(lab)) speakersByLabel.set(lab, new Set());
        speakersByLabel.get(lab).add(sp);
      }
    }
  }
}

let lastLabel = null;
for (const d of directives) {
  if (d.kind === "label") {
    flush(currentLabel);
    // 兜底清场：本段整段没台词的立绘退场（@enter ... hold 的角色豁免）
    const keep = speakersByLabel.get(d.id) || new Set();
    for (const role of [...stage.chars.keys()]) {
      if (keep.has(role) || stage.hold.has(role)) continue;
      stage.chars.delete(role);
      autoExited.add(role);
    }
    currentLabel = d.id;
    lastLabel = d.id;
    if (!labelFirstBeat.has(d.id)) labelFirstBeat.set(d.id, beats.length);
    continue;
  }
  if (currentLabel === null) currentLabel = "__start";
  if (d.file) currentChapter = d.file;

  switch (d.kind) {
    case "scene":
      flush(currentLabel);
      stage.bg = d.id;
      stage.title = d.title;
      stage.cg = null;   // 换场景收起 CG（对应剧情侧约定：hold 到 @scene 为止）
      break;
    case "cg": {
      flush(currentLabel);   // 先把已经攒下的台词收尾成上一拍
      if (d.mode === "clear") {
        stage.cg = null;
        break;
      }
      stage.cg = { id: d.id, hold: d.mode === "hold" };
      // hold = 「背景层 CG」：CG 留在后续拍里，文字/UI 照常压在它上面（本期剧本 0 处使用）
      if (d.mode === "hold") break;
      // flash（默认）= 「CG 独占一拍」：本拍只含 背景 + CG + 整屏「继续」按钮，
      // 不带立绘 / UI 皮肤 / 台词 —— 否则 CG 只是 bg 类节点，永远压在内容节点（文字）之下
      // （引擎 ReaderView.isStageNode：bg/char 是舞台节点，每拍被压到最底层）。
      // 台词顺延到下一拍：点一下继续后才出字。
      newBeat(currentLabel, "cgcard", { chars: [] });
      break;
    }
    case "pose": {
      // 持久取景：本拍收尾后再改，避免把上一句台词也切了取景
      flush(currentLabel);
      stage.pose.set(d.role, d.pose);
      break;
    }
    case "debut": {
      // 登场卡拍：只有这一拍用 full，且只放该角色（无对话框 → 全身完全可见）
      flush(currentLabel);
      const cur = stage.chars.get(d.role);
      const prev = stage.pose.get(d.role);
      newBeat(currentLabel, "debut", {
        chars: [{ role: d.role, expr: cur ? cur.expr : "normal", pos: "center" }],
        poses: { [d.role]: "full" },
      });
      if (prev) stage.pose.set(d.role, prev); else stage.pose.delete(d.role);
      break;
    }
    case "se":
      pendingOps.push(...seOps(d));
      break;
    case "bgm":
      // 显式 @bgm：锁到"下一拍"（由 newBeat 消费）
      pendingBgmLock = d.mode === "stop" ? { stop: true } : { cat: d.id };
      break;
    case "enter":
      flush(currentLabel);
      stage.chars.set(d.role, { expr: d.expr, pos: d.pos });
      lastExpr.set(d.role, d.expr);
      lastPos.set(d.role, d.pos);
      if (d.hold) stage.hold.add(d.role); else stage.hold.delete(d.role);
      break;
    case "exit":
      flush(currentLabel);
      stage.chars.delete(d.role);
      stage.hold.delete(d.role);
      break;
    case "dialog": {
      // 说话人变了就拆成一拍：这样名牌与「说话者高亮」能精确到句
      const sp = d.speaker || "narr";
      if (dialogBuf.length && dialogSpeaker && dialogSpeaker !== sp) flush(currentLabel);
      // 说话人不在场 → 自动补上场（否则只有名牌没有立绘，看起来像"话外音"）
      if (sp !== "narr" && !stage.chars.has(sp)) {
        const pos = ["center", "left", "right"].find(
          (p) => ![...stage.chars.values()].some((c) => c.pos === p)) || "center";
        stage.chars.set(sp, { expr: lastExpr.get(sp) || "base", pos });
        lastPos.set(sp, pos);
        autoEntered.add(sp);
      }
      dialogSpeaker = sp;
      dialogBuf.push(d.text);
      break;
    }
    case "if": {
      // 条件跳转：本拍自带分支（两个槽：select 写变量 + goto 读变量），禁止自动补 goto
      flush(currentLabel);
      const b = newBeat(currentLabel, "logic");
      b.branch = { name: d.name, op: d.op, value: d.value, label: d.label };
      break;
    }
    case "ending": {
      // 结局：独立场景（scene 级属性 ending = <id>），进入后引擎停住推进
      flush(currentLabel);
      const b = newBeat(currentLabel, "ending");
      b.endingId = d.id;
      if (!(d.id in ENDING_NAME)) problems.unknownEndings.add(d.id);
      break;
    }
    case "banner": {
      flush(currentLabel);
      const b = newBeat(currentLabel, "banner");
      b.banner = d.text;
      b.chapter = currentChapter;
      break;
    }
    case "flag": {
      flush(currentLabel);
      flags.add(d.name);
      const b = newBeat(currentLabel, "logic");
      b.ops.push(`@plugin(add) | @var(${d.name}) | @int(${flagDelta(d.op)}) | @var(${d.name})`);
      break;
    }
    case "save": {
      flush(currentLabel);
      const b = newBeat(currentLabel, "logic");
      b.ops.push(`save | | slot_${currentLabel}`);
      break;
    }
    case "goto": {
      flush(currentLabel);
      const b = newBeat(currentLabel, "logic");
      b.goto = d.target;
      break;
    }
    case "minigame": {
      flush(currentLabel);
      // flash 的 @cg 已经在解析时就出了独占卡拍（见上面的 case "cg"），这里只剩兜底：
      // 万一还有未消费的 hold CG，也不能让它顺延到小游戏【之后】的拍点上
      // （历史 bug：cg_ch09_gomoku 会掉到输棋重开的 ch9_taunt1）。
      if (stage.cg && !stage.cg.hold) {
        // cgcard 是可视拍点：newBeat 已把 stage.cg 拷进本拍并清空 stage.cg，这里不要再赋值
        newBeat(currentLabel, "cgcard", { chars: [] });
      }
      const b = newBeat(currentLabel, "minigame");
      b.mg = d.mg;
      break;
    }
    case "choiceStart":
      flush(currentLabel);
      newBeat(currentLabel, "choice");
      break;
    case "choice": {
      let b = beats[beats.length - 1];
      if (!b || b.kind !== "choice" || b.label !== currentLabel) b = newBeat(currentLabel, "choice");
      b.buttons.push(d.opt);
      if (d.opt.flag) flags.add(d.opt.flag);
      break;
    }
    default:
      throw new Error(`未处理的指令 ${d.kind}`);
  }
}
flush(currentLabel);

// 章末交棒：未编译章节的跳转统一指向占位场景（见 resolveTarget）
const TAIL = "__待续";

// ---- 场景命名与串联 ----
const counters = new Map();
for (const b of beats) {
  const n = counters.get(b.label) || 0;
  b.scene = sceneName(b.label, n);
  counters.set(b.label, n + 1);
  if (labelFirstBeat.get(b.label) === null || labelFirstBeat.get(b.label) === undefined) {
    // 该 label 的首拍：记录场景名供 goto 解析
    if (!labelFirstBeat.has(b.label + "@scene")) labelFirstBeat.set(b.label + "@scene", b.scene);
  }
}
// label → 首拍场景名
const labelScene = new Map();
for (const b of beats) if (!labelScene.has(b.label)) labelScene.set(b.label, b.scene);
/** 跳转目标解析：未编译的章节（如 ch3_start）统一导向「本章待实现」占位场景 */
const resolveTarget = (x) => (x && labelScene.get(x)) || TAIL;
// 选项逻辑拍点（每个选项一个），插到 choice 拍点之后
const extra = [];
for (let bi = 0; bi < beats.length; bi++) {
  const b = beats[bi];
  if (b.kind !== "choice") continue;
  // 选项可以只写 flag、不写 goto（分支交给紧随其后的 @if 链，见第6章点歌 / 终章三选一）
  // → 这类选项应"顺延到选择之后的下一拍"，而不是落到 __待续
  const fallthrough = (bi + 1 < beats.length) ? beats[bi + 1].scene : TAIL;
  b.optionScenes = b.buttons.map((opt, i) => {
    if (!opt.flag) return opt.goto ? resolveTarget(opt.goto) : fallthrough;
    const logic = {
      label: b.label,
      kind: "optlogic",
      scene: `${b.scene}__opt${i + 1}`,
      bg: b.bg,
      chars: b.chars,
      dialog: [],
      ops: [`@plugin(add) | @var(${opt.flag}) | @int(${flagDelta(opt.value)}) | @var(${opt.flag})`],
      goto: opt.goto ? resolveTarget(opt.goto) : fallthrough,
      buttons: [],
      mg: null,
      banner: null,
    };
    extra.push(logic);
    return logic.scene;
  });
}
// 重排：把 optlogic 放在各自 choice 之后
if (extra.length) {
  const withExtra = [];
  for (const b of beats) {
    withExtra.push(b);
    if (b.kind === "choice") for (const l of extra) if (l.scene.startsWith(b.scene + "__opt")) withExtra.push(l);
  }
  beats.length = 0;
  beats.push(...withExtra);
}

// 串联：默认指向下一个拍点；显式 goto / 选项按钮 / 小游戏拍点按各自语义
for (let i = 0; i < beats.length; i++) {
  const b = beats[i];
  const next = beats[i + 1];
  if (b.kind === "logic" && b.branch) {
    // @if：select 写链路变量 → goto 读它；假分支 = 顺延到下一拍（等价于剧本的"落到下一行"）
    if (!labelScene.has(b.branch.label)) problems.missingTargets.add("@" + b.branch.label);
    const thenScene = b.branch.label && labelScene.get(b.branch.label) ? labelScene.get(b.branch.label) : TAIL;
    const elseScene = next ? next.scene : TAIL;
    const pred = PRED[b.branch.op];
    b.ops = [
      `@plugin(select) | @${pred}(@var(${b.branch.name}, 0), ${b.branch.value}) | ${thenScene} | ${elseScene} | @var(${FLOW_VAR})`,
    ];
    b.gotoVar = `@var(${FLOW_VAR})`;
    b.goto = null;
  } else if (b.kind === "ending") {
    b.goto = null;
    b.target = null;          // 结局不自动跳转：停在这里
  } else if (b.kind === "logic") {
    b.goto = b.goto ? resolveTarget(b.goto) : (next ? next.scene : TAIL);
  } else if (b.kind === "optlogic") {
    // 已设 goto
  } else if (b.kind === "minigame") {
    // 结果由引擎按 mg.onWin / mg.onLose 路由，不需要 target
  } else {
    if (b.goto) { /* 显式跳转优先 */ }
    else b.target = next && next.kind === "tail" ? TAIL : (next ? next.scene : TAIL);
    if (b.kind === "tail") b.target = null;
  }
}

// ---- BGM 统一分配：只在"类别变化"时给该拍加一条槽（避免每幕重启音乐）----
(function assignBgm() {
  if (!BGM_CATS.size) return;
  let ch = "";
  for (const b of beats) {
    if (b.chapter) ch = b.chapter;
    else b.chapter = ch;
  }
  let cur = "";
  let assigned = 0;
  for (const b of beats) {
    const want = bgmCategoryOf(b);
    if (want === "__stop") {
      if (cur) {
        b.ops.push("@plugin(audio) | stop | | bgm");
        cur = "";
      }
      continue;
    }
    if (!want || want === cur) continue;
    b.ops.push(`@plugin(audio) | loopcat | ${want} | bgm`);
    cur = want;
    assigned++;
  }
  problems.bgmAssigned = assigned;
  problems.bgmLast = cur;
})();

// ---- UI 交互打磨（PR #18 的思路，改成编译器发射以便随时重生成）----
//   ① 对话框点击跟手：底板与对话框同步缩放
//   ② 名牌图文同步滑入：底板与文字一起从左侧归位（解决"底板滑入、文字直接出现"）
//   ③ 幕题入场脉冲：章节卡与幕题文字缩放入场后还原
(function assignUiPolish() {
  let lastTitle = null;
  for (const b of beats) {
    if (b.kind === "dialog") {
      b.ops.push("slot = 对话按下 | set | ui_dialog | scale | value=1.02");
      b.ops.push("slot = 对话按下 | set | 对话框 | scale | value=1.02");
      b.ops.push("slot = 对话松开 | set | ui_dialog | scale | value=1");
      b.ops.push("slot = 对话松开 | set | 对话框 | scale | value=1");
      if (b.speaker && b.speaker !== "narr") {
        b.ops.push("slot = 场景进入 | set | ui_nameplate | x | value=56");
        b.ops.push("slot = 场景进入 | set | 名牌 | x | value=100");
        b.ops.push("slot = 场景进入 | @plugin(after) | 0.06 | 名牌入场归位");
        b.ops.push("slot = 名牌入场归位 | set | ui_nameplate | x | value=96");
        b.ops.push("slot = 名牌入场归位 | set | 名牌 | x | value=140");
      }
    }
    if (b.title && b.title !== lastTitle) {
      lastTitle = b.title;
      b.ops.push("slot = 场景进入 | set | ui_chapter_banner | scale | value=0.96");
      b.ops.push("slot = 场景进入 | set | 幕题 | scale | value=0.96");
      b.ops.push("slot = 场景进入 | @plugin(after) | 0.18 | 幕题还原");
      b.ops.push("slot = 幕题还原 | set | ui_chapter_banner | scale | value=1");
      b.ops.push("slot = 幕题还原 | set | 幕题 | scale | value=1");
    }
  }
})();

// =====================================================================
// 4) 生成 scenario.txt
// =====================================================================
const L = [];
L.push("# ============================================================");
L.push("# 剧情地图: story（由 tools/build_story.mjs 编译生成，请勿手改）");
L.push(`# 源: docs/ds-adventrue/剧本/ 共 ${CHAPTERS.length} 章（${CHAPTERS[0].replace(/\.txt$/, "")} … ${CHAPTERS[CHAPTERS.length - 1].replace(/\.txt$/, "")}）`);
L.push("# 语法: [option] / [场景名] / { 节点属性 } / 场景级 signal|slot");
L.push("# ============================================================");
L.push("");
L.push("[option]");
L.push(`initialScene = ${labelScene.get("ch0_start") || beats[0].scene}`);
L.push("background = #070a14");
L.push("volume = 0.6");
L.push("typewriterSpeed = 18");
for (const f of [...flags].sort()) L.push(`savevar = ${f} | int | 0`);
// @if / retry 用到的变量：必须声明，否则读档后计数丢失
const extraVars = new Set(["retry_count", FLOW_VAR]);
for (const b of beats) if (b.branch) extraVars.add(b.branch.name);
for (const v of [...extraVars].sort()) {
  if (flags.has(v)) continue;
  L.push(`savevar = ${v} | ${v === FLOW_VAR ? "str" : "int"} | ${v === FLOW_VAR ? '""' : "0"}`);
}
L.push("");

/** 去掉台词开头的「角色: 」前缀（名牌已显示说话人） */
function stripSpeaker(text) {
  return String(text).replace(/^[A-Za-z0-9_\u4e00-\u9fa5]+:\s*/, "");
}

let lastEmittedTitle = null;   // 幕题只在"变化时"发一次章节卡（否则每拍都弹）
const emitStage = (b, out) => {
  // 背景
  if (b.bg) {
    // 背景必须带固定 id：引擎的 stage=keep 复用是按 id + 签名匹配的（无 id 会被每拍重建）
    out.push("{", "type = bg", "id = 背景", "x = 0", "y = 0", "width = 1280", "height = 720",
      `path = ${bgPath(b.bg)}`);
    if (b.title) out.push(`# 幕题: ${b.title}`);
    out.push("}");
  }
  // 章节标题卡（美术侧 §2.5）：场景带 title 时，在上方叠一张 chapter_banner + 幕题文字
  // CG 卡拍例外：幕题文字是内容节点，会压在 CG 上面（违反「CG 盖住一切」），
  // 而且不消费 lastEmittedTitle，幕题留给紧随其后的正常拍点去发。
  if (b.title && b.title !== lastEmittedTitle && b.kind !== "cgcard") {
    lastEmittedTitle = b.title;
    const bw = 720, bh = 120;
    out.push("{", "type = char", "id = ui_chapter_banner", `x = ${Math.round((1280 - bw) / 2)}`, "y = 64",
      `width = ${bw}`, `height = ${bh}`,
      "path = assets/sprites/ui/chapter_banner.png", "opacity = 0.96",
      "transition = opacity/scale:180ms", "}");
    out.push("{", "type = text", "id = 幕题", `x = ${Math.round((1280 - bw) / 2)}`, "y = 104",
      `width = ${bw}`, "height = 44", `text = ${b.title}`, "fontSize = 26", "align = center",
      "style = -fx-text-fill: #ffd76a;", "transition = opacity/scale:180ms", "}");
  }
  // CG 层：放在背景之后、立绘之前 —— 引擎按节点顺序绘制，天然是「背景之上、立绘之下」
  if (b.cg) {
    out.push("{", "type = bg", "id = CG", "x = 0", "y = 0", "width = 1280", "height = 720",
      `path = ${cgPath(b.cg.id)}`, "}");
  }
  // 立绘（同角色固定节点 id，换表情只改 path）
  // 取景档：@pose 按角色覆盖 > 按本拍人数自适应（见 FRAME_BY_CAST）
  const castPose = poseForCast(b.chars.length);
  const used = new Map();
  for (const c of b.chars) {
    const pose = (b.poses && b.poses[c.role]) || castPose;
    const fr = FRAMES[pose] || FRAMES.bust;
    let x = frameX(fr, c.pos);
    if (used.has(c.pos)) { // 同位置错开，避免完全重叠（错开量随框宽缩放，别让人多的拍挤成一坨）
      const k = used.get(c.pos);
      x += (k % 2 === 1 ? -1 : 1) * (Math.round(120 * (fr.w / 320)) * Math.ceil(k / 2));
    }
    used.set(c.pos, (used.get(c.pos) || 0) + 1);
    // 说话者高亮：说话人 1.0，其他立绘压暗到 0.5；旁白拍不压暗
    const speaker = b.speaker && b.speaker !== "narr" ? b.speaker : "";
    const op = !speaker || c.role === speaker ? "1.0" : "0.5";
    out.push("{", "type = char", `id = char_${c.role}`, `x = ${x}`, `y = ${fr.y}`,
      `width = ${fr.w}`, `height = ${fr.h}`, `path = ${spritePath(c.role, c.expr)}`,
      `opacity = ${op}`, "}");
  }
};

const labelOfSpeaker = (text) => {
  const m = text.match(/^([A-Za-z0-9_\u4e00-\u9fa5]+):/);
  return m ? m[1] : "";
};

let prevBg = null;
for (const b of beats) {
  const out = [];
  out.push(`[${b.scene}]`);
  // 背景没变 → 承接上一拍的舞台（引擎 stage=keep：不整屏淡入、背景/立绘不重建）
  if (prevBg !== null && b.bg === prevBg) out.push("stage = keep");
  prevBg = b.bg;
  if (b.kind === "ending") out.push(`ending = ${b.endingId}`);
  if (b.kind === "minigame") {
    const gid = GAME_ALIAS[b.mg.id] || b.mg.id;
    if (!GAME_KNOWN.has(gid)) problems.unknownGames.add(gid);
    out.push(`event = ${gid}`);
    out.push(`mg.mode = ${b.mg.mode}`);
    out.push(`mg.onWin = ${resolveTarget(b.mg.onWin)}`);
    out.push(`mg.onLose = ${resolveTarget(b.mg.onLose)}`);
    if (b.mg.loop) out.push(`mg.loop = ${resolveTarget(b.mg.loop)}`);
    if (b.mg.with) out.push(`mg.with = ${b.mg.with}`);
  }
  out.push("");
  // 进入即执行的槽（SE / BGM / 逻辑运算）—— 任何拍点都可能有
  for (const op of b.ops) out.push(`slot = 场景进入 | ${op}`);
  emitStage(b, out);

  if (b.kind === "dialog") {
    const hasSpeaker = !!(b.speaker && b.speaker !== "narr");
    // UI 皮肤（美术侧交付）：暗角 → 对话框皮 → 名牌皮，然后才是引擎的文字节点
    emitUiSkin(out, hasSpeaker);
    if (hasSpeaker) {
      out.push("{", "type = name", "id = 名牌", "x = " + NAME.x, "y = " + NAME.y,
        "width = " + NAME.w, "height = " + NAME.h,
        "text = " + (DISPLAY_NAME[b.speaker] || b.speaker), "fontSize = 22", "align = left",
        "style = -fx-background-color: transparent;", "transition = x:300ms", "}");
    }
    out.push("{", "type = dialog", "id = 对话框", "x = " + DIALOG_BOX.x, "y = " + DIALOG_BOX.y,
      "width = " + DIALOG_BOX.w, "height = " + DIALOG_BOX.h,
      "style = -fx-background-color: transparent;", "transition = opacity/scale:150ms");
    out.push("text = <<<");
    // 每行台词 = 一个独立段落（引擎按独立一行 --- 分段，点击逐段推进）；
    // 若整段堆在一起，引擎会一次性渲染全部行 → 必然溢出对话框。
    // 说话人由上面的名牌节点承担，所以这里去掉「角色: 」前缀。
    b.dialog.forEach((t, i) => {
      if (i > 0) out.push("---");
      out.push(stripSpeaker(t));
    });
    out.push("<<<");
    if (b.target) out.push(`target = ${b.target}`);
    out.push("}");
  } else if (b.kind === "banner") {
    // 横幅弹出音（美术侧 §5.1 全局 UI 音之一）：任意 st: 横幅都带一声
    out.push("slot = 场景进入 | @plugin(audio) | play | assets/sounds/se_banner.wav | se_banner");
    const bf = bannerFile(b.banner, b.chapter);
    const dim = bf ? pngSize(path.join(BANNER_DIR, `${bf}.png`)) : null;
    if (bf && dim) {
      // 美术侧交付的系统横幅（14 条，高 100）：按原宽居中，超过画布才等比缩小
      const w = Math.min(dim.w, 1160);
      const h = Math.max(1, Math.round((dim.h * w) / dim.w));
      out.push("{", "type = char", "id = 横幅", `x = ${Math.round((1280 - w) / 2)}`, "y = 250",
        `width = ${w}`, `height = ${h}`,
        `path = assets/sprites/ui/banners/${bf}.png`, "}");
      // 图片横幅本身已含文案 → 用整屏透明按钮承载「点一下继续」，
      // 不再在底部重复一遍同样的文字（文字回退时才用对话框）
      out.push("{", "type = button", "id = 继续", "x = 0", "y = 0",
        "width = 1280", "height = 720", "text = ", "action = target",
        `target = ${b.target || ""}`,
        "style = -fx-background-color: transparent; -fx-border-color: transparent;"
          + " -fx-text-fill: #ffe9b0; -fx-font-size: 17px;", "}");
    } else {
      // 未映射到图片的文案：回退文字横幅 + 对话框（保证演出与可读性都不缺）
      emitUiSkin(out, false);
      out.push("{", "type = text", "id = 横幅", "x = " + BANNER.x, "y = " + BANNER.y,
        "width = " + BANNER.w, "height = " + BANNER.h, `text = ${b.banner}`,
        "fontSize = 40", "align = center",
        "style = -fx-text-fill: #ffd76a; -fx-background-color: rgba(10,12,26,0.86); -fx-background-radius: 18; -fx-border-color: #ffd76a; -fx-border-radius: 18;", "}");
      out.push("{", "type = dialog", "id = 对话框", "x = " + DIALOG_BOX.x, "y = " + DIALOG_BOX.y,
        "width = " + DIALOG_BOX.w, "height = " + DIALOG_BOX.h);
      out.push("text = <<<");
      out.push(b.banner);
      out.push("<<<");
      if (b.target) out.push(`target = ${b.target}`);
      out.push("}");
    }
  } else if (b.kind === "choice") {
    const n = b.buttons.length;
    const y0 = 430 - Math.floor((n - 1) * 34);
    b.buttons.forEach((opt, i) => {
      const y = y0 + i * 68;
      // 选项按钮皮（美术侧 §2.3）：先垫一张 choice_button_normal，再放透明按钮接管点击
      out.push("{", "type = char", `id = ui_choice_${i + 1}`, "x = 360", `y = ${y}`,
        "width = 560", "height = 56",
        "path = assets/sprites/ui/choice_button_normal.png", "opacity = 1.0", "}");
      out.push("{", "type = button", `id = 选项${i + 1}`, `x = 360`, `y = ${y}`,
        "width = 560", "height = 56", `text = ${opt.text}`, "action = target",
        `target = ${b.optionScenes[i]}`,
        "style = -fx-background-color: transparent; -fx-border-color: transparent;", "}");
    });
  } else if (b.kind === "logic" || b.kind === "optlogic") {
    // @if 分支拍点：goto 的目标由前面 select 写进链路变量
    if (b.gotoVar) out.push(`slot = 场景进入 | goto | | ${b.gotoVar}`);
    else if (b.goto) out.push(`slot = 场景进入 | goto | | ${b.goto}`);
  } else if (b.kind === "cgcard" || b.kind === "debut") {
    // 卡拍（CG 卡 / 登场卡）：画面已由 emitStage 发出；整屏透明按钮承接点击（点一下继续）
    out.push("{", "type = button", "id = 继续", "x = 0", "y = 0",
      "width = 1280", "height = 720", "text = ", "action = target",
      `target = ${b.target || ""}`,
      "style = -fx-background-color: transparent; -fx-border-color: transparent;", "}");
  } else if (b.kind === "ending") {
    const name = ENDING_NAME[b.endingId] || b.endingId;
    out.push("{", "type = text", "id = 结局卡", "x = 340", "y = 250", "width = 600", "height = 90",
      `text = —— ${name} ——`, "fontSize = 40", "align = center",
      "style = -fx-text-fill: #ffd76a; -fx-background-color: rgba(10,12,26,0.86); -fx-background-radius: 18;",
      "}");
    out.push("{", "type = text", "id = 结局提示", "x = 390", "y = 360", "width = 500", "height = 40",
      "text = （本作到此结束 · 感谢游玩）", "fontSize = 18", "align = center",
      "style = -fx-text-fill: #cfd4ea;", "}");
    out.push("{", "type = button", "id = 退出游戏", "x = 470", "y = 430", "width = 340, ".replace(", ", ""),
      "text = 退出", "action = call", "target = @plugin(quit)", "}");
  } else if (b.kind === "tail") {
    out.push("{", "type = text", "x = 240", "y = 280", "width = 800", "height = 120",
      "text = 本章待实现 —— 后续章节接入中", "fontSize = 34", "align = center", "}");
    out.push("{", "type = dialog", "x = " + DIALOG_BOX.x, "y = " + DIALOG_BOX.y,
      "width = " + DIALOG_BOX.w, "height = " + DIALOG_BOX.h,
      "text = <<<", "narr: （本章待实现 —— 敬请期待）", "<<<", "}");
  }
  out.push("");
  L.push(out.join("\n"));
}

// 尾部占位场景（若 goto 到未实现章节）
L.push(`[${TAIL}]`);
L.push("");
L.push("{");
L.push("type = text");
L.push("x = 240");
L.push("y = 280");
L.push("width = 800");
L.push("height = 120");
L.push("text = 本章待实现 —— 后续章节接入中");
L.push("fontSize = 34");
L.push("align = center");
L.push("}");
L.push("");

// 编译期问题：不静默（缺失目标 / 未实现的游戏 / 未知结局）
if (problems.missingTargets.size) {
  console.error("✗ 以下 @if 目标标签不存在：" + [...problems.missingTargets].sort().join("、"));
  process.exit(4);
}
if (problems.unknownEndings.size) {
  console.error("✗ 未知 @ending id：" + [...problems.unknownEndings].sort().join("、")
    + "（已知：" + Object.keys(ENDING_NAME).join(" / ") + "）");
  process.exit(4);
}

const text = L.join("\n") + "\n";

// =====================================================================
// 5) 输出 / 校验
// =====================================================================
const summary = `场景 ${beats.length + 1} 个 | flag ${flags.size} 个 | 立绘引用 ${beats.reduce((a, b) => a + b.chars.length, 0)} 处`;

if (CHECK) {
  const old = exists(OUT_FILE) ? fs.readFileSync(OUT_FILE, "utf8") : "";
  if (old === text) { console.log("CHECK OK（重编译零差异） | " + summary); process.exit(0); }
  console.log("CHECK DIFF（产物与现有文件不一致） | " + summary);
  process.exit(3);
}

fs.mkdirSync(path.dirname(OUT_FILE), { recursive: true });
fs.writeFileSync(OUT_FILE, text, "utf8");
console.log(`已生成 ${path.relative(ROOT, OUT_FILE)} | ${summary}`);
console.log(`  label ${labelScene.size} 个 | 输出 ${text.split("\n").length} 行`);
if (problems.bgmAssigned !== undefined) {
  console.log(`  BGM 分配 ${problems.bgmAssigned} 处（当前类别 ${problems.bgmLast || "-"}）`);
}
if (problems.unknownGames.size) {
  console.warn("⚠ 以下 @minigame 尚无插件实现（编译通过，运行时会提示缺插件）："
    + [...problems.unknownGames].sort().join("、"));
}
if (autoEntered.size) {
  console.warn("⚠ 以下说话人在剧本里没有 @enter，编译器已自动补上场（建议补写 @enter 指定表情/站位）："
    + [...autoEntered].sort().join("、"));
}
if (autoExited.size) {
  console.log(`  段落边界兜底清场：请下台 ${autoExited.size} 个角色（${[...autoExited].sort().join("、")}）`);
}

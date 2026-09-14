// deepseek_voice.mjs v2 —— 全员原声版：让 DeepSeek 官方 API「一人分饰全团」
// 用法:
//   node deepseek_voice.mjs mask   <原剧本> <masked输出>                       # 挖空所有角色台词(保留括号舞台提示)
//   node deepseek_voice.mjs call   <masked文件> <answers输出.json> [model] [temp]
//   node deepseek_voice.mjs splice <masked文件> <answers.json> <原声输出>       # 回填生成原声版
import fs from 'node:fs';
import https from 'node:https';
import os from 'node:os';
import path from 'node:path';

const [, , mode, a, b, c, d] = process.argv;

const CAST = `ds=ds 娘(你本人:自称「我」,直率聪明带冷幽默,可自然冒 Hmm/But wait/Let me,幻觉处一本正经说错)
sclerk=司秤吏(柜台后只闻其声,推销腔;全剧唯一可冒现代词,必被吐槽) 秤主=会长(贵气报价体,败而不恼更客气)
灯官=ChatGPT 娘(会员管家腔:先夸你再说档位) 契官=Claude 娘(极度礼貌,道歉式条款) 戏官=Gemini 娘(报幕狂,永远在开场)
glm=GLM 娘(毒舌护短短句) qianwen=千问酱(老好人商量腔) kimi=Kimi 娘(记忆当铺,温和低语)
reimu=博丽灵梦(直率暴脾气心软) paimon=派蒙(自称「派蒙」,话痨) creeper=苦力怕(只会嘶嘶气音+写字板)
miku=初音未来(礼貌柔软) amiya=阿米娅(条理公关) 怪力(憨直短句) 皮卡丘(只说「皮卡」变体) sai=藤原佐为(古风短句如落子)
snake_expert=蛇专家(童声自称「人家」,戏精) bugs=报幕虫(只会报幕腔)`;

const SYSTEM = `你是《ds 娘的奇妙冒险》剧组的「全能演员」，而且你就是 DeepSeek 本尊——主角 ds 娘（深海巨鲸娘）由你本色出演。
舞台本中所有角色的台词都被挖空成【#01】~【#NN】占位符；行首的角色名告诉你此刻你是谁。像广播剧一样从头演到尾：轮到谁，就真心实意变成谁说话——不是按提示词写作，是「轮到TA说话时，TA真的会说的那句」。
不归你演：narr 旁白 / st 系统横幅 / @ 舞台指令 / > 选项行（那是用户桑=玩家的原话）。
规则：
- 只输出 JSON：{"lines":["台词1","台词2",...]}；lines 顺序严格对应【#01】~【#NN】，只输出台词文字本身（可含（舞台小动作）），不带引号、不换行
- 每句 ≤55 字（NPC/报幕虫可更短）；声线切换要明显
- 可以自然带出思维链口水词 Hmm / But wait / Let me——但**只有 ds 娘的台词里能用**
- 红线禁词（全员）：资本 理财 提现 年化 充值 会员 订阅 广告 抽成 分期 优惠 剩余价值 异化 地租 剥削 阶级 证券化 对价
- 台词必须与前后文咬合、推进剧情；不复述舞台指示
【选角速记】${CAST}`;

function tryOnce(key, model, temperature, messages) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({
      model,
      messages,
      temperature,
      response_format: { type: 'json_object' },
      max_tokens: 12000,
    });
    const req = https.request({
      hostname: 'api.deepseek.com',
      path: '/chat/completions',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${key}` },
      timeout: 300000,
    }, res => {
      let buf = '';
      res.on('data', dd => { buf += dd; });
      res.on('end', () => {
        if (res.statusCode !== 200) return reject(new Error(`HTTP ${res.statusCode}: ${buf.slice(0, 300)}`));
        try { resolve(JSON.parse(buf).choices[0].message.content); }
        catch (e) { reject(e); }
      });
    });
    req.on('error', reject);
    req.on('timeout', () => req.destroy(new Error('timeout')));
    req.write(body); req.end();
  });
}

function keyCandidates() {
  const p = path.join(os.homedir(), '.dsh', '.credentials.yaml');
  const t = fs.readFileSync(p, 'utf8');
  const found = [];
  for (const name of ['DEEPSEEK_API_KEY', 'DEEPSEEK_PLATFORM_TOKEN']) {
    const i = t.indexOf(name);
    if (i < 0) continue;
    const m = t.slice(i, i + 4000).match(/secret:\s*["']?([A-Za-z0-9_\-\.]+)/);
    if (m) found.push({ name, key: m[1] });
  }
  if (!found.length) throw new Error('no DeepSeek credentials');
  return found;
}

async function chat(messages, model, temperature) {
  const cands = keyCandidates();
  let lastErr;
  for (const cand of cands) {
    try { return await tryOnce(cand.key, model, temperature, messages); }
    catch (e) {
      lastErr = e;
      if (!/HTTP 401|HTTP 403/.test(String(e))) throw e;
      console.error(`[auth] ${cand.name} 无效，尝试下一凭据…`);
    }
  }
  throw lastErr;
}

// 判断一行是否为「角色台词行」：有说话者冒号 + 引号台词；narr/st/@/#/> 排除
function isDialogue(l) {
  if (/^(narr|st):/.test(l)) return false;
  if (/^[@#>]/.test(l)) return false;
  const i1 = l.indexOf('「');
  const i2 = l.lastIndexOf('」');
  if (i1 < 0 || i2 <= i1) return false;
  const head = l.slice(0, i1);
  return /^[^:（#][^:]*:\s*/.test(head);
}

function mask(inFile, outFile) {
  const lines = fs.readFileSync(inFile, 'utf8').split(/\r?\n/);
  let n = 0; const out = [];
  for (const l of lines) {
    if (!isDialogue(l)) { out.push(l); continue; }
    const i1 = l.indexOf('「');
    const i2 = l.lastIndexOf('」');
    n++;
    out.push(l.slice(0, i1) + `【#${String(n).padStart(2, '0')}】` + l.slice(i2 + 1));
  }
  fs.writeFileSync(outFile, out.join('\n'), 'utf8');
  console.log(`masked=${n}`);
}

async function call(maskedFile, outFile, model, temperature) {
  const text = fs.readFileSync(maskedFile, 'utf8');
  const content = await chat([
    { role: 'system', content: SYSTEM },
    { role: 'user', content: '【舞台本（全角色台词已挖空）】\n\n' + text },
  ], model || 'deepseek-chat', Number(temperature) || 1.1);
  fs.writeFileSync(outFile, content, 'utf8');
  console.log('answers written:', outFile);
}

function splice(maskedFile, answersFile, outFile) {
  const lines = fs.readFileSync(maskedFile, 'utf8').split(/\r?\n/);
  const answers = JSON.parse(fs.readFileSync(answersFile, 'utf8')).lines;
  let n = 0; const out = [];
  for (const l of lines) {
    const m = l.match(/^(.*)【#(\d+)】(.*)$/);
    if (m) {
      const ans = String(answers[Number(m[2]) - 1] ?? '').trim();
      out.push(`${m[1]}「${ans}」${m[3]}`);
      n++;
    } else out.push(l);
  }
  fs.writeFileSync(outFile, out.join('\n'), 'utf8');
  console.log(`spliced=${n}, answers=${answers.length}`);
}

if (mode === 'mask') mask(a, b);
else if (mode === 'call') await call(a, b, c, d);
else if (mode === 'splice') splice(a, b, c);
else { console.error('usage: mask|call|splice'); process.exit(1); }
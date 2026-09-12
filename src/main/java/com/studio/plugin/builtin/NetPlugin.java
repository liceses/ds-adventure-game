package com.studio.plugin.builtin;

import com.studio.flow.PluginContext;
import com.studio.util.Logs;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 编辑器<b>自带</b>插件：<b>网络</b>——HTTP 请求与大模型对话（把回复写进存档变量）。
 *
 * <p>剧情可以因此“活”起来：查天气、取一句随机台词、把玩家的输入真的发给模型，
 * 再把返回的文字当成下一条台词。请求<b>异步</b>执行，不阻塞界面；完成后
 * 把结果写进输出位，并（可选）向场景发一个信号，让槽链继续往下走。</p>
 *
 * <h3>可用插件 ID</h3>
 * <pre>
 *   # 普通 GET（第 4 个参数是“完成信号”，可不写）
 *   slot = 取天气 | @plugin(http) | get  | https://api.example.com/w?city=1 | 天气到了 | @var(天气)
 *
 *   # POST 文本
 *   slot = 上报   | @plugin(http) | post | https://example.com/log | hello=1 | 上报完成 | @var(结果)
 *
 *   # 大模型对话：把回复写进变量（默认走 DeepSeek 的 OpenAI 兼容接口）
 *   slot = 问一问 | @plugin(llm) | 用一句话安慰玩家 | 回复到了 | @var(回复)
 * </pre>
 *
 * <h3>llm 的配置（写在 [option] 的存档变量里，都有默认值）</h3>
 * <table border="1">
 *   <caption>llm 用到的变量</caption>
 *   <tr><th>变量名</th><th>默认值</th><th>说明</th></tr>
 *   <tr><td>{@code AI密钥}</td><td>（空）</td><td>API Key；空则不发请求，直接把提示写进输出位</td></tr>
 *   <tr><td>{@code AI地址}</td><td>https://api.deepseek.com/chat/completions</td><td>OpenAI 兼容的 chat completions 地址</td></tr>
 *   <tr><td>{@code AI模型}</td><td>deepseek-chat</td><td>模型名</td></tr>
 *   <tr><td>{@code AI系统词}</td><td>你是一个视觉小说里的角色…</td><td>系统提示词（人设）</td></tr>
 * </table>
 *
 * <p><b>失败也不炸</b>：断网、超时、没有密钥、返回不是 JSON……都会把一条可读的说明
 * （例如 {@code （请求失败：连接超时）}）写进输出位，并照常发完成信号，剧情可以继续走。
 * 超时默认 15 秒，可用第 5 个参数覆盖（例如 {@code 30}）。</p>
 */
public class NetPlugin extends BuiltinPlugin {

    private enum Act { HTTP, LLM }

    private static final Map<String, Act> IDS = new LinkedHashMap<>();
    static {
        IDS.put("http", Act.HTTP);   IDS.put("请求", Act.HTTP);  IDS.put("网络", Act.HTTP);
        IDS.put("llm", Act.LLM);     IDS.put("ai", Act.LLM);     IDS.put("大模型", Act.LLM);
    }

    /** 默认完成信号名（不写第 4 个参数时用） */
    private static final String DEFAULT_SIGNAL = "网络完成";

    private final Act act;

    /** 共享 HttpClient（线程安全、内部有连接池） */
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public NetPlugin() { this(Act.HTTP, "http"); }

    public NetPlugin(Act act, String id) {
        super(id);
        this.act = act == null ? Act.HTTP : act;
    }

    public static List<String> ids() { return new ArrayList<>(IDS.keySet()); }

    public static NetPlugin of(String id) {
        if (id == null) return null;
        String k = id.trim();
        Act a = IDS.get(k.toLowerCase(Locale.ROOT));
        if (a == null) a = IDS.get(k);
        return a == null ? null : new NetPlugin(a, k);
    }

    /** 编辑器插件目录 */
    public static List<PluginInfo> catalog() {
        List<PluginInfo> out = new ArrayList<>();
        out.add(new PluginInfo("http", "网络", "请求,网络",
                "@plugin(http) | get | https://api.example.com/x | 完成信号 | @var(回复)",
                "HTTP 请求（get/post）：异步执行，把返回文本写进输出位并发出完成信号；失败写说明文本，不中断剧情"));
        out.add(new PluginInfo("llm", "网络", "ai,大模型",
                "@plugin(llm) | 用一句话安慰玩家 | 回复到了 | @var(回复)",
                "大模型对话：按 [option] 里的 AI密钥/AI地址/AI模型 变量调用 chat 接口，把回复写进变量（无密钥时不发请求）"));
        return out;
    }

    @Override
    protected String group() { return "网络"; }

    @Override
    public String description() {
        return act == Act.HTTP
                ? "HTTP 请求：异步 get/post，结果写进变量并发出完成信号（失败也会继续）"
                : "大模型对话：调用 OpenAI 兼容接口，把回复写进变量（配置见 AI密钥/AI地址/AI模型）";
    }

    @Override
    public String usage() {
        return act == Act.HTTP
                ? "@plugin(http) | get|post | 地址 | 请求体（post 用） | 完成信号（可空） | @var(输出)"
                : "@plugin(llm) | 提示词 | 完成信号（可空） | @var(输出)";
    }

    // =====================================================================

    @Override
    protected void run(PluginContext ctx, String[] in, String[] out) {
        if (act == Act.HTTP) runHttp(ctx, in, out);
        else runLlm(ctx, in, out);
    }

    /** http | get|post | 地址 | [请求体] | [完成信号] | @var(输出) */
    private void runHttp(PluginContext ctx, String[] in, String[] out) {
        if (count(in) < 3) {
            warn(ctx, "参数不足（用法：" + usage() + "）");
            return;
        }
        String method = arg(in, 0).toLowerCase(Locale.ROOT);
        String url = arg(in, 1);
        boolean post = method.startsWith("post") || method.equals("提交");
        String body = post ? raw(in, 2) : "";
        // 有请求体时参数整体后移一位
        String signal = arg(in, post ? 3 : 2);
        int timeoutIndex = post ? 4 : 3;
        int timeout = i(arg(in, timeoutIndex), 15);
        if (url.isEmpty()) {
            warn(ctx, "没有给请求地址");
            return;
        }
        send(ctx, out, post ? "POST" : "GET", url, body, signal, timeout, Map.of(), null);
    }

    /** llm | 提示词 | [完成信号] | @var(输出) */
    private void runLlm(PluginContext ctx, String[] in, String[] out) {
        if (count(in) < 2) {
            warn(ctx, "参数不足（用法：" + usage() + "）");
            return;
        }
        String prompt = raw(in, 0);
        String signal = arg(in, 1);
        int timeout = i(arg(in, 2), 30);
        String key = ctx.var("AI密钥", "").trim();
        String base = ctx.var("AI地址", "https://api.deepseek.com/chat/completions").trim();
        String model = ctx.var("AI模型", "deepseek-chat").trim();
        String system = ctx.var("AI系统词", "你是一个中文视觉小说里的角色，回答要短（一到两句），口语化。");
        if (key.isEmpty()) {
            setOut(out, "（还没配置 AI密钥，先把 [option] 里的 AI密钥 填上）");
            log(ctx, "llm：没有配置 AI密钥，未发送请求");
            emitDone(ctx, signal, "无密钥");
            return;
        }
        String body = "{\"model\":\"" + esc(model) + "\",\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + esc(system) + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + esc(prompt) + "\"}],"
                + "\"stream\":false}";
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + key);
        send(ctx, out, "POST", base, body, signal, timeout, headers, "llm");
    }

    /** 真正发请求（异步，不阻塞界面） */
    private void send(PluginContext ctx, String[] out, String method, String url, String body,
                      String signal, int timeoutSeconds, Map<String, String> headers, String kind) {
        final String outValue = out != null && out.length > 0 ? out[out.length - 1] : "";
        HttpRequest.Builder rb;
        try {
            rb = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                    .header("User-Agent", "ds-adventure/1.0")
                    .header("Accept", "*/*");
        } catch (RuntimeException e) {
            fail(ctx, out, "（请求地址不合法：" + url + "）", signal, outValue);
            return;
        }
        rb.header("Content-Type", "application/json; charset=utf-8");
        for (Map.Entry<String, String> e : headers.entrySet()) rb.header(e.getKey(), e.getValue());
        if ("POST".equals(method)) rb.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        else rb.GET();

        log(ctx, "发起 " + method + " " + url + (kind == null ? "" : "（" + kind + "）"));
        CLIENT.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((resp, err) -> {
                    String value;
                    if (err != null) {
                        value = "（请求失败：" + shortReason(err) + "）";
                    } else {
                        String text = resp.body() == null ? "" : resp.body();
                        if (kind != null) {
                            String reply = extractContent(text);
                            value = reply.isEmpty() ? shorten(text, 400) : reply;
                        } else {
                            value = shorten(text, 2000);
                        }
                    }
                    finishAsync(ctx, out, value, signal, outValue);
                });
    }

    /** 回到引擎里写结果、发完成信号（切回 JavaFX 线程） */
    private void finishAsync(PluginContext ctx, String[] out, String value, String signal, String outValue) {
        if (ctx == null) return;
        ctx.onUi(() -> {
            // ① 常规回写：万一请求极快，在本次调用内就返回了，返回值仍然有效
            if (out != null && out.length > 0) out[out.length - 1] = value;
            // ② 异步回写：请求返回时本次调用早就结束了，返回值已经没人看，
            //    所以用“原始参数里的输出位变量名”直接把结果写进变量
            String varName = ctx.outputVarName();
            if (!varName.isEmpty()) {
                ctx.setVar(varName, value);
                Logs.info("[Plugin:net] 异步结果 → " + varName + " = " + shorten(value, 80));
            }
            log(ctx, "网络返回：" + shorten(value, 120));
            emitDone(ctx, signal, value);
        });
    }

    private void emitDone(PluginContext ctx, String signal, String value) {
        if (ctx == null) return;
        String name = signal == null || signal.isBlank() ? DEFAULT_SIGNAL : signal;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("结果", value == null ? "" : value);
        ctx.emit("", name, params);
    }

    private void fail(PluginContext ctx, String[] out, String message, String signal, String outValue) {
        setOut(out, message);
        warn(ctx, message);
        emitDone(ctx, signal, message);
    }

    // =====================================================================

    /** 从 OpenAI 兼容响应里抠出 choices[0].message.content（抠不到就返回空串） */
    static String extractContent(String json) {
        if (json == null || json.isEmpty()) return "";
        Matcher m = Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
        if (m.find()) return unescapeJson(m.group(1)).trim();
        Matcher e = Pattern.compile("\"error\"\\s*:\\s*\\{[^}]*\"message\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
        if (e.find()) return "（接口报错：" + unescapeJson(e.group(1)) + "）";
        return "";
    }

    private static String unescapeJson(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'u' -> {
                        if (i + 4 < s.length()) {
                            try {
                                sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                                i += 4;
                            } catch (NumberFormatException ignored) { /* 保留原样 */ }
                        }
                    }
                    default -> sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        String t = s.strip();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private static String shortReason(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String msg = root.getMessage();
        return msg == null || msg.isBlank() ? root.getClass().getSimpleName() : msg;
    }
}

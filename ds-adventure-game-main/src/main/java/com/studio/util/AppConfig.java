package com.studio.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * config.ini 读写器（键值对风格，# 开头为注释）。
 * <p>
 * 读取器(Player)启动时根据它确定“要加载的地图文件夹”，
 * 编辑器(Studio)用它记忆最近打开的地图，二者共用同一文件。
 */
public final class AppConfig {

    /** 工程根目录下默认配置文件名 */
    public static final String FILE_NAME = "config.ini";

    private final Path file;
    private final LinkedHashMap<String, String> values = new LinkedHashMap<>();

    public AppConfig(Path file) {
        this.file = file;
    }

    /** 读取工程根目录 config.ini（不存在时返回仅含默认值的实例，不主动建文件） */
    public static AppConfig loadDefault() {
        Path p = Path.of(System.getProperty("user.dir"), FILE_NAME);
        return load(p);
    }

    public static AppConfig load(Path file) {
        AppConfig c = new AppConfig(file);
        if (Files.exists(file)) {
            try {
                for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String line = raw.strip();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    c.values.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            } catch (IOException e) {
                Logs.warn("读取配置文件失败: " + file + "（" + e.getMessage() + "），使用默认值");
            }
        }
        return c;
    }

    public String get(String key) { return values.get(key); }

    public String get(String key, String def) {
        String v = values.get(key);
        return v == null || v.isBlank() ? def : v;
    }

    public int getInt(String key, int def) {
        String v = get(key);
        try { return v == null ? def : Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { return def; }
    }

    public double getDouble(String key, double def) {
        String v = get(key);
        try { return v == null ? def : Double.parseDouble(v.trim()); }
        catch (NumberFormatException e) { return def; }
    }

    public void set(String key, String value) {
        if (value == null || value.isBlank()) values.remove(key);
        else values.put(key, value);
    }

    /** 把内存中的改动写回磁盘（保留原文件里的注释头） */
    public void save() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# ============================================================\n");
            sb.append("# 剧情游戏编辑器 / 读取器 —— 全局配置（自动维护）\n");
            sb.append("# 读取器(Player)依据 map.folder 加载地图文件夹；\n");
            sb.append("# 插件文件夹 plugins.dir 存放外部插件 .class/.jar。\n");
            sb.append("# ============================================================\n");
            for (Map.Entry<String, String> e : values.entrySet()) {
                sb.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Logs.warn("写回配置文件失败: " + file + "（" + e.getMessage() + "）");
        }
    }

    @Override
    public String toString() {
        return "AppConfig{" + file + ", " + values.size() + " 项}";
    }
}

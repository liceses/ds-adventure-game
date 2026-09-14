package com.studio.saves;

import com.studio.util.Logs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 存档管理器 —— 负责地图文件夹下 saves 目录的读写。
 *
 * <p>目录结构：</p>
 * <pre>
 *   maps/某地图/
 *   ├── scenario.txt
 *   ├── resources/…
 *   └── saves/                 ← 自动创建；可含多个 .txt 存档文件
 *       ├── slot1.txt
 *       └── 自定义名.txt
 * </pre>
 *
 * <p>文件名即“存档槽位”。读取/写入由地图里的按钮动作（save / load）或
 * 后端插件通过 {@link GameSaveManager} 调用触发 —— 引擎不提供专门存档界面，
 * 界面与槽位布局完全由地图工程师自定义。</p>
 */
public class GameSaveManager {

    private final File mapDir;

    public GameSaveManager(File mapDir) {
        this.mapDir = mapDir;
    }

    /** saves 目录（未创建时调用方可用 ensureDir） */
    public File dir() {
        return new File(mapDir, "saves");
    }

    public boolean ensureDir() {
        File d = dir();
        if (d.isDirectory()) return true;
        return d.mkdirs() || d.isDirectory();
    }

    // =====================================================================
    // 文件名工具
    // =====================================================================

    /** 槽位名规范化：空 → slot1.txt；无扩展名自动补 .txt；禁止路径穿越 */
    public static String normalizeName(String name) throws IOException {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) n = "slot1";
        if (n.indexOf('/') >= 0 || n.indexOf('\\') >= 0 || n.equals("..")
                || n.startsWith("..")) {
            throw new IOException("非法存档文件名（不允许路径分隔符）: " + name);
        }
        return n.endsWith(".txt") ? n : n + ".txt";
    }

    public File fileOf(String name) throws IOException {
        return new File(dir(), normalizeName(name));
    }

    // =====================================================================
    // 槽位列表 / 判断
    // =====================================================================

    /** 列出 saves 目录下全部 .txt 存档（按文件名排序） */
    public List<String> listSaveFiles() {
        List<String> result = new ArrayList<>();
        File d = dir();
        if (!d.isDirectory()) return result;
        File[] files = d.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));
        if (files == null) return result;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File f : files) result.add(f.getName());
        return result;
    }

    public boolean exists(String name) {
        try {
            return fileOf(name).isFile();
        } catch (IOException e) {
            return false;
        }
    }

    // =====================================================================
    // 读写 / 删除
    // =====================================================================

    /** 读取一个存档；不存在或解析失败返回 null（警告已记日志） */
    public SaveData read(String name) {
        try {
            File f = fileOf(name);
            if (!f.isFile()) {
                Logs.info("存档不存在: " + f.getAbsolutePath());
                return null;
            }
            SaveData data = SaveFileCodec.readOrNull(f.toPath());
            if (data == null) {
                Logs.warn("存档读取失败或为空: " + f.getAbsolutePath());
            }
            return data;
        } catch (IOException e) {
            Logs.error("读取存档失败 " + name, e);
            return null;
        }
    }

    /** 写入一个存档（覆盖同名文件）；失败抛异常 */
    public void write(String name, SaveData data) throws IOException {
        if (!ensureDir()) {
            throw new IOException("无法创建存档目录: " + dir().getAbsolutePath());
        }
        File f = fileOf(name);
        SaveFileCodec.write(data, f.toPath());
        Logs.info("存档已写入: " + f.getAbsolutePath());
    }

    public boolean delete(String name) {
        try {
            File f = fileOf(name);
            boolean ok = f.exists() && f.delete();
            Logs.info("删除存档 " + f.getName() + " → " + ok);
            return ok;
        } catch (IOException e) {
            Logs.warn("删除存档失败: " + e.getMessage());
            return false;
        }
    }

    /** 供需要路径的插件使用 */
    public Path pathOf(String name) throws IOException {
        return fileOf(name).toPath();
    }

    @Override
    public String toString() {
        return "GameSaveManager{" + dir().getAbsolutePath() + "}";
    }
}

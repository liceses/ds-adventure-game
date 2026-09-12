package com.studio.editor;

import com.studio.ui.Ui;
import com.studio.util.AppConfig;
import com.studio.util.Logs;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <b>外部编辑</b>：把当前地图复制一份到编辑器的临时目录，交给地图工程师用别的工具改，
 * 改完再导回来（覆盖原地图）。
 *
 * <p>为什么需要它：编辑器画布适合摆位置、连信号，但有时直接改 {@code scenario.txt} 文本、
 * 批量替换台词、或者在别的编辑器里对照着改更顺手。直接在原地图上改容易和编辑器的内存副本打架，
 * 所以这里走“复制 → 外部改 → 导入并重新加载”的流程。</p>
 *
 * <h3>目录约定</h3>
 * <pre>
 *   &lt;工程根&gt;/外部编辑/&lt;地图名&gt;_&lt;时间戳&gt;/          ← 本次会话的工作副本（工程师在这里改）
 *   &lt;工程根&gt;/外部编辑/&lt;地图名&gt;_&lt;时间戳&gt;/_来源.txt     ← 标记：来源地图路径 + 开始时间
 *   &lt;工程根&gt;/外部编辑/&lt;地图名&gt;_&lt;时间戳&gt;/_导入前备份/  ← 导入前自动备份的原地图
 * </pre>
 *
 * <p>目录名、会话记录都写在 {@code config.ini}（键 {@code editor.external.dir} /
 * {@code editor.external.session}），所以关掉编辑器再打开还能接着导入。</p>
 */
public final class ExternalEdit {

    /** 外部编辑根目录名（工程根下；<b>用 ASCII 命名</b>，避免中文目录带来的编码/工具兼容问题） */
    public static final String ROOT_NAME = "external-edit";
    /** 会话标记文件名 */
    public static final String MARKER = "_来源.txt";
    /** 导入前备份目录名 */
    public static final String BACKUP = "_导入前备份";

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ExternalEdit() { }

    // =====================================================================
    // 目录
    // =====================================================================

    /** 外部编辑根目录（工程根/外部编辑） */
    public static File rootDir() {
        return new File(System.getProperty("user.dir"), ROOT_NAME);
    }

    /** 本次会话目录（未开始时返回 null） */
    public static File sessionDir(AppConfig config) {
        String path = config == null ? null : config.get("editor.external.session");
        if (path == null || path.isBlank()) return null;
        File f = new File(path);
        return f.isDirectory() ? f : null;
    }

    /** 会话对应的“来源地图”（读 _来源.txt 的第一行；读不到返回 null） */
    public static File sourceOf(File session) {
        if (session == null) return null;
        File marker = new File(session, MARKER);
        if (!marker.isFile()) return null;
        try {
            for (String line : Files.readAllLines(marker.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                String s = line.strip();
                if (s.startsWith("来源=")) {
                    File f = new File(s.substring("来源=".length()).trim());
                    return f.isDirectory() ? f : null;
                }
            }
        } catch (IOException e) {
            Logs.warn("[ExternalEdit] 读取来源标记失败：" + e.getMessage());
        }
        return null;
    }

    /**
     * 开始一次外部编辑：把 {@code source} 整个复制到新的会话目录，并记进 config。
     *
     * @return 会话目录（复制失败返回 null）
     */
    public static File beginSession(File source, AppConfig config) throws IOException {
        if (source == null || !source.isDirectory()) throw new IOException("地图目录不存在：" + source);
        File root = rootDir();
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("无法创建外部编辑目录：" + root);
        String name = source.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
        File session = new File(root, name + "_" + LocalDateTime.now().format(STAMP));
        int n = 0;
        while (session.exists() && n++ < 100) {
            session = new File(root, name + "_" + LocalDateTime.now().format(STAMP) + "-" + n);
        }
        copyTree(source, session);
        Files.write(new File(session, MARKER).toPath(),
                ("来源=" + source.getAbsolutePath() + "\n开始=" + LocalDateTime.now() + "\n"
                        + "\n本目录是编辑器复制出来的工作副本：改完回到编辑器点「导入外部更改」即可覆盖原地图。\n")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (config != null) {
            config.set("editor.external.dir", root.getAbsolutePath());
            config.set("editor.external.session", session.getAbsolutePath());
            config.save();
        }
        Logs.info("[ExternalEdit] 已创建工作副本：" + session.getAbsolutePath() + "（来源 " + source + "）");
        return session;
    }

    // =====================================================================
    // 差异 / 导入
    // =====================================================================

    /** 相对原地图有差异的文件数（内容比较；忽略标记与备份目录） */
    public static int countChangedFiles(File source, File session) {
        return changedFiles(source, session).size();
    }

    /** 列出有差异的文件（相对路径） */
    public static List<String> changedFiles(File source, File session) {
        List<String> out = new ArrayList<>();
        if (source == null || session == null || !session.isDirectory()) return out;
        for (File f : listFiles(session)) {
            String rel = relativize(session, f);
            if (isInternal(rel)) continue;
            File origin = new File(source, rel);
            if (!origin.isFile() || !sameContent(origin, f)) out.add(rel);
        }
        // 原地图里有、副本里被删掉的文件
        if (source.isDirectory()) {
            for (File f : listFiles(source)) {
                String rel = relativize(source, f);
                if (isInternal(rel)) continue;
                if (!new File(session, rel).isFile()) out.add(rel + "（副本里已删除）");
            }
        }
        return out;
    }

    /**
     * 把副本导回原地图：<b>先自动备份</b>原地图到会话目录的 {@link #BACKUP}，再整体覆盖。
     *
     * @return 备份目录（没有覆盖任何文件时返回 null）
     */
    public static File importChanges(File source, File session) throws IOException {
        if (source == null || !source.isDirectory()) throw new IOException("原地图目录不存在：" + source);
        if (session == null || !session.isDirectory()) throw new IOException("工作副本不存在：" + session);
        File backup = new File(session, BACKUP);
        // 备份（先清掉上一次的备份内容，避免越滚越大）
        deleteTree(backup);
        copyTree(source, backup);
        // 覆盖回去（标记文件与备份目录由 copyTree 自动跳过）
        copyTree(session, source);
        Logs.info("[ExternalEdit] 已导入外部更改：" + session.getAbsolutePath() + " → " + source.getAbsolutePath());
        return backup;
    }

    /** 结束会话记录（导入后调用；工作副本本身保留，方便对照） */
    public static void endSession(AppConfig config) {
        if (config == null) return;
        config.set("editor.external.session", "");
        config.save();
    }

    /** 在系统文件管理器里打开目录（失败只记日志） */
    public static void openInExplorer(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                java.awt.Desktop.getDesktop().open(dir);
                return;
            }
        } catch (Exception e) {
            Logs.warn("[ExternalEdit] Desktop.open 失败：" + e.getMessage());
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
            if (os.contains("win")) new ProcessBuilder("explorer.exe", dir.getAbsolutePath()).start();
            else if (os.contains("mac")) new ProcessBuilder("open", dir.getAbsolutePath()).start();
            else new ProcessBuilder("xdg-open", dir.getAbsolutePath()).start();
        } catch (Exception e) {
            Logs.warn("[ExternalEdit] 打开目录失败：" + e.getMessage());
        }
    }

    // =====================================================================
    // 三选一对话框：导入更改 / 重新复制一份 / 取消
    // =====================================================================

    /** 外部编辑遇到“已有一份改过的副本”时的选择 */
    public enum Next { IMPORT, RESTART, CANCEL }

    /**
     * 弹窗询问接下来怎么做。
     *
     * @param changed 已改动的文件数
     */
    public static Next askNext(Stage owner, File source, File session, int changed) {
        ButtonType bImport = new ButtonType("📥 导入更改并覆盖原地图", ButtonBar.ButtonData.OK_DONE);
        ButtonType bRestart = new ButtonType("🗂 重新复制一份", ButtonBar.ButtonData.OTHER);
        ButtonType bCancel = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(owner);
        alert.setTitle("外部编辑");
        alert.setHeaderText("上一次的外部编辑还没导入（有 " + changed + " 个文件被改过）");
        alert.setContentText("原地图：" + (source == null ? "（未知）" : source.getAbsolutePath())
                + "\n工作副本：" + (session == null ? "（无）" : session.getName())
                + "\n\n要现在导入，还是丢掉这份、重新复制一份当前地图？");
        alert.getButtonTypes().setAll(bImport, bRestart, bCancel);
        Optional<ButtonType> r = alert.showAndWait();
        if (r.isEmpty() || r.get() == bCancel) return Next.CANCEL;
        return r.get() == bImport ? Next.IMPORT : Next.RESTART;
    }

    // =====================================================================
    // 文件工具
    // =====================================================================

    private interface RelFilter { boolean accept(String rel); }

    /** 递归复制目录；标记文件与备份目录不会被复制（两个方向都用同一套规则） */
    private static void copyTree(File from, File to) throws IOException {
        if (!to.isDirectory() && !to.mkdirs()) throw new IOException("无法创建目录：" + to);
        Path fromPath = from.toPath();
        Path toPath = to.toPath();
        Files.walkFileTree(fromPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String rel = fromPath.relativize(dir).toString().replace('\\', '/');
                if (!rel.isEmpty() && isInternal(rel)) return FileVisitResult.SKIP_SUBTREE;
                Files.createDirectories(toPath.resolve(rel));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String rel = fromPath.relativize(file).toString().replace('\\', '/');
                if (isInternal(rel)) return FileVisitResult.CONTINUE;
                Files.copy(file, toPath.resolve(rel), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteTree(File dir) throws IOException {
        if (dir == null || !dir.exists()) return;
        Path p = dir.toPath();
        Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.deleteIfExists(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** 会话目录自身的文件（标记、备份）不参与差异比较与导入 */
    private static boolean isInternal(String rel) {
        String r = rel.replace('\\', '/');
        return r.equals(MARKER) || r.equals(BACKUP) || r.startsWith(BACKUP + "/");
    }

    private static List<File> listFiles(File dir) {
        List<File> out = new ArrayList<>();
        if (dir == null || !dir.isDirectory()) return out;
        try {
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    out.add(file.toFile());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            Logs.warn("[ExternalEdit] 遍历目录失败：" + e.getMessage());
        }
        return out;
    }

    private static String relativize(File base, File f) {
        return base.toPath().relativize(f.toPath()).toString().replace('\\', '/');
    }

    private static boolean sameContent(File a, File b) {
        try {
            if (a.length() != b.length()) return false;
            return java.util.Arrays.equals(Files.readAllBytes(a.toPath()), Files.readAllBytes(b.toPath()));
        } catch (IOException e) {
            return false;
        }
    }
}

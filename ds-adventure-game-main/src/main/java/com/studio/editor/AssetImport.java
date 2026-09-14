package com.studio.editor;

import com.studio.model.GameProject;
import com.studio.model.StoryNode;
import com.studio.util.Logs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 素材导入助手：把外部图片/音频复制进地图的 resources 目录，
 * 返回“相对地图根目录”的引用路径（editor 保存的就是这种相对路径）。
 */
final class AssetImport {

    private AssetImport() { }

    /**
     * @param node   正在编辑的节点
     * @param audio  true=音频资源，false=图片资源
     * @return 选择并导入后的相对路径（形如 resources/images/xxx.png）；取消返回 null
     */
    static String pickAndImport(javafx.stage.Window owner, GameProject project,
                                StoryNode node, boolean audio) {
        if (project == null || project.rootDir() == null) return null;
        File root = project.rootDir();
        String sub = audio ? "audio" : "images";
        File destDir = new File(root, "resources/" + sub);

        String ext = audio ? "音频" : "图片";
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("选择要导入的" + ext + "文件（自动复制进地图 resources 目录）");
        fc.getExtensionFilters().add(audio
                ? new javafx.stage.FileChooser.ExtensionFilter("音频文件", "*.mp3", "*.wav", "*.ogg", "*.m4a", "*.flac")
                : new javafx.stage.FileChooser.ExtensionFilter("图片文件", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp", "*.bmp"));
        File picked = fc.showOpenDialog(owner);
        if (picked == null) return null;

        try {
            // 若素材本来就在地图内，直接引用
            File canonical = picked.getCanonicalFile();
            File rootCanonical = root.getCanonicalFile();
            if (canonical.getPath().startsWith(rootCanonical.getPath())) {
                return canonical.getPath().substring(rootCanonical.getPath().length())
                        .replace('\\', '/').replaceFirst("^/+", "");
            }
            if (!destDir.exists() && !destDir.mkdirs()) {
                throw new IOException("无法创建素材目录: " + destDir);
            }
            Path target = uniqueTarget(destDir.toPath(), picked.getName());
            Files.copy(picked.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            String rel = "resources/" + sub + "/" + target.getFileName();
            Logs.info("素材已导入: " + picked + " → " + rel);
            return rel;
        } catch (IOException e) {
            com.studio.ui.Ui.error(owner, "导入失败", "复制素材失败: " + e.getMessage(), e);
            return null;
        }
    }

    private static Path uniqueTarget(Path dir, String name) {
        Path target = dir.resolve(name);
        if (!Files.exists(target)) return target;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String suffix = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; ; i++) {
            Path t = dir.resolve(base + "_" + i + suffix);
            if (!Files.exists(t)) return t;
        }
    }
}

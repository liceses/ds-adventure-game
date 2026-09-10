package com.studio.ui;

import com.studio.util.Logs;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 图片加载助手：优先读取“地图根目录下的相对路径”素材；
 * 文件缺失或解码失败时，现场绘制一张“渐变色占位图”，保证画布/播放器永不白屏。
 */
public final class FxAssets {

    private static final Map<String, Image> CACHE = new HashMap<>();

    private FxAssets() { }

    /** 依据地图根目录 + 相对路径加载图片 */
    public static Image loadRooted(File mapRoot, String relPath) {
        return loadRooted(mapRoot, relPath, 320, 200, "");
    }

    /**
     * @param fallbackW/fallbackH 占位图尺寸（通常取节点尺寸）
     * @param label               占位图文字提示（可空）
     */
    public static Image loadRooted(File mapRoot, String relPath,
                                   double fallbackW, double fallbackH, String label) {
        String key = mapRoot.getAbsolutePath() + "|" + (relPath == null ? "" : relPath);
        Image cached = CACHE.get(key);
        if (cached != null) return cached;

        Image img = null;
        if (relPath != null && !relPath.isBlank() && mapRoot != null) {
            File file;
            try {
                file = new File(mapRoot, relPath.replace('\\', '/')).getCanonicalFile();
                if (file.exists() && file.getPath().startsWith(mapRoot.getCanonicalFile().getPath())) {
                    img = new Image(file.toURI().toString());
                }
            } catch (Exception e) {
                Logs.warn("读取图片失败 " + relPath + ": " + e.getMessage());
            }
        }
        if (img == null || img.isError()) {
            String hint = (label == null || label.isBlank())
                    ? (relPath == null ? "无素材" : "缺图:" + relPath)
                    : label;
            img = placeholder((int) Math.max(32, fallbackW), (int) Math.max(32, fallbackH), hint);
        }
        CACHE.put(key, img);
        return img;
    }

    /** 现场绘制占位图（对角渐变 + 网纹，标签哈希决定配色） */
    public static Image placeholder(int w, int h, String label) {
        w = Math.max(8, w);
        h = Math.max(8, h);
        WritableImage img = new WritableImage(w, h);
        PixelWriter pw = img.getPixelWriter();

        int seed = label == null ? 7 : label.hashCode();
        int hue = Math.floorMod(seed, 360);
        // 取 HSV 的两个邻近色调作为渐变色
        java.awt.Color c0 = java.awt.Color.getHSBColor(hue / 360f, 0.42f, 0.32f);
        java.awt.Color c1 = java.awt.Color.getHSBColor(((hue + 55) % 360) / 360f, 0.50f, 0.20f);
        java.awt.Color band = java.awt.Color.getHSBColor(((hue + 110) % 360) / 360f, 0.45f, 0.42f);

        for (int y = 0; y < h; y++) {
            double ty = (double) y / Math.max(1, h - 1);
            for (int x = 0; x < w; x++) {
                double tx = (double) x / Math.max(1, w - 1);
                double m = (tx + ty) / 2.0;
                double r = c0.getRed() * (1 - m) + c1.getRed() * m;
                double g = c0.getGreen() * (1 - m) + c1.getGreen() * m;
                double b = c0.getBlue() * (1 - m) + c1.getBlue() * m;
                // 斜向网纹（每 26px 提亮一档）
                if (Math.floorMod(x + y, 26) < 2) {
                    r = r * 0.7 + band.getRed() * 0.3;
                    g = g * 0.7 + band.getGreen() * 0.3;
                    b = b * 0.7 + band.getBlue() * 0.3;
                }
                pw.setColor(x, y, javafx.scene.paint.Color.color(
                        Math.min(1, r), Math.min(1, g), Math.min(1, b)));
            }
        }
        return img;
    }

    /** 清空缓存（素材文件可能被外部替换） */
    public static void clearCache() {
        CACHE.clear();
    }
}

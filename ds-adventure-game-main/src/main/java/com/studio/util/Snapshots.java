package com.studio.util;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 画面截图工具：把 JavaFX {@link Image} 存成 PNG。
 *
 * <p>刻意<b>不依赖 javafx.swing</b>（工程没有引入那个模块），而是自己逐像素搬到
 * {@link BufferedImage} 再交给 ImageIO，所以在只有 javafx-base/graphics/controls 的
 * 依赖下也能用（自带插件 {@code @plugin(screenshot)} 就靠它）。</p>
 */
public final class Snapshots {

    private Snapshots() { }

    /** 把图像写成 PNG；成功返回 true（父目录不存在会自动创建） */
    public static boolean save(Image image, File out) {
        if (image == null || out == null) return false;
        try {
            File parent = out.getParentFile();
            if (parent != null && !parent.isDirectory()) parent.mkdirs();
            int w = (int) Math.max(1, Math.rint(image.getWidth()));
            int h = (int) Math.max(1, Math.rint(image.getHeight()));
            BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            PixelReader pr = image.getPixelReader();
            if (pr != null) {
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) buf.setRGB(x, y, pr.getArgb(x, y));
                }
            }
            String name = out.getName().toLowerCase(java.util.Locale.ROOT);
            String format = name.endsWith(".jpg") || name.endsWith(".jpeg") ? "jpg" : "png";
            return ImageIO.write(buf, format, out);
        } catch (Exception e) {
            Logs.warn("[Snapshots] 保存截图失败 " + out + "：" + e.getMessage());
            return false;
        }
    }

    /** 把一张节点/场景快照裁成不超过 maxWidth 的缩略图（避免大图写盘太慢） */
    public static WritableImage scaled(Image src, double maxWidth) {
        if (src == null) return null;
        double w = src.getWidth();
        double h = src.getHeight();
        if (w <= maxWidth || maxWidth <= 0) {
            return src instanceof WritableImage wi ? wi : new WritableImage(src.getPixelReader(), (int) w, (int) h);
        }
        double scale = maxWidth / w;
        javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(src);
        view.setFitWidth(w * scale);
        view.setFitHeight(h * scale);
        javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
        params.setFill(javafx.scene.paint.Color.TRANSPARENT);
        return view.snapshot(params, null);
    }
}

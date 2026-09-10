package com.studio.util;

import com.studio.model.GameProject;
import com.studio.model.GameScene;
import com.studio.model.NodeType;
import com.studio.model.StoryNode;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * 占位素材生成器 —— 用 java.awt（纯 JDK，无需 JavaFX）把“占位图片”
 * 画成真实 PNG 文件。
 *
 * <p>用途：</p>
 * <ul>
 *   <li>“文件→新建”地图时自动生成背景与角色立绘占位图，保证导出的地图文件夹开箱即用；</li>
 *   <li>读取器首次运行、示例地图不存在时生成示例素材；</li>
 *   <li>导出地图时，对引用却缺失的图片按节点尺寸兜底生成（见 synthesizeMissing）。</li>
 * </ul>
 *
 * <p>注意：真实素材（美术立绘/音乐）仍由美工放入 resources 后覆盖同名文件即可，
 * 占位图不会在文件已存在时重复覆盖。</p>
 */
public final class MapAssets {

    /** 素材类型 */
    public enum Kind { BACKGROUND, CHARACTER }

    private MapAssets() { }

    /**
     * 确保 resourceRoot 下的 relPath 图片存在；不存在则按 kind 绘制占位图。
     *
     * @param resourceRoot 地图文件夹的 resources 目录
     * @param relPath      相对 resources 的路径，如 "images/hero_left.png"
     * @return 图片文件（存在或新建）
     */
    public static File ensure(File resourceRoot, String relPath, Kind kind,
                              int seed, String label, double w, double h) throws IOException {
        File target = new File(resourceRoot, relPath).getCanonicalFile();
        if (target.exists()) return target;
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建素材目录: " + parent);
        }
        BufferedImage img = draw(kind, (int) w, (int) h, seed, label);
        if (!ImageIO.write(img, "png", target)) {
            throw new IOException("PNG 编码器不可用: " + target);
        }
        Logs.info("已生成占位素材: " + target.getAbsolutePath());
        return target;
    }

    /** 兜底：遍历工程所有图片类节点，把仍缺失的素材按节点尺寸生成到 targetResources */
    public static void synthesizeMissing(GameProject project, File targetResources) {
        int made = 0;
        for (GameScene scene : project.scenes().values()) {
            for (StoryNode n : scene.nodes()) {
                if (n.getType() != NodeType.MUSIC && !n.getPath().isBlank()
                        && looksLikeImage(n.getPath())) {
                    try {
                        File f = resolveUnderResources(targetResources, n.getPath());
                        if (f != null && !f.exists()) {
                            Kind kind = n.getType() == NodeType.CHARACTER ? Kind.CHARACTER : Kind.BACKGROUND;
                            double w = Math.max(16, n.getWidth());
                            double h = Math.max(16, n.getHeight());
                            ensure(targetResources, relPathOf(targetResources, n.getPath()),
                                    kind, n.getPath().hashCode(), n.getId(), w, h);
                            made++;
                        }
                    } catch (IOException e) {
                        Logs.warn("兜底生成素材失败 " + n.getPath() + ": " + e.getMessage());
                    }
                }
            }
        }
        if (made > 0) Logs.info("导出兜底生成了 " + made + " 个缺失素材");
    }

    private static boolean looksLikeImage(String path) {
        String p = path.toLowerCase();
        return p.endsWith(".png") || p.endsWith(".jpg") || p.endsWith(".jpeg")
                || p.endsWith(".gif") || p.endsWith(".webp") || p.endsWith(".bmp");
    }

    /** 把 resources 相对路径解析为资源目录下的绝对文件（非法越界路径返回 null） */
    private static File resolveUnderResources(File resourceRoot, String relPath) {
        File base;
        try {
            base = resourceRoot.getCanonicalFile();
            File f = new File(base, relPath).getCanonicalFile();
            return f.getPath().startsWith(base.getPath()) ? f : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static String relPathOf(File resourceRoot, String relPath) {
        // 相对 resources 目录（去 resources 前缀/绝对路径干扰）
        String p = relPath.replace('\\', '/');
        String prefix = "resources/";
        if (p.startsWith(prefix)) p = p.substring(prefix.length());
        return p;
    }

    // =====================================================================
    // 音频占位：合成一段极简正弦波 WAV（22050Hz / 16bit / 单声道）
    // =====================================================================

    /**
     * 确保 WAV 音频占位文件存在（不存在则合成一段轻柔正弦音）。
     * 真实背景音乐放入同名路径即可覆盖。
     */
    public static File ensureWav(File resourceRoot, String relPath,
                                 double seconds, double freqHz) throws IOException {
        File target = new File(resourceRoot, relPath).getCanonicalFile();
        if (target.exists()) return target;
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建音频目录: " + parent);
        }
        writeSineWav(target, seconds, freqHz);
        Logs.info("已生成占位音频: " + target.getAbsolutePath());
        return target;
    }

    private static void writeSineWav(File f, double seconds, double freqHz) throws IOException {
        int sampleRate = 22050;
        int n = (int) (sampleRate * seconds);
        int dataLen = n * 2;
        try (java.io.OutputStream out = new java.io.BufferedOutputStream(new java.io.FileOutputStream(f))) {
            // RIFF 头
            writeAscii(out, "RIFF");
            writeLEInt(out, 36 + dataLen);
            writeAscii(out, "WAVE");
            writeAscii(out, "fmt ");
            writeLEInt(out, 16);            // fmt 块长度
            writeLEShort(out, 1);           // PCM
            writeLEShort(out, 1);           // 单声道
            writeLEInt(out, sampleRate);
            writeLEInt(out, sampleRate * 2); // 字节率
            writeLEShort(out, 2);           // 块对齐
            writeLEShort(out, 16);          // 位深
            writeAscii(out, "data");
            writeLEInt(out, dataLen);

            // 采样：基音 + 轻微颤音，包络淡入淡出避免爆音
            double tremolo = freqHz * 1.5;
            for (int i = 0; i < n; i++) {
                double t = (double) i / sampleRate;
                double env = Math.min(1.0, Math.min(t / 0.3, (seconds - t) / 0.4));
                double v = 0.22 * Math.sin(2 * Math.PI * freqHz * t)
                        + 0.06 * Math.sin(2 * Math.PI * tremolo * t);
                v *= Math.max(0, env);
                short s = (short) (v * 32767);
                out.write(s & 0xFF);
                out.write((s >> 8) & 0xFF);
            }
        }
    }

    private static void writeAscii(java.io.OutputStream out, String s) throws IOException {
        for (byte b : s.getBytes(java.nio.charset.StandardCharsets.US_ASCII)) out.write(b);
    }

    private static void writeLEInt(java.io.OutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 24) & 0xFF);
    }

    private static void writeLEShort(java.io.OutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    // =====================================================================
    // 绘图实现（java.awt）
    // =====================================================================

    private static BufferedImage draw(Kind kind, int w, int h, int seed, String label) {
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (kind == Kind.BACKGROUND) drawBackground(g, w, h, seed);
            else drawCharacter(g, w, h, seed, label);
        } finally {
            g.dispose();
        }
        return img;
    }

    /** 星空+远山背景 */
    private static void drawBackground(Graphics2D g, int w, int h, int seed) {
        // 夜空渐变
        Color top = hex(seed % 2 == 0 ? "#0a0f24" : "#101a2c");
        Color bottom = hex(seed % 3 == 0 ? "#2b2150" : "#241c3d");
        g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
        g.fillRect(0, 0, w, h);

        // 月亮
        int moonX = w * 3 / 4;
        int moonY = h / 5;
        g.setColor(new Color(255, 240, 200, 230));
        g.fillOval(moonX, moonY, w / 14, w / 14);
        g.setColor(new Color(255, 240, 200, 90));
        g.fillOval(moonX - w / 60, moonY + w / 28, w / 12, w / 16);

        // 星星
        Random r = new Random(seed * 7919L);
        g.setColor(Color.WHITE);
        int stars = w * h / 9000;
        for (int i = 0; i < stars; i++) {
            int x = r.nextInt(Math.max(1, w));
            int y = r.nextInt(Math.max(1, h * 2 / 3));
            int s = r.nextInt(3) + 1;
            g.setColor(new Color(255, 255, 255, 120 + r.nextInt(135)));
            g.fillOval(x, y, s, s);
        }

        // 远山剪影（两层）
        g.setColor(hex("#151022"));
        drawHills(g, w, h, h * 3 / 5, 120, seed);
        g.setColor(hex("#0b0816"));
        drawHills(g, w, h, h * 4 / 5, 90, seed + 7);

        // “占位素材”角标
        g.setFont(new Font("Microsoft YaHei", Font.BOLD, Math.max(12, w / 46)));
        g.setColor(new Color(255, 255, 255, 60));
        String mark = "占位背景 · resources 目录放入同名文件可替换";
        int tw = g.getFontMetrics().stringWidth(mark);
        g.drawString(mark, (w - tw) / 2, h - Math.max(18, h / 28));
    }

    private static void drawHills(Graphics2D g, int w, int h, int baseY, int amp, int seed) {
        Random r = new Random(seed);
        int[] xs = new int[w / 24 + 2];
        int[] ys = new int[w / 24 + 2];
        int n = 0;
        for (int x = 0; x <= w; x += 24) {
            xs[n] = x;
            ys[n] = baseY - r.nextInt(amp);
            n++;
        }
        int[] polyX = new int[n + 2];
        int[] polyY = new int[n + 2];
        System.arraycopy(xs, 0, polyX, 0, n);
        System.arraycopy(ys, 0, polyY, 0, n);
        polyX[n] = w; polyY[n] = h;
        polyX[n + 1] = 0; polyY[n + 1] = h;
        g.fillPolygon(polyX, polyY, n + 2);
    }

    /** 扁平剪影式角色立绘 */
    private static void drawCharacter(Graphics2D g, int w, int h, int seed, String label) {
        double u = Math.min(w, h) / 100.0; // 以 1% 为单位的“角色比例尺”
        Random r = new Random(seed * 31L);
        int hairIdx = r.nextInt(4);
        Color hair = switch (hairIdx) {
            case 0 -> hex("#3a2b52");  // 紫黑
            case 1 -> hex("#5b3a29");  // 棕
            case 2 -> hex("#2f4d3a");  // 深绿
            default -> hex("#4a3f6b"); // 蓝紫
        };
        Color cloth = switch (hairIdx) {
            case 0 -> hex("#7a5fd0");
            case 1 -> hex("#c96f4a");
            case 2 -> hex("#4a9d8f");
            default -> hex("#8f6a4a");
        };

        int cx = w / 2;
        // 头部中心与半径
        int headR = (int) (13 * u);
        int headY = (int) (18 * u);
        // 身体：肩部弧形到腰
        int bodyTop = headY + headR + (int) (2 * u);
        g.setColor(new Color(0, 0, 0, 90));
        g.fillOval(cx - (int) (34 * u), (int) (86 * u), (int) (68 * u), (int) (10 * u)); // 地面阴影

        // 身体（衣服）
        g.setColor(cloth);
        int[] bx = {cx - (int) (34 * u), cx - (int) (16 * u), cx + (int) (16 * u), cx + (int) (34 * u)};
        int[] by = {bodyTop, (int) (56 * u), (int) (56 * u), bodyTop};
        g.fillPolygon(bx, by, 4);
        g.fillRoundRect(cx - (int) (30 * u), bodyTop, (int) (60 * u), (int) (28 * u), (int) (10 * u), (int) (10 * u));

        // 领口皮肤三角 + 颈部
        g.setColor(hex("#f2c9a0"));
        g.fillRoundRect(cx - (int) (3.4 * u), bodyTop - (int) (2 * u), (int) (6.8 * u), (int) (6 * u), 3, 3);

        // 头部皮肤
        g.setColor(hex("#f7d3b0"));
        g.fillOval(cx - headR, headY, headR * 2, headR * 2);

        // 头发（后脑勺半圆 + 刘海）
        g.setColor(hair);
        g.fillOval(cx - headR - (int) (1.2 * u), headY - (int) (2 * u), headR * 2 + (int) (2.4 * u), (int) (headR * 1.9));
        // 刘海覆盖发际线
        int fringe = (int) (9 * u);
        g.fillArc(cx - headR - (int) (1.2 * u), headY - (int) (2 * u), headR * 2 + (int) (2.4 * u), headR * 2, 200, 140);
        g.fillRoundRect(cx - headR - (int) (1.2 * u), headY + headR - fringe, headR * 2 + (int) (2.4 * u), fringe + 1, 6, 6);

        // 脸
        g.setColor(hex("#f7d3b0"));
        int faceH = headR + (int) (3.2 * u);
        g.fillArc(cx - headR, headY + (int) (headR * 0.2), headR * 2, faceH * 2, 200, 140);

        // 眼睛
        g.setColor(hex("#241c3a"));
        int eyeY = headY + (int) (headR * 1.05);
        int eyeDX = (int) (headR * 0.42);
        g.fillOval(cx - eyeDX - (int) (1.6 * u), eyeY, (int) (3 * u), (int) (4.6 * u));
        g.fillOval(cx + eyeDX - (int) (1.4 * u), eyeY, (int) (3 * u), (int) (4.6 * u));

        // 腮红
        g.setColor(new Color(255, 140, 140, 90));
        g.fillOval(cx - (int) (headR * 0.9), eyeY + (int) (4 * u), (int) (4 * u), (int) (2.2 * u));
        g.fillOval(cx + (int) (headR * 0.9) - (int) (4 * u), eyeY + (int) (4 * u), (int) (4 * u), (int) (2.2 * u));

        // 占位角色名（底部徽章）
        if (label != null && !label.isBlank()) {
            g.setFont(new Font("Microsoft YaHei", Font.BOLD, Math.max(11, (int) (5.2 * u))));
            String tag = "占位 · " + label;
            int tw = g.getFontMetrics().stringWidth(tag);
            int px = cx - tw / 2 - 8;
            int py = h - (int) (6 * u);
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRoundRect(px - 4, py - g.getFontMetrics().getAscent() - 3, tw + 8,
                    g.getFontMetrics().getHeight() + 6, 10, 10);
            g.setColor(new Color(255, 255, 255, 200));
            g.drawString(tag, px, py);
        }

        // 细轮廓线
        g.setStroke(new BasicStroke(Math.max(1f, (float) (0.35 * u))));
        g.setColor(new Color(0, 0, 0, 40));
        g.drawOval(cx - headR, headY, headR * 2, headR * 2);
    }

    private static Color hex(String s) {
        try {
            return Color.decode(s);
        } catch (NumberFormatException e) {
            return Color.DARK_GRAY;
        }
    }
}

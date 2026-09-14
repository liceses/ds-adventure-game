package com.studio.util;

import java.io.File;

/**
 * 命令行工具：生成示例地图（含占位素材与扫雷插件演示）。
 *
 * <pre>
 *   java -cp ... com.studio.util.DemoMapBootstrapMain [目标文件夹]
 * </pre>
 *
 * 缺省生成到 工程根目录/maps/demo_map。
 */
public final class DemoMapBootstrapMain {

    private DemoMapBootstrapMain() { }

    public static void main(String[] args) throws Exception {
        File target;
        if (args.length > 0) {
            target = new File(args[0]);
        } else {
            target = new File(System.getProperty("user.dir"), "maps/demo_map");
        }
        if (new File(target, "scenario.txt").isFile()) {
            System.out.println("示例地图已存在: " + target.getAbsolutePath());
            return;
        }
        MapTemplateFactory.createMap(target, true);
        System.out.println("示例地图生成完成: " + target.getAbsolutePath());
        System.out.println("  ├─ scenario.txt");
        System.out.println("  └─ resources/（占位背景/立绘/音频）");
    }
}

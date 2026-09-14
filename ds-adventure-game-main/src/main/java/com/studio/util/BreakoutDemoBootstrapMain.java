package com.studio.util;

import java.io.File;

/**
 * 命令行工具：生成“打砖块”示例地图，并做解析回读校验。
 *
 * <pre>
 *   java -cp ... com.studio.util.BreakoutDemoBootstrapMain [目标文件夹]
 * </pre>
 * 缺省生成到 工程根目录/maps/demo_breakout。
 * 编辑器菜单「文件 → 打开打砖块演示地图…」调用的是同一个工厂类。
 */
public final class BreakoutDemoBootstrapMain {

    private BreakoutDemoBootstrapMain() { }

    public static void main(String[] args) throws Exception {
        File target = args.length > 0
                ? new File(args[0])
                : new File(System.getProperty("user.dir"), BreakoutMapFactory.DEFAULT_FOLDER);
        if (new File(target, "scenario.txt").isFile()) {
            System.out.println("打砖块示例地图已存在: " + target.getAbsolutePath());
        } else {
            BreakoutMapFactory.createMap(target);
            System.out.println("打砖块示例地图生成完成: " + target.getAbsolutePath());
        }
        int scenes = BreakoutMapFactory.validate(target);
        System.out.println("解析校验通过：共 " + scenes + " 个场景（起点 / 砖墙迷宫 / 结局·破墙之后）");
        System.out.println("事件 ID: " + BreakoutMapFactory.EVENT_ID
                + "（需与 plugins/plugins.ini 中的登记一致）");
    }
}

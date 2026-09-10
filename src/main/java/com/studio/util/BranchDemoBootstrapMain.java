package com.studio.util;

import java.io.File;

/**
 * 命令行工具：生成“分支冒险 + 2048”示例地图。
 *
 * <pre>
 *   java -cp ... com.studio.util.BranchDemoBootstrapMain [目标文件夹]
 * </pre>
 * 缺省生成到 工程根目录/maps/branch_demo_2048，并做解析回读校验。
 */
public final class BranchDemoBootstrapMain {

    private BranchDemoBootstrapMain() { }

    public static void main(String[] args) throws Exception {
        File target = args.length > 0
                ? new File(args[0])
                : new File(System.getProperty("user.dir"), BranchMapFactory.DEFAULT_FOLDER);
        if (new File(target, "scenario.txt").isFile()) {
            System.out.println("分支示例地图已存在: " + target.getAbsolutePath());
        } else {
            BranchMapFactory.createMap(target);
            System.out.println("分支示例地图生成完成: " + target.getAbsolutePath());
        }
        int scenes = BranchMapFactory.validate(target);
        System.out.println("解析校验通过：共 " + scenes + " 个场景（起点 / Forest2048 / 湖畔 / 湖心岛 / 结局×2）");
    }
}

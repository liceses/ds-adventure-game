package com.studio.plugin.demo.snake;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 贪吃蛇规则引擎单元测试（对齐需求 F4 的验收要点）。
 *
 * <p>纯规则、无 JavaFX 依赖，可在无图形环境下运行。</p>
 *
 * <p>推进方式说明：{@link SnakeGame#step(double)} 内部用累加器换算整格移动，
 * 因此单次较大的 dt 可能推进多格（这是帧率无关的正确行为）。测试里需要
 * “精确走一格”时统一使用 {@link #moveOneCell(SnakeGame)}：以小步长推进，
 * 直到蛇头发生位移或本局结束。</p>
 */
class SnakeGameTest {

    /** 单次推进的小步长：远小于一格的间隔（6 格/秒 → 约 0.167 秒/格） */
    private static final double TICK = 0.01;

    private static SnakeGame newDefaultGame() {
        return new SnakeGame(SnakeConfig.defaults(), new Random(42L));
    }

    /** 以小步长推进，直到蛇头移动一格或本局结束 */
    private static void moveOneCell(SnakeGame game) {
        int[] before = game.snakeCells().get(0);
        for (int i = 0; i < 1000; i++) {
            game.step(TICK);
            if (game.isOver()) {
                return;
            }
            int[] now = game.snakeCells().get(0);
            if (now[0] != before[0] || now[1] != before[1]) {
                return;
            }
        }
    }

    /** 把食物放在当前蛇头正前方，然后走一格（用于制造“必定吃到”） */
    private static void eatOneBean(SnakeGame game, int dx, int dy) {
        int[] head = game.snakeCells().get(0);
        game.placeFood(head[0] + dx, head[1] + dy);
        moveOneCell(game);
    }

    @Test
    void defaultConfigMatchesF4Spec() {
        SnakeConfig c = SnakeConfig.defaults();
        assertEquals(10, c.getCols());
        assertEquals(10, c.getRows());
        assertEquals(3, c.getInitialLength());
        assertEquals(97, c.getWinBeans());
        assertEquals(6.0, c.getInitialSpeed(), 1e-9);
        assertEquals(0.01, c.getSpeedGrowthPerSecond(), 1e-9);
        assertEquals(0, c.getObstacleCount(), "超纲玩法默认关闭");
        assertFalse(c.timeLimitEnabled(), "限时默认关闭");
    }

    @Test
    void initialStateIsThreeCellsAndRunning() {
        SnakeGame game = newDefaultGame();
        assertEquals(3, game.getLength());
        assertEquals(0, game.getScore());
        assertEquals(SnakeGame.State.RUNNING, game.getState());
        assertFalse(game.isOver());
    }

    @Test
    void movesOneCellForward() {
        SnakeGame game = newDefaultGame();
        int[] before = game.snakeCells().get(0);
        moveOneCell(game);
        int[] after = game.snakeCells().get(0);
        assertEquals(before[0] + 1, after[0], "初始朝右，走一格");
        assertEquals(before[1], after[1]);
        assertEquals(3, game.getLength(), "未吃豆长度不变");
    }

    @Test
    void eatingBeanGrowsAndScores() {
        SnakeGame game = newDefaultGame();
        eatOneBean(game, 1, 0);
        assertEquals(1, game.getScore());
        assertEquals(4, game.getLength());
    }

    @Test
    void foodIsNeverOnSnakeBody() {
        SnakeGame game = newDefaultGame();
        for (int i = 0; i < 3; i++) {
            eatOneBean(game, 1, 0);
        }
        int[] food = game.getFood();
        for (int[] c : game.snakeCells()) {
            assertFalse(c[0] == food[0] && c[1] == food[1], "食物不应生成在蛇身上");
        }
    }

    @Test
    void hittingWallLoses() {
        SnakeGame game = newDefaultGame();
        for (int i = 0; i < 20 && !game.isOver(); i++) {
            moveOneCell(game);
        }
        assertTrue(game.isLose(), "越出棋盘边界应判失败");
        assertEquals(SnakeGame.State.LOSE, game.getState());
    }

    @Test
    void reverseIsAcceptedButOverlappingBodyLoses() {
        SnakeGame game = newDefaultGame();
        // 先吃一颗，长度变 4，使“脖子”不再是尾巴
        eatOneBean(game, 1, 0);
        assertEquals(4, game.getLength());
        assertFalse(game.isOver());

        // 180° 反向：请求被接受，但下一步蛇头与身体重叠 → 失败
        game.setDirection(SnakeGame.Direction.LEFT);
        moveOneCell(game);
        assertTrue(game.isLose(), "反向导致蛇头与身体重叠应判失败");
    }

    @Test
    void hittingSelfLoses() {
        SnakeGame game = new SnakeGame(SnakeConfig.defaults(), new Random(7L));
        for (int i = 0; i < 4; i++) {
            eatOneBean(game, 1, 0);          // 身体拉长到 7
        }
        assertFalse(game.isOver(), "此时仍在运行");

        game.setDirection(SnakeGame.Direction.DOWN);
        moveOneCell(game);
        game.setDirection(SnakeGame.Direction.LEFT);
        moveOneCell(game);
        game.setDirection(SnakeGame.Direction.UP);
        moveOneCell(game);
        assertTrue(game.isLose(), "绕回撞到自身应判失败");
    }

    @Test
    void reachingWinBeansWins() {
        SnakeConfig c = SnakeConfig.defaults();
        c.setCols(6);
        c.setRows(6);
        c.setInitialLength(2);
        c.setWinBeans(3);
        c.setCountdownSeconds(0);
        SnakeGame game = new SnakeGame(c, new Random(1L));

        eatOneBean(game, 1, 0);              // 朝右吃第 1 颗
        game.setDirection(SnakeGame.Direction.DOWN);
        eatOneBean(game, 0, 1);              // 向下吃第 2 颗
        game.setDirection(SnakeGame.Direction.LEFT);
        eatOneBean(game, -1, 0);             // 向左吃第 3 颗 → 达标

        assertEquals(3, game.getScore());
        assertTrue(game.isWin(), "累计吃豆达到目标即通关");
        assertTrue(game.isOver());
    }

    @Test
    void speedFollowsSpecFormula() {
        SnakeGame game = newDefaultGame();
        assertEquals(6.0, game.speed(), 1e-9);
        game.step(2.0);
        assertEquals(6.0 + 0.01 * 2.0, game.speed(), 1e-9);
        game.reset();
        game.step(0.5);
        game.step(0.5);
        assertEquals(6.0 + 0.01 * 1.0, game.speed(), 1e-9, "两次 0.5 秒累计为 1 秒");
    }

    @Test
    void frameRateDoesNotChangeOutcome() {
        SnakeConfig c = SnakeConfig.defaults();
        c.setCountdownSeconds(0);
        SnakeGame coarse = new SnakeGame(c, new Random(99L));
        SnakeGame fine = new SnakeGame(c, new Random(99L));

        coarse.step(2.0);                    // 一大帧
        for (int i = 0; i < 20; i++) {
            fine.step(0.1);                  // 二十小帧
        }

        assertEquals(coarse.getState(), fine.getState(), "状态一致");
        assertEquals(coarse.getScore(), fine.getScore(), "分数一致");
        assertEquals(coarse.snakeCells().get(0)[0], fine.snakeCells().get(0)[0], "蛇头 x 一致");
        assertEquals(coarse.snakeCells().get(0)[1], fine.snakeCells().get(0)[1], "蛇头 y 一致");
        assertEquals(coarse.getLength(), fine.getLength(), "长度一致");
    }

    @Test
    void timeLimitOffByDefaultAndWorksWhenEnabled() {
        SnakeGame game = newDefaultGame();
        game.step(0.5);
        assertFalse(game.isLose(), "默认不限时");

        SnakeConfig limited = SnakeConfig.defaults();
        limited.setTimeLimitSeconds(1);
        SnakeGame limitedGame = new SnakeGame(limited, new Random(5L));
        limitedGame.step(1.5);
        assertTrue(limitedGame.isLose(), "限时到判失败");
    }

    @Test
    void obstaclesAndFoodPlacementValidation() {
        SnakeGame game = newDefaultGame();
        assertTrue(game.obstacles().isEmpty(), "默认无障碍");

        int[] foodBefore = game.getFood();
        game.placeFood(-1, 0);       // 越界应被忽略
        game.placeFood(0, 999);
        assertEquals(foodBefore[0], game.getFood()[0]);
        assertEquals(foodBefore[1], game.getFood()[1]);

        game.placeFood(0, 0);
        assertEquals(0, game.getFood()[0]);
        assertEquals(0, game.getFood()[1]);
        List<int[]> cells = game.snakeCells();
        assertFalse(cells.isEmpty());
    }
}

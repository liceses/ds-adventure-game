package com.studio.plugin.demo.breakout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 打砖块规则引擎单元测试。
 *
 * <p>全部只依赖 {@link BreakoutGame} 与 {@link BreakoutConfig}，不需要 JavaFX 运行时，
 * 因此可直接由 {@code mvnw.cmd test} 执行。</p>
 */
class BreakoutGameTest {

    private static final double FRAME = 1.0 / 60;
    private static final double TOLERANCE = 0.001;
    private static final int MAX_LOOP = 3000;

    private static BreakoutConfig config() {
        return new BreakoutConfig();
    }

    private static BreakoutGame newGame() {
        BreakoutGame game = new BreakoutGame();
        game.reset(config());
        return game;
    }

    /** 把球放到指定砖块中心撞一下（砖块在场地内，不会碰到挡板或墙）。 */
    private static void hitBrick(BreakoutGame game, int row, int col) {
        BreakoutConfig config = config();
        game.placeBall(config.getBrickCenterX(col), config.getBrickCenterY(row), 1, 1);
        game.tick(FRAME);
    }

    @Test
    @DisplayName("初始化：第 1 关、3 条命、球停在挡板上")
    void resetInitializesRound() {
        BreakoutGame game = newGame();

        assertEquals(1, game.level());
        assertEquals(3, game.lives());
        assertEquals(0, game.score());
        assertEquals(5 * 10, game.bricksLeft(), "第 1 关应有 5 行 x 10 列");
        assertTrue(game.isBallWaiting(), "开局球应停在挡板上");
        assertFalse(game.isOver());
    }

    @Test
    @DisplayName("挡板从场地正中开始")
    void paddleStartsCentered() {
        BreakoutGame game = newGame();
        BreakoutConfig config = config();

        double expected = (config.getViewWidth() - config.getPaddleWidth()) / 2.0;
        assertEquals(expected, game.paddleX(), TOLERANCE);
    }

    @Test
    @DisplayName("砖块越靠上血量越高（不同颜色不同血量）")
    void brickHpDecreasesDownTheRows() {
        BreakoutGame game = newGame();

        assertEquals(3, game.brickMaxHp(0), "最上面一行 3 血");
        assertEquals(3, game.brickMaxHp(1), "第二行 3 血");
        assertEquals(2, game.brickMaxHp(2), "第三行 2 血");
        assertEquals(1, game.brickMaxHp(4), "最后一行 1 血");
    }

    @Test
    @DisplayName("空格发射后球向上飞")
    void launchSendsBallUpward() {
        BreakoutGame game = newGame();
        game.launch();

        assertFalse(game.isBallWaiting());
        assertTrue(game.ballVY() < 0, "球应向上飞，实际 vY=" + game.ballVY());
    }

    @Test
    @DisplayName("球撞左右墙与天花板都会反弹")
    void ballBouncesOffWalls() {
        BreakoutGame game = newGame();
        BreakoutConfig config = config();
        int radius = config.getBallRadius();

        game.placeBall(radius + 1, 300, -300, 0);
        game.tick(FRAME);
        assertTrue(game.ballVX() > 0, "撞左墙应向右反弹");

        game.placeBall(config.getViewWidth() - radius - 1, 300, 300, 0);
        game.tick(FRAME);
        assertTrue(game.ballVX() < 0, "撞右墙应向左反弹");

        game.placeBall(400, config.getFieldTop() + radius + 1, 0, -300);
        game.tick(FRAME);
        assertTrue(game.ballVY() > 0, "撞天花板应向下反弹");
    }

    @Test
    @DisplayName("打在挡板左半边往左飞、右半边往右飞")
    void paddleEdgeChangesAngle() {
        BreakoutGame left = newGame();
        BreakoutConfig config = config();
        double ballY = config.getPaddleY() - config.getBallRadius() - 1;

        left.movePaddleTo(400);
        left.placeBall(left.paddleX() + config.getBallRadius() + 1, ballY, 0, 300);
        left.tick(FRAME);
        assertTrue(left.ballVX() < 0, "打左边缘应往左飞");

        BreakoutGame right = newGame();
        right.movePaddleTo(400);
        right.placeBall(right.paddleX() + config.getPaddleWidth() - config.getBallRadius() - 1, ballY, 0, 300);
        right.tick(FRAME);
        assertTrue(right.ballVX() > 0, "打右边缘应往右飞");
    }

    @Test
    @DisplayName("砖块要打满血量才消失")
    void brickIsRemovedOnlyAfterHpRunsOut() {
        BreakoutGame game = newGame();
        int before = game.bricksLeft();

        hitBrick(game, 0, 0);
        assertEquals(2, game.brickHp(0, 0), "第一次命中后应剩 2 血");
        assertEquals(before, game.bricksLeft(), "没碎时剩余砖块数不变");
        assertTrue(game.score() > 0, "命中就要得分");

        hitBrick(game, 0, 0);
        assertEquals(1, game.brickHp(0, 0));

        hitBrick(game, 0, 0);
        assertEquals(0, game.brickHp(0, 0), "第三次命中应打碎");
        assertEquals(before - 1, game.bricksLeft(), "碎掉后剩余砖块数减一");
        assertFalse(game.isBrickAlive(0, 0));
    }

    @Test
    @DisplayName("掉球扣一条命并回到等待发射")
    void ballFallsCostsOneLife() {
        BreakoutGame game = newGame();
        BreakoutConfig config = config();

        game.placeBall(400, config.getViewHeight() + 40, 0, 300);
        game.tick(FRAME);

        assertEquals(2, game.lives());
        assertTrue(game.isBallWaiting(), "掉球后球应回到挡板上");
    }

    @Test
    @DisplayName("三条命用完判定失败")
    void losingAllLivesLoses() {
        BreakoutGame game = newGame();
        BreakoutConfig config = config();

        for (int i = 0; i < 3; i++) {
            game.placeBall(400, config.getViewHeight() + 40, 0, 300);
            game.tick(FRAME);
        }

        assertEquals(0, game.lives());
        assertTrue(game.isLose());
        assertTrue(game.isOver());
    }

    @Test
    @DisplayName("打碎当前关全部砖块进入下一关")
    void clearingAllBricksAdvancesLevel() {
        BreakoutGame game = newGame();
        int startLevel = game.level();
        int guard = 0;

        while (game.bricksLeft() > 0 && game.level() == startLevel && guard++ < MAX_LOOP) {
            smashFirstAlive(game);
        }

        assertEquals(startLevel + 1, game.level());
        assertTrue(game.bricksLeft() > 0, "新一关应重新铺满砖块");
        assertTrue(game.isBallWaiting(), "新一关球应回到挡板上");
    }

    @Test
    @DisplayName("打通全部关卡判定通关")
    void clearingLastLevelWins() {
        BreakoutGame game = newGame();
        int guard = 0;

        while (!game.isOver() && guard++ < MAX_LOOP) {
            if (game.isBallWaiting()) {
                game.launch();
            }
            if (!smashFirstAlive(game)) {
                break;
            }
        }

        assertTrue(game.isWin(), "应判定通关，实际 level=" + game.level());
        assertTrue(game.isOver());
    }

    @Test
    @DisplayName("结束后不再响应 tick")
    void tickAfterOverDoesNothing() {
        BreakoutGame game = newGame();
        BreakoutConfig config = config();

        for (int i = 0; i < 3; i++) {
            game.placeBall(400, config.getViewHeight() + 40, 0, 300);
            game.tick(FRAME);
        }

        int lives = game.lives();
        double paddle = game.paddleX();
        game.movePaddle(1);
        game.tick(FRAME);

        assertEquals(lives, game.lives(), "结束后不应再扣命");
        assertEquals(paddle, game.paddleX(), TOLERANCE, "结束后挡板不应再移动");
    }

    /**
     * 把当前第一块活砖打到碎，@return 是否找到并处理了砖块。
     *
     * <p>注意要在打的过程中盯住关卡号：打碎本关最后一砖会立刻进入下一关并重新铺砖，
     * 若继续按坐标打，就会误打新关卡的砖。</p>
     */
    private static boolean smashFirstAlive(BreakoutGame game) {
        BreakoutConfig config = config();
        int level = game.level();
        int rows = game.brickRows();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < config.getBrickColumns(); col++) {
                if (!game.isBrickAlive(row, col)) {
                    continue;
                }
                for (int hit = 0; hit < game.brickMaxHp(row)
                        && game.isBrickAlive(row, col) && game.level() == level; hit++) {
                    hitBrick(game, row, col);
                }
                return true;
            }
        }
        return false;
    }
}

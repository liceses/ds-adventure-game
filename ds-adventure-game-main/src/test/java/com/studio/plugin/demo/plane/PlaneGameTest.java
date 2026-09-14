package com.studio.plugin.demo.plane;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 飞机大战规则引擎单元测试。
 *
 * <p>全部只依赖 {@link PlaneGame} 与 {@link PlaneConfig}，不需要 JavaFX 运行时，
 * 因此可直接由 {@code mvnw.cmd test} 执行。</p>
 */
class PlaneGameTest {

    private static final double FRAME = 1.0 / 60;
    private static final double TOLERANCE = 0.05;

    private static PlaneConfig config() {
        return new PlaneConfig();
    }

    private static PlaneGame newGame() {
        PlaneGame game = new PlaneGame();
        game.reset(config());
        return game;
    }

    @Test
    @DisplayName("初始化：3 条命、0 分、无护盾、场内无实体")
    void resetInitializesRound() {
        PlaneGame game = newGame();

        assertEquals(3, game.lives());
        assertEquals(0, game.score());
        assertEquals(0, game.kills());
        assertEquals(20, game.winKills(), "击落目标应为 20 架（需求 F5）");
        assertFalse(game.hasShield());
        assertFalse(game.isOver());
        assertTrue(game.bullets().isEmpty());
        assertTrue(game.enemies().isEmpty());
        assertTrue(game.enemyBullets().isEmpty());
    }

    @Test
    @DisplayName("战机开局在底部车道正中")
    void playerStartsBottomCenter() {
        PlaneGame game = newGame();
        PlaneConfig config = config();

        assertEquals(config.getViewWidth() / 2.0, game.playerX(), TOLERANCE);
        assertEquals(config.getBottomLaneY(), game.playerY(), TOLERANCE);
    }

    @Test
    @DisplayName("战机被限制在场地内，飞不出边界")
    void playerCannotLeaveField() {
        PlaneGame game = newGame();
        PlaneConfig config = config();

        game.move(-1, -1);
        for (int i = 0; i < 200; i++) {
            game.tick(FRAME);
        }
        assertEquals(config.getPlayerLeftLimit(), game.playerX(), TOLERANCE);
        assertEquals(config.getPlayerTopLimit(), game.playerY(), TOLERANCE);

        game.move(1, 1);
        for (int i = 0; i < 200; i++) {
            game.tick(FRAME);
        }
        assertEquals(config.getPlayerRightLimit(), game.playerX(), TOLERANCE);
        assertEquals(config.getPlayerBottomLimit(), game.playerY(), TOLERANCE);
    }

    @Test
    @DisplayName("斜向移动不会比单轴更快")
    void diagonalMoveIsNotFaster() {
        PlaneGame straight = newGame();
        straight.placePlayer(400, 300);
        straight.move(1, 0);
        for (int i = 0; i < 12; i++) {
            straight.tick(FRAME);
        }
        double straightDx = straight.playerX() - 400;

        PlaneGame diagonal = newGame();
        diagonal.placePlayer(400, 300);
        diagonal.move(1, 1);
        for (int i = 0; i < 12; i++) {
            diagonal.tick(FRAME);
        }
        double diagonalDx = diagonal.playerX() - 400;

        assertEquals(straightDx / Math.sqrt(2), diagonalDx, 0.5);
    }

    @Test
    @DisplayName("射击有冷却：同一瞬间连按只出一发")
    void shootRespectsCooldown() {
        PlaneGame game = newGame();

        game.shoot();
        game.shoot();
        assertEquals(1, game.bullets().size(), "冷却中不应再发弹");

        for (int i = 0; i < 20; i++) {
            game.tick(FRAME);
        }
        game.shoot();
        assertEquals(2, game.bullets().size(), "冷却结束后应能再发弹");
    }

    @Test
    @DisplayName("玩家子弹击落敌机：得分 + 击落数 + 敌机消失")
    void bulletKillsEnemyAndScores() {
        PlaneGame game = newGame();
        game.placeEnemy(400, 220);
        game.placeBullet(400, 230);

        game.tick(FRAME);

        assertTrue(game.enemies().isEmpty(), "敌机应被击落");
        assertEquals(1, game.kills());
        assertEquals(10, game.score(), "击落 1 架应得 10 分");
    }

    @Test
    @DisplayName("击落数达到目标即通关")
    void reachingKillTargetWins() {
        PlaneGame game = newGame();
        game.setKillsForTest(19);
        game.placeEnemy(400, 220);
        game.placeBullet(400, 230);

        game.tick(FRAME);

        assertEquals(20, game.kills());
        assertTrue(game.isWin(), "击落 20 架应通关");
        assertTrue(game.isOver());
    }

    @Test
    @DisplayName("被敌机撞到扣一条命")
    void enemyCollisionCostsLife() {
        PlaneGame game = newGame();
        game.placeEnemy(game.playerX(), game.playerY());

        game.tick(FRAME);

        assertEquals(2, game.lives());
        assertTrue(game.hasShield(), "受击后应有短暂无敌，避免同帧连扣");
    }

    @Test
    @DisplayName("被敌弹打中扣一条命")
    void enemyBulletCostsLife() {
        PlaneGame game = newGame();
        game.placeEnemyBullet(game.playerX(), game.playerY());

        game.tick(FRAME);

        assertEquals(2, game.lives());
    }

    @Test
    @DisplayName("生命耗尽即失败；结束后 tick 不再生效")
    void losingAllLivesLoses() {
        PlaneGame game = newGame();
        game.setLivesForTest(1);
        game.placeEnemyBullet(game.playerX(), game.playerY());

        game.tick(FRAME);

        assertEquals(0, game.lives());
        assertTrue(game.isLose());
        assertTrue(game.isOver());

        int lives = game.lives();
        game.tick(FRAME);
        assertEquals(lives, game.lives(), "结束后不应再变化");
    }

    @Test
    @DisplayName("护盾有效期内免伤，takeHit 返回 false 且不扣命")
    void shieldBlocksDamage() {
        PlaneGame game = newGame();
        game.placePlayer(400, 300);
        game.placePowerup(400, 300);

        game.tick(FRAME);
        assertTrue(game.hasShield(), "拾取护盾道具后应进入护盾状态");
        assertEquals(3.0, game.shieldSeconds(), TOLERANCE, "护盾应持续 3 秒（需求 F5）");

        assertFalse(game.takeHit(), "护盾期内伤害不应生效");
        assertEquals(3, game.lives(), "护盾期内不应扣命");
    }

    @Test
    @DisplayName("护盾到期后伤害恢复生效")
    void shieldExpiresAfterDuration() {
        PlaneGame game = newGame();
        game.placePlayer(400, 300);
        game.placePowerup(400, 300);
        game.tick(FRAME);
        assertTrue(game.hasShield());

        // 3.1 秒内敌机还来不及打到玩家，因此这段时间不会触发受击
        for (int i = 0; i < 186; i++) {
            game.tick(FRAME);
        }

        assertFalse(game.hasShield(), "3 秒后护盾应失效");
        assertEquals(3, game.lives(), "护盾期内不应掉命");
        assertTrue(game.takeHit(), "护盾失效后伤害应恢复生效");
    }

    @Test
    @DisplayName("未持护盾时受击：扣命并返回 true")
    void takeHitWithoutShieldDamages() {
        PlaneGame game = newGame();

        assertTrue(game.takeHit());
        assertEquals(2, game.lives());
        assertFalse(game.takeHit(), "受击后短暂无敌，第二次不应再扣命");
        assertEquals(2, game.lives());
    }

    @Test
    @DisplayName("攻势强度：开局 0，随生成间隔收紧而上升，到下限后维持 1")
    void intensityGrowsWithTime() {
        PlaneGame game = newGame();
        assertEquals(0.0, game.intensity(), TOLERANCE, "开局攻势强度应为 0");

        game.setElapsedForTest(10);
        assertEquals(0.4, game.intensity(), TOLERANCE, "10 秒后间隔 2.0 秒，强度应为 0.4");

        game.setElapsedForTest(100);
        assertEquals(1.0, game.intensity(), TOLERANCE, "压到下限后强度应为 1");
    }

    @Test
    @DisplayName("难度曲线：生成间隔随时间递减到下限，下落速度递增")
    void difficultyRampsOverTime() {
        PlaneConfig config = config();

        assertEquals(3.0, config.spawnIntervalAt(0), TOLERANCE, "开局每 3 秒 1 架");
        assertEquals(2.0, config.spawnIntervalAt(10), TOLERANCE, "每过 1 秒递减 0.1 秒");
        assertEquals(0.5, config.spawnIntervalAt(999), TOLERANCE, "下限 0.5 秒");

        assertTrue(config.enemySpeedAt(30) > config.enemySpeedAt(0), "敌机应越往后越快");
    }

    @Test
    @DisplayName("敌机出现后很快开火（首发射击已提前）")
    void enemiesFireSoonAfterAppearing() {
        PlaneGame game = newGame();

        int guard = 0;
        while (game.enemies().isEmpty() && guard++ < 600) {
            game.tick(FRAME);
        }
        assertFalse(game.enemies().isEmpty(), "应有敌机生成");
        assertTrue(game.enemyBullets().isEmpty(), "敌机刚出现时还没有子弹");

        for (int i = 0; i < 60; i++) {
            game.tick(FRAME);
        }
        assertFalse(game.enemyBullets().isEmpty(), "敌机应在出现后 1 秒内开火");
    }

    @Test
    @DisplayName("时间推进后会有敌机进入场地")
    void enemiesAppearOverTime() {
        PlaneGame game = newGame();

        for (int i = 0; i < 150; i++) {
            game.tick(FRAME);
        }

        assertFalse(game.enemies().isEmpty(), "2.5 秒后应已有敌机生成");
    }
}

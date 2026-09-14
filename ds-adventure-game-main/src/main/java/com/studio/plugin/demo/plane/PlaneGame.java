package com.studio.plugin.demo.plane;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 飞机大战纯规则引擎，不含任何界面代码、不 import javafx。
 *
 * <p>这样规则既能被插件界面驱动，也能脱离 JavaFX 单独跑单元测试
 * （见 src/test/java/com/studio/plugin/demo/plane/PlaneGameTest）。</p>
 *
 * <p>坐标单位为像素：x 向右、y 向下；玩家在下方，敌机自上而下推进。
 * 场地顶部 {@link PlaneConfig#getFieldTop()} 以上留给信息栏。</p>
 *
 * <p>胜利条件（对齐需求 F5）：击落 {@link PlaneConfig#getWinKills()} 架敌机；
 * 失败条件：生命耗尽。护盾由掉落的护盾道具提供，持续
 * {@link PlaneConfig#getShieldDuration()} 秒。</p>
 */
public final class PlaneGame {

    /** 开局到第一架敌机出现的延迟（秒）。 */
    private static final double FIRST_SPAWN_DELAY = 1.0;
    /** 受击后的短暂无敌时长（秒），避免同一帧连续掉光生命。 */
    private static final double HIT_INVULNERABLE_SECONDS = 1.2;
    /** 护盾道具的掉落间隔基准（秒）。 */
    private static final double POWERUP_INTERVAL = 13.0;
    /** 护盾道具的掉落间隔浮动范围（秒）。 */
    private static final double POWERUP_JITTER = 5.0;
    /** 敌机横向出现时距离左右边界的留白（像素）。 */
    private static final double SPAWN_MARGIN = 8.0;
    /** 斜向移动归一化系数，使斜向速度与单轴一致。 */
    private static final double DIAGONAL_FACTOR = 0.7071;
    /** 判定用的极小量。 */
    private static final double EPSILON = 1e-6;

    /** 玩家子弹。 */
    public static final class Bullet {

        private double x;
        private double y;

        Bullet(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        boolean offField(double fieldTop, double viewHeight) {
            return y + 40 < fieldTop || y - 40 > viewHeight;
        }
    }

    /** 敌机。 */
    public static final class Enemy {

        private double x;
        private double y;
        private double fireTimer;

        Enemy(double x, double y, double fireTimer) {
            this.x = x;
            this.y = y;
            this.fireTimer = fireTimer;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }
    }

    /** 掉落的护盾道具。 */
    public static final class Powerup {

        private double x;
        private double y;

        Powerup(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }
    }

    private final Random random = new Random();
    private final List<Bullet> bullets = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Bullet> enemyBullets = new ArrayList<>();
    private final List<Powerup> powerups = new ArrayList<>();

    private PlaneConfig config;

    private double playerX;
    private double playerY;
    private double moveDx;
    private double moveDy;

    private int lives;
    private int score;
    private int kills;

    private double shieldTimer;
    private double fireCooldown;
    private double spawnTimer;
    private double powerupTimer;
    private double elapsed;
    private boolean won;

    /** 按配置初始化一局。 */
    public void reset(PlaneConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config 不能为空");
        }
        this.config = config;
        this.playerX = config.getViewWidth() / 2.0;
        this.playerY = config.getBottomLaneY();
        this.moveDx = 0;
        this.moveDy = 0;
        this.lives = config.getInitialLives();
        this.score = 0;
        this.kills = 0;
        this.shieldTimer = 0;
        this.fireCooldown = 0;
        this.spawnTimer = FIRST_SPAWN_DELAY;
        this.powerupTimer = POWERUP_INTERVAL;
        this.elapsed = 0;
        this.won = false;
        this.bullets.clear();
        this.enemies.clear();
        this.enemyBullets.clear();
        this.powerups.clear();
    }

    /**
     * 设置战机移动方向：取值 -1 / 0 / 1。
     * 仅记录方向，实际位移由 {@link #tick(double)} 按速度积分，保证位移与帧率无关。
     */
    public void move(double dx, double dy) {
        this.moveDx = clamp(dx, -1, 1);
        this.moveDy = clamp(dy, -1, 1);
    }

    /** 发射一枚子弹；仍在冷却中则不发射。 */
    public void shoot() {
        if (config == null || isOver() || fireCooldown > 0) {
            return;
        }
        bullets.add(new Bullet(playerX, playerY - config.getPlayerHeight() / 2.0));
        fireCooldown = config.getBulletCooldown();
    }

    /** 按帧间隔推进：玩家移动、敌机生成、子弹与碰撞、护盾计时、胜负结算。 */
    public void tick(double dt) {
        if (config == null || isOver() || dt <= 0) {
            return;
        }
        elapsed += dt;
        shieldTimer = Math.max(0, shieldTimer - dt);
        fireCooldown = Math.max(0, fireCooldown - dt);

        updatePlayer(dt);
        spawnEnemies(dt);
        spawnPowerups(dt);

        moveBullets(dt);
        moveEnemies(dt);
        moveEnemyBullets(dt);
        movePowerups(dt);

        resolveBulletHits();
        resolvePickups();
        resolvePlayerHits();
    }

    /**
     * 受到一次伤害。
     *
     * @return 本次伤害是否真的生效；护盾期内返回 false 且不扣生命
     */
    public boolean takeHit() {
        if (config == null || isOver() || shieldTimer > 0) {
            return false;
        }
        lives--;
        shieldTimer = HIT_INVULNERABLE_SECONDS;
        return true;
    }

    // ---------------- 状态查询（供界面绘制与测试） ----------------

    public int lives() {
        return lives;
    }

    public int score() {
        return score;
    }

    /** 已击落敌机数。 */
    public int kills() {
        return kills;
    }

    public int winKills() {
        return config == null ? 0 : config.getWinKills();
    }

    public double playerX() {
        return playerX;
    }

    public double playerY() {
        return playerY;
    }

    public boolean hasShield() {
        return shieldTimer > 0;
    }

    public double shieldSeconds() {
        return shieldTimer;
    }

    public double elapsed() {
        return elapsed;
    }

    public List<Bullet> bullets() {
        return Collections.unmodifiableList(bullets);
    }

    public List<Enemy> enemies() {
        return Collections.unmodifiableList(enemies);
    }

    public List<Bullet> enemyBullets() {
        return Collections.unmodifiableList(enemyBullets);
    }

    public List<Powerup> powerups() {
        return Collections.unmodifiableList(powerups);
    }

    /**
     * 当前敌机攻势强度（0..1），供界面画渐变读条。
     *
     * <p>0 = 开局最松（生成间隔为初值 {@link PlaneConfig#getSpawnInterval()}）；
     * 1 = 已经压到下限 {@link PlaneConfig#getSpawnMin()}，之后维持 1 不再上升。</p>
     */
    public double intensity() {
        if (config == null) {
            return 0;
        }
        double slow = config.getSpawnInterval();
        double fast = config.getSpawnMin();
        double span = slow - fast;
        if (span <= EPSILON) {
            return 1;
        }
        double interval = config.spawnIntervalAt(elapsed);
        return clamp((slow - interval) / span, 0, 1);
    }

    /** 是否通关（击落数达到目标）。 */
    public boolean isWin() {
        return won;
    }

    /** 是否失败（生命耗尽）。 */
    public boolean isLose() {
        return lives <= 0;
    }

    /** 本局是否结束。 */
    public boolean isOver() {
        return isWin() || isLose();
    }

    // ---------------- 内部规则 ----------------

    private void updatePlayer(double dt) {
        double dx = moveDx;
        double dy = moveDy;
        if (dx != 0 && dy != 0) {
            dx *= DIAGONAL_FACTOR;
            dy *= DIAGONAL_FACTOR;
        }
        double speed = config.getPlayerSpeed() * dt;
        playerX = clamp(playerX + dx * speed, config.getPlayerLeftLimit(), config.getPlayerRightLimit());
        playerY = clamp(playerY + dy * speed, config.getPlayerTopLimit(), config.getPlayerBottomLimit());
    }

    private void spawnEnemies(double dt) {
        spawnTimer -= dt;
        int guard = 0;
        while (spawnTimer <= 0 && guard++ < 8) {
            enemies.add(createEnemy());
            spawnTimer += config.spawnIntervalAt(elapsed);
        }
        if (spawnTimer <= 0) {
            spawnTimer = config.spawnIntervalAt(elapsed);
        }
    }

    private Enemy createEnemy() {
        double half = config.getEnemyWidth() / 2.0;
        double span = config.getViewWidth() - config.getEnemyWidth() - SPAWN_MARGIN * 2;
        double x = SPAWN_MARGIN + half + random.nextDouble() * Math.max(1, span);
        double y = config.getFieldTop() - config.getEnemyHeight() / 2.0;
        // 首次开火提前：敌机刚进场很快就射击；各机随机错开，避免齐射
        double firstMin = config.getEnemyFirstFireMin();
        double firstSpan = Math.max(0, config.getEnemyFirstFireMax() - firstMin);
        double fire = firstMin + random.nextDouble() * firstSpan;
        return new Enemy(x, y, fire);
    }

    private void spawnPowerups(double dt) {
        powerupTimer -= dt;
        if (powerupTimer > 0) {
            return;
        }
        double margin = config.getPlayerWidth();
        double x = margin + random.nextDouble() * (config.getViewWidth() - margin * 2);
        powerups.add(new Powerup(x, config.getFieldTop() + 10));
        powerupTimer = POWERUP_INTERVAL + random.nextDouble() * POWERUP_JITTER;
    }

    private void moveBullets(double dt) {
        double speed = config.getBulletSpeed() * dt;
        for (Bullet b : bullets) {
            b.y -= speed;
        }
        bullets.removeIf(b -> b.offField(config.getFieldTop(), config.getViewHeight()));
    }

    private void moveEnemies(double dt) {
        double speed = config.enemySpeedAt(elapsed) * dt;
        for (Enemy e : enemies) {
            e.y += speed;
            updateEnemyFire(e, dt);
        }
        // 冲过底边的敌机算它逃脱，不额外惩罚，直接移除
        enemies.removeIf(e -> e.y - config.getEnemyHeight() / 2.0 > config.getViewHeight());
    }

    private void moveEnemyBullets(double dt) {
        double speed = config.getEnemyBulletSpeed() * dt;
        for (Bullet b : enemyBullets) {
            b.y += speed;
        }
        enemyBullets.removeIf(b -> b.offField(config.getFieldTop(), config.getViewHeight()));
    }

    private void movePowerups(double dt) {
        double speed = config.getEnemyBulletSpeed() * 0.6 * dt;
        for (Powerup p : powerups) {
            p.y += speed;
        }
        powerups.removeIf(p -> p.y - 20 > config.getViewHeight());
    }

    /** 玩家子弹命中敌机：敌机被击落，计分并累计击落数。 */
    private void resolveBulletHits() {
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            int hitIndex = findEnemyAt(b.x, b.y);
            if (hitIndex < 0) {
                continue;
            }
            bullets.remove(i);
            enemies.remove(hitIndex);
            kills++;
            score += config.getKillScore();
            if (kills >= config.getWinKills()) {
                won = true;
                return;
            }
        }
    }

    private int findEnemyAt(double x, double y) {
        double halfW = config.getEnemyWidth() / 2.0;
        double halfH = config.getEnemyHeight() / 2.0;
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            if (Math.abs(e.x - x) <= halfW && Math.abs(e.y - y) <= halfH) {
                return i;
            }
        }
        return -1;
    }

    /** 拾取护盾道具：清空计时并重新计时。 */
    private void resolvePickups() {
        double halfW = config.getPlayerWidth() / 2.0;
        double halfH = config.getPlayerHeight() / 2.0;
        for (int i = powerups.size() - 1; i >= 0; i--) {
            Powerup p = powerups.get(i);
            if (Math.abs(p.x - playerX) <= halfW + 10 && Math.abs(p.y - playerY) <= halfH + 10) {
                powerups.remove(i);
                shieldTimer = config.getShieldDuration();
            }
        }
    }

    /** 敌机或敌弹撞到玩家：走 takeHit 规则（护盾期内免除伤害）。 */
    private void resolvePlayerHits() {
        if (shieldTimer > 0) {
            return;
        }
        double halfW = config.getPlayerWidth() / 2.0;
        double halfH = config.getPlayerHeight() / 2.0;

        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (Math.abs(e.x - playerX) <= halfW + config.getEnemyWidth() / 2.0
                    && Math.abs(e.y - playerY) <= halfH + config.getEnemyHeight() / 2.0) {
                enemies.remove(i);
                takeHit();
                return;
            }
        }

        double radius = config.getEnemyBulletRadius();
        for (int i = enemyBullets.size() - 1; i >= 0; i--) {
            Bullet b = enemyBullets.get(i);
            if (Math.abs(b.x - playerX) <= halfW + radius && Math.abs(b.y - playerY) <= halfH + radius) {
                enemyBullets.remove(i);
                takeHit();
                return;
            }
        }
    }

    /** 敌机开火：向下直射一发敌弹。 */
    private void updateEnemyFire(Enemy e, double step) {
        e.fireTimer -= step;
        if (e.fireTimer > 0) {
            return;
        }
        e.fireTimer = config.getEnemyFireInterval();
        enemyBullets.add(new Bullet(e.x, e.y + config.getEnemyHeight() / 2.0));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ---------------- 同包测试用的状态注入 ----------------

    /** 仅供同包单元测试使用：直接摆战机位置。 */
    void placePlayer(double x, double y) {
        playerX = clamp(x, config.getPlayerLeftLimit(), config.getPlayerRightLimit());
        playerY = clamp(y, config.getPlayerTopLimit(), config.getPlayerBottomLimit());
    }

    /** 仅供同包单元测试使用：放一架敌机到指定位置。 */
    void placeEnemy(double x, double y) {
        enemies.add(new Enemy(x, y, config.getEnemyFireInterval()));
    }

    /** 仅供同包单元测试使用：放一枚敌弹到指定位置。 */
    void placeEnemyBullet(double x, double y) {
        enemyBullets.add(new Bullet(x, y));
    }

    /** 仅供同包单元测试使用：放一枚玩家子弹到指定位置。 */
    void placeBullet(double x, double y) {
        bullets.add(new Bullet(x, y));
    }

    /** 仅供同包单元测试使用：放一个护盾道具到指定位置。 */
    void placePowerup(double x, double y) {
        powerups.add(new Powerup(x, y));
    }

    /** 仅供同包单元测试使用：把生命设成指定值。 */
    void setLivesForTest(int value) {
        lives = value;
    }

    /** 仅供同包单元测试使用：直接推进击落数。 */
    void setKillsForTest(int value) {
        kills = value;
    }

    /** 仅供同包单元测试使用：直接推进已进行时间，用于验证难度曲线。 */
    void setElapsedForTest(double seconds) {
        elapsed = seconds;
    }
}

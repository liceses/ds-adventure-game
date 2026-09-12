package com.studio.plugin.demo.breakout;

import java.util.Random;

/**
 * 打砖块纯规则引擎，不含任何界面代码、不 import javafx。
 *
 * <p>这样规则既能被插件界面驱动，也能脱离 JavaFX 单独跑单元测试
 * （见 src/test/java/com/studo/... 下的 BreakoutGameTest）。</p>
 *
 * <p>坐标单位为像素：x 向右、y 向下；场地顶部 {@link BreakoutConfig#getFieldTop()}
 * 以上留给信息栏，球撞到该高度会弹回。</p>
 */
public final class BreakoutGame {

    /** 发射角上限（相对竖直方向），避免开局就贴着侧墙飞。 */
    private static final double LAUNCH_MAX_ANGLE = Math.toRadians(35);
    /** 打在挡板边缘时的最大反弹角。 */
    private static final double MAX_BOUNCE_ANGLE = Math.toRadians(60);
    /** 单次位移超过该像素数就切成小步，防止高速时一帧穿过砖块。 */
    private static final double MAX_SUBSTEP = 4.0;
    /** 浮点脱出重叠用的极小量。 */
    private static final double EPSILON = 0.001;
    /** 居中计算用的除数。 */
    private static final double CENTER_DIVISOR = 2.0;
    /** 随机角度取值范围（对称于竖直方向）。 */
    private static final double ANGLE_SPAN = 2.0;
    /** 击中砖块但未打碎时的得分。 */
    private static final int HIT_SCORE = 5;
    /** 打碎砖块时按行加权的分值基数：第 row 行 (brickRows - row) * 该值。 */
    private static final int DESTROY_SCORE_STEP = 10;

    private final Random random = new Random();

    private BreakoutConfig config;
    private int[][] brickHp = new int[0][0];

    private int brickRows;
    private int bricksLeft;

    private double ballX;
    private double ballY;
    private double ballVX;
    private double ballVY;

    private double paddleX;
    private double paddleDirection;

    private int level;
    private int score;
    private int lives;
    private boolean ballWaiting;
    private boolean won;

    /** 按配置初始化一局。 */
    public void reset(BreakoutConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config 不能为空");
        }
        this.config = config;
        this.brickHp = new int[config.getMaxBrickRows()][config.getBrickColumns()];
        this.level = 1;
        this.score = 0;
        this.lives = config.getInitialLives();
        this.won = false;
        this.paddleDirection = 0;
        this.paddleX = (config.getViewWidth() - config.getPaddleWidth()) / CENTER_DIVISOR;

        buildBricks();
        resetBallOnPaddle();
    }

    /** 设置挡板移动方向：-1 左、0 停、1 右。实际位移由 {@link #tick(double)} 按速度积分。 */
    public void movePaddle(double dx) {
        if (dx < 0) {
            paddleDirection = -1;
        } else if (dx > 0) {
            paddleDirection = 1;
        } else {
            paddleDirection = 0;
        }
    }

    /** 发射球（球初始化时停在挡板上等待发射）。 */
    public void launch() {
        if (!ballWaiting || isOver()) {
            return;
        }
        double angle = (random.nextDouble() * ANGLE_SPAN - 1) * LAUNCH_MAX_ANGLE;
        double speed = currentBallSpeed();
        ballVX = Math.sin(angle) * speed;
        ballVY = -Math.cos(angle) * speed;
        ballWaiting = false;
    }

    /** 按帧间隔推进：挡板移动、球移动、碰撞检测、关卡与生命结算。 */
    public void tick(double dt) {
        if (config == null || isOver() || dt < 0) {
            return;
        }
        updatePaddle(dt);
        if (ballWaiting) {
            keepBallOnPaddle();
            return;
        }
        moveBall(dt);
    }

    // ---------------- 状态查询（供界面绘制与测试） ----------------

    public int score() {
        return score;
    }

    public int lives() {
        return lives;
    }

    public int level() {
        return level;
    }

    public int bricksLeft() {
        return bricksLeft;
    }

    public int brickRows() {
        return brickRows;
    }

    public int brickColumns() {
        return config == null ? 0 : config.getBrickColumns();
    }

    /** 指定砖块剩余血量，0 表示已打碎。 */
    public int brickHp(int row, int col) {
        if (row < 0 || row >= brickHp.length || col < 0 || col >= brickHp[row].length) {
            return 0;
        }
        return brickHp[row][col];
    }

    /** 指定砖块的初始血量，供界面换算颜色深浅。 */
    public int brickMaxHp(int row) {
        return config == null ? 1 : config.getBrickMaxHp(row);
    }

    public boolean isBrickAlive(int row, int col) {
        return brickHp(row, col) > 0;
    }

    public boolean isBallWaiting() {
        return ballWaiting;
    }

    public double paddleX() {
        return paddleX;
    }

    public double ballX() {
        return ballX;
    }

    public double ballY() {
        return ballY;
    }

    public double ballVX() {
        return ballVX;
    }

    public double ballVY() {
        return ballVY;
    }

    /** 是否通关（打通全部关卡）。 */
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

    private void buildBricks() {
        brickRows = Math.min(config.getBaseBrickRows() + (level - 1), config.getMaxBrickRows());
        bricksLeft = 0;
        for (int row = 0; row < brickHp.length; row++) {
            int hp = config.getBrickMaxHp(row);
            for (int col = 0; col < brickHp[row].length; col++) {
                boolean alive = row < brickRows;
                brickHp[row][col] = alive ? hp : 0;
                if (alive) {
                    bricksLeft++;
                }
            }
        }
    }

    private void resetBallOnPaddle() {
        ballX = config.getPaddleCenterX(paddleX);
        ballY = config.getPaddleY() - config.getBallRadius() - EPSILON;
        ballVX = 0;
        ballVY = 0;
        ballWaiting = true;
    }

    private void keepBallOnPaddle() {
        ballX = config.getPaddleCenterX(paddleX);
        ballY = config.getPaddleY() - config.getBallRadius() - EPSILON;
    }

    private void updatePaddle(double dt) {
        paddleX += paddleDirection * config.getPaddleSpeed() * dt;
        double maxX = config.getViewWidth() - config.getPaddleWidth();
        paddleX = clamp(paddleX, 0, maxX);
    }

    /** 当前关卡的球速，随关卡递增但有上限。 */
    private double currentBallSpeed() {
        double speed = config.getBallSpeedBase() + (level - 1) * config.getBallSpeedPerLevel();
        return Math.min(speed, config.getMaxBallSpeed());
    }

    private void moveBall(double dt) {
        double distance = Math.hypot(ballVX, ballVY) * dt;
        int steps = (int) Math.ceil(distance / MAX_SUBSTEP);
        if (steps < 1) {
            steps = 1;
        }
        double subDt = dt / steps;

        for (int i = 0; i < steps; i++) {
            ballX += ballVX * subDt;
            ballY += ballVY * subDt;

            collideWalls();

            if (ballY - config.getBallRadius() > config.getViewHeight()) {
                loseLife();
                return;
            }

            collidePaddle();

            if (collideBricks()) {
                return;
            }
        }
    }

    private void collideWalls() {
        int radius = config.getBallRadius();

        if (ballX - radius < 0) {
            ballX = radius;
            ballVX = Math.abs(ballVX);
        } else if (ballX + radius > config.getViewWidth()) {
            ballX = config.getViewWidth() - radius;
            ballVX = -Math.abs(ballVX);
        }

        if (ballY - radius < config.getFieldTop()) {
            ballY = config.getFieldTop() + radius;
            ballVY = Math.abs(ballVY);
        }
    }

    private void collidePaddle() {
        if (ballVY <= 0) {
            return;
        }

        int radius = config.getBallRadius();
        double paddleY = config.getPaddleY();
        double paddleRight = paddleX + config.getPaddleWidth();

        boolean touching = ballX + radius > paddleX
                && ballX - radius < paddleRight
                && ballY + radius > paddleY
                && ballY - radius < paddleY + config.getPaddleHeight();
        if (!touching) {
            return;
        }

        ballY = paddleY - radius - EPSILON;

        // 打在左半边往左飞、右半边往右飞，越靠边角度越大
        double relative = (ballX - config.getPaddleCenterX(paddleX)) / (config.getPaddleWidth() / CENTER_DIVISOR);
        relative = clamp(relative, -1, 1);

        double angle = relative * MAX_BOUNCE_ANGLE;
        double speed = Math.max(Math.hypot(ballVX, ballVY), currentBallSpeed());
        ballVX = Math.sin(angle) * speed;
        ballVY = -Math.cos(angle) * speed;
    }

    /**
     * 砖块碰撞。命中即扣 1 点血：没碎也反弹，碎了才移除。
     *
     * @return 本步是否击中砖块
     */
    private boolean collideBricks() {
        int radius = config.getBallRadius();
        double left = ballX - radius;
        double right = ballX + radius;
        double top = ballY - radius;
        double bottom = ballY + radius;

        for (int row = 0; row < brickRows; row++) {
            for (int col = 0; col < brickHp[row].length; col++) {
                if (brickHp[row][col] <= 0) {
                    continue;
                }

                double brickLeft = config.getBrickLeftX(col);
                double brickRight = brickLeft + config.getBrickWidth();
                double brickTop = config.getBrickTopY(row);
                double brickBottom = brickTop + config.getBrickHeight();

                if (right <= brickLeft || left >= brickRight || bottom <= brickTop || top >= brickBottom) {
                    continue;
                }

                bounceOffBrick(brickLeft, brickRight, brickTop, brickBottom);
                damageBrick(row, col);
                return true;
            }
        }
        return false;
    }

    /** 按穿透更浅的那条轴反弹：从侧面撞改 X 速度，从上下撞改 Y 速度。 */
    private void bounceOffBrick(double brickLeft, double brickRight, double brickTop, double brickBottom) {
        int radius = config.getBallRadius();
        double overlapX = Math.min(ballX + radius - brickLeft, brickRight - (ballX - radius));
        double overlapY = Math.min(ballY + radius - brickTop, brickBottom - (ballY - radius));

        if (overlapX < overlapY) {
            ballVX = -ballVX;
            ballX += ballVX > 0 ? overlapX : -overlapX;
        } else {
            ballVY = -ballVY;
            ballY += ballVY > 0 ? overlapY : -overlapY;
        }
    }

    private void damageBrick(int row, int col) {
        brickHp[row][col]--;
        score += HIT_SCORE;

        if (brickHp[row][col] > 0) {
            return;
        }
        bricksLeft--;
        score += (brickRows - row) * DESTROY_SCORE_STEP;

        if (bricksLeft == 0) {
            advanceLevel();
        }
    }

    /** 砖块清空：还有下一关就铺新砖并把球放回挡板，否则判定通关。 */
    private void advanceLevel() {
        if (level >= config.getTotalLevels()) {
            won = true;
            return;
        }
        level++;
        buildBricks();
        resetBallOnPaddle();
    }

    private void loseLife() {
        lives--;
        resetBallOnPaddle();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ---------------- 同包测试用的状态注入 ----------------

    /** 仅供同包单元测试使用：直接摆球的位置与速度。 */
    void placeBall(double x, double y, double vx, double vy) {
        ballX = x;
        ballY = y;
        ballVX = vx;
        ballVY = vy;
        ballWaiting = false;
    }

    /** 直接把挡板移到指定中心位置（鼠标控制用），供界面层调用。 */
    public void movePaddleTo(double centerX) {
        if (config == null) {
            return;
        }
        paddleX = clamp(centerX - config.getPaddleWidth() / CENTER_DIVISOR,
                0, config.getViewWidth() - config.getPaddleWidth());
        if (ballWaiting) {
            keepBallOnPaddle();
        }
    }
}

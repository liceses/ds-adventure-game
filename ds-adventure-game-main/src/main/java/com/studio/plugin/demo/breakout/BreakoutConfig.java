package com.studio.plugin.demo.breakout;

/**
 * 打砖块玩法配置。
 *
 * <p>各数值字段带默认值，可直接 new 出来后按需覆写；砖块与挡板的几何量
 * （砖块宽、砖块左上角坐标、挡板 Y）统一由这里推导，保证规则引擎与界面读的是
 * 同一份布局，不会出现两边各写一遍常量而失配。</p>
 */
public final class BreakoutConfig {

    /** 挡板厚度（像素）。 */
    private static final int PADDLE_HEIGHT = 14;
    /** 挡板下沿到场地底边的距离（像素）。 */
    private static final int PADDLE_BOTTOM_MARGIN = 40;
    /** 球半径（像素）。 */
    private static final int BALL_RADIUS = 8;
    /** 单块砖高度（像素）。 */
    private static final int BRICK_HEIGHT = 24;
    /** 相邻砖块的水平间距（像素）。 */
    private static final int BRICK_GAP_X = 6;
    /** 相邻砖块的垂直间距（像素）。 */
    private static final int BRICK_GAP_Y = 6;
    /** 砖块阵列距离左右边界的边距（像素）。 */
    private static final int BRICK_MARGIN_X = 40;
    /** 第一行砖块距离场地顶部的距离（像素）。 */
    private static final int BRICK_TOP_OFFSET = 36;
    /** 居中计算用的除数。 */
    private static final double CENTER_DIVISOR = 2.0;
    /** 砖块最高血量档位（越靠上的行越硬）。 */
    private static final int MAX_BRICK_HP = 3;
    /** 每多少行降低一档血量。 */
    private static final int HP_ROWS_PER_STEP = 2;

    private int viewWidth = 800;
    private int viewHeight = 600;
    /** 顶部信息栏高度，球在这一高度（下沿）反弹。 */
    private int fieldTop = 56;

    private int paddleWidth = 110;
    private double paddleSpeed = 540;

    private double ballSpeedBase = 380;
    private double ballSpeedPerLevel = 25;
    private double maxBallSpeed = 720;

    private int initialLives = 3;
    private int totalLevels = 5;

    private int brickColumns = 10;
    private int baseBrickRows = 5;
    private int maxBrickRows = 8;

    public int getViewWidth() {
        return viewWidth;
    }

    public void setViewWidth(int viewWidth) {
        this.viewWidth = viewWidth;
    }

    public int getViewHeight() {
        return viewHeight;
    }

    public void setViewHeight(int viewHeight) {
        this.viewHeight = viewHeight;
    }

    public int getFieldTop() {
        return fieldTop;
    }

    public void setFieldTop(int fieldTop) {
        this.fieldTop = fieldTop;
    }

    public int getPaddleWidth() {
        return paddleWidth;
    }

    public void setPaddleWidth(int paddleWidth) {
        this.paddleWidth = paddleWidth;
    }

    public double getPaddleSpeed() {
        return paddleSpeed;
    }

    public void setPaddleSpeed(double paddleSpeed) {
        this.paddleSpeed = paddleSpeed;
    }

    public double getBallSpeedBase() {
        return ballSpeedBase;
    }

    public void setBallSpeedBase(double ballSpeedBase) {
        this.ballSpeedBase = ballSpeedBase;
    }

    public double getBallSpeedPerLevel() {
        return ballSpeedPerLevel;
    }

    public void setBallSpeedPerLevel(double ballSpeedPerLevel) {
        this.ballSpeedPerLevel = ballSpeedPerLevel;
    }

    public double getMaxBallSpeed() {
        return maxBallSpeed;
    }

    public void setMaxBallSpeed(double maxBallSpeed) {
        this.maxBallSpeed = maxBallSpeed;
    }

    public int getInitialLives() {
        return initialLives;
    }

    public void setInitialLives(int initialLives) {
        this.initialLives = initialLives;
    }

    public int getTotalLevels() {
        return totalLevels;
    }

    public void setTotalLevels(int totalLevels) {
        this.totalLevels = totalLevels;
    }

    public int getBrickColumns() {
        return brickColumns;
    }

    public void setBrickColumns(int brickColumns) {
        this.brickColumns = brickColumns;
    }

    public int getBaseBrickRows() {
        return baseBrickRows;
    }

    public void setBaseBrickRows(int baseBrickRows) {
        this.baseBrickRows = baseBrickRows;
    }

    public int getMaxBrickRows() {
        return maxBrickRows;
    }

    public void setMaxBrickRows(int maxBrickRows) {
        this.maxBrickRows = maxBrickRows;
    }

    // ---------------- 由数值参数推导的几何量 ----------------

    public int getPaddleHeight() {
        return PADDLE_HEIGHT;
    }

    public int getBallRadius() {
        return BALL_RADIUS;
    }

    public int getBrickHeight() {
        return BRICK_HEIGHT;
    }

    /** 挡板上沿 Y 坐标。 */
    public int getPaddleY() {
        return viewHeight - PADDLE_BOTTOM_MARGIN;
    }

    /** 给定挡板左沿时的挡板中心 X。 */
    public double getPaddleCenterX(double paddleX) {
        return paddleX + paddleWidth / CENTER_DIVISOR;
    }

    /** 单块砖宽度：列数与边距确定后由场地宽度均分。 */
    public int getBrickWidth() {
        int usable = viewWidth - 2 * BRICK_MARGIN_X - (brickColumns - 1) * BRICK_GAP_X;
        return usable / brickColumns;
    }

    /** 砖块阵列的左起点（整行居中）。 */
    public int getBrickOffsetX() {
        int used = brickColumns * getBrickWidth() + (brickColumns - 1) * BRICK_GAP_X;
        return (viewWidth - used) / 2;
    }

    /** 第 col 列砖块的左沿 X 坐标。 */
    public int getBrickLeftX(int col) {
        return getBrickOffsetX() + col * (getBrickWidth() + BRICK_GAP_X);
    }

    /** 第 row 行砖块的上沿 Y 坐标。 */
    public int getBrickTopY(int row) {
        return fieldTop + BRICK_TOP_OFFSET + row * (BRICK_HEIGHT + BRICK_GAP_Y);
    }

    /** 第 col 列砖块的中心 X 坐标。 */
    public double getBrickCenterX(int col) {
        return getBrickLeftX(col) + getBrickWidth() / CENTER_DIVISOR;
    }

    /** 第 row 行砖块的中心 Y 坐标。 */
    public double getBrickCenterY(int row) {
        return getBrickTopY(row) + BRICK_HEIGHT / CENTER_DIVISOR;
    }

    /**
     * 第 row 行砖块的初始血量：越靠上的行越硬，用来实现"不同颜色不同血量"。
     * 最上面一行 3 血，往下每 2 行降一档，最低 1 血。
     */
    public int getBrickMaxHp(int row) {
        int hp = MAX_BRICK_HP - row / HP_ROWS_PER_STEP;
        return Math.max(1, hp);
    }
}

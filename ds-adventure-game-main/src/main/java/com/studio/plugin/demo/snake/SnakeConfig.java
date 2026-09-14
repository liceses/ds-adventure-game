package com.studio.plugin.demo.snake;

/**
 * 贪吃蛇本局参数（纯数据对象，不依赖 JavaFX）。
 *
 * <p>默认值对齐《需求规格说明书》F4：
 * 10×10 格、蛇初始长度 3、累计吃豆 <b>97</b> 通关、
 * 移动速度 = <b>初始速度 + 0.01 × 游戏时间(秒)</b>。</p>
 *
 * <p>其中 {@code obstacleCount} 与 {@code timeLimitSeconds} 属于规格里
 * “可扩充玩法”的范围，默认<b>关闭</b>：因为 97 豆通关需要较长对局，
 * 加限时会与之冲突。需要时改配置即可开启。</p>
 */
public class SnakeConfig {

    private int cols = 10;
    private int rows = 10;
    private int initialLength = 3;
    private int winBeans = 97;
    /** 初始速度（格/秒） */
    private double initialSpeed = 6.0;
    /** 速度每秒递增（格/秒），需求 F4 = 0.01 */
    private double speedGrowthPerSecond = 0.01;
    /** 障碍数（超纲玩法，默认 0 = 无） */
    private int obstacleCount = 0;
    /** 限时秒数（0 = 不限时） */
    private int timeLimitSeconds = 0;
    /** 开场倒计时秒数（0 = 直接开始） */
    private int countdownSeconds = 3;

    public static SnakeConfig defaults() {
        return new SnakeConfig();
    }

    public int getCols() {
        return cols;
    }

    public void setCols(int cols) {
        this.cols = Math.max(4, cols);
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = Math.max(4, rows);
    }

    public int getInitialLength() {
        return initialLength;
    }

    public void setInitialLength(int initialLength) {
        this.initialLength = Math.max(1, initialLength);
    }

    public int getWinBeans() {
        return winBeans;
    }

    public void setWinBeans(int winBeans) {
        this.winBeans = Math.max(1, winBeans);
    }

    public double getInitialSpeed() {
        return initialSpeed;
    }

    public void setInitialSpeed(double initialSpeed) {
        this.initialSpeed = Math.max(0.1, initialSpeed);
    }

    public double getSpeedGrowthPerSecond() {
        return speedGrowthPerSecond;
    }

    public void setSpeedGrowthPerSecond(double speedGrowthPerSecond) {
        this.speedGrowthPerSecond = Math.max(0.0, speedGrowthPerSecond);
    }

    public int getObstacleCount() {
        return obstacleCount;
    }

    public void setObstacleCount(int obstacleCount) {
        this.obstacleCount = Math.max(0, obstacleCount);
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public void setTimeLimitSeconds(int timeLimitSeconds) {
        this.timeLimitSeconds = Math.max(0, timeLimitSeconds);
    }

    public boolean timeLimitEnabled() {
        return timeLimitSeconds > 0;
    }

    public int getCountdownSeconds() {
        return countdownSeconds;
    }

    public void setCountdownSeconds(int countdownSeconds) {
        this.countdownSeconds = Math.max(0, countdownSeconds);
    }
}

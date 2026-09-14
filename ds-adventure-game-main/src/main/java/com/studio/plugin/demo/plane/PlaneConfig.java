package com.studio.plugin.demo.plane;

/**
 * 飞机大战玩法配置。
 *
 * <p>数值对齐《需求规格说明书》F5：生命 3、击落 20 架通关、击落 1 架 10 分、
 * 敌机生成间隔从 3 秒起每秒递减 0.1 秒、下限 0.5 秒、护盾持续 3 秒。</p>
 *
 * <p>几何量（场地、顶栏、玩家/敌机尺寸）也集中在这里，供规则引擎与界面共用同一份，
 * 避免两侧各写一遍常量而失配。</p>
 */
public final class PlaneConfig {

    /** 顶部信息栏高度（像素），玩法区域从它下面开始。 */
    private static final int DEFAULT_FIELD_TOP = 56;
    /** 玩家开局位置距场地底边的距离（像素）。 */
    private static final int PLAYER_LANE_BOTTOM_MARGIN = 90;

    private int viewWidth = 800;
    private int viewHeight = 600;
    private int fieldTop = DEFAULT_FIELD_TOP;

    private int playerWidth = 40;
    private int playerHeight = 44;
    private double playerSpeed = 340;

    private int bulletWidth = 5;
    private int bulletHeight = 14;
    private double bulletSpeed = 640;
    /** 连续射击的最小间隔（秒）。 */
    private double bulletCooldown = 0.16;

    private int enemyWidth = 38;
    private int enemyHeight = 34;
    private double enemySpeedBase = 95;
    /** 每过 1 秒，敌机下落速度增加的像素数（难度曲线）。 */
    private double enemySpeedPerSecond = 3.5;
    private double enemyFireInterval = 2.4;
    /** 敌机出现后到"第一次开火"的随机区间（秒），越小越快进入交火。 */
    private double enemyFirstFireMin = 0.30;
    private double enemyFirstFireMax = 0.85;
    private double enemyBulletSpeed = 250;
    private int enemyBulletRadius = 5;

    private int initialLives = 3;
    private int winKills = 20;
    private int killScore = 10;

    private double spawnInterval = 3.0;
    private double spawnDecrease = 0.1;
    private double spawnMin = 0.5;

    private double shieldDuration = 3.0;

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

    public int getPlayerWidth() {
        return playerWidth;
    }

    public void setPlayerWidth(int playerWidth) {
        this.playerWidth = playerWidth;
    }

    public int getPlayerHeight() {
        return playerHeight;
    }

    public void setPlayerHeight(int playerHeight) {
        this.playerHeight = playerHeight;
    }

    public double getPlayerSpeed() {
        return playerSpeed;
    }

    public void setPlayerSpeed(double playerSpeed) {
        this.playerSpeed = playerSpeed;
    }

    public int getBulletWidth() {
        return bulletWidth;
    }

    public void setBulletWidth(int bulletWidth) {
        this.bulletWidth = bulletWidth;
    }

    public int getBulletHeight() {
        return bulletHeight;
    }

    public void setBulletHeight(int bulletHeight) {
        this.bulletHeight = bulletHeight;
    }

    public double getBulletSpeed() {
        return bulletSpeed;
    }

    public void setBulletSpeed(double bulletSpeed) {
        this.bulletSpeed = bulletSpeed;
    }

    public double getBulletCooldown() {
        return bulletCooldown;
    }

    public void setBulletCooldown(double bulletCooldown) {
        this.bulletCooldown = bulletCooldown;
    }

    public int getEnemyWidth() {
        return enemyWidth;
    }

    public void setEnemyWidth(int enemyWidth) {
        this.enemyWidth = enemyWidth;
    }

    public int getEnemyHeight() {
        return enemyHeight;
    }

    public void setEnemyHeight(int enemyHeight) {
        this.enemyHeight = enemyHeight;
    }

    public double getEnemySpeedBase() {
        return enemySpeedBase;
    }

    public void setEnemySpeedBase(double enemySpeedBase) {
        this.enemySpeedBase = enemySpeedBase;
    }

    public double getEnemySpeedPerSecond() {
        return enemySpeedPerSecond;
    }

    public void setEnemySpeedPerSecond(double enemySpeedPerSecond) {
        this.enemySpeedPerSecond = enemySpeedPerSecond;
    }

    public double getEnemyFireInterval() {
        return enemyFireInterval;
    }

    public void setEnemyFireInterval(double enemyFireInterval) {
        this.enemyFireInterval = enemyFireInterval;
    }

    public double getEnemyFirstFireMin() {
        return enemyFirstFireMin;
    }

    public void setEnemyFirstFireMin(double enemyFirstFireMin) {
        this.enemyFirstFireMin = enemyFirstFireMin;
    }

    public double getEnemyFirstFireMax() {
        return enemyFirstFireMax;
    }

    public void setEnemyFirstFireMax(double enemyFirstFireMax) {
        this.enemyFirstFireMax = enemyFirstFireMax;
    }

    public double getEnemyBulletSpeed() {
        return enemyBulletSpeed;
    }

    public void setEnemyBulletSpeed(double enemyBulletSpeed) {
        this.enemyBulletSpeed = enemyBulletSpeed;
    }

    public int getEnemyBulletRadius() {
        return enemyBulletRadius;
    }

    public void setEnemyBulletRadius(int enemyBulletRadius) {
        this.enemyBulletRadius = enemyBulletRadius;
    }

    public int getInitialLives() {
        return initialLives;
    }

    public void setInitialLives(int initialLives) {
        this.initialLives = initialLives;
    }

    public int getWinKills() {
        return winKills;
    }

    public void setWinKills(int winKills) {
        this.winKills = winKills;
    }

    public int getKillScore() {
        return killScore;
    }

    public void setKillScore(int killScore) {
        this.killScore = killScore;
    }

    public double getSpawnInterval() {
        return spawnInterval;
    }

    public void setSpawnInterval(double spawnInterval) {
        this.spawnInterval = spawnInterval;
    }

    public double getSpawnDecrease() {
        return spawnDecrease;
    }

    public void setSpawnDecrease(double spawnDecrease) {
        this.spawnDecrease = spawnDecrease;
    }

    public double getSpawnMin() {
        return spawnMin;
    }

    public void setSpawnMin(double spawnMin) {
        this.spawnMin = spawnMin;
    }

    public double getShieldDuration() {
        return shieldDuration;
    }

    public void setShieldDuration(double shieldDuration) {
        this.shieldDuration = shieldDuration;
    }

    // ---------------- 由参数推导 ----------------

    /** 玩家纵向可活动的最低位置（机身中心），略高于场地底边。 */
    public double getPlayerBottomLimit() {
        return viewHeight - playerHeight / 2.0;
    }

    /** 玩家开局的纵向位置（机身中心）：停在靠下的"车道"上。 */
    public double getBottomLaneY() {
        return viewHeight - PLAYER_LANE_BOTTOM_MARGIN;
    }

    /** 玩家纵向可活动的最高位置：不允许顶到信息栏里。 */
    public double getPlayerTopLimit() {
        return fieldTop + playerHeight / 2.0;
    }

    /** 玩家横向可活动范围（机身中心）。 */
    public double getPlayerLeftLimit() {
        return playerWidth / 2.0;
    }

    public double getPlayerRightLimit() {
        return viewWidth - playerWidth / 2.0;
    }

    /** 当前时刻的敌机生成间隔：随时间递减，到下限后不再减少。 */
    public double spawnIntervalAt(double elapsedSeconds) {
        double interval = spawnInterval - elapsedSeconds * spawnDecrease;
        return Math.max(spawnMin, interval);
    }

    /** 当前时刻的敌机下落速度：随时间缓慢加快。 */
    public double enemySpeedAt(double elapsedSeconds) {
        return enemySpeedBase + elapsedSeconds * enemySpeedPerSecond;
    }
}

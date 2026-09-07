package com.lab.galgame.model;

/**
 * 游戏状态（模型层）：玩家位置、暂停/运行标记。
 * 以后 Galgame 的剧情进度、存档数据也归这里。
 */
public class GameState {
    private double playerX;
    private double playerY;
    private boolean paused;
    private boolean running;

    public void reset(double startX, double startY) {
        this.playerX = startX;
        this.playerY = startY;
        this.paused = false;
    }

    public double getPlayerX() {
        return playerX;
    }

    public void setPlayerX(double playerX) {
        this.playerX = playerX;
    }

    public double getPlayerY() {
        return playerY;
    }

    public void setPlayerY(double playerY) {
        this.playerY = playerY;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }
}

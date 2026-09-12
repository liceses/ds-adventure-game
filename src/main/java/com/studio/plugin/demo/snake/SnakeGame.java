package com.studio.plugin.demo.snake;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * 贪吃蛇纯规则引擎（<b>不 import javafx</b>），可脱离界面单元测试。
 *
 * <p>对齐《需求规格说明书》F4 的验收要点：</p>
 * <ul>
 *   <li>区域 10×10 格；蛇初始长度 3；每吃 1 豆 分数 +1、长度 +1；</li>
 *   <li>累计吃豆 <b>97</b> 即通关（3 + 97 = 占满 100 格）；</li>
 *   <li>移动速度 = {@code 初始速度 + 0.01 × 已玩秒数}（由 {@link #speed()} 给出，格/秒）；</li>
 *   <li>允许 180° 反向；反向导致蛇头与身体重叠时判失败（见 {@link #step(double)}）；</li>
 *   <li>蛇头越界或与自身重叠 → 失败。</li>
 * </ul>
 *
 * <p>时间推进由 {@link #step(double)} 接收帧间隔，因此同一总时长无论分几帧推进，
 * 结果一致（帧率无关）。</p>
 */
public class SnakeGame {

    /** 本局状态 */
    public enum State { RUNNING, WIN, LOSE }

    /** 方向（含 180° 反向判断） */
    public enum Direction {
        UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

        public final int dx;
        public final int dy;

        Direction(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        /** 是否与另一方向相反（180°） */
        public boolean isOpposite(Direction other) {
            return other != null && dx + other.dx == 0 && dy + other.dy == 0;
        }
    }

    private final SnakeConfig config;
    private final Random random;
    /** 蛇身：第一个元素为蛇头 */
    private final Deque<int[]> snake = new ArrayDeque<>();
    private final List<int[]> obstacles = new ArrayList<>();

    private Direction direction = Direction.RIGHT;
    private Direction pendingDirection = Direction.RIGHT;
    private int[] food = {0, 0};
    private int score;
    private double elapsedSeconds;
    private double moveAccumulator;
    private State state = State.RUNNING;

    public SnakeGame(SnakeConfig config) {
        this(config, new Random(20260912L));
    }

    public SnakeGame(SnakeConfig config, Random random) {
        this.config = config == null ? SnakeConfig.defaults() : config;
        this.random = random == null ? new Random() : random;
        reset();
    }

    public SnakeConfig config() {
        return config;
    }

    /** 重置到初始初态（蛇初始长度 3、居中偏左、朝右） */
    public void reset() {
        snake.clear();
        obstacles.clear();
        score = 0;
        elapsedSeconds = 0;
        moveAccumulator = 0;
        state = State.RUNNING;
        direction = Direction.RIGHT;
        pendingDirection = Direction.RIGHT;

        int cy = config.getRows() / 2;
        int headX = Math.max(config.getInitialLength() - 1, config.getCols() / 2);
        for (int i = 0; i < config.getInitialLength(); i++) {
            snake.addLast(new int[]{headX - i, cy});
        }
        generateObstacles();
        placeFoodRandomly();
    }

    /** 请求转向：允许 180° 反向；若反向导致蛇头与身体重叠，会在下一步判失败 */
    public void setDirection(Direction d) {
        if (d == null) {
            return;
        }
        pendingDirection = d;
    }

    /** 按帧间隔推进：累计时间，按当前速度换算成整格移动 */
    public void step(double dt) {
        if (state != State.RUNNING || dt <= 0) {
            return;
        }
        elapsedSeconds += dt;
        if (config.timeLimitEnabled() && elapsedSeconds >= config.getTimeLimitSeconds()) {
            state = State.LOSE;   // 限时到（默认关闭，不影响 F4 验收）
            return;
        }
        moveAccumulator += dt;

        double interval = 1.0 / speed();
        int guard = 0;
        while (state == State.RUNNING && moveAccumulator >= interval && guard++ < 1000) {
            moveAccumulator -= interval;
            advanceOneCell();
            interval = 1.0 / speed();
        }
    }

    /** 推进一格 */
    private void advanceOneCell() {
        direction = pendingDirection;
        int[] head = snake.peekFirst();
        if (head == null) {
            state = State.LOSE;
            return;
        }
        int nx = head[0] + direction.dx;
        int ny = head[1] + direction.dy;

        // 撞墙
        if (nx < 0 || ny < 0 || nx >= config.getCols() || ny >= config.getRows()) {
            state = State.LOSE;
            return;
        }

        boolean willEat = (nx == food[0] && ny == food[1]);

        // 撞自身：不吃时尾巴会让位，故末位不参与判定
        List<int[]> cells = new ArrayList<>(snake);
        for (int i = 0; i < cells.size(); i++) {
            if (!willEat && i == cells.size() - 1) {
                continue;
            }
            int[] c = cells.get(i);
            if (c[0] == nx && c[1] == ny) {
                state = State.LOSE;   // 含“180° 反向撞到身体”的情形
                return;
            }
        }

        snake.addFirst(new int[]{nx, ny});
        if (willEat) {
            score++;
            if (score >= config.getWinBeans()) {
                state = State.WIN;
                return;
            }
            placeFoodRandomly();
        } else {
            snake.removeLast();
        }
    }

    /** 当前移动速度（格/秒）= 初始速度 + 0.01 × 已玩秒数 */
    public double speed() {
        return config.getInitialSpeed() + config.getSpeedGrowthPerSecond() * elapsedSeconds;
    }

    /** 在指定格放置食物（供测试与关卡设计使用；越界会被忽略） */
    public void placeFood(int x, int y) {
        if (x < 0 || y < 0 || x >= config.getCols() || y >= config.getRows()) {
            return;
        }
        food = new int[]{x, y};
    }

    private void placeFoodRandomly() {
        List<int[]> empty = new ArrayList<>();
        for (int y = 0; y < config.getRows(); y++) {
            for (int x = 0; x < config.getCols(); x++) {
                if (!occupied(x, y)) {
                    empty.add(new int[]{x, y});
                }
            }
        }
        if (empty.isEmpty()) {
            state = State.WIN;   // 棋盘填满（F4：3 + 97 = 100 格）
            return;
        }
        food = empty.get(random.nextInt(empty.size()));
    }

    private void generateObstacles() {
        int want = config.getObstacleCount();
        int guard = 0;
        while (obstacles.size() < want && guard++ < 1000) {
            int x = random.nextInt(config.getCols());
            int y = random.nextInt(config.getRows());
            if (occupied(x, y)) {
                continue;
            }
            obstacles.add(new int[]{x, y});
        }
    }

    private boolean occupied(int x, int y) {
        if (x == food[0] && y == food[1]) {
            return true;
        }
        for (int[] c : snake) {
            if (c[0] == x && c[1] == y) {
                return true;
            }
        }
        for (int[] c : obstacles) {
            if (c[0] == x && c[1] == y) {
                return true;
            }
        }
        return false;
    }

    public State getState() {
        return state;
    }

    public int getScore() {
        return score;
    }

    public int getLength() {
        return snake.size();
    }

    public boolean isWin() {
        return state == State.WIN;
    }

    public boolean isLose() {
        return state == State.LOSE;
    }

    public boolean isOver() {
        return state != State.RUNNING;
    }

    public double getElapsedSeconds() {
        return elapsedSeconds;
    }

    /** 蛇身格子副本（头在前） */
    public List<int[]> snakeCells() {
        return new ArrayList<>(snake);
    }

    /** 食物格（[x, y]） */
    public int[] getFood() {
        return new int[]{food[0], food[1]};
    }

    /** 障碍格副本 */
    public List<int[]> obstacles() {
        return new ArrayList<>(obstacles);
    }

    public Direction getDirection() {
        return direction;
    }
}

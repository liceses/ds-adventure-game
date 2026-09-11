package com.example.snake;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.LinkedList;
import java.util.Random;

public class GamePanel {

    // =========================
    // 游戏基本设置
    // =========================

    private static final int WIDTH = 600;
    private static final int HEIGHT = 600;

    private static final int CELL_SIZE = 20;

    private static final int COLS = WIDTH / CELL_SIZE;
    private static final int ROWS = HEIGHT / CELL_SIZE;

    // 游戏总时间：60秒
    private static final long GAME_TIME = 60_000_000_000L;

    // 通关所需分数
    private static final int WIN_SCORE = 15;

    // 障碍物数量
    private static final int OBSTACLE_COUNT = 12;


    // =========================
    // 游戏对象
    // =========================

    // 蛇
    private final LinkedList<int[]> snake = new LinkedList<>();

    // 障碍物
    private final LinkedList<int[]> obstacles = new LinkedList<>();

    // 随机数
    private final Random random = new Random();

    // 画布
    private final Canvas canvas;

    // 画笔
    private final GraphicsContext gc;


    // =========================
    // 游戏状态
    // =========================

    private Dir currentDir = Dir.RIGHT;

    private Dir nextDir = Dir.RIGHT;

    // 食物坐标
    private int foodX;
    private int foodY;

    // 分数
    private int score;

    // 游戏是否结束
    private boolean gameOver;

    // 是否通关
    private boolean win;

    // 是否正在倒计时
    private boolean countingDown = true;

    // 倒计时开始时间
    private long countdownStartTime;

    // 游戏开始时间
    private long gameStartTime;

    // 当前剩余时间
    private int remainingTime = 60;

    // 倒计时数字
    private int countdownNumber = 3;


    // =========================
    // 蛇移动速度
    // =========================

    private long lastMoveTime = 0;

    private long moveInterval = 150_000_000L;


    // =========================
    // 游戏计时器
    // =========================

    private AnimationTimer timer;


    // =========================
    // 构造方法
    // =========================

    public GamePanel(Canvas canvas) {

        this.canvas = canvas;

        this.gc = canvas.getGraphicsContext2D();

        restart();
    }


    // =========================
    // 开始游戏
    // =========================

    public void start() {

        countdownStartTime = System.nanoTime();

        timer = new AnimationTimer() {

            @Override
            public void handle(long now) {

                // =====================
                // 3秒开始倒计时
                // =====================

                if (countingDown) {

                    long elapsed =
                            now - countdownStartTime;

                    countdownNumber =
                            3 - (int) (elapsed / 1_000_000_000L);

                    if (elapsed >= 3_000_000_000L) {

                        countingDown = false;

                        gameStartTime = now;

                        lastMoveTime = now;
                    }

                    draw();

                    return;
                }


                // =====================
                // 游戏结束
                // =====================

                if (gameOver) {

                    draw();

                    return;
                }


                // =====================
                // 计算游戏剩余时间
                // =====================

                long gameElapsed =
                        now - gameStartTime;

                long remaining =
                        GAME_TIME - gameElapsed;

                remainingTime =
                        Math.max(
                                0,
                                (int) Math.ceil(
                                        remaining / 1_000_000_000.0
                                )
                        );


                // =====================
                // 时间到了
                // =====================

                if (gameElapsed >= GAME_TIME) {

                    gameOver = true;

                    if (score >= WIN_SCORE) {
                        win = true;
                    } else {
                        win = false;
                    }

                    draw();

                    return;
                }


                // =====================
                // 蛇移动
                // =====================

                if (now - lastMoveTime >= moveInterval) {

                    move();

                    draw();

                    lastMoveTime = now;
                }
            }
        };

        timer.start();
    }


    // =========================
    // 修改蛇的方向
    // =========================

    public void changeDirection(Dir dir) {

        // 防止直接掉头

        if (currentDir == Dir.UP &&
                dir == Dir.DOWN) {
            return;
        }

        if (currentDir == Dir.DOWN &&
                dir == Dir.UP) {
            return;
        }

        if (currentDir == Dir.LEFT &&
                dir == Dir.RIGHT) {
            return;
        }

        if (currentDir == Dir.RIGHT &&
                dir == Dir.LEFT) {
            return;
        }

        nextDir = dir;
    }


    // =========================
    // 蛇移动
    // =========================

    private void move() {

        currentDir = nextDir;

        int[] head = snake.getFirst();

        int newX = head[0];

        int newY = head[1];


        // 根据方向计算新位置

        switch (currentDir) {

            case UP:
                newY--;
                break;

            case DOWN:
                newY++;
                break;

            case LEFT:
                newX--;
                break;

            case RIGHT:
                newX++;
                break;
        }


        // =====================
        // 撞墙
        // =====================

        if (newX < 0 ||
                newX >= COLS ||
                newY < 0 ||
                newY >= ROWS) {

            gameOver = true;

            return;
        }


        // =====================
        // 撞自己
        // =====================

        for (int[] part : snake) {

            if (part[0] == newX &&
                    part[1] == newY) {

                gameOver = true;

                return;
            }
        }


        // =====================
        // 撞障碍物
        // =====================

        for (int[] obstacle : obstacles) {

            if (obstacle[0] == newX &&
                    obstacle[1] == newY) {

                gameOver = true;

                return;
            }
        }


        // 添加新的蛇头

        snake.addFirst(
                new int[]{newX, newY}
        );


        // =====================
        // 吃食物
        // =====================

        if (newX == foodX &&
                newY == foodY) {

            score++;

            generateFood();

            updateSpeed();

        } else {

            // 没有吃到食物
            // 删除蛇尾

            snake.removeLast();
        }
    }


    // =========================
    // 生成食物
    // =========================

    private void generateFood() {

        while (true) {

            int x =
                    random.nextInt(COLS);

            int y =
                    random.nextInt(ROWS);

            boolean occupied = false;


            // 判断是否在蛇身上

            for (int[] part : snake) {

                if (part[0] == x &&
                        part[1] == y) {

                    occupied = true;

                    break;
                }
            }


            // 判断是否在障碍物上

            if (!occupied) {

                for (int[] obstacle : obstacles) {

                    if (obstacle[0] == x &&
                            obstacle[1] == y) {

                        occupied = true;

                        break;
                    }
                }
            }


            if (!occupied) {

                foodX = x;

                foodY = y;

                break;
            }
        }
    }


    // =========================
    // 生成障碍物
    // =========================

    private void generateObstacles() {

        obstacles.clear();


        while (obstacles.size() < OBSTACLE_COUNT) {

            int x =
                    random.nextInt(COLS);

            int y =
                    random.nextInt(ROWS);


            // 不要生成在蛇附近

            if (x >= 12 &&
                    x <= 18 &&
                    y >= 13 &&
                    y <= 17) {

                continue;
            }


            // 不要重复

            boolean exists = false;

            for (int[] obstacle : obstacles) {

                if (obstacle[0] == x &&
                        obstacle[1] == y) {

                    exists = true;

                    break;
                }
            }


            if (!exists) {

                obstacles.add(
                        new int[]{x, y}
                );
            }
        }
    }


    // =========================
    // 增加游戏速度
    // =========================

    private void updateSpeed() {

        moveInterval =
                Math.max(
                        60_000_000L,
                        150_000_000L
                                - score * 5_000_000L
                );
    }


    // =========================
    // 绘制游戏
    // =========================

    private void draw() {

        // 黑色背景

        gc.setFill(Color.BLACK);

        gc.fillRect(
                0,
                0,
                WIDTH,
                HEIGHT
        );


        // 网格

        drawGrid();


        // 障碍物

        drawObstacles();


        // 食物

        drawFood();


        // 蛇

        drawSnake();


        // 分数

        drawScore();


        // 时间

        drawTime();


        // 游戏开始倒计时

        if (countingDown) {

            drawCountdown();
        }


        // 游戏结束

        if (gameOver) {

            drawGameOver();
        }
    }


    // =========================
    // 绘制网格
    // =========================

    private void drawGrid() {

        gc.setStroke(
                Color.rgb(40, 40, 40)
        );


        for (int x = 0;
             x <= WIDTH;
             x += CELL_SIZE) {

            gc.strokeLine(
                    x,
                    0,
                    x,
                    HEIGHT
            );
        }


        for (int y = 0;
             y <= HEIGHT;
             y += CELL_SIZE) {

            gc.strokeLine(
                    0,
                    y,
                    WIDTH,
                    y
            );
        }
    }


    // =========================
    // 绘制蛇
    // =========================

    private void drawSnake() {

        // 彩虹颜色

        Color[] colors = {

                Color.LIMEGREEN,

                Color.YELLOWGREEN,

                Color.YELLOW,

                Color.ORANGE,

                Color.ORANGERED,

                Color.HOTPINK,

                Color.MEDIUMPURPLE,

                Color.DEEPSKYBLUE,

                Color.TURQUOISE
        };


        for (int i = 0;
             i < snake.size();
             i++) {

            int[] part =
                    snake.get(i);


            // 蛇头稍微特殊一点

            if (i == 0) {

                gc.setFill(
                        Color.LIME
                );

            } else {

                gc.setFill(
                        colors[
                                (i - 1)
                                        % colors.length
                                ]
                );
            }


            gc.fillRoundRect(

                    part[0] * CELL_SIZE,

                    part[1] * CELL_SIZE,

                    CELL_SIZE - 1,

                    CELL_SIZE - 1,

                    5,

                    5
            );
        }
    }


    // =========================
    // 绘制障碍物
    // =========================

    private void drawObstacles() {

        for (int[] obstacle :
                obstacles) {

            int x =
                    obstacle[0]
                            * CELL_SIZE;

            int y =
                    obstacle[1]
                            * CELL_SIZE;


            // 障碍物

            gc.setFill(
                    Color.DARKGRAY
            );

            gc.fillRoundRect(
                    x + 1,
                    y + 1,
                    CELL_SIZE - 2,
                    CELL_SIZE - 2,
                    5,
                    5
            );


            // 障碍物边框

            gc.setStroke(
                    Color.GRAY
            );

            gc.strokeRoundRect(
                    x + 2,
                    y + 2,
                    CELL_SIZE - 4,
                    CELL_SIZE - 4,
                    4,
                    4
            );
        }
    }


    // =========================
    // 绘制食物
    // =========================

    private void drawFood() {

        gc.setFill(
                Color.RED
        );

        gc.fillOval(

                foodX * CELL_SIZE + 2,

                foodY * CELL_SIZE + 2,

                CELL_SIZE - 4,

                CELL_SIZE - 4
        );
    }


    // =========================
    // 绘制分数
    // =========================

    private void drawScore() {

        gc.setFill(
                Color.WHITE
        );

        gc.setFont(
                Font.font(16)
        );

        gc.fillText(
                "Score: " + score
                        + " / " + WIN_SCORE,
                15,
                25
        );
    }


    // =========================
    // 绘制时间
    // =========================

    private void drawTime() {

        gc.setFill(
                Color.WHITE
        );

        gc.setFont(
                Font.font(16)
        );

        gc.fillText(
                "Time: "
                        + remainingTime
                        + "s",
                500,
                25
        );
    }


    // =========================
    // 绘制开始倒计时
    // =========================

    private void drawCountdown() {

        gc.setFill(
                Color.rgb(
                        0,
                        0,
                        0,
                        0.7
                )
        );

        gc.fillRect(
                0,
                0,
                WIDTH,
                HEIGHT
        );


        gc.setFill(
                Color.WHITE
        );

        gc.setFont(
                Font.font(80)
        );


        String text =
                String.valueOf(
                        countdownNumber
                );


        gc.fillText(
                text,
                WIDTH / 2.0 - 25,
                HEIGHT / 2.0 + 25
        );


        gc.setFont(
                Font.font(20)
        );

        gc.fillText(
                "准备开始！",
                WIDTH / 2.0 - 50,
                HEIGHT / 2.0 + 70
        );
    }


    // =========================
    // 游戏结束画面
    // =========================

    private void drawGameOver() {

        gc.setFill(
                Color.rgb(
                        0,
                        0,
                        0,
                        0.8
                )
        );

        gc.fillRect(
                0,
                0,
                WIDTH,
                HEIGHT
        );


        gc.setFont(
                Font.font(40)
        );


        // 通关

        if (win) {

            gc.setFill(
                    Color.LIMEGREEN
            );

            gc.fillText(
                    "🎉 挑战成功！",
                    180,
                    250
            );

        } else {

            gc.setFill(
                    Color.RED
            );

            gc.fillText(
                    "挑战失败",
                    205,
                    250
            );
        }


        // 分数

        gc.setFill(
                Color.WHITE
        );

        gc.setFont(
                Font.font(22)
        );

        gc.fillText(
                "最终得分："
                        + score,
                220,
                300
        );


        // 通关要求

        gc.fillText(
                "通关要求："
                        + WIN_SCORE
                        + " 分",
                220,
                340
        );


        // 重新开始

        gc.setFont(
                Font.font(18)
        );

        gc.fillText(
                "按 SPACE 重新开始",
                205,
                390
        );
    }


    // =========================
    // 重新开始
    // =========================

    public void restart() {

        // 清空蛇

        snake.clear();


        // 初始蛇

        snake.add(
                new int[]{15, 15}
        );

        snake.add(
                new int[]{14, 15}
        );

        snake.add(
                new int[]{13, 15}
        );


        // 初始方向

        currentDir =
                Dir.RIGHT;

        nextDir =
                Dir.RIGHT;


        // 分数

        score = 0;


        // 游戏状态

        gameOver = false;

        win = false;


        // 倒计时

        countingDown = true;

        countdownNumber = 3;

        countdownStartTime =
                System.nanoTime();


        // 游戏时间

        remainingTime = 60;


        // 恢复速度

        moveInterval =
                150_000_000L;


        // 生成障碍物

        generateObstacles();


        // 生成食物

        generateFood();


        // 绘制

        draw();


        // 重置移动时间

        lastMoveTime = 0;
    }
}
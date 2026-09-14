package com.studio.plugin;

/**
 * 小游戏结果：插件通过 {@link GamePlugin#PARAM_RESULT_SINK} 回传给引擎，
 * 引擎据此在剧情里路由到 {@code mg.onWin} / {@code mg.onLose} 指定的场景。
 *
 * <p>约定（对齐《剧情-游戏逻辑交接文档》§4）：</p>
 * <ul>
 *   <li>{@link #win()} 为胜负；{@link #score()} 为可选分数（仅记录）；</li>
 *   <li>插件未回传结果时，引擎按<b>胜利</b>处理（保证剧情不阻塞）；</li>
 *   <li>玩家在局中主动退出，按需求记为失败（由插件在返回前回传 lose）。</li>
 * </ul>
 */
public final class MiniGameResult {

    private final boolean win;
    private final int score;

    public MiniGameResult(boolean win, int score) {
        this.win = win;
        this.score = score;
    }

    public static MiniGameResult win(int score) {
        return new MiniGameResult(true, score);
    }

    public static MiniGameResult lose(int score) {
        return new MiniGameResult(false, score);
    }

    public boolean win() {
        return win;
    }

    public int score() {
        return score;
    }

    @Override
    public String toString() {
        return (win ? "通关" : "失败") + "(score=" + score + ")";
    }
}

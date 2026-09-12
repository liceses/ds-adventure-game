package com.studio.plugin.demo.memory;

/**
 * 记忆翻牌玩法配置。
 *
 * <p>牌面图案按主题从内置符号表里取，不依赖任何美术素材；总牌数必须为偶数，
 * 网格尺寸在 {@link #setGrid(int, int)} 里统一校正，避免出现半对牌。</p>
 */
public final class MemoryConfig {

    /** 图案主题：动物。 */
    public static final String THEME_ANIMALS = "animals";
    /** 图案主题：水果。 */
    public static final String THEME_FRUITS = "fruits";
    /** 图案主题：汉字。 */
    public static final String THEME_HANZI = "hanzi";

    private static final String[] ANIMALS = {
        "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
        "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔",
        "🐧", "🦄", "🐝", "🦋", "🐢", "🐙", "🦀", "🐬",
    };

    private static final String[] FRUITS = {
        "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓",
        "🫐", "🍒", "🍑", "🥝", "🍍", "🥥", "🥕", "🌽",
        "🍄", "🌰", "🥜", "🍞", "🧀", "🍩", "🍪", "🎂",
    };

    private static final String[] HANZI = {
        "日", "月", "山", "川", "水", "火", "木", "金",
        "土", "石", "田", "雨", "云", "风", "花", "鸟",
        "鱼", "虫", "龙", "虎", "马", "牛", "羊", "人",
    };

    /** 单边最小格数。 */
    private static final int MIN_SIDE = 2;
    /** 单边最大格数。 */
    private static final int MAX_SIDE = 8;
    /** 总牌数上限，等于符号表容量（保证每对图案都不重复）。 */
    private static final int MAX_CARDS = 24 * 2;

    private int rows = 4;
    private int columns = 6;
    private double flipBackDelay = 0.9;
    private double peekSeconds = 1.2;
    private int peekUses = 1;
    private boolean twoPlayer;
    private String theme = THEME_ANIMALS;

    public MemoryConfig() {
        setGrid(rows, columns);
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return columns;
    }

    /** 设置网格尺寸；会自动夹到合法范围，并把总牌数校正为偶数。 */
    public void setGrid(int rows, int columns) {
        int r = clamp(rows, MIN_SIDE, MAX_SIDE);
        int c = clamp(columns, MIN_SIDE, MAX_SIDE);
        while (r * c > MAX_CARDS && c > MIN_SIDE) {
            c--;
        }
        if ((r * c) % 2 != 0 && c > MIN_SIDE) {
            c--;
        }
        this.rows = r;
        this.columns = c;
    }

    /** 总牌数（必为偶数）。 */
    public int getCardCount() {
        return rows * columns;
    }

    /** 对子数。 */
    public int getTotalPairs() {
        return getCardCount() / 2;
    }

    public double getFlipBackDelay() {
        return flipBackDelay;
    }

    /** 两张不相同的牌翻开后停留多久再盖回去（秒）。 */
    public void setFlipBackDelay(double flipBackDelay) {
        this.flipBackDelay = Math.max(0.1, flipBackDelay);
    }

    public double getPeekSeconds() {
        return peekSeconds;
    }

    /** 使用「提示」后所有牌展示多久（秒）。 */
    public void setPeekSeconds(double peekSeconds) {
        this.peekSeconds = Math.max(0.2, peekSeconds);
    }

    public int getPeekUses() {
        return peekUses;
    }

    /** 每局可用的提示次数，0 表示关闭提示。 */
    public void setPeekUses(int peekUses) {
        this.peekUses = Math.max(0, peekUses);
    }

    public boolean isTwoPlayer() {
        return twoPlayer;
    }

    /** 是否双人轮流模式（翻到对子继续，翻错换人）。 */
    public void setTwoPlayer(boolean twoPlayer) {
        this.twoPlayer = twoPlayer;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        if (THEME_FRUITS.equals(theme) || THEME_HANZI.equals(theme)) {
            this.theme = theme;
        } else {
            this.theme = THEME_ANIMALS;
        }
    }

    /** 当前主题的符号表。 */
    public String[] getSymbols() {
        if (THEME_FRUITS.equals(theme)) {
            return FRUITS.clone();
        }
        if (THEME_HANZI.equals(theme)) {
            return HANZI.clone();
        }
        return ANIMALS.clone();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

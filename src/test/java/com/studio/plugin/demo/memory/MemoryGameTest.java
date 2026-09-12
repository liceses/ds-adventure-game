package com.studio.plugin.demo.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 记忆翻牌规则引擎单元测试。
 *
 * <p>只依赖 {@link MemoryGame} 与 {@link MemoryConfig}，不需要 JavaFX 运行时。</p>
 */
class MemoryGameTest {

    private static final double FRAME = 1.0 / 60;
    private static final int MAX_LOOP = 500;

    private static MemoryConfig smallConfig() {
        MemoryConfig config = new MemoryConfig();
        config.setGrid(2, 2);
        config.setFlipBackDelay(0.5);
        return config;
    }

    /** 3 对牌的盘面：配对掉一对后仍能找出「图案不同」的两张，便于测换人规则。 */
    private static MemoryConfig threePairConfig() {
        MemoryConfig config = new MemoryConfig();
        config.setGrid(2, 3);
        config.setFlipBackDelay(0.5);
        return config;
    }

    private static MemoryGame newGame(MemoryConfig config) {
        MemoryGame game = new MemoryGame();
        game.reset(config);
        return game;
    }

    /** 找出一张还没配对的牌。 */
    private static int firstFaceDown(MemoryGame game) {
        for (int i = 0; i < game.cardCount(); i++) {
            if (!game.isMatched(i)) {
                return i;
            }
        }
        return -1;
    }

    /** 找出一张与 index 图案不同、且未配对的牌。 */
    private static int firstOther(MemoryGame game, int index) {
        int symbol = game.symbolIndexOf(index);
        for (int i = 0; i < game.cardCount(); i++) {
            if (i != index && !game.isMatched(i) && game.symbolIndexOf(i) != symbol) {
                return i;
            }
        }
        return -1;
    }

    @Test
    @DisplayName("开局：所有牌背面朝上，步数与用时归零")
    void resetHidesAllCards() {
        MemoryGame game = newGame(smallConfig());

        assertEquals(4, game.cardCount());
        assertEquals(2, game.totalPairs());
        assertEquals(0, game.pairsFound());
        assertEquals(0, game.moves());
        assertEquals(0.0, game.elapsedSeconds(), 0.0001);
        for (int i = 0; i < game.cardCount(); i++) {
            assertFalse(game.isFaceUp(i), "开局所有牌都应盖着");
        }
    }

    @Test
    @DisplayName("网格尺寸总是偶数张牌")
    void gridAlwaysHasEvenCardCount() {
        MemoryConfig config = new MemoryConfig();
        config.setGrid(3, 3);
        assertEquals(0, config.getCardCount() % 2, "3x3 应被校正为偶数");

        config.setGrid(99, 99);
        assertEquals(0, config.getCardCount() % 2);
        assertTrue(config.getCardCount() <= 48, "总牌数不应超过符号表容量");
    }

    @Test
    @DisplayName("翻到相同图案：两张都留在正面并得分")
    void matchingCardsStayFaceUp() {
        MemoryGame game = newGame(smallConfig());
        int a = 0;
        int b = game.partnerOf(a);

        game.flip(a);
        assertTrue(game.isFaceUp(a), "第一张应翻开");
        game.flip(b);

        assertTrue(game.isMatched(a));
        assertTrue(game.isMatched(b));
        assertTrue(game.isFaceUp(a));
        assertEquals(1, game.pairsFound());
        assertEquals(1, game.moves());
        assertFalse(game.isComparing());
    }

    @Test
    @DisplayName("翻到不同图案：停留一会儿后自己盖回去")
    void mismatchedCardsHideAfterDelay() {
        MemoryGame game = newGame(smallConfig());
        int a = 0;
        int b = firstOther(game, a);

        game.flip(a);
        game.flip(b);

        assertTrue(game.isComparing(), "不同的两张应进入对比状态");
        assertTrue(game.isFaceUp(a), "对比期间两张都要露着");
        assertTrue(game.isFaceUp(b));

        game.tick(0.2);
        assertTrue(game.isFaceUp(a), "延迟没到不应盖回");

        game.tick(0.5);
        assertFalse(game.isComparing(), "延迟到点应结束对比");
        assertFalse(game.isFaceUp(a));
        assertFalse(game.isFaceUp(b));
        assertEquals(0, game.pairsFound());
        assertEquals(1, game.moves());
    }

    @Test
    @DisplayName("对比期间再翻牌会被忽略")
    void flipIgnoredWhileComparing() {
        MemoryGame game = newGame(smallConfig());
        int a = 0;
        int b = firstOther(game, a);
        game.flip(a);
        game.flip(b);

        int moves = game.moves();
        game.flip(firstFaceDown(game));

        assertEquals(moves, game.moves(), "对比期间不应计入新的步数");
    }

    @Test
    @DisplayName("已经配对的牌不能再翻")
    void matchedCardCannotBeFlipped() {
        MemoryGame game = newGame(smallConfig());
        int a = 0;
        int b = game.partnerOf(a);
        game.flip(a);
        game.flip(b);

        int moves = game.moves();
        game.flip(a);
        game.flip(b);

        assertEquals(moves, game.moves(), "已配对的牌不应再被翻开");
        assertTrue(game.isMatched(a));
    }

    @Test
    @DisplayName("提示：翻开全部牌，次数用完就不能再用")
    void peekRevealsAllAndIsLimited() {
        MemoryConfig config = smallConfig();
        config.setPeekUses(1);
        config.setPeekSeconds(1.0);
        MemoryGame game = newGame(config);

        assertTrue(game.usePeek(), "第一次提示应成功");
        assertEquals(0, game.peekLeft());
        for (int i = 0; i < game.cardCount(); i++) {
            assertTrue(game.isFaceUp(i), "提示期间所有牌都应可见");
        }

        assertFalse(game.usePeek(), "次数用完不应再成功");

        game.tick(1.2);
        assertFalse(game.isPeeking(), "提示时间到应结束");
        assertFalse(game.isFaceUp(0));
    }

    @Test
    @DisplayName("提示期间不接受翻牌")
    void flipIgnoredWhilePeeking() {
        MemoryGame game = newGame(smallConfig());
        game.usePeek();
        int moves = game.moves();

        game.flip(0);

        assertEquals(moves, game.moves());
        assertTrue(game.isPeeking(), "提示期间翻牌不应打断提示");
    }

    @Test
    @DisplayName("双人模式：翻到对子继续，翻错换人")
    void twoPlayerTurnRules() {
        MemoryConfig config = threePairConfig();
        config.setTwoPlayer(true);
        MemoryGame game = newGame(config);

        assertEquals(0, game.currentPlayer());

        int a = 0;
        int b = game.partnerOf(a);
        game.flip(a);
        game.flip(b);
        assertEquals(0, game.currentPlayer(), "翻到对子应继续本回合");
        assertEquals(1, game.pairsOf(0));

        int c = firstFaceDown(game);
        int d = firstOther(game, c);
        game.flip(c);
        game.flip(d);
        assertEquals(0, game.currentPlayer(), "对比还没结束，暂时不换人");

        game.tick(0.6);
        assertEquals(1, game.currentPlayer(), "翻错应在盖回后换人");
    }

    @Test
    @DisplayName("翻完全部对子即通关")
    void clearingAllPairsWins() {
        MemoryGame game = newGame(smallConfig());
        int guard = 0;

        while (!game.isWin() && guard++ < MAX_LOOP) {
            int a = firstFaceDown(game);
            if (a < 0) {
                break;
            }
            game.flip(a);
            game.flip(game.partnerOf(a));
        }

        assertTrue(game.isWin(), "应通关");
        assertTrue(game.isOver());
        assertEquals(game.totalPairs(), game.pairsFound());
    }

    @Test
    @DisplayName("双人模式：对子多的一方获胜")
    void twoPlayerWinnerIsTheOneWithMorePairs() {
        MemoryConfig config = smallConfig();
        config.setTwoPlayer(true);
        MemoryGame game = newGame(config);

        // 玩家0 连吃两对，玩家1 全程没得分
        for (int i = 0; i < config.getTotalPairs(); i++) {
            int a = firstFaceDown(game);
            game.flip(a);
            game.flip(game.partnerOf(a));
        }

        assertTrue(game.isWin());
        assertEquals(2, game.pairsOf(0));
        assertEquals(0, game.pairsOf(1));
        assertEquals(0, game.winner(), "玩家1 应获胜");
    }

    @Test
    @DisplayName("通关后用时不再增长")
    void elapsedStopsAfterWin() {
        MemoryGame game = newGame(smallConfig());
        while (!game.isWin()) {
            int a = firstFaceDown(game);
            game.flip(a);
            game.flip(game.partnerOf(a));
        }

        game.tick(5.0);

        assertEquals(0.0, game.elapsedSeconds(), 0.0001, "通关后不应再计时");
    }

    @Test
    @DisplayName("每张牌都能找到唯一搭档")
    void everyCardHasExactlyOnePartner() {
        MemoryGame game = newGame(new MemoryConfig());

        for (int i = 0; i < game.cardCount(); i++) {
            int partner = game.partnerOf(i);
            assertNotEquals(-1, partner, "第 " + i + " 张应有搭档");
            assertEquals(game.symbolIndexOf(i), game.symbolIndexOf(partner));
            assertEquals(i, game.partnerOf(partner), "搭档关系应互相成立");
        }
    }

    @Test
    @DisplayName("不同主题的符号表都能覆盖最大盘面")
    void everyThemeHasEnoughSymbols() {
        String[] themes = {
            MemoryConfig.THEME_ANIMALS, MemoryConfig.THEME_FRUITS, MemoryConfig.THEME_HANZI,
        };
        MemoryConfig config = new MemoryConfig();
        for (String theme : themes) {
            config.setTheme(theme);
            config.setGrid(8, 6);
            assertTrue(config.getTotalPairs() <= config.getSymbols().length,
                    theme + " 主题符号不够：" + config.getTotalPairs());
        }
    }
}

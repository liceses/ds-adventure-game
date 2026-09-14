package com.studio.plugin.demo.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 记忆翻牌纯规则引擎，不含任何界面代码、不 import javafx。
 *
 * <p>玩法：所有牌背面朝上，每次翻两张，图案相同就留在正面，不同则在
 * {@link MemoryConfig#getFlipBackDelay()} 秒后盖回去；把所有对子都翻出来即通关。
 * 支持单人计时与双人轮流计分两种模式。</p>
 *
 * <p>两张牌不一致时不会立刻盖回，而是进入「对比中」状态，等
 * {@link #tick(double)} 把计时走完才翻回去，给玩家记忆的时间。</p>
 */
public final class MemoryGame {

    /** 双人模式的玩家人数。 */
    private static final int PLAYER_COUNT = 2;
    /** 未翻开任何牌时的哨兵值。 */
    private static final int NO_CARD = -1;

    private final Random random = new Random();
    private final int[] playerPairs = new int[PLAYER_COUNT];

    private MemoryConfig config;
    private int[] cardSymbol = new int[0];
    private boolean[] matched = new boolean[0];

    private int firstCard = NO_CARD;
    private int secondCard = NO_CARD;
    private boolean comparing;
    private double compareTimer;

    private int moves;
    private int matchedPairs;
    private int currentPlayer;
    private double elapsed;
    private int peekLeft;
    private boolean peeking;
    private double peekTimer;
    private boolean won;

    /** 按配置开一局：洗牌、全部盖回、计时归零。 */
    public void reset(MemoryConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config 不能为空");
        }
        this.config = config;

        int count = config.getCardCount();
        int pairs = config.getTotalPairs();
        String[] symbols = config.getSymbols();
        if (pairs > symbols.length) {
            throw new IllegalArgumentException("符号不够：需要 " + pairs + " 种，主题只有 " + symbols.length + " 种");
        }

        cardSymbol = new int[count];
        matched = new boolean[count];

        List<Integer> deck = new ArrayList<>(count);
        for (int i = 0; i < pairs; i++) {
            deck.add(i);
            deck.add(i);
        }
        Collections.shuffle(deck, random);
        for (int i = 0; i < count; i++) {
            cardSymbol[i] = deck.get(i);
        }

        firstCard = NO_CARD;
        secondCard = NO_CARD;
        comparing = false;
        compareTimer = 0;
        moves = 0;
        matchedPairs = 0;
        currentPlayer = 0;
        elapsed = 0;
        peekLeft = config.getPeekUses();
        peeking = false;
        peekTimer = 0;
        won = false;
        playerPairs[0] = 0;
        playerPairs[1] = 0;
    }

    /**
     * 翻开第 index 张牌。
     *
     * <p>以下情况会被忽略：本局已结束、正在对比两张不同的牌、正在提示展示、
     * 该牌已配对、该牌已经是当前翻开的牌。</p>
     */
    public void flip(int index) {
        if (!canFlip(index)) {
            return;
        }

        if (firstCard == NO_CARD) {
            firstCard = index;
            return;
        }

        secondCard = index;
        moves++;
        if (cardSymbol[firstCard] == cardSymbol[secondCard]) {
            resolveMatch();
        } else {
            comparing = true;
            compareTimer = config.getFlipBackDelay();
        }
    }

    private boolean canFlip(int index) {
        if (config == null || isOver() || comparing || peeking) {
            return false;
        }
        if (index < 0 || index >= cardSymbol.length || matched[index]) {
            return false;
        }
        return index != firstCard;
    }

    private void resolveMatch() {
        matched[firstCard] = true;
        matched[secondCard] = true;
        matchedPairs++;
        if (config.isTwoPlayer()) {
            playerPairs[currentPlayer]++;
        }
        firstCard = NO_CARD;
        secondCard = NO_CARD;
        if (matchedPairs == config.getTotalPairs()) {
            won = true;
        }
    }

    /**
     * 按帧间隔推进：累计用时、处理「对比中」的盖回计时与提示展示计时。
     *
     * @param dt 距上一帧的秒数
     */
    public void tick(double dt) {
        if (config == null || dt <= 0) {
            return;
        }
        if (!isOver()) {
            elapsed += dt;
        }

        if (peeking) {
            peekTimer -= dt;
            if (peekTimer <= 0) {
                peeking = false;
            }
        }

        if (comparing) {
            compareTimer -= dt;
            if (compareTimer <= 0) {
                comparing = false;
                firstCard = NO_CARD;
                secondCard = NO_CARD;
                if (config.isTwoPlayer()) {
                    currentPlayer = (currentPlayer + 1) % PLAYER_COUNT;
                }
            }
        }
    }

    /** 使用一次提示：所有牌正面展示一小段时间，次数用完或正在对比时返回 false。 */
    public boolean usePeek() {
        if (config == null || isOver() || peeking || comparing || peekLeft <= 0) {
            return false;
        }
        peekLeft--;
        peeking = true;
        peekTimer = config.getPeekSeconds();
        return true;
    }

    // ---------------- 状态查询（供界面绘制与测试） ----------------

    /** 该牌此刻是否可见（已配对、正在对比的两张、或提示展示中）。 */
    public boolean isFaceUp(int index) {
        if (index < 0 || index >= cardSymbol.length) {
            return false;
        }
        return peeking || matched[index] || index == firstCard || index == secondCard;
    }

    public boolean isMatched(int index) {
        return index >= 0 && index < matched.length && matched[index];
    }

    /** 该牌的图案（无论是否可见）；界面应在 {@link #isFaceUp(int)} 为真时才展示。 */
    public String symbolAt(int index) {
        if (index < 0 || index >= cardSymbol.length) {
            return "";
        }
        return config.getSymbols()[cardSymbol[index]];
    }

    public int cardCount() {
        return cardSymbol.length;
    }

    public int pairsFound() {
        return matchedPairs;
    }

    public int totalPairs() {
        return config == null ? 0 : config.getTotalPairs();
    }

    /** 已经翻牌比较过的次数。 */
    public int moves() {
        return moves;
    }

    public double elapsedSeconds() {
        return elapsed;
    }

    public boolean isTwoPlayer() {
        return config != null && config.isTwoPlayer();
    }

    public int currentPlayer() {
        return currentPlayer;
    }

    public int pairsOf(int player) {
        return player >= 0 && player < PLAYER_COUNT ? playerPairs[player] : 0;
    }

    public int peekLeft() {
        return peekLeft;
    }

    public boolean isPeeking() {
        return peeking;
    }

    /** 是否正在对比两张不同的牌（此时不接受新的翻牌）。 */
    public boolean isComparing() {
        return comparing;
    }

    /** 是否通关（所有对子都翻出）。 */
    public boolean isWin() {
        return won;
    }

    /** 双人模式下的胜者：0 或 1；平局返回 -1；非双人局返回 -1。 */
    public int winner() {
        if (!isTwoPlayer() || !won) {
            return -1;
        }
        if (playerPairs[0] == playerPairs[1]) {
            return -1;
        }
        return playerPairs[0] > playerPairs[1] ? 0 : 1;
    }

    public boolean isOver() {
        return won;
    }

    // ---------------- 同包测试用 ----------------

    /** 仅供同包单元测试使用：读取第 index 张牌真实的图案编号。 */
    int symbolIndexOf(int index) {
        return cardSymbol[index];
    }

    /** 仅供同包单元测试使用：找出与第 index 张牌成对的另一张牌的位置。 */
    int partnerOf(int index) {
        for (int i = 0; i < cardSymbol.length; i++) {
            if (i != index && cardSymbol[i] == cardSymbol[index]) {
                return i;
            }
        }
        return NO_CARD;
    }

    /** 仅供同包单元测试使用：指定随机种子，让洗牌结果可复现。 */
    void seed(long seed) {
        random.setSeed(seed);
    }
}

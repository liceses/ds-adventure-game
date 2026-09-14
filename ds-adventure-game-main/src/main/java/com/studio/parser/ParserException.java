package com.studio.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * 脚本语法异常 / 解析警告。
 * <p>
 * 解析器采用“宽容模式”：能继续解析的结构性小问题会收集为警告，
 * 只有完全无法理解（如花括号不配对导致的死循环风险）才抛出致命异常。
 */
public class ParserException extends RuntimeException {

    private final int line;            // 出错行号（1 起）
    private final List<String> warnings; // 同时携带此前收集的警告

    public ParserException(String message, int line, List<String> warnings) {
        super("第 " + line + " 行: " + message);
        this.line = line;
        this.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
    }

    public ParserException(String message) {
        super(message);
        this.line = -1;
        this.warnings = new ArrayList<>();
    }

    public int line() { return line; }

    /** 全部警告（含致命错误前收集到的） */
    public List<String> warnings() { return warnings; }
}

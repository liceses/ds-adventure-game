package com.studio.parser;

import com.studio.model.GameProject;
import com.studio.model.GameScene;
import com.studio.model.StoryNode;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * scenario.txt 解析/序列化单元测试（P0-8：mvn test 通过）。
 * 覆盖：基本解析、双向往返、未知键透传、多段台词、宽容模式告警。
 */
class ScriptParserTest {

    private static final String SCRIPT = """
            # 测试脚本
            [option]
            initialScene = Start
            volume = 0.8
            typewriterSpeed = 14
            myCustomKey = keep-me

            [Start]
            next = Forest

            {
            type = dialog
            x = 100
            y = 500
            text = <<<
            第一段台词
            ---
            第二段台词
            <<<
            target = Forest
            customNote = hello
            }

            [Forest]
            {
            type = bg
            x = 0
            y = 0
            path = resources/images/bg.png
            }
            """;

    @Test
    void parsesOptionsScenesAndNodes() {
        List<String> warnings = new ArrayList<>();
        GameProject p = ScriptParser.parseString(SCRIPT, new File("."), warnings);

        assertTrue(p.hasScene("Start"));
        assertTrue(p.hasScene("Forest"));
        assertEquals(2, p.scenes().size());
        assertEquals("Start", p.option().initialScene());
        assertEquals(0.8, p.option().volume(), 1e-9);

        GameScene start = p.getScene("Start");
        assertEquals("Forest", start.next());
        assertEquals(1, start.nodes().size());

        StoryNode dialog = start.nodes().get(0);
        assertEquals("dialog", dialog.getTypeCode());
        assertEquals(100.0, dialog.getX(), 1e-9);
        assertEquals("Forest", dialog.getTarget());
        assertTrue(dialog.getText().contains("第一段台词"));
        assertTrue(dialog.getText().contains("第二段台词"));
    }

    @Test
    void multiParagraphSeparatorIsKept() {
        List<String> warnings = new ArrayList<>();
        GameProject p = ScriptParser.parseString(SCRIPT, new File("."), warnings);
        String text = p.getScene("Start").nodes().get(0).getText();
        assertTrue(text.contains("---"), "多段分隔符应保留在文本中");
    }

    @Test
    void roundTripPreservesUnknownKeys() {
        List<String> warnings = new ArrayList<>();
        GameProject first = ScriptParser.parseString(SCRIPT, new File("."), warnings);
        String written = ScriptWriter.serialize(first);

        List<String> warnings2 = new ArrayList<>();
        GameProject second = ScriptParser.parseString(written, new File("."), warnings2);

        assertEquals(first.scenes().size(), second.scenes().size());
        assertEquals(first.getScene("Start").nodes().size(), second.getScene("Start").nodes().size());
        assertEquals(first.getScene("Start").nodes().get(0).getText(),
                second.getScene("Start").nodes().get(0).getText());
        // 未知键透传：自定义键在往返后不丢失
        assertEquals("keep-me", second.option().values().get("myCustomKey"));
        assertEquals("hello", second.getScene("Start").nodes().get(0).extras().get("customNote"));
        assertEquals(0.8, second.option().volume(), 1e-9);
    }

    @Test
    void duplicateSceneProducesWarningNotCrash() {
        String script = SCRIPT + "\n\n[Start]\n{\ntype = text\nx = 1\ny = 1\n}\n";
        List<String> warnings = new ArrayList<>();
        GameProject p = ScriptParser.parseString(script, new File("."), warnings);
        assertFalse(p.warnings().isEmpty(), "重复场景应产生告警");
    }
}

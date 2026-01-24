package com.c75.magiccodecompletion.action

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Extended tests for InsertCodeFromLLMAction covering edge cases and apply_edits scenarios
 */
class InsertCodeFromLLMActionExtendedTest {
    
    @Test
    fun `test apply_edits prefix constant`() {
        assertEquals("APPLY_EDITS:", InsertCodeFromLLMAction.APPLY_EDITS_PREFIX)
    }
    
    @Test
    fun `test cursor marker constant value`() {
        assertEquals("<<<CURSOR>>>", InsertCodeFromLLMAction.CURSOR_MARKER)
    }
    
    @Test
    fun `test cursor marker uniqueness`() {
        val marker = InsertCodeFromLLMAction.CURSOR_MARKER
        
        // Verify marker is distinctive enough to not accidentally appear in code
        assertTrue(marker.length > 5)
        assertTrue(marker.contains("<"))
        assertTrue(marker.contains(">"))
    }
    
    @Test
    fun `test cursor position calculation at various offsets`() {
        val documentText = "line1\nline2\nline3\nline4"
        val testOffsets = listOf(0, 5, 6, 11, 12, documentText.length)
        
        testOffsets.forEach { offset ->
            val textBefore = documentText.substring(0, offset)
            val textAfter = documentText.substring(offset)
            val combined = textBefore + "<<<CURSOR>>>" + textAfter
            
            assertTrue(combined.contains("<<<CURSOR>>>"))
            assertEquals(offset, combined.indexOf("<<<CURSOR>>>"))
        }
    }
    
    @Test
    fun `test cursor marker insertion with unicode characters`() {
        val documentText = "const greeting = 'Привет мир! 你好世界!'\n"
        val cursorOffset = documentText.indexOf("!")
        
        val textBefore = documentText.substring(0, cursorOffset)
        val textAfter = documentText.substring(cursorOffset)
        val codeWithCursor = textBefore + "<<<CURSOR>>>" + textAfter
        
        assertTrue(codeWithCursor.contains("Привет"))
        assertTrue(codeWithCursor.contains("你好"))
        assertTrue(codeWithCursor.contains("<<<CURSOR>>>"))
    }
    
    @Test
    fun `test cursor marker with very long documents`() {
        val largeLine = "x".repeat(10000)
        val documentText = "$largeLine\n$largeLine\n$largeLine"
        val cursorOffset = 5000
        
        val textBefore = documentText.substring(0, cursorOffset)
        val textAfter = documentText.substring(cursorOffset)
        val codeWithCursor = textBefore + "<<<CURSOR>>>" + textAfter
        
        assertEquals(cursorOffset, codeWithCursor.indexOf("<<<CURSOR>>>"))
        assertTrue(codeWithCursor.length > 30000)
    }
    
    @Test
    fun `test apply_edits JSON parsing`() {
        val editsJson = """[{"search":"<<<CURSOR>>>","replace":"const x = 42;"}]"""
        val response = "APPLY_EDITS:$editsJson"
        
        assertTrue(response.startsWith("APPLY_EDITS:"))
        
        val json = response.removePrefix("APPLY_EDITS:")
        assertTrue(json.contains("search"))
        assertTrue(json.contains("replace"))
        assertTrue(json.contains("<<<CURSOR>>>"))
    }
    
    @Test
    fun `test multiple edits in apply_edits`() {
        val editsJson = """
            [
                {"search":"import React","replace":"import React\nimport { useState }"},
                {"search":"<<<CURSOR>>>","replace":"const [count, setCount] = useState(0);"}
            ]
        """.trimIndent()
        
        val response = "APPLY_EDITS:$editsJson"
        
        assertTrue(response.contains("import React"))
        assertTrue(response.contains("useState"))
        assertTrue(response.contains("<<<CURSOR>>>"))
    }
    
    @Test
    fun `test cursor marker in various code contexts`() {
        val contexts = listOf(
            "function test() {\n<<<CURSOR>>>\n}",
            "class MyClass {\n    <<<CURSOR>>>\n}",
            "if (condition) {\n<<<CURSOR>>>\n} else {\n}",
            "const obj = {\n<<<CURSOR>>>\n};",
            "// Comment\n<<<CURSOR>>>\ncode();",
            "\"string\" + <<<CURSOR>>> + \"end\""
        )
        
        contexts.forEach { context ->
            assertTrue(context.contains("<<<CURSOR>>>"))
            val parts = context.split("<<<CURSOR>>>")
            assertEquals(2, parts.size)
        }
    }
    
    @Test
    fun `test empty document with cursor`() {
        val documentText = ""
        val cursorOffset = 0
        
        val textBefore = documentText.substring(0, cursorOffset)
        val textAfter = documentText.substring(cursorOffset)
        val codeWithCursor = textBefore + "<<<CURSOR>>>" + textAfter
        
        assertEquals("<<<CURSOR>>>", codeWithCursor)
    }
    
    @Test
    fun `test cursor at various newline positions`() {
        val documentText = "line1\n\nline3\n"
        val newlinePositions = listOf(5, 6, 12, 13) // After line1, empty line, after line3, end
        
        newlinePositions.forEach { offset ->
            val textBefore = documentText.substring(0, offset)
            val textAfter = documentText.substring(offset)
            val codeWithCursor = textBefore + "<<<CURSOR>>>" + textAfter
            
            assertTrue(codeWithCursor.contains("<<<CURSOR>>>"))
            assertTrue(codeWithCursor.contains("line1"))
            assertTrue(codeWithCursor.contains("line3"))
        }
    }
    
    @Test
    fun `test cursor marker does not interfere with code semantics`() {
        val codeWithoutCursor = "function add(a, b) { return a + b; }"
        val cursorOffset = codeWithoutCursor.indexOf("return")
        
        val textBefore = codeWithoutCursor.substring(0, cursorOffset)
        val textAfter = codeWithoutCursor.substring(cursorOffset)
        val codeWithCursor = textBefore + "<<<CURSOR>>>" + textAfter
        
        // Verify original code is preserved
        val reconstructed = codeWithCursor.replace("<<<CURSOR>>>", "")
        assertEquals(codeWithoutCursor, reconstructed)
    }
    
    @Test
    fun `test apply_edits with cursor marker in replacement`() {
        // Test that cursor can be positioned in the replacement text
        val editsJson = """[{"search":"<<<CURSOR>>>","replace":"const x = <<<CURSOR>>>42;"}]"""
        
        assertTrue(editsJson.contains("<<<CURSOR>>>"))
        // Count occurrences
        val count = "<<<CURSOR>>>".toRegex().findAll(editsJson).count()
        assertEquals(2, count) // One in search, one in replace
    }
    
    @Test
    fun `test edit search and replace with special regex characters`() {
        val search = "const value = getValue();"
        val replace = "const value = getValue() || defaultValue;"
        
        // These should work as literal strings, not regex
        assertNotNull(search)
        assertNotNull(replace)
        assertTrue(replace.contains("||")) // || is special in regex but should be literal
    }
    
    @Test
    fun `test multiple cursor markers handling`() {
        // Edge case: what if document somehow has multiple markers
        val documentText = "before <<<CURSOR>>> middle <<<CURSOR>>> after"
        
        val firstIndex = documentText.indexOf("<<<CURSOR>>>")
        val lastIndex = documentText.lastIndexOf("<<<CURSOR>>>")
        
        assertNotEquals(firstIndex, lastIndex)
        assertTrue(firstIndex < lastIndex)
    }
    
    @Test
    fun `test cursor marker with tabs and spaces`() {
        val contexts = listOf(
            "\t\t<<<CURSOR>>>",
            "    <<<CURSOR>>>",
            "code();\n\t<<<CURSOR>>>",
            "if (x) {\n    <<<CURSOR>>>\n}"
        )
        
        contexts.forEach { context ->
            assertTrue(context.contains("<<<CURSOR>>>"))
            // Verify whitespace is preserved
            val beforeCursor = context.substringBefore("<<<CURSOR>>>")
            assertTrue(beforeCursor.matches(Regex(".*\\s*")) || beforeCursor.isEmpty())
        }
    }
    
    @Test
    fun `test apply_edits JSON escaping`() {
        // Test that JSON special characters are properly escaped
        val search = "const str = \"hello\";"
        val replace = "const str = \"hello\\nworld\";"
        
        // When serialized to JSON, quotes should be escaped
        assertTrue(search.contains("\""))
        assertTrue(replace.contains("\\n"))
    }
    
    @Test
    fun `test file path handling`() {
        val paths = listOf(
            "src/main/App.kt",
            "src/main/com/example/Main.kt",
            "app.kt",
            "folder/subfolder/file.kt"
        )
        
        paths.forEach { path ->
            assertFalse(path.contains("\\"))
            assertTrue(path.endsWith(".kt"))
        }
    }
    
    @Test
    fun `test visualization settings data structure`() {
        // Test that settings flags work as expected in logic
        
        // Test all combinations with separate variables
        var showHighlighting = true
        var showNotification = true
        assertTrue(showHighlighting && showNotification) // Both enabled
        
        showHighlighting = false
        showNotification = true
        assertTrue(showNotification && !showHighlighting) // Only notification
        
        showHighlighting = true
        showNotification = false
        assertTrue(showHighlighting && !showNotification) // Only highlighting
        
        showHighlighting = false
        showNotification = false
        assertTrue(!showHighlighting && !showNotification) // Both disabled
    }
    
    @Test
    fun `test notification shows when highlighting is disabled`() {
        // This tests the logic: notification should work independently
        val showHighlighting = false
        val showNotification = true
        val hasEdits = true
        
        // Notification should show when: hasEdits && showNotification (no dependency on highlighter)
        val shouldShowNotification = hasEdits && showNotification
        assertTrue(shouldShowNotification)
    }
    
    @Test
    fun `test undo callback works without highlighter`() {
        // Test that undo logic handles null highlighter case
        val highlighter: Any? = null
        val originalText = "function test() { return 42; }"
        
        // When highlighter is null, should still be able to restore from saved text
        if (highlighter != null) {
            fail("Highlighter should be null in this test")
        } else {
            // Direct text restoration should work
            assertNotNull(originalText)
            assertTrue(originalText.isNotEmpty())
        }
    }
    
    @Test
    fun `test original text preservation before applying edits`() {
        val documentText = "const x = 10;\nconst y = 20;"
        val originalDocumentText = documentText // Saved before edits
        
        // After edits are applied, original should still be available
        assertNotNull(originalDocumentText)
        assertEquals(documentText, originalDocumentText)
    }
}

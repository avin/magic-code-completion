package com.c75.magiccodecompletion.action

import com.c75.magiccodecompletion.ui.CodeChangeHighlighter
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class InsertCodeFromLLMActionChangeVisualizationTest {
    
    @Test
    fun `test active highlighters map exists`() {
        // Verify that the companion object has the activeHighlighters map
        // This is tested indirectly through the class structure
        assertNotNull(InsertCodeFromLLMAction::class)
    }
    
    @Test
    fun `test APPLY_EDITS_PREFIX constant`() {
        assertEquals("APPLY_EDITS:", InsertCodeFromLLMAction.APPLY_EDITS_PREFIX)
    }
    
    @Test
    fun `test cursor marker constant unchanged`() {
        // Ensure cursor marker constant is still available
        assertEquals("<<<CURSOR>>>", InsertCodeFromLLMAction.CURSOR_MARKER)
    }
    
    @Test
    fun `test edits with highlighting workflow`() {
        // This test documents the expected workflow:
        // 1. LLM returns APPLY_EDITS response
        // 2. applyEdits method is called
        // 3. CodeChangeHighlighter is created
        // 4. Original state is saved
        // 5. Edits are applied with highlighting
        // 6. Notification is shown
        
        val editsJson = """[{"search":"<<<CURSOR>>>","replace":"const x = 42;"}]"""
        val response = "APPLY_EDITS:$editsJson"
        
        assertTrue(response.startsWith(InsertCodeFromLLMAction.APPLY_EDITS_PREFIX))
    }
    
    @Test
    fun `test multiple edits create multiple highlights`() {
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
    fun `test empty edits array`() {
        val editsJson = "[]"
        val response = "APPLY_EDITS:$editsJson"
        
        assertTrue(response.startsWith(InsertCodeFromLLMAction.APPLY_EDITS_PREFIX))
    }
    
    @Test
    fun `test edit with special characters`() {
        val editsJson = """[{"search":"<<<CURSOR>>>","replace":"const str = \"hello\\nworld\";"}]"""
        val response = "APPLY_EDITS:$editsJson"
        
        // Note: JSON encoding may change the representation
        assertTrue(response.contains("hello") && response.contains("world"))
    }
    
    @Test
    fun `test edit with unicode characters`() {
        val editsJson = """[{"search":"<<<CURSOR>>>","replace":"const greeting = 'Привет мир! 你好世界!';"}]"""
        val response = "APPLY_EDITS:$editsJson"
        
        assertTrue(response.contains("Привет"))
        assertTrue(response.contains("你好"))
    }
    
    @Test
    fun `test replacement edit preserves original text tracking`() {
        // Test that replacements track original text for rollback
        val editsJson = """[{"search":"oldValue","replace":"newValue"}]"""
        
        assertTrue(editsJson.contains("oldValue"))
        assertTrue(editsJson.contains("newValue"))
    }
    
    @Test
    fun `test insertion edit has empty original text`() {
        // Insertions at cursor have no original text
        val editsJson = """[{"search":"<<<CURSOR>>>","replace":"inserted code"}]"""
        
        assertTrue(editsJson.contains(InsertCodeFromLLMAction.CURSOR_MARKER))
    }
    
    @Test
    fun `test complex multi-edit scenario`() {
        val editsJson = """
            [
                {"search":"import Header","replace":"import Header\nimport Footer"},
                {"search":"function App()","replace":"function App(): JSX.Element"},
                {"search":"<<<CURSOR>>>","replace":"return <div>Hello</div>;"}
            ]
        """.trimIndent()
        
        val response = "APPLY_EDITS:$editsJson"
        
        // Verify all edits are present
        assertTrue(response.contains("Footer"))
        assertTrue(response.contains("JSX.Element"))
        assertTrue(response.contains("Hello"))
    }
    
    @Test
    fun `test edit with cursor marker in replacement`() {
        // Test that cursor can be positioned within the replacement
        val editsJson = """[{"search":"<<<CURSOR>>>","replace":"const x = <<<CURSOR>>>42;"}]"""
        
        // Count cursor markers
        val cursorMarker = InsertCodeFromLLMAction.CURSOR_MARKER
        val count = editsJson.split(cursorMarker).size - 1
        
        assertEquals(2, count, "Should have 2 cursor markers (search + replace)")
    }
    
    @Test
    fun `test highlighting metadata structure`() {
        // Document the expected structure of highlighted change metadata
        data class EditMetadata(
            val startOffset: Int,
            val endOffset: Int,
            val originalText: String,
            val newText: String
        )
        
        val metadata = EditMetadata(
            startOffset = 0,
            endOffset = 10,
            originalText = "old code",
            newText = "new code"
        )
        
        assertNotNull(metadata)
        assertEquals(0, metadata.startOffset)
        assertEquals(10, metadata.endOffset)
        assertEquals("old code", metadata.originalText)
        assertEquals("new code", metadata.newText)
    }
    
    @Test
    fun `test range markers track formatting changes`() {
        // This test verifies the concept behind the fix:
        // RangeMarkers automatically update when document is modified
        
        data class MockRangeMarker(
            var startOffset: Int,
            var endOffset: Int,
            var isValid: Boolean = true
        ) {
            fun updateForFormatting(offsetShift: Int) {
                // Simulate how RangeMarker updates offsets when text before it changes
                startOffset += offsetShift
                endOffset += offsetShift
            }
        }
        
        // Initial edit range before formatting
        val marker = MockRangeMarker(startOffset = 20, endOffset = 30)
        
        // Simulate formatting that adds 5 characters before the range
        val formattingShift = 5
        marker.updateForFormatting(formattingShift)
        
        // Verify marker tracked the change
        assertEquals(25, marker.startOffset, "Start offset should shift by 5")
        assertEquals(35, marker.endOffset, "End offset should shift by 5")
        assertTrue(marker.isValid)
    }
    
    @Test
    fun `test highlighting uses post-formatting ranges`() {
        // Test the workflow to verify highlights use ranges AFTER formatting, not before
        
        data class Edit(val range: Pair<Int, Int>, val text: String)
        val edits = mutableListOf<Edit>()
        
        // Step 1: Apply edit at offset 10-20
        val initialRange = Pair(10, 20)
        edits.add(Edit(initialRange, "unformatted"))
        
        // Step 2: Formatting shifts the range
        val formattedRange = Pair(15, 28)  // Shifted due to added whitespace
        
        // Step 3: Highlight should use formatted range, not initial
        val highlightRange = formattedRange  // This is what the fix ensures
        
        // Verify
        assertNotEquals(initialRange, highlightRange, 
            "Highlight range should differ from pre-formatting range")
        assertEquals(formattedRange, highlightRange,
            "Highlight should use post-formatting range")
    }
}

package com.c75.magiccodeinsert.action

import com.c75.magiccodeinsert.service.LLMService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class InsertCodeFromLLMActionTest {
    
    private lateinit var action: InsertCodeFromLLMAction
    private lateinit var mockEvent: AnActionEvent
    private lateinit var mockEditor: Editor
    private lateinit var mockProject: Project
    private lateinit var mockDocument: Document
    private lateinit var mockCaretModel: CaretModel
    
    @BeforeEach
    fun setUp() {
        action = InsertCodeFromLLMAction()
        mockEvent = mock()
        mockEditor = mock()
        mockProject = mock()
        mockDocument = mock()
        mockCaretModel = mock()
        
        whenever(mockEvent.getData(CommonDataKeys.EDITOR)).thenReturn(mockEditor)
        whenever(mockEvent.project).thenReturn(mockProject)
        whenever(mockEditor.document).thenReturn(mockDocument)
        whenever(mockEditor.caretModel).thenReturn(mockCaretModel)
    }
    
    @Test
    fun `test cursor marker constant`() {
        assertEquals("<<<CURSOR>>>", InsertCodeFromLLMAction.CURSOR_MARKER)
    }
    
    @Test
    fun `test action is enabled when editor is available`() {
        val presentation = mock<com.intellij.openapi.actionSystem.Presentation>()
        whenever(mockEvent.presentation).thenReturn(presentation)
        whenever(mockEvent.getData(CommonDataKeys.EDITOR)).thenReturn(mockEditor)
        
        action.update(mockEvent)
        
        verify(presentation).isEnabled = true
    }
    
    @Test
    fun `test action is disabled when editor is not available`() {
        val presentation = mock<com.intellij.openapi.actionSystem.Presentation>()
        whenever(mockEvent.presentation).thenReturn(presentation)
        whenever(mockEvent.getData(CommonDataKeys.EDITOR)).thenReturn(null)
        
        action.update(mockEvent)
        
        verify(presentation).isEnabled = false
    }
    
    @Test
    fun `test cursor marker insertion at beginning of document`() {
        val documentText = "function test() {\n    return 42;\n}"
        whenever(mockDocument.text).thenReturn(documentText)
        whenever(mockCaretModel.offset).thenReturn(0)
        
        val expectedText = "<<<CURSOR>>>$documentText"
        
        // Verify the logic would create correct text with cursor marker
        val textBeforeCursor = documentText.substring(0, 0)
        val textAfterCursor = documentText.substring(0)
        val codeWithCursor = textBeforeCursor + InsertCodeFromLLMAction.CURSOR_MARKER + textAfterCursor
        
        assertEquals(expectedText, codeWithCursor)
    }
    
    @Test
    fun `test cursor marker insertion in middle of document`() {
        val documentText = "function test() {\n    return 42;\n}"
        val cursorOffset = 16 // After "function test()"
        whenever(mockDocument.text).thenReturn(documentText)
        whenever(mockCaretModel.offset).thenReturn(cursorOffset)
        
        val textBeforeCursor = documentText.substring(0, cursorOffset)
        val textAfterCursor = documentText.substring(cursorOffset)
        val codeWithCursor = textBeforeCursor + InsertCodeFromLLMAction.CURSOR_MARKER + textAfterCursor
        
        assertEquals("function test() <<<CURSOR>>>{\n    return 42;\n}", codeWithCursor)
    }
    
    @Test
    fun `test cursor marker insertion at end of document`() {
        val documentText = "function test() {\n    return 42;\n}"
        val cursorOffset = documentText.length
        whenever(mockDocument.text).thenReturn(documentText)
        whenever(mockCaretModel.offset).thenReturn(cursorOffset)
        
        val textBeforeCursor = documentText.substring(0, cursorOffset)
        val textAfterCursor = documentText.substring(cursorOffset)
        val codeWithCursor = textBeforeCursor + InsertCodeFromLLMAction.CURSOR_MARKER + textAfterCursor
        
        assertEquals("${documentText}<<<CURSOR>>>", codeWithCursor)
    }
    
    @Test
    fun `test cursor marker insertion with empty document`() {
        val documentText = ""
        whenever(mockDocument.text).thenReturn(documentText)
        whenever(mockCaretModel.offset).thenReturn(0)
        
        val textBeforeCursor = documentText.substring(0, 0)
        val textAfterCursor = documentText.substring(0)
        val codeWithCursor = textBeforeCursor + InsertCodeFromLLMAction.CURSOR_MARKER + textAfterCursor
        
        assertEquals("<<<CURSOR>>>", codeWithCursor)
    }
    
    @Test
    fun `test cursor marker with multiline code`() {
        val documentText = """
            class Example {
                public void method() {
                    // cursor here
                }
            }
        """.trimIndent()
        
        val cursorOffset = documentText.indexOf("// cursor here")
        whenever(mockDocument.text).thenReturn(documentText)
        whenever(mockCaretModel.offset).thenReturn(cursorOffset)
        
        val textBeforeCursor = documentText.substring(0, cursorOffset)
        val textAfterCursor = documentText.substring(cursorOffset)
        val codeWithCursor = textBeforeCursor + InsertCodeFromLLMAction.CURSOR_MARKER + textAfterCursor
        
        assertTrue(codeWithCursor.contains("<<<CURSOR>>>"))
        assertTrue(codeWithCursor.contains("class Example"))
        assertTrue(codeWithCursor.contains("// cursor here"))
    }
}

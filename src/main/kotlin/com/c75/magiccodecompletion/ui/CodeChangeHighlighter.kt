package com.c75.magiccodecompletion.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.TimeUnit

/**
 * Stores information about a code change for potential rollback
 */
data class CodeEdit(
    val range: TextRange,
    val originalText: String,
    val newText: String
)

/**
 * Manages highlighting and rollback of LLM-generated code changes
 */
class CodeChangeHighlighter(
    private val project: Project,
    private val editor: Editor
) {
    private val highlighters = mutableListOf<RangeHighlighter>()
    private val edits = mutableListOf<CodeEdit>()
    private var originalDocumentText: String = ""
    private var isInternalEdit = false // Flag to prevent recursive listener calls
    private var autoAcceptEnabled = false // Flag to control auto-accept behavior
    private var isDocumentListenerRegistered = false
    
    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            // Ignore changes made by our own reject/rejectAll operations
            if (isInternalEdit) return
            
            // Don't auto-accept if it's disabled (e.g., during initial formatting)
            if (!autoAcceptEnabled) return
            
            // Check if the change overlaps with any highlighted range
            val changedRange = TextRange(event.offset, event.offset + event.newLength)
            val highlightersToAccept = highlighters.filter { highlighter ->
                val highlightRange = TextRange(highlighter.startOffset, highlighter.endOffset)
                highlightRange.intersects(changedRange) || changedRange.contains(highlightRange)
            }.toList()
            
            // Auto-accept all overlapping changes
            highlightersToAccept.forEach { acceptChange(it) }
        }
    }
    
    /**
     * Store the original document state before changes
     */
    fun saveOriginalState(documentText: String) {
        originalDocumentText = documentText
        autoAcceptEnabled = false // Start with auto-accept disabled
        // Register document listener when we start tracking changes
        registerDocumentListenerIfNeeded()
    }
    
    /**
     * Enable auto-accept after a delay to allow formatting to complete
     * This gives external formatters (prettier, black, etc.) time to finish
     */
    fun enableAutoAcceptAfterDelay(delayMs: Long = 500) {
        AppExecutorUtil.getAppScheduledExecutorService().schedule(
            {
                ApplicationManager.getApplication().invokeLater {
                    autoAcceptEnabled = true
                }
            },
            delayMs,
            TimeUnit.MILLISECONDS
        )
    }
    
    /**
     * Add a code edit and highlight it
     */
    fun addEdit(range: TextRange, originalText: String, newText: String) {
        edits.add(CodeEdit(range, originalText, newText))
        highlightRange(range)
    }
    
    /**
     * Highlight a range with green background to indicate LLM changes
     */
    private fun highlightRange(range: TextRange) {
        val markupModel = editor.markupModel
        
        // Create text attributes for highlighting (light green background)
        val attributes = TextAttributes().apply {
            backgroundColor = JBColor(0xE6FFE6, 0x2D4A2D) // Light green for light theme, dark green for dark theme
        }
        
        val highlighter = markupModel.addRangeHighlighter(
            range.startOffset,
            range.endOffset,
            HighlighterLayer.SELECTION - 1, // Below selection layer
            attributes,
            HighlighterTargetArea.EXACT_RANGE
        )
        
        // Add gutter icon renderer for Accept/Reject actions
        highlighter.gutterIconRenderer = CodeChangeGutterRenderer(
            onAccept = { acceptChange(highlighter) },
            onReject = { rejectChange(highlighter) }
        )
        
        highlighters.add(highlighter)
    }
    
    /**
     * Accept a specific change - remove highlight but keep the code
     */
    private fun acceptChange(highlighter: RangeHighlighter) {
        editor.markupModel.removeHighlighter(highlighter)
        highlighters.remove(highlighter)
        
        // If all changes accepted, clear the saved state
        if (highlighters.isEmpty()) {
            clearAll()
        }
    }
    
    /**
     * Reject a specific change - revert this particular edit
     */
    private fun rejectChange(highlighter: RangeHighlighter) {
        // Find the edit associated with this highlighter
        val startOffset = highlighter.startOffset
        val endOffset = highlighter.endOffset
        
        val edit = edits.find { 
            it.range.startOffset == startOffset && it.range.endOffset == endOffset 
        }
        
        if (edit != null) {
            // Revert this specific change - must be in write action
            isInternalEdit = true
            try {
                WriteCommandAction.runWriteCommandAction(project) {
                    val document = editor.document
                    document.replaceString(startOffset, endOffset, edit.originalText)
                }
            } finally {
                isInternalEdit = false
            }
            
            // Remove highlighter
            editor.markupModel.removeHighlighter(highlighter)
            highlighters.remove(highlighter)
            edits.remove(edit)
            
            // If all changes rejected, clear the saved state
            if (highlighters.isEmpty()) {
                clearAll()
            }
        }
    }
    
    /**
     * Accept all changes at once
     */
    fun acceptAll() {
        clearAll()
    }
    
    /**
     * Reject all changes - restore original document
     */
    fun rejectAll() {
        val savedOriginalText = originalDocumentText
        if (savedOriginalText.isNotEmpty()) {
            isInternalEdit = true
            try {
                WriteCommandAction.runWriteCommandAction(project) {
                    val document = editor.document
                    document.setText(savedOriginalText)
                }
            } finally {
                isInternalEdit = false
            }
        }
        clearAll()
    }
    
    /**
     * Clear all highlighting and stored data
     */
    fun clearAll() {
        highlighters.forEach { editor.markupModel.removeHighlighter(it) }
        highlighters.clear()
        edits.clear()
        originalDocumentText = ""
        // Remove document listener when all changes are cleared
        unregisterDocumentListenerIfNeeded()
    }
    
    /**
     * Check if there are any pending changes
     */
    fun hasPendingChanges(): Boolean = highlighters.isNotEmpty()

    private fun registerDocumentListenerIfNeeded() {
        if (isDocumentListenerRegistered) return
        editor.document.addDocumentListener(documentListener, project)
        isDocumentListenerRegistered = true
    }

    private fun unregisterDocumentListenerIfNeeded() {
        if (!isDocumentListenerRegistered) return
        editor.document.removeDocumentListener(documentListener)
        isDocumentListenerRegistered = false
    }
}

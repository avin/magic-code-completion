package com.c75.magiccodecompletion.action

import com.c75.magiccodecompletion.service.LLMService
import com.c75.magiccodecompletion.settings.MagicCodeCompletionSettings
import com.c75.magiccodecompletion.ui.CodeChangeHighlighter
import com.c75.magiccodecompletion.ui.CodeChangeNotification
import com.google.gson.Gson
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import java.awt.event.KeyEvent
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class InsertCodeFromLLMAction : AnAction() {
    
    companion object {
        const val CURSOR_MARKER = "<<<CURSOR>>>"
        const val APPLY_EDITS_PREFIX = "APPLY_EDITS:"
        
        private val SPINNER_FRAMES = arrayOf(
            "[    ]",
            "[=   ]",
            "[==  ]",
            "[=== ]",
            "[====]",
            "[ ===]",
            "[  ==]",
            "[   =]"
        )
        private val scheduler = Executors.newScheduledThreadPool(1)
        private val isGenerating = AtomicBoolean(false)
    }
    
    private data class LlmEdit(
        val search: String? = null,
        val replace: String? = null
    )
    
    private val gson = Gson()
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
    
    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabled = editor != null && !isGenerating.get()
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        if (!isGenerating.compareAndSet(false, true)) {
            return
        }
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        
        val document = editor.document
        val caretModel = editor.caretModel
        val caretOffset = caretModel.offset
        
        // Get relative file path
        val currentFilePath = if (virtualFile != null && project.basePath != null) {
            virtualFile.path.removePrefix(project.basePath!!).removePrefix("/").removePrefix("\\")
        } else {
            null
        }
        
        // Get document text and insert cursor marker
        val textBeforeCursor = document.text.substring(0, caretOffset)
        val textAfterCursor = document.text.substring(caretOffset)
        val codeWithCursor = textBeforeCursor + CURSOR_MARKER + textAfterCursor
        
        // Start spinner animation at cursor position
        var inlayHint: Inlay<*>? = null
        val frameIndex = AtomicInteger(0)
        val statusText = AtomicReference("Generating code...")
        var animationTask: ScheduledFuture<*>? = null
        val cancelled = AtomicBoolean(false)
        val indicatorRef = AtomicReference<ProgressIndicator?>(null)
        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val updateStatus: (String) -> Unit = { newText ->
            statusText.set(newText)
            ApplicationManager.getApplication().invokeLater {
                inlayHint?.update()
            }
        }
        
        val escDispatcher = KeyEventDispatcher { event ->
            if (event.id == KeyEvent.KEY_PRESSED && event.keyCode == KeyEvent.VK_ESCAPE) {
                cancelled.set(true)
                indicatorRef.get()?.cancel()
                false
            } else {
                false
            }
        }

        focusManager.addKeyEventDispatcher(escDispatcher)

        ApplicationManager.getApplication().invokeLater {
            inlayHint = editor.inlayModel.addInlineElement(
                caretOffset,
                true,
                object : com.intellij.openapi.editor.EditorCustomElementRenderer {
                    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
                        val metrics = inlay.editor.contentComponent.getFontMetrics(inlay.editor.colorsScheme.getFont(com.intellij.openapi.editor.colors.EditorFontType.PLAIN))
                        return metrics.stringWidth(getCurrentSpinnerText())
                    }
                    
                    override fun paint(
                        inlay: Inlay<*>,
                        g: java.awt.Graphics,
                        targetRect: java.awt.Rectangle,
                        textAttributes: com.intellij.openapi.editor.markup.TextAttributes
                    ) {
                        g.color = java.awt.Color(128, 128, 128) // Gray color
                        g.font = inlay.editor.colorsScheme.getFont(com.intellij.openapi.editor.colors.EditorFontType.PLAIN)
                        g.drawString(getCurrentSpinnerText(), targetRect.x, targetRect.y + inlay.editor.ascent)
                    }
                    
                    private fun getCurrentSpinnerText(): String {
                        val frame = SPINNER_FRAMES[frameIndex.get() % SPINNER_FRAMES.size]
                        return "$frame ${statusText.get()}"
                    }
                }
            )
            
            // Start animation timer
            animationTask = scheduler.scheduleAtFixedRate({
                frameIndex.incrementAndGet()
                ApplicationManager.getApplication().invokeLater {
                    inlayHint?.update()
                }
            }, 0, 100, TimeUnit.MILLISECONDS)
        }
        
        // Run LLM request in background task
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Getting Code Completion from LLM...",
            true
        ) {
            var generatedCode: String? = null
            var error: String? = null
            
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Sending request to LLM..."
                indicatorRef.set(indicator)
                
                try {
                    val llmService = LLMService.getInstance()
                    
                    // Check for cancellation from ESC key
                    if (cancelled.get()) {
                        indicator.cancel()
                        return
                    }
                    
                    // Pass progress indicator to enable HTTP request cancellation
                    // Create a custom indicator that checks our cancelled flag
                    val wrappedIndicator = object : ProgressIndicator by indicator {
                        override fun isCanceled(): Boolean {
                            return cancelled.get() || indicator.isCanceled
                        }
                    }
                    
                    generatedCode = llmService.getCodeCompletion(
                        codeWithCursor, 
                        project, 
                        currentFilePath,
                        null, // settingsState
                        wrappedIndicator, // Wrapped indicator that checks our cancelled flag
                        updateStatus
                    )
                    
                    // Check again after completion
                    if (cancelled.get()) {
                        indicator.cancel()
                    }
                } catch (e: LLMService.LLMException) {
                    if (cancelled.get() || indicator.isCanceled) {
                        // Silently ignore cancellation errors
                        return
                    }
                    error = e.message
                } catch (e: Exception) {
                    if (cancelled.get() || indicator.isCanceled) {
                        // Silently ignore cancellation errors
                        return
                    }
                    error = "Unexpected error: ${e.message}"
                }
            }
            
            override fun onSuccess() {
                cleanupGeneration(animationTask, focusManager, escDispatcher, inlayHint)
                
                isGenerating.set(false)
                
                // Check if cancelled
                if (cancelled.get()) {
                    return
                }
                
                if (error != null) {
                    Messages.showErrorDialog(project, error, "LLM Error")
                    return
                }
                
                val code = generatedCode ?: return
                
                // Check if this is apply_edits response
                try {
                    if (code.startsWith(APPLY_EDITS_PREFIX)) {
                        val editsJson = code.removePrefix(APPLY_EDITS_PREFIX)
                        applyEdits(project, editor, document, editsJson, caretOffset)
                    } else {
                        // Simple insertion at cursor (legacy behavior)
                        ApplicationManager.getApplication().invokeLater {
                            WriteCommandAction.runWriteCommandAction(project) {
                                document.insertString(caretOffset, code)
                                // Move cursor to end of inserted text
                                caretModel.moveToOffset(caretOffset + code.length)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Handle any errors during code application
                    Messages.showErrorDialog(
                        project,
                        "Failed to apply code changes: ${e.message}",
                        "Code Application Error"
                    )
                }
            }
            
            override fun onThrowable(throwable: Throwable) {
                cleanupGeneration(animationTask, focusManager, escDispatcher, inlayHint)
                
                isGenerating.set(false)
                
                Messages.showErrorDialog(
                    project,
                    "Failed to get code completion: ${throwable.message}",
                    "Error"
                )
            }
            
            override fun onCancel() {
                cleanupGeneration(animationTask, focusManager, escDispatcher, inlayHint)
                
                isGenerating.set(false)
            }
        })
    }
    
    private fun applyEdits(
        project: Project,
        editor: Editor,
        document: com.intellij.openapi.editor.Document,
        editsJson: String,
        caretOffset: Int
    ) {
        try {
            val settings = MagicCodeCompletionSettings.getInstance().state
            val editsData = gson.fromJson(editsJson, Array<LlmEdit>::class.java)?.toList().orEmpty()
            
            // Save original document state before changes
            val originalDocumentText = document.text
            
            // Create highlighter if visualization is enabled
            val highlighter = if (settings.showChangeHighlighting) {
                CodeChangeHighlighter(project, editor).apply {
                    saveOriginalState(originalDocumentText)
                }
            } else {
                null
            }
            
            ApplicationManager.getApplication().invokeLater {
                data class EditInfo(val range: TextRange, val originalText: String, val newText: String)
                val editedRanges = mutableListOf<EditInfo>()
                var finalCursorPosition = caretOffset
                
                WriteCommandAction.runWriteCommandAction(project) {
                    var currentText = document.text
                    var cursorPosition = caretOffset
                    
                    // Apply edits sequentially
                    for (edit in editsData) {
                        val search = edit.search ?: continue
                        val rawReplace = edit.replace ?: ""
                        
                        // Check if replace contains cursor marker - remember position and remove it
                        val cursorInReplace = rawReplace.indexOf(CURSOR_MARKER)
                        val replace = rawReplace.replace(CURSOR_MARKER, "")
                        
                        if (search == CURSOR_MARKER) {
                            // Insert at cursor position
                            document.insertString(cursorPosition, replace)
                            editedRanges.add(EditInfo(
                                TextRange(cursorPosition, cursorPosition + replace.length),
                                "",
                                replace
                            ))
                            
                            // If replace had cursor marker, position cursor there, otherwise at end of insert
                            cursorPosition = if (cursorInReplace != -1) {
                                cursorPosition + cursorInReplace
                            } else {
                                cursorPosition + replace.length
                            }
                        } else {
                            // Remove cursor marker from search string since document doesn't have it
                            val searchWithoutMarker = search.replace(CURSOR_MARKER, "")
                            
                            // Find and replace in current text
                            val searchIndex = currentText.indexOf(searchWithoutMarker)
                            if (searchIndex != -1) {
                                // Save original text before replacing
                                val originalText = searchWithoutMarker
                                document.replaceString(searchIndex, searchIndex + searchWithoutMarker.length, replace)
                                editedRanges.add(EditInfo(
                                    TextRange(searchIndex, searchIndex + replace.length),
                                    originalText,
                                    replace
                                ))
                                
                                // Update cursor position
                                if (searchIndex < cursorPosition) {
                                    // Replacement happened before cursor - adjust for size difference
                                    cursorPosition += (replace.length - searchWithoutMarker.length)
                                }
                                
                                // If replace had cursor marker, position cursor at that offset
                                if (cursorInReplace != -1) {
                                    cursorPosition = searchIndex + cursorInReplace
                                }
                                
                                // Update current text for next search
                                currentText = document.text
                            }
                        }
                    }
                    
                    // Move cursor to final position
                    finalCursorPosition = cursorPosition
                    editor.caretModel.moveToOffset(cursorPosition)
                }
                
                // Create range markers OUTSIDE WriteCommandAction to survive async formatting
                val rangeMarkers = editedRanges.map { editInfo ->
                    document.createRangeMarker(editInfo.range.startOffset, editInfo.range.endOffset).apply {
                        isGreedyToLeft = true
                        isGreedyToRight = true
                    } to editInfo
                }
                
                // Trigger formatting (may be async for external formatters)
                val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
                if (psiFile != null) {
                    WriteCommandAction.runWriteCommandAction(project) {
                        val codeStyleManager = CodeStyleManager.getInstance(project)
                        for ((marker, _) in rangeMarkers) {
                            try {
                                if (marker.isValid) {
                                    codeStyleManager.reformatText(psiFile, marker.startOffset, marker.endOffset)
                                }
                            } catch (e: Exception) {
                                // Ignore formatting errors
                            }
                        }
                    }
                }
                
                // Wait for all PSI/document commits to finish (including async formatters)
                // then add highlighting with final ranges
                val psiDocumentManager = PsiDocumentManager.getInstance(project)
                psiDocumentManager.performWhenAllCommitted {
                    ApplicationManager.getApplication().invokeLater {
                        highlighter?.let { h ->
                            for ((marker, editInfo) in rangeMarkers) {
                                if (marker.isValid) {
                                    // Use final range from marker after all formatting completed
                                    val updatedRange = TextRange(marker.startOffset, marker.endOffset)
                                    h.addEdit(updatedRange, editInfo.originalText, editInfo.newText)
                                }
                                // Dispose marker after use
                                marker.dispose()
                            }
                            
                            // Enable auto-accept after 500ms delay to allow external formatters (prettier, etc.) to finish
                            h.enableAutoAcceptAfterDelay(500)
                        }
                        
                        // Show notification AFTER highlighting is added
                        if (editsData.isNotEmpty() && settings.showChangeNotification) {
                            CodeChangeNotification.showChangeNotification(
                                project = project,
                                changeCount = editsData.size,
                                onAcceptAll = { 
                                    highlighter?.acceptAll()
                                },
                                onUndo = {
                                    // If highlighter exists, use it; otherwise restore original text directly
                                    if (highlighter != null) {
                                        highlighter.rejectAll()
                                    } else {
                                        WriteCommandAction.runWriteCommandAction(project) {
                                            document.setText(originalDocumentText)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to apply edits: ${e.message}",
                "Error"
            )
        }
    }
    
    private fun cleanupGeneration(
        animationTask: ScheduledFuture<*>?,
        focusManager: KeyboardFocusManager,
        escDispatcher: KeyEventDispatcher,
        inlayHint: Inlay<*>?
    ) {
        animationTask?.cancel(false)
        ApplicationManager.getApplication().invokeLater {
            focusManager.removeKeyEventDispatcher(escDispatcher)
            inlayHint?.let { Disposer.dispose(it) }
        }
    }
}

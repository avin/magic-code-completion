package com.c75.magiccodeinsert.action

import com.c75.magiccodeinsert.service.LLMService
import com.google.gson.Gson
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager

class InsertCodeFromLLMAction : AnAction() {
    
    companion object {
        const val CURSOR_MARKER = "<<<CURSOR>>>"
        const val APPLY_EDITS_PREFIX = "APPLY_EDITS:"
    }
    
    private val gson = Gson()
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
    
    override fun update(e: AnActionEvent) {
        // Enable action only when editor is available
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabled = editor != null
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
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
                
                try {
                    val llmService = LLMService.getInstance()
                    generatedCode = llmService.getCodeCompletion(codeWithCursor, project, currentFilePath)
                } catch (e: LLMService.LLMException) {
                    error = e.message
                } catch (e: Exception) {
                    error = "Unexpected error: ${e.message}"
                }
            }
            
            override fun onSuccess() {
                if (error != null) {
                    Messages.showErrorDialog(project, error, "LLM Error")
                    return
                }
                
                val code = generatedCode ?: return
                
                // Check if this is apply_edits response
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
            }
            
            override fun onThrowable(throwable: Throwable) {
                Messages.showErrorDialog(
                    project,
                    "Failed to get code completion: ${throwable.message}",
                    "Error"
                )
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
            val editsData = gson.fromJson(editsJson, List::class.java) as List<Map<String, String>>
            
            ApplicationManager.getApplication().invokeLater {
                WriteCommandAction.runWriteCommandAction(project) {
                    var currentText = document.text
                    var cursorPosition = caretOffset
                    val editedRanges = mutableListOf<TextRange>()
                    
                    // Apply edits sequentially
                    for (edit in editsData) {
                        val search = edit["search"] ?: continue
                        val replace = edit["replace"] ?: ""
                        
                        if (search == CURSOR_MARKER) {
                            // Insert at cursor position
                            document.insertString(cursorPosition, replace)
                            editedRanges.add(TextRange(cursorPosition, cursorPosition + replace.length))
                            cursorPosition += replace.length
                        } else {
                            // Remove cursor marker from search string since document doesn't have it
                            val searchWithoutMarker = search.replace(CURSOR_MARKER, "")
                            
                            // Find and replace in current text
                            val searchIndex = currentText.indexOf(searchWithoutMarker)
                            if (searchIndex != -1) {
                                document.replaceString(searchIndex, searchIndex + searchWithoutMarker.length, replace)
                                editedRanges.add(TextRange(searchIndex, searchIndex + replace.length))
                                
                                // Update cursor if replacement happened before cursor position
                                if (searchIndex < cursorPosition) {
                                    cursorPosition += (replace.length - searchWithoutMarker.length)
                                }
                                
                                // Update current text for next search
                                currentText = document.text
                            }
                        }
                    }
                    
                    // Auto-format edited ranges
                    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
                    if (psiFile != null) {
                        val codeStyleManager = CodeStyleManager.getInstance(project)
                        for (range in editedRanges) {
                            try {
                                codeStyleManager.reformatText(psiFile, range.startOffset, range.endOffset)
                            } catch (e: Exception) {
                                // Ignore formatting errors
                            }
                        }
                        
                        // Update cursor position after formatting (it might have shifted)
                        cursorPosition = editor.caretModel.offset
                    }
                    
                    // Move cursor to final position
                    editor.caretModel.moveToOffset(cursorPosition)
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
}

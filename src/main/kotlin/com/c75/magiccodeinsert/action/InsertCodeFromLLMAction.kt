package com.c75.magiccodeinsert.action

import com.c75.magiccodeinsert.service.LLMService
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

class InsertCodeFromLLMAction : AnAction() {
    
    companion object {
        const val CURSOR_MARKER = "<<<CURSOR>>>"
    }
    
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
        
        val document = editor.document
        val caretModel = editor.caretModel
        val caretOffset = caretModel.offset
        
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
                    generatedCode = llmService.getCodeCompletion(codeWithCursor)
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
                
                // Insert generated code at cursor position
                ApplicationManager.getApplication().invokeLater {
                    WriteCommandAction.runWriteCommandAction(project) {
                        document.insertString(caretOffset, code)
                        // Move cursor to end of inserted text
                        caretModel.moveToOffset(caretOffset + code.length)
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
}

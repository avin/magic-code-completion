package com.c75.magiccodecompletion.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.markup.GutterIconRenderer
import javax.swing.Icon

/**
 * Gutter icon renderer for LLM code changes - shows accept/reject actions
 */
class CodeChangeGutterRenderer(
    private val onAccept: () -> Unit,
    private val onReject: () -> Unit
) : GutterIconRenderer() {
    
    override fun getIcon(): Icon {
        // Show a lightning bolt icon to indicate AI-generated change
        return AllIcons.Actions.Lightning
    }
    
    override fun getTooltipText(): String {
        return "LLM Generated Change - Click to accept or reject"
    }
    
    override fun getClickAction(): AnAction? {
        return object : AnAction("Accept Change", "Accept this LLM-generated change", AllIcons.Actions.Checked) {
            override fun actionPerformed(e: AnActionEvent) {
                onAccept()
            }
        }
    }
    
    override fun getPopupMenuActions(): ActionGroup? {
        return object : ActionGroup() {
            override fun getChildren(e: AnActionEvent?): Array<AnAction> {
                return arrayOf(
                    object : AnAction("Accept Change", "Keep this change", AllIcons.Actions.Checked) {
                        override fun actionPerformed(e: AnActionEvent) {
                            onAccept()
                        }
                    },
                    object : AnAction("Reject Change", "Revert this change", AllIcons.Actions.Cancel) {
                        override fun actionPerformed(e: AnActionEvent) {
                            onReject()
                        }
                    }
                )
            }
        }
    }
    
    override fun equals(other: Any?): Boolean {
        return other is CodeChangeGutterRenderer
    }
    
    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}

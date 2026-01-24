package com.c75.magiccodecompletion.ui

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

/**
 * Helper for showing notifications about LLM code changes
 */
object CodeChangeNotification {
    
    private const val NOTIFICATION_GROUP_ID = "MagicCodeCompletion.Changes"
    
    /**
     * Show a notification with Accept All and Undo All actions
     */
    fun showChangeNotification(
        project: Project,
        changeCount: Int,
        onAcceptAll: () -> Unit,
        onUndo: () -> Unit
    ) {
        val message = if (changeCount == 1) {
            "LLM code change applied. Click highlighted areas to accept/reject."
        } else {
            "$changeCount LLM code changes applied. Click highlighted areas to accept/reject."
        }
        
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                "Code Changes Applied",
                message,
                NotificationType.INFORMATION
            )
        
        // Add Accept All action
        notification.addAction(object : NotificationAction("Accept All Changes") {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                onAcceptAll()
                notification.expire()
            }
        })
        
        // Add Undo All action
        notification.addAction(object : NotificationAction("Undo All Changes") {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                onUndo()
                notification.expire()
            }
        })
        
        notification.notify(project)
    }
}

package com.c75.magiccodecompletion.ui

import com.intellij.openapi.actionSystem.AnAction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CodeChangeGutterRendererTest {
    
    @Test
    fun `test icon is not null`() {
        var acceptCalled = false
        var rejectCalled = false
        
        val renderer = CodeChangeGutterRenderer(
            onAccept = { acceptCalled = true },
            onReject = { rejectCalled = true }
        )
        
        assertNotNull(renderer.icon)
    }
    
    @Test
    fun `test tooltip text is descriptive`() {
        val renderer = CodeChangeGutterRenderer(
            onAccept = {},
            onReject = {}
        )
        
        val tooltip = renderer.tooltipText
        assertNotNull(tooltip)
        assertTrue(tooltip!!.contains("LLM") || tooltip.contains("Generated") || tooltip.contains("Change"))
    }
    
    @Test
    fun `test click action is not null`() {
        val renderer = CodeChangeGutterRenderer(
            onAccept = {},
            onReject = {}
        )
        
        val clickAction = renderer.clickAction
        assertNotNull(clickAction)
    }
    
    @Test
    fun `test popup menu actions exist`() {
        val renderer = CodeChangeGutterRenderer(
            onAccept = {},
            onReject = {}
        )
        
        val menuActions = renderer.popupMenuActions
        assertNotNull(menuActions)
    }
    
    @Test
    fun `test popup menu has accept and reject actions`() {
        val renderer = CodeChangeGutterRenderer(
            onAccept = {},
            onReject = {}
        )
        
        val menuActions = renderer.popupMenuActions
        assertNotNull(menuActions)
        
        val children = menuActions!!.getChildren(null)
        assertNotNull(children)
        assertEquals(2, children.size, "Should have Accept and Reject actions")
    }
    
    @Test
    fun `test renderer equality`() {
        val renderer1 = CodeChangeGutterRenderer(
            onAccept = {},
            onReject = {}
        )
        
        val renderer2 = CodeChangeGutterRenderer(
            onAccept = {},
            onReject = {}
        )
        
        // All CodeChangeGutterRenderer instances should be equal
        assertEquals(renderer1, renderer2)
    }
    
    @Test
    fun `test renderer hashCode`() {
        val renderer1 = CodeChangeGutterRenderer(
            onAccept = {},
            onReject = {}
        )
        
        val renderer2 = CodeChangeGutterRenderer(
            onAccept = {},
            onReject = {}
        )
        
        // Hash codes should be the same
        assertEquals(renderer1.hashCode(), renderer2.hashCode())
    }
    
    @Test
    fun `test accept action has correct text`() {
        val renderer = CodeChangeGutterRenderer(
            onAccept = {},
            onReject = {}
        )
        
        val children = renderer.popupMenuActions!!.getChildren(null)
        val acceptAction = children[0]
        
        val text = acceptAction.templateText
        assertNotNull(text)
        assertTrue(text!!.contains("Accept"))
    }
    
    @Test
    fun `test reject action has correct text`() {
        val renderer = CodeChangeGutterRenderer(
            onAccept = {},
            onReject = {}
        )
        
        val children = renderer.popupMenuActions!!.getChildren(null)
        val rejectAction = children[1]
        
        val text = rejectAction.templateText
        assertNotNull(text)
        assertTrue(text!!.contains("Reject"))
    }
}

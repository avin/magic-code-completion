package com.c75.magiccodecompletion.settings

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MagicCodeCompletionSettingsTest {
    
    private lateinit var settings: MagicCodeCompletionSettings
    
    @BeforeEach
    fun setUp() {
        settings = MagicCodeCompletionSettings()
    }
    
    @Test
    fun `test default settings values`() {
        val state = settings.state
        
        assertEquals("https://api.openai.com/v1/chat/completions", state.apiEndpoint)
        assertEquals("", state.apiKey)
        assertEquals("gpt-4", state.model)
        assertEquals(0.7, state.temperature, 0.001)
        assertEquals(2000, state.maxTokens)
        assertTrue(state.systemPrompt.contains("<<<CURSOR>>>"))
    }
    
    @Test
    fun `test state persistence`() {
        val state = settings.state
        state.apiEndpoint = "https://custom.api.com/v1/chat"
        state.apiKey = "custom-key-123"
        state.model = "gpt-3.5-turbo"
        state.temperature = 0.5
        state.maxTokens = 1500
        state.systemPrompt = "Custom prompt"
        
        // Save state
        val savedState = settings.state
        
        // Create new settings instance and load state
        val newSettings = MagicCodeCompletionSettings()
        newSettings.loadState(savedState)
        
        assertEquals("https://custom.api.com/v1/chat", newSettings.state.apiEndpoint)
        assertEquals("custom-key-123", newSettings.state.apiKey)
        assertEquals("gpt-3.5-turbo", newSettings.state.model)
        assertEquals(0.5, newSettings.state.temperature, 0.001)
        assertEquals(1500, newSettings.state.maxTokens)
        assertEquals("Custom prompt", newSettings.state.systemPrompt)
    }
    
    @Test
    fun `test state modification`() {
        val state = settings.state
        val originalEndpoint = state.apiEndpoint
        
        state.apiEndpoint = "https://new.endpoint.com"
        
        assertNotEquals(originalEndpoint, state.apiEndpoint)
        assertEquals("https://new.endpoint.com", state.apiEndpoint)
    }
    
    @Test
    fun `test temperature bounds`() {
        val state = settings.state
        
        // Test setting various temperature values
        state.temperature = 0.0
        assertEquals(0.0, state.temperature, 0.001)
        
        state.temperature = 1.0
        assertEquals(1.0, state.temperature, 0.001)
        
        state.temperature = 2.0
        assertEquals(2.0, state.temperature, 0.001)
    }
    
    @Test
    fun `test max tokens modification`() {
        val state = settings.state
        
        state.maxTokens = 500
        assertEquals(500, state.maxTokens)
        
        state.maxTokens = 4000
        assertEquals(4000, state.maxTokens)
    }
    
    @Test
    fun `test default system prompt contains cursor marker`() {
        assertEquals(true, MagicCodeCompletionSettings.DEFAULT_SYSTEM_PROMPT.contains("<<<CURSOR>>>"))
    }
    
    @Test
    fun `test system prompt can be customized`() {
        val state = settings.state
        val customPrompt = "This is a custom prompt for testing"
        
        state.systemPrompt = customPrompt
        
        assertEquals(customPrompt, state.systemPrompt)
    }
}

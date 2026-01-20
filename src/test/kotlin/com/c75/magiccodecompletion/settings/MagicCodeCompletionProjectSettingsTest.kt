package com.c75.magiccodecompletion.settings

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MagicCodeCompletionProjectSettingsTest {
    
    private lateinit var settings: MagicCodeCompletionProjectSettings
    
    @BeforeEach
    fun setUp() {
        settings = MagicCodeCompletionProjectSettings()
    }
    
    @Test
    fun `test default settings values`() {
        val state = settings.state
        
        assertNotNull(state.codeMapIncludePatterns)
        assertTrue(state.codeMapIncludePatterns.isNotEmpty())
        assertTrue(state.codeMapIncludePatterns.contains("src/**/*.{ts,js,tsx,jsx,css,scss}"))
        assertTrue(state.excludeFiles)
        assertTrue(state.excludePatterns.isNotEmpty())
    }
    
    @Test
    fun `test state persistence`() {
        val state = settings.state
        
        // Modify state
        state.codeMapIncludePatterns = mutableListOf("**/*.kt", "**/*.java")
        state.excludeFiles = false
        state.excludePatterns = mutableListOf(".*\\.test\\..*")
        
        // Save state
        val savedState = settings.state
        
        // Create new settings instance and load state
        val newSettings = MagicCodeCompletionProjectSettings()
        newSettings.loadState(savedState)
        
        assertEquals(2, newSettings.state.codeMapIncludePatterns.size)
        assertTrue(newSettings.state.codeMapIncludePatterns.contains("**/*.kt"))
        assertTrue(newSettings.state.codeMapIncludePatterns.contains("**/*.java"))
        assertFalse(newSettings.state.excludeFiles)
        assertEquals(1, newSettings.state.excludePatterns.size)
        assertEquals(".*\\.test\\..*", newSettings.state.excludePatterns[0])
    }
    
    @Test
    fun `test include patterns modification`() {
        val state = settings.state
        val originalPatterns = state.codeMapIncludePatterns.toList()
        
        state.codeMapIncludePatterns.add("**/*.py")
        
        assertEquals(originalPatterns.size + 1, state.codeMapIncludePatterns.size)
        assertTrue(state.codeMapIncludePatterns.contains("**/*.py"))
    }
    
    @Test
    fun `test exclude patterns modification`() {
        val state = settings.state
        val originalPatterns = state.excludePatterns.toList()
        
        state.excludePatterns.add(".*\\.fixture\\..*")
        
        assertEquals(originalPatterns.size + 1, state.excludePatterns.size)
        assertTrue(state.excludePatterns.contains(".*\\.fixture\\..*"))
    }
    
    @Test
    fun `test exclude files toggle`() {
        val state = settings.state
        
        state.excludeFiles = false
        assertFalse(state.excludeFiles)
        
        state.excludeFiles = true
        assertTrue(state.excludeFiles)
    }
    
    @Test
    fun `test empty include patterns`() {
        val state = settings.state
        state.codeMapIncludePatterns = mutableListOf()
        
        assertTrue(state.codeMapIncludePatterns.isEmpty())
    }
    
    @Test
    fun `test empty exclude patterns`() {
        val state = settings.state
        state.excludePatterns = mutableListOf()
        
        assertTrue(state.excludePatterns.isEmpty())
    }
    
    @Test
    fun `test default exclude patterns contain test files`() {
        val state = settings.state
        
        // Verify default patterns exclude common test file patterns
        val hasTestPattern = state.excludePatterns.any { pattern ->
            pattern.contains("test") || pattern.contains("spec")
        }
        
        assertTrue(hasTestPattern, "Default exclude patterns should include test file patterns")
    }
    
    @Test
    fun `test multiple include patterns`() {
        val state = settings.state
        
        state.codeMapIncludePatterns = mutableListOf(
            "src/**/*.kt",
            "src/**/*.java",
            "**/*.xml",
            "**/*.json"
        )
        
        assertEquals(4, state.codeMapIncludePatterns.size)
        assertTrue(state.codeMapIncludePatterns.all { it.isNotBlank() })
    }
    
    @Test
    fun `test pattern with special characters`() {
        val state = settings.state
        
        state.codeMapIncludePatterns = mutableListOf("src/**/*.{ts,tsx,js,jsx}")
        state.excludePatterns = mutableListOf(".*\\.(test|spec)\\.(ts|tsx|js|jsx)$")
        
        assertEquals("src/**/*.{ts,tsx,js,jsx}", state.codeMapIncludePatterns[0])
        assertEquals(".*\\.(test|spec)\\.(ts|tsx|js|jsx)$", state.excludePatterns[0])
    }
    
    @Test
    fun `test state copy creates independent instance`() {
        val state1 = settings.state
        state1.codeMapIncludePatterns = mutableListOf("**/*.kt")
        
        val state2 = MagicCodeCompletionProjectSettings.State(
            codeMapIncludePatterns = state1.codeMapIncludePatterns.toMutableList(),
            excludeFiles = state1.excludeFiles,
            excludePatterns = state1.excludePatterns.toMutableList()
        )
        
        // Modify state2
        state2.codeMapIncludePatterns.add("**/*.java")
        
        // state1 should not be affected
        assertEquals(1, state1.codeMapIncludePatterns.size)
        assertEquals(2, state2.codeMapIncludePatterns.size)
    }
}

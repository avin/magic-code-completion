package com.c75.magiccodecompletion.settings

import com.intellij.openapi.components.*

@Service(Service.Level.APP)
@State(
    name = "MagicCodeCompletionSettings",
    storages = [Storage("MagicCodeCompletionSettings.xml")]
)
class MagicCodeCompletionSettings : PersistentStateComponent<MagicCodeCompletionSettings.State> {
    
    data class State(
        var apiEndpoint: String = "https://api.openai.com/v1",
        var apiKey: String = "",
        var model: String = "gpt-4",
        var systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
        var temperature: Double = 0.7,
        var maxTokens: Int = 2000,
        var connectTimeout: Int = 30,
        var readTimeout: Int = 120,
        var writeTimeout: Int = 30,
        var debugMode: Boolean = false,
        // Change visualization settings
        var showChangeHighlighting: Boolean = true,
        var showChangeNotification: Boolean = false
    )
    
    private var myState = State()
    
    override fun getState(): State = myState
    
    override fun loadState(state: State) {
        myState = state
    }
    
    companion object {
        private const val ACTIVE_PROMPT_FILE = "prompts/system_prompt_v3.txt"
        
        val DEFAULT_SYSTEM_PROMPT: String by lazy {
            loadPromptFromResources(ACTIVE_PROMPT_FILE)
        }
        
        private fun loadPromptFromResources(resourcePath: String): String {
            return try {
                val classLoader = MagicCodeCompletionSettings::class.java.classLoader
                val inputStream = classLoader.getResourceAsStream(resourcePath)
                    ?: throw IllegalStateException("Prompt file not found: $resourcePath")
                inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                // Fallback to minimal prompt if resource loading fails
                """No prompt"""
            }
        }
        
        fun getInstance(): MagicCodeCompletionSettings = service()
    }
}

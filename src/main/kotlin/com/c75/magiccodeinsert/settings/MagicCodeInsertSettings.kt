package com.c75.magiccodeinsert.settings

import com.intellij.openapi.components.*

@Service(Service.Level.APP)
@State(
    name = "MagicCodeInsertSettings",
    storages = [Storage("MagicCodeInsertSettings.xml")]
)
class MagicCodeInsertSettings : PersistentStateComponent<MagicCodeInsertSettings.State> {
    
    data class State(
        var apiEndpoint: String = "https://api.openai.com/v1/chat/completions",
        var apiKey: String = "",
        var model: String = "gpt-4",
        var systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
        var temperature: Double = 0.7,
        var maxTokens: Int = 2000,
        var connectTimeout: Int = 30,
        var readTimeout: Int = 120,
        var writeTimeout: Int = 30,
        var codeMapIncludePatterns: MutableList<String> = mutableListOf(),
        var excludeFiles: Boolean = true,
        var excludePatterns: MutableList<String> = mutableListOf(
            ".*\\.test\\.[^.]+$",
            ".*\\.spec\\.[^.]+$",
            ".*/__tests__/.*",
            ".*\\.test/.*",
            ".*\\.spec/.*"
        ),
        var debugMode: Boolean = false
    )
    
    private var myState = State()
    
    override fun getState(): State = myState
    
    override fun loadState(state: State) {
        myState = state
    }
    
    companion object {
        const val DEFAULT_SYSTEM_PROMPT = """You are a code completion assistant. 
You will receive code with a <<<CURSOR>>> marker indicating where new code should be inserted.
Your task is to generate ONLY the code that should replace the <<<CURSOR>>> marker.
Do not include any explanations, markdown formatting, or code blocks.
Return only the raw code that should be inserted at the cursor position."""
        
        fun getInstance(): MagicCodeInsertSettings = service()
    }
}

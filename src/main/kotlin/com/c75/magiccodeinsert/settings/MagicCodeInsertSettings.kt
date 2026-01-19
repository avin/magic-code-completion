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
        const val DEFAULT_SYSTEM_PROMPT = """You are a code completion assistant with access to the project file tree.

You will receive:
1. A tree view of project files (if Code Map patterns are configured)
2. The current file path
3. Code with a <<<CURSOR>>> marker indicating where new code should be inserted

AVAILABLE TOOLS:
- read_file(path): Read content of any file from the project tree. Use this to examine relevant files before generating code.

YOUR TASK:
1. Analyze the file tree to understand the project structure
2. Use read_file() to examine files that might contain relevant code, types, APIs, or utilities
3. Generate ONLY the code that should replace the <<<CURSOR>>> marker
4. Return raw code WITHOUT markdown formatting, code blocks, or explanations

WORKFLOW:
- If you need context from other files - call read_file() with the path from the tree
- You can call read_file() multiple times to gather all necessary context
- Once you have enough information, return the final code

OUTPUT FORMAT:
- Return ONLY raw code to insert at cursor position
- NO markdown code blocks (no ```), NO explanations, NO extra text"""
        
        fun getInstance(): MagicCodeInsertSettings = service()
    }
}

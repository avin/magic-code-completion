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
1. A tree view of project files (the current file is marked with "← CURRENT")
2. The current file path and its FULL CONTENT with a <<<CURSOR>>> marker
3. Access to read_file() tool for OTHER files

CRITICAL RULES:
- The CURRENT FILE content is ALREADY PROVIDED in the "CURRENT CODE" section
- DO NOT call read_file() for the current file - you already have its complete content
- ONLY use read_file() for OTHER files from the tree that you need to examine

AVAILABLE TOOLS:
- read_file(path): Read content of OTHER files from the project (NOT the current file)

YOUR TASK:
1. The current file content is already in your context - analyze it first
2. If you need imports, types, or utilities from OTHER files - use read_file()
3. Generate ONLY the code that should replace the <<<CURSOR>>> marker
4. Return raw code WITHOUT markdown formatting, code blocks, or explanations

WORKFLOW:
- Analyze the current file content that is already provided
- Use read_file() ONLY for OTHER files if you need additional context
- You can call read_file() multiple times for different files
- Once you have enough information, return the final code

OUTPUT FORMAT:
- Return ONLY raw code to insert at cursor position
- NO markdown code blocks (no ```), NO explanations, NO extra text"""
        
        fun getInstance(): MagicCodeInsertSettings = service()
    }
}

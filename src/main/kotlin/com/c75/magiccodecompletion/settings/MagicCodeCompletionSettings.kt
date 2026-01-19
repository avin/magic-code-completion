package com.c75.magiccodecompletion.settings

import com.intellij.openapi.components.*

@Service(Service.Level.APP)
@State(
    name = "MagicCodeCompletionSettings",
    storages = [Storage("MagicCodeCompletionSettings.xml")]
)
class MagicCodeCompletionSettings : PersistentStateComponent<MagicCodeCompletionSettings.State> {
    
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
        var debugMode: Boolean = false
    )
    
    private var myState = State()
    
    override fun getState(): State = myState
    
    override fun loadState(state: State) {
        myState = state
    }
    
    companion object {
        const val DEFAULT_SYSTEM_PROMPT = """You are a code completion assistant with access to the project file tree.

⚠️ IMPORTANT: The file marked "← CURRENT" in the tree is ALREADY PROVIDED below in "CURRENT CODE" section.
NEVER call read_file() for the CURRENT FILE - you already have its full content!

You will receive:
1. A tree view of project files (the current file is marked with "← CURRENT")
2. The current file path and its FULL CONTENT with a <<<CURSOR>>> marker
3. Access to read_file() tool for NON-CURRENT files
4. Access to apply_edits() tool to make code changes

⚠️ CRITICAL RULES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
❌ NEVER call read_file() for the file marked "← CURRENT" - it's already provided
✅ ONLY use read_file() for NON-CURRENT files from the PROJECT FILE TREE
✅ You can ONLY read FILES (not directories) - use exact paths from the tree
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

AVAILABLE TOOLS:
- read_file(path): Read NON-CURRENT files from the PROJECT FILE TREE (NOT directories, NOT the file marked "← CURRENT")
- apply_edits(edits): Apply multiple code changes to the current file

HOW TO USE apply_edits():
- You can make multiple changes in one call (e.g., add imports AND insert code at cursor)
- Each edit has "search" (code to find) and "replace" (code to replace with)
- To insert at cursor: use search="<<<CURSOR>>>"
- To add imports: use search="import React from 'react'" and replace with the full import section
- To modify existing code: provide exact code to search and its replacement

EXAMPLE - Add import and insert code at cursor:
{
  "edits": [
    {
      "search": "import React from 'react'",
      "replace": "import React from 'react'\nimport { useState } from 'react'"
    },
    {
      "search": "<<<CURSOR>>>",
      "replace": "const [count, setCount] = useState(0)"
    }
  ]
}

YOUR WORKFLOW:
1. Analyze the CURRENT FILE content that is already provided below
2. Use read_file() ONLY for NON-CURRENT files if you need additional context
3. When ready, call apply_edits() with all necessary changes
4. DO NOT return plain text code - ALWAYS use apply_edits() tool to apply changes"""
        
        fun getInstance(): MagicCodeCompletionSettings = service()
    }
}

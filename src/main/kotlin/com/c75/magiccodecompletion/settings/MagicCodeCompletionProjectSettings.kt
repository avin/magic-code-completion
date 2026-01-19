package com.c75.magiccodecompletion.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "MagicCodeCompletionProjectSettings",
    storages = [Storage("MagicCodeCompletionProjectSettings.xml")]
)
class MagicCodeCompletionProjectSettings : PersistentStateComponent<MagicCodeCompletionProjectSettings.State> {
    
    data class State(
        var codeMapIncludePatterns: MutableList<String> = mutableListOf(
            "src/**/*.{ts,js,tsx,jsx,css,scss}"
        ),
        var excludeFiles: Boolean = true,
        var excludePatterns: MutableList<String> = mutableListOf(
            ".*/(__tests__|__mocks__|tests?|specs?)/.*",
            ".*\\.(test|spec|e2e|int|cy|stories|fixture)\\..*"
        )
    )
    
    private var myState = State()
    
    override fun getState(): State = myState
    
    override fun loadState(state: State) {
        myState = state
    }
    
    companion object {
        fun getInstance(project: Project): MagicCodeCompletionProjectSettings = project.service()
    }
}

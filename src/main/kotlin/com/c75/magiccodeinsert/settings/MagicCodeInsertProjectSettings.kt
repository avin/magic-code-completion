package com.c75.magiccodeinsert.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "MagicCodeInsertProjectSettings",
    storages = [Storage("MagicCodeInsertProjectSettings.xml")]
)
class MagicCodeInsertProjectSettings : PersistentStateComponent<MagicCodeInsertProjectSettings.State> {
    
    data class State(
        var codeMapIncludePatterns: MutableList<String> = mutableListOf(
            "src/**/*.ts",
            "src/**/*.tsx",
            "src/**/*.js",
            "src/**/*.jsx"
        ),
        var excludeFiles: Boolean = true,
        var excludePatterns: MutableList<String> = mutableListOf(
            ".*\\.test\\..*",
            ".*\\.spec\\..*"
        )
    )
    
    private var myState = State()
    
    override fun getState(): State = myState
    
    override fun loadState(state: State) {
        myState = state
    }
    
    companion object {
        fun getInstance(project: Project): MagicCodeInsertProjectSettings = project.service()
    }
}

package com.c75.magiccodeinsert.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

class MagicCodeInsertConfigurable(private val project: Project) : Configurable {
    
    private var settingsPanel: DialogPanel? = null
    
    // UI components
    private val apiEndpointField = JBTextField()
    private val apiKeyField = JBPasswordField()
    private val modelField = JBTextField()
    private var temperatureValue = 0.7
    private var maxTokensValue = 2000
    private var connectTimeoutValue = 30
    private var readTimeoutValue = 120
    private var writeTimeoutValue = 30
    private var codeMapPatternsText = ""
    private var systemPromptContent = ""
    private var excludeFilesEnabled = true
    private var excludePatternsText = ""
    private var debugModeEnabled = false
    
    override fun getDisplayName(): String = "Magic Code Insert"
    
    override fun createComponent(): JComponent {
        val settings = MagicCodeInsertSettings.getInstance().state
        val projectSettings = MagicCodeInsertProjectSettings.getInstance(project).state
        
        // Initialize fields with current settings
        apiEndpointField.text = settings.apiEndpoint
        apiKeyField.text = settings.apiKey
        modelField.text = settings.model
        temperatureValue = settings.temperature
        maxTokensValue = settings.maxTokens
        connectTimeoutValue = settings.connectTimeout
        readTimeoutValue = settings.readTimeout
        writeTimeoutValue = settings.writeTimeout
        codeMapPatternsText = projectSettings.codeMapIncludePatterns.joinToString("\n")
        excludeFilesEnabled = projectSettings.excludeFiles
        excludePatternsText = projectSettings.excludePatterns.joinToString("\n")
        systemPromptContent = settings.systemPrompt
        debugModeEnabled = settings.debugMode
        
        settingsPanel = panel {
            group("OpenAI API Configuration") {
                row("API Endpoint:") {
                    cell(apiEndpointField)
                        .columns(COLUMNS_LARGE)
                        .comment("OpenAI-compatible API endpoint")
                }
                row("API Key:") {
                    cell(apiKeyField)
                        .columns(COLUMNS_LARGE)
                        .comment("Your API key (will be stored securely)")
                }
                row("Model:") {
                    cell(modelField)
                        .columns(COLUMNS_MEDIUM)
                        .comment("Model name (e.g., gpt-4, gpt-3.5-turbo)")
                }
                row("Temperature:") {
                    val temperatureSlider = slider(0, 20, 1, 10)
                        .comment("Creativity level (0.0 - 2.0)")
                    temperatureSlider.component.value = (temperatureValue * 10).toInt()
                    temperatureSlider.component.addChangeListener {
                        temperatureValue = temperatureSlider.component.value / 10.0
                    }
                }
                row("Max Tokens:") {
                    intTextField(100..8000)
                        .comment("Maximum tokens in response")
                        .bindIntText({ maxTokensValue }, { maxTokensValue = it })
                }
            }
            
            group("Timeouts (seconds)") {
                row("Connect Timeout:") {
                    intTextField(5..300)
                        .comment("Timeout for establishing connection")
                        .bindIntText({ connectTimeoutValue }, { connectTimeoutValue = it })
                }
                row("Read Timeout:") {
                    intTextField(10..600)
                        .comment("Timeout for reading LLM response (increase for slow APIs)")
                        .bindIntText({ readTimeoutValue }, { readTimeoutValue = it })
                }
                row("Write Timeout:") {
                    intTextField(5..300)
                        .comment("Timeout for sending request")
                        .bindIntText({ writeTimeoutValue }, { writeTimeoutValue = it })
                }
            }
            
            group("Project Files (Context for LLM)") {
                row {
                    textArea()
                        .rows(5)
                        .resizableColumn()
                        .align(AlignX.FILL)
                        .bindText({ codeMapPatternsText }, { codeMapPatternsText = it })
                        .comment("Glob patterns for files to include in project tree (one per line)")
                }
                row {
                    checkBox("Exclude files")
                        .bindSelected({ excludeFilesEnabled }, { excludeFilesEnabled = it })
                        .comment("Automatically exclude files matching patterns below")
                }
                row {
                    textArea()
                        .rows(5)
                        .resizableColumn()
                        .align(AlignX.FILL)
                        .bindText({ excludePatternsText }, { excludePatternsText = it })
                        .comment("Regex patterns to exclude files (one per line)")
                        .enabled(excludeFilesEnabled)
                }
            }
            
            group("Debug") {
                row {
                    checkBox("Enable debug mode")
                        .bindSelected({ debugModeEnabled }, { debugModeEnabled = it })
                        .comment("Log full LLM request to IDE log and show notification")
                }
            }
            
            group("System Prompt") {
                row {
                    textArea()
                        .rows(10)
                        .resizableColumn()
                        .align(AlignX.FILL)
                        .bindText({ systemPromptContent }, { systemPromptContent = it })
                        .comment("Instructions for the LLM. Use <<<CURSOR>>> as placeholder for cursor position.")
                }
                row {
                    button("Reset to Default") {
                        systemPromptContent = MagicCodeInsertSettings.DEFAULT_SYSTEM_PROMPT
                        settingsPanel?.reset()
                    }
                }
            }
        }
        
        return settingsPanel!!
    }
    
    override fun isModified(): Boolean {
        val settings = MagicCodeInsertSettings.getInstance().state
        val projectSettings = MagicCodeInsertProjectSettings.getInstance(project).state
        val panelModified = settingsPanel?.isModified() ?: false
        return panelModified ||
                apiEndpointField.text != settings.apiEndpoint ||
                String(apiKeyField.password) != settings.apiKey ||
                modelField.text != settings.model ||
                temperatureValue != settings.temperature
    }
    
    override fun apply() {
        settingsPanel?.apply()
        val settings = MagicCodeInsertSettings.getInstance().state
        val projectSettings = MagicCodeInsertProjectSettings.getInstance(project).state
        
        settings.apiEndpoint = apiEndpointField.text
        settings.apiKey = String(apiKeyField.password)
        settings.model = modelField.text
        settings.temperature = temperatureValue
        settings.maxTokens = maxTokensValue
        settings.connectTimeout = connectTimeoutValue
        settings.readTimeout = readTimeoutValue
        settings.writeTimeout = writeTimeoutValue
        settings.systemPrompt = systemPromptContent
        settings.debugMode = debugModeEnabled
        
        projectSettings.excludeFiles = excludeFilesEnabled
        projectSettings.codeMapIncludePatterns = codeMapPatternsText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()
        projectSettings.excludePatterns = excludePatternsText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()
    }
    
    override fun reset() {
        val settings = MagicCodeInsertSettings.getInstance().state
        val projectSettings = MagicCodeInsertProjectSettings.getInstance(project).state
        
        apiEndpointField.text = settings.apiEndpoint
        apiKeyField.text = settings.apiKey
        modelField.text = settings.model
        temperatureValue = settings.temperature
        maxTokensValue = settings.maxTokens
        connectTimeoutValue = settings.connectTimeout
        readTimeoutValue = settings.readTimeout
        writeTimeoutValue = settings.writeTimeout
        codeMapPatternsText = projectSettings.codeMapIncludePatterns.joinToString("\n")
        excludeFilesEnabled = projectSettings.excludeFiles
        excludePatternsText = projectSettings.excludePatterns.joinToString("\n")
        debugModeEnabled = settings.debugMode
        systemPromptContent = settings.systemPrompt
        settingsPanel?.reset()
    }
}

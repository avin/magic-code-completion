package com.c75.magiccodecompletion.settings

import com.c75.magiccodecompletion.service.LLMService
import com.c75.magiccodecompletion.services.FileTreeService
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import java.awt.Dimension
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent

class MagicCodeCompletionConfigurable(private val project: Project) : Configurable {
    
    private var settingsPanel: DialogPanel? = null
    
    // UI components
    private val apiEndpointField = JBTextField()
    private val apiKeyField = JBPasswordField()
    private val modelComboBoxModel = DefaultComboBoxModel<String>()
    private val modelComboBox = ComboBox<String>(modelComboBoxModel)
    private val refreshModelsButton = JButton("Refresh")
    private var modelsLoadedFromApi = false
    private var modelRefreshInProgress = false
    private var currentModels: List<String> = emptyList()
    private var modelRequestId = 0
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
    private var showChangeHighlightingEnabled = true
    private var showChangeNotificationEnabled = false
    
    override fun getDisplayName(): String = "Magic Code Completion"
    
    override fun createComponent(): JComponent {
        val settings = MagicCodeCompletionSettings.getInstance().state
        val projectSettings = MagicCodeCompletionProjectSettings.getInstance(project).state
        
        // Initialize fields with current settings
        apiEndpointField.text = settings.apiEndpoint
        apiKeyField.text = settings.apiKey
        updateModelOptions(listOf(settings.model), settings.model, fromApi = false)
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
        showChangeHighlightingEnabled = settings.showChangeHighlighting
        showChangeNotificationEnabled = settings.showChangeNotification
        modelComboBox.isEditable = false
        refreshModelsButton.addActionListener {
            val currentSelection = modelComboBox.selectedItem?.toString()
            refreshModelsAsync(showErrors = true, preferredModel = currentSelection)
        }
        
        settingsPanel = panel {
            group("OpenAI-Compatible API Configuration") {
                row("API Endpoint:") {
                    cell(apiEndpointField)
                        .columns(COLUMNS_LARGE)
                        .comment("API base URL (e.g., https://api.openai.com/v1)")
                }
                row("API Key:") {
                    cell(apiKeyField)
                        .columns(COLUMNS_LARGE)
                        .comment("API key")
                }
                row("Model:") {
                    cell(modelComboBox)
                        .columns(COLUMNS_LARGE)
                        .comment("Select a model from the list")
                    cell(refreshModelsButton)
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
                        .comment("Timeout for reading LLM response")
                        .bindIntText({ readTimeoutValue }, { readTimeoutValue = it })
                }
                row("Write Timeout:") {
                    intTextField(5..300)
                        .comment("Timeout for sending request")
                        .bindIntText({ writeTimeoutValue }, { writeTimeoutValue = it })
                }
            }
            
            val projectFilesLabel = JBLabel("Project Files (Context for LLM)", AllIcons.General.ProjectConfigurable, JBLabel.TRAILING)
            group(projectFilesLabel) {
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
                row {
                    button("Show files") {
                        showFileTreePreview()
                    }.comment("Preview the file tree from current patterns as it will be seen by the LLM")
                }
            }
            
            group("Code Change Visualization") {
                row {
                    checkBox("Show change highlighting with gutter icons")
                        .bindSelected({ showChangeHighlightingEnabled }, { showChangeHighlightingEnabled = it })
                        .comment("Highlight LLM-generated changes with green background and icon in gutter")
                }
                row {
                    checkBox("Show notification with Accept/Undo buttons")
                        .bindSelected({ showChangeNotificationEnabled }, { showChangeNotificationEnabled = it })
                        .comment("Display notification balloon with 'Accept/Undo Changes'")
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
                        systemPromptContent = MagicCodeCompletionSettings.DEFAULT_SYSTEM_PROMPT
                        settingsPanel?.reset()
                    }
                }
            }
        }
        
        return settingsPanel!!
    }
    
    override fun isModified(): Boolean {
        val settings = MagicCodeCompletionSettings.getInstance().state
        val projectSettings = MagicCodeCompletionProjectSettings.getInstance(project).state
        val panelModified = settingsPanel?.isModified() ?: false
        return panelModified ||
                apiEndpointField.text != settings.apiEndpoint ||
                String(apiKeyField.password) != settings.apiKey ||
                modelComboBox.selectedItem?.toString() != settings.model ||
                temperatureValue != settings.temperature
    }
    
    override fun apply() {
        settingsPanel?.apply()
        val settings = MagicCodeCompletionSettings.getInstance().state
        val projectSettings = MagicCodeCompletionProjectSettings.getInstance(project).state
        
        settings.apiEndpoint = apiEndpointField.text
        settings.apiKey = String(apiKeyField.password)
        val selectedModel = modelComboBox.selectedItem?.toString()?.trim().orEmpty()
        if (selectedModel.isNotBlank()) {
            settings.model = selectedModel
        }
        settings.temperature = temperatureValue
        settings.maxTokens = maxTokensValue
        settings.connectTimeout = connectTimeoutValue
        settings.readTimeout = readTimeoutValue
        settings.writeTimeout = writeTimeoutValue
        settings.systemPrompt = systemPromptContent
        settings.debugMode = debugModeEnabled
        settings.showChangeHighlighting = showChangeHighlightingEnabled
        settings.showChangeNotification = showChangeNotificationEnabled
        
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
        val settings = MagicCodeCompletionSettings.getInstance().state
        val projectSettings = MagicCodeCompletionProjectSettings.getInstance(project).state
        
        apiEndpointField.text = settings.apiEndpoint
        apiKeyField.text = settings.apiKey
        val modelsSource = if (modelsLoadedFromApi) currentModels else listOf(settings.model)
        updateModelOptions(modelsSource, settings.model, fromApi = modelsLoadedFromApi)
        temperatureValue = settings.temperature
        maxTokensValue = settings.maxTokens
        connectTimeoutValue = settings.connectTimeout
        readTimeoutValue = settings.readTimeout
        writeTimeoutValue = settings.writeTimeout
        codeMapPatternsText = projectSettings.codeMapIncludePatterns.joinToString("\n")
        excludeFilesEnabled = projectSettings.excludeFiles
        excludePatternsText = projectSettings.excludePatterns.joinToString("\n")
        debugModeEnabled = settings.debugMode
        showChangeHighlightingEnabled = settings.showChangeHighlighting
        showChangeNotificationEnabled = settings.showChangeNotification
        systemPromptContent = settings.systemPrompt
        settingsPanel?.reset()
    }
    
    private fun updateModelOptions(models: List<String>, preferredModel: String?, fromApi: Boolean) {
        modelsLoadedFromApi = fromApi
        currentModels = models
        modelComboBoxModel.removeAllElements()
        models.forEach { modelComboBoxModel.addElement(it) }
        
        val selectedModel = when {
            preferredModel != null && models.contains(preferredModel) -> preferredModel
            models.isNotEmpty() -> models.first()
            else -> null
        }
        
        if (selectedModel != null) {
            modelComboBox.selectedItem = selectedModel
        }
    }
    
    private fun refreshModelsAsync(showErrors: Boolean, preferredModel: String?) {
        if (modelRefreshInProgress) {
            return
        }
        
        val apiBaseUrl = apiEndpointField.text.trim()
        val apiKey = String(apiKeyField.password).trim()
        if (apiBaseUrl.isBlank() || apiKey.isBlank()) {
            if (showErrors) {
                notifyModelLoad("API base URL and API key are required to load models.", NotificationType.WARNING)
            }
            return
        }
        
        setRefreshInProgress(true)
        val modalityState = settingsPanel?.let { ModalityState.stateForComponent(it) } ?: ModalityState.any()
        val connectTimeout = minOf(connectTimeoutValue, MODEL_REFRESH_CONNECT_TIMEOUT_SECONDS)
        val readTimeout = minOf(readTimeoutValue, MODEL_REFRESH_READ_TIMEOUT_SECONDS)
        val writeTimeout = minOf(writeTimeoutValue, MODEL_REFRESH_WRITE_TIMEOUT_SECONDS)
        val requestId = ++modelRequestId
        
        ApplicationManager.getApplication().executeOnPooledThread {
            var models: List<String>? = null
            var errorMessage: String? = null
            try {
                models = LLMService.getInstance().fetchModels(
                    apiBaseUrl,
                    apiKey,
                    connectTimeout,
                    readTimeout,
                    writeTimeout,
                    MODEL_REFRESH_CALL_TIMEOUT_SECONDS
                )
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to load models."
            }
            
            ApplicationManager.getApplication().invokeLater({
                if (requestId != modelRequestId) {
                    return@invokeLater
                }
                setRefreshInProgress(false)
                
                if (errorMessage != null) {
                    if (showErrors || !modelsLoadedFromApi) {
                        notifyModelLoad(errorMessage!!, NotificationType.ERROR)
                    }
                    return@invokeLater
                }
                
                if (models == null) {
                    return@invokeLater
                }
                if (models!!.isEmpty()) {
                    if (showErrors) {
                        notifyModelLoad("No models returned by the API.", NotificationType.WARNING)
                    }
                    return@invokeLater
                }
                
                updateModelOptions(models!!, preferredModel, fromApi = true)
            }, modalityState)
        }
    }
    
    private fun setRefreshInProgress(inProgress: Boolean) {
        modelRefreshInProgress = inProgress
        refreshModelsButton.isEnabled = !inProgress
        refreshModelsButton.text = if (inProgress) "Loading..." else "Refresh"
    }
    
    private fun notifyModelLoad(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Magic Code Completion")
            .createNotification("Model List", message, type)
            .notify(project)
    }
    
    companion object {
        private const val MODEL_REFRESH_CONNECT_TIMEOUT_SECONDS = 5
        private const val MODEL_REFRESH_READ_TIMEOUT_SECONDS = 15
        private const val MODEL_REFRESH_WRITE_TIMEOUT_SECONDS = 5
        private const val MODEL_REFRESH_CALL_TIMEOUT_SECONDS = 20
    }

    private fun showFileTreePreview() {
        settingsPanel?.apply()

        val includePatterns = codeMapPatternsText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        if (includePatterns.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "No include patterns configured. Add patterns to preview the project file tree.",
                "Project File Tree"
            )
            return
        }

        val excludePatterns = if (excludeFilesEnabled) {
            excludePatternsText
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
        } else {
            emptyList()
        }

        val fileTreeService = project.service<FileTreeService>()
        val currentFilePath = resolveCurrentFilePath()
        val fileTree = fileTreeService.generateFileTreePreview(
            includePatterns = includePatterns,
            excludeFiles = excludeFilesEnabled,
            excludePatterns = excludePatterns,
            currentFilePath = currentFilePath
        )

        val displayText = if (fileTree.isNotBlank()) {
            fileTree
        } else {
            "No files matched the current patterns."
        }

        FileTreePreviewDialog(project, displayText).show()
    }

    private fun resolveCurrentFilePath(): String? {
        val basePath = project.basePath ?: return null
        val selectedFile = FileEditorManager.getInstance(project).selectedEditor?.file ?: return null
        val fullPath = selectedFile.path
        return if (fullPath.startsWith(basePath)) {
            fullPath.removePrefix(basePath).removePrefix("/").removePrefix("\\")
        } else {
            null
        }
    }

    private class FileTreePreviewDialog(project: Project, treeText: String) : DialogWrapper(project, true) {
        private val textArea = JBTextArea(treeText)

        init {
            title = "Project File Tree"
            init()
        }

        override fun createCenterPanel(): JComponent {
            textArea.isEditable = false
            textArea.lineWrap = false
            textArea.wrapStyleWord = false
            textArea.caretPosition = 0
            textArea.font = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)

            val scrollPane = JBScrollPane(textArea)
            scrollPane.preferredSize = Dimension(760, 520)
            return scrollPane
        }
    }
}

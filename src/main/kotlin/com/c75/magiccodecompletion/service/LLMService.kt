package com.c75.magiccodecompletion.service

import com.c75.magiccodecompletion.settings.MagicCodeCompletionSettings
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.util.net.ssl.CertificateManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.awt.datatransfer.StringSelection
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.APP)
class LLMService {
    
    private val gson = Gson()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val LOG = Logger.getInstance(LLMService::class.java)
    
    private fun createClient(settings: MagicCodeCompletionSettings.State): OkHttpClient {
        return createClient(settings.connectTimeout, settings.readTimeout, settings.writeTimeout)
    }
    
    private fun createClient(
        connectTimeout: Int,
        readTimeout: Int,
        writeTimeout: Int,
        callTimeoutSeconds: Int? = null
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(connectTimeout.toLong(), TimeUnit.SECONDS)
            .readTimeout(readTimeout.toLong(), TimeUnit.SECONDS)
            .writeTimeout(writeTimeout.toLong(), TimeUnit.SECONDS)
        
        if (callTimeoutSeconds != null) {
            builder.callTimeout(callTimeoutSeconds.toLong(), TimeUnit.SECONDS)
        }
        
        // Add IDE SSL certificate trust
        try {
            val sslContext = CertificateManager.getInstance().sslContext
            builder.sslSocketFactory(sslContext.socketFactory, CertificateManager.getInstance().trustManager)
        } catch (e: Exception) {
            // Continue with default SSL if custom certs fail
        }
        
        return builder.build()
    }
    
    data class ChatRequest(
        val model: String,
        val messages: List<Message>,
        val temperature: Double,
        @SerializedName("max_tokens")
        val maxTokens: Int,
        val tools: List<Tool>? = null,
        @SerializedName("tool_choice")
        val toolChoice: String? = null
    )
    
    data class Message(
        val role: String,
        val content: String? = null,
        @SerializedName("tool_calls")
        val toolCalls: List<ToolCall>? = null,
        @SerializedName("tool_call_id")
        val toolCallId: String? = null
    )
    
    data class Tool(
        val type: String = "function",
        val function: FunctionDef
    )
    
    data class FunctionDef(
        val name: String,
        val description: String,
        val parameters: Map<String, Any>
    )
    
    data class ToolCall(
        val id: String,
        val type: String,
        val function: FunctionCall
    )
    
    data class FunctionCall(
        val name: String,
        val arguments: String
    )
    
    data class ChatResponse(
        val id: String?,
        val choices: List<Choice>?,
        val error: ErrorResponse?
    )
    
    data class Choice(
        val message: Message,
        @SerializedName("finish_reason")
        val finishReason: String?
    )
    
    data class ErrorResponse(
        val message: String,
        val type: String?
    )
    
    data class ModelsResponse(
        val data: List<ModelInfo>?,
        val error: ErrorResponse?
    )
    
    data class ModelInfo(
        val id: String
    )
    
    fun fetchModels(
        apiBaseUrl: String,
        apiKey: String,
        connectTimeout: Int,
        readTimeout: Int,
        writeTimeout: Int
    ): List<String> {
        return fetchModels(
            apiBaseUrl = apiBaseUrl,
            apiKey = apiKey,
            connectTimeout = connectTimeout,
            readTimeout = readTimeout,
            writeTimeout = writeTimeout,
            callTimeoutSeconds = DEFAULT_MODEL_CALL_TIMEOUT_SECONDS
        )
    }
    
    fun fetchModels(
        apiBaseUrl: String,
        apiKey: String,
        connectTimeout: Int,
        readTimeout: Int,
        writeTimeout: Int,
        callTimeoutSeconds: Int
    ): List<String> {
        if (apiKey.isBlank()) {
            throw LLMException("API key is not configured. Please set it in Settings > Magic Code Completion")
        }
        
        val modelsUrl = buildApiUrl(apiBaseUrl, MODELS_PATH)
        
        val request = Request.Builder()
            .url(modelsUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .get()
            .build()
        
        val client = createClient(connectTimeout, readTimeout, writeTimeout, callTimeoutSeconds)
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw LLMException("Empty response from API")
        
        if (!response.isSuccessful) {
            val errorResponse = try {
                gson.fromJson(responseBody, ModelsResponse::class.java)
            } catch (e: Exception) {
                null
            }
            
            val errorMessage = errorResponse?.error?.message
                ?: "HTTP ${response.code}: ${response.message}"
            throw LLMException("API request failed: $errorMessage")
        }
        
        val modelsResponse = gson.fromJson(responseBody, ModelsResponse::class.java)
        
        if (modelsResponse.error != null) {
            throw LLMException("API error: ${modelsResponse.error.message}")
        }
        
        return modelsResponse.data
            ?.map { it.id }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }
    
    /**
     * Send code with cursor marker to LLM and get completion
     * @param codeWithCursor The code with <<<CURSOR>>> marker
     * @param project Current project (for file tree and reading files)
     * @param currentFilePath Current file path relative to project root
     * @param settingsState Optional settings state (for testing)
     * @param progressIndicator Optional progress indicator for cancellation support
     * @return Generated code to insert at cursor position
     * @throws IOException if network request fails
     * @throws LLMException if API returns an error
     */
    fun getCodeCompletion(
        codeWithCursor: String, 
        project: com.intellij.openapi.project.Project? = null,
        currentFilePath: String? = null,
        settingsState: MagicCodeCompletionSettings.State? = null,
        progressIndicator: com.intellij.openapi.progress.ProgressIndicator? = null
    ): String {
        val settings = settingsState ?: MagicCodeCompletionSettings.getInstance().state
        
        if (settings.apiKey.isBlank()) {
            throw LLMException("API key is not configured. Please set it in Settings > Magic Code Completion")
        }
        
        val baseUrl = normalizeApiBaseUrl(settings.apiEndpoint)
        if (baseUrl.isBlank()) {
            throw LLMException("API base URL is not configured. Please set it in Settings > Magic Code Completion")
        }
        
        val chatUrl = buildApiUrl(baseUrl, CHAT_COMPLETIONS_PATH)
        
        // Build initial user message with file tree
        val userMessage = buildString {
            if (project != null) {
                val projectSettings = com.c75.magiccodecompletion.settings.MagicCodeCompletionProjectSettings.getInstance(project).state
                if (projectSettings.codeMapIncludePatterns.isNotEmpty()) {
                    val fileTreeService = project.service<com.c75.magiccodecompletion.services.FileTreeService>()
                    val fileTree = fileTreeService.generateFileTree(projectSettings.codeMapIncludePatterns, currentFilePath)
                    if (fileTree.isNotBlank()) {
                        appendLine(fileTree)
                        appendLine()
                    }
                }
            }
            
            if (currentFilePath != null) {
                appendLine("CURRENT FILE: $currentFilePath")
                appendLine()
            }
            
            appendLine("CURRENT CODE:")
            append(codeWithCursor)
        }
        
        // Define available tools
        val tools = if (project != null) {
            listOf(
                Tool(
                    function = FunctionDef(
                        name = "read_file",
                        description = "Read the content of a file from the project. Use this to examine files that might be relevant for generating the code.",
                        parameters = mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "path" to mapOf(
                                    "type" to "string",
                                    "description" to "File path relative to project root (e.g., 'src/utils/api.ts')"
                                )
                            ),
                            "required" to listOf("path")
                        )
                    )
                ),
                Tool(
                    function = FunctionDef(
                        name = "apply_edits",
                        description = "Apply multiple code edits to the current file. Use this to insert imports, add code at cursor, or make other changes. If you need to insert code at the cursor position, use search='<<<CURSOR>>>'.",
                        parameters = mapOf(
                            "type" to "object",
                            "properties" to mapOf(
                                "edits" to mapOf(
                                    "type" to "array",
                                    "description" to "List of edits to apply",
                                    "items" to mapOf(
                                        "type" to "object",
                                        "properties" to mapOf(
                                            "search" to mapOf(
                                                "type" to "string",
                                                "description" to "Exact code to find and replace. Use '<<<CURSOR>>>' to insert at cursor position."
                                            ),
                                            "replace" to mapOf(
                                                "type" to "string",
                                                "description" to "Code to replace with"
                                            )
                                        ),
                                        "required" to listOf("search", "replace")
                                    )
                                )
                            ),
                            "required" to listOf("edits")
                        )
                    )
                )
            )
        } else {
            null
        }
        
        // Conversation messages
        val messages = mutableListOf<Message>(
            Message(role = "system", content = settings.systemPrompt),
            Message(role = "user", content = userMessage)
        )
        
        // Tool call loop - max 10 iterations to prevent infinite loops
        var iteration = 0
        val maxIterations = 10
        val currentCall = AtomicReference<Call?>(null)
        
        // Start cancellation monitor thread if progressIndicator is provided
        val cancellationExecutor: ScheduledExecutorService? = if (progressIndicator != null) {
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "MagicCodeCompletion-cancel").apply { isDaemon = true }
            }
        } else {
            null
        }
        val cancellationMonitor: ScheduledFuture<*>? = cancellationExecutor?.scheduleAtFixedRate({
            if (progressIndicator?.isCanceled == true) {
                currentCall.get()?.cancel()
            }
        }, 0, 50, TimeUnit.MILLISECONDS) // Check every 50ms
        
        try {
            while (iteration < maxIterations) {
                iteration++
                
                // Check for cancellation before each iteration
                if (progressIndicator?.isCanceled == true) {
                    currentCall.get()?.cancel()
                    throw LLMException("Operation cancelled by user")
                }
                
                val chatRequest = ChatRequest(
                    model = settings.model,
                    messages = messages,
                    temperature = settings.temperature,
                    maxTokens = settings.maxTokens,
                    tools = tools
                )
                
                val requestBody = gson.toJson(chatRequest).toRequestBody(JSON_MEDIA_TYPE)
                
                // Debug logging if enabled
                if (settings.debugMode && iteration == 1) {
                    logDebugRequest(settings, userMessage, baseUrl, chatUrl)
                }
                
                val request = Request.Builder()
                    .url(chatUrl)
                    .addHeader("Authorization", "Bearer ${settings.apiKey}")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()
                
                val client = createClient(settings)
                val call = client.newCall(request)
                currentCall.set(call)
                
                // Check for cancellation before executing the request
                if (progressIndicator?.isCanceled == true) {
                    call.cancel()
                    throw LLMException("Operation cancelled by user")
                }
                
                val response = try {
                    call.execute()
                } catch (e: IOException) {
                    if (progressIndicator?.isCanceled == true) {
                        throw LLMException("Operation cancelled by user")
                    }
                    throw e
                } finally {
                    currentCall.set(null)
                }
            
            val responseBody = response.body?.string()
                ?: throw LLMException("Empty response from API")
            
            if (!response.isSuccessful) {
                val errorResponse = try {
                    gson.fromJson(responseBody, ChatResponse::class.java)
                } catch (e: Exception) {
                    null
                }
                
                val errorMessage = errorResponse?.error?.message
                    ?: "HTTP ${response.code}: ${response.message}"
                throw LLMException("API request failed: $errorMessage")
            }
            
            val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
            
            if (chatResponse.error != null) {
                throw LLMException("API error: ${chatResponse.error.message}")
            }
            
            val choice = chatResponse.choices?.firstOrNull()
                ?: throw LLMException("No completion in response")
            
            val assistantMessage = choice.message
            
            // Check if LLM wants to use tools
            if (assistantMessage.toolCalls != null && assistantMessage.toolCalls.isNotEmpty()) {
                // Add assistant message with tool calls
                messages.add(assistantMessage)
                
                // Debug: log tool calls
                if (settings.debugMode) {
                    val toolCallsInfo = buildString {
                        appendLine("=".repeat(80))
                        appendLine("LLM TOOL CALLS (Iteration $iteration)")
                        appendLine("=".repeat(80))
                        assistantMessage.toolCalls.forEach { toolCall ->
                            appendLine("Tool: ${toolCall.function.name}")
                            appendLine("Arguments: ${toolCall.function.arguments}")
                        }
                        appendLine("=".repeat(80))
                    }
                    LOG.info(toolCallsInfo)
                    
                    val toolCallsSummary = assistantMessage.toolCalls
                        .joinToString(", ") { it.function.name + "(${it.function.arguments})" }
                    
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Magic Code Completion")
                        .createNotification(
                            "LLM Tool Calls",
                            "Iteration $iteration: $toolCallsSummary",
                            NotificationType.INFORMATION
                        )
                        .notify(null)
                }
                
                // Process each tool call
                for (toolCall in assistantMessage.toolCalls) {
                    when (toolCall.function.name) {
                        "read_file" -> {
                            val args = gson.fromJson(toolCall.function.arguments, Map::class.java)
                            val filePath = args["path"]?.toString() ?: ""
                            
                            val fileTreeService = project?.service<com.c75.magiccodecompletion.services.FileTreeService>()
                            val fileContent = fileTreeService?.readFile(filePath)
                            
                            val result = if (fileContent != null) {
                                "Content of $filePath:\n\n$fileContent"
                            } else {
                                "Error: File not found or cannot be read: $filePath"
                            }
                            
                            // Debug: log tool response
                            if (settings.debugMode) {
                                val toolResponseInfo = buildString {
                                    appendLine("-".repeat(80))
                                    appendLine("TOOL RESPONSE: read_file")
                                    appendLine("-".repeat(80))
                                    appendLine("File: $filePath")
                                    appendLine("Status: ${if (fileContent != null) "SUCCESS" else "ERROR"}")
                                    if (fileContent != null) {
                                        appendLine("Size: ${fileContent.length} chars")
                                        appendLine("-".repeat(80))
                                        appendLine("Content Preview (first 500 chars):")
                                        appendLine(fileContent.take(500))
                                        if (fileContent.length > 500) {
                                            appendLine("... (${fileContent.length - 500} more chars)")
                                        }
                                    }
                                    appendLine("-".repeat(80))
                                }
                                LOG.info(toolResponseInfo)
                                
                                val statusMsg = if (fileContent != null) {
                                    "✓ Sent $filePath (${fileContent.length} chars)"
                                } else {
                                    "✗ File not found: $filePath"
                                }
                                
                                NotificationGroupManager.getInstance()
                                    .getNotificationGroup("Magic Code Completion")
                                    .createNotification(
                                        "Tool Response",
                                        statusMsg,
                                        if (fileContent != null) NotificationType.INFORMATION else NotificationType.WARNING
                                    )
                                    .notify(null)
                            }
                            
                            // Add tool response
                            messages.add(Message(
                                role = "tool",
                                content = result,
                                toolCallId = toolCall.id
                            ))
                        }
                        
                        "apply_edits" -> {
                            // This is the final answer - edits to apply
                            // Parse edits and return them for processing
                            val args = gson.fromJson(toolCall.function.arguments, Map::class.java)
                            val editsData = args["edits"] as? List<*> ?: emptyList<Map<String, String>>()
                            
                            if (settings.debugMode) {
                                LOG.info("LLM requested apply_edits with ${editsData.size} edits")
                            }
                            
                            // Signal that we have edits to apply by storing them temporarily
                            // We'll handle them after the loop
                            val editsJson = gson.toJson(editsData)
                            messages.add(Message(
                                role = "tool",
                                content = "Edits will be applied: $editsJson",
                                toolCallId = toolCall.id
                            ))
                            
                            // Return the edits as final result
                            cancellationMonitor?.cancel(true)
                            return "APPLY_EDITS:$editsJson"
                        }
                    }
                }
                
                // Continue loop to get next response
            } else {
                // No tool calls - return final answer
                if (settings.debugMode) {
                    val finalAnswerInfo = buildString {
                        appendLine("=".repeat(80))
                        appendLine("LLM FINAL ANSWER (Iteration $iteration)")
                        appendLine("=".repeat(80))
                        appendLine(assistantMessage.content ?: "")
                        appendLine("=".repeat(80))
                    }
                    LOG.info(finalAnswerInfo)
                    
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Magic Code Completion")
                        .createNotification(
                            "LLM Final Answer",
                            "Generated code ready (${assistantMessage.content?.length ?: 0} chars)",
                            NotificationType.INFORMATION
                        )
                        .notify(null)
                }
                
                val completion = assistantMessage.content
                    ?: throw LLMException("No completion in response")
                
                cancellationMonitor?.cancel(true)
                return completion.trim()
            }
            }
            
            throw LLMException("Maximum tool call iterations exceeded ($maxIterations)")
        } finally {
            // Always stop the cancellation monitor
            cancellationMonitor?.cancel(true)
            cancellationExecutor?.shutdownNow()
            currentCall.get()?.cancel()
        }
    }
    
    private fun logDebugRequest(
        settings: MagicCodeCompletionSettings.State,
        userMessage: String,
        baseUrl: String,
        chatUrl: String
    ) {
        val fullRequest = buildString {
            appendLine("=".repeat(80))
            appendLine("LLM REQUEST DEBUG")
            appendLine("=".repeat(80))
            appendLine("API Base URL: $baseUrl")
            appendLine("Chat Endpoint: $chatUrl")
            appendLine("Model: ${settings.model}")
            appendLine("Temperature: ${settings.temperature}")
            appendLine("Max Tokens: ${settings.maxTokens}")
            appendLine("-".repeat(80))
            appendLine("System Prompt:")
            appendLine(settings.systemPrompt)
            appendLine("-".repeat(80))
            appendLine("User Message:")
            appendLine(userMessage)
            appendLine("=".repeat(80))
        }
        
        LOG.info(fullRequest)
        
        // Show notification with copy button
        val preview = if (userMessage.length > 500) {
            userMessage.substring(0, 500) + "... (${userMessage.length} chars total)"
        } else {
            userMessage
        }
        
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Magic Code Completion")
            .createNotification(
                "LLM Request Debug",
                "Request: ${preview}\n\nFull request logged to IDE log. Click to copy full request.",
                NotificationType.INFORMATION
            )
            .addAction(object : com.intellij.openapi.actionSystem.AnAction("Copy Full Request") {
                override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                    CopyPasteManager.getInstance().setContents(StringSelection(fullRequest))
                }
            })
            .notify(null)
    }
    
    class LLMException(message: String, cause: Throwable? = null) : Exception(message, cause)
    
    companion object {
        private const val CHAT_COMPLETIONS_PATH = "/chat/completions"
        private const val MODELS_PATH = "/models"
        private const val DEFAULT_MODEL_CALL_TIMEOUT_SECONDS = 20
        
        fun getInstance(): LLMService = service()
    }
    
    private fun normalizeApiBaseUrl(apiBaseUrl: String): String {
        val trimmed = apiBaseUrl.trim()
        if (trimmed.isEmpty()) {
            return trimmed
        }
        
        val withoutTrailingSlash = trimmed.removeSuffix("/")
        val chatSuffix = CHAT_COMPLETIONS_PATH
        return if (withoutTrailingSlash.endsWith(chatSuffix)) {
            withoutTrailingSlash.removeSuffix(chatSuffix)
        } else {
            withoutTrailingSlash
        }
    }
    
    private fun buildApiUrl(apiBaseUrl: String, path: String): String {
        val normalizedBase = normalizeApiBaseUrl(apiBaseUrl)
        if (normalizedBase.isBlank()) {
            throw LLMException("API base URL is not configured. Please set it in Settings > Magic Code Completion")
        }
        
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return normalizedBase + normalizedPath
    }
}

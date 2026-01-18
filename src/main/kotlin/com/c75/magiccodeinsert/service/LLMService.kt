package com.c75.magiccodeinsert.service

import com.c75.magiccodeinsert.settings.MagicCodeInsertSettings
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.net.ssl.CertificateManager
import com.intellij.util.proxy.CommonProxy
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

@Service(Service.Level.APP)
class LLMService {
    
    private val gson = Gson()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    
    private fun createClient(settings: MagicCodeInsertSettings.State): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(settings.connectTimeout.toLong(), TimeUnit.SECONDS)
            .readTimeout(settings.readTimeout.toLong(), TimeUnit.SECONDS)
            .writeTimeout(settings.writeTimeout.toLong(), TimeUnit.SECONDS)
        
        // Use IDE proxy settings via CommonProxy
        try {
            val uri = URI(settings.apiEndpoint)
            val proxySelector = CommonProxy.getInstance()
            val proxies = proxySelector.select(uri)
            
            if (proxies != null && proxies.isNotEmpty()) {
                val proxy = proxies.first()
                if (proxy.type() != java.net.Proxy.Type.DIRECT) {
                    builder.proxy(proxy)
                }
            }
        } catch (e: Exception) {
            // If proxy configuration fails, continue without proxy
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
        val maxTokens: Int
    )
    
    data class Message(
        val role: String,
        val content: String
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
    
    /**
     * Send code with cursor marker to LLM and get completion
     * @param codeWithCursor The code with <<<CURSOR>>> marker
     * @param settingsState Optional settings state (for testing)
     * @return Generated code to insert at cursor position
     * @throws IOException if network request fails
     * @throws LLMException if API returns an error
     */
    fun getCodeCompletion(codeWithCursor: String, settingsState: MagicCodeInsertSettings.State? = null): String {
        val settings = settingsState ?: MagicCodeInsertSettings.getInstance().state
        
        if (settings.apiKey.isBlank()) {
            throw LLMException("API key is not configured. Please set it in Settings > Magic Code Insert")
        }
        
        val chatRequest = ChatRequest(
            model = settings.model,
            messages = listOf(
                Message(role = "system", content = settings.systemPrompt),
                Message(role = "user", content = codeWithCursor)
            ),
            temperature = settings.temperature,
            maxTokens = settings.maxTokens
        )
        
        val requestBody = gson.toJson(chatRequest).toRequestBody(JSON_MEDIA_TYPE)
        
        val request = Request.Builder()
            .url(settings.apiEndpoint)
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        
        val client = createClient(settings)
        client.newCall(request).execute().use { response ->
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
            
            val completion = chatResponse.choices?.firstOrNull()?.message?.content
                ?: throw LLMException("No completion in response")
            
            return completion.trim()
        }
    }
    
    class LLMException(message: String, cause: Throwable? = null) : Exception(message, cause)
    
    companion object {
        fun getInstance(): LLMService = service()
    }
}

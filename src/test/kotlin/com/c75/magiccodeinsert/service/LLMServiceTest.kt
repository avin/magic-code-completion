package com.c75.magiccodeinsert.service

import com.c75.magiccodeinsert.settings.MagicCodeInsertSettings
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LLMServiceTest {
    
    private lateinit var mockServer: MockWebServer
    private lateinit var llmService: LLMService
    private lateinit var settings: MagicCodeInsertSettings.State
    
    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        
        llmService = LLMService()
        settings = MagicCodeInsertSettings.State()
        
        // Configure settings to use mock server
        settings.apiEndpoint = mockServer.url("/v1/chat/completions").toString()
        settings.apiKey = "test-api-key"
        settings.model = "gpt-4"
        settings.temperature = 0.7
        settings.maxTokens = 2000
        settings.connectTimeout = 30
        settings.readTimeout = 120
        settings.writeTimeout = 30
    }
    
    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }
    
    @Test
    fun `test successful code completion`() {
        val mockResponse = """
            {
                "id": "chatcmpl-123",
                "choices": [
                    {
                        "message": {
                            "role": "assistant",
                            "content": "function hello() {\n    console.log('Hello');\n}"
                        },
                        "finish_reason": "stop"
                    }
                ]
            }
        """.trimIndent()
        
        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(mockResponse)
            .addHeader("Content-Type", "application/json"))
        
        val codeWithCursor = "// Add hello function\n<<<CURSOR>>>"
        val result = llmService.getCodeCompletion(codeWithCursor, null, null, settings)
        
        assertEquals("function hello() {\n    console.log('Hello');\n}", result)
        
        // Verify request
        val request = mockServer.takeRequest()
        assertEquals("Bearer test-api-key", request.getHeader("Authorization"))
        val requestBody = request.body.readUtf8()
        // Gson escapes < and > to \u003c and \u003e
        assertTrue(
            requestBody.contains("<<<CURSOR>>>") || requestBody.contains("\\u003c\\u003c\\u003cCURSOR\\u003e\\u003e\\u003e"),
            "Request body should contain cursor marker"
        )
    }
    
    @Test
    fun `test API error response`() {
        val mockResponse = """
            {
                "error": {
                    "message": "Invalid API key",
                    "type": "invalid_request_error"
                }
            }
        """.trimIndent()
        
        mockServer.enqueue(MockResponse()
            .setResponseCode(401)
            .setBody(mockResponse)
            .addHeader("Content-Type", "application/json"))
        
        val exception = assertThrows(LLMService.LLMException::class.java) {
            llmService.getCodeCompletion("test code", null, null, settings)
        }
        
        assertTrue(exception.message!!.contains("Invalid API key"))
    }
    
    @Test
    fun `test HTTP error without JSON response`() {
        mockServer.enqueue(MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error"))
        
        val exception = assertThrows(LLMService.LLMException::class.java) {
            llmService.getCodeCompletion("test code", null, null, settings)
        }
        
        assertTrue(exception.message!!.contains("HTTP 500"))
    }
    
    @Test
    fun `test missing API key`() {
        settings.apiKey = ""
        
        val exception = assertThrows(LLMService.LLMException::class.java) {
            llmService.getCodeCompletion("test code", null, null, settings)
        }
        
        assertTrue(exception.message!!.contains("API key is not configured"))
    }
    
    @Test
    fun `test empty response choices`() {
        val mockResponse = """
            {
                "id": "chatcmpl-123",
                "choices": []
            }
        """.trimIndent()
        
        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(mockResponse)
            .addHeader("Content-Type", "application/json"))
        
        val exception = assertThrows(LLMService.LLMException::class.java) {
            llmService.getCodeCompletion("test code", null, null, settings)
        }
        
        assertTrue(exception.message!!.contains("No completion in response"))
    }
    
    @Test
    fun `test request contains all parameters`() {
        val mockResponse = """
            {
                "id": "chatcmpl-123",
                "choices": [
                    {
                        "message": {
                            "role": "assistant",
                            "content": "test response"
                        },
                        "finish_reason": "stop"
                    }
                ]
            }
        """.trimIndent()
        
        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(mockResponse)
            .addHeader("Content-Type", "application/json"))
        
        settings.temperature = 0.5
        settings.maxTokens = 1000
        settings.systemPrompt = "Custom prompt"
        
        llmService.getCodeCompletion("test code", null, null, settings)
        
        val request = mockServer.takeRequest()
        val requestBody = request.body.readUtf8()
        
        assertTrue(requestBody.contains("\"model\":\"gpt-4\""))
        assertTrue(requestBody.contains("\"temperature\":0.5"))
        assertTrue(requestBody.contains("\"max_tokens\":1000"))
        assertTrue(requestBody.contains("Custom prompt"))
        assertTrue(requestBody.contains("test code"))
    }
    
    @Test
    fun `test completion text is trimmed`() {
        val mockResponse = """
            {
                "id": "chatcmpl-123",
                "choices": [
                    {
                        "message": {
                            "role": "assistant",
                            "content": "  \n  trimmed content  \n  "
                        },
                        "finish_reason": "stop"
                    }
                ]
            }
        """.trimIndent()
        
        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(mockResponse)
            .addHeader("Content-Type", "application/json"))
        
        val result = llmService.getCodeCompletion("test", null, null, settings)
        
        assertEquals("trimmed content", result)
    }
}

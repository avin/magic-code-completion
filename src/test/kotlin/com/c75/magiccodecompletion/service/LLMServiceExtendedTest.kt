package com.c75.magiccodecompletion.service

import com.c75.magiccodecompletion.settings.MagicCodeCompletionSettings
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * Extended tests for LLMService covering edge cases and advanced scenarios
 */
class LLMServiceExtendedTest {
    
    private lateinit var mockServer: MockWebServer
    private lateinit var llmService: LLMService
    private lateinit var settings: MagicCodeCompletionSettings.State
    
    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        
        llmService = LLMService()
        settings = MagicCodeCompletionSettings.State()
        
        settings.apiEndpoint = mockServer.url("/v1/chat/completions").toString()
        settings.apiKey = "test-api-key"
        settings.model = "gpt-4"
        settings.temperature = 0.7
        settings.maxTokens = 2000
        settings.connectTimeout = 5
        settings.readTimeout = 10
        settings.writeTimeout = 5
    }
    
    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }
    
    @Test
    fun `test request with file tree context`() {
        val mockResponse = """
            {
                "id": "chatcmpl-123",
                "choices": [
                    {
                        "message": {
                            "role": "assistant",
                            "content": "// Generated code based on file tree"
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
        
        val result = llmService.getCodeCompletion("<<<CURSOR>>>", null, "src/main.kt", settings)
        
        assertEquals("// Generated code based on file tree", result)
    }
    
    @Test
    fun `test timeout handling`() {
        mockServer.enqueue(MockResponse()
            .setSocketPolicy(SocketPolicy.NO_RESPONSE))
        
        assertThrows(Exception::class.java) {
            llmService.getCodeCompletion("test code", null, null, settings)
        }
    }
    
    @Test
    fun `test malformed JSON response`() {
        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("{ invalid json }")
            .addHeader("Content-Type", "application/json"))
        
        assertThrows(Exception::class.java) {
            llmService.getCodeCompletion("test code", null, null, settings)
        }
    }
    
    @Test
    fun `test response with whitespace and newlines`() {
        val mockResponse = """
            {
                "id": "chatcmpl-123",
                "choices": [
                    {
                        "message": {
                            "role": "assistant",
                            "content": "\n\n  function test() {\n    return 42;\n  }\n\n  "
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
        
        // Verify trimming works
        assertEquals("function test() {\n    return 42;\n  }", result)
        assertFalse(result.startsWith("\n"))
        assertFalse(result.endsWith("\n"))
    }
    
    @Test
    fun `test rate limit error`() {
        val mockResponse = """
            {
                "error": {
                    "message": "Rate limit exceeded",
                    "type": "rate_limit_error"
                }
            }
        """.trimIndent()
        
        mockServer.enqueue(MockResponse()
            .setResponseCode(429)
            .setBody(mockResponse)
            .addHeader("Content-Type", "application/json"))
        
        val exception = assertThrows(LLMService.LLMException::class.java) {
            llmService.getCodeCompletion("test", null, null, settings)
        }
        
        assertTrue(exception.message!!.contains("Rate limit exceeded"))
    }
    
    @Test
    fun `test authentication error`() {
        val mockResponse = """
            {
                "error": {
                    "message": "Incorrect API key provided",
                    "type": "invalid_request_error"
                }
            }
        """.trimIndent()
        
        mockServer.enqueue(MockResponse()
            .setResponseCode(401)
            .setBody(mockResponse)
            .addHeader("Content-Type", "application/json"))
        
        val exception = assertThrows(LLMService.LLMException::class.java) {
            llmService.getCodeCompletion("test", null, null, settings)
        }
        
        assertTrue(exception.message!!.contains("Incorrect API key"))
    }
    
    @Test
    fun `test very long completion`() {
        val longContent = "x".repeat(10000)
        val mockResponse = """
            {
                "id": "chatcmpl-123",
                "choices": [
                    {
                        "message": {
                            "role": "assistant",
                            "content": "$longContent"
                        },
                        "finish_reason": "length"
                    }
                ]
            }
        """.trimIndent()
        
        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(mockResponse)
            .addHeader("Content-Type", "application/json"))
        
        val result = llmService.getCodeCompletion("test", null, null, settings)
        
        assertEquals(longContent, result)
    }
    
    @Test
    fun `test response with special characters`() {
        val mockResponse = """
            {
                "id": "chatcmpl-123",
                "choices": [
                    {
                        "message": {
                            "role": "assistant",
                            "content": "const str = \"Hello \\\"World\\\"\";\nconst regex = /[a-z]+/g;"
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
        
        assertTrue(result.contains("\\\""))
        assertTrue(result.contains("/[a-z]+/g"))
    }
    
    @Test
    fun `test multiple concurrent requests`() {
        repeat(3) {
            mockServer.enqueue(MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                        "id": "chatcmpl-$it",
                        "choices": [
                            {
                                "message": {
                                    "role": "assistant",
                                    "content": "Response $it"
                                },
                                "finish_reason": "stop"
                            }
                        ]
                    }
                """.trimIndent())
                .addHeader("Content-Type", "application/json"))
        }
        
        val results = (0..2).map {
            llmService.getCodeCompletion("request $it", null, null, settings)
        }
        
        assertEquals(3, results.size)
        results.forEachIndexed { index, result ->
            assertEquals("Response $index", result)
        }
    }
    
    @Test
    fun `test system prompt is included in request`() {
        val customPrompt = "You are a test assistant"
        settings.systemPrompt = customPrompt
        
        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""
                {
                    "id": "chatcmpl-123",
                    "choices": [
                        {
                            "message": {
                                "role": "assistant",
                                "content": "OK"
                            },
                            "finish_reason": "stop"
                        }
                    ]
                }
            """.trimIndent())
            .addHeader("Content-Type", "application/json"))
        
        llmService.getCodeCompletion("test", null, null, settings)
        
        val request = mockServer.takeRequest()
        val requestBody = request.body.readUtf8()
        
        assertTrue(requestBody.contains(customPrompt))
    }
    
    @Test
    fun `test different temperature values`() {
        listOf(0.0, 0.5, 1.0, 1.5, 2.0).forEach { temp ->
            settings.temperature = temp
            
            mockServer.enqueue(MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                        "id": "chatcmpl-123",
                        "choices": [
                            {
                                "message": {
                                    "role": "assistant",
                                    "content": "test"
                                },
                                "finish_reason": "stop"
                            }
                        ]
                    }
                """.trimIndent())
                .addHeader("Content-Type", "application/json"))
            
            llmService.getCodeCompletion("test", null, null, settings)
            
            val request = mockServer.takeRequest()
            val requestBody = request.body.readUtf8()
            
            assertTrue(requestBody.contains("\"temperature\":$temp"))
        }
    }
    
    @Test
    fun `test different max tokens values`() {
        listOf(100, 1000, 2000, 4000, 8000).forEach { maxTokens ->
            settings.maxTokens = maxTokens
            
            mockServer.enqueue(MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                        "id": "chatcmpl-123",
                        "choices": [
                            {
                                "message": {
                                    "role": "assistant",
                                    "content": "test"
                                },
                                "finish_reason": "stop"
                            }
                        ]
                    }
                """.trimIndent())
                .addHeader("Content-Type", "application/json"))
            
            llmService.getCodeCompletion("test", null, null, settings)
            
            val request = mockServer.takeRequest()
            val requestBody = request.body.readUtf8()
            
            assertTrue(requestBody.contains("\"max_tokens\":$maxTokens"))
        }
    }
    
    @Test
    fun `test finish reason variations`() {
        listOf("stop", "length", "content_filter", "tool_calls").forEach { finishReason ->
            val mockResponse = """
                {
                    "id": "chatcmpl-123",
                    "choices": [
                        {
                            "message": {
                                "role": "assistant",
                                "content": "test response"
                            },
                            "finish_reason": "$finishReason"
                        }
                    ]
                }
            """.trimIndent()
            
            mockServer.enqueue(MockResponse()
                .setResponseCode(200)
                .setBody(mockResponse)
                .addHeader("Content-Type", "application/json"))
            
            val result = llmService.getCodeCompletion("test", null, null, settings)
            assertEquals("test response", result)
        }
    }
}

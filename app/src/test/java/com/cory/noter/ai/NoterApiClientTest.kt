package com.cory.noter.ai

import com.cory.noter.agent.AgentLlmRequest
import com.cory.noter.agent.AgentLlmResult
import com.cory.noter.agent.AgentMessage
import com.cory.noter.agent.AgentMessageRole
import com.cory.noter.agent.AgentToolCall
import com.cory.noter.agent.AgentToolChoice
import com.cory.noter.agent.AgentToolSpec
import com.cory.noter.voice.NoterVoiceAsrTranscriber
import com.cory.noter.voice.RecordedVoiceAudio
import com.cory.noter.voice.TemporaryAudioHandle
import com.cory.noter.voice.VoiceAsrRequest
import com.cory.noter.voice.VoiceAsrResult
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test

class NoterApiClientTest {
    private lateinit var server: RawHttpServer

    @Before
    fun setUp() {
        server = RawHttpServer()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `chat client sends public contract and maps assistant tool calls`() = runTest {
        server.enqueue {
            RawHttpResponse(
                body = """
                    {
                      "requestId":"req-chat-1",
                      "message":{
                        "role":"assistant",
                        "content":"",
                        "toolCalls":[{"id":"call-1","name":"create_alarm","arguments":"{}"}]
                      }
                    }
                """.trimIndent(),
            )
        }
        val client = NoterAgentClient(
            callFactory = OkHttpClient(),
            baseUrl = server.baseUrl,
            clientToken = "app-token",
        )

        val result = client.complete(
            AgentLlmRequest(
                messages = listOf(
                    AgentMessage(AgentMessageRole.SYSTEM, "local system prompt"),
                    AgentMessage(AgentMessageRole.USER, "wake me at eight"),
                    AgentMessage(
                        role = AgentMessageRole.ASSISTANT,
                        content = "",
                        toolCalls = listOf(AgentToolCall("call-0", "create_alarm", "{}")),
                    ),
                    AgentMessage(
                        role = AgentMessageRole.TOOL,
                        content = "{\"status\":\"created\"}",
                        toolCallId = "call-0",
                        toolName = "create_alarm",
                    ),
                ),
                tools = listOf(
                    AgentToolSpec(
                        name = "create_alarm",
                        description = "Create an alarm.",
                        parameters = buildJsonObject { put("type", "object") },
                    ),
                ),
                toolChoice = AgentToolChoice.Required("create_alarm"),
            ),
        )

        assertThat(result).isEqualTo(
            AgentLlmResult.Message(
                AgentMessage(
                    role = AgentMessageRole.ASSISTANT,
                    content = "",
                    toolCalls = listOf(AgentToolCall("call-1", "create_alarm", "{}")),
                ),
            ),
        )
        val request = server.requests.single()
        assertThat(request.path).isEqualTo("/api/v1/agent/completions")
        assertThat(request.headers["authorization"]).isEqualTo("Bearer app-token")
        assertThat(request.headers["content-type"]).contains("application/json")
        assertThat(request.body).contains("\"toolChoice\":{\"mode\":\"named\",\"toolName\":\"create_alarm\"}")
        assertThat(request.body).contains("\"toolCallId\":\"call-0\"")
        assertThat(request.body).doesNotContain("apiKey")
        assertThat(request.body).doesNotContain("maxTokens")
        assertThat(request.body).doesNotContain("provider")
        assertThat(request.body).doesNotContain("model")
    }

    @Test
    fun `chat client maps retry metadata and invalid responses to typed failures`() = runTest {
        server.enqueue {
            RawHttpResponse(
                status = 429,
                headers = mapOf("Retry-After" to "3"),
                body = """
                    {"error":{"code":"rate_limited","message":"sentinel provider body","requestId":"req-2","retryable":true}}
                """.trimIndent(),
            )
        }
        val client = NoterAgentClient(
            callFactory = OkHttpClient(),
            baseUrl = server.baseUrl,
            clientToken = "app-token",
        )
        val request = minimalLlmRequest(AgentToolChoice.Auto)

        assertThat(client.complete(request)).isEqualTo(
            AgentLlmResult.ServiceFailure(
                code = NoterApiFailureCode.RATE_LIMITED.wireValue,
                retryable = true,
                retryAfterSeconds = 3,
            ),
        )

        server.enqueue { RawHttpResponse(body = "{\"message\":{\"role\":\"assistant\",\"content\":null}}") }
        assertThat(client.complete(request)).isEqualTo(
            AgentLlmResult.ServiceFailure(
                code = NoterApiFailureCode.INVALID_SERVICE_RESPONSE.wireValue,
                retryable = false,
            ),
        )

        server.enqueue {
            RawHttpResponse(body = "{\"message\":{\"role\":\"assistant\",\"content\":7}}")
        }
        assertThat(client.complete(request)).isEqualTo(
            AgentLlmResult.ServiceFailure(
                code = NoterApiFailureCode.INVALID_SERVICE_RESPONSE.wireValue,
                retryable = false,
            ),
        )

        server.enqueue { RawHttpResponse(status = 503, body = "{\"error\":\"malformed\"}") }
        assertThat(client.complete(request)).isEqualTo(
            AgentLlmResult.ServiceFailure(
                code = NoterApiFailureCode.SERVICE_UNAVAILABLE.wireValue,
                retryable = true,
            ),
        )
    }

    @Test
    fun `asr client sends one m4a part and optional language without model`() = runTest {
        server.enqueue { RawHttpResponse(body = "{\"requestId\":\"req-asr-1\",\"text\":\"  wake me at eight  \"}") }
        val client = NoterAsrClient(
            callFactory = OkHttpClient(),
            baseUrl = server.baseUrl,
            clientToken = "app-token",
        )
        val audio = "m4a-sentinel".toByteArray(StandardCharsets.UTF_8)

        assertThat(client.transcribe(NoterAsrRequest("zh-HK", audio)))
            .isEqualTo(NoterAsrResult.Transcribed("wake me at eight"))
        val request = server.requests.single()
        assertThat(request.path).isEqualTo("/api/v1/asr/transcriptions")
        assertThat(request.headers["authorization"]).isEqualTo("Bearer app-token")
        assertThat(request.headers["content-type"]).contains("multipart/form-data")
        assertThat(request.body).contains("name=\"audio\"")
        assertThat(request.body).contains("recording.m4a")
        assertThat(request.body).contains("name=\"language\"")
        assertThat(request.body).contains("zh-HK")
        assertThat(request.body).contains("m4a-sentinel")
        assertThat(request.body).doesNotContain("model")
    }

    @Test
    fun `asr client maps auth and invalid transcript to typed failures`() = runTest {
        server.enqueue {
            RawHttpResponse(
                status = 401,
                body = "{\"error\":{\"code\":\"unauthorized\",\"message\":\"secret\",\"retryable\":false}}",
            )
        }
        val client = NoterAsrClient(
            callFactory = OkHttpClient(),
            baseUrl = server.baseUrl,
            clientToken = "app-token",
        )
        val audio = byteArrayOf(1, 2, 3)

        assertThat(client.transcribe(NoterAsrRequest(null, audio))).isEqualTo(
            NoterAsrResult.ServiceFailure(NoterApiFailureCode.UNAUTHORIZED, false),
        )

        server.enqueue { RawHttpResponse(body = "{\"text\":\" \"}") }
        assertThat(client.transcribe(NoterAsrRequest(null, audio))).isEqualTo(
            NoterAsrResult.ServiceFailure(NoterApiFailureCode.INVALID_SERVICE_RESPONSE, false),
        )

        server.enqueue { RawHttpResponse(body = "{\"text\":7}") }
        assertThat(client.transcribe(NoterAsrRequest(null, audio))).isEqualTo(
            NoterAsrResult.ServiceFailure(NoterApiFailureCode.INVALID_SERVICE_RESPONSE, false),
        )
    }

    @Test
    fun `voice transcriber retries once and maps final ASR failures without provider text`() = runTest {
        val gateway = RecordingAsrGateway(
            mutableListOf(
                NoterAsrResult.ServiceFailure(
                    NoterApiFailureCode.RATE_LIMITED,
                    retryable = true,
                    retryAfterSeconds = 2,
                ),
                NoterAsrResult.ServiceFailure(
                    NoterApiFailureCode.INVALID_SERVICE_RESPONSE,
                    retryable = false,
                ),
            ),
        )
        val delays = mutableListOf<Long>()
        val transcriber = NoterVoiceAsrTranscriber(gateway, delayProvider = { delays += it })

        val result = transcriber.transcribe(
            VoiceAsrRequest(
                languageCode = "en",
                audio = RecordedVoiceAudio(TemporaryAudioHandle("audio-id"), byteArrayOf(1, 2)),
            ),
        )

        assertThat(result).isEqualTo(VoiceAsrResult.Failed(com.cory.noter.voice.VoiceCaptureFailure.UpdateRequired))
        assertThat(gateway.requests).hasSize(2)
        assertThat(delays).containsExactly(2_000L)
    }

    private fun minimalLlmRequest(choice: AgentToolChoice) = AgentLlmRequest(
        messages = listOf(AgentMessage(AgentMessageRole.USER, "hello")),
        tools = emptyList(),
        toolChoice = choice,
    )

    private class RecordingAsrGateway(
        private val results: MutableList<NoterAsrResult>,
    ) : NoterAsrGateway {
        val requests = mutableListOf<NoterAsrRequest>()

        override suspend fun transcribe(request: NoterAsrRequest): NoterAsrResult {
            requests += request
            return results.removeAt(0)
        }
    }
}

private class RawHttpServer : Closeable {
    private val socket = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
    private val responses = ArrayDeque<() -> RawHttpResponse>()
    private val responseLock = Any()
    val requests = CopyOnWriteArrayList<RawHttpRequest>()
    val baseUrl: String = "http://127.0.0.1:${socket.localPort}"
    private val worker = thread(isDaemon = true, name = "noter-api-test-server") {
        while (!socket.isClosed) {
            try {
                socket.accept().use(::handle)
            } catch (_: SocketException) {
                return@thread
            }
        }
    }

    fun enqueue(response: () -> RawHttpResponse) {
        synchronized(responseLock) { responses.addLast(response) }
    }

    private fun handle(client: Socket) {
        val input = client.getInputStream()
        val requestLine = readAsciiLine(input) ?: return
        val lineParts = requestLine.split(' ', limit = 3)
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = readAsciiLine(input) ?: return
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).lowercase()] = line.substring(separator + 1).trim()
            }
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = input.readNBytes(length).toString(StandardCharsets.ISO_8859_1)
        val request = RawHttpRequest(
            method = lineParts.getOrElse(0) { "" },
            path = lineParts.getOrElse(1) { "" },
            headers = headers,
            body = body,
        )
        requests += request
        val response = synchronized(responseLock) {
            if (responses.isEmpty()) RawHttpResponse(status = 500, body = "no test response queued") else responses.removeFirst().invoke()
        }
        val responseBytes = response.body.toByteArray(StandardCharsets.UTF_8)
        val output = client.getOutputStream()
        output.write("HTTP/1.1 ${response.status} Test\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write("Content-Type: application/json\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write("Content-Length: ${responseBytes.size}\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        response.headers.forEach { (name, value) ->
            output.write("$name: $value\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        }
        output.write("Connection: close\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        output.write(responseBytes)
        output.flush()
    }

    override fun close() {
        socket.close()
        worker.join(1_000)
    }
}

private data class RawHttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String,
)

private data class RawHttpResponse(
    val status: Int = 200,
    val headers: Map<String, String> = emptyMap(),
    val body: String,
)

private fun readAsciiLine(input: InputStream): String? {
    val bytes = ByteArrayOutputStream()
    var previous = -1
    while (true) {
        val next = input.read()
        if (next == -1) return if (bytes.size() == 0) null else bytes.toString(StandardCharsets.ISO_8859_1.name())
        if (previous == '\r'.code && next == '\n'.code) {
            val raw = bytes.toByteArray()
            return raw.copyOf(raw.size - 1).toString(StandardCharsets.ISO_8859_1)
        }
        bytes.write(next)
        previous = next
    }
}

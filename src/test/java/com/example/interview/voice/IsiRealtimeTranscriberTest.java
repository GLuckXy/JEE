package com.example.interview.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IsiRealtimeTranscriberTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final WebSocket socket = mock(WebSocket.class);
    private final AtomicReference<WebSocket.Listener> listener = new AtomicReference<>();
    private final IsiRealtimeTranscriber asr;

    IsiRealtimeTranscriberTest() {
        var http = mock(HttpClient.class);
        var builder = mock(WebSocket.Builder.class);
        when(http.newWebSocketBuilder()).thenReturn(builder);
        when(builder.connectTimeout(any())).thenReturn(builder);
        when(builder.buildAsync(any(), any())).thenAnswer(call -> {
            assertThat(call.<URI>getArgument(0).toASCIIString()).isEqualTo("wss://isi.example.test/ws/v1?token=test%2Btoken");
            listener.set(call.getArgument(1));
            listener.get().onOpen(socket);
            return CompletableFuture.completedFuture(socket);
        });
        when(socket.sendClose(eq(WebSocket.NORMAL_CLOSURE), anyString()))
                .thenReturn(CompletableFuture.completedFuture(socket));
        asr = new IsiRealtimeTranscriber(http, "wss://isi.example.test/ws/v1");
    }

    @Test
    void streamsPcmFramesAndReturnsFinalSentencesInOrder() {
        var commands = new ArrayList<JsonNode>();
        var frames = new ArrayList<Integer>();
        byte[] pcm = new byte[6_402];
        pcm[0] = 42;
        pcm[6_400] = 43;
        when(socket.sendText(any(), eq(true))).thenAnswer(call -> {
            var command = JSON.readTree(call.<CharSequence>getArgument(0).toString());
            commands.add(command);
            if (commands.size() == 1) {
                assertThat(frames).isEmpty();
                event("TranscriptionStarted", "{}");
            } else {
                assertThat(frames).containsExactly(3_200, 3_200, 2);
                event("SentenceEnd", "{\"index\":2,\"result\":\"最后一句。\"}");
                event("TranscriptionResultChanged", "{\"index\":1,\"result\":\"中间结果\"}");
                event("SentenceEnd", "{\"index\":1,\"result\":\"第一句。\"}");
                event("TranscriptionCompleted", "{}");
            }
            return CompletableFuture.completedFuture(socket);
        });
        when(socket.sendBinary(any(), eq(true))).thenAnswer(call -> {
            ByteBuffer frame = call.getArgument(0);
            assertThat((int) frame.get(frame.position())).isEqualTo(List.of(42, 0, 43).get(frames.size()));
            frames.add(frame.remaining());
            return CompletableFuture.completedFuture(socket);
        });

        assertThat(asr.transcribe("test-app", "test+token", pcm, this::failure))
                .isEqualTo("第一句。\n最后一句。");
        var start = commands.get(0);
        var stop = commands.get(1);
        assertThat(start.at("/header/name").asText()).isEqualTo("StartTranscription");
        assertThat(start.at("/header/namespace").asText()).isEqualTo("SpeechTranscriber");
        assertThat(start.at("/header/appkey").asText()).isEqualTo("test-app");
        assertThat(start.at("/payload/format").asText()).isEqualTo("pcm");
        assertThat(start.at("/payload/sample_rate").asInt()).isEqualTo(16_000);
        assertThat(stop.at("/header/name").asText()).isEqualTo("StopTranscription");
        assertThat(stop.at("/header/task_id").asText()).matches("[a-f0-9]{32}")
                .isEqualTo(start.at("/header/task_id").asText());
        assertThat(stop.at("/header/message_id").asText()).matches("[a-f0-9]{32}")
                .isNotEqualTo(start.at("/header/message_id").asText());
        verify(socket).sendClose(WebSocket.NORMAL_CLOSURE, "");
        verify(socket).abort();
    }

    @Test
    void returnsProviderFailureWithoutStreamingOrExposingCredentials() {
        when(socket.sendText(any(), eq(true))).thenAnswer(call -> {
            listener.get().onText(socket, """
                    {"header":{"name":"TaskFailed","status":40000010,"status_text":"test+token"}}
                    """, true);
            return CompletableFuture.completedFuture(socket);
        });

        assertThatThrownBy(() -> asr.transcribe("test-app", "test+token", new byte[3200], this::failure))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("40000010")
                .hasMessageNotContaining("test+token");
        verify(socket, never()).sendBinary(any(), anyBoolean());
        verify(socket).abort();
    }

    @Test
    void doesNotReturnPartialTextAfterConnectionCloses() {
        when(socket.sendText(any(), eq(true))).thenAnswer(call -> {
            event("TranscriptionStarted", "{}");
            return CompletableFuture.completedFuture(socket);
        });
        when(socket.sendBinary(any(), eq(true))).thenAnswer(call -> {
            event("SentenceEnd", "{\"index\":1,\"result\":\"部分识别结果\"}");
            listener.get().onClose(socket, 1001, "");
            return CompletableFuture.completedFuture(socket);
        });

        assertThatThrownBy(() -> asr.transcribe("test-app", "test+token", new byte[3200], this::failure))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("连接已关闭");
        verify(socket).sendText(any(), eq(true));
        verify(socket).abort();
    }

    private void event(String name, String payload) {
        listener.get().onText(socket, "{\"header\":{\"name\":\"" + name + "\",\"status\":20000000},", false);
        listener.get().onText(socket, "\"payload\":" + payload + "}", true);
    }

    private ResponseStatusException failure(int status) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "服务状态 " + status);
    }
}

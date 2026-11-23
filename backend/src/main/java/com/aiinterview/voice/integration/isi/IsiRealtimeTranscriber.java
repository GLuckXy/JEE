package com.aiinterview.voice.integration.isi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntFunction;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

final class IsiRealtimeTranscriber {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int SUCCESS = 20000000;
    private static final int FRAME_MS = 100;
    private static final int FRAME_BYTES = FfmpegPcmConverter.RATE * 2 * FRAME_MS / 1000;
    private final HttpClient http;
    private final String endpoint;

    IsiRealtimeTranscriber(HttpClient http, String endpoint) {
        this.http = http;
        this.endpoint = endpoint;
    }

    String transcribe(String appKey, String token, byte[] pcm, IntFunction<ResponseStatusException> serviceError) {
        var uri = UriComponentsBuilder.fromUriString(endpoint).queryParam("token", "{token}")
                .encode().buildAndExpand(token).toUri();
        var events = new TaskEvents(serviceError);
        String taskId = messageId();
        long audioMs = (long) pcm.length * FRAME_MS / FRAME_BYTES;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(audioMs + 30_000);
        var connection = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5)).buildAsync(uri, events);
        WebSocket socket = null;
        try {
            socket = connection.get(5, TimeUnit.SECONDS);
            var start = command(appKey, taskId, "StartTranscription");
            start.putObject("payload").put("format", "pcm").put("sample_rate", FfmpegPcmConverter.RATE)
                    .put("enable_punctuation_prediction", true).put("enable_inverse_text_normalization", true);
            await(socket.sendText(start.toString(), true), deadline, 5);
            await(events.started, deadline, 8);
            for (int offset = 0; offset < pcm.length && !events.completed.isDone(); offset += FRAME_BYTES) {
                long nextFrame = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(FRAME_MS);
                int length = Math.min(FRAME_BYTES, pcm.length - offset);
                await(socket.sendBinary(ByteBuffer.wrap(pcm, offset, length), true), deadline, 5);
                if (offset + length < pcm.length) {
                    long remaining = nextFrame - System.nanoTime();
                    if (remaining > 0) TimeUnit.NANOSECONDS.sleep(remaining);
                }
            }
            if (!events.completed.isDone()) {
                events.stopRequested = true;
                await(socket.sendText(command(appKey, taskId, "StopTranscription").toString(), true), deadline, 5);
            }
            String text = await(events.completed, deadline, 12);
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "").orTimeout(1, TimeUnit.SECONDS).exceptionally(error -> null).join();
            return text;
        } catch (TimeoutException error) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "阿里云 ISI 实时转写超时，可用原录音重试");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "阿里云 ISI 实时转写已中断，可用原录音重试");
        } catch (ExecutionException error) {
            if (events.completed.isCompletedExceptionally()) {
                try {
                    events.completed.join();
                } catch (CompletionException failure) {
                    if (failure.getCause() instanceof ResponseStatusException status) throw status;
                }
            }
            if (error.getCause() instanceof ResponseStatusException failure) throw failure;
            if (error.getCause() instanceof WebSocketHandshakeException failure) {
                int code = failure.getResponse().statusCode();
                if (code == 401) throw serviceError.apply(40000001);
                throw gatewayError("握手失败（HTTP " + code + "），请检查地域和 WebSocket 连接");
            }
            throw gatewayError("连接失败或意外断开，可用原录音重试");
        } finally {
            if (socket != null) socket.abort();
            else connection.thenAccept(WebSocket::abort);
        }
    }

    private static <T> T await(CompletableFuture<T> future, long deadline, int seconds)
            throws InterruptedException, TimeoutException, ExecutionException {
        return future.get(Math.min(deadline - System.nanoTime(), TimeUnit.SECONDS.toNanos(seconds)), TimeUnit.NANOSECONDS);
    }

    private static ObjectNode command(String appKey, String taskId, String name) {
        var command = JSON.createObjectNode();
        command.putObject("header").put("appkey", appKey).put("task_id", taskId).put("message_id", messageId())
                .put("namespace", "SpeechTranscriber").put("name", name);
        return command;
    }

    private static String messageId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static ResponseStatusException gatewayError(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "阿里云 ISI 实时转写" + detail);
    }

    private static final class TaskEvents implements WebSocket.Listener {
        private final CompletableFuture<Void> started = new CompletableFuture<>();
        private final CompletableFuture<String> completed = new CompletableFuture<>();
        private final StringBuilder message = new StringBuilder();
        private final TreeMap<Integer, String> sentences = new TreeMap<>();
        private final IntFunction<ResponseStatusException> serviceError;
        private volatile boolean stopRequested;

        TaskEvents(IntFunction<ResponseStatusException> serviceError) {
            this.serviceError = serviceError;
        }

        @Override
        public CompletableFuture<?> onText(WebSocket socket, CharSequence data, boolean last) {
            message.append(data);
            if (last) {
                try {
                    handleMessage();
                } catch (JsonProcessingException error) {
                    fail(gatewayError("收到无法解析的响应，可用原录音重试"));
                } finally {
                    message.setLength(0);
                }
            }
            socket.request(1);
            return null;
        }

        private void handleMessage() throws JsonProcessingException {
            var event = JSON.readTree(message.toString());
            var header = event.path("header");
            int status = header.path("status").asInt(-1);
            if (status != SUCCESS) {
                fail(serviceError.apply(status));
                return;
            }
            switch (header.path("name").asText()) {
                case "TranscriptionStarted" -> started.complete(null);
                case "SentenceEnd" -> {
                    var payload = event.path("payload");
                    int index = payload.path("index").asInt();
                    String text = payload.path("result").asText("").strip();
                    if (index > 0 && !text.isEmpty()) sentences.put(index, text);
                }
                case "TranscriptionCompleted" -> {
                    if (stopRequested) completed.complete(String.join("\n", sentences.values()));
                    else fail(gatewayError("提前结束，可用原录音重试"));
                }
                default -> { }
            }
        }

        @Override
        public CompletableFuture<?> onClose(WebSocket socket, int statusCode, String reason) {
            fail(gatewayError("连接已关闭（WebSocket " + statusCode + "），可用原录音重试"));
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            fail(gatewayError("连接异常，可用原录音重试"));
        }

        private void fail(ResponseStatusException error) {
            completed.completeExceptionally(error);
            started.completeExceptionally(error);
        }
    }
}

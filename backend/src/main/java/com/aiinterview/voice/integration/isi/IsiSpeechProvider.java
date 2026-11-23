package com.aiinterview.voice.integration.isi;

import com.aiinterview.voice.service.SpeechProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.function.BiFunction;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import static com.aiinterview.common.web.ApiErrors.invalid;

final class IsiSpeechProvider implements SpeechProvider {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final MediaType MP3 = MediaType.parseMediaType("audio/mpeg");
    private static final int INVALID_TOKEN = 40000001;
    private final IsiRealtimeTranscriber asr;
    private final RestClient tts;
    private final String base;
    private final String appKey;
    private final IsiTokenService tokens;
    private final BiFunction<Resource, String, byte[]> converter;

    IsiSpeechProvider(IsiRealtimeTranscriber asr, RestClient tts, String base, String appKey, IsiTokenService tokens,
                      BiFunction<Resource, String, byte[]> converter) {
        this.asr = asr;
        this.tts = tts;
        this.base = base;
        this.appKey = appKey;
        this.tokens = tokens;
        this.converter = converter;
    }

    @Override public boolean asrConfigured() { return tokens.configured() && !appKey.isBlank(); }
    @Override public boolean ttsConfigured() { return asrConfigured(); }

    @Override
    public String transcribe(Resource audio, String mediaType) {
        String text = transcribeRecording(audio, mediaType);
        if (text.isBlank()) throw invalid("未识别到可用文字，请重试或手动填写");
        return text;
    }

    @Override
    public String transcribeRecording(Resource audio, String mediaType) {
        requireCredentials();
        byte[] pcm = converter.apply(audio, mediaType);
        String token = tokens.get();
        return asr.transcribe(appKey, token, pcm, status -> serviceError("实时转写", status, token));
    }

    @Override
    public byte[] speak(String text) {
        requireCredentials();
        String token = tokens.get();
        try {
            ResponseEntity<byte[]> response = tts.post().uri(base + "/stream/v1/tts")
                    .header("X-NLS-Token", token).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("appkey", appKey, "text", text, "voice", "xiaoyun",
                            "format", "mp3", "sample_rate", FfmpegPcmConverter.RATE))
                    .retrieve().toEntity(byte[].class);
            byte[] audio = response.getBody();
            MediaType type = response.getHeaders().getContentType();
            if (type != null && MP3.isCompatibleWith(type) && audio != null && audio.length > 0) return audio;
            throw serviceError("播报", parse(audio).path("status").asInt(-1), token);
        } catch (RestClientException error) {
            throw requestError("播报", error, token);
        }
    }

    private void requireCredentials() {
        if (!asrConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "阿里云 ISI 未配置，请设置 ALIYUN_AK_ID、ALIYUN_AK_SECRET 和 NLS_APP_KEY");
        }
    }

    private ResponseStatusException requestError(String action, RestClientException error, String token) {
        if (error instanceof ResourceAccessException) {
            return new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "阿里云 ISI " + action + "连接失败或超时，请重试");
        }
        if (error instanceof RestClientResponseException response) {
            int code = response.getStatusCode().value();
            int status = parse(response.getResponseBodyAsByteArray()).path("status").asInt(-1);
            if (status > 0) return serviceError(action, status, token);
            if (code == 401) tokens.invalidate(token);
            return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "阿里云 ISI " + action
                    + "失败（HTTP " + code + "），请检查 AppKey、服务开通状态和地域");
        }
        return serviceError(action, -1, token);
    }

    private ResponseStatusException serviceError(String action, int status, String token) {
        if (status == INVALID_TOKEN) tokens.invalidate(token);
        String detail = status < 0 ? "未收到有效响应" : "服务状态 " + status;
        String hint = switch (status) {
            case INVALID_TOKEN -> "可用原录音重试，后端将重新获取令牌";
            case 40000005 -> "请求达到服务并发限制，请稍后重试";
            case 40000010 -> "请检查对应服务的试用有效期、开通状态和账户余额";
            default -> "请检查 AppKey 和对应语音服务的开通状态";
        };
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "阿里云 ISI " + action + "失败（" + detail
                + "），" + hint);
    }

    private static JsonNode parse(byte[] body) {
        if (body != null) {
            try {
                JsonNode node = JSON.readTree(body);
                if (node != null && node.isObject()) return node;
            } catch (IOException ignored) {
                // Errors may contain credential-bearing details. Only the numeric status is returned.
            }
        }
        return JSON.createObjectNode();
    }

}

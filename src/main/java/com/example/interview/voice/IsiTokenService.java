package com.example.interview.voice;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

final class IsiTokenService {
    private static final long REFRESH_AHEAD_SECONDS = 180;
    private record Token(String value, long expiresAt) {}

    private final RestClient client;
    private final String endpoint;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final Clock clock;
    private Token cached;

    IsiTokenService(RestClient client, String endpoint, String accessKeyId, String accessKeySecret, Clock clock) {
        this.client = client;
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.clock = clock;
    }

    boolean configured() {
        return !accessKeyId.isBlank() && !accessKeySecret.isBlank();
    }

    synchronized String get() {
        long now = clock.instant().getEpochSecond();
        if (cached != null && cached.expiresAt() > now + REFRESH_AHEAD_SECONDS) return cached.value();
        cached = fetch();
        return cached.value();
    }

    synchronized void invalidate(String rejected) {
        if (cached != null && cached.value().equals(rejected)) cached = null;
    }

    private Token fetch() {
        var params = Map.of(
                "AccessKeyId", accessKeyId, "Action", "CreateToken", "Version", "2019-02-28",
                "Format", "JSON", "RegionId", "cn-shanghai", "SignatureMethod", "HMAC-SHA1",
                "SignatureVersion", "1.0", "SignatureNonce", UUID.randomUUID().toString(),
                "Timestamp", clock.instant().truncatedTo(ChronoUnit.SECONDS).toString());
        String form = signedForm("POST", params, accessKeySecret);
        try {
            var response = client.post().uri(endpoint).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON).body(form).retrieve().body(JsonNode.class);
            JsonNode token = response == null ? null : response.path("Token");
            if (token != null && token.path("Id").isTextual()) {
                String value = token.path("Id").asText();
                long expiresAt = token.path("ExpireTime").asLong();
                if (!value.isBlank() && expiresAt > clock.instant().getEpochSecond()) {
                    return new Token(value, expiresAt);
                }
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "阿里云 ISI 未返回有效的 NLS Token");
        } catch (ResourceAccessException error) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "连接阿里云 ISI Token 服务失败或超时");
        } catch (RestClientResponseException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "阿里云 ISI 获取 Token 失败（HTTP "
                    + error.getStatusCode().value() + "），请检查 AccessKey 和 RAM 的 nls:CreateToken 权限");
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "阿里云 ISI Token 服务响应无效");
        }
    }

    static String signedForm(String method, Map<String, String> params, String secret) {
        String canonical = new TreeMap<>(params).entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        String signingInput = method + "&%2F&" + encode(canonical);
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec((secret + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            String signature = Base64.getEncoder().encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
            return canonical + "&Signature=" + encode(signature);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("无法生成阿里云 ISI 请求签名", error);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }
}

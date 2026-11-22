package edu.zjut.interview.formal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

final class DeepSeekClient implements ModelClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json;
    private final URI url;
    private final String key;
    private final String model;

    DeepSeekClient(ObjectMapper json, URI url, String key, String model) {
        this.json = json;
        this.url = url;
        this.key = key;
        this.model = model;
    }

    @Override
    public boolean available() {
        return !key.isBlank() && !model.isBlank();
    }

    @Override
    public JsonNode completeJson(String systemPrompt, JsonNode input) {
        if (!available()) {
            throw new Unavailable("请配置大模型服务密钥");
        }
        try {
            String body = json.writeValueAsString(Map.of(
                    "model", model,
                    "max_tokens", 512,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(Map.of("role", "system", "content", systemPrompt + "\n仅输出 JSON。"),
                            Map.of("role", "user", "content", input.toString()))));
            HttpRequest request = HttpRequest.newBuilder(url)
                    .timeout(Duration.ofSeconds(25))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new Unavailable("大模型服务暂不可用");
            }
            JsonNode choice = json.readTree(response.body()).path("choices").path(0);
            JsonNode result = json.readTree(choice.path("message").path("content").asText());
            if (!"stop".equals(choice.path("finish_reason").asText()) || result == null || !result.isObject()) {
                throw new Unavailable("大模型返回了无效的 JSON 对象");
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Unavailable("大模型调用已中断");
        } catch (IOException e) {
            throw new Unavailable("大模型请求失败或返回内容无法解析");
        }
    }
}

package com.example.interview.voice;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IsiSpeechProviderTest {
    @Test
    void matchesAliyunPublishedSignatureVector() {
        var parameters = Map.of("AccessKeyId", "my_access_key_id", "Action", "CreateToken", "Format", "JSON",
                "RegionId", "cn-shanghai", "SignatureMethod", "HMAC-SHA1", "SignatureVersion", "1.0",
                "SignatureNonce", "b924c8c3-6d03-4c5d-ad36-d984d3116788", "Timestamp", "2019-04-18T08:32:31Z",
                "Version", "2019-02-28");
        assertThat(IsiTokenService.signedForm("GET", parameters, "my_access_key_secret"))
                .endsWith("&Signature=hHq4yNsPitlfDJ2L0nQPdugdEzM%3D");
    }

    @Test
    void cachesTokenAcrossTranscriptionAndSpeechAndRefreshesItAfterRejection() {
        String base = "https://isi.example.test";
        String tokenUrl = "https://token.example.test/";
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = builder.build();
        var clock = Clock.fixed(Instant.ofEpochSecond(1000), ZoneOffset.UTC);
        var tokens = new IsiTokenService(client, tokenUrl, "test-ak-id", "test-ak-secret", clock);
        var asr = mock(IsiRealtimeTranscriber.class);
        byte[] pcm = new byte[75 * 32000];
        when(asr.transcribe(eq("test-app-key"), eq("test-token"), same(pcm), any()))
                .thenReturn("第一句。\n最后一句。");
        var provider = new IsiSpeechProvider(asr, client, base, "test-app-key", tokens,
                (resource, type) -> pcm);
        server.expect(requestTo(tokenUrl)).andExpect(method(HttpMethod.POST)).andExpect(request -> {
            String form = ((MockClientHttpRequest) request).getBodyAsString();
            assertThat(form).contains("AccessKeyId=test-ak-id", "Action=CreateToken", "&Signature=")
                    .doesNotContain("test-ak-secret");
        }).andRespond(withSuccess("{\"Token\":{\"Id\":\"test-token\",\"ExpireTime\":1500}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(base + "/stream/v1/tts")).andExpect(header("X-NLS-Token", "test-token"))
                .andExpect(jsonPath("$.appkey").value("test-app-key")).andExpect(jsonPath("$.voice").value("xiaoyun"))
                .andRespond(withSuccess(new byte[]{73, 68, 51}, MediaType.parseMediaType("audio/mpeg")));
        server.expect(requestTo(base + "/stream/v1/tts"))
                .andRespond(withSuccess("{\"status\":40000001}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(tokenUrl))
                .andRespond(withSuccess("{\"Token\":{\"Id\":\"refreshed\",\"ExpireTime\":1800}}", MediaType.APPLICATION_JSON));

        assertThat(provider.transcribe(null, "audio/webm")).isEqualTo("第一句。\n最后一句。");
        assertThat(provider.speak("请回答")).containsExactly(73, 68, 51);
        assertThatThrownBy(() -> provider.speak("请回答")).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("40000001").hasMessageNotContaining("test-token");
        assertThat(tokens.get()).isEqualTo("refreshed");
        server.verify();
    }
}

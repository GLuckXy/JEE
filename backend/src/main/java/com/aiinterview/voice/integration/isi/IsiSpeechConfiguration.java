package com.aiinterview.voice.integration.isi;

import com.aiinterview.voice.service.SpeechProvider;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "training.speech.provider", havingValue = "isi")
class IsiSpeechConfiguration {
    @Bean
    @ConditionalOnMissingBean(SpeechProvider.class)
    SpeechProvider isiSpeechProvider(@Value("${training.speech.isi-access-key-id}") String accessKeyId,
                                     @Value("${training.speech.isi-access-key-secret}") String accessKeySecret,
                                     @Value("${training.speech.isi-app-key}") String appKey,
                                     @Value("${training.speech.isi-region}") String region,
                                     @Value("${training.speech.ffmpeg-path}") String ffmpeg) {
        region = region.strip();
        if (!Set.of("cn-shanghai", "cn-beijing", "cn-shenzhen").contains(region)) {
            throw new IllegalArgumentException("TRAINING_ISI_REGION 须为 cn-shanghai、cn-beijing 或 cn-shenzhen");
        }
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var tokens = new IsiTokenService(client(http, 10), "https://nls-meta.cn-shanghai.aliyuncs.com/",
                accessKeyId.strip(), accessKeySecret.strip(), Clock.systemUTC());
        String gateway = "nls-gateway-" + region + ".aliyuncs.com";
        return new IsiSpeechProvider(new IsiRealtimeTranscriber(http, "wss://" + gateway + "/ws/v1"), client(http, 60),
                "https://" + gateway, appKey.strip(), tokens,
                new FfmpegPcmConverter(ffmpeg, 300)::convert);
    }

    private static RestClient client(HttpClient http, long timeoutSeconds) {
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return RestClient.builder().requestFactory(factory).build();
    }
}

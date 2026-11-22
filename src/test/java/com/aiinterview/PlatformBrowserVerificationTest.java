package com.aiinterview;

import com.aiinterview.auth.AuthRequests;
import com.aiinterview.auth.AuthService;
import com.aiinterview.persistence.entity.User;
import com.aiinterview.persistence.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "VERIFY_PLATFORM_BROWSER", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.datasource.url=jdbc:h2:mem:platform_browser_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "training.speech.provider=none", "interview.ai.key="
})
class PlatformBrowserVerificationTest {
    @LocalServerPort int backendPort;
    @Autowired AuthService auth;
    @Autowired UserMapper users;

    @Test void navigatesSingleAppFromLoginThroughTrainingAndReports() throws Exception {
        String account = "itadmin" + UUID.randomUUID().toString().substring(0, 8);
        String password = "T9-" + UUID.randomUUID();
        var admin = auth.register(new AuthRequests.Register(account, password, "验收管理员"));
        users.update(null, new UpdateWrapper<User>().eq("id", admin.id()).set("role", "ADMIN"));

        int frontendPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            frontendPort = socket.getLocalPort();
        }
        Path frontend = Path.of("frontend").toAbsolutePath();
        Path viteLog = Path.of("target", "platform-browser-vite.log").toAbsolutePath();
        String origin = "http://127.0.0.1:" + frontendPort;
        ProcessBuilder vite = new ProcessBuilder("node", "node_modules/vite/bin/vite.js", "--host", "127.0.0.1", "--port",
                Integer.toString(frontendPort), "--strictPort").directory(frontend.toFile()).redirectErrorStream(true).redirectOutput(viteLog.toFile());
        vite.environment().put("API_TARGET", "http://127.0.0.1:" + backendPort);
        Process frontendServer = vite.start();
        try {
            assertThat(ready(origin, frontendServer)).withFailMessage(() -> "Vite did not start; see " + viteLog).isTrue();
            Path testLog = Path.of("target", "platform-browser-result.log").toAbsolutePath();
            ProcessBuilder browser = new ProcessBuilder("node", "tests/browser-platform.mjs").directory(frontend.toFile())
                    .redirectErrorStream(true).redirectOutput(testLog.toFile());
            browser.environment().put("FRONTEND_URL", origin);
            browser.environment().put("BOOTSTRAP_ADMIN_ACCOUNT", account);
            browser.environment().put("BOOTSTRAP_ADMIN_PASSWORD", password);
            Process exercise = browser.start();
            try {
                assertThat(exercise.waitFor(90, TimeUnit.SECONDS)).as("browser test completed; see %s", testLog).isTrue();
                assertThat(exercise.exitValue()).withFailMessage(() -> readLog(testLog)).isZero();
            } finally {
                exercise.destroy();
            }
        } finally {
            frontendServer.destroy();
        }
    }

    private boolean ready(String origin, Process vite) throws InterruptedException {
        var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofMillis(500)).build();
        var request = HttpRequest.newBuilder(URI.create(origin + "/api/content/jobs")).timeout(Duration.ofSeconds(2)).build();
        for (int attempt = 0; attempt < 16 && vite.isAlive(); attempt++) {
            try {
                if (client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 401) return true;
            } catch (IOException ignored) {
            }
            Thread.sleep(250);
        }
        return false;
    }

    private String readLog(Path path) {
        try { return Files.readString(path, StandardCharsets.UTF_8); }
        catch (IOException error) { return "See " + path; }
    }
}

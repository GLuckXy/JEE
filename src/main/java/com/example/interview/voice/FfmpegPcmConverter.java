package com.example.interview.voice;

import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.example.interview.api.ApiErrors.invalid;

final class FfmpegPcmConverter {
    static final int RATE = 16000;
    private static final Map<String, String> DEMUXERS = Map.of(
            "audio/webm", "matroska", "audio/ogg", "ogg", "audio/mp4", "mov", "audio/wav", "wav");

    private final String executable;
    private final int maxSeconds;

    FfmpegPcmConverter(String executable) {
        this(executable, 60);
    }

    FfmpegPcmConverter(String executable, int maxSeconds) {
        this.executable = executable;
        this.maxSeconds = maxSeconds;
    }

    byte[] convert(Resource recording, String mediaType) {
        String demuxer = DEMUXERS.get(mediaType);
        if (demuxer == null) throw invalid("这段录音的格式不支持转写");
        Path pcm = null;
        Process child = null;
        try {
            Path input = recording.getFile().toPath().toAbsolutePath();
            pcm = Files.createTempFile("training-asr-", ".pcm");
            try {
                // Decode one extra second to detect overlong recordings without sending partial answers.
                child = new ProcessBuilder(executable, "-nostdin", "-loglevel", "error", "-y",
                        "-protocol_whitelist", "file", "-f", demuxer, "-i", input.toString(),
                        "-map", "0:a:0", "-t", Integer.toString(maxSeconds + 1), "-ac", "1", "-ar", Integer.toString(RATE),
                        "-c:a", "pcm_s16le", "-f", "s16le", pcm.toString())
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            } catch (IOException error) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "无法启动 FFmpeg，请检查 TRAINING_FFMPEG_PATH 或系统 PATH");
            }
            if (!child.waitFor(15, TimeUnit.SECONDS)) {
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "录音转换超时，可用原录音重试");
            }
            long size = Files.size(pcm);
            if (child.exitValue() != 0 || size < 2) throw invalid("无法解码录音，请重新录音或手动填写");
            if (size > RATE * 2L * maxSeconds) {
                throw invalid("当前转写最多支持 " + maxSeconds + " 秒，原录音仍可回放，请缩短录音或手动填写");
            }
            return Files.readAllBytes(pcm);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "录音转换已中断，可用原录音重试");
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "录音转换文件读取或写入失败");
        } finally {
            if (child != null && child.isAlive()) {
                child.destroyForcibly();
                try {
                    child.waitFor(2, TimeUnit.SECONDS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            }
            if (pcm != null) {
                try {
                    Files.deleteIfExists(pcm);
                } catch (IOException error) {
                    LoggerFactory.getLogger(FfmpegPcmConverter.class).warn("Failed to remove temporary ASR audio");
                }
            }
        }
    }
}

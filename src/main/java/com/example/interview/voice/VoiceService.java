package com.example.interview.voice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.example.interview.api.ApiErrors.invalid;
import static com.example.interview.api.ApiErrors.missing;

@Service
public class VoiceService {
    public record Audio(String id, String mediaType, long byteCount, String playbackUrl) {}
    public record Playback(Resource resource, String mediaType) {}
    public record Status(boolean asrConfigured, boolean ttsConfigured) {}

    private record Format(String extension, String signature, int offset) {}
    private static final Map<String, Format> FORMATS = Map.of(
            "audio/webm", new Format("webm", "1a45dfa3", 0),
            "audio/ogg", new Format("ogg", "4f676753", 0),
            "audio/mp4", new Format("m4a", "66747970", 4),
            "audio/wav", new Format("wav", "52494646", 0));
    private static final long MAX_BYTES = 20L * 1024 * 1024;

    private final Path root;
    private final VoiceAssetMapper assets;
    private final SpeechProvider speech;

    VoiceService(@Value("${training.audio-dir}") String root, VoiceAssetMapper assets, ObjectProvider<SpeechProvider> speech) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.assets = assets;
        this.speech = speech.getIfAvailable(VoiceService::unconfiguredSpeech);
    }

    public String transcribeRecording(byte[] audio, String mediaType) {
        return speech.transcribeRecording(new ByteArrayResource(audio), mediaType);
    }

    private static SpeechProvider unconfiguredSpeech() {
        return new SpeechProvider() {
            public boolean asrConfigured() { return false; }
            public boolean ttsConfigured() { return false; }
            public String transcribe(Resource audio, String type) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "尚未配置语音转写服务");
            }
            public byte[] speak(String text) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "尚未配置语音播报服务");
            }
        };
    }

    public Status status() {
        return new Status(speech.asrConfigured(), speech.ttsConfigured());
    }

    Audio upload(String owner, MultipartFile file) {
        String type = Objects.toString(file.getContentType(), "").split(";", 2)[0].toLowerCase(Locale.ROOT);
        if ("audio/x-wav".equals(type)) type = "audio/wav";
        var format = FORMATS.get(type);
        if (format == null || file.getSize() < 12 || file.getSize() > MAX_BYTES) {
            throw invalid("录音需为 20 MB 以内的 WebM、Ogg、M4A 或 WAV");
        }
        var asset = new VoiceAssetMapper.Asset();
        asset.id = UUID.randomUUID().toString();
        asset.ownerId = owner;
        asset.mediaType = type;
        asset.fileExtension = format.extension();
        asset.byteCount = file.getSize();
        Path destination = filePath(asset);
        boolean saved = false;
        try (var input = new java.io.BufferedInputStream(file.getInputStream())) {
            input.mark(12);
            byte[] header = input.readNBytes(12);
            var hex = java.util.HexFormat.of();
            int offset = format.offset();
            if (header.length < 12 || !format.signature().equals(hex.formatHex(header, offset, offset + 4))
                    || ("audio/wav".equals(type) && !"57415645".equals(hex.formatHex(header, 8, 12)))) {
                throw invalid("录音内容与文件格式不符");
            }
            input.reset();
            Files.createDirectories(root);
            Files.copy(input, destination);
            assets.insert(asset);
            saved = true;
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "音频保存失败，可重新上传");
        } finally {
            if (!saved) {
                try {
                    Files.deleteIfExists(destination);
                } catch (IOException error) {
                    org.slf4j.LoggerFactory.getLogger(VoiceService.class).warn("Failed to remove incomplete audio {}", asset.id, error);
                }
            }
        }
        return new Audio(asset.id, type, asset.byteCount, "/api/voice/audio/" + asset.id);
    }

    public void requireOwned(String owner, String id) {
        if (id != null) playback(owner, id);
    }

    Playback playback(String owner, String id) {
        var asset = assets.selectById(id);
        if (asset == null || !owner.equals(asset.ownerId)) throw missing("找不到这段录音");
        var resource = new FileSystemResource(filePath(asset));
        if (!resource.isReadable()) throw missing("录音文件不可用");
        return new Playback(resource, asset.mediaType);
    }

    public String transcribe(String owner, String id) {
        if (id == null || id.isBlank()) throw invalid("请先上传录音");
        var audio = playback(owner, id);
        return speech.transcribeForUser(owner, audio.resource(), audio.mediaType());
    }

    byte[] speak(String text) {
        if (text == null || text.isBlank() || text.length() > 1000) {
            throw invalid("请输入 1 至 1000 字的播报内容");
        }
        return speech.speak(text.strip());
    }

    private Path filePath(VoiceAssetMapper.Asset asset) {
        return root.resolve(asset.id + "." + asset.fileExtension);
    }
}

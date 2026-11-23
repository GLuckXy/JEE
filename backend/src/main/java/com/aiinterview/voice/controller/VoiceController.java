package com.aiinterview.voice.controller;

import com.aiinterview.common.web.CurrentUser;
import com.aiinterview.voice.service.VoiceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/voice")
class VoiceController {
    record SpeechText(String text) {}

    private final VoiceService voice;
    private final CurrentUser user;

    VoiceController(VoiceService voice, CurrentUser user) {
        this.voice = voice;
        this.user = user;
    }

    @GetMapping("/status")
    VoiceService.Status status() {
        return voice.status();
    }

    @PostMapping("/audio")
    VoiceService.Audio upload(@RequestParam MultipartFile file, HttpServletRequest request, HttpServletResponse response) {
        var audio = voice.upload(user.requireId(request, response), file);
        return new VoiceService.Audio(audio.id(), audio.mediaType(), audio.byteCount(),
                request.getContextPath() + audio.playbackUrl());
    }

    @GetMapping("/audio/{id}")
    ResponseEntity<Resource> playback(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        var audio = voice.playback(user.requireId(request, response), id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(audio.mediaType()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(audio.resource());
    }

    @PostMapping("/audio/{id}/transcribe")
    Map<String, String> transcribe(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        return Map.of("text", voice.transcribe(user.requireId(request, response), id));
    }

    @PostMapping("/speech")
    ResponseEntity<byte[]> speak(@RequestBody SpeechText input, HttpServletRequest request, HttpServletResponse response) {
        user.requireId(request, response);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg"))
                .cacheControl(CacheControl.noStore()).body(voice.speak(input.text()));
    }
}

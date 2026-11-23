package com.aiinterview.voice.service;

import org.springframework.core.io.Resource;

public interface SpeechProvider {
    boolean asrConfigured();
    boolean ttsConfigured();
    String transcribe(Resource audio, String mediaType);
    default String transcribeRecording(Resource audio, String mediaType) {
        return transcribe(audio, mediaType);
    }
    default String transcribeForUser(String owner, Resource audio, String mediaType) {
        return transcribe(audio, mediaType);
    }
    byte[] speak(String text);
}

package com.aiinterview.formal.integration;

import com.aiinterview.formal.service.SpeechTranscriber;
import com.aiinterview.voice.service.VoiceService;
import org.springframework.stereotype.Component;

@Component
public class SpeechTranscriberAdapter implements SpeechTranscriber {
    private final VoiceService voice;

    public SpeechTranscriberAdapter(VoiceService voice) {
        this.voice = voice;
    }

    @Override public boolean available() { return voice.status().asrConfigured(); }

    @Override public String transcribe(byte[] audio, String mediaType) {
        return voice.transcribeRecording(audio, mediaType);
    }
}

package com.aiinterview.formal;

import com.example.interview.voice.VoiceService;
import edu.zjut.interview.formal.SpeechTranscriber;
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

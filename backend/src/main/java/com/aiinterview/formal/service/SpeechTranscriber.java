package com.aiinterview.formal.service;

public interface SpeechTranscriber {
    boolean available();

    // Return an empty string when recognition succeeded and detected no speech.
    String transcribe(byte[] audio, String mediaType);
}

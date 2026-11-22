package edu.zjut.interview.formal;

public interface SpeechTranscriber {
    boolean available();

    // Return an empty string when recognition succeeded and detected no speech.
    String transcribe(byte[] audio, String mediaType);
}

package edu.zjut.interview.formal;

import com.fasterxml.jackson.databind.JsonNode;

public interface ModelClient {
    default boolean available() { return true; }
    JsonNode completeJson(String systemPrompt, JsonNode input);

    final class Unavailable extends RuntimeException {
        public Unavailable(String message) {
            super(message);
        }
    }
}

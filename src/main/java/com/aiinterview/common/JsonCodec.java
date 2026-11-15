package com.aiinterview.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonCodec {
    private final ObjectMapper mapper;
    public JsonCodec(ObjectMapper mapper) { this.mapper=mapper; }
    public String write(Object value) {
        try { return mapper.writeValueAsString(value); } catch(JsonProcessingException e) { throw new IllegalStateException("JSON encoding failed"); }
    }
    public <T> T read(String value,Class<T> type) {
        if(value==null) return null;
        try { return mapper.readValue(value,type); } catch(JsonProcessingException e) { throw new IllegalStateException("Stored JSON cannot be decoded"); }
    }
    public com.fasterxml.jackson.databind.JsonNode tree(String value) { return read(value,com.fasterxml.jackson.databind.JsonNode.class); }
}

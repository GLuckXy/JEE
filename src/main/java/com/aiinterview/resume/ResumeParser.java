package com.aiinterview.resume;

import java.nio.file.Path;

public interface ResumeParser {
    ParsedResume parse(Path path,String mime) throws Exception;
    record Evidence(String field,String source,String confidence) {}
    record ParsedResume(String text,ResumePayload extracted,String parserVersion,
            java.util.List<Evidence> evidence,java.util.List<String> warnings) {}
}

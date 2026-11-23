package com.aiinterview.resume.parser;

import com.aiinterview.resume.dto.ResumePayload;
import java.nio.file.Path;

public interface ResumeParser {
    ParsedResume parse(Path path,String mime) throws Exception;
    record Evidence(String field,String source,String confidence) {}
    record ParsedResume(String text,ResumePayload extracted,String parserVersion,
            java.util.List<Evidence> evidence,java.util.List<String> warnings) {}
}

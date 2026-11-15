package com.aiinterview.resume;

import com.aiinterview.config.AppProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@Component
public class DocumentResumeParser implements ResumeParser {
    private final AppProperties config;
    public DocumentResumeParser(AppProperties config) { this.config=config; }
    @Override public ParsedResume parse(Path path,String mime) throws Exception {
        String text;
        if("application/pdf".equals(mime)) {
            try(var document=Loader.loadPDF(path.toFile())) {
                if(document.getNumberOfPages()>100) throw new IOException("Page limit");
                if(!document.getCurrentAccessPermission().canExtractContent()) throw new IOException("Extraction denied");
                var stripper=new PDFTextStripper() {
                    @Override protected void writeString(String value, java.util.List<org.apache.pdfbox.text.TextPosition> positions) throws IOException {
                        super.writeString(value,positions);
                        if(getOutput() instanceof StringWriter writer && writer.getBuffer().length()>config.maxTextChars()) throw new IOException("Text limit");
                    }
                };
                stripper.setSortByPosition(true);
                text=stripper.getText(document);
            }
        } else if("application/msword".equals(mime)) {
            try(var in=Files.newInputStream(path);var document=new HWPFDocument(in);var extractor=new WordExtractor(document)) { text=extractor.getText(); }
        } else {
            try(var in=Files.newInputStream(path);var document=new XWPFDocument(in);var extractor=new XWPFWordExtractor(document)) { text=extractor.getText(); }
        }
        text=text.replace("\u0000","").strip();
        if(text.isBlank()) throw new IOException("No extractable text");
        if(text.length()>config.maxTextChars()) throw new IOException("Text limit");
        return ResumeTextExtractor.extract(text);
    }
}


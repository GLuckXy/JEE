package com.aiinterview.file.storage;

import com.aiinterview.common.BusinessException;
import com.aiinterview.config.AppProperties;
import com.aiinterview.resume.parser.DocumentResumeParser;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.*;

class FileValidationTest {
    private final AppProperties config=new AppProperties("test-only-signing-material-for-file-validation",Duration.ofHours(2),
            Path.of("target","unit-private-files"),10485760,Set.of("pdf","doc","docx"),200000);
    @Test void rejectsTraversalInStorageKeys() {
        var store=new PrivateFileStore(config);
        for(String key:List.of("../resume.pdf","C:\\private.pdf","name.exe","123.pdf"))
            assertThatThrownBy(()->store.resolve(key)).isInstanceOf(IllegalStateException.class);
    }
    @Test void docxMustContainWordDocumentAndContentTypes() throws Exception {
        var store=new PrivateFileStore(config);
        try(var out=new ByteArrayOutputStream()) {
            try(var zip=new ZipOutputStream(out)) { zip.putNextEntry(new ZipEntry("random.txt"));zip.write("not word".getBytes());zip.closeEntry(); }
            var file=new MockMultipartFile("file","fake.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",out.toByteArray());
            assertThatThrownBy(()->store.validate(file)).isInstanceOf(BusinessException.class);
        }
    }
    @Test void highlyCompressedOversizedZipRejectedBeforeSaving() throws Exception {
        var store=new PrivateFileStore(config);
        try(var out=new ByteArrayOutputStream()) {
            try(var zip=new ZipOutputStream(out)) {
                zip.putNextEntry(new ZipEntry("word/document.xml"));
                byte[] block=new byte[1024*1024]; for(int i=0;i<41;i++) zip.write(block);zip.closeEntry();
                zip.putNextEntry(new ZipEntry("[Content_Types].xml"));zip.write("<Types/>".getBytes());zip.closeEntry();
            }
            var file=new MockMultipartFile("file","bomb.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",out.toByteArray());
            assertThatThrownBy(()->store.validate(file)).isInstanceOf(BusinessException.class);
        }
    }
    @Test void legacyWordContainerMustContainWordStream() throws Exception {
        var store=new PrivateFileStore(config);
        try(var fs=new org.apache.poi.poifs.filesystem.POIFSFileSystem();var out=new ByteArrayOutputStream()) {
            fs.createDocument(new ByteArrayInputStream("spreadsheet".getBytes()),"Workbook");fs.writeFilesystem(out);
            assertThatThrownBy(()->store.validate(new MockMultipartFile("file","fake.doc","application/msword",out.toByteArray())))
                    .isInstanceOf(BusinessException.class);
        }
    }
    @Test void corruptedLegacyWordRetainedAsParseFailure() throws Exception {
        var store=new PrivateFileStore(config);
        try(var fs=new org.apache.poi.poifs.filesystem.POIFSFileSystem();var out=new ByteArrayOutputStream()) {
            fs.createDocument(new ByteArrayInputStream(new byte[512]),"WordDocument");fs.writeFilesystem(out);
            var accepted=store.validate(new MockMultipartFile("file","corrupt.doc","application/msword",out.toByteArray()));
            String key=UUID.randomUUID()+".doc";store.write(key,accepted.bytes());
            assertThatThrownBy(()->new DocumentResumeParser(config).parse(store.resolve(key),accepted.mime())).isInstanceOf(Exception.class);
            assertThat(Files.exists(store.resolve(key))).isTrue();
        }
    }
}

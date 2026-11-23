package com.aiinterview.file.storage;

import com.aiinterview.common.BusinessException;
import com.aiinterview.config.AppProperties;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class PrivateFileStore {
    private final AppProperties config;
    public PrivateFileStore(AppProperties config) { this.config=config; }
    public record Validated(byte[] bytes,String originalName,String extension,String mime) {}
    public Validated validate(MultipartFile file) {
        if(file.isEmpty()) throw BusinessException.bad("FILE_TYPE_INVALID","文件不能为空");
        if(file.getSize()>config.maxUploadBytes()) throw new BusinessException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,"FILE_TOO_LARGE","文件超过上传限制");
        String name=Optional.ofNullable(file.getOriginalFilename()).orElse("");
        name=name.replace('\\','/'); name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]", "");
        if(name.length()>255 || name.isBlank()) throw BusinessException.bad("FILE_TYPE_INVALID","文件名不合法");
        String extension=name.contains(".") ? name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT) : "";
        String expected=switch(extension) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "";
        };
        String declared=Optional.ofNullable(file.getContentType()).orElse("").split(";")[0].trim().toLowerCase(Locale.ROOT);
        if(!config.allowedExtensions().contains(extension) || expected.isEmpty() || !expected.equals(declared))
            throw BusinessException.bad("FILE_TYPE_INVALID","扩展名或 MIME 不符合允许的 PDF/Word 类型");
        try(InputStream in=file.getInputStream()) {
            int readLimit=(int)Math.min(config.maxUploadBytes()+1,Integer.MAX_VALUE);
            byte[] bytes=in.readNBytes(readLimit);
            if(bytes.length>config.maxUploadBytes()) throw new BusinessException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,"FILE_TOO_LARGE","文件超过上传限制");
            FileMagic magic=FileMagic.valueOf(bytes);
            boolean actual=switch(extension) {
                case "pdf" -> magic==FileMagic.PDF;
                case "doc" -> magic==FileMagic.OLE2 && isWordOle(bytes);
                case "docx" -> magic==FileMagic.OOXML && isWordZip(bytes);
                default -> false;
            };
            if(!actual) throw BusinessException.bad("FILE_TYPE_INVALID","文件实际内容与类型不符");
            return new Validated(bytes,name,extension,expected);
        } catch(IOException e) { throw BusinessException.bad("FILE_TYPE_INVALID","无法读取或识别文件"); }
    }
    private boolean isWordOle(byte[] bytes) throws IOException {
        try(var fs=new org.apache.poi.poifs.filesystem.POIFSFileSystem(new ByteArrayInputStream(bytes))) { return fs.getRoot().hasEntry("WordDocument"); }
    }
    private boolean isWordZip(byte[] bytes) throws IOException {
        // Bound both entry count and total inflated bytes to reject ZIP bombs.
        try(var zip=new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes))) {
            boolean document=false, types=false; int count=0; long inflated=0;
            java.util.zip.ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                if(++count>2000) return false;
                if(entry.getName().equals("word/document.xml")) document=true;
                if(entry.getName().equals("[Content_Types].xml")) types=true;
                byte[] buffer=new byte[8192]; int n;
                while((n=zip.read(buffer))!=-1) { inflated+=n; if(inflated>40L*1024*1024) return false; }
            }
            return document && types;
        }
    }
    public void write(String key,byte[] bytes) throws IOException {
        Path root=config.storageRoot().toAbsolutePath().normalize(); Files.createDirectories(root);
        Path path=resolve(key);
        try(var out=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) { out.write(bytes); }
    }
    public Path resolve(String key) {
        if(key==null || !key.matches("[0-9a-f-]{36}\\.(pdf|doc|docx)")) throw new IllegalStateException("Invalid storage key");
        Path root=config.storageRoot().toAbsolutePath().normalize(); Path path=root.resolve(key).normalize();
        if(!path.startsWith(root)) throw new IllegalStateException("Storage path outside root");
        return path;
    }
}

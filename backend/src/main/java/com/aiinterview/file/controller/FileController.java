package com.aiinterview.file.controller;

import com.aiinterview.auth.service.UserAccessService;
import com.aiinterview.common.BusinessException;
import com.aiinterview.file.persistence.FileObject;
import com.aiinterview.file.persistence.FileObjectMapper;
import com.aiinterview.file.storage.PrivateFileStore;
import com.aiinterview.resume.persistence.Resume;
import com.aiinterview.resume.persistence.ResumeMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class FileController {
    private final FileObjectMapper files;
    private final ResumeMapper resumes;
    private final UserAccessService access;
    private final PrivateFileStore storage;
    public FileController(FileObjectMapper files,ResumeMapper resumes,UserAccessService access,PrivateFileStore storage) { this.files=files; this.resumes=resumes; this.access=access; this.storage=storage; }
    @GetMapping("/api/files/{id}") public ResponseEntity<Resource> download(@PathVariable String id) {
        String owner=access.requireStudent().id();
        FileObject file=files.selectOne(new QueryWrapper<FileObject>().eq("id",id).eq("owner_id",owner).eq("biz_type","RESUME").eq("storage_status","STORED"));
        if(file==null || resumes.selectCount(new QueryWrapper<Resume>().eq("id",file.bizId).eq("file_id",file.id).eq("user_id",owner).isNull("deleted_at"))!=1)
            throw BusinessException.missing("FILE_NOT_FOUND");
        var path=storage.resolve(file.storageKey);
        if(!Files.isRegularFile(path,java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw BusinessException.missing("FILE_NOT_FOUND");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mimeType)).contentLength(file.fileSize)
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(file.originalName,StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL,"no-store").header("X-Content-Type-Options","nosniff").body(new FileSystemResource(path));
    }
}

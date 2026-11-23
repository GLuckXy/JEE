package com.aiinterview.resume.service;

import com.aiinterview.auth.service.UserAccessService;
import com.aiinterview.common.BusinessException;
import com.aiinterview.common.JsonCodec;
import com.aiinterview.common.PageResult;
import com.aiinterview.common.Values;
import com.aiinterview.file.persistence.FileObject;
import com.aiinterview.file.persistence.FileObjectMapper;
import com.aiinterview.file.storage.PrivateFileStore;
import com.aiinterview.resume.dto.ResumePayload;
import com.aiinterview.resume.dto.ResumeRequests;
import com.aiinterview.resume.dto.ResumeViews;
import com.aiinterview.resume.parser.ResumeParser;
import com.aiinterview.resume.persistence.Resume;
import com.aiinterview.resume.persistence.ResumeMapper;
import com.aiinterview.resume.persistence.ResumeProject;
import com.aiinterview.resume.persistence.ResumeProjectMapper;
import com.aiinterview.resume.persistence.ResumeVersion;
import com.aiinterview.resume.persistence.ResumeVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumeService {
    private final ResumeMapper resumes;
    private final FileObjectMapper files;
    private final ResumeVersionMapper versions;
    private final ResumeProjectMapper projects;
    private final UserAccessService access;
    private final PrivateFileStore storage;
    private final ResumeParser parser;
    private final JsonCodec json;
    private final TransactionTemplate tx;
    public ResumeService(ResumeMapper resumes, FileObjectMapper files, ResumeVersionMapper versions,
            ResumeProjectMapper projects,UserAccessService access,PrivateFileStore storage,ResumeParser parser,
            JsonCodec json,org.springframework.transaction.PlatformTransactionManager manager) {
        this.resumes=resumes; this.files=files; this.versions=versions; this.projects=projects; this.access=access;
        this.storage=storage; this.parser=parser; this.json=json; this.tx=new TransactionTemplate(manager);
    }
    public ResumeViews.Detail upload(MultipartFile upload) {
        String owner=access.requireStudent().id(); var valid=storage.validate(upload);
        Resume r=new Resume(); r.id=Values.id(); r.userId=owner; r.fileId=Values.id();
        r.parseStatus="UPLOADED"; r.confirmStatus="DRAFT"; r.version=0; r.revision=0;
        r.draftPayload=json.write(ResumePayload.empty()); r.createdAt=Values.now(); r.updatedAt=r.createdAt;
        FileObject f=new FileObject(); f.id=r.fileId; f.ownerId=owner; f.storageKey=Values.id()+"."+valid.extension();
        f.originalName=valid.originalName(); f.mimeType=valid.mime(); f.fileSize=(long)valid.bytes().length;
        f.bizType="RESUME"; f.bizId=r.id; f.storageStatus="WRITING"; f.createdAt=r.createdAt;
        // Persist the tracking record before touching disk. Failures never leave an untracked private file.
        tx.executeWithoutResult(s -> { files.insert(f); resumes.insert(r); });
        try {
            storage.write(f.storageKey,valid.bytes());
            tx.executeWithoutResult(s -> files.update(null,new UpdateWrapper<FileObject>().eq("id",f.id).set("storage_status","STORED")));
        } catch(Exception e) {
            tx.executeWithoutResult(s -> {
                files.update(null,new UpdateWrapper<FileObject>().eq("id",f.id).set("storage_status","CLEANUP_REQUIRED"));
                resumes.update(null,new UpdateWrapper<Resume>().eq("id",r.id).set("parse_status","FAILED").set("failure_reason","原文件写入未完成，请重新上传"));
            });
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"FILE_STORAGE_FAILED","文件保存失败，已记录补偿状态，请重新上传");
        }
        parse(r.id,owner);
        return detail(r.id);
    }
    public PageResult<ResumeViews.Summary> list(int page,int size) {
        String owner=access.requireStudent().id(); int offset=PageResult.offset(page,size);
        var rows=resumes.selectList(new QueryWrapper<Resume>().eq("user_id",owner).isNull("deleted_at").orderByDesc("created_at").orderByAsc("id").last("LIMIT "+size+" OFFSET "+offset));
        return new PageResult<>(resumes.selectCount(new QueryWrapper<Resume>().eq("user_id",owner).isNull("deleted_at")),page,size,rows.stream().map(this::summary).toList());
    }
    public ResumeViews.Detail detail(String id) {
        Resume r=owned(id,false); ResumeVersion v=currentVersion(r);
        FileObject file=ownedFile(r);
        List<String> actions=new ArrayList<>(List.of("SAVE_DRAFT","CONFIRM"));
        if("STORED".equals(file.storageStatus)) { actions.add("DOWNLOAD_FILE"); if("DRAFT".equals(r.confirmStatus)) actions.add("RETRY_PARSE"); }
        if("CONFIRMED".equals(r.confirmStatus)) actions.add("SELECT_PROJECT");
        if(!"PARSING".equals(r.parseStatus) && !"UPLOADED".equals(r.parseStatus) && !"WRITING".equals(file.storageStatus)) actions.add("DELETE");
        return new ResumeViews.Detail(summary(r),json.tree(r.rawParseResult),json.read(r.draftPayload,ResumePayload.class),
                v==null?null:json.read(v.confirmedPayload,ResumePayload.class),List.copyOf(actions));
    }
    @Transactional
    public void delete(String id,ResumeRequests.Delete request) {
        Resume r=owned(id,true); checkRevision(r,request.expectedRevision());
        FileObject file=ownedFile(r);
        if("PARSING".equals(r.parseStatus) || "UPLOADED".equals(r.parseStatus) || "WRITING".equals(file.storageStatus))
            throw BusinessException.conflict("RESUME_BUSY","文件写入或解析尚未结束，请稍后删除");
        resumes.update(null,new UpdateWrapper<Resume>().eq("id",id).eq("user_id",r.userId).isNull("deleted_at")
                .set("deleted_at",Values.now()).set("revision",r.revision+1).set("updated_at",Values.now()));
    }
    @Transactional
    public ResumeViews.Detail saveDraft(String id,ResumeRequests.Draft request) {
        Resume r=owned(id,true); checkRevision(r,request.expectedRevision());
        resumes.update(null,new UpdateWrapper<Resume>().eq("id",id).eq("user_id",r.userId)
                .set("draft_payload",json.write(request.payload())).set("confirm_status","DRAFT")
                .set("revision",r.revision+1).set("updated_at",Values.now()));
        return detail(id);
    }
    @Transactional
    public ResumeViews.Detail confirm(String id,ResumeRequests.Confirm request) {
        Resume r=owned(id,true); checkRevision(r,request.expectedRevision());
        if("CONFIRMED".equals(r.confirmStatus)) throw BusinessException.conflict("RESUME_ALREADY_CONFIRMED","请先保存修改草稿再确认");
        ResumePayload payload=json.read(r.draftPayload,ResumePayload.class); validateConfirmation(payload);
        ResumeVersion v=new ResumeVersion(); v.id=Values.id(); v.resumeId=id; v.version=r.version+1; v.confirmedPayload=json.write(payload); v.confirmedAt=Values.now();
        versions.insert(v); int order=0;
        for(var p:payload.projects()) {
            ResumeProject item=new ResumeProject(); item.id=Values.id(); item.resumeId=id; item.versionId=v.id;
            item.name=p.name(); item.goal=p.goal(); item.responsibility=p.responsibility(); item.techStack=json.write(p.techStack());
            item.difficulty=p.difficulty(); item.solution=p.solution(); item.result=p.result(); item.improvement=p.improvement(); item.sortNo=order++;
            projects.insert(item);
        }
        resumes.update(null,new UpdateWrapper<Resume>().eq("id",id).eq("user_id",r.userId)
                .set("confirm_status","CONFIRMED").set("version",v.version).set("revision",r.revision+1)
                .set("confirmed_at",v.confirmedAt).set("updated_at",Values.now()));
        return detail(id);
    }
    public ResumeViews.Detail retry(String id) { String owner=access.requireStudent().id(); parse(id,owner); return detail(id); }
    private void parse(String id,String owner) {
        // A committed PARSING marker survives process interruption; retry accepts this state.
        record ParseTarget(String fileId,String key,String mime) {}
        ParseTarget target=tx.execute(s -> {
            Resume r=ownedBy(id,owner,true);
            if(!"DRAFT".equals(r.confirmStatus)) throw BusinessException.conflict("RESUME_ALREADY_CONFIRMED","已确认简历不可重新解析，请先保存草稿");
            if("PARSING".equals(r.parseStatus) && r.updatedAt.isAfter(Values.now().minusMinutes(5)))
                throw BusinessException.conflict("RESUME_PARSE_IN_PROGRESS","解析处理中，请稍后重试");
            FileObject f=ownedFile(r);
            if(!"STORED".equals(f.storageStatus)) throw BusinessException.conflict("FILE_NOT_AVAILABLE","文件未保存完成，请重新上传");
            resumes.update(null,new UpdateWrapper<Resume>().eq("id",id).set("parse_status","PARSING").set("failure_reason",null).set("updated_at",Values.now()));
            return new ParseTarget(f.id,f.storageKey,f.mimeType);
        });
        ResumeParser.ParsedResume parsed=null;
        try { parsed=parser.parse(storage.resolve(target.key()),target.mime()); } catch(Exception ignored) { /* Never expose parser text, physical paths or document content in errors. */ }
        ResumeParser.ParsedResume result=parsed;
        tx.executeWithoutResult(s -> {
            Resume r=ownedBy(id,owner,true);
            UpdateWrapper<Resume> update=new UpdateWrapper<Resume>().eq("id",id).set("updated_at",Values.now());
            if(result==null) update.set("parse_status","FAILED").set("failure_reason","未能提取有效文本；可能为扫描件、加密文件、损坏或超出解析限制，可重试或手动补充");
            else {
                update.set("parse_status","SUCCESS").set("raw_parse_result",json.write(result)).set("failure_reason",null);
                // A retry must not overwrite saved manual edits, even when an upload is still parsing.
                if(r.revision==0) update.set("draft_payload",json.write(result.extracted()));
            }
            resumes.update(null,update);
        });
    }
    public List<ResumeViews.Version> history(String id) {
        Resume r=owned(id,false);
        return versions.selectList(new QueryWrapper<ResumeVersion>().eq("resume_id",r.id).orderByDesc("version")).stream()
                .map(v->new ResumeViews.Version(v.id,v.version,json.read(v.confirmedPayload,ResumePayload.class),v.confirmedAt)).toList();
    }
    public List<ResumeViews.Project> projectList(String id) {
        Resume r=confirmed(id); ResumeVersion v=currentVersion(r);
        return projects.selectList(new QueryWrapper<ResumeProject>().eq("resume_id",r.id).eq("version_id",v.id).orderByAsc("sort_no"))
                .stream().map(p->projectView(p,v.version)).toList();
    }
    public ResumeViews.Project projectDetail(String projectId) {
        // Scope the SQL through owned resumes, rather than fetching an arbitrary project by primary key.
        String owner=access.requireStudent().id();
        ResumeProject p=projects.selectOne(new QueryWrapper<ResumeProject>().eq("id",projectId)
                .exists("SELECT 1 FROM resume r WHERE r.id = resume_project.resume_id AND r.user_id = {0} AND r.deleted_at IS NULL",owner));
        if(p==null) throw BusinessException.missing("PROJECT_NOT_FOUND");
        return confirmedProject(p.resumeId,projectId);
    }
    List<ResumeViews.Project> confirmedProjects() {
        String owner = access.requireStudent().id();
        var owned = resumes.selectList(new QueryWrapper<Resume>().eq("user_id", owner).isNull("deleted_at")
                .eq("confirm_status", "CONFIRMED").orderByDesc("confirmed_at", "id"));
        return owned.stream().flatMap(resume -> projectList(resume.id).stream()).toList();
    }
    public ResumeViews.SelectedProject select(String id,ResumeRequests.Selection request) {
        ResumeViews.Project project=confirmedProject(id,request.projectId());
        if(project.version()!=request.version()) throw BusinessException.conflict("VERSION_CONFLICT","确认版本已变化，请重新选择");
        return new ResumeViews.SelectedProject(id,project.id(),project.version(),project);
    }
    public ResumeViews.Version confirmedResume(String id) {
        Resume r=confirmed(id); ResumeVersion v=currentVersion(r);
        return new ResumeViews.Version(v.id,v.version,json.read(v.confirmedPayload,ResumePayload.class),v.confirmedAt);
    }
    public ResumeViews.Project confirmedProject(String resumeId,String projectId) {
        Resume r=confirmed(resumeId); ResumeVersion v=currentVersion(r);
        ResumeProject p=projects.selectOne(new QueryWrapper<ResumeProject>().eq("id",projectId).eq("resume_id",r.id).eq("version_id",v.id));
        if(p==null) throw BusinessException.missing("PROJECT_NOT_CONFIRMED");
        return projectView(p,v.version);
    }
    private Resume confirmed(String id) {
        Resume r=owned(id,false);
        if(!"CONFIRMED".equals(r.confirmStatus)) throw BusinessException.conflict("RESUME_NOT_CONFIRMED","简历尚未确认");
        return r;
    }
    private Resume owned(String id,boolean lock) { return ownedBy(id,access.requireStudent().id(),lock); }
    private Resume ownedBy(String id,String owner,boolean lock) {
        QueryWrapper<Resume> query=new QueryWrapper<Resume>().eq("id",id).eq("user_id",owner).isNull("deleted_at");
        if(lock) query.last("FOR UPDATE");
        Resume r=resumes.selectOne(query); if(r==null) throw BusinessException.missing("RESUME_NOT_FOUND"); return r;
    }
    private FileObject ownedFile(Resume r) {
        FileObject f=files.selectOne(new QueryWrapper<FileObject>().eq("id",r.fileId).eq("owner_id",r.userId).eq("biz_id",r.id).eq("biz_type","RESUME"));
        if(f==null) throw BusinessException.missing("FILE_NOT_FOUND"); return f;
    }
    private ResumeVersion currentVersion(Resume r) {
        if(r.version==0) return null;
        return versions.selectOne(new QueryWrapper<ResumeVersion>().eq("resume_id",r.id).eq("version",r.version));
    }
    private ResumeViews.Summary summary(Resume r) {
        FileObject f=ownedFile(r);
        return new ResumeViews.Summary(r.id,r.fileId,f.originalName,r.parseStatus,r.confirmStatus,r.version,r.revision,r.failureReason,r.createdAt,r.confirmedAt);
    }
    @SuppressWarnings("unchecked") private ResumeViews.Project projectView(ResumeProject p,int version) {
        return new ResumeViews.Project(p.id,p.resumeId,version,p.name,p.goal,p.responsibility,json.read(p.techStack,List.class),p.difficulty,p.solution,p.result,p.improvement,p.sortNo);
    }
    private static void checkRevision(Resume r,int revision) {
        if(r.revision!=revision) throw BusinessException.conflict("VERSION_CONFLICT","资料已变化，请重新获取后提交");
    }
    private static void validateConfirmation(ResumePayload p) {
        if(p==null || p.projects()==null || p.projects().isEmpty()) throw BusinessException.bad("RESUME_CONFIRMATION_INVALID","请至少填写一个项目");
        for(int i=0;i<p.projects().size();i++) {
            var project=p.projects().get(i);
            if(project==null || project.name()==null || project.name().isBlank() || project.responsibility()==null || project.responsibility().isBlank())
                throw BusinessException.bad("RESUME_CONFIRMATION_INVALID","projects["+i+"] 的 name 和 responsibility 必填");
        }
    }
}

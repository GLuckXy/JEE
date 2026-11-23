package com.aiinterview;

import com.aiinterview.auth.model.CurrentUser;
import com.aiinterview.auth.persistence.AuthSessionMapper;
import com.aiinterview.common.BusinessException;
import com.aiinterview.file.persistence.FileObject;
import com.aiinterview.file.persistence.FileObjectMapper;
import com.aiinterview.file.storage.PrivateFileStore;
import com.aiinterview.resume.persistence.Resume;
import com.aiinterview.resume.persistence.ResumeMapper;
import com.aiinterview.resume.persistence.ResumeProject;
import com.aiinterview.resume.persistence.ResumeProjectMapper;
import com.aiinterview.resume.persistence.ResumeVersion;
import com.aiinterview.resume.persistence.ResumeVersionMapper;
import com.aiinterview.resume.service.ResumeQueryService;
import com.aiinterview.user.persistence.User;
import com.aiinterview.user.persistence.UserAuditLog;
import com.aiinterview.user.persistence.UserAuditLogMapper;
import com.aiinterview.user.persistence.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModuleIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserMapper users;
    @Autowired AuthSessionMapper sessions;
    @Autowired ResumeMapper resumes;
    @Autowired ResumeVersionMapper versions;
    @Autowired FileObjectMapper files;
    @Autowired UserAuditLogMapper audit;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired ResumeQueryService query;
    @MockitoSpyBean ResumeProjectMapper projectMapper;
    @MockitoSpyBean PrivateFileStore storage;
    private static final String PASSWORD="StudyPass2026";
    record Actor(String id,String account,String token) {}
    private String account() { return "s"+UUID.randomUUID().toString().replace("-", "").substring(0,11); }
    private JsonNode data(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()).path("data"); }
    private Actor student() throws Exception {
        String account=account();
        var result=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("account",account,"password",PASSWORD,"nickname","学生"))))
                .andExpect(status().isOk()).andReturn();
        return new Actor(data(result).path("id").asText(),account,login(account));
    }
    private String login(String account) throws Exception {
        return data(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("account",account,"password",PASSWORD)))).andExpect(status().isOk()).andReturn()).path("accessToken").asText();
    }
    private Actor admin() throws Exception {
        Actor a=student(); users.update(null,new UpdateWrapper<User>().eq("id",a.id()).set("role","ADMIN"));
        return new Actor(a.id(),a.account(),login(a.account()));
    }
    private byte[] pdf(boolean text) throws Exception {
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()) {
            var page=new PDPage(); doc.addPage(page);
            if(text) try(var canvas=new PDPageContentStream(doc,page)) {
                canvas.beginText(); canvas.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12); canvas.newLineAtOffset(50,700);
                for(String line:List.of("Education","Test University","Skills","Java, SQL","Projects","Course Platform")) {
                    canvas.showText(line); canvas.newLineAtOffset(0,-20);
                }
                canvas.endText();
            }
            doc.save(out); return out.toByteArray();
        }
    }
    private JsonNode upload(Actor actor,byte[] bytes,String name,String mime) throws Exception {
        return data(mvc.perform(multipart("/api/resumes").file(new MockMultipartFile("file",name,mime,bytes))
                .header("Authorization","Bearer "+actor.token())).andExpect(status().isOk()).andReturn());
    }
    private Map<String,Object> payload(String name,String responsibility) {
        return Map.of("education",List.of(),"skills",List.of("Java"),"internships",List.of(),
                "projects",List.of(Map.of("name",name,"goal","课程练习","responsibility",responsibility,"techStack",List.of("Java","MySQL"),
                        "difficulty","","solution","","result","","improvement","")));
    }
    private JsonNode draft(Actor a,String id,int revision,String name,String duty) throws Exception {
        return data(mvc.perform(put("/api/resumes/"+id+"/parsed-data").header("Authorization","Bearer "+a.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedRevision",revision,"payload",payload(name,duty)))))
                .andExpect(status().isOk()).andReturn());
    }
    private JsonNode confirm(Actor a,String id,int revision) throws Exception {
        return data(mvc.perform(post("/api/resumes/"+id+"/confirm").header("Authorization","Bearer "+a.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedRevision",revision))))
                .andExpect(status().isOk()).andReturn());
    }
    @Test void registrationStoresHashAndStudentRole() throws Exception {
        Actor a=student(); User u=users.selectById(a.id());
        assertThat(u.role).isEqualTo("STUDENT"); assertThat(u.passwordHash).doesNotContain(PASSWORD); assertThat(encoder.matches(PASSWORD,u.passwordHash)).isTrue();
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+a.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist()).andExpect(jsonPath("$.data.account").value(a.account()));
    }
    @Test void duplicateAndAccountLengthAndPasswordRules() throws Exception {
        Actor a=student();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("account",a.account(),"password",PASSWORD,"nickname","学生"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));
        for(String invalid:List.of("short77","abcdefghijklmnopq","bad-name!")) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("account",invalid,"password",PASSWORD,"nickname","学生"))))
                    .andExpect(status().isBadRequest());
        }
        for(String password:List.of("12345678","abcdefgh","Abc1234","测".repeat(24)+"Ab1"))
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("account",account(),"password",password,"nickname","学生"))))
                    .andExpect(status().isBadRequest());
    }
    @Test void massAssignmentAndUnknownProfileFieldsRejected() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("account",account(),"password",PASSWORD,"nickname","学生","role","ADMIN"))))
                .andExpect(status().isBadRequest());
        Actor a=student();
        mvc.perform(put("/api/users/me").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"new\",\"role\":\"ADMIN\"}")).andExpect(status().isBadRequest());
        assertThat(users.selectById(a.id()).role).isEqualTo("STUDENT");
    }
    @Test void validProfileUpdateAndStudentCannotReadAdmin() throws Exception {
        Actor a=student();
        mvc.perform(put("/api/users/me").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"祥\",\"email\":\"student@example.test\",\"school\":\"测试学校\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nickname").value("祥"));
        mvc.perform(get("/api/admin/users").header("Authorization","Bearer "+a.token())).andExpect(status().isForbidden());
        mvc.perform(get("/api/users/"+a.id()).header("Authorization","Bearer "+a.token())).andExpect(status().isNotFound());
    }
    @Test void authenticationWrongMissingInvalidExpiredAndLogout() throws Exception {
        Actor a=student();
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization","Bearer bad")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("account",a.account(),"password","WrongPass123"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        String expired=jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder()
                .issuer("ai-interview").subject(a.id()).id("expired").issuedAt(Instant.now().minusSeconds(3600)).expiresAt(Instant.now().minusSeconds(600)).build())).getTokenValue();
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+expired)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").header("Authorization","Bearer "+a.token())).andExpect(status().isOk());
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+a.token())).andExpect(status().isUnauthorized());
    }
    @Test void disablingUserBlocksOldTokenAndLoginAndRecordsAudit() throws Exception {
        Actor a=student(), admin=admin();
        mvc.perform(put("/api/admin/users/"+a.id()+"/status").header("Authorization","Bearer "+admin.token()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+a.token())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("account",a.account(),"password",PASSWORD))))
                .andExpect(status().isForbidden());
        assertThat(audit.selectCount(new QueryWrapper<UserAuditLog>().eq("target_id",a.id()))).isEqualTo(1);
    }
    @Test void pdfAndDocxUploadExtractActualText() throws Exception {
        Actor a=student(); JsonNode first=upload(a,pdf(true),"resume.pdf","application/pdf");
        assertThat(first.path("summary").path("parseStatus").asText()).isEqualTo("SUCCESS");
        assertThat(first.path("rawParseResult").path("text").asText()).contains("Test University");
        assertThat(first.path("draft").path("skills").toString()).contains("Java");
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Skills"); doc.createParagraph().createRun().setText("Java, SQL"); doc.write(out);
            JsonNode second=upload(a,out.toByteArray(),"resume.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            assertThat(second.path("summary").path("parseStatus").asText()).isEqualTo("SUCCESS");
        }
        mvc.perform(get("/api/resumes").header("Authorization","Bearer "+a.token())).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
    }
    @Test void invalidSpoofedEmptyAndOversizedFilesRejected() throws Exception {
        Actor a=student(); long before=files.selectCount(null);
        for(var file:List.of(new MockMultipartFile("file","x.exe","application/octet-stream",new byte[]{1}),
                new MockMultipartFile("file","x.pdf","application/pdf","fake content".getBytes()),
                new MockMultipartFile("file","x.pdf","application/msword",pdf(true)),
                new MockMultipartFile("file","x.pdf","application/pdf",new byte[0])))
            mvc.perform(multipart("/api/resumes").file(file).header("Authorization","Bearer "+a.token())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/resumes").file(new MockMultipartFile("file","x.pdf","application/pdf",new byte[10485761]))
                .header("Authorization","Bearer "+a.token())).andExpect(status().isPayloadTooLarge());
        assertThat(files.selectCount(null)).isEqualTo(before);
    }
    @Test void scanFailureRetainsFileAndAllowsManualConfirmation() throws Exception {
        Actor a=student(); JsonNode uploaded=upload(a,pdf(false),"scan.pdf","application/pdf");
        String id=uploaded.path("summary").path("id").asText(),fileId=uploaded.path("summary").path("fileId").asText();
        assertThat(uploaded.path("summary").path("parseStatus").asText()).isEqualTo("FAILED");
        mvc.perform(get("/api/files/"+fileId).header("Authorization","Bearer "+a.token())).andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
        draft(a,id,0,"课程项目","本人实现用户模块"); JsonNode confirmed=confirm(a,id,1);
        assertThat(confirmed.path("summary").path("confirmStatus").asText()).isEqualTo("CONFIRMED");
        assertThat(confirmed.path("summary").path("parseStatus").asText()).isEqualTo("FAILED");
        assertThat(confirmed.path("summary").path("version").asInt()).isEqualTo(1);
    }
    @Test void unconfirmedAndIncompleteProjectsCannotBeUsed() throws Exception {
        Actor a=student(); String id=upload(a,pdf(false),"scan.pdf","application/pdf").path("summary").path("id").asText();
        mvc.perform(get("/api/resumes/"+id+"/projects").header("Authorization","Bearer "+a.token()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESUME_NOT_CONFIRMED"));
        mvc.perform(post("/api/resumes/"+id+"/confirm").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESUME_CONFIRMATION_INVALID"));
        draft(a,id,0,"项目","");
        mvc.perform(post("/api/resumes/"+id+"/confirm").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":1}"))
                .andExpect(status().isBadRequest());
    }
    @Test void confirmedProjectSelectionAndHorizontalAccessAreEnforced() throws Exception {
        Actor a=student(),b=student(),admin=admin();
        var uploaded=upload(a,pdf(false),"scan.pdf","application/pdf"); String id=uploaded.path("summary").path("id").asText(),fileId=uploaded.path("summary").path("fileId").asText();
        draft(a,id,0,"平台","实现后端"); confirm(a,id,1);
        String projectId=data(mvc.perform(get("/api/resumes/"+id+"/projects").header("Authorization","Bearer "+a.token())).andExpect(status().isOk()).andReturn()).get(0).path("id").asText();
        mvc.perform(post("/api/resumes/"+id+"/select-project").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("projectId",projectId,"version",1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.snapshot.responsibility").value("实现后端"));
        mvc.perform(get("/api/resume-projects/"+projectId).header("Authorization","Bearer "+a.token())).andExpect(status().isOk());
        for(String path:List.of("/api/resumes/"+id,"/api/resumes/"+id+"/projects","/api/resumes/"+id+"/versions","/api/resume-projects/"+projectId,"/api/files/"+fileId)) {
            mvc.perform(get(path).header("Authorization","Bearer "+b.token())).andExpect(status().isNotFound());
            mvc.perform(get(path).header("Authorization","Bearer "+admin.token())).andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/resumes/"+id+"/parsed-data").header("Authorization","Bearer "+b.token()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("expectedRevision",2,"payload",payload("伪造","不允许"))))).andExpect(status().isNotFound());
        mvc.perform(post("/api/resumes/"+id+"/confirm").header("Authorization","Bearer "+b.token()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":2}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/resumes").header("Authorization","Bearer "+b.token())).andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void editingCreatesNewVersionAndOldSnapshotIsImmutable() throws Exception {
        Actor a=student(); String id=upload(a,pdf(false),"scan.pdf","application/pdf").path("summary").path("id").asText();
        draft(a,id,0,"旧项目","旧职责"); confirm(a,id,1);
        var old=versions.selectOne(new QueryWrapper<ResumeVersion>().eq("resume_id",id).eq("version",1));
        draft(a,id,2,"新项目","新职责");
        mvc.perform(get("/api/resumes/"+id+"/projects").header("Authorization","Bearer "+a.token())).andExpect(status().isConflict());
        confirm(a,id,3);
        assertThat(versions.selectById(old.id).confirmedPayload).isEqualTo(old.confirmedPayload);
        assertThat(versions.selectCount(new QueryWrapper<ResumeVersion>().eq("resume_id",id))).isEqualTo(2);
        mvc.perform(get("/api/resumes/"+id+"/projects").header("Authorization","Bearer "+a.token())).andExpect(jsonPath("$.data[0].name").value("新项目"));
        mvc.perform(get("/api/resumes/"+id+"/versions").header("Authorization","Bearer "+a.token())).andExpect(jsonPath("$.data.length()").value(2));
        var reupload=upload(a,pdf(false),"scan.pdf","application/pdf"); assertThat(reupload.path("summary").path("id").asText()).isNotEqualTo(id);
    }
    @Test void staleRevisionAndRepeatedConfirmationRejected() throws Exception {
        Actor a=student(); String id=upload(a,pdf(false),"scan.pdf","application/pdf").path("summary").path("id").asText(); draft(a,id,0,"项目","职责");
        mvc.perform(post("/api/resumes/"+id+"/confirm").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        confirm(a,id,1);
        mvc.perform(post("/api/resumes/"+id+"/confirm").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":2}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESUME_ALREADY_CONFIRMED"));
    }
    @Test void retryDoesNotOverwriteManualDraft() throws Exception {
        Actor a=student(); String id=upload(a,pdf(true),"resume.pdf","application/pdf").path("summary").path("id").asText(); draft(a,id,0,"人工项目","人工职责");
        mvc.perform(post("/api/resumes/"+id+"/retry-parse").header("Authorization","Bearer "+a.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draft.projects[0].name").value("人工项目"));
    }
    @Test void confirmationFailureRollsBackVersionAndProjects() throws Exception {
        Actor a=student(); String id=upload(a,pdf(false),"scan.pdf","application/pdf").path("summary").path("id").asText(); draft(a,id,0,"项目","职责");
        doThrow(new IllegalStateException("simulated insertion failure")).when(projectMapper).insert(any(ResumeProject.class));
        mvc.perform(post("/api/resumes/"+id+"/confirm").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":1}"))
                .andExpect(status().isInternalServerError());
        assertThat(versions.selectCount(new QueryWrapper<ResumeVersion>().eq("resume_id",id))).isZero();
        assertThat(resumes.selectById(id).confirmStatus).isEqualTo("DRAFT");
    }
    @Test void storageFailureIsTrackedWithoutAutomaticDeletion() throws Exception {
        Actor a=student(); doThrow(new IOException("simulated storage failure")).when(storage).write(anyString(),any(byte[].class));
        mvc.perform(multipart("/api/resumes").file(new MockMultipartFile("file","x.pdf","application/pdf",pdf(true))).header("Authorization","Bearer "+a.token()))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("FILE_STORAGE_FAILED"));
        FileObject f=files.selectOne(new QueryWrapper<FileObject>().eq("owner_id",a.id())); assertThat(f.storageStatus).isEqualTo("CLEANUP_REQUIRED");
        assertThat(resumes.selectById(f.bizId).parseStatus).isEqualTo("FAILED");
    }
    @Test void administratorResponseContainsNoPrivateResumeOrPassword() throws Exception {
        Actor a=student(),admin=admin(); upload(a,pdf(false),"private.pdf","application/pdf");
        var result=mvc.perform(get("/api/admin/users/"+a.id()).header("Authorization","Bearer "+admin.token())).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("passwordHash","private.pdf","storageKey","rawParseResult",PASSWORD);
        mvc.perform(put("/api/admin/users/"+a.id()).header("Authorization","Bearer "+admin.token()).contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"新昵称\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void unauthenticatedInternalServiceAndInvalidPaginationRejected() throws Exception {
        assertThatThrownBy(()->query.getConfirmedResume("anything")).isInstanceOf(BusinessException.class);
        Actor a=student(); mvc.perform(get("/api/resumes?page=0&size=1000").header("Authorization","Bearer "+a.token())).andExpect(status().isBadRequest());
    }
    @Test void accountBoundariesAndCaseInsensitiveUniqueness() throws Exception {
        String prefix=UUID.randomUUID().toString().replace("-", "");
        for(String name:List.of(prefix.substring(0,8),prefix.substring(0,16))) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("account",name,"password",PASSWORD,"nickname","学生")))).andExpect(status().isOk());
            login(name.toUpperCase(Locale.ROOT));
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("account",name.toUpperCase(Locale.ROOT),"password",PASSWORD,"nickname","学生"))))
                    .andExpect(status().isConflict());
        }
    }
    @Test void concurrentConfirmationsCreateOnlyOneVersion() throws Exception {
        Actor a=student(); String id=upload(a,pdf(false),"scan.pdf","application/pdf").path("summary").path("id").asText(); draft(a,id,0,"项目","职责");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var ready=new java.util.concurrent.CountDownLatch(2); var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> request=()-> {
                ready.countDown(); start.await();
                return mvc.perform(post("/api/resumes/"+id+"/confirm").header("Authorization","Bearer "+a.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":1}")).andReturn().getResponse().getStatus();
            };
            var first=pool.submit(request);var second=pool.submit(request); ready.await();start.countDown();
            assertThat(List.of(first.get(),second.get())).containsExactlyInAnyOrder(200,409);
        }
        assertThat(versions.selectCount(new QueryWrapper<ResumeVersion>().eq("resume_id",id))).isEqualTo(1);
    }
    @Test void trainingServiceRequiresOwnedCurrentConfirmedProject() throws Exception {
        Actor a=student(),b=student();
        String id=upload(a,pdf(false),"scan.pdf","application/pdf").path("summary").path("id").asText();draft(a,id,0,"项目","职责");confirm(a,id,1);
        String otherId=upload(a,pdf(false),"second.pdf","application/pdf").path("summary").path("id").asText();draft(a,otherId,0,"第二项目","第二职责");confirm(a,otherId,1);
        String pid=projectMapper.selectOne(new QueryWrapper<ResumeProject>().eq("resume_id",id)).id;
        try {
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(new CurrentUser(a.id(),a.account(),"STUDENT","test"),null,List.of()));
            assertThat(query.getConfirmedProject(id,pid).responsibility()).isEqualTo("职责");
            assertThat(query.getConfirmedResume(id).version()).isEqualTo(1);
            assertThatThrownBy(()->query.getConfirmedProject(otherId,pid)).isInstanceOf(BusinessException.class);
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(new CurrentUser(b.id(),b.account(),"STUDENT","test"),null,List.of()));
            assertThatThrownBy(()->query.getConfirmedProject(id,pid)).isInstanceOf(BusinessException.class);
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
    @Test void softDeleteDeniesEveryReadAndRetainsFileAndHistory() throws Exception {
        Actor a=student(),b=student();
        var uploaded=upload(a,pdf(true),"delete-check.pdf","application/pdf");
        String id=uploaded.path("summary").path("id").asText(),fid=uploaded.path("summary").path("fileId").asText();
        draft(a,id,0,"删除验证项目","本人职责");confirm(a,id,1);
        String pid=projectMapper.selectOne(new QueryWrapper<ResumeProject>().eq("resume_id",id)).id;
        String path="/api/resumes/"+id;
        mvc.perform(delete(path).header("Authorization","Bearer "+b.token()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":2}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(path).header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":0}"))
                .andExpect(status().isConflict());
        FileObject file=files.selectById(fid);
        mvc.perform(delete(path).header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":2}"))
                .andExpect(status().isOk());
        assertThat(resumes.selectById(id).deletedAt).isNotNull();
        assertThat(versions.selectCount(new QueryWrapper<ResumeVersion>().eq("resume_id",id))).isEqualTo(1);
        assertThat(Files.exists(storage.resolve(file.storageKey))).isTrue();
        for(String endpoint:List.of(path,path+"/versions",path+"/projects","/api/files/"+fid,"/api/resume-projects/"+pid))
            mvc.perform(get(endpoint).header("Authorization","Bearer "+a.token())).andExpect(status().isNotFound());
        mvc.perform(get("/api/resumes").header("Authorization","Bearer "+a.token())).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(post(path+"/retry-parse").header("Authorization","Bearer "+a.token())).andExpect(status().isNotFound());
        mvc.perform(post(path+"/confirm").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":3}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(path).header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":3}"))
                .andExpect(status().isNotFound());
        mvc.perform(put(path+"/parsed-data").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":3,\"payload\":{\"education\":[],\"skills\":[],\"projects\":[],\"internships\":[]}}"))
                .andExpect(status().isNotFound());
        mvc.perform(post(path+"/select-project").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectId\":\""+pid+"\",\"version\":1}"))
                .andExpect(status().isNotFound());
        try {
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(new CurrentUser(a.id(),a.account(),"STUDENT","test"),null,List.of()));
            assertThatThrownBy(()->query.getConfirmedResume(id)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(()->query.getConfirmedProject(id,pid)).isInstanceOf(BusinessException.class);
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
    @Test void cannotDeleteDuringParsingOrWriting() throws Exception {
        Actor a=student();String id=upload(a,pdf(true),"busy.pdf","application/pdf").path("summary").path("id").asText();
        resumes.update(null,new UpdateWrapper<Resume>().eq("id",id).set("parse_status","PARSING"));
        mvc.perform(delete("/api/resumes/"+id).header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESUME_BUSY"));
        assertThat(resumes.selectById(id).deletedAt).isNull();
        resumes.update(null,new UpdateWrapper<Resume>().eq("id",id).set("parse_status","SUCCESS"));
        files.update(null,new UpdateWrapper<FileObject>().eq("id",resumes.selectById(id).fileId).set("storage_status","WRITING"));
        mvc.perform(delete("/api/resumes/"+id).header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESUME_BUSY"));
        assertThat(resumes.selectById(id).deletedAt).isNull();
    }
}

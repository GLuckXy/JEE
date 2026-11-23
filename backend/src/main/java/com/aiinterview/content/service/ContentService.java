package com.aiinterview.content.service;

import com.aiinterview.content.common.BusinessException;
import com.aiinterview.content.common.PageResponse;
import com.aiinterview.content.dto.DemoStatus;
import com.aiinterview.content.dto.Difficulty;
import com.aiinterview.content.dto.Requests.*;
import com.aiinterview.content.dto.Views.*;
import com.aiinterview.content.entity.Category;
import com.aiinterview.content.entity.Demo;
import com.aiinterview.content.entity.DemoStage;
import com.aiinterview.content.entity.Job;
import com.aiinterview.content.entity.Question;
import com.aiinterview.content.mapper.CategoryMapper;
import com.aiinterview.content.mapper.DemoMapper;
import com.aiinterview.content.mapper.DemoStageMapper;
import com.aiinterview.content.mapper.JobMapper;
import com.aiinterview.content.mapper.QuestionMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentService {
  private final JobMapper jobs;
  private final CategoryMapper categories;
  private final QuestionMapper questions;
  private final DemoMapper demos;
  private final DemoStageMapper stages;

  public ContentService(
      JobMapper j, CategoryMapper c, QuestionMapper q, DemoMapper d, DemoStageMapper s) {
    jobs = j;
    categories = c;
    questions = q;
    demos = d;
    stages = s;
  }

  private <T> Page<T> page(int p, int s) {
    if (p < 1 || s < 1 || s > 100) throw BusinessException.invalid("page应大于0，size范围为1至100");
    return new Page<>(p, s);
  }

  private String search(String k) {
    if (k == null) return "";
    k = k.trim();
    if (k.length() > 100) throw BusinessException.invalid("关键词不能超过100字");
    return k;
  }

  public Job getJob(long id, boolean pub) {
    var j = jobs.selectById(id);
    if (j == null || (pub && !Boolean.TRUE.equals(j.getEnabled())))
      throw BusinessException.missing();
    return j;
  }

  public PageResponse<Job> listJobs(boolean pub, int p, int s, String keyword) {
    var q = new QueryWrapper<Job>();
    if (pub) q.eq("enabled", true);
    var k = search(keyword);
    if (!k.isEmpty()) q.like("name", k);
    q.orderByAsc("sort_order", "id");
    return PageResponse.from(jobs.selectPage(page(p, s), q), Function.identity());
  }

  public List<Job> activeJobs() {
    return jobs.selectList(new QueryWrapper<Job>().eq("enabled", true).orderByAsc("sort_order", "id"));
  }

  boolean hasJob(String name) {
    return jobs.exists(new QueryWrapper<Job>().eq("name", name));
  }

  @Transactional
  public Job createJob(JobInput r) {
    var j = new Job();
    apply(j, r);
    jobs.insert(j);
    return j;
  }

  @Transactional
  public Job updateJob(long id, JobInput r) {
    var j = getJob(id, false);
    apply(j, r);
    jobs.updateById(j);
    return j;
  }

  private void apply(Job j, JobInput r) {
    j.setName(r.name().trim());
    j.setDescription(r.description().trim());
    j.setRequirements(r.requirements().trim());
    j.setInterviewFocus(r.interviewFocus().trim());
    j.setEnabled(r.enabled());
    j.setSortOrder(r.sortOrder());
    if (j.getId() == null) j.setCreatedAt(LocalDateTime.now());
    j.setUpdatedAt(LocalDateTime.now());
  }

  @Transactional
  public Job enableJob(long id, boolean b) {
    var j = getJob(id, false);
    j.setEnabled(b);
    j.setUpdatedAt(LocalDateTime.now());
    jobs.updateById(j);
    return j;
  }

  public Category getCategory(long id, boolean pub) {
    var c = categories.selectById(id);
    if (c == null) throw BusinessException.missing();
    if (pub) {
      if (!Boolean.TRUE.equals(c.getEnabled())) throw BusinessException.missing();
      getJob(c.getJobId(), true);
    }
    return c;
  }

  public PageResponse<Category> listCategories(
      boolean pub, Long jobId, int p, int s, String keyword) {
    var q = new QueryWrapper<Category>();
    if (jobId != null) {
      getJob(jobId, pub);
      q.eq("job_id", jobId);
    }
    if (pub) q.eq("enabled", true).inSql("job_id", "SELECT id FROM content_job WHERE enabled=TRUE");
    var k = search(keyword);
    if (!k.isEmpty()) q.like("name", k);
    q.orderByAsc("sort_order", "id");
    return PageResponse.from(categories.selectPage(page(p, s), q), Function.identity());
  }

  @Transactional
  public Category createCategory(CategoryInput r) {
    var c = new Category();
    apply(c, r);
    categories.insert(c);
    return c;
  }

  @Transactional
  public Category updateCategory(long id, CategoryInput r) {
    var c = getCategory(id, false);
    if (!Objects.equals(c.getJobId(), r.jobId())
        && questions.selectCount(new QueryWrapper<Question>().eq("category_id", id)) > 0)
      throw BusinessException.conflict("分类已有题目，不能直接修改所属岗位");
    apply(c, r);
    categories.updateById(c);
    return c;
  }

  private void apply(Category c, CategoryInput r) {
    getJob(r.jobId(), false);
    c.setJobId(r.jobId());
    c.setName(r.name().trim());
    c.setDescription(r.description().trim());
    c.setEnabled(r.enabled());
    c.setSortOrder(r.sortOrder());
    if (c.getId() == null) c.setCreatedAt(LocalDateTime.now());
    c.setUpdatedAt(LocalDateTime.now());
  }

  @Transactional
  public Category enableCategory(long id, boolean b) {
    var c = getCategory(id, false);
    c.setEnabled(b);
    c.setUpdatedAt(LocalDateTime.now());
    categories.updateById(c);
    return c;
  }

  public Question getQuestion(long id, boolean pub) {
    var q = questions.selectById(id);
    if (q == null) throw BusinessException.missing();
    if (pub) {
      if (!Boolean.TRUE.equals(q.getEnabled())) throw BusinessException.missing();
      getJob(q.getJobId(), true);
      getCategory(q.getCategoryId(), true);
    }
    return q;
  }

  private QueryWrapper<Question> questionFilter(
      boolean pub, Long jobId, Long categoryId, Difficulty d, String keyword) {
    if (jobId != null) getJob(jobId, pub);
    if (categoryId != null) {
      var c = getCategory(categoryId, pub);
      if (jobId != null && !Objects.equals(jobId, c.getJobId()))
        throw BusinessException.invalid("分类不属于所选岗位");
    }
    var q = new QueryWrapper<Question>();
    if (pub)
      q.eq("enabled", true)
          .inSql("job_id", "SELECT id FROM content_job WHERE enabled=TRUE")
          .inSql("category_id", "SELECT id FROM content_category WHERE enabled=TRUE");
    if (jobId != null) q.eq("job_id", jobId);
    if (categoryId != null) q.eq("category_id", categoryId);
    if (d != null) q.eq("difficulty", d.name());
    var k = search(keyword);
    if (!k.isEmpty()) q.like("title", k);
    return q.orderByDesc("id");
  }

  public PageResponse<Question> adminQuestions(
      Long j, Long c, Difficulty d, int p, int s, String k) {
    return PageResponse.from(
        questions.selectPage(page(p, s), questionFilter(false, j, c, d, k)), Function.identity());
  }

  public PageResponse<QuestionView> publicQuestions(
      Long j, Long c, Difficulty d, int p, int s, String k) {
    return PageResponse.from(
        questions.selectPage(page(p, s), questionFilter(true, j, c, d, k)),
        ContentService::questionView);
  }

  public static QuestionView questionView(Question q) {
    return new QuestionView(
        q.getId(),
        q.getJobId(),
        q.getCategoryId(),
        q.getTitle(),
        q.getContent(),
        q.getDifficulty());
  }

  @Transactional
  public Question createQuestion(QuestionInput r) {
    var q = new Question();
    apply(q, r);
    questions.insert(q);
    return q;
  }

  @Transactional
  public Question updateQuestion(long id, QuestionInput r) {
    var q = getQuestion(id, false);
    apply(q, r);
    questions.updateById(q);
    return q;
  }

  private void apply(Question q, QuestionInput r) {
    getJob(r.jobId(), false);
    var c = getCategory(r.categoryId(), false);
    if (!Objects.equals(c.getJobId(), r.jobId())) throw BusinessException.invalid("题目的分类与岗位不匹配");
    q.setJobId(r.jobId());
    q.setCategoryId(r.categoryId());
    q.setTitle(r.title().trim());
    q.setContent(r.content().trim());
    q.setDifficulty(r.difficulty().name());
    q.setReferencePoints(r.referencePoints().trim());
    q.setSourceNote(r.sourceNote() == null ? "" : r.sourceNote().trim());
    q.setEnabled(r.enabled());
    if (q.getId() == null) q.setCreatedAt(LocalDateTime.now());
    q.setUpdatedAt(LocalDateTime.now());
  }

  @Transactional
  public Question enableQuestion(long id, boolean b) {
    var q = getQuestion(id, false);
    q.setEnabled(b);
    q.setUpdatedAt(LocalDateTime.now());
    questions.updateById(q);
    return q;
  }

  private Demo findDemo(long id, boolean pub) {
    var d = demos.selectById(id);
    if (d == null) throw BusinessException.missing();
    if (pub) {
      if (!"PUBLISHED".equals(d.getStatus())) throw BusinessException.missing();
      getJob(d.getJobId(), true);
    }
    return d;
  }

  public PageResponse<DemoSummary> listDemos(
      boolean pub, Long jobId, DemoStatus status, int p, int s, String keyword) {
    var q = new QueryWrapper<Demo>();
    if (jobId != null) {
      getJob(jobId, pub);
      q.eq("job_id", jobId);
    }
    if (pub)
      q.eq("status", "PUBLISHED").inSql("job_id", "SELECT id FROM content_job WHERE enabled=TRUE");
    else if (status != null) q.eq("status", status.name());
    var k = search(keyword);
    if (!k.isEmpty()) q.like("title", k);
    q.orderByDesc("updated_at", "id");
    return PageResponse.from(
        demos.selectPage(page(p, s), q),
        d ->
            new DemoSummary(
                d.getId(), d.getJobId(), d.getTitle(), d.getIntroduction(), d.getStatus()));
  }

  public DemoDetail demoDetail(long id, boolean pub) {
    var d = findDemo(id, pub);
    var rows =
        stages.selectList(
            new QueryWrapper<DemoStage>().eq("demo_id", id).orderByAsc("sort_order", "id"));
    return new DemoDetail(
        d.getId(),
        d.getJobId(),
        d.getTitle(),
        d.getIntroduction(),
        d.getSourceNote(),
        d.getStatus(),
        rows.stream()
            .map(
                s ->
                    new StageView(
                        s.getId(),
                        s.getSortOrder(),
                        s.getTitle(),
                        s.getInterviewerQuestion(),
                        s.getSampleAnswer(),
                        s.getFollowUp(),
                        s.getExplanation()))
            .toList());
  }

  @Transactional
  public DemoDetail createDemo(DemoInput r) {
    var d = new Demo();
    apply(d, r);
    demos.insert(d);
    saveStages(d.getId(), r.stages());
    return demoDetail(d.getId(), false);
  }

  @Transactional
  public DemoDetail updateDemo(long id, DemoInput r) {
    var d = findDemo(id, false);
    apply(d, r);
    demos.updateById(d);
    stages.delete(new QueryWrapper<DemoStage>().eq("demo_id", id));
    saveStages(id, r.stages());
    return demoDetail(id, false);
  }

  private void apply(Demo d, DemoInput r) {
    getJob(r.jobId(), false);
    var orders = new HashSet<Integer>();
    for (var s : r.stages())
      if (!orders.add(s.sortOrder())) throw BusinessException.invalid("示范阶段序号不能重复");
    d.setJobId(r.jobId());
    d.setTitle(r.title().trim());
    d.setIntroduction(r.introduction().trim());
    d.setSourceNote(r.sourceNote().trim());
    d.setStatus("DRAFT");
    if (d.getId() == null) d.setCreatedAt(LocalDateTime.now());
    d.setUpdatedAt(LocalDateTime.now());
  }

  private void saveStages(long id, List<StageInput> inputs) {
    for (var r : inputs) {
      var s = new DemoStage();
      s.setDemoId(id);
      s.setSortOrder(r.sortOrder());
      s.setTitle(r.title().trim());
      s.setInterviewerQuestion(r.interviewerQuestion().trim());
      s.setSampleAnswer(r.sampleAnswer().trim());
      s.setFollowUp(r.followUp() == null ? "" : r.followUp().trim());
      s.setExplanation(r.explanation().trim());
      s.setCreatedAt(LocalDateTime.now());
      s.setUpdatedAt(LocalDateTime.now());
      stages.insert(s);
    }
  }

  @Transactional
  public DemoDetail setDemoStatus(long id, DemoStatus status) {
    var d = findDemo(id, false);
    if (status == DemoStatus.PUBLISHED) {
      getJob(d.getJobId(), true);
      if (stages.selectCount(new QueryWrapper<DemoStage>().eq("demo_id", id)) == 0)
        throw BusinessException.invalid("发布前至少添加一个完整示范阶段");
    }
    d.setStatus(status.name());
    d.setUpdatedAt(LocalDateTime.now());
    demos.updateById(d);
    return demoDetail(id, false);
  }

  public List<GuideStep> guide() {
    return List.of(
        new GuideStep(1, "开场", "了解面试安排与交流方式"),
        new GuideStep(2, "自我介绍", "说明背景、目标岗位与相关经历"),
        new GuideStep(3, "项目介绍", "说明项目背景、个人职责与实施过程"),
        new GuideStep(4, "技术问答", "结合岗位考察基础知识和应用能力"),
        new GuideStep(5, "追问", "说明选择理由、实现细节及问题处理过程"),
        new GuideStep(6, "结束", "回顾交流并礼貌结束；实际企业流程可能不同"));
  }

  public List<TrainingOption> trainingOptions() {
    return List.of(
        new TrainingOption(
            "FORMAL", "正式模拟", "限时语音作答，使用已确认项目，保留首次回答，结束后反馈", "/formal", true, true),
        new TrainingOption(
            "NORMAL", "普通训练", "文字或语音问答，支持暂停、重答和即时反馈", "/training", false, false),
        new TrainingOption("SPECIAL", "专项训练", "按知识点或弱项开展针对性练习", "/practice", false, false),
        new TrainingOption(
            "PROJECT", "项目深挖", "正式模拟中的环节，基于已确认项目开展有限追问", "/formal", true, true));
  }
}

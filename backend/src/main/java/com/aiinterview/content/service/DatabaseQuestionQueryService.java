package com.aiinterview.content.service;

import com.aiinterview.content.common.BusinessException;
import com.aiinterview.content.dto.Difficulty;
import com.aiinterview.content.dto.Views.TrainingQuestion;
import com.aiinterview.content.entity.Question;
import com.aiinterview.content.mapper.QuestionMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class DatabaseQuestionQueryService implements QuestionQueryService {
  private final ContentService content;
  private final QuestionMapper questions;

  public DatabaseQuestionQueryService(ContentService c, QuestionMapper q) {
    content = c;
    questions = q;
  }

  @Override
  public List<TrainingQuestion> listForTraining(long jobId) {
    content.getJob(jobId, true);
    return questions.selectList(activeByJob(jobId)).stream().map(this::view).toList();
  }

  private QueryWrapper<Question> activeByJob(long jobId) {
    return new QueryWrapper<Question>().eq("job_id", jobId).eq("enabled", true)
        .inSql("category_id", "SELECT id FROM content_category WHERE enabled=TRUE").orderByAsc("id");
  }

  public List<TrainingQuestion> selectForTraining(
      long jobId, Long categoryId, Difficulty difficulty, int limit, Set<Long> excludedIds) {
    if (limit < 1 || limit > 100) throw BusinessException.invalid("选题数量范围为1至100");
    content.getJob(jobId, true);
    if (categoryId != null
        && !Objects.equals(content.getCategory(categoryId, true).getJobId(), jobId))
      throw BusinessException.invalid("分类不属于所选岗位");
    if (excludedIds != null && excludedIds.size() > 1000)
      throw BusinessException.invalid("排除题目数量不能超过1000");
    var q = activeByJob(jobId);
    if (categoryId != null) q.eq("category_id", categoryId);
    if (difficulty != null) q.eq("difficulty", difficulty.name());
    if (excludedIds != null && !excludedIds.isEmpty()) q.notIn("id", excludedIds);
    return questions.selectPage(new Page<Question>(1, limit, false), q).getRecords().stream()
        .map(this::view)
        .toList();
  }

  /** 内部调用方须先检查会话归属；历史评价应使用会话保存的题目和参考要点快照。 */
  public TrainingQuestion getForEvaluation(long id) {
    return view(content.getQuestion(id, false));
  }

  private TrainingQuestion view(Question q) {
    return new TrainingQuestion(
        q.getId(),
        q.getJobId(),
        q.getCategoryId(),
        q.getTitle(),
        q.getContent(),
        q.getDifficulty(),
        q.getReferencePoints());
  }
}

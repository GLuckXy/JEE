package cn.edu.interview.content.service;

import cn.edu.interview.content.dto.Difficulty;
import cn.edu.interview.content.dto.Views.TrainingQuestion;
import java.util.*;

/** 组员后端注入使用；禁止直接将参考答案返回给尚未作答的学生。 */
public interface QuestionQueryService {
  List<TrainingQuestion> listForTraining(long jobId);

  List<TrainingQuestion> selectForTraining(
      long jobId, Long categoryId, Difficulty difficulty, int limit, Set<Long> excludedIds);

  TrainingQuestion getForEvaluation(long questionId);
}

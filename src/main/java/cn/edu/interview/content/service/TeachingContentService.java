package cn.edu.interview.content.service;

import cn.edu.interview.content.common.BusinessException;
import cn.edu.interview.content.dto.Difficulty;
import cn.edu.interview.content.dto.Requests.CategoryInput;
import cn.edu.interview.content.dto.Requests.DemoInput;
import cn.edu.interview.content.dto.Requests.JobInput;
import cn.edu.interview.content.dto.Requests.QuestionInput;
import cn.edu.interview.content.dto.Requests.StageInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeachingContentService {
  private static final String PREFIX = "【测试】";
  private final ContentService content;
  private final ObjectMapper json;
  private final Validator validator;

  public TeachingContentService(ContentService content, ObjectMapper json, Validator validator) {
    this.content = content;
    this.json = json;
    this.validator = validator;
  }

  @Transactional
  public ImportResult install() {
    var bundle = load();
    int jobs = 0;
    int categories = 0;
    int questions = 0;
    int drafts = 0;
    int skippedJobs = 0;
    for (var sample : bundle.jobs()) {
      var job = sample.job();
      var name = PREFIX + job.name();
      if (content.hasJob(name)) {
        skippedJobs++;
        continue;
      }
      long jobId;
      try {
        jobId = content.createJob(checked(new JobInput(name, job.description(), job.requirements(),
            job.interviewFocus(), job.enabled(), job.sortOrder() + 100))).getId();
      } catch (DuplicateKeyException conflict) {
        throw BusinessException.conflict("测试题库正在导入，请刷新后查看结果");
      }
      jobs++;
      int order = 1;
      for (var category : sample.categories()) {
        long categoryId = content.createCategory(checked(new CategoryInput(jobId, category.name(),
            category.description(), true, order++))).getId();
        categories++;
        for (var question : category.questions()) {
          content.createQuestion(checked(new QuestionInput(jobId, categoryId, question.title(),
              question.content(), question.difficulty(), question.referencePoints(), bundle.source(), true)));
          questions++;
        }
      }
      var demo = sample.demo();
      content.createDemo(checked(new DemoInput(jobId, PREFIX + demo.title(), demo.introduction(),
          demo.sourceNote(), demo.stages())));
      drafts++;
    }
    return new ImportResult(jobs, categories, questions, drafts, skippedJobs);
  }

  private TeachingBundle load() {
    try (var stream = new ClassPathResource("teaching/content-samples.json").getInputStream()) {
      return json.readValue(stream, TeachingBundle.class);
    } catch (IOException failure) {
      throw new IllegalStateException("Teaching content bundle cannot be read", failure);
    }
  }

  private <T> T checked(T input) {
    if (!validator.validate(input).isEmpty()) throw new IllegalStateException("Invalid teaching content bundle");
    return input;
  }

  public record ImportResult(int jobs, int categories, int questions, int drafts, int skippedJobs) {}
  private record TeachingBundle(String source, List<TeachingJob> jobs) {}
  private record TeachingJob(JobInput job, List<TeachingCategory> categories, TeachingDemo demo) {}
  private record TeachingCategory(String name, String description, List<TeachingQuestion> questions) {}
  private record TeachingQuestion(String title, String content, Difficulty difficulty, String referencePoints) {}
  private record TeachingDemo(String title, String introduction, String sourceNote, List<StageInput> stages) {}
}

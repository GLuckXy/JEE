package com.aiinterview.training.config;

import com.aiinterview.auth.service.UserAccessService;
import com.aiinterview.common.web.CurrentUser;
import com.aiinterview.report.service.engine.ReferencePointAnalyzer;
import com.aiinterview.training.service.AnswerEvaluator;
import com.aiinterview.training.service.QuestionBank;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TrainingModuleConfiguration {
    @Bean CurrentUser trainingCurrentUser(UserAccessService access) {
        return (request, response) -> access.requireStudent().id();
    }

    @Bean AnswerEvaluator trainingAnswerEvaluator(ReferencePointAnalyzer analyzer) {
        return input -> {
            List<String> references = input.question().points().stream().map(QuestionBank.Point::text).toList();
            var analysis = analyzer.analyze(input.answer(), String.join("\n", references));
            var missing = analysis.getMissing();
            var suggestion = missing.isEmpty() ? "尝试补充具体例子，说明方案的边界和验证方法。"
                    : "建议先说明结论，再补充：" + String.join("；", missing) + "。";
            return new AnswerEvaluator.Feedback("规则反馈", references, analysis.getCovered(), missing, suggestion);
        };
    }
}

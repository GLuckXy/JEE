package edu.zjut.interview.formal;

import com.aiinterview.report.SessionCompleted;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationEventPublisher;

@Configuration(proxyBeanMethods = false)
public class FormalInterviewModule {
    @Bean
    @ConditionalOnMissingBean(ModelClient.class)
    ModelClient formalInterviewModel(ObjectMapper json,
                                    @Value("${interview.ai.url:https://api.deepseek.com/chat/completions}") URI url,
                                    @Value("${interview.ai.key:}") String key,
                                    @Value("${interview.ai.model:deepseek-chat}") String model) {
        return new DeepSeekClient(json, url, key, model);
    }

    @Bean
    ProjectDeepDive projectDeepDive(ModelClient client, ObjectMapper json) {
        return new ProjectQuestions(client, json);
    }

    @Bean
    InterviewService formalInterviewService(DataSource dataSource, ObjectMapper json, InterviewCatalog catalog,
                                            SpeechTranscriber speech, ProjectDeepDive projects,
                                            ObjectProvider<Clock> clock, ApplicationEventPublisher events) {
        return new InterviewService(new SqlInterviewStore(dataSource, json,
                (owner, id) -> events.publishEvent(new SessionCompleted(owner, id.toString()))), catalog, speech, projects,
                clock.getIfAvailable(Clock::systemUTC));
    }
}

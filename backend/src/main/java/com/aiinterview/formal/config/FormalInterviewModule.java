package com.aiinterview.formal.config;

import com.aiinterview.formal.integration.DeepSeekClient;
import com.aiinterview.formal.persistence.SqlInterviewStore;
import com.aiinterview.formal.service.InterviewCatalog;
import com.aiinterview.formal.service.InterviewService;
import com.aiinterview.formal.service.ModelClient;
import com.aiinterview.formal.service.ProjectDeepDive;
import com.aiinterview.formal.service.ProjectQuestions;
import com.aiinterview.formal.service.SpeechTranscriber;
import com.aiinterview.report.domain.SessionCompleted;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                (owner, id) -> events.publishEvent(new SessionCompleted(owner, id.toString())), null), catalog, speech, projects,
                clock.getIfAvailable(Clock::systemUTC));
    }
}

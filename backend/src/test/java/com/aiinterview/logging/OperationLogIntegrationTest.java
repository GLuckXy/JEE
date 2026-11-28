package com.aiinterview.logging;

import com.aiinterview.user.persistence.User;
import com.aiinterview.user.persistence.UserMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:operation_log_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OperationLogIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserMapper users;
    private static final String PASSWORD="StudyPass2026";
    record Actor(String id,String account,String token) {}
    private JsonNode data(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()).path("data"); }
    private Actor student() throws Exception {
        String account="s"+UUID.randomUUID().toString().replace("-","").substring(0,11);
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
    @Test void authOperationsRecordedAndAdminQueriesWithFilters() throws Exception {
        Actor a=student(), admin=admin();
        mvc.perform(post("/api/auth/logout").header("Authorization","Bearer "+a.token())).andExpect(status().isOk());
        var result=mvc.perform(get("/api/admin/logs").header("Authorization","Bearer "+admin.token())
                .param("userId",a.id())).andExpect(status().isOk()).andReturn();
        JsonNode page=data(result);
        assertThat(page.path("total").asLong()).isGreaterThanOrEqualTo(3);
        Set<String> actions=new HashSet<>();
        for (JsonNode row : page.path("records")) {
            actions.add(row.path("action").asText());
            assertThat(row.path("userId").asText()).isEqualTo(a.id());
            assertThat(row.path("account").asText()).isEqualTo(a.account());
        }
        assertThat(actions).contains("AUTH_REGISTER","AUTH_LOGIN","AUTH_LOGOUT");
        var filtered=data(mvc.perform(get("/api/admin/logs").header("Authorization","Bearer "+admin.token())
                .param("action","AUTH_LOGIN").param("userId",a.id())).andExpect(status().isOk()).andReturn());
        assertThat(filtered.path("total").asLong()).isGreaterThanOrEqualTo(1);
        for (JsonNode row : filtered.path("records")) assertThat(row.path("action").asText()).isEqualTo("AUTH_LOGIN");
    }
    @Test void studentCannotReadOperationLogs() throws Exception {
        Actor a=student();
        mvc.perform(get("/api/admin/logs").header("Authorization","Bearer "+a.token())).andExpect(status().isForbidden());
    }
}

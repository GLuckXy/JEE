package com.aiinterview.resume.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ResumeTextExtractorTest {
    @Test void extractsEducationInternshipsAndMultipleProjectsWithEvidence() {
        var parsed=ResumeTextExtractor.extract("""
                一、教育背景
                2023.09—2027.06 浙江工业大学 软件工程 本科
                主修课程：数据结构、数据库
                2027.09—2030.06 测试大学 计算机技术 硕士
                二、实习经验
                2026.06 至 2026.09 杭州测试有限公司 Java开发实习生
                参与接口开发与测试
                2026.10—2026.12 杭州另一公司 后端开发工程师
                参与文档维护
                三、项目实践
                项目名称：面试平台
                项目目标：为学生提供模拟面试
                本人职责：负责用户模块
                完成接口验证
                技术栈：Java、Spring Boot、MySQL
                项目成果：完成课程验收
                项目名称：课程系统
                我的职责：实现选课接口
                技术栈：Vue, Java
                四、荣誉奖项
                学业奖学金
                """);
        assertThat(parsed.parserVersion()).isEqualTo("sections-v2");
        assertThat(parsed.extracted().education()).hasSize(2);
        var education=parsed.extracted().education().getFirst();
        assertThat(education.organization()).isEqualTo("浙江工业大学");
        assertThat(education.title()).isEqualTo("软件工程 / 本科");
        assertThat(education.period()).isEqualTo("2023.09—2027.06");
        assertThat(education.description()).contains("数据结构");
        assertThat(parsed.extracted().internships()).hasSize(2);
        assertThat(parsed.extracted().internships().getFirst().organization()).isEqualTo("杭州测试有限公司");
        assertThat(parsed.extracted().internships().getFirst().title()).isEqualTo("Java开发实习生");
        assertThat(parsed.extracted().projects()).hasSize(2);
        assertThat(parsed.extracted().projects().getFirst().responsibility()).contains("负责用户模块","完成接口验证");
        assertThat(parsed.extracted().projects().getFirst().techStack()).containsExactly("Java","Spring Boot","MySQL");
        assertThat(parsed.extracted().projects().get(1).responsibility()).doesNotContain("奖学金");
        assertThat(parsed.evidence()).anyMatch(e->e.field().equals("education[0].organization")&&e.source().contains("浙江工业大学"));
    }
    @Test void preservesUnknownDescriptionWithoutInventingPersonalDuties() {
        var parsed=ResumeTextExtractor.extract("""
                【项目经历】
                2025.01—2025.06 校园平台
                团队实现了完整后台与网页
                团队职责：开发全部系统
                """);
        assertThat(parsed.extracted().projects()).hasSize(1);
        assertThat(parsed.extracted().projects().getFirst().name()).isEqualTo("校园平台");
        assertThat(parsed.extracted().projects().getFirst().responsibility()).isEmpty();
        assertThat(parsed.warnings()).anyMatch(w->w.contains("本人职责"));
        assertThat(parsed.text()).contains("团队实现了完整后台与网页");
    }
    @Test void acceptsExplicitFieldsAndDateOnSeparateLine() {
        var parsed=ResumeTextExtractor.extract("""
                1. 教育信息
                2023/09 - 2027/06
                学校：测试大学
                专业：信息安全
                学历：本科
                2. 实习经历
                公司：测试科技
                岗位：后端开发
                2025.01—至今
                说明：接口开发
                """);
        assertThat(parsed.extracted().education()).hasSize(1);
        assertThat(parsed.extracted().education().getFirst().title()).isEqualTo("信息安全 / 本科");
        assertThat(parsed.extracted().internships().getFirst().title()).isEqualTo("后端开发");
        assertThat(parsed.extracted().internships().getFirst().period()).isEqualTo("2025.01—至今");
    }
    @Test void keepsOlderSimpleEnglishSkillAndProjectSections() {
        var parsed=ResumeTextExtractor.extract("Skills\nJava, SQL\nProjects\nCourse Platform");
        assertThat(parsed.extracted().skills()).containsExactly("Java","SQL");
        assertThat(parsed.extracted().projects().getFirst().name()).isEqualTo("Course Platform");
    }
    @Test void projectDatesOnSeparateLinesDoNotCreateExtraProjects() {
        var parsed=ResumeTextExtractor.extract("项目经历\n项目名称：面试平台\n2025.01—2025.06\n本人职责：负责接口\n项目名称：课程系统\n2025.07—2025.12\n本人职责：负责选课");
        assertThat(parsed.extracted().projects()).hasSize(2);
        assertThat(parsed.extracted().projects().getFirst().name()).isEqualTo("面试平台");
        assertThat(parsed.extracted().projects().get(1).responsibility()).isEqualTo("负责选课");
        var dateFirst=ResumeTextExtractor.extract("项目经历\n2025.01—2025.06\n面试平台\n本人职责：负责接口");
        assertThat(dateFirst.extracted().projects().getFirst().name()).isEqualTo("面试平台");
    }
    @Test void evidenceOnlyReferencesLinesThatActuallyPopulateAField() {
        var parsed=ResumeTextExtractor.extract("教育背景\n测试大学 软件工程 本科\n主修课程：数据库\n项目经历\n面试平台\n团队职责：全部后端");
        assertThat(parsed.evidence()).noneMatch(e->e.field().endsWith(".title")&&e.source().contains("数据库"));
        assertThat(parsed.evidence()).noneMatch(e->e.field().endsWith("unassigned"));
        assertThat(parsed.warnings()).anyMatch(w->w.contains("未明确标注用途"));
    }
    @Test void unknownSectionsAndLongDescriptionsDoNotProduceInvalidPayloads() {
        var parsed=ResumeTextExtractor.extract("教育背景\n测试大学 软件工程 本科\n"+"描述".repeat(4000));
        assertThat(parsed.extracted().education().getFirst().description()).hasSize(5000);
        assertThat(parsed.warnings()).anyMatch(w->w.contains("超过字段长度"));
    }
}

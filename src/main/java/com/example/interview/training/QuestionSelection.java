package com.example.interview.training;

import com.example.interview.training.QuestionBank.Question;
import com.example.interview.training.TrainingApi.Create;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import static com.example.interview.api.ApiErrors.invalid;

@Component
class QuestionSelection {
    private final QuestionBank bank;

    QuestionSelection(QuestionBank bank) {
        this.bank = bank;
    }

    List<Question> select(Create input, Question source) {
        if (!List.of("NORMAL", "SPECIAL").contains(Objects.toString(input.mode(), ""))) {
            throw invalid("请选择普通训练或专项训练");
        }
        if (bank.roles().stream().noneMatch(role -> role.id().equals(input.roleId()))) {
            throw invalid("请选择有效的岗位");
        }
        List<Question> available = bank.questions(input.roleId());
        if (input.source() != null) {
            if (!"SPECIAL".equals(input.mode()) || source == null || !source.roleId().equals(input.roleId())) {
                throw invalid("复练来源与专项岗位不匹配");
            }
            return switch (Objects.toString(input.source().strategy(), "")) {
                case "ORIGINAL" -> List.of(available.stream().filter(question -> question.id().equals(source.id()))
                        .findFirst().orElseThrow(() -> invalid("原题已停用，可选择同类别复练")));
                case "SIMILAR" -> {
                    var similar = available.stream().filter(question -> question.categoryId().equals(source.categoryId()))
                            .filter(question -> !question.id().equals(source.id())).limit(10).toList();
                    if (similar.isEmpty()) throw invalid("该知识点暂时没有可练的新题");
                    yield similar;
                }
                default -> throw invalid("请选择复练方式");
            };
        }
        List<Question> candidates = available;
        if ("SPECIAL".equals(input.mode())) {
            if (blank(input.topic()) && blank(input.type())) throw invalid("请选择知识点或题型");
            candidates = available.stream().filter(question -> blank(input.topic()) || input.topic().equals(question.topic()))
                    .filter(question -> blank(input.type()) || input.type().equals(question.type())).toList();
        }
        if (input.questionIds() == null || input.questionIds().isEmpty()) {
            if ("NORMAL".equals(input.mode())) throw invalid("请至少选择一道题");
            if (candidates.isEmpty()) throw invalid("当前条件下没有可用题目");
            return candidates.stream().limit(10).toList();
        }
        var requested = new LinkedHashSet<>(input.questionIds());
        if (requested.size() > 10) throw invalid("每次最多选择 10 道题");
        List<Question> eligible = candidates;
        return requested.stream().map(id -> eligible.stream().filter(question -> question.id().equals(id))
                .findFirst().orElseThrow(() -> invalid("所选题目不在当前岗位或专项内"))).toList();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

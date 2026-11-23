package com.aiinterview.training.service;

import com.aiinterview.training.dto.TrainingApi;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

@Service
public class EvaluationService implements DisposableBean {
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16));
    private final TrainingService training;
    private final AnswerEvaluator evaluator;

    EvaluationService(TrainingService training, AnswerEvaluator evaluator) {
        this.training = training;
        this.evaluator = evaluator;
    }

    public TrainingApi.AnswerView evaluate(String owner, String answerId) {
        var attempt = training.beginEvaluation(owner, answerId);
        if (attempt.ticket() == null) return attempt.answer();
        Future<AnswerEvaluator.Feedback> task = null;
        AnswerEvaluator.Feedback feedback = null;
        String failure = null;
        try {
            task = workers.submit(() -> evaluator.evaluate(attempt.input()));
            var result = task.get(training.evaluationTimeout(), TimeUnit.MILLISECONDS);
            if (result == null || result.label() == null || result.label().isBlank()
                    || result.referencePoints() == null || result.covered() == null || result.missing() == null
                    || result.suggestion() == null || result.suggestion().isBlank()) {
                failure = "评价结果缺少必要字段，可按原回答重试";
            } else {
                feedback = result;
            }
        } catch (TimeoutException error) {
            failure = "评价超时，可按原回答重试";
        } catch (InterruptedException error) {
            failure = "评价已中断，可按原回答重试";
        } catch (RejectedExecutionException error) {
            failure = "评价服务繁忙，可按原回答重试";
        } catch (ExecutionException error) {
            if (error.getCause() instanceof TimeoutException) {
                failure = "评价超时，可按原回答重试";
            } else {
                LoggerFactory.getLogger(EvaluationService.class).warn("Evaluation failed for {}", answerId, error.getCause());
                failure = "评价服务调用失败，可按原回答重试";
            }
        } finally {
            if (task != null && !task.isDone()) {
                task.cancel(true);
                workers.purge();
            }
        }
        return training.finishEvaluation(owner, answerId, attempt.ticket(), feedback, failure);
    }

    @Override
    public void destroy() {
        workers.shutdownNow();
    }
}

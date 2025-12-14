package com.kensai.sandbox.lexio.web.dto.exercise;

public record ExerciseStartedResponse(
    String exerciseId,
    int currentQuestionNumber,
    int totalQuestions,
    String sentenceWithBlank
) {}

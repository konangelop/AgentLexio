package com.kensai.sandbox.lexio.web.dto.exercise;

import java.util.List;

public record ExerciseSummaryResponse(
    int totalQuestions,
    int correctAnswers,
    int skipped,
    int hintsUsed,
    double accuracyPercentage,
    List<MissedWord> missedWords
) {}

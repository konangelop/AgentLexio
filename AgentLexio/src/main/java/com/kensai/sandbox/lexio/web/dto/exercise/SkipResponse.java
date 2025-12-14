package com.kensai.sandbox.lexio.web.dto.exercise;

public record SkipResponse(
    String skippedWord,
    String skippedSentenceComplete,
    boolean exerciseComplete,
    Integer nextQuestionNumber,
    String nextSentence
) {}

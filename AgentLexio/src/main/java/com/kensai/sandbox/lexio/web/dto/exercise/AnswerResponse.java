package com.kensai.sandbox.lexio.web.dto.exercise;

public record AnswerResponse(
    boolean correct,
    String userAnswer,
    String correctWord,
    String explanation,
    boolean exerciseComplete,
    Integer nextQuestionNumber,
    String nextSentence
) {}

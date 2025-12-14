package com.kensai.sandbox.lexio.web.dto.exercise;

public record TopicWarningResponse(
    String topic,
    String topicLevel,
    String userLevel,
    String warning,
    String suggestedSimplerTopic,
    boolean proceedAnyway
) {}

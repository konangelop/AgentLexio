package com.kensai.sandbox.lexio.web.dto;

public record TopicAssessment(
    String topic,
    String assessedLevel,
    String reasoning,
    String suggestedSimplerTopic
) {}

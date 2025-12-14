package com.kensai.sandbox.lexio.web.dto.exercise;

public record TranslationResponse(
    String englishTranslation,
    String sentenceWithBlank
) {}

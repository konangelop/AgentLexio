package com.kensai.sandbox.lexio.ai.tools;

import com.kensai.sandbox.lexio.service.UserProfileService;
import com.kensai.sandbox.lexio.service.UserProfileService.CefrLevel;
import com.kensai.sandbox.lexio.service.VocabularyGenerationService;
import com.kensai.sandbox.lexio.service.VocabularyGenerationService.GeneratedQuestion;
import com.kensai.sandbox.lexio.web.dto.TopicAssessment;
import com.kensai.sandbox.lexio.web.dto.exercise.*;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class VocabularyExerciseTools {

    private final UserProfileService userProfileService;
    private final VocabularyGenerationService vocabularyGenerationService;

    private final Map<String, ExerciseState> activeExercises = new ConcurrentHashMap<>();
    private final Map<String, PendingExercise> pendingExercises = new ConcurrentHashMap<>();

    @Tool("""
        Sets the user's German proficiency level. Call this when the user tells you their level.
        Valid levels are: A1, A2, B1, B2, C1, C2 (CEFR scale).
        A1 is beginner, C2 is native-like proficiency.
        """)
    public String setUserLevel(
            @P("The CEFR level: A1, A2, B1, B2, C1, or C2")
            String level) {
        log.info("Setting user level to: {}", level);
        CefrLevel cefrLevel = CefrLevel.fromString(level);
        userProfileService.setLevel(cefrLevel);
        return String.format("Your German level has been set to %s.", cefrLevel);
    }

    @Tool("""
        Gets the user's current German proficiency level.
        Call this when the user asks about their level or you need to check it.
        """)
    public String getUserLevel() {
        CefrLevel level = userProfileService.getLevel();
        log.info("Getting user level: {}", level);
        return String.format("Your current German level is set to %s.", level);
    }

    @Tool("""
        Assesses a vocabulary topic and generates exercises if appropriate.
        Call this when the user wants to practice vocabulary on a specific topic.
        If the topic is above the user's level, returns a warning with options.
        If the topic is appropriate or the user confirms, generates the exercise.
        """)
    public Object generateVocabularyExercise(
            @P("The vocabulary topic to practice, e.g., 'legal terms', 'cooking', 'sports'. Can be any topic.")
            String topic,
            @P("Number of sentences to generate. Default to 5 if user doesn't specify. Maximum is 10.")
            int numberOfQuestions,
            @P("Set to true if the user has already been warned about difficulty and wants to proceed anyway")
            boolean proceedDespiteWarning) {

        log.info("Generating vocabulary exercise for topic: {}, questions: {}, proceedDespiteWarning: {}",
                topic, numberOfQuestions, proceedDespiteWarning);

        int questionsCount = Math.min(Math.max(numberOfQuestions, 1), 10);

        // Assess the topic difficulty
        TopicAssessment assessment = vocabularyGenerationService.assessTopic(topic);
        CefrLevel topicLevel = CefrLevel.fromString(assessment.assessedLevel());
        CefrLevel userLevel = userProfileService.getLevel();

        log.info("Topic '{}' assessed at {} level, user is at {} level", topic, topicLevel, userLevel);

        // Check if topic is too advanced
        if (!proceedDespiteWarning && userLevel.isLowerThan(topicLevel)) {
            String pendingId = UUID.randomUUID().toString().substring(0, 8);
            pendingExercises.put(pendingId, new PendingExercise(topic, questionsCount, topicLevel.name()));

            String warning = String.format(
                "The topic '%s' is typically at %s level, but your current level is %s. " +
                "This might be challenging! Would you like to:\n" +
                "1. Continue anyway (I'll adjust the vocabulary to be more accessible)\n" +
                "2. Try a simpler topic%s",
                topic, topicLevel, userLevel,
                assessment.suggestedSimplerTopic() != null
                    ? " (suggested: " + assessment.suggestedSimplerTopic() + ")"
                    : ""
            );

            return new TopicWarningResponse(
                topic,
                topicLevel.name(),
                userLevel.name(),
                warning,
                assessment.suggestedSimplerTopic(),
                false
            );
        }

        // Generate the exercise
        return createExercise(topic, questionsCount, userLevel.name());
    }

    @Tool("""
        Confirms that the user wants to proceed with a difficult topic after being warned.
        Call this when the user says they want to continue despite the topic being above their level.
        """)
    public ExerciseStartedResponse confirmDifficultTopic(
            @P("The topic that was previously assessed as difficult")
            String topic,
            @P("Number of questions for the exercise")
            int numberOfQuestions) {

        log.info("User confirmed difficult topic: {}", topic);
        CefrLevel userLevel = userProfileService.getLevel();
        return createExercise(topic, numberOfQuestions, userLevel.name());
    }

    private ExerciseStartedResponse createExercise(String topic, int questionsCount, String level) {
        String exerciseId = UUID.randomUUID().toString().substring(0, 8);

        // Generate questions using AI
        List<GeneratedQuestion> generatedQuestions =
            vocabularyGenerationService.generateQuestions(topic, level, questionsCount);

        // Convert to internal QuestionData format
        List<QuestionData> questions = generatedQuestions.stream()
            .map(gq -> new QuestionData(
                gq.sentenceWithBlank(),
                gq.completeSentence(),
                gq.targetWord(),
                gq.englishWord(),
                gq.englishTranslation()
            ))
            .toList();

        ExerciseState state = new ExerciseState(exerciseId, List.of(topic), questions);
        activeExercises.put(exerciseId, state);

        QuestionData firstQuestion = questions.get(0);
        return new ExerciseStartedResponse(
            exerciseId,
            1,
            questions.size(),
            firstQuestion.sentenceWithBlank()
        );
    }

    @Tool("""
        Submits the user's answer for the current question and returns feedback.
        Call this when the user provides their guess for the missing word.
        Returns whether the answer was correct, the correct word if wrong,
        and the next question if the exercise isn't complete yet.
        """)
    public AnswerResponse submitAnswer(
            @P("The exercise ID from when the exercise was started")
            String exerciseId,
            @P("The user's answer - the German word they think fills the blank")
            String answer) {

        log.info("Submitting answer for exercise {}: {}", exerciseId, answer);

        ExerciseState state = activeExercises.get(exerciseId);
        if (state == null) {
            return new AnswerResponse(false, answer, null,
                "Exercise not found. Please start a new exercise.",
                true, null, null);
        }

        QuestionData currentQuestion = state.getCurrentQuestion();
        boolean isCorrect = normalizeAnswer(answer).equals(normalizeAnswer(currentQuestion.targetWord()));

        state.recordAnswer(answer, isCorrect);
        state.moveToNext();

        boolean exerciseComplete = state.isComplete();
        QuestionData nextQuestion = exerciseComplete ? null : state.getCurrentQuestion();

        String explanation = isCorrect ? null :
            String.format("The correct word was '%s' (%s).",
                currentQuestion.targetWord(),
                currentQuestion.englishWord());

        return new AnswerResponse(
            isCorrect,
            answer,
            currentQuestion.targetWord(),
            explanation,
            exerciseComplete,
            exerciseComplete ? null : state.getCurrentIndex() + 1,
            exerciseComplete ? null : nextQuestion.sentenceWithBlank()
        );
    }

    @Tool("""
        Provides the English translation of the current sentence as a hint.
        Call this when the user asks for help, a translation, or says they don't know the word.
        This marks the question as 'hint used' for progress tracking.
        """)
    public TranslationResponse requestTranslation(
            @P("The exercise ID")
            String exerciseId) {

        log.info("Translation requested for exercise {}", exerciseId);

        ExerciseState state = activeExercises.get(exerciseId);
        if (state == null) {
            return new TranslationResponse(
                "Exercise not found. Please start a new exercise.",
                null
            );
        }

        state.markHintUsed();
        QuestionData currentQuestion = state.getCurrentQuestion();

        return new TranslationResponse(
            currentQuestion.englishTranslation(),
            currentQuestion.sentenceWithBlank()
        );
    }

    @Tool("""
        Skips the current question and moves to the next one.
        Call this when the user wants to skip, give up, or says they can't answer.
        Returns the correct answer for the skipped question and the next question.
        """)
    public SkipResponse skipQuestion(
            @P("The exercise ID")
            String exerciseId) {

        log.info("Skipping question for exercise {}", exerciseId);

        ExerciseState state = activeExercises.get(exerciseId);
        if (state == null) {
            return new SkipResponse(null, null, true, null, null);
        }

        QuestionData skippedQuestion = state.getCurrentQuestion();
        state.recordSkip();
        state.moveToNext();

        boolean exerciseComplete = state.isComplete();
        QuestionData nextQuestion = exerciseComplete ? null : state.getCurrentQuestion();

        return new SkipResponse(
            skippedQuestion.targetWord(),
            skippedQuestion.completeSentence(),
            exerciseComplete,
            exerciseComplete ? null : state.getCurrentIndex() + 1,
            exerciseComplete ? null : nextQuestion.sentenceWithBlank()
        );
    }

    @Tool("""
        Gets a summary of the exercise with statistics and results.
        Call this when the exercise is finished or when the user asks for their results.
        Returns accuracy, hints used, and the words that were missed.
        """)
    public ExerciseSummaryResponse getExerciseSummary(
            @P("The exercise ID")
            String exerciseId) {

        log.info("Getting summary for exercise {}", exerciseId);

        ExerciseState state = activeExercises.get(exerciseId);
        if (state == null) {
            return new ExerciseSummaryResponse(0, 0, 0, 0, 0.0, List.of());
        }

        return state.getSummary();
    }

    private String normalizeAnswer(String answer) {
        if (answer == null) return "";
        String normalized = answer.trim().toLowerCase();
        normalized = normalized.replaceAll("^(der|die|das|ein|eine|einen|einem|einer)\\s+", "");
        return normalized;
    }

    // Inner classes for state management

    private record PendingExercise(String topic, int questionCount, String topicLevel) {}

    private static class ExerciseState {
        private final String id;
        private final List<String> topics;
        private final List<QuestionData> questions;
        private final List<AttemptRecord> attempts;
        private int currentIndex;

        ExerciseState(String id, List<String> topics, List<QuestionData> questions) {
            this.id = id;
            this.topics = new ArrayList<>(topics);
            this.questions = new ArrayList<>(questions);
            this.attempts = new ArrayList<>();
            this.currentIndex = 0;
            for (int i = 0; i < questions.size(); i++) {
                attempts.add(new AttemptRecord());
            }
        }

        QuestionData getCurrentQuestion() {
            if (currentIndex >= questions.size()) return null;
            return questions.get(currentIndex);
        }

        int getCurrentIndex() { return currentIndex; }
        void moveToNext() { currentIndex++; }
        boolean isComplete() { return currentIndex >= questions.size(); }

        void recordAnswer(String answer, boolean correct) {
            if (currentIndex < attempts.size()) {
                AttemptRecord record = attempts.get(currentIndex);
                record.answer = answer;
                record.correct = correct;
                record.answered = true;
            }
        }

        void recordSkip() {
            if (currentIndex < attempts.size()) {
                AttemptRecord record = attempts.get(currentIndex);
                record.skipped = true;
                record.answered = true;
            }
        }

        void markHintUsed() {
            if (currentIndex < attempts.size()) {
                attempts.get(currentIndex).hintUsed = true;
            }
        }

        ExerciseSummaryResponse getSummary() {
            int correct = 0, skipped = 0, hintsUsed = 0;
            List<MissedWord> missedWords = new ArrayList<>();

            for (int i = 0; i < attempts.size(); i++) {
                AttemptRecord record = attempts.get(i);
                if (record.correct) correct++;
                if (record.skipped) skipped++;
                if (record.hintUsed) hintsUsed++;

                if (!record.correct && record.answered) {
                    QuestionData q = questions.get(i);
                    missedWords.add(new MissedWord(q.targetWord(), q.englishWord(), q.completeSentence()));
                }
            }

            double accuracy = questions.isEmpty() ? 0.0 : (correct * 100.0) / questions.size();
            return new ExerciseSummaryResponse(questions.size(), correct, skipped, hintsUsed,
                Math.round(accuracy * 10.0) / 10.0, missedWords);
        }
    }

    private static class AttemptRecord {
        String answer;
        boolean correct;
        boolean skipped;
        boolean hintUsed;
        boolean answered;
    }

    public record QuestionData(
        String sentenceWithBlank,
        String completeSentence,
        String targetWord,
        String englishWord,
        String englishTranslation
    ) {}
}

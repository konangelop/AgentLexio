package com.kensai.sandbox.lexio.ai.tools;

import com.kensai.sandbox.lexio.web.dto.exercise.*;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class VocabularyExerciseTools {

    private final Map<String, ExerciseState> activeExercises = new ConcurrentHashMap<>();

    private static final List<TopicInfo> AVAILABLE_TOPICS = List.of(
        new TopicInfo("jobs", "Arbeit und Beruf", "Jobs and Work", "A2"),
        new TopicInfo("hobbies", "Hobbys und Freizeit", "Hobbies and Free Time", "A1"),
        new TopicInfo("food", "Essen und Trinken", "Food and Drink", "A1"),
        new TopicInfo("travel", "Reisen und Urlaub", "Travel and Vacation", "A2"),
        new TopicInfo("daily_routine", "Tagesablauf", "Daily Routine", "A1"),
        new TopicInfo("family", "Familie und Freunde", "Family and Friends", "A1")
    );

    @Tool("""
        Generates a new vocabulary exercise with fill-in-the-blank sentences.
        Call this when the user wants to practice vocabulary on specific topics.
        Returns the exercise ID and the first question to present to the user.
        """)
    public ExerciseStartedResponse generateVocabularyExercise(
            @P("The vocabulary topics to practice, e.g., 'jobs', 'hobbies', 'food'. Can be one or multiple topics.")
            List<String> topics,
            @P("Number of sentences to generate. Default to 5 if user doesn't specify. Maximum is 10.")
            int numberOfQuestions) {

        log.info("Generating vocabulary exercise for topics: {}, questions: {}", topics, numberOfQuestions);

        int questionsCount = Math.min(Math.max(numberOfQuestions, 1), 10);
        String exerciseId = UUID.randomUUID().toString().substring(0, 8);
        List<QuestionData> questions = generateSampleSentences(topics, questionsCount);

        ExerciseState state = new ExerciseState(exerciseId, topics, questions);
        activeExercises.put(exerciseId, state);

        QuestionData firstQuestion = questions.get(0);
        return new ExerciseStartedResponse(
            exerciseId,
            1,
            questionsCount,
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

    @Tool("""
        Lists all available vocabulary topics that users can practice.
        Call this when the user asks what topics are available or wants suggestions.
        """)
    public List<TopicInfo> listAvailableTopics() {
        log.info("Listing available topics");
        return AVAILABLE_TOPICS;
    }

    private String normalizeAnswer(String answer) {
        if (answer == null) return "";
        String normalized = answer.trim().toLowerCase();
        normalized = normalized.replaceAll("^(der|die|das|ein|eine|einen|einem|einer)\\s+", "");
        return normalized;
    }

    private List<QuestionData> generateSampleSentences(List<String> topics, int count) {
        List<QuestionData> allSentences = new ArrayList<>();

        if (topics.stream().anyMatch(t -> t.toLowerCase().contains("job") || t.toLowerCase().contains("work") || t.toLowerCase().contains("beruf"))) {
            allSentences.addAll(List.of(
                new QuestionData(
                    "Mein Bruder arbeitet als ___ in einem Krankenhaus.",
                    "Mein Bruder arbeitet als Arzt in einem Krankenhaus.",
                    "Arzt", "doctor",
                    "My brother works as a ___ in a hospital."
                ),
                new QuestionData(
                    "Sie ist ___ und unterrichtet Mathematik an einer Schule.",
                    "Sie ist Lehrerin und unterrichtet Mathematik an einer Schule.",
                    "Lehrerin", "teacher",
                    "She is a ___ and teaches mathematics at a school."
                ),
                new QuestionData(
                    "Der ___ repariert jeden Tag viele Autos in seiner Werkstatt.",
                    "Der Mechaniker repariert jeden Tag viele Autos in seiner Werkstatt.",
                    "Mechaniker", "mechanic",
                    "The ___ repairs many cars every day in his workshop."
                ),
                new QuestionData(
                    "Meine Mutter arbeitet als ___ in einem großen Büro.",
                    "Meine Mutter arbeitet als Sekretärin in einem großen Büro.",
                    "Sekretärin", "secretary",
                    "My mother works as a ___ in a large office."
                ),
                new QuestionData(
                    "Nach dem Studium möchte er ___ werden und Häuser entwerfen.",
                    "Nach dem Studium möchte er Architekt werden und Häuser entwerfen.",
                    "Architekt", "architect",
                    "After his studies, he wants to become an ___ and design houses."
                )
            ));
        }

        if (topics.stream().anyMatch(t -> t.toLowerCase().contains("hobb") || t.toLowerCase().contains("freizeit"))) {
            allSentences.addAll(List.of(
                new QuestionData(
                    "In meiner Freizeit spiele ich gern ___ mit meinen Freunden.",
                    "In meiner Freizeit spiele ich gern Fußball mit meinen Freunden.",
                    "Fußball", "football/soccer",
                    "In my free time, I like to play ___ with my friends."
                ),
                new QuestionData(
                    "Am Wochenende gehe ich oft ___, um fit zu bleiben.",
                    "Am Wochenende gehe ich oft schwimmen, um fit zu bleiben.",
                    "schwimmen", "swimming",
                    "On weekends, I often go ___ to stay fit."
                ),
                new QuestionData(
                    "Mein liebstes Hobby ist das ___ von Büchern.",
                    "Mein liebstes Hobby ist das Lesen von Büchern.",
                    "Lesen", "reading",
                    "My favorite hobby is ___ books."
                ),
                new QuestionData(
                    "Sie ___ jeden Abend Klavier, weil sie Musik liebt.",
                    "Sie spielt jeden Abend Klavier, weil sie Musik liebt.",
                    "spielt", "plays",
                    "She ___ piano every evening because she loves music."
                ),
                new QuestionData(
                    "Im Sommer gehen wir oft ___ in den Bergen.",
                    "Im Sommer gehen wir oft wandern in den Bergen.",
                    "wandern", "hiking",
                    "In summer, we often go ___ in the mountains."
                )
            ));
        }

        if (topics.stream().anyMatch(t -> t.toLowerCase().contains("food") || t.toLowerCase().contains("essen"))) {
            allSentences.addAll(List.of(
                new QuestionData(
                    "Zum Frühstück esse ich gern ___ mit Butter und Marmelade.",
                    "Zum Frühstück esse ich gern Brot mit Butter und Marmelade.",
                    "Brot", "bread",
                    "For breakfast, I like to eat ___ with butter and jam."
                ),
                new QuestionData(
                    "In Deutschland trinkt man viel ___, besonders am Morgen.",
                    "In Deutschland trinkt man viel Kaffee, besonders am Morgen.",
                    "Kaffee", "coffee",
                    "In Germany, people drink a lot of ___, especially in the morning."
                ),
                new QuestionData(
                    "Mein Lieblingsessen ist ___ mit Pommes und Salat.",
                    "Mein Lieblingsessen ist Schnitzel mit Pommes und Salat.",
                    "Schnitzel", "schnitzel",
                    "My favorite food is ___ with fries and salad."
                ),
                new QuestionData(
                    "Kannst du mir bitte das ___ geben? Mein Essen ist nicht salzig genug.",
                    "Kannst du mir bitte das Salz geben? Mein Essen ist nicht salzig genug.",
                    "Salz", "salt",
                    "Can you please pass me the ___? My food isn't salty enough."
                ),
                new QuestionData(
                    "Zum Nachtisch möchte ich einen ___ mit Sahne.",
                    "Zum Nachtisch möchte ich einen Kuchen mit Sahne.",
                    "Kuchen", "cake",
                    "For dessert, I would like a ___ with cream."
                )
            ));
        }

        // Generic sentences as fallback
        if (allSentences.size() < count) {
            allSentences.addAll(List.of(
                new QuestionData(
                    "Das ___ ist heute sehr schön, wir sollten spazieren gehen.",
                    "Das Wetter ist heute sehr schön, wir sollten spazieren gehen.",
                    "Wetter", "weather",
                    "The ___ is very nice today, we should go for a walk."
                ),
                new QuestionData(
                    "Ich muss zum ___ gehen, um Milch und Eier zu kaufen.",
                    "Ich muss zum Supermarkt gehen, um Milch und Eier zu kaufen.",
                    "Supermarkt", "supermarket",
                    "I need to go to the ___ to buy milk and eggs."
                ),
                new QuestionData(
                    "Meine ___ ist sehr groß. Ich habe drei Brüder und zwei Schwestern.",
                    "Meine Familie ist sehr groß. Ich habe drei Brüder und zwei Schwestern.",
                    "Familie", "family",
                    "My ___ is very large. I have three brothers and two sisters."
                )
            ));
        }

        Collections.shuffle(allSentences);
        return allSentences.subList(0, Math.min(count, allSentences.size()));
    }

    // Inner classes for state management

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

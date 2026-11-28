package modules.query.domain.services;

import java.util.Locale;

/**
 * Feature 41: puntua un candidato del autocompletado frente a lo escrito, sin distinguir mayusculas. Mejor cuanto mas
 * alto: empieza por (300) > empieza una palabra de snake_case (200, "cre" en "user_created") > contiene (100) > tiene
 * las letras en orden (fuzzy, 1-99: "uca" en "user_created_at"). -1: no casa. Con prefijo vacio, todo casa (0).
 */
public final class FuzzyMatcher {

    // de mas a menos: igual, empieza por lo escrito, lo escrito empieza una palabra (tras "_"), lo contiene; las
    // letras sueltas en orden puntuan por debajo de CONTAINS_SCORE
    private static final int EXACT_SCORE = 400;
    private static final int PREFIX_SCORE = 300;
    private static final int WORD_PREFIX_SCORE = 200;
    private static final int CONTAINS_SCORE = 100;
    private static final int NO_MATCH_SCORE = -1;
    private static final int LETTER_SCORE = 1;
    private static final int CONSECUTIVE_BONUS = 3;
    private static final int WORD_START_BONUS = 5;
    private static final char WORD_SEPARATOR = '_';

    private FuzzyMatcher() {
    }

    public static FuzzyMatcher getInstance() {
        return new FuzzyMatcher();
    }

    public int getScore(String candidate, String typed) {
        if (typed.isEmpty()) return 0;
        String lowerCandidate = candidate.toLowerCase(Locale.ROOT);
        String lowerTyped = typed.toLowerCase(Locale.ROOT);
        if (lowerCandidate.equals(lowerTyped)) return EXACT_SCORE;
        if (lowerCandidate.startsWith(lowerTyped)) return PREFIX_SCORE;
        if (lowerCandidate.contains(WORD_SEPARATOR + lowerTyped)) return WORD_PREFIX_SCORE;
        if (lowerCandidate.contains(lowerTyped)) return CONTAINS_SCORE;
        return getSubsequenceScore(lowerCandidate, lowerTyped);
    }

    /**
     * Las letras en orden; puntua mas si van juntas o tras un "_".
     */
    private int getSubsequenceScore(String candidate, String typed) {
        int score = 0;
        int candidateIndex = 0;
        int previousMatch = -2;
        for (char typedChar : typed.toCharArray()) {
            int found = candidate.indexOf(typedChar, candidateIndex);
            if (found == -1) return NO_MATCH_SCORE;
            if (found == previousMatch + 1) score += CONSECUTIVE_BONUS;
            if (found == 0 || candidate.charAt(found - 1) == WORD_SEPARATOR) score += WORD_START_BONUS;
            score += LETTER_SCORE;
            previousMatch = found;
            candidateIndex = found + 1;
        }
        return Math.min(CONTAINS_SCORE - 1, score);
    }
}

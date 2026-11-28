package modules.query.domain.services;

import java.util.Locale;

/**
 * Feature 41: puntua un candidato del autocompletado frente a lo escrito, sin distinguir mayusculas. Mejor cuanto mas
 * alto: empieza por (300) > empieza una palabra de snake_case (200, "cre" en "user_created") > contiene (100) > tiene
 * las letras en orden (fuzzy, 1-99: "uca" en "user_created_at"). -1: no casa. Con prefijo vacio, todo casa (0).
 */
public final class FuzzyMatcher {

    private FuzzyMatcher() {
    }

    public static FuzzyMatcher getInstance() {
        return new FuzzyMatcher();
    }

    public int getScore(String candidate, String typed) {
        if (typed.isEmpty()) return 0;
        String lowerCandidate = candidate.toLowerCase(Locale.ROOT);
        String lowerTyped = typed.toLowerCase(Locale.ROOT);
        if (lowerCandidate.equals(lowerTyped)) return 400;
        if (lowerCandidate.startsWith(lowerTyped)) return 300;
        if (lowerCandidate.contains("_" + lowerTyped)) return 200;
        if (lowerCandidate.contains(lowerTyped)) return 100;
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
            if (found == -1) return -1;
            if (found == previousMatch + 1) score += 3;
            if (found == 0 || candidate.charAt(found - 1) == '_') score += 5;
            score += 1;
            previousMatch = found;
            candidateIndex = found + 1;
        }
        return Math.min(99, score);
    }
}

package com.example.plms.core.credit;

/**
 * Maps a synthetic credit score to a configurable band. Scores outside the valid range
 * are treated as malformed provider responses, never as low scores.
 */
public final class ScoreBands {

    public static final int MIN_SCORE = 300;
    public static final int MAX_SCORE = 900;

    private ScoreBands() {
    }

    public static boolean isValid(Integer score) {
        return score != null && score >= MIN_SCORE && score <= MAX_SCORE;
    }

    public static String band(int score) {
        if (!isValid(score)) {
            throw new IllegalArgumentException("Score outside synthetic range");
        }
        if (score >= 750) {
            return "EXCELLENT";
        }
        if (score >= 700) {
            return "GOOD";
        }
        if (score >= 650) {
            return "FAIR";
        }
        if (score >= 600) {
            return "WEAK";
        }
        return "POOR";
    }
}

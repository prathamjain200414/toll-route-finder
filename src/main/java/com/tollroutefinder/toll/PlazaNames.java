package com.tollroutefinder.toll;

import java.util.Locale;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * Fuzzy comparison of toll plaza names. The same plaza is spelled many ways across sources (the NHAI list,
 * FASTag statements, transporter invoices): "Kulumapalya" / "Kulumepalya", "Banglaore-Nelamangala Toll Plaza" /
 * "Nelamangala", "Khed-Shivapur" / "Khedshivapur".
 */
public final class PlazaNames {

    private static final Pattern NOISE_WORDS = Pattern.compile("\\b(toll|plaza|plaz|booth|gate|tollway)\\b");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");
    private static final int MAX_TYPO_DISTANCE = 2;
    static final int MIN_LENGTH_FOR_FUZZY_MATCH = 5;

    /** Score for "one name contains the other": weaker evidence than a near-exact spelling. */
    public static final int CONTAINMENT_SCORE = MAX_TYPO_DISTANCE + 1;

    private PlazaNames() {
    }

    /**
     * Lower-cases, removes words like "toll plaza"/"booth"/"gate" and strips punctuation, so that
     * "Khed-Shivapur Toll Plaza" and "Khedshivapur Toll Plaza" compare equal. "nan" and blanks become empty.
     */
    public static String normalise(String name) {
        if (name == null) {
            return "";
        }
        String lower = name.trim().toLowerCase(Locale.ROOT);
        if (lower.equals("nan") || lower.equals("null")) {
            return "";
        }
        return NON_ALPHANUMERIC.matcher(NOISE_WORDS.matcher(lower).replaceAll("")).replaceAll("");
    }

    /**
     * How closely two already-normalised names match: 0 for identical, 1-2 for a small typo,
     * {@link #CONTAINMENT_SCORE} when one contains the other, empty when they are different names.
     * Short names (under 5 characters) must match exactly; fuzzy-matching them produces false positives.
     */
    public static OptionalInt matchScore(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return OptionalInt.empty();
        }
        if (a.equals(b)) {
            return OptionalInt.of(0);
        }
        if (Math.min(a.length(), b.length()) < MIN_LENGTH_FOR_FUZZY_MATCH) {
            return OptionalInt.empty();
        }
        int distance = levenshtein(a, b);
        if (distance <= MAX_TYPO_DISTANCE) {
            return OptionalInt.of(distance);
        }
        return a.contains(b) || b.contains(a) ? OptionalInt.of(CONTAINMENT_SCORE) : OptionalInt.empty();
    }

    /**
     * {@link #matchScore} that also accepts transliteration variants common in Indian place names, by comparing
     * phonetically folded spellings as well: "Karajeevanahalli" / "Karjeevanhally", "Vanagari" / "Vanagera".
     * Meant for matching against a short list of candidates (the plazas on one route), not for merging records.
     */
    public static OptionalInt lenientMatchScore(String a, String b) {
        OptionalInt strict = matchScore(a, b);
        if (strict.isPresent()) {
            return strict;
        }
        OptionalInt folded = matchScore(fold(a), fold(b));
        // Rank folded matches after every strict match.
        return folded.isPresent() ? OptionalInt.of(CONTAINMENT_SCORE + 1 + folded.getAsInt()) : OptionalInt.empty();
    }

    /**
     * Folds spelling choices that transliteration makes arbitrarily: y/i, w/v, ee/i, oo/u, doubled letters, and the
     * inherent "a" between consonants that some sources write and others drop.
     */
    static String fold(String normalised) {
        String s = normalised.replace('y', 'i').replace('w', 'v')
                .replace("ee", "i").replace("oo", "u");
        s = s.replaceAll("(.)\\1+", "$1");
        if (s.length() < 2) {
            return s;
        }
        StringBuilder out = new StringBuilder().append(s.charAt(0));
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean inner = i < s.length() - 1;
            if (c == 'a' && inner && !isVowel(s.charAt(i - 1)) && !isVowel(s.charAt(i + 1))) {
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean isVowel(char c) {
        return "aeiou".indexOf(c) >= 0;
    }

    public static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}

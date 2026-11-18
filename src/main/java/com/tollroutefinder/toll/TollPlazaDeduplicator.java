package com.tollroutefinder.toll;

import com.tollroutefinder.geo.GeoMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Collapses the many duplicate records in the source data into one record per physical toll plaza.
 * <p>
 * Two records are considered the same plaza when either:
 * <ul>
 *   <li>they are within {@code duplicateRadiusKm} of each other (same spot, whatever the name), or</li>
 *   <li>they are within {@code sameNameRadiusKm} and their normalised names match or differ only by a small typo.</li>
 * </ul>
 * The first named record in a cluster provides the name and coordinates, so the output is deterministic.
 * Unnamed records ("nan", blank) join a nearby named plaza if there is one.
 */
public class TollPlazaDeduplicator {

    private static final Pattern NOISE_WORDS = Pattern.compile("\\b(toll|plaza|plaz|booth|gate|tollway)\\b");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");
    private static final int MAX_TYPO_DISTANCE = 2;
    private static final int MIN_LENGTH_FOR_TYPO_MATCH = 5;

    private final double duplicateRadiusKm;
    private final double sameNameRadiusKm;

    public TollPlazaDeduplicator(double duplicateRadiusKm, double sameNameRadiusKm) {
        this.duplicateRadiusKm = duplicateRadiusKm;
        this.sameNameRadiusKm = sameNameRadiusKm;
    }

    public Result deduplicate(List<RawTollPlaza> rows) {
        List<Cluster> clusters = new ArrayList<>();
        for (RawTollPlaza row : rows) {
            String normalised = normalise(row.name());
            Cluster match = findCluster(clusters, row, normalised);
            if (match == null) {
                clusters.add(new Cluster(row, normalised));
            } else {
                match.absorb(row, normalised);
            }
        }
        List<TollPlaza> plazas = clusters.stream().map(Cluster::toPlaza).toList();
        return new Result(plazas, rows.size() - plazas.size());
    }

    private Cluster findCluster(List<Cluster> clusters, RawTollPlaza row, String normalised) {
        for (Cluster cluster : clusters) {
            double distance = GeoMath.haversineKm(cluster.representative.location(), row.location());
            if (distance <= duplicateRadiusKm) {
                return cluster;
            }
            if (distance <= sameNameRadiusKm && namesMatch(cluster.normalisedName, normalised)) {
                return cluster;
            }
        }
        return null;
    }

    private static boolean namesMatch(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        return Math.min(a.length(), b.length()) >= MIN_LENGTH_FOR_TYPO_MATCH
                && levenshtein(a, b) <= MAX_TYPO_DISTANCE;
    }

    /**
     * Lower-cases, removes words like "toll plaza"/"booth"/"gate" and strips punctuation, so that
     * "Khed-Shivapur Toll Plaza" and "Khedshivapur Toll Plaza" compare equal. "nan" and blanks become empty.
     */
    static String normalise(String name) {
        if (name == null) {
            return "";
        }
        String lower = name.trim().toLowerCase(Locale.ROOT);
        if (lower.equals("nan") || lower.equals("null")) {
            return "";
        }
        return NON_ALPHANUMERIC.matcher(NOISE_WORDS.matcher(lower).replaceAll("")).replaceAll("");
    }

    static int levenshtein(String a, String b) {
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

    static String keyFor(String normalisedName, double lat, double lon) {
        String base = normalisedName.isEmpty() ? "unnamed" : normalisedName;
        return String.format(Locale.ROOT, "%s@%.4f,%.4f", base, lat, lon);
    }

    private static final class Cluster {
        private RawTollPlaza representative;
        private String normalisedName;

        Cluster(RawTollPlaza first, String normalisedName) {
            this.representative = first;
            this.normalisedName = normalisedName;
        }

        void absorb(RawTollPlaza row, String normalised) {
            // An unnamed cluster takes on the first real name that joins it.
            if (normalisedName.isEmpty() && !normalised.isEmpty()) {
                representative = row;
                normalisedName = normalised;
            }
        }

        TollPlaza toPlaza() {
            String name = normalisedName.isEmpty() ? TollPlaza.UNNAMED : representative.name().trim();
            double lat = representative.location().latitude();
            double lon = representative.location().longitude();
            return new TollPlaza(keyFor(normalisedName, lat, lon), name, representative.location(),
                    representative.state() == null ? null : representative.state().trim());
        }
    }

    public record Result(List<TollPlaza> plazas, int duplicatesRemoved) {
    }
}

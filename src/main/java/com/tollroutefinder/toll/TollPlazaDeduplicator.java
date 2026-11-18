package com.tollroutefinder.toll;

import com.tollroutefinder.geo.GeoMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

/**
 * Collapses the many duplicate records in the source data into one record per physical toll plaza.
 * <p>
 * Two records are considered the same plaza when either:
 * <ul>
 *   <li>they are within {@code duplicateRadiusKm} of each other (same spot, whatever the name), or</li>
 *   <li>they are within {@code sameNameRadiusKm} and their normalised names match, differ only by a small typo,
 *       or (within {@value #CONTAINED_NAME_RADIUS_KM} km) one contains the other, e.g. "Banglaore-Nelamangala"
 *       vs "Nelamangala". Containment is weaker evidence (a bare city name), hence the tighter radius.</li>
 * </ul>
 * The first named record in a cluster provides the name and coordinates, so the output is deterministic.
 * Unnamed records ("nan", blank) join a nearby named plaza if there is one. Name comparison is in {@link PlazaNames}.
 */
public class TollPlazaDeduplicator {

    static final double CONTAINED_NAME_RADIUS_KM = 1.0;

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
            if (distance <= sameNameRadiusKm && namesMatch(cluster.normalisedName, normalised, distance)) {
                return cluster;
            }
        }
        return null;
    }

    private static boolean namesMatch(String a, String b, double distanceKm) {
        OptionalInt score = PlazaNames.matchScore(a, b);
        return score.isPresent()
                && (score.getAsInt() < PlazaNames.CONTAINMENT_SCORE || distanceKm <= CONTAINED_NAME_RADIUS_KM);
    }

    static String normalise(String name) {
        return PlazaNames.normalise(name);
    }

    static int levenshtein(String a, String b) {
        return PlazaNames.levenshtein(a, b);
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

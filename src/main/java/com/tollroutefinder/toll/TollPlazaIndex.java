package com.tollroutefinder.toll;

import com.tollroutefinder.geo.GeoMath;
import com.tollroutefinder.geo.GeoPoint;
import com.tollroutefinder.geo.RouteLine;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.index.strtree.STRtree;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory spatial index (Sort-Tile-Recursive R-tree) over all toll plazas.
 * <p>
 * A long route has ~10k points and India has ~1.5k plazas; checking every plaza against every segment would be
 * ~15M distance computations per request. The R-tree lets us fetch only plazas near the route in a few
 * hundred cheap envelope queries, after which exact distances are computed for just those candidates.
 * <p>
 * The tree is immutable once built and is swapped atomically, so concurrent reads need no locking.
 */
@Component
public class TollPlazaIndex {

    /** Consecutive route points grouped into one envelope query. Trades query count against envelope size. */
    private static final int POINTS_PER_QUERY = 25;

    private volatile Snapshot snapshot = Snapshot.empty();

    public void replaceAll(Collection<TollPlaza> plazas) {
        STRtree tree = new STRtree();
        for (TollPlaza plaza : plazas) {
            GeoPoint p = plaza.location();
            tree.insert(new Envelope(p.longitude(), p.longitude(), p.latitude(), p.latitude()), plaza);
        }
        tree.build(); // build eagerly: lazy building on first query is not thread-safe
        this.snapshot = new Snapshot(tree, List.copyOf(plazas));
    }

    public int size() {
        return snapshot.plazas().size();
    }

    /** Every indexed plaza, for lookups that are not spatial (e.g. by name). */
    public List<TollPlaza> all() {
        return snapshot.plazas();
    }

    /**
     * Returns every plaza that might be within {@code bufferKm} of the route (a superset, never missing one).
     */
    public List<TollPlaza> candidatesNear(RouteLine route, double bufferKm) {
        Snapshot current = this.snapshot;
        if (current.plazas().isEmpty()) {
            return List.of();
        }
        double buffer = GeoMath.kmToDegreeBuffer(bufferKm);
        List<GeoPoint> points = route.points();
        Map<TollPlaza, Boolean> found = new IdentityHashMap<>();
        for (int start = 0; start < points.size() - 1; start += POINTS_PER_QUERY) {
            int end = Math.min(start + POINTS_PER_QUERY, points.size() - 1);
            Envelope envelope = new Envelope();
            for (int i = start; i <= end; i++) {
                envelope.expandToInclude(points.get(i).longitude(), points.get(i).latitude());
            }
            envelope.expandBy(buffer);
            for (Object hit : current.tree().query(envelope)) {
                found.put((TollPlaza) hit, Boolean.TRUE);
            }
        }
        return new ArrayList<>(found.keySet());
    }

    private record Snapshot(STRtree tree, List<TollPlaza> plazas) {
        static Snapshot empty() {
            STRtree tree = new STRtree();
            tree.build();
            return new Snapshot(tree, List.of());
        }
    }
}

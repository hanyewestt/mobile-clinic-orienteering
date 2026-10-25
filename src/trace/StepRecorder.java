package trace;

import java.util.List;
import java.util.Map;

/**
 * Receives every step a solver takes, in a form that does not depend on the
 * algorithm. The step player in {@code web-visual/step-player/} replays these
 * events; see {@code web-visual/step-player/TRACE_FORMAT.md}.
 *
 * <p>All methods default to doing nothing, so solvers can call them
 * unconditionally. Recording must never change a solver's decisions.
 */
public interface StepRecorder {
    StepRecorder NONE = new StepRecorder() {
    };

    /** Outcomes of a {@link #consider} step. The player colours each one differently. */
    String ACCEPT = "accept";
    String DEFER = "defer";
    String REQUEUE = "requeue";
    String SKIP = "skip";
    String REJECT = "reject";

    /** Grid-cell states for {@link #cell}. */
    String CELL_OPEN = "open";
    String CELL_EMPTY = "empty";

    /** False lets a solver skip work it does only for the trace, such as listing newly served towns. */
    default boolean enabled() {
        return false;
    }

    /** Starts a named stage of the algorithm, such as selection, polishing or refill. */
    default void phase(String name, String text) {
    }

    /** Free-text explanation that changes no state. */
    default void note(String text) {
    }

    /**
     * The solver looked at one candidate site and decided what to do with it.
     * Use a negative {@code position} or {@code NaN} distances when not known;
     * use a negative {@code rivalSiteId} when there was no rival.
     */
    default void consider(
            int siteId,
            long gain,
            double addedKm,
            int position,
            double score,
            String outcome,
            int rivalSiteId,
            double rivalScore,
            String text) {
        consider(siteId, gain, addedKm, position, score, outcome, rivalSiteId, rivalScore, -1, text);
    }

    /** As above, for a solver that groups sites into the grid cells declared by {@link #grid}. */
    default void consider(
            int siteId,
            long gain,
            double addedKm,
            int position,
            double score,
            String outcome,
            int rivalSiteId,
            double rivalScore,
            int cellId,
            String text) {
    }

    /** A site joins the route at {@code position} and serves the listed towns for the first time. */
    default void insert(
            int siteId,
            int position,
            double addedKm,
            long gain,
            int[] newlyServedTownIds,
            double routeKm,
            String text) {
    }

    /** The whole route was replaced, for example by a 2-opt move. */
    default void route(List<Integer> stopIds, double routeKm, String text) {
    }

    /**
     * A site is chosen before any routing happens (select-then-route solvers).
     * The listed towns become planned coverage, not served coverage.
     */
    default void select(int siteId, long gain, int[] newlyPlannedTownIds, int cellId, String text) {
    }

    /**
     * Declares the grid overlay. Each cell is {@code [minLat, minLon, maxLat, maxLon]};
     * its index in the list is the cell id used by later events.
     */
    default void grid(double cellSideKm, List<double[]> cellBounds, String text) {
    }

    /** A cell's best site and gain were recomputed. Use a negative site id when the cell has none left. */
    default void cell(int cellId, int bestSiteId, long gain, String status, String text) {
    }

    /** Final totals reported by the solver, so the player can check its replay against them. */
    default void result(long populationCovered, int townsCovered, double routeKm, int stops, long runtimeMillis) {
        result(populationCovered, townsCovered, routeKm, stops, runtimeMillis, Map.of());
    }

    /** As above, with algorithm-specific counters for the dashboard (insertion order is kept). */
    default void result(
            long populationCovered,
            int townsCovered,
            double routeKm,
            int stops,
            long runtimeMillis,
            Map<String, ? extends Number> statistics) {
    }
}

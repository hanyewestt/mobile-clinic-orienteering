package algorithms;

import coverage.CoverageIndex;
import coverage.CoverageTracker;
import geometry.Haversine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import model.CandidateStop;
import model.ProblemInstance;
import model.Route;
import model.RunResult;
import routing.CheapestInsertion;
import routing.Insertion;
import routing.RouteCalculator;
import routing.TwoOptPolisher;
import routing.TwoOptResult;

/**
 * Approach A: select stops by marginal covered population per cheapest added
 * route distance. Scores can be refreshed lazily or fully after each selection.
 */
public final class LazyGreedySolver implements RoutingSolver {
    private static final double SCORE_EPSILON = 1.0e-9;

    private final ScoreRefreshMode refreshMode;
    private final int maximumTwoOptPasses;

    public LazyGreedySolver(ScoreRefreshMode refreshMode, int maximumTwoOptPasses) {
        this.refreshMode = Objects.requireNonNull(refreshMode, "refreshMode");
        if (maximumTwoOptPasses < 0) {
            throw new IllegalArgumentException("Maximum 2-opt pass count must not be negative.");
        }
        this.maximumTwoOptPasses = maximumTwoOptPasses;
    }

    @Override
    public RunResult solve(ProblemInstance instance, CoverageIndex coverageIndex) {
        return solveDetailed(instance, coverageIndex).runResult();
    }

    public ApproachAResult solveDetailed(ProblemInstance instance, CoverageIndex coverageIndex) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(coverageIndex, "coverageIndex");
        validateCoverageIndex(instance, coverageIndex);

        long startedAtNanos = System.nanoTime();
        CoverageTracker coverageTracker = new CoverageTracker(coverageIndex, instance.demandTowns());
        boolean[] accepted = new boolean[instance.candidateStops().size()];
        List<Integer> routeStopIds = List.of();
        Route route = RouteCalculator.createRoute(routeStopIds, instance.depot(), instance.candidateStops());
        List<Integer> deferred = new ArrayList<>();
        MutableStatistics statistics = new MutableStatistics();

        if (refreshMode == ScoreRefreshMode.LAZY) {
            route = solveLazily(instance, coverageTracker, accepted, deferred, route, statistics);
        } else {
            route = solveFully(instance, coverageTracker, accepted, deferred, route, statistics);
        }

        TwoOptResult polished = TwoOptPolisher.polish(
                route, instance.depot(), instance.candidateStops(), maximumTwoOptPasses);
        route = polished.route();

        RefillOutcome refillOutcome = refillDeferred(
                instance, coverageTracker, accepted, deferred, route, statistics);
        route = refillOutcome.route();

        long runtimeMillis = (System.nanoTime() - startedAtNanos) / 1_000_000;
        RunResult runResult = new RunResult(
                algorithmName(),
                route,
                coverageTracker.coveredPopulation(),
                coverageTracker.servedTownCount(),
                runtimeMillis);
        return new ApproachAResult(
                runResult,
                statistics.candidateEvaluations,
                statistics.heapPops,
                countAccepted(accepted),
                deferred.size(),
                refillOutcome.refilledStops(),
                polished.improvements());
    }

    private Route solveLazily(
            ProblemInstance instance,
            CoverageTracker coverageTracker,
            boolean[] accepted,
            List<Integer> deferred,
            Route route,
            MutableStatistics statistics) {
        PriorityQueue<CandidateScore> heap = new PriorityQueue<>(
                Comparator.comparingDouble(CandidateScore::score).reversed());

        for (CandidateStop candidateStop : instance.candidateStops()) {
            long gain = coverageTracker.marginalPopulationGain(candidateStop.id());
            if (gain > 0) {
                double initialCost = 2.0 * Haversine.distanceKm(instance.depot(), candidateStop.coordinate());
                heap.add(new CandidateScore(candidateStop.id(), score(gain, initialCost)));
            }
        }

        while (!heap.isEmpty()) {
            CandidateScore entry = heap.remove();
            statistics.heapPops++;
            int candidateStopId = entry.candidateStopId();
            if (accepted[candidateStopId]) {
                continue;
            }

            long gain = coverageTracker.marginalPopulationGain(candidateStopId);
            statistics.candidateEvaluations++;
            if (gain == 0) {
                continue;
            }

            Insertion insertion = CheapestInsertion.findBest(
                    route.stopIds(), candidateStopId, instance.depot(), instance.candidateStops());
            if (!RouteCalculator.fitsBudget(
                    route.totalDistanceKm(), insertion.addedDistanceKm(), instance.travelBudgetKm())) {
                deferred.add(candidateStopId);
                continue;
            }

            double refreshedScore = score(gain, insertion.addedDistanceKm());
            if (heap.isEmpty() || refreshedScore >= heap.peek().score()) {
                route = acceptStop(
                        route, candidateStopId, insertion, instance, coverageTracker, accepted);
            } else {
                heap.add(new CandidateScore(candidateStopId, refreshedScore));
            }
        }

        return route;
    }

    private Route solveFully(
            ProblemInstance instance,
            CoverageTracker coverageTracker,
            boolean[] accepted,
            List<Integer> deferred,
            Route route,
            MutableStatistics statistics) {
        while (true) {
            CandidateChoice bestChoice = null;
            for (CandidateStop candidateStop : instance.candidateStops()) {
                int candidateStopId = candidateStop.id();
                if (accepted[candidateStopId]) {
                    continue;
                }

                long gain = coverageTracker.marginalPopulationGain(candidateStopId);
                statistics.candidateEvaluations++;
                if (gain == 0) {
                    continue;
                }

                Insertion insertion = CheapestInsertion.findBest(
                        route.stopIds(), candidateStopId, instance.depot(), instance.candidateStops());
                if (!RouteCalculator.fitsBudget(
                        route.totalDistanceKm(), insertion.addedDistanceKm(), instance.travelBudgetKm())) {
                    continue;
                }

                CandidateChoice choice = new CandidateChoice(
                        candidateStopId, insertion, score(gain, insertion.addedDistanceKm()));
                if (bestChoice == null || choice.score() > bestChoice.score()) {
                    bestChoice = choice;
                }
            }

            if (bestChoice == null) {
                addOverBudgetCandidates(instance, coverageTracker, accepted, route, deferred, statistics);
                return route;
            }

            route = acceptStop(
                    route,
                    bestChoice.candidateStopId(),
                    bestChoice.insertion(),
                    instance,
                    coverageTracker,
                    accepted);
        }
    }

    private void addOverBudgetCandidates(
            ProblemInstance instance,
            CoverageTracker coverageTracker,
            boolean[] accepted,
            Route route,
            List<Integer> deferred,
            MutableStatistics statistics) {
        for (CandidateStop candidateStop : instance.candidateStops()) {
            int candidateStopId = candidateStop.id();
            if (accepted[candidateStopId] || coverageTracker.marginalPopulationGain(candidateStopId) == 0) {
                continue;
            }
            statistics.candidateEvaluations++;
            Insertion insertion = CheapestInsertion.findBest(
                    route.stopIds(), candidateStopId, instance.depot(), instance.candidateStops());
            if (!RouteCalculator.fitsBudget(
                    route.totalDistanceKm(), insertion.addedDistanceKm(), instance.travelBudgetKm())) {
                deferred.add(candidateStopId);
            }
        }
    }

    private RefillOutcome refillDeferred(
            ProblemInstance instance,
            CoverageTracker coverageTracker,
            boolean[] accepted,
            List<Integer> deferred,
            Route route,
            MutableStatistics statistics) {
        int refilledStops = 0;
        while (true) {
            CandidateChoice bestChoice = null;
            for (int candidateStopId : deferred) {
                if (accepted[candidateStopId]) {
                    continue;
                }
                long gain = coverageTracker.marginalPopulationGain(candidateStopId);
                statistics.candidateEvaluations++;
                if (gain == 0) {
                    continue;
                }
                Insertion insertion = CheapestInsertion.findBest(
                        route.stopIds(), candidateStopId, instance.depot(), instance.candidateStops());
                if (!RouteCalculator.fitsBudget(
                        route.totalDistanceKm(), insertion.addedDistanceKm(), instance.travelBudgetKm())) {
                    continue;
                }

                CandidateChoice choice = new CandidateChoice(
                        candidateStopId, insertion, score(gain, insertion.addedDistanceKm()));
                if (bestChoice == null || choice.score() > bestChoice.score()) {
                    bestChoice = choice;
                }
            }

            if (bestChoice == null) {
                return new RefillOutcome(route, refilledStops);
            }

            route = acceptStop(
                    route,
                    bestChoice.candidateStopId(),
                    bestChoice.insertion(),
                    instance,
                    coverageTracker,
                    accepted);
            refilledStops++;
        }
    }

    private static Route acceptStop(
            Route route,
            int candidateStopId,
            Insertion insertion,
            ProblemInstance instance,
            CoverageTracker coverageTracker,
            boolean[] accepted) {
        List<Integer> updatedStopIds = RouteCalculator.insertStop(
                route.stopIds(), candidateStopId, insertion.position());
        coverageTracker.applyStop(candidateStopId);
        accepted[candidateStopId] = true;
        return RouteCalculator.createRoute(updatedStopIds, instance.depot(), instance.candidateStops());
    }

    private static double score(long gain, double additionalDistanceKm) {
        return gain / (additionalDistanceKm + SCORE_EPSILON);
    }

    private static int countAccepted(boolean[] accepted) {
        int acceptedCount = 0;
        for (boolean wasAccepted : accepted) {
            if (wasAccepted) {
                acceptedCount++;
            }
        }
        return acceptedCount;
    }

    private static void validateCoverageIndex(ProblemInstance instance, CoverageIndex coverageIndex) {
        if (coverageIndex.candidateStopCount() != instance.candidateStops().size()) {
            throw new IllegalArgumentException("Coverage index and candidate-stop list have different sizes.");
        }
        if (coverageIndex.demandTownCount() != instance.demandTowns().size()) {
            throw new IllegalArgumentException("Coverage index and demand-town list have different sizes.");
        }
    }

    private String algorithmName() {
        return refreshMode == ScoreRefreshMode.LAZY
                ? "Approach A - route-aware lazy greedy"
                : "Approach A - route-aware full greedy";
    }

    private record CandidateScore(int candidateStopId, double score) {
    }

    private record CandidateChoice(int candidateStopId, Insertion insertion, double score) {
    }

    private record RefillOutcome(Route route, int refilledStops) {
    }

    private static final class MutableStatistics {
        private long candidateEvaluations;
        private long heapPops;
    }
}

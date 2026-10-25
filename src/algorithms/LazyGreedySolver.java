package algorithms;

import coverage.CoverageChange;
import coverage.CoverageIndex;
import coverage.CoverageTracker;
import geometry.Haversine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import trace.StepRecorder;

/**
 * Approach A: select stops by marginal covered population per cheapest added
 * route distance. Scores can be refreshed lazily or fully after each selection.
 */
public final class LazyGreedySolver implements RoutingSolver {
    private static final double SCORE_EPSILON = 1.0e-9;

    private final ScoreRefreshMode refreshMode;
    private final int maximumTwoOptPasses;
    private final boolean recordDecisionTrace;
    private final StepRecorder steps;

    public LazyGreedySolver(ScoreRefreshMode refreshMode, int maximumTwoOptPasses) {
        this(refreshMode, maximumTwoOptPasses, false);
    }

    /**
     * The optional trace audits decisions without contributing to their score or selection.
     */
    public LazyGreedySolver(
            ScoreRefreshMode refreshMode,
            int maximumTwoOptPasses,
            boolean recordDecisionTrace) {
        this(refreshMode, maximumTwoOptPasses, recordDecisionTrace, StepRecorder.NONE);
    }

    /**
     * The step recorder receives every heap pop and route change for the step
     * player. Like the decision trace, it never influences selection.
     */
    public LazyGreedySolver(
            ScoreRefreshMode refreshMode,
            int maximumTwoOptPasses,
            boolean recordDecisionTrace,
            StepRecorder steps) {
        this.refreshMode = Objects.requireNonNull(refreshMode, "refreshMode");
        if (maximumTwoOptPasses < 0) {
            throw new IllegalArgumentException("Solver limits must not be negative.");
        }
        this.maximumTwoOptPasses = maximumTwoOptPasses;
        this.recordDecisionTrace = recordDecisionTrace;
        this.steps = Objects.requireNonNull(steps, "steps");
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
        List<DecisionTrace> decisionTrace = new ArrayList<>();
        MutableStatistics statistics = new MutableStatistics();

        if (refreshMode == ScoreRefreshMode.LAZY) {
            steps.phase("select", "Lazy greedy: pop the best-scoring site from a max-heap, re-score it on the "
                    + "current route, and accept it only if it still beats the next heap entry.");
            route = solveLazily(instance, coverageIndex, coverageTracker, accepted, deferred, route, statistics, decisionTrace);
        } else {
            steps.phase("select", "Full greedy: after every accepted stop, re-score every remaining site on the "
                    + "current route and take the single best one that fits the budget.");
            route = solveFully(instance, coverageIndex, coverageTracker, accepted, deferred, route, statistics, decisionTrace);
        }

        steps.phase("polish", "2-opt: reverse route segments when that shortens the tour (at most "
                + maximumTwoOptPasses + " passes, one move per pass).");
        TwoOptResult polished = TwoOptPolisher.polish(
                route, instance.depot(), instance.candidateStops(), maximumTwoOptPasses,
                steps.enabled() ? (stopIds, first, second, changeKm, lengthKm) -> steps.route(
                        stopIds, lengthKm, String.format(Locale.ROOT,
                                "2-opt reversed route positions %d to %d, saving %.3f km.",
                                first + 1, second + 1, -changeKm)) : null);
        route = polished.route();
        if (polished.improvements() == 0) {
            steps.note("2-opt found no move that shortens the route.");
        }

        steps.phase("refill", "Refill: retry the deferred sites with whatever budget is left, best score first.");
        RefillOutcome refillOutcome = refillDeferred(
                instance, coverageIndex, coverageTracker, accepted, deferred, route, statistics, decisionTrace);
        route = refillOutcome.route();

        long runtimeMillis = (System.nanoTime() - startedAtNanos) / 1_000_000;
        if (steps.enabled()) {
            Map<String, Number> counters = new LinkedHashMap<>();
            counters.put("Heap pops", statistics.heapPops);
            counters.put("Candidate evaluations", statistics.candidateEvaluations);
            counters.put("Deferred sites", deferred.size());
            counters.put("Refilled stops", refillOutcome.refilledStops());
            counters.put("2-opt improvements", polished.improvements());
            steps.result(coverageTracker.coveredPopulation(), coverageTracker.servedTownCount(),
                    route.totalDistanceKm(), route.stopIds().size(), runtimeMillis, counters);
        }
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
                polished.improvements(),
                decisionTrace);
    }

    private Route solveLazily(
            ProblemInstance instance,
            CoverageIndex coverageIndex,
            CoverageTracker coverageTracker,
            boolean[] accepted,
            List<Integer> deferred,
            Route route,
            MutableStatistics statistics,
            List<DecisionTrace> decisionTrace) {
        PriorityQueue<CandidateScore> heap = new PriorityQueue<>(
                Comparator.comparingDouble(CandidateScore::score).reversed());

        for (CandidateStop candidateStop : instance.candidateStops()) {
            long gain = coverageTracker.marginalPopulationGain(candidateStop.id());
            if (gain > 0) {
                double initialCost = 2.0 * Haversine.distanceKm(instance.depot(), candidateStop.coordinate());
                heap.add(new CandidateScore(candidateStop.id(), score(gain, initialCost)));
            }
        }
        steps.note(String.format(Locale.ROOT,
                "Heap built with %d sites that cover at least one town. Each starts with score = "
                        + "population within r / (2 x distance from depot), i.e. the cost of a solo round trip.",
                heap.size()));

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
                steps.consider(candidateStopId, 0, Double.NaN, -1, 0.0, StepRecorder.SKIP, -1, Double.NaN,
                        "Every town within r of this site is already served by earlier stops, so it is dropped.");
                continue;
            }

            Insertion insertion = CheapestInsertion.findBest(
                    route.stopIds(), candidateStopId, instance.depot(), instance.candidateStops());
            if (!RouteCalculator.fitsBudget(
                    route.totalDistanceKm(), insertion.addedDistanceKm(), instance.travelBudgetKm())) {
                deferred.add(candidateStopId);
                steps.consider(candidateStopId, gain, insertion.addedDistanceKm(), insertion.position(),
                        score(gain, insertion.addedDistanceKm()), StepRecorder.DEFER, -1, Double.NaN,
                        String.format(Locale.ROOT,
                                "Even its cheapest insertion would make the route %.1f km, over the %.1f km "
                                        + "budget. Moved to the deferred list and not pushed back.",
                                route.totalDistanceKm() + insertion.addedDistanceKm(), instance.travelBudgetKm()));
                continue;
            }

            double refreshedScore = score(gain, insertion.addedDistanceKm());
            if (heap.isEmpty() || refreshedScore >= heap.peek().score()) {
                if (steps.enabled()) {
                    CandidateScore rival = heap.peek();
                    steps.consider(candidateStopId, gain, insertion.addedDistanceKm(), insertion.position(),
                            refreshedScore, StepRecorder.ACCEPT,
                            rival == null ? -1 : rival.candidateStopId(),
                            rival == null ? Double.NaN : rival.score(),
                            rival == null
                                    ? "The heap is empty after this pop, so it is accepted."
                                    : "Its fresh score is still at least the best score left in the heap, "
                                            + "so (lazily) nothing else can beat it. Accepted.");
                }
                route = acceptStop(
                        route, candidateStopId, insertion, instance, coverageIndex, coverageTracker, accepted, decisionTrace);
            } else {
                if (steps.enabled()) {
                    CandidateScore rival = heap.peek();
                    steps.consider(candidateStopId, gain, insertion.addedDistanceKm(), insertion.position(),
                            refreshedScore, StepRecorder.REQUEUE, rival.candidateStopId(), rival.score(),
                            String.format(Locale.ROOT,
                                    "Its score was stale: now %.0f people/km, below the heap's next entry. "
                                            + "Pushed back with the fresh score.",
                                    refreshedScore));
                }
                heap.add(new CandidateScore(candidateStopId, refreshedScore));
            }
        }

        return route;
    }

    private Route solveFully(
            ProblemInstance instance,
            CoverageIndex coverageIndex,
            CoverageTracker coverageTracker,
            boolean[] accepted,
            List<Integer> deferred,
            Route route,
            MutableStatistics statistics,
            List<DecisionTrace> decisionTrace) {
        while (true) {
            CandidateChoice bestChoice = null;
            CandidateChoice runnerUp = null;
            int fittingCandidates = 0;
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

                fittingCandidates++;
                CandidateChoice choice = new CandidateChoice(
                        candidateStopId, insertion, score(gain, insertion.addedDistanceKm()));
                if (bestChoice == null || choice.score() > bestChoice.score()) {
                    runnerUp = bestChoice;
                    bestChoice = choice;
                } else if (runnerUp == null || choice.score() > runnerUp.score()) {
                    runnerUp = choice;
                }
            }

            if (bestChoice == null) {
                steps.note("No remaining site both adds coverage and fits the budget. Selection ends.");
                addOverBudgetCandidates(instance, coverageTracker, accepted, route, deferred, statistics);
                return route;
            }

            if (steps.enabled()) {
                steps.consider(bestChoice.candidateStopId(),
                        coverageTracker.marginalPopulationGain(bestChoice.candidateStopId()),
                        bestChoice.insertion().addedDistanceKm(), bestChoice.insertion().position(),
                        bestChoice.score(), StepRecorder.ACCEPT,
                        runnerUp == null ? -1 : runnerUp.candidateStopId(),
                        runnerUp == null ? Double.NaN : runnerUp.score(),
                        String.format(Locale.ROOT,
                                "Re-scored all %d sites that add coverage and fit the budget; this one has the "
                                        + "highest score. The rival shown is the runner-up.",
                                fittingCandidates));
            }
            route = acceptStop(
                    route,
                    bestChoice.candidateStopId(),
                    bestChoice.insertion(),
                    instance,
                    coverageIndex,
                    coverageTracker,
                    accepted,
                    decisionTrace);
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
                steps.consider(candidateStopId, coverageTracker.marginalPopulationGain(candidateStopId),
                        insertion.addedDistanceKm(), insertion.position(),
                        score(coverageTracker.marginalPopulationGain(candidateStopId), insertion.addedDistanceKm()),
                        StepRecorder.DEFER, -1, Double.NaN,
                        "Still adds coverage but does not fit the remaining budget. Kept for the refill phase.");
            }
        }
    }

    private RefillOutcome refillDeferred(
            ProblemInstance instance,
            CoverageIndex coverageIndex,
            CoverageTracker coverageTracker,
            boolean[] accepted,
            List<Integer> deferred,
            Route route,
            MutableStatistics statistics,
            List<DecisionTrace> decisionTrace) {
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
                steps.note(String.format(Locale.ROOT,
                        "None of the %d deferred sites both adds coverage and fits the %.2f km left. Done.",
                        deferred.size(), instance.travelBudgetKm() - route.totalDistanceKm()));
                return new RefillOutcome(route, refilledStops);
            }

            if (steps.enabled()) {
                steps.consider(bestChoice.candidateStopId(),
                        coverageTracker.marginalPopulationGain(bestChoice.candidateStopId()),
                        bestChoice.insertion().addedDistanceKm(), bestChoice.insertion().position(),
                        bestChoice.score(), StepRecorder.ACCEPT, -1, Double.NaN,
                        "Best-scoring deferred site that now fits the remaining budget.");
            }
            route = acceptStop(
                    route,
                    bestChoice.candidateStopId(),
                    bestChoice.insertion(),
                    instance,
                    coverageIndex,
                    coverageTracker,
                    accepted,
                    decisionTrace);
            refilledStops++;
        }
    }

    private Route acceptStop(
            Route route,
            int candidateStopId,
            Insertion insertion,
            ProblemInstance instance,
            CoverageIndex coverageIndex,
            CoverageTracker coverageTracker,
            boolean[] accepted,
            List<DecisionTrace> decisionTrace) {
        if (recordDecisionTrace) {
            decisionTrace.add(captureTrace(
                    decisionTrace.size() + 1,
                    route,
                    candidateStopId,
                    insertion,
                    instance,
                    coverageIndex,
                    coverageTracker,
                    accepted));
        }
        int[] newlyServedTownIds = steps.enabled()
                ? newlyServedTowns(candidateStopId, instance, coverageIndex, coverageTracker).stream()
                        .mapToInt(NewlyServedTown::demandTownId).toArray()
                : null;
        List<Integer> updatedStopIds = RouteCalculator.insertStop(
                route.stopIds(), candidateStopId, insertion.position());
        CoverageChange change = coverageTracker.applyStop(candidateStopId);
        accepted[candidateStopId] = true;
        Route updatedRoute = RouteCalculator.createRoute(updatedStopIds, instance.depot(), instance.candidateStops());
        if (steps.enabled()) {
            steps.insert(candidateStopId, insertion.position(), insertion.addedDistanceKm(),
                    change.newlyCoveredPopulation(), newlyServedTownIds, updatedRoute.totalDistanceKm(),
                    String.format(Locale.ROOT,
                            "Inserted at route position %d. %d new towns served; route is now %.1f of %.1f km.",
                            insertion.position() + 1, change.newlyCoveredTowns(),
                            updatedRoute.totalDistanceKm(), instance.travelBudgetKm()));
        }
        return updatedRoute;
    }

    private DecisionTrace captureTrace(
            int selectionStep,
            Route route,
            int chosenCandidateStopId,
            Insertion chosenInsertion,
            ProblemInstance instance,
            CoverageIndex coverageIndex,
            CoverageTracker coverageTracker,
            boolean[] accepted) {
        long populationBefore = coverageTracker.coveredPopulation();
        int townsBefore = coverageTracker.servedTownCount();
        List<NewlyServedTown> newlyServedTowns = newlyServedTowns(
                chosenCandidateStopId, instance, coverageIndex, coverageTracker);
        long chosenGain = newlyServedTowns.stream().mapToLong(NewlyServedTown::population).sum();
        double addedDistanceKm = chosenInsertion.addedDistanceKm();
        return new DecisionTrace(
                selectionStep,
                chosenCandidateStopId,
                chosenGain,
                newlyServedTowns.size(),
                chosenInsertion.position(),
                addedDistanceKm,
                score(chosenGain, addedDistanceKm),
                route.totalDistanceKm(),
                route.totalDistanceKm() + addedDistanceKm,
                populationBefore,
                populationBefore + chosenGain,
                townsBefore,
                townsBefore + newlyServedTowns.size(),
                newlyServedTowns);
    }

    private static List<NewlyServedTown> newlyServedTowns(
            int candidateStopId,
            ProblemInstance instance,
            CoverageIndex coverageIndex,
            CoverageTracker coverageTracker) {
        CandidateStop candidateStop = instance.candidateStops().get(candidateStopId);
        List<NewlyServedTown> towns = new ArrayList<>();
        int count = coverageIndex.coveredTownCount(candidateStopId);
        for (int position = 0; position < count; position++) {
            int townId = coverageIndex.coveredTownIdAt(candidateStopId, position);
            if (!coverageTracker.isServed(townId)) {
                var town = instance.demandTowns().get(townId);
                towns.add(new NewlyServedTown(
                        town.id(), town.geonameId(), town.name(), town.coordinate().latitude(),
                        town.coordinate().longitude(), town.population(),
                        Haversine.distanceKm(candidateStop.coordinate(), town.coordinate())));
            }
        }
        return towns;
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

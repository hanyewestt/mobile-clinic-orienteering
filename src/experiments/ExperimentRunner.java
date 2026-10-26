package experiments;

import algorithms.GridClusterSolver;
import algorithms.LazyGreedySolver;
import algorithms.ScoreRefreshMode;
import coverage.CoverageBuilder;
import coverage.CoverageIndex;
import data.ProcessedInstanceLoader;
import java.io.IOException;
import java.nio.file.Path;
import model.Coordinate;
import model.ProblemInstance;
import trace.StepRecorder;

/** Runs and exports reproducible Approach A and Approach B experiments. */
public final class ExperimentRunner {
    private static final Coordinate KUALA_LUMPUR_DEPOT = new Coordinate(3.1412, 101.6865);

    private ExperimentRunner() {
    }

    public static ExperimentOutcome runApproachA(ProblemInstance instance, ExperimentConfig config) throws IOException {
        if (Double.compare(instance.serviceRadiusKm(), config.serviceRadiusKm()) != 0
                || Double.compare(instance.travelBudgetKm(), config.travelBudgetKm()) != 0) {
            throw new IllegalArgumentException("Experiment config must match the problem instance radius and budget.");
        }
        CoverageIndex coverageIndex = CoverageBuilder.build(
                instance.candidateStops(), instance.demandTowns(), instance.serviceRadiusKm());
        LazyGreedySolver solver = new LazyGreedySolver(
                config.refreshMode(),
                config.maximumTwoOptPasses(),
                config.recordDecisionTrace());
        ExperimentOutcome outcome = new ExperimentOutcome(config, instance, solver.solveDetailed(instance, coverageIndex));
        ResultsWriter.writeApproachA(outcome);
        return outcome;
    }

    public static ExperimentOutcome runApproachAFromProcessedData(
            Path processedDirectory,
            ExperimentConfig config) throws IOException {
        ProblemInstance instance = ProcessedInstanceLoader.loadPeninsular(
                processedDirectory,
                KUALA_LUMPUR_DEPOT,
                config.serviceRadiusKm(),
                config.travelBudgetKm());
        return runApproachA(instance, config);
    }

    public static ApproachBOutcome runApproachB(ProblemInstance instance, ApproachBConfig config) throws IOException {
        if (Double.compare(instance.serviceRadiusKm(), config.serviceRadiusKm()) != 0
                || Double.compare(instance.travelBudgetKm(), config.travelBudgetKm()) != 0) {
            throw new IllegalArgumentException("Experiment config must match the problem instance radius and budget.");
        }
        CoverageIndex coverageIndex = CoverageBuilder.build(
                instance.candidateStops(), instance.demandTowns(), instance.serviceRadiusKm());
        GridClusterSolver solver = new GridClusterSolver(
                config.maximumTwoOptPasses(), config.pruneUnreachable(), StepRecorder.NONE);
        ApproachBOutcome outcome = new ApproachBOutcome(config, instance, solver.solveDetailed(instance, coverageIndex));
        ResultsWriter.writeApproachB(outcome);
        return outcome;
    }

    public static ApproachBOutcome runApproachBFromProcessedData(
            Path processedDirectory,
            ApproachBConfig config) throws IOException {
        ProblemInstance instance = ProcessedInstanceLoader.loadPeninsular(
                processedDirectory,
                KUALA_LUMPUR_DEPOT,
                config.serviceRadiusKm(),
                config.travelBudgetKm());
        return runApproachB(instance, config);
    }

    /**
     * Usage: ExperimentRunner <processed-data-dir> <output-dir> <radius-km>
     * <budget-km> <LAZY|FULL|B> [run-name] [--trace]
     *
     * <p>{@code --trace} adds Approach A's decision trace to result.json. Step
     * traces for the step player come from {@code trace.StepTraceMain} instead.
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 5 || args.length > 7) {
            throw new IllegalArgumentException(
                    "Usage: ExperimentRunner <processed-data-dir> <output-dir> <radius-km> "
                            + "<budget-km> <LAZY|FULL|B> [run-name] [--trace]");
        }

        double radiusKm = Double.parseDouble(args[2]);
        double budgetKm = Double.parseDouble(args[3]);
        boolean trace = args.length == 7 && "--trace".equals(args[6]);
        if (args.length == 7 && !trace) {
            throw new IllegalArgumentException("The optional final argument must be --trace.");
        }

        if ("B".equalsIgnoreCase(args[4])) {
            if (trace) {
                throw new IllegalArgumentException(
                        "--trace is Approach A only; use trace.StepTraceMain for Approach B step traces.");
            }
            String runName = args.length >= 6 ? args[5]
                    : "approach-b-r" + trimDecimal(radiusKm) + "-b" + trimDecimal(budgetKm);
            ApproachBConfig config = new ApproachBConfig(runName, radiusKm, budgetKm, 3, true, Path.of(args[1]));
            ApproachBOutcome outcome = runApproachBFromProcessedData(Path.of(args[0]), config);
            System.out.println("Experiment complete: " + outcome.config().outputDirectory());
            System.out.println("Population covered: " + outcome.approachBResult().runResult().populationCovered());
            System.out.println("Route length: " + outcome.approachBResult().runResult().route().totalDistanceKm() + " km");
            return;
        }

        ScoreRefreshMode mode = ScoreRefreshMode.valueOf(args[4].toUpperCase());
        String runName = args.length >= 6 ? args[5] : defaultRunName(radiusKm, budgetKm, mode);
        ExperimentConfig config = new ExperimentConfig(
                runName,
                radiusKm,
                budgetKm,
                mode,
                3,
                Path.of(args[1]),
                trace);
        ExperimentOutcome outcome = runApproachAFromProcessedData(Path.of(args[0]), config);
        System.out.println("Experiment complete: " + outcome.config().outputDirectory());
        System.out.println("Population covered: " + outcome.approachAResult().runResult().populationCovered());
        System.out.println("Route length: " + outcome.approachAResult().runResult().route().totalDistanceKm() + " km");
    }

    private static String defaultRunName(double radiusKm, double budgetKm, ScoreRefreshMode mode) {
        return "approach-a-" + mode.name().toLowerCase()
                + "-r" + trimDecimal(radiusKm)
                + "-b" + trimDecimal(budgetKm);
    }

    private static String trimDecimal(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value).replace('.', '_');
    }
}

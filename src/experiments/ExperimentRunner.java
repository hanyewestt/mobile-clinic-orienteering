package experiments;

import algorithms.ApproachBResult;
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

/** Runs and exports reproducible Approach A experiments. */
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

    public static ApproachBResult runApproachB(ProblemInstance instance, ExperimentConfig config) throws IOException {
        if (Double.compare(instance.serviceRadiusKm(), config.serviceRadiusKm()) != 0
                || Double.compare(instance.travelBudgetKm(), config.travelBudgetKm()) != 0) {
            throw new IllegalArgumentException("Experiment config must match the problem instance radius and budget.");
        }
        CoverageIndex coverageIndex = CoverageBuilder.build(
                instance.candidateStops(), instance.demandTowns(), instance.serviceRadiusKm());
        ApproachBResult result = new GridClusterSolver(config.maximumTwoOptPasses())
                .solveDetailed(instance, coverageIndex);
        ResultsWriter.writeApproachB(config, instance, result);
        return result;
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

    /**
     * Usage: ExperimentRunner <processed-data-dir> <output-dir> <radius-km>
     * <budget-km> <LAZY|FULL|B> [run-name] [--trace]
     * (B runs Approach B; --trace applies to Approach A only.)
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 5 || args.length > 7) {
            throw new IllegalArgumentException(
                    "Usage: ExperimentRunner <processed-data-dir> <output-dir> <radius-km> "
                            + "<budget-km> <LAZY|FULL> [run-name] [--trace]");
        }

        double radiusKm = Double.parseDouble(args[2]);
        double budgetKm = Double.parseDouble(args[3]);
        if ("B".equalsIgnoreCase(args[4])) {
            String runName = args.length >= 6 ? args[5] : "approach-b-r" + trimDecimal(radiusKm)
                    + "-b" + trimDecimal(budgetKm);
            ExperimentConfig config = new ExperimentConfig(
                    runName, radiusKm, budgetKm, ScoreRefreshMode.LAZY, 3, Path.of(args[1]));
            ProblemInstance instance = ProcessedInstanceLoader.loadPeninsular(
                    Path.of(args[0]), KUALA_LUMPUR_DEPOT, radiusKm, budgetKm);
            ApproachBResult result = runApproachB(instance, config);
            System.out.println("Experiment complete: " + config.outputDirectory());
            System.out.println("Population covered: " + result.runResult().populationCovered());
            System.out.println("Route length: " + result.runResult().route().totalDistanceKm() + " km");
            return;
        }
        ScoreRefreshMode mode = ScoreRefreshMode.valueOf(args[4].toUpperCase());
        boolean trace = args.length == 7 && "--trace".equals(args[6]);
        if (args.length == 7 && !trace) {
            throw new IllegalArgumentException("The optional final argument must be --trace.");
        }
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

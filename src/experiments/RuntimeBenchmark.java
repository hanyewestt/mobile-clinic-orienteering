package experiments;

import algorithms.GridClusterSolver;
import algorithms.LazyGreedySolver;
import algorithms.RoutingSolver;
import coverage.CoverageBuilder;
import coverage.CoverageIndex;
import data.ProcessedInstanceLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import model.Coordinate;
import model.ProblemInstance;
import routing.TwoOptPolisher;
import trace.StepRecorder;

/**
 * Measures solver runtime fairly: each solver runs many times inside one JVM,
 * the first runs are discarded as JIT warm-up, and the median of the rest is
 * reported. Coverage precomputation is shared and not timed, so only the
 * solver itself is compared.
 *
 * <p>Usage: RuntimeBenchmark &lt;processed-data-dir&gt; &lt;output-dir&gt;
 * &lt;radius-km&gt; &lt;budget-km&gt;[,&lt;budget-km&gt;...] [runs] [warm-up]
 */
public final class RuntimeBenchmark {
    private static final Coordinate KUALA_LUMPUR_DEPOT = new Coordinate(3.1412, 101.6865);

    private RuntimeBenchmark() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            System.err.println("Usage: RuntimeBenchmark <processed-data-dir> <output-dir> <radius-km>"
                    + " <budget-km>[,<budget-km>...] [runs=40] [warm-up=20]");
            System.exit(1);
        }
        Path processed = Path.of(args[0]);
        Path output = Path.of(args[1]);
        double radius = Double.parseDouble(args[2]);
        String[] budgets = args[3].split(",");
        int runs = args.length > 4 ? Integer.parseInt(args[4]) : 40;
        int warmUp = args.length > 5 ? Integer.parseInt(args[5]) : 20;
        if (warmUp >= runs) {
            throw new IllegalArgumentException("warm-up must be smaller than runs");
        }

        List<String> rows = new ArrayList<>();
        rows.add("service_radius_km,travel_budget_km,algorithm,runs,warm_up_runs,first_run_ms,"
                + "warm_median_ms,warm_min_ms,warm_max_ms,population_covered");
        for (String budgetText : budgets) {
            double budget = Double.parseDouble(budgetText.trim());
            ProblemInstance instance = ProcessedInstanceLoader.loadPeninsular(
                    processed, KUALA_LUMPUR_DEPOT, radius, budget);
            CoverageIndex index = CoverageBuilder.build(
                    instance.candidateStops(), instance.demandTowns(), instance.serviceRadiusKm());
            double medianA = 0;
            for (String algorithm : new String[] {"A", "B"}) {
                double[] ms = new double[runs];
                long population = 0;
                for (int i = 0; i < runs; i++) {
                    RoutingSolver solver = algorithm.equals("A")
                            ? new LazyGreedySolver(TwoOptPolisher.NO_PASS_LIMIT)
                            : new GridClusterSolver(TwoOptPolisher.NO_PASS_LIMIT, true, StepRecorder.NONE);
                    long start = System.nanoTime();
                    population = solver.solve(instance, index).populationCovered();
                    ms[i] = (System.nanoTime() - start) / 1e6;
                }
                double[] warm = Arrays.copyOfRange(ms, warmUp, runs);
                Arrays.sort(warm);
                double median = warm.length % 2 == 1
                        ? warm[warm.length / 2]
                        : (warm[warm.length / 2 - 1] + warm[warm.length / 2]) / 2.0;
                rows.add(String.format(Locale.ROOT, "%s,%s,%s,%d,%d,%.3f,%.3f,%.3f,%.3f,%d",
                        fmt(radius), fmt(budget), algorithm.equals("A") ? "Approach A" : "Approach B",
                        runs, warmUp, ms[0], median, warm[0], warm[warm.length - 1], population));
                String speedup = "";
                if (algorithm.equals("A")) {
                    medianA = median;
                } else {
                    speedup = String.format(Locale.ROOT, "  (B is %.1fx faster)", medianA / median);
                }
                System.out.printf(Locale.ROOT, "r=%s km B=%s km  Approach %s  warm median %.1f ms%s%n",
                        fmt(radius), fmt(budget), algorithm, median, speedup);
            }
        }
        Files.createDirectories(output);
        Path file = output.resolve("runtime.csv");
        Files.write(file, rows);
        System.out.println("Wrote " + file);
    }

    private static String fmt(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}

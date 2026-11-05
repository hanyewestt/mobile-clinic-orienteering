package trace;

import algorithms.ApproachAResult;
import algorithms.GridClusterSolver;
import algorithms.LazyGreedySolver;
import algorithms.ScoreRefreshMode;
import coverage.CoverageBuilder;
import coverage.CoverageIndex;
import data.ProcessedInstanceLoader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import model.Coordinate;
import model.ProblemInstance;
import model.RunResult;

/**
 * Runs one solver with step recording and writes a trace for the step player.
 *
 * <p>Usage: {@code StepTraceMain <processed-data-dir> <radius-km> <budget-km> <algorithm> [traces-dir]}
 * where algorithm is {@code A-LAZY}, {@code A-FULL} or {@code B}. The traces directory
 * defaults to {@code web-visual/step-player/traces}.
 */
public final class StepTraceMain {
    private static final Coordinate KUALA_LUMPUR_DEPOT = new Coordinate(3.1412, 101.6865);
    private static final Path DEFAULT_TRACES_DIRECTORY = Path.of("web-visual", "step-player", "traces");
    private static final int TWO_OPT_PASSES = 3;

    private StepTraceMain() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 4 || args.length > 5) {
            throw new IllegalArgumentException(
                    "Usage: StepTraceMain <processed-data-dir> <radius-km> <budget-km> <A-LAZY|A-FULL|B> [traces-dir]");
        }
        double radiusKm = Double.parseDouble(args[1]);
        double budgetKm = Double.parseDouble(args[2]);
        String algorithmKey = args[3].toUpperCase(Locale.ROOT);
        Path tracesDirectory = args.length == 5 ? Path.of(args[4]) : DEFAULT_TRACES_DIRECTORY;

        ProblemInstance instance = ProcessedInstanceLoader.loadPeninsular(
                Path.of(args[0]), KUALA_LUMPUR_DEPOT, radiusKm, budgetKm);
        CoverageIndex coverageIndex = CoverageBuilder.build(
                instance.candidateStops(), instance.demandTowns(), instance.serviceRadiusKm());

        JsonStepRecorder recorder = new JsonStepRecorder();
        RunResult runResult;
        switch (algorithmKey) {
            case "A-LAZY", "A-FULL" -> {
                ScoreRefreshMode mode = algorithmKey.equals("A-LAZY") ? ScoreRefreshMode.LAZY : ScoreRefreshMode.FULL;
                ApproachAResult result = new LazyGreedySolver(mode, TWO_OPT_PASSES, false, recorder)
                        .solveDetailed(instance, coverageIndex);
                runResult = result.runResult();
            }
            case "B" -> runResult = new GridClusterSolver(TWO_OPT_PASSES, true, recorder)
                    .solveDetailed(instance, coverageIndex).runResult();
            default -> throw new IllegalArgumentException("Unknown algorithm: " + args[3]);
        }

        String traceName = algorithmKey.toLowerCase(Locale.ROOT)
                + "-r" + trimDecimal(radiusKm) + "-b" + trimDecimal(budgetKm);
        Path traceFile = tracesDirectory.resolve(traceName + ".js");
        recorder.writeScript(traceFile, traceName, runResult.algorithmName(), instance);
        writeManifest(tracesDirectory);

        System.out.println("Step trace written: " + traceFile);
        System.out.println("  Events: " + recorder.eventCount());
        System.out.println("  Population covered: " + runResult.populationCovered());
        System.out.println("  Towns covered: " + runResult.townsCovered());
        System.out.printf(Locale.ROOT, "  Route length: %.3f km%n", runResult.route().totalDistanceKm());
    }

    /** Lists every trace in the directory so the player can offer them in a menu. */
    private static void writeManifest(Path tracesDirectory) throws IOException {
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(tracesDirectory)) {
            files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".js") && !name.equals("manifest.js"))
                    .sorted()
                    .forEach(name -> names.add(name.substring(0, name.length() - 3)));
        }
        try (BufferedWriter writer = Files.newBufferedWriter(
                tracesDirectory.resolve("manifest.js"), StandardCharsets.UTF_8)) {
            writer.write("window.STEP_TRACE_MANIFEST = [");
            for (int index = 0; index < names.size(); index++) {
                writer.write((index == 0 ? "" : ", ") + "\"" + names.get(index) + "\"");
            }
            writer.write("];\n");
        }
    }

    private static String trimDecimal(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value).replace('.', '_');
    }
}

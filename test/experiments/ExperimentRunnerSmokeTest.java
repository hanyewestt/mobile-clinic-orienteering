/*
 * Compile from the project root:
 * javac -d out $(find src test -name '*.java' -print)
 *
 * Run this test:
 * java -cp out experiments.ExperimentRunnerSmokeTest
 */
package experiments;

import algorithms.ScoreRefreshMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import model.CandidateStop;
import model.Coordinate;
import model.DemandTown;
import model.ProblemInstance;

/** Checks that small Approach A and B runs write every expected result artifact. */
public final class ExperimentRunnerSmokeTest {
    private ExperimentRunnerSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        List<CandidateStop> candidates = List.of(
                new CandidateStop(0, 1, "Depot Stop", new Coordinate(0.0, 0.0), "PPL"),
                new CandidateStop(1, 2, "East Stop", new Coordinate(0.0, 0.20), "PPL"),
                new CandidateStop(2, 3, "Too Far Stop", new Coordinate(0.0, 0.40), "PPL"));
        List<DemandTown> demandTowns = List.of(
                new DemandTown(0, 10, "Depot Town", new Coordinate(0.0, 0.0), "PPL", 100),
                new DemandTown(1, 11, "Nearby Town", new Coordinate(0.0, 0.05), "PPL", 200),
                new DemandTown(2, 12, "East Town", new Coordinate(0.0, 0.20), "PPL", 400),
                new DemandTown(3, 13, "Far Town", new Coordinate(0.0, 0.40), "PPL", 1_000));
        ProblemInstance instance = new ProblemInstance(
                "small experiment",
                new Coordinate(0.0, 0.0),
                candidates,
                demandTowns,
                10.0,
                50.0);
        Path outputDirectory = Files.createTempDirectory("mobile-clinic-experiment-");
        ExperimentConfig config = new ExperimentConfig(
                "smoke-run", 10.0, 50.0, ScoreRefreshMode.LAZY, 3, outputDirectory, true);

        ExperimentOutcome outcome = ExperimentRunner.runApproachA(instance, config);

        check(outcome.approachAResult().runResult().populationCovered() == 700,
                "The small experiment should cover 700 people.");
        check(Files.isRegularFile(outputDirectory.resolve("summary.csv")), "summary.csv must be written.");
        check(Files.isRegularFile(outputDirectory.resolve("route-stops.csv")), "route-stops.csv must be written.");
        check(Files.isRegularFile(outputDirectory.resolve("result.json")), "result.json must be written.");
        check(Files.readString(outputDirectory.resolve("result.json")).contains("\"populationCovered\": 700"),
                "The JSON result should contain the covered population.");
        check(Files.readString(outputDirectory.resolve("result.json")).contains("\"decisionTrace\":"),
                "A traced experiment must export its decision trace.");

        Path approachBDirectory = Files.createTempDirectory("mobile-clinic-experiment-b-");
        ApproachBOutcome approachB = ExperimentRunner.runApproachB(
                instance, new ApproachBConfig("smoke-run-b", 10.0, 50.0, 3, true, approachBDirectory));
        check(approachB.approachBResult().runResult().populationCovered() == 700,
                "Approach B should also cover 700 people.");
        for (String file : List.of("summary.csv", "route-stops.csv", "result.json")) {
            check(Files.isRegularFile(approachBDirectory.resolve(file)), "Approach B must write " + file + ".");
        }
        String approachAHeader = Files.readAllLines(outputDirectory.resolve("summary.csv")).get(0);
        String approachBHeader = Files.readAllLines(approachBDirectory.resolve("summary.csv")).get(0);
        String sharedColumns = "run_name,algorithm,service_radius_km,travel_budget_km,population_covered,"
                + "towns_covered,route_length_km,selected_stops,runtime_ms,";
        check(approachAHeader.startsWith(sharedColumns) && approachBHeader.startsWith(sharedColumns),
                "Both approaches must share the first nine summary columns.");
        check(Files.readString(approachBDirectory.resolve("result.json")).contains("\"populationCovered\": 700"),
                "Approach B's JSON result should contain the covered population.");

        System.out.println("Experiment runner smoke test passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

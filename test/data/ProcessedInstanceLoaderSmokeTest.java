/*
 * Compile from the project root:
 * javac -d out $(find src test -name '*.java' -print)
 *
 * Run this test:
 * java -cp out data.ProcessedInstanceLoaderSmokeTest
 */
package data;

import java.nio.file.Path;
import model.Coordinate;
import model.ProblemInstance;

/** Checks that the processed Peninsular data can be loaded into model classes. */
public final class ProcessedInstanceLoaderSmokeTest {
    private ProcessedInstanceLoaderSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        ProblemInstance instance = ProcessedInstanceLoader.loadPeninsular(
                Path.of("data/processed"),
                new Coordinate(3.1412, 101.6865),
                10.0,
                180.0);

        check(instance.candidateStops().size() == 14_034, "Expected 14,034 candidate stops.");
        check(instance.demandTowns().size() == 636, "Expected 636 demand towns.");
        check(instance.candidateStops().getFirst().id() == 0, "Candidate ids should start at zero.");
        check(instance.demandTowns().getFirst().id() == 0, "Demand-town ids should start at zero.");

        System.out.println("Processed instance loader smoke test passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

/*
 * Compile from the project root:
 * javac -d out $(find src test -name '*.java' -print)
 *
 * Run this test:
 * java -cp out coverage.CoverageSmokeTest
 */
package coverage;

import java.util.List;
import model.CandidateStop;
import model.Coordinate;
import model.DemandTown;

/** Small dependency-free check for radius coverage and overlap handling. */
public final class CoverageSmokeTest {
    private CoverageSmokeTest() {
    }

    public static void main(String[] args) {
        List<CandidateStop> candidateStops = List.of(
                new CandidateStop(0, 1, "Stop A", new Coordinate(0.0, 0.0), "PPL"),
                new CandidateStop(1, 2, "Stop B", new Coordinate(0.0, 0.08), "PPL"),
                new CandidateStop(2, 3, "Stop C", new Coordinate(0.0, 0.20), "PPL"));
        List<DemandTown> demandTowns = List.of(
                new DemandTown(0, 10, "Town One", new Coordinate(0.0, 0.0), "PPL", 100),
                new DemandTown(1, 11, "Town Two", new Coordinate(0.0, 0.05), "PPL", 250),
                new DemandTown(2, 12, "Town Three", new Coordinate(0.0, 0.20), "PPL", 400));

        CoverageIndex coverageIndex = CoverageBuilder.build(candidateStops, demandTowns, 10.0);
        check(coverageIndex.coveredTownCount(0) == 2, "Stop A should cover two towns.");
        check(coverageIndex.coveredTownCount(1) == 2, "Stop B should cover two towns.");
        check(coverageIndex.coveredTownCount(2) == 1, "Stop C should cover one town.");

        CoverageTracker tracker = new CoverageTracker(coverageIndex, demandTowns);
        check(tracker.marginalPopulationGain(0) == 350, "Stop A should initially gain 350 people.");
        check(tracker.applyStop(0).newlyCoveredPopulation() == 350, "Stop A should add 350 people.");
        check(tracker.marginalPopulationGain(1) == 0, "Overlap must not be counted twice.");
        check(tracker.applyStop(2).newlyCoveredPopulation() == 400, "Stop C should add Town Three.");
        check(tracker.coveredPopulation() == 750, "Total covered population should be 750.");
        check(tracker.servedTownCount() == 3, "All three towns should be served.");

        System.out.println("Coverage smoke test passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

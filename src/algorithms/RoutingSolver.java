package algorithms;

import coverage.CoverageIndex;
import model.ProblemInstance;
import model.RunResult;

/** Common contract for the routing approaches. */
public interface RoutingSolver {
    RunResult solve(ProblemInstance instance, CoverageIndex coverageIndex);
}

# mobile-clinic-orienteering
A route-aware Orienteering Problem solver for mobile medical clinics, maximizing population coverage within a fixed travel budget.

CS201 group G1-2. Two approaches are compared on the same Peninsular Malaysia
data (GeoNames `MY.txt`) with a Kuala Lumpur depot:

- **Approach A, route-aware lazy greedy** (`src/algorithms/LazyGreedySolver.java`):
  max-heap of stops, re-scored against the current route when popped.
- **Approach B, grid clusters then route** (`src/algorithms/GridClusterSolver.java`):
  hash grid of cells, version-stamped heap with 3x3 cell refreshes, then
  cheapest-insertion routing.

## Reproduce everything in the presentation

Requires Java 17 or later (no other libraries). From the project root:

```bash
bash run-experiments.sh        # macOS, Linux, Git Bash
```

```bat
run-experiments.bat            :: Windows Command Prompt
```

The script compiles the code, runs all tests, re-creates every folder in
`results/`, and runs the runtime benchmark. It takes about a minute.

### Experiments and where their results are

All experiments use a 5 km service radius and the Kuala Lumpur depot
(3.1412 N, 101.6865 E). The independent variable is the travel budget.

| Experiment (slide) | Budget | Approach A folder | Approach B folder |
| --- | --- | --- | --- |
| Small budget | 100 km | `results/approach-a-lazy-r5-b100/` | `results/approach-b-r5-b100/` |
| Realistic budget | 200 km | `results/approach-a-lazy-r5-b200/` | `results/approach-b-r5-b200/` |
| Large budget | 300 km | `results/approach-a-lazy-r5-b300/` | `results/approach-b-r5-b300/` |
| Runtime comparison | 100, 200, 300 km | `results/runtime-benchmark-r5/runtime.csv` (both approaches) | |

Each run folder holds `summary.csv` (one row: population covered, towns
covered, route length, stops, runtime and the approach's own counters),
`route-stops.csv` (the route in visiting order) and `result.json` (everything,
including the final route).

Coverage, route and stop counts are deterministic and match the slides exactly.
Runtimes depend on the machine: `runtime_ms` in `summary.csv` is a single cold
run, so speed is compared using `runtime-benchmark-r5/runtime.csv`, which runs
each solver 40 times in one JVM and reports the median of the last 20
(after JIT warm-up). The speed-ups (about 6x, 13x and 16x) are what to compare
across machines, not the absolute milliseconds.

To run one experiment by hand (after compiling, see [Running](#running)):

```bash
java -cp out experiments.ExperimentRunner data/processed results/approach-b-r5-b200 5 200 B
java -cp out experiments.ExperimentRunner data/processed results/approach-a-lazy-r5-b200 5 200 LAZY
java -cp out experiments.RuntimeBenchmark data/processed results/runtime-benchmark-r5 5 100,200,300
```

## Project structure

Plain Java; Maven is not required.

```text
mobile-clinic-orienteering/
├── README.md
├── run-experiments.sh / .bat   reproduce every experiment in the slides
├── data/
│   ├── raw/MY.txt              GeoNames Malaysia file, unchanged
│   └── processed/              candidate-stops.csv (~14,000), demand-towns.csv (636)
├── results/                    one folder per experiment (see the table above)
├── src/
│   ├── Main.java
│   ├── model/
│   ├── data/
│   ├── geometry/
│   ├── coverage/
│   ├── routing/
│   ├── algorithms/
│   ├── experiments/
│   └── trace/
├── test/                       smoke tests, one per package
└── web-visual/                 optional viewer, not needed for the experiments
    ├── step-player/            step-by-step replay of any run, plus traces/
    └── dashboard/              A vs B comparison page
```

## Where outputs go

| Output | Written by | Location | Use it for |
| --- | --- | --- | --- |
| Standard result per run: `summary.csv`, `route-stops.csv`, `result.json` | `experiments.ExperimentRunner` (A and B) | `results/<run-name>/` | Experiment tables, plots and slides |
| Runtime medians for both approaches | `experiments.RuntimeBenchmark` | `results/runtime-benchmark-r5/` | The speed comparison |
| Step trace per run (`<run>.js`) and `manifest.js` | `trace.StepTraceMain` (A and B) | `web-visual/step-player/traces/` | The step player and the dashboard |

Both approaches share the first nine `summary.csv` columns (`run_name` to
`runtime_ms`) and the `result` and `finalRoute` sections of `result.json`; the
remaining columns and `statistics` hold each approach's own counters. Runtimes
in `results/` are measured without step recording; runtimes inside step traces
include recording overhead, so compare speed using `results/` only.

## Running

Requires Java 17 or later. From the project root:

```bash
javac -d out $(find src -name '*.java')
```

On Windows Command Prompt:

```bat
dir /s /b src\*.java > sources.txt && javac -d out @sources.txt
```

Tests (each prints "... smoke test passed."; use `;` instead of `:` in the
classpath on Windows):

```bash
javac -d out-test -cp out $(find test -name '*.java')
java -ea -cp out:out-test routing.RoutingSmokeTest
```

The test classes are `coverage.CoverageSmokeTest`, `routing.RoutingSmokeTest`,
`data.ProcessedInstanceLoaderSmokeTest`, `algorithms.HeldKarpSolverSmokeTest`,
`algorithms.LazyGreedySolverSmokeTest`, `algorithms.GridClusterSolverSmokeTest`
and `experiments.ExperimentRunnerSmokeTest`.

Re-create the processed data from `data/raw/MY.txt` (optional; the output is
already included):

```bash
java -cp out data.DataPreprocessor
```

Standard results (`LAZY` for Approach A, `B` for Approach B):

```bash
java -cp out experiments.ExperimentRunner data/processed results/approach-b-r5-b200 5 200 B
```

Step traces for the step player and dashboard:

```bash
java -cp out trace.StepTraceMain data/processed 5 200 B
```

View the pages through a local server, then open
`http://localhost:8765/dashboard/index.html` or
`http://localhost:8765/step-player/index.html`:

```bash
python -m http.server 8765 --directory web-visual
```

### `data/`

Holds data files, not Java source code. Keep the supplied GeoNames file in
`data/raw/` unchanged. Put reproducible outputs of the preprocessing step in
`data/processed/`, such as the Peninsular candidate-stop list and demand-town
list.

### `results/`

Stores generated experiment outputs: CSV files, route summaries, timings, and
charts or tables used in the report. Do not place source data or Java code here.

### `src/`

Contains all application source code. `Main.java` is the entry point; it passes its arguments to
`experiments.ExperimentRunner`.

### `src/model/`

Contains the small classes representing project concepts: `Coordinate`,
`CandidateStop`, `DemandTown`, `Route`, `ProblemInstance`, and `RunResult`.
These classes should not contain algorithm logic.

### `src/data/`

Contains code that reads and prepares the dataset. `DataPreprocessor` creates
the processed CSV files; `ProcessedInstanceLoader` reads those files into a
`ProblemInstance` for the solvers.

### `src/geometry/`

Contains geographic calculations. `Haversine` calculates exact great-circle
distances, while `CoordinateProjector` creates `PlanarPoint` values in
kilometres for Approach B's grid cells.

### `src/coverage/`

Contains coverage logic. `CoverageBuilder` precomputes a `CoverageIndex` of
demand towns within each candidate site's service radius. `CoverageTracker`
records towns already served so their population is not counted twice, and
returns a `CoverageChange` whenever a stop is accepted.

### `src/routing/`

Contains shared route operations. `RouteCalculator` calculates closed-route
lengths and checks budgets; `CheapestInsertion` finds the lowest-cost insertion;
and `TwoOptPolisher` improves a route with 2-opt swaps until none helps.
`reachableSites` marks the sites a route could visit at all, and both approaches
use it to skip the rest.
Both approaches must use this shared code for a fair comparison.

### `src/algorithms/`

Contains the solvers only. `RoutingSolver` is the common interface;
`LazyGreedySolver` implements Approach A and returns `ApproachAResult`
statistics for the comparison experiment.
`GridClusterSolver` implements Approach B (hash grid of side about 2r,
selection by new population per km of depot round trip with exact 3x3
updates, then cheapest-insertion
routing) and returns `GridClusterResult` statistics.
`HeldKarpSolver` is an exact baseline restricted to small, hand-checkable
instances of at most 15 candidate stops.

### `src/experiments/`

Contains repeatable experiment code: run configurations, result writing, and
the runtime benchmark (`RuntimeBenchmark`).
`ExperimentRunner` runs one Approach A or Approach B scenario and exports
`summary.csv`, `route-stops.csv`, and `result.json`.

### `src/trace/`

Step recording for the visual pages. Solvers report every step to a
`StepRecorder`; `StepTraceMain` runs a solver with recording on and writes a
trace to `web-visual/step-player/traces/`. See
`web-visual/step-player/TRACE_FORMAT.md`.

### `test/`

Contains small automatic or manual checks for distance calculations, coverage
overlap, route-budget enforcement, and the expected behaviour of each solver.

## Build order

See [BUILD_ORDER.md](BUILD_ORDER.md) for the step-by-step implementation plan.

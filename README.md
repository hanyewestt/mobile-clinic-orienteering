# mobile-clinic-orienteering
A route-aware Orienteering Problem solver for mobile medical clinics, maximizing population coverage within a fixed travel budget.

## Project structure

The project can use this plain-Java layout; Maven is not required.

```text
mobile-clinic-orienteering/
├── README.md
├── data/
│   ├── raw/
│   └── processed/
├── results/
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
├── test/
└── web-visual/
    ├── index.html, data/   first visual prototype (Approach A only)
    ├── step-player/        step-by-step replay of any run, plus traces/
    └── dashboard/          A vs B comparison page
```

## Where outputs go

| Output | Written by | Location | Use it for |
| --- | --- | --- | --- |
| Standard result per run: `summary.csv`, `route-stops.csv`, `result.json` | `experiments.ExperimentRunner` (A and B) | `results/<run-name>/` | Experiment tables, plots and slides |
| Step trace per run (`<run>.js`) and `manifest.js` | `trace.StepTraceMain` (A and B) | `web-visual/step-player/traces/` | The step player and the dashboard |
| Hand-exported route data | Nobody; copied by hand | `web-visual/data/` | The first prototype page only |

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

Standard results (`LAZY` or `FULL` for Approach A, `B` for Approach B):

```bash
java -cp out experiments.ExperimentRunner data/processed results/approach-b-r10-b180 10 180 B
```

Step traces for the step player and dashboard:

```bash
java -cp out trace.StepTraceMain data/processed 10 180 B
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

Contains all application source code. `Main.java` is the entry point that
chooses a preprocessing task, solver, or experiment run.

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
and `TwoOptPolisher` improves a route with a capped number of 2-opt passes.
Both approaches must use this shared code for a fair comparison.

### `src/algorithms/`

Contains the solvers only. `RoutingSolver` is the common interface;
`LazyGreedySolver` implements Approach A in lazy and full-recompute modes and
returns `ApproachAResult` statistics for the comparison experiment.
`GridClusterSolver` implements Approach B (hash grid of side about 2r,
coverage-only selection with exact 3x3 updates, then cheapest-insertion
routing) and returns `GridClusterResult` statistics.
`HeldKarpSolver` is an exact baseline restricted to small, hand-checkable
instances of at most 15 candidate stops.

### `src/experiments/`

Contains repeatable experiment code: configurations, budget/radius sweeps,
lazy-versus-full comparisons, synthetic-data generation, and result collection.
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

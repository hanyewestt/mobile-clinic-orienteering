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
│   └── experiments/
└── test/
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
`HeldKarpSolver` is an exact baseline restricted to small, hand-checkable
instances of at most 15 candidate stops.

### `src/experiments/`

Contains repeatable experiment code: configurations, budget/radius sweeps,
lazy-versus-full comparisons, synthetic-data generation, and result collection.

### `test/`

Contains small automatic or manual checks for distance calculations, coverage
overlap, route-budget enforcement, and the expected behaviour of each solver.

## Build order

See [BUILD_ORDER.md](BUILD_ORDER.md) for the step-by-step implementation plan.

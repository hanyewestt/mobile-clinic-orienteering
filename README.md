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

Contains code that reads and prepares the dataset. `DataPreprocessor` currently
creates the processed CSV files; `GeoNamesReader` and `InstanceLoader` will
later read those files into the model classes.

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

Contains the solvers only: the `RoutingSolver` interface, Approach A
(`LazyGreedySolver`), Approach B (`GridClusterSolver`), and the small-instance
`HeldKarpSolver` baseline.

### `src/experiments/`

Contains repeatable experiment code: configurations, budget/radius sweeps,
lazy-versus-full comparisons, synthetic-data generation, and result collection.

### `test/`

Contains small automatic or manual checks for distance calculations, coverage
overlap, route-budget enforcement, and the expected behaviour of each solver.

## Build order

Build the project in this order. Finish and test each step before moving to the next.

1. **Create the Java skeleton**
   - Start with `src/Main.java` and folders for `model`, `data`, `geometry`,
     `coverage`, `routing`, `algorithms`, and `experiments`.
   - Maven is optional. The project can be compiled with `javac` directly.

2. **Load and preprocess the supplied GeoNames data**
   - Read `MY.txt` without modifying it.
   - Keep populated places, remove historical and abandoned places, deduplicate
     identical coordinates, and split Peninsular Malaysia from Borneo.
   - Produce a processed Peninsular instance containing candidate stops and
     demand towns with populations.
   - Run the completed preprocessor from the project root:

     ```bash
     javac -d out src/data/DataPreprocessor.java
     java -cp out data.DataPreprocessor
     ```

     It reads `data/raw/MY.txt` and writes
     `data/processed/candidate-stops.csv` and
     `data/processed/demand-towns.csv`.

3. **Implement the shared model and geometry**
   - Add `CandidateStop`, `DemandTown`, `Route`, and `RunResult` classes.
   - Implement Haversine distance and use Kuala Lumpur as the initial depot.

4. **Implement coverage correctly**
   - For each candidate stop, precompute the demand towns within a service
     radius `r`.
   - Track served towns so overlapping stops never count the same population
     twice.

5. **Implement shared routing tools**
   - Store a route as an array/list of site indices.
   - Add route-length calculation, cheapest insertion, budget checks, and
     2-opt polishing.

6. **Build Approach A: route-aware lazy greedy**
   - Use a priority queue of candidates scored by newly covered population per
     extra route distance.
   - Keep deferred sites that are temporarily over budget.

7. **Build Approach B: grid clusters, then route**
   - Use a hash grid with cells of side `2r`.
   - Select coverage anchors locally, then insert them into a route using the
     shared routing tools.

8. **Add a small exact baseline and tests**
   - Use Held-Karp only on a tiny 10-15-site instance.
   - Test distance, overlap handling, route budget, and return-to-depot logic.

9. **Build the experiment runner**
   - Run budget and radius sweeps, lazy-versus-full comparison, PPLX
     sensitivity, depot/region checks, and synthetic scaling.
   - Save population covered, towns covered, route length, runtime, and
     relevant heap/grid statistics to CSV.

10. **Prepare results and slides last**
    - Compare both approaches fairly by using the same coverage, insertion,
      and 2-opt code.
    - Add tables or route visuals only after the algorithms and experiments
      are reliable.

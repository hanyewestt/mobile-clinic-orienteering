# Build Order

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

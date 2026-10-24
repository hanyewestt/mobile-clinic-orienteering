# Visualisation Workflow

This folder contains a standalone mobile-clinic visualisation. Its default
Approach A view is backed by the exported `approach-a-lazy-r10-b180` result;
Approach B remains unavailable because it has not been implemented.

## Purpose

The visual should let a viewer compare Approach A and Approach B using the
same scenario, then inspect why a particular selected stop or grid anchor won
over the other options considered at that point.

## Build the visualiser

Build the visualiser in this order. The first five steps can use illustrative
data; connect real Java results only after the interaction design works.

1. **Create a standalone page**

   Keep the draft in `web-visual/index.html`, separate from the Java source.
   Use a responsive layout with a map area, scenario controls, result summary,
   and a comparison panel.

2. **Draw the common map layer**

   Add the depot, demand towns, candidate/selected stops, service-radius rings,
   and a closed route line. Keep the same bounds, colours, and population-dot
   scale when comparing algorithms.

3. **Add scenario controls**

   Include an Approach A/B switch, visible travel budget, and visible service
   radius. At this stage controls may change illustrative values only.

4. **Add the summary view**

   Show population covered, towns covered, route length, selected-stop count,
   and runtime. These numbers must use identical definitions for both
   algorithms.

5. **Make selected stops interactive**

   Each numbered selected stop should be clickable. Clicking it opens the
   selection record for that exact step, rather than a generic explanation of
   the algorithm.

6. **Show alternatives for each decision**

   For a selected stop or anchor, show the winner, strongest alternative, and
   other viable candidates considered at that moment. Explain the result in
   plain language:

   - Approach A: marginal population, insertion distance, and score.
   - Approach B: updated anchor coverage after local overlap correction and
     the relevant grid cell.

7. **Connect real experiment output**

   Replace illustrative JavaScript data with a saved JSON result file from the
   Java experiment runner. Do not calculate algorithm scores again in the
   browser; display the values produced by the solver.

8. **Validate the final visual**

   Verify every displayed stop, route segment, total, and decision comparison
   against the source experiment result. Label the input scenario clearly.

## Workflow

1. **Run one solver scenario**

   Use the same processed candidate-stop and demand-town data for both
   algorithms. Record the depot, travel budget, service radius, final route,
   covered population, covered towns, runtime, and selected stops.

2. **Capture each selection decision**

   Every time a solver accepts a stop, save a decision record. Keep the chosen
   stop and a few strongest alternatives from that same moment.

   For Approach A, capture:

   ```text
   step number
   chosen stop
   newly covered population
   cheapest insertion cost
   score = newly covered population / added distance
   alternative stops and their scores
   reason an alternative was deferred, skipped, or lost
   ```

   For Approach B, capture:

   ```text
   anchor number
   chosen grid-cell anchor
   newly covered population after local overlap updates
   grid-cell coordinate
   alternative anchors and their updated coverage values
   route-budget result when anchors are later inserted
   ```

3. **Export a small JSON result file**

   The Java experiment runner should eventually write one JSON file per run.
   It should contain a shared scenario section, an `approachA` section, and an
   `approachB` section. The visual can then replace its illustrative JavaScript
   values with this file.

   ```text
   results/
   └── peninsular-r10-b180.json
   ```

4. **Load the result into the visual**

   The map draws demand towns, the depot, selected stops, service-radius rings,
   and the closed route. The summary panel uses the final result metrics.

5. **Inspect a selected stop**

   The user clicks a numbered orange stop on the map. The comparison panel then
   shows the decision record for that exact selection:

   ```text
   selected stop
   versus strongest alternative
   other candidates considered
   the data used by the algorithm
   plain-language reason for the choice
   ```

6. **Compare both approaches fairly**

   Keep the depot, radius, budget, map bounds, population-circle scale, and
   colours consistent. Only the selection logic and resulting route should
   differ.

7. **Validate before presenting**

   - Check that every displayed selected stop appears in the solver route.
   - Check that displayed coverage totals match the solver's `RunResult`.
   - Check that every decision comparison comes from the actual saved decision
     record, rather than recalculated display-only values.
   - Label illustrative or synthetic data clearly until real experiment output
     is connected.

## Current draft interaction

`index.html` currently supports these presentation interactions:

- Show the actual Approach A route, metrics, and selected stops from the
  exported 10 km / 180 km experiment.
- Click a selected map stop to inspect its position and coordinates in the
  actual final route.
- Keep the service radius and travel budget visible as fixed scenario inputs.
- Clearly state that per-decision alternatives are unavailable until the Java
  solver exports decision traces.

## Build plan: data needed for the complete visual

The current page has enough information to draw the **final route**, but not
enough to explain the individual choices that created it. The following is the
smallest honest data plan for the original interaction: click a chosen stop
and see why it won over the other possible stops at that selection step.

### 1. Keep selection order separate from final route order

The solver chooses stops one at a time, then 2-opt may reorder the final
route. Therefore a stop needs both a `selectionStep` and a
`finalRouteVisitOrder`. The visual must not assume they are the same thing.

For every selected stop, export:

```text
candidate ID, GeoNames ID, name, latitude, longitude
selection step
final route visit order
insertion position when selected
route length before and after that insertion
whether a later 2-opt pass moved it
```

### 2. Record the accepted decision only

`LazyGreedySolver` already makes the selection. The trace should record the
chosen stop at that exact moment, without re-evaluating alternative candidates
for presentation. This keeps traced runs close to the normal solver runtime.

For each selected stop, capture:

```text
candidate ID
newly covered population and newly served-town count
cheapest insertion position and added distance
score = newly covered population / added distance
```

### 3. Export the coverage evidence for the chosen stop

For each accepted stop, save the demand towns it newly serves at that point:

```text
demand-town ID, name, latitude, longitude, population
distance from the chosen stop
```

Also export totals before and after applying the stop: covered population,
served-town count, and remaining route budget. This lets the map highlight
exactly what was gained and explains why overlap is not double-counted.

### 4. Export map data deliberately

The visual needs source data, not reconstructed guesses:

- depot coordinate and label;
- all final selected stops and the closed final route;
- all demand towns with coordinates and population;
- the service radius, travel budget, score-refresh mode, and 2-opt setting;
- preprocessing provenance: input dataset/version and candidate/demand
  filtering rules.

There are about 14,000 candidates, so the initial page should not draw every
candidate as a full interactive marker. Export them separately and use an
optional low-detail layer, clustering, or a viewport filter. The selected
stops remain individually visible.

### 5. Define a versioned result format

Extend `result.json` rather than relying on a hand-written JavaScript copy.
Use a `schemaVersion` such as `1` and include a `decisionTrace` only when trace
mode is enabled. A shape like this keeps the file self-contained:

```text
scenario
finalResult
finalRoute
selectedStops
decisionTrace[
  { selectionStep, chosen, routeBefore, routeAfter,
    coverageBefore, coverageAfter, newlyServedTowns }
]
```

The final route is still the authority for final map geometry after 2-opt.

### 6. Implement in this order

1. Add immutable trace records in `algorithms` (decision and newly served
   town).
2. Add an opt-in trace flag to `ExperimentConfig` and pass a trace collector
   through `LazyGreedySolver`.
3. Capture the chosen stop's pre-acceptance state and coverage change; do not
   alter the solver's scoring or route-selection logic.
4. Add the trace to `ApproachAResult` and write it through `ResultsWriter` as
   valid JSON.
5. Add small deterministic tests that verify the selected candidate is present
   in each trace and totals before/after match coverage tracking.
6. Re-run the 10 km / 180 km experiment with tracing enabled and validate its
   final summary against the current non-traced run.
7. Replace the visual's embedded route data with the exported artifact. On a
   selected stop, show its chosen coverage and insertion cost; a later map
   layer may highlight its exported newly served towns.

### 7. Keep the labels honest

Until trace mode is exported, the page can show final-route facts only. Once
tracing is available, label each explanation as the **chosen-stop record at
selection step N**. The visual does not make claims about unexported
alternatives.

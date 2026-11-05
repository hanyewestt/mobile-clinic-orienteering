# Step player and trace format

`index.html` replays a solver run one step at a time. It knows nothing about
any particular algorithm: a solver reports what it did through
`trace.StepRecorder`, and the player rebuilds the route and coverage from those
steps alone. Approach B (or any other solver) gets the same player by calling
the same recorder methods.

## Generate a trace

From the project root, after compiling into `out/`:

```bash
javac -d out $(find src -name '*.java')
java -cp out trace.StepTraceMain data/processed 10 180 A-LAZY
java -cp out trace.StepTraceMain data/processed 10 180 A-FULL
java -cp out trace.StepTraceMain data/processed 10 180 B
```

Arguments: processed-data directory, radius (km), budget (km), algorithm, and
optionally the output directory (default `web-visual/step-player/traces`).
Each run writes `traces/<algorithm>-r<r>-b<B>.js` and refreshes
`traces/manifest.js`, so the new trace shows up in the player's menu.

Open `web-visual/step-player/index.html` directly in a browser; no server is
needed. `?trace=<name>` in the URL picks a trace. If a viewer blocks local
scripts, serve the folder instead and open
`http://localhost:8765/step-player/index.html`:

```bash
python -m http.server 8765 --directory web-visual
```

## Using the player

| Control | What it does |
| --- | --- |
| Space / ▶ | Play or pause |
| → / ← | One step forward or back |
| N / Next stop | Run until the route next changes |
| P / Next phase | Run until the next phase (select, polish, refill) |
| Speed, `[` `]` | 0.5 to 5,000 steps per second |
| Show | Which steps playback and stepping stop on. Hidden steps still run, so the map stays correct |
| Pause on | Stop playback automatically when a stop is added or a phase starts |
| Scrubber | Jump anywhere; stepping back is instant |

The final step compares the replayed totals with the solver's own result. A
mismatch means a solver changed state without recording it.

## File format (schema version 1)

A trace file is `window.STEP_TRACE = { ... };` so it loads from disk. The same
object is accepted as plain JSON through **Open file…**.

```text
schemaVersion  1
name           trace name
algorithm      display name
radiusKm, budgetKm
depot          [lat, lon]
sites          [[lat, lon, name], ...]        index = candidate-site id
towns          [[lat, lon, population, name], ...]   index = demand-town id
events         ordered list, below
```

| kind | fields | effect on replay state |
| --- | --- | --- |
| `phase` | `name`, `text` | sets the current phase |
| `note` | `text` | none |
| `consider` | `site`, `gain`, `addedKm`, `position`, `score`, `outcome`, optional `rival`, `rivalScore`, `text` | `outcome = defer` marks the site deferred; others change nothing |
| `insert` | `site`, `position`, `addedKm`, `gain`, `towns` (newly served ids), `routeKm`, `text` | inserts the site into the route at `position`, marks the towns served |
| `route` | `stops`, `routeKm`, `text` | replaces the whole route (2-opt, or any reorder) |
| `select` | `site`, `gain`, `towns` (newly planned ids), optional `cell`, `text` | marks the site as a chosen anchor (not on the route) and the towns as planned |
| `grid` | `cellKm`, `cells` (`[minLat, minLon, maxLat, maxLon]` each), `text` | turns on the grid overlay; cell ids are list positions |
| `cell` | `cell`, `site` (or null), `gain`, `status` (`open` or `empty`), `text` | sets that cell's current best site and gain, which shades it |
| `result` | `populationCovered`, `townsCovered`, `routeKm`, `stops`, `runtimeMs`, `stats` | none; checked against the replay. `stats` holds named counters shown in the dashboard |

`consider` steps may also carry `cell`. When a step has a cell, the player
outlines it and dashes the 3x3 block around it.

## Dashboard

`web-visual/dashboard/index.html` reads every trace in `traces/manifest.js`,
groups them by scenario (`r..-b..`) and compares `a-lazy`, `b` and `a-full`
side by side: headline numbers, bars, a rough replay of how each route was
built, counters, and a table of all scenarios. Generate both approaches for a
scenario to see it there:

```bash
java -cp out trace.StepTraceMain data/processed 10 180 A-LAZY
java -cp out trace.StepTraceMain data/processed 10 180 B
```

`outcome` is one of `accept`, `defer`, `requeue`, `skip`, `reject`. `text` is
the solver's own plain-language reason and is shown as-is. The player never
recomputes scores.

## Adding a new solver

1. Accept a `StepRecorder` (default `StepRecorder.NONE`) and call it at each
   decision. Recording must not change any decision.
2. Emit `insert` whenever a site joins the route and `route` whenever the order
   changes, so the replay stays in step with the solver.
3. Add the algorithm key to the `switch` in `StepTraceMain`.

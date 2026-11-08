#!/usr/bin/env bash
# Reproduces every experiment in the presentation (macOS, Linux, Git Bash).
# Run from the project root:  bash run-experiments.sh
set -euo pipefail
cd "$(dirname "$0")"

SEP=":"
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; esac

echo "== 1. Compile =="
rm -rf out
javac -d out $(find src -name '*.java')
javac -d out-test -cp out $(find test -name '*.java')

echo "== 2. Tests =="
for t in coverage.CoverageSmokeTest routing.RoutingSmokeTest data.ProcessedInstanceLoaderSmokeTest \
         algorithms.HeldKarpSolverSmokeTest algorithms.LazyGreedySolverSmokeTest \
         algorithms.GridClusterSolverSmokeTest experiments.ExperimentRunnerSmokeTest \
         algorithms.CorrectnessRegressionTest; do
  java -ea -cp "out${SEP}out-test" "$t"
done

echo "== 3. Coverage and route experiments (radius 5 km, budgets 100/200/300 km) =="
for b in 100 200 300; do
  java -cp out experiments.ExperimentRunner data/processed "results/approach-a-lazy-r5-b$b" 5 "$b" LAZY
  java -cp out experiments.ExperimentRunner data/processed "results/approach-b-r5-b$b"      5 "$b" B
done

echo "== 4. Runtime benchmark (40 runs each, median of the last 20) =="
java -cp out experiments.RuntimeBenchmark data/processed results/runtime-benchmark-r5 5 100,200,300

echo "Done. See results/."

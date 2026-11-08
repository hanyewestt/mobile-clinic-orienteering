@echo off
rem Reproduces every experiment in the presentation (Windows Command Prompt).
rem Run from the project root:  run-experiments.bat
setlocal
cd /d "%~dp0"

echo == 1. Compile ==
if exist out rmdir /s /q out
if exist out-test rmdir /s /q out-test
dir /s /b src\*.java > sources.txt
javac -d out @sources.txt || goto :fail
dir /s /b test\*.java > tests.txt
javac -d out-test -cp out @tests.txt || goto :fail
del sources.txt tests.txt

echo == 2. Tests ==
for %%t in (coverage.CoverageSmokeTest routing.RoutingSmokeTest data.ProcessedInstanceLoaderSmokeTest algorithms.HeldKarpSolverSmokeTest algorithms.LazyGreedySolverSmokeTest algorithms.GridClusterSolverSmokeTest experiments.ExperimentRunnerSmokeTest algorithms.CorrectnessRegressionTest) do (
  java -ea -cp "out;out-test" %%t || goto :fail
)

echo == 3. Coverage and route experiments (radius 5 km, budgets 100/200/300 km) ==
for %%b in (100 200 300) do (
  java -cp out experiments.ExperimentRunner data\processed results\approach-a-lazy-r5-b%%b 5 %%b LAZY || goto :fail
  java -cp out experiments.ExperimentRunner data\processed results\approach-b-r5-b%%b 5 %%b B || goto :fail
)

echo == 4. Runtime benchmark (40 runs each, median of the last 20) ==
java -cp out experiments.RuntimeBenchmark data\processed results\runtime-benchmark-r5 5 100,200,300 || goto :fail

echo Done. See results\.
exit /b 0

:fail
echo FAILED
exit /b 1

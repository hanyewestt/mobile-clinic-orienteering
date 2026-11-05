package experiments;

import algorithms.DecisionTrace;
import algorithms.GridClusterResult;
import algorithms.NewlyServedTown;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import model.CandidateStop;
import model.ProblemInstance;
import model.Route;
import model.RunResult;

/** Writes portable experiment artifacts without external libraries. */
public final class ResultsWriter {
    private ResultsWriter() {
    }

    public static void writeApproachA(ExperimentOutcome outcome) throws IOException {
        Files.createDirectories(outcome.config().outputDirectory());
        writeSummaryCsv(outcome);
        writeRouteCsv(outcome);
        writeJson(outcome);
    }

    /**
     * Writes the same three files as Approach A. The first nine summary
     * columns and the "result" and "finalRoute" JSON sections are identical
     * in both approaches; only the algorithm-specific counters differ.
     */
    public static void writeApproachB(ApproachBOutcome outcome) throws IOException {
        Path outputDirectory = outcome.config().outputDirectory();
        Files.createDirectories(outputDirectory);
        GridClusterResult detail = outcome.approachBResult();
        RunResult result = detail.runResult();

        try (BufferedWriter writer = Files.newBufferedWriter(
                outputDirectory.resolve("summary.csv"), StandardCharsets.UTF_8)) {
            writer.write("run_name,algorithm,service_radius_km,travel_budget_km,population_covered,"
                    + "towns_covered,route_length_km,selected_stops,runtime_ms,cell_side_km,grid_cells,"
                    + "pruned_sites,anchors_selected,heap_pops,stale_pops,cell_refreshes,site_evaluations,"
                    + "anchors_skipped,refilled_stops,two_opt_improvements\n");
            writer.write(csv(outcome.config().runName()));
            writer.write(',' + csv(result.algorithmName()));
            writer.write(',' + decimal(outcome.config().serviceRadiusKm()));
            writer.write(',' + decimal(outcome.config().travelBudgetKm()));
            writer.write(',' + Long.toString(result.populationCovered()));
            writer.write(',' + Integer.toString(result.townsCovered()));
            writer.write(',' + decimal(result.route().totalDistanceKm()));
            writer.write(',' + Integer.toString(result.route().stopIds().size()));
            writer.write(',' + Long.toString(result.runtimeMillis()));
            writer.write(',' + decimal(detail.cellSideKm()));
            writer.write(',' + Integer.toString(detail.nonEmptyCells()));
            writer.write(',' + Integer.toString(detail.prunedSites()));
            writer.write(',' + Integer.toString(detail.anchorsSelected()));
            writer.write(',' + Long.toString(detail.heapPops()));
            writer.write(',' + Long.toString(detail.stalePops()));
            writer.write(',' + Long.toString(detail.cellRefreshes()));
            writer.write(',' + Long.toString(detail.siteEvaluations()));
            writer.write(',' + Integer.toString(detail.anchorsSkipped()));
            writer.write(',' + Integer.toString(detail.refilledStops()));
            writer.write(',' + Integer.toString(detail.twoOptImprovements()));
            writer.newLine();
        }

        writeRouteCsv(outputDirectory, outcome.instance(), result.route());

        try (BufferedWriter writer = Files.newBufferedWriter(
                outputDirectory.resolve("result.json"), StandardCharsets.UTF_8)) {
            writer.write("{\n");
            writer.write("  \"runName\": \"" + json(outcome.config().runName()) + "\",\n");
            writer.write("  \"schemaVersion\": 1,\n");
            writer.write("  \"algorithm\": \"" + json(result.algorithmName()) + "\",\n");
            writer.write("  \"scenario\": {\n");
            writer.write("    \"serviceRadiusKm\": " + decimal(outcome.config().serviceRadiusKm()) + ",\n");
            writer.write("    \"travelBudgetKm\": " + decimal(outcome.config().travelBudgetKm()) + ",\n");
            writer.write("    \"maximumTwoOptPasses\": " + outcome.config().maximumTwoOptPasses() + ",\n");
            writer.write("    \"pruneUnreachable\": " + outcome.config().pruneUnreachable() + ",\n");
            writer.write("    \"depot\": {\"latitude\": " + decimal(outcome.instance().depot().latitude())
                    + ", \"longitude\": " + decimal(outcome.instance().depot().longitude()) + "}\n");
            writer.write("  },\n");
            writer.write("  \"result\": {\n");
            writer.write("    \"populationCovered\": " + result.populationCovered() + ",\n");
            writer.write("    \"townsCovered\": " + result.townsCovered() + ",\n");
            writer.write("    \"routeLengthKm\": " + decimal(result.route().totalDistanceKm()) + ",\n");
            writer.write("    \"runtimeMillis\": " + result.runtimeMillis() + ",\n");
            writer.write("    \"routeStopIds\": [");
            for (int index = 0; index < result.route().stopIds().size(); index++) {
                if (index > 0) {
                    writer.write(", ");
                }
                writer.write(Integer.toString(result.route().stopIds().get(index)));
            }
            writer.write("]\n");
            writer.write("  },\n");
            writer.write("  \"statistics\": {\n");
            writer.write("    \"cellSideKm\": " + decimal(detail.cellSideKm()) + ",\n");
            writer.write("    \"gridCells\": " + detail.nonEmptyCells() + ",\n");
            writer.write("    \"prunedSites\": " + detail.prunedSites() + ",\n");
            writer.write("    \"anchorsSelected\": " + detail.anchorsSelected() + ",\n");
            writer.write("    \"heapPops\": " + detail.heapPops() + ",\n");
            writer.write("    \"stalePops\": " + detail.stalePops() + ",\n");
            writer.write("    \"cellRefreshes\": " + detail.cellRefreshes() + ",\n");
            writer.write("    \"siteEvaluations\": " + detail.siteEvaluations() + ",\n");
            writer.write("    \"anchorsSkipped\": " + detail.anchorsSkipped() + ",\n");
            writer.write("    \"refilledStops\": " + detail.refilledStops() + ",\n");
            writer.write("    \"twoOptImprovements\": " + detail.twoOptImprovements() + "\n");
            writer.write("  },\n");
            writeFinalRoute(writer, outcome.instance(), result.route());
            writer.write("\n}\n");
        }
    }

    private static void writeSummaryCsv(ExperimentOutcome outcome) throws IOException {
        RunResult result = outcome.approachAResult().runResult();
        Path path = outcome.config().outputDirectory().resolve("summary.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("run_name,algorithm,service_radius_km,travel_budget_km,population_covered,"
                    + "towns_covered,route_length_km,selected_stops,runtime_ms,candidate_evaluations,"
                    + "heap_pops,deferred_stops,refilled_stops,two_opt_improvements\n");
            writer.write(csv(outcome.config().runName()));
            writer.write(',' + csv(result.algorithmName()));
            writer.write(',' + decimal(outcome.config().serviceRadiusKm()));
            writer.write(',' + decimal(outcome.config().travelBudgetKm()));
            writer.write(',' + Long.toString(result.populationCovered()));
            writer.write(',' + Integer.toString(result.townsCovered()));
            writer.write(',' + decimal(result.route().totalDistanceKm()));
            writer.write(',' + Integer.toString(result.route().stopIds().size()));
            writer.write(',' + Long.toString(result.runtimeMillis()));
            writer.write(',' + Long.toString(outcome.approachAResult().candidateEvaluations()));
            writer.write(',' + Long.toString(outcome.approachAResult().heapPops()));
            writer.write(',' + Integer.toString(outcome.approachAResult().deferredStops()));
            writer.write(',' + Integer.toString(outcome.approachAResult().refilledStops()));
            writer.write(',' + Integer.toString(outcome.approachAResult().twoOptImprovements()));
            writer.newLine();
        }
    }

    private static void writeRouteCsv(ExperimentOutcome outcome) throws IOException {
        writeRouteCsv(outcome.config().outputDirectory(), outcome.instance(), outcome.approachAResult().runResult().route());
    }

    private static void writeRouteCsv(Path outputDirectory, ProblemInstance instance, Route route) throws IOException {
        Path path = outputDirectory.resolve("route-stops.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("visit_order,candidate_id,geoname_id,name,latitude,longitude,feature_code\n");
            for (int visitOrder = 0; visitOrder < route.stopIds().size(); visitOrder++) {
                CandidateStop stop = instance.candidateStops().get(route.stopIds().get(visitOrder));
                writer.write(Integer.toString(visitOrder + 1));
                writer.write(',' + Integer.toString(stop.id()));
                writer.write(',' + Long.toString(stop.geonameId()));
                writer.write(',' + csv(stop.name()));
                writer.write(',' + decimal(stop.coordinate().latitude()));
                writer.write(',' + decimal(stop.coordinate().longitude()));
                writer.write(',' + csv(stop.featureCode()));
                writer.newLine();
            }
        }
    }

    private static void writeJson(ExperimentOutcome outcome) throws IOException {
        RunResult result = outcome.approachAResult().runResult();
        Path path = outcome.config().outputDirectory().resolve("result.json");
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("{\n");
            writer.write("  \"runName\": \"" + json(outcome.config().runName()) + "\",\n");
            writer.write("  \"schemaVersion\": 1,\n");
            writer.write("  \"algorithm\": \"" + json(result.algorithmName()) + "\",\n");
            writer.write("  \"scenario\": {\n");
            writer.write("    \"serviceRadiusKm\": " + decimal(outcome.config().serviceRadiusKm()) + ",\n");
            writer.write("    \"travelBudgetKm\": " + decimal(outcome.config().travelBudgetKm()) + ",\n");
            writer.write("    \"scoreRefreshMode\": \"" + outcome.config().refreshMode() + "\",\n");
            writer.write("    \"maximumTwoOptPasses\": " + outcome.config().maximumTwoOptPasses() + ",\n");
            writer.write("    \"decisionTraceCaptured\": " + outcome.config().recordDecisionTrace() + ",\n");
            writer.write("    \"depot\": {\"latitude\": " + decimal(outcome.instance().depot().latitude())
                    + ", \"longitude\": " + decimal(outcome.instance().depot().longitude()) + "}\n");
            writer.write("  },\n");
            writer.write("  \"result\": {\n");
            writer.write("    \"populationCovered\": " + result.populationCovered() + ",\n");
            writer.write("    \"townsCovered\": " + result.townsCovered() + ",\n");
            writer.write("    \"routeLengthKm\": " + decimal(result.route().totalDistanceKm()) + ",\n");
            writer.write("    \"runtimeMillis\": " + result.runtimeMillis() + ",\n");
            writer.write("    \"routeStopIds\": [");
            for (int index = 0; index < result.route().stopIds().size(); index++) {
                if (index > 0) {
                    writer.write(", ");
                }
                writer.write(Integer.toString(result.route().stopIds().get(index)));
            }
            writer.write("]\n");
            writer.write("  },\n");
            writer.write("  \"statistics\": {\n");
            writer.write("    \"candidateEvaluations\": " + outcome.approachAResult().candidateEvaluations() + ",\n");
            writer.write("    \"heapPops\": " + outcome.approachAResult().heapPops() + ",\n");
            writer.write("    \"deferredStops\": " + outcome.approachAResult().deferredStops() + ",\n");
            writer.write("    \"refilledStops\": " + outcome.approachAResult().refilledStops() + ",\n");
            writer.write("    \"twoOptImprovements\": " + outcome.approachAResult().twoOptImprovements() + "\n");
            writer.write("  },\n");
            writeFinalRoute(writer, outcome);
            writer.write(",\n");
            writeDecisionTrace(writer, outcome);
            writer.write("\n");
            writer.write("}\n");
        }
    }

    private static void writeFinalRoute(BufferedWriter writer, ExperimentOutcome outcome) throws IOException {
        writeFinalRoute(writer, outcome.instance(), outcome.approachAResult().runResult().route());
    }

    private static void writeFinalRoute(BufferedWriter writer, ProblemInstance instance, Route route) throws IOException {
        writer.write("  \"finalRoute\": [\n");
        List<Integer> stopIds = route.stopIds();
        for (int index = 0; index < stopIds.size(); index++) {
            CandidateStop stop = instance.candidateStops().get(stopIds.get(index));
            writer.write("    {\"visitOrder\": " + (index + 1)
                    + ", \"candidateStopId\": " + stop.id()
                    + ", \"geonameId\": " + stop.geonameId()
                    + ", \"name\": \"" + json(stop.name()) + "\""
                    + ", \"latitude\": " + decimal(stop.coordinate().latitude())
                    + ", \"longitude\": " + decimal(stop.coordinate().longitude())
                    + ", \"featureCode\": \"" + json(stop.featureCode()) + "\"}");
            writer.write(index + 1 == stopIds.size() ? "\n" : ",\n");
        }
        writer.write("  ]");
    }

    private static void writeDecisionTrace(BufferedWriter writer, ExperimentOutcome outcome) throws IOException {
        writer.write("  \"decisionTrace\": [\n");
        List<DecisionTrace> trace = outcome.approachAResult().decisionTrace();
        for (int index = 0; index < trace.size(); index++) {
            DecisionTrace decision = trace.get(index);
            writer.write("    {\n");
            writer.write("      \"selectionStep\": " + decision.selectionStep() + ",\n");
            writer.write("      \"chosenCandidateStopId\": " + decision.chosenCandidateStopId() + ",\n");
            writer.write("      \"marginalPopulation\": " + decision.marginalPopulation() + ",\n");
            writer.write("      \"marginalTownCount\": " + decision.marginalTownCount() + ",\n");
            writer.write("      \"insertionPosition\": " + decision.insertionPosition() + ",\n");
            writer.write("      \"addedDistanceKm\": " + decimal(decision.addedDistanceKm()) + ",\n");
            writer.write("      \"score\": " + decimal(decision.score()) + ",\n");
            writer.write("      \"routeLengthBeforeKm\": " + decimal(decision.routeLengthBeforeKm()) + ",\n");
            writer.write("      \"routeLengthAfterKm\": " + decimal(decision.routeLengthAfterKm()) + ",\n");
            writer.write("      \"populationBefore\": " + decision.populationBefore() + ",\n");
            writer.write("      \"populationAfter\": " + decision.populationAfter() + ",\n");
            writer.write("      \"townsBefore\": " + decision.townsBefore() + ",\n");
            writer.write("      \"townsAfter\": " + decision.townsAfter() + ",\n");
            writer.write("      \"newlyServedTowns\": [");
            writeNewlyServedTowns(writer, decision.newlyServedTowns());
            writer.write("]\n    }");
            writer.write(index + 1 == trace.size() ? "\n" : ",\n");
        }
        writer.write("  ]");
    }

    private static void writeNewlyServedTowns(BufferedWriter writer, List<NewlyServedTown> towns) throws IOException {
        for (int index = 0; index < towns.size(); index++) {
            NewlyServedTown town = towns.get(index);
            writer.write("{\"demandTownId\": " + town.demandTownId()
                    + ", \"geonameId\": " + town.geonameId()
                    + ", \"name\": \"" + json(town.name()) + "\""
                    + ", \"latitude\": " + decimal(town.latitude())
                    + ", \"longitude\": " + decimal(town.longitude())
                    + ", \"population\": " + town.population()
                    + ", \"distanceFromStopKm\": " + decimal(town.distanceFromStopKm()) + "}");
            if (index + 1 < towns.size()) {
                writer.write(", ");
            }
        }
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

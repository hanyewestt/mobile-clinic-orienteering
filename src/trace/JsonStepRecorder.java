package trace;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import model.CandidateStop;
import model.DemandTown;
import model.ProblemInstance;

/**
 * Collects steps in memory and writes them as a step-trace file that the step
 * player can open. The file holds the whole instance (sites, towns, depot) so
 * the player needs nothing else.
 */
public final class JsonStepRecorder implements StepRecorder {
    public static final int SCHEMA_VERSION = 1;

    private final List<String> events = new ArrayList<>();

    @Override
    public boolean enabled() {
        return true;
    }

    public int eventCount() {
        return events.size();
    }

    @Override
    public void phase(String name, String text) {
        events.add("{\"kind\":\"phase\",\"name\":" + json(name) + ",\"text\":" + json(text) + "}");
    }

    @Override
    public void note(String text) {
        events.add("{\"kind\":\"note\",\"text\":" + json(text) + "}");
    }

    @Override
    public void consider(
            int siteId,
            long gain,
            double addedKm,
            int position,
            double score,
            String outcome,
            int rivalSiteId,
            double rivalScore,
            int cellId,
            String text) {
        StringBuilder event = new StringBuilder(160);
        event.append("{\"kind\":\"consider\",\"site\":").append(siteId)
                .append(",\"gain\":").append(gain)
                .append(",\"addedKm\":").append(number(addedKm, 3))
                .append(",\"position\":").append(position < 0 ? "null" : Integer.toString(position))
                .append(",\"score\":").append(number(score, 2))
                .append(",\"outcome\":").append(json(outcome));
        if (rivalSiteId >= 0) {
            event.append(",\"rival\":").append(rivalSiteId)
                    .append(",\"rivalScore\":").append(number(rivalScore, 2));
        }
        if (cellId >= 0) {
            event.append(",\"cell\":").append(cellId);
        }
        event.append(",\"text\":").append(json(text)).append('}');
        events.add(event.toString());
    }

    @Override
    public void select(int siteId, long gain, int[] newlyPlannedTownIds, int cellId, String text) {
        StringBuilder event = new StringBuilder(128);
        event.append("{\"kind\":\"select\",\"site\":").append(siteId)
                .append(",\"gain\":").append(gain)
                .append(",\"towns\":");
        appendInts(event, newlyPlannedTownIds);
        if (cellId >= 0) {
            event.append(",\"cell\":").append(cellId);
        }
        event.append(",\"text\":").append(json(text)).append('}');
        events.add(event.toString());
    }

    @Override
    public void grid(double cellSideKm, List<double[]> cellBounds, String text) {
        StringBuilder event = new StringBuilder(64 + cellBounds.size() * 48);
        event.append("{\"kind\":\"grid\",\"cellKm\":").append(number(cellSideKm, 4)).append(",\"cells\":[");
        for (int index = 0; index < cellBounds.size(); index++) {
            double[] bounds = cellBounds.get(index);
            event.append(index == 0 ? "[" : ",[")
                    .append(number(bounds[0], 5)).append(',')
                    .append(number(bounds[1], 5)).append(',')
                    .append(number(bounds[2], 5)).append(',')
                    .append(number(bounds[3], 5)).append(']');
        }
        event.append("],\"text\":").append(json(text)).append('}');
        events.add(event.toString());
    }

    @Override
    public void cell(int cellId, int bestSiteId, long gain, String status, String text) {
        events.add("{\"kind\":\"cell\",\"cell\":" + cellId
                + ",\"site\":" + (bestSiteId < 0 ? "null" : Integer.toString(bestSiteId))
                + ",\"gain\":" + gain
                + ",\"status\":" + json(status)
                + ",\"text\":" + json(text) + "}");
    }

    private static void appendInts(StringBuilder builder, int[] values) {
        builder.append('[');
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(values[index]);
        }
        builder.append(']');
    }

    @Override
    public void insert(
            int siteId,
            int position,
            double addedKm,
            long gain,
            int[] newlyServedTownIds,
            double routeKm,
            String text) {
        StringBuilder event = new StringBuilder(160);
        event.append("{\"kind\":\"insert\",\"site\":").append(siteId)
                .append(",\"position\":").append(position)
                .append(",\"addedKm\":").append(number(addedKm, 3))
                .append(",\"gain\":").append(gain)
                .append(",\"towns\":");
        appendInts(event, newlyServedTownIds);
        event.append(",\"routeKm\":").append(number(routeKm, 3))
                .append(",\"text\":").append(json(text)).append('}');
        events.add(event.toString());
    }

    @Override
    public void route(List<Integer> stopIds, double routeKm, String text) {
        StringBuilder event = new StringBuilder(64 + stopIds.size() * 6);
        event.append("{\"kind\":\"route\",\"stops\":[");
        for (int index = 0; index < stopIds.size(); index++) {
            if (index > 0) {
                event.append(',');
            }
            event.append(stopIds.get(index));
        }
        event.append("],\"routeKm\":").append(number(routeKm, 3))
                .append(",\"text\":").append(json(text)).append('}');
        events.add(event.toString());
    }

    @Override
    public void result(
            long populationCovered,
            int townsCovered,
            double routeKm,
            int stops,
            long runtimeMillis,
            Map<String, ? extends Number> statistics) {
        StringBuilder event = new StringBuilder(256);
        event.append("{\"kind\":\"result\",\"populationCovered\":").append(populationCovered)
                .append(",\"townsCovered\":").append(townsCovered)
                .append(",\"routeKm\":").append(number(routeKm, 3))
                .append(",\"stops\":").append(stops)
                .append(",\"runtimeMs\":").append(runtimeMillis)
                .append(",\"stats\":{");
        boolean first = true;
        for (Map.Entry<String, ? extends Number> entry : statistics.entrySet()) {
            event.append(first ? "" : ",").append(json(entry.getKey())).append(':');
            Number value = entry.getValue();
            event.append(value instanceof Double || value instanceof Float
                    ? number(value.doubleValue(), 3)
                    : Long.toString(value.longValue()));
            first = false;
        }
        event.append("}}");
        events.add(event.toString());
    }

    /**
     * Writes {@code <name>.js}, which sets {@code window.STEP_TRACE} so the
     * player can load it from disk without a web server.
     */
    public void writeScript(Path outputFile, String traceName, String algorithm, ProblemInstance instance)
            throws IOException {
        Objects.requireNonNull(outputFile, "outputFile");
        Path parent = outputFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            writer.write("window.STEP_TRACE = ");
            writeJson(writer, traceName, algorithm, instance);
            writer.write(";\n");
        }
    }

    private void writeJson(BufferedWriter writer, String traceName, String algorithm, ProblemInstance instance)
            throws IOException {
        writer.write("{\n\"schemaVersion\":" + SCHEMA_VERSION
                + ",\n\"name\":" + json(traceName)
                + ",\n\"algorithm\":" + json(algorithm)
                + ",\n\"instance\":" + json(instance.name())
                + ",\n\"radiusKm\":" + number(instance.serviceRadiusKm(), 3)
                + ",\n\"budgetKm\":" + number(instance.travelBudgetKm(), 3)
                + ",\n\"depot\":[" + number(instance.depot().latitude(), 5) + ","
                + number(instance.depot().longitude(), 5) + "]");

        writer.write(",\n\"sites\":[");
        List<CandidateStop> sites = instance.candidateStops();
        for (int index = 0; index < sites.size(); index++) {
            CandidateStop site = sites.get(index);
            writer.write((index == 0 ? "" : ",") + "[" + number(site.coordinate().latitude(), 5) + ","
                    + number(site.coordinate().longitude(), 5) + "," + json(site.name()) + "]");
            if (index % 8 == 7) {
                writer.write('\n');
            }
        }

        writer.write("],\n\"towns\":[");
        List<DemandTown> towns = instance.demandTowns();
        for (int index = 0; index < towns.size(); index++) {
            DemandTown town = towns.get(index);
            writer.write((index == 0 ? "" : ",") + "[" + number(town.coordinate().latitude(), 5) + ","
                    + number(town.coordinate().longitude(), 5) + "," + town.population() + ","
                    + json(town.name()) + "]");
            if (index % 6 == 5) {
                writer.write('\n');
            }
        }

        writer.write("],\n\"events\":[\n");
        for (int index = 0; index < events.size(); index++) {
            writer.write(events.get(index));
            writer.write(index == events.size() - 1 ? "\n" : ",\n");
        }
        writer.write("]}");
    }

    private static String number(double value, int decimals) {
        if (!Double.isFinite(value)) {
            return "null";
        }
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static String json(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                case '<' -> escaped.append("\\u003c");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }
}

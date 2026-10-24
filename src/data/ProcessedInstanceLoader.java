package data;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import model.CandidateStop;
import model.Coordinate;
import model.DemandTown;
import model.ProblemInstance;

/** Loads the CSV files created by {@link DataPreprocessor} into a solver instance. */
public final class ProcessedInstanceLoader {
    private ProcessedInstanceLoader() {
    }

    public static ProblemInstance loadPeninsular(
            Path processedDirectory,
            Coordinate depot,
            double serviceRadiusKm,
            double travelBudgetKm) throws IOException {
        return load(
                processedDirectory.resolve("candidate-stops.csv"),
                processedDirectory.resolve("demand-towns.csv"),
                depot,
                serviceRadiusKm,
                travelBudgetKm);
    }

    public static ProblemInstance load(
            Path candidateStopsCsv,
            Path demandTownsCsv,
            Coordinate depot,
            double serviceRadiusKm,
            double travelBudgetKm) throws IOException {
        List<CandidateStop> candidateStops = loadCandidateStops(candidateStopsCsv);
        List<DemandTown> demandTowns = loadDemandTowns(demandTownsCsv);
        return new ProblemInstance(
                "Peninsular Malaysia",
                depot,
                candidateStops,
                demandTowns,
                serviceRadiusKm,
                travelBudgetKm);
    }

    private static List<CandidateStop> loadCandidateStops(Path csvPath) throws IOException {
        List<CandidateStop> candidateStops = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            requireHeader(reader, csvPath, "geoname_id,name,latitude,longitude,feature_code");
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = parseCsvLine(line, 5);
                candidateStops.add(new CandidateStop(
                        candidateStops.size(),
                        Long.parseLong(values[0]),
                        values[1],
                        new Coordinate(Double.parseDouble(values[2]), Double.parseDouble(values[3])),
                        values[4]));
            }
        }
        return List.copyOf(candidateStops);
    }

    private static List<DemandTown> loadDemandTowns(Path csvPath) throws IOException {
        List<DemandTown> demandTowns = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            requireHeader(reader, csvPath, "geoname_id,name,latitude,longitude,feature_code,population");
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = parseCsvLine(line, 6);
                demandTowns.add(new DemandTown(
                        demandTowns.size(),
                        Long.parseLong(values[0]),
                        values[1],
                        new Coordinate(Double.parseDouble(values[2]), Double.parseDouble(values[3])),
                        values[4],
                        Long.parseLong(values[5])));
            }
        }
        return List.copyOf(demandTowns);
    }

    private static void requireHeader(BufferedReader reader, Path csvPath, String expected) throws IOException {
        String actual = reader.readLine();
        if (!expected.equals(actual)) {
            throw new IOException("Unexpected CSV header in " + csvPath + ": " + actual);
        }
    }

    private static String[] parseCsvLine(String line, int expectedColumnCount) throws IOException {
        List<String> values = new ArrayList<>(expectedColumnCount);
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;

        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (insideQuotes && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    insideQuotes = !insideQuotes;
                }
            } else if (character == ',' && !insideQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        if (insideQuotes) {
            throw new IOException("Unclosed CSV quote: " + line);
        }
        values.add(current.toString());
        if (values.size() != expectedColumnCount) {
            throw new IOException("Expected " + expectedColumnCount + " CSV columns but found " + values.size());
        }
        return values.toArray(String[]::new);
    }
}

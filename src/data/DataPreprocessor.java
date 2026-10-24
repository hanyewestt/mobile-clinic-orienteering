package data;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Creates the Peninsular Malaysia candidate-stop and demand-town datasets from
 * the supplied GeoNames MY.txt file.
 *
 * <p>Run from the project root with:
 * <pre>java -cp out data.DataPreprocessor</pre>
 *
 * <p>Optional arguments replace the default input file and output directory:
 * <pre>java -cp out data.DataPreprocessor path/to/MY.txt path/to/output</pre>
 */
public final class DataPreprocessor {
    private static final int GEONAME_ID = 0;
    private static final int NAME = 1;
    private static final int LATITUDE = 4;
    private static final int LONGITUDE = 5;
    private static final int FEATURE_CLASS = 6;
    private static final int FEATURE_CODE = 7;
    private static final int POPULATION = 14;

    private static final double PENINSULAR_LONGITUDE_LIMIT = 108.0;

    private DataPreprocessor() {
    }

    public static void main(String[] args) throws IOException {
        Path input = args.length >= 1 ? Path.of(args[0]) : Path.of("data/raw/MY.txt");
        Path outputDirectory = args.length >= 2 ? Path.of(args[1]) : Path.of("data/processed");

        if (args.length > 2) {
            throw new IllegalArgumentException(
                    "Usage: DataPreprocessor [input-MY.txt] [output-directory]");
        }

        PreprocessingSummary summary = preprocess(input, outputDirectory);
        System.out.println(summary.toDisplayString());
    }

    public static PreprocessingSummary preprocess(Path input, Path outputDirectory) throws IOException {
        if (!Files.isRegularFile(input)) {
            throw new IllegalArgumentException("GeoNames input file not found: " + input.toAbsolutePath());
        }

        Files.createDirectories(outputDirectory);
        Path candidateOutput = outputDirectory.resolve("candidate-stops.csv");
        Path demandOutput = outputDirectory.resolve("demand-towns.csv");

        long totalRows = 0;
        long malformedRows = 0;
        long candidateRowsBeforeDeduplication = 0;
        long duplicateCandidateRows = 0;
        long demandRows = 0;
        Set<String> seenCandidateCoordinates = new HashSet<>();

        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8);
             BufferedWriter candidateWriter = Files.newBufferedWriter(candidateOutput, StandardCharsets.UTF_8);
             BufferedWriter demandWriter = Files.newBufferedWriter(demandOutput, StandardCharsets.UTF_8)) {

            candidateWriter.write("geoname_id,name,latitude,longitude,feature_code\n");
            demandWriter.write("geoname_id,name,latitude,longitude,feature_code,population\n");

            String line;
            while ((line = reader.readLine()) != null) {
                totalRows++;
                String[] fields = line.split("\\t", -1);
                if (fields.length <= POPULATION) {
                    malformedRows++;
                    continue;
                }

                try {
                    double latitude = Double.parseDouble(fields[LATITUDE]);
                    double longitude = Double.parseDouble(fields[LONGITUDE]);
                    long population = Long.parseLong(fields[POPULATION]);
                    String featureClass = fields[FEATURE_CLASS];
                    String featureCode = fields[FEATURE_CODE];

                    if (longitude >= PENINSULAR_LONGITUDE_LIMIT) {
                        continue;
                    }

                    boolean populatedPlace = "P".equals(featureClass);
                    boolean inactivePlace = "PPLH".equals(featureCode) || "PPLQ".equals(featureCode);

                    if (populatedPlace && !inactivePlace) {
                        candidateRowsBeforeDeduplication++;
                        String coordinateKey = coordinateKey(latitude, longitude);
                        if (seenCandidateCoordinates.add(coordinateKey)) {
                            writeCsvRow(candidateWriter,
                                    fields[GEONAME_ID], fields[NAME], fields[LATITUDE], fields[LONGITUDE], featureCode);
                        } else {
                            duplicateCandidateRows++;
                        }
                    }

                    boolean isDemandTown = populatedPlace
                            && population > 0
                            && !"PPLX".equals(featureCode);
                    if (isDemandTown) {
                        demandRows++;
                        writeCsvRow(demandWriter,
                                fields[GEONAME_ID], fields[NAME], fields[LATITUDE], fields[LONGITUDE],
                                featureCode, Long.toString(population));
                    }
                } catch (NumberFormatException exception) {
                    malformedRows++;
                }
            }
        }

        return new PreprocessingSummary(
                totalRows,
                malformedRows,
                candidateRowsBeforeDeduplication,
                candidateRowsBeforeDeduplication - duplicateCandidateRows,
                duplicateCandidateRows,
                demandRows,
                candidateOutput,
                demandOutput);
    }

    private static String coordinateKey(double latitude, double longitude) {
        return String.format(Locale.ROOT, "%.8f,%.8f", latitude, longitude);
    }

    private static void writeCsvRow(BufferedWriter writer, String... values) throws IOException {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                writer.write(',');
            }
            writer.write(csvEscape(values[index]));
        }
        writer.newLine();
    }

    private static String csvEscape(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    public record PreprocessingSummary(
            long totalRows,
            long malformedRows,
            long candidateRowsBeforeDeduplication,
            long candidateRowsAfterDeduplication,
            long duplicateCandidateRows,
            long demandRows,
            Path candidateOutput,
            Path demandOutput) {

        public String toDisplayString() {
            return "Preprocessing complete\n"
                    + "  Rows read: " + totalRows + "\n"
                    + "  Malformed rows skipped: " + malformedRows + "\n"
                    + "  Peninsular candidate stops before deduplication: " + candidateRowsBeforeDeduplication + "\n"
                    + "  Duplicate-coordinate candidates removed: " + duplicateCandidateRows + "\n"
                    + "  Peninsular candidate stops: " + candidateRowsAfterDeduplication + "\n"
                    + "  Peninsular demand towns: " + demandRows + "\n"
                    + "  Wrote: " + candidateOutput + "\n"
                    + "  Wrote: " + demandOutput;
        }
    }
}

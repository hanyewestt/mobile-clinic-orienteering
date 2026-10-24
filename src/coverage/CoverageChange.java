package coverage;

/** The additional coverage gained when a candidate stop is accepted into a route. */
public record CoverageChange(int newlyCoveredTowns, long newlyCoveredPopulation) {
    public CoverageChange {
        if (newlyCoveredTowns < 0 || newlyCoveredPopulation < 0) {
            throw new IllegalArgumentException("Coverage gains must not be negative.");
        }
    }
}

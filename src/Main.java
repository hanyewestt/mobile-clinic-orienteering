import experiments.ExperimentRunner;

/** Plain-Java entry point. Delegates to the experiment runner. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        ExperimentRunner.main(args);
    }
}

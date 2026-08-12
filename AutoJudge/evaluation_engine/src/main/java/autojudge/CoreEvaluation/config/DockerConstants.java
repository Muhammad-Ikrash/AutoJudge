package autojudge.CoreEvaluation.config;

public final class DockerConstants {

    // Universal container image used for all evaluations
    public static final String DEFAULT_IMAGE = "auto-judge-container:v1.0";
    public static final String DEFAULT_NETWORK_MODE = "none";
    public static final String DEFAULT_WORKING_DIRECTORY = "/workspace";

    // Timeouts and Resource Limits
    public static final long DEFAULT_COMPILE_TIMEOUT_SEC = 30L;
    public static final String SIGKILL_GRACE_PERIOD = "1s";
    public static final long DEFAULT_PIDS_LIMIT = 100L;
    public static final int MAX_OUTPUT_BYTES = 1_000_000;
    public static final int MAX_STREAM_BYTES = 1_000_000_000;
    public static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

    private DockerConstants() {
    }
}
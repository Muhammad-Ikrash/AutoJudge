package autojudge.docker;

public final class DockerConstants {

    // Universal container image used for all evaluations
    public static final String DEFAULT_IMAGE = "auto-judge-container:v1.0";

    public static final String DEFAULT_NETWORK_MODE = "none";
    public static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

    private DockerConstants() {
    }
}
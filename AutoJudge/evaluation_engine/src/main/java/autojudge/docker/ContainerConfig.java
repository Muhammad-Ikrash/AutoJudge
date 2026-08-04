package autojudge.docker;

import autojudge.model.Assignment;

public record ContainerConfig(
    String image,
    long memoryLimitMb,
    double cpuLimit,
    String networkMode,
    String workingDirectory,
    boolean autoRemove,
    long timeLimitMs
) {

    public ContainerConfig(
        String image,
        long memoryLimitMb,
        double cpuLimit,
        String networkMode,
        String workingDirectory,
        boolean autoRemove
    ) {
        this(image, memoryLimitMb, cpuLimit, networkMode, workingDirectory, autoRemove, 5000L);
    }

    public static ContainerConfig from(Assignment assignment) {
        return from(assignment, DockerConstants.DEFAULT_IMAGE, DockerConstants.DEFAULT_NETWORK_MODE);
    }

    public static ContainerConfig from(Assignment assignment, String image) {
        return from(assignment, image, DockerConstants.DEFAULT_NETWORK_MODE);
    }

    public static ContainerConfig from(Assignment assignment, String image, String networkMode) {
        long timeLimit = assignment != null && assignment.resourceLimits() != null
                ? assignment.resourceLimits().timeLimitMs()
                : 5000L;
        if (timeLimit <= 0) {
            timeLimit = 5000L;
        }

        long memoryMb = assignment != null && assignment.resourceLimits() != null
                ? assignment.resourceLimits().memoryLimitMb()
                : 256L;

        double cpuLimit = assignment != null && assignment.resourceLimits() != null
                ? assignment.resourceLimits().cpuLimit()
                : 1.0;

        String workDir = assignment != null && assignment.executionProfile() != null
                ? assignment.executionProfile().workingDirectory()
                : "/workspace";

        boolean autoRemove = assignment != null && assignment.executionProfile() != null
                && assignment.executionProfile().autoRemove();

        return new ContainerConfig(
            image != null ? image : DockerConstants.DEFAULT_IMAGE,
            memoryMb,
            cpuLimit,
            networkMode,
            workDir,
            autoRemove,
            timeLimit
        );
    }

    public long memoryLimitBytes() {
        return memoryLimitMb * DockerConstants.BYTES_PER_MEGABYTE;
    }

    public long timeLimitSeconds() {
        return Math.max(1L, (long) Math.ceil(timeLimitMs / 1000.0));
    }
}

package autojudge.docker;

import autojudge.model.Assignment;

public record ContainerConfig(
    String image,
    long memoryLimitMb,
    double cpuLimit,
    String networkMode,
    String workingDirectory,
    boolean autoRemove
) {

    public static ContainerConfig from(Assignment assignment, String image) {
        return from(assignment, image, DockerConstants.DEFAULT_NETWORK_MODE);
    }

    public static ContainerConfig from(Assignment assignment, String image, String networkMode) {
        return new ContainerConfig(
            image,
            assignment.resourceLimits().memoryLimitMb(),
            assignment.resourceLimits().cpuLimit(),
            networkMode,
            assignment.executionProfile().workingDirectory(),
            assignment.executionProfile().autoRemove()
        );
    }

    public long memoryLimitBytes() {
        return memoryLimitMb * DockerConstants.BYTES_PER_MEGABYTE;
    }
}


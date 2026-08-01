package autojudge.Docker;

public record ContainerConfig (
    String image,
    long memoryLimit,
    double cpuLimit,
    String networkMode,
    String workingDirectory,
    boolean autoRemove

) {
    
}
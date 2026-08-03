package autojudge.Docker;

import java.nio.file.Path;

record ContainerConfig (
    String image,
    long memoryLimit,
    double cpuLimit,
    String networkMode,
    String workingDirectory,
    boolean autoRemove

) {
    
    ContainerConfig(Path jsonConfig) {
        // will write the mapping 
    }

}


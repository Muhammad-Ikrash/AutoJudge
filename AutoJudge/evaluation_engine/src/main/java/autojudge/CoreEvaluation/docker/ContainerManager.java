package autojudge.CoreEvaluation.docker;

import autojudge.CoreEvaluation.config.ContainerConfig;
import autojudge.CoreEvaluation.exception.DockerException;
import autojudge.CoreEvaluation.model.ExecCMD;

import java.nio.file.Path;
import java.util.List;

/**
 * Interface defining Docker container lifecycle and management operations.
 */
public interface ContainerManager {

    String createInstance(ContainerConfig config) throws DockerException;

    void startInstance(String id) throws DockerException;

    void stopContainer(String id);

    void removeContainer(String id);

    void copyToContainer(String containerId, Path hostPath, Path containerDirectory) throws DockerException;

    boolean directoryExists(String containerId, String containerPath);

    List<String> listFiles(String containerId, String containerPath);

    void removeFile(String containerId, String containerPath);

    boolean isRunning(String id);

    boolean isOOMKilled(String id);

    void kill(String id);

    ExecCMD exec(String id, List<String> command) throws DockerException;

    ExecCMD execInDir(String id, List<String> command, String workingDir) throws DockerException;

    ExecCMD execInDir(String id, List<String> command, String workingDir, long timeoutSeconds) throws DockerException;

    int countProcesses(String containerId) throws DockerException;
}

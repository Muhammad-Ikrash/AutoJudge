package autojudge.docker;

import autojudge.CoreEvaluation.config.ContainerConfig;
import autojudge.CoreEvaluation.docker.ContainerManager;
import autojudge.CoreEvaluation.exception.DockerException;
import autojudge.CoreEvaluation.model.ExecCMD;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MockContainerManager implements ContainerManager {

    private boolean destroyed = false;
    private boolean oomKilled = false;
    private List<String> simulatedFiles = new ArrayList<>();
    private final List<List<String>> listFilesQueue = new ArrayList<>();
    private ExecCMD execResponse = new ExecCMD(0, "hello", "");

    public boolean isDestroyed() {
        return destroyed;
    }

    public void setOomKilled(boolean oomKilled) {
        this.oomKilled = oomKilled;
    }

    public void setSimulatedFiles(List<String> simulatedFiles) {
        this.simulatedFiles = new ArrayList<>(simulatedFiles);
    }

    public void enqueueFilesResponse(List<String> files) {
        listFilesQueue.add(new ArrayList<>(files));
    }

    public void setExecResponse(ExecCMD execResponse) {
        this.execResponse = execResponse;
    }

    @Override
    public String createInstance(ContainerConfig config) throws DockerException {
        return "mock-container-123";
    }

    @Override
    public void startInstance(String id) throws DockerException {
    }

    @Override
    public void stopContainer(String id) {
        this.destroyed = true;
    }

    @Override
    public void removeContainer(String id) {
        this.destroyed = true;
    }

    @Override
    public void copyToContainer(String containerId, Path hostPath, Path containerDirectory) throws DockerException {
    }

    @Override
    public boolean directoryExists(String containerId, String containerPath) {
        return true;
    }

    @Override
    public List<String> listFiles(String containerId, String containerPath) {
        if (!listFilesQueue.isEmpty()) {
            return listFilesQueue.remove(0);
        }
        return simulatedFiles;
    }

    @Override
    public void removeFile(String containerId, String containerPath) {
    }

    @Override
    public boolean isRunning(String id) {
        return !destroyed;
    }

    @Override
    public boolean isOOMKilled(String id) {
        return oomKilled;
    }

    @Override
    public void kill(String id) {
        this.destroyed = true;
    }

    @Override
    public ExecCMD exec(String id, List<String> command) throws DockerException {
        return execResponse;
    }

    @Override
    public ExecCMD execInDir(String id, List<String> command, String workingDir) throws DockerException {
        return execResponse;
    }

    @Override
    public ExecCMD execInDir(String id, List<String> command, String workingDir, long timeoutSeconds) throws DockerException {
        return execResponse;
    }

    @Override
    public int countProcesses(String id) throws DockerException {
        return 1;
    }

}

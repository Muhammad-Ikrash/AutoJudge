package autojudge.docker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;

import autojudge.exception.DockerException;
import autojudge.model.ExecCMD;

class ContainerManager {

    private final DockerClient client;

    ContainerManager(DockerClient otherClient) {
        this.client = otherClient;
    }

    public String createInstance(ContainerConfig config) throws DockerException {
        HostConfig hostConfig = new HostConfig();
        if (config.memoryLimitBytes() > 0) {
            hostConfig = hostConfig.withMemory(config.memoryLimitBytes());
        }
        if (config.autoRemove()) {
            hostConfig = hostConfig.withAutoRemove(true);
        }

        try {
            return createContainerInternal(config, hostConfig);
        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            try {
                client.pullImageCmd(config.image()).start().awaitCompletion();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new DockerException("Interrupted while pulling image " + config.image(), ie);
            } catch (Exception pe) {
                throw new DockerException("Failed to pull image " + config.image(), pe);
            }
            return createContainerInternal(config, hostConfig);
        }
    }

    private String createContainerInternal(ContainerConfig config, HostConfig hostConfig) {
        CreateContainerCmd createCmd = client.createContainerCmd(config.image())
                .withHostConfig(hostConfig)
                .withTty(true)
                .withCmd("tail", "-f", "/dev/null");

        if (config.workingDirectory() != null && !config.workingDirectory().isBlank()) {
            createCmd.withWorkingDir(config.workingDirectory());
        }

        CreateContainerResponse response = createCmd.exec();
        return response.getId();
    }

    public void startInstance(String id) {
        client.startContainerCmd(id).exec();
    }

    public void stopContainer(String id) {
        try {
            client.stopContainerCmd(id).exec();
        } catch (Exception e) {
            // Ignore if container is already stopped or removed
        }
    }

    public void removeContainer(String id) {
        try {
            client.removeContainerCmd(id).withForce(true).exec();
        } catch (Exception e) {
            // Ignore if container is already removed
        }
    }

    public void copyToContainer(
            String containerId,
            Path hostPath,
            Path containerDirectory) throws DockerException {

        try {
            client.copyArchiveToContainerCmd(containerId)
                    .withHostResource(hostPath.toAbsolutePath().toString())
                    .withRemotePath(containerDirectory.toString())
                    .exec();
        } catch (Exception e) {
            throw new DockerException(
                    "Failed to copy " + hostPath + " to " + containerDirectory,
                    e);
        }
    }

    public boolean directoryExists(String containerId, String containerPath) {
        try {
            ExecCMD result = exec(containerId, List.of("test", "-d", containerPath));
            return result.getExitCode() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public List<String> listFiles(String containerId, String containerPath) {
        try {
            ExecCMD result = exec(containerId, List.of("find", containerPath, "-type", "f"));
            if (result.getExitCode() != 0 || result.getStdout() == null) {
                return List.of();
            }
            return Arrays.stream(result.getStdout().split("\\R"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .sorted()
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public void removeFile(String containerId, String containerPath) {
        try {
            exec(containerId, List.of("rm", "-rf", containerPath));
        } catch (Exception e) {
            // Ignore failure on cleanup
        }
    }

    public boolean isRunning(String id) {
        InspectContainerResponse info = client.inspectContainerCmd(id).exec();
        if (info == null || info.getState() == null)
            return false;
        Boolean running = info.getState().getRunning();
        return running != null && running;
    }

    public boolean isOOMKilled(String id) {
        try {
            InspectContainerResponse info = client.inspectContainerCmd(id).exec();
            if (info != null && info.getState() != null) {
                Boolean oom = info.getState().getOOMKilled();
                return oom != null && oom;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public void kill(String id) {
        try {
            client.killContainerCmd(id).exec();
        } catch (Exception e) {
            // Ignore if already dead
        }
    }

    public ExecCMD exec(String id, List<String> command) throws DockerException {
        return execInDir(id, command, null);
    }

    public ExecCMD execInDir(String id, List<String> command, String workingDir) throws DockerException {
        ExecCreateCmd execCmd = client.execCreateCmd(id)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withCmd(command.toArray(new String[0]));

        if (workingDir != null && !workingDir.isBlank()) {
            execCmd.withWorkingDir(workingDir);
        }

        ExecCreateCmdResponse response = execCmd.exec();

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        ResultCallback.Adapter<Frame> resultCallback = new ResultCallback.Adapter<Frame>() {
            @Override
            public void onNext(Frame frame) {
                if (frame != null) {
                    try {
                        switch (frame.getStreamType()) {
                            case STDOUT:
                            case RAW:
                                stdout.write(frame.getPayload());
                                break;
                            case STDERR:
                                stderr.write(frame.getPayload());
                                break;
                            default:
                                break;
                        }
                    } catch (IOException e) {
                        onError(e);
                    }
                }
            }
        };

        try {
            client.execStartCmd(response.getId())
                    .exec(resultCallback)
                    .awaitCompletion();

            InspectExecResponse inspectResponse = client.inspectExecCmd(response.getId()).exec();
            Long exitCode = inspectResponse.getExitCodeLong();

            return new ExecCMD(
                    exitCode != null ? exitCode.intValue() : -1,
                    stdout.toString(StandardCharsets.UTF_8),
                    stderr.toString(StandardCharsets.UTF_8));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DockerException(
                    "Interrupted while waiting for command to finish.",
                    e);
        } catch (Exception e) {
            throw new DockerException(
                    "Failed to execute command inside container " + id,
                    e);
        }
    }
}


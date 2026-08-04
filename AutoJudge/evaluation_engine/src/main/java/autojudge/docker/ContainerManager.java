package autojudge.docker;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.nio.charset.StandardCharsets;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.Frame;
import java.io.IOException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.HostConfig;

import autojudge.exception.DockerException;
import autojudge.model.ExecCMD;

final class ContainerManager {

    private final DockerClient client;

    ContainerManager(DockerClient otherClient) {
        this.client = otherClient;
    }

    public String createInstance(ContainerConfig config) {
        HostConfig hostConfig = new HostConfig();
        if (config.memoryLimitBytes() > 0) {
            hostConfig = hostConfig.withMemory(config.memoryLimitBytes());
        }
        if (config.autoRemove()) {
            hostConfig = hostConfig.withAutoRemove(true);
        }

        CreateContainerResponse response = client.createContainerCmd(config.image())
                .withHostConfig(hostConfig)
                .withWorkingDir(config.workingDirectory())
                .exec();

        return response.getId();
    }

    public void startInstance(String id) {
        client.startContainerCmd(id).exec();
    }

    public void stopContainer(String id) {
        client.stopContainerCmd(id).exec();
    }

    public void removeContainer(String id) {
        client.removeContainerCmd(id).withForce(true).exec();
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
                    "Failed to copy " + hostPath +
                            " to " + containerDirectory,
                    e);
        }
    }

    public boolean isRunning(String id) {
        InspectContainerResponse info = client.inspectContainerCmd(id).exec();
        if (info == null || info.getState() == null)
            return false;
        Boolean running = info.getState().getRunning();
        return running != null && running;
    }

    public void kill(String id) {
        client.killContainerCmd(id).exec();
    }

    public ExecCMD exec(String id, List<String> command) throws DockerException {

        ExecCreateCmdResponse response = client.execCreateCmd(id)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withCmd(command.toArray(new String[0]))
                .exec();

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
                    .awaitCompletion(); // This blocks execution cleanly until the remote process finishes

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

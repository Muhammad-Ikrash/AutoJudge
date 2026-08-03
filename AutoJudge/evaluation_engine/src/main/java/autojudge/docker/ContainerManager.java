package autojudge.docker;

import java.nio.file.Path;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.HostConfig;

public final class ContainerManager {

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

    public void copyToContainer(String id, Path file) {
        InspectContainerResponse info = client.inspectContainerCmd(id).exec();

        String remotePath = "/";
        if (info != null && info.getConfig() != null && info.getConfig().getWorkingDir() != null && !info.getConfig().getWorkingDir().isEmpty()) {
            remotePath = info.getConfig().getWorkingDir();
        }

        client.copyArchiveToContainerCmd(id)
                .withHostResource(file.toString())
                .withRemotePath(remotePath)
                .exec();
    }

    public boolean isRunning(String id) {
        InspectContainerResponse info = client.inspectContainerCmd(id).exec();
        if (info == null || info.getState() == null) return false;
        Boolean running = info.getState().getRunning();
        return running != null && running;
    }

    public void kill(String id) {
        client.killContainerCmd(id).exec();
    }

}

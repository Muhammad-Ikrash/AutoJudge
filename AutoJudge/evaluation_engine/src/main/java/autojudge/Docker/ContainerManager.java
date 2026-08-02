package autojudge.Docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.HostConfig;

class ContainerManager {

    private final DockerClient client;

    ContainerManager(DockerClient otherClient) {
        this.client = otherClient;
    }

    public String CreateInstance(ContainerConfig config) {

        // Create HostConfig and apply limits
        HostConfig hostConfig = new HostConfig();
        if (config.memoryLimit() > 0) {
            hostConfig = hostConfig.withMemory(config.memoryLimit());       // in bytes
        }
        if (config.autoRemove()) {
            hostConfig = hostConfig.withAutoRemove(true);
        }

        // Create container with provided image and working directory
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
        
        // Force remove to ensure container is removed even if running
        client.removeContainerCmd(id).withForce(true).exec();
    }

    public void copyToContainer(String id, String file) {
        
        // Inspect container to determine working directory (fallback to /)
        InspectContainerResponse info = client.inspectContainerCmd(id).exec();


        String remotePath = "/";
        if (info != null && info.getConfig() != null && info.getConfig().getWorkingDir() != null && !info.getConfig().getWorkingDir().isEmpty()) {
            remotePath = info.getConfig().getWorkingDir();
        }

        // Copy host resource (file or tar archive) into container
        client.copyArchiveToContainerCmd(id)
                .withHostResource(file)
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

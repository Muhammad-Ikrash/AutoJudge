package autojudge.Docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;

final class DockerClientFactory {

    private static final DockerClient CLIENT =
            DockerClientBuilder.getInstance().build();

    private DockerClientFactory() {
        // Prevent object creation
    }

    public static DockerClient getClient() {
        return CLIENT;
    }
}
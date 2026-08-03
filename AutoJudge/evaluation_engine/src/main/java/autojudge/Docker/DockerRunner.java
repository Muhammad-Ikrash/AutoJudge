package autojudge.Docker;

import java.nio.file.Path;
import java.util.concurrent.SubmissionPublisher;

import com.github.dockerjava.api.DockerClient;
import autojudge.Models.ExecutionResult;
import autojudge.Models.Submission;
import autojudge.Models.TestCase;

public class DockerRunner {

    private final ContainerManager containerManager;


    private DockerRunner(){
        DockerClient client = DockerClientFactory.getClient();
        this.containerManager = new ContainerManager(client);
    }

    public ExecutionResult runSubmission(
        Path config,
        TestCase sub
    ){
        
    }



}

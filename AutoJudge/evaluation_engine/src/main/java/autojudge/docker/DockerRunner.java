package autojudge.docker;

import autojudge.compiler.CompileCommandBuilder;
import autojudge.compiler.Compiler;
import autojudge.compiler.SubmissionLayout;
import autojudge.compiler.SubmissionScanner;
import autojudge.exception.CompilationException;
import autojudge.exception.DockerException;
import autojudge.exception.ExecutionException;
import autojudge.model.ExecCMD;
import autojudge.model.ExecutionResult;
import autojudge.model.Language;
import autojudge.model.Submission;
import autojudge.model.SubmissionResult;
import autojudge.model.TestCase;
import autojudge.model.Verdict;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class DockerRunner {

    private final ContainerManager containerManager;

    public DockerRunner() {
        this(new ContainerManager(DockerClientFactory.getClient()));
    }

    DockerRunner(ContainerManager containerManager) {
        this.containerManager = Objects.requireNonNull(containerManager, "containerManager");
    }

    public SubmissionResult evaluate(Submission submission, ContainerConfig config)
            throws CompilationException, DockerException, IOException {

        String containerId = CreateExecContainer(config);

        SubmissionLayout layout = SubmissionScanner.scan(submission.getSubmissionRoot());

        compileSubmission(containerId, layout);

        executeTestCase(containerId, layout);

    };

    private String CreateExecContainer(ContainerConfig config) {
        String containerId = containerManager.createInstance(config);
        return containerId;
    }

    private void copySubmission(
            String containerId,
            Submission submission,
            ContainerConfig config) throws DockerException {

        try {

            containerManager.copyToContainer(
                    containerId,
                    submission.getSubmissionRoot(),
                    Path.of(config.getWorkingDirectory()));

        } catch (Exception e) {

            throw new DockerException(
                    "Failed to copy submission into container.",
                    e);

        }
    }

    private void compileSubmission(
            String containerId,
            SubmissionLayout layout) throws CompilationException, DockerException, IOException {

        List<String> compileCommand = CompileCommandBuilder.buildCommand(layout);

        // Python
        if (compileCommand.isEmpty())
            return;

        ExecCMD result = containerManager.exec(containerId, compileCommand);

        if (result.getExitCode() != 0) {
            throw new CompilationException(result.getStderr());
        }
    }

    private ExecutionResult executeTestCase() {

    }

    private Verdict determineVerdict() {

    }

    private Verdict cleanUpForNextTestCase() {

    }

    private void copyNextTestCase() {

    }

    private void containerCleanUp() {

    }

}

package autojudge.docker;

import autojudge.compiler.CompileCommandBuilder;
import autojudge.config.DockerConstants;
import autojudge.exception.DockerException;
import autojudge.model.ExecCMD;
import autojudge.model.SubmissionLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Responsible for compiling submission source files inside the evaluation container.
 */
public class CompilationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CompilationOrchestrator.class);
    private final ContainerManager containerManager;

    public CompilationOrchestrator(ContainerManager containerManager) {
        this.containerManager = Objects.requireNonNull(containerManager, "containerManager must not be null");
    }

    public ExecCMD compile(String containerId, String submissionDir, SubmissionLayout layout) throws DockerException {
        List<String> rawCompileCommand;
        try {
            rawCompileCommand = CompileCommandBuilder.buildCommand(layout);
        } catch (Exception e) {
            throw new DockerException("Failed to build compile command for layout: " + layout, e);
        }

        if (rawCompileCommand.isEmpty()) {
            log.debug("No compilation step required for language {}", layout.language());
            return new ExecCMD(0, "", "");
        }

        String compileScript = "timeout -k " + DockerConstants.SIGKILL_GRACE_PERIOD + " "
                + DockerConstants.DEFAULT_COMPILE_TIMEOUT_SEC + "s "
                + String.join(" ", rawCompileCommand);

        log.debug("Executing compilation in container {} at {}: {}", containerId, submissionDir, compileScript);
        return containerManager.execInDir(containerId, List.of("sh", "-c", compileScript), submissionDir);
    }

    public boolean isCompileFailed(ExecCMD compileResult) {
        return compileResult != null && compileResult.getExitCode() != 0;
    }
}

package autojudge.docker;

import autojudge.config.DockerConstants;
import autojudge.model.ExecCMD;

/**
 * Handles output size validation, truncation, and output limit enforcement.
 */
public class OutputValidator {

    private final int maxOutputBytes;

    public OutputValidator() {
        this(DockerConstants.MAX_OUTPUT_BYTES);
    }

    public OutputValidator(int maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }

    public String truncateIfNeeded(String output) {
        if (output == null) {
            return "";
        }
        if (output.length() > maxOutputBytes) {
            return output.substring(0, maxOutputBytes) + "\n[Output truncated]";
        }
        return output;
    }

    public boolean isOutputExceeded(ExecCMD execResult) {
        if (execResult == null) {
            return false;
        }
        String stdout = execResult.getStdout() != null ? execResult.getStdout() : "";
        String stderr = execResult.getStderr() != null ? execResult.getStderr() : "";
        return execResult.isTruncated()
                || stdout.length() > maxOutputBytes
                || stderr.length() > maxOutputBytes;
    }

    public String processStderr(String stderr, boolean outputExceeded) {
        String processed = truncateIfNeeded(stderr);
        if (outputExceeded) {
            processed = processed.isEmpty() ? "Output limit exceeded" : processed + "\nOutput limit exceeded";
        }
        return processed;
    }
}

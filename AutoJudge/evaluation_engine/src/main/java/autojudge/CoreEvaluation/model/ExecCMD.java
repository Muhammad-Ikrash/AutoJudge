package autojudge.CoreEvaluation.model;

public class ExecCMD {
    private final int exitCode;
    private final String stdout;
    private final String stderr;
    private final boolean truncated;

    public ExecCMD(int exitCode, String stdout, String stderr) {
        this(exitCode, stdout, stderr, false);
    }

    public ExecCMD(int exitCode, String stdout, String stderr, boolean truncated) {
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
        this.truncated = truncated;
    }

    public int getExitCode() { return exitCode; }
    public String getStdout() { return stdout; }
    public String getStderr() { return stderr; }
    public boolean isTruncated() { return truncated; }
}

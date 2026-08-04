package autojudge.model;

public class ExecCMD {
    private final int exitCode;
    private final String stdout;
    private final String stderr;

    public ExecCMD(int exitCode, String stdout, String stderr) {
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
    }

    // Getters for completed, exitCode, stdout, and stderr...
    public int getExitCode() { return exitCode; }
    public String getStdout() { return stdout; }
    public String getStderr() { return stderr; }
}

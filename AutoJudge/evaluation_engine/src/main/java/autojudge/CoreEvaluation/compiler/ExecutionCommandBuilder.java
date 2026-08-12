package autojudge.CoreEvaluation.compiler;

import autojudge.CoreEvaluation.model.SubmissionLayout;

public final class ExecutionCommandBuilder {

    private ExecutionCommandBuilder() {
    }

    public static String buildExecutionCommand(SubmissionLayout layout, String inputFileName) {
        String safeInputFileName = "'" + inputFileName.replace("'", "'\\''") + "'";
        return switch (layout.language()) {
            case CPP, C -> "./solution < " + safeInputFileName;
            case PYTHON -> {
                String mainScript = layout.sourceFiles().isEmpty() ? "main.py" : layout.sourceFiles().get(0).toString();
                yield "python3 " + mainScript + " < " + safeInputFileName;
            }
            case JAVA -> {
                String mainClass = layout.sourceFiles().isEmpty() ? "Main" : layout.sourceFiles().get(0).toString().replace(".java", "");
                yield "java " + mainClass + " < " + safeInputFileName;
            }
        };
    }
}

package autojudge.docker;

import autojudge.compiler.Compiler;
import autojudge.model.ExecutionResult;
import autojudge.model.Language;
import autojudge.model.Submission;
import autojudge.model.TestCase;
import autojudge.model.Verdict;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DockerRunner {

    private final Compiler compiler;
    private final ContainerManager containerManager;

    public DockerRunner() {
        this(new Compiler(), new ContainerManager(DockerClientFactory.getClient()));
    }

    DockerRunner(Compiler compiler, ContainerManager containerManager) {
        this.compiler = Objects.requireNonNull(compiler, "compiler");
        this.containerManager = Objects.requireNonNull(containerManager, "containerManager");
    }

    public List<ExecutionResult> runSubmission(
        ContainerConfig containerConfig,
        Submission submission,
        List<TestCase> testCases
    ) {
        ensureReady();
        List<ExecutionResult> results = new ArrayList<>();
        for (TestCase testCase : testCases) {
            results.add(runTestCase(containerConfig, submission, testCase));
        }
        return results;
    }

    public ExecutionResult runTestCase(
        ContainerConfig containerConfig,
        Submission submission,
        TestCase testCase
    ) {
        ensureReady();
        try {
            Language language = resolveLanguage(submission, containerConfig);
            Path sourceDirectory = resolveSourceDirectory(submission);
            Path buildDirectory = Files.createTempDirectory("autojudge-build-");
            List<Path> sourceFiles = compiler.collectSourceFiles(language, sourceDirectory);
            if (sourceFiles.isEmpty()) {
                return executionResult(testCase, Verdict.COMPILATION_ERROR, "", "No source files found", 1, 0, 0);
            }

            String outputName = resolveOutputName(language, sourceFiles);
            Compiler.CompileResult compileResult = compiler.compile(language, sourceDirectory, buildDirectory, outputName);
            if (!compileResult.success()) {
                return executionResult(
                    testCase,
                    Verdict.COMPILATION_ERROR,
                    compileResult.standardOutput(),
                    compileResult.errorOutput(),
                    compileResult.exitCode(),
                    0,
                    0
                );
            }

            ProcessResult processResult = executeProgram(language, sourceDirectory, buildDirectory, outputName, testCase);
            Verdict verdict = processResult.exitCode == 0 ? Verdict.ACCEPTED : Verdict.RUNTIME_ERROR;
            return executionResult(
                testCase,
                verdict,
                processResult.standardOutput,
                processResult.errorOutput,
                processResult.exitCode,
                processResult.executionTimeMs,
                0
            );
        } catch (Exception exception) {
            return executionResult(testCase, Verdict.INTERNAL_ERROR, "", exception.getMessage(), 1, 0, 0);
        }
    }

    private void ensureReady() {
        Objects.requireNonNull(compiler, "compiler");
        Objects.requireNonNull(containerManager, "containerManager");
    }

    private Language resolveLanguage(Submission submission, ContainerConfig containerConfig) {
        String fileName = submission.filePath() == null ? "" : submission.filePath().getFileName().toString().toLowerCase();
        if (fileName.endsWith(".py")) {
            return Language.PYTHON;
        }
        if (fileName.endsWith(".java")) {
            return Language.JAVA;
        }
        if (fileName.endsWith(".cpp") || fileName.endsWith(".cc") || fileName.endsWith(".cxx") || fileName.endsWith(".hpp") || fileName.endsWith(".h")) {
            return Language.CPP;
        }

        String image = containerConfig.image() == null ? "" : containerConfig.image().toLowerCase();
        if (image.contains("python")) {
            return Language.PYTHON;
        }
        if (image.contains("java") || image.contains("jdk")) {
            return Language.JAVA;
        }
        if (image.contains("gcc") || image.contains("cpp")) {
            return Language.CPP;
        }

        throw new IllegalArgumentException("Unable to determine submission language from submission or container image");
    }

    private Path resolveSourceDirectory(Submission submission) {
        if (submission.filePath() == null) {
            return Path.of(".");
        }
        if (Files.isDirectory(submission.filePath())) {
            return submission.filePath();
        }
        return submission.filePath().getParent() != null ? submission.filePath().getParent() : Path.of(".");
    }

    private String resolveOutputName(Language language, List<Path> sourceFiles) {
        if (sourceFiles.isEmpty()) {
            return "app";
        }
        String fileName = sourceFiles.get(0).getFileName().toString();
        if (language == Language.JAVA) {
            return fileName.replaceFirst("\\.java$", "");
        }
        return fileName.replaceFirst("\\.(cpp|cc|cxx|hpp|h|py)$", "");
    }

    private ProcessResult executeProgram(
        Language language,
        Path sourceDirectory,
        Path buildDirectory,
        String outputName,
        TestCase testCase
    ) throws IOException, InterruptedException {
        List<String> command = switch (language) {
            case PYTHON -> List.of("python3", resolvePrimarySourceFile(sourceDirectory, language).toString());
            case JAVA -> List.of(
                "java",
                "-cp",
                buildDirectory.toString(),
                resolvePrimarySourceFile(sourceDirectory, language).getFileName().toString().replaceFirst("\\.java$", "")
            );
            case CPP -> List.of(buildDirectory.resolve(outputName).toString());
        };

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(sourceDirectory.toFile());
        processBuilder.redirectInput(testCase.inputFile().toFile());
        processBuilder.redirectErrorStream(false);
        long startedAt = System.nanoTime();
        Process process = processBuilder.start();
        String standardOutput = readStream(process.getInputStream());
        String errorOutput = readStream(process.getErrorStream());
        int exitCode = process.waitFor();
        long executionTimeMs = (System.nanoTime() - startedAt) / 1_000_000L;
        return new ProcessResult(exitCode, standardOutput, errorOutput, executionTimeMs);
    }

    private Path resolvePrimarySourceFile(Path sourceDirectory, Language language) throws IOException {
        List<Path> sourceFiles = compiler.collectSourceFiles(language, sourceDirectory);
        if (sourceFiles.isEmpty()) {
            throw new IllegalArgumentException("No source files found for execution");
        }
        return sourceFiles.get(0);
    }

    private ExecutionResult executionResult(
        TestCase testCase,
        Verdict verdict,
        String standardOutput,
        String errorOutput,
        int exitCode,
        long executionTime,
        long memoryUsed
    ) {
        return new ExecutionResult(
            testCase.id(),
            verdict,
            standardOutput,
            errorOutput,
            "",
            exitCode,
            executionTime,
            memoryUsed
        );
    }

    private String readStream(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append(System.lineSeparator());
            }
        }
        return builder.toString();
    }

    private record ProcessResult(
        int exitCode,
        String standardOutput,
        String errorOutput,
        long executionTimeMs
    ) {
    }
}

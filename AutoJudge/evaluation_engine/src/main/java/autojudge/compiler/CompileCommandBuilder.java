package autojudge.compiler;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import autojudge.model.SubmissionLayout;

public final class CompileCommandBuilder {

    private CompileCommandBuilder() {
    }

    public static List<String> buildCommand(SubmissionLayout layout) throws IOException {
        return switch (layout.language()) {
            case CPP -> buildCppCommand(layout);
            case C -> buildCCommand(layout);
            case PYTHON -> buildPythonCommand(layout);
            case JAVA -> buildJavaCommand(layout);
        };
    }

    private static List<String> buildPythonCommand(SubmissionLayout layout) {
        return List.of();
    }

    private static List<String> buildCCommand(SubmissionLayout layout) {
        List<String> command = new ArrayList<>();
        command.add("gcc");
        command.add("-std=c17");
        command.add("-O2");
        command.add("-Wall");
        layout.sourceFiles()
                .stream()
                .map(Path::toString)
                .forEach(command::add);
        command.add("-o");
        command.add("solution");
        return command;
    }

    private static List<String> buildCppCommand(SubmissionLayout layout) {
        List<String> command = new ArrayList<>();
        command.add("g++");
        command.add("-std=c++20");
        command.add("-O2");
        command.add("-Wall");
        layout.sourceFiles()
                .stream()
                .map(Path::toString)
                .forEach(command::add);
        command.add("-o");
        command.add("solution");
        return command;
    }

    private static List<String> buildJavaCommand(SubmissionLayout layout) {
        List<String> command = new ArrayList<>();
        command.add("javac");
        layout.sourceFiles()
                .stream()
                .map(Path::toString)
                .forEach(command::add);
        return command;
    }
}
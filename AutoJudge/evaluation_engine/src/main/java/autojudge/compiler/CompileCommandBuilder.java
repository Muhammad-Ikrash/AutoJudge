package autojudge.compiler;

import autojudge.model.Language;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CompileCommandBuilder {

	public List<String> buildCommand(
		Language language,
		List<Path> sourceFiles,
		Path outputDirectory,
		String outputName
	) {
		return switch (language) {
			case CPP -> buildCppCommand(sourceFiles, outputDirectory, outputName);
			case JAVA -> buildJavaCommand(sourceFiles, outputDirectory);
			case PYTHON -> buildPythonCommand(sourceFiles, outputDirectory);
		};
	}

	private List<String> buildCppCommand(List<Path> sourceFiles, Path outputDirectory, String outputName) {
		List<String> command = new ArrayList<>();
		command.add("g++");
		command.add("-std=c++17");
		command.add("-O2");
		command.add("-o");
		command.add(outputDirectory.resolve(outputName).toString());
		for (Path sourceFile : sourceFiles) {
			command.add(sourceFile.toString());
		}
		return command;
	}

	private List<String> buildJavaCommand(List<Path> sourceFiles, Path outputDirectory) {
		List<String> command = new ArrayList<>();
		command.add("javac");
		command.add("-d");
		command.add(outputDirectory.toString());
		for (Path sourceFile : sourceFiles) {
			command.add(sourceFile.toString());
		}
		return command;
	}

	private List<String> buildPythonCommand(List<Path> sourceFiles, Path outputDirectory) {
		List<String> command = new ArrayList<>();
		command.add("python3");
		command.add("-m");
		command.add("py_compile");
		for (Path sourceFile : sourceFiles) {
			command.add(sourceFile.toString());
		}
		return command;
	}
}
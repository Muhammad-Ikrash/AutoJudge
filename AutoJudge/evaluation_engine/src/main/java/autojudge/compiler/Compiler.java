package autojudge.compiler;

import autojudge.model.Language;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Compiler {

	private final CompileCommandBuilder commandBuilder;

	public Compiler() {
		this.commandBuilder = new CompileCommandBuilder();
	}

	public CompileResult compile(
		Language language,
		Path sourceDirectory,
		Path outputDirectory,
		String outputName
	) throws IOException, InterruptedException {
		List<Path> sourceFiles = collectSourceFiles(language, sourceDirectory);
		List<String> command = commandBuilder.buildCommand(language, sourceFiles, outputDirectory, outputName);

		if (command.isEmpty()) {
			return new CompileResult(true, 0, "", "");
		}

		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.redirectErrorStream(false);
		Process process = processBuilder.start();

		String standardOutput = readStream(process.getInputStream());
		String errorOutput = readStream(process.getErrorStream());
		int exitCode = process.waitFor();

		return new CompileResult(exitCode == 0, exitCode, standardOutput, errorOutput);
	}

	public List<Path> collectSourceFiles(Language language, Path sourceDirectory) throws IOException {
		if (!Files.isDirectory(sourceDirectory)) {
			return List.of();
		}

		List<Path> sourceFiles = new ArrayList<>();
		try (var stream = Files.list(sourceDirectory)) {
			stream
				.filter(Files::isRegularFile)
				.filter(path -> isSourceFile(language, path))
				.sorted(Comparator.comparing(path -> path.getFileName().toString()))
				.forEach(sourceFiles::add);
		}
		return sourceFiles;
	}

	private boolean isSourceFile(Language language, Path path) {
		String fileName = path.getFileName().toString().toLowerCase();
		return switch (language) {
			case CPP -> fileName.endsWith(".cpp") || fileName.endsWith(".cc") || fileName.endsWith(".cxx") || fileName.endsWith(".hpp") || fileName.endsWith(".h");
			case JAVA -> fileName.endsWith(".java");
			case PYTHON -> fileName.endsWith(".py");
		};
	}

	private String readStream(java.io.InputStream inputStream) throws IOException {
		StringBuilder builder = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				builder.append(line).append(System.lineSeparator());
			}
		}
		return builder.toString();
	}

	public record CompileResult(
		boolean success,
		int exitCode,
		String standardOutput,
		String errorOutput
	) {
	}
}
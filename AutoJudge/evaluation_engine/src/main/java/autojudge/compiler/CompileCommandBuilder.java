package autojudge.compiler;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import autojudge.model.Submission;
public final class CompileCommandBuilder {

	private CompileCommandBuilder() {
	}

	public static List<String> buildCommand(SubmissionLayout layout) throws IOException {

		return switch (layout.language()) {

			case CPP -> buildCppCommand(layout);

			case C -> buildCCommand(layout);

			case PYTHON -> buildPythonCommand(layout);
			
			case JAVA -> List.of();
			// case JAVA -> buildJavaCommand(layout);				// commented as java is not allowed and will never be returned from Submission Scanner
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

	private static List<String> buildCppCommand(
			SubmissionLayout layout) {

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

}
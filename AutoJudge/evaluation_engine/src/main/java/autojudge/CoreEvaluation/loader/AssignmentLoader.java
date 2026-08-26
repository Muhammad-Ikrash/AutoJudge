package autojudge.CoreEvaluation.loader;
import autojudge.CoreEvaluation.model.Assignment;

import java.io.IOException;
import java.nio.file.Path;


public class AssignmentLoader {

	private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

	private AssignmentLoader() {
	}

	public static Assignment load(Path assignmentDirectory) throws IOException {
		return loadConfig(resolveConfigPath(assignmentDirectory));
	}

	public static Path resolveConfigPath(Path assignmentDirectory) {
		return assignmentDirectory.resolve("config.json");
	}

	public static Assignment loadConfig(Path configPath) throws IOException {
		return mapper.readValue(configPath.toFile(), Assignment.class);
	}

	public static void saveConfig(Path configPath, Assignment assignment) throws IOException {
		mapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), assignment);
	}
}
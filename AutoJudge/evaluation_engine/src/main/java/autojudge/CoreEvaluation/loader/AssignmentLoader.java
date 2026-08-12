package autojudge.CoreEvaluation.loader;
import autojudge.CoreEvaluation.model.Assignment;

import java.io.IOException;
import java.nio.file.Path;


public class AssignmentLoader {

	private AssignmentLoader() {
	}

	public static Assignment load(Path assignmentDirectory) throws IOException {
		return loadConfig(resolveConfigPath(assignmentDirectory));
	}

	public static Path resolveConfigPath(Path assignmentDirectory) {
		return assignmentDirectory.resolve("config.json");
	}

	public static Assignment loadConfig(Path configPath) throws IOException {
		String json = ConfigLoader.load(configPath);
		Assignment.ResourceLimits resourceLimits = new Assignment.ResourceLimits(
			extractLong(json, "timeLimitMs"),
			(int) extractLong(json, "memoryLimitMb"),
			extractDouble(json, "cpuLimit")
		);
		Assignment.ExecutionProfile executionProfile = new Assignment.ExecutionProfile(
			extractBoolean(json, "autoRemove"),
			extractString(json, "workingDirectory")
		);
		return new Assignment(
			extractString(json, "assignmentId"),
			resourceLimits,
			executionProfile
		);
	}

	private static String extractString(String json, String key) {
		String marker = "\"" + key + "\"";
		int keyIndex = json.indexOf(marker);
		if (keyIndex < 0) {
			return "";
		}
		int colonIndex = json.indexOf(':', keyIndex + marker.length());
		int firstQuote = json.indexOf('"', colonIndex + 1);
		int secondQuote = json.indexOf('"', firstQuote + 1);
		if (firstQuote < 0 || secondQuote < 0) {
			return "";
		}
		return json.substring(firstQuote + 1, secondQuote);
	}

	private static boolean extractBoolean(String json, String key) {
		String marker = "\"" + key + "\"";
		int keyIndex = json.indexOf(marker);
		if (keyIndex < 0) {
			return false;
		}
		int colonIndex = json.indexOf(':', keyIndex + marker.length());
		if (colonIndex < 0) {
			return false;
		}
		String tail = json.substring(colonIndex + 1).trim();
		return tail.startsWith("true");
	}

	private static long extractLong(String json, String key) {
		String marker = "\"" + key + "\"";
		int keyIndex = json.indexOf(marker);
		if (keyIndex < 0) {
			return 0L;
		}
		int colonIndex = json.indexOf(':', keyIndex + marker.length());
		if (colonIndex < 0) {
			return 0L;
		}
		int endIndex = colonIndex + 1;
		while (endIndex < json.length()) {
			char current = json.charAt(endIndex);
			if ((current >= '0' && current <= '9') || current == '.' || current == '-' || current == '+') {
				endIndex++;
				continue;
			}
			break;
		}
		String numeric = json.substring(colonIndex + 1, endIndex).trim();
		if (numeric.isEmpty()) {
			return 0L;
		}
		return Math.round(Double.parseDouble(numeric));
	}

	private static double extractDouble(String json, String key) {
		return (double) extractLong(json, key);
	}
}
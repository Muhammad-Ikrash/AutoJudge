package autojudge.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class JsonUtils {

	public static String readJson(Path path) throws IOException {
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	public static void writeJson(Path path, String json) throws IOException {
		FileUtils.writeText(path, json);
	}

	public static String compactJson(String json) {
		StringBuilder result = new StringBuilder(json.length());
		boolean insideString = false;
		boolean escaped = false;

		for (int index = 0; index < json.length(); index++) {
			char current = json.charAt(index);
			if (escaped) {
				result.append(current);
				escaped = false;
				continue;
			}
			if (current == '\\') {
				result.append(current);
				escaped = insideString;
				continue;
			}
			if (current == '"') {
				insideString = !insideString;
				result.append(current);
				continue;
			}
			if (!insideString && Character.isWhitespace(current)) {
				continue;
			}
			result.append(current);
		}

		return result.toString();
	}
}
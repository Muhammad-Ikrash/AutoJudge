package autojudge.CoreEvaluation.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtils {

	public static String readText(Path path) throws IOException {
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	public static void writeText(Path path, String content) throws IOException {
		ensureParentDirectory(path);
		Files.writeString(path, content, StandardCharsets.UTF_8);
	}

	public static byte[] readBytes(Path path) throws IOException {
		return Files.readAllBytes(path);
	}

	public static void writeBytes(Path path, byte[] content) throws IOException {
		ensureParentDirectory(path);
		Files.write(path, content);
	}

	public static void ensureParentDirectory(Path path) throws IOException {
		Path parent = path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
	}

	public static boolean exists(Path path) {
		return Files.exists(path);
	}

	public static void deleteIfExists(Path path) throws IOException {
		Files.deleteIfExists(path);
	}
}
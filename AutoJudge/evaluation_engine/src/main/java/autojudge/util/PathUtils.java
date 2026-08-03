package autojudge.util;

import java.nio.file.Path;

public class PathUtils {

	public static Path resolve(Path basePath, String... segments) {
		Path resolved = basePath;
		for (String segment : segments) {
			resolved = resolved.resolve(segment);
		}
		return resolved.normalize();
	}

	public static Path parentOrSelf(Path path) {
		Path parent = path.getParent();
		return parent != null ? parent : path;
	}

	public static Path replaceExtension(Path path, String newExtension) {
		String fileName = path.getFileName().toString();
		int dotIndex = fileName.lastIndexOf('.');
		String baseName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
		String suffix = newExtension.startsWith(".") ? newExtension : "." + newExtension;
		return path.resolveSibling(baseName + suffix);
	}

	public static String fileNameWithoutExtension(Path path) {
		String fileName = path.getFileName().toString();
		int dotIndex = fileName.lastIndexOf('.');
		return dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
	}

	public static String extension(Path path) {
		String fileName = path.getFileName().toString();
		int dotIndex = fileName.lastIndexOf('.');
		return dotIndex >= 0 ? fileName.substring(dotIndex + 1) : "";
	}
}
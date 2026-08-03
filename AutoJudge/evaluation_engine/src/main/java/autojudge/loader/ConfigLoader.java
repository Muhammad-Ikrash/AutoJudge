package autojudge.loader;

import java.io.IOException;
import java.nio.file.Path;

import autojudge.util.FileUtils;

public class ConfigLoader {

	private ConfigLoader() {
	}

	public static String load(Path configPath) throws IOException {
		return FileUtils.readText(configPath);
	}
}
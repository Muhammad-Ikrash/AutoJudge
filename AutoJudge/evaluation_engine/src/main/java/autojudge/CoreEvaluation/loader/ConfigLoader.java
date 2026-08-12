package autojudge.CoreEvaluation.loader;
import autojudge.CoreEvaluation.util.FileUtils;

import java.io.IOException;
import java.nio.file.Path;


public class ConfigLoader {

	private ConfigLoader() {
	}

	public static String load(Path configPath) throws IOException {
		return FileUtils.readText(configPath);
	}
}
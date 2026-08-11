package autojudge.loader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses test case weight configuration from JSON or key-value format files.
 */
public class WeightsFileParser {

    private static final Logger log = LoggerFactory.getLogger(WeightsFileParser.class);
    private static final Pattern WEIGHT_JSON_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\\d+)");

    public Map<String, Integer> parse(Path weightsFile) throws IOException {
        Map<String, Integer> weights = new HashMap<>();
        if (weightsFile == null || !Files.exists(weightsFile)) {
            log.debug("Weights file {} does not exist. Using default weights of 1.", weightsFile);
            return weights;
        }

        String content = Files.readString(weightsFile);
        Matcher jsonMatcher = WEIGHT_JSON_PATTERN.matcher(content);
        while (jsonMatcher.find()) {
            weights.put(jsonMatcher.group(1), Integer.parseInt(jsonMatcher.group(2)));
        }

        if (!weights.isEmpty()) {
            log.info("Parsed {} weights from JSON format in {}", weights.size(), weightsFile);
            return weights;
        }

        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            String[] parts = trimmed.split("[,:=\\s]+", 2);
            if (parts.length == 2) {
                try {
                    weights.put(parts[0].trim(), Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException e) {
                    log.warn("Invalid weight format on line '{}' in {}", line, weightsFile);
                }
            }
        }

        log.info("Parsed {} weights from key-value format in {}", weights.size(), weightsFile);
        return weights;
    }
}

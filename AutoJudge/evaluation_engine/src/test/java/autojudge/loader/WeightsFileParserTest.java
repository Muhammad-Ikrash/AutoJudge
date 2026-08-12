package autojudge.loader;

import autojudge.CoreEvaluation.loader.WeightsFileParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeightsFileParserTest {

    @TempDir
    Path tempDir;

    private final WeightsFileParser parser = new WeightsFileParser();

    @Test
    void testParseJsonWeights() throws Exception {
        Path jsonFile = tempDir.resolve("weights.json");
        Files.writeString(jsonFile, "{\n  \"case1\": 10,\n  \"case2\": 20\n}");

        Map<String, Integer> weights = parser.parse(jsonFile);
        assertEquals(10, weights.get("case1"));
        assertEquals(20, weights.get("case2"));
    }

    @Test
    void testParseKeyValueWeights() throws Exception {
        Path kvFile = tempDir.resolve("weights.txt");
        Files.writeString(kvFile, "# Weights config\ncase1 = 5\ncase2: 15\n");

        Map<String, Integer> weights = parser.parse(kvFile);
        assertEquals(5, weights.get("case1"));
        assertEquals(15, weights.get("case2"));
    }
}

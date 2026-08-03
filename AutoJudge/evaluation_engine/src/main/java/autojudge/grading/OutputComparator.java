package autojudge.grading;

public final class OutputComparator {

    public boolean matches(String expectedOutput, String actualOutput) {
        return normalize(expectedOutput).equals(normalize(actualOutput));
    }

    public String normalize(String output) {
        if (output == null || output.isEmpty()) {
            return "";
        }

        String normalized = output.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder builder = new StringBuilder();

        for (int index = 0; index < lines.length; index++) {
            builder.append(stripTrailingWhitespace(lines[index]));
            if (index < lines.length - 1) {
                builder.append('\n');
            }
        }

        return builder.toString().stripTrailing();
    }

    private String stripTrailingWhitespace(String value) {
        int endIndex = value.length();
        while (endIndex > 0 && Character.isWhitespace(value.charAt(endIndex - 1))) {
            endIndex--;
        }
        return value.substring(0, endIndex);
    }
}

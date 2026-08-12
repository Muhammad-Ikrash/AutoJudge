package autojudge.CoreEvaluation.loader;

import autojudge.CoreEvaluation.model.Submission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SubmissionLoader {

	private SubmissionLoader() {
	}

	public static Submission load(
		Path submissionFilePath,
		Path inputFilePath,
		Path expectedOutputFilePath,
		String studentId,
		String assignmentId
	) {
		return new Submission(
			submissionFilePath,
			inputFilePath,
			expectedOutputFilePath,
			studentId,
			assignmentId
		);
	}

	public static List<Submission> loadAll(
		Path submissionsDirectory,
		Path inputFilePath,
		Path expectedOutputFilePath,
		String assignmentId
	) throws IOException {
		List<Submission> submissions = new ArrayList<>();
		if (!Files.isDirectory(submissionsDirectory)) {
			return submissions;
		}

		List<Path> submissionFiles = Files.list(submissionsDirectory)
			.filter(Files::isRegularFile)
			.sorted(Comparator.comparing(path -> path.getFileName().toString()))
			.toList();

		for (Path submissionFile : submissionFiles) {
			String fileName = submissionFile.getFileName().toString();
			String studentId = fileName.contains(".")
				? fileName.substring(0, fileName.lastIndexOf('.'))
				: fileName;

			submissions.add(load(
				submissionFile,
				inputFilePath,
				expectedOutputFilePath,
				studentId,
				assignmentId
			));
		}

		return submissions;
	}
}
package autojudge.CoreEvaluation.model;

public record Assignment(
	String assignmentId,
	ResourceLimits resourceLimits,
	ExecutionProfile executionProfile
) {

	public record ResourceLimits(
		long timeLimitMs,
		int memoryLimitMb,
		double cpuLimit
	) {
	}

	public record ExecutionProfile(
		boolean autoRemove,
		String workingDirectory
	) {
	}
}
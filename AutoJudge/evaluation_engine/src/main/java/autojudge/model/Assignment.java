package autojudge.model;

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
		String language,
		boolean autoRemove,
		String workingDirectory
	) {
	}
}
package autojudge.CoreEvaluation.docker;

public class ProcessLimitCheck {
    int PROCESS_LIMIT = 30;

    public Boolean checkProcessLimit(int processCount) {
        return processCount > PROCESS_LIMIT;
    }
}

package me.asu.aidev.workflow.domain;

import java.util.Optional;
import java.util.regex.Pattern;
import me.asu.aidev.work.domain.TaskId;

/** Orders original rounds, explicitly authorized correction rounds, and repeated reviews. */
public record WorkflowTaskPosition(int step, int round, boolean review, int resume) {
    public long sequence() { return step * 2_000_000_000L + round * 2L + (review ? 1 : 0); }

    public static Optional<WorkflowTaskPosition> parse(WorkflowPlan plan, TaskId id) {
        for (int step = 0; step < plan.spec().steps().size(); step++) {
            String prefix = plan.id() + "~" + plan.spec().steps().get(step).key() + "~";
            var match = Pattern.compile(Pattern.quote(prefix)
                    + "(work|review)-([1-9][0-9]{0,8})(?:-resume-([1-9][0-9]{0,8}))?").matcher(id.value());
            if (match.matches()) {
                boolean review = match.group(1).equals("review");
                if (!review && match.group(3) != null) return Optional.empty();
                return Optional.of(new WorkflowTaskPosition(step, Integer.parseInt(match.group(2)), review,
                        match.group(3) == null ? 0 : Integer.parseInt(match.group(3))));
            }
        }
        return Optional.empty();
    }
}

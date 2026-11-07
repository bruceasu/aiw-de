package me.asu.aidev.workflow.domain;

import java.util.List;
import java.util.stream.IntStream;

/** Human-confirmed review boundaries. A Worker report cannot select a weaker policy. */
public final class WorkflowReviewPolicy {
    private WorkflowReviewPolicy() {}

    public static List<Integer> scope(WorkflowPlanSpec spec,int step) {
        var mode=spec.steps().get(step).effectiveReviewMode();
        int first=step;
        if(mode==WorkflowPlanSpec.ReviewMode.MILESTONE) {
            while(first>0&&spec.steps().get(first-1).effectiveReviewMode()==WorkflowPlanSpec.ReviewMode.SELF)first--;
        }
        return IntStream.rangeClosed(first,step).boxed().toList();
    }
}

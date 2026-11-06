package me.asu.aidev.goal.application;

import me.asu.aidev.workflow.application.PlanLocations;
import org.springframework.stereotype.Service;

/** Location allocation only; Workers decide how to prepare it. */
@Service
public class GoalWorkspace {
    private final PlanLocations locations;
    public GoalWorkspace(PlanLocations locations) { this.locations=locations; }
    public String ensure(String version) { return locations.allocate("goal:"+version,null); }
}

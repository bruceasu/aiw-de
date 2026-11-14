package me.asu.aidev.workflow.application;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.asu.aidev.execution.guard.GuardContract;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WorkflowWorkspaceSpecTest {
    private final GuardContract.Command command=new GuardContract.Command("check",List.of("git","status"),".","workspace",1000,4096,33554432,4);
    WorkflowRuns.Node node(boolean files){return new WorkflowRuns.Node("work","Work",WorkflowRuns.Kind.TASK,"Do the work","end",Map.of(),null,WorkflowRuns.Assignment.GENERAL,List.of(),files);}
    WorkflowRuns.Node end(){return new WorkflowRuns.Node("end","Done",WorkflowRuns.Kind.END,"",null,Map.of(),null,null,List.of());}
    @Test void persistedSpecificationRetainsExactCommandsAndExplicitFileMode()throws Exception{
        var mutable=new ArrayList<>(List.of(command));
        var spec=new WorkflowRuns.Spec("Work","Goal","work",List.of(node(true),end()),List.of(),mutable);mutable.clear();
        var json=new ObjectMapper();var decoded=json.readValue(json.writeValueAsString(spec),WorkflowRuns.Spec.class);
        assertThat(decoded.commands()).containsExactly(command);assertThat(decoded.nodes().getFirst().workspaceTools()).isTrue();
    }
    @Test void textOnlyWorkflowCannotAcquireCommandAuthorityThroughAnExtraField(){
        assertThatThrownBy(()->new WorkflowRuns.Spec("Work","Goal","work",List.of(node(false),end()),List.of(),List.of(command)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("explicit workspace task");
    }
}

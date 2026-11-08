package me.asu.aidev.goal.application;

import java.util.List;
import me.asu.aidev.ai.application.AiProfileCatalog;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DeepSeekDiscussionTest {
    @Test void openAiProtocolSelectionAlsoRejectsADifferentProtocolWorker() {
        var policy=new GoalTaskPolicy(null,null);
        var profile=new AiProfileCatalog.NamedProfile("openai",new AiProfileCatalog.Profile("openai","openai-agents","account-model","none",false,1));
        var frozen=new GoalTaskPolicy.Frozen("openai","factory-one",List.of(profile),"chat-completions");
        assertThat(policy.matches(frozen,"openai-agents:openai:chat-completions:goal-discussion")).isTrue();
        assertThat(policy.matches(frozen,"openai-agents:goal-discussion")).isFalse();
        var legacy=new GoalTaskPolicy.Frozen("openai","factory-old",List.of(profile));
        assertThat(policy.matches(legacy,"openai-agents:goal-discussion")).isTrue();
        assertThat(policy.matches(legacy,"openai-agents:openai:chat-completions:goal-discussion")).isFalse();
    }
    @Test void freezeReadsOneFactorySnapshotForModelProtocolAndVersion() {
        var factory=org.mockito.Mockito.mock(me.asu.aidev.execution.config.AgentFactorySettings.class);
        var settings=new me.asu.aidev.execution.config.AgentFactorySettings.Settings("openai-agents","account-model","high",true,"python","","",null,null,"deepseek","chat-completions");
        org.mockito.Mockito.when(factory.snapshot()).thenReturn(new me.asu.aidev.execution.config.AgentFactorySettings.Snapshot(settings,"factory-one"));
        var policy=new GoalTaskPolicy(null,null);
        org.springframework.test.util.ReflectionTestUtils.setField(policy,"factorySettings",factory);
        var frozen=policy.freeze("deepseek");
        assertThat(frozen.version()).isEqualTo("factory-one");assertThat(frozen.apiProtocol()).isEqualTo("chat-completions");
        assertThat(frozen.profiles().getFirst().profile().provider()).isEqualTo("deepseek");
        org.mockito.Mockito.verify(factory,org.mockito.Mockito.never()).current();
        org.mockito.Mockito.verify(factory,org.mockito.Mockito.never()).profiles();
        org.mockito.Mockito.verify(factory,org.mockito.Mockito.never()).version();
    }
    @Test void providerAndProtocolPreventWrongDiscussionWorkerFromClaiming() {
        var policy=new GoalTaskPolicy(null,null);
        var profile=new AiProfileCatalog.NamedProfile("deepseek",new AiProfileCatalog.Profile("deepseek","openai-agents","account-model","none",false,1));
        var frozen=new GoalTaskPolicy.Frozen("deepseek","version-a",List.of(profile),"responses");
        assertThat(policy.matches(frozen,"openai-agents:deepseek:responses:goal-discussion")).isTrue();
        assertThat(policy.matches(frozen,"openai-agents:goal-discussion")).isFalse();
        assertThat(policy.matches(frozen,"openai-agents:deepseek:chat-completions:goal-discussion")).isFalse();
        assertThat(policy.matches(frozen,"codex-app-server:goal-discussion")).isFalse();
        assertThat(GoalTaskPolicy.runtime("openai","openai-agents:deepseek:responses:goal-discussion")).isFalse();
    }
}

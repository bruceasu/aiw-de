package me.asu.aidev.execution.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeepSeekFactorySettingsTest {
    private AgentFactorySettings.Settings deepseek(String protocol,String effort,String credential) {
        return new AgentFactorySettings.Settings("openai-agents","account-model",effort,true,"python","","",credential,credential==null?null:1L,"deepseek",protocol);
    }
    @Test void existingSettingsRemainOpenAiResponses() {
        var old=new AgentFactorySettings.Settings("openai-agents","account-model","medium",true,"python","","");
        assertThat(old.apiProvider()).isEqualTo("openai");assertThat(old.apiProtocol()).isEqualTo("responses");
    }
    @Test void chatgptCredentialsCannotBeSentToDeepseek() {
        assertThatThrownBy(()->deepseek("responses","none","MC-00000000-0000-0000-0000-000000000000"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ChatGPT OAuth");
    }
    @Test void protocolChangesCreateDifferentWorkerIdentitiesAndCompatibilityKeys() {
        var responses=deepseek("responses","none",null);var chat=deepseek("chat-completions","none",null);
        assertThat(responses.workerRuntime()).isNotEqualTo(chat.workerRuntime());
        assertThat(ManagedWorkerProvisioner.compatibilityKey("goal-discussion",null,responses))
            .isNotEqualTo(ManagedWorkerProvisioner.compatibilityKey("goal-discussion",null,chat));
    }
    @Test void unsupportedThinkingSettingsAreRejectedInsteadOfSilentlyChanged() {
        assertThatCode(()->deepseek("responses","max",null)).doesNotThrowAnyException();
        assertThatCode(()->deepseek("chat-completions","high",null)).doesNotThrowAnyException();
        assertThatThrownBy(()->deepseek("responses","medium",null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void preparedDeepseekTaskRejectsOtherProviderAndProtocolBeforeDatabaseAccess() {
        var configurations=mock(ExecutionConfigurations.class,CALLS_REAL_METHODS);
        var worker=mock(me.asu.aidev.worker.domain.Worker.class);
        when(worker.capabilities()).thenReturn(java.util.Set.of(ConfigurationContract.CAPABILITY,
            me.asu.aidev.execution.guard.GuardContract.CAPABILITY,me.asu.aidev.execution.skills.SkillFileContract.CAPABILITY));
        var profile=new me.asu.aidev.ai.application.AiProfileCatalog.NamedProfile("deepseek",new me.asu.aidev.ai.application.AiProfileCatalog.Profile("deepseek","openai-agents","account-model","none",false,1));
        var guidance=new me.asu.aidev.ai.application.AiProfileCatalog.Guidance("v1",java.util.List.of(profile),null,"deepseek");
        var binding=new ConfigurationContract.Binding("owner",null,"openai-agents",guidance,"responses");
        when(worker.runtimeType()).thenReturn("openai-agents:coder");
        assertThat(configurations.compatible(worker,binding)).isFalse();
        when(worker.runtimeType()).thenReturn("openai-agents:deepseek:chat-completions:coder");
        assertThat(configurations.compatible(worker,binding)).isFalse();
    }
}

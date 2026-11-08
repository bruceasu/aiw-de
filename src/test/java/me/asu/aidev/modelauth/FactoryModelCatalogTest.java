package me.asu.aidev.modelauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FactoryModelCatalogTest {
    private final ObjectMapper json=new ObjectMapper();
    @Test void deepseekDirectoryUsesOnlyDeepseekKeyAndOfficialEndpoint() throws Exception {
        var connections=mock(ModelConnections.class);when(connections.actor()).thenReturn("web:admin");
        var oauth=mock(OpenAiOAuth.class);var settings=mock(me.asu.aidev.execution.config.AgentFactorySettings.class);
        when(settings.snapshot()).thenReturn(new me.asu.aidev.execution.config.AgentFactorySettings.Snapshot(null,"v1"));when(settings.version()).thenReturn("v1");
        var catalog=spy(new FactoryModelCatalog(connections,oauth,settings,null));
        doReturn("deepseek-test-key").when(catalog).environment("DEEPSEEK_API_KEY");
        doReturn("https://another-provider.invalid").when(catalog).environment("OPENAI_BASE_URL");
        when(oauth.models("deepseek-test-key","https://api.deepseek.com")).thenReturn(json.readTree("{\"data\":[{\"id\":\"account-model\"}]}"));
        var result=catalog.models(new FactoryModelCatalog.Query("openai-agents","api-key",null,null,false,"deepseek"));
        assertThat(result.source()).isEqualTo("DEEPSEEK_API_ACCOUNT");
        assertThat(result.models()).extracting(FactoryModelCatalog.Model::id).containsExactly("account-model");
        verify(catalog,never()).environment("OPENAI_API_KEY");verify(oauth).models("deepseek-test-key","https://api.deepseek.com");
    }
    @Test void missingDeepseekKeyCannotFallBackToOpenAi() {
        var connections=mock(ModelConnections.class);when(connections.actor()).thenReturn("web:admin");
        var oauth=mock(OpenAiOAuth.class);var settings=mock(me.asu.aidev.execution.config.AgentFactorySettings.class);
        when(settings.snapshot()).thenReturn(new me.asu.aidev.execution.config.AgentFactorySettings.Snapshot(null,"v1"));
        var catalog=spy(new FactoryModelCatalog(connections,oauth,settings,null));
        doReturn("").when(catalog).environment("DEEPSEEK_API_KEY");doReturn("other-key").when(catalog).environment("OPENAI_API_KEY");
        assertThatThrownBy(()->catalog.models(new FactoryModelCatalog.Query("openai-agents","api-key",null,null,false,"deepseek")))
            .hasMessage("MODEL_DIRECTORY_DEEPSEEK_KEY_NOT_CONFIGURED");
        verifyNoInteractions(oauth);
    }
    @Test void chatgptDirectoryPreservesAccountSlugsOrderAndVisibility() throws Exception {
        var rows=json.readTree("""
                {"models":[{"slug":"model-b","display_name":"Second","visibility":"list"},
                  {"slug":"hidden-model","display_name":"Hidden","visibility":"hidden"},
                  {"slug":"model-a","display_name":"First","visibility":"list"}]}
                """);
        var models=FactoryModelCatalog.parse(rows,"CHATGPT_ACCOUNT");
        assertThat(models).extracting(FactoryModelCatalog.Model::id).containsExactly("model-b","model-a");
        assertThat(models.getFirst().name()).isEqualTo("Second");assertThat(models.getFirst().efforts()).isEmpty();
    }
    @Test void codexDirectoryUsesReturnedEffortsAndFiltersHiddenOrUnsupportedIds() throws Exception {
        var rows=json.readTree("""
                {"data":[{"model":"model-a","displayName":"Coding","defaultReasoningEffort":"high",
                  "supportedReasoningEfforts":[{"reasoningEffort":"low"},{"reasoningEffort":"high"},{"reasoningEffort":"invented"}]},
                  {"model":"hidden","hidden":true},{"model":"unsupported:model"}]}
                """);
        var models=FactoryModelCatalog.parse(rows,"CODEX_RPC");
        assertThat(models).hasSize(1);assertThat(models.getFirst().efforts()).containsExactly("low","high");assertThat(models.getFirst().defaultEffort()).isEqualTo("high");
    }
    @Test void apiKeyDirectoryDoesNotPretendToKnowResponsesOrEffortCapabilities() throws Exception {
        var models=FactoryModelCatalog.parse(json.readTree("{\"data\":[{\"id\":\"model-a\"},{\"id\":\"embedding-model\"}]}"),"OPENAI_API_ACCOUNT");
        assertThat(models).extracting(FactoryModelCatalog.Model::id).containsExactly("model-a","embedding-model");
        assertThat(models).allMatch(model->model.efforts().isEmpty()&&model.defaultEffort()==null);
    }
    @Test void mismatchedOrMissingCredentialModesCannotFallBackToAnotherAccount() {
        for(var query:List.of(new FactoryModelCatalog.Query("codex-app-server","chatgpt","MC-test",1L,false),
                new FactoryModelCatalog.Query("openai-agents","chatgpt",null,null,false),
                new FactoryModelCatalog.Query("openai-agents","api-key","MC-test",1L,false)))
            assertThatThrownBy(()->FactoryModelCatalog.validate(query)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(()->FactoryModelCatalog.validate(new FactoryModelCatalog.Query("openai-agents","api-key",null,null,false))).doesNotThrowAnyException();
    }
    @Test void unrecognizedDirectoryCannotBecomeAnInventedModelList() throws Exception {
        var root=json.readTree("{\"unexpected\":[]}");
        assertThatThrownBy(()->FactoryModelCatalog.parse(root,"CHATGPT_ACCOUNT")).hasMessage("MODEL_DIRECTORY_FORMAT_UNSUPPORTED");
    }
    @Test void catalogReadRequiresAHumanBeforeAnyCredentialOrNetworkAccess() {
        var connections=mock(ModelConnections.class);var oauth=mock(OpenAiOAuth.class);
        when(connections.actor()).thenThrow(new SecurityException());
        var catalog=new FactoryModelCatalog(connections,oauth,null,null);
        assertThatThrownBy(()->catalog.models(new FactoryModelCatalog.Query("openai-agents","api-key",null,null,false))).isInstanceOf(SecurityException.class);
        verifyNoInteractions(oauth);
    }
    @Test void lateDirectoryFromAnOldSavedEnvironmentIsNotCachedAsTheNewSource() throws Exception {
        var connections=mock(ModelConnections.class);when(connections.actor()).thenReturn("web:admin");var oauth=mock(OpenAiOAuth.class);
        var settings=mock(me.asu.aidev.execution.config.AgentFactorySettings.class);
        var config=new me.asu.aidev.execution.config.AgentFactorySettings.Settings("openai-agents","model-a","medium",true,"python","","");
        when(settings.snapshot()).thenReturn(new me.asu.aidev.execution.config.AgentFactorySettings.Snapshot(config,"source-a"));when(settings.version()).thenReturn("source-b");
        var catalog=spy(new FactoryModelCatalog(connections,oauth,settings,null));doReturn("test-api-key").when(catalog).environment("OPENAI_API_KEY");
        when(oauth.models(anyString(),anyString())).thenReturn(json.readTree("{\"data\":[{\"id\":\"old-source-model\"}]}"));
        var query=new FactoryModelCatalog.Query("openai-agents","api-key",null,null,false);
        assertThatThrownBy(()->catalog.models(query)).hasMessage("MODEL_DIRECTORY_SOURCE_CHANGED");
        when(settings.snapshot()).thenReturn(new me.asu.aidev.execution.config.AgentFactorySettings.Snapshot(config,"source-b"));
        when(oauth.models(anyString(),anyString())).thenReturn(json.readTree("{\"data\":[{\"id\":\"new-source-model\"}]}"));
        assertThat(catalog.models(query).models()).extracting(FactoryModelCatalog.Model::id).containsExactly("new-source-model");
        verify(oauth,times(2)).models(anyString(),anyString());
    }
}

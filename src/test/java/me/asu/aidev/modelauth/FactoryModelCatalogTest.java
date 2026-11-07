package me.asu.aidev.modelauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FactoryModelCatalogTest {
    private final ObjectMapper json=new ObjectMapper();
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
}

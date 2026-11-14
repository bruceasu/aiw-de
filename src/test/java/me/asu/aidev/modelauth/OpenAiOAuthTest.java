package me.asu.aidev.modelauth;

import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static me.asu.aidev.modelauth.ModelAuthContract.*;

class OpenAiOAuthTest {
    @Test void protocolBridgeReturnsReferencesAndSendsRefreshIdentityWithoutTokens(){
        var json=new ObjectMapper();var calls=new ArrayList<Map<String,Object>>();
        var oauth=new OpenAiOAuth(null){
            @Override JsonNode call(Map<String,Object> input){
                calls.add(input);
                return json.valueToTree(new Issued("test.json","a".repeat(64),Kind.CHATGPT_RESPONSES,"client","subject",null,null,1234,0,true));
            }
        };
        var result=oauth.refresh(Kind.CHATGPT_RESPONSES,"client","subject","original.json");
        assertThat(result.credentialFile()).isEqualTo("test.json");
        assertThat(calls.getFirst()).containsEntry("credentialFile","original.json").containsEntry("subject","subject");
        assertThat(calls.getFirst()).doesNotContainKeys("access_token","refresh_token","id_token");
        assertThat(result.toString()).doesNotContain("test.json");
    }
    @Test void revocationFailureDoesNotClaimRemoteSuccess(){
        var oauth=new OpenAiOAuth(null){@Override JsonNode call(Map<String,Object> input){throw new IllegalStateException("MODEL_AUTH_PROTOCOL_FAILED");}};
        assertThat(oauth.revoke("test.json")).isFalse();
    }
}

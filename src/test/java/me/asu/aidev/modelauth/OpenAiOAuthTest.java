package me.asu.aidev.modelauth;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static me.asu.aidev.modelauth.ModelAuthContract.*;

class OpenAiOAuthTest {
    RSAKey key;OpenAiOAuth oauth;
    @BeforeEach void setup() throws Exception {
        key=new RSAKeyGenerator(2048).keyID("test-key").generate();oauth=new OpenAiOAuth();
        ReflectionTestUtils.setField(oauth,"keys",new JWKSet(key.toPublicJWK()));ReflectionTestUtils.setField(oauth,"keysUntil",System.currentTimeMillis()+60000);
    }
    String signed(String audience,String client,String nonce,String subject) throws Exception {
        var claims=new JWTClaimsSet.Builder().issuer(OpenAiOAuth.ISSUER).audience(audience).subject(subject).issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis()+3600000)).claim("client_id",client).claim("scope",OpenAiOAuth.SCOPES);
        if(nonce!=null)claims.claim("nonce",nonce);
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(),claims.build());jwt.sign(new RSASSASigner(key));return jwt.serialize();
    }
    com.fasterxml.jackson.databind.node.ObjectNode response(String nonce) throws Exception {
        var json=new ObjectMapper().createObjectNode();json.put("access_token",signed(OpenAiOAuth.RESOURCE,"oaiapp_test",null,"subject"));
        json.put("id_token",signed("oaiapp_test","oaiapp_test",nonce,"subject"));json.put("refresh_token","renewable-test");json.put("scope",OpenAiOAuth.SCOPES);json.put("token_type","Bearer");json.put("expires_in",3600);return json;
    }
    @Test void validatesActualRsaSignatureNonceClientAndIdentityWithoutNetwork() throws Exception {
        var issued=oauth.validate(response("nonce"),Kind.CHATGPT_RESPONSES,"oaiapp_test","nonce",null);
        assertThat(issued.inferenceAllowed()).isTrue();assertThat(issued.subject()).isEqualTo("subject");
    }
    @Test void changedNonceClientAndSubjectCannotReplaceSelectedAccount() throws Exception {
        var data=response("nonce");
        assertThatThrownBy(()->oauth.validate(data,Kind.CHATGPT_RESPONSES,"oaiapp_test","wrong",null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->oauth.validate(data,Kind.CHATGPT_RESPONSES,"another","nonce",null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->oauth.validate(data,Kind.CHATGPT_RESPONSES,"oaiapp_test","nonce","another")).isInstanceOf(SecurityException.class);
    }
    @Test void signedIdentityWithoutInferenceScopeRemainsIdentityOnly() throws Exception {
        var data=response("nonce");data.put("scope","openid profile email");
        assertThat(oauth.validate(data,Kind.CHATGPT_RESPONSES,"oaiapp_test","nonce",null).inferenceAllowed()).isFalse();
    }
    @Test void anUntrustedSigningKeyAndCodexMisclassificationAreRejected() throws Exception {
        var data=response("nonce");var other=new RSAKeyGenerator(2048).keyID("test-key").generate();
        ReflectionTestUtils.setField(oauth,"keys",new JWKSet(other.toPublicJWK()));
        assertThatThrownBy(()->oauth.validate(data,Kind.CHATGPT_RESPONSES,"oaiapp_test","nonce",null)).isInstanceOf(SecurityException.class);
        ReflectionTestUtils.setField(oauth,"keys",new JWKSet(key.toPublicJWK()));
        assertThatThrownBy(()->oauth.validate(data,Kind.CODEX_APP_SERVER,"oaiapp_test","nonce",null)).isInstanceOf(SecurityException.class);
    }
}

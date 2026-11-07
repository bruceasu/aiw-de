package me.asu.aidev.modelauth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The deployment supplies a 256-bit key outside the database and task environment. */
@Component
public class CredentialVault {
    private final byte[] key;
    public CredentialVault(@Value("${aidev.model-auth.key:}") String configured) {
        byte[] parsed=null;
        if(configured!=null&&!configured.isBlank())try {parsed=Base64.getDecoder().decode(configured.strip());}catch(IllegalArgumentException ignored) { /* Fail closed without logging the key. */ }
        key=parsed!=null&&parsed.length==32?parsed:null;
    }
    public boolean ready() {return key!=null;}
    public String seal(String aad,String plain) {return crypt(true,aad,plain);}
    public String open(String aad,String encoded) {return crypt(false,aad,encoded);}
    private String crypt(boolean encrypt,String aad,String value) {
        if(!ready())throw new IllegalStateException("MODEL_CREDENTIAL_VAULT_NOT_CONFIGURED");
        try {
            byte[] nonce,input;
            if(encrypt) {nonce=new byte[12];new SecureRandom().nextBytes(nonce);input=value.getBytes(StandardCharsets.UTF_8);}
            else {
                var bytes=Base64.getDecoder().decode(value);
                if(bytes.length<28||bytes.length>131072)throw new IllegalArgumentException();
                nonce=Arrays.copyOf(bytes,12);input=Arrays.copyOfRange(bytes,12,bytes.length);
            }
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(encrypt?Cipher.ENCRYPT_MODE:Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));var output=cipher.doFinal(input);
            if(!encrypt)return new String(output,StandardCharsets.UTF_8);
            var result=Arrays.copyOf(nonce,nonce.length+output.length);System.arraycopy(output,0,result,nonce.length,output.length);
            return Base64.getEncoder().encodeToString(result);
        } catch(Exception error) {throw new IllegalStateException("MODEL_CREDENTIAL_VAULT_UNAVAILABLE");}
    }
}

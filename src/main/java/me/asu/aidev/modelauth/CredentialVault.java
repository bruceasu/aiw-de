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
import me.asu.aidev.authority.AuthorityRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit deployment keys or a database-scoped, CurrentUser DPAPI-protected local key. */
@Component
public class CredentialVault {
    private final byte[] key;
    private final boolean explicit;
    private final AuthorityRepository repo;
    private final TransactionTemplate transaction;
    private final LocalModelKeyStore local;
    public record Status(boolean ready,String mode,String state,String revision) {}
    @org.springframework.beans.factory.annotation.Autowired
    public CredentialVault(@Value("${aidev.model-auth.key:}") String configured,AuthorityRepository repo,PlatformTransactionManager manager) {
        this(configured,repo,manager,new LocalModelKeyStore());
    }
    /** Isolated explicit-key seam; never touches a real OS key store. */
    public CredentialVault(String configured) {this(configured,null,null,null);}
    CredentialVault(String configured,AuthorityRepository repo,PlatformTransactionManager manager,LocalModelKeyStore local) {
        byte[] parsed=null;
        if(configured!=null&&!configured.isBlank())try {parsed=Base64.getDecoder().decode(configured.strip());}catch(IllegalArgumentException ignored) { /* Fail closed without logging the key. */ }
        key=parsed!=null&&parsed.length==32?parsed:null;
        explicit=configured!=null&&!configured.isBlank();this.repo=repo;this.local=local;
        transaction=manager==null?null:new TransactionTemplate(manager);
    }
    public boolean ready() {return status().ready();}
    public Status status() {
        String mode=explicit?"CONFIGURED":"WINDOWS_DPAPI";
        if(explicit&&key==null)return new Status(false,mode,"MODEL_CREDENTIAL_KEY_INVALID","");
        if(explicit&&transaction==null)return new Status(true,mode,"READY","");
        if(!explicit&&(local==null||!local.supported()))return new Status(false,"UNAVAILABLE","MODEL_LOCAL_KEY_PLATFORM_UNSUPPORTED","");
        try {return transaction.execute(ignored->{
            var metadata=metadata();String id=(String)metadata.get("key_id");
            try {byte[] material=explicit?configuredMaterial(metadata):material(metadata);Arrays.fill(material,(byte)0);return new Status(true,mode,"READY",id);}
            catch(IllegalStateException error) {return new Status(false,mode,error.getMessage(),id);}
        });}catch(org.springframework.dao.DataAccessException error) {return new Status(false,mode,"MODEL_LOCAL_KEY_UNAVAILABLE","");}
    }
    private java.util.Map<String,Object> metadata() {
        repo.jdbc().update("INSERT INTO model_vault_local(id,key_id,key_digest) VALUES(1,?,NULL) ON CONFLICT(id) DO NOTHING",java.util.UUID.randomUUID().toString());
        return repo.jdbc().queryForMap("SELECT * FROM model_vault_local WHERE id=1");
    }
    private byte[] material(java.util.Map<String,Object> metadata) {
        String id=(String)metadata.get("key_id"),digest=(String)metadata.get("key_digest");
        if(digest==null&&repo.jdbc().queryForObject("SELECT COUNT(*) FROM model_connection WHERE secret_cipher IS NOT NULL",Long.class)>0)
            throw new IllegalStateException("MODEL_LOCAL_KEY_MISSING");
        byte[] material=local.load(id,digest==null);
        String actual=AuthorityRepository.hash(Base64.getEncoder().encodeToString(material));
        if(digest==null) {
            repo.jdbc().update("UPDATE model_vault_local SET key_digest=? WHERE id=1 AND key_id=? AND key_digest IS NULL",actual,id);
            digest=actual;
        }
        // A write fence also holds the epoch through an enclosing credential save transaction.
        if(!actual.equals(digest)||repo.jdbc().update("UPDATE model_vault_local SET key_id=key_id WHERE id=1 AND key_id=? AND key_digest=?",id,actual)!=1) {
            Arrays.fill(material,(byte)0);throw new IllegalStateException("MODEL_LOCAL_KEY_CHANGED");
        }
        return material;
    }
    private byte[] configuredMaterial(java.util.Map<String,Object> metadata) {
        String id=(String)metadata.get("key_id"),digest=(String)metadata.get("key_digest");
        String actual=AuthorityRepository.hash(Base64.getEncoder().encodeToString(key));
        if(digest==null) {
            // An upgrade may contain pre-metadata ciphertext. Verify the supplied key before recording its identity.
            for(var row:repo.jdbc().queryForList("SELECT id,owner_actor,client_id,generation,secret_cipher FROM model_connection WHERE secret_cipher IS NOT NULL"))
                cryptWith(key,false,String.join("\n","aiw.model-auth.v1",(String)row.get("id"),(String)row.get("owner_actor"),(String)row.get("client_id"),Long.toString(((Number)row.get("generation")).longValue())),(String)row.get("secret_cipher"));
            repo.jdbc().update("UPDATE model_vault_local SET key_digest=? WHERE id=1 AND key_id=? AND key_digest IS NULL",actual,id);
            digest=actual;
        }
        if(!actual.equals(digest)||repo.jdbc().update("UPDATE model_vault_local SET key_id=key_id WHERE id=1 AND key_id=? AND key_digest=?",id,actual)!=1)
            throw new IllegalStateException("MODEL_LOCAL_KEY_CHANGED");
        return key.clone();
    }
    /** Caller owns the database transaction, authority checks and clearing of affected credentials. */
    String rotateLocal(String expected) {
        if(explicit||local==null||!local.supported())throw new IllegalStateException("MODEL_LOCAL_KEY_RESET_UNAVAILABLE");
        String next=java.util.UUID.randomUUID().toString();
        if(expected==null||repo.jdbc().update("UPDATE model_vault_local SET key_id=?,key_digest=NULL WHERE id=1 AND key_id=?",next,expected)!=1)
            throw new IllegalStateException("MODEL_LOCAL_KEY_CHANGED");
        return next;
    }
    boolean removeLocal(String prior) {return local!=null&&local.remove(prior);}
    void requireRevision(String revision) {
        if(transaction==null)return;
        if(revision==null||repo.jdbc().update("UPDATE model_vault_local SET key_id=key_id WHERE id=1 AND key_id=?",revision)!=1)
            throw new IllegalStateException("MODEL_LOCAL_KEY_CHANGED");
    }
    public String seal(String aad,String plain) {return crypt(true,aad,plain);}
    public String open(String aad,String encoded) {return crypt(false,aad,encoded);}
    private String crypt(boolean encrypt,String aad,String value) {
        if(transaction==null) {
            if(key==null)throw new IllegalStateException("MODEL_CREDENTIAL_VAULT_NOT_CONFIGURED");
            return cryptWith(key,encrypt,aad,value);
        }
        return transaction.execute(ignored->{
            if(explicit&&key==null)throw new IllegalStateException("MODEL_CREDENTIAL_VAULT_NOT_CONFIGURED");
            byte[] material=explicit?configuredMaterial(metadata()):material(metadata());
            try {return cryptWith(material,encrypt,aad,value);}finally {Arrays.fill(material,(byte)0);}
        });
    }
    private String cryptWith(byte[] material,boolean encrypt,String aad,String value) {
        try {
            byte[] nonce,input;
            if(encrypt) {nonce=new byte[12];new SecureRandom().nextBytes(nonce);input=value.getBytes(StandardCharsets.UTF_8);}
            else {
                var bytes=Base64.getDecoder().decode(value);
                if(bytes.length<28||bytes.length>131072)throw new IllegalArgumentException();
                nonce=Arrays.copyOf(bytes,12);input=Arrays.copyOfRange(bytes,12,bytes.length);
            }
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(encrypt?Cipher.ENCRYPT_MODE:Cipher.DECRYPT_MODE,new SecretKeySpec(material,"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));var output=cipher.doFinal(input);
            if(!encrypt)return new String(output,StandardCharsets.UTF_8);
            var result=Arrays.copyOf(nonce,nonce.length+output.length);System.arraycopy(output,0,result,nonce.length,output.length);
            return Base64.getEncoder().encodeToString(result);
        } catch(Exception error) {throw new IllegalStateException("MODEL_CREDENTIAL_VAULT_UNAVAILABLE");}
    }
}

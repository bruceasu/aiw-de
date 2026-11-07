package me.asu.aidev.modelauth;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

/** Windows CurrentUser DPAPI; disk/argv/environment never receive the plaintext AES key. */
class LocalModelKeyStore {
    interface Protector { byte[] protect(byte[] plain); byte[] unprotect(byte[] protectedBytes); }
    private final Path directory;
    private final Protector protector;
    private String cachedId;
    private byte[] cachedBlob,cachedKey;
    LocalModelKeyStore() {
        String appData=System.getenv("LOCALAPPDATA");
        boolean windows=System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows");
        directory=windows&&appData!=null&&!appData.isBlank()?Path.of(appData).resolve("AIW-DE/model-keys").toAbsolutePath().normalize():null;
        protector=new WindowsProtector();
    }
    LocalModelKeyStore(Path directory,Protector protector) {this.directory=directory;this.protector=protector;}
    boolean supported() {return directory!=null;}
    private Path file(String id,String suffix) {
        if(!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException();
        return directory.resolve(id+suffix);
    }
    synchronized byte[] load(String id,boolean create) {
        if(!supported())throw new IllegalStateException("MODEL_LOCAL_KEY_PLATFORM_UNSUPPORTED");
        try {
            Files.createDirectories(directory);
            if(Files.isSymbolicLink(directory))throw new IOException();
            Path target=file(id,".dpapi");
            // No database access under this lock; database transactions always precede file operations.
            try(var channel=FileChannel.open(file(id,".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.lock()) {
                if(Files.isSymbolicLink(target))throw new IOException();
                if(!Files.exists(target)) {
                    if(!create)throw new IllegalStateException("MODEL_LOCAL_KEY_MISSING");
                    byte[] key=new byte[32];new SecureRandom().nextBytes(key);
                    byte[] protectedBytes;
                    try {protectedBytes=protector.protect(key);}finally {Arrays.fill(key,(byte)0);}
                    Path temporary=Files.createTempFile(directory,id+"-",".pending");
                    try {
                        Files.write(temporary,protectedBytes,StandardOpenOption.TRUNCATE_EXISTING);
                        try(var output=FileChannel.open(temporary,StandardOpenOption.WRITE)) {output.force(true);}
                        Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);
                    }finally {Files.deleteIfExists(temporary);}
                }
                try(var input=Files.newInputStream(target)) {
                    byte[] encrypted=input.readNBytes(16385);
                    if(encrypted.length==0||encrypted.length>16384)throw new IOException();
                    if(id.equals(cachedId)&&Arrays.equals(encrypted,cachedBlob))return cachedKey.clone();
                    byte[] key=protector.unprotect(encrypted);
                    if(key.length!=32) {Arrays.fill(key,(byte)0);throw new IOException();}
                    if(cachedKey!=null)Arrays.fill(cachedKey,(byte)0);
                    cachedId=id;cachedBlob=encrypted;cachedKey=key.clone();
                    return key;
                }
            }
        }catch(IllegalStateException error) {throw error;}
        catch(Exception error) {throw new IllegalStateException("MODEL_LOCAL_KEY_UNAVAILABLE");}
    }
    synchronized boolean remove(String id) {
        if(!supported())return false;
        if(id.equals(cachedId)) {Arrays.fill(cachedKey,(byte)0);cachedKey=null;cachedBlob=null;cachedId=null;}
        try {return Files.deleteIfExists(file(id,".dpapi"))||!Files.exists(file(id,".dpapi"));}
        catch(Exception error) {return false;}
    }
    private static final class WindowsProtector implements Protector {
        public byte[] protect(byte[] value) {return invoke(value,true);}
        public byte[] unprotect(byte[] value) {return invoke(value,false);}
        private byte[] invoke(byte[] value,boolean protect) {
            String system=System.getenv("SystemRoot");
            if(system==null||system.isBlank())throw new IllegalStateException("MODEL_LOCAL_KEY_UNAVAILABLE");
            String script="$ErrorActionPreference='Stop'; Add-Type -AssemblyName System.Security; "
                    +"$b=[Convert]::FromBase64String([Console]::In.ReadToEnd()); "
                    +"$entropy=[Text.Encoding]::UTF8.GetBytes('aiw.model-vault.v1'); "
                    +"$r=[Security.Cryptography.ProtectedData]::"+(protect?"Protect":"Unprotect")
                    +"($b,$entropy,[Security.Cryptography.DataProtectionScope]::CurrentUser); "
                    +"[Console]::Out.Write([Convert]::ToBase64String($r))";
            var builder=new ProcessBuilder(Path.of(system,"System32","WindowsPowerShell","v1.0","powershell.exe").toString(),
                    "-NoLogo","-NoProfile","-NonInteractive","-WindowStyle","Hidden","-Command",script).redirectError(ProcessBuilder.Redirect.DISCARD);
            builder.environment().clear();
            for(String name:List.of("SystemRoot","WINDIR","TEMP","TMP","USERPROFILE","LOCALAPPDATA"))
                if(System.getenv(name)!=null)builder.environment().put(name,System.getenv(name));
            Process process=null;
            try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
                process=builder.start();Process child=process;
                try {
                var output=executor.submit(()->child.getInputStream().readNBytes(24577));
                try(var input=process.getOutputStream()) {input.write(Base64.getEncoder().encode(value));}
                if(!process.waitFor(15,TimeUnit.SECONDS)) {process.destroyForcibly();throw new IOException();}
                byte[] encoded=output.get(1,TimeUnit.SECONDS);
                if(process.exitValue()!=0||encoded.length>24576)throw new IOException();
                return Base64.getDecoder().decode(new String(encoded,StandardCharsets.US_ASCII));
                }finally {if(child.isAlive())child.destroyForcibly();}
            }catch(InterruptedException error) {Thread.currentThread().interrupt();throw new IllegalStateException("MODEL_LOCAL_KEY_UNAVAILABLE");}
            catch(Exception error) {throw new IllegalStateException("MODEL_LOCAL_KEY_UNAVAILABLE");}
            finally {if(process!=null&&process.isAlive())process.destroyForcibly();}
        }
    }
}

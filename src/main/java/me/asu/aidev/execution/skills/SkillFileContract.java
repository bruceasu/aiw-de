package me.asu.aidev.execution.skills;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Data-only, portable file sets. Text asset hashes retain their original meaning. */
public final class SkillFileContract {
    public static final String PROTOCOL="aiw.skill.files.v1", CAPABILITY="skill-files-v1";
    private SkillFileContract() {}
    public record File(String path,long size,String sha256,String base64) {
        public File {
            SkillFileContract.path(path);
            if(size<0 || size>1_048_576 || sha256==null || !sha256.matches("[0-9a-f]{64}") || base64==null || base64.length()>1_398_104)
                throw new IllegalArgumentException("Invalid Skill file bounds");
            byte[] bytes=Base64.getDecoder().decode(base64);
            if(bytes.length!=size || !hash(bytes).equals(sha256) || !Base64.getEncoder().encodeToString(bytes).equals(base64))
                throw new IllegalArgumentException("Skill file digest/encoding mismatch");
        }
    }
    public record Document(String protocol,List<File> files) {
        public Document { if(!PROTOCOL.equals(protocol)) throw new IllegalArgumentException("Unsupported Skill files protocol");files=validate(files); }
    }
    public record Ref(String ref,String version,String source,String ownerId,String repositoryRef,String assetVersion,String assetDigest,String manifestDigest) {}
    public record Bundle(String protocol,String ref,String version,String source,String ownerId,String repositoryRef,
                         String assetVersion,String assetDigest,String manifestDigest,List<File> files) {
        public Bundle {
            if(!PROTOCOL.equals(protocol) || ref==null || !ref.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,95}")) throw new IllegalArgumentException("Invalid Skill identity");
            files=validate(files);
            if(!manifest(files).equals(manifestDigest) || !Objects.equals(version,"files-"+manifestDigest)) throw new IllegalArgumentException("Skill manifest mismatch");
            if(!Set.of("platform-builtin","platform-asset").contains(source) || ownerId==null || repositoryRef==null
                    || source.equals("platform-builtin") && (!ownerId.isEmpty() || !repositoryRef.isEmpty())
                    || source.equals("platform-asset") && (ownerId.isBlank() || repositoryRef.isBlank())
                    || assetVersion==null || assetVersion.length()>64 || assetDigest==null || !assetDigest.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid Skill source scope");
        }
        public Ref reference() { return new Ref(ref,version,source,ownerId,repositoryRef,assetVersion,assetDigest,manifestDigest); }
    }
    public record Payload(String protocol,String taskId,String attemptId,List<Ref> references,List<Bundle> packages) {}
    public static Bundle bundle(String ref,String source,String owner,String repository,String assetVersion,String assetDigest,List<File> files) {
        files=validate(files);String digest=manifest(files);
        return new Bundle(PROTOCOL,ref,"files-"+digest,source,owner,repository,assetVersion,assetDigest,digest,files);
    }
    public static File file(String path,byte[] bytes) { return new File(path,bytes.length,hash(bytes),Base64.getEncoder().encodeToString(bytes)); }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static String manifest(List<File> files) {
        var b=new StringBuilder();for(var f:files) b.append(f.path()).append('\t').append(f.size()).append('\t').append(f.sha256()).append('\n');
        return hash(b.toString().getBytes(StandardCharsets.US_ASCII));
    }
    private static List<File> validate(List<File> files) {
        if(files==null || files.isEmpty() || files.size()>128) throw new IllegalArgumentException("Bounded Skill files required");
        var seen=new HashSet<String>();String previous="";long bytes=0;
        for(var f:files) {
            if(f==null || f.path().compareTo(previous)<=0 || !seen.add(f.path().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Sorted unique Skill paths required");
            previous=f.path();bytes+=f.size();
        }
        if(bytes>4_194_304 || !seen.contains("skill.md")) throw new IllegalArgumentException("Skill entry/budget invalid");
        // File/directory aliases must not differ by case or prefix, on any supported OS.
        var prefixes=new HashMap<String,String>();
        for(var f:files) {
            String[] parts=f.path().split("/");String prefix="";
            for(int i=0;i<parts.length;i++) {
                prefix=prefix.isEmpty()?parts[i]:prefix+"/"+parts[i];String key=prefix.toLowerCase(Locale.ROOT);
                String old=prefixes.putIfAbsent(key,prefix);
                if(old!=null && !old.equals(prefix) || i<parts.length-1 && seen.contains(key)) throw new IllegalArgumentException("Skill path alias");
            }
        }
        if(files.stream().noneMatch(f->f.path().equals("SKILL.md"))) throw new IllegalArgumentException("Exact SKILL.md required");
        return List.copyOf(files);
    }
    public static void path(String value) {
        if(value==null || value.length()>240 || !value.matches("[A-Za-z0-9._/-]+")) throw new IllegalArgumentException("Unsafe Skill path");
        for(String p:value.split("/",-1))
            if(p.isEmpty() || p.equals(".") || p.equals("..") || p.endsWith(".") || p.matches("(?i)(con|prn|aux|nul|com[0-9]|lpt[0-9])(?:\\..*)?") || p.equalsIgnoreCase(".git"))
                throw new IllegalArgumentException("Unsafe Skill segment");
    }
}

package me.asu.aidev.execution.tools;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import me.asu.aidev.authority.AuthorityBoundary;
import me.asu.aidev.authority.AuthorityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SharedToolCatalog {
    public enum Kind { MCP, HTTP }
    public enum State { ALL, ENABLED, DISABLED }
    public record Edit(String id,long expectedRevision,Kind kind,String title,String description,
                       String endpoint,String operation,String httpMethod,Map<String,Object> inputSchema,boolean enabled) {}
    public record Item(String id,long revision,Kind kind,String title,String description,String operation,
                       String httpMethod,Map<String,Object> inputSchema,boolean enabled) {}
    public record Entry(Item tool,String endpoint) {}
    public record Page(List<Item> items,String next) {}

    private static final Set<String> METHODS=Set.of("GET","HEAD","POST","PUT","PATCH","DELETE");
    private final AuthorityRepository db;
    private final AuthorityBoundary boundary;

    public SharedToolCatalog(AuthorityRepository db,AuthorityBoundary boundary) {
        this.db=db;this.boundary=boundary;
    }

    public Page search(String query,Kind kind,State state,String after,int limit) {
        if(limit<1||limit>100)throw new IllegalArgumentException("每页必须包含 1 到 100 项。");
        String term=query==null?"":query.strip().toLowerCase(Locale.ROOT);
        if(term.length()>120||term.indexOf(0)>=0)throw new IllegalArgumentException("检索词不能超过 120 个字符。");
        String pattern="%"+term.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        String start=after==null||after.isEmpty()?"":id(after);
        String type=kind==null?"":kind.name();
        String availability=state==null?State.ALL.name():state.name();
        var rows=db.jdbc().query("""
                SELECT id,revision,kind,title,description,operation,http_method,input_schema,enabled
                  FROM shared_tool
                 WHERE id>? AND (?='' OR kind=?)
                   AND (?='ALL' OR enabled=(?='ENABLED'))
                   AND (lower(id) LIKE ? ESCAPE '!' OR lower(title) LIKE ? ESCAPE '!'
                        OR lower(description) LIKE ? ESCAPE '!' OR lower(operation) LIKE ? ESCAPE '!')
                 ORDER BY id LIMIT ?
                """,this::item,start,type,type,availability,availability,pattern,pattern,pattern,pattern,limit+1);
        return rows.size()<=limit?new Page(rows,null):new Page(List.copyOf(rows.subList(0,limit)),rows.get(limit-1).id());
    }

    public Entry get(String id) {
        return db.jdbc().query("SELECT * FROM shared_tool WHERE id=?",
                (rs,row)->new Entry(item(rs,row),rs.getString("endpoint")),id(id))
                .stream().findFirst().orElseThrow();
    }

    @Transactional
    public Entry save(String actor,Edit input) {
        if(input==null||input.kind()==null||input.expectedRevision()<0||input.expectedRevision()==Long.MAX_VALUE)
            throw new IllegalArgumentException("请提供工具种类及当前版本。");
        String id=id(input.id());
        String title=text(input.title(),160,"名称");
        String description=input.description()==null?"":input.description().strip();
        if(description.length()>2000||description.indexOf(0)>=0)throw new IllegalArgumentException("工具说明过长或含非法字符。");
        String endpoint=endpoint(input.endpoint());
        String operation=text(input.operation(),120,"操作标识");
        if(!operation.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,119}"))
            throw new IllegalArgumentException("操作标识只能使用字母、数字、点、下划线、冒号或短横线。");
        String method=input.httpMethod();
        if(input.kind()==Kind.HTTP&&(method==null||!METHODS.contains(method))
                ||input.kind()==Kind.MCP&&method!=null)
            throw new IllegalArgumentException("请为 HTTP 选择请求方法。MCP 使用工具名称。");
        var schema=input.inputSchema();
        if(schema==null)throw new IllegalArgumentException("入参结构必须是 JSON 对象。");
        String schemaJson=db.encode(schema);
        if(schemaJson.getBytes(StandardCharsets.UTF_8).length>8192)throw new IllegalArgumentException("入参结构不能超过 8 KiB。");
        long expected=input.expectedRevision();
        long revision=Math.incrementExact(expected);
        long now=System.currentTimeMillis();
        boundary.lock();
        int changed=expected==0?db.jdbc().update("""
                INSERT INTO shared_tool(id,revision,kind,title,description,endpoint,operation,http_method,input_schema,enabled,updated_ms)
                VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO NOTHING
                """,id,revision,input.kind().name(),title,description,endpoint,operation,method,schemaJson,input.enabled(),now):
                db.jdbc().update("""
                UPDATE shared_tool SET revision=?,kind=?,title=?,description=?,endpoint=?,operation=?,http_method=?,input_schema=?,enabled=?,updated_ms=?
                 WHERE id=? AND revision=?
                """,revision,input.kind().name(),title,description,endpoint,operation,method,schemaJson,input.enabled(),now,id,expected);
        if(changed!=1)throw new IllegalStateException("工具版本已变化，请重新载入。");
        db.audit("SHARED_TOOL:"+id,null,null,actor,"SHARED_TOOL_SAVED","v"+revision+" "+(input.enabled()?"ENABLED":"DISABLED"));
        return get(id);
    }

    @SuppressWarnings("unchecked")
    private Item item(ResultSet rs,int row) throws SQLException {
        return new Item(rs.getString("id"),rs.getLong("revision"),Kind.valueOf(rs.getString("kind")),rs.getString("title"),
                rs.getString("description"),rs.getString("operation"),rs.getString("http_method"),
                db.decode(rs.getString("input_schema"),Map.class),rs.getBoolean("enabled"));
    }

    private static String id(String value) {
        if(value==null||!value.matches("[a-z][a-z0-9._-]{0,63}"))throw new IllegalArgumentException("工具标识必须以小写字母开头。");
        return value;
    }

    private static String text(String value,int limit,String label) {
        if(value==null||value.isBlank()||value.length()>limit||value.indexOf(0)>=0)
            throw new IllegalArgumentException(label+"为空、过长或含非法字符。");
        return value.strip();
    }

    private static String endpoint(String value) {
        String address=text(value,2048,"服务地址");
        URI uri;
        try {uri=URI.create(address);}
        catch(IllegalArgumentException invalid) {throw new IllegalArgumentException("服务地址格式无效。");}
        if(!Set.of("http","https").contains(java.util.Objects.toString(uri.getScheme(),""))
                ||uri.getHost()==null||uri.getUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null
                ||uri.getPort()==0||uri.getPort()>65535||uri.getRawAuthority().endsWith(":"))
            throw new IllegalArgumentException("服务地址必须是无账号、查询参数和片段的 HTTP 或 HTTPS URL。");
        String stored=uri.toASCIIString();
        if(stored.length()>2048)throw new IllegalArgumentException("编码后的服务地址不能超过 2048 个字符。");
        return stored;
    }
}

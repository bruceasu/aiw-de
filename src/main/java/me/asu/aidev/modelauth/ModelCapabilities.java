package me.asu.aidev.modelauth;

import java.util.LinkedHashMap;
import java.util.Map;

/** Documented connection semantics, not a successful model probe or permission grant. */
public final class ModelCapabilities {
    private ModelCapabilities() {}
    public static boolean protocolMatches(String runtime,String provider,String protocol) {
        if(protocol==null)return true;
        if(provider.equals("deepseek"))return runtime.startsWith("openai-agents:deepseek:"+protocol+":");
        boolean chat=runtime.startsWith("openai-agents:openai:chat-completions:");
        return protocol.equals("chat-completions")?chat:runtime.startsWith("openai-agents:")&&!runtime.startsWith("openai-agents:deepseek:")&&!chat;
    }
    public record Declaration(String loop,String history,String tools,String reasoning,String images,String files,String streaming,String verification) {}
    public static Map<String,Declaration> catalog() {
        var result=new LinkedHashMap<String,Declaration>();
        result.put("openai|responses|api-key",new Declaration("SDK","客户端历史","本机校验与执行","以模型支持为准","显式选择；以模型支持为准","默认内联；可配置请求内临时文件","增量事件","文档声明，未实测"));
        result.put("openai|chat-completions|api-key",new Declaration("SDK","客户端历史","本机校验与执行","以模型支持为准","以模型支持为准","未接入","增量事件","文档声明，未实测"));
        result.put("openai|responses|chatgpt",new Declaration("Host actions","客户端历史","受限的 Host JSON 工具","以授权和模型支持为准","未接入","未接入","增量事件","文档声明，未实测"));
        result.put("deepseek|responses|api-key",new Declaration("SDK","客户端历史；服务端无状态","本机校验；未启用 Beta strict","none / low / high / max","显式选择；以模型支持为准","默认内联；可配置请求内临时文件","增量事件","文档声明，未实测"));
        result.put("deepseek|chat-completions|api-key",new Declaration("SDK","客户端历史；原样保留推理与工具消息","本机校验；未启用 Beta strict","none / low / high / max","显式选择；以模型支持为准","默认内联；可配置请求内临时文件","完整回合事件；上游读取流","文档声明，未实测"));
        return Map.copyOf(result);
    }
}

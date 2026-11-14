package me.asu.aidev.webconsole.security;

/** Console JSON/file APIs keep their session boundary after moving out of /ui. */
public final class WebConsoleApiRoutes {
    private WebConsoleApiRoutes() {}
    public static boolean matches(String path) {
        return path.startsWith("/api/r1/")&&!path.startsWith("/api/r1/worker-protocol/")&&!path.startsWith("/api/r1/authority/");
    }
    public static String[] patterns() {
        return new String[]{"/api/r1/**"};
    }
    public static String path(jakarta.servlet.http.HttpServletRequest request) {
        var parsed=org.springframework.web.util.ServletRequestPathUtils.hasParsedRequestPath(request)?org.springframework.web.util.ServletRequestPathUtils.getParsedRequestPath(request):org.springframework.http.server.RequestPath.parse(request.getRequestURI(),request.getContextPath());
        var path=new StringBuilder();for(var element:parsed.pathWithinApplication().elements())path.append(element instanceof org.springframework.http.server.PathContainer.PathSegment segment?segment.valueToMatch():element.value());return path.toString();
    }
}

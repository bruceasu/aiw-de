package me.asu.aidev.authority;

import java.util.*;

/** Human intent and concrete selection; never a wildcard ActionScope. */
public final class ActionPermissionContract {
    private ActionPermissionContract() {}
    public enum Scope { ALL, PLAN, TASK }
    public record Issue(String requestId,String ownerId,String workerId,ActionScope.Action action,Scope scopeKind,
                        String scopeId,long maximumUses,long expiresAt,String expectedPriorId) {
        public Issue {
            uuid(requestId);Objects.requireNonNull(action);Objects.requireNonNull(scopeKind);
            text(ownerId,64);text(workerId,128);
            if(maximumUses<1||maximumUses>Integer.MAX_VALUE)throw new IllegalArgumentException("Bounded action count required");
            if(scopeKind==Scope.ALL&&scopeId!=null||scopeKind!=Scope.ALL&&(scopeId==null||scopeId.isBlank()))
                throw new IllegalArgumentException("Optional scope must be explicit");
            if(scopeId!=null)text(scopeId,128);
        }
    }
    public record Selection(String requestId,String permissionId,String targetId,Integer step,String sourceTaskId,
                            Map<String,String> parameters,String projectionJson) {
        public Selection {
            uuid(requestId);text(permissionId,128);text(targetId,128);
            if(sourceTaskId!=null)text(sourceTaskId,128);
            if(step!=null&&(step<0||step>31))throw new IllegalArgumentException("Bounded step required");
            parameters=parameters==null?Map.of():Map.copyOf(parameters);
            if(parameters.size()>16||projectionJson!=null&&projectionJson.length()>28000)throw new IllegalArgumentException("Bounded selection required");
        }
    }
    public static void uuid(String value) {
        if(value==null||!UUID.fromString(value).toString().equals(value))throw new IllegalArgumentException("Canonical request UUID required");
    }
    private static void text(String value,int limit) {
        if(value==null||value.isBlank()||value.length()>limit||!value.equals(value.strip()))throw new IllegalArgumentException("Bounded reference required");
    }
    public static Set<Scope> scopes(ActionScope.Action action) {
        return switch(action) {
            case A13,A14,A15 -> Set.of(Scope.ALL);
            case A03,A11_APPROVE,A11_REJECT,A12_CREATE,A12_EXECUTE,A19_ANSWER -> Set.of(Scope.ALL,Scope.PLAN,Scope.TASK);
            default -> Set.of(Scope.ALL,Scope.PLAN);
        };
    }
}

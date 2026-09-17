package dev.jevcraft.core;

import java.util.*;

public final class PermissionPolicy {
    public enum Role { OWNER, ADMIN, OBSERVER, NONE }
    private final UUID owner;
    private final Set<UUID> admins;
    private final boolean operatorTeleportEnabled;
    public PermissionPolicy(UUID owner, Collection<UUID> admins, boolean operatorTeleportEnabled) {
        this.owner = Objects.requireNonNull(owner); this.admins = Set.copyOf(admins); this.operatorTeleportEnabled = operatorTeleportEnabled;
    }
    public Role role(UUID player, boolean serverOperator) {
        if (owner.equals(player)) return Role.OWNER;
        if (admins.contains(player) || serverOperator) return Role.ADMIN;
        return Role.NONE;
    }
    public boolean canIssueGoal(UUID player, boolean serverOperator) { return role(player, serverOperator).ordinal() <= Role.ADMIN.ordinal(); }
    public boolean canEditAdmins(UUID player, boolean serverOperator) { return owner.equals(player) || serverOperator; }
    public boolean canTeleport(UUID player, boolean serverOperator) { return operatorTeleportEnabled && serverOperator && canIssueGoal(player, true); }
}

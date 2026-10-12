package org.fourz.RVNKQuests.waypoint;

/**
 * One component's waypoint (#2264): where the player should go while that component is active.
 *
 * <p>Immutable and Bukkit-free, so the parser and the resolver can be tested without a server. The
 * world is a name, not a {@code World}: the target world may be unloaded when the quest loads.</p>
 *
 * @param componentId the trigger or objective this waypoint belongs to
 * @param world       target world name, as authored
 * @param x           target x
 * @param y           target y
 * @param z           target z
 * @param label       the text shown to the player; never null (falls back to the component
 *                    description, then the component id)
 * @param style       how it is shown
 * @param derived     true when {@code metadata.waypoints: auto} took it from the component's own
 *                    coordinates rather than an authored {@code waypoint} block
 */
public record Waypoint(String componentId, String world, double x, double y, double z,
                       String label, WaypointStyle style, boolean derived) {

    /** True when this waypoint's world is {@code worldName}, ignoring case (Paper lowercases migrated names, #1627). */
    public boolean isInWorld(String worldName) {
        return worldName != null && world.equalsIgnoreCase(worldName);
    }

    /** Straight-line distance squared from a point. */
    public double distanceSquared(double px, double py, double pz) {
        double dx = x - px;
        double dy = y - py;
        double dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }
}

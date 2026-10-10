package org.fourz.RVNKQuests.waypoint;

/**
 * The arrow from where a player faces toward a target (#2264). Pure, no Bukkit.
 *
 * <p>Minecraft yaw: 0 faces south (+z), 90 west (-x), 180 north (-z), 270 (or -90) east (+x); yaw
 * grows clockwise seen from above. The bearing to a target is {@code atan2(-dx, dz)} in the same
 * frame. The relative angle {@code bearing - yaw}, in 0..360, falls in one of 8 sectors of 45
 * degrees centred on ahead (0), ahead-right (45), right (90) and so on. A boundary angle (22.5,
 * 67.5, ...) belongs to the next sector clockwise.</p>
 */
public final class BearingArrow {

    /** Index 0 = ahead, then clockwise. */
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    /** Shown when the target is straight above or below (less than one block away horizontally). */
    public static final String VERTICAL = "↕";

    private BearingArrow() {
    }

    /**
     * The sector 0..7, ahead = 0, clockwise; or -1 when the target is within one block horizontally.
     *
     * @param yaw the player's yaw, any range
     * @param dx  target x minus player x
     * @param dz  target z minus player z
     */
    public static int sector(float yaw, double dx, double dz) {
        if (dx * dx + dz * dz < 1.0) return -1;
        double bearing = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = (bearing - yaw) % 360.0;
        if (rel < 0) rel += 360.0;
        int s = (int) Math.floor((rel + 22.5) / 45.0);
        return s % 8;
    }

    /** The arrow for {@link #sector}. */
    public static String arrow(float yaw, double dx, double dz) {
        return arrowFor(sector(yaw, dx, dz));
    }

    /** The arrow for a sector index; {@link #VERTICAL} for -1. */
    public static String arrowFor(int sector) {
        return sector < 0 ? VERTICAL : ARROWS[sector & 7];
    }
}

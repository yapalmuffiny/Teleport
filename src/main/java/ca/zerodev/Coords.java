package ca.zerodev;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Parses coordinate arguments, supporting absolute values and {@code ~} relative offsets.
 */
public final class Coords {

    private Coords() {}

    /** True if the three arguments starting at index {@code i} all look like coordinate tokens. */
    public static boolean isTriplet(String[] args, int i) {
        return args.length >= i + 3
                && isCoordinate(args[i]) && isCoordinate(args[i + 1]) && isCoordinate(args[i + 2]);
    }

    private static boolean isCoordinate(String s) {
        if (s.startsWith("~")) s = s.substring(1);
        if (s.isEmpty()) return true;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Parses three coordinate tokens starting at index {@code i}. {@code base} supplies the origin for
     * {@code ~} relative values and the yaw/pitch to preserve; it may be {@code null}.
     */
    public static Location parse(Location base, World world, String[] args, int i) {
        double bx = base != null ? base.getX() : 0;
        double by = base != null ? base.getY() : 0;
        double bz = base != null ? base.getZ() : 0;
        double x = axis(args[i], bx, true);
        double y = axis(args[i + 1], by, false);
        double z = axis(args[i + 2], bz, true);
        float yaw = base != null ? base.getYaw() : 0f;
        float pitch = base != null ? base.getPitch() : 0f;
        return new Location(world, x, y, z, yaw, pitch);
    }

    private static double axis(String token, double base, boolean center) {
        if (token.startsWith("~")) {
            String rest = token.substring(1);
            return base + (rest.isEmpty() ? 0 : Double.parseDouble(rest));
        }
        double v = Double.parseDouble(token);
        if (center && token.indexOf('.') < 0) v += 0.5;
        return v;
    }
}

package rauk.com.hotel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import rauk.com.hotel.HotelNav.Portal;

/**
 * Rauk's way round the hotel ({@link HotelNav}) in world terms: where to make for next on the way
 * from one place to another, and whether a place is inside the building at all.
 */
public final class HotelRoutes {
	private HotelRoutes() {
	}

	@Nullable
	private static HotelFrame frame(Level level) {
		return level instanceof ServerLevel server && level.dimension() == Level.OVERWORLD ? HotelData.get(server).frame() : null;
	}

	/** The hotel's map as things stand: its lift cars at the floors they are standing at now, the doors unlocked open. */
	public static HotelNav nav(ServerLevel level) {
		Map<String, HotelPlan.Level> cars = new HashMap<>();
		for (LiftCars.Lift lift : LiftCars.ALL) {
			cars.put(lift.id(), HotelLifts.carFloor(level, lift));
		}
		return HotelNav.get(cars, HotelData.get(level).unlocked());
	}

	/** An opening on a route: where it is (the middle of its floor), and its plan cell, to steer clear of it if it fails. */
	public record Leg(Vec3 at, long cell) {
	}

	/** A whole route across the hotel, opening by opening, and what it costs (about a block walked for each). */
	public record Way(List<Leg> legs, double cost) {
	}

	/**
	 * The best way from one place to another in the hotel, for Rauk (who climbs) or someone on foot,
	 * keeping away from the openings given (by plan cell). Its legs are empty when both are in the
	 * same space; null when there's no way, or either isn't in the hotel.
	 */
	@Nullable
	public static Way route(Level level, Vec3 from, Vec3 to, Set<Long> avoid, HotelNav.Mode mode) {
		HotelFrame frame = frame(level);
		if (frame == null) {
			return null;
		}
		double[] f = plan(frame, from), t = plan(frame, to);
		HotelNav.Route r = nav((ServerLevel) level).route(f[0], f[1], f[2], t[0], t[1], t[2], p -> avoid.contains(cell(p)), mode);
		if (r == null) {
			return null;
		}
		List<Leg> legs = new ArrayList<>();
		for (Portal p : r.portals()) {
			legs.add(new Leg(world(frame, p), cell(p)));
		}
		return new Way(legs, r.cost());
	}

	/** A way out of the space a place is in: where it is, its plan cell, and how far above (or below) the place it is. */
	public record Exit(Vec3 at, long cell, double rise) {
	}

	/** Every way out of the space (or the part of it) a place is in; empty outside the hotel. */
	public static List<Exit> exits(Level level, Vec3 from) {
		HotelFrame frame = frame(level);
		List<Exit> out = new ArrayList<>();
		if (frame == null) {
			return out;
		}
		double[] f = plan(frame, from);
		HotelNav nav = nav((ServerLevel) level);
		int part = nav.areaAt(f[0], f[1], f[2]);
		if (part < 0) {
			return out;
		}
		for (Portal p : nav.portalsOf(part)) {
			Vec3 at = world(frame, p);
			out.add(new Exit(at, cell(p), at.y - from.y));
		}
		return out;
	}

	/** Which space (or part of one) a place is in, to tell whether two places are in the same one; -1 if none. */
	public static int part(Level level, Vec3 at) {
		HotelFrame frame = frame(level);
		if (frame == null) {
			return -1;
		}
		double[] f = plan(frame, at);
		return nav((ServerLevel) level).areaAt(f[0], f[1], f[2]);
	}

	/** What kind of space a place is in (a stairwell, a shaft, a room), or null outside the hotel's spaces. */
	@Nullable
	public static HotelPlan.Kind kind(Level level, Vec3 at) {
		HotelFrame frame = frame(level);
		if (frame == null) {
			return null;
		}
		double[] f = plan(frame, at);
		HotelNav nav = nav((ServerLevel) level);
		int part = nav.areaAt(f[0], f[1], f[2]);
		return part < 0 ? null : nav.kindOf(part);
	}

	private static long cell(Portal p) {
		return HotelPlan.cellKey(p.x(), p.y(), p.z());
	}

	/** Whether a block is part of the hotel's building, in a world that has one. */
	public static boolean inHotel(Level level, BlockPos pos) {
		HotelFrame frame = frame(level);
		return frame != null && frame.inBuilding(pos);
	}

	/** The hotel's building, if this world has one and this block is in it: a fence for its routes. */
	@Nullable
	public static HotelFrame around(Level level, BlockPos pos) {
		HotelFrame frame = frame(level);
		return frame != null && frame.inBuilding(pos) ? frame : null;
	}

	/** A world point in plan coordinates (the plan cell's corner plus what's left over). */
	private static double[] plan(HotelFrame frame, Vec3 at) {
		BlockPos block = BlockPos.containing(at);
		int[] c = frame.toPlan(block);
		return new double[] {c[0] + 0.5, c[1] + (at.y - block.getY()), c[2] + 0.5};
	}

	private static Vec3 world(HotelFrame frame, Portal p) {
		BlockPos b = frame.toWorld(p.x(), p.y(), p.z());
		return new Vec3(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
	}
}

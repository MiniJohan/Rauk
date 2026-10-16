package rauk.com.hotel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import rauk.com.hotel.HotelPlan.Area;
import rauk.com.hotel.HotelPlan.Kind;
import rauk.com.hotel.HotelPlan.Mark;

/**
 * Where Rauk is when the hotel is first found (playtest, 2026-10-06): at home in the Cabinet now
 * and then, but more often anywhere in the hotel, in any room, hall or corridor it fits in, though
 * never right by the coal chute you come in by. Plain Java: the plan and a seed in, a plan cell
 * out, the same for every chunk of the same hotel.
 */
public final class RaukStart {
	/** How often it is at home. */
	public static final double HOME = 0.3;
	/** How close to the bottom of the chute it may not be. */
	private static final double CLEAR_OF_ENTRY = 26;

	private RaukStart() {
	}

	/** The plan cell it starts on (the floor cell it stands in). */
	public static int[] pick(HotelPlan plan, long seed) {
		Random random = new Random(seed);
		Mark den = plan.marks.get("rauk_den");
		int[] home = {den.x(), den.y(), den.z()};
		if (random.nextDouble() < HOME) {
			return home;
		}
		List<int[]> spots = spots(plan);
		return spots.isEmpty() ? home : spots.get(random.nextInt(spots.size()));
	}

	/** One standing place in each space it could start in: any it can get out of, not one behind a locked door. */
	public static List<int[]> spots(HotelPlan plan) {
		Mark entry = plan.marks.get("chute_bottom");
		Mark den = plan.marks.get("rauk_den");
		HotelNav nav = HotelNav.get();
		java.util.Set<Integer> open = nav.reachable(den.x() + 0.5, den.y(), den.z() + 0.5);
		List<int[]> out = new ArrayList<>();
		for (Area a : plan.areas) {
			if (a.kind() != Kind.ROOM && a.kind() != Kind.HALL && a.kind() != Kind.CORRIDOR || a.level() == HotelPlan.Level.R) {
				continue;
			}
			int[] spot = spot(plan, a);
			if (spot == null) {
				continue;
			}
			double dx = spot[0] - entry.x(), dy = (spot[1] - entry.y()) * 2, dz = spot[2] - entry.z();
			if (Math.sqrt(dx * dx + dy * dy + dz * dz) < CLEAR_OF_ENTRY || !open.contains(nav.areaAt(spot[0] + 0.5, spot[1], spot[2] + 0.5))) {
				continue;
			}
			out.add(spot);
		}
		return out;
	}

	/**
	 * The open floor nearest the middle of a space with room round it for its body on all fours:
	 * three wide each way, three high, on something solid.
	 */
	private static int[] spot(HotelPlan plan, Area a) {
		int y = a.y0();
		int cx = (a.x0() + a.x1()) / 2, cz = (a.z0() + a.z1()) / 2;
		int reach = Math.max(a.x1() - a.x0(), a.z1() - a.z0());
		for (int r = 0; r <= reach; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int x = cx + dx, z = cz + dz;
					if (a.contains(x, y, z) && fits(plan, x, y, z)) {
						return new int[] {x, y, z};
					}
				}
			}
		}
		return null;
	}

	private static boolean fits(HotelPlan plan, int x, int y, int z) {
		if (plan.isAir(x, y - 1, z)) {
			return false;
		}
		for (int i = -1; i <= 1; i++) {
			for (int k = -1; k <= 1; k++) {
				for (int h = 0; h < 3; h++) {
					if (!plan.isAir(x + i, y + h, z + k)) {
						return false;
					}
				}
			}
		}
		return true;
	}
}

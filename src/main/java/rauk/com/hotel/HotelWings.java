package rauk.com.hotel;

import static rauk.com.hotel.HotelBuilder.AIR;
import static rauk.com.hotel.HotelBuilder.SOLID;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import rauk.com.hotel.HotelPlan.Area;
import rauk.com.hotel.HotelPlan.Kind;
import rauk.com.hotel.HotelPlan.Level;
import rauk.com.hotel.HotelPlan.Slant;
import rauk.com.hotel.HotelPlan.Style;

/**
 * The reshaped hotel (hotel design 14): two arms running south from the ends of the old range,
 * turning in at 45 degrees to meet at the front corner, round a courtyard. In the corner, the
 * rotunda: the new entrance hall and reception, a round stair up round a brass cage lift, galleries
 * over it and the lantern room under its dome. In the courtyard's point, the palm court. Each floor
 * has a ring of corridors round the court; the ground floor the arcade of shops and the cinema.
 *
 * <p>Laid out for the west side and mirrored for the east (x to 79 - x). Across a slanted wing,
 * {@code v = z - x} on the west side (u on the east): the court wall at 58 and 59, rooms 60 to 65, a
 * wall, the corridor 67 to 72 (six across, so Rauk can go along it on all fours), a wall, rooms 74
 * to 81 on the street side and the outer wall at 82 and 83. Along it, the rotunda starts at
 * {@code u = x + z = 139}.
 */
final class HotelWings {
	private static final int ANY = Slant.ANY;
	/** Bands across the west wing; and cut short of the rotunda (rooms), or running into it (corridors). */
	private static final Slant STREET = Slant.v(74, 81), CORRIDOR = Slant.v(67, 72), BEFORE = new Slant(-ANY, ANY, -ANY, 137),
			INTO = new Slant(-ANY, ANY, -ANY, 138);
	/** The rotunda: an octagon in the corner. */
	private static final Slant OCTAGON = new Slant(60, 81, 139, 160);
	private static final int OX0 = 31, OX1 = 48, OZ0 = 99, OZ1 = 116;
	/** The round stair's well (the lift's cage in its middle) and the cage. */
	private static final int WX0 = 35, WX1 = 43, WZ0 = 104, WZ1 = 112, CX0 = 37, CX1 = 41, CZ0 = 106, CZ1 = 110;

	private final HotelBuilder b;
	private final HotelPlan p;
	private int roomNumber;

	/** One side of the U, laid out in the west side's coordinates and mirrored for the east. */
	private record Side(boolean east) {
		String name() {
			return east ? "East" : "West";
		}

		int x(int x) {
			return east ? 79 - x : x;
		}

		/** A slant given for the west side, for this side: v and u trade places. */
		Slant cut(Slant s) {
			if (s == null || !east) {
				return s;
			}
			return new Slant(shift(s.uMin(), -79), shift(s.uMax(), -79), shift(s.vMin(), 79), shift(s.vMax(), 79));
		}

		private static int shift(int a, int d) {
			return Math.abs(a) >= ANY ? a : a + d;
		}

		String facing(String f) {
			if (!east) {
				return f;
			}
			return f.equals("east") ? "west" : f.equals("west") ? "east" : f;
		}
	}

	private static final Side WEST = new Side(false), EAST = new Side(true);
	private static final Side[] SIDES = {WEST, EAST};

	HotelWings(HotelBuilder b) {
		this.b = b;
		this.p = b.p;
	}

	// --- the shape ---------------------------------------------------------------------------------

	/** Whether a cell is in the new part's footprint on a floor: the arms, the slanted wings and the corner. */
	static boolean footprint(int x, int z) {
		int v = z - x, u = x + z;
		boolean arm = (x >= HotelPlan.FOOT_X0 && x <= HotelPlan.ARM_W1 || x >= HotelPlan.ARM_E0 && x <= HotelPlan.FOOT_X1) && z >= 48
				&& z <= HotelPlan.ARM_END;
		boolean wing = (v >= HotelPlan.WEST_IN && v <= HotelPlan.WEST_OUT || u >= HotelPlan.EAST_IN && u <= HotelPlan.EAST_OUT)
				&& v <= HotelPlan.WEST_OUT && u <= HotelPlan.EAST_OUT && x >= HotelPlan.FOOT_X0 && x <= HotelPlan.FOOT_X1 && z >= 48
				&& z <= HotelPlan.FRONT_Z;
		return arm || wing;
	}

	/** The same set back a block all round: the attic under the mansard, and the roof over it. */
	static boolean atticFootprint(int x, int z) {
		int v = z - x, u = x + z;
		boolean arm = (x >= 0 && x <= HotelPlan.ARM_W1 - 1 || x >= HotelPlan.ARM_E0 + 1 && x <= HotelPlan.FOOT_X1 - 1) && z >= 48
				&& z <= HotelPlan.ARM_END - 1;
		boolean wing = (v >= HotelPlan.WEST_IN + 1 && v <= HotelPlan.WEST_OUT - 1 || u >= HotelPlan.EAST_IN + 1 && u <= HotelPlan.EAST_OUT - 1)
				&& v <= HotelPlan.WEST_OUT - 1 && u <= HotelPlan.EAST_OUT - 1 && x >= 0 && x <= HotelPlan.FOOT_X1 - 1 && z >= 48
				&& z <= HotelPlan.FRONT_Z - 1;
		return arm || wing;
	}

	/** The palm court's point of the courtyard, under glass: between the wings, south of the court. */
	static boolean palmCourt(int x, int z) {
		return z >= 84 && z - x <= HotelPlan.WEST_IN - 1 && x + z <= HotelPlan.EAST_IN - 1;
	}

	/** The courtyard: between the arms and the slanted wings, north of the palm court, open to the sky. */
	static boolean courtyard(int x, int z) {
		return HotelPlan.inCourtyard(x, Level.L1.base, z);
	}

	/** Near a slanted wall: where the outside ornament of straight walls gives way to slanted pieces. */
	static boolean slanted(int x, int z) {
		int v = z - x, u = x + z;
		return z >= 70 && (v >= 54 && v <= 88 || u >= 133 && u <= 167);
	}

	/** The masonry of the new part, level by level, as the old range's is (HotelBuilder.envelope). */
	void envelope() {
		for (Level lv : new Level[] {Level.B1, Level.L1, Level.L2, Level.L3}) {
			for (int z = 48; z <= HotelPlan.FRONT_Z; z++) {
				for (int x = HotelPlan.FOOT_X0; x <= HotelPlan.FOOT_X1; x++) {
					boolean palm = palmCourt(x, z) && lv != Level.L3;
					if (footprint(x, z) || palm) {
						p.fill(x, lv.base - 1, z, x, lv.base + 5, z, SOLID);
					}
				}
			}
		}
		int ledge = Level.L4.base - 1;
		for (int z = 48; z <= HotelPlan.FRONT_Z; z++) {
			for (int x = HotelPlan.FOOT_X0; x <= HotelPlan.FOOT_X1; x++) {
				if (footprint(x, z)) {
					p.set(x, ledge, z, SOLID);
				}
				if (atticFootprint(x, z)) {
					p.fill(x, Level.L4.base, z, x, Level.L4.base + 5, z, SOLID);
					p.set(x, Level.R.base - 1, z, SOLID);
				}
			}
		}
	}

	// --- rooms -------------------------------------------------------------------------------------

	/** A space of a wing, given for the west side: a box cut by a slant, h high from the floor. */
	private Area room(Side s, String name, Level lv, Style st, Kind k, int x0, int z0, int x1, int z1, Slant cut, int h) {
		int a = s.x(x0), c = s.x(x1);
		return b.slantSpace(name, lv, st, k, Math.min(a, c), lv.base, z0, Math.max(a, c), lv.base + h - 1, z1, s.cut(cut));
	}

	private Area room(Side s, String name, Level lv, Style st, int x0, int z0, int x1, int z1, Slant cut) {
		return room(s, name, lv, st, Kind.ROOM, x0, z0, x1, z1, cut, 4);
	}

	/**
	 * A corridor, 3 high, with Rauk's crawlspace over it the same shape (2 high), and a way up into
	 * the crawlspace at each end of it.
	 */
	private Area corridor(Side s, String name, Level lv, Style st, int x0, int z0, int x1, int z1, Slant cut) {
		Area a = room(s, name, lv, st, Kind.CORRIDOR, x0, z0, x1, z1, cut, 3);
		Area crawl = b.slantSpace(name + " crawlspace", lv, Style.HIDDEN, Kind.CRAWL, a.x0(), lv.base + 4, a.z0(), a.x1(), lv.base + 5, a.z1(),
				a.slant());
		// Its two ends: the cells furthest each way along it.
		int[] lo = null, hi = null;
		double loT = Double.MAX_VALUE, hiT = -Double.MAX_VALUE;
		for (int z = a.z0(); z <= a.z1(); z++) {
			for (int x = a.x0(); x <= a.x1(); x++) {
				if (!a.contains(x, lv.base, z) || p.owner(x, lv.base, z) != a) {
					continue;
				}
				double t = a.slant() == null ? (a.x1() - a.x0() >= a.z1() - a.z0() ? x : z) : s.east() ? z - x : x + z;
				if (t < loT) {
					loT = t;
					lo = new int[] {x, z};
				}
				if (t > hiT) {
					hiT = t;
					hi = new int[] {x, z};
				}
			}
		}
		for (int[] end : new int[][] {lo, hi}) {
			if (end == null) {
				continue;
			}
			for (int[] d : new int[][] {{0, 0}, {1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
				int x = end[0] + d[0], z = end[1] + d[1];
				if (p.owner(x, lv.base, z) == a) {
					b.carve(crawl, x, lv.base + 3, z, x, lv.base + 3, z);
				}
			}
		}
		return a;
	}

	/** A door, given for the west side. */
	private void door(Side s, Level lv, Area into, int x, int z, String facing, String wood) {
		b.door(lv, into, s.x(x), z, s.facing(facing), wood);
	}

	/** A pair of doors side by side along x (given for the west side, from its western one). */
	private void doubleDoorX(Side s, Level lv, Area into, int x, int z, String facing, String wood) {
		b.doubleDoor(lv, into, Math.min(s.x(x), s.x(x + 1)), z, s.facing(facing), wood);
	}

	/**
	 * A door through a slanted wall (given for the west side): the wall is built out straight either
	 * side of it, a block each way, so the door hangs square in it and shuts against something.
	 */
	private void slantDoor(Side s, Level lv, Area into, int x, int z, String facing, String wood) {
		int wx = s.x(x);
		String f = s.facing(facing);
		boolean alongX = f.equals("east") || f.equals("west");
		for (int side : new int[] {-1, 1}) {
			int sx = alongX ? wx : wx + side, sz = alongX ? z + side : z;
			Area o = p.owner(sx, lv.base, sz);
			if (o != null && o.kind() != Kind.CRAWL) {
				b.solid(sx, lv.base, sz, sx, o.y1(), sz);
			}
		}
		b.door(lv, into, wx, z, f, wood);
	}

	/** A room on the street side of a slanted wing, x from x0 to x1, its door in from the corridor. */
	private Area streetRoom(Side s, Level lv, String name, Style st, int x0, int x1, int vTop) {
		Area a = room(s, name, lv, st, x0, 60, x1, 125, Slant.v(74, vTop).and(BEFORE));
		int dx = (x0 + x1) / 2 + 1;
		slantDoor(s, lv, a, dx, dx + 73, "west", "dark_oak");
		return a;
	}

	/** A room on the court side of a slanted wing, its door in from the corridor. */
	private Area courtRoom(Side s, Level lv, String name, Style st, int x0, int x1, int vBottom, String wood) {
		Area a = room(s, name, lv, st, x0, 60, x1, 125, Slant.v(vBottom, 65).and(BEFORE));
		int dx = (x0 + x1) / 2;
		slantDoor(s, lv, a, dx, dx + 66, "north", wood);
		return a;
	}

	/**
	 * The room in the corner where an arm turns in: from the arm corridor out to the street wall,
	 * round the bend (the street side of the slanted wing begins past it). Its door is through the
	 * corridor's wall, two thick there.
	 */
	private Area cornerRoom(Side s, Level lv, String name, Style st, int x0, int vTop) {
		Area a = room(s, name, lv, st, x0, 68, 5, 95, Slant.v(-ANY, vTop));
		b.carve(a, s.x(6), lv.base, 71, s.x(6), lv.base + 1, 71);
		door(s, lv, a, 7, 71, "west", "spruce");
		return a;
	}

	private String nextRoom(Level lv) {
		return "Room " + lv.label + String.format("%02d", roomNumber++);
	}

	// --- the floors --------------------------------------------------------------------------------

	/** The stair towers where the arms turn in, B1 to the attic: switchback flights round an open middle. */
	void towers() {
		for (Side s : SIDES) {
			int xa = s.x(12), xb = s.x(16);
			int x0 = Math.min(xa, xb), x1 = Math.max(xa, xb);
			Area well = b.space(s.name() + " stair tower", Level.B1, Style.SERVICE, Kind.STAIRS, x0, Level.B1.base, 68, x1,
					Level.L4.base + 3, 75);
			// The flights: the west pair down from the door end, the east pair back up to the next floor.
			int fa = s.x(12), fa2 = s.x(13), fb = s.x(15), fb2 = s.x(16);
			for (Level lv : new Level[] {Level.B1, Level.L1, Level.L2, Level.L3, Level.L4}) {
				int base = lv.base;
				if (lv != Level.B1) {
					b.solid(x0, base - 1, 68, x1, base - 1, 69);
				}
				door(s, lv, well, 11, 69, "east", "spruce");
				if (lv == Level.L4) {
					continue;
				}
				b.solid(x0, base + 2, 73, x1, base + 2, 75);
				for (int i = 0; i < 3; i++) {
					for (int x : new int[] {fa, fa2}) {
						b.stair("minecraft:spruce_stairs", x, base + i, 70 + i, "south", false);
						b.solid(x, base + i - 1, 70 + i, x, base + i - 1, 70 + i);
					}
					for (int x : new int[] {fb, fb2}) {
						b.stair("minecraft:spruce_stairs", x, base + 3 + i, 72 - i, "north", false);
					}
				}
			}
		}
	}

	/**
	 * The shaft in the thick outer wall at the corner of each arm, B1 to the third floor: the laundry
	 * chute on the west side, the dumbwaiter on the east, a hatch onto it on each floor (too narrow
	 * for anyone but Rauk).
	 */
	void shafts() {
		for (Side s : SIDES) {
			int x = s.x(0);
			b.space(s.east() ? "Dumbwaiter" : "Laundry chute", Level.B1, Style.HIDDEN, Kind.TUNNEL, x, Level.B1.base, 70, x, Level.L3.base + 3, 70);
			// An open trapdoor stands against the side away from its facing: the room's side.
			String hatch = s.east() ? "minecraft:spruce_trapdoor" : "minecraft:dark_oak_trapdoor";
			String face = s.facing("west");
			for (Level lv : new Level[] {Level.L1, Level.L2, Level.L3}) {
				b.later(x, lv.base, 70, x, lv.base + 3, 70, hatch + "[facing=" + face + ",half=bottom,open=true,powered=false,waterlogged=false]");
			}
			b.mark(s.east() ? "dumbwaiter" : "laundry_chute", x, Level.B1.base, 70);
		}
	}

	/** Levels 2 and 3: guest rooms all round, the corridors in a ring round the court. */
	void guestFloor(Level lv, int firstRoom) {
		roomNumber = firstRoom;
		boolean upper = lv == Level.L3;
		for (Side s : SIDES) {
			boolean burned = upper && s.east();
			Style corr = upper ? (burned ? Style.RUINED : Style.WORN) : Style.COZY;
			String n = s.name() + " ";
			corridor(s, n + "arm corridor " + lv.label, lv, corr, 8, 37, 10, 77, null);
			corridor(s, n + "wing corridor " + lv.label, lv, corr, 8, 70, 40, 125, CORRIDOR.and(INTO));
			int i = 0;
			for (int[] z : new int[][] {{49, 53}, {55, 59}, {61, 66}}) {
				Style st = burned ? Style.RUINED : upper ? Style.WORN : i++ % 2 == 0 ? Style.COZY : Style.COZY_RED;
				Area a = room(s, nextRoom(lv), lv, st, 1, z[0], 6, z[1], null);
				door(s, lv, a, 7, (z[0] + z[1]) / 2, "west", "dark_oak");
				String court = s.east() || z[0] != 49 || upper ? nextRoom(lv) : "Children's playroom";
				Area c = room(s, court, lv, burned ? Style.RUINED : upper ? Style.WORN : Style.COZY, 12, z[0], 16, z[1], null);
				door(s, lv, c, 11, (z[0] + z[1]) / 2, "east", "dark_oak");
			}
			cornerRoom(s, lv, n + (s.east() ? "pantry " : "linen room ") + lv.label, burned ? Style.RUINED : Style.RUNDOWN, 1, 81);
			for (int[] x : new int[][] {{7, 11}, {13, 17}, {19, 23}, {25, 29}}) {
				Style st = burned ? Style.RUINED : upper ? Style.WORN : x[0] % 12 == 7 ? Style.COZY_RED : Style.COZY;
				streetRoom(s, lv, nextRoom(lv), st, x[0], x[1], 81);
			}
			courtRoom(s, lv, n + "maids' closet " + lv.label, Style.RUNDOWN, 18, 19, 60, "spruce");
			for (int[] x : new int[][] {{21, 25}, {27, 31}, {33, 36}}) {
				courtRoom(s, lv, nextRoom(lv), burned ? Style.RUINED : upper ? Style.WORN : Style.COZY, x[0], x[1], 60, "dark_oak");
			}
		}
		if (upper) {
			// The fire went on down the east wing: through the floor of its corridor to the one below.
			b.space("Burned through the east wing", Level.L3, Style.RUINED, Kind.TUNNEL, 58, Level.L2.base + 3, 92, 59, Level.L3.base - 1, 93);
		}
	}

	/**
	 * The ground floor: on the west, the arm's rooms and the arcade of shops down the slanted wing; on
	 * the east, the cinema, its foyer and the tea room.
	 */
	void lobbyFloor() {
		Level lv = Level.L1;
		Side s = WEST;
		Area passage = corridor(s, "West arm passage", lv, Style.LOBBY, 8, 48, 10, 77, null);
		b.door(lv, passage, 9, 47, "north", "spruce");
		Area luggage = room(s, "Luggage hall", lv, Style.RUNDOWN, 1, 49, 6, 58, null);
		door(s, lv, luggage, 7, 53, "west", "dark_oak");
		Area music = room(s, "Music room", lv, Style.COZY, 1, 60, 6, 66, null);
		door(s, lv, music, 7, 63, "west", "dark_oak");
		cornerRoom(s, lv, "Manager's office", Style.WORN, 1, 81);
		Area lounge = room(s, "Ladies' lounge", lv, Style.COZY_RED, 12, 49, 16, 58, null);
		door(s, lv, lounge, 11, 53, "east", "dark_oak");
		Area lav = room(s, "Lavatories", lv, Style.KITCHEN, 12, 60, 16, 66, null);
		door(s, lv, lav, 11, 63, "east", "spruce");
		corridor(s, "The Arcade", lv, Style.LOBBY, 8, 70, 40, 125, CORRIDOR.and(INTO));
		String[] shops = {"Barber's", "Telegraph office", "Florist", "Jeweller"};
		Style[] looks = {Style.WORN, Style.COZY, Style.GRAND, Style.COZY_RED};
		int[][] xs = {{7, 11}, {13, 17}, {19, 23}, {25, 29}};
		for (int i = 0; i < 4; i++) {
			Area shop = room(s, shops[i], lv, looks[i], xs[i][0], 60, xs[i][1], 125, STREET.and(BEFORE));
			int dx = (xs[i][0] + xs[i][1]) / 2 + 1;
			slantDoor(s, lv, shop, dx, dx + 73, "west", "oak");
		}
		courtRoom(s, lv, "Porters' cupboard", Style.RUNDOWN, 18, 19, 60, "spruce");
		courtRoom(s, lv, "Waiting room", Style.COZY, 21, 27, 60, "dark_oak");
		courtRoom(s, lv, "Lost property", Style.RUNDOWN, 29, 36, 60, "spruce");

		s = EAST;
		Area cinema = room(s, "Cinema", lv, Style.COZY_RED, Kind.HALL, 1, 49, 16, 66, null, 5);
		Area cpass = corridor(s, "Cinema passage", lv, Style.LOBBY, 8, 68, 10, 77, null);
		doubleDoorX(s, lv, cinema, 8, 67, "north", "dark_oak");
		cornerRoom(s, lv, "Projection room", Style.RUNDOWN, 1, 81);
		// The projector's slot through the back wall of the cinema.
		b.carve(cinema, Math.min(s.x(3), s.x(4)), lv.base + 2, 67, Math.max(s.x(3), s.x(4)), lv.base + 2, 67);
		room(s, "Cinema foyer", lv, Style.COZY_RED, Kind.HALL, 8, 60, 40, 125, Slant.v(67, 81).and(INTO), 4);
		courtRoom(s, lv, "Box office", Style.COZY_RED, 21, 25, 60, "oak");
		courtRoom(s, lv, "Tea room", Style.COZY, 27, 36, 60, "oak");
		courtRoom(s, lv, "Usherettes' cupboard", Style.RUNDOWN, 18, 19, 60, "spruce");
		b.mark("cinema", s.x(8), lv.base, 58);
		b.mark("foyer", s.x(18), lv.base, 92);
	}

	/** B1: the servants' tunnels in a ring under the new wings, their stores, and the ice house. */
	void serviceLevel() {
		Level lv = Level.B1;
		for (Side s : SIDES) {
			String n = s.name() + " ";
			Area tunnel = corridor(s, n + "servants' tunnel", lv, Style.SERVICE, 8, 48, 10, 77, null);
			// Into the old service level: the staff dormitory on the west, the spa on the east.
			door(s, lv, tunnel, s.east() ? 9 : 10, 47, "north", "spruce");
			corridor(s, n + "servants' tunnel south", lv, Style.SERVICE, 8, 70, 40, 125, CORRIDOR.and(INTO));
			String[] street = s.east() ? new String[] {"Organ blower room", "Cinema boiler room"} : new String[] {"Porters' rooms", "Old trunks"};
			String[] court = s.east() ? new String[] {"Staff lockers", "Coke store"} : new String[] {"Lamp room", "Linen store"};
			int k = 0;
			for (int[] z : new int[][] {{49, 58}, {60, 66}}) {
				Area a = room(s, street[k], lv, k == 0 && s.east() ? Style.MACHINE : Style.RUNDOWN, 1, z[0], 6, z[1], null);
				door(s, lv, a, 7, (z[0] + z[1]) / 2, "west", "spruce");
				Area c = room(s, court[k], lv, Style.SERVICE, 12, z[0], 16, z[1], null);
				door(s, lv, c, 11, (z[0] + z[1]) / 2, "east", "spruce");
				k++;
			}
			cornerRoom(s, lv, s.east() ? "Dumbwaiter room" : "Laundry chute room", Style.KITCHEN, 1, 81);
			String[] stores = s.east() ? new String[] {"Wine store", "Cold store", "Kitchen stores"} : new String[] {"Stores", "Coal bunker", "Furnace room"};
			int[][] xs = {{7, 13}, {15, 21}, {23, 29}};
			for (int i = 0; i < 3; i++) {
				Area a = room(s, stores[i], lv, i == 2 && !s.east() ? Style.MACHINE : Style.SERVICE, xs[i][0], 60, xs[i][1], 125, STREET.and(BEFORE));
				int dx = (xs[i][0] + xs[i][1]) / 2 + 1;
				slantDoor(s, lv, a, dx, dx + 73, "west", "spruce");
			}
			courtRoom(s, lv, s.east() ? "Ice store" : "Servants' hall", s.east() ? Style.KITCHEN : Style.RUNDOWN, 21, 36, 60, "spruce");
		}
		// The ice house, under the palm court, through the rotunda's cellar.
		Area ice = b.slantSpace("Ice house", lv, Style.KITCHEN, Kind.ROOM, 20, lv.base, 85, 59, lv.base + 3, 97,
				new Slant(-ANY, HotelPlan.WEST_IN - 1, -ANY, HotelPlan.EAST_IN - 1));
		b.carve(ice, 39, lv.base, 97, 40, lv.base + 2, 98);
		b.doubleDoor(lv, ice, 39, 98, "north", "spruce");
	}

	/** The attic under the mansard all round: servants' rooms off a ring of corridors. */
	void attic(int firstRoom) {
		Level lv = Level.L4;
		roomNumber = firstRoom;
		for (Side s : SIDES) {
			Style st = s.east() ? Style.RUINED : Style.RUNDOWN;
			String n = s.name() + " ";
			corridor(s, n + "attic arm corridor", lv, st, 8, 37, 10, 77, null);
			corridor(s, n + "attic wing corridor", lv, st, 8, 70, 40, 125, CORRIDOR.and(INTO));
			for (int[] z : new int[][] {{49, 53}, {55, 59}, {61, 66}}) {
				Area a = room(s, "Attic room " + roomNumber++, lv, st, 2, z[0], 6, z[1], null);
				door(s, lv, a, 7, (z[0] + z[1]) / 2, "west", "spruce");
				Area c = room(s, "Attic room " + roomNumber++, lv, st, 12, z[0], 15, z[1], null);
				door(s, lv, c, 11, (z[0] + z[1]) / 2, "east", "spruce");
			}
			cornerRoom(s, lv, n + (s.east() ? "pantry 4" : "linen room 4"), st, 2, 80);
			for (int[] x : new int[][] {{7, 11}, {13, 17}, {19, 23}, {25, 29}}) {
				streetRoom(s, lv, "Attic room " + roomNumber++, st, x[0], x[1], 80);
			}
			for (int[] x : new int[][] {{21, 25}, {27, 31}, {33, 36}}) {
				courtRoom(s, lv, "Attic room " + roomNumber++, st, x[0], x[1], 61, "spruce");
			}
		}
	}

	// --- the rotunda -------------------------------------------------------------------------------

	/**
	 * The rotunda in the corner, B1 to the attic: the cellar, reception (the new front doors open
	 * into it), the galleries on 2 and 3, the lantern room under the dome. Up its middle the round
	 * stair climbs round the brass cage of its lift, half way round each floor.
	 */
	void rotunda() {
		b.slantSpace("Rotunda cellar", Level.B1, Style.SERVICE, Kind.HALL, OX0, Level.B1.base, OZ0, OX1, Level.B1.base + 3, OZ1, OCTAGON);
		Area hall = b.slantSpace("Reception", Level.L1, Style.LOBBY, Kind.HALL, OX0, Level.L1.base, OZ0, OX1, Level.L1.base + 4, OZ1, OCTAGON);
		Area g2 = b.slantSpace("Rotunda gallery 2", Level.L2, Style.LOBBY, Kind.HALL, OX0, Level.L2.base, OZ0, OX1, Level.L2.base + 3, OZ1, OCTAGON);
		Area g3 = b.slantSpace("Rotunda gallery 3", Level.L3, Style.WORN, Kind.HALL, OX0, Level.L3.base, OZ0, OX1, Level.L3.base + 3, OZ1, OCTAGON);
		b.slantSpace("Lantern room", Level.L4, Style.RUNDOWN, Kind.HALL, OX0, Level.L4.base, OZ0, OX1, Level.L4.base + 3, OZ1, OCTAGON);
		b.mark("lobby", 39, 0, 114);
		b.mark("front_doors", 39, 0, HotelPlan.FRONT_Z);
		// The front doors, two pairs of glazed doors at the corner under a glass transom, set back in
		// the wall behind the lockdown's shutters, the last to rise in the finale (hotel design 4.6).
		// (Cut through to the outer face, so no window is set beside them; the shutters fill it again.)
		b.carve(hall, 38, Level.L1.base, OZ1 + 1, 41, Level.L1.base + 3, HotelPlan.FRONT_Z);
		b.doubleDoor(Level.L1, hall, 38, HotelPlan.FRONT_Z - 1, "south", "oak");
		b.doubleDoor(Level.L1, hall, 40, HotelPlan.FRONT_Z - 1, "south", "oak");
		b.later(38, Level.L1.base + 2, HotelPlan.FRONT_Z - 1, 41, Level.L1.base + 3, HotelPlan.FRONT_Z - 1, "minecraft:glass");
		b.later(38, Level.L1.base, HotelPlan.FRONT_Z, 41, Level.L1.base + 3, HotelPlan.FRONT_Z, HotelBuilder.SHUTTER + "[facing=south]");
		// Through to the palm court behind it.
		Area palm = b.slantSpace("Palm court", Level.L1, Style.LOBBY, Kind.HALL, 20, Level.L1.base, 85, 59, Level.L2.base + 4, 97,
				new Slant(-ANY, HotelPlan.WEST_IN - 1, -ANY, HotelPlan.EAST_IN - 1));
		b.carve(palm, 39, Level.L1.base, 97, 40, Level.L1.base + 3, 98);
		b.doubleDoor(Level.L1, palm, 39, 98, "north", "oak");
		// The tall window of brass lattice up the corner, over the doors, into the galleries.
		for (Level lv : new Level[] {Level.L2, Level.L3}) {
			Area g = lv == Level.L2 ? g2 : g3;
			b.carve(g, 38, lv.base, OZ1 + 1, 41, lv.base + 3, OZ1 + 1);
		}
		b.later(38, Level.L2.base, HotelPlan.FRONT_Z, 41, Level.L3.base + 3, HotelPlan.FRONT_Z, "rauk:brass_lattice");
		b.later(37, Level.L2.base - 1, HotelPlan.FRONT_Z, 37, Level.L3.base + 4, HotelPlan.FRONT_Z, "rauk:limestone");
		b.later(42, Level.L2.base - 1, HotelPlan.FRONT_Z, 42, Level.L3.base + 4, HotelPlan.FRONT_Z, "rauk:limestone");
		b.later(38, Level.L3.base + 4, HotelPlan.FRONT_Z, 41, Level.L3.base + 4, HotelPlan.FRONT_Z, "rauk:limestone");
		b.later(39, Level.L3.base + 4, HotelPlan.FRONT_Z, 40, Level.L3.base + 4, HotelPlan.FRONT_Z, "rauk:limestone_keystone[facing=south]");
		b.later(38, Level.L2.base - 1, HotelPlan.FRONT_Z, 41, Level.L2.base - 1, HotelPlan.FRONT_Z, "rauk:limestone");

		// The well, the cage and its lift.
		b.space("Rotunda stair", Level.L1, Style.LOBBY, Kind.STAIRS, WX0, Level.L1.base, WZ0, WX1, Level.L4.base + 3, WZ1);
		b.space("Rotunda lift", Level.L1, Style.LOBBY, Kind.LIFT, CX0 + 1, Level.L1.base, CZ0 + 1, CX1 - 1, Level.L4.base - 2, CZ1 - 1);
		b.later(CX0, Level.L4.base - 1, CZ0, CX1, Level.L4.base - 1, CZ1, "rauk:brass");
		// Each floor's flights: up one side to a corner landing and along the next side to the floor
		// above, half way round; the floor over them left open, the rest of the floor kept.
		Level[] from = {Level.L1, Level.L2, Level.L3};
		for (int f = 0; f < 3; f++) {
			int base = from[f].base;
			boolean westNorth = f != 1;
			int top = base + 7;
			// The floor of the level above, over the parts of the ring not climbing to it.
			for (int z = WZ0; z <= WZ1; z++) {
				for (int x = WX0; x <= WX1; x++) {
					if (x >= CX0 && x <= CX1 && z >= CZ0 && z <= CZ1) {
						continue;
					}
					boolean open = westNorth ? x <= WX0 + 1 || z <= WZ0 + 1 && x <= 39 : x >= WX1 - 1 || z >= WZ1 - 1 && x >= 39;
					if (!open) {
						b.solid(x, top - 1, z, x, top - 1, z);
					}
				}
			}
			for (int i = 0; i < 4; i++) {
				if (westNorth) {
					for (int x = WX0; x <= WX0 + 1; x++) {
						b.stair("rauk:marble_stairs", x, base + i, 110 - i, "north", false);
					}
				} else {
					for (int x = WX1 - 1; x <= WX1; x++) {
						b.stair("rauk:marble_stairs", x, base + i, 106 + i, "south", false);
					}
				}
			}
			if (westNorth) {
				b.solid(WX0, base + 3, WZ0, WX0 + 1, base + 3, CZ0);
				for (int i = 0; i < 3; i++) {
					for (int z = WZ0; z <= WZ0 + 1; z++) {
						b.stair("rauk:marble_stairs", CX0 + i, base + 4 + i, z, "east", false);
					}
				}
			} else {
				b.solid(WX1 - 1, base + 3, CZ1, WX1, base + 3, WZ1);
				for (int i = 0; i < 3; i++) {
					for (int z = WZ1 - 1; z <= WZ1; z++) {
						b.stair("rauk:marble_stairs", CX1 - i, base + 4 + i, z, "west", false);
					}
				}
			}
		}
		// The cage: brass lattice, banded in brass at each floor, a gate onto each floor it serves.
		b.fittings.add(() -> {
			for (int y = Level.L1.base; y <= Level.L4.base - 2; y++) {
				for (int z = CZ0; z <= CZ1; z++) {
					for (int x = CX0; x <= CX1; x++) {
						boolean edge = x == CX0 || x == CX1 || z == CZ0 || z == CZ1;
						boolean gate = x == 39 && z == CZ1 && Math.floorMod(y - Level.L1.base, 7) <= 1 && y < Level.L4.base;
						boolean band = Math.floorMod(y - Level.L1.base, 7) == 6;
						if (edge && !gate) {
							p.set(x, y, z, band ? "rauk:brass" : "rauk:brass_lattice");
						}
					}
				}
			}
			// Railings round the open well on each floor above, where it falls away.
			for (Level lv : new Level[] {Level.L2, Level.L3, Level.L4}) {
				int y = lv.base;
				for (int z = WZ0 - 1; z <= WZ1 + 1; z++) {
					for (int x = WX0 - 1; x <= WX1 + 1; x++) {
						boolean ring = x >= WX0 && x <= WX1 && z >= WZ0 && z <= WZ1;
						if (ring || !p.isAir(x, y, z) || !p.isAir(x, y - 1, z) && p.at(x, y - 1, z) != 0 && !floorAt(x, y - 1, z)) {
							continue;
						}
						for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
							int nx = x + d[0], nz = z + d[1];
							if (nx >= WX0 && nx <= WX1 && nz >= WZ0 && nz <= WZ1 && p.isAir(nx, y - 1, nz) && !(nx >= CX0 && nx <= CX1 && nz >= CZ0 && nz <= CZ1)) {
								p.set(x, y, z, "rauk:wrought_iron_railing");
								break;
							}
						}
					}
				}
			}
		});
		b.mark("rotunda_lift", 39, Level.L1.base, 108);
		// The palm court under glass: a door out to the court through its glass front, the front and
		// the roof glazed on brass bars.
		b.carve(palm, 39, Level.L1.base, 84, 40, Level.L1.base + 1, 84);
		b.doubleDoor(Level.L1, palm, 39, 84, "north", "oak");
		b.fittings.add(() -> {
			int roof = Level.L2.base + 5;
			for (int z = 84; z <= 97; z++) {
				for (int x = 20; x <= 59; x++) {
					if (!palmCourt(x, z)) {
						continue;
					}
					if (z >= 85 && p.owner(x, roof - 1, z) == palm) {
						p.set(x, roof, z, Math.floorMod(x - 39, 5) == 0 || Math.floorMod(z, 5) == 0 ? "rauk:brass" : "minecraft:glass");
					}
					if (z == 84) {
						for (int y = Level.L1.base; y < roof; y++) {
							if (p.isAir(x, y, z + 1) && p.owner(x, y, z + 1) == palm && !p.get(x, y, z).contains("door")) {
								p.set(x, y, z, Math.floorMod(x - 39, 4) == 0 || y == Level.L2.base - 1 ? "rauk:brass" : "minecraft:glass");
							}
						}
					}
				}
			}
		});
	}

	private boolean floorAt(int x, int y, int z) {
		String s = p.get(x, y, z);
		return !s.isEmpty() && !p.isAir(x, y, z) && !s.contains("stairs") && !s.contains("water");
	}

	// --- outside ------------------------------------------------------------------------------------

	/**
	 * The courtyard (after the skin and the ornament, so its walls are outside walls): grass, a cross
	 * of gravel paths, dead hedges along them, the dry fountain in the middle. An area of its own a
	 * floor high, so Rauk's map takes in the way across it.
	 */
	Area courtyard() {
		int y = Level.L1.base;
		int fx = 39, fz = 66;
		for (int z = 49; z <= 83; z++) {
			for (int x = HotelPlan.ARM_W1 + 1; x < HotelPlan.ARM_E0; x++) {
				if (!courtyard(x, z) || p.at(x, y - 1, z) != 0) {
					continue;
				}
				boolean path = x == fx || x == fx + 1 || z == fz || z == fz + 1;
				double d = Math.hypot(x + 0.5 - (fx + 1), z + 0.5 - (fz + 1));
				p.set(x, y - 1, z, path || d < 5.5 ? "minecraft:gravel" : "minecraft:grass_block[snowy=false]");
				p.fill(x, y - 4, z, x, y - 2, z, "minecraft:dirt");
				// Hedges along the paths, gaps here and there; a dead tree or two in the beds.
				boolean beside = (x == fx - 1 || x == fx + 2 || z == fz - 1 || z == fz + 2) && !path && d >= 5.5;
				if (beside && Math.floorMod(x * 7 + z * 3, 9) != 0 && p.at(x, y, z) == 0) {
					p.set(x, y, z, "rauk:dead_hedge");
				}
			}
		}
		// The fountain: a round basin, dry, round a pedestal with an urn on it.
		for (int z = fz - 4; z <= fz + 5; z++) {
			for (int x = fx - 4; x <= fx + 5; x++) {
				double d = Math.hypot(x + 0.5 - (fx + 1), z + 0.5 - (fz + 1));
				if (d >= 3.2 && d < 4.4) {
					p.set(x, y, z, "minecraft:stone_brick_wall[east=low,north=low,south=low,up=true,waterlogged=false,west=low]");
					p.set(x, y - 1, z, "minecraft:stone_bricks");
				} else if (d < 3.2) {
					p.set(x, y - 1, z, d < 1.3 ? "minecraft:chiseled_stone_bricks" : "minecraft:cracked_stone_bricks");
				}
			}
		}
		p.fill(fx, y, fz, fx + 1, y + 1, fz + 1, "minecraft:stone_bricks");
		p.fill(fx, y + 2, fz, fx + 1, y + 2, fz + 1, "minecraft:chiseled_stone_bricks");
		p.set(fx, y + 3, fz, "minecraft:decorated_pot[cracked=true,facing=south,waterlogged=false]");
		// The courtyard as a space: its open air a floor high.
		Area court = new Area("Courtyard", Level.L1, Style.LOBBY, Kind.OUTSIDE, HotelPlan.ARM_W1 + 1, y, 49, HotelPlan.ARM_E0 - 1, y + 5, 83);
		p.areas.add(court);
		int id = p.areas.size();
		for (int yy = y; yy <= y + 5; yy++) {
			for (int z = 49; z <= 83; z++) {
				for (int x = HotelPlan.ARM_W1 + 1; x < HotelPlan.ARM_E0; x++) {
					if (courtyard(x, z) && p.at(x, yy, z) == 0) {
						p.set(x, yy, z, AIR);
						p.setOwner(x, yy, z, id);
					}
				}
			}
		}
		b.mark("courtyard", fx - 3, y, fz);
		return court;
	}

	/**
	 * The corner from the street (after the reference photo): a bronze canopy round the doors on
	 * branching brass columns, a string of bulbs under its edge; the dome over the rotunda, the
	 * hotel's name in brass on it, and bulbs along the parapet. Nearly all of them are dead.
	 */
	void corner() {
		int z = HotelPlan.FRONT_Z, y = Level.L1.base;
		p.fill(32, y + 5, z + 1, 47, y + 5, z + 3, "rauk:canopy_slab[type=bottom,waterlogged=false]");
		for (int x = 32; x <= 47; x++) {
			p.set(x, y + 4, z + 3, "rauk:festoon[axis=x,lit=" + (x == 35 || x == 44) + "]");
		}
		for (int x : new int[] {33, 37, 42, 46}) {
			for (int yy = y; yy <= y + 4; yy++) {
				String part = yy == y ? "base" : yy == y + 4 ? "capital" : "shaft";
				p.set(x, yy, z + 3, "rauk:brass_column[part=" + part + "]");
			}
		}
		p.fill(31, y - 1, z + 1, 48, y - 1, z + 4, "rauk:limestone");
		dome();
		// Bulbs along the drum's limestone band, nearly all dead.
		int band = Level.R.base + 2;
		for (int x = 35; x <= 44; x++) {
			if (p.at(x, band, OZ1 + 1) == 0) {
				p.set(x, band, OZ1 + 1, "rauk:festoon[axis=x,lit=" + (x == 41) + "]");
			}
		}
	}

	/**
	 * The dome on the rotunda, after the photo's crown: a drum of stone standing up out of the roof,
	 * "RAUKVIK GRAND" in brass letters across its front and small windows round it, then a dome of
	 * slate curving in to a brass-and-glass lantern with a finial.
	 */
	private void dome() {
		int y0 = Level.R.base;
		// The drum: three courses of ashlar, a band of limestone round its top.
		for (int k = 0; k < 3; k++) {
			for (int z = OZ0; z <= OZ1; z++) {
				for (int x = OX0; x <= OX1; x++) {
					if (OCTAGON.contains(x, z)) {
						p.set(x, y0 + k, z, k == 2 ? "rauk:limestone" : "rauk:ashlar");
					}
				}
			}
		}
		// The dome: each ring set in from the last, steeply at first and flattening toward the top.
		int[] inset = {0, 0, 1, 1, 2, 3, 4, 6};
		for (int k = 0; k < inset.length; k++) {
			int i = inset[k];
			Slant ring = new Slant(OCTAGON.vMin() + i, OCTAGON.vMax() - i, OCTAGON.uMin() + i, OCTAGON.uMax() - i);
			for (int z = OZ0 + i; z <= OZ1 - i; z++) {
				for (int x = OX0 + i; x <= OX1 - i; x++) {
					if (ring.contains(x, z)) {
						p.set(x, y0 + 3 + k, z, "rauk:slate_shingles");
					}
				}
			}
		}
		// The lantern on top.
		int ly = y0 + 3 + inset.length;
		for (int z = 106; z <= 110; z++) {
			for (int x = 37; x <= 41; x++) {
				boolean edge = x == 37 || x == 41 || z == 106 || z == 110;
				boolean post = (x == 37 || x == 41) && (z == 106 || z == 110);
				if (edge) {
					p.set(x, ly, z, post ? "rauk:brass" : "minecraft:glass");
					p.set(x, ly + 1, z, post ? "rauk:brass" : "minecraft:glass");
				}
				p.set(x, ly + 2, z, "rauk:brass");
			}
		}
		p.set(39, ly + 3, 108, "rauk:brass_finial");
		// The name across the front of the drum, in two lines, the letters standing just off it.
		String[] lines = {"GRAND", "RAUKVIK"};
		for (int l = 0; l < 2; l++) {
			String word = lines[l];
			int x0 = 40 - (word.length() + 1) / 2;
			for (int i = 0; i < word.length(); i++) {
				p.set(x0 + i, y0 + l, OZ1 + 1, "rauk:brass_letter[facing=south,letter=" + Character.toLowerCase(word.charAt(i)) + "]");
			}
		}

	}

	// --- the slanted walls --------------------------------------------------------------------------

	private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
	/** For each pair of walls (north and east, east and south...), the slant's facing: it looks away from them. */
	private static final String[] SLANT_FACING = {"west", "north", "east", "south"};

	/** Whether a cell is wall a slant can stand against: masonry, stone, panelling; not an opening or fitting. */
	private boolean wall(int x, int y, int z) {
		String s = p.get(x, y, z);
		if (s.isEmpty() || p.isAir(x, y, z)) {
			return false;
		}
		return !(s.contains("door") || s.contains("stairs") || s.contains("slab") || s.contains("pane") || s.contains("bars") || s.contains("lattice")
				|| s.contains("railing") || s.contains("glass") || s.contains("slant") || s.contains("shutter") || s.contains("water")
				|| s.contains("trapdoor") || s.contains("ladder") || s.contains("letter") || s.contains("festoon") || s.contains("finial"));
	}

	/** Which pair of walls a cell sits in the corner of (0 north-east, 1 east-south, 2 south-west, 3 west-north), or -1. */
	private int corner(int x, int y, int z) {
		for (int c = 0; c < 4; c++) {
			int[] a = DIRS[c], d = DIRS[(c + 1) % 4], na = DIRS[(c + 2) % 4], nd = DIRS[(c + 3) % 4];
			if (wall(x + a[0], y, z + a[1]) && wall(x + d[0], y, z + d[1]) && !wall(x + na[0], y, z + na[1]) && !wall(x + nd[0], y, z + nd[1])
					&& wall(x + a[0] + d[0], y, z + a[1] + d[1])) {
				return c;
			}
		}
		return -1;
	}

	/** The slanted piece matching a wall cell's surface, or null. */
	private static String slantFor(String block) {
		String id = HotelPlan.blockId(block);
		String name = id.substring(id.indexOf(':') + 1);
		if (name.equals("deepslate_tiles")) {
			name = "slate_shingles";
		}
		if (name.equals("limestone_keystone") || name.equals("limestone_cornice")) {
			name = "limestone";
		}
		String slant = HotelMaterials.slantId(name);
		for (HotelMaterials.Material m : HotelMaterials.ALL) {
			if (m.id().equals(slant)) {
				return "rauk:" + slant;
			}
		}
		return null;
	}

	/**
	 * Fits the slanted pieces into the steps of every wall at 45 degrees (after the skin, before the
	 * ornament): inside, each in the surface of the wall it stands in; outside, in the stone (slate up
	 * the mansard). Only in a run of steps along a slanted wall, never in a square room's corner.
	 * Windows go in the outside walls too: every sixth step along the floors' window heights where a
	 * room is behind, a slanted shutter outside and the same inside.
	 */
	void slants() {
		record Notch(int x, int y, int z, int c) {
		}
		List<Notch> notches = new ArrayList<>();
		for (int y = Level.B1.base; y <= Level.R.base + 6; y++) {
			for (int z = 60; z <= HotelPlan.FRONT_Z + 2; z++) {
				for (int x = -3; x <= 82; x++) {
					if (!slanted(x, z)) {
						continue;
					}
					boolean outside = p.at(x, y, z) == 0 && y >= Level.L1.base;
					Area o = p.owner(x, y, z);
					boolean inside = p.isAir(x, y, z) && o != null && o.kind() != Kind.CRAWL && o.kind() != Kind.LIFT && o.kind() != Kind.TUNNEL
							&& o.kind() != Kind.OUTSIDE;
					if (!outside && !inside) {
						continue;
					}
					int c = corner(x, y, z);
					if (c < 0) {
						continue;
					}
					// In a run: the next cell along the slanted wall each way, one at least, is the same.
					int[] a = DIRS[c], d = DIRS[(c + 1) % 4];
					int ax = a[0] - d[0], az = a[1] - d[1];
					if (corner(x + ax, y, z + az) != c && corner(x - ax, y, z - az) != c) {
						continue;
					}
					notches.add(new Notch(x, y, z, c));
				}
			}
		}
		// Windows: which outside steps, and the step inside each looks into.
		Set<Long> windows = new HashSet<>();
		for (Notch n : notches) {
			if (p.at(n.x(), n.y(), n.z()) != 0 || !windowHeight(n.y())) {
				continue;
			}
			boolean westLine = n.c() == 0 || n.c() == 2;
			int along = westLine ? n.x() + n.z() : n.z() - n.x();
			if (Math.floorMod(along, 6) != 0) {
				continue;
			}
			for (int dz = -3; dz <= 3; dz++) {
				for (int dx = -3; dx <= 3; dx++) {
					int x = n.x() + dx, z = n.z() + dz;
					Area o = p.owner(x, n.y(), z);
					if (o != null && p.isAir(x, n.y(), z) && (o.kind() == Kind.ROOM || o.kind() == Kind.HALL || o.kind() == Kind.CORRIDOR)
							&& corner(x, n.y(), z) == (n.c() + 2) % 4 && Math.abs((westLine ? x + z : z - x) - along) <= 1) {
						windows.add(HotelPlan.cellKey(n.x(), n.y(), n.z()));
						windows.add(HotelPlan.cellKey(x, n.y(), z));
					}
				}
			}
		}
		for (Notch n : notches) {
			int[] a = DIRS[n.c()], d = DIRS[(n.c() + 1) % 4];
			String piece = windows.contains(HotelPlan.cellKey(n.x(), n.y(), n.z())) ? "rauk:slant_shutter" : null;
			if (piece == null) {
				piece = slantFor(p.get(n.x() + a[0], n.y(), n.z() + a[1]));
			}
			if (piece == null) {
				piece = slantFor(p.get(n.x() + d[0], n.y(), n.z() + d[1]));
			}
			if (piece == null) {
				continue;
			}
			p.set(n.x(), n.y(), n.z(), piece + "[facing=" + SLANT_FACING[n.c()] + "]");
		}
	}

	/** Whether a height is at a window: the middle two blocks of a floor's rooms, on 1, 2 and 3. */
	private static boolean windowHeight(int y) {
		for (Level lv : new Level[] {Level.L1, Level.L2, Level.L3}) {
			if (y == lv.base + 1 || y == lv.base + 2) {
				return true;
			}
		}
		return false;
	}
}

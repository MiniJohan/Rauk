package rauk.com.hotel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole hotel as blocks, worked out in code (hotel design 7): every cell of a box around the
 * building holds a block, as a block state string ({@code minecraft:oak_door[facing=south,...]}),
 * or nothing (leave the world as it is). Built by {@link HotelBuilder}; copied into the world by
 * world generation and by the test command, and drawn as floor plans for review. Plain Java, no
 * game classes, so the plans can be drawn outside the game.
 *
 * <p>Plan space: x runs west to east, z north (the sea) to south (the front doors), y up, with the
 * lobby floor at y 0. The old building, the north range, is x 0..79, z 0..47; since the reshaping
 * (hotel design 14) two arms run south from its ends and turn in at 45 degrees to meet at the front
 * corner (z 118), round a courtyard. Along a slanted wing two other coordinates are handy:
 * {@code v = z - x} runs across the west wing (and along the east one), {@code u = x + z} across
 * the east wing (and along the west one).
 */
public final class HotelPlan {
	/** The plan box: x -8..87, y -16..47, z -8..129. */
	public static final int X0 = -8, Y0 = -16, Z0 = -8, SX = 96, SY = 64, SZ = 138;
	/** The core of the old building, the north range, inside its outer walls: x 0..79, z 0..47. */
	public static final int WIDTH = 80, DEPTH = 48;
	/** The old building's walls outside, its old front pavilions included (hotel design 11.1). */
	public static final int FOOT_X0 = -1, FOOT_X1 = 80, FOOT_Z0 = -1, FOOT_Z1 = 50;
	/** The front corner's face, where the doors are now, and how deep the whole building is. */
	public static final int FRONT_Z = 118, TOTAL_DEPTH = 119;
	/** The arms (hotel design 14): west x -1..17, east x 62..80, from the old range south to z 82. */
	public static final int ARM_W1 = 17, ARM_E0 = 62, ARM_END = 82;
	/** The slanted wings: the west one between v 58 (the court) and 83 (the street), the east between u 137 and 162. */
	public static final int WEST_IN = 58, WEST_OUT = 83, EAST_IN = 137, EAST_OUT = 162;

	/** The levels, bottom to top, each 7 blocks above the last. */
	public enum Level {
		B2("B2", "Machinery", -14), B1("B1", "Service level", -7), L1("1", "Lobby floor", 0), L2("2", "Lower guest floor", 7),
		L3("3", "Upper guest floor", 14), L4("4", "Attic and penthouse", 21), R("R", "Roof", 28);

		public final String label, title;
		/** The y of its floor: the floor block is one below. */
		public final int base;

		Level(String label, String title, int base) {
			this.label = label;
			this.title = title;
			this.base = base;
		}
	}

	/**
	 * The look of an area (hotel design 11.2): its floor, the panelling along the bottom of its
	 * walls, the walls above, its ceiling, and whether its corridors have the red runner. Intact,
	 * worn or wrecked.
	 */
	public enum Style {
		/** The lobby and atrium: cream and black marble, worn. */
		LOBBY("rauk:marble_cream", "rauk:marble_black", "rauk:marble_wall", "rauk:plaster_ceiling", 0xE8E2D4, true),
		/** The dining room: parquet, faded gilt panels, worn. */
		GRAND("rauk:parquet", "rauk:mahogany_wainscot_worn", "rauk:cream_plaster_worn", "rauk:plaster_ceiling_cracked", 0xD8C8A8, false),
		/** The ballroom: cream and gold, peeling, the gilded ceiling. */
		BALLROOM("rauk:parquet", "rauk:ballroom_panel_worn", "rauk:cream_plaster_worn", "rauk:gilded_ceiling", 0xE0CC98, false),
		/** Intact and cozy: mahogany, green damask, coffered ceilings. */
		COZY("rauk:dark_boards", "rauk:mahogany_wainscot", "rauk:damask_green", "rauk:coffered_mahogany", 0x7A9A6E, true),
		/** Intact and cozy, in red: the bar, the best suites. */
		COZY_RED("rauk:parquet", "rauk:mahogany_wainscot", "rauk:damask_red", "rauk:coffered_mahogany", 0xB05050, true),
		/** The same, faded and stained. */
		WORN("rauk:dark_boards", "rauk:mahogany_wainscot_worn", "rauk:damask_green_worn", "rauk:plaster_ceiling_cracked", 0x8C9A84, true),
		/** A fine room wrecked: the smoking room, the billiard room. */
		WRECKED("rauk:dark_boards", "rauk:mahogany_wainscot_worn", "rauk:damask_red_worn", "rauk:plaster_ceiling_cracked", 0x9A6A60, false),
		/** The staff's rooms and the attic: old boards, plaster coming down. */
		RUNDOWN("rauk:dark_boards", "rauk:mahogany_wainscot_worn", "rauk:plaster_peeling", "rauk:plaster_ceiling_cracked", 0xA79A86, false),
		/** Service passages and stairs: concrete and peeling plaster. */
		SERVICE("rauk:concrete_stained", "rauk:plaster_peeling", "rauk:plaster_peeling", "rauk:plaster_ceiling_cracked", 0x9C8F8A, false),
		/** The kitchen, laundry and cold rooms: grimy tiles, a concrete ceiling, pipes. */
		KITCHEN("rauk:tile_floor_grimy", "rauk:tile_wall_grimy", "rauk:tile_wall_grimy", "rauk:concrete_stained", 0xB8B4A0, false),
		/** The machinery: bare concrete. */
		MACHINE("rauk:concrete_stained", "rauk:concrete_stained", "rauk:concrete_stained", "rauk:concrete_stained", 0x7A7872, false),
		/** The spa and the drains: algae on the tiles. */
		WET("rauk:algae_mosaic", "rauk:algae_mosaic", "rauk:tile_wall_grimy", "rauk:plaster_ceiling_cracked", 0x7FB3A8, false),
		/** Burned: charred boards and panelling. */
		RUINED("rauk:charred_boards", "rauk:charred_wainscot", "rauk:plaster_peeling", "rauk:plaster_ceiling_cracked", 0x5E5652, false),
		/** Wennberg's own: kept, red and dark wood. */
		PENTHOUSE("rauk:parquet", "rauk:mahogany_wainscot", "rauk:damask_red", "rauk:coffered_mahogany", 0xB8786A, true),
		DEN("minecraft:packed_mud", "minecraft:mossy_stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:stone_bricks", 0x7C8C5E, false),
		HIDDEN("minecraft:bricks", "minecraft:bricks", "minecraft:bricks", "minecraft:bricks", 0x5A4E4A, false);

		public final String floor, wainscot, wall, ceiling;
		/** Its color on the floor plans. */
		public final int color;
		/** Whether its corridors have the red runner down them. */
		public final boolean runner;

		Style(String floor, String wainscot, String wall, String ceiling, int color, boolean runner) {
			this.floor = floor;
			this.wainscot = wainscot;
			this.wall = wall;
			this.ceiling = ceiling;
			this.color = color;
			this.runner = runner;
		}
	}

	/** What an area is for, which decides windows, crawlspaces and how it is drawn. */
	public enum Kind { ROOM, HALL, CORRIDOR, STAIRS, LIFT, CRAWL, TUNNEL, SECRET, OUTSIDE }

	/**
	 * A cut at 45 degrees through a box: only the cells with {@code v = z - x} and {@code u = x + z}
	 * within these bounds. The rooms of the slanted wings are boxes cut so.
	 */
	public record Slant(int vMin, int vMax, int uMin, int uMax) {
		public static final int ANY = 100000;

		public boolean contains(int x, int z) {
			int v = z - x, u = x + z;
			return v >= vMin && v <= vMax && u >= uMin && u <= uMax;
		}

		/** Cells across the west wing, {@code v} from a to b. */
		public static Slant v(int a, int b) {
			return new Slant(a, b, -ANY, ANY);
		}

		/** Cells across the east wing, {@code u} from a to b. */
		public static Slant u(int a, int b) {
			return new Slant(-ANY, ANY, a, b);
		}

		public Slant and(Slant o) {
			return new Slant(Math.max(vMin, o.vMin), Math.min(vMax, o.vMax), Math.max(uMin, o.uMin), Math.min(uMax, o.uMax));
		}
	}

	/** A named space of the hotel, inclusive bounds in plan space, and in a slanted wing its cut. */
	public record Area(String name, Level level, Style style, Kind kind, int x0, int y0, int z0, int x1, int y1, int z1, Slant slant) {
		public Area(String name, Level level, Style style, Kind kind, int x0, int y0, int z0, int x1, int y1, int z1) {
			this(name, level, style, kind, x0, y0, z0, x1, y1, z1, null);
		}

		public boolean contains(int x, int y, int z) {
			return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1 && (slant == null || slant.contains(x, z));
		}

		public double centerX() {
			return (x0 + x1 + 1) / 2.0;
		}

		public double centerZ() {
			return (z0 + z1 + 1) / 2.0;
		}
	}

	/** A place things happen: Rauk's den, where you climb in, and so on. */
	public record Mark(String name, int x, int y, int z) {
	}

	/**
	 * The hotel's wiring (hotel design 6.3): a light switch and the electric lamps it works, by
	 * their cells (a lamp of several blocks by one of them).
	 */
	public record Circuit(int[] light, List<int[]> lamps) {
	}

	private final short[] cells = new short[SX * SY * SZ];
	/** For each carved cell, the area it belongs to (index + 1). */
	private final short[] owner = new short[SX * SY * SZ];
	private final List<String> palette = new ArrayList<>(List.of(""));
	private final Map<String, Short> ids = new HashMap<>();
	public final List<Area> areas = new ArrayList<>();
	public final Map<String, Mark> marks = new HashMap<>();
	public final List<Circuit> circuits = new ArrayList<>();
	/** What a lift's car stands on and over at its first floor: the plan's blocks there without it. */
	public final Map<Long, String> underCar = new HashMap<>();

	public static long cellKey(int x, int y, int z) {
		return ((long) (x - X0) << 40) | ((long) (y - Y0) << 20) | (z - Z0);
	}

	/** A cell's x, y and z from its key. */
	public static int[] cellXYZ(long key) {
		return new int[] {(int) (key >> 40) + X0, (int) ((key >> 20) & 0xFFFFF) + Y0, (int) (key & 0xFFFFF) + Z0};
	}

	public static boolean inBox(int x, int y, int z) {
		return x >= X0 && x < X0 + SX && y >= Y0 && y < Y0 + SY && z >= Z0 && z < Z0 + SZ;
	}

	private static int index(int x, int y, int z) {
		return ((y - Y0) * SZ + (z - Z0)) * SX + (x - X0);
	}

	public short id(String block) {
		return ids.computeIfAbsent(block, b -> {
			palette.add(b);
			return (short) (palette.size() - 1);
		});
	}

	public List<String> palette() {
		return palette;
	}

	/** The palette index at a cell: 0 for nothing (and outside the box). */
	public int at(int x, int y, int z) {
		return inBox(x, y, z) ? cells[index(x, y, z)] : 0;
	}

	public String get(int x, int y, int z) {
		return palette.get(at(x, y, z));
	}

	public void set(int x, int y, int z, String block) {
		if (inBox(x, y, z)) {
			cells[index(x, y, z)] = id(block);
		}
	}

	public void clear(int x, int y, int z) {
		if (inBox(x, y, z)) {
			cells[index(x, y, z)] = 0;
			owner[index(x, y, z)] = 0;
		}
	}

	public void fill(int x0, int y0, int z0, int x1, int y1, int z1, String block) {
		for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					set(x, y, z, block);
				}
			}
		}
	}

	/** The area a carved cell belongs to, or null. */
	public Area owner(int x, int y, int z) {
		int o = inBox(x, y, z) ? owner[index(x, y, z)] : 0;
		return o == 0 ? null : areas.get(o - 1);
	}

	/** Gives a cell to an area, by its index in {@link #areas} plus one (0 for none). */
	void setOwner(int x, int y, int z, int area) {
		if (inBox(x, y, z)) {
			owner[index(x, y, z)] = (short) area;
		}
	}

	public Area area(String name) {
		for (Area a : areas) {
			if (a.name().equals(name)) {
				return a;
			}
		}
		throw new IllegalArgumentException("No area " + name);
	}

	/** The block id of a state string: {@code minecraft:oak_door[...]} is {@code minecraft:oak_door}. */
	public static String blockId(String state) {
		int i = state.indexOf('[');
		return i < 0 ? state : state.substring(0, i);
	}

	public boolean isAir(int x, int y, int z) {
		String s = get(x, y, z);
		return s.equals("minecraft:air") || s.equals("minecraft:cave_air");
	}

	/** The lowest and highest y with something in this column, or null. */
	public int[] column(int x, int z) {
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int y = Y0; y < Y0 + SY; y++) {
			if (at(x, y, z) != 0) {
				lo = Math.min(lo, y);
				hi = Math.max(hi, y);
			}
		}
		return lo > hi ? null : new int[] {lo, hi};
	}

	/** Whether a point is in the core of the building, inside its outer walls (the courtyard counts). */
	public static boolean inCore(double x, double y, double z) {
		return outside(x, z) < -1.5 && y >= Level.B2.base - 1 && y < Level.R.base + 7;
	}

	/** Whether a point is inside the building itself (its walls and ornaments included), not the ground round it. */
	public static boolean inBuilding(double x, double y, double z) {
		return outside(x, z) <= 3 && y >= Level.B2.base - 1 && y < Level.R.base + 14;
	}

	/**
	 * How far a point is outside the building's outer walls, in blocks (negative inside, as far as
	 * the nearest wall): the old range, the arms, the slanted wings and the corner, and the courtyard
	 * they close in. For shaping the ground round it and telling inside from out.
	 */
	public static double outside(double x, double z) {
		double r2 = Math.sqrt(2);
		double v = z - x, u = x + z;
		// The old range (with its old pavilions), the arms, and the court between them.
		double range = box(x, z, FOOT_X0, FOOT_X1 + 1, FOOT_Z0, DEPTH + 1);
		double arms = box(x, z, FOOT_X0, FOOT_X1 + 1, DEPTH, ARM_END + 1);
		// The slanted wings and the corner: between the street fronts and the court, cut off at the front.
		double wings = Math.max(Math.max((v - (WEST_OUT + 1)) / r2, (u - (EAST_OUT + 1)) / r2),
				Math.max(Math.max(FOOT_X0 - x, x - (FOOT_X1 + 1)), Math.max(ARM_END - 8 - z, z - (FRONT_Z + 1))));
		return Math.min(range, Math.min(arms, wings));
	}

	/**
	 * Whether a cell is in the courtyard, open to the sky (hotel design 14): between the arms and the
	 * slanted wings, north of the palm court, at the ground floor or above.
	 */
	public static boolean inCourtyard(int x, int y, int z) {
		return y >= Level.L1.base && x > ARM_W1 && x < ARM_E0 && z >= 49 && z <= 83 && z - x <= WEST_IN - 1 && x + z <= EAST_IN - 1;
	}

	/** How far a point is outside an axis-aligned box (negative inside). */
	private static double box(double x, double z, double x0, double x1, double z0, double z1) {
		double dx = Math.max(x0 - x, x - x1), dz = Math.max(z0 - z, z - z1);
		if (dx <= 0 && dz <= 0) {
			return Math.max(dx, dz);
		}
		return Math.hypot(Math.max(dx, 0), Math.max(dz, 0));
	}
}

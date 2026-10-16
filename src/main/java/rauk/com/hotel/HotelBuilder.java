package rauk.com.hotel;

import static rauk.com.hotel.HotelPlan.DEPTH;
import static rauk.com.hotel.HotelPlan.FOOT_X0;
import static rauk.com.hotel.HotelPlan.FOOT_X1;
import static rauk.com.hotel.HotelPlan.FOOT_Z0;
import static rauk.com.hotel.HotelPlan.FOOT_Z1;
import static rauk.com.hotel.HotelPlan.WIDTH;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import rauk.com.hotel.HotelPlan.Area;
import rauk.com.hotel.HotelPlan.Kind;
import rauk.com.hotel.HotelPlan.Level;
import rauk.com.hotel.HotelPlan.Mark;
import rauk.com.hotel.HotelPlan.Slant;
import rauk.com.hotel.HotelPlan.Style;

/**
 * Lays out the Raukvik Grand (hotel design 3 and 11), level by level: its rooms, corridors, stairs,
 * lift shafts and the atrium, its walls inside and out, and the ornament that gives it depth.
 * Then {@link HotelDresser} furnishes it (design 12).
 *
 * <p>It works the way the building was built: each level starts as solid masonry, the rooms and
 * corridors are carved out of it, the windows are cut through the walls, and what's left is
 * skinned: floor where something stands on it, ceiling where it's overhead, panelling and
 * wallpaper (or tiles, or plaster) where it faces a room, according to the room's condition;
 * rusticated limestone, blue stucco and slate outside; brick where nobody sees it. Then come the
 * cornices, pilasters, quoins, dormers, turrets and portico outside, the stairs, doors and shutters,
 * and inside the runners, pilasters and columns.
 *
 * <p>Every floor is 7 blocks above the last: rooms 4 high (the grand ones 5), corridors 3 high with
 * a 2-high crawlspace over them, which is Rauk's (design 3.2). The outer walls are two blocks thick
 * and the front pavilions four, so every window sits in a deep reveal.
 */
public final class HotelBuilder {
	static final String SOLID = "#solid", AIR = "minecraft:air", WATER = "minecraft:water";
	/** The lockdown's iron shutters. */
	static final String SHUTTER = "rauk:shutter";
	private static final String FOUNDATION = "minecraft:stone_bricks", FILLER = "minecraft:bricks", ROOF = "minecraft:deepslate_tiles",
			LIMESTONE = "rauk:limestone", RUSTICATED = "rauk:rusticated_limestone", STUCCO = "rauk:stucco",
			STUCCO_STAINED = "rauk:stucco_stained", ASHLAR = "rauk:ashlar", ASHLAR_STAINED = "rauk:ashlar_stained", SLATE = "rauk:slate_shingles",
			RAILING = "rauk:wrought_iron_railing", LATTICE = "rauk:iron_lattice", BRASS = "rauk:brass", GLASS = "minecraft:glass",
			BARS = "minecraft:iron_bars";
	/** The pavilions standing forward of the front: the two corners and the middle, as x ranges. */
	private static final int[][] PAVILIONS = {{FOOT_X0, 17}, {62, FOOT_X1}, {32, 47}};

	private static HotelPlan built;

	/** The hotel, built once and kept. */
	public static synchronized HotelPlan plan() {
		if (built == null) {
			built = new HotelBuilder().build();
		}
		return built;
	}

	final HotelPlan p = new HotelPlan();
	/** Blocks placed once the masonry is skinned: stairs, doors, shutters, water. */
	final List<Runnable> fittings = new ArrayList<>();

	/** A window cut through an outer wall: its shutter's cells, which way it looks, its level. */
	private record Window(int x, int z, int y0, int y1, int[] out, Level level) {
	}

	private final List<Window> windows = new ArrayList<>();

	private HotelBuilder() {
	}

	private HotelPlan build() {
		// The reshaped hotel (hotel design 14): the old building as the north range, the new wings
		// round the courtyard laid out by HotelWings.
		HotelWings wings = new HotelWings(this);
		envelope();
		wings.envelope();
		b2();
		b1();
		l1();
		guestFloor(Level.L2);
		guestFloor(Level.L3);
		attic();
		penthouse();
		staffCore();
		atrium();
		wings.serviceLevel();
		wings.lobbyFloor();
		wings.guestFloor(Level.L2, 15);
		wings.guestFloor(Level.L3, 22);
		wings.attic(14);
		wings.towers();
		wings.shafts();
		wings.rotunda();
		lifts();
		windows();
		skin();
		wings.corner();
		wings.slants();
		exterior();
		turrets();
		fittings.forEach(Runnable::run);
		interior();
		wings.courtyard();
		new HotelDresser(p).dress();
		connect();
		return p;
	}

	// --- the masonry ------------------------------------------------------------------------------

	/** Each level as a solid band of masonry, floor slab to the slab of the next. */
	private void envelope() {
		band(Level.B2, 10, 0, 56, 33);
		band(Level.B1, 0, 0, WIDTH - 1, DEPTH - 1);
		for (Level lv : new Level[] {Level.L1, Level.L2, Level.L3}) {
			band(lv, FOOT_X0, FOOT_Z0, FOOT_X1, DEPTH);
			for (int[] pv : PAVILIONS) {
				band(lv, pv[0], DEPTH + 1, pv[1], FOOT_Z1);
			}
		}
		// The roof of the third floor, all round the foot of the mansard.
		int ledge = Level.L4.base - 1;
		p.fill(FOOT_X0, ledge, FOOT_Z0, FOOT_X1, ledge, DEPTH, SOLID);
		for (int[] pv : PAVILIONS) {
			p.fill(pv[0], ledge, DEPTH + 1, pv[1], ledge, FOOT_Z1, SOLID);
		}
		// The attic under the mansard, set back from the walls below; the central pavilion's front
		// stands up through it; the roof over all.
		band(Level.L4, 0, 0, WIDTH - 1, DEPTH - 1);
		band(Level.L4, 32, DEPTH, 47, FOOT_Z1);
		p.fill(0, Level.R.base - 1, 0, WIDTH - 1, Level.R.base - 1, DEPTH - 1, SOLID);
		p.fill(32, Level.R.base - 1, DEPTH, 47, Level.R.base - 1, FOOT_Z1, SOLID);
		// On the penthouse roof: Wennberg's glasshouse, and the service lift's machine room.
		p.fill(22, Level.R.base - 1, 0, 37, Level.R.base + 5, 11, SOLID);
		p.fill(37, Level.R.base - 1, 0, 43, Level.R.base + 4, 5, SOLID);
	}

	void band(Level lv, int x0, int z0, int x1, int z1) {
		p.fill(x0, lv.base - 1, z0, x1, lv.base + 5, z1, SOLID);
	}

	/** Carves a space out of the masonry, from the level's floor up to {@code h} blocks high. */
	Area room(String name, Level lv, Style style, Kind kind, int x0, int z0, int x1, int z1, int h) {
		return space(name, lv, style, kind, x0, lv.base, z0, x1, lv.base + h - 1, z1);
	}

	Area space(String name, Level lv, Style style, Kind kind, int x0, int y0, int z0, int x1, int y1, int z1) {
		Area a = new Area(name, lv, style, kind, Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1),
				Math.max(z0, z1));
		p.areas.add(a);
		carve(a, a.x0(), a.y0(), a.z0(), a.x1(), a.y1(), a.z1());
		return a;
	}

	/**
	 * A space cut at 45 degrees (hotel design 14): the cells of the box that are inside the slant,
	 * the area's bounds drawn in tight round them. Without a slant, a plain box.
	 */
	Area slantSpace(String name, Level lv, Style style, Kind kind, int x0, int y0, int z0, int x1, int y1, int z1, Slant slant) {
		if (slant == null) {
			return space(name, lv, style, kind, x0, y0, z0, x1, y1, z1);
		}
		int ax0 = Integer.MAX_VALUE, az0 = Integer.MAX_VALUE, ax1 = Integer.MIN_VALUE, az1 = Integer.MIN_VALUE;
		for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				if (slant.contains(x, z)) {
					ax0 = Math.min(ax0, x);
					ax1 = Math.max(ax1, x);
					az0 = Math.min(az0, z);
					az1 = Math.max(az1, z);
				}
			}
		}
		if (ax0 > ax1) {
			throw new IllegalStateException("Nothing of " + name + " is left inside its slant");
		}
		Area a = new Area(name, lv, style, kind, ax0, Math.min(y0, y1), az0, ax1, Math.max(y0, y1), az1, slant);
		p.areas.add(a);
		int id = p.areas.size();
		for (int y = a.y0(); y <= a.y1(); y++) {
			for (int z = az0; z <= az1; z++) {
				for (int x = ax0; x <= ax1; x++) {
					if (slant.contains(x, z)) {
						p.set(x, y, z, AIR);
						p.setOwner(x, y, z, id);
					}
				}
			}
		}
		return a;
	}

	void carve(Area owner, int x0, int y0, int z0, int x1, int y1, int z1) {
		int id = p.areas.indexOf(owner) + 1;
		for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					p.set(x, y, z, AIR);
					p.setOwner(x, y, z, id);
				}
			}
		}
	}

	/** A guest room, 4 high. */
	Area room(String name, Level lv, Style style, int x0, int z0, int x1, int z1) {
		return room(name, lv, style, Kind.ROOM, x0, z0, x1, z1, 4);
	}

	/**
	 * A corridor, 3 high, with Rauk's crawlspace over it (2 high, under the next floor) and a way
	 * up into the crawlspace at each end: a gap in the ceiling, 2 by 2.
	 */
	Area corridor(String name, Level lv, Style style, int x0, int z0, int x1, int z1) {
		Area a = room(name, lv, style, Kind.CORRIDOR, x0, z0, x1, z1, 3);
		Area crawl = space(name + " crawlspace", lv, Style.HIDDEN, Kind.CRAWL, x0, lv.base + 4, z0, x1, lv.base + 5, z1);
		carve(crawl, x0, lv.base + 3, z0, x0 + 1, lv.base + 3, z0 + 1);
		carve(crawl, x1 - 1, lv.base + 3, z1 - 1, x1, lv.base + 3, z1);
		return a;
	}

	/** An opening through a wall, with no door: {@code h} high from the level's floor. */
	void arch(Level lv, Area into, int x0, int z0, int x1, int z1, int h) {
		carve(into, x0, lv.base, z0, x1, lv.base + h - 1, z1);
	}

	/** A door in a wall cell, facing the way given; wood is the door's wood ({@code oak}, {@code spruce}...). */
	void door(Level lv, Area into, int x, int z, String facing, String wood) {
		door(lv, into, x, z, facing, wood, "left");
	}

	void door(Level lv, Area into, int x, int z, String facing, String wood, String hinge) {
		carve(into, x, lv.base, z, x, lv.base + 1, z);
		// The hotel's own swinging doors (design 4b): mahogany for the guests, painted pine for the
		// staff, glazed for the spa.
		String kind = switch (wood) {
			case "dark_oak" -> "rauk:hotel_door";
			case "spruce" -> "rauk:service_door";
			case "bookcase" -> "rauk:bookcase_door";
			default -> "rauk:glass_door";
		};
		String block = kind + "[facing=" + facing + ",hinge=" + hinge + ",open=false,powered=false,half=";
		fittings.add(() -> {
			p.set(x, lv.base, z, block + "lower]");
			p.set(x, lv.base + 1, z, block + "upper]");
		});
	}

	/**
	 * Locks a door put in with {@link #door} (hotel design 4): to the staff keyring ("staff") or
	 * Wennberg's study key ("study").
	 */
	void lock(Level lv, int x, int z, String key) {
		fittings.add(() -> {
			for (int y = lv.base; y <= lv.base + 1; y++) {
				String door = p.get(x, y, z);
				if (!door.contains("_door[")) {
					throw new IllegalStateException("No door to lock at " + x + " " + y + " " + z);
				}
				p.set(x, y, z, door.replace("]", ",lock=" + key + "]"));
			}
		});
	}

	/**
	 * A pair of doors side by side, along x or z from (x, z), hinged at their outer edges so they
	 * open apart. A door's left is to the left of someone facing the way it faces.
	 */
	void doubleDoor(Level lv, Area into, int x, int z, String facing, String wood) {
		boolean alongX = facing.equals("north") || facing.equals("south");
		// The first door's left side is towards lower x (or z) when it faces north (or east).
		boolean firstLeft = facing.equals("north") || facing.equals("east");
		door(lv, into, x, z, facing, wood, firstLeft ? "left" : "right");
		door(lv, into, alongX ? x + 1 : x, alongX ? z : z + 1, facing, wood, firstLeft ? "right" : "left");
	}

	/** Puts masonry back, to be skinned: a landing, a gallery floor, the fill under a stair. */
	void solid(int x0, int y0, int z0, int x1, int y1, int z1) {
		for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					p.clear(x, y, z);
					p.set(x, y, z, SOLID);
				}
			}
		}
	}

	/** A stair block (its full id, {@code minecraft:stone_brick_stairs}), climbing the way it faces. */
	void stair(String block, int x, int y, int z, String facing, boolean wet) {
		fittings.add(() -> p.set(x, y, z, block + "[facing=" + facing + ",half=bottom,shape=straight,waterlogged=" + wet + "]"));
	}

	void later(int x0, int y0, int z0, int x1, int y1, int z1, String block) {
		fittings.add(() -> p.fill(x0, y0, z0, x1, y1, z1, block));
	}

	void mark(String name, int x, int y, int z) {
		p.marks.put(name, new Mark(name, x, y, z));
	}

	// --- the levels --------------------------------------------------------------------------------

	/** B2, the machinery: the generator, the lift pit, the flooded drainage tunnels. */
	private void b2() {
		Level lv = Level.B2;
		corridor("Service passage B2", lv, Style.SERVICE, 22, 10, 50, 11);
		Area gen = room("Generator and boiler room", lv, Style.MACHINE, Kind.HALL, 22, 13, 46, 30, 5);
		door(lv, gen, 30, 12, "south", "spruce");
		Area fuse = room("Fuse room", lv, Style.MACHINE, 48, 13, 54, 20);
		door(lv, fuse, 49, 12, "south", "spruce");
		// The drainage tunnels: west to the sea grate, east up under the Cabinet. Ankle-deep water.
		room("West drain", lv, Style.MACHINE, Kind.TUNNEL, 12, 1, 13, 30, 3);
		room("East drain", lv, Style.MACHINE, Kind.TUNNEL, 53, 1, 54, 12, 3);
		later(12, lv.base, 1, 13, lv.base, 30, WATER);
		later(53, lv.base, 1, 54, lv.base, 12, WATER);
		// Up a step out of the water into the generator room, and the east drain into the fuse room.
		space("Drain steps", lv, Style.SERVICE, Kind.TUNNEL, 14, lv.base + 1, 20, 21, lv.base + 3, 21);
		solid(14, lv.base, 20, 21, lv.base, 21);
		solid(53, lv.base, 13, 54, lv.base, 13);
		carve(fuse, 53, lv.base + 1, 13, 54, lv.base + 3, 13);
		// The sea grate at the end of the west drain.
		later(12, lv.base, 0, 13, lv.base + 2, 0, BARS);
	}

	/** B1, the service level: staff quarters, laundry, cold rooms, the spa and the Cabinet. */
	private void b1() {
		Level lv = Level.B1;
		corridor("Service corridor", lv, Style.SERVICE, 1, 21, 50, 23);
		corridor("Service passage B1", lv, Style.SERVICE, 22, 10, 50, 11);
		corridor("Back stairs passage", lv, Style.SERVICE, 48, 12, 50, 20);
		// North: the cold rooms (one is its larder), the porter's lodge, the wine cellar.
		Area coldA = room("Cold room", lv, Style.KITCHEN, 1, 1, 9, 19);
		Area coldB = room("Meat store", lv, Style.KITCHEN, 11, 1, 20, 19);
		door(lv, coldA, 5, 20, "north", "spruce");
		// The night porter locked it on what was inside (puzzle 2 has a fuse in there); the
		// keyring from the safe (puzzle 1) opens it.
		lock(lv, 5, 20, "staff");
		door(lv, coldB, 15, 20, "north", "spruce");
		Area lodge = room("Night porter's lodge", lv, Style.RUNDOWN, 22, 1, 28, 8);
		door(lv, lodge, 25, 9, "north", "spruce");
		Area wine = room("Wine cellar", lv, Style.MACHINE, Kind.ROOM, 22, 13, 46, 19, 4);
		door(lv, wine, 35, 20, "north", "spruce");
		// South: where the staff lived and worked.
		Area coal = room("Coal store", lv, Style.SERVICE, 1, 25, 8, 33);
		door(lv, coal, 5, 24, "south", "spruce");
		Area dorm = room("Staff dormitory", lv, Style.RUNDOWN, Kind.ROOM, 10, 25, 20, 46, 4);
		door(lv, dorm, 15, 24, "south", "spruce");
		Area wash = room("Staff washroom", lv, Style.KITCHEN, 1, 35, 8, 46);
		door(lv, wash, 9, 40, "west", "spruce");
		Area laundry = room("Laundry", lv, Style.KITCHEN, Kind.ROOM, 22, 25, 36, 35, 4);
		door(lv, laundry, 29, 24, "south", "spruce");
		lock(lv, 29, 24, "staff");
		// The soaking tank sunk in its floor, full of black water: a fuse lies at the bottom (puzzle 2).
		// (In the south-west corner: clear of the generator room under the rest of the floor, and of
		// the door through to housekeeping.)
		carve(laundry, 23, lv.base - 3, 33, 25, lv.base - 1, 35);
		// Tiled all round and underneath, a tiled rim in the floor; then the water.
		later(22, lv.base - 4, 32, 26, lv.base - 1, 36, "rauk:tile_wall_grimy");
		later(23, lv.base - 3, 33, 25, lv.base - 1, 35, WATER);
		later(24, lv.base - 3, 34, 24, lv.base - 3, 34, "rauk:fuse_lying[facing=north,waterlogged=true]");
		mark("laundry_fuse", 24, lv.base - 3, 34);
		Area linen = room("Housekeeping", lv, Style.RUNDOWN, 22, 37, 36, 46);
		door(lv, linen, 29, 36, "south", "spruce");
		Area mess = room("Staff mess", lv, Style.RUNDOWN, 38, 25, 50, 35);
		door(lv, mess, 44, 24, "south", "spruce");
		Area porter = room("Boot room", lv, Style.RUNDOWN, 38, 37, 50, 46);
		door(lv, porter, 44, 36, "south", "spruce");
		// East, through the spa: the pump passage, and behind it the Cabinet.
		Area vest = room("Spa vestibule", lv, Style.WET, Kind.CORRIDOR, 52, 21, 56, 23, 3);
		door(lv, vest, 51, 22, "east", "oak");
		Area spa = room("Spa", lv, Style.WET, Kind.HALL, 52, 25, 78, 46, 5);
		doubleDoor(lv, spa, 54, 24, "south", "oak");
		Area pump = room("Pump passage", lv, Style.WET, Kind.CORRIDOR, 58, 21, 78, 23, 3);
		door(lv, pump, 74, 24, "north", "spruce");
		Area cabinet = room("The Cabinet", lv, Style.DEN, Kind.HALL, 52, 1, 78, 19, 5);
		door(lv, cabinet, 66, 20, "north", "spruce");
		mark("rauk_den", 70, lv.base, 8);
		// The seawater pool, three deep, its water level with the floor.
		space("Pool", lv, Style.WET, Kind.ROOM, 58, lv.base - 3, 30, 72, lv.base - 1, 41);
		solid(57, lv.base - 4, 29, 73, lv.base - 4, 42);
		solid(57, lv.base - 3, 29, 73, lv.base - 1, 29);
		solid(57, lv.base - 3, 42, 73, lv.base - 1, 42);
		solid(57, lv.base - 3, 29, 57, lv.base - 1, 42);
		solid(73, lv.base - 3, 29, 73, lv.base - 1, 42);
		later(58, lv.base - 3, 30, 72, lv.base - 1, 41, WATER);
		// Its own way down from the Cabinet to the east drain.
		space("Cabinet drain", lv, Style.DEN, Kind.TUNNEL, 53, Level.B2.base + 3, 2, 54, lv.base - 1, 3);
		// The coal chute: the one way in (hotel design 2), from a hatch outside the west wall.
		chute();
		mark("dormitory_bunk", 12, lv.base, 27);
	}

	/**
	 * The coal chute: a hatch in the ground just outside the west wall, a ladder down a shaft one
	 * block wide (too narrow for Rauk), and a low passage through the wall into the coal store.
	 * It gives way behind you (HotelRules.collapseChute).
	 */
	private void chute() {
		int x = -3, z = 29, top = -1, bottom = Level.B1.base;
		p.fill(x - 1, bottom - 1, z - 1, 0, top, z + 1, FOUNDATION);
		space("Coal chute", Level.B1, Style.SERVICE, Kind.OUTSIDE, x, bottom, z, x, top, z);
		space("Chute passage", Level.B1, Style.SERVICE, Kind.TUNNEL, x + 1, bottom, z, 0, bottom + 1, z);
		later(x, bottom + 1, z, x, top - 1, z, "minecraft:ladder[facing=east,waterlogged=false]");
		later(x, top, z, x, top, z, "minecraft:spruce_trapdoor[facing=east,half=top,open=true,powered=false,waterlogged=false]");
		mark("chute_top", x, top, z);
		mark("chute_bottom", x, bottom, z);
	}

	/** The lobby floor: atrium, entrance hall, reception, bar, dining room, kitchen, ballroom. */
	private void l1() {
		Level lv = Level.L1;
		corridor("West corridor", lv, Style.LOBBY, 2, 21, 28, 23);
		corridor("East corridor", lv, Style.LOBBY, 51, 21, 78, 23);
		corridor("Service passage", lv, Style.SERVICE, 22, 10, 50, 11);
		Area dining = room("Dining room", lv, Style.GRAND, Kind.HALL, 1, 1, 20, 19, 5);
		doubleDoor(lv, dining, 10, 20, "north", "dark_oak");
		door(lv, dining, 21, 10, "west", "spruce");
		Area pantry = room("Pantry", lv, Style.KITCHEN, 22, 1, 28, 8);
		door(lv, pantry, 21, 4, "east", "spruce");
		door(lv, pantry, 25, 9, "north", "spruce");
		Area cloak = room("Cloakroom", lv, Style.COZY, 22, 13, 28, 19);
		door(lv, cloak, 25, 20, "north", "dark_oak");
		Area kitchen = room("Kitchen", lv, Style.KITCHEN, Kind.HALL, 1, 25, 13, 46, 4);
		doubleDoor(lv, kitchen, 6, 24, "south", "spruce");
		Area luggage = room("Luggage room", lv, Style.RUNDOWN, 15, 25, 19, 33);
		door(lv, luggage, 17, 24, "south", "spruce");
		Area phone = room("Writing room", lv, Style.COZY, 21, 25, 28, 32);
		door(lv, phone, 25, 24, "south", "dark_oak");
		Area office = room("Night clerk's office", lv, Style.RUNDOWN, 15, 35, 19, 46);
		room("Lobby lounge", lv, Style.LOBBY, Kind.HALL, 21, 34, 32, 46, 5);
		door(lv, office, 20, 40, "west", "dark_oak");
		Area hall = room("Garden hall", lv, Style.LOBBY, Kind.HALL, 34, 34, 45, 46, 5);
		arch(lv, hall, 33, 38, 33, 40, 4);
		// The vestibule through the thick old front wall to its doors, out onto the courtyard now.
		carve(hall, 38, lv.base, DEPTH - 1, 41, lv.base + 3, FOOT_Z1);
		Area bar = room("Bar", lv, Style.COZY_RED, Kind.HALL, 47, 34, 58, 46, 5);
		arch(lv, bar, 46, 38, 46, 40, 4);
		mark("garden_doors", 39, lv.base, FOOT_Z1);
		// East: the ballroom, two floors high, and the rooms off the east corridor.
		Area ballroom = room("Ballroom", lv, Style.BALLROOM, Kind.HALL, 52, 1, 78, 19, 12);
		doubleDoor(lv, ballroom, 60, 20, "north", "dark_oak");
		doubleDoor(lv, ballroom, 69, 20, "north", "dark_oak");
		door(lv, ballroom, 51, 10, "east", "spruce");
		Area band = room("Band room", lv, Style.RUNDOWN, 52, 25, 58, 32);
		door(lv, band, 55, 24, "south", "dark_oak");
		Area billiards = room("Billiard room", lv, Style.WRECKED, 60, 25, 78, 32);
		door(lv, billiards, 64, 24, "south", "dark_oak");
		Area smoking = room("Smoking room", lv, Style.WRECKED, Kind.ROOM, 60, 34, 78, 46, 4);
		door(lv, smoking, 69, 33, "south", "dark_oak");
		door(lv, smoking, 59, 40, "east", "dark_oak");
	}

	/**
	 * A guest floor (2 and 3): corridors in a loop round the atrium gallery, rooms along them, the
	 * library on 2 over the dining room, and on 3 the burned east wing over the ballroom and the
	 * servants' stair up to the attic.
	 */
	private void guestFloor(Level lv) {
		boolean upper = lv == Level.L3;
		String n = upper ? " 3" : " 2";
		Style west = upper ? Style.WORN : Style.COZY;
		Style east = upper ? Style.RUINED : Style.COZY;
		corridor("West corridor" + n, lv, west, 1, 21, 28, 23);
		corridor("East corridor" + n, lv, east, 51, 21, 78, 23);
		corridor("South corridor" + n, lv, upper ? Style.WORN : Style.COZY, 1, 34, 78, 36);
		corridor("West stair corridor" + n, lv, west, 1, 24, 3, 33);
		corridor("East stair corridor" + n, lv, east, 76, 24, 78, 33);
		corridor("Service passage" + n, lv, Style.SERVICE, 30, 10, 50, 11);
		int r = 1;
		// The rooms between the corridors, west and east of the atrium.
		for (int[] x : new int[][] {{5, 12}, {14, 20}, {22, 28}}) {
			Area a = room("Room " + lv.label + String.format("%02d", r++), lv, west, x[0], 25, x[1], 32);
			door(lv, a, (x[0] + x[1]) / 2, 24, "south", "dark_oak");
		}
		for (int[] x : new int[][] {{52, 58}, {60, 66}, {68, 74}}) {
			Area a = room("Room " + lv.label + String.format("%02d", r++), lv, east, x[0], 25, x[1], 32);
			door(lv, a, (x[0] + x[1]) / 2, 24, "south", "dark_oak");
		}
		// The front rooms, over the reception, entrance hall and bar.
		for (int[] x : new int[][] {{1, 6}, {12, 17}, {19, 23}, {25, 31}, {48, 54}, {56, 60}, {62, 67}, {73, 78}}) {
			Style st = x[0] > 40 ? east : west;
			Area a = room("Room " + lv.label + String.format("%02d", r++), lv, st, x[0], 38, x[1], 46);
			door(lv, a, (x[0] + x[1]) / 2, 37, "north", "dark_oak");
		}
		Area suite = room(upper ? "Honeymoon suite" : "Bridal suite", lv, upper ? Style.WORN : Style.COZY_RED, 33, 38, 46, 46);
		doubleDoor(lv, suite, 39, 37, "north", "dark_oak");
		if (!upper) {
			Area library = room("Library", lv, Style.COZY, Kind.HALL, 1, 1, 20, 19, 5);
			doubleDoor(lv, library, 10, 20, "north", "dark_oak");
			Area reading = room("Reading room", lv, Style.COZY, 22, 10, 28, 19);
			door(lv, reading, 25, 20, "north", "dark_oak");
			arch(lv, reading, 21, 15, 21, 17, 3);
			// Wennberg's hidden study, behind the library shelves (puzzle 4 opens it).
			Area study = room("Wennberg's hidden study", lv, Style.PENTHOUSE, Kind.SECRET, 22, 1, 28, 8, 4);
			// The way in: a section of the library's shelves, two bookcase doors flush with the rest
			// that only the catalogue opens, and the passage through the wall behind them.
			carve(study, 20, lv.base, 3, 21, lv.base + 1, 4);
			doubleDoor(lv, study, 20, 3, "east", "bookcase");
			lock(lv, 20, 3, "secret");
			lock(lv, 20, 4, "secret");
			mark("hidden_study_door", 20, lv.base, 3);
		} else {
			for (int[] x : new int[][] {{1, 6}, {8, 13}, {15, 20}}) {
				suite(lv, west, "Suite " + lv.label + String.format("%02d", r++), x[0], x[1]);
			}
			Area a = room("Room " + lv.label + String.format("%02d", r++), lv, west, 22, 1, 28, 19);
			door(lv, a, 25, 20, "north", "dark_oak");
			for (int[] x : new int[][] {{52, 57}, {59, 64}, {66, 71}, {73, 78}}) {
				String name = x[0] == 66 ? "Bandleader's room" : "Suite " + lv.label + String.format("%02d", r++);
				suite(lv, east, name, x[0], x[1]);
			}
			// Burned through: holes from 3 down to 2, and on down into the billiard room.
			Level l2 = Level.L2, l1 = Level.L1;
			space("Burned through to 2", lv, Style.RUINED, Kind.TUNNEL, 62, l2.base + 4, 28, 63, lv.base - 1, 29);
			space("Burned through to 1", l2, Style.RUINED, Kind.TUNNEL, 64, l1.base + 4, 30, 65, l2.base - 1, 31);
			space("Burned-through corridor", lv, Style.RUINED, Kind.TUNNEL, 70, l2.base + 3, 22, 71, lv.base - 1, 23);
		}
	}

	/** A two-room suite on 3, its bedroom on the sea side. */
	private void suite(Level lv, Style style, String name, int x0, int x1) {
		Area parlour = room(name, lv, style, x0, 11, x1, 19);
		Area bed = room(name + " bedroom", lv, style, x0, 1, x1, 9);
		door(lv, parlour, (x0 + x1) / 2, 20, "north", "dark_oak");
		door(lv, bed, (x0 + x1) / 2, 10, "north", "dark_oak");
	}

	/**
	 * The servants' stair from 3 up to the attic: narrow wooden flights switching back round an open
	 * middle, at the west end of the front.
	 */
	private void servantsStair() {
		Level lv = Level.L3, up = Level.L4;
		int b = lv.base;
		Area well = space("Servants' stair", lv, Style.SERVICE, Kind.STAIRS, 2, b, 38, 7, up.base + 3, 45);
		door(lv, well, 3, 37, "north", "spruce");
		door(up, well, 3, 37, "north", "spruce");
		// The half landing at the back, then the flights: up away from the door, and back up to it.
		solid(2, b + 2, 43, 7, b + 2, 45);
		for (int i = 0; i < 3; i++) {
			stair("minecraft:dark_oak_stairs", 2, b + i, 40 + i, "south", false);
			stair("minecraft:dark_oak_stairs", 3, b + i, 40 + i, "south", false);
			solid(2, b + i - 1, 40 + i, 3, b + i - 1, 40 + i);
			stair("minecraft:dark_oak_stairs", 6, b + 3 + i, 42 - i, "north", false);
			stair("minecraft:dark_oak_stairs", 7, b + 3 + i, 42 - i, "north", false);
		}
		solid(2, up.base - 1, 38, 7, up.base - 1, 39);
	}

	/**
	 * The attic under the mansard (hotel design 11.4): the servants' rooms in the west, lumber and
	 * the water tanks in the east (burned through from below), the old nursery behind the central
	 * gable, and a gallery round the top of the atrium. The penthouse in the middle is walled off.
	 */
	private void attic() {
		Level lv = Level.L4;
		corridor("Attic corridor west", lv, Style.RUNDOWN, 2, 21, 28, 23);
		corridor("Attic corridor east", lv, Style.RUINED, 51, 21, 77, 23);
		corridor("Attic corridor south", lv, Style.RUNDOWN, 2, 34, 77, 36);
		corridor("Attic stair corridor west", lv, Style.RUNDOWN, 2, 24, 4, 33);
		corridor("Attic stair corridor east", lv, Style.RUINED, 75, 24, 77, 33);
		Area dorm = room("Servants' dormitory", lv, Style.RUNDOWN, 2, 2, 20, 19);
		door(lv, dorm, 10, 20, "north", "spruce");
		Area box = room("Box room", lv, Style.RUNDOWN, 22, 14, 28, 19);
		door(lv, box, 25, 20, "north", "spruce");
		int r = 1;
		for (int[] x : new int[][] {{6, 12}, {14, 20}, {22, 28}}) {
			Area a = room("Servant's room " + r++, lv, Style.RUNDOWN, x[0], 25, x[1], 32);
			door(lv, a, (x[0] + x[1]) / 2, 24, "south", "spruce");
		}
		for (int[] x : new int[][] {{52, 58}, {60, 66}, {68, 73}}) {
			Area a = room("Attic room " + r++, lv, Style.RUINED, x[0], 25, x[1], 32);
			door(lv, a, (x[0] + x[1]) / 2, 24, "south", "spruce");
		}
		for (int[] x : new int[][] {{2, 6}, {12, 17}, {19, 23}, {25, 31}, {48, 54}, {56, 60}, {62, 67}, {73, 77}}) {
			Area a = room("Attic room " + r++, lv, x[0] > 40 ? Style.RUINED : Style.RUNDOWN, x[0], 38, x[1], 45);
			door(lv, a, (x[0] + x[1]) / 2, 37, "north", "spruce");
		}
		Area nursery = room("Old nursery", lv, Style.WORN, 33, 38, 46, 45);
		doubleDoor(lv, nursery, 39, 37, "north", "dark_oak");
		Area tanks = room("Water tank room", lv, Style.SERVICE, 51, 14, 56, 19);
		door(lv, tanks, 54, 20, "north", "spruce");
		Area lumber = room("Lumber room", lv, Style.RUINED, Kind.HALL, 59, 2, 77, 19, 4);
		door(lv, lumber, 66, 20, "north", "spruce");
		// The fire went up through the east wing into the attic.
		space("Burned through to the attic", lv, Style.RUINED, Kind.TUNNEL, 62, Level.L3.base + 4, 5, 63, lv.base - 1, 6);
	}

	/** Wennberg's penthouse on top of the staff core, and his glasshouse on its roof. */
	private void penthouse() {
		Level lv = Level.L4;
		room("Penthouse lift lobby", lv, Style.PENTHOUSE, Kind.ROOM, 38, 5, 42, 11, 5);
		Area living = room("Wennberg's drawing room", lv, Style.PENTHOUSE, Kind.HALL, 23, 1, 36, 11, 5);
		door(lv, living, 37, 8, "east", "dark_oak");
		Area study = room("Wennberg's study", lv, Style.PENTHOUSE, Kind.ROOM, 44, 1, 50, 11, 5);
		door(lv, study, 43, 8, "west", "dark_oak");
		// Its key went down to the ballroom with the bandleader (puzzle 3).
		lock(lv, 43, 8, "study");
		Area bed = room("Wennberg's bedroom", lv, Style.PENTHOUSE, Kind.ROOM, 52, 7, 56, 11, 5);
		door(lv, bed, 51, 9, "west", "dark_oak");
		// The lockdown control room, behind the stopped clock (puzzle 5 opens it): a panel door in the
		// wall the clock stands against, locked to no key.
		Area control = room("Lockdown control room", lv, Style.SERVICE, Kind.SECRET, 52, 1, 56, 5, 5);
		door(lv, control, 51, 3, "east", "dark_oak");
		lock(lv, 51, 3, "secret");
		mark("control_room_door", 51, lv.base, 3);
		// Up a ladder through the ceiling to the glasshouse.
		Level r = Level.R;
		Area glass = room("Glasshouse observatory", r, Style.PENTHOUSE, Kind.HALL, 23, 1, 36, 10, 5);
		carve(glass, 24, lv.base + 5, 1, 24, r.base - 1, 1);
		later(24, lv.base, 1, 24, r.base - 1, 1, "minecraft:ladder[facing=south,waterlogged=false]");
		Area machine = room("Lift machine room", r, Style.MACHINE, Kind.ROOM, 38, 1, 42, 4, 4);
		door(r, machine, 37, 2, "east", "spruce");
		fittings.add(() -> {
			// The glasshouse: glass walls and roof on a frame of brass.
			for (int y = r.base; y <= r.base + 5; y++) {
				for (int z = 0; z <= 11; z++) {
					for (int x = 22; x <= 37; x++) {
						if (p.isAir(x, y, z) || p.get(x, y, z).contains("door") || p.get(x, y, z).contains("ladder")) {
							continue;
						}
						boolean edgeX = x == 22 || x == 37, edgeZ = z == 0 || z == 11, top = y == r.base + 5;
						boolean frame = (edgeX && edgeZ) || (top && (edgeX || edgeZ)) || (!top && (x - 22) % 5 == 0 && edgeZ)
								|| (!top && z % 5 == 0 && edgeX) || (top && (x - 22) % 5 == 0);
						p.set(x, y, z, frame ? BRASS : GLASS);
					}
				}
			}
		});
	}

	/**
	 * The staff core, on every level from B2 to 3 (the lift goes on to 4): the service stairs,
	 * switching back twice a floor round an open middle Rauk climbs, and the service lift.
	 */
	private void staffCore() {
		Area well = space("Service stairs", Level.B2, Style.SERVICE, Kind.STAIRS, 31, Level.B2.base, 1, 36, Level.L3.base + 3, 8);
		Area shaft = space("Service lift shaft", Level.B2, Style.SERVICE, Kind.LIFT, 39, Level.B2.base, 1, 41, Level.R.base - 1, 3);
		for (Level lv : Level.values()) {
			if (lv == Level.R) {
				continue;
			}
			if (lv != Level.L4) {
				int b = lv.base;
				// The landing at this floor, and the door out of it.
				if (lv != Level.B2) {
					solid(31, b - 1, 7, 36, b - 1, 8);
				}
				door(lv, well, 32, 9, "south", "spruce");
				Area lobby = room("Lift lobby " + lv.label, lv, Style.SERVICE, Kind.ROOM, 38, 5, 42, 8, 4);
				door(lv, lobby, 40, 9, "south", "spruce");
				if (lv != Level.L3) {
					boolean wet = lv == Level.B2;
					// Up to the half landing on the sea side, then back up to the next floor.
					solid(31, b + 2, 1, 36, b + 2, 3);
					for (int i = 0; i < 3; i++) {
						stair("minecraft:stone_brick_stairs", 31, b + i, 6 - i, "north", wet);
						stair("minecraft:stone_brick_stairs", 32, b + i, 6 - i, "north", wet);
						solid(31, b + i - 1, 6 - i, 32, b + i - 1, 6 - i);
					}
					// Between 1 and 2 the upper flight is gone.
					if (lv != Level.L1) {
						for (int i = 0; i < 3; i++) {
							stair("minecraft:stone_brick_stairs", 35, b + 3 + i, 4 + i, "south", wet);
							stair("minecraft:stone_brick_stairs", 36, b + 3 + i, 4 + i, "south", wet);
						}
					}
				}
			}
			// The lift doors on every floor it stops at.
			carve(shaft, 40, lv.base, 4, 40, lv.base + 1, 4);
		}
		// The bottom of the stairs is under water, up to just below the B1 landing.
		fittings.add(() -> {
			for (int y = Level.B2.base; y <= Level.B1.base - 2; y++) {
				for (int z = 1; z <= 8; z++) {
					for (int x = 31; x <= 36; x++) {
						if (p.isAir(x, y, z)) {
							p.set(x, y, z, WATER);
						}
					}
				}
			}
		});
		// Linen rooms beside the lift lobby, and the staff door out to the atrium on 1 to 3.
		for (Level lv : new Level[] {Level.B1, Level.L1, Level.L2, Level.L3}) {
			Area linen = room((lv == Level.B1 ? "Electrical room" : "Linen room ") + (lv == Level.B1 ? "" : lv.label), lv,
					lv == Level.B1 ? Style.MACHINE : Style.RUNDOWN, Kind.ROOM, 44, 1, 49, 8, 4);
			door(lv, linen, 46, 9, "south", "spruce");
		}
		for (Level lv : new Level[] {Level.L1, Level.L2, Level.L3}) {
			door(lv, p.owner(40, lv.base, 10), 39, 12, "south", "spruce");
		}
	}

	/**
	 * The atrium (hotel design 3.4 and 11): marble, four floors high under a glass roof, galleries
	 * round it on 2, 3 and the attic, the grand staircase up its middle to the gallery on 2 (the
	 * flight on up to 3 fell in the fire), marble columns at the corners of the open middle, and the
	 * guest lift in its iron lattice cage.
	 */
	private void atrium() {
		Level l1 = Level.L1, l2 = Level.L2, l3 = Level.L3, l4 = Level.L4;
		// One space, but a space a floor as far as getting about goes: the open middle doesn't join
		// the galleries for anything that can't fly.
		Area atrium = space("Atrium", l1, Style.LOBBY, Kind.HALL, 30, l1.base, 13, 49, l2.base - 1, 32);
		Area gallery2 = space("Atrium gallery 2", l2, Style.LOBBY, Kind.HALL, 30, l2.base, 13, 49, l3.base - 1, 32);
		Area gallery3 = space("Atrium gallery 3", l3, Style.LOBBY, Kind.HALL, 30, l3.base, 13, 49, l4.base - 1, 32);
		Area gallery4 = space("Atrium gallery 4", l4, Style.LOBBY, Kind.HALL, 30, l4.base, 13, 49, Level.R.base - 2, 32);
		Map<Level, Area> floors = Map.of(l1, atrium, l2, gallery2, l3, gallery3, l4, gallery4);
		// The galleries: a ring three wide round the open middle, on 2, 3 and 4.
		for (Level lv : new Level[] {l2, l3, l4}) {
			int y = lv.base - 1;
			for (int z = 13; z <= 32; z++) {
				for (int x = 30; x <= 49; x++) {
					if (x < 33 || x > 46 || z < 16 || z > 29) {
						solid(x, y, z, x, y, z);
					}
				}
			}
		}
		// Arches out to the corridors each side, the entrance hall, reception, the bar.
		for (Level lv : new Level[] {l1, l2, l3, l4}) {
			arch(lv, floors.get(lv), 29, 21, 29, 23, 3);
			arch(lv, floors.get(lv), 50, 21, 50, 23, 3);
		}
		arch(l1, atrium, 37, 33, 42, 33, 5);
		arch(l1, atrium, 30, 33, 32, 33, 4);
		arch(l1, atrium, 47, 33, 49, 33, 4);
		doubleDoor(l2, gallery2, 39, 33, "south", "dark_oak");
		doubleDoor(l3, gallery3, 39, 33, "south", "dark_oak");
		doubleDoor(l4, gallery4, 39, 33, "south", "dark_oak");
		// The grand staircase up the middle to the gallery on 2: a red runner up cream marble.
		for (int i = 0; i < 7; i++) {
			int z = 22 - i;
			for (int x = 38; x <= 41; x++) {
				stair(x == 39 || x == 40 ? "rauk:carpeted_marble_stairs" : "rauk:marble_stairs", x, l1.base + i, z, "north", false);
			}
			if (i > 0) {
				solid(38, l1.base, z, 41, l1.base + i - 1, z);
			}
		}
		// From 2 to 3 up the west gallery: only its first steps are left, under the hole it fell through.
		for (int i = 0; i < 2; i++) {
			int z = 29 - i;
			stair("rauk:marble_stairs", 30, l2.base + i, z, "north", false);
			stair("rauk:marble_stairs", 31, l2.base + i, z, "north", false);
			if (i > 0) {
				solid(30, l2.base, z, 31, l2.base + i - 1, z);
			}
		}
		carve(gallery3, 30, l3.base - 1, 24, 31, l3.base - 1, 29);
		// Iron railings round the open middle, and round the hole the stair went up through.
		for (Level lv : new Level[] {l2, l3, l4}) {
			int y = lv.base;
			for (int x = 32; x <= 47; x++) {
				for (int z = 15; z <= 30; z++) {
					boolean edge = x == 32 || x == 47 || z == 15 || z == 30;
					boolean stairTop = lv == l2 && z == 15 && x >= 38 && x <= 41;
					boolean lift = x == 47 && z >= 20 && z <= 24;
					if (edge && !stairTop && !lift) {
						later(x, y, z, x, y, z, RAILING);
					}
				}
			}
		}
		later(30, l3.base, 30, 31, l3.base, 30, RAILING);
		// Marble columns at the corners of the open middle, floor to glass roof; Rauk climbs these.
		for (int[] c : new int[][] {{32, 15}, {47, 15}, {32, 30}, {47, 30}}) {
			int top = Level.R.base - 2;
			fittings.add(() -> {
				for (int y = l1.base; y <= top; y++) {
					String part = y == l1.base ? "base" : y == top ? "capital" : "shaft";
					p.set(c[0], y, c[1], "rauk:marble_column[part=" + part + "]");
				}
			});
		}
		// The guest lift: a cage of iron lattice round a shaft through the four floors, banded with
		// brass at each, a gate on each of the three it stops at.
		Area lift = space("Guest lift", l1, Style.LOBBY, Kind.LIFT, 43, l1.base, 21, 45, Level.R.base + 4, 23);
		fittings.add(() -> {
			for (int y = l1.base; y <= Level.R.base - 2; y++) {
				for (int z = 20; z <= 24; z++) {
					for (int x = 42; x <= 46; x++) {
						boolean edge = x == 42 || x == 46 || z == 20 || z == 24;
						boolean gate = x == 46 && z == 22 && (y - l1.base) % 7 <= 1 && y < l4.base;
						boolean band = (y - l1.base) % 7 == 6;
						if (edge && !gate) {
							p.set(x, y, z, band ? BRASS : LATTICE);
						}
					}
				}
			}
		});
		// Its winding gear in a hut on the glass roof.
		solid(42, Level.R.base - 1, 20, 46, Level.R.base + 5, 24);
		carve(lift, 43, Level.R.base - 1, 21, 45, Level.R.base + 4, 23);
		// The glass roof over it all, on iron ribs.
		fittings.add(() -> {
			int y = Level.R.base - 1;
			for (int z = 13; z <= 32; z++) {
				for (int x = 30; x <= 49; x++) {
					boolean hut = x >= 42 && x <= 46 && z >= 20 && z <= 24;
					if (!hut) {
						p.set(x, y, z, (x - 30) % 5 == 4 || (z - 13) % 5 == 4 ? "minecraft:iron_block" : GLASS);
					}
				}
			}
		});
	}

	/**
	 * The lifts (hotel design 3.2, {@link LiftCars}): a gate onto each landing, a call button
	 * beside it, and the car standing at its first floor, the shaft open above and below it.
	 */
	private void lifts() {
		fittings.add(() -> {
			for (LiftCars.Lift lift : LiftCars.ALL) {
				for (Level lv : lift.floors()) {
					String gate = "rauk:lift_door[facing=" + lift.gateFacing() + ",hinge=left,open=false,powered=false,half=";
					p.set(lift.gateX(), lv.base, lift.gateZ(), gate + "lower]");
					p.set(lift.gateX(), lv.base + 1, lift.gateZ(), gate + "upper]");
					p.set(lift.callX(), lv.base + 1, lift.callZ(), "rauk:lift_button[facing=" + lift.callFacing() + "]");
				}
				for (LiftCars.Part part : LiftCars.car(lift, false)) {
					int y = lift.home().base + part.dy();
					p.underCar.put(HotelPlan.cellKey(part.x(), y, part.z()), p.get(part.x(), y, part.z()));
					p.set(part.x(), y, part.z(), part.block());
				}
			}
		});
	}

	// --- windows -------------------------------------------------------------------------------------

	private static final int[][] SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};

	private boolean masonry(int x, int y, int z) {
		return p.get(x, y, z).equals(SOLID);
	}

	/**
	 * Windows all round the outside, behind iron shutters (the lockdown): one every four blocks
	 * wherever a room or corridor lies behind the wall, cut through the wall's thickness so each
	 * sits in a deep reveal. Up in the mansard they are dormers, standing out from the slope.
	 */
	private void windows() {
		for (Level lv : new Level[] {Level.L1, Level.L2, Level.L3, Level.L4}) {
			int y = lv.base + 1;
			for (int z = HotelPlan.Z0; z < HotelPlan.Z0 + HotelPlan.SZ; z++) {
				for (int x = HotelPlan.X0; x < HotelPlan.X0 + HotelPlan.SX; x++) {
					for (int[] d : SIDES) {
						window(lv, x, y, z, d);
					}
				}
			}
		}
		// The central gable's great window, over the front, into the old nursery.
		Area nursery = p.owner(39, Level.L4.base, 40);
		carve(nursery, 38, Level.L4.base, DEPTH - 2, 41, Level.L4.base + 3, FOOT_Z1 - 1);
		windows.add(new Window(38, FOOT_Z1, Level.L4.base, Level.L4.base + 3, new int[] {0, 0, 1}, null));
	}

	private void window(Level lv, int x, int y, int z, int[] out) {
		if (!masonry(x, y, z) || p.at(x + out[0], y, z + out[2]) != 0) {
			return;
		}
		if (lv == Level.L4 && z >= DEPTH) {
			return;
		}
		int along = out[0] != 0 ? z : x;
		if (Math.floorMod(along, 4) != 2) {
			return;
		}
		// Not at a corner: the wall goes on either side, facing out the same way.
		int ax = out[2] != 0 ? 1 : 0, az = out[0] != 0 ? 1 : 0;
		for (int s : new int[] {-1, 1}) {
			if (!masonry(x + ax * s, y, z + az * s) || p.at(x + ax * s + out[0], y, z + az * s + out[2]) != 0) {
				return;
			}
		}
		Area inside = null;
		int depth = 0;
		for (int k = 1; k <= 5 && inside == null; k++) {
			int ix = x - out[0] * k, iz = z - out[2] * k;
			if (p.isAir(ix, y, iz)) {
				inside = p.owner(ix, y, iz);
				depth = k;
			} else if (!masonry(ix, y, iz)) {
				return;
			}
		}
		if (inside == null || inside.kind() != Kind.ROOM && inside.kind() != Kind.HALL && inside.kind() != Kind.CORRIDOR
				|| inside.name().equals("Cinema")) {
			return;
		}
		int height = lv == Level.L4 ? 2 : inside.y1() - lv.base >= 3 ? 3 : 2;
		int y0 = lv == Level.L4 ? y + 1 : y;
		boolean dormer = lv == Level.L4;
		for (int yy = y0; yy < y0 + height; yy++) {
			if (!p.isAir(x - out[0] * depth, yy, z - out[2] * depth)) {
				return;
			}
		}
		for (int yy = y0; yy < y0 + height; yy++) {
			for (int k = dormer ? 0 : 1; k < depth; k++) {
				carve(inside, x - out[0] * k, yy, z - out[2] * k, x - out[0] * k, yy, z - out[2] * k);
			}
		}
		windows.add(new Window(dormer ? x + out[0] : x, dormer ? z + out[2] : z, y0, y0 + height - 1, out, lv));
	}

	// --- skinning -----------------------------------------------------------------------------------

	/** What each bit of leftover masonry is made of, from what it faces. */
	private void skin() {
		List<int[]> todo = new ArrayList<>();
		for (int y = HotelPlan.Y0; y < HotelPlan.Y0 + HotelPlan.SY; y++) {
			for (int z = HotelPlan.Z0; z < HotelPlan.Z0 + HotelPlan.SZ; z++) {
				for (int x = HotelPlan.X0; x < HotelPlan.X0 + HotelPlan.SX; x++) {
					if (masonry(x, y, z)) {
						todo.add(new int[] {x, y, z});
					}
				}
			}
		}
		List<String> skins = new ArrayList<>(todo.size());
		for (int[] c : todo) {
			skins.add(skinFor(c[0], c[1], c[2]));
		}
		for (int i = 0; i < todo.size(); i++) {
			int[] c = todo.get(i);
			p.set(c[0], c[1], c[2], skins.get(i));
		}
	}

	private String skinFor(int x, int y, int z) {
		if (p.at(x, y + 1, z) == 0) {
			return ROOF;
		}
		for (int[] d : SIDES) {
			if (p.at(x + d[0], y, z + d[2]) == 0) {
				return outside(x, y, z);
			}
		}
		Area above = p.owner(x, y + 1, z);
		if (above != null && above.kind() != Kind.CRAWL) {
			return floor(above.style(), x, z);
		}
		// A ceiling over a room; but over a doorway or a window's recess, a lintel: wall.
		Area below = p.owner(x, y - 1, z);
		if (below != null && below.kind() != Kind.CRAWL && below.contains(x, y - 1, z)) {
			return below.style().ceiling;
		}
		Area best = null;
		for (int[] d : SIDES) {
			Area a = p.owner(x + d[0], y, z + d[2]);
			if (a != null && (best == null || rank(a) > rank(best))) {
				best = a;
			}
		}
		if (p.at(x, y - 1, z) == 0) {
			return FOUNDATION;
		}
		if (best == null || best.kind() == Kind.CRAWL) {
			return FILLER;
		}
		// Panelling along the bottom of the wall, wallpaper (or tiles, plaster) above it.
		return y == best.y0() ? best.style().wainscot : best.style().wall;
	}

	/** A floor: the lobby's marble in a cream and black chequer, the rest as the style has it. */
	private static String floor(Style style, int x, int z) {
		if (style == Style.LOBBY && Math.floorMod(x + z, 2) == 1) {
			return "rauk:marble_black";
		}
		return style.floor;
	}

	/**
	 * The outside of the walls (hotel design 11.1): rusticated limestone below, blue stucco above
	 * (streaked in places) with limestone bands at the floors, slate up the mansard, foundations
	 * underground.
	 */
	private String outside(int x, int y, int z) {
		if (y < -1) {
			return FOUNDATION;
		}
		if (y <= Level.L1.base + 5) {
			return RUSTICATED;
		}
		if (y == Level.L2.base - 1 || y == Level.L3.base - 1 || y == Level.L4.base - 1) {
			return LIMESTONE;
		}
		if (y < Level.L4.base) {
			int streak = Math.floorMod(x * 7 + z * 13, 10);
			return streak < 3 ? ASHLAR_STAINED : ASHLAR;
		}
		if (y < Level.R.base - 1) {
			return z >= DEPTH && z <= FOOT_Z1 && x >= 32 && x <= 47 ? ASHLAR : SLATE;
		}
		return LIMESTONE;
	}

	/**
	 * Whose wall a wall is when it's between two spaces: the grander side's (marble over panelling
	 * over the staff's plaster), and between two of a kind the corridor's, so corridors look the same
	 * all along.
	 */
	private static int rank(Area a) {
		int style = switch (a.style()) {
			case LOBBY -> 12;
			case BALLROOM -> 11;
			case GRAND -> 10;
			case PENTHOUSE -> 9;
			case COZY_RED -> 8;
			case COZY -> 7;
			case WORN -> 6;
			case WRECKED -> 5;
			case RUINED -> 4;
			case WET -> 3;
			case KITCHEN -> 3;
			case DEN -> 3;
			case RUNDOWN -> 2;
			case SERVICE, MACHINE -> 1;
			case HIDDEN -> 0;
		};
		int kind = switch (a.kind()) {
			case CORRIDOR -> 6;
			case HALL -> 5;
			case ROOM, SECRET -> 4;
			case STAIRS, LIFT -> 3;
			case TUNNEL, OUTSIDE -> 2;
			case CRAWL -> 0;
		};
		return a.kind() == Kind.CRAWL ? 0 : style * 10 + kind;
	}

	// --- outside --------------------------------------------------------------------------------------

	private static String facing(int[] d) {
		return d[0] > 0 ? "east" : d[0] < 0 ? "west" : d[2] > 0 ? "south" : "north";
	}

	/** Which way out a wall cell faces, if it's on the outside of the walls below the roof; else null. */
	private int[] faceOut(int x, int y, int z) {
		if (p.at(x, y, z) == 0 || p.isAir(x, y, z)) {
			return null;
		}
		for (int[] d : SIDES) {
			if (p.at(x + d[0], y, z + d[2]) == 0) {
				return d;
			}
		}
		return null;
	}

	/** How many ways out a wall cell faces: two at an outside corner. */
	private int outs(int x, int y, int z) {
		int n = 0;
		for (int[] d : SIDES) {
			if (p.at(x + d[0], y, z + d[2]) == 0) {
				n++;
			}
		}
		return n;
	}

	private void setIfEmpty(int x, int y, int z, String block) {
		if (p.at(x, y, z) == 0) {
			p.set(x, y, z, block);
		}
	}

	private boolean onPavilionFront(int x, int z) {
		if (z != FOOT_Z1) {
			return false;
		}
		for (int[] pv : PAVILIONS) {
			if (x >= pv[0] && x <= pv[1]) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The ornament outside (hotel design 11.1): string course and cornice, quoins at the corners,
	 * pilasters up the pavilions, the windows framed in limestone (keystones on the ground floor,
	 * hoods above), the balustrade along the foot of the mansard and its dormers, the curb and iron
	 * cresting round the top, the central gable, the portico and balconies over the front doors.
	 */
	private void exterior() {
		int ledge = Level.L4.base - 1, mansardTop = Level.R.base - 1;
		// First find every outside face as the walls stand, then dress them: what's added outside
		// mustn't itself be taken for a wall.
		record Face(int x, int y, int z, int[] d, int outs, boolean nearCorner) {
		}
		List<Face> walls = new ArrayList<>(), ledges = new ArrayList<>(), curbs = new ArrayList<>();
		for (int z = HotelPlan.Z0; z < HotelPlan.Z0 + HotelPlan.SZ; z++) {
			for (int x = HotelPlan.X0; x < HotelPlan.X0 + HotelPlan.SX; x++) {
				for (int y = Level.L1.base; y < ledge; y++) {
					int[] d = faceOut(x, y, z);
					if (d != null && !(HotelWings.slanted(x, z) && outs(x, y, z) >= 2)) {
						walls.add(new Face(x, y, z, d, outs(x, y, z), cornerNear(x, y, z, d)));
					}
				}
				int[] d = faceOut(x, ledge, z);
				if (d != null && p.at(x, ledge + 1, z) == 0 && !(HotelWings.slanted(x, z) && outs(x, ledge, z) >= 2)) {
					ledges.add(new Face(x, ledge, z, d, outs(x, ledge, z), false));
				}
				int[] top = faceOut(x, mansardTop, z);
				if (top != null && p.at(x, mansardTop + 1, z) == 0 && !(HotelWings.slanted(x, z) && outs(x, mansardTop, z) >= 2)) {
					curbs.add(new Face(x, mansardTop, z, top, 1, false));
				}
			}
		}
		for (Face f : walls) {
			int x = f.x(), y = f.y(), z = f.z();
			int[] d = f.d();
			int along = d[0] != 0 ? z : x;
			// The string course over the ground floor, and the cornice under the roof.
			if (y == Level.L2.base - 1 || y == ledge - 1) {
				setIfEmpty(x + d[0], y, z + d[2], "rauk:limestone_cornice[facing=" + facing(d) + "]");
			}
			// Quoins: alternately long and short stones up each outside corner.
			if (f.outs() >= 2 && y >= Level.L2.base) {
				p.set(x, y, z, LIMESTONE);
			} else if (y >= Level.L2.base && Math.floorMod(y, 4) < 2 && f.nearCorner()) {
				p.set(x, y, z, LIMESTONE);
			}
			// Pilasters up the second and third floors: every four along the pavilion fronts, every
			// eight elsewhere, between the windows.
			boolean pilaster = onPavilionFront(x, z) ? Math.floorMod(along, 4) == 0 : Math.floorMod(along, 8) == 0;
			if (pilaster && f.outs() == 1 && y >= Level.L2.base && y <= ledge - 2 && p.at(x + d[0], y, z + d[2]) == 0) {
				String part = y == Level.L2.base ? "base" : y == ledge - 2 ? "capital" : "shaft";
				p.set(x + d[0], y, z + d[2], "rauk:limestone_pilaster[facing=" + facing(d) + ",part=" + part + "]");
			}
		}
		// The ledge at the foot of the mansard: limestone, with a balustrade along it.
		for (Face f : ledges) {
			p.set(f.x(), ledge, f.z(), LIMESTONE);
			String axis = f.d()[0] != 0 ? "z" : "x";
			p.set(f.x(), ledge + 1, f.z(), f.outs() >= 2 ? LIMESTONE : "rauk:limestone_balustrade[axis=" + axis + "]");
		}
		// The curb at the top of the mansard, and iron cresting along the roof behind it.
		for (Face f : curbs) {
			int[] top = f.d();
			String inward = facing(new int[] {-top[0], 0, -top[2]});
			p.set(f.x(), mansardTop, f.z(), "rauk:slate_shingle_stairs[facing=" + inward + ",half=bottom,shape=straight,waterlogged=false]");
			setIfEmpty(f.x() - top[0], mansardTop + 1, f.z() - top[2], RAILING);
		}
		// The windows' frames, and the dormers.
		for (Window w : windows) {
			frame(w);
		}
		gable();
		portico();
	}

	/** Whether a wall cell is beside an outside corner along its face (the short quoin stones). */
	private boolean cornerNear(int x, int y, int z, int[] d) {
		int ax = d[2] != 0 ? 1 : 0, az = d[0] != 0 ? 1 : 0;
		return outs(x + ax, y, z + az) >= 2 || outs(x - ax, y, z - az) >= 2;
	}

	/**
	 * A window's frame: limestone either side and below, a keystone over it on the ground floor and
	 * a cornice hood over it above; up in the mansard, a dormer of limestone with a slate gable.
	 */
	private void frame(Window w) {
		int[] d = w.out();
		String face = facing(d);
		int ax = d[2] != 0 ? 1 : 0, az = d[0] != 0 ? 1 : 0;
		for (int y = w.y0(); y <= w.y1(); y++) {
			p.set(w.x(), y, w.z(), SHUTTER + "[facing=" + face + "]");
		}
		if (w.level() == null) {
			// The gable's great window: four wide, arched with keystones.
			for (int i = 0; i < 4; i++) {
				for (int y = w.y0(); y <= w.y1(); y++) {
					p.set(w.x() + i, y, w.z(), SHUTTER + "[facing=" + face + "]");
				}
				p.set(w.x() + i, w.y1() + 1, w.z(), i == 1 || i == 2 ? "rauk:limestone_keystone[facing=" + face + "]" : LIMESTONE);
			}
			for (int y = w.y0(); y <= w.y1() + 1; y++) {
				p.set(w.x() - 1, y, w.z(), LIMESTONE);
				p.set(w.x() + 4, y, w.z(), LIMESTONE);
			}
			return;
		}
		if (w.level() == Level.L4) {
			// A dormer standing out of the slate: limestone cheeks, a keystone, a slate gable over it.
			for (int y = w.y0() - 1; y <= w.y1() + 1; y++) {
				p.set(w.x() + ax, y, w.z() + az, LIMESTONE);
				p.set(w.x() - ax, y, w.z() - az, LIMESTONE);
			}
			p.set(w.x(), w.y0() - 1, w.z(), LIMESTONE);
			p.set(w.x(), w.y1() + 1, w.z(), "rauk:limestone_keystone[facing=" + face + "]");
			String up = facing(new int[] {ax, 0, az}), down = facing(new int[] {-ax, 0, -az});
			p.set(w.x() - ax, w.y1() + 2, w.z() - az, "rauk:slate_shingle_stairs[facing=" + up + ",half=bottom,shape=straight,waterlogged=false]");
			p.set(w.x() + ax, w.y1() + 2, w.z() + az, "rauk:slate_shingle_stairs[facing=" + down + ",half=bottom,shape=straight,waterlogged=false]");
			p.set(w.x(), w.y1() + 2, w.z(), SLATE);
			return;
		}
		p.set(w.x(), w.y0() - 1, w.z(), LIMESTONE);
		if (w.level() == Level.L1) {
			p.set(w.x(), w.y1() + 1, w.z(), "rauk:limestone_keystone[facing=" + face + "]");
		} else {
			p.set(w.x(), w.y1() + 1, w.z(), LIMESTONE);
			setIfEmpty(w.x() + d[0], w.y1() + 1, w.z() + d[2], "rauk:limestone_cornice[facing=" + face + "]");
		}
	}

	/**
	 * The central gable over the front (after the reference's tall central bay): the pavilion's
	 * stucco front carried up through the mansard, pilasters each side of the great window, a
	 * stepped limestone pediment with a cornice, and a brass finial on top.
	 */
	private void gable() {
		int z = FOOT_Z1, y0 = Level.R.base - 1;
		p.fill(32, y0, z, 47, y0, z, LIMESTONE);
		for (int x = 32; x <= 47; x++) {
			setIfEmpty(x, y0, z + 1, "rauk:limestone_cornice[facing=south]");
		}
		for (int step = 0; step < 4; step++) {
			int y = y0 + 1 + step;
			for (int x = 33 + step * 2; x <= 46 - step * 2; x++) {
				p.set(x, y, z, LIMESTONE);
			}
		}
		p.set(39, y0 + 5, z, "rauk:brass_finial");
		for (int x : new int[] {34, 45}) {
			for (int y = Level.L4.base; y <= y0 - 1; y++) {
				String part = y == Level.L4.base ? "base" : y == y0 - 1 ? "capital" : "shaft";
				p.set(x, y, z + 1, "rauk:limestone_pilaster[facing=south,part=" + part + "]");
			}
		}
	}

	/**
	 * The front doors under their shutter, a columned portico before them with a balcony on top,
	 * balconies over the central windows, and a limestone apron down to the drive.
	 */
	private void portico() {
		int z = FOOT_Z1;
		for (int x = 38; x <= 41; x++) {
			String hinge = x == 38 || x == 40 ? "right" : "left";
			String door = "rauk:glass_door[facing=south,hinge=" + hinge + ",open=false,powered=false,half=";
			p.set(x, 0, z, door + "lower]");
			p.set(x, 1, z, door + "upper]");
		}
		p.fill(38, 2, z, 41, 3, z, "minecraft:glass");
		p.fill(37, 4, z, 42, 4, z, LIMESTONE);
		p.set(39, 4, z, "rauk:limestone_keystone[facing=south]");
		p.set(40, 4, z, "rauk:limestone_keystone[facing=south]");
		p.fill(33, -1, z + 1, 46, -1, z + 4, LIMESTONE);
		for (int x : new int[] {34, 37, 42, 45}) {
			for (int y = 0; y <= 4; y++) {
				String part = y == 0 ? "base" : y == 4 ? "capital" : "shaft";
				p.set(x, y, z + 3, "rauk:limestone_column[part=" + part + "]");
			}
		}
		p.fill(33, 5, z + 1, 46, 5, z + 3, LIMESTONE);
		for (int x = 33; x <= 46; x++) {
			p.set(x, 5, z + 4, "rauk:limestone_cornice[facing=south]");
			p.set(x, 6, z + 3, "rauk:limestone_balustrade[axis=x]");
		}
		for (int zz = z + 1; zz <= z + 2; zz++) {
			p.set(33, 6, zz, "rauk:limestone_balustrade[axis=z]");
			p.set(46, 6, zz, "rauk:limestone_balustrade[axis=z]");
		}
		// Iron balconies before the central windows of the 2nd and 3rd floors.
		// On 2 the portico's roof is the balcony, behind its balustrade; on 3 a balcony of its own, its
		// railing standing on the slab's front edge.
		int y = Level.L3.base - 1;
		for (int x = 36; x <= 43; x++) {
			p.set(x, y, z + 1, LIMESTONE);
			p.set(x, y, z + 2, LIMESTONE);
			p.set(x, y + 1, z + 2, RAILING);
		}
		for (int zz = z + 1; zz <= z + 2; zz++) {
			p.set(35, y, zz, "rauk:limestone_cornice[facing=west]");
			p.set(44, y, zz, "rauk:limestone_cornice[facing=east]");
			p.set(36, y + 1, zz, RAILING);
			p.set(43, y + 1, zz, RAILING);
		}
		mark("garden_hall", 39, 0, 40);
	}

	/**
	 * Turrets at the four outside corners above the cornice, after the reference's corner domes:
	 * stucco drums with limestone bands, narrow shuttered windows, and pointed slate caps with brass
	 * finials.
	 */
	private void turrets() {
		int y0 = Level.L4.base - 1;
		for (int[] c : new int[][] {{FOOT_X0 - 2, FOOT_Z0 - 2}, {FOOT_X1 - 2, FOOT_Z0 - 2}}) {
			int x0 = c[0], z0 = c[1];
			for (int y = y0; y <= y0 + 7; y++) {
				for (int z = z0; z <= z0 + 4; z++) {
					for (int x = x0; x <= x0 + 4; x++) {
						boolean edge = x == x0 || x == x0 + 4 || z == z0 || z == z0 + 4;
						boolean corner = (x == x0 || x == x0 + 4) && (z == z0 || z == z0 + 4);
						String block = y == y0 || y == y0 + 7 ? LIMESTONE : corner ? LIMESTONE : ASHLAR;
						if (!edge && y > y0 && p.at(x, y, z) != 0) {
							continue;
						}
						p.set(x, y, z, block);
					}
				}
			}
			for (int[] w : new int[][] {{x0 + 2, z0, 0, -1}, {x0 + 2, z0 + 4, 0, 1}, {x0, z0 + 2, -1, 0}, {x0 + 4, z0 + 2, 1, 0}}) {
				if (p.at(w[0] + w[2], y0 + 3, w[1] + w[3]) == 0) {
					String f = facing(new int[] {w[2], 0, w[3]});
					p.set(w[0], y0 + 3, w[1], SHUTTER + "[facing=" + f + "]");
					p.set(w[0], y0 + 4, w[1], SHUTTER + "[facing=" + f + "]");
					p.set(w[0], y0 + 5, w[1], "rauk:limestone_keystone[facing=" + f + "]");
				}
			}
			// The cap: rings of slate stepping in to a point.
			for (int ring = 0; ring < 3; ring++) {
				int y = y0 + 8 + ring, a = x0 + ring, b = x0 + 4 - ring, e = z0 + ring, f = z0 + 4 - ring;
				for (int z = e; z <= f; z++) {
					for (int x = a; x <= b; x++) {
						boolean edge = x == a || x == b || z == e || z == f;
						if (!edge) {
							p.set(x, y, z, SLATE);
							continue;
						}
						String face = z == e ? "south" : z == f ? "north" : x == a ? "east" : "west";
						String shape = (x == a || x == b) && (z == e || z == f) ? "outer_left" : "straight";
						p.set(x, y, z, a == b ? SLATE
								: "rauk:slate_shingle_stairs[facing=" + face + ",half=bottom,shape=" + shape + ",waterlogged=false]");
					}
				}
			}
			p.set(x0 + 2, y0 + 11, z0 + 2, "rauk:brass_finial");
		}
	}

	// --- inside ---------------------------------------------------------------------------------------

	/**
	 * The fittings that give the rooms depth (hotel design 11.2): red runners with gold borders down
	 * the corridors, mahogany pilasters along the good ones, gilded pilasters round the ballroom,
	 * marble columns in the entrance hall, and pipes along the ceilings of the kitchen and the
	 * machinery.
	 */
	private void interior() {
		for (Area a : p.areas) {
			if (a.kind() == Kind.CORRIDOR && a.style().runner) {
				runner(a);
			}
		}
		for (Area a : p.areas) {
			if (a.kind() != Kind.CORRIDOR || a.level() == null || a.slant() != null) {
				continue;
			}
			switch (a.style()) {
				case COZY, COZY_RED, WORN, LOBBY, PENTHOUSE -> pilasters(a, "rauk:mahogany_pilaster", 4);
				default -> {
				}
			}
		}
		Area ballroom = p.owner(65, Level.L1.base, 10);
		pilasters(ballroom, "rauk:limestone_pilaster", 4);
		for (int[] c : new int[][] {{36, 37}, {43, 37}, {36, 43}, {43, 43}}) {
			for (int y = 0; y <= 4; y++) {
				p.set(c[0], y, c[1], "rauk:marble_column[part=" + (y == 0 ? "base" : y == 4 ? "capital" : "shaft") + "]");
			}
		}
		for (Area a : p.areas) {
			if (a.style() == Style.KITCHEN && a.kind() != Kind.CORRIDOR || a.style() == Style.MACHINE) {
				pipes(a);
			}
		}
	}

	/** A runner down the middle of a corridor, its edges bordered in gold. */
	private void runner(Area a) {
		if (a.slant() != null) {
			for (int z = a.z0(); z <= a.z1(); z++) {
				for (int x = a.x0(); x <= a.x1(); x++) {
					if (p.owner(x, a.y0(), z) == a && !p.isAir(x, a.y0() - 1, z) && p.at(x, a.y0() - 1, z) != 0) {
						p.set(x, a.y0() - 1, z, "rauk:carpet_floor");
					}
				}
			}
			return;
		}
		boolean alongX = a.x1() - a.x0() >= a.z1() - a.z0();
		int y = a.y0();
		for (int z = a.z0(); z <= a.z1(); z++) {
			for (int x = a.x0(); x <= a.x1(); x++) {
				if (!p.isAir(x, y, z) || p.owner(x, y, z) != a) {
					continue;
				}
				int across = alongX ? z : x, lo = alongX ? a.z0() : a.x0(), hi = alongX ? a.z1() : a.x1();
				String part = across == lo || across == hi ? "edge" : "center";
				String facing = across == lo ? (alongX ? "north" : "west") : across == hi ? (alongX ? "south" : "east") : (alongX ? "east" : "south");
				p.set(x, y, z, "rauk:runner[part=" + part + ",facing=" + facing + "]");
			}
		}
	}

	/**
	 * Pilasters along both long walls of a space every few blocks, floor to ceiling, wherever the
	 * wall is whole there and nothing opens beside it.
	 */
	private void pilasters(Area a, String block, int every) {
		boolean alongX = a.x1() - a.x0() >= a.z1() - a.z0();
		int from = alongX ? a.x0() : a.z0(), to = alongX ? a.x1() : a.z1();
		for (int i = from + 1; i < to; i++) {
			if (Math.floorMod(i, every) != 0) {
				continue;
			}
			for (int side : new int[] {-1, 1}) {
				int cx = alongX ? i : side < 0 ? a.x0() : a.x1(), cz = alongX ? (side < 0 ? a.z0() : a.z1()) : i;
				int wx = alongX ? cx : cx + side, wz = alongX ? cz + side : cz;
				boolean ok = true;
				for (int y = a.y0(); y <= a.y1() && ok; y++) {
					ok = p.owner(cx, y, cz) == a && (p.isAir(cx, y, cz) || p.get(cx, y, cz).startsWith("rauk:runner")) && wall(wx, y, wz)
							&& wall(wx + (alongX ? 1 : 0), y, wz + (alongX ? 0 : 1)) && wall(wx - (alongX ? 1 : 0), y, wz - (alongX ? 0 : 1));
				}
				if (!ok) {
					continue;
				}
				String facing = alongX ? (side < 0 ? "south" : "north") : (side < 0 ? "east" : "west");
				for (int y = a.y0(); y <= a.y1(); y++) {
					String part = y == a.y0() ? "base" : y == a.y1() ? "capital" : "shaft";
					p.set(cx, y, cz, block + "[facing=" + facing + ",part=" + part + "]");
				}
			}
		}
	}

	/** Whether a cell is plain wall: not open, no door or window in it. */
	private boolean wall(int x, int y, int z) {
		String s = p.get(x, y, z);
		return p.at(x, y, z) != 0 && !p.isAir(x, y, z) && !s.contains("door") && !s.startsWith(SHUTTER) && !s.contains("bookshelf")
				&& !s.contains("stairs");
	}

	/** Old iron pipes along the ceiling of a space, a couple of runs along its length. */
	private void pipes(Area a) {
		boolean alongX = a.x1() - a.x0() >= a.z1() - a.z0();
		int y = a.y1();
		int lo = alongX ? a.z0() : a.x0(), hi = alongX ? a.z1() : a.x1();
		for (int across : new int[] {lo + 1, hi - 1}) {
			if (across < lo || across > hi || hi - lo < 3) {
				continue;
			}
			int from = alongX ? a.x0() : a.z0(), to = alongX ? a.x1() : a.z1();
			for (int i = from; i <= to; i++) {
				int x = alongX ? i : across, z = alongX ? across : i;
				if (p.isAir(x, y, z) && p.owner(x, y, z) == a) {
					p.set(x, y, z, "rauk:iron_pipe[axis=" + (alongX ? "x" : "z") + "]");
				}
			}
		}
	}

	/** Bars, railings and panes join up with what's beside them, as the game would have them. */
	private void connect() {
		for (int y = HotelPlan.Y0; y < HotelPlan.Y0 + HotelPlan.SY; y++) {
			for (int z = HotelPlan.Z0; z < HotelPlan.Z0 + HotelPlan.SZ; z++) {
				for (int x = HotelPlan.X0; x < HotelPlan.X0 + HotelPlan.SX; x++) {
					String id = HotelPlan.blockId(p.get(x, y, z));
					if (joining(id)) {
						boolean wet = p.get(x, y, z).contains("waterlogged=true");
						p.set(x, y, z, id + "[east=" + joins(x + 1, y, z) + ",north=" + joins(x, y, z - 1) + ",south=" + joins(x, y, z + 1)
								+ ",waterlogged=" + wet + ",west=" + joins(x - 1, y, z) + "]");
					}
				}
			}
		}
	}

	private static boolean joining(String id) {
		// Every lattice joins up (the rotunda lift's brass cage too): left alone, each would stand as a post on its own, with gaps between.
		return id.equals(BARS) || id.equals(RAILING) || id.endsWith("_lattice") || id.endsWith("_pane");
	}

	private boolean joins(int x, int y, int z) {
		String s = p.get(x, y, z);
		String id = HotelPlan.blockId(s);
		if (s.isEmpty() || id.equals(AIR) || id.equals(WATER)) {
			return false;
		}
		if (joining(id)) {
			return true;
		}
		return !(id.contains("door") || id.contains("stairs") || id.contains("ladder") || id.contains("slab") || id.contains("fence")
				|| id.contains("pilaster") || id.contains("cornice") || id.contains("balustrade") || id.contains("runner")
				|| id.contains("pipe") || id.contains("finial"));
	}
}

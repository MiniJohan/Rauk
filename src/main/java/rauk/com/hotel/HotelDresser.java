package rauk.com.hotel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import rauk.com.hotel.HotelFurniture.Form;
import rauk.com.hotel.HotelFurniture.Light;
import rauk.com.hotel.HotelFurniture.Piece;
import rauk.com.hotel.HotelPlan.Area;
import rauk.com.hotel.HotelPlan.Circuit;
import rauk.com.hotel.HotelPlan.Kind;
import rauk.com.hotel.HotelPlan.Style;

/**
 * Furnishes the hotel (hotel design 12), room by room, after its purpose and its condition: the
 * guest rooms with their beds, the library's shelves, the bar, the kitchen, the ballroom laid for
 * the gala; dust sheets where it was shut up, ash where it burned. It lights it as design 6.3 has
 * it (lamps in rooms, sconces along corridors, chandeliers only in the big spaces), wires each
 * room's lamps to a switch by its door, and lights a few candles.
 *
 * <p>Furniture goes against the walls and leaves the doorways and the middle of rooms clear, so
 * Rauk can still get everywhere it could (a rug or a place setting is nothing to it).
 */
final class HotelDresser {
	private static final String[] FACING = {"north", "east", "south", "west"};
	private static final int[][] DIR = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

	private final HotelPlan p;
	private final Random rnd = new Random(1931);
	/** What's been put where: the piece, its facing, its part, its look and whether it's lit. */
	private final Map<Long, Placed> placed = new LinkedHashMap<>();
	/** Cells kept clear: in front of doors. */
	private final java.util.Set<Long> clear = new java.util.HashSet<>();
	private final Map<Area, List<int[]>> lamps = new LinkedHashMap<>();
	private final Map<Area, int[]> switches = new HashMap<>();

	private record Placed(Piece piece, String facing, int part, int look, boolean lit) {
	}

	/** A space being furnished: its floor, its doors and openings, its windows. */
	private final class Room {
		final Area a;
		final int y, x0, z0, x1, z1;
		/** Doors and openings: the cell in the wall, and which side of the room it's on. */
		final List<int[]> doors = new ArrayList<>();
		/** The inner ends of the window recesses: the cell, its side, its bottom and top. */
		final List<int[]> windows = new ArrayList<>();

		Room(Area a) {
			this.a = a;
			y = a.y0();
			x0 = a.x0();
			z0 = a.z0();
			x1 = a.x1();
			z1 = a.z1();
			for (int side = 0; side < 4; side++) {
				for (int[] c : edge(side)) {
					int ox = c[0] + DIR[side][0], oz = c[1] + DIR[side][1];
					String s = p.get(ox, y, oz);
					// (Not a secret door: that hides behind what stands in front of it.)
					if (s.contains("_door") && !s.contains("lock=secret") || p.isAir(ox, y, oz) && p.owner(ox, y, oz) != null) {
						doors.add(new int[] {ox, oz, side});
					} else if (p.isAir(ox, y + 1, oz) && p.owner(ox, y + 1, oz) == a || p.isAir(ox, y + 2, oz) && p.owner(ox, y + 2, oz) == a
							&& !p.isAir(ox, y + 1, oz)) {
						int lo = p.isAir(ox, y + 1, oz) ? y + 1 : y + 2, hi = lo;
						while (p.isAir(ox, hi + 1, oz) && p.owner(ox, hi + 1, oz) == a) {
							hi++;
						}
						windows.add(new int[] {ox, oz, side, lo, hi});
					}
				}
			}
			// Keep the way in clear: two cells in from every door and opening.
			for (int[] d : doors) {
				for (int k = 1; k <= 2; k++) {
					int cx = d[0] - DIR[d[2]][0] * k, cz = d[1] - DIR[d[2]][1] * k;
					for (int yy = y; yy <= a.y1(); yy++) {
						clear.add(key(cx, yy, cz));
					}
				}
			}
		}

		/** The floor cells along one side of the room, in order. */
		List<int[]> edge(int side) {
			List<int[]> out = new ArrayList<>();
			switch (side) {
				case 0 -> {
					for (int x = x0; x <= x1; x++) {
						out.add(new int[] {x, z0});
					}
				}
				case 2 -> {
					for (int x = x0; x <= x1; x++) {
						out.add(new int[] {x, z1});
					}
				}
				case 1 -> {
					for (int z = z0; z <= z1; z++) {
						out.add(new int[] {x1, z});
					}
				}
				default -> {
					for (int z = z0; z <= z1; z++) {
						out.add(new int[] {x0, z});
					}
				}
			}
			return out;
		}

		int width() {
			return x1 - x0 + 1;
		}

		int depth() {
			return z1 - z0 + 1;
		}

		/** The side the main door is on (the first door), or south if none. */
		int doorSide() {
			return doors.isEmpty() ? 2 : doors.get(0)[2];
		}

		Style style() {
			return a.style();
		}
	}

	HotelDresser(HotelPlan p) {
		this.p = p;
	}

	private static long key(int x, int y, int z) {
		return ((long) (x - HotelPlan.X0) << 40) | ((long) (y - HotelPlan.Y0) << 20) | (z - HotelPlan.Z0);
	}

	private static int[] unkey(long k) {
		return new int[] {(int) (k >> 40) + HotelPlan.X0, (int) ((k >> 20) & 0xFFFFF) + HotelPlan.Y0, (int) (k & 0xFFFFF) + HotelPlan.Z0};
	}

	private static int opposite(int side) {
		return (side + 2) % 4;
	}

	// --- the whole hotel -------------------------------------------------------------------------

	void dress() {
		List<Room> rooms = new ArrayList<>();
		for (Area a : p.areas) {
			if (a.level() != null && a.kind() != Kind.CRAWL && a.kind() != Kind.TUNNEL && (a.kind() != Kind.OUTSIDE || a.name().equals("Courtyard"))
					&& a.kind() != Kind.LIFT) {
				rooms.add(new Room(a));
			}
		}
		for (int y = HotelPlan.Y0; y < HotelPlan.Y0 + HotelPlan.SY; y++) {
			for (int z = HotelPlan.Z0; z < HotelPlan.Z0 + HotelPlan.SZ; z++) {
				for (int x = HotelPlan.X0; x < HotelPlan.X0 + HotelPlan.SX; x++) {
					String s = p.get(x, y, z);
					if (!s.contains("_door[") || !s.contains("half=lower") || s.contains("lock=secret")) {
						continue;
					}
					boolean alongX = s.contains("facing=east") || s.contains("facing=west");
					for (int k = -2; k <= 2; k++) {
						for (int yy = y; yy <= y + 2; yy++) {
							clear.add(key(alongX ? x + k : x, yy, alongX ? z : z + k));
						}
					}
				}
			}
		}
		for (Room r : rooms) {
			furnish(r);
		}
		for (Room r : rooms) {
			if (lamps.containsKey(r.a) && !switches.containsKey(r.a)) {
				lightSwitch(r);
			}
		}
		wire();
		write();
	}

	private void furnish(Room r) {
		String n = r.a.name();
		switch (n) {
			case "Library" -> library(r);
			case "Reading room" -> readingRoom(r);
			case "Wennberg's hidden study" -> hiddenStudy(r);
			case "Dining room" -> diningRoom(r);
			case "Pantry" -> pantry(r);
			case "Cloakroom" -> cloakroom(r);
			case "Kitchen" -> kitchen(r);
			case "Luggage room", "Box room" -> luggage(r);
			case "Writing room" -> writingRoom(r);
			case "Night clerk's office" -> clerksOffice(r);
			case "Reception" -> {
				if (r.a.slant() != null) {
					rotundaReception(r);
				} else {
					reception(r);
				}
			}
			case "Entrance hall" -> entranceHall(r);
			case "Bar" -> bar(r);
			case "Ballroom" -> ballroom(r);
			case "Band room" -> bandRoom(r);
			case "Billiard room" -> billiardRoom(r);
			case "Smoking room" -> smokingRoom(r);
			case "Atrium" -> atrium(r);
			case "Cold room" -> porterLarder(r);
			case "Meat store" -> coldRoom(r);
			case "Fuse room" -> fuseRoom(r);
			case "Night porter's lodge" -> porterLodge(r);
			case "Wine cellar" -> wineCellar(r);
			case "Coal store" -> coalStore(r);
			case "Staff dormitory" -> staffDormitory(r);
			case "Staff washroom" -> washroom(r);
			case "Laundry" -> laundry(r);
			case "Housekeeping" -> housekeeping(r);
			case "Staff mess" -> staffMess(r);
			case "Boot room" -> bootRoom(r);
			case "Spa" -> spa(r);
			case "Generator and boiler room" -> generatorRoom(r);
			case "Servants' dormitory" -> servantsDormitory(r);
			case "Old nursery" -> nursery(r);
			case "Water tank room" -> tankRoom(r);
			case "Lumber room" -> lumberRoom(r);
			case "Wennberg's drawing room" -> drawingRoom(r);
			case "Wennberg's study" -> study(r);
			case "Lockdown control room" -> controlRoom(r);
			case "Wennberg's bedroom" -> wennbergsBedroom(r);
			case "Penthouse lift lobby" -> penthouseLobby(r);
			case "Glasshouse observatory" -> glasshouse(r);
			case "Bridal suite", "Honeymoon suite" -> bridalSuite(r);
			// The reshaped hotel (hotel design 14).
			case "Garden hall" -> entranceHall(r);
			case "Lobby lounge" -> lounge(r);
			case "Rotunda gallery 2", "Rotunda gallery 3" -> rotundaGallery(r);
			case "Lantern room", "Rotunda cellar" -> lumberRoom(r);
			case "Palm court" -> palmCourt(r);
			case "Courtyard" -> courtyard(r);
			case "Cinema" -> cinema(r);
			case "Cinema foyer" -> cinemaFoyer(r);
			case "Projection room" -> projectionRoom(r);
			case "Box office" -> boxOffice(r);
			case "Tea room" -> teaRoom(r);
			case "Usherettes' cupboard", "Porters' cupboard" -> cupboard(r);
			case "Luggage hall", "Lost property", "Old trunks" -> luggage(r);
			case "Music room" -> musicRoom(r);
			case "Manager's office" -> managerOffice(r);
			case "Ladies' lounge" -> ladiesLounge(r);
			case "Lavatories" -> washroom(r);
			case "Waiting room" -> waitingRoom(r);
			case "Barber's" -> barber(r);
			case "Telegraph office" -> telegraph(r);
			case "Florist" -> florist(r);
			case "Jeweller" -> jeweller(r);
			case "Children's playroom" -> nursery(r);
			case "Porters' rooms" -> staffDormitory(r);
			case "Stores", "Kitchen stores", "Dumbwaiter room" -> pantry(r);
			case "Lamp room", "Linen store" -> linenRoom(r);
			case "Organ blower room", "Cinema boiler room", "Furnace room" -> boilerRoom(r);
			case "Coke store", "Coal bunker" -> coalStore(r);
			case "Wine store" -> wineCellar(r);
			case "Cold store", "Ice store", "Ice house" -> coldRoom(r);
			case "Servants' hall" -> staffMess(r);
			case "Laundry chute room" -> laundry(r);
			case "Staff lockers" -> lockers(r);
			default -> {
				if (r.a.kind() == Kind.CORRIDOR) {
					corridor(r);
				} else if (n.startsWith("Atrium gallery")) {
					gallery(r);
				} else if (n.startsWith("Room ") || n.endsWith(" bedroom")) {
					guestRoom(r);
				} else if (n.startsWith("Suite ") || n.equals("Bandleader's room")) {
					parlour(r);
				} else if (n.startsWith("Servant's room")) {
					servantsRoom(r);
				} else if (n.startsWith("Attic room")) {
					atticRoom(r);
				} else if (n.startsWith("Linen room") || n.contains("linen room") || n.contains("maids' closet")) {
					linenRoom(r);
				} else if (n.contains(" pantry")) {
					pantry(r);
				} else if (r.a.kind() == Kind.STAIRS || n.startsWith("Lift lobby") || n.equals("Electrical room") || n.equals("Fuse room")
						|| n.equals("Lift machine room") || n.equals("Lockdown control room") || n.equals("Spa vestibule")) {
					ceilingLamp(r, "caged_bulb");
				}
			}
		}
	}

	// --- putting things down ------------------------------------------------------------------------

	private boolean free(Room r, int x, int y, int z) {
		long k = key(x, y, z);
		return p.isAir(x, y, z) && p.owner(x, y, z) == r.a && !placed.containsKey(k) && !clear.contains(k);
	}

	/** Free, and standing on something (not water). */
	private boolean floor(Room r, int x, int z) {
		String below = p.get(x, r.y - 1, z);
		return free(r, x, r.y, z) && !p.isAir(x, r.y - 1, z) && !below.isEmpty() && !below.contains("water");
	}

	/** The cells a piece takes from its first part, turned to a facing. */
	private static List<int[]> cells(Piece piece, int x, int y, int z, String facing) {
		List<int[]> out = new ArrayList<>();
		for (int part = 0; part < piece.parts(); part++) {
			int[] c = piece.cellOf(part);
			int[] t = turn(c[0], c[2], facing);
			out.add(new int[] {x + t[0], y + c[1], z + t[1], part});
		}
		return out;
	}

	private static int[] turn(int x, int z, String facing) {
		return switch (facing) {
			case "east" -> new int[] {-z, x};
			case "south" -> new int[] {-x, -z};
			case "west" -> new int[] {z, -x};
			default -> new int[] {x, z};
		};
	}

	/** Puts a piece down with its first part at a cell, if there's room for all of it. */
	private boolean put(Room r, String id, int x, int y, int z, String facing, int look, boolean lit) {
		Piece piece = HotelFurniture.get(id);
		List<int[]> cells = cells(piece, x, y, z, piece.facing ? facing : "north");
		for (int[] c : cells) {
			if (!free(r, c[0], c[1], c[2])) {
				return false;
			}
		}
		for (int[] c : cells) {
			placed.put(key(c[0], c[1], c[2]), new Placed(piece, piece.facing ? facing : "north", c[3], look, lit));
		}
		if (piece.light == Light.ELECTRIC) {
			lamps.computeIfAbsent(r.a, k -> new ArrayList<>()).add(new int[] {x, y, z});
		}
		return true;
	}

	/** Puts a piece down, unlit: ninety years on, the candles have long gone out. */
	private boolean put(Room r, String id, int x, int y, int z, String facing) {
		return put(r, id, x, y, z, facing, 0, false);
	}

	/** Puts a piece down with its footprint's north-west corner at a cell. */
	private boolean putAt(Room r, String id, int minX, int y, int minZ, String facing, int look, boolean lit) {
		Piece piece = HotelFurniture.get(id);
		List<int[]> cells = cells(piece, 0, 0, 0, piece.facing ? facing : "north");
		int mx = Integer.MAX_VALUE, mz = Integer.MAX_VALUE;
		for (int[] c : cells) {
			mx = Math.min(mx, c[0]);
			mz = Math.min(mz, c[2]);
		}
		return put(r, id, minX - mx, y, minZ - mz, facing, look, lit);
	}

	/** How wide a piece is along a wall, and how deep out from it. */
	private static int[] footprint(Piece piece) {
		return new int[] {piece.w, piece.d};
	}

	/** Whether the wall behind a cell, on a side, is whole from a height for so many blocks. */
	private boolean wallBehind(Room r, int x, int z, int side, int y0, int h) {
		int ox = x + DIR[side][0], oz = z + DIR[side][1];
		for (int yy = y0; yy < y0 + h; yy++) {
			String s = p.get(ox, yy, oz);
			// Not a door, a window, water, or the library's shelves that are the hidden study's door.
			if (s.isEmpty() || p.isAir(ox, yy, oz) || s.contains("door") || s.startsWith("rauk:shutter") || s.contains("water")
					|| s.contains("bookshelf")) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Puts a floor piece against a wall, its back to it, at a place along the wall (its low corner
	 * there); the wall behind must be whole as high as it stands.
	 */
	private boolean againstWall(Room r, int side, String id, int along, int look, boolean lit) {
		Piece piece = HotelFurniture.get(id);
		int[] f = footprint(piece);
		int w = f[0], d = f[1];
		int minX, minZ;
		List<int[]> backs = new ArrayList<>();
		switch (side) {
			case 0 -> {
				minX = along;
				minZ = r.z0;
				for (int i = 0; i < w; i++) {
					backs.add(new int[] {along + i, r.z0});
				}
			}
			case 2 -> {
				minX = along;
				minZ = r.z1 - d + 1;
				for (int i = 0; i < w; i++) {
					backs.add(new int[] {along + i, r.z1});
				}
			}
			case 3 -> {
				minX = r.x0;
				minZ = along;
				for (int i = 0; i < w; i++) {
					backs.add(new int[] {r.x0, along + i});
				}
			}
			default -> {
				minX = r.x1 - d + 1;
				minZ = along;
				for (int i = 0; i < w; i++) {
					backs.add(new int[] {r.x1, along + i});
				}
			}
		}
		int h = Math.min(piece.h + 1, 2);
		for (int[] b : backs) {
			if (!wallBehind(r, b[0], b[1], side, r.y, h) || !floor(r, b[0], b[1])) {
				return false;
			}
		}
		// Turned to face out of the wall, from the corner of its footprint.
		return putAt(r, id, minX, r.y, minZ, FACING[opposite(side)], look, lit);
	}

	/** Tries a piece against a wall at each place along it, from the middle out (or from one end); the place used, or -1. */
	private int alongWall(Room r, int side, String id, boolean middleFirst, int look, boolean lit) {
		List<int[]> edge = r.edge(side);
		int n = edge.size();
		List<Integer> order = new ArrayList<>();
		if (middleFirst) {
			for (int k = 0; k < n; k++) {
				int i = n / 2 - HotelFurniture.get(id).w / 2 + (k % 2 == 0 ? k / 2 : -(k / 2 + 1));
				if (i >= 0 && i < n) {
					order.add(i);
				}
			}
		} else {
			for (int i = 0; i < n; i++) {
				order.add(i);
			}
		}
		for (int i : order) {
			int along = side == 0 || side == 2 ? edge.get(i)[0] : edge.get(i)[1];
			if (againstWall(r, side, id, along, look, lit)) {
				return along;
			}
		}
		return -1;
	}

	private int alongWall(Room r, int side, String id, boolean middleFirst) {
		return alongWall(r, side, id, middleFirst, 0, false);
	}

	/** A piece hung on a wall at a height, at a place along it. */
	private boolean onWall(Room r, int side, String id, int along, int y, int look) {
		Piece piece = HotelFurniture.get(id);
		int x = side == 0 || side == 2 ? along : side == 3 ? r.x0 : r.x1;
		int z = side == 1 || side == 3 ? along : side == 0 ? r.z0 : r.z1;
		for (int i = 0; i < piece.w; i++) {
			int cx = side == 0 || side == 2 ? x + i : x, cz = side == 1 || side == 3 ? z + i : z;
			if (!wallBehind(r, cx, cz, side, y, piece.h)) {
				return false;
			}
		}
		String facing = FACING[opposite(side)];
		int minX = x, minZ = z;
		return putAt(r, id, minX, y, minZ, facing, look, false);
	}

	/** Hangs pieces along a wall every so many blocks, from an offset. */
	private void alongWallEvery(Room r, int side, String id, int y, int every, int offset) {
		List<int[]> edge = r.edge(side);
		for (int i = offset; i < edge.size(); i += every) {
			int along = side == 0 || side == 2 ? edge.get(i)[0] : edge.get(i)[1];
			onWall(r, side, id, along, y, rnd.nextInt(HotelFurniture.get(id).looks));
		}
	}

	/** Something on top of what's at a cell (a lamp on a table): one block up, if that's free. */
	private boolean onTop(Room r, int x, int z, String id, String facing, boolean lit) {
		if (!placed.containsKey(key(x, r.y, z))) {
			return false;
		}
		return put(r, id, x, r.y + 1, z, facing, 0, lit);
	}

	/** A rug over the free floor of a rectangle. */
	private void rug(Room r, String id, int x0, int z0, int x1, int z1) {
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				if (floor(r, x, z)) {
					put(r, id, x, r.y, z, "north");
				}
			}
		}
	}

	/** A rug over the middle of the room, a block or two in from its walls. */
	private void rugMiddle(Room r, String id, int inset) {
		rug(r, id, r.x0 + inset, r.z0 + inset, r.x1 - inset, r.z1 - inset);
	}

	/** Curtains drawn across every window. */
	private void curtains(Room r, String id) {
		for (int[] w : r.windows) {
			for (int y = w[3]; y <= w[4]; y++) {
				put(r, id, w[0], y, w[1], FACING[opposite(w[2])]);
			}
		}
	}

	/** A few crates against a wall, one or two high. */
	private void crates(Room r, int side, int n) {
		for (int[] c : r.edge(side)) {
			if (n <= 0) {
				return;
			}
			if (floor(r, c[0], c[1]) && wallBehind(r, c[0], c[1], side, r.y, 1) && rnd.nextBoolean()) {
				p.set(c[0], r.y, c[1], "rauk:crate");
				if (rnd.nextBoolean() && free(r, c[0], r.y + 1, c[1])) {
					p.set(c[0], r.y + 1, c[1], "rauk:crate");
				}
				n--;
			}
		}
	}

	/** Some of a debris piece scattered over free floor. */
	private void scatter(Room r, String id, int n) {
		for (int k = 0; k < n * 6 && n > 0; k++) {
			int x = r.x0 + rnd.nextInt(r.width()), z = r.z0 + rnd.nextInt(r.depth());
			if (floor(r, x, z) && put(r, id, x, r.y, z, FACING[rnd.nextInt(4)])) {
				n--;
			}
		}
	}

	/** A lamp hung in the middle of the ceiling (or several along a long room). */
	private void ceilingLamp(Room r, String id) {
		int y = r.a.y1();
		boolean alongX = r.width() >= r.depth();
		int len = alongX ? r.width() : r.depth();
		int count = Math.max(1, len / 9);
		for (int i = 0; i < count; i++) {
			int along = (alongX ? r.x0 : r.z0) + (len * (2 * i + 1)) / (2 * count);
			int x = alongX ? along : (r.x0 + r.x1) / 2, z = alongX ? (r.z0 + r.z1) / 2 : along;
			if (free(r, x, y, z) && p.at(x, y + 1, z) != 0 && !p.isAir(x, y + 1, z)) {
				put(r, id, x, y, z, "north");
			}
		}
	}

	/** A grand chandelier hung on chains from the ceiling down to a height. */
	private void hangChandelier(Room r, String id, int x, int z, int bottom) {
		int top = r.a.y1();
		if (p.isAir(x, top + 1, z)) {
			return;
		}
		for (int y = top; y > bottom; y--) {
			if (!free(r, x, y, z)) {
				return;
			}
		}
		for (int y = top; y > bottom; y--) {
			put(r, "chandelier_chain", x, y, z, "north");
		}
		put(r, id, x, bottom, z, "north");
		// Under it, as low as there is room: a chandelier hung low fills that space itself.
		for (int y : new int[] {r.y + 2, r.y + 1, r.y + 3}) {
			if (y < bottom && fill(r.a, r.a, x, y, z)) {
				break;
			}
		}
	}

	/** Where its chandeliers' light reaches the floor: invisible light, on their circuit (hotel design 6.3). */
	private final List<int[]> fills = new ArrayList<>();

	/**
	 * A chandelier lights a room from on high, all of it, and Minecraft's light falls off too fast
	 * from one hung up there; so its light is also put lower down, where it would fall, as an
	 * invisible light block on the same switch.
	 */
	private boolean fill(Area owner, Area circuit, int x, int y, int z) {
		if (!p.isAir(x, y, z) || placed.containsKey(key(x, y, z)) || p.owner(x, y, z) != owner) {
			return false;
		}
		fills.add(new int[] {x, y, z});
		lamps.computeIfAbsent(circuit, k -> new ArrayList<>()).add(new int[] {x, y, z});
		return true;
	}

	/** The switch for a room's lights: on the wall beside its first door, inside, at hand height. */
	private boolean lightSwitch(Room r) {
		int y = r.y + 1;
		List<int[]> doors = new ArrayList<>(r.doors);
		for (int[] d : doors) {
			int side = d[2];
			int ix = d[0] - DIR[side][0], iz = d[1] - DIR[side][1];
			int ax = DIR[(side + 1) % 4][0], az = DIR[(side + 1) % 4][1];
			for (int s : new int[] {1, -1, 2, -2}) {
				int x = ix + ax * s, z = iz + az * s;
				if (x < r.x0 || x > r.x1 || z < r.z0 || z > r.z1) {
					continue;
				}
				if (p.isAir(x, y, z) && p.owner(x, y, z) == r.a && !placed.containsKey(key(x, y, z)) && wallBehind(r, x, z, side, y, 1)) {
					placed.put(key(x, y, z), new Placed(null, FACING[opposite(side)], 0, 0, true));
					switches.put(r.a, new int[] {x, y, z});
					return true;
				}
			}
		}
		// No door to put it by (a corridor's end): the first whole bit of wall.
		for (int side = 0; side < 4; side++) {
			for (int[] c : r.edge(side)) {
				if (p.isAir(c[0], y, c[1]) && p.owner(c[0], y, c[1]) == r.a && !placed.containsKey(key(c[0], y, c[1]))
						&& wallBehind(r, c[0], c[1], side, y, 1) && !p.get(c[0], r.y, c[1]).contains("pilaster")) {
					placed.put(key(c[0], y, c[1]), new Placed(null, FACING[opposite(side)], 0, 0, true));
					switches.put(r.a, new int[] {c[0], y, c[1]});
					return true;
				}
			}
		}
		return false;
	}

	// --- the wiring and the writing -------------------------------------------------------------------

	/**
	 * The big rooms hung with chandeliers are the hotel's bright places (hotel design 6.3): their
	 * light is spread through them, a little above the floor every six blocks, on their switch.
	 */
	private void spreadChandelierLight() {
		for (Map.Entry<Area, List<int[]>> e : new ArrayList<>(lamps.entrySet())) {
			Area a = e.getKey();
			boolean chandeliers = false;
			for (int[] l : e.getValue()) {
				Placed pl = placed.get(key(l[0], l[1], l[2]));
				chandeliers |= pl != null && pl.piece() != null && pl.piece().id.contains("chandelier");
			}
			if (!chandeliers || a.kind() != Kind.HALL) {
				continue;
			}
			for (int x = a.x0() + 2; x <= a.x1() - 1; x += 6) {
				for (int z = a.z0() + 2; z <= a.z1() - 1; z += 6) {
					for (int y : new int[] {a.y0() + 2, a.y0() + 1, a.y0() + 3}) {
						if (fill(a, a, x, y, z)) {
							break;
						}
					}
				}
			}
		}
	}

	/** Each room's lamps on its switch; a room with no switch of its own, on the nearest one on its floor. */
	private void wire() {
		spreadChandelierLight();
		for (Map.Entry<Area, List<int[]>> e : lamps.entrySet()) {
			int[] sw = switches.get(e.getKey());
			if (sw == null) {
				double best = Double.MAX_VALUE;
				int[] at = e.getValue().get(0);
				for (Map.Entry<Area, int[]> s : switches.entrySet()) {
					int[] c = s.getValue();
					double d = Math.abs(c[0] - at[0]) + Math.abs(c[2] - at[2]) + Math.abs(c[1] - at[1]) * 4;
					if (s.getKey().level() == e.getKey().level() && d < best) {
						best = d;
						sw = c;
					}
				}
			}
			if (sw != null) {
				p.circuits.add(new Circuit(sw, e.getValue()));
			}
		}
	}

	/** Writes every piece into the plan as a block state, with its joins to its neighbours worked out. */
	private void write() {
		for (int[] f : fills) {
			p.set(f[0], f[1], f[2], "minecraft:light[level=0,waterlogged=false]");
		}
		for (Map.Entry<Long, Placed> e : placed.entrySet()) {
			int[] c = unkey(e.getKey());
			Placed pl = e.getValue();
			if (pl.piece() == null) {
				p.set(c[0], c[1], c[2], "rauk:light_switch[face=wall,facing=" + pl.facing() + ",powered=true]");
				continue;
			}
			Piece piece = pl.piece();
			StringBuilder s = new StringBuilder("rauk:").append(piece.id).append('[');
			List<String> props = new ArrayList<>();
			if (piece.facing) {
				props.add("facing=" + pl.facing());
			}
			switch (piece.form) {
				case RUN -> {
					int f = List.of(FACING).indexOf(pl.facing());
					boolean low = same(c, (f + 3) % 4, pl), high = same(c, (f + 1) % 4, pl);
					props.add("run=" + (low && high ? "middle" : low ? "right" : high ? "left" : "single"));
				}
				case STACK -> {
					boolean below = sameAt(c[0], c[1] - 1, c[2], pl), above = sameAt(c[0], c[1] + 1, c[2], pl);
					props.add("stack=" + (below && above ? "middle" : below ? "top" : above ? "bottom" : "single"));
				}
				case TABLE -> {
					for (int d = 0; d < 4; d++) {
						props.add(FACING[d] + "=" + sameAt(c[0] + DIR[d][0], c[1], c[2] + DIR[d][1], pl));
					}
				}
				case MULTI -> props.add("part=" + pl.part());
				default -> {
				}
			}
			if (piece.looks > 1) {
				props.add("look=" + pl.look());
			}
			if (piece.light != Light.NONE) {
				props.add("lit=" + pl.lit());
			}
			s.append(String.join(",", props)).append(']');
			p.set(c[0], c[1], c[2], props.isEmpty() ? "rauk:" + piece.id : s.toString());
		}
	}

	private boolean same(int[] c, int dir, Placed pl) {
		return sameAt(c[0] + DIR[dir][0], c[1], c[2] + DIR[dir][1], pl);
	}

	private boolean sameAt(int x, int y, int z, Placed pl) {
		Placed o = placed.get(key(x, y, z));
		return o != null && o.piece() == pl.piece() && (!pl.piece().facing || o.facing().equals(pl.facing()));
	}

	// --- kits by condition --------------------------------------------------------------------------

	/** What a room in a given condition is furnished with. */
	private record Kit(String bed, String wardrobe, String chair, String armchair, String sofa, String rug, String curtain, String lamp,
			String table, String debris) {
	}

	private static Kit kit(Style style) {
		return switch (style) {
			case COZY, PENTHOUSE -> new Kit("hotel_bed", "wardrobe", "dining_chair", "club_chair", "chesterfield", "persian_rug",
					"velvet_curtain", "silk_lamp", "round_table", null);
			case COZY_RED -> new Kit("hotel_bed", "wardrobe", "dining_chair", "velvet_armchair", "velvet_settee", "persian_rug",
					"velvet_curtain", "silk_lamp", "round_table", null);
			case WORN, GRAND, BALLROOM, LOBBY -> new Kit("stripped_bed", "wardrobe", "dining_chair", "sheeted_armchair", "sheeted_sofa",
					"worn_rug", "green_curtain", "silk_lamp", "round_table", "plaster_rubble");
			case WRECKED -> new Kit("stripped_bed", null, "overturned_chair", "torn_club_chair", "torn_chesterfield", "worn_rug",
					"torn_curtain", null, "overturned_table", "broken_glass");
			case RUINED -> new Kit("burned_bed", null, "broken_chair", null, null, null, "torn_curtain", null, null, "ash_heap");
			default -> new Kit("iron_bed", null, "kitchen_chair", null, null, null, null, "candles", "pine_table", "plaster_rubble");
		};
	}

	// --- bedrooms -------------------------------------------------------------------------------------

	/** A guest room: the bed against the wall away from the door, the rest round it. */
	private void guestRoom(Room r) {
		Kit k = kit(r.style());
		int door = r.doorSide(), back = opposite(door);
		boolean lit = false;
		int bedAt = alongWall(r, back, k.bed(), true);
		int[] sides = {(back + 1) % 4, (back + 3) % 4};
		if (bedAt < 0) {
			for (int s : sides) {
				if ((bedAt = alongWall(r, s, k.bed(), true)) >= 0) {
					back = s;
					break;
				}
			}
		}
		if (bedAt >= 0 && r.style() != Style.RUINED) {
			// Nightstands either side of the bed's head, a lamp on one.
			for (int off : new int[] {-1, 2}) {
				if (againstWall(r, back, "nightstand", bedAt + off, 0, false)) {
					int[] c = cell(r, back, bedAt + off);
					if (k.lamp() != null && !lit) {
						lit = onTop(r, c[0], c[1], k.lamp(), FACING[opposite(back)], false);
					}
				}
			}
		}
		if (k.wardrobe() != null) {
			for (int s : sides) {
				if (alongWall(r, s, k.wardrobe(), false) >= 0) {
					break;
				}
			}
		}
		// A desk and its chair, or a chest of drawers, on a side wall.
		for (int s : sides) {
			int at = alongWall(r, s, r.style() == Style.RUINED ? "dresser" : "writing_desk", true);
			if (at >= 0) {
				int[] c = cell(r, s, at);
				int ox = -DIR[s][0], oz = -DIR[s][1];
				if (r.style() != Style.RUINED) {
					put(r, k.chair(), c[0] + ox, r.y, c[1] + oz, FACING[s]);
				}
				onWall(r, s, rnd.nextBoolean() ? "portrait" : "wall_mirror", at, r.y + 1, rnd.nextInt(4));
				break;
			}
		}
		if (k.armchair() != null) {
			corner(r, k.armchair());
		}
		if (k.rug() != null) {
			rugMiddle(r, k.rug(), 2);
		}
		if (k.curtain() != null) {
			curtains(r, k.curtain());
		}
		alongWall(r, door, "radiator", false);
		condition(r, k);
		if (r.style() == Style.WORN) {
			scatter(r, "scattered_papers", 1);
		}
	}

	/** The floor cell against a wall at a place along it. */
	private static int[] cell(Room r, int side, int along) {
		return switch (side) {
			case 0 -> new int[] {along, r.z0};
			case 2 -> new int[] {along, r.z1};
			case 3 -> new int[] {r.x0, along};
			default -> new int[] {r.x1, along};
		};
	}

	/** A chair turned into a free corner, facing out into the room. */
	private void corner(Room r, String id) {
		int[][] corners = {{r.x0, r.z0, 2}, {r.x1, r.z0, 2}, {r.x0, r.z1, 0}, {r.x1, r.z1, 0}};
		for (int[] c : corners) {
			if (floor(r, c[0], c[1]) && put(r, id, c[0], r.y, c[1], FACING[c[2]])) {
				return;
			}
		}
	}

	/** What ninety years did, by condition: plaster down, ash, glass. */
	private void condition(Room r, Kit k) {
		switch (r.style()) {
			case WORN -> scatter(r, "plaster_rubble", 1);
			case WRECKED -> {
				scatter(r, "broken_glass", 2);
				scatter(r, "plaster_rubble", 2);
				scatter(r, "scattered_papers", 1);
			}
			case RUINED -> {
				scatter(r, "ash_heap", 4);
				scatter(r, "plaster_rubble", 2);
				scatter(r, "broken_chair", 1);
			}
			case RUNDOWN -> scatter(r, "plaster_rubble", 1);
			default -> {
			}
		}
	}

	/** A suite's parlour: a sofa and chairs round a table, a lamp. */
	private void parlour(Room r) {
		Kit k = kit(r.style());
		int back = opposite(r.doorSide());
		if (k.sofa() != null) {
			int at = alongWall(r, back, k.sofa(), true);
			if (at >= 0) {
				againstWall(r, back, k.sofa(), at + 1, 0, false);
				againstWall(r, back, k.sofa(), at + 2, 0, false);
			}
		}
		if (k.armchair() != null) {
			corner(r, k.armchair());
			corner(r, k.armchair());
		}
		if (k.lamp() != null && k.lamp().equals("silk_lamp")) {
			corner(r, "floor_lamp");
		}
		if (r.a.name().equals("Bandleader's room")) {
			// Puzzle 3: his letter on the desk, the waltz on his stand.
			int side = (back + 1) % 4;
			int at = alongWall(r, side, "writing_desk", true);
			if (at >= 0) {
				int dx = side == 0 || side == 2 ? at : side == 1 ? r.x1 : r.x0, dzz = side == 1 || side == 3 ? at : side == 0 ? r.z0 : r.z1;
				put(r, "bandleader_letter", dx, r.y + 1, dzz, "north");
			}
			corner(r, "waltz_stand");
			scatter(r, "scattered_papers", 2);
		} else {
			alongWall(r, (back + 1) % 4, "dresser", true);
		}
		if (k.rug() != null) {
			rugMiddle(r, k.rug(), 1);
		}
		if (k.curtain() != null) {
			curtains(r, k.curtain());
		}
		condition(r, k);
	}

	/** The bridal suite (and on 3 the honeymoon suite, faded): a four-poster, a settee, the best of everything. */
	private void bridalSuite(Room r) {
		boolean worn = r.style() == Style.WORN;
		int back = opposite(r.doorSide());
		int at = alongWall(r, back, worn ? "stripped_bed" : "four_poster_bed", true);
		if (at >= 0) {
			againstWall(r, back, "nightstand", at - 1, 0, false);
			int[] c = cell(r, back, at - 1);
			onTop(r, c[0], c[1], worn ? "candles" : "silk_lamp", FACING[opposite(back)], false);
			againstWall(r, back, "nightstand", at + 2, 0, false);
		}
		for (int s : new int[] {(back + 1) % 4, (back + 3) % 4}) {
			alongWall(r, s, worn ? "sheeted_sofa" : "velvet_settee", true);
			alongWall(r, s, worn ? "sheeted_sofa" : "velvet_settee", true);
		}
		alongWall(r, (back + 1) % 4, "wardrobe", false);
		alongWall(r, (back + 3) % 4, "dresser", false);
		corner(r, worn ? "sheeted_armchair" : "velvet_armchair");
		corner(r, "floor_lamp");
		rugMiddle(r, worn ? "worn_rug" : "persian_rug", 2);
		curtains(r, worn ? "green_curtain" : "velvet_curtain");
		alongWallEvery(r, (back + 1) % 4, "portrait", r.y + 1, 5, 2);
		if (worn) {
			scatter(r, "plaster_rubble", 1);
		}
	}

	private void servantsRoom(Room r) {
		int back = opposite(r.doorSide());
		alongWall(r, back, "iron_bed", false);
		alongWall(r, (back + 1) % 4, "washstand", true);
		alongWall(r, (back + 3) % 4, "steamer_trunk", true);
		corner(r, "kitchen_chair");
		scatter(r, "plaster_rubble", 1);
	}

	private void atticRoom(Room r) {
		boolean burned = r.style() == Style.RUINED;
		if (burned) {
			scatter(r, "ash_heap", 4);
			scatter(r, "plaster_rubble", 3);
			alongWall(r, 0, "burned_bed", false);
			scatter(r, "broken_chair", 1);
			return;
		}
		alongWall(r, opposite(r.doorSide()), "sheeted_sofa", true);
		corner(r, "sheeted_armchair");
		corner(r, "steamer_trunk");
		corner(r, "suitcases");
		scatter(r, "plaster_rubble", 1);
		crates(r, (r.doorSide() + 1) % 4, 2);
	}

	// --- lobby floor -------------------------------------------------------------------------------------

	private void reception(Room r) {
		// The desk across the room facing the entrance hall to the east, the pigeonholes behind it.
		int x = r.x0 + 4;
		for (int z = r.z0 + 3; z <= r.z1 - 3; z++) {
			put(r, "reception_desk", x, r.y, z, "east");
		}
		int mid = (r.z0 + r.z1) / 2;
		put(r, "service_bell", x, r.y + 1, mid - 1, "east");
		put(r, "open_ledger", x, r.y + 1, mid, "east");
		put(r, "bankers_lamp", x, r.y + 1, mid + 2, "east");
		for (int z = r.z0 + 1; z <= r.z1 - 1; z++) {
			if (wallBehind(r, r.x0, z, 3, r.y, 2) && free(r, r.x0, r.y, z) && free(r, r.x0, r.y + 1, z)) {
				p.set(r.x0, r.y, z, "rauk:pigeonholes[facing=east]");
				p.set(r.x0, r.y + 1, z, "rauk:pigeonholes[facing=east]");
			}
		}
		onWall(r, 3, "wall_clock", mid, r.y + 3, 0);
		put(r, "suitcases", x + 1, r.y, r.z0 + 2, "east");
		put(r, "potted_palm", r.x1, r.y, r.z1, "north");
		put(r, "potted_palm", r.x1, r.y, r.z0, "north");
		alongWall(r, 2, "hall_bench", true);
		hangChandelier(r, "chandelier", x + 3, r.z0 + 3, r.a.y1() - 1);
		hangChandelier(r, "chandelier", x + 3, r.z1 - 3, r.a.y1() - 1);
	}

	// --- the reshaped hotel's rooms (hotel design 14) -----------------------------------------------

	/** Where reception was: a lounge now, armchairs about low tables, palms, the chandelier. */
	private void lounge(Room r) {
		for (int[] t : new int[][] {{r.x0 + 3, r.z0 + 3}, {r.x0 + 3, r.z1 - 3}, {r.x1 - 3, (r.z0 + r.z1) / 2}}) {
			if (put(r, "round_table", t[0], r.y, t[1], "north")) {
				put(r, "velvet_armchair", t[0] - 1, r.y, t[1], "east");
				put(r, "velvet_armchair", t[0] + 1, r.y, t[1], "west");
				put(r, "ashtray_stand", t[0], r.y, t[1] + 1, "north");
			}
		}
		alongWall(r, 3, "velvet_settee", true);
		corner(r, "potted_palm");
		rugMiddle(r, "persian_rug", 2);
		hangChandelier(r, "chandelier", (r.x0 + r.x1) / 2, (r.z0 + r.z1) / 2, r.a.y1() - 1);
	}

	/**
	 * The rotunda's reception: the desk across its west end facing the round stair, the key board
	 * on the wall behind, the night ledger open on it. Palms either side of the doors, benches.
	 */
	private void rotundaReception(Room r) {
		for (int z = 107; z <= 112; z++) {
			put(r, "reception_desk", 33, r.y, z, "east");
		}
		put(r, "service_bell", 33, r.y + 1, 108, "east");
		// Puzzle 1: the night ledger open on the desk, the safe in the wall of pigeonholes behind it.
		put(r, "night_ledger", 33, r.y + 1, 110, "east");
		put(r, "bankers_lamp", 33, r.y + 1, 111, "east");
		put(r, "telephone", 33, r.y + 1, 107, "east");
		put(r, "reception_safe", 31, r.y, 109, "east");
		for (int z = 108; z <= 112; z++) {
			if (free(r, 31, r.y, z) && free(r, 31, r.y + 1, z)) {
				p.set(31, r.y, z, "rauk:pigeonholes[facing=east]");
				p.set(31, r.y + 1, z, "rauk:pigeonholes[facing=east]");
			}
		}
		for (int x : new int[] {36, 43}) {
			put(r, "potted_palm", x, r.y, 116, "north");
		}
		put(r, "hall_bench", 47, r.y, 108, "west");
		put(r, "hall_bench", 47, r.y, 110, "west");
		put(r, "suitcases", 34, r.y, 113, "east");
		put(r, "coat_stand", 45, r.y, 114, "north");
		rug(r, "persian_rug", 38, 113, 41, 116);
		hangChandelier(r, "grand_chandelier", 39, 114, r.a.y1() - 1);
		hangChandelier(r, "chandelier", 39, 102, r.a.y1() - 1);
	}

	/** A gallery round the well: settees and palms about its walls, a chandelier. */
	private void rotundaGallery(Room r) {
		corner(r, "potted_palm");
		for (int side = 0; side < 4; side++) {
			alongWall(r, side, side % 2 == 0 ? "velvet_settee" : "console_table", true);
		}
		put(r, "potted_palm", 33, r.y, 110, "north");
		put(r, "potted_palm", 46, r.y, 110, "north");
		hangChandelier(r, "chandelier", 39, 115, r.a.y1());
		scatter(r, r.style() == Style.WORN ? "plaster_rubble" : "scattered_papers", 2);
	}

	/** The palm court: dead palms all about, wicker chairs round little tables laid for tea. */
	private void palmCourt(Room r) {
		for (int z = r.z0; z <= r.z1; z += 3) {
			for (int x = r.x0; x <= r.x1; x += 4) {
				if (rnd.nextInt(3) > 0) {
					put(r, "potted_palm", x, r.y, z, "north");
				}
			}
		}
		for (int[] t : new int[][] {{36, 88}, {43, 88}, {39, 91}}) {
			if (put(r, "round_table", t[0], r.y, t[1], "north")) {
				put(r, "wicker_chair", t[0], r.y, t[1] - 1, "south");
				put(r, "wicker_chair", t[0] - 1, r.y, t[1], "east");
				put(r, "place_setting", t[0], r.y + 1, t[1], "north");
			}
		}
		put(r, "statue", 39, r.y, 94, "north");
		scatter(r, "broken_glass", 3);
	}

	/** The courtyard: benches along the paths, a statue in each corner of the beds, a dead palm or two. */
	private void courtyard(Room r) {
		for (int[] b : new int[][] {{35, 60, 0}, {44, 60, 0}, {35, 73, 2}, {44, 73, 2}}) {
			put(r, "staff_bench", b[0], r.y, b[1], FACING[b[2]]);
			put(r, "staff_bench", b[0] + 1, r.y, b[1], FACING[b[2]]);
		}
		for (int[] s : new int[][] {{22, 53}, {57, 53}, {22, 78}, {57, 78}}) {
			put(r, "statue", s[0], r.y, s[1], FACING[rnd.nextInt(4)]);
		}
		for (int[] c : new int[][] {{30, 52}, {49, 52}}) {
			put(r, "potted_palm", c[0], r.y, c[1], "north");
		}
	}

	/**
	 * The cinema: the screen across the north wall over a low stage, the organ before it, rows of
	 * tip-up seats facing it with an aisle down the middle, curtains drawn back either side.
	 */
	private void cinema(Room r) {
		for (int x = r.x0 + 2; x <= r.x1 - 2; x++) {
			for (int y = r.y + 1; y <= r.a.y1() - 1; y++) {
				if (p.isAir(x, y, r.z0) && wallBehind(r, x, r.z0, 0, y, 1)) {
					p.set(x, y, r.z0, "rauk:cinema_screen");
					placed.put(key(x, y, r.z0), null);
				}
			}
		}
		placed.values().removeIf(v -> v == null);
		for (int y = r.y; y <= r.a.y1() - 1; y++) {
			put(r, "velvet_curtain", r.x0 + 1, y, r.z0, "south");
			put(r, "velvet_curtain", r.x1 - 1, y, r.z0, "south");
		}
		int mid = (r.x0 + r.x1) / 2;
		put(r, "theatre_organ", mid - 4, r.y, r.z0 + 3, "south");
		put(r, "organ_pipes", r.x0, r.y, r.z0, "north");
		put(r, "organ_pipes", r.x1, r.y, r.z0, "north");
		for (int z = r.z0 + 6; z <= r.z1 - 1; z += 2) {
			for (int x = r.x0 + 1; x <= r.x1 - 1; x++) {
				if (x == mid || x == mid + 1) {
					continue;
				}
				boolean gone = rnd.nextInt(9) == 0;
				put(r, gone ? "overturned_chair" : "cinema_seat", x, r.y, z, gone ? FACING[rnd.nextInt(4)] : "north");
			}
		}
		alongWallEvery(r, 1, "wall_sconce", r.y + 2, 5, 2);
		alongWallEvery(r, 3, "wall_sconce", r.y + 2, 5, 2);
		scatter(r, "scattered_papers", 3);
	}

	/** The foyer: a bar along one wall, settees and palms, a chandelier or two, posters. */
	private void cinemaFoyer(Room r) {
		for (int side = 0; side < 4; side++) {
			alongWall(r, side, "velvet_settee", true);
			alongWall(r, side, "potted_palm", false);
		}
		scatter(r, "scattered_papers", 3);
		for (int i = 0; i < 3; i++) {
			int x = r.x0 + (r.width() * (i + 1)) / 4, z = r.z0 + (r.depth() * (i + 1)) / 4;
			hangChandelier(r, "chandelier", x, z, r.a.y1());
		}
	}

	private void projectionRoom(Room r) {
		put(r, "projector", 75, r.y, 68, "north");
		put(r, "projector", 76, r.y, 68, "north");
		scatter(r, "film_reels", 4);
		crates(r, 1, 2);
		alongWall(r, 1, "wooden_table", false);
		ceilingLamp(r, "caged_bulb");
	}

	private void boxOffice(Room r) {
		alongWall(r, r.doorSide() == 0 ? 2 : 0, "reception_desk", true);
		corner(r, "filing_cabinet");
		onWall(r, 1, "notice_board", r.z0 + 1, r.y + 1, 0);
		ceilingLamp(r, "enamel_pendant");
	}

	/** The tea room: round tables laid for tea, wicker chairs, a palm by the window. */
	private void teaRoom(Room r) {
		for (int z = r.z0 + 1; z <= r.z1 - 1; z += 3) {
			for (int x = r.x0 + 1; x <= r.x1 - 1; x += 3) {
				if (put(r, "round_table", x, r.y, z, "north")) {
					put(r, "wicker_chair", x, r.y, z + 1, "north");
					put(r, "wicker_chair", x + 1, r.y, z, "west");
					put(r, "place_setting", x, r.y + 1, z, "north");
				}
			}
		}
		corner(r, "potted_palm");
		ceilingLamp(r, "pendant_lantern");
	}

	private void cupboard(Room r) {
		corner(r, "coat_stand");
		crates(r, 1, 2);
		scatter(r, "sacks", 1);
	}

	private void musicRoom(Room r) {
		put(r, "grand_piano", r.x0 + 1, r.y, r.z0 + 1, "east");
		put(r, "music_stand", r.x1 - 1, r.y, r.z0 + 2, "west");
		for (int z = r.z0 + 2; z <= r.z1 - 1; z += 2) {
			put(r, "gilt_chair", r.x1 - 1, r.y, z, "west");
		}
		put(r, "gramophone", r.x1, r.y, r.z1, "north");
		rugMiddle(r, "persian_rug", 1);
		ceilingLamp(r, "pendant_lantern");
	}

	private void managerOffice(Room r) {
		put(r, "writing_desk", r.x0 + 2, r.y, r.z0 + 2, "south");
		put(r, "club_chair", r.x0 + 2, r.y, r.z0 + 1, "south");
		put(r, "typewriter", r.x0 + 2, r.y + 1, r.z0 + 2, "south");
		put(r, "bankers_lamp", r.x0 + 3, r.y + 1, r.z0 + 2, "south");
		alongWall(r, 3, "filing_cabinet", false);
		onWall(r, 1, "portrait", r.z0 + 2, r.y + 2, 0);
		scatter(r, "scattered_papers", 3);
		rugMiddle(r, "worn_rug", 1);
	}

	private void ladiesLounge(Room r) {
		alongWall(r, 3, "velvet_settee", true);
		put(r, "velvet_armchair", r.x1 - 1, r.y, r.z0 + 2, "west");
		put(r, "round_table", r.x1 - 1, r.y, r.z0 + 3, "north");
		put(r, "silk_lamp", r.x1 - 1, r.y + 1, r.z0 + 3, "west");
		onWall(r, 1, "wall_mirror", r.z0 + 4, r.y + 1, 0);
		corner(r, "potted_palm");
		rugMiddle(r, "persian_rug", 1);
	}

	private void waitingRoom(Room r) {
		for (int side = 0; side < 4; side++) {
			alongWall(r, side, "hall_bench", true);
		}
		corner(r, "potted_palm");
		corner(r, "coat_stand");
		onWall(r, 1, "wall_clock", r.z0 + 2, r.y + 2, 0);
		ceilingLamp(r, "enamel_pendant");
	}

	/** The barber's: chairs facing the mirrors along a wall, a basin, the pole by the door. */
	private void barber(Room r) {
		for (int z = r.z0 + 1; z <= r.z1 - 1; z += 2) {
			if (put(r, "barber_chair", r.x0 + 1, r.y, z, "east")) {
				onWall(r, 3, "wall_mirror", z, r.y + 1, 0);
			}
		}
		alongWall(r, 1, "pedestal_sink", false);
		corner(r, "barber_pole");
		corner(r, "coat_stand");
		ceilingLamp(r, "enamel_pendant");
	}

	/** The telegraph and post office: a counter, the pigeonholes for the post behind it, the key and its tape. */
	private void telegraph(Room r) {
		for (int z = r.z0 + 2; z <= r.z1 - 2; z++) {
			put(r, "bar_counter", r.x0 + 2, r.y, z, "east");
		}
		put(r, "typewriter", r.x0 + 2, r.y + 1, r.z0 + 3, "east");
		put(r, "telephone", r.x0 + 2, r.y + 1, r.z0 + 5, "east");
		for (int z = r.z0; z <= r.z1; z++) {
			if (wallBehind(r, r.x0, z, 3, r.y, 2) && free(r, r.x0, r.y, z) && free(r, r.x0, r.y + 1, z)) {
				p.set(r.x0, r.y, z, "rauk:pigeonholes[facing=east]");
				p.set(r.x0, r.y + 1, z, "rauk:pigeonholes[facing=east]");
			}
		}
		scatter(r, "scattered_papers", 4);
		onWall(r, 1, "notice_board", r.z0 + 2, r.y + 1, 0);
		ceilingLamp(r, "enamel_pendant");
	}

	/** The florist's: flowers long dead in vases along the walls and on a table. */
	private void florist(Room r) {
		for (int side = 0; side < 4; side++) {
			alongWall(r, side, "wooden_table", true);
		}
		for (int[] c : new int[][] {{r.x0, r.z0}, {r.x1, r.z0}, {r.x0, r.z1}, {r.x1, r.z1}}) {
			put(r, "vase_of_flowers", c[0], r.y, c[1], "north");
		}
		for (Map.Entry<Long, Placed> e : new ArrayList<>(placed.entrySet())) {
			int[] c = unkey(e.getKey());
			if (e.getValue() != null && e.getValue().piece().id.equals("wooden_table") && p.owner(c[0], c[1], c[2]) == r.a) {
				put(r, "vase_of_flowers", c[0], c[1] + 1, c[2], "north");
			}
		}
		corner(r, "potted_palm");
		ceilingLamp(r, "enamel_pendant");
	}

	/** The jeweller's: glass cases smashed and emptied, a mirror, the safe. */
	private void jeweller(Room r) {
		for (int z = r.z0 + 2; z <= r.z1 - 2; z++) {
			put(r, "display_case", r.x0 + 2, r.y, z, "east");
		}
		alongWall(r, 1, "display_case", true);
		onWall(r, 3, "wall_mirror", r.z0 + 3, r.y + 1, 0);
		scatter(r, "broken_glass", 3);
		ceilingLamp(r, "pendant_lantern");
	}

	private void lockers(Room r) {
		for (int side = 0; side < 4; side++) {
			alongWall(r, side, "staff_locker", true);
		}
		alongWall(r, 1, "staff_bench", true);
		ceilingLamp(r, "caged_bulb");
	}

	private void entranceHall(Room r) {
		// A runner of rug from the front doors to the atrium, palms by the columns, benches along the sides.
		rug(r, "persian_rug", 38, r.z0, 41, r.z1);
		for (int[] c : new int[][] {{35, 37}, {44, 37}, {35, 43}, {44, 43}}) {
			put(r, "potted_palm", c[0], r.y, c[1], "north");
		}
		alongWall(r, 1, "hall_bench", true);
		againstWall(r, 1, "hall_bench", r.z0 + 3, 0, false);
		againstWall(r, 1, "hall_bench", r.z0 + 4, 0, false);
		corner(r, "coat_stand");
		hangChandelier(r, "grand_chandelier", 39, 40, r.a.y1() - 1);
		hangChandelier(r, "chandelier", 39, 36, r.a.y1() - 1);
	}

	private void bar(Room r) {
		// The counter along the east side facing west, the back bar behind it on the east wall.
		int cx = r.x1 - 3;
		for (int z = r.z0 + 2; z <= r.z1 - 3; z++) {
			put(r, "bar_counter", cx, r.y, z, "west");
			if ((z - r.z0) % 2 == 0) {
				put(r, "bar_stool", cx - 1, r.y, z, "north");
			}
		}
		// The till: a fuse among the coins in its drawer (puzzle 2).
		put(r, "bar_till", cx, r.y + 1, r.z0 + 3, "west");
		put(r, "bottles", cx, r.y + 1, r.z0 + 6, "west");
		put(r, "bottles", cx, r.y + 1, r.z1 - 4, "west");
		for (int z = r.z0 + 1; z <= r.z1 - 1; z++) {
			if (wallBehind(r, r.x1, z, 1, r.y, 3) && free(r, r.x1, r.y, z)) {
				p.set(r.x1, r.y, z, "rauk:bar_cabinet[facing=west]");
				p.set(r.x1, r.y + 1, z, "rauk:bottle_shelves[facing=west]");
				p.set(r.x1, r.y + 2, z, "rauk:bottle_shelves[facing=west]");
			}
		}
		// Little round tables with club chairs in the west half, oil lamps lit on them: the bar's lamps.
		boolean lamp = true;
		for (int[] t : new int[][] {{r.x0 + 2, r.z0 + 2}, {r.x0 + 2, r.z1 - 2}, {r.x0 + 5, (r.z0 + r.z1) / 2 - 2}}) {
			if (put(r, "round_table", t[0], r.y, t[1], "north")) {
				put(r, "club_chair", t[0] - 1, r.y, t[1], "east");
				put(r, "club_chair", t[0] + 1, r.y, t[1], "west");
				put(r, lamp ? "oil_lamp" : "bottles", t[0], r.y + 1, t[1], "north", 0, lamp);
				lamp = !lamp;
			}
		}
		put(r, "round_table", r.x0, r.y, r.z1, "north");
		put(r, "gramophone", r.x0, r.y + 1, r.z1, "east");
		curtains(r, "velvet_curtain");
		hangChandelier(r, "chandelier", r.x0 + 4, r.z0 + 3, r.a.y1() - 1);
		hangChandelier(r, "chandelier", r.x0 + 4, r.z1 - 3, r.a.y1() - 1);
		alongWallEvery(r, 3, "wall_sconce", r.y + 2, 4, 1);
	}

	/** The dining room, laid for the gala: two long tables, chairs either side, candelabras. */
	private void diningRoom(Room r) {
		for (int row : new int[] {r.x0 + 4, r.x1 - 5}) {
			for (int z = r.z0 + 3; z <= r.z1 - 4; z++) {
				put(r, "dining_table", row, r.y, z, "north");
				put(r, "dining_table", row + 1, r.y, z, "north");
				boolean fallen = rnd.nextInt(7) == 0;
				put(r, fallen ? "overturned_chair" : "dining_chair", row - 1, r.y, z, fallen ? FACING[rnd.nextInt(4)] : "east");
				put(r, "dining_chair", row + 2, r.y, z, "west");
				put(r, "place_setting", row, r.y + 1, z, "west");
				put(r, "place_setting", row + 1, r.y + 1, z, "east");
			}
			put(r, "candelabra", row, r.y + 1, r.z0 + 6, "north");
			put(r, "candelabra", row + 1, r.y + 1, r.z1 - 7, "north");
		}
		alongWall(r, 3, "dresser", true);
		alongWall(r, 1, "console_table", true);
		curtains(r, "velvet_curtain");
		for (int x : new int[] {r.x0 + 5, r.x1 - 4}) {
			for (int z : new int[] {r.z0 + 5, r.z1 - 5}) {
				hangChandelier(r, "chandelier", x, z, r.a.y1() - 1);
			}
		}
		scatter(r, "broken_glass", 1);
	}

	private void pantry(Room r) {
		for (int side = 0; side < 4; side++) {
			for (int[] c : r.edge(side)) {
				if (wallBehind(r, c[0], c[1], side, r.y, 2) && free(r, c[0], r.y, c[1]) && free(r, c[0], r.y + 1, c[1]) && rnd.nextInt(3) > 0) {
					p.set(c[0], r.y, c[1], "rauk:pantry_shelves[facing=" + FACING[opposite(side)] + "]");
					p.set(c[0], r.y + 1, c[1], "rauk:pantry_shelves[facing=" + FACING[opposite(side)] + "]");
					placed.put(key(c[0], r.y, c[1]), null);
				}
			}
		}
		placed.values().removeIf(v -> v == null);
		scatter(r, "sacks", 2);
		ceilingLamp(r, "caged_bulb");
	}

	private void cloakroom(Room r) {
		int back = opposite(r.doorSide());
		for (int i = 0; i < 3; i++) {
			alongWall(r, back, "coat_stand", false);
		}
		for (int x = r.x0 + 1; x <= r.x1 - 1; x++) {
			if (!clear.contains(key(x, r.y, r.z0 + 3))) {
				put(r, "bar_counter", x, r.y, r.z0 + 3, "south");
			}
		}
		ceilingLamp(r, "pendant_lantern");
	}

	/**
	 * The kitchen, gross: black ranges down one wall, zinc tables down the middle under racks of
	 * pans, sinks, and where the chef made his stand, the barricade pulled apart, burned-down
	 * candles in bottles, ash where the lamp oil went up.
	 */
	private void kitchen(Room r) {
		for (int z = r.z0 + 2; z <= r.z1 - 2; z += 2) {
			againstWall(r, 3, "kitchen_range", z, 0, false);
		}
		for (int x = r.x0 + 4; x <= r.x0 + 7; x++) {
			for (int z = r.z0 + 5; z <= r.z1 - 4; z++) {
				if (x == r.x0 + 5 || x == r.x0 + 6) {
					put(r, "prep_table", x, r.y, z, "north");
				}
			}
		}
		for (int z = r.z0 + 6; z <= r.z1 - 5; z += 3) {
			put(r, "pot_rack", r.x0 + 5, r.a.y1(), z, "east");
		}
		for (int z = r.z0 + 3; z <= r.z1 - 3; z += 4) {
			againstWall(r, 1, "kitchen_sink", z, 0, false);
		}
		alongWall(r, 2, "ice_box", false);
		// The barricade, shoved aside: tables over on their sides and crates either side of the doors.
		for (int[] d : r.doors) {
			for (int s : new int[] {-2, 2, 3}) {
				int x = d[0] + s, z = d[1] - DIR[d[2]][1];
				if (floor(r, x, z)) {
					put(r, rnd.nextBoolean() ? "overturned_table" : "sacks", x, r.y, z, FACING[rnd.nextInt(4)]);
				}
			}
		}
		scatter(r, "bottle_candle", 3);
		scatter(r, "ash_heap", 3);
		scatter(r, "broken_glass", 2);
		ceilingLamp(r, "enamel_pendant");
	}

	private void luggage(Room r) {
		for (int side = 0; side < 4; side++) {
			alongWall(r, side, "steamer_trunk", false);
			alongWall(r, side, "suitcases", false);
		}
		scatter(r, "suitcases", 2);
		corner(r, "sheeted_armchair");
		ceilingLamp(r, "caged_bulb");
	}

	private void writingRoom(Room r) {
		int back = opposite(r.doorSide());
		for (int s : new int[] {(back + 1) % 4, (back + 3) % 4, back}) {
			int at = alongWall(r, s, "writing_desk", true);
			if (at >= 0) {
				int[] c = cell(r, s, at);
				put(r, "dining_chair", c[0] - DIR[s][0], r.y, c[1] - DIR[s][1], FACING[s]);
				onTop(r, c[0], c[1], s == back ? "telephone" : "bankers_lamp", FACING[opposite(s)], false);
			}
		}
		alongWall(r, back, "console_table", false);
		rugMiddle(r, "persian_rug", 2);
		curtains(r, "velvet_curtain");
		alongWallEvery(r, (back + 1) % 4, "portrait", r.y + 1, 4, 1);
	}

	private void clerksOffice(Room r) {
		int at = alongWall(r, 3, "writing_desk", true);
		if (at >= 0) {
			int[] c = cell(r, 3, at);
			put(r, "dining_chair", c[0] + 1, r.y, c[1], "west");
			onTop(r, c[0], c[1], "typewriter", "east", false);
		}
		int at2 = alongWall(r, 3, "writing_desk", true);
		if (at2 >= 0) {
			int[] c = cell(r, 3, at2);
			onTop(r, c[0], c[1], "bankers_lamp", "east", false);
		}
		alongWall(r, 0, "filing_cabinet", false);
		alongWall(r, 2, "filing_cabinet", false);
		alongWall(r, 2, "filing_cabinet", false);
		onWall(r, 3, "notice_board", r.z0 + 2, r.y + 2, 0);
		onWall(r, 0, "wall_clock", (r.x0 + r.x1) / 2, r.y + 2, 0);
		// The finale: the lockdown's release panel on the wall where the clerk could see the doors,
		// its lever beside it.
		for (int z = (r.z0 + r.z1) / 2; z < r.z1 - 1; z++) {
			if (onWall(r, 1, "release_panel", z, r.y + 1, 0)) {
				onWall(r, 1, "release_lever", z + 1, r.y + 1, 0);
				p.marks.put("release_panel", new HotelPlan.Mark("release_panel", r.x1, r.y + 1, z));
				break;
			}
		}
		scatter(r, "scattered_papers", 3);
	}

	/**
	 * The ballroom, frozen in the middle of the gala: the band's stand at the east end with the
	 * piano on it, tables laid round the edges, the dance floor clear, three chandeliers hung and
	 * the fourth on the floor where it fell.
	 */
	private void ballroom(Room r) {
		// The bandstand: a step up across the east end, a balustrade along its front.
		int stage = r.x1 - 4;
		for (int z = r.z0; z <= r.z1; z++) {
			for (int x = stage; x <= r.x1; x++) {
				if (free(r, x, r.y, z)) {
					p.set(x, r.y, z, "rauk:parquet");
					placed.put(key(x, r.y, z), null);
				}
			}
		}
		int sy = r.y + 1;
		// Puzzle 3: the grand piano the waltz is played on, and the drawer in the front of the stand.
		put(r, "ballroom_piano", stage + 1, sy, (r.z0 + r.z1) / 2 + 1, "west");
		int dz = (r.z0 + r.z1) / 2 - 3;
		p.set(stage, r.y, dz, "rauk:bandstand_drawer[facing=west,look=0]");
		p.marks.put("bandstand_drawer", new HotelPlan.Mark("bandstand_drawer", stage, r.y, dz));
		for (int z = r.z0 + 3; z <= r.z1 - 3; z += 3) {
			put(r, "music_stand", r.x1 - 1, sy, z, "west");
			put(r, "gilt_chair", r.x1, sy, z, "west");
		}
		// Round tables laid along the north and south sides.
		for (int x = r.x0 + 2; x < stage - 4; x += 5) {
			for (int z : new int[] {r.z0 + 2, r.z1 - 3}) {
				banquetTable(r, x, z);
			}
		}
		// Chairs along the long walls for those not dancing.
		for (int side : new int[] {0, 2}) {
			for (int[] c : r.edge(side)) {
				if (c[0] < stage - 1 && Math.floorMod(c[0], 3) != 0 && floor(r, c[0], c[1]) && wallBehind(r, c[0], c[1], side, r.y, 1)) {
					put(r, rnd.nextInt(9) == 0 ? "overturned_chair" : "gilt_chair", c[0], r.y, c[1], FACING[opposite(side)]);
				}
			}
		}
		curtains(r, "velvet_curtain");
		int midZ = (r.z0 + r.z1) / 2;
		int[] xs = {r.x0 + 4, r.x0 + 15, stage - 4};
		for (int i = 0; i < xs.length; i++) {
			hangChandelier(r, "grand_chandelier", xs[i], midZ, r.y + 4);
		}
		// The fourth came down: its chain hanging short above where it lies.
		put(r, "fallen_chandelier", r.x0 + 9, r.y, midZ + 3, "east");
		for (int y = r.a.y1(); y > r.a.y1() - 2; y--) {
			put(r, "chandelier_chain", r.x0 + 9, y, midZ + 3, "north");
		}
		alongWallEvery(r, 0, "wall_sconce", r.y + 3, 6, 3);
		alongWallEvery(r, 2, "wall_sconce", r.y + 3, 6, 3);
		scatter(r, "broken_glass", 3);
		scatter(r, "overturned_chair", 2);
	}

	/** A round table for four (two by two under one cloth), laid, gilt chairs round it. */
	private void banquetTable(Room r, int x, int z) {
		for (int dx = 0; dx < 2; dx++) {
			for (int dz = 0; dz < 2; dz++) {
				if (!free(r, x + dx, r.y, z + dz)) {
					return;
				}
			}
		}
		for (int dx = 0; dx < 2; dx++) {
			for (int dz = 0; dz < 2; dz++) {
				put(r, "dining_table", x + dx, r.y, z + dz, "north");
				put(r, dz == 0 && dx == 0 ? "candelabra" : "place_setting", x + dx, r.y + 1, z + dz, FACING[(dx == 0 ? 3 : 1)]);
			}
		}
		put(r, "gilt_chair", x - 1, r.y, z, "east");
		put(r, "gilt_chair", x + 2, r.y, z + 1, "west");
		put(r, rnd.nextInt(3) == 0 ? "overturned_chair" : "gilt_chair", x, r.y, z - 1, "south");
		put(r, "gilt_chair", x + 1, r.y, z + 2, "north");
	}

	private void bandRoom(Room r) {
		for (int side = 0; side < 4; side++) {
			alongWall(r, side, "steamer_trunk", false);
		}
		corner(r, "music_stand");
		corner(r, "kitchen_chair");
		corner(r, "coat_stand");
		scatter(r, "scattered_papers", 3);
		ceilingLamp(r, "caged_bulb");
	}

	private void billiardRoom(Room r) {
		int mid = (r.z0 + r.z1) / 2;
		for (int[] t : new int[][] {{r.x0 + 3, mid - 1}, {r.x1 - 9, mid - 1}}) {
			for (int dx = 0; dx < 3; dx++) {
				for (int dz = 0; dz < 2; dz++) {
					put(r, "torn_billiard_table", t[0] + dx, r.y, t[1] + dz, "north");
				}
			}
		}
		alongWall(r, 2, "torn_chesterfield", false);
		corner(r, "torn_club_chair");
		corner(r, "torn_club_chair");
		alongWall(r, 0, "ashtray_stand", false);
		scatter(r, "overturned_chair", 2);
		scatter(r, "broken_glass", 2);
		scatter(r, "plaster_rubble", 4);
		curtains(r, "torn_curtain");
		ceilingLamp(r, "pendant_lantern");
	}

	/** The smoking room, wrecked: what was the finest room after the library, torn up and thrown about. */
	private void smokingRoom(Room r) {
		int back = 2;
		int at = alongWall(r, back, "fireplace", true);
		for (int s : new int[] {1, 0}) {
			int a = alongWall(r, s, "torn_chesterfield", true);
			if (a >= 0) {
				againstWall(r, s, "torn_chesterfield", a + 1, 0, false);
			}
		}
		corner(r, "torn_club_chair");
		corner(r, "torn_club_chair");
		// Three groups of chairs round little tables, one table knocked over and its chairs flung back.
		int mz = (r.z0 + r.z1) / 2;
		int[][] groups = {{r.x0 + 4, mz - 2}, {(r.x0 + r.x1) / 2, mz + 1}, {r.x1 - 5, mz - 2}};
		for (int i = 0; i < groups.length; i++) {
			int gx = groups[i][0], gz = groups[i][1];
			boolean wrecked = i == 1;
			put(r, wrecked ? "overturned_table" : "round_table", gx, r.y, gz, "east");
			put(r, wrecked ? "torn_club_chair" : "torn_club_chair", gx - 1, r.y, gz, wrecked ? "south" : "east");
			put(r, "torn_club_chair", gx + 1, r.y, gz, "west");
			put(r, "torn_club_chair", gx, r.y, gz + 1, "north");
			put(r, "ashtray_stand", gx + 1, r.y, gz - 1, "north");
			if (!wrecked) {
				put(r, "bottles", gx, r.y + 1, gz, "north");
			}
		}
		scatter(r, "ashtray_stand", 1);
		for (int[] c : r.edge(3)) {
			if (wallBehind(r, c[0], c[1], 3, r.y, 2) && free(r, c[0], r.y, c[1]) && free(r, c[0], r.y + 1, c[1]) && rnd.nextInt(3) == 0) {
				p.set(c[0], r.y, c[1], "rauk:bookcase_worn[facing=east]");
				p.set(c[0], r.y + 1, c[1], "rauk:bookcase_worn[facing=east]");
				placed.put(key(c[0], r.y, c[1]), null);
				placed.put(key(c[0], r.y + 1, c[1]), null);
			}
		}
		placed.values().removeIf(v -> v == null);
		rugMiddle(r, "worn_rug", 3);
		curtains(r, "torn_curtain");
		scatter(r, "broken_glass", 3);
		scatter(r, "scattered_papers", 2);
		scatter(r, "plaster_rubble", 2);
		if (at >= 0) {
			ceilingLamp(r, "chandelier");
		}
	}

	/** The atrium's floor: benches and palms round the foot of the stair, a chandelier down the middle. */
	private void atrium(Room r) {
		for (int[] c : new int[][] {{36, 22}, {43, 22}, {33, 17}, {46, 17}, {33, 28}}) {
			put(r, "potted_palm", c[0], r.y, c[1], "north");
		}
		for (int z = 25; z <= 27; z++) {
			put(r, "hall_bench", 34, r.y, z, "east");
		}
		for (int x = 36; x <= 38; x++) {
			put(r, "hall_bench", x, r.y, 30, "north");
		}
		for (int[] g : new int[][] {{31, 15}, {48, 15}, {31, 30}, {48, 30}}) {
			put(r, "round_table", g[0], r.y, g[1], "north");
			put(r, "velvet_armchair", g[0], r.y, g[1] + (g[1] < 20 ? -1 : 1), g[1] < 20 ? "south" : "north");
		}
		// From the glass roof, down through the open middle of the galleries.
		hangAtrium(39, 25);
	}

	/** The atrium's chandelier, on a long chain from the glass roof's ribs to above the gallery on 2. */
	private void hangAtrium(int x, int z) {
		int top = HotelPlan.Level.R.base - 2, bottom = HotelPlan.Level.L3.base + 1;
		for (int y = top; y > bottom; y--) {
			if (!p.isAir(x, y, z)) {
				return;
			}
		}
		Area gallery4 = p.owner(x, top, z);
		for (int y = top; y > bottom; y--) {
			placed.put(key(x, y, z), new Placed(HotelFurniture.get("chandelier_chain"), "north", 0, 0, false));
		}
		placed.put(key(x, bottom, z), new Placed(HotelFurniture.get("grand_chandelier"), "north", 0, 0, false));
		Area at = p.owner(x, bottom, z);
		Area circuit = at != null ? at : gallery4;
		lamps.computeIfAbsent(circuit, k -> new ArrayList<>()).add(new int[] {x, bottom, z});
		// Its light down through the open middle to the atrium floor, and out to its corners.
		int floor = HotelPlan.Level.L1.base;
		Area atrium = p.owner(x, floor, z);
		for (int[] c : new int[][] {{x, z}, {34, 18}, {45, 18}, {34, 28}, {45, 28}}) {
			fill(atrium, circuit, c[0], floor + 2, c[1]);
		}
	}

	private void gallery(Room r) {
		for (int[] c : new int[][] {{30, 13}, {49, 13}, {30, 32}, {49, 32}}) {
			put(r, "potted_palm", c[0], r.y, c[1], "north");
		}
		alongWallEvery(r, 0, "wall_sconce", r.y + 1, 6, 3);
		alongWallEvery(r, 2, "wall_sconce", r.y + 1, 6, 3);
		alongWallEvery(r, 0, "portrait", r.y + 1, 6, 6);
	}

	// --- guest floors, library, penthouse ------------------------------------------------------------------

	/** The library: shelves floor to ceiling round its walls, reading tables with green lamps, a globe. */
	private void library(Room r) {
		for (int side = 0; side < 4; side++) {
			for (int[] c : r.edge(side)) {
				boolean ok = free(r, c[0], r.y, c[1]);
				for (int yy = r.y; yy <= r.y + 3 && ok; yy++) {
					ok = wallBehind(r, c[0], c[1], side, yy, 1) && free(r, c[0], yy, c[1]);
				}
				if (ok) {
					for (int yy = r.y; yy <= r.y + 3; yy++) {
						p.set(c[0], yy, c[1], "rauk:bookcase[facing=" + FACING[opposite(side)] + "]");
						placed.put(key(c[0], yy, c[1]), null);
					}
				}
			}
		}
		placed.values().removeIf(v -> v == null);
		if (r.a.name().equals("Library")) {
			// Puzzle 4: shelving carried on over the section that swings, and the catalogue shelf.
			for (int z = 3; z <= 4; z++) {
				for (int yy = r.y + 2; yy <= r.y + 3; yy++) {
					if (p.isAir(r.x1, yy, z) && p.owner(r.x1, yy, z) == r.a) {
						p.set(r.x1, yy, z, "rauk:bookcase[facing=west]");
						placed.put(key(r.x1, yy, z), null);
					}
				}
			}
			p.set(r.x1, r.y + 1, 6, "minecraft:chiseled_bookshelf[facing=west]");
			p.marks.put("catalogue_shelf", new HotelPlan.Mark("catalogue_shelf", r.x1, r.y + 1, 6));
			placed.values().removeIf(v -> v == null);
		}
		int table = 0;
		for (int[] t : new int[][] {{r.x0 + 4, r.z0 + 4}, {r.x0 + 4, r.z1 - 6}, {r.x1 - 6, r.z0 + 4}, {r.x1 - 6, r.z1 - 6}}) {
			for (int dx = 0; dx < 3; dx++) {
				for (int dz = 0; dz < 2; dz++) {
					put(r, "wooden_table", t[0] + dx, r.y, t[1] + dz, "north");
				}
			}
			if (r.a.name().equals("Library")) {
				// The catalogue open on the first table; a volume on each of the others.
				put(r, table == 0 ? "cabinet_catalogue" : "catalogue_volume_" + (table - 1), t[0], r.y + 1, t[1] + 1, "north");
				table++;
			}
			put(r, "bankers_lamp", t[0] + 1, r.y + 1, t[1], "north");
			put(r, "books", t[0] + 2, r.y + 1, t[1] + 1, "east");
			for (int dx = 0; dx < 3; dx += 2) {
				put(r, "dining_chair", t[0] + dx, r.y, t[1] - 1, "south");
				put(r, "dining_chair", t[0] + dx, r.y, t[1] + 2, "north");
			}
		}
		put(r, "globe", (r.x0 + r.x1) / 2, r.y, (r.z0 + r.z1) / 2, "south");
		put(r, "green_club_chair", (r.x0 + r.x1) / 2 - 2, r.y, (r.z0 + r.z1) / 2, "east");
		put(r, "green_club_chair", (r.x0 + r.x1) / 2 + 2, r.y, (r.z0 + r.z1) / 2, "west");
		if (r.a.name().equals("Library")) {
			// The last volume dropped on the floor by the chairs.
			put(r, "catalogue_volume_5", (r.x0 + r.x1) / 2 - 2, r.y, (r.z0 + r.z1) / 2 + 2, "east");
		}
		rug(r, "green_rug", r.x0 + 7, r.z0 + 7, r.x1 - 7, r.z1 - 7);
		for (int[] t : new int[][] {{r.x0 + 5, r.z0 + 5}, {r.x0 + 5, r.z1 - 5}, {r.x1 - 5, r.z0 + 5}, {r.x1 - 5, r.z1 - 5}}) {
			hangChandelier(r, "chandelier", t[0], t[1], r.a.y1() - 1);
		}
	}

	/** The reading room: the fire lit (it's lit before the power is), two chairs drawn up to it. */
	private void readingRoom(Room r) {
		int at = alongWall(r, 0, "fireplace", true, 0, true);
		int side = 0;
		if (at < 0) {
			side = 1;
			at = alongWall(r, 1, "fireplace", true, 0, true);
		}
		if (at >= 0) {
			int[] c = cell(r, side, at);
			int ox = -DIR[side][0], oz = -DIR[side][1];
			put(r, "green_club_chair", c[0] + ox * 2, r.y, c[1] + oz * 2, FACING[side]);
			put(r, "green_club_chair", c[0] + ox * 2 + (side == 0 ? 2 : 0), r.y, c[1] + oz * 2 + (side == 0 ? 0 : 2), FACING[side]);
			put(r, "round_table", c[0] + ox * 2 + (side == 0 ? 1 : 0), r.y, c[1] + oz * 2 + (side == 0 ? 0 : 1), "north");
			put(r, "bankers_lamp", c[0] + ox * 2 + (side == 0 ? 1 : 0), r.y + 1, c[1] + oz * 2 + (side == 0 ? 0 : 1), FACING[side]);
		}
		corner(r, "floor_lamp");
		rugMiddle(r, "green_rug", 1);
		int console = alongWall(r, 2, "console_table", false);
		if (console >= 0) {
			put(r, "catalogue_volume_3", console, r.y + 1, r.z1, "north");
		}
		// Two more volumes, put down and left: on a chair by the fire, and on the floor.
		scatter(r, "catalogue_volume_4", 1);
		curtains(r, "green_curtain");
	}

	private void hiddenStudy(Room r) {
		int at = alongWall(r, 0, "writing_desk", true);
		if (at >= 0) {
			put(r, "green_club_chair", at, r.y, r.z0 + 1, "north");
			// Puzzle 4: his book on No. 7 on the desk; the card with the code's first half and the
			// penthouse lift key on little tables either side.
			put(r, "no_seven", at, r.y + 1, r.z0, "south");
			for (int[] side : new int[][] {{-2, 0}, {2, 1}}) {
				if (put(r, "round_table", at + side[0], r.y, r.z0, "north")) {
					put(r, side[1] == 0 ? "code_card" : "penthouse_key_lying", at + side[0], r.y + 1, r.z0, "north");
				}
			}
		}
		alongWall(r, 2, "filing_cabinet", false);
		corner(r, "globe");
		onWall(r, 1, "portrait", (r.z0 + r.z1) / 2, r.y + 1, 0);
		scatter(r, "scattered_papers", 2);
		corner(r, "candelabra");
	}

	private void drawingRoom(Room r) {
		int at = alongWall(r, 3, "fireplace", true);
		if (at >= 0) {
			onWall(r, 3, "portrait", at + 1, r.y + 2, 0);
		}
		int s = alongWall(r, 2, "chesterfield", true);
		if (s >= 0) {
			againstWall(r, 2, "chesterfield", s + 1, 0, false);
			againstWall(r, 2, "chesterfield", s + 2, 0, false);
		}
		corner(r, "velvet_armchair");
		corner(r, "velvet_armchair");
		corner(r, "floor_lamp");
		corner(r, "grand_piano");
		put(r, "round_table", (r.x0 + r.x1) / 2, r.y, (r.z0 + r.z1) / 2, "north");
		put(r, "vase_of_flowers", (r.x0 + r.x1) / 2, r.y + 1, (r.z0 + r.z1) / 2, "north");
		alongWall(r, 0, "console_table", true);
		rugMiddle(r, "persian_rug", 2);
		curtains(r, "velvet_curtain");
		hangChandelier(r, "chandelier", (r.x0 + r.x1) / 2, (r.z0 + r.z1) / 2 - 2, r.a.y1());
	}

	/** Wennberg's study: the stopped clock against the wall the control room is behind (puzzle 5). */
	private void study(Room r) {
		// Puzzle 5: the stopped clock against the wall the control room is behind, his journal on the desk.
		// (Put there, not against the wall: behind it is the panel door, not wall.)
		put(r, "study_clock", r.x1, r.y, r.z0 + 2, "west");
		int at = alongWall(r, 0, "writing_desk", true);
		if (at >= 0) {
			put(r, "green_club_chair", at, r.y, r.z0 + 1, "north");
			onTop(r, at, r.z0, "wennberg_journal", "south", false);
			if (put(r, "round_table", at + 2, r.y, r.z0, "north")) {
				put(r, "bankers_lamp", at + 2, r.y + 1, r.z0, "south");
			}
		}
		for (int[] c : r.edge(3)) {
			if (wallBehind(r, c[0], c[1], 3, r.y, 3) && free(r, c[0], r.y, c[1]) && !clear.contains(key(c[0], r.y, c[1]))) {
				for (int yy = r.y; yy <= r.y + 2; yy++) {
					p.set(c[0], yy, c[1], "rauk:bookcase[facing=east]");
					placed.put(key(c[0], yy, c[1]), null);
				}
			}
		}
		placed.values().removeIf(v -> v == null);
		corner(r, "globe");
		rugMiddle(r, "persian_rug", 2);
		scatter(r, "scattered_papers", 2);
	}

	/**
	 * The lockdown control room (puzzle 5): his machinery, a desk with the end of his journal on it,
	 * the code's second half and the lockdown key on a table, and him, where he lay down.
	 */
	private void controlRoom(Room r) {
		for (int i = 0; i < 3; i++) {
			alongWall(r, 1, "filing_cabinet", false);
		}
		int at = alongWall(r, 0, "writing_desk", true);
		if (at >= 0) {
			put(r, "kitchen_chair", at, r.y, r.z0 + 1, "north");
			put(r, "wennberg_last_pages", at, r.y + 1, r.z0, "south");
		}
		int t = alongWall(r, 2, "round_table", true);
		if (t >= 0) {
			put(r, "code_card_2", t, r.y + 1, r.z1, "north");
			put(r, "lockdown_key_lying", t + 1, r.y, r.z1 - 1, "north");
		}
		put(r, "wennberg_remains", (r.x0 + r.x1) / 2, r.y, (r.z0 + r.z1) / 2, "west");
		ceilingLamp(r, "caged_bulb");
	}

	private void wennbergsBedroom(Room r) {
		int back = 1;
		alongWall(r, back, "four_poster_bed", true);
		alongWall(r, 0, "nightstand", false);
		alongWall(r, 2, "dresser", false);
		corner(r, "silk_lamp");
	}

	private void penthouseLobby(Room r) {
		alongWall(r, 0, "console_table", true);
		onWall(r, 0, "wall_mirror", (r.x0 + r.x1) / 2, r.y + 1, 0);
		corner(r, "potted_palm");
		ceilingLamp(r, "pendant_lantern");
	}

	private void glasshouse(Room r) {
		for (int x = r.x0 + 1; x <= r.x1 - 1; x += 3) {
			put(r, "potted_palm", x, r.y, r.z0 + 1, "north");
			put(r, "potted_palm", x, r.y, r.z1 - 1, "north");
		}
		put(r, "round_table", (r.x0 + r.x1) / 2, r.y, (r.z0 + r.z1) / 2, "north");
		put(r, "kitchen_chair", (r.x0 + r.x1) / 2 - 1, r.y, (r.z0 + r.z1) / 2, "east");
		put(r, "candles", (r.x0 + r.x1) / 2, r.y + 1, (r.z0 + r.z1) / 2, "north");
	}

	private void linenRoom(Room r) {
		for (int side = 0; side < 4; side++) {
			for (int[] c : r.edge(side)) {
				if (wallBehind(r, c[0], c[1], side, r.y, 2) && free(r, c[0], r.y, c[1]) && free(r, c[0], r.y + 1, c[1]) && side != r.doorSide()) {
					p.set(c[0], r.y, c[1], "rauk:linen_shelves[facing=" + FACING[opposite(side)] + "]");
					p.set(c[0], r.y + 1, c[1], "rauk:linen_shelves[facing=" + FACING[opposite(side)] + "]");
					placed.put(key(c[0], r.y, c[1]), null);
					placed.put(key(c[0], r.y + 1, c[1]), null);
				}
			}
		}
		placed.values().removeIf(v -> v == null);
		scatter(r, "laundry_basket", 2);
		ceilingLamp(r, "caged_bulb");
	}

	// --- corridors ---------------------------------------------------------------------------------------

	/**
	 * A corridor: dim sconces every few doors along the good ones, portraits between; bare bulbs
	 * along the service passages and the attic; nothing that burned; plaster down where it's worn.
	 */
	private void corridor(Room r) {
		boolean alongX = r.width() >= r.depth();
		int[] sides = alongX ? new int[] {0, 2} : new int[] {3, 1};
		switch (r.style()) {
			case COZY, COZY_RED, WORN, LOBBY, PENTHOUSE -> {
				alongWallEvery(r, sides[0], "wall_sconce", r.y + 1, 8, 2);
				alongWallEvery(r, sides[1], "wall_sconce", r.y + 1, 8, 6);
				alongWallEvery(r, sides[0], "portrait", r.y + 1, 8, 6);
				alongWallEvery(r, sides[1], rnd.nextBoolean() ? "seascape" : "portrait", r.y + 1, 8, 2 + 4 * rnd.nextInt(2) + 1);
				if (r.style() == Style.WORN) {
					scatter(r, "plaster_rubble", 1);
				}
			}
			case RUINED -> {
				scatter(r, "ash_heap", 3);
				scatter(r, "plaster_rubble", 3);
			}
			default -> ceilingLamp(r, "caged_bulb");
		}
	}

	// --- the service level and machinery ----------------------------------------------------------------------

	private void coldRoom(Room r) {
		for (int x = r.x0 + 2; x <= r.x1 - 2; x += 3) {
			for (int z = r.z0 + 2; z <= r.z1 - 3; z += 2) {
				put(r, "meat_hook", x, r.a.y1(), z, "north");
			}
		}
		crates(r, 0, 4);
		scatter(r, "sacks", 1);
		ceilingLamp(r, "caged_bulb");
	}

	private void porterLodge(Room r) {
		int at = alongWall(r, 0, "writing_desk", true);
		if (at >= 0) {
			put(r, "kitchen_chair", at, r.y, r.z0 + 1, "north");
			put(r, "candles", at, r.y + 1, r.z0, "north", 0, true);
		}
		alongWall(r, 3, "iron_bed", false);
		corner(r, "iron_stove");
		for (int[] c : r.edge(1)) {
			if (wallBehind(r, c[0], c[1], 1, r.y + 1, 1) && free(r, c[0], r.y + 1, c[1])) {
				p.set(c[0], r.y + 1, c[1], "rauk:pigeonholes[facing=west]");
				placed.put(key(c[0], r.y + 1, c[1]), null);
				break;
			}
		}
		placed.values().removeIf(v -> v == null);
		onWall(r, 2, "notice_board", (r.x0 + r.x1) / 2, r.y + 1, 0);
	}

	private void wineCellar(Room r) {
		for (int side : new int[] {0, 2}) {
			for (int[] c : r.edge(side)) {
				if (wallBehind(r, c[0], c[1], side, r.y, 3) && free(r, c[0], r.y, c[1]) && free(r, c[0], r.y + 2, c[1])) {
					for (int yy = r.y; yy <= r.y + 2; yy++) {
						p.set(c[0], yy, c[1], "rauk:wine_rack[facing=" + FACING[opposite(side)] + "]");
						placed.put(key(c[0], yy, c[1]), null);
					}
				}
			}
		}
		placed.values().removeIf(v -> v == null);
		scatter(r, "bottle_candle", 2);
		scatter(r, "broken_glass", 1);
		ceilingLamp(r, "caged_bulb");
	}

	private void coalStore(Room r) {
		for (int x = r.x0; x <= r.x1; x++) {
			if (x != 5 && x != 4 && x != 6) {
				put(r, "coal_heap", x, r.y, r.z1, "north");
			}
		}
		put(r, "coal_heap", r.x1, r.y, r.z1 - 1, "north");
		put(r, "coal_heap", r.x1, r.y, r.z0, "north");
	}

	/** The staff dormitory you wake up in: iron bunks down both walls, lockers, a candle left burning. */
	private void staffDormitory(Room r) {
		for (int side : new int[] {3, 1}) {
			for (int z = r.z0 + 1; z <= r.z1 - 1; z += 3) {
				againstWall(r, side, "iron_bunk", z, 0, false);
			}
		}
		alongWall(r, 2, "staff_locker", false);
		alongWall(r, 2, "staff_locker", false);
		alongWall(r, 2, "washstand", true);
		put(r, "pine_table", (r.x0 + r.x1) / 2, r.y, (r.z0 + r.z1) / 2, "north");
		put(r, "candles", (r.x0 + r.x1) / 2, r.y + 1, (r.z0 + r.z1) / 2, "north", 0, true);
		put(r, "kitchen_chair", (r.x0 + r.x1) / 2, r.y, (r.z0 + r.z1) / 2 + 1, "north");
		// Next door to the way in, by the candle: the first flashlight (hotel design 4d).
		put(r, "pine_table", (r.x0 + r.x1) / 2 + 1, r.y, (r.z0 + r.z1) / 2, "north");
		put(r, "flashlight_lying", (r.x0 + r.x1) / 2 + 1, r.y + 1, (r.z0 + r.z1) / 2, "west");
		ceilingLamp(r, "caged_bulb");
	}

	private void washroom(Room r) {
		for (int z = r.z0 + 1; z <= r.z1 - 1; z += 2) {
			againstWall(r, 3, "toilet", z, 0, false);
		}
		for (int x = r.x0 + 1; x <= r.x1 - 1; x += 2) {
			againstWall(r, 2, "pedestal_sink", x, 0, false);
		}
		onWall(r, 2, "wall_mirror", r.x0 + 3, r.y + 1, 0);
		ceilingLamp(r, "caged_bulb");
	}

	private void laundry(Room r) {
		for (int x = r.x0 + 1; x <= r.x1 - 1; x += 3) {
			againstWall(r, 0, "laundry_copper", x, 0, false);
		}
		againstWall(r, 2, "mangle", r.x0 + 2, 0, false);
		againstWall(r, 2, "mangle", r.x0 + 5, 0, false);
		for (int x = r.x0 + 4; x <= r.x1 - 4; x++) {
			put(r, "pine_table", x, r.y, (r.z0 + r.z1) / 2, "north");
		}
		scatter(r, "laundry_basket", 3);
		ceilingLamp(r, "caged_bulb");
	}

	private void housekeeping(Room r) {
		linenRoom(r);
		for (int x = r.x0 + 4; x <= r.x1 - 4; x++) {
			put(r, "pine_table", x, r.y, (r.z0 + r.z1) / 2, "north");
		}
	}

	/** The staff mess: one long table, benches either side, the stove, the notices still pinned up. */
	private void staffMess(Room r) {
		int mz = (r.z0 + r.z1) / 2;
		for (int x = r.x0 + 2; x <= r.x1 - 2; x++) {
			put(r, "pine_table", x, r.y, mz, "north");
			put(r, "staff_bench", x, r.y, mz - 1, "south");
			put(r, "staff_bench", x, r.y, mz + 1, "north");
		}
		// The second flashlight, left at the end of the table.
		put(r, "flashlight_lying", r.x1 - 2, r.y + 1, mz, "south");
		corner(r, "iron_stove");
		onWall(r, 0, "notice_board", r.x0 + 3, r.y + 1, 0);
		onWall(r, 0, "wall_clock", r.x1 - 3, r.y + 2, 0);
		ceilingLamp(r, "caged_bulb");
	}

	private void bootRoom(Room r) {
		for (int side = 0; side < 4; side++) {
			if (side == r.doorSide()) {
				continue;
			}
			for (int i = 0; i < 4; i++) {
				alongWall(r, side, "staff_locker", false);
			}
		}
		put(r, "staff_bench", (r.x0 + r.x1) / 2, r.y, (r.z0 + r.z1) / 2, "north");
		put(r, "staff_bench", (r.x0 + r.x1) / 2 + 1, r.y, (r.z0 + r.z1) / 2, "north");
		ceilingLamp(r, "caged_bulb");
	}

	private void spa(Room r) {
		for (int x = 56; x <= 74; x += 4) {
			put(r, "potted_palm", x, r.y, 27, "north");
			put(r, "potted_palm", x, r.y, 44, "north");
		}
		for (int z = 31; z <= 40; z += 3) {
			put(r, "hall_bench", 54, r.y, z, "east");
			put(r, "hall_bench", 76, r.y, z, "west");
		}
		ceilingLamp(r, "caged_bulb");
	}

	/** The boiler room: two boilers on their sides along the north wall, coal heaped by them. */
	private void boilerRoom(Room r) {
		putAt(r, "boiler", r.x0 + 2, r.y, r.z0, "south", 0, false);
		putAt(r, "boiler", r.x0 + 6, r.y, r.z0, "south", 0, false);
		for (int x = r.x0 + 1; x <= r.x0 + 8; x++) {
			put(r, "coal_heap", x, r.y, r.z0 + 4, "north");
		}
		int table = alongWall(r, 2, "pine_table", false);
		if (r.a.name().equals("Generator and boiler room") && table >= 0) {
			put(r, "engineers_log", table, r.y + 1, r.z1, "north");
		}
		ceilingLamp(r, "caged_bulb");
	}

	/**
	 * The generator and boiler room (puzzle 2): the boilers and their coal, and the generator in the
	 * middle of the floor, its fuel pump, choke and crank on its front, the engineer's log on the
	 * table by the wall.
	 */
	private void generatorRoom(Room r) {
		boilerRoom(r);
		int x = 36, z = 16;
		putAt(r, "generator", x, r.y, z, "south", 0, false);
		String[] controls = {"gen_primer", "gen_choke", "gen_crank"};
		for (int i = 0; i < 3; i++) {
			put(r, controls[i], x + i, r.y + 1, z + 2, "south");
		}
		// Clear in front of it to work the controls.
		for (int i = 0; i < 3; i++) {
			clear.add(key(x + i, r.y, z + 2));
			clear.add(key(x + i, r.y, z + 3));
		}
	}

	/** The fuse room (puzzle 2): the board on the wall, empty, and the engineer's spares long gone. */
	private void fuseRoom(Room r) {
		onWall(r, 1, "fuse_board", (r.z0 + r.z1) / 2, r.y + 1, 0);
		crates(r, 3, 2);
		ceilingLamp(r, "caged_bulb");
	}

	/**
	 * The cold room, locked since that night (puzzle 2): one of Rauk's larders. The night porter is
	 * still in it, what is left of him, his cap by his skull, old blood dried black all round, and the
	 * fuse he took in with him on the floor beside him.
	 */
	private void porterLarder(Room r) {
		int x = (r.x0 + r.x1) / 2, z = r.z0 + 3;
		put(r, "porter_remains", x, r.y, z, "east");
		put(r, "fuse_lying", x + 1, r.y, z - 1, "north");
		for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {1, 1}, {-1, 1}, {-1, -1}, {-2, 0}, {0, 2}, {1, 2}, {0, 3}, {0, 4},
				{1, 5}, {1, 6}, {1, 8}}) {
			put(r, "old_blood", x + d[0], r.y, z + d[1], "north");
		}
		coldRoom(r);
	}

	// --- the attic ----------------------------------------------------------------------------------------

	private void servantsDormitory(Room r) {
		for (int side : new int[] {0, 2}) {
			for (int x = r.x0 + 1; x <= r.x1 - 1; x += 3) {
				againstWall(r, side, "iron_bed", x, 0, false);
			}
		}
		for (int x = r.x0 + 2; x <= r.x1 - 2; x += 6) {
			put(r, "steamer_trunk", x, r.y, (r.z0 + r.z1) / 2, FACING[rnd.nextInt(4)]);
		}
		alongWall(r, 3, "washstand", true);
		scatter(r, "plaster_rubble", 2);
		ceilingLamp(r, "caged_bulb");
	}

	/** The old nursery, behind the great window: cots, a rocking horse, dolls sitting where they were left. */
	private void nursery(Room r) {
		for (int x = r.x0 + 1; x <= r.x1 - 1; x += 3) {
			againstWall(r, 0, "crib", x, 0, false);
		}
		put(r, "rocking_horse", r.x0 + 3, r.y, r.z1 - 2, "east");
		put(r, "round_table", r.x1 - 3, r.y, r.z1 - 3, "north");
		put(r, "porcelain_doll", r.x1 - 3, r.y + 1, r.z1 - 3, "north");
		put(r, "kitchen_chair", r.x1 - 4, r.y, r.z1 - 3, "east");
		put(r, "porcelain_doll", r.x0 + 1, r.y, r.z1, "north");
		put(r, "porcelain_doll", r.x1, r.y, r.z0 + 3, "west");
		rugMiddle(r, "worn_rug", 2);
		curtains(r, "green_curtain");
		scatter(r, "plaster_rubble", 1);
		ceilingLamp(r, "pendant_lantern");
	}

	private void tankRoom(Room r) {
		putAt(r, "boiler", r.x0 + 1, r.y, r.z0, "south", 0, false);
		ceilingLamp(r, "caged_bulb");
	}

	private void lumberRoom(Room r) {
		for (int side = 0; side < 4; side++) {
			alongWall(r, side, "sheeted_sofa", false);
			crates(r, side, 2);
			alongWall(r, side, "sheeted_armchair", false);
		}
		scatter(r, "broken_chair", 3);
		scatter(r, "ash_heap", 4);
		scatter(r, "plaster_rubble", 4);
		scatter(r, "steamer_trunk", 2);
	}
}

package rauk.com.client.dev;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import rauk.com.hotel.HotelBuilder;
import rauk.com.hotel.HotelPlan;

/**
 * Checks the hotel's outside for blocks out of place ({@code ./gradlew auditHotel}): ornament that
 * hangs off nothing (a cornice or pilaster with no wall behind it, a railing, balustrade or column
 * with nothing under it), blocks floating free, slanted pieces without the two walls they belong
 * in, and anything left as raw masonry. Each kind of fault counted, with the first few places.
 */
public final class HotelAudit {
	private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private HotelAudit() {
	}

	public static void main(String[] args) {
		HotelPlan p = HotelBuilder.plan();
		if (args.length == 6) {
			probe(p, args);
			return;
		}
		if (args.length == 1 && args[0].startsWith("rooms")) {
			// Every space whose name contains what follows "rooms:", with its level and bounds.
			String like = args[0].contains(":") ? args[0].substring(6) : "";
			for (var a : p.areas) {
				if (a.name().contains(like)) {
					System.out.println(a.name() + " | " + a.level() + " | " + a.style() + " | x " + a.x0() + ".." + a.x1() + " y " + a.y0() + ".." + a.y1()
							+ " z " + a.z0() + ".." + a.z1());
				}
			}
			return;
		}
		Map<String, List<String>> faults = new LinkedHashMap<>();
		for (int y = HotelPlan.Y0; y < HotelPlan.Y0 + HotelPlan.SY; y++) {
			for (int z = HotelPlan.Z0; z < HotelPlan.Z0 + HotelPlan.SZ; z++) {
				for (int x = HotelPlan.X0; x < HotelPlan.X0 + HotelPlan.SX; x++) {
					String s = p.get(x, y, z);
					if (s.isEmpty() || p.isAir(x, y, z)) {
						continue;
					}
					String id = HotelPlan.blockId(s);
					String where = x + " " + y + " " + z;
					if (id.equals("#solid")) {
						add(faults, "raw masonry left", where);
						continue;
					}
					boolean outside = false;
					for (int[] d : SIDES) {
						outside |= p.at(x + d[0], y, z + d[1]) == 0;
					}
					outside |= p.at(x, y + 1, z) == 0;
					if (!outside) {
						continue;
					}
					String facing = prop(s, "facing");
					if ((id.endsWith("cornice") || id.endsWith("pilaster")) && facing != null) {
						int[] back = back(facing);
						if (!solid(p, x + back[0], y, z + back[1])) {
							add(faults, id + " with no wall behind", where);
						}
					}
					if ((id.endsWith("balustrade") || id.endsWith("_column") || id.equals("rauk:wrought_iron_railing") || id.endsWith("finial"))
							&& !solid(p, x, y - 1, z) && !id.equals(HotelPlan.blockId(p.get(x, y - 1, z)))) {
						add(faults, id + " standing on nothing", where);
					}
					if (id.startsWith("rauk:slant_") && facing != null) {
						int[][] walls = slantWalls(facing);
						if (!solid(p, x + walls[0][0], y, z + walls[0][1]) || !solid(p, x + walls[1][0], y, z + walls[1][1])) {
							add(faults, "slant without its walls", where + " " + s + " by " + p.get(x + walls[0][0], y, z + walls[0][1]) + " / "
									+ p.get(x + walls[1][0], y, z + walls[1][1]));
						}
					}
					boolean alone = true;
					for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
						String n = p.get(x + d[0], y + d[1], z + d[2]);
						alone &= n.isEmpty() || p.isAir(x + d[0], y + d[1], z + d[2]);
					}
					if (alone && !id.contains("festoon") && !id.contains("letter")) {
						add(faults, "floating " + id, where);
					}
				}
			}
		}
		if (faults.isEmpty()) {
			System.out.println("Hotel audit: nothing out of place.");
		}
		faults.forEach((k, v) -> System.out.println("Hotel audit: " + v.size() + " x " + k + ": " + String.join(" | ", v.subList(0, Math.min(8, v.size())))));
	}

	/**
	 * A box of the plan printed a layer at a time ({@code --args="x0 y0 z0 x1 y1 z1"}), x across and z
	 * down: # stone, . air, s stair, d door, t trapdoor, l ladder, f furniture, other blocks by their
	 * first letter; and which spaces own the cells.
	 */
	private static void probe(HotelPlan p, String[] args) {
		int[] a = new int[6];
		for (int i = 0; i < 6; i++) {
			a[i] = Integer.parseInt(args[i]);
		}
		java.util.Set<String> owners = new java.util.LinkedHashSet<>();
		for (int y = a[4]; y >= a[1]; y--) {
			System.out.println("y " + y);
			for (int z = a[2]; z <= a[5]; z++) {
				StringBuilder row = new StringBuilder(String.format("%4d ", z));
				for (int x = a[0]; x <= a[3]; x++) {
					String s = p.get(x, y, z);
					String id = s.isEmpty() ? "" : HotelPlan.blockId(s);
					char c = s.isEmpty() || id.equals("#solid") ? '#' : p.isAir(x, y, z) ? '.' : id.endsWith("_stairs") ? 's'
							: id.endsWith("trapdoor") ? 't' : id.endsWith("_door") ? 'd' : id.contains("ladder") ? 'l'
							: rauk.com.hotel.HotelFurniture.is(id) ? 'f' : Character.toUpperCase(id.charAt(id.indexOf(':') + 1));
					row.append(c);
					if (p.owner(x, y, z) != null) {
						owners.add(p.owner(x, y, z).name());
					}
				}
				System.out.println(row);
			}
		}
		System.out.println("owners: " + owners);
		java.util.Set<String> blocks = new java.util.TreeSet<>();
		for (int y = a[1]; y <= a[4]; y++) {
			for (int z = a[2]; z <= a[5]; z++) {
				for (int x = a[0]; x <= a[3]; x++) {
					blocks.add(p.get(x, y, z));
				}
			}
		}
		System.out.println("blocks: " + blocks);
	}

	private static void add(Map<String, List<String>> faults, String kind, String where) {
		faults.computeIfAbsent(kind, k -> new ArrayList<>()).add(where);
	}

	private static String prop(String state, String name) {
		int i = state.indexOf(name + "=");
		if (i < 0) {
			return null;
		}
		int end = state.indexOf(',', i);
		if (end < 0) {
			end = state.indexOf(']', i);
		}
		return state.substring(i + name.length() + 1, end);
	}

	/** The wall behind a piece facing a way: the other way. */
	private static int[] back(String facing) {
		return switch (facing) {
			case "north" -> new int[] {0, 1};
			case "south" -> new int[] {0, -1};
			case "east" -> new int[] {-1, 0};
			default -> new int[] {1, 0};
		};
	}

	/** The two walls a slanted piece stands against: it looks away from them (facing north, the walls east and south). */
	private static int[][] slantWalls(String facing) {
		return switch (facing) {
			case "north" -> new int[][] {{1, 0}, {0, 1}};
			case "east" -> new int[][] {{0, 1}, {-1, 0}};
			case "south" -> new int[][] {{-1, 0}, {0, -1}};
			default -> new int[][] {{0, -1}, {1, 0}};
		};
	}

	private static boolean solid(HotelPlan p, int x, int y, int z) {
		String s = p.get(x, y, z);
		if (s.isEmpty() || p.isAir(x, y, z)) {
			return false;
		}
		return !(s.contains("door") && !s.contains("trapdoor") || s.contains("pane") || s.contains("railing") || s.contains("festoon") || s.contains("letter")
				|| HotelPlan.blockId(s).equals("minecraft:water") || s.contains("ladder") || s.contains("bars"));
	}
}

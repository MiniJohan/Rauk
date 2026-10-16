package rauk.com.hotel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import rauk.com.hotel.HotelPlan.Area;

/**
 * The hotel as a map of its spaces and the openings between them (doors, arches, the lift and
 * stair doors, holes in the floors, the gaps up into the crawlspaces), worked out once from the
 * plan. Rauk's route finder searches block by block, every floor, wall and ceiling, which is fine
 * across a room or two but runs out of breath across the whole building; this picks the run of
 * openings to go through, and the route finder only has to reach the next one.
 */
public final class HotelNav {
	/** An opening between two spaces: one block of it near its middle, at its lowest. Plan cells. */
	public record Portal(int id, int a, int b, int x, int y, int z) {
		double to(double px, double py, double pz) {
			double dx = px - (x + 0.5), dz = pz - (z + 0.5), dy = py - y;
			// Climbing costs more than walking.
			return Math.sqrt(dx * dx + dz * dz) + Math.abs(dy) * 1.5;
		}
	}

	/** Who is getting about: Rauk, who climbs, or someone on foot, who takes the stairs and the doors. */
	public enum Mode { CLIMBER, WALKER }

	/** A run of openings, and what getting along it costs (about a block walked for each). */
	public record Route(List<Portal> portals, double cost) {
	}

	private static HotelNav built;
	private static String builtFor = "";

	private final HotelPlan plan;
	/** Blocks that aren't as the plan has them: the lift cars, wherever they are standing now, and the doors unlocked. */
	private final Map<Long, String> overrides;
	public final List<Portal> portals = new ArrayList<>();
	private final List<List<Integer>> byArea = new ArrayList<>();
	/** Each open cell's part of its space, and the space each part is of. */
	private final Map<Long, Integer> parts = new HashMap<>();
	private final List<Area> partArea = new ArrayList<>();

	/** The hotel with its lift cars where the plan has them (at their home floors), its locked doors locked. */
	public static synchronized HotelNav get() {
		return get(Map.of(), Set.of());
	}

	/**
	 * The hotel with each lift's car standing at the floor given for it (lift id to floor; a lift
	 * left out is at home), and the locked doors given (plan cells) opened with their keys. Worked
	 * out again only when a car has moved or a door been unlocked.
	 */
	public static synchronized HotelNav get(Map<String, HotelPlan.Level> cars, Set<Long> unlocked) {
		StringBuilder key = new StringBuilder();
		for (LiftCars.Lift lift : LiftCars.ALL) {
			key.append(lift.id()).append('=').append(cars.getOrDefault(lift.id(), lift.home())).append(';');
		}
		unlocked.stream().sorted().forEach(c -> key.append(c).append(','));
		if (built == null || !key.toString().equals(builtFor)) {
			HotelPlan plan = HotelBuilder.plan();
			Map<Long, String> overrides = carsAt(plan, cars);
			for (long cell : unlocked) {
				int[] c = HotelPlan.cellXYZ(cell);
				overrides.put(cell, plan.get(c[0], c[1], c[2]).replaceAll("lock=[a-z]+", "lock=none"));
			}
			built = new HotelNav(plan, overrides);
			builtFor = key.toString();
		}
		return built;
	}

	/** The blocks that differ from the plan with the cars at these floors: home cleared, the car where it is. */
	static Map<Long, String> carsAt(HotelPlan plan, Map<String, HotelPlan.Level> cars) {
		Map<Long, String> out = new HashMap<>();
		for (LiftCars.Lift lift : LiftCars.ALL) {
			HotelPlan.Level at = cars.getOrDefault(lift.id(), lift.home());
			if (at == lift.home()) {
				continue;
			}
			for (LiftCars.Part part : LiftCars.car(lift, false)) {
				long home = HotelPlan.cellKey(part.x(), lift.home().base + part.dy(), part.z());
				out.put(home, plan.underCar.getOrDefault(home, "minecraft:air"));
			}
			for (LiftCars.Part part : LiftCars.car(lift, false)) {
				out.put(HotelPlan.cellKey(part.x(), at.base + part.dy(), part.z()), part.block());
			}
		}
		return out;
	}

	/** The block in a cell as things stand (the plan, with the cars moved). */
	private String block(int x, int y, int z) {
		String o = overrides.isEmpty() ? null : overrides.get(HotelPlan.cellKey(x, y, z));
		return o != null ? o : plan.get(x, y, z);
	}

	private HotelNav(HotelPlan plan, Map<Long, String> overrides) {
		this.plan = plan;
		this.overrides = overrides;
		// Each space as the parts of it that actually join up: a lift's car standing in its shaft
		// cuts the shaft in two, and the part inside the car is a dead end.
		// A lift shaft walled in something it can't climb (the guest lift's lattice cage) is no way
		// between floors for it: each floor's stretch of it is a space of its own.
		int[][] six = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (int y = HotelPlan.Y0; y < HotelPlan.Y0 + HotelPlan.SY; y++) {
			for (int z = HotelPlan.Z0; z < HotelPlan.Z0 + HotelPlan.SZ; z++) {
				for (int x = HotelPlan.X0; x < HotelPlan.X0 + HotelPlan.SX; x++) {
					Area owner = plan.owner(x, y, z);
					if (owner == null || !open(block(x, y, z)) || parts.containsKey(key(x, y, z))) {
						continue;
					}
					boolean caged = owner.kind() == HotelPlan.Kind.LIFT && caged(plan, owner);
					int id = partArea.size();
					partArea.add(owner);
					byArea.add(new ArrayList<>());
					ArrayDeque<int[]> todo = new ArrayDeque<>();
					todo.add(new int[] {x, y, z});
					parts.put(key(x, y, z), id);
					while (!todo.isEmpty()) {
						int[] c = todo.poll();
						for (int[] d : six) {
							int nx = c[0] + d[0], ny = c[1] + d[1], nz = c[2] + d[2];
							long k = key(nx, ny, nz);
							if (caged && floor(ny) != floor(c[1])) {
								continue;
							}
							if (HotelPlan.inBox(nx, ny, nz) && !parts.containsKey(k) && plan.owner(nx, ny, nz) == owner && open(block(nx, ny, nz))) {
								parts.put(k, id);
								todo.add(new int[] {nx, ny, nz});
							}
						}
					}
				}
			}
		}
		// Every pair of neighbouring open cells that belong to different spaces is part of a way
		// between them; the cells of each pair of spaces are then grouped into separate openings.
		Map<Long, Set<Long>> boundary = new HashMap<>();
		int[][] steps = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
		for (int y = HotelPlan.Y0; y < HotelPlan.Y0 + HotelPlan.SY; y++) {
			for (int z = HotelPlan.Z0; z < HotelPlan.Z0 + HotelPlan.SZ; z++) {
				for (int x = HotelPlan.X0; x < HotelPlan.X0 + HotelPlan.SX; x++) {
					int a = area(x, y, z);
					if (a < 0) {
						continue;
					}
					for (int[] s : steps) {
						int b = area(x + s[0], y + s[1], z + s[2]);
						if (b >= 0 && b != a && (s[1] == 0 || upward(a, b, x, y, z))) {
							long pair = (long) Math.min(a, b) << 32 | Math.max(a, b);
							Set<Long> cells = boundary.computeIfAbsent(pair, k -> new HashSet<>());
							cells.add(key(x, y, z));
							cells.add(key(x + s[0], y + s[1], z + s[2]));
						}
					}
				}
			}
		}
		for (var e : boundary.entrySet()) {
			int a = (int) (e.getKey() >> 32), b = (int) (long) e.getKey();
			for (List<Long> group : groups(e.getValue())) {
				Portal p = portal(a, b, group);
				p = new Portal(portals.size(), p.a(), p.b(), p.x(), p.y(), p.z());
				byArea.get(a).add(portals.size());
				byArea.get(b).add(portals.size());
				portals.add(p);
			}
		}
	}

	private static long key(int x, int y, int z) {
		return ((long) (x - HotelPlan.X0) << 40) | ((long) (y - HotelPlan.Y0) << 20) | (z - HotelPlan.Z0);
	}

	/** Which floor a height is on: each runs from its floor up to just under the next. */
	private static int floor(int y) {
		return Math.floorDiv(y - HotelPlan.Level.B2.base, HotelPlan.Level.L1.base - HotelPlan.Level.B1.base);
	}

	/** Whether a shaft's walls are a cage it can't climb (lattice, brass, glass) rather than masonry. */
	private static boolean caged(HotelPlan plan, Area a) {
		int y = a.y0() + 3;
		for (int z = a.z0(); z <= a.z1(); z++) {
			String wall = plan.get(a.x0() - 1, y, z);
			if (wall.contains("iron_lattice") || wall.contains("rauk:brass") || wall.contains("glass")) {
				return true;
			}
		}
		return false;
	}

	private static int[] unkey(long k) {
		return new int[] {(int) (k >> 40) + HotelPlan.X0, (int) ((k >> 20) & 0xFFFFF) + HotelPlan.Y0, (int) (k & 0xFFFFF) + HotelPlan.Z0};
	}

	/** The part of a space an open cell (one something can be in) belongs to, or -1. */
	private int area(int x, int y, int z) {
		Integer part = parts.get(key(x, y, z));
		return part == null ? -1 : part;
	}

	/**
	 * Whether going up or down from one space into the one over it is a way at all: by a stair,
	 * up a shaft or stairwell, through a hole into a crawlspace or a burned floor. Not across the
	 * open middle of the atrium (nothing there to climb), and not up a ladder (too narrow for it).
	 * Out of water (the pool) it hauls itself up over the edge.
	 */
	private boolean upward(int a, int b, int x, int y, int z) {
		for (int i : new int[] {a, b}) {
			switch (partArea.get(i).kind()) {
				case STAIRS, LIFT, CRAWL, TUNNEL -> {
					return true;
				}
				default -> {
				}
			}
		}
		// Up out of water (the pool) it hauls itself over the edge.
		return block(x, y, z).contains("_stairs") || block(x, y, z).contains("water");
	}

	/**
	 * Whether a block is one something can be in: air, water, a door, a stair, a ladder; and the
	 * furniture, which stands in rooms rather than making their walls.
	 */
	private static boolean open(String block) {
		String id = HotelPlan.blockId(block);
		if (id.endsWith("_door") && block.contains("lock=") && !block.contains("lock=none")) {
			// Locked: a wall, until it's opened with its key.
			return false;
		}
		return id.equals("minecraft:air") || id.equals("minecraft:water") || id.equals("minecraft:light") || id.endsWith("_door") || id.endsWith("_stairs")
				|| id.endsWith("ladder") || id.endsWith("_trapdoor") || id.equals("rauk:runner") || id.equals("rauk:light_switch")
				|| HotelFurniture.is(id);
	}

	/** Cells split into groups that touch (corners included). */
	private static List<List<Long>> groups(Set<Long> cells) {
		List<List<Long>> out = new ArrayList<>();
		Set<Long> left = new HashSet<>(cells);
		while (!left.isEmpty()) {
			long first = left.iterator().next();
			left.remove(first);
			List<Long> group = new ArrayList<>();
			ArrayDeque<Long> todo = new ArrayDeque<>(List.of(first));
			while (!todo.isEmpty()) {
				long k = todo.poll();
				group.add(k);
				int[] c = unkey(k);
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						for (int dz = -1; dz <= 1; dz++) {
							long n = key(c[0] + dx, c[1] + dy, c[2] + dz);
							if (left.remove(n)) {
								todo.add(n);
							}
						}
					}
				}
			}
			out.add(group);
		}
		return out;
	}

	/** An opening's place: the cell nearest its middle across, at the opening's lowest level. */
	private static Portal portal(int a, int b, List<Long> group) {
		double cx = 0, cz = 0;
		int low = Integer.MAX_VALUE;
		for (long k : group) {
			int[] c = unkey(k);
			cx += c[0] + 0.5;
			cz += c[2] + 0.5;
			low = Math.min(low, c[1]);
		}
		cx /= group.size();
		cz /= group.size();
		int[] best = null;
		double bestD = Double.MAX_VALUE;
		for (long k : group) {
			int[] c = unkey(k);
			double d = Math.pow(c[0] + 0.5 - cx, 2) + Math.pow(c[2] + 0.5 - cz, 2) + (c[1] - low) * 4;
			if (d < bestD) {
				bestD = d;
				best = c;
			}
		}
		return new Portal(-1, a, b, best[0], best[1], best[2]);
	}

	/** Every space that can be got to from a point (Rauk's way, through any opening), as parts. */
	public Set<Integer> reachable(double x, double y, double z) {
		Set<Integer> seen = new HashSet<>();
		int start = areaAt(x, y, z);
		if (start < 0) {
			return seen;
		}
		seen.add(start);
		ArrayDeque<Integer> todo = new ArrayDeque<>(List.of(start));
		while (!todo.isEmpty()) {
			int a = todo.poll();
			for (int i : byArea.get(a)) {
				Portal p = portals.get(i);
				int o = p.a() == a ? p.b() : p.a();
				if (seen.add(o)) {
					todo.add(o);
				}
			}
		}
		return seen;
	}

	/** The space a point is in (or just above or below it), or -1 outside the hotel's spaces. */
	public int areaAt(double x, double y, double z) {
		int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
		for (int dy : new int[] {0, 1, -1, 2}) {
			int a = area(bx, by + dy, bz);
			if (a >= 0) {
				return a;
			}
		}
		return -1;
	}

	/**
	 * The openings to go through, in order, from one point to another (plan coordinates): empty
	 * when both are in the same space, null when there's no way or either is outside the hotel.
	 */
	public List<Portal> route(double fx, double fy, double fz, double tx, double ty, double tz) {
		return route(fx, fy, fz, tx, ty, tz, Set.of());
	}

	/** The same, keeping away from some openings (ones it has found it can't get through). */
	public List<Portal> route(double fx, double fy, double fz, double tx, double ty, double tz, Set<Integer> avoid) {
		Route r = route(fx, fy, fz, tx, ty, tz, p -> avoid.contains(p.id()), Mode.CLIMBER);
		return r == null ? null : r.portals();
	}

	/** Whether someone getting about this way can use this space at all (on foot: no shafts, no crawlspaces). */
	private boolean usable(int part, Mode mode) {
		return mode == Mode.CLIMBER || switch (partArea.get(part).kind()) {
			case LIFT, CRAWL, TUNNEL -> false;
			default -> true;
		};
	}

	/** The kind of space a part is. */
	public HotelPlan.Kind kindOf(int part) {
		return partArea.get(part).kind();
	}

	/** The openings of a part of a space. */
	public List<Portal> portalsOf(int part) {
		List<Portal> out = new ArrayList<>();
		for (int i : byArea.get(part)) {
			out.add(portals.get(i));
		}
		return out;
	}

	/**
	 * The cheapest run of openings from one point to another (plan coordinates), for Rauk or for
	 * someone on foot, keeping away from openings it is told to; with what it costs. Empty when
	 * both are in the same space; null when there's no way, or either is outside the hotel.
	 */
	public Route route(double fx, double fy, double fz, double tx, double ty, double tz, java.util.function.Predicate<Portal> avoid, Mode mode) {
		int from = areaAt(fx, fy, fz), to = areaAt(tx, ty, tz);
		if (from < 0 || to < 0) {
			return null;
		}
		if (from == to) {
			return new Route(List.of(), cost(fx, fy, fz, tx, ty, tz, mode));
		}
		int n = portals.size();
		double[] cost = new double[n];
		int[] prev = new int[n];
		Arrays.fill(cost, Double.MAX_VALUE);
		Arrays.fill(prev, -1);
		PriorityQueue<double[]> open = new PriorityQueue<>((p, q) -> Double.compare(p[0], q[0]));
		if (!usable(from, mode) || !usable(to, mode)) {
			return null;
		}
		for (int i : byArea.get(from)) {
			Portal p = portals.get(i);
			if (avoid.test(p) || !usable(p.a(), mode) || !usable(p.b(), mode)) {
				continue;
			}
			cost[i] = cost(p.x() + 0.5, p.y(), p.z() + 0.5, fx, fy, fz, mode);
			open.add(new double[] {cost[i], i});
		}
		double best = Double.MAX_VALUE;
		int last = -1;
		while (!open.isEmpty()) {
			double[] top = open.poll();
			int i = (int) top[1];
			if (top[0] > cost[i] || top[0] >= best) {
				continue;
			}
			Portal p = portals.get(i);
			if (p.a() == to || p.b() == to) {
				double total = cost[i] + cost(p.x() + 0.5, p.y(), p.z() + 0.5, tx, ty, tz, mode);
				if (total < best) {
					best = total;
					last = i;
				}
			}
			for (int side : new int[] {p.a(), p.b()}) {
				for (int j : byArea.get(side)) {
					Portal q = portals.get(j);
					if (avoid.test(q) || !usable(q.a(), mode) || !usable(q.b(), mode)) {
						continue;
					}
					double c = cost[i] + cost(q.x() + 0.5, q.y(), q.z() + 0.5, p.x() + 0.5, p.y(), p.z() + 0.5, mode) + 0.5;
					if (c < cost[j]) {
						cost[j] = c;
						prev[j] = i;
						open.add(new double[] {c, j});
					}
				}
			}
		}
		if (last < 0) {
			return null;
		}
		List<Portal> out = new ArrayList<>();
		for (int i = last; i >= 0; i = prev[i]) {
			out.add(0, portals.get(i));
		}
		return new Route(out, best);
	}

	/**
	 * Getting from one point to another across a space. Climbing (a shaft, a wall) costs half as
	 * much again as walking; on foot, a floor up or down a stairwell is the flights and landings
	 * between, about twice the height.
	 */
	private static double cost(double ax, double ay, double az, double bx, double by, double bz, Mode mode) {
		double dx = ax - bx, dz = az - bz, dy = Math.abs(ay - by);
		return Math.sqrt(dx * dx + dz * dz) + dy * (mode == Mode.WALKER ? 2.2 : 1.5);
	}
}

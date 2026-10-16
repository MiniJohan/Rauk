package rauk.com.hotel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntPredicate;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import rauk.com.Rauk;
import rauk.com.entity.move.SurfaceNode;
import rauk.com.entity.nav.NavBlock;
import rauk.com.entity.nav.NavRules;
import rauk.com.entity.nav.NavView;

/**
 * Rauk's map of the whole hotel (design doc 4.7), worked out from the plan with the same rules its
 * body moves by ({@link NavRules}), away from the game's thread, and again when a lift car moves
 * or a locked door is opened.
 *
 * <ul>
 * <li><b>Places:</b> every place its body can be in the hotel's spaces, floor, wall or ceiling, and
 * every way from one to the next.</li>
 * <li><b>Sections:</b> each space of the plan (a room, a stretch of corridor, a stairwell, a shaft),
 * split into the parts of it that really join up for it (a car standing in a shaft cuts it).</li>
 * <li><b>Doorways:</b> where it really can get from one section into the next, found from its moves,
 * not from where the openings are drawn; and what it costs to cross each section from one doorway
 * to another, by the way it would really go.</li>
 * </ul>
 *
 * <p>A <b>field</b> is worked out back from a place (where the prey was last known to be) to every
 * doorway in the hotel: from wherever Rauk is, it then knows which way is fastest, and the next
 * doorway on from each. Someone on foot gets a map of their own (stairs and doors only), for
 * working out where they can get to and how soon.
 */
public final class HotelGraph {
	private static final Direction[] SURFACES = Direction.values();
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Rauk hotel map");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});
	private static volatile HotelGraph current;
	private static volatile String pending = "";

	/** What the map was made for: the hotel's place and the lift cars and doors as they stood. */
	public final String key;
	public final HotelFrame frame;
	public final HotelPlan plan;
	/** How long it took to work out, in milliseconds. */
	public final long millis;

	// Places: by number; their cell (plan index), side and shape; the section each is in.
	private final Int2IntOpenHashMap byKey;
	private final int[] nodeCell;
	private final byte[] nodeSurface;
	private final short[] nodeCode;
	private final int[] nodePart;
	// Ways between places, as lists: the ways from place i are start[i] to start[i + 1].
	private final int[] start;
	private final int[] to;
	private final short[] cost;
	// Sections and doorways.
	private final int[] partArea;
	private final int[] partSize;
	public final List<Gate> gates;
	private final int[][] gatesOut, gatesIn;
	/** From each doorway, through the section it leads into, on through another: that doorway and the cost. */
	private final int[][] fwdTo;
	private final float[][] fwdCost;
	private final int[][] revFrom;
	private final float[][] revCost;
	/** Where someone on foot can stand, by plan cell: 0 nowhere, else 1 plus the height above the cell's bottom in 64ths. */
	private final byte[] walk;
	private final NavView view;

	/**
	 * A way from one section into the next: the place it leaves from and the one it comes into, what
	 * crossing costs, and every place on the far side it can come in at (a wide arch has many).
	 */
	public record Gate(int id, int from, int to, int out, int in, float cost, int[] ins, Vec3 at) {
	}

	private HotelGraph(String key, HotelFrame frame, HotelPlan plan, long millis, Int2IntOpenHashMap byKey, int[] nodeCell,
			byte[] nodeSurface, short[] nodeCode, int[] nodePart, int[] start, int[] to, short[] cost, int[] partArea, int[] partSize,
			List<Gate> gates, int[][] fwdTo, float[][] fwdCost, int[][] revFrom, float[][] revCost, byte[] walk, NavView view) {
		this.key = key;
		this.frame = frame;
		this.plan = plan;
		this.millis = millis;
		this.byKey = byKey;
		this.nodeCell = nodeCell;
		this.nodeSurface = nodeSurface;
		this.nodeCode = nodeCode;
		this.nodePart = nodePart;
		this.start = start;
		this.to = to;
		this.cost = cost;
		this.partArea = partArea;
		this.partSize = partSize;
		this.gates = gates;
		this.fwdTo = fwdTo;
		this.fwdCost = fwdCost;
		this.revFrom = revFrom;
		this.revCost = revCost;
		this.walk = walk;
		this.view = view;
		int parts = partArea.length;
		List<IntArrayList> out = new ArrayList<>(), in = new ArrayList<>();
		for (int p = 0; p < parts; p++) {
			out.add(new IntArrayList());
			in.add(new IntArrayList());
		}
		for (Gate g : gates) {
			out.get(g.from()).add(g.id());
			in.get(g.to()).add(g.id());
		}
		gatesOut = new int[parts][];
		gatesIn = new int[parts][];
		for (int p = 0; p < parts; p++) {
			gatesOut[p] = out.get(p).toIntArray();
			gatesIn[p] = in.get(p).toIntArray();
		}
	}

	// --- getting it ---------------------------------------------------------------------------------

	/**
	 * The hotel's map as things stand, or as they stood a moment ago while it is being worked out
	 * again (a lift car has moved); null if this world has no hotel or the first map isn't ready.
	 */
	@Nullable
	public static HotelGraph get(ServerLevel level) {
		if (level.dimension() != Level.OVERWORLD) {
			return null;
		}
		HotelFrame frame = HotelData.get(level).frame();
		if (frame == null) {
			return null;
		}
		String key = key(level, frame);
		HotelGraph g = current;
		if (g != null && g.key.equals(key)) {
			return g;
		}
		if (!key.equals(pending)) {
			pending = key;
			Inputs in = inputs(level, frame);
			WORKER.submit(() -> {
				try {
					HotelGraph built = build(in, key);
					if (current == null || key.equals(pending)) {
						current = built;
					}
				} catch (Throwable t) {
					Rauk.LOGGER.error("Rauk's map of the hotel failed", t);
					pending = "";
				}
			});
		}
		return g != null && g.frame.equals(frame) ? g : null;
	}

	/** The map as things stand now, worked out here and now if it isn't (test commands). */
	@Nullable
	public static HotelGraph now(ServerLevel level) {
		HotelFrame frame = level.dimension() == Level.OVERWORLD ? HotelData.get(level).frame() : null;
		if (frame == null) {
			return null;
		}
		String key = key(level, frame);
		HotelGraph g = current;
		if (g == null || !g.key.equals(key)) {
			pending = key;
			g = build(inputs(level, frame), key);
			current = g;
		}
		return g;
	}

	/** Forgets the map (the hotel was built again somewhere else). */
	public static void forget() {
		current = null;
		pending = "";
	}

	private static String key(ServerLevel level, HotelFrame frame) {
		StringBuilder key = new StringBuilder(frame.origin().toShortString()).append('/').append(frame.rotation()).append('/');
		for (LiftCars.Lift lift : LiftCars.ALL) {
			key.append(lift.id()).append('=').append(HotelLifts.carFloor(level, lift)).append(';');
		}
		HotelData.get(level).unlocked().stream().sorted().forEach(c -> key.append(c).append(','));
		return key.toString();
	}

	/** What a map is worked out from, gathered on the game's thread: the plan's blocks as Rauk sees them, the lift cars and the doors. */
	private record Inputs(HotelPlan plan, HotelFrame frame, NavBlock[] palette, Map<Integer, NavBlock> overrides) {
	}

	private static Inputs inputs(ServerLevel level, HotelFrame frame) {
		HotelPlan plan = HotelBuilder.plan();
		BlockState[] states = HotelWriter.states(frame.rotation());
		NavBlock[] palette = new NavBlock[states.length];
		palette[0] = NavBlock.SOLID;
		for (int i = 1; i < states.length; i++) {
			palette[i] = NavBlock.of(states[i] == null ? Blocks.STONE.defaultBlockState() : states[i]);
		}
		Map<String, HotelPlan.Level> cars = new HashMap<>();
		for (LiftCars.Lift lift : LiftCars.ALL) {
			cars.put(lift.id(), HotelLifts.carFloor(level, lift));
		}
		Map<Long, String> changed = HotelNav.carsAt(plan, cars);
		for (long cell : HotelData.get(level).unlocked()) {
			int[] c = HotelPlan.cellXYZ(cell);
			changed.put(cell, plan.get(c[0], c[1], c[2]).replaceAll("lock=[a-z]+", "lock=none"));
		}
		Map<Integer, NavBlock> overrides = new HashMap<>();
		for (var e : changed.entrySet()) {
			int[] c = HotelPlan.cellXYZ(e.getKey());
			BlockState state;
			try {
				state = e.getValue().isEmpty() || e.getValue().startsWith("#") ? Blocks.AIR.defaultBlockState()
						: BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), e.getValue(), false).blockState();
			} catch (Exception ex) {
				state = Blocks.AIR.defaultBlockState();
			}
			overrides.put(index(c[0], c[1], c[2]), NavBlock.of(state.rotate(frame.rotation())));
		}
		return new Inputs(plan, frame, palette, overrides);
	}

	// --- plan space ---------------------------------------------------------------------------------

	private static int index(int x, int y, int z) {
		return ((y - HotelPlan.Y0) * HotelPlan.SZ + (z - HotelPlan.Z0)) * HotelPlan.SX + (x - HotelPlan.X0);
	}

	private static int[] xyz(int index) {
		int x = index % HotelPlan.SX, rest = index / HotelPlan.SX;
		int z = rest % HotelPlan.SZ, y = rest / HotelPlan.SZ;
		return new int[] {x + HotelPlan.X0, y + HotelPlan.Y0, z + HotelPlan.Z0};
	}

	/** A world block's plan cell, or -1 outside the plan's box. */
	private int cellOf(int wx, int wy, int wz) {
		int[] p = frame.toPlan(wx, wy, wz);
		return HotelPlan.inBox(p[0], p[1], p[2]) ? index(p[0], p[1], p[2]) : -1;
	}

	private BlockPos world(int cell) {
		int[] p = xyz(cell);
		return frame.toWorld(p[0], p[1], p[2]);
	}

	/** The plan, its lift cars and doors as they stood, seen as the world (by world position). */
	private record PlanView(HotelPlan plan, HotelFrame frame, NavBlock[] palette, Map<Integer, NavBlock> overrides) implements NavView {
		@Override
		public NavBlock at(int x, int y, int z) {
			int[] p = frame.toPlan(x, y, z);
			if (!HotelPlan.inBox(p[0], p[1], p[2])) {
				return NavBlock.SOLID;
			}
			if (!overrides.isEmpty()) {
				NavBlock o = overrides.get(index(p[0], p[1], p[2]));
				if (o != null) {
					return o;
				}
			}
			return palette[plan.at(p[0], p[1], p[2])];
		}
	}

	/** The places, kept in a block of memory for the whole plan box while the map is worked out. */
	private record DenseCache(HotelFrame frame, short[] codes) implements NavRules.Cache {
		@Override
		public int get(int x, int y, int z, int surface) {
			int[] p = frame.toPlan(x, y, z);
			if (!HotelPlan.inBox(p[0], p[1], p[2])) {
				return 2;
			}
			return codes[index(p[0], p[1], p[2]) * 6 + surface] & 0xFFFF;
		}

		@Override
		public void put(int x, int y, int z, int surface, int code) {
			int[] p = frame.toPlan(x, y, z);
			if (HotelPlan.inBox(p[0], p[1], p[2])) {
				codes[index(p[0], p[1], p[2]) * 6 + surface] = (short) code;
			}
		}
	}

	// --- working it out -----------------------------------------------------------------------------

	private static HotelGraph build(Inputs in, String key) {
		long t0 = System.nanoTime();
		HotelPlan plan = in.plan();
		HotelFrame frame = in.frame();
		PlanView view = new PlanView(plan, frame, in.palette(), in.overrides());
		short[] dense = new short[HotelPlan.SX * HotelPlan.SY * HotelPlan.SZ * 6];
		NavRules rules = new NavRules(view, new DenseCache(frame, dense));
		Map<HotelPlan.Area, Integer> areaIndex = new IdentityHashMap<>();
		for (int i = 0; i < plan.areas.size(); i++) {
			areaIndex.put(plan.areas.get(i), i);
		}

		// Every place in the hotel's spaces.
		Int2IntOpenHashMap byKey = new Int2IntOpenHashMap();
		byKey.defaultReturnValue(-1);
		IntArrayList cells = new IntArrayList(), areas = new IntArrayList();
		ByteList surfaces = new ByteList();
		ShortArrayList codes = new ShortArrayList();
		for (int y = HotelPlan.Y0; y < HotelPlan.Y0 + HotelPlan.SY; y++) {
			for (int z = HotelPlan.Z0; z < HotelPlan.Z0 + HotelPlan.SZ; z++) {
				for (int x = HotelPlan.X0; x < HotelPlan.X0 + HotelPlan.SX; x++) {
					HotelPlan.Area a = plan.owner(x, y, z);
					if (a == null) {
						continue;
					}
					BlockPos w = frame.toWorld(x, y, z);
					int cell = index(x, y, z);
					for (int s = 0; s < 6; s++) {
						if (rules.node(w.getX(), w.getY(), w.getZ(), SURFACES[s]) == null) {
							continue;
						}
						byKey.put(cell * 6 + s, cells.size());
						cells.add(cell);
						surfaces.add((byte) s);
						codes.add(dense[cell * 6 + s]);
						areas.add(areaIndex.get(a));
					}
				}
			}
		}
		int n = cells.size();
		long tNodes = System.nanoTime();

		// Every way from each to the next.
		int[] start = new int[n + 1];
		IntArrayList to = new IntArrayList(n * 10);
		ShortArrayList cost = new ShortArrayList(n * 10);
		for (int i = 0; i < n; i++) {
			SurfaceNode a = decode(frame, cells.getInt(i), surfaces.get(i), codes.getShort(i));
			for (NavRules.Move m : rules.moves(a)) {
				int[] p = frame.toPlan(m.to().cell().getX(), m.to().cell().getY(), m.to().cell().getZ());
				if (!HotelPlan.inBox(p[0], p[1], p[2])) {
					continue;
				}
				int j = byKey.get(index(p[0], p[1], p[2]) * 6 + m.to().surface().ordinal());
				if (j >= 0 && j != i) {
					to.add(j);
					cost.add((short) Math.min(32767, Math.round(m.cost() * 64)));
				}
			}
			start[i + 1] = to.size();
		}
		int[] toArr = to.toIntArray();
		short[] costArr = cost.toShortArray();
		long tMoves = System.nanoTime();

		// Sections: the places of each space that join up, either way round.
		int[] inDeg = new int[n + 1];
		for (int j : toArr) {
			inDeg[j + 1]++;
		}
		for (int i = 0; i < n; i++) {
			inDeg[i + 1] += inDeg[i];
		}
		int[] back = new int[toArr.length];
		int[] fill = Arrays.copyOf(inDeg, n);
		for (int i = 0; i < n; i++) {
			for (int e = start[i]; e < start[i + 1]; e++) {
				back[fill[toArr[e]]++] = i;
			}
		}
		int[] part = new int[n];
		Arrays.fill(part, -1);
		IntArrayList partAreas = new IntArrayList(), partSizes = new IntArrayList();
		int[] queue = new int[n];
		for (int i = 0; i < n; i++) {
			if (part[i] >= 0) {
				continue;
			}
			int p = partAreas.size(), area = areas.getInt(i), head = 0, tail = 0;
			partAreas.add(area);
			part[i] = p;
			queue[tail++] = i;
			while (head < tail) {
				int c = queue[head++];
				for (int e = start[c]; e < start[c + 1]; e++) {
					int j = toArr[e];
					if (part[j] < 0 && areas.getInt(j) == area) {
						part[j] = p;
						queue[tail++] = j;
					}
				}
				for (int e = inDeg[c]; e < inDeg[c + 1]; e++) {
					int j = back[e];
					if (part[j] < 0 && areas.getInt(j) == area) {
						part[j] = p;
						queue[tail++] = j;
					}
				}
			}
			partSizes.add(tail);
		}
		int parts = partAreas.size();

		// Doorways: the ways from one section into another, those close together taken as one.
		Map<Long, List<int[]>> crossings = new HashMap<>();
		for (int i = 0; i < n; i++) {
			for (int e = start[i]; e < start[i + 1]; e++) {
				int j = toArr[e];
				if (part[i] != part[j]) {
					crossings.computeIfAbsent((long) part[i] << 32 | part[j], k -> new ArrayList<>()).add(new int[] {i, j, costArr[e]});
				}
			}
		}
		List<Gate> gates = new ArrayList<>();
		for (var e : crossings.entrySet()) {
			int from = (int) (e.getKey() >> 32), into = (int) (long) e.getKey();
			List<int[]> list = e.getValue();
			int[] group = new int[list.size()];
			for (int k = 0; k < group.length; k++) {
				group[k] = k;
			}
			Vec3[] at = new Vec3[list.size()];
			for (int k = 0; k < at.length; k++) {
				at[k] = Vec3.atCenterOf(world(frame, cells.getInt(list.get(k)[0])));
			}
			for (int a = 0; a < at.length; a++) {
				for (int b = a + 1; b < at.length; b++) {
					if (at[a].distanceToSqr(at[b]) <= 2.0 * 2.0) {
						union(group, a, b);
					}
				}
			}
			Map<Integer, List<Integer>> clusters = new HashMap<>();
			for (int k = 0; k < group.length; k++) {
				clusters.computeIfAbsent(find(group, k), c -> new ArrayList<>()).add(k);
			}
			for (List<Integer> cluster : clusters.values()) {
				Vec3 centre = Vec3.ZERO;
				for (int k : cluster) {
					centre = centre.add(at[k]);
				}
				centre = centre.scale(1.0 / cluster.size());
				int best = -1;
				double bestScore = Double.MAX_VALUE;
				IntArrayList ins = new IntArrayList();
				for (int k : cluster) {
					int[] c = list.get(k);
					boolean floor = surfaces.get(c[0]) == Direction.DOWN.ordinal() && surfaces.get(c[1]) == Direction.DOWN.ordinal();
					double score = at[k].distanceTo(centre) + (floor ? 0 : 50) + c[2] / 64.0 * 0.1;
					if (score < bestScore) {
						bestScore = score;
						best = k;
					}
					if (!ins.contains(c[1])) {
						ins.add(c[1]);
					}
				}
				int[] c = list.get(best);
				gates.add(new Gate(gates.size(), from, into, c[0], c[1], c[2] / 64f, ins.toIntArray(), at[best]));
			}
		}

		// Across each section from the doorway in to every doorway out, the way it would go.
		List<IntArrayList> outOf = new ArrayList<>();
		for (int p = 0; p < parts; p++) {
			outOf.add(new IntArrayList());
		}
		for (Gate g : gates) {
			outOf.get(g.from()).add(g.id());
		}
		int[][] fwdTo = new int[gates.size()][];
		float[][] fwdCost = new float[gates.size()][];
		Dijkstra dj = new Dijkstra(n);
		for (Gate g : gates) {
			dj.run(g.in(), start, toArr, costArr, part, g.to(), Double.MAX_VALUE);
			IntArrayList ts = new IntArrayList();
			List<Float> cs = new ArrayList<>();
			for (int h : outOf.get(g.to()).toIntArray()) {
				double d = dj.dist(gates.get(h).out());
				if (d < Double.MAX_VALUE) {
					ts.add(h);
					cs.add((float) (d + gates.get(h).cost()));
				}
			}
			fwdTo[g.id()] = ts.toIntArray();
			fwdCost[g.id()] = new float[cs.size()];
			for (int k = 0; k < cs.size(); k++) {
				fwdCost[g.id()][k] = cs.get(k);
			}
		}
		List<IntArrayList> rf = new ArrayList<>();
		List<List<Float>> rc = new ArrayList<>();
		for (int k = 0; k < gates.size(); k++) {
			rf.add(new IntArrayList());
			rc.add(new ArrayList<>());
		}
		for (int f = 0; f < gates.size(); f++) {
			for (int k = 0; k < fwdTo[f].length; k++) {
				rf.get(fwdTo[f][k]).add(f);
				rc.get(fwdTo[f][k]).add(fwdCost[f][k]);
			}
		}
		int[][] revFrom = new int[gates.size()][];
		float[][] revCost = new float[gates.size()][];
		for (int k = 0; k < gates.size(); k++) {
			revFrom[k] = rf.get(k).toIntArray();
			revCost[k] = new float[rc.get(k).size()];
			for (int q = 0; q < revCost[k].length; q++) {
				revCost[k][q] = rc.get(k).get(q);
			}
		}

		// Where someone on foot can stand.
		byte[] walk = new byte[HotelPlan.SX * HotelPlan.SY * HotelPlan.SZ];
		for (int y = HotelPlan.Y0; y < HotelPlan.Y0 + HotelPlan.SY; y++) {
			for (int z = HotelPlan.Z0; z < HotelPlan.Z0 + HotelPlan.SZ; z++) {
				for (int x = HotelPlan.X0; x < HotelPlan.X0 + HotelPlan.SX; x++) {
					if (plan.owner(x, y, z) != null) {
						BlockPos w = frame.toWorld(x, y, z);
						walk[index(x, y, z)] = (byte) walker(rules, w.getX(), w.getY(), w.getZ());
					}
				}
			}
		}
		long millis = (System.nanoTime() - t0) / 1_000_000;
		Rauk.LOGGER.info("Rauk's map of the hotel: {} places ({} ms), {} ways ({} ms), {} sections, {} doorways; {} ms in all", n,
				(tNodes - t0) / 1_000_000, toArr.length, (tMoves - tNodes) / 1_000_000, parts, gates.size(), millis);
		return new HotelGraph(key, frame, plan, millis, byKey, cells.toIntArray(), surfaces.toArray(), codes.toShortArray(), part, start,
				toArr, costArr, partAreas.toIntArray(), partSizes.toIntArray(), List.copyOf(gates), fwdTo, fwdCost, revFrom, revCost, walk,
				view);
	}

	/** A small growable list of bytes. */
	private static final class ByteList {
		private byte[] items = new byte[1024];
		private int size;

		void add(byte b) {
			if (size == items.length) {
				items = Arrays.copyOf(items, size * 2);
			}
			items[size++] = b;
		}

		byte get(int i) {
			return items[i];
		}

		byte[] toArray() {
			return Arrays.copyOf(items, size);
		}
	}

	private static int find(int[] group, int k) {
		while (group[k] != k) {
			group[k] = group[group[k]];
			k = group[k];
		}
		return k;
	}

	private static void union(int[] group, int a, int b) {
		group[find(group, a)] = find(group, b);
	}

	private static BlockPos world(HotelFrame frame, int cell) {
		int[] p = xyz(cell);
		return frame.toWorld(p[0], p[1], p[2]);
	}

	private static SurfaceNode decode(HotelFrame frame, int cell, int surface, short code) {
		BlockPos w = world(frame, cell);
		return NavRules.decode(w.getX(), w.getY(), w.getZ(), SURFACES[surface], code & 0xFFFF);
	}

	/**
	 * Where someone on foot stands in a cell: on whatever is under the middle of it, if a player's
	 * box fits there; anywhere in water (they swim) or on a ladder. 0 for nowhere, else 1 plus the
	 * height above the cell's bottom in 64ths.
	 */
	private static int walker(NavRules rules, int x, int y, int z) {
		NavBlock self = rules.view.at(x, y, z);
		double h = rules.topAt(x + 0.5, z + 0.5, y - 0.5, y + 0.999);
		if (Double.isNaN(h)) {
			if (!self.water && !self.climbable) {
				return 0;
			}
			h = y;
		}
		if (h < y || h >= y + 1) {
			return 0;
		}
		if (!rules.fits(new net.minecraft.world.phys.AABB(x + 0.2, h + 0.01, z + 0.2, x + 0.8, h + 1.8, z + 0.8))) {
			return 0;
		}
		return 1 + Mth.clamp((int) Math.round((h - y) * 64), 0, 63);
	}

	// --- searching it -------------------------------------------------------------------------------

	/** Dijkstra over the places of one section, with its arrays kept for the next search. */
	private static final class Dijkstra {
		private final double[] dist;
		private final IntArrayList touched = new IntArrayList();
		private int[] heap = new int[256];
		private double[] keys = new double[256];
		private int size;

		Dijkstra(int n) {
			dist = new double[n];
			Arrays.fill(dist, Double.MAX_VALUE);
		}

		double dist(int i) {
			return dist[i];
		}

		void run(int from, int[] start, int[] to, short[] cost, int[] part, int inPart, double max) {
			for (int i = 0; i < touched.size(); i++) {
				dist[touched.getInt(i)] = Double.MAX_VALUE;
			}
			touched.clear();
			size = 0;
			dist[from] = 0;
			touched.add(from);
			push(from, 0);
			while (size > 0) {
				double d = keys[0];
				int c = pop();
				if (d > dist[c] || d > max) {
					continue;
				}
				for (int e = start[c]; e < start[c + 1]; e++) {
					int j = to[e];
					if (part[j] != inPart) {
						continue;
					}
					double nd = d + cost[e] / 64.0;
					if (nd < dist[j]) {
						if (dist[j] == Double.MAX_VALUE) {
							touched.add(j);
						}
						dist[j] = nd;
						push(j, nd);
					}
				}
			}
		}

		private void push(int v, double k) {
			if (size == heap.length) {
				heap = Arrays.copyOf(heap, size * 2);
				keys = Arrays.copyOf(keys, size * 2);
			}
			int i = size++;
			while (i > 0) {
				int parent = (i - 1) / 2;
				if (keys[parent] <= k) {
					break;
				}
				heap[i] = heap[parent];
				keys[i] = keys[parent];
				i = parent;
			}
			heap[i] = v;
			keys[i] = k;
		}

		private int pop() {
			int top = heap[0];
			int lastV = heap[--size];
			double lastK = keys[size];
			int i = 0;
			while (true) {
				int l = 2 * i + 1;
				if (l >= size) {
					break;
				}
				int r = l + 1, m = r < size && keys[r] < keys[l] ? r : l;
				if (keys[m] >= lastK) {
					break;
				}
				heap[i] = heap[m];
				keys[i] = keys[m];
				i = m;
			}
			heap[i] = lastV;
			keys[i] = lastK;
			return top;
		}
	}

	/** One search's arrays, for the game's thread. */
	private Dijkstra local;

	private Dijkstra local() {
		if (local == null) {
			local = new Dijkstra(nodeCell.length);
		}
		return local;
	}

	// --- places and sections --------------------------------------------------------------------------

	public int places() {
		return nodeCell.length;
	}

	public int sections() {
		return partArea.length;
	}

	/** A place by its number, in the world. */
	public SurfaceNode node(int i) {
		return decode(frame, nodeCell[i], nodeSurface[i], nodeCode[i]);
	}

	/** The number of the place in a cell holding a side, or -1 if the map has none. */
	public int place(BlockPos cell, Direction surface) {
		int c = cellOf(cell.getX(), cell.getY(), cell.getZ());
		return c < 0 ? -1 : byKey.get(c * 6 + surface.ordinal());
	}

	public int place(SurfaceNode n) {
		return place(n.cell(), n.surface());
	}

	/** The section a place is in. */
	public int part(int place) {
		return place < 0 ? -1 : nodePart[place];
	}

	/** The section a place in the world is in, or -1 if the map doesn't have it. */
	public int partOf(SurfaceNode n) {
		return part(place(n));
	}

	/** The plan's space (its index in the plan's areas) a section is part of. */
	public int area(int part) {
		return partArea[part];
	}

	public int size(int part) {
		return partSize[part];
	}

	/**
	 * The map's place nearest a point: on the floor under it if there is one, else wherever it holds
	 * on in that cell or round it; -1 if none.
	 */
	public int placeNear(Vec3 at) {
		BlockPos c = BlockPos.containing(at);
		for (int dy : new int[] {0, -1, 1, -2}) {
			int i = place(c.above(dy), Direction.DOWN);
			if (i >= 0) {
				return i;
			}
		}
		for (Direction s : SURFACES) {
			int i = place(c, s);
			if (i >= 0) {
				return i;
			}
		}
		int best = -1;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -2, -2), c.offset(2, 2, 2))) {
			for (Direction s : SURFACES) {
				int i = place(p, s);
				if (i >= 0) {
					double d = node(i).position().distanceToSqr(at);
					if (d < bestD) {
						bestD = d;
						best = i;
					}
				}
			}
		}
		return best;
	}

	/** The section a point in the world is in, or -1. */
	public int partAt(Vec3 at) {
		return part(placeNear(at));
	}

	public int[] gatesOut(int part) {
		return gatesOut[part];
	}

	public int[] gatesIn(int part) {
		return gatesIn[part];
	}

	private int[] centres;

	/** The section's place nearest the middle of its space's floor, for going to have a look round it; -1 if it has none. */
	public int centre(int part) {
		if (centres == null) {
			int[] best = new int[partArea.length];
			double[] bestD = new double[partArea.length];
			Arrays.fill(best, -1);
			Arrays.fill(bestD, Double.MAX_VALUE);
			Vec3[] mids = new Vec3[plan.areas.size()];
			for (int a = 0; a < mids.length; a++) {
				HotelPlan.Area area = plan.areas.get(a);
				mids[a] = Vec3.atBottomCenterOf(frame.toWorld((int) Math.floor(area.centerX()), area.y0(), (int) Math.floor(area.centerZ())));
			}
			for (int i = 0; i < nodePart.length; i++) {
				int p = nodePart[i];
				double d = node(i).position().distanceToSqr(mids[partArea[p]]) + (nodeSurface[i] == Direction.DOWN.ordinal() ? 0 : 400);
				if (d < bestD[p]) {
					bestD[p] = d;
					best[p] = i;
				}
			}
			centres = best;
		}
		return part < 0 ? -1 : centres[part];
	}

	// --- fields and routes ----------------------------------------------------------------------------

	/** What it costs from a place to every place of its own section, the way it would go. */
	public Int2DoubleOpenHashMap within(int place) {
		Int2DoubleOpenHashMap out = new Int2DoubleOpenHashMap();
		out.defaultReturnValue(Double.MAX_VALUE);
		if (place < 0) {
			return out;
		}
		Dijkstra dj = local();
		dj.run(place, start, to, cost, nodePart, nodePart[place], Double.MAX_VALUE);
		for (int i = 0; i < dj.touched.size(); i++) {
			int k = dj.touched.getInt(i);
			out.put(k, dj.dist(k));
		}
		return out;
	}

	/**
	 * How far it is to a place, worked out back from it (design doc 4.7: from the prey's last known
	 * place, traced back): for every doorway, what it costs from just through it to there.
	 * Sections it is told to keep out of aren't gone through.
	 */
	public final class Field {
		public final Vec3 target;
		public final int targetPlace, targetPart;
		final double[] toTarget;
		final Int2DoubleOpenHashMap near;
		final IntPredicate avoid, avoidGate;
		public final long made;

		Field(Vec3 target, int targetPlace, IntPredicate avoid, IntPredicate avoidGate, long made) {
			this.target = target;
			this.targetPlace = targetPlace;
			this.targetPart = nodePart[targetPlace];
			this.avoid = avoid;
			this.avoidGate = avoidGate;
			this.made = made;
			// From the target out through its own section (either way round costs about the same in a room).
			near = within(targetPlace);
			toTarget = new double[gates.size()];
			Arrays.fill(toTarget, Double.MAX_VALUE);
			java.util.PriorityQueue<double[]> open = new java.util.PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
			for (int g : gatesIn[targetPart]) {
				if (avoidGate.test(g)) {
					continue;
				}
				double d = near.get(gates.get(g).in());
				if (d < Double.MAX_VALUE) {
					toTarget[g] = d;
					open.add(new double[] {d, g});
				}
			}
			while (!open.isEmpty()) {
				double[] top = open.poll();
				int g = (int) top[1];
				if (top[0] > toTarget[g]) {
					continue;
				}
				for (int k = 0; k < revFrom[g].length; k++) {
					int f = revFrom[g][k];
					// A way through a section it keeps out of isn't one.
					if (avoidGate.test(f) || avoid.test(gates.get(f).to()) && gates.get(f).to() != targetPart) {
						continue;
					}
					double d = top[0] + revCost[g][k];
					if (d < toTarget[f]) {
						toTarget[f] = d;
						open.add(new double[] {d, f});
					}
				}
			}
		}

		/** From just through a doorway to the target. */
		public double fromGate(int gate) {
			return toTarget[gate];
		}

		/** From a place to the target (the place's own section crossed the way it would go); infinite if no way. */
		public double from(int place) {
			if (place < 0) {
				return Double.MAX_VALUE;
			}
			Int2DoubleOpenHashMap here = within(place);
			return from(place, here);
		}

		private double from(int place, Int2DoubleOpenHashMap here) {
			int p = nodePart[place];
			double best = p == targetPart ? here.get(targetPlace) : Double.MAX_VALUE;
			for (int h : gatesOut[p]) {
				Gate g = gates.get(h);
				double d = here.get(g.out());
				if (d < Double.MAX_VALUE && toTarget[h] < Double.MAX_VALUE) {
					best = Math.min(best, d + g.cost() + toTarget[h]);
				}
			}
			return best;
		}

		/**
		 * The fastest way from a place, doorway by doorway, following the field down: empty if the
		 * target is in the same section, null if there's no way at all.
		 */
		@Nullable
		public Route route(int place) {
			if (place < 0) {
				return null;
			}
			int p = nodePart[place];
			Int2DoubleOpenHashMap here = within(place);
			double direct = p == targetPart ? here.get(targetPlace) : Double.MAX_VALUE;
			int first = -1;
			double best = direct;
			for (int h : gatesOut[p]) {
				Gate g = gates.get(h);
				if (avoidGate.test(h) || avoid.test(g.to()) && g.to() != targetPart) {
					continue;
				}
				double d = here.get(g.out());
				if (d < Double.MAX_VALUE && toTarget[h] < Double.MAX_VALUE && d + g.cost() + toTarget[h] < best - 1e-6) {
					best = d + g.cost() + toTarget[h];
					first = h;
				}
			}
			if (first < 0) {
				return direct < Double.MAX_VALUE ? new Route(List.of(), direct) : null;
			}
			List<Gate> way = new ArrayList<>();
			int cur = first;
			java.util.Set<Integer> seen = new java.util.HashSet<>();
			while (cur >= 0 && seen.add(cur)) {
				Gate g = gates.get(cur);
				way.add(g);
				if (g.to() == targetPart && Math.abs(toTarget[cur] - near.get(g.in())) < 1e-6) {
					break;
				}
				int next = -1;
				double nb = Double.MAX_VALUE;
				for (int k = 0; k < fwdTo[cur].length; k++) {
					int h = fwdTo[cur][k];
					if (avoidGate.test(h) || avoid.test(gates.get(h).to()) && gates.get(h).to() != targetPart) {
						continue;
					}
					double d = fwdCost[cur][k] + toTarget[h];
					if (d < nb) {
						nb = d;
						next = h;
					}
				}
				cur = next;
			}
			return new Route(way, best);
		}
	}

	/** A way across the hotel: the doorways in order, and what it costs (about a block walked for each). */
	public record Route(List<Gate> gates, double cost) {
		/** The sections it goes through, in order, the first included (from the first doorway's side). */
		public List<Integer> parts(int startPart) {
			List<Integer> out = new ArrayList<>(gates.size() + 1);
			out.add(startPart);
			for (Gate g : gates) {
				out.add(g.to());
			}
			return out;
		}
	}

	/** The field back from a point, or null if the map doesn't reach there. */
	@Nullable
	public Field fieldTo(Vec3 target, IntPredicate avoid, IntPredicate avoidGate, long now) {
		int t = placeNear(target);
		return t < 0 ? null : new Field(target, t, avoid, avoidGate, now);
	}

	/**
	 * Forward from a place: what it costs to get just through every doorway in the hotel, keeping
	 * out of the sections it is told to (how soon Rauk can be anywhere).
	 */
	public double[] reach(int place, IntPredicate avoid, IntPredicate avoidGate) {
		double[] dist = new double[gates.size()];
		Arrays.fill(dist, Double.MAX_VALUE);
		if (place < 0) {
			return dist;
		}
		Int2DoubleOpenHashMap here = within(place);
		java.util.PriorityQueue<double[]> open = new java.util.PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
		for (int h : gatesOut[nodePart[place]]) {
			Gate g = gates.get(h);
			double d = here.get(g.out());
			if (d < Double.MAX_VALUE && !avoid.test(g.to()) && !avoidGate.test(h)) {
				dist[h] = d + g.cost();
				open.add(new double[] {dist[h], h});
			}
		}
		while (!open.isEmpty()) {
			double[] top = open.poll();
			int g = (int) top[1];
			if (top[0] > dist[g]) {
				continue;
			}
			for (int k = 0; k < fwdTo[g].length; k++) {
				int h = fwdTo[g][k];
				if (avoid.test(gates.get(h).to()) || avoidGate.test(h)) {
					continue;
				}
				double d = top[0] + fwdCost[g][k];
				if (d < dist[h]) {
					dist[h] = d;
					open.add(new double[] {d, h});
				}
			}
		}
		return dist;
	}

	// --- on foot --------------------------------------------------------------------------------------

	/**
	 * How far someone on foot is from everywhere they can get to from a point, up to a limit: by
	 * plan cell. Stairs, doors, ladders, short drops; never up a wall or through a shaft.
	 */
	public Int2DoubleOpenHashMap onFoot(Vec3 from, double max) {
		Int2DoubleOpenHashMap dist = new Int2DoubleOpenHashMap();
		dist.defaultReturnValue(Double.MAX_VALUE);
		int s = walkCell(from);
		if (s < 0) {
			return dist;
		}
		java.util.PriorityQueue<double[]> open = new java.util.PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
		dist.put(s, 0);
		open.add(new double[] {0, s});
		while (!open.isEmpty()) {
			double[] top = open.poll();
			int c = (int) top[1];
			if (top[0] > dist.get(c) || top[0] > max) {
				continue;
			}
			int[] p = xyz(c);
			double h = p[1] + (walk[c] - 1) / 64.0;
			boolean[] orth = new boolean[4];
			for (int k : ORDER) {
				int dx = RING[k][0], dz = RING[k][1];
				if (k % 2 == 1 && !(orth[(k - 1) / 2] && orth[((k + 1) / 2) % 4])) {
					continue;
				}
				int best = -1;
				double bh = 0;
				for (int dy : new int[] {0, 1, -1}) {
					int q = p[1] + dy;
					if (!HotelPlan.inBox(p[0] + dx, q, p[2] + dz)) {
						continue;
					}
					int j = index(p[0] + dx, q, p[2] + dz);
					if (walk[j] != 0) {
						double jh = q + (walk[j] - 1) / 64.0;
						if (Math.abs(jh - h) <= 1.25 && (best < 0 || Math.abs(jh - h) < Math.abs(bh - h))) {
							best = j;
							bh = jh;
						}
					}
				}
				if (best >= 0) {
					if (k % 2 == 0) {
						orth[k / 2] = true;
					}
					relax(dist, open, best, top[0] + Math.hypot(dx, dz) + Math.max(0, bh - h) * 0.5);
				} else if (k % 2 == 0) {
					// Off an edge: down to the floor below, a few blocks at most.
					for (int down = 2; down <= 5; down++) {
						if (!HotelPlan.inBox(p[0] + dx, p[1] - down + 1, p[2] + dz)) {
							break;
						}
						int j = index(p[0] + dx, p[1] - down + 1, p[2] + dz);
						if (walk[j] != 0) {
							if (down <= 4) {
								relax(dist, open, j, top[0] + 1 + 0.5 * down);
							}
							break;
						}
					}
				}
			}
			// Up and down a ladder, or swimming up and down.
			for (int dy : new int[] {1, -1}) {
				if (HotelPlan.inBox(p[0], p[1] + dy, p[2])) {
					int j = index(p[0], p[1] + dy, p[2]);
					BlockPos w = world(c);
					NavBlock here = view.at(w.getX(), w.getY(), w.getZ()), there = view.at(w.getX(), w.getY() + dy, w.getZ());
					if (walk[j] != 0 && (here.climbable || there.climbable || here.water && there.water)) {
						relax(dist, open, j, top[0] + 1.6);
					}
				}
			}
		}
		return dist;
	}

	private static final int[][] RING = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
	/** The four straight ways first, then the diagonals (which need both straight ways beside them). */
	private static final int[] ORDER = {0, 2, 4, 6, 1, 3, 5, 7};

	private static void relax(Int2DoubleOpenHashMap dist, java.util.PriorityQueue<double[]> open, int j, double d) {
		if (d < dist.get(j)) {
			dist.put(j, d);
			open.add(new double[] {d, j});
		}
	}

	/** The plan cell someone on foot at a point stands in, or -1. */
	public int walkCell(Vec3 at) {
		BlockPos b = BlockPos.containing(at.add(0, 0.05, 0));
		for (int dy : new int[] {0, -1, 1}) {
			int c = cellOf(b.getX(), b.getY() + dy, b.getZ());
			if (c >= 0 && walk[c] != 0) {
				return c;
			}
		}
		return -1;
	}

	/** How far on foot a place is in a field from {@link #onFoot}: at its cell or a step up or down. */
	public double onFootTo(Int2DoubleOpenHashMap dist, Vec3 at) {
		int c = walkCell(at);
		if (c >= 0 && dist.containsKey(c)) {
			return dist.get(c);
		}
		BlockPos b = BlockPos.containing(at);
		double best = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(b.offset(-1, -1, -1), b.offset(1, 1, 1))) {
			int k = cellOf(p.getX(), p.getY(), p.getZ());
			if (k >= 0 && dist.containsKey(k)) {
				best = Math.min(best, dist.get(k) + 1);
			}
		}
		return best;
	}
}

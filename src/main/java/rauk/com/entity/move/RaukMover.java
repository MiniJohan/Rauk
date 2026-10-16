package rauk.com.entity.move;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import rauk.com.Rauk;
import rauk.com.entity.RaukEntity;
import rauk.com.entity.RaukPosture;
import rauk.com.entity.nav.NavPath;
import rauk.com.entity.nav.NavRules;
import rauk.com.entity.nav.NavSearch;
import rauk.com.hotel.HotelGraph;
import rauk.com.hotel.HotelRoutes;

/**
 * Moves Rauk on the server (design doc 4.7): it works out its way once and keeps to it, following
 * a smooth line ({@link NavPath}) rather than block to block, and the client turns that into the
 * body's movement.
 *
 * <ul>
 * <li><b>Its way:</b> in the hotel, back from where it is going to every doorway
 * ({@link HotelGraph.Field}): the fastest run of doorways from where it is; the next stretch, up to
 * a few doorways on, is searched block by block in the world and pulled into a line. Outside the
 * hotel, only the search.</li>
 * <li><b>Keeping to it:</b> it goes where it first meant to. It works its way out again only when
 * it gets near the end of the stretch it has, when what it follows moves, or when it gets stuck.</li>
 * <li><b>Following the line:</b> on the floor it heads for a point a little way along the line
 * (further the faster it goes), gathering speed on the straight and slowing into bends and before
 * doorways, so it runs in curves; on walls and ceilings it clings and climbs along it.</li>
 * <li><b>Stuck:</b> it works its way out again from where it is; stuck again, it keeps off the
 * blocks it stuck at; again, it gives up that doorway and goes another way.</li>
 * </ul>
 */
public final class RaukMover {
	public static final int LEAP_NONE = 0, LEAP_WINDUP = 1, LEAP_FLIGHT = 2, LEAP_LAND = 3;
	/** How far along its way it works out block by block at a time. */
	private static final double HORIZON = 26;
	/** Blocks a second per second: speeding up, slowing down, and the most sideways a bend may push it. */
	private static final double ACCEL = 14, DECEL = 26, LATERAL = 18;

	private final RaukEntity rauk;
	@Nullable
	private Vec3 goal;
	@Nullable
	private Direction goalSurface;
	@Nullable
	private Entity follow;
	private double followStop;
	private double speed = 2.5;
	private RaukPathfinder.Costs costs = RaukPathfinder.Costs.NONE;
	private boolean paused, unreachable;

	/** The line it follows, how far along it it is, and whether it ends where it is going or at a doorway on the way. */
	private NavPath path = NavPath.EMPTY;
	private int index;
	private double along;
	private boolean pathFinal;
	/** The doorways ahead of it on this way, for the debug overlay and for giving one up. */
	private List<HotelGraph.Gate> route = List.of();
	@Nullable
	private HotelGraph.Field field;
	@Nullable
	private HotelGraph fieldGraph;
	private Set<Integer> fieldShun = Set.of();
	private int repath;
	/** Doorways (by where they are) it found it couldn't get through, and blocks it got stuck at: until when it keeps off them. */
	private final Map<Long, Long> failedGates = new HashMap<>();
	private final Map<Long, Long> avoidCells = new HashMap<>();
	/** Where and when things went wrong lately: x, y, z, time. */
	private final List<double[]> troubles = new ArrayList<>();
	/** Places whose sections it keeps out of for now: the one its prey is in, while cutting it off. */
	private Set<Vec3> shunned = Set.of();
	private int stuck, extended = -100, plans, plansChecked, attachedAt = -100, planTick;
	private Vec3 progressPos = Vec3.ZERO;
	private double progressAlong;
	private int noGrip;
	@Nullable
	private Leap leap;
	private int landing;
	/** Its walking velocity on the floor, blocks per tick. */
	private Vec3 walk = Vec3.ZERO;

	private static final class Leap {
		final Vec3 from, to;
		final double apex;
		final int windup, flight;
		final Direction surface;
		int tick;
		boolean flying;

		Leap(Vec3 from, Vec3 to, double apex, int windup, int flight, Direction surface) {
			this.from = from;
			this.to = to;
			this.apex = apex;
			this.windup = windup;
			this.flight = flight;
			this.surface = surface;
		}

		Vec3 at(double u) {
			return from.lerp(to, u).add(0, 4 * apex * u * (1 - u), 0);
		}
	}

	public RaukMover(RaukEntity rauk) {
		this.rauk = rauk;
	}

	// --- orders ---------------------------------------------------------------------------------

	/** Go to a place, holding the given surface there (or any surface if null). */
	public void moveTo(Vec3 target, @Nullable Direction surface, double blocksPerSecond) {
		moveTo(target, surface, blocksPerSecond, RaukPathfinder.Costs.NONE);
	}

	/** The same, choosing its route by the given costs. */
	public void moveTo(Vec3 target, @Nullable Direction surface, double blocksPerSecond, RaukPathfinder.Costs costs) {
		follow = null;
		goal = target;
		goalSurface = surface;
		speed = blocksPerSecond;
		this.costs = costs;
		plan();
	}

	/** Holds still where it is, keeping its plan, until unpaused: freezing while watched. */
	public void setPaused(boolean paused) {
		this.paused = paused;
	}

	/** Whether the last plan found no way at all toward the goal. */
	public boolean unreachable() {
		return unreachable;
	}

	/** Where it is going, if anywhere. */
	@Nullable
	public Vec3 goal() {
		return goal;
	}

	/** Keep after an entity until within stopAt blocks of it. */
	public void follow(Entity target, double blocksPerSecond, double stopAt) {
		costs = RaukPathfinder.Costs.NONE;
		follow = target;
		followStop = stopAt;
		goal = target.position();
		goalSurface = null;
		speed = blocksPerSecond;
		plan();
	}

	public void stop() {
		goal = null;
		follow = null;
		path = NavPath.EMPTY;
		route = List.of();
	}

	/** Its line ahead, for the debug overlay: every other point. */
	public List<Vec3> pathPoints() {
		List<Vec3> out = new ArrayList<>();
		if (goal == null) {
			return out;
		}
		for (int i = index; i < path.size(); i += 2) {
			out.add(path.point(i).at());
		}
		if (!path.isEmpty()) {
			out.add(path.last().at());
		}
		return out;
	}

	/** The doorways it means to go through, for the debug overlay. */
	public List<Vec3> doorways() {
		List<Vec3> out = new ArrayList<>();
		for (HotelGraph.Gate g : route) {
			out.add(g.at());
		}
		return out;
	}

	public boolean isMoving() {
		return goal != null || leap != null;
	}

	public double speed() {
		return speed;
	}

	/** Keeps its routes out of the sections these places are in, for now (or none). */
	public void shun(Set<Vec3> places) {
		if (!places.equals(shunned)) {
			shunned = Set.copyOf(places);
			field = null;
		}
	}

	/**
	 * Leap to a surface it can see: only along an arc that is clear all the way (design doc 4.3).
	 * Returns false if the arc is blocked or the target out of range.
	 */
	public boolean leap(Vec3 target, Direction surface) {
		Vec3 start = rauk.position();
		double distance = target.distanceTo(start);
		if (distance > 16 || distance < 1.5) {
			return false;
		}
		int flight = (int) Math.round(Math.clamp(7 + distance * 1.1, 8, 24));
		double apex = Math.max(0.8, distance * 0.18);
		Leap l = new Leap(start, target, apex, (int) Math.round(Math.clamp(5 + distance * 0.5, 5, 12)), flight, surface);
		NavRules check = NavRules.world(rauk.level());
		RaukPosture body = RaukPosture.SQUEEZE;
		for (int i = 2; i < flight - 1; i++) {
			if (!check.fits(body.dimensions().makeBoundingBox(l.at(i / (double) flight)).deflate(0.05))) {
				return false;
			}
		}
		stop();
		leap = l;
		rauk.setLeapState(LEAP_WINDUP);
		return true;
	}

	// --- working out its way --------------------------------------------------------------------

	private void plan() {
		plan(0);
	}

	/**
	 * Works out its way from where it is. In the hotel: the fastest run of doorways back from the
	 * goal, and the next few of them searched block by block, keeping to their sections. Elsewhere,
	 * or if the map can't help, only the search, within a few dozen blocks.
	 */
	private void plan(int tries) {
		repath = follow != null ? 8 : 100;
		if (goal == null || !(rauk.level() instanceof ServerLevel level)) {
			return;
		}
		long t0 = System.nanoTime();
		NavRules rules = rules(level);
		SurfaceNode start = here(rules);
		if (start == null) {
			path = NavPath.EMPTY;
			unreachable = true;
			return;
		}
		long now = level.getGameTime();
		failedGates.values().removeIf(until -> until < now);
		avoidCells.values().removeIf(until -> until < now);
		BlockPos startCell = start.cell();
		Predicate<SurfaceNode> clear = n -> avoidCells.isEmpty() || n.cell().equals(startCell) || !avoidCells.containsKey(n.cell().asLong());

		NavSearch.Result result = null;
		boolean whole = true;
		route = List.of();
		HotelGraph graph = HotelGraph.get(level);
		boolean close = follow != null && rauk.position().distanceTo(goal) < 14;
		if (graph != null && !close) {
			int here = graph.placeNear(start.position());
			HotelGraph.Field f = field(graph, now);
			HotelGraph.Route r = f == null || here < 0 ? null : f.route(here);
			if (r == null && here >= 0 && tries == 0 && (!failedGates.isEmpty() || !shunned.isEmpty())) {
				// Every way it has left is one it gave up on, or keeps out of: better one of those than none.
				failedGates.clear();
				Set<Vec3> keep = shunned;
				shunned = Set.of();
				field = null;
				f = field(graph, now);
				r = f == null ? null : f.route(here);
				shunned = keep;
				field = null;
			}
			if (r != null && !r.gates().isEmpty()) {
				// Up to the furthest doorway within the stretch it works out now, keeping to the sections on the way.
				Set<Integer> parts = new HashSet<>();
				parts.add(graph.part(here));
				int last = 0;
				for (int k = 0; k < r.gates().size() && k < 4; k++) {
					HotelGraph.Gate g = r.gates().get(k);
					// At least past the doorways close by, then as far as the stretch reaches.
					if (k > 0 && g.at().distanceTo(start.position()) > HORIZON && r.gates().get(k - 1).at().distanceTo(start.position()) > 8) {
						break;
					}
					parts.add(g.from());
					parts.add(g.to());
					last = k;
				}
				route = r.gates();
				boolean reachesGoal = r.gates().get(last).to() == f.targetPart && goal.distanceTo(start.position()) <= HORIZON + 6;
				Predicate<SurfaceNode> inWay = n -> {
					int p = graph.partOf(n);
					return (p < 0 || parts.contains(p)) && clear.test(n);
				};
				NavSearch.Goal to;
				if (reachesGoal || last == r.gates().size() - 1 && r.gates().get(last).to() == f.targetPart) {
					to = NavSearch.Goal.near(goal, goalSurface, goalSurface == null ? 1.2 : 0.9);
				} else {
					HotelGraph.Gate g = r.gates().get(last);
					Set<NavSearch.Key> ins = new HashSet<>();
					for (int i : g.ins()) {
						ins.add(NavSearch.Key.of(graph.node(i)));
					}
					Vec3 at = graph.node(g.in()).position();
					to = new NavSearch.Goal() {
						@Override
						public boolean reached(SurfaceNode n) {
							return ins.contains(NavSearch.Key.of(n));
						}

						@Override
						public double estimate(SurfaceNode n) {
							return Math.max(0, n.position().distanceTo(at) - 1.5);
						}
					};
					whole = false;
				}
				result = NavSearch.find(rules, start, to, inWay, costs, 9000, 64);
				if (!result.reached() && result.timedOut() && !result.path().isEmpty()) {
					// Not worked out all the way in the time there was: as far as it got, and the rest on the way.
					whole = false;
				} else if (!result.reached()) {
					// The map says this way goes; the world says not (or not yet). That doorway is out for a minute.
					int stuckAt = firstGateNotReached(result, r);
					HotelGraph.Gate bad = r.gates().get(stuckAt);
					failedGates.put(BlockPos.containing(bad.at()).asLong(), now + 1200);
					field = null;
					if (DEBUG) {
						Rauk.LOGGER.info("mover: can't get through the doorway at {}, another way", fmt(bad.at()));
					}
					if (tries < 3) {
						plan(tries + 1);
						return;
					}
					whole = true;
				}
			} else if (r != null) {
				// In the same section as the goal.
				int part = graph.part(here);
				result = NavSearch.find(rules, start, NavSearch.Goal.near(goal, goalSurface, goalSurface == null ? 1.2 : 0.9),
						n -> (graph.partOf(n) < 0 || graph.partOf(n) == part) && clear.test(n), costs, 9000, 64);
				if (!result.reached() && result.timedOut() && !result.path().isEmpty()) {
					whole = false;
				} else if (!result.reached()) {
					result = null;
				}
			}
		}
		if (result == null || result.path().isEmpty()) {
			// In the hotel with its map still being worked out (just after the world loads): a short
			// way toward the goal for now, and the whole way once the map is there.
			boolean waiting = graph == null && HotelRoutes.inHotel(level, rauk.blockPosition());
			result = NavSearch.find(rules, start, NavSearch.Goal.near(goal, goalSurface, goalSurface == null ? 1.2 : 0.9), clear, costs,
					close || waiting ? 2500 : 7000, waiting ? 24 : 48);
			whole = !waiting && !result.timedOut();
			if (waiting) {
				repath = 20;
			}
		}
		path = NavPath.of(rules, result.path(), result.reached(), radius(), costs);
		path = turnFirst(rules, path);
		pathFinal = whole;
		unreachable = !result.reached() && !result.timedOut() && start.center().distanceTo(goal) > 2;
		index = 0;
		along = 0;
		plans++;
		planTick = rauk.tickCount;
		if (DEBUG) {
			Rauk.LOGGER.info("path from {} {} to {}: {} places, {} points over {} blocks{}{}, {} ms", startCell.toShortString(), start.surface(),
					fmt(goal), result.path().size(), path.size(), String.format("%.1f", path.length()), route.isEmpty() ? "" : ", " + route.size()
							+ " doorways", result.reached() ? "" : " (as near as it gets)", String.format("%.1f", (System.nanoTime() - t0) / 1e6));
		}
	}

	/**
	 * The places it has worked out lately, kept a few seconds from one plan to the next (the next
	 * stretch, a target that moved): the hotel doesn't change under it in that time, bar a lift.
	 */
	private NavRules rules(Level level) {
		if (cachedRules == null || rauk.tickCount - rulesMade > 100 || rulesLevel != level) {
			cachedRules = NavRules.world(level);
			rulesMade = rauk.tickCount;
			rulesLevel = level;
		}
		return cachedRules;
	}

	@Nullable
	private NavRules cachedRules;
	private int rulesMade;
	@Nullable
	private Level rulesLevel;

	/**
	 * A way that sets off back the way it is facing: like an animal, it turns round in a small
	 * circle first where there is room for its body all round it (design doc 5.12), then joins the
	 * way a few blocks on. Where there is no room it turns where it stands, as before.
	 */
	private NavPath turnFirst(NavRules rules, NavPath way) {
		if (way.size() < 4 || rauk.surface() != Direction.DOWN || way.point(0).surface() != Direction.DOWN
				|| rauk.posture() == RaukPosture.SQUEEZE) {
			return way;
		}
		Vec3 pos = rauk.position();
		double yaw = Math.toRadians(rauk.getYRot());
		Vec3 facing = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		// Where the way goes: a few blocks along it, and the point it joins at after the turn.
		int join = 0;
		while (join < way.size() - 1 && way.point(join).s() < 4 && way.point(join + 1).surface() == Direction.DOWN) {
			join++;
		}
		Vec3 target = way.point(join).at();
		Vec3 to = new Vec3(target.x - pos.x, 0, target.z - pos.z);
		if (to.length() < 1.5 || facing.dot(to.normalize()) > Math.cos(Math.toRadians(100))) {
			return way;
		}
		RaukPosture posture = rauk.posture() == RaukPosture.CRAWL ? RaukPosture.CRAWL : RaukPosture.QUADRUPED;
		double turnSide = facing.z * to.x - facing.x * to.z;
		for (int side : turnSide >= 0 ? new int[] {1, -1} : new int[] {-1, 1}) {
			for (double r : new double[] {2.2, 1.7}) {
				List<Vec3> arc = arc(rules, pos, facing, side, r, target, posture);
				if (arc != null) {
					if (DEBUG) {
						Rauk.LOGGER.info("mover: turns round in a circle {} wide to the {}", String.format("%.1f", 2 * r), side > 0 ? "left" : "right");
					}
					return way.withTurn(arc, posture, join);
				}
			}
		}
		return way;
	}

	/**
	 * A circle of radius r to one side from where it stands facing a way, as far round as it takes to
	 * face the point it heads for next, every bit of it floor with room for its body; null if not.
	 */
	@Nullable
	private List<Vec3> arc(NavRules rules, Vec3 pos, Vec3 facing, int side, double r, Vec3 target, RaukPosture posture) {
		// Its left (side 1) or right, about the vertical.
		Vec3 out = new Vec3(facing.z * side, 0, -facing.x * side);
		Vec3 centre = pos.add(out.scale(r));
		List<Vec3> points = new ArrayList<>();
		points.add(pos);
		double start = Math.atan2(pos.x - centre.x, pos.z - centre.z);
		double h = pos.y;
		for (int k = 1; k <= 18; k++) {
			double a = start + side * Math.toRadians(15 * k);
			Vec3 p = new Vec3(centre.x + Math.sin(a) * r, h, centre.z + Math.cos(a) * r);
			SurfaceNode m = rules.floorNear(Mth.floor(p.x), Mth.floor(h + 0.01), Mth.floor(p.z), h, 0.6);
			if (m == null || !rules.fits(posture.dimensions().makeBoundingBox(p.x, m.position().y, p.z).inflate(0.05, -0.02, 0.05))) {
				return null;
			}
			h = m.position().y;
			p = new Vec3(p.x, h, p.z);
			points.add(p);
			// The way round it is going now, against the way to the point it heads for.
			Vec3 tangent = new Vec3(Math.cos(a) * side, 0, -Math.sin(a) * side);
			Vec3 toTarget = new Vec3(target.x - p.x, 0, target.z - p.z);
			if (k >= 6 && toTarget.length() > 0.5 && tangent.normalize().dot(toTarget.normalize()) > Math.cos(Math.toRadians(30))) {
				return points;
			}
		}
		return null;
	}

	/** How widely it rounds corners off: wider the faster it means to go. */
	private double radius() {
		return Mth.clamp(speed * 0.3, 1.2, 3.5);
	}

	/**
	 * The field back from where it is going, worked out again when the goal has moved, the map has
	 * changed or it has given up a doorway, or now and then while it follows something.
	 */
	@Nullable
	private HotelGraph.Field field(HotelGraph graph, long now) {
		Set<Integer> shun = new HashSet<>();
		for (Vec3 p : shunned) {
			int part = graph.partAt(p);
			if (part >= 0) {
				shun.add(part);
			}
		}
		if (field == null || fieldGraph != graph || !shun.equals(fieldShun) || field.target.distanceTo(goal) > 2
				|| now - field.made > (follow != null ? 40 : 400)) {
			Set<Long> failed = Set.copyOf(failedGates.keySet());
			field = graph.fieldTo(goal, shun::contains, g -> failed.contains(BlockPos.containing(graph.gates.get(g).at()).asLong()), now);
			fieldGraph = graph;
			fieldShun = shun;
		}
		return field;
	}

	/** Which doorway on the way the search didn't get through: the first it didn't reach. */
	private static int firstGateNotReached(NavSearch.Result result, HotelGraph.Route r) {
		if (result.path().isEmpty()) {
			return 0;
		}
		Vec3 end = result.path().get(result.path().size() - 1).position();
		int best = 0;
		double bestD = Double.MAX_VALUE;
		for (int k = 0; k < Math.min(4, r.gates().size()); k++) {
			double d = r.gates().get(k).at().distanceTo(end);
			if (d < bestD) {
				bestD = d;
				best = k;
			}
		}
		return best;
	}

	private static final boolean DEBUG = Boolean.getBoolean("rauk.devshot");

	private static String fmt(Vec3 v) {
		return String.format("(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
	}

	/** The place Rauk is at now, or a valid one next to it. */
	@Nullable
	public SurfaceNode here(NavRules rules) {
		Direction surface = rauk.surface();
		RaukPosture p = rauk.posture();
		Vec3 pos = rauk.position();
		Vec3 probe = switch (surface) {
			case DOWN -> pos.add(0, 0.05, 0);
			case UP -> pos.add(0, p.height - 0.05, 0);
			default -> pos.add(surface.getStepX() * (p.width / 2 - 0.45), 0.5, surface.getStepZ() * (p.width / 2 - 0.45));
		};
		BlockPos cell = BlockPos.containing(probe);
		SurfaceNode node = rules.node(cell, surface);
		if (node != null) {
			return node;
		}
		if (surface == Direction.DOWN) {
			node = rules.floorNear(cell.getX(), cell.getY(), cell.getZ(), pos.y, 1.0);
			if (node != null) {
				return node;
			}
			// Standing on something that isn't floor to it (a seat, a table): the nearest floor at
			// about its own height before anything below.
			SurfaceNode best = null;
			for (BlockPos near : BlockPos.withinManhattan(cell, 2, 0, 2)) {
				SurfaceNode m = rules.floorNear(near.getX(), near.getY(), near.getZ(), pos.y, 1.2);
				if (m != null && (best == null || m.position().distanceToSqr(pos) < best.position().distanceToSqr(pos))) {
					best = m;
				}
			}
			if (best != null) {
				return best;
			}
		}
		for (BlockPos near : BlockPos.withinManhattan(cell, 1, 1, 1)) {
			node = rules.node(near, surface);
			if (node != null) {
				return node;
			}
		}
		for (BlockPos near : BlockPos.withinManhattan(cell, 1, 2, 1)) {
			node = rules.node(near, Direction.DOWN);
			if (node != null) {
				return node;
			}
		}
		return null;
	}

	// --- every tick -----------------------------------------------------------------------------

	public void tick() {
		if (landing > 0 && --landing == 0) {
			rauk.setLeapState(LEAP_NONE);
		}
		if (leap != null) {
			tickLeap();
			return;
		}
		if (rauk.tickCount % 10 == 0) {
			unstick();
		}
		Direction surface = rauk.surface();
		if (surface != Direction.DOWN && !hasGrip(surface)) {
			// Rounding an edge from one surface onto the next it is between the two for a moment:
			// it doesn't let go then, only when there is nothing to round onto.
			// Rounding an edge it hangs on a while; just after taking hold (from a little way off) it
			// has a moment to press in to the wall before it counts as not holding it.
			if (++noGrip > (rounding() ? 30 : rauk.tickCount - attachedAt < 15 ? 12 : 3)) {
				if (DEBUG) {
					Rauk.LOGGER.info("mover: lets go of {} at {} after {} ticks", surface, fmt(rauk.position()), noGrip);
				}
				attach(Direction.DOWN);
				if (goal != null && !path.isEmpty()) {
					// It couldn't hold on along this climb: keep off it for a while, and another way.
					long now = rauk.level().getGameTime();
					for (int i = Math.max(0, index - 4); i < path.size() && path.point(i).s() - along < 4; i++) {
						NavPath.Point p = path.point(i);
						if (p.surface() != Direction.DOWN) {
							avoidCells.put(BlockPos.containing(p.at().add(0, 0.5, 0)).asLong(), now + 1200);
						}
					}
					trouble(now);
					plan();
				}
			}
		} else {
			noGrip = 0;
		}

		if (goal == null || paused) {
			rauk.setPostureAhead(rauk.posture());
			hold();
			return;
		}
		if (follow != null) {
			if (!follow.isAlive() || follow.level() != rauk.level() || rauk.distanceTo(follow) < followStop) {
				stop();
				hold();
				return;
			}
			if (--repath <= 0 && follow.position().distanceTo(goal) > 0.8 || path.isEmpty() && repath <= 0) {
				goal = follow.position();
				plan();
			}
		}
		if (path.isEmpty()) {
			if (--repath <= 0) {
				plan();
			}
			hold();
			return;
		}
		// Back on the surface of the line (it took hold of the next, or fell off it): find its place.
		if (rauk.surface() != path.point(index).surface() && !rejoin()) {
			plan();
			if (path.isEmpty()) {
				hold();
				return;
			}
		}
		advance();
		slippingBack();
		if (fellOff()) {
			plan();
			if (path.isEmpty()) {
				hold();
				return;
			}
		}
		if (rauk.tickCount % 20 == 0) {
			checkStuck();
		}
		if (path.isEmpty()) {
			hold();
			return;
		}

		int change = path.nextChange(index);
		int runEnd = change < 0 ? path.size() - 1 : change - 1;
		double left = path.point(runEnd).s() - along;
		if (change < 0) {
			if (!pathFinal && left < 6 && rauk.tickCount - extended > 10) {
				extended = rauk.tickCount;
				// Near the end of the stretch it has: the next, without stopping.
				plan();
				if (path.isEmpty()) {
					hold();
					return;
				}
				change = path.nextChange(index);
				runEnd = change < 0 ? path.size() - 1 : change - 1;
				left = path.point(runEnd).s() - along;
			} else if (pathFinal && left < 0.4 && rauk.position().distanceTo(path.last().at()) < 0.6) {
				if (path.reached || rauk.position().distanceTo(goal) < 2) {
					goal = null;
					path = NavPath.EMPTY;
					route = List.of();
					hold();
					return;
				}
				// As near as that way went, not there: from here, again; if that gets no further, there is no way.
				if (rauk.tickCount - extended > 10) {
					extended = rauk.tickCount;
					plan();
					if (path.isEmpty() || !path.reached && path.length() < 1.5) {
						unreachable = true;
						goal = null;
						path = NavPath.EMPTY;
						route = List.of();
					}
				}
				hold();
				return;
			}
		}

		// Where it heads: a little way along the line; or, nearly at the place it takes hold of
		// something new, that place, the way it always has.
		SurfaceNode node = null;
		Vec3 aim;
		RaukPosture limit = path.point(index).posture();
		if (change >= 0 && (left < 0.9 || rauk.position().distanceTo(path.point(runEnd).at()) < 0.8)) {
			node = path.point(change).node();
			if (node == null) {
				node = nodeAt(path.point(change));
			}
			aim = dropAim(node);
			limit = node.posture();
			if (node.surface() == rauk.surface() && landed(node)) {
				// Down off a ledge and on the floor below: on along the line from there.
				index = change;
				along = path.point(change).s();
			}
		} else {
			aim = path.at(along + lookahead(), index);
			limit = tightestAhead(1.5);
		}

		// Fallen back off a step-over (on its feet in mid-air, under the sill it was making for):
		// it catches the wall again, rather than leaping at a sill it can't reach.
		if (node != null && rauk.surface() == Direction.DOWN && node.surface() == Direction.DOWN && !rauk.onGround()
				&& rauk.getDeltaMovement().y < 0 && node.position().y > rauk.getY() + 0.6) {
			Direction toward = facing(node.position());
			if (toward != Direction.DOWN && toward != Direction.UP && hasGrip(toward)) {
				attach(toward);
			} else {
				for (Direction d : Direction.Plane.HORIZONTAL) {
					if (hasGrip(d)) {
						attach(d);
						break;
					}
				}
			}
		}
		if (node != null && node.surface() != rauk.surface()) {
			double reach = rauk.position().distanceTo(node.position());
			boolean ready = true;
			if (rauk.surface() == Direction.DOWN) {
				// Off the floor onto a wall: one across a doorway is walked up to first, and one
				// down the far side of an edge (a doorway sill into a shaft) is taken hold of only
				// once it has tipped over, so the wall is under its hands.
				boolean overEdge = node.position().y < rauk.getY() - 0.5;
				double across = Math.hypot(node.position().x - rauk.getX(), node.position().z - rauk.getZ());
				ready = overEdge ? !rauk.onGround() || reach < 0.6 : reach < 1.25 || across < 0.7 || rauk.horizontalCollision && reach < 2.5;
				// Only onto something there is to hold: falling past it, it waits till it's there.
				ready &= hasGrip(node.surface(), 0.6);
			} else if (node.surface() != Direction.DOWN) {
				// From a wall or ceiling onto another: it takes hold once the other is under its
				// hands, not from where it is (round an edge, the body has to get there first).
				ready = within(node, 0.6) || reach < 0.5;
			} else if (node.position().y > rauk.getY() - 0.3) {
				// Up a wall and over the top onto a floor (out of a shaft through its doorway): it
				// climbs till its feet are level, narrows if the way over is narrow, and lets go
				// of the wall only once there is floor under it.
				boolean level = rauk.getY() >= node.position().y - 0.1;
				ready = level && supported();
				if (level && !ready) {
					// Holding a side wall, it moves round onto the wall the way over is in first.
					Direction toward = facing(node.position());
					if (toward != rauk.surface() && hasGrip(toward)) {
						attach(toward);
					}
					if (node.posture() == RaukPosture.SQUEEZE && rauk.posture() != RaukPosture.SQUEEZE && rauk.fits(RaukPosture.SQUEEZE)) {
						rauk.setPosture(RaukPosture.SQUEEZE);
					}
					aim = new Vec3(node.position().x, rauk.getY() + 0.02, node.position().z);
				}
			}
			if (ready) {
				attach(node.surface());
				index = change;
				along = path.point(change).s();
			}
		}
		if (rauk.surface() == Direction.DOWN || node == null || node.surface() == rauk.surface()) {
			choosePosture(limit);
		}
		rauk.setPostureAhead(lookAhead());
		rauk.setPathAhead(pointsAhead());
		openDoorsAhead();
		// Until it has taken hold of the next surface it moves the way it does on the one it's on.
		double remaining = change < 0 && pathFinal ? path.length() - along : Double.MAX_VALUE;
		steer(aim, rauk.surface(), remaining, node != null);
	}

	/** After a change of surface: its place on the line, among the next points on the surface it now holds. */
	private boolean rejoin() {
		Direction s = rauk.surface();
		int best = -1;
		double bestD = 2.5;
		for (int i = Math.max(0, index - 20); i < Math.min(path.size(), index + 40); i++) {
			NavPath.Point p = path.point(i);
			if (p.surface() != s) {
				continue;
			}
			double d = p.at().distanceTo(rauk.position());
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		if (best < 0) {
			return false;
		}
		index = best;
		along = path.point(best).s();
		return true;
	}

	/** Moves its place along the line on to wherever it now is, never back. */
	private void advance() {
		double[] p = path.project(rauk.position(), index, rauk.surface() == Direction.DOWN);
		int i = (int) p[0];
		if (i > index || p[1] > along) {
			index = i;
			along = Math.max(along, p[1]);
		}
	}

	/**
	 * Just over the top of a wall onto a floor and slipping back off the lip (it let go of the wall
	 * a moment too soon): it catches the wall again, and climbs over properly.
	 */
	private void slippingBack() {
		NavPath.Point p = path.point(index);
		if (rauk.surface() != Direction.DOWN || rauk.onGround() || rauk.getDeltaMovement().y > -0.08 || p.surface() != Direction.DOWN
				|| p.at().y < rauk.getY() + 0.4 || index == 0 || path.point(index - 1).surface() == Direction.DOWN) {
			return;
		}
		Direction wall = path.point(index - 1).surface();
		if (hasGrip(wall, 0.4)) {
			if (DEBUG) {
				Rauk.LOGGER.info("mover: slipping back off the lip at {}, takes hold of the wall again", fmt(rauk.position()));
			}
			attach(wall);
			rejoin();
		}
	}

	/**
	 * On its feet well above or below its line (it slipped back down the wall it climbed, or fell
	 * off a ledge): the climb that failed is kept off for a while, and it works out its way again.
	 */
	private boolean fellOff() {
		int change = path.nextChange(index);
		if (change >= 0 && path.point(change).node() != null && path.point(change).surface() == Direction.DOWN
				&& landed(path.point(change).node())) {
			// Down where it meant to drop to.
			index = change;
			along = path.point(change).s();
			return false;
		}
		NavPath.Point p = path.point(index);
		if (rauk.surface() != Direction.DOWN || !rauk.onGround() || p.surface() != Direction.DOWN || Math.abs(rauk.getY() - p.at().y) < 1.6
				|| rauk.tickCount - planTick < 30) {
			// (A way just worked out may start a little below it: it gets down to it first.)
			return false;
		}
		long now = rauk.level().getGameTime();
		for (int i = Math.max(0, index - 16); i <= index; i++) {
			NavPath.Point q = path.point(i);
			if (q.surface() != Direction.DOWN) {
				avoidCells.put(BlockPos.containing(q.at().add(0, 0.5, 0)).asLong(), now + 1200);
			}
		}
		if (DEBUG) {
			Rauk.LOGGER.info("mover: off its line at {} (line at {}), another way", fmt(rauk.position()), fmt(p.at()));
		}
		trouble(now);
		return true;
	}

	/** Whether it has come down onto a landing it dropped to. */
	private boolean landed(SurfaceNode node) {
		return rauk.onGround() && Math.abs(rauk.getY() - node.position().y) < 0.7
				&& Math.hypot(rauk.getX() - node.position().x, rauk.getZ() - node.position().z) < 1.6;
	}

	/** A place on the line as a place of its own (where the search's isn't kept). */
	private SurfaceNode nodeAt(NavPath.Point p) {
		return new SurfaceNode(BlockPos.containing(p.at().add(0, 0.05, 0)), p.surface(), p.posture(), p.at());
	}

	/**
	 * How far ahead along the line it heads for: further the faster it goes; only a little way in a
	 * tight place (a doorway, a low passage), where cutting in would put it against the frame.
	 */
	private double lookahead() {
		if (rauk.surface() != Direction.DOWN) {
			return 0.8;
		}
		RaukPosture tight = path.isEmpty() ? rauk.posture() : tightestAhead(2);
		if (tight == RaukPosture.SQUEEZE || rauk.posture() == RaukPosture.SQUEEZE) {
			return 0.55;
		}
		double v = walk.horizontalDistance() * 20;
		return Mth.clamp(0.9 + 0.13 * Math.max(v, Math.min(speed, 4)), 1.0, 2.6);
	}

	/** The tightest it must fold on the line close ahead. */
	private RaukPosture tightestAhead(double distance) {
		RaukPosture tight = path.point(index).posture();
		Direction s = path.point(index).surface();
		for (int i = index; i < path.size(); i++) {
			NavPath.Point p = path.point(i);
			if (p.surface() != s || p.s() - along > distance) {
				break;
			}
			if (p.posture().ordinal() > tight.ordinal()) {
				tight = p.posture();
			}
		}
		return tight;
	}

	/**
	 * Something went wrong here (stuck, or lost its grip). Three times close by within a minute, and
	 * the doorway it is making for is given up for a minute: there is another way.
	 */
	private void trouble(long now) {
		Vec3 here = rauk.position();
		troubles.removeIf(t -> now - t[3] > 1200);
		int near = 0;
		for (double[] t : troubles) {
			if (here.distanceToSqr(t[0], t[1], t[2]) < 16) {
				near++;
			}
		}
		troubles.add(new double[] {here.x, here.y, here.z, now});
		if (near >= 2 && !route.isEmpty()) {
			HotelGraph.Gate g = route.get(0);
			failedGates.put(BlockPos.containing(g.at()).asLong(), now + 1200);
			field = null;
			troubles.clear();
			if (DEBUG) {
				Rauk.LOGGER.info("mover: trouble three times at {}: gives up the doorway at {}", fmt(here), fmt(g.at()));
			}
		}
	}

	/**
	 * Stuck: no real progress for a second. It works its way out again from where it is; stuck
	 * again, it keeps off the blocks ahead of it for half a minute; again, it gives up the doorway
	 * it was making for and goes another way; after that it tries to shake itself loose, and in
	 * the end tells whoever sent it that it can't get there.
	 */
	private void checkStuck() {
		Vec3 moved = rauk.position().subtract(progressPos);
		// On the floor only getting somewhere counts: bobbing up and down against a wall it can't
		// take hold of is being stuck too. Along the line only counts on the same line.
		double progress = rauk.surface() == Direction.DOWN && !rauk.isPassenger() ? Math.hypot(moved.x, moved.z) : moved.length();
		double gained = plans == plansChecked ? along - progressAlong : 0;
		progressPos = rauk.position();
		progressAlong = along;
		plansChecked = plans;
		// Getting somewhere is a third of what it could cover in that second, at most 0.3 blocks:
		// folded small on a stair it goes slowly, and that isn't being stuck.
		double expected = Math.min(speed, rauk.speedCap()) * speedFactor(rauk.posture(), rauk.surface());
		double enough = Mth.clamp(expected / 3, 0.08, 0.3);
		if (progress >= enough || gained >= enough) {
			stuck = 0;
			return;
		}
		stuck++;
		long now = rauk.level().getGameTime();
		trouble(now);
		if (DEBUG) {
			StringBuilder ahead = new StringBuilder();
			for (int i = index; i < Math.min(path.size(), index + 12); i += 2) {
				NavPath.Point p = path.point(i);
				ahead.append(fmt(p.at())).append(p.surface().getSerializedName().charAt(0)).append(p.posture().name().charAt(0)).append(' ');
			}
			StringBuilder hits = new StringBuilder();
			AABB box = rauk.getBoundingBox();
			for (Vec3 probe : new Vec3[] {new Vec3(0.2, 0, 0), new Vec3(-0.2, 0, 0), new Vec3(0, 0, 0.2), new Vec3(0, 0, -0.2), new Vec3(0, 0.2, 0)}) {
				for (var shape : rauk.level().getBlockCollisions(rauk, box.move(probe))) {
					hits.append(fmt(probe)).append("->").append(shape.bounds()).append(' ');
					break;
				}
			}
			Rauk.LOGGER.info("mover: stuck ({}) at {} {} {} on the way to {}; ahead {}; blocked {}", stuck, fmt(rauk.position()), rauk.surface(),
					rauk.posture(), fmt(goal), ahead, hits);
		}
		switch (stuck) {
			case 1 -> plan();
			case 2 -> {
				// The blocks just ahead are no way, for now.
				for (int i = index; i < path.size() && path.point(i).s() - along < 2.5; i++) {
					BlockPos c = BlockPos.containing(path.point(i).at().add(0, 0.05, 0));
					if (!c.equals(rauk.blockPosition())) {
						avoidCells.put(c.asLong(), now + 600);
					}
				}
				plan();
			}
			case 3 -> {
				if (!route.isEmpty()) {
					failedGates.put(BlockPos.containing(route.get(0).at()).asLong(), now + 1200);
					field = null;
				}
				plan();
			}
			default -> {
				// A shove up and on, as if it had scrambled.
				if (rauk.surface() == Direction.DOWN && rauk.onGround()) {
					Vec3 on = path.at(along + 1.5, index).subtract(rauk.position());
					Vec3 flat = new Vec3(on.x, 0, on.z);
					if (flat.lengthSqr() > 1e-4) {
						flat = flat.normalize().scale(0.25);
					}
					rauk.setDeltaMovement(flat.x, 0.5, flat.z);
				}
				if (stuck >= 6) {
					unreachable = true;
					stuck = 0;
				}
				plan();
				unreachable |= stuck == 0;
			}
		}
	}

	/** The tightest posture on the floor in the next few blocks of its line: the body lowers for it early. */
	private RaukPosture lookAhead() {
		RaukPosture now = rauk.posture();
		if (rauk.surface() != Direction.DOWN || now == RaukPosture.CLING) {
			return now;
		}
		RaukPosture tight = now;
		for (int i = index; i < path.size(); i++) {
			NavPath.Point p = path.point(i);
			if (p.s() - along > 3.5) {
				break;
			}
			if (p.surface() == Direction.DOWN && p.posture() != RaukPosture.CLING && p.posture().ordinal() > tight.ordinal()) {
				tight = p.posture();
			}
		}
		return tight;
	}

	/**
	 * Points along its line one, two, three, four and five blocks on, for the body to follow (the
	 * head leads round a corner, through a doorway and over a ledge before the rest of it gets
	 * there): on whatever surface the line is on there, each where the middle of its body sits on
	 * that surface (design doc 5.12).
	 */
	private List<Vec3> pointsAhead() {
		List<Vec3> out = new ArrayList<>(5);
		// From where it is along the line, if that is further on than where it is steering from: at
		// the top of a climb it rises past the last point up the wall before it gets over.
		double from = along;
		int start = index;
		Vec3 pos = rauk.position();
		double best = Double.MAX_VALUE;
		for (int i = index; i < Math.min(path.size(), index + 24); i++) {
			double d = path.point(i).at().distanceToSqr(pos);
			if (d < best) {
				best = d;
				start = i;
			}
		}
		if (path.point(start).s() > from) {
			from = path.point(start).s();
		} else {
			start = index;
		}
		for (int k = 1; k <= 5; k++) {
			double target = from + k;
			int i = start;
			while (i < path.size() - 1 && path.point(i + 1).s() < target) {
				i++;
			}
			if (i >= path.size() - 1) {
				break;
			}
			NavPath.Point a = path.point(i), b = path.point(i + 1);
			double span = b.s() - a.s(), t = span < 1e-9 ? 0 : Mth.clamp((target - a.s()) / span, 0, 1);
			NavPath.Point near = t < 0.5 ? a : b;
			Vec3 d = b.at().subtract(a.at());
			double climb = d.length() > 1e-6 ? d.y / d.length() : 0;
			out.add(a.at().lerp(b.at(), t).add(onSurface(near.surface(), near.posture(), climb)));
		}
		return out;
	}

	/** How far behind the middle of its box its body's middle sits climbing a wall, the way it climbs. */
	public static final double CLIMB_BACK = 0.6;

	/**
	 * Where the middle of its body sits relative to its box's bottom centre: on the surface it holds;
	 * on a wall a little behind the middle of its box the way it climbs (climb: up 1, down -1).
	 */
	public static Vec3 onSurface(Direction surface, RaukPosture p, double climb) {
		return switch (surface) {
			case DOWN -> Vec3.ZERO;
			case UP -> new Vec3(0, p.height, 0);
			default -> new Vec3(surface.getStepX() * p.width / 2, p.height / 2 - CLIMB_BACK * climb, surface.getStepZ() * p.width / 2);
		};
	}

	private boolean close(SurfaceNode node) {
		Vec3 to = node.position().subtract(rauk.position());
		return to.length() < (node.surface() == rauk.surface() ? 0.45 : 0.7);
	}

	/**
	 * Where to head for a place it is about to take hold at. Usually the place itself; but when it
	 * is on the floor below, down through a hole, the spot over the hole where its body falls
	 * clear, so it doesn't stand on the lip.
	 */
	private Vec3 dropAim(SurfaceNode node) {
		Vec3 at = node.position();
		if (rauk.surface() != Direction.DOWN || node.surface() != Direction.DOWN || at.y > rauk.getY() - 1.2) {
			return at;
		}
		if (new Vec3(at.x - rauk.getX(), 0, at.z - rauk.getZ()).length() > 1.6) {
			return at;
		}
		Vec3 clear = NavRules.world(rauk.level()).dropPoint(at, rauk.getY(), rauk.posture());
		return clear == null ? at : new Vec3(clear.x, at.y, clear.z);
	}

	/**
	 * Caught inside a block (put down too close to a wall, a posture that didn't fit, a landing):
	 * it is eased out to the nearest place its body is clear, rather than left stuck there.
	 */
	private void unstick() {
		AABB box = rauk.getBoundingBox().deflate(0.01);
		if (rauk.level().noCollision(rauk, box)) {
			return;
		}
		// The body it should have here, if a tighter one fits where it is.
		if (rauk.surface() != Direction.DOWN && rauk.posture() != RaukPosture.CLING && rauk.posture() != RaukPosture.SQUEEZE) {
			for (RaukPosture p : new RaukPosture[] {RaukPosture.CLING, RaukPosture.SQUEEZE}) {
				if (rauk.fits(p)) {
					rauk.setPosture(p);
					return;
				}
			}
		}
		Direction[] ways = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
		for (double d = 0.1; d <= 1.01; d += 0.1) {
			for (Direction way : ways) {
				Vec3 off = new Vec3(way.getStepX() * d, way.getStepY() * d, way.getStepZ() * d);
				if (rauk.level().noCollision(rauk, box.move(off))) {
					if (DEBUG) {
						Rauk.LOGGER.info("mover: stuck in a block at {}, out {} by {}", fmt(rauk.position()), way, String.format("%.1f", d));
					}
					rauk.setPos(rauk.position().add(off));
					return;
				}
			}
		}
	}

	/** Standing still: on a wall or ceiling it stays pressed to it. */
	private void hold() {
		rauk.setPathAhead(List.of());
		Direction surface = rauk.surface();
		if (surface == Direction.DOWN) {
			walk = walk.scale(0.5);
			Vec3 v = rauk.getDeltaMovement();
			rauk.setDeltaMovement(walk.x, v.y, walk.z);
			choosePosture(null);
		} else {
			rauk.setDeltaMovement(press(surface));
		}
	}

	private static Vec3 press(Direction surface) {
		return new Vec3(surface.getStepX(), surface.getStepY(), surface.getStepZ()).scale(0.06);
	}

	/**
	 * Heads for a point. On the floor it runs: its speed is set by the bends and narrows and the end
	 * of the line ahead (it slows in time for each, by how hard it can brake), and its velocity
	 * swings round toward the point no faster than it can turn its weight, so it runs in curves.
	 * On walls and ceilings it climbs straight for the point, pressed to the surface.
	 */
	private void steer(Vec3 target, Direction surface, double remaining, boolean exact) {
		Vec3 to = target.subtract(rauk.position());
		double top = Math.min(speed, rauk.speedCap()) * speedFactor(rauk.posture(), surface);
		if (surface == Direction.DOWN) {
			Vec3 flat = new Vec3(to.x, 0, to.z);
			double len = flat.length();
			double v = Math.min(top, allowedSpeed(top, exact));
			// Into the end of the line, or onto the place it takes hold at: it eases in.
			boolean last = remaining < lookahead() + 0.1;
			if (exact) {
				v = Math.min(v, Math.sqrt(2 * DECEL * 0.5 * Math.max(0, len)) + 0.4);
			} else if (remaining < Double.MAX_VALUE) {
				v = Math.min(v, Math.sqrt(2 * DECEL * 0.5 * Math.max(0, remaining)) + 0.4);
			}
			double perTick = v / 20;
			double pace = last ? Math.min(perTick, len) : exact ? Math.min(perTick, Math.max(len, perTick * 0.5)) : perTick;
			Vec3 want = len > 1e-3 ? flat.scale(pace / len) : Vec3.ZERO;
			want = new Vec3(nudge(want.x, to.x), 0, nudge(want.z, to.z));
			Vec3 change = want.subtract(walk);
			// Folded small it is nimble: in a doorway it corrects at once.
			double limit = (want.lengthSqr() < walk.lengthSqr() ? DECEL : ACCEL) / 400 * (rauk.posture() == RaukPosture.SQUEEZE ? 2 : 1);
			walk = change.length() > limit ? walk.add(change.scale(limit / change.length())) : want;
			walk = new Vec3(nudge(walk.x, to.x), 0, nudge(walk.z, to.z));
			rauk.setDeltaMovement(walk.x, rauk.getDeltaMovement().y, walk.z);
			Vec3 face = walk.horizontalDistance() > 0.02 ? walk : flat;
			if (face.horizontalDistance() > 1e-3 && len > 0.1) {
				float yaw = (float) Math.toDegrees(Math.atan2(-face.x, face.z));
				double turn = Mth.lerp(Mth.clamp(walk.horizontalDistance() * 20 / 12, 0, 1), 20, 11);
				rauk.setYRot(Mth.approachDegrees(rauk.getYRot(), yaw, (float) turn));
				rauk.yBodyRot = rauk.getYRot();
			}
			// A ledge or step it can't walk up: the step height covers one block, a hop does the rest.
			if (rauk.horizontalCollision && rauk.onGround() && to.y > 0.6) {
				rauk.setDeltaMovement(walk.x, 0.55, walk.z);
			}
		} else {
			// A hair off lined up along the surface (a thousandth of a block under a landing's edge is
			// enough to stop it going up past it), it is put exactly in line.
			double sx = Math.abs(to.x) < 0.01 && surface.getAxis() != Direction.Axis.X ? to.x : 0;
			double sy = Math.abs(to.y) < 0.01 && surface.getAxis() != Direction.Axis.Y ? to.y : 0;
			double sz = Math.abs(to.z) < 0.01 && surface.getAxis() != Direction.Axis.Z ? to.z : 0;
			if ((sx != 0 || sy != 0 || sz != 0) && rauk.level().noCollision(rauk, rauk.getBoundingBox().move(sx, sy, sz))) {
				rauk.setPos(rauk.getX() + sx, rauk.getY() + sy, rauk.getZ() + sz);
				to = to.subtract(sx, sy, sz);
			}
			double len = to.length();
			double perTick = top / 20;
			Vec3 move = len > 1e-3 ? to.scale(Math.min(perTick, len) / len) : Vec3.ZERO;
			// On a wall a hair short of a lip (the edge of a ceiling hole) the last bit still moves it.
			move = new Vec3(nudge(move.x, to.x), nudge(move.y, to.y), nudge(move.z, to.z));
			rauk.setDeltaMovement(move.add(press(surface)));
			walk = Vec3.ZERO;
		}
	}

	/**
	 * The fastest it can be going now and still take what is ahead on the line: every bend at no
	 * more than it can corner at, every narrowing and change of surface slowly, all braked for in
	 * time.
	 */
	private double allowedSpeed(double top, boolean exact) {
		if (path.isEmpty() || exact) {
			return top;
		}
		double horizon = top * top / (2 * DECEL) + 1.5;
		double allowed = top;
		Direction s = path.point(index).surface();
		RaukPosture now = rauk.posture();
		for (int i = Math.max(1, index); i < path.size() - 1; i++) {
			NavPath.Point p = path.point(i);
			double d = p.s() - along;
			if (d > horizon) {
				break;
			}
			double cap = top;
			NavPath.Point next = path.point(i + 1);
			if (next.surface() != s) {
				cap = 2.5;
			} else {
				Vec3 a = p.at().subtract(path.point(i - 1).at()), b = next.at().subtract(p.at());
				double la = Math.hypot(a.x, a.z), lb = Math.hypot(b.x, b.z);
				if (la > 1e-3 && lb > 1e-3) {
					double cos = Mth.clamp((a.x * b.x + a.z * b.z) / (la * lb), -1, 1);
					double angle = Math.acos(cos);
					if (angle > 0.02) {
						double curvature = angle / Math.max(0.15, (la + lb) / 2);
						cap = Math.sqrt(LATERAL / curvature);
					}
				}
				if (p.posture().ordinal() > now.ordinal() && p.posture() != RaukPosture.CLING) {
					cap = Math.min(cap, Math.min(speed, rauk.speedCap()) * speedFactor(p.posture(), s));
				}
			}
			allowed = Math.min(allowed, Math.sqrt(cap * cap + 2 * DECEL * Math.max(0, d)));
			if (next.surface() != s) {
				break;
			}
		}
		return allowed;
	}

	/**
	 * Keeps a small correction big enough that vanilla doesn't drop it, all the way in: lined up a
	 * hair off a narrow doorway it would otherwise stay pressed against the frame.
	 */
	private static double nudge(double v, double distance) {
		if (Math.abs(distance) > 0.004 && Math.abs(v) < 0.008 && Math.signum(v) != -Math.signum(distance)) {
			return Math.copySign(Math.max(0.0035, Math.min(0.008, Math.abs(distance))), distance);
		}
		return v;
	}

	private static double speedFactor(RaukPosture posture, Direction surface) {
		double f = switch (posture) {
			case SQUEEZE -> 0.3;
			case CRAWL -> 0.6;
			default -> 1.0;
		};
		if (surface == Direction.UP) {
			f *= 0.6;
		} else if (surface != Direction.DOWN) {
			f *= 0.8;
		}
		return f;
	}

	// --- posture and surfaces -------------------------------------------------------------------

	/**
	 * On the floor: its preferred posture (upright or on all fours) if that fits here, otherwise
	 * the largest that does, no larger than the line allows here. On walls and ceilings it clings,
	 * squeezing in where it's narrow.
	 */
	private void choosePosture(@Nullable RaukPosture limit) {
		Direction surface = rauk.surface();
		RaukPosture[] order = surface == Direction.DOWN
				? new RaukPosture[] {rauk.floorStyle(), RaukPosture.QUADRUPED, RaukPosture.CRAWL, RaukPosture.SQUEEZE}
				: new RaukPosture[] {RaukPosture.CLING, RaukPosture.SQUEEZE};
		RaukPosture max = surface == Direction.DOWN && limit != null && limit != RaukPosture.CLING ? limit : null;
		for (RaukPosture p : order) {
			boolean allowed = max == null || p.ordinal() >= max.ordinal() || (p == RaukPosture.UPRIGHT && max == RaukPosture.QUADRUPED);
			if (!allowed || (p == RaukPosture.UPRIGHT && rauk.floorStyle() != RaukPosture.UPRIGHT)) {
				continue;
			}
			if (p == rauk.posture() || rauk.fits(p)) {
				if (p != rauk.posture()) {
					rauk.setPosture(p);
				}
				return;
			}
		}
	}

	/** Takes hold of a surface (or lets go onto the floor with DOWN). */
	public void attach(Direction surface) {
		attachedAt = rauk.tickCount;
		rauk.setSurface(surface);
		rauk.setNoGravity(surface != Direction.DOWN);
		rauk.resetFallDistance();
		if (surface != Direction.DOWN) {
			choosePosture(null);
		}
	}

	/**
	 * Whether the very block a place holds on to is under its hands: on that side of its body,
	 * within reach, and alongside it (a landing over its head is no wall in front of it).
	 */
	private boolean within(SurfaceNode node, double reach) {
		BlockPos support = node.cell().relative(node.surface());
		return face(node.surface(), reach).intersects(new AABB(support))
				&& NavRules.world(rauk.level()).grips(support, node.surface().getOpposite());
	}

	/** The slab of space just beyond one side of its body, as deep as it reaches. */
	private AABB face(Direction surface, double reach) {
		AABB box = rauk.getBoundingBox();
		return switch (surface) {
			case UP -> new AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY + reach, box.maxZ);
			case DOWN -> new AABB(box.minX, box.minY - reach, box.minZ, box.maxX, box.minY, box.maxZ);
			case NORTH -> new AABB(box.minX, box.minY, box.minZ - reach, box.maxX, box.maxY, box.minZ);
			case SOUTH -> new AABB(box.minX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ + reach);
			case WEST -> new AABB(box.minX - reach, box.minY, box.minZ, box.minX, box.maxY, box.maxZ);
			case EAST -> new AABB(box.maxX, box.minY, box.minZ, box.maxX + reach, box.maxY, box.maxZ);
		};
	}

	/**
	 * Whether it is on its way from the surface it holds onto another close by (a wall onto a
	 * ceiling, round the edge of a landing), so a moment without a grip is part of the move.
	 */
	private boolean rounding() {
		if (goal == null || paused || path.isEmpty()) {
			return false;
		}
		int change = path.nextChange(index);
		if (change < 0) {
			return false;
		}
		NavPath.Point next = path.point(change);
		if (next.surface() == rauk.surface() || rauk.position().distanceTo(next.at()) >= 2.6) {
			return false;
		}
		// Onto another wall or the ceiling; or up and over the top of the wall onto a floor (out of a
		// shaft, onto a landing), where its hands are past the top before its feet are.
		return next.surface() != Direction.DOWN || next.at().y > rauk.getY() - 0.3;
	}

	/** Which way a place is from it, along whichever level axis it's furthest along. */
	private Direction facing(Vec3 at) {
		double dx = at.x - rauk.getX(), dz = at.z - rauk.getZ();
		return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	/** Whether there is floor under it: it couldn't sink a little without hitting something. */
	private boolean supported() {
		return !NavRules.world(rauk.level()).fits(rauk.getBoundingBox().move(0, -0.15, 0).deflate(0.02, 0, 0.02));
	}

	/** Whether there is still something to hold on to on that side. */
	private boolean hasGrip(Direction surface) {
		return hasGrip(surface, 0.2);
	}

	/** Whether there is something to hold on to on that side within reach. */
	private boolean hasGrip(Direction surface, double reach) {
		AABB box = rauk.getBoundingBox();
		AABB face = switch (surface) {
			case UP -> new AABB(box.minX, box.maxY, box.minZ, box.maxX, box.maxY + reach, box.maxZ);
			case NORTH -> new AABB(box.minX, box.minY, box.minZ - reach, box.maxX, box.maxY, box.minZ);
			case SOUTH -> new AABB(box.minX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ + reach);
			case WEST -> new AABB(box.minX - reach, box.minY, box.minZ, box.minX, box.maxY, box.maxZ);
			case EAST -> new AABB(box.maxX, box.minY, box.minZ, box.maxX + reach, box.maxY, box.maxZ);
			default -> box;
		};
		NavRules rules = NavRules.world(rauk.level());
		for (BlockPos p : BlockPos.betweenClosed(Mth.floor(face.minX), Mth.floor(face.minY), Mth.floor(face.minZ),
				Mth.floor(face.maxX - 1e-4), Mth.floor(face.maxY - 1e-4), Mth.floor(face.maxZ - 1e-4))) {
			if (rules.grips(p, surface.getOpposite())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Opens doors, gates and trapdoors on the next stretch of the line and leaves them open. Its
	 * head goes a good four blocks ahead of the middle of its body, and a door takes a moment to
	 * swing: so it reaches for one six blocks on, further the faster it goes, and the door is open
	 * before its head gets there (design doc 5.12).
	 */
	private void openDoorsAhead() {
		Level level = rauk.level();
		double ahead = 6 + speed * 0.5;
		for (int i = index; i < path.size(); i += 2) {
			NavPath.Point p = path.point(i);
			if (p.s() - along > ahead) {
				break;
			}
			// Only what the line goes through, not what it brushes past: a narrow box down its middle.
			AABB full = p.posture().dimensions().makeBoundingBox(p.at());
			AABB box = new AABB(p.at().x - 0.3, full.minY, p.at().z - 0.3, p.at().x + 0.3, full.maxY, p.at().z + 0.3);
			for (BlockPos b : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ), Mth.floor(box.maxX),
					Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
				open(level, b.immutable());
			}
		}
	}

	private void open(Level level, BlockPos p) {
		BlockState state = level.getBlockState(p);
		if (state.getBlock() instanceof DoorBlock door && RaukPathfinder.openable(state) && !door.isOpen(state)) {
			door.setOpen(rauk, level, state, p, true);
			BlockPos lower = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? p : p.below();
			rauk.mind().territory.expect(lower, level.getBlockState(lower));
		} else if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
			level.setBlock(p, state.setValue(FenceGateBlock.OPEN, true), 10);
			rauk.mind().territory.expect(p, level.getBlockState(p));
			level.playSound(null, p, SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS, 1, 0.8f);
		} else if (state.getBlock() instanceof TrapDoorBlock && RaukPathfinder.openable(state) && !state.getValue(TrapDoorBlock.OPEN)) {
			level.setBlock(p, state.setValue(TrapDoorBlock.OPEN, true), 10);
			rauk.mind().territory.expect(p, level.getBlockState(p));
			level.playSound(null, p, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1, 0.8f);
		}
	}

	// --- leaping --------------------------------------------------------------------------------

	private void tickLeap() {
		rauk.setPathAhead(List.of());
		Leap l = leap;
		l.tick++;
		if (!l.flying) {
			// The wind-up: crouched and coiled, then it goes.
			rauk.setDeltaMovement(rauk.surface() == Direction.DOWN ? new Vec3(0, rauk.getDeltaMovement().y, 0) : press(rauk.surface()));
			if (l.tick >= l.windup) {
				l.flying = true;
				l.tick = 0;
				rauk.setSurface(Direction.DOWN);
				rauk.setNoGravity(true);
				if (rauk.fits(RaukPosture.SQUEEZE)) {
					rauk.setPosture(RaukPosture.SQUEEZE);
				}
				rauk.setLeapState(LEAP_FLIGHT);
			}
			return;
		}
		Vec3 want = l.at(Math.min(1, (l.tick + 1) / (double) l.flight));
		rauk.setDeltaMovement(want.subtract(rauk.position()));
		boolean hit = l.tick > 2 && (rauk.horizontalCollision || rauk.verticalCollision);
		if (l.tick >= l.flight || hit) {
			Direction surface = l.tick >= l.flight ? l.surface : impact();
			leap = null;
			attach(surface);
			rauk.setDeltaMovement(surface == Direction.DOWN ? Vec3.ZERO : press(surface));
			rauk.setLeapState(LEAP_LAND);
			landing = 8;
			landed(surface, l.from.y - rauk.getY());
		}
	}

	/**
	 * Landing: dust and grit off whatever it hit, plaster dust off walls and ceilings; a hard landing
	 * from high up shakes the floor (the client does the shaking and the sound).
	 */
	private void landed(Direction surface, double drop) {
		if (!(rauk.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 at = switch (surface) {
			case DOWN -> rauk.position();
			case UP -> rauk.position().add(0, rauk.getBbHeight(), 0);
			default -> rauk.position().add(surface.getStepX() * rauk.getBbWidth() / 2, rauk.getBbHeight() / 2,
					surface.getStepZ() * rauk.getBbWidth() / 2);
		};
		BlockPos block = BlockPos.containing(at.add(surface.getStepX() * 0.3, surface.getStepY() * 0.3, surface.getStepZ() * 0.3));
		BlockState state = rauk.level().getBlockState(block);
		if (!state.isAir()) {
			level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, state),
					at.x, at.y + 0.1, at.z, 30 + (int) Math.min(30, drop * 4), 0.9, 0.15, 0.9, 0.15);
		}
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.WHITE_ASH, at.x, at.y + 0.3, at.z, 24, 1.2, 0.4, 1.2, 0.02);
		if (surface != Direction.DOWN) {
			// Plaster dust drifting down from where it struck the wall or ceiling.
			level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.FALLING_DUST,
					net.minecraft.world.level.block.Blocks.CALCITE.defaultBlockState()), at.x, at.y, at.z, 16, 0.8, 0.3, 0.8, 0);
		}
		rauk.landedFrom(drop);
	}

	/** Which way the surface it ran into faces, from how it was moving when it hit. */
	private Direction impact() {
		Vec3 v = rauk.getDeltaMovement();
		if (rauk.verticalCollision) {
			return v.y > 0 ? Direction.UP : Direction.DOWN;
		}
		return Math.abs(v.x) > Math.abs(v.z) ? (v.x > 0 ? Direction.EAST : Direction.WEST) : (v.z > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	public boolean isLeaping() {
		return leap != null;
	}
}

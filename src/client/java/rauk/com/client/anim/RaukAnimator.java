package rauk.com.client.anim;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import rauk.com.client.model.RaukRig;
import rauk.com.client.sound.RaukSoundClient;
import rauk.com.client.model.RaukRig.Frame;
import rauk.com.client.model.RaukRig.V;
import rauk.com.entity.RaukEntity;
import rauk.com.entity.RaukPosture;
import rauk.com.entity.combat.RaukCombat;

import rauk.com.entity.mind.RaukMind;
import rauk.com.entity.move.RaukMover;

/**
 * Rauk's procedural body (design doc section 5), run on the client every frame from the state the
 * server syncs: where it is, its posture, the surface it holds on to, whether it is leaping,
 * Tension, Attention, what it is looking at, whether it has risen to observe, and what last
 * startled it.
 *
 * <p>Each frame: the body planner orients the body to its surface (on a wall "up" points away from
 * the wall, on a ceiling it hangs upside down) and sets the height, pitch and lean from the
 * posture's stance; the gait engine plants and lifts the feet (and the hands, on all fours) on that
 * surface so planted limbs never slide; two-bone IK places the legs and arms; the neck bends so
 * the head reaches its carriage point and the skull looks at its target, keeping as upright in the
 * world as the neck allows; the tail and the drool are ropes that hang, trail and drag under real
 * gravity. Fast on all fours it gallops, the spine flexing and stretching with each stride.
 * Everything is worked out in body space (design space turned to the body's orientation, origin on
 * the surface under it) and handed to the model as each part's transform relative to its parent.
 */
public final class RaukAnimator {
	private static final double BALL_HEIGHT = 0.30;

	/**
	 * How the body is held in a posture: pelvis height above the surface, its pitch and how far back
	 * it sits; how much each vertebra arches (negative flattens the hump); where the skull is
	 * carried; where the feet and hands go (sideways as a multiple of the hip or shoulder width,
	 * forward in blocks); which way the knees and elbows point out; how far the shoulders collapse
	 * inward; whether the arms walk (1) or hang (0); and the tilt of the metatarsus in degrees.
	 */
	private record Stance(double height, double pitch, double z, double arch, V head, double legOut, double legZ,
			double armOut, double armZ, double kneeOut, double elbowOut, double shoulderIn, double armsDown, double metaTilt) {
		Stance toward(Stance o, double t) {
			return new Stance(mix(height, o.height, t), mix(pitch, o.pitch, t), mix(z, o.z, t), mix(arch, o.arch, t),
					head.lerp(o.head, t), mix(legOut, o.legOut, t), mix(legZ, o.legZ, t), mix(armOut, o.armOut, t),
					mix(armZ, o.armZ, t), mix(kneeOut, o.kneeOut, t), mix(elbowOut, o.elbowOut, t),
					mix(shoulderIn, o.shoulderIn, t), mix(armsDown, o.armsDown, t), mix(metaTilt, o.metaTilt, t));
		}

		private static double mix(double a, double b, double t) {
			return a + (b - a) * t;
		}
	}

	private final Stance upright, quadruped, crawl, squeeze, cling;
	private Stance stance;

	/**
	 * A gait: where each limb lifts off in the cycle (left leg, right leg, left arm, right arm), the
	 * share of the cycle a limb spends in the air, and the stride (blocks per cycle) standing plus
	 * per block per second. The gallop is rotary (hinds then fronts, with long flights); on walls
	 * the limbs move in diagonal pairs like a gecko's; on a ceiling one at a time.
	 */
	private record Gait(double[] offsets, double swing, double stride, double stridePerSpeed) {
	}

	private static final Gait WALK = new Gait(new double[] {0, 0.5, 0.25, 0.75}, 0.42, 2.0, 0.35);
	private static final Gait PROWL = new Gait(new double[] {0, 0.5, 0.25, 0.75}, 0.30, 2.3, 0.25);
	private static final Gait GALLOP = new Gait(new double[] {0, 0.12, 0.62, 0.5}, 0.62, 3.2, 0.38);
	private static final Gait CREEP = new Gait(new double[] {0, 0.5, 0.25, 0.75}, 0.30, 1.6, 0.30);
	private static final Gait CLIMB = new Gait(new double[] {0, 0.5, 0.5, 0}, 0.38, 1.8, 0.30);
	private static final Gait CEILING = new Gait(new double[] {0, 0.5, 0.25, 0.75}, 0.22, 1.8, 0.25);

	private final RaukSkeleton sk = RaukSkeleton.shared();
	/** Each part's transform relative to its parent this frame; null keeps the rest pose. */
	public final Xform[] local;
	private final Xform[] body;
	/** How the body is turned in the world, and where its origin sits relative to the entity's position. */
	public Quat orientation = Quat.IDENTITY;
	public V rootOffset = V.ZERO;
	/** Minecraft yaw of the body on the floor, in degrees. */
	private double heading;
	private V forward = V.Z;

	private double time = -1;
	private V root = V.ZERO;
	private V position = V.ZERO;
	private final VSpring velocity = new VSpring(), offset = new VSpring();
	private final Spring headingSpring = new Spring(), tension = new Spring(), attention = new Spring();
	private final Spring lean = new Spring(), coil = new Spring(), panting = new Spring();
	private double forwardSpeed;
	private double headingRate;

	private double phase;
	private boolean galloping;
	private final Foot[] feet = new Foot[4];
	/** 0 standing normally, 1 risen to full height; moves linearly so a rise takes five seconds. */
	private double rise;
	private int startlesSeen = Integer.MIN_VALUE;
	private double startleTime = -100;
	private V startleAt = V.ZERO;
	private int lastLeap;
	/** The fight: what it holds, the grab's phase and when it started. */
	@Nullable
	private Entity held;
	private int grabPhase;
	private double grabPhaseStart, actionEnd;
	private boolean dead, carrying;
	/** Room above the head (blocks from the floor), measured now and then, so the skull ducks under lintels. */
	private double headRoom = 99;
	/** Blinking (design doc 5.8): when the membrane last swept across, and when it will next. */
	private double blinkStart = -100, nextBlink;
	private double lastAttention;
	/** The last tick this body was worked out, so it can be kept going off screen. */
	public int lastUpdateTick = Integer.MIN_VALUE;
	/** For the sounds the body makes: the breath being held, squeezing, dragging, the last gallop, a skid. */
	private boolean breathHeld, skidded;
	private RaukPosture lastPosture = RaukPosture.UPRIGHT;
	private double lastDrag, lastGallop = -100;
	/**
	 * How far the back is being pressed down under a low ceiling, 0 (free) to about 1: the pelvis
	 * lowers, the body levels and the spine straightens until the hump fits. Worked out from the
	 * ceiling over each vertebra of the back; the trunk's joints and the ceiling over each, kept a
	 * tick at a time.
	 */
	private double flatten;
	private final int[] trunk;
	private final double[] trunkCeiling;
	private int ceilingTick = Integer.MIN_VALUE;
	/** How fast it is going, for the tail: dragged behind when moving, curled up again when still. */
	private double lastSpeed;

	/** The world transform last used, for hit tests, and where its face is this frame. */
	private Xform lastToWorld = Xform.IDENTITY;
	public V faceWorld = V.ZERO;
	private Direction lastSurface = Direction.DOWN;
	private int noticesSeen = Integer.MIN_VALUE;
	private double noticeTime = -100;
	private V noticeAt = V.ZERO;
	/** What its hands are doing (tapping, listening), since when, and where. */
	private int action;
	private double actionStart;
	private V actionAt = V.ZERO;
	private double landTime = -100;
	private final Spring[] armDown = {new Spring(), new Spring()};
	/** How far the claws of each planted hand straighten so they rest on the floor, not in it. */
	private final double[] clawLift = new double[2];
	/** Each hand turned about its own up so its fingers are clear of a step or wall (radians), and the way it is turning to. */
	private final double[] handYaw = new double[2], handYawTo = new double[2];
	/** How much further back each hand's fingers bend, up off what they would otherwise poke into (radians). */
	private final double[] fingerWrap = new double[2];

	private final Quat[] neckDelta;
	private Quat headDelta = Quat.IDENTITY;
	private final VSpring gaze = new VSpring();
	private double nextScan;
	private V scanDir = V.Z;
	private double breath;
	private double floorY;

	private final V[] tailRest;
	private final double[] tailLength;
	private V[] tail, tailPrev;
	private final V[][] droolRest;
	private final double[] droolLength;
	private V[][] drool, droolPrev;

	/**
	 * The way the body goes (2026-10-12): where its root has been, newest first, and where its path
	 * goes next, eased. Together they make a line down the middle of it from behind the tail to
	 * ahead of the head; each joint of the trunk sits on that line where it would be on a straight
	 * one, turned along it, the head is carried along it ahead and the tail is drawn onto it behind.
	 * Round a corner the head goes first, the body after it and the tail last, in an S where the way
	 * bends back and forth, and none of it cuts across the corner into the wall.
	 */
	private static final int AHEAD = 5, CURVE = 128;
	private static final double TRAIL_STEP = 0.2, TRAIL_LENGTH = 17;
	private final java.util.ArrayList<V> trail = new java.util.ArrayList<>();
	private final VSpring[] aheadSpring = new VSpring[AHEAD];
	private final V[] ahead = new V[AHEAD];
	/** The line in body space, from back to front: arc length (negative behind the root) and where it is. */
	private final double[] cs = new double[CURVE], cx = new double[CURVE], cy = new double[CURVE], cz = new double[CURVE];
	private int cn;
	/** How far the body follows the line, 0 (straight) to 1: it lets go on a new surface and in the air. */
	private double bendWeight = 1;
	/** The trunk's joints as they would be on a straight line, before they are bent onto it. */
	private final Xform[] straight;
	/** The blocks round it: the head and the tail are kept out of them. */
	private final BodySolids solids = new BodySolids();
	/** Where the skull last was clear of everything: what it comes back toward out of a wall or a shut door. */
	@Nullable
	private V lastClearSkull;

	/**
	 * Where the skull comes back to out of something: the way ahead (where the body is about to go)
	 * if that is clear; if the way ahead runs into a door still shut, where the skull last was clear,
	 * so it waits at the door rather than come out through it (design doc 5.12).
	 */
	private V safeSkull(Xform toWorld, V safe) {
		V from = toWorld.point(safe);
		if (solids.hits(from, SKULL_RADIUS) && lastClearSkull != null && lastClearSkull.sub(from).len() < 4) {
			return lastClearSkull;
		}
		return from;
	}

	/** How far the skull is being held off a wall, eased. */
	private final VSpring headPush = new VSpring();
	/** The skull as a ball round its middle, and the dropped jaw below the face as another. */
	private static final double SKULL_RADIUS = 0.48, JAW_RADIUS = 0.26;
	/** Off, the body turns as one piece as it did before (for comparing the two in DevShot). */
	public static boolean pathBody = true;
	/** DevShot: for each part of the body, frames with any of it inside a block, and frames counted. */
	public static boolean audit;
	public static final java.util.Map<String, int[]> AUDIT = new java.util.LinkedHashMap<>();
	/** DevShot: how often each part was inside, and the first few times something was, where. */
	public static final java.util.Map<String, Integer> AUDIT_PARTS = new java.util.HashMap<>();
	public static final java.util.List<String> AUDIT_SEEN = new java.util.ArrayList<>();
	private String[] auditGroup;
	private static final V JAW_BALL = new V(0, -0.42, 0.2);

	public static RaukAnimator of(RaukEntity entity) {
		if (entity.clientBody instanceof RaukAnimator animator) {
			return animator;
		}
		RaukAnimator animator = new RaukAnimator();
		entity.clientBody = animator;
		return animator;
	}

	private RaukAnimator() {
		local = new Xform[sk.count];
		body = sk.restWorld.clone();
		straight = sk.restWorld.clone();
		for (int k = 0; k < AHEAD; k++) {
			aheadSpring[k] = new VSpring();
		}
		int trunkJoints = sk.spine.length - sk.neck.length;
		trunk = new int[trunkJoints + 1];
		trunk[0] = sk.pelvis;
		System.arraycopy(sk.spine, 0, trunk, 1, trunkJoints);
		trunkCeiling = new double[trunk.length];
		trunkProbes = trunkProbes();
		trunkPush = new V[trunk.length];
		java.util.Arrays.fill(trunkPush, V.ZERO);
		neckDelta = new Quat[sk.neck.length];
		java.util.Arrays.fill(neckDelta, Quat.IDENTITY);
		for (int i = 0; i < 4; i++) {
			feet[i] = new Foot(i >= 2, i % 2 == 0 ? 1 : -1);
		}

		upright = new Stance(3.98, 0, -0.26, 0, sk.restWorld[sk.head].o(), 1.2, 0.28, 1.45, 0.8, 0.15, 0.3, 0, 0, 32);
		quadruped = new Stance(3.0, Math.toRadians(35), -1.15, 0, new V(0, 2.2, 3.3), 1.2, 0.2, 1.45, 0.8, 0.15, 1.0, 0, 1, 42);
		crawl = new Stance(1.45, Math.toRadians(42), -1.3, Math.toRadians(-1.4), new V(0, 1.1, 3.6), 2.8, 0.5, 2.4, 1.0, 1.3, 1.6,
				0, 1, 52);
		squeeze = new Stance(1.3, Math.toRadians(42), -1.3, Math.toRadians(-1.4), new V(0, 0.95, 3.9), 0.9, 0.2, 0.4, 1.6, 0.4,
				-0.2, 0.7, 1, 48);
		cling = new Stance(1.4, Math.toRadians(30), -1.0, Math.toRadians(-1.8), new V(0, 1.5, 3.6), 3.4, 0.0, 2.4, 1.2, 1.5, 1.6,
				0, 1, 55);
		stance = upright;

		int n = sk.tail.length;
		tailRest = new V[n + 1];
		tailLength = new double[n];
		Xform pelvisRest = sk.restWorld[sk.pelvis];
		for (int k = 0; k <= n; k++) {
			tailRest[k] = pelvisRest.toLocal(sk.tailJoints[k]);
		}
		for (int k = 0; k < n; k++) {
			tailLength[k] = sk.tailJoints[k + 1].sub(sk.tailJoints[k]).len();
		}

		droolRest = new V[sk.drool.length][];
		droolLength = new double[sk.drool.length];
		for (int s = 0; s < sk.drool.length; s++) {
			int[] strand = sk.drool[s];
			droolLength[s] = sk.restWorld[strand[1]].o().sub(sk.restWorld[strand[0]].o()).len();
			droolRest[s] = new V[strand.length + 1];
			for (int k = 0; k < strand.length; k++) {
				droolRest[s][k] = sk.restWorld[strand[k]].o();
			}
			droolRest[s][strand.length] = sk.restWorld[strand[strand.length - 1]].point(new V(0, droolLength[s], 0));
		}
	}

	private Stance stanceFor(RaukPosture p) {
		return switch (p) {
			case UPRIGHT -> upright;
			case QUADRUPED -> quadruped;
			case CRAWL -> crawl;
			case SQUEEZE -> squeeze;
			case CLING -> cling;
		};
	}

	/** The surface's outward normal: up from a floor, down from a ceiling, out from a wall. */
	private static V normal(Direction surface) {
		return new V(-surface.getStepX(), -surface.getStepY(), -surface.getStepZ());
	}

	/**
	 * Where the body's origin sits relative to the box's bottom centre: on the surface it holds. On a
	 * wall it sits a little behind the middle of its box the way it climbs (climbing out of a hole,
	 * its middle is still down in it while its head comes over the lip). The server's line ahead
	 * puts its points the same way ({@link RaukMover#onSurface}).
	 */
	private static V surfaceOffset(Direction surface, RaukPosture p, double climb) {
		return switch (surface) {
			case DOWN -> V.ZERO;
			case UP -> new V(0, p.height, 0);
			default -> new V(surface.getStepX() * p.width / 2, p.height / 2 - RaukMover.CLIMB_BACK * climb, surface.getStepZ() * p.width / 2);
		};
	}

	// --- per frame ----------------------------------------------------------------------------

	public void update(RaukEntity e, float partialTick) {
		lastUpdateTick = e.tickCount;
		double t = (e.tickCount + partialTick) / 20.0;
		V pos = new V(Mth.lerp(partialTick, e.xo, e.getX()), Mth.lerp(partialTick, e.yo, e.getY()), Mth.lerp(partialTick, e.zo, e.getZ()));
		if (time < 0 || t < time || pos.sub(position).len() > 8) {
			reset(e, pos, t, partialTick);
		}
		double dt = Math.min(t - time, 0.1);
		if (dt <= 0) {
			return;
		}
		time = t;
		frameDt = dt;
		position = pos;

		RaukPosture posture = e.posture();
		Direction surface = e.surface();
		int leap = e.leapState();
		boolean flying = leap == RaukMover.LEAP_FLIGHT;
		if (leap != lastLeap) {
			if (leap == RaukMover.LEAP_LAND) {
				landTime = t;
			}
			if (leap == RaukMover.LEAP_FLIGHT || leap == RaukMover.LEAP_LAND) {
				for (Foot f : feet) {
					f.plant = null;
					f.swinging = false;
				}
			}
			lastLeap = leap;
		}
		double coiled = coil.update(leap == RaukMover.LEAP_WINDUP ? 1 : 0, 3, dt);
		// Taking hold of a new surface: each hand and foot steps across onto it in turn.
		Direction surfaceNow = e.surface();
		boolean surfaceChanged = surfaceNow != lastSurface;
		if (surfaceNow != lastSurface && !flying) {
			for (int i = 0; i < 4; i++) {
				Foot f = feet[i];
				if (f.plant != null && f.contact != null) {
					f.swinging = true;
					f.from = f.contact;
					f.s = -0.2 * new int[] {0, 2, 1, 3}[i];
					f.duration = 0.35;
					f.lift = 0.35;
				}
			}
		}
		lastSurface = surfaceNow;
		double sinceLand = t - landTime;
		double landed = sinceLand < 0.45 ? Math.sin(Math.PI * sinceLand / 0.45) : 0;

		// The body's origin sits on the surface it holds; it eases across when that changes.
		rootOffset = offset.update(flying ? V.ZERO : surfaceOffset(surface, posture, forward.y()), 6, dt);
		V newRoot = pos.add(rootOffset);
		// Up and down steps on a floor its middle rises and falls smoothly, not a half block at once.
		if (surface == Direction.DOWN && !flying) {
			newRoot = new V(newRoot.x(), rootHeight.update(newRoot.y(), 6, dt), newRoot.z());
		} else {
			rootHeight.reset(newRoot.y());
		}
		V raw = newRoot.sub(root).mul(1 / dt);
		root = newRoot;
		V vel = velocity.update(raw, 5, dt);
		V up = flying ? V.Y : normal(surface);
		V along = vel.reject(up);
		double speed = along.len();

		// A startle: the head snaps to it at once, the trunk twists after it, then the body turns.
		int startles = e.startleCount();
		if (startles != startlesSeen) {
			if (startlesSeen != Integer.MIN_VALUE) {
				Vec3 p = e.startlePoint();
				startleAt = new V(p.x, p.y, p.z);
				startleTime = t;
				gaze.reset(startleAt);
			}
			startlesSeen = startles;
		}
		// Something noticed: the head snaps to it; the body stays where it is.
		int notices = e.noticeCount();
		if (notices != noticesSeen) {
			if (noticesSeen != Integer.MIN_VALUE) {
				Vec3 p = e.noticePoint();
				noticeAt = new V(p.x, p.y, p.z);
				noticeTime = t;
				gaze.reset(noticeAt);
			}
			noticesSeen = notices;
		}
		if (e.action() != action) {
			action = e.action();
			actionStart = t;
		}
		held = e.grabbed();
		if (e.grabPhase() != grabPhase) {
			grabPhase = e.grabPhase();
			grabPhaseStart = t;
		}

		dead = e.isDeadOrDying();
		carrying = e.carrying();
		Vec3 ap = e.actionPoint();
		actionAt = new V(ap.x, ap.y, ap.z);
		double sinceStartle = t - startleTime;
		double alarm = sinceStartle < 1.2 ? 1 : Math.clamp(1 - (sinceStartle - 1.2) / 1.3, 0, 1);
		double flinch = sinceStartle < 0.5 ? Math.sin(Math.PI * sinceStartle / 0.5) : 0;
		double startleYaw = Math.toDegrees(Math.atan2(-(startleAt.x() - root.x()), startleAt.z() - root.z()));
		// Listening after a knock it holds its breath and goes rigid.
		double tens = Math.max(tension.update(e.tension(), 0.8, dt), Math.max(0.95 * alarm, action == RaukMind.ACTION_LISTEN ? 0.9 : 0));
		double att = attention.update(e.attention(), 2, dt);
		// The membrane sweeps across every four to ten seconds, never while it is locked on; a hurt
		// head blinks far more.
		lastAttention = att;
		if (t >= nextBlink) {
			var random = e.getRandom();
			if (att > 0.8) {
				nextBlink = t + 0.6;
			} else {
				blinkStart = t;
				nextBlink = t + 4 + 6 * random.nextDouble();
			}
		}

		// Facing: on the floor a heading that follows its movement (or its body, or a startle); on a
		// wall or ceiling, the way it is crawling; in the air, along its flight.
		double before = heading;
		if (surface == Direction.DOWN && !flying) {
			double bodyYaw = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot);
			double targetYaw = speed > 0.5 ? Math.toDegrees(Math.atan2(-along.x(), along.z())) : bodyYaw;
			if (alarm > 0 && sinceStartle > 0.3 && speed < 0.5) {
				targetYaw = startleYaw;
			}
			heading = headingSpring.updateAngle(targetYaw, 0.8 + 1.6 * tens + 1.5 * alarm, dt);
			forward = new V(-Math.sin(Math.toRadians(heading)), 0, Math.cos(Math.toRadians(heading)));
		} else {
			V f = flying && vel.len() > 0.5 ? vel : speed > 0.3 ? along : forward.reject(up);
			if (f.len() < 1e-3) {
				f = up.cross(V.X).len() > 0.1 ? up.cross(V.X) : up.cross(V.Z);
			}
			forward = f.norm();
			if (flying) {
				up = V.Y.reject(forward).len() > 0.1 ? V.Y.reject(forward).norm() : V.Z;
			}
			heading = Math.toDegrees(Math.atan2(-forward.x(), forward.z()));
			headingSpring.reset(heading);
		}
		headingRate = Mth.lerp(Math.min(1, dt * 8), headingRate, Mth.wrapDegrees(heading - before) / dt);
		Quat want = Quat.fromAxes(up.cross(forward).norm(), up, forward.reject(up).norm());
		orientation = Quat.slerp(orientation, want, 1 - Math.exp(-dt * (surface == Direction.DOWN && !flying ? 14 : 5)));
		Xform toWorld = new Xform(root, orientation);
		lastToWorld = toWorld;

		// The posture's stance, eased toward; and the gait that goes with posture, surface and speed.
		// Dying, it collapses into a crawl. A doorway or low tunnel just ahead on its path: it starts
		// lowering for it already, and lowers faster than it rises, so it never pokes through the top.
		RaukPosture shape = dead ? RaukPosture.CRAWL : posture;
		RaukPosture ahead = e.postureAhead();
		if (!dead && surface == Direction.DOWN && !flying && ahead != RaukPosture.CLING && ahead.ordinal() > posture.ordinal()) {
			shape = ahead;
		}
		Stance goal = stanceFor(shape);
		double ease = dead ? 4 : goal.height() < stance.height() - 0.05 ? 5 : 2.2;
		stance = stance.toward(goal, 1 - Math.exp(-dt * ease));
		if (dead) {
			tens = 0;
		}
		double down = stance.armsDown();
		if (posture == RaukPosture.QUADRUPED && surface == Direction.DOWN) {
			galloping = galloping ? speed > 5.5 : speed > 6.5;
		} else {
			galloping = false;
		}
		Gait gait = switch (posture) {
			case UPRIGHT -> WALK;
			case QUADRUPED -> galloping ? GALLOP : PROWL;
			case CRAWL, SQUEEZE -> CREEP;
			case CLING -> surface == Direction.UP ? CEILING : CLIMB;
		};
		double gallop = (posture == RaukPosture.QUADRUPED ? down : 0) * Math.clamp((speed - 4.5) / 4, 0, 1);
		double running = (1 - down) * Math.clamp((speed - 2.5) / 3, 0, 1);

		// Rising to full height to observe: the body first, the neck last, over five seconds.
		boolean rising = e.risen() && posture == RaukPosture.UPRIGHT && surface == Direction.DOWN && speed < 0.4;
		rise = Math.clamp(rise + (rising ? dt : -dt) / 5.0, 0, 1);
		double riseBody = smooth(Math.clamp(rise / 0.65, 0, 1)), riseNeck = smooth(Math.clamp((rise - 0.35) / 0.65, 0, 1));

		V velBody = toWorld.r().conj().rotate(vel);
		double accel = (velBody.z() - forwardSpeed) / dt;
		forwardSpeed = velBody.z();
		double leanAngle = lean.update(Math.atan2(Math.clamp(accel, -6, 6), 9.8) * 0.6, 2.5, dt) * (flying ? 0 : 1);

		// Pelvis: height, pitch and sway from the stance, gait, lean, rising, flinching and leaping.
		boolean moving = speed > 0.15 && !flying;
		double stride = gait.stride() + gait.stridePerSpeed() * speed;
		double prevPhase = phase;
		if (moving) {
			phase = (phase + speed * dt / stride) % 1;
		}
		double gaitAmount = Math.min(1, speed / 2.0);
		double bob = -0.06 * gaitAmount * Math.cos(4 * Math.PI * phase) * (1 - gallop)
				+ 0.16 * gallop * Math.sin(2 * Math.PI * (phase - 0.1));
		double roll = Math.toRadians(3) * gaitAmount * Math.sin(2 * Math.PI * phase) * (1 - down);
		double sway = Math.toRadians(5) * gaitAmount * Math.sin(2 * Math.PI * phase) * (1 - gallop);
		// Under a low ceiling the body sinks and levels out (see flatten).
		double height = stance.height() - 0.18 * tens * (1 - down) + bob - 0.3 * gallop + 0.4 * riseBody - 0.25 * flinch
				- 0.45 * coiled - 0.4 * landed - 0.6 * flatten;
		height = Math.max(Math.min(stance.height(), 0.95), height);
		// Fitted to its feet (see fitToFeet): over its hind feet, pitched toward its front ones,
		// rolled toward the higher side, leaning toward a foot far out. Not in the air.
		double fit = flying || dead ? 0 : 1;
		height += fit * fitHeight.x;
		double pitch = stance.pitch() + leanAngle + Math.toRadians(9) * running - Math.toRadians(14) * riseBody
				+ Math.toRadians(5) * gallop * Math.sin(2 * Math.PI * (phase + 0.25)) + Math.toRadians(8) * coiled
				+ Math.toRadians(14) * flatten + fit * fitPitch.x;
		roll += fit * fitRoll.x;
		V leanBy = fitLean.x();
		Quat pelvisRot = Quat.axisAngle(V.X, pitch)
				.mul(Quat.axisAngle(V.Z, roll)).mul(Quat.axisAngle(V.Y, sway)).mul(sk.restWorld[sk.pelvis].r());
		Xform pelvis = new Xform(new V(fit * leanBy.x(), height + fit * leanBy.y(), stance.z()), pelvisRot);

		// Breathing: slow at rest, faster when tense or moving, held when committed.
		// Panting after a spent burst: mouth open, ribs heaving, never held.
		double pant = panting.update(e.panting() ? 1 : 0, 1.5, dt);
		double held = Math.clamp((tens - 0.85) / 0.1, 0, 1) * (1 - pant);
		double breathBefore = breath;
		breath += dt * (Mth.lerp(tens, 0.2, 0.5) * (1 + 0.6 * Math.min(speed / 3, 1)) * (1 - held) + 1.8 * pant);
		double breathe = Math.sin(2 * Math.PI * breath) * (1 - held) * (1 + 0.8 * pant);
		// What it sounds like: each breath drawn in; the breath caught when it goes rigid (and then
		// nothing); joints popping as it squeezes into a gap, skin dragging; a skid out of a gallop.
		if (Math.floor(breath) != Math.floor(breathBefore) && held < 0.5 && !dead) {
			RaukSoundClient.breath(e, faceWorld, pant > 0.5, tens);
		}
		if (held > 0.7 && !breathHeld) {
			breathHeld = true;
			RaukSoundClient.breathHeld(e, faceWorld);
		} else if (held < 0.3) {
			breathHeld = false;
		}
		if (posture == RaukPosture.SQUEEZE && lastPosture != RaukPosture.SQUEEZE && !flying) {
			RaukSoundClient.squeeze(e, root.add(up.mul(1.0)));
		}
		lastPosture = posture;
		if ((posture == RaukPosture.SQUEEZE || posture == RaukPosture.CRAWL) && speed > 0.3 && t - lastDrag > 0.9) {
			lastDrag = t;
			RaukSoundClient.drag(e, root);
		}
		if (galloping) {
			lastGallop = t;
			skidded = false;
		} else if (!skidded && t - lastGallop < 0.6 && accel < -9 && surface == Direction.DOWN) {
			skidded = true;
			RaukSoundClient.skid(e, toWorld.point(new V(0, 0, 1.6)));
		}

		// Fit the pelvis to the legs: no higher than a planted foot below it can reach (on steps or
		// a slope). A foot left far behind doesn't count: it is about to step.
		double drop = 0, reach = 0.97 * (RaukRig.THIGH + RaukRig.SHIN);
		for (int s = 0; s < 2; s++) {
			Foot f = feet[s];
			if (f.plant != null && !f.swinging && !flying) {
				V hip = pelvis.point(sk.restLocal[sk.legs[s].upper()].o());
				V ankle = toWorld.toLocal(f.plant).add(new V(0, BALL_HEIGHT, 0))
						.sub(metatarsus(0, f.side, riseBody, gallop, Quat.IDENTITY).mul(RaukRig.METATARSUS));
				double across = horizontal(hip.sub(ankle));
				if (across < reach * 0.9) {
					drop = Math.max(drop, hip.y() - (ankle.y() + Math.sqrt(reach * reach - across * across)));
				}
			}
		}
		if (drop > 0) {
			pelvis = new Xform(pelvis.o().sub(new V(0, Math.min(drop, 0.6), 0)), pelvis.r());
		}
		body[sk.pelvis] = pelvis;
		local[sk.pelvis] = pelvis;

		// The trunk twists toward a startle before the body turns: the angle still to turn, briefly.
		double twistWeight = smooth(Math.clamp((sinceStartle - 0.08) / 0.25, 0, 1))
				* (1 - smooth(Math.clamp((sinceStartle - 1.2) / 1.0, 0, 1)));
		double twist = surface == Direction.DOWN
				? Math.clamp(-Math.toRadians(Mth.wrapDegrees(startleYaw - heading)), -0.8, 0.8) * twistWeight : 0;
		spine(breathe, gallop, riseBody, twist, coiled);
		// The trunk bent onto the way it is going (see trail).
		solids.begin(e.level(), e.tickCount);
		// Onto another surface the line goes on (the body comes over the edge after its head); only
		// a leap starts it again.
		trail(up, speed, flying, dt);
		ahead(e, up, flying, dt);
		curve(toWorld);
		bendTrunk();
		fitUnderCeiling(e, toWorld, surface == Direction.DOWN && !flying && !dead, dt);
		if (pathBody && !dead) {
			keepTrunkOut(toWorld, dt);
		}
		if (flying) {
			airborne(toWorld, pelvis);
		} else {
			gait(e, toWorld, pelvis, vel, speed, moving, prevPhase, stride, gait, running, gallop, dt);
		}
		legs(toWorld, body[sk.pelvis], riseBody, gallop, flying);
		arms(toWorld, tens, speed, running, flying, dt, t);
		neckAndHead(e, toWorld, tens, att, gallop, riseNeck, rise, sinceStartle, partialTick, dt, t);
		jawAndRibs(down, breathe, pant, t);
		faceWorld = toWorld.point(body[sk.head].point(new V(0, -0.1, 0.25)));
		if (traceFrames) {
			// DevShot: a trace every frame, to find what jumps.
			V pw = toWorld.point(body[sk.pelvis].o()), nw = toWorld.point(body[sk.neck[0]].o()), hw = toWorld.point(body[sk.head].o());
			V a0 = this.ahead[0] == null ? V.ZERO : this.ahead[0];
			rauk.com.Rauk.LOGGER.info(String.format(java.util.Locale.ROOT,
					"trace %d %.2f pos %.2f %.2f %.2f root %.2f %.2f %.2f pelvis %.2f %.2f %.2f neck %.2f %.2f %.2f skull %.2f %.2f %.2f ahead0 %.2f %.2f %.2f fit %.2f %.2f n%d",
					e.tickCount, partialTick, pos.x(), pos.y(), pos.z(), root.x(), root.y(), root.z(), pw.x(), pw.y(), pw.z(), nw.x(), nw.y(), nw.z(),
					hw.x(), hw.y(), hw.z(), a0.x(), a0.y(), a0.z(), fitHeight.x, fitPitch.x, e.pathAhead().size()));
		}
		if (e.tickCount % 5 == 0 || floorY == 0) {
			floorY = floorBelow(e, toWorld.point(pelvis.o()));
		}
		if (e.tickCount % 3 == 0) {
			headRoom = surface == Direction.DOWN && !flying ? Math.min(roomAbove(e, faceWorld), roomAbove(e, faceWorld.add(forward.mul(1.2))))
					- (root.y() - rootOffset.y()) : 99;
		}
		lastSpeed = speed;
		tail(toWorld, flying ? null : up, dt);
		drool(toWorld, dt);
		if (audit) {
			audit();
		}
	}

	/** DevShot: which parts of the body are inside a block this frame (by more than 5 cm). */
	/** DevShot: which part of the body (head, neck, trunk, arms, legs, tail) a bone of the audit counts toward. */
	public static String auditGroupOf(String bone) {
		RaukAnimator a = instanceForAudit;
		if (a == null || a.auditGroup == null) {
			return null;
		}
		try {
			return a.auditGroup[a.sk.index(bone)];
		} catch (IllegalArgumentException missing) {
			return null;
		}
	}

	private static RaukAnimator instanceForAudit;

	private void audit() {
		instanceForAudit = this;
		if (auditGroup == null) {
			auditGroup = new String[sk.count];
			java.util.Set<Integer> neck = new java.util.HashSet<>();
			for (int j : sk.neck) {
				neck.add(j);
			}
			for (int j = 0; j < sk.count; j++) {
				String n = sk.names[j];
				auditGroup[j] = n.startsWith("head") || n.startsWith("jaw") || n.startsWith("eye_") || n.startsWith("skull_") ? "head"
						: n.startsWith("drool") ? null
						: neck.contains(j) || neck.contains(sk.parent[j]) && n.contains("spike") ? "neck"
						: n.startsWith("tail_") ? "tail"
						: n.startsWith("leg_") || n.startsWith("toe_") || n.startsWith("dewclaw_") ? "legs"
						: n.startsWith("arm_") || n.startsWith("hand_") || n.startsWith("finger_") || n.startsWith("thumb_") ? "arms"
						: "trunk";
			}
		}
		Xform[] w = worldTransforms();
		java.util.Set<String> inside = new java.util.HashSet<>();
		for (int j = 0; j < sk.count; j++) {
			if (auditGroup[j] == null) {
				continue;
			}
			// The joint, and along the bone to it from the one it hangs off: a thigh through a wall
			// counts though both its ends are clear. Hands and feet on the floor don't count: only
			// into walls (or the floor deeper than 25 cm).
			boolean limb = "arms".equals(auditGroup[j]) || "legs".equals(auditGroup[j]);
			int parent = sk.parent[j];
			boolean in = false;
			for (int k = 4; k >= 1 && !in; k--) {
				V q = parent < 0 ? w[j].o() : w[parent].o().lerp(w[j].o(), k / 4.0);
				in = solids.inside(q, 0.05) && !(limb && !solids.inside(q.add(new V(0, 0.25, 0)), 0));
			}
			if (in) {
				inside.add(auditGroup[j]);
				AUDIT_PARTS.merge(sk.names[j], 1, Integer::sum);
				if (AUDIT_SEEN.size() < 40 && (!limb || sk.names[j].endsWith("claw_2") && AUDIT_SEEN.size() < 6)) {
					V p = w[j].o();
					AUDIT_SEEN.add(String.format(java.util.Locale.ROOT, "t%.2f %s at %.2f %.2f %.2f (root %.2f %.2f %.2f)", time, sk.names[j],
							p.x(), p.y(), p.z(), root.x(), root.y(), root.z()));
				}
			}
		}
		for (String g : new String[] {"head", "neck", "trunk", "arms", "legs", "tail"}) {
			int[] c = AUDIT.computeIfAbsent(g, k -> new int[2]);
			c[0] += inside.contains(g) ? 1 : 0;
			c[1]++;
		}
	}

	private void reset(RaukEntity e, V pos, double t, float partialTick) {
		time = t - 1 / 60.0;
		position = pos;
		heading = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot);
		headingSpring.reset(heading);
		stance = stanceFor(e.posture());
		tension.reset(e.tension());
		attention.reset(e.attention());
		velocity.reset(V.ZERO);
		Direction surface = e.surface();
		rootOffset = surfaceOffset(surface, e.posture(), 0);
		offset.reset(rootOffset);
		root = pos.add(rootOffset);
		rootHeight.reset(root.y());
		V up = normal(surface);
		forward = surface.getAxis() == Direction.Axis.Y
				? new V(-Math.sin(Math.toRadians(heading)), 0, Math.cos(Math.toRadians(heading))) : V.Y;
		orientation = Quat.fromAxes(up.cross(forward).norm(), up, forward);
		Xform toWorld = new Xform(root, orientation);
		for (Foot f : feet) {
			f.plant = null;
			f.swinging = false;
		}
		Xform pelvisWorld = toWorld.then(sk.restWorld[sk.pelvis]);
		int n = sk.tail.length;
		tail = new V[n + 1];
		tailPrev = new V[n + 1];
		for (int k = 0; k <= n; k++) {
			tail[k] = pelvisWorld.point(tailRest[k]);
			tailPrev[k] = tail[k];
		}
		drool = new V[droolRest.length][];
		droolPrev = new V[droolRest.length][];
		for (int s = 0; s < droolRest.length; s++) {
			drool[s] = new V[droolRest[s].length];
			droolPrev[s] = new V[droolRest[s].length];
			for (int k = 0; k < droolRest[s].length; k++) {
				drool[s][k] = toWorld.point(droolRest[s][k]);
				droolPrev[s][k] = drool[s][k];
			}
		}
		gaze.reset(toWorld.point(sk.restWorld[sk.head].point(new V(0, 0, 8))));
		V back = forward.reject(up).len() > 1e-4 ? forward.reject(up).norm().mul(-1) : V.Z.mul(-1);
		straighten(back);
		for (int k = 0; k < AHEAD; k++) {
			ahead[k] = root.sub(back.mul(k + 1));
			aheadSpring[k].reset(ahead[k]);
		}
		headPush.reset(V.ZERO);
		bendWeight = 1;
		startlesSeen = e.startleCount();
		noticesSeen = e.noticeCount();
		action = e.action();
		actionStart = t;
		lastLeap = e.leapState();
		lastSurface = e.surface();
		rise = e.risen() ? 1 : 0;
		floorY = 0;
	}

	private static double smooth(double x) {
		return x * x * (3 - 2 * x);
	}

	/**
	 * Each part's transform in the world this frame: the parts it moved, and the rest of the
	 * skeleton carried along with them. For hit tests against the body.
	 */
	public Xform[] worldTransforms() {
		Xform[] w = new Xform[sk.count];
		for (int j = 0; j < sk.count; j++) {
			Xform l = local[j] != null ? local[j] : sk.restLocal[j];
			w[j] = sk.parent[j] < 0 ? lastToWorld.then(l) : w[sk.parent[j]].then(l);
		}
		return w;
	}

	// --- spine --------------------------------------------------------------------------------

	/**
	 * The trunk lifts with each breath (it bends round corners on the way it goes: see trail). Each posture arches or
	 * flattens the back; galloping, it curls as the hind legs swing under and stretches as the
	 * front legs reach; rising, it straightens; coiling for a leap, it curls; after a startle the
	 * chest twists toward it first. The neck has its own solver.
	 */
	private void spine(double breathe, double gallop, double riseBody, double twist, double coiled) {
		double turn = pathBody ? 0 : Math.clamp(Math.toRadians(headingRate) * 0.02, -0.035, 0.035);
		double flex = Math.toRadians(2.6) * gallop * Math.sin(2 * Math.PI * phase) + Math.toRadians(2.5) * coiled;
		double straighten = -Math.toRadians(1.2) * riseBody + stance.arch() - Math.toRadians(2.4) * flatten;
		int chestCount = 0;
		for (boolean c : sk.chest) {
			chestCount += c ? 1 : 0;
		}
		int neckStart = sk.spine.length - sk.neck.length;
		for (int k = 0; k < sk.spine.length; k++) {
			int j = sk.spine[k];
			Quat d;
			if (k >= neckStart) {
				d = neckDelta[k - neckStart];
			} else {
				double yaw = turn + (sk.chest[k] ? twist / chestCount : 0);
				d = Quat.axisAngle(V.Y, yaw).mul(Quat.axisAngle(V.X, Math.toRadians(0.35) * breathe + flex + straighten));
			}
			place(j, d);
		}
	}

	/** Forward kinematics for one part: its rest offset from the parent, turned by d. */
	private void place(int j, Quat d) {
		Xform rest = sk.restLocal[j];
		Xform l = new Xform(rest.o(), d.mul(rest.r()));
		local[j] = l;
		body[j] = body[sk.parent[j]].then(l);
	}

	// --- gait ---------------------------------------------------------------------------------

	private static final class Foot {
		final boolean arm;
		final int side;
		V plant, contact, from;
		boolean swinging;
		/** Whether it has hold of something (a floor, a step, a lip, a wall); if not, it hangs tucked up. */
		boolean grounded = true;
		/** How far along the body (straight) its home is: where its part of the body is on the line. */
		double homeZ;
		String source = "";
		/** Which way the surface it holds faces (world), and the one it is swinging to; null with nothing held. */
		V normal, nextNormal;
		double s, duration, lift;

		Foot(boolean arm, int side) {
			this.arm = arm;
			this.side = side;
		}

		/** How far through its swing the limb is lifted, 0 on the ground. */
		double lifted() {
			return swinging && s > 0 ? Math.sin(Math.PI * s) : 0;
		}
	}

	/** The shoulder joint, pulled in toward the spine when the shoulders collapse to squeeze. */
	private V shoulder(Xform chest, RaukSkeleton.Limb arm) {
		return chest.point(sk.restLocal[arm.upper()].o()).sub(chest.x().mul(arm.side() * 0.3 * stance.shoulderIn()));
	}

	/**
	 * Plants and lifts the feet (and hands on all fours) on the surface it holds. Moving, each limb
	 * swings in its window of the gait cycle. Standing, a limb that ends up too far from where it
	 * belongs takes a small step, one at a time.
	 */
	private void gait(RaukEntity e, Xform toWorld, Xform pelvis, V vel, double speed, boolean moving, double prevPhase,
			double stride, Gait gait, double running, double gallop, double dt) {
		boolean armsWalk = stance.armsDown() > 0.5;
		double cycle = stride / Math.max(speed, 0.4);
		double swingFraction = gait.swing();
		V up = toWorld.y(), fwd = toWorld.z();
		Xform chest = straight[sk.parent[sk.arms[0].upper()]];
		for (int i = 0; i < 4; i++) {
			Foot f = feet[i];
			// Where it would go on a straight body, then on round the way the body bends; and the hip
			// or shoulder it hangs from, which it can reach no further than its length from.
			V straightHome, straightJoint;
			if (f.arm) {
				V sh = shoulder(chest, sk.arms[i - 2]);
				straightHome = new V(sh.x() * stance.armOut(), 0, sh.z() + stance.armZ());
				straightJoint = sh;
			} else {
				V hip = straight[sk.pelvis].point(sk.restLocal[sk.legs[i].upper()].o());
				straightHome = new V(hip.x() * stance.legOut(), 0, hip.z() + stance.legZ());
				straightJoint = hip;
			}
			double reach = f.arm ? ARM_REACH : LEG_REACH;
			V joint = toWorld.point(bendPoint(straightJoint));
			// Its part of the body's own up and forward: over an edge, or down in the hole it climbs
			// out of, not the root's.
			Quat turn = rotAt(straightHome.z());
			up = toWorld.dir(turn.rotate(V.Y));
			fwd = toWorld.dir(turn.rotate(V.Z));
			f.homeZ = straightHome.z();
			// Never put down in a wall: in a narrow place they come in toward the middle of the way.
			V mid = toWorld.point(bendPoint(new V(0, 0, straightHome.z())));
			V homeWorld = keepIn(mid, toWorld.point(bendPoint(straightHome)), up);
			if (f.plant == null || (f.arm && !armsWalk)) {
				// Arms off the ground carry their footholds along, ready to put down.
				Hold h = hold(e, homeWorld, up, fwd, mid, joint, reach);
				f.grounded = h != null;
				f.source = h != null ? holdSource : "tuck";
				f.plant = h != null ? h.at() : tuck(joint, homeWorld, up, reach);
				f.normal = h != null ? h.normal() : null;
				f.contact = f.plant;
				f.swinging = false;
				continue;
			}
			double stanceTime = moving ? (1 - swingFraction) * cycle : 0;
			if (f.swinging) {
				f.s = Math.min(1, f.s + dt / f.duration);
				double s01 = Math.max(0, f.s);
				Hold h = hold(e, keepIn(mid, homeWorld.add(vel.mul((1 - s01) * f.duration + stanceTime * 0.5)), up), up, fwd, mid, joint, reach);
				V land = h != null ? h.at() : tuck(joint, homeWorld, up, reach);
				f.nextNormal = h != null ? h.normal() : null;
				if (f.s >= 1) {
					f.plant = land;
					f.contact = land;
					f.swinging = false;
					f.normal = f.nextNormal;
					f.grounded = h != null;
					f.source = h != null ? holdSource : "tuck";
					if (f.grounded) {
						RaukSoundClient.footfall(e, land, f.arm, speed, galloping);
					}
				} else {
					double ease = s01 * s01 * (3 - 2 * s01);
					f.contact = f.from.lerp(land, ease).add(up.mul(f.lift * Math.sin(Math.PI * s01)));
				}
				continue;
			}
			if (!f.grounded) {
				// Nothing to hold: it hangs tucked up and keeps feeling for something; in reach, it steps to it.
				Hold h = hold(e, homeWorld, up, fwd, mid, joint, reach);
				if (h == null) {
					f.plant = tuck(joint, homeWorld, up, reach);
					f.contact = f.plant;
					continue;
				}
			}
			double off = f.plant.sub(homeWorld).reject(up).len();
			// Stretched as far as it goes: it steps now rather than pull its leg out straight.
			boolean stretched = f.plant.sub(joint).len() > reach * 0.97;
			boolean step;
			if (moving) {
				step = crossed(prevPhase, phase, gait.offsets()[i]) || off > Math.min(stride * 0.85, 2.6) || stretched || !f.grounded;
			} else {
				step = (off > 0.3 || stretched || !f.grounded) && !partnerSwinging(i);
			}
			if (step) {
				f.swinging = true;
				f.s = 0;
				f.from = f.plant;
				f.duration = moving ? Math.clamp(swingFraction * cycle, 0.2, 0.6) : 0.4;
				f.lift = (f.arm ? 0.3 : 0.2) + 0.08 * Math.min(off, 2) + 0.25 * running + 0.25 * gallop;
			}
			f.contact = f.plant;
		}
		fitToFeet(toWorld, armsWalk, dt);
		if (logLimbs && e.tickCount % 10 == 0 && e.tickCount != loggedTick) {
			loggedTick = e.tickCount;
			StringBuilder line = new StringBuilder(String.format(java.util.Locale.ROOT, "limbs %s %s:", e.surface(), e.posture()));
			for (int i = 0; i < 4; i++) {
				Foot f = feet[i];
				V c = toWorld.toLocal(f.contact);
				line.append(String.format(java.util.Locale.ROOT, " [%s%s %s %s (%.1f %.1f %.1f)]", f.arm ? "arm" : "leg", f.side > 0 ? "L" : "R",
						f.swinging ? "swing" : f.grounded ? "hold" : "free", f.source, c.x(), c.y(), c.z()));
			}
			// The line ahead (world, from the root) as synced and as eased, and where the skull is.
			line.append(String.format(java.util.Locale.ROOT, " synced %d:", e.pathAhead().size()));
			for (Vec3 p : e.pathAhead()) {
				line.append(String.format(java.util.Locale.ROOT, " (%.1f %.1f %.1f)", p.x - root.x(), p.y - root.y(), p.z - root.z()));
			}
			line.append(" line:");
			for (double s = -4; s <= 4; s += s == -1 ? 2 : 1) {
				V p = toWorld.point(curveAt(s)).sub(root);
				line.append(String.format(java.util.Locale.ROOT, " (%.1f %.1f %.1f)", p.x(), p.y(), p.z()));
			}
			line.append(String.format(java.util.Locale.ROOT, " fit h%.2f p%.2f lean(%.1f %.1f)", fitHeight.x, fitPitch.x, fitLean.x().x(), fitLean.x().y()));
			V skull = toWorld.point(body[sk.head].o()).sub(root);
			line.append(String.format(java.util.Locale.ROOT, " skull (%.1f %.1f %.1f) up (%.1f %.1f %.1f)", skull.x(), skull.y(), skull.z(),
					toWorld.y().x(), toWorld.y().y(), toWorld.y().z()));
			rauk.com.Rauk.LOGGER.info(line.toString());
		}
	}

	private int loggedTick = Integer.MIN_VALUE;

	/** How far a hip or shoulder reaches to the ball of its foot or the palm of its hand, with a little to spare. */
	private static final double LEG_REACH = 0.94 * (RaukRig.THIGH + RaukRig.SHIN + 0.8 * RaukRig.METATARSUS),
			ARM_REACH = 0.94 * (RaukRig.UPPER_ARM + RaukRig.FOREARM + 0.2);
	/** How far below the surface it walks on a foot reaches down for a step, and how far it feels round the spot. */
	private static final double STEP_DOWN = 1.25;

	/** Something a hand or foot holds: where, and which way the surface there faces. */
	private record Hold(V at, V normal) {
	}

	/**
	 * Where a hand or foot goes (design doc 5.12), felt for the way a climbing animal does, never
	 * further than its hip or shoulder reaches: straight down onto the surface it walks on, a step
	 * below it at most (so never down to the floor under a stair beside it); then round the edges
	 * of the blocks about that spot (the lip of a hole, the edge of a step); then back along its
	 * body (over the top of a wall it is climbing, the floor beyond the lip); then in toward its
	 * middle (the wall of the hole it is climbing out of). Null if nothing is in reach.
	 */
	@Nullable
	private Hold hold(RaukEntity e, V home, V up, V fwd, V mid, V joint, double reach) {
		V side = up.cross(fwd).norm();
		holdSource = "down";
		Hold h = probe(e, above(home, up), up.mul(-1), STEP_DOWN, home, joint, reach);
		// About level with where it stands, it takes it; lower (a stair's side, a step down), it first
		// looks round the spot for something nearer its own level.
		if (h != null && h.at().sub(home).dot(up) >= -0.35) {
			return h;
		}
		Hold best = h;
		double bestD = h == null ? Double.MAX_VALUE : holdScore(h, home, mid, up);
		for (double a : new double[] {-0.7, 0, 0.7}) {
			for (double b : new double[] {-0.7, 0, 0.7}) {
				if (a == 0 && b == 0) {
					continue;
				}
				V p = home.add(side.mul(a)).add(fwd.mul(b));
				Hold c = probe(e, above(p, up), up.mul(-1), STEP_DOWN, p, joint, reach);
				if (c != null) {
					double d = holdScore(c, home, mid, up);
					if (d < bestD) {
						bestD = d;
						best = c;
						holdSource = "edge";
					}
				}
			}
		}
		if (best != null) {
			return best;
		}
		// Past the top of a wall it climbs: down from just behind the wall's face, onto the floor beyond the lip.
		holdSource = "over";
		V over = home.sub(up.mul(0.6));
		if (!solids.inside(over, 0)) {
			h = probe(e, over, fwd.mul(-1), 3.5, null, joint, reach);
			if (h != null) {
				return h;
			}
		}
		V start = home.add(up.mul(0.35));
		if (!solids.inside(start, 0)) {
			holdSource = "back";
			h = probe(e, start, fwd.mul(-1), 3.5, null, joint, reach);
			if (h != null) {
				return h;
			}
			holdSource = "in";
			V in = mid.sub(home).reject(up);
			if (in.len() > 1e-3) {
				h = probe(e, start, in.norm(), 2.2, null, joint, reach);
				if (h != null) {
					return h;
				}
			}
		}
		return null;
	}

	/** Which way the last hold was found (DevShot's limb log). */
	private String holdSource = "";
	/** DevShot: each limb's state written to the log every half second. */
	/** DevShot: where the root, hips, neck, skull and the line ahead are, every frame. */
	public static boolean traceFrames;
	public static boolean logLimbs;

	/**
	 * How good a hold is, lower better: near the spot it meant, not far below its own level, and
	 * rather toward the middle of its body than out from it.
	 */
	private static double holdScore(Hold c, V home, V mid, V up) {
		V d = c.at().sub(home);
		double below = Math.max(0, -d.dot(up) - 0.35);
		return d.len() + 0.8 * below + 0.3 * Math.max(0, c.at().sub(mid).reject(up).len() - home.sub(mid).reject(up).len());
	}

	/** A point above a spot to feel down from: as high as it is clear, up to a block and a bit. */
	private V above(V p, V up) {
		for (double k : new double[] {1.2, 0.8, 0.45, 0.15}) {
			V a = p.add(up.mul(k));
			if (!solids.inside(a, 0)) {
				return a;
			}
		}
		return p;
	}

	/** A ray from a point a way, as far as it goes: what it hits, if the hip or shoulder can reach it. */
	@Nullable
	private Hold probe(RaukEntity e, V from, V dir, double depth, @Nullable V spot, V joint, double reach) {
		// Straight down it feels from above the spot to a step below it; along, the length given.
		double length = spot == null ? depth : from.sub(spot).len() + depth;
		V to = from.add(dir.mul(length));
		BlockHitResult hit = e.level().clip(new ClipContext(new Vec3(from.x(), from.y(), from.z()), new Vec3(to.x(), to.y(), to.z()),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
		if (hit.getType() == HitResult.Type.MISS || hit.isInside()) {
			return null;
		}
		Vec3 l = hit.getLocation();
		V at = new V(l.x, l.y, l.z);
		if (at.sub(joint).len() > reach) {
			return null;
		}
		var n = hit.getDirection().getNormal();
		return new Hold(at, new V(n.getX(), n.getY(), n.getZ()));
	}

	/** Where a hand or foot with nothing to hold hangs: drawn in under the hip or shoulder, half out. */
	private static V tuck(V joint, V home, V up, double reach) {
		return joint.add(home.sub(joint).reject(up).mul(0.45)).sub(up.mul(reach * 0.45));
	}

	/**
	 * The body fits its feet (design doc 5.12): its hips sit at its stance's height over its hind
	 * feet (lower on a stair going down behind it, down in a hole it is climbing out of); its trunk
	 * pitches toward its front feet (up the stairs, over the lip it is climbing), and rolls toward
	 * the side whose feet stand higher; and a hip stretched toward a foot far out leans toward it,
	 * rather than pull the leg out straight. Eased, and worked out for the next frame.
	 */
	private void fitToFeet(Xform toWorld, boolean armsWalk, double dt) {
		double hind = 0, front = 0, left = 0, right = 0;
		int nh = 0, nf = 0, nl = 0, nr = 0;
		V lean = V.ZERO;
		for (int i = 0; i < 4; i++) {
			Foot f = feet[i];
			if (!f.grounded || f.contact == null || (f.arm && !armsWalk)) {
				continue;
			}
			// Measured off its own part of the body: down in the hole, on the wall there.
			V c = local(f.homeZ, toWorld.toLocal(f.swinging && f.from != null ? f.from : f.contact));
			if (f.arm) {
				front += c.y();
				nf++;
			} else {
				hind += c.y();
				nh++;
				// The hip it hangs from on the body as it is now, and how far past easy reach the foot is.
				V hip = straight[sk.pelvis].point(sk.restLocal[sk.legs[i].upper()].o());
				V toFoot = c.sub(new V(hip.x(), hip.y(), f.homeZ));
				double excess = toFoot.len() - 0.86 * LEG_REACH;
				if (excess > 0) {
					lean = lean.add(new V(toFoot.x(), toFoot.y(), 0).norm().mul(excess));
				}
			}
			if (f.side > 0) {
				left += c.y();
				nl++;
			} else {
				right += c.y();
				nr++;
			}
		}
		double height = nh > 0 ? Math.clamp(hind / nh, -2.6, 1.2) : 0;
		double pitch = 0;
		if (nh > 0 && nf > 0) {
			double span = Math.max(1.5, sk.restWorld[sk.arms[0].upper()].o().z() - sk.restWorld[sk.legs[0].upper()].o().z());
			// Gently: a body as long as its is never laid along a stair's pitch.
			pitch = Math.clamp(0.7 * Math.atan2(hind / nh - front / nf, span), -0.8, 0.8);
		}
		double roll = nl > 0 && nr > 0 ? Math.clamp(Math.atan2(left / nl - right / nr, 2.2), -0.4, 0.4) : 0;
		if (lean.len() > 0.6) {
			lean = lean.norm().mul(0.6);
		}
		fitHeight.update(height, 3, dt);
		fitPitch.update(pitch, 2.5, dt);
		fitRoll.update(roll, 2.5, dt);
		fitLean.update(lean, 3, dt);
	}

	/** A body-space point as seen from the bent body's part at s: across, up off its surface, and along. */
	private V local(double s, V p) {
		V rel = rotAt(s).conj().rotate(p.sub(curveAt(s)));
		return new V(rel.x(), rel.y(), s + rel.z());
	}

	/** The root's height on a floor, eased over steps. */
	private final Spring rootHeight = new Spring();

	/** How the body fits its feet, eased: see {@link #fitToFeet}. */
	private final Spring fitHeight = new Spring(), fitPitch = new Spring(), fitRoll = new Spring();
	private final VSpring fitLean = new VSpring();

	/**
	 * How strongly its eyes throw back light at the camera this frame (design doc 9): only when the
	 * head faces the camera (within about 25 degrees), and only as much as light falls on them,
	 * from the lamps around it or a light the viewer carries. It reflects; it never glows, so in
	 * daylight there is almost nothing. Blinking hides it.
	 */
	public float eyeshine(RaukEntity e, float partialTick) {
		if (time - blinkStart < 0.2 || time < 0) {
			return 0;
		}
		Minecraft mc = Minecraft.getInstance();
		Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
		Xform head = lastToWorld.then(body[sk.head]);
		V eye = faceWorld;
		V face = head.z();
		V to = new V(camera.x - eye.x(), camera.y - eye.y(), camera.z - eye.z());
		double distance = to.len();
		if (distance < 1e-3) {
			return 0;
		}
		double cos = face.dot(to.mul(1 / distance)), edge = Math.cos(Math.toRadians(40));
		double facing = smooth(Math.clamp((cos - edge) / (1 - edge), 0, 1));
		if (facing <= 0) {
			return 0;
		}
		BlockPos at = BlockPos.containing(eye.x(), eye.y(), eye.z());
		var level = e.level();
		double block = level.getBrightness(LightLayer.BLOCK, at) / 15.0;
		double sky = Math.max(0, level.getBrightness(LightLayer.SKY, at) - level.getSkyDarken()) / 15.0;
		double carried = mc.player != null && RaukMind.carriesLight(mc.player) ? Math.clamp(1 - distance / 32, 0, 1) : 0;
		double onEyes = Math.max(0.5, Math.max(block * 0.9, carried));
		// A tapetum throws back most of what falls on it: bright, against the dark of the sockets;
		// even in the dark the pupils smoulder (playtest, 2026-10-06: they glow, small and hard).
		return (float) Math.min(1, 2.8 * facing * onEyes * (1 - 0.7 * sky) * (0.85 + 0.15 * lastAttention));
	}

	/** Whether the right hand is busy: knocking, listening at a door, reaching, or holding down a kill. */
	private boolean handAction() {
		return action == RaukMind.ACTION_TAP || action == RaukMind.ACTION_LISTEN || action == RaukMind.ACTION_REACH
				|| action == RaukMind.ACTION_FEED;
	}

	/** The jaw's transform in the world this frame: what it carries hangs from it. */
	public Xform jawWorld() {
		return lastToWorld.then(body[sk.jaw]);
	}


	/** In the air: legs tucked up under the body, arms reaching ahead for the landing. */
	private void airborne(Xform toWorld, Xform pelvis) {
		Xform chest = body[sk.parent[sk.arms[0].upper()]];
		for (int i = 0; i < 4; i++) {
			Foot f = feet[i];
			V at;
			if (f.arm) {
				V sh = shoulder(chest, sk.arms[i - 2]);
				at = sh.add(new V(sh.x() * 0.6, -1.0, 2.4));
			} else {
				V hip = pelvis.point(sk.restLocal[sk.legs[i].upper()].o());
				at = hip.add(new V(hip.x() * 0.8, -1.9 - BALL_HEIGHT, -0.6));
			}
			f.contact = toWorld.point(at);
			f.plant = null;
			f.swinging = false;
		}
	}

	private boolean partnerSwinging(int i) {
		for (int j = 0; j < 4; j++) {
			if (j != i && feet[j].swinging && feet[j].arm == feet[i].arm) {
				return true;
			}
		}
		return false;
	}

	private static boolean crossed(double from, double to, double mark) {
		return to >= from ? from < mark && mark <= to : mark > from || mark <= to;
	}

	private static double horizontal(V v) {
		return Math.hypot(v.x(), v.z());
	}

	/** The surface under a point, found by a short ray back along the surface normal. */
	private V ground(RaukEntity e, V p, V up, double depth) {
		V a = p.add(up.mul(1.2)), b = p.sub(up.mul(depth));
		BlockHitResult hit = e.level().clip(new ClipContext(new Vec3(a.x(), a.y(), a.z()), new Vec3(b.x(), b.y(), b.z()),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
		if (hit.getType() == HitResult.Type.MISS) {
			// Nothing there: keep the point on the body's own surface plane.
			return p.sub(up.mul(p.sub(root).dot(up)));
		}
		Vec3 l = hit.getLocation();
		return new V(l.x, l.y, l.z);
	}

	/** The height of the ceiling above a point's floor, up to 12 blocks over it. */
	private double roomAbove(RaukEntity e, V p) {
		// From a little below the face (to catch a ceiling it is already poking up into), but never
		// from under what it is over: up a stair open underneath, the stair's underside is no ceiling.
		double ground = floorBelow(e, p.add(new V(0, 0.3, 0)));
		Vec3 from = new Vec3(p.x(), Math.max(Math.max(floorY + 0.5, ground + 0.3), p.y() - 1.5), p.z());
		// Over a wall (its head round a corner, or looking along a wall): a wall is no ceiling, and
		// counting it as one would only push the head down into it.
		if (solids.inside(new V(from.x, from.y, from.z), 0)) {
			return 999;
		}
		BlockHitResult hit = e.level().clip(new ClipContext(from, from.add(0, 12, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
		return hit.getType() == HitResult.Type.MISS ? 999 : hit.getLocation().y;
	}

	/** The height of the floor below a point (down to 16 blocks). */
	private double floorBelow(RaukEntity e, V p) {
		BlockHitResult hit = e.level().clip(new ClipContext(new Vec3(p.x(), p.y(), p.z()), new Vec3(p.x(), p.y() - 16, p.z()),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
		return hit.getType() == HitResult.Type.MISS ? p.y() - 16 : hit.getLocation().y;
	}

	// --- legs and arms ------------------------------------------------------------------------

	/**
	 * The metatarsus runs from the ankle down and forward to the ball of the foot; it tips up as the
	 * foot lifts and stands steeper when it rises onto its toes.
	 */
	private V metatarsus(double swing, int side, double riseBody, double gallop, Quat turn) {
		double tilt = Math.toRadians(stance.metaTilt() + 20 * swing - 18 * riseBody + 10 * gallop);
		V fwd = V.Z.rotate(V.Y, Math.toRadians(7 * side));
		return turn.rotate(fwd.mul(Math.sin(tilt)).add(V.Y.mul(-Math.cos(tilt))));
	}

	private void legs(Xform toWorld, Xform pelvis, double riseBody, double gallop, boolean flying) {
		for (int s = 0; s < 2; s++) {
			RaukSkeleton.Limb leg = sk.legs[s];
			Foot f = feet[s];
			V hip = pelvis.point(sk.restLocal[leg.upper()].o());
			Quat turn = rotAt(straight[sk.pelvis].point(sk.restLocal[leg.upper()].o()).z());
			// The ball of the foot stands off the surface it holds, whichever way that faces (a wall, the
			// underside of a landing), not just off the floor.
			V off = turn.rotate(V.Y);
			if (f.normal != null && !f.swinging && !flying) {
				off = toWorld.r().conj().rotate(f.normal);
			}
			V ball = toWorld.toLocal(f.contact).add(off.mul(BALL_HEIGHT));
			V meta = metatarsus(flying ? 0.8 : f.lifted(), f.side, riseBody, gallop, turn);
			V ankle = ball.sub(meta.mul(RaukRig.METATARSUS));
			V knee = underCeiling(hip, ankle, RaukRig.THIGH, RaukRig.SHIN, turn.rotate(new V(stance.kneeOut() * f.side, 0, 1)));
			ankle = knee.add(ankle.sub(knee).norm().mul(RaukRig.SHIN));
			setBody(leg.upper(), Frame.bar(hip, knee.sub(hip), V.X));
			setBody(leg.lower(), Frame.bar(knee, ankle.sub(knee), V.X));
			setBody(leg.end(), Frame.bar(ankle, ball.sub(ankle), V.X));

			// Toes: they curl closed as the foot lifts and open again just before it lands.
			double spread = f.swinging ? smooth(Math.clamp((f.s - 0.7) / 0.3, 0, 1)) : 0;
			double curl = flying ? 0.7 : f.lifted() * (1 - spread) - 0.25 * spread;
			double[] angles = {28, 38, 30, 22};
			for (int[] toe : leg.digits()) {
				for (int k = 0; k < toe.length; k++) {
					curlDigit(toe[k], Math.toRadians(angles[Math.min(k, 3)]) * curl - toeLift[s] * (k == 0 ? 0.5 : 1));
				}
			}
			// The dewclaw at the back of the foot folds up against it with the toes.
			int[] dew = dewclaws(leg);
			for (int k = 0; k < dew.length; k++) {
				curlDigit(dew[k], -toeLift[s] * (k == 0 ? 0.5 : 1));
			}
			// Toes that would poke into a riser or a wall bend back up off it (design doc 5.13).
			V lift = toWorld.dir(turn.rotate(V.Y)).mul(0.12);
			int in = 0;
			for (int[] toe : java.util.stream.Stream.concat(java.util.Arrays.stream(leg.digits()), java.util.stream.Stream.<int[]>of(dew)).toList()) {
				Xform x = body[leg.end()];
				for (int j : toe) {
					x = x.then(local[j]);
					V p = toWorld.point(x.o());
					if (solids.inside(p, 0.04) && solids.inside(p.add(lift), 0)) {
						in++;
					}
				}
			}
			toeLift[s] = Math.clamp(toeLift[s] + (in > 0 ? 3 : -0.8) * frameDt, 0, Math.toRadians(50));
		}
	}

	/** The dewclaw's joints of a hind foot, from the foot out. */
	private int[] dewclaws(RaukSkeleton.Limb leg) {
		int s = leg == sk.legs[0] ? 0 : 1;
		if (dewclaw[s] == null) {
			String prefix = "dewclaw_" + (leg.side() > 0 ? "l" : "r") + "_";
			java.util.List<Integer> joints = new java.util.ArrayList<>();
			for (int j = 0; j < sk.count; j++) {
				if (sk.names[j].startsWith(prefix)) {
					joints.add(j);
				}
			}
			dewclaw[s] = joints.stream().mapToInt(Integer::intValue).toArray();
		}
		return dewclaw[s];
	}

	private final int[][] dewclaw = new int[2][];

	/** How much further back each foot's toes bend, up off what they would otherwise poke into (radians). */
	private final double[] toeLift = new double[2];
	/** This frame's time step (seconds). */
	private double frameDt;

	/** Bends a finger or toe segment about its own side-to-side axis (toward the palm or sole). */
	private void curlDigit(int j, double angle) {
		Xform rest = sk.restLocal[j];
		local[j] = new Xform(rest.o(), Quat.axisAngle(rest.r().rotate(V.X), angle).mul(rest.r()));
	}

	private void arms(Xform toWorld, double tens, double speed, double running, boolean flying, double dt, double t) {
		Xform chest = body[sk.parent[sk.arms[0].upper()]];
		for (int s = 0; s < 2; s++) {
			RaukSkeleton.Limb arm = sk.arms[s];
			Foot f = feet[2 + s];
			int m = arm.side();
			double down = flying ? 1 : Math.clamp(armDown[s].update(stance.armsDown() > 0.5 ? 1 : 0, 1.1, dt), 0, 1);
			V shoulder = shoulder(chest, arm);
			// Turned the way the chest is, round the bend.
			Quat turn = rotAt(shoulder(straight[sk.parent[arm.upper()]], arm).z());

			// Hanging: the rest pose's hang, swinging against the legs as it walks.
			V hangOffset = turn.rotate(sk.restWorld[arm.end()].o().sub(sk.restWorld[arm.upper()].o()));
			double swing = Math.sin(2 * Math.PI * (phase + (m > 0 ? 0.5 : 0))) * (0.35 + 0.45 * running) * Math.min(speed / 2.5, 1);
			V hang = shoulder.add(hangOffset).add(turn.rotate(new V(0, 0, swing)));
			// Planted: the wrist just above and behind the palm on the surface; in the air, reaching ahead.
			V palm = toWorld.toLocal(f.contact);
			V planted = flying ? palm : palm.add(turn.rotate(new V(0, 0.22, -0.2)));
			V wrist = hang.lerp(planted, down);
			V handDir = turn.rotate(sk.restWorld[arm.end()].y().lerp(new V(0, -0.55, 0.83), down).norm());
			V handUp = turn.rotate(V.Y);
			handYaw[s] += (handYawTo[s] - handYaw[s]) * (1 - Math.exp(-8 * dt));
			handDir = handDir.rotate(handUp, handYaw[s] * down);
			// Holding a face its body doesn't stand on (the front of a step, a wall, the lip it climbs
			// over), the palm lies on the face and the fingers reach up it, over the edge, the way a
			// hand grips the edge of a step (design doc 5.13).
			V faceN = f.normal == null || f.swinging || flying ? null : toWorld.r().conj().rotate(f.normal);
			double onFace = faceN == null ? 0 : smooth(Math.clamp((0.8 - faceN.dot(handUp)) / 0.5, 0, 1)) * down;
			if (onFace > 0) {
				V upFace = handUp.reject(faceN);
				upFace = upFace.len() > 1e-3 ? upFace.norm() : turn.rotate(V.Z).reject(faceN).norm();
				wrist = wrist.lerp(palm.add(faceN.mul(0.22)).sub(upFace.mul(0.2)), onFace);
				handDir = handDir.lerp(upFace.mul(0.97).sub(faceN.mul(0.25)), onFace).norm();
			}
			// Knocking: the right hand reaches to the door or wall and raps it with its knuckles,
			// once every 0.8 seconds; listening afterwards it rests flat against it. Reaching, it
			// stretches to the lever or into the gap; feeding, it holds the kill down.
			if (m < 0 && handAction()) {
				V point = toWorld.toLocal(actionAt);
				V toPoint = point.sub(shoulder).norm();
				double off = 0.08;
				if (action == RaukMind.ACTION_TAP) {
					double p = ((t - actionStart) / 0.8) % 1;
					off = p < 0.15 ? Mth.lerp(p / 0.15, 0.45, 0.04) : Mth.lerp(smooth((p - 0.15) / 0.85), 0.04, 0.45);
				}
				wrist = point.sub(toPoint.mul(0.55 + off));
				handDir = toPoint;
				f.contact = toWorld.point(wrist);
			}
			boolean gripping = false;
			if (m < 0 && held != null && grabPhase != RaukCombat.PHASE_NONE && grabPhase != RaukCombat.PHASE_RECOIL) {
				// The right hand closed around the prey's body, holding it up to its face: the elbow
				// bent out and down, never locked straight, the fingers wrapped round the near side,
				// the hand working against the prey as it struggles.
				V body = toWorld.toLocal(new V(held.getX(), held.getY() + held.getBbHeight() * 0.55, held.getZ()));
				V toBody = body.sub(shoulder);
				double far = 0.8 * (RaukRig.UPPER_ARM + RaukRig.FOREARM);
				V grip = body.sub(toBody.norm().mul(0.35)).add(new V(0, -0.15, 0));
				if (grip.sub(shoulder).len() > far) {
					grip = shoulder.add(grip.sub(shoulder).norm().mul(far));
				}
				if (grabPhase == RaukCombat.PHASE_STRUGGLE) {
					grip = grip.add(new V(Math.sin(t * 13) * 0.05, Math.sin(t * 17 + 1) * 0.06, Math.sin(t * 11 + 2) * 0.04));
				}
				wrist = grip;
				handDir = body.sub(wrist).norm().add(new V(0, 0.25, 0)).norm();
				f.contact = toWorld.point(wrist);
				gripping = true;
			} else if (m < 0 && action == RaukMind.ACTION_HOOK && t - actionStart < 0.8) {
				// The hook: a short reach to the shield and a sharp pull back.
				double u = (t - actionStart) / 0.3;
				V point = toWorld.toLocal(actionAt);
				V start = point.sub(point.sub(shoulder).norm().mul(0.4));
				V end = shoulder.add(new V(m * 0.8, -1.2, 0.6));
				double k = u < 1 ? smooth(Math.clamp(u, 0, 1)) : 1 - smooth(Math.clamp((u - 1) / 0.8, 0, 1));
				V swingTo = u < 1 ? start.lerp(end, k) : wrist.lerp(end, k);
				wrist = swingTo;
				handDir = end.sub(start).norm();
				f.contact = toWorld.point(wrist);
			}
			V pole = gripping ? new V(m * 1.0, -0.25, -0.35) : turn.rotate(new V(0.3 * m, 0, -1).lerp(new V(m * stance.elbowOut(), 0.3, -0.5), down));
			V elbow = underCeiling(shoulder, wrist, RaukRig.UPPER_ARM, RaukRig.FOREARM, pole);
			wrist = elbow.add(wrist.sub(elbow).norm().mul(RaukRig.FOREARM));
			setBody(arm.upper(), Frame.bar(shoulder, elbow.sub(shoulder), V.X));
			setBody(arm.lower(), Frame.bar(elbow, wrist.sub(elbow), V.X));
			setBody(arm.end(), Frame.bar(wrist, handDir, V.X));

			// Fingers: loose and curled at rest, hooked and spread when tense, flat on the surface when
			// planted, curling closed while the hand swings to its next step, spread wide in the air.
			double[] curls = {18, 32, 30, 22, 30, 30};
			double[] spread = {-1, -0.3, 0.3, 1, 0};
			double lifted = f.lifted() * down;
			double reachOut = flying ? 1 : 0;
			for (int d = 0; d < arm.digits().length; d++) {
				int[] digit = arm.digits()[d];
				for (int k = 0; k < digit.length; k++) {
					double flat = (k < 3 ? 0.75 : 0.3) * Math.toRadians(curls[Math.min(k, 5)]) * (down - lifted);
					double grip = gripping ? Math.toRadians(42) : 0;
					double lift = (k >= 1 ? clawLift[s] : 0) + fingerWrap[s] * down;
					Quat delta = Quat.axisAngle(V.X, Math.toRadians(10) * tens + Math.toRadians(20) * lifted - flat - lift + grip);
					if (k == 0) {
						delta = Quat.axisAngle(V.Z, -m * spread[d] * Math.toRadians(6 * tens + 14 * reachOut)).mul(delta);
					}
					local[digit[k]] = new Xform(sk.restLocal[digit[k]].o(), delta.mul(sk.restLocal[digit[k]].r()));
				}
			}
			// Planted, the claws rest on the floor rather than sinking into it: they straighten until
			// the lowest of them is at the surface (measured this frame, for the next).
			if (down > 0.5 && lifted < 0.1 && !flying && !gripping && !(m < 0 && handAction())) {
				double lowest = Double.MAX_VALUE;
				for (int[] digit : arm.digits()) {
					Xform x = body[arm.end()];
					for (int j : digit) {
						x = x.then(local[j]);
						lowest = Math.min(lowest, x.o().y());
					}
				}
				double depth = palm.y() + 0.06 - lowest;
				clawLift[s] = Math.clamp(clawLift[s] + (depth > 0 ? Math.min(depth * 1.5, 0.12) : -dt * 0.3), 0, Math.toRadians(75));
			} else {
				clawLift[s] = Math.max(0, clawLift[s] - dt);
			}
			// Planted on a step or a lip, its fingers would poke into the riser or the wall in front of
			// them (design doc 5.13): the hand turns to where they are clear, the clearest way as near
			// to straight on as there is, and what is still in bends back up off it.
			if (down > 0.5 && !flying && !gripping && !(m < 0 && handAction())) {
				int now = fingersIn(toWorld, wrist, handDir, handUp, 0, arm);
				if (now > 0) {
					int best = now;
					double bestYaw = 0;
					for (int deg : new int[] {30, -30, 60, -60, 90, -90}) {
						double yaw = Math.toRadians(deg);
						int in = fingersIn(toWorld, wrist, handDir, handUp, yaw, arm);
						if (in < best - (Math.abs(yaw + handYaw[s] - handYawTo[s]) < 0.1 ? 0 : 1)) {
							best = in;
							bestYaw = yaw;
						}
					}
					handYawTo[s] = Math.clamp(handYaw[s] + bestYaw, -Math.PI / 2, Math.PI / 2);
					fingerWrap[s] = Math.min(Math.toRadians(50), fingerWrap[s] + dt * (best > 0 ? 3 : 0));
				} else {
					fingerWrap[s] = Math.max(0, fingerWrap[s] - dt * 0.8);
					handYawTo[s] *= Math.exp(-0.5 * dt);
				}
			} else {
				handYawTo[s] = 0;
				fingerWrap[s] = Math.max(0, fingerWrap[s] - dt * 2);
			}
		}
	}

	/**
	 * How many joints of a hand's fingers are in a block (more than a touch: fingers flat on a floor
	 * don't count) with the hand at the wrist pointing the given way, turned about its up by yaw more.
	 */
	private int fingersIn(Xform toWorld, V wrist, V dir, V up, double yaw, RaukSkeleton.Limb arm) {
		Xform hand = Xform.of(Frame.bar(wrist, dir.rotate(up, yaw), V.X));
		V lift = toWorld.dir(up).mul(0.12);
		int in = 0;
		for (int[] digit : arm.digits()) {
			Xform x = hand;
			for (int j : digit) {
				x = x.then(local[j]);
				V p = toWorld.point(x.o());
				if (solids.inside(p, 0.04) && solids.inside(p.add(lift), 0)) {
					in++;
				}
			}
		}
		return in;
	}

	/**
	 * The middle joint (knee or elbow) as usual, unless that puts it into the ceiling or the floor:
	 * then the joint folds round the line between its ends to whichever side keeps it in the room,
	 * as close to the usual way as it can. Folded low through a doorway, the knees go out and back
	 * rather than up.
	 */
	private V underCeiling(V root, V target, double l1, double l2, V pole) {
		V joint = ik(root, target, l1, l2, pole);
		double room = lowestCeiling();
		double best = cramped(joint, room) + walled(root, joint, target);
		if (best <= 0) {
			return joint;
		}
		V axis = target.sub(root);
		if (axis.len() < 1e-4) {
			return joint;
		}
		axis = axis.norm();
		for (int deg : new int[] {30, -30, 60, -60, 90, -90, 120, -120, 150, -150, 180}) {
			V j = ik(root, target, l1, l2, pole.rotate(axis, Math.toRadians(deg)));
			double v = cramped(j, room) + walled(root, j, target) + Math.abs(deg) * 0.0005;
			if (v < best) {
				best = v;
				joint = j;
			}
		}
		return joint;
	}

	/**
	 * How much of a limb (body space: from its root to the middle joint to its end) is in a wall or
	 * anything else solid: the middle joint and along both bones, so a shin through a wall counts
	 * though the knee is clear (design doc 5.13).
	 */
	private double walled(V root, V joint, V end) {
		if (!pathBody) {
			return 0;
		}
		int in = solids.inside(lastToWorld.point(joint), 0.02) ? 1 : 0;
		for (int k = 1; k <= 3; k++) {
			in += solids.inside(lastToWorld.point(root.lerp(joint, k / 4.0)), 0.02) ? 1 : 0;
			in += solids.inside(lastToWorld.point(joint.lerp(end, k / 4.0)), 0.02) ? 1 : 0;
		}
		return Math.min(1, in * 0.3);
	}

	/** A hand or foot's place brought in from a wall toward the middle of the way (mid) until it's clear. */
	private V keepIn(V mid, V p, V up) {
		if (!pathBody) {
			return p;
		}
		V lift = up.mul(0.7);
		return solids.clear(mid.add(lift), p.add(lift), 0.3).sub(lift);
	}

	/** How far a joint (body space, floor at 0) pokes into the ceiling or the floor. */
	private static double cramped(V joint, double room) {
		return Math.max(0, joint.y() - (room - 0.2)) + 2 * Math.max(0, 0.1 - joint.y());
	}

	/** The lowest ceiling over its back, above the floor it stands on; large when there is none near. */
	private double lowestCeiling() {
		double lowest = Double.MAX_VALUE;
		for (double c : trunkCeiling) {
			lowest = Math.min(lowest, c);
		}
		return lowest == Double.MAX_VALUE || lastSurface != Direction.DOWN ? 99 : lowest - root.y();
	}

	/** Where the middle joint goes so both bones keep their lengths, bending toward the pole. */
	private static V ik(V root, V target, double l1, double l2, V pole) {
		V d = target.sub(root);
		double dist = Math.clamp(d.len(), Math.abs(l1 - l2) + 1e-3, l1 + l2 - 1e-3);
		V dn = d.norm();
		double a = (l1 * l1 - l2 * l2 + dist * dist) / (2 * dist);
		double h = Math.sqrt(Math.max(l1 * l1 - a * a, 0));
		V bend = pole.reject(dn);
		if (bend.len() < 1e-6) {
			bend = V.Z.reject(dn);
		}
		return root.add(dn.mul(a)).add(bend.norm().mul(h));
	}

	/**
	 * Keeps its back under the ceiling: the top of each vertebra of the back (spikes and all) is
	 * measured against the ceiling right over it. Any of them pressing into it pushes the body down
	 * and straighter for the next frame, quickly; with room to spare it eases back up, slowly. So it
	 * sinks before it touches, stays down under a low ceiling, and rises again once clear.
	 */
	private void fitUnderCeiling(RaukEntity e, Xform toWorld, boolean onFloor, double dt) {
		if (!onFloor) {
			flatten = Math.max(0, flatten - dt);
			return;
		}
		boolean measure = e.tickCount != ceilingTick;
		ceilingTick = e.tickCount;
		double excess = -99;
		for (int k = 0; k < trunk.length; k++) {
			V top = toWorld.point(body[trunk[k]].point(new V(0, 0.42, 0)));
			if (measure) {
				Vec3 from = new Vec3(top.x(), Math.min(top.y(), floorY + 0.5), top.z());
				BlockHitResult hit = e.level().clip(new ClipContext(from, from.add(0, 9, 0), ClipContext.Block.COLLIDER,
						ClipContext.Fluid.NONE, e));
				trunkCeiling[k] = hit.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : hit.getLocation().y;
			}
			excess = Math.max(excess, top.y() - (trunkCeiling[k] - 0.15));
		}
		if (excess > 0) {
			flatten = Math.min(1.3, flatten + excess * 2 + dt * 0.8);
		} else if (excess < -0.3) {
			flatten = Math.max(0, flatten - dt * 0.6);
		}
	}

	/** For each joint of the trunk, the points of what rides on it (ribs, spikes, shoulder blades, hip bones) in its own frame. */
	private final V[][] trunkProbes;
	/** How far each joint of the trunk is pushed out of what it would be in (world), eased off. */
	private final V[] trunkPush;

	private V[][] trunkProbes() {
		java.util.Map<Integer, Integer> trunkIndex = new java.util.HashMap<>();
		for (int t = 0; t < trunk.length; t++) {
			trunkIndex.put(trunk[t], t);
		}
		java.util.Set<Integer> neck = new java.util.HashSet<>();
		for (int j : sk.neck) {
			neck.add(j);
		}
		java.util.List<java.util.List<V>> probes = new java.util.ArrayList<>();
		for (int t = 0; t < trunk.length; t++) {
			java.util.List<V> list = new java.util.ArrayList<>();
			list.add(V.ZERO);
			probes.add(list);
		}
		for (int d = 0; d < sk.count; d++) {
			if (trunkIndex.containsKey(d)) {
				continue;
			}
			// Up to the joint of the trunk it hangs from, through nothing that moves on its own.
			int j = d;
			boolean own = true;
			while (j >= 0 && !trunkIndex.containsKey(j)) {
				String n = sk.names[j];
				if (neck.contains(j) || n.startsWith("leg_") || n.startsWith("arm_") || n.startsWith("toe_") || n.startsWith("finger_")
						|| n.startsWith("hand_") || n.startsWith("thumb_") || n.startsWith("dewclaw_") || n.startsWith("tail_")
						|| n.startsWith("head") || n.startsWith("jaw") || n.startsWith("eye_") || n.startsWith("skull_") || n.startsWith("drool")) {
					own = false;
					break;
				}
				j = sk.parent[j];
			}
			if (own && j >= 0) {
				probes.get(trunkIndex.get(j)).add(sk.restWorld[j].toLocal(sk.restWorld[d]).o());
			}
		}
		V[][] out = new V[trunk.length][];
		for (int t = 0; t < trunk.length; t++) {
			out[t] = probes.get(t).toArray(new V[0]);
		}
		return out;
	}

	/**
	 * Keeps the trunk out of walls (design doc 5.13): each joint of it, with its ribs, spikes,
	 * shoulder blades and hip bones, that would be in a block is pushed out the shortest way, the
	 * pushes spread along the back so it bends away from a wall rather than kinking, the chest kept
	 * whole. A push is taken at once and let go of over a moment, so the body doesn't shiver along
	 * a wall. The legs and arms are worked out after, from where the trunk ends up.
	 */
	private void keepTrunkOut(Xform toWorld, double dt) {
		int n = trunk.length;
		V[] push = new V[n];
		java.util.Arrays.fill(push, V.ZERO);
		for (int iter = 0; iter < 3; iter++) {
			V[] need = new V[n];
			boolean any = false;
			for (int t = 0; t < n; t++) {
				need[t] = V.ZERO;
				Xform w = toWorld.then(body[trunk[t]]);
				for (V q : trunkProbes[t]) {
					V p = w.point(q).add(push[t]);
					if (solids.inside(p, 0.02)) {
						V d = solids.escape(p, 0.06, 1.2).sub(p);
						if (d.len() > need[t].len()) {
							need[t] = d;
						}
					}
				}
				any |= need[t].len() > 0;
			}
			if (!any) {
				break;
			}
			// Spread along the back, and the chest moved as one piece.
			V chest = V.ZERO;
			int chestCount = 0;
			for (int t = 0; t < n; t++) {
				V s = need[t].mul(0.5);
				if (t > 0) {
					s = s.add(need[t - 1].mul(0.25));
				}
				if (t < n - 1) {
					s = s.add(need[t + 1].mul(0.25));
				}
				// Never less than it needs itself.
				V own = need[t];
				if (own.len() > 0 && s.dot(own.norm()) < own.len()) {
					s = s.add(own.norm().mul(own.len() - s.dot(own.norm())));
				}
				push[t] = push[t].add(s);
				if (t > 0 && sk.chest[t - 1]) {
					chest = chest.add(push[t]);
					chestCount++;
				}
			}
			if (chestCount > 0) {
				V whole = chest.mul(1.0 / chestCount);
				V most = V.ZERO;
				for (int t = 1; t < n; t++) {
					if (sk.chest[t - 1] && push[t].len() > most.len()) {
						most = push[t];
					}
				}
				for (int t = 1; t < n; t++) {
					if (sk.chest[t - 1]) {
						push[t] = most.len() > whole.len() ? most : whole;
					}
				}
			}
		}
		double ease = 1 - Math.exp(-6 * dt);
		Quat toBody = toWorld.r().conj();
		for (int t = 0; t < n; t++) {
			trunkPush[t] = push[t].len() >= trunkPush[t].len() ? push[t] : trunkPush[t].lerp(push[t], ease);
			if (trunkPush[t].len() > 1e-4) {
				body[trunk[t]] = new Xform(body[trunk[t]].o().add(toBody.rotate(trunkPush[t])), body[trunk[t]].r());
			}
		}
		for (int j : trunk) {
			local[j] = sk.parent[j] < 0 ? body[j] : body[sk.parent[j]].toLocal(body[j]);
		}
	}

	/** Sets a part from its new body-space frame, and its transform relative to its parent. */
	private void setBody(int j, Frame f) {
		Xform x = Xform.of(f);
		body[j] = x;
		local[j] = body[sk.parent[j]].toLocal(x);
	}

	// --- neck, head and gaze ------------------------------------------------------------------

	/**
	 * The neck bends (spread over all its vertebrae) so the skull reaches its carriage point, which
	 * swings toward whatever it is looking at; then the skull turns to look. The skull keeps its
	 * orientation in the world, crown up, whatever the body does underneath, as far as the neck
	 * allows: upside down on a ceiling it ends up almost upright, but tilted. Scanning, the gaze
	 * snaps between points like a bird's; locked on, it tracks smoothly.
	 */
	private void neckAndHead(RaukEntity e, Xform toWorld, double tens, double att, double gallop, double riseNeck, double rise,
			double sinceStartle, float partialTick, double dt, double t) {
		int head = sk.head;
		Entity target = e.gazeTarget();
		V gazeTarget;
		double gazeHz;
		boolean startled = sinceStartle < 1.6;
		boolean noticed = t - noticeTime < 1.4;
		Vec3 point = e.lookPoint();
		double sincePhase = t - grabPhaseStart;
		if (held != null && grabPhase != RaukCombat.PHASE_NONE) {
			gazeTarget = toLocal(toWorld, held.getEyePosition(partialTick));
			gazeHz = 20;
			att = 1;
		} else if (startled) {
			gazeTarget = toWorld.toLocal(startleAt);
			gazeHz = 40;
			att = 1;
		} else if (noticed) {
			gazeTarget = toWorld.toLocal(noticeAt);
			gazeHz = 30;
			att = Math.max(att, 0.8);
		} else if (handAction()) {
			gazeTarget = toWorld.toLocal(actionAt);
			gazeHz = 6;
			att = 1;
		} else if (target != null && att > 0.3) {
			gazeTarget = toLocal(toWorld, target.getEyePosition(partialTick));
			gazeHz = 4 + 4 * tens;
		} else if (point != null) {
			gazeTarget = toLocal(toWorld, point);
			gazeHz = 5;
			att = Math.max(att, 0.6);
		} else {
			if (t >= nextScan) {
				var random = e.getRandom();
				double yaw = Math.toRadians((random.nextDouble() * 2 - 1) * 100 * (0.4 + 0.6 * random.nextDouble()));
				double pitch = Math.toRadians(-25 + 35 * random.nextDouble());
				scanDir = new V(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
				nextScan = t + 0.3 + 0.9 * random.nextDouble();
			}
			gazeTarget = body[head].o().add(scanDir.mul(10));
			gazeHz = 14;
		}
		V gazeAt = toLocal(toWorld, gaze.update(toWorld.point(gazeTarget), gazeHz, dt));

		// Where the skull should be: the stance's carriage, swung toward the gaze.
		int base = sk.neck[0];
		V neckBase = body[base].o();
		V carriage = stance.head().add(new V(0, -0.3 * tens * (1 - stance.armsDown()), 0))
				.add(new V(0, -0.35 * gallop, 0.7 * gallop)).lerp(new V(0, 7.6, 1.6), riseNeck);
		// Carried along the way it is going: round a corner the head goes first. That is clear (it
		// is where the body is about to go), so it is where the skull comes back to off a wall.
		double headZ = carriage.z();
		carriage = bendPoint(carriage);
		V onPath = new V(carriage.x(), Math.max(0.6, Math.min(carriage.y(), headRoom - 0.5)), carriage.z());
		boolean along = true;
		if (action == RaukMind.ACTION_LISTEN) {
			// The side of its head pressed close to where it knocked.
			V at = toWorld.toLocal(actionAt);
			V back = neckBase.sub(at);
			carriage = at.add(back.norm().mul(0.7));
			along = false;
		}
		if (held != null && (grabPhase == RaukCombat.PHASE_LIFT || grabPhase == RaukCombat.PHASE_STRUGGLE)) {
			// Its face close to its catch, looking at it.
			V prey = toLocal(toWorld, held.getEyePosition(partialTick));
			carriage = prey.add(neckBase.sub(prey).norm().mul(1.3));
			along = false;
		} else if (held != null && grabPhase == RaukCombat.PHASE_BITE) {
			// The jaw opens fully, it draws back, then lunges onto the head and neck.
			V prey = toLocal(toWorld, held.getEyePosition(partialTick));
			double back = sincePhase < 0.4 ? 1.6 : sincePhase < 0.6 ? Mth.lerp((sincePhase - 0.4) / 0.2, 1.6, 0.3) : 0.3;
			carriage = prey.add(neckBase.sub(prey).norm().mul(back));
			along = false;
		} else if (grabPhase == RaukCombat.PHASE_RECOIL && sincePhase < 1.0) {
			// Struck in the face: the head jerks back and up, then comes forward again.
			double k = sincePhase < 0.12 ? sincePhase / 0.12 : 1 - smooth((sincePhase - 0.12) / 0.88);
			carriage = carriage.add(new V(0, 0.7, -1.2).mul(k));
		}
		double sinceAction = t - actionStart;
		if (action == RaukMind.ACTION_LUNGE && sinceAction < 0.6) {
			// The snap: the head darts out to just short of the prey and comes back.
			V at = toWorld.toLocal(actionAt);
			V dart = at.add(neckBase.sub(at).norm().mul(0.5));
			V rel = dart.sub(neckBase);
			if (rel.len() > 4.2) {
				dart = neckBase.add(rel.norm().mul(4.2));
			}
			double k = sinceAction < 0.12 ? smooth(sinceAction / 0.12) : 1 - smooth(Math.clamp((sinceAction - 0.2) / 0.4, 0, 1));
			carriage = carriage.lerp(dart, k);
			along = false;
		} else if (action == RaukMind.ACTION_PEEK) {
			// Peeking: only the head comes round the corner, level and still.
			V rel = toWorld.toLocal(actionAt).sub(neckBase);
			carriage = neckBase.add(rel.len() > 3.8 ? rel.norm().mul(3.8) : rel);
			along = false;
		} else if (action == RaukMind.ACTION_FEED) {
			// Feeding: down onto the kill, a yank back and up as it tears a mouthful away, then chewing
			// with its head a little raised, listening. One mouthful every two seconds.
			V at = toWorld.toLocal(actionAt);
			V down = at.add(new V(0, 0.45, 0)).add(neckBase.sub(at).norm().mul(0.55));
			V up = down.add(neckBase.sub(down).norm().mul(0.9)).add(new V(0, 0.55, 0));
			double s = (sinceAction % 2.0);
			double k = s < 0.3 ? 1 - smooth(s / 0.3) : s < 0.5 ? smooth((s - 0.3) / 0.2) : 1 - 0.12 * Math.abs(Math.sin(Math.PI * 2 * (s - 0.5)));
			carriage = down.lerp(up, k);
			along = false;
		} else if (carrying) {
			// The kill hangs from its jaws: the head carried low to take the weight.
			carriage = carriage.add(new V(0, -0.45, 0.15));
		}
		if (!dead && carriage.y() > headRoom - 0.5) {
			// Ducking under a lintel or a low ceiling: the skull stays below it.
			carriage = new V(carriage.x(), Math.max(0.6, headRoom - 0.5), carriage.z());
		}
		if (dead) {
			carriage = stance.head().add(new V(0, -0.8, 0.4));
			along = false;
		}
		// It swings its head toward what it looks at, from the way it is going.
		V toGaze = gazeAt.sub(neckBase);
		double from = along ? Math.toDegrees(yawAt(headZ)) : 0;
		double yaw = Math.toRadians(Math.clamp(Mth.wrapDegrees(Math.toDegrees(Math.atan2(toGaze.x(), toGaze.z())) - from), -150, 150));
		double pitch = Math.clamp(Math.atan2(toGaze.y(), horizontal(toGaze)), -0.9, 0.9);
		V rel = carriage.sub(neckBase).rotate(V.Y, 0.55 * yaw).add(new V(0, 0.5 * Math.sin(pitch), 0));
		V reach = neckBase.add(rel).add(gazeAt.sub(neckBase.add(rel)).norm().mul(0.25 * att));
		reach = clearHead(toWorld, reach, onPath, dt);
		// Never through a wall to the far side of it, however hard it swings round (a startle in a
		// corridor): the skull goes only as far toward its place as it can get from the base of the
		// neck without touching anything (design doc 5.13).
		if (pathBody) {
			V neckWorld = toWorld.point(neckBase);
			if (!solids.hits(neckWorld, SKULL_RADIUS * 0.6)) {
				reach = toWorld.toLocal(solids.sweep(neckWorld, toWorld.point(reach), SKULL_RADIUS * 0.6));
			}
		}

		// Bend the neck toward it, a few degrees at a time per vertebra (cyclic coordinate descent).
		for (int k = 0; k < neckDelta.length; k++) {
			neckDelta[k] = neckDelta[k].scale(1 - Math.min(1, dt * 0.5));
		}
		boolean fast = startled || grabPhase == RaukCombat.PHASE_BITE || grabPhase == RaukCombat.PHASE_RECOIL
				|| (action == RaukMind.ACTION_LUNGE && sinceAction < 0.6);
		double stepLimit = Math.toRadians(fast ? 12 : 4), jointLimit = Math.toRadians(30 + 15 * rise);
		bendNeck(reach, 4, stepLimit, jointLimit);
		// The neck can't always get the skull where it's sent at once (turning back on itself in a
		// corridor): if the skull is in a wall now, it bends on harder toward the nearest clear place.
		V skull = toWorld.point(body[head].o());
		if (pathBody && solids.hits(skull, SKULL_RADIUS * 0.85)) {
			V out = toWorld.toLocal(solids.clear(safeSkull(toWorld, onPath), skull, SKULL_RADIUS));
			bendNeck(out, 6, Math.toRadians(14), jointLimit);
			skull = toWorld.point(body[head].o());
		}
		// Not just the skull's ball: its clasping hands and the neck, spikes and all, kept out of walls
		// too (design doc 5.13). Whichever of them is furthest in, the skull is sent out that much
		// further, and the neck bends after it.
		for (int iter = 0; iter < 2 && pathBody; iter++) {
			V push = headNeckPush(toWorld);
			if (push.len() < 0.02) {
				break;
			}
			bendNeck(toWorld.toLocal(skull.add(push.mul(1.2))), 6, Math.toRadians(14), jointLimit);
			skull = toWorld.point(body[head].o());
		}
		if (!solids.hits(skull, SKULL_RADIUS * 0.85)) {
			lastClearSkull = skull;
		}
		for (int k = 0; k < sk.neck.length; k++) {
			local[sk.neck[k]] = new Xform(sk.restLocal[sk.neck[k]].o(), neckDelta[k].mul(sk.restLocal[sk.neck[k]].r()));
		}

		// The skull looks at the gaze point, face drooping a little unless locked on, crown toward
		// the world's up.
		V worldUp = toWorld.r().conj().rotate(V.Y);
		V headPos = body[head].o();
		V look = gazeAt.sub(headPos).norm();
		double droop = Math.toRadians(16) * (1 - 0.6 * att);
		V face = look.add(worldUp.mul(-Math.sin(droop))).norm();
		V crown = worldUp.reject(face);
		if (crown.len() < 1e-3) {
			crown = V.Y.reject(face);
		}
		crown = crown.norm();
		Quat want = Quat.fromAxes(crown.cross(face), crown, face);
		Quat parent = body[sk.parent[head]].r();
		// Risen, the neck stands nearly upright, so the skull bends further on it to look down;
		// upside down it rolls as far as it can toward upright.
		double limit = 60 + 45 * rise + 60 * (1 - worldUp.y()) / 2;
		Quat delta = parent.conj().mul(want).mul(sk.restLocal[head].r().conj()).clamp(Math.toRadians(limit));
		headDelta = Quat.slerp(headDelta, delta, 1 - Math.exp(-dt * (fast ? 60 : att > 0.5 ? 12 : 25)));
		place(head, headDelta);
	}

	/** Points kept out of walls on the neck and head besides the skull's ball: the joint each rides on and where it is in that joint's frame. */
	private int[] headProbeAnchor;
	private V[] headProbeAt;

	private void headProbes() {
		java.util.Set<Integer> neckSet = new java.util.HashSet<>();
		for (int j : sk.neck) {
			neckSet.add(j);
		}
		java.util.List<Integer> anchors = new java.util.ArrayList<>();
		java.util.List<V> at = new java.util.ArrayList<>();
		for (int j : sk.neck) {
			anchors.add(j);
			at.add(V.ZERO);
		}
		for (int d = 0; d < sk.count; d++) {
			String n = sk.names[d];
			boolean skullBone = n.startsWith("skull_");
			if (neckSet.contains(d) || !(skullBone || n.contains("spike"))) {
				continue;
			}
			// Up to the neck vertebra or the skull it rides on.
			int j = sk.parent[d];
			while (j >= 0 && !neckSet.contains(j) && j != sk.head) {
				j = sk.parent[j];
			}
			if (j >= 0 && (skullBone ? j == sk.head : neckSet.contains(j))) {
				anchors.add(j);
				at.add(sk.restWorld[j].toLocal(sk.restWorld[d]).o());
			}
		}
		headProbeAnchor = anchors.stream().mapToInt(Integer::intValue).toArray();
		headProbeAt = at.toArray(new V[0]);
	}

	/** The way out (world) for whichever point of the neck and head besides the skull's ball is furthest into a wall; zero if none is. */
	private V headNeckPush(Xform toWorld) {
		if (headProbeAnchor == null) {
			headProbes();
		}
		V most = V.ZERO;
		for (int i = 0; i < headProbeAnchor.length; i++) {
			V p = toWorld.point(body[headProbeAnchor[i]].point(headProbeAt[i]));
			if (solids.inside(p, 0.03)) {
				V d = solids.escape(p, 0.05, 1.0).sub(p);
				if (d.len() > most.len()) {
					most = d;
				}
			}
		}
		return most;
	}

	/** Bends the neck toward the skull's place, a few degrees at a time per vertebra (cyclic coordinate descent). */
	private void bendNeck(V reach, int iterations, double stepLimit, double jointLimit) {
		for (int iter = 0; iter < iterations; iter++) {
			for (int k = sk.neck.length - 1; k >= 0; k--) {
				refreshNeck(k);
				V joint = body[sk.neck[k]].o();
				V end = body[sk.head].o();
				Quat turn = Quat.fromTo(end.sub(joint), reach.sub(joint)).scale(0.5).clamp(stepLimit);
				Quat parent = body[sk.parent[sk.neck[k]]].r();
				neckDelta[k] = parent.conj().mul(turn).mul(parent).mul(neckDelta[k]).clamp(jointLimit);
			}
		}
		refreshNeck(0);
	}

	/** Recomputes the neck from vertebra k up to the skull. */
	private void refreshNeck(int k) {
		for (int i = k; i < sk.neck.length; i++) {
			int j = sk.neck[i];
			Xform rest = sk.restLocal[j];
			body[j] = body[sk.parent[j]].then(new Xform(rest.o(), neckDelta[i].mul(rest.r())));
		}
		Xform rest = sk.restLocal[sk.head];
		body[sk.head] = body[sk.parent[sk.head]].then(new Xform(rest.o(), headDelta.mul(rest.r())));
	}

	private static V toLocal(Xform toWorld, Vec3 p) {
		return toWorld.toLocal(new V(p.x, p.y, p.z));
	}

	private static V toLocal(Xform toWorld, V p) {
		return toWorld.toLocal(p);
	}

	private void jawAndRibs(double down, double breathe, double pant, double t) {
		double open = Math.toRadians(3) * breathe + Math.toRadians(4) * down + pant * Math.toRadians(16 + 6 * breathe);
		double sincePhase = t - grabPhaseStart;
		if (held != null && grabPhase == RaukCombat.PHASE_STRUGGLE) {
			open += Math.toRadians(12);
		} else if (grabPhase == RaukCombat.PHASE_BITE) {
			// Wide, then shut on the bite.
			open += Math.toRadians(sincePhase < 0.55 ? 62 * smooth(Math.clamp(sincePhase / 0.4, 0, 1)) : 8);
		}

		double sinceAction = t - actionStart;
		if (action == RaukMind.ACTION_LUNGE && sinceAction < 0.3) {
			// Wide as it darts, shut hard on the snap.
			open += Math.toRadians(55) * (sinceAction < 0.12 ? smooth(sinceAction / 0.12) : Math.max(0, 1 - (sinceAction - 0.12) / 0.06));
		} else if (action == RaukMind.ACTION_FEED) {
			double s = sinceAction % 2.0;
			open += s > 0.1 && s < 0.35 ? Math.toRadians(40) * Math.sin(Math.PI * (s - 0.1) / 0.25)
					: s >= 0.5 ? Math.toRadians(12) * Math.abs(Math.sin(Math.PI * 2.5 * (s - 0.5))) : 0;
		} else if (carrying) {
			open += Math.toRadians(14);
		}
		if (dead) {
			open = Math.toRadians(22);
		}
		place(sk.jaw, Quat.axisAngle(V.X, open));
		// Squeezing through a doorway the ribcage folds in toward the spine, narrower than the gap
		// (design doc 5.13).
		double fold = Math.toRadians(28) * Math.clamp(stance.shoulderIn() / 0.7, 0, 1);
		for (int i = 0; i < sk.ribs.length; i++) {
			int j = sk.ribs[i];
			Quat d = Quat.axisAngle(V.Z, sk.ribSide[i] * (Math.toRadians(2.5) * (breathe * 0.5 + 0.5) - fold));
			local[j] = new Xform(sk.restLocal[j].o(), d.mul(sk.restLocal[j].r()));
		}
	}

	// --- ropes: tail and drool ----------------------------------------------------------------

	/**
	 * The tail as a rope in the world: stiff near the pelvis, where it keeps its rest shape, and
	 * looser toward the tip, which trails behind, swings with the body and drags on the ground.
	 * It stays out of the surface the body holds and off the floor; on a wall or ceiling it hangs.
	 */
	private void tail(Xform toWorld, V surfaceUp, double dt) {
		int n = sk.tail.length;
		Xform pelvisWorld = toWorld.then(body[sk.pelvis]);
		// Moving, the loose end lets go of its resting curl and drags along behind; standing, it
		// slowly curls back round beside the feet.
		double moving = Math.clamp(lastSpeed / 1.2, 0, 1);
		int steps = Math.max(1, (int) Math.ceil(dt * 60));
		double h = dt / steps;
		for (int st = 0; st < steps; st++) {
			for (int k = 0; k <= n; k++) {
				V home = pelvisWorld.point(tailRest[k]);
				if (k == 0) {
					tail[0] = home;
					tailPrev[0] = home;
					continue;
				}
				V v = tail[k].sub(tailPrev[k]).mul(0.985);
				tailPrev[k] = tail[k];
				V p = tail[k].add(v).add(new V(0, -18 * h * h, 0));
				double u = k / (double) n;
				double loose = u > 0.3 ? Mth.lerp(moving, 1, 0.08) : u > 0.12 && pathBody ? Mth.lerp(moving, 1, 0.35) : 1;
				double stiffness = 45 * Math.pow(1 - u, 3) * loose + 0.4 * (1 - moving);
				tail[k] = p.lerp(home, 1 - Math.exp(-stiffness * h));
			}
			for (int iter = 0; iter < 6; iter++) {
				for (int k = 0; k < n; k++) {
					V d = tail[k + 1].sub(tail[k]);
					double len = d.len();
					if (len < 1e-6) {
						continue;
					}
					V fix = d.mul((len - tailLength[k]) / len);
					if (k == 0) {
						tail[1] = tail[1].sub(fix);
					} else {
						tail[k] = tail[k].add(fix.mul(0.5));
						tail[k + 1] = tail[k + 1].sub(fix.mul(0.5));
					}
				}
				for (int k = 1; k <= n; k++) {
					// Pushed out, not thrown: the push gives it no speed.
					V out = keepOut(tail[k], k < n ? sk.tailRadius[k] : 0.02, surfaceUp);
					tailPrev[k] = tailPrev[k].add(out.sub(tail[k]));
					tail[k] = out;
				}
			}
			if (surfaceUp != null && moving > 0 && pathBody) {
				tailFollow(surfaceUp, moving, h);
			}
			// Never through a wall: each bone of it is kept out of whatever is solid, coming back
			// toward the one before it (which is clear) if the shortest way out isn't on that side.
			for (int k = 1; k <= n && pathBody; k++) {
				double r = (k < n ? sk.tailRadius[k] : 0.02) + 0.03;
				V out = solids.pushOut(tail[k], r);
				if (out != tail[k] && solids.hits(tail[k - 1].lerp(out, 0.5), r * 0.6)) {
					out = solids.clear(tail[k - 1], tail[k], r);
				}
				tailPrev[k] = tailPrev[k].add(out.sub(tail[k]));
				tail[k] = out;
			}
			for (int k = 1; k <= n; k++) {
				double r = k < n ? sk.tailRadius[k] : 0.02;
				if (tail[k].y() <= floorY + r + 0.005) {
					// Dragging on the floor: friction takes most of its slide.
					V p = tail[k], prev = tailPrev[k];
					tailPrev[k] = new V(p.x() - (p.x() - prev.x()) * 0.5, prev.y(), p.z() - (p.z() - prev.z()) * 0.5);
				}
			}
		}
		V dorsal = body[sk.pelvis].then(sk.restLocal[sk.tail[0]]).y();
		for (int k = 0; k < n; k++) {
			V a = toWorld.toLocal(tail[k]), b = toWorld.toLocal(tail[k + 1]);
			Frame f = Frame.along(a, b.sub(a), dorsal);
			setBody(sk.tail[k], f);
			dorsal = f.y();
		}
	}

	/** Pushes a point out of the floor and out of the surface the body holds. */
	private V keepOut(V p, double r, V surfaceUp) {
		if (p.y() < floorY + r) {
			p = new V(p.x(), floorY + r, p.z());
		}
		if (surfaceUp != null) {
			double above = p.sub(root).dot(surfaceUp);
			if (above < r) {
				p = p.add(surfaceUp.mul(r - above));
			}
		}
		return p;
	}

	/** Each string of drool is a short rope hanging from the teeth, swinging as the head moves. */
	private void drool(Xform toWorld, double dt) {
		int steps = Math.max(1, (int) Math.ceil(dt * 60));
		double h = dt / steps;
		for (int s = 0; s < sk.drool.length; s++) {
			int[] strand = sk.drool[s];
			V[] p = drool[s], prev = droolPrev[s];
			Xform parent = toWorld.then(body[sk.parent[strand[0]]]);
			for (int st = 0; st < steps; st++) {
				p[0] = parent.point(sk.restLocal[strand[0]].o());
				prev[0] = p[0];
				for (int k = 1; k < p.length; k++) {
					// Heavy, sticky strings: they swing a little and settle quickly.
					V v = p[k].sub(prev[k]).mul(0.88);
					prev[k] = p[k];
					p[k] = p[k].add(v).add(new V(0, -40 * h * h, 0));
				}
				for (int iter = 0; iter < 4; iter++) {
					for (int k = 0; k + 1 < p.length; k++) {
						V d = p[k + 1].sub(p[k]);
						double len = d.len();
						if (len < 1e-6) {
							continue;
						}
						V fix = d.mul((len - droolLength[s]) / len);
						if (k == 0) {
							p[1] = p[1].sub(fix);
						} else {
							p[k] = p[k].add(fix.mul(0.5));
							p[k + 1] = p[k + 1].sub(fix.mul(0.5));
						}
					}
				}
				// Drool hangs; when the head whips round, the strings trail but never stick out sideways.
				for (int k = 0; k + 1 < p.length; k++) {
					V d = p[k + 1].sub(p[k]);
					double side = horizontal(d), max = droolLength[s] * Math.sin(Math.toRadians(45));
					if (side > max) {
						double f = max / side;
						p[k + 1] = p[k].add(new V(d.x() * f, -droolLength[s] * Math.cos(Math.toRadians(45)), d.z() * f));
					}
				}
			}
			for (int k = 0; k < strand.length; k++) {
				V a = toWorld.toLocal(p[k]), b = toWorld.toLocal(p[k + 1]);
				setBody(strand[k], Frame.bar(a, b.sub(a), V.X));
			}
		}
	}

	// --- the way it goes -----------------------------------------------------------------------

	/**
	 * The trail of where its root has been: a point every fifth of a block, newest first, as far back
	 * as the tail reaches. Backing up, the points it comes back over go. Standing, and above all
	 * turning on the spot, the trail swings round to lie straight out behind it again, a little at a
	 * time, so the hind end follows the front round. Onto another surface it goes on (up a wall it
	 * runs back down to the floor it left, so the hind end follows where the head went); only in the
	 * air does it start again, straight out behind, the body taking up its bends again over a moment.
	 */
	private void trail(V up, double speed, boolean restart, double dt) {
		V back = forward.reject(up);
		back = back.len() > 1e-4 ? back.norm().mul(-1) : V.Z.mul(-1);
		if (restart || trail.isEmpty()) {
			straighten(back);
			if (restart) {
				bendWeight = 0;
			}
		}
		bendWeight = Math.min(1, bendWeight + dt * 1.5);
		while (trail.size() > 1 && trail.get(0).sub(root).reject(up).dot(back) < -0.05) {
			trail.remove(0);
		}
		if (root.sub(trail.get(0)).len() >= TRAIL_STEP) {
			trail.add(0, root);
		}
		double length = 0;
		V prev = root;
		for (int i = 0; i < trail.size(); i++) {
			length += trail.get(i).sub(prev).len();
			prev = trail.get(i);
			if (length > TRAIL_LENGTH) {
				trail.subList(i + 1, trail.size()).clear();
				break;
			}
		}
		double still = 1 - Math.clamp(speed / 0.6, 0, 1);
		double rate = still * (0.15 + 3 * Math.clamp(Math.abs(headingRate) / 90, 0, 1));
		// Turned round where there was no room to go round (at the end of a narrow landing), the way
		// it came lies in front of it now: that swings round behind it as it sets off, moving or not.
		if (trail.size() > 3 && trail.get(3).sub(root).reject(up).dot(back) < 0) {
			rate = Math.max(rate, 2.5);
		}
		if (rate > 1e-3) {
			double k = 1 - Math.exp(-rate * dt), arc = 0;
			prev = root;
			for (int i = 0; i < trail.size(); i++) {
				V p = trail.get(i);
				arc += p.sub(prev).len();
				prev = p;
				V moved = p.lerp(root.add(back.mul(arc)).add(up.mul(p.sub(root).dot(up))), k);
				// Not through a wall, though: that part stays where it was.
				if (!solids.hits(moved.add(up.mul(0.7)), 0.3)) {
					trail.set(i, moved);
				}
			}
		}
	}

	private void straighten(V back) {
		trail.clear();
		for (int k = 1; k * TRAIL_STEP <= TRAIL_LENGTH; k++) {
			trail.add(root.add(back.mul(k * TRAIL_STEP)));
		}
	}

	/**
	 * Where its path goes next (from the server, one to five blocks on), eased so a new path doesn't
	 * jerk the head round; past its end, or with no path, straight on the way it faces.
	 */
	private void ahead(RaukEntity e, V up, boolean restart, double dt) {
		java.util.List<Vec3> synced = new java.util.ArrayList<>(e.pathAhead());
		// One right on top of it, or one the line would turn right back at to go on (just round a
		// surface's edge it has got a little past the line): the line starts at the next.
		while (!synced.isEmpty()) {
			V p0 = new V(synced.get(0).x, synced.get(0).y, synced.get(0).z), to = p0.sub(root);
			boolean back = synced.size() > 1 && to.dot(new V(synced.get(1).x, synced.get(1).y, synced.get(1).z).sub(p0)) < 0;
			if (to.len() >= 0.3 && !back) {
				break;
			}
			synced.remove(0);
		}
		V last = root, dir = forward.reject(up);
		dir = dir.len() > 1e-4 ? dir.norm() : V.Z;
		for (int k = 0; k < AHEAD; k++) {
			V target;
			if (k < synced.size()) {
				Vec3 p = synced.get(k);
				target = new V(p.x, p.y, p.z);
				V d = target.sub(last);
				if (d.len() > 0.05) {
					dir = d.norm();
				}
			} else {
				target = last.add(dir);
			}
			if (restart || ahead[k] == null) {
				aheadSpring[k].reset(target);
				ahead[k] = target;
			} else {
				ahead[k] = aheadSpring[k].update(target, 6, dt);
			}
			last = target;
		}
	}

	/**
	 * The line this frame in body space, from behind the tail to ahead of the head (design doc
	 * 5.12): in three dimensions, so it goes back down the wall it has just climbed and on over the
	 * lip onto the floor ahead. Kept to what a spine can bend to, and given its way round at every
	 * point.
	 */
	private void curve(Xform toWorld) {
		int nb = Math.min(trail.size(), CURVE - AHEAD - 1);
		cn = nb + 1 + AHEAD;
		double arc = 0;
		V prev = V.ZERO;
		for (int i = 0; i < nb; i++) {
			V l = toWorld.toLocal(trail.get(i));
			arc += l.sub(prev).len();
			int j = nb - 1 - i;
			cs[j] = -arc;
			cx[j] = l.x();
			cy[j] = l.y();
			cz[j] = l.z();
			prev = l;
		}
		cs[nb] = 0;
		cx[nb] = 0;
		cy[nb] = 0;
		cz[nb] = 0;
		arc = 0;
		prev = V.ZERO;
		for (int k = 0; k < AHEAD; k++) {
			V l = toWorld.toLocal(ahead[k]);
			arc += l.sub(prev).len();
			int j = nb + 1 + k;
			cs[j] = arc;
			cx[j] = l.x();
			cy[j] = l.y();
			cz[j] = l.z();
			prev = l;
		}
		double w = pathBody ? bendWeight : 0;
		for (int j = 0; j < cn; j++) {
			cx[j] *= w;
			cy[j] *= w;
			cz[j] = cs[j] + (cz[j] - cs[j]) * w;
		}
		smoothHeights(nb);
		limitBend(nb);
		keepLineOut(toWorld, nb);
		frames(nb);
	}

	private V pt(int j) {
		return new V(cx[j], cy[j], cz[j]);
	}

	/** How far along the line its height is evened out, each way: a stair's steps become a ramp. */
	private static final double SMOOTH = 0.7;
	private final double[] smoothed = new double[CURVE];

	/**
	 * Evens out the line's height off the surface it holds over about a block and a half: where the
	 * root has been goes up a stair in sudden half-block steps, and a body laid along that would rock
	 * back and forth at every one. Then its length is measured again, along the ramp.
	 */
	private void smoothHeights(int root) {
		for (int j = 0; j < cn; j++) {
			if (j == root) {
				smoothed[j] = 0;
				continue;
			}
			double sum = 0, weights = 0;
			for (int i = 0; i < cn; i++) {
				double d = Math.abs(cs[i] - cs[j]);
				if (d < SMOOTH) {
					double wt = 1 - d / SMOOTH;
					sum += wt * cy[i];
					weights += wt;
				}
			}
			smoothed[j] = weights > 0 ? sum / weights : cy[j];
		}
		System.arraycopy(smoothed, 0, cy, 0, cn);
		cs[root] = 0;
		for (int j = root + 1; j < cn; j++) {
			cs[j] = cs[j - 1] + pt(j).sub(pt(j - 1)).len();
		}
		for (int j = root - 1; j >= 0; j--) {
			cs[j] = cs[j + 1] - pt(j).sub(pt(j + 1)).len();
		}
	}

	private void setPt(int j, V p) {
		cx[j] = p.x();
		cy[j] = p.y();
		cz[j] = p.z();
	}

	/**
	 * Keeps the line out of blocks (design doc 5.13): evened out over a lip or a stair, or swung
	 * round on the spot, a stretch of it can cut through the corner of a block, and everything laid
	 * along it would follow. Each point of it that is in one is moved out by the nearest way.
	 */
	private void keepLineOut(Xform toWorld, int root) {
		for (int j = 0; j < cn; j++) {
			if (j == root) {
				continue;
			}
			V w = toWorld.point(pt(j));
			if (solids.inside(w, 0.03)) {
				V out = solids.escape(w, 0.05, 1.0);
				if (out != w) {
					setPt(j, toWorld.toLocal(out));
				}
			}
		}
	}

	/**
	 * The most the line may turn, per block along it (design doc 5.12): side to side no more than a
	 * spine bends; over an edge (up or down) much more, the chest folding over a lip.
	 */
	private static final double MAX_BEND = Math.toRadians(35), MAX_PITCH = Math.toRadians(80);

	/** The line as it came, before it was kept to what a spine can bend to. */
	private final double[] ox = new double[CURVE], oy = new double[CURVE], oz = new double[CURVE];

	/**
	 * Keeps the line to what a spine can bend to (design doc 5.12): out from the root both ways,
	 * each point goes its own length on from the one before toward where it was, turning no more
	 * sharply than that. So the line curves round as tightly as it can and comes back onto its way
	 * after: on a U-turn the head leads round as far as the neck and chest can follow and the body
	 * flows after it, never folding double at the chest; over an edge it bends round in a tight
	 * curve and lies down on the floor beyond, never kinked.
	 */
	private void limitBend(int root) {
		for (int j = 0; j < cn; j++) {
			ox[j] = cx[j];
			oy[j] = cy[j];
			oz[j] = cz[j];
		}
		for (int dir : new int[] {1, -1}) {
			V heading = dir > 0 ? V.Z : V.Z.mul(-1);
			V n = V.Y;
			for (int j = root; j + dir >= 0 && j + dir < cn; j += dir) {
				int k = j + dir;
				V was = new V(ox[k], oy[k], oz[k]);
				double len = was.sub(new V(ox[j], oy[j], oz[j])).len();
				V v = was.sub(pt(j));
				if (len < 1e-6 || v.len() < 1e-6) {
					setPt(k, pt(j).add(heading.mul(len)));
					continue;
				}
				V vn = v.norm();
				V side = n.cross(heading);
				double a = vn.dot(heading), b = vn.dot(side), c = vn.dot(n);
				// Straight round toward it, as far as it can: over an edge (the lip it climbs out
				// over), or round a bend ahead.
				V over = vn;
				V axis = heading.cross(vn);
				if (axis.len() > 1e-6) {
					axis = axis.norm();
					double aside = axis.dot(n) * axis.dot(n);
					double max = (MAX_BEND * aside + MAX_PITCH * (1 - aside)) * len;
					if (Math.acos(Math.clamp(a, -1, 1)) > max) {
						over = heading.rotate(axis, max);
					}
				}
				// Round aside to it, as far as it can, not up or down till it faces it: a way that
				// goes back behind it (turning round at the top of a stair, the head comes round
				// level first; straight round would go through the floor).
				V round = vn;
				double yaw = Math.atan2(b, a), pitch = Math.atan2(c, Math.hypot(a, b));
				double maxYaw = MAX_BEND * len, maxPitch = MAX_PITCH * len;
				if (Math.abs(yaw) > maxYaw || Math.abs(pitch) > maxPitch) {
					double held = Math.abs(yaw) - maxYaw;
					if (held > 0) {
						pitch *= Math.max(0, Math.cos(Math.min(held, Math.PI / 2)));
					}
					yaw = Math.clamp(yaw, -maxYaw, maxYaw);
					pitch = Math.clamp(pitch, -maxPitch, maxPitch);
					round = heading.mul(Math.cos(yaw)).add(side.mul(Math.sin(yaw))).mul(Math.cos(pitch)).add(n.mul(Math.sin(pitch)));
				}
				// Ahead, or more below or above it than behind it, it goes straight round; more
				// behind it, round aside.
				double g = a >= 0 ? 1 : Math.clamp((Math.abs(c) - Math.abs(a)) / 0.4 + 0.5, 0, 1);
				vn = g >= 1 ? over : g <= 0 ? round : round.lerp(over, g).norm();
				setPt(k, pt(j).add(vn.mul(len)));
				n = carry(n, heading, vn);
				heading = vn;
			}
		}
	}

	/** The way the body is turned at each point of the line: along it, and up off the surface it carries round with it. */
	private final Quat[] cq = new Quat[CURVE];

	/**
	 * Each point's way round: along the line, with "up" carried from the root's up along it without
	 * twisting, so where the line goes over an edge the body's up turns over with it.
	 */
	private void frames(int root) {
		V[] t = new V[cn];
		for (int j = 0; j < cn; j++) {
			V d = pt(Math.min(cn - 1, j + 1)).sub(pt(Math.max(0, j - 1)));
			t[j] = d.len() > 1e-6 ? d.norm() : V.Z;
		}
		V n0 = Quat.fromTo(V.Z, t[root]).rotate(V.Y).reject(t[root]);
		n0 = n0.len() < 1e-3 ? V.X.cross(t[root]).norm() : n0.norm();
		cq[root] = along(t[root], n0);
		V n = n0;
		for (int j = root + 1; j < cn; j++) {
			n = carry(n, t[j - 1], t[j]);
			cq[j] = along(t[j], n);
		}
		n = n0;
		for (int j = root - 1; j >= 0; j--) {
			n = carry(n, t[j + 1], t[j]);
			cq[j] = along(t[j], n);
		}
	}

	private static V carry(V n, V from, V to) {
		V m = Quat.fromTo(from, to).rotate(n).reject(to);
		return m.len() < 1e-6 ? V.Y.reject(to).norm() : m.norm();
	}

	private static Quat along(V t, V n) {
		return Quat.fromAxes(n.cross(t).norm(), n, t);
	}

	/** The point on the line s along it (negative behind the root); past either end, on along it. */
	private V curveAt(double s) {
		if (cn < 2) {
			return new V(0, 0, s);
		}
		if (s <= cs[0]) {
			return extend(0, Math.min(cn - 1, 3), s);
		}
		if (s >= cs[cn - 1]) {
			return extend(cn - 1, cn - 2, s);
		}
		int lo = segment(s), hi = lo + 1;
		double span = cs[hi] - cs[lo], t = span < 1e-9 ? 0 : (s - cs[lo]) / span;
		return pt(lo).lerp(pt(hi), t);
	}

	/** The index of the point the stretch of the line holding s starts at. */
	private int segment(double s) {
		int lo = 0, hi = cn - 1;
		while (hi - lo > 1) {
			int mid = (lo + hi) >>> 1;
			if (cs[mid] <= s) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return lo;
	}

	private V extend(int end, int other, double s) {
		V d = pt(end).sub(pt(other));
		double len = d.len();
		if (len < 1e-6) {
			d = new V(0, 0, end == 0 ? -1 : 1);
			len = 1;
		}
		return pt(end).add(d.mul(Math.abs(s - cs[end]) / len));
	}

	/** How the body is turned at s along the line, from straight. */
	private Quat rotAt(double s) {
		if (cn < 2 || cq[0] == null) {
			return Quat.IDENTITY;
		}
		if (s <= cs[0]) {
			return cq[0];
		}
		if (s >= cs[cn - 1]) {
			return cq[cn - 1];
		}
		int lo = segment(s), hi = lo + 1;
		double span = cs[hi] - cs[lo], t = span < 1e-9 ? 0 : (s - cs[lo]) / span;
		return Quat.slerp(cq[lo], cq[hi], t);
	}

	/** The way the line runs at s, as a turn about the body's up from straight ahead (radians). */
	private double yawAt(double s) {
		V d = rotAt(s).rotate(V.Z);
		return Math.atan2(d.x(), d.z());
	}

	/** A point of the straight body (body space) where it is on the bent one. */
	private V bendPoint(V p) {
		return curveAt(p.z()).add(rotAt(p.z()).rotate(new V(p.x(), p.y(), 0)));
	}

	/**
	 * The trunk bent onto the line, joint by joint; what hangs off it goes along with it. The chest
	 * stays whole, as a ribcage does: it is laid as one piece across the line from where its back
	 * end is on it to where its front end is, so it joins the waist and the neck where they are;
	 * the bending is done in the waist and the neck (over a lip, round a U-turn), and its ribs never
	 * fan apart.
	 */
	private void bendTrunk() {
		for (int j : trunk) {
			straight[j] = body[j];
		}
		double rear = Double.MAX_VALUE, front = -Double.MAX_VALUE;
		for (int t = 1; t < trunk.length; t++) {
			if (sk.chest[t - 1]) {
				double z = straight[trunk[t]].o().z();
				rear = Math.min(rear, z);
				front = Math.max(front, z);
			}
		}
		Xform chest = null;
		V chestMid = V.ZERO;
		if (front > rear) {
			double mid = (rear + front) / 2;
			chestMid = new V(0, 0, mid);
			V back = curveAt(rear), ahead = curveAt(front), along = ahead.sub(back);
			if (along.len() > 1e-6) {
				along = along.norm();
				V up = rotAt(mid).rotate(V.Y).reject(along);
				up = up.len() > 1e-6 ? up.norm() : rotAt(mid).rotate(V.Y);
				chest = new Xform(back.add(ahead).mul(0.5), Quat.fromAxes(up.cross(along).norm(), up, along));
			}
		}
		for (int t = 0; t < trunk.length; t++) {
			int j = trunk[t];
			Xform x = straight[j];
			if (chest != null && t > 0 && sk.chest[t - 1]) {
				body[j] = new Xform(chest.point(x.o().sub(chestMid)), chest.r().mul(x.r()));
			} else {
				body[j] = new Xform(bendPoint(x.o()), rotAt(x.o().z()).mul(x.r()));
			}
		}
		// The waist never stretches: over an edge (the top of a stair, a lip) the back is on the
		// outside of the bend, where the line laid along the ground would pull its vertebrae apart;
		// from the chest back, each is kept within its length of the one before, and the hips come
		// in after them.
		int firstChest = -1;
		for (int t = 1; t < trunk.length && firstChest < 0; t++) {
			if (sk.chest[t - 1]) {
				firstChest = t;
			}
		}
		for (int t = firstChest - 1; t >= 0 && firstChest > 0; t--) {
			V from = body[trunk[t + 1]].o(), d = body[trunk[t]].o().sub(from);
			double rest = straight[trunk[t]].o().sub(straight[trunk[t + 1]].o()).len();
			if (d.len() > rest * 1.05) {
				body[trunk[t]] = new Xform(from.add(d.norm().mul(rest * 1.05)), body[trunk[t]].r());
			}
		}
		for (int j : trunk) {
			local[j] = sk.parent[j] < 0 ? body[j] : body[sk.parent[j]].toLocal(body[j]);
		}
	}

	/**
	 * Keeps the skull (and the jaw hanging under it) out of walls: where the neck would carry it,
	 * unless that is in something; then as far toward it from the way ahead as it can go. Eased, so
	 * it slides off a wall rather than jumping, but never left in one.
	 */
	private V clearHead(Xform toWorld, V reach, V safe, double dt) {
		if (!pathBody) {
			return reach;
		}
		V want = toWorld.point(reach), from = safeSkull(toWorld, safe);
		V jaw = toWorld.dir(body[sk.head].r().rotate(JAW_BALL));
		V at = solids.clear(from, want, SKULL_RADIUS);
		at = solids.clear(from.add(jaw), at.add(jaw), JAW_RADIUS).sub(jaw);
		V push = toWorld.r().conj().rotate(at.sub(want));
		V eased = headPush.update(push, 10, dt);
		if (solids.hits(toWorld.point(reach.add(eased)), SKULL_RADIUS * 0.9)) {
			headPush.reset(push);
			eased = push;
		}
		return reach.add(eased);
	}

	/**
	 * Moving, the loose part of the tail is drawn across onto the trail, so it follows the body
	 * round corners instead of cutting across them, and down a stair or a wall the way the body
	 * went instead of standing up off it. Only across: along the way it drags as it would.
	 */
	private void tailFollow(V up, double follow, double h) {
		int n = sk.tail.length, seg = 0, segs = trail.size();
		for (int k = 1; k <= n; k++) {
			double w = follow * smooth(Math.clamp((k / (double) n - 0.05) / 0.2, 0, 1));
			if (w <= 0) {
				continue;
			}
			V p = tail[k], best = null, along = up;
			double nearest = Double.MAX_VALUE;
			int bestSeg = seg;
			for (int i = Math.max(0, seg - 2); i < Math.min(segs, seg + 25); i++) {
				V a = i == 0 ? root : trail.get(i - 1), b = trail.get(i);
				V ab = b.sub(a);
				double l2 = ab.dot(ab);
				double t = l2 < 1e-9 ? 0 : Math.clamp(p.sub(a).dot(ab) / l2, 0, 1);
				V c = a.add(ab.mul(t));
				double d = c.sub(p).len();
				if (d < nearest) {
					nearest = d;
					best = c;
					bestSeg = i;
					along = l2 < 1e-9 ? up : ab.norm();
				}
			}
			if (best == null) {
				continue;
			}
			seg = bestSeg;
			V move = best.sub(p).reject(along).mul(1 - Math.exp(-14 * w * h));
			tail[k] = p.add(move);
			tailPrev[k] = tailPrev[k].add(move);
		}
	}

	// --- springs ------------------------------------------------------------------------------

	/** A critically damped spring: follows its target smoothly with no overshoot. */
	static final class Spring {
		double x, v;

		void reset(double value) {
			x = value;
			v = 0;
		}

		double update(double target, double hz, double dt) {
			double w = 2 * Math.PI * hz, y = x - target, e = Math.exp(-w * dt), c = v + w * y;
			x = target + (y + c * dt) * e;
			v = (v - w * c * dt) * e;
			return x;
		}

		/** The same for an angle in degrees, taking the short way round. */
		double updateAngle(double target, double hz, double dt) {
			return update(x + Mth.wrapDegrees(target - x), hz, dt);
		}
	}

	static final class VSpring {
		private final Spring x = new Spring(), y = new Spring(), z = new Spring();

		V x() {
			return new V(x.x, y.x, z.x);
		}

		void reset(V v) {
			x.reset(v.x());
			y.reset(v.y());
			z.reset(v.z());
		}

		V update(V target, double hz, double dt) {
			return new V(x.update(target.x(), hz, dt), y.update(target.y(), hz, dt), z.update(target.z(), hz, dt));
		}
	}
}

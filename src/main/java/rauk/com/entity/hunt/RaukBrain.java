package rauk.com.entity.hunt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import rauk.com.Rauk;
import rauk.com.block.BloodBlock;
import rauk.com.block.hotel.HotelDoorBlock;
import rauk.com.entity.RaukEntity;
import rauk.com.entity.RaukPosture;
import rauk.com.entity.combat.RaukCombat;
import rauk.com.entity.mind.Belief;
import rauk.com.entity.mind.PlayerProfile;
import rauk.com.entity.mind.Prey;
import rauk.com.entity.mind.RaukMind;
import rauk.com.entity.mind.ScentData;
import rauk.com.entity.mind.Territory;
import rauk.com.entity.move.RaukPathfinder;
import rauk.com.entity.move.SurfaceNode;
import rauk.com.hotel.HotelGraph;
import rauk.com.hotel.HotelHeat;
import rauk.com.hotel.LastWaltz;
import rauk.com.hotel.LockdownRelease;
import rauk.com.sound.RaukSounds;

/**
 * Rauk's hunt (design doc 6.1–6.4): it re-scores what to do a few times a second, from what it
 * senses and remembers, and does it.
 *
 * <ul>
 * <li><b>Rest / Patrol</b>: nothing to go on. It rests in its den when fed, otherwise moves through
 * its hotel almost silently, often along walls and ceilings.</li>
 * <li><b>Investigate / Track / Search</b>: a noise, a change, a scent trail, or a rough idea which
 * rooms someone is in. Searching, it goes door to door: listens at each, sniffs the gap, knocks or
 * flicks the lights and listens again.</li>
 * <li><b>Hunt or play</b>: each time it finds a prey it decides how to go about it. Mostly it hunts:
 * it closes in without stopping, watched or not, and strikes as soon as it is close enough. Now and
 * then (less when hungry, never with someone who has got away from it before) it plays first: it
 * keeps its distance, holds still when watched from afar, shows itself or peeks round a corner,
 * melts away when the prey comes close and turns up where the prey is going, and strikes the
 * moment the prey gives it an opening. The game doesn't last: after a minute or so, or the moment
 * the prey strikes it, it hunts.</li>
 * <li><b>Predicting</b>: it reckons where the prey is going from where it has found it most, the
 * bolt-holes it ran to before, the doors ahead and which way it is running ({@link Prediction}),
 * and cuts it off when it can get there first.</li>
 * <li><b>Stalk</b>: it knows where the prey is: it goes for the ceiling above where the prey is
 * heading, switches lights off on its way and knocks down lanterns. Hunting it doesn't stop;
 * playing it keeps its distance.</li>
 * <li><b>Probe</b> (playing): it lets the prey see it, at the edge of the light and risen to its
 * full height in a big room; in a corridor only its head comes round a corner. Running sets off the
 * chase; anything else, and it withdraws and comes back unseen.</li>
 * <li><b>Commit / Strike</b>: it holds its breath and goes, by leap from a wall or ceiling, or at a
 * gallop, and grabs. If it misses it comes straight again.</li>
 * <li><b>Flank</b>: armed prey that faces it it goes round for a moment, to its side or back; then
 * it stops respecting the standoff and takes it head on.</li>
 * <li><b>Reach</b>: prey it can't get to but can almost touch (in a vent, up on a gallery) gets
 * one long arm, and is dragged out.</li>
 * <li><b>Feed</b>: a kill goes to one of its larders, dragged and leaving a smear; it feeds there
 * and leaves the remains.</li>
 * <li><b>Chase / Intercept</b>: running prey is chased at a gallop, leading it, until the burst
 * runs out; then it goes quiet and heads for where the prey is going (its room, or along its
 * heading) and waits there.</li>
 * <li><b>Ambush</b>: it waits flat on the ceiling where the prey will come: its room, its route,
 * having put the lights on there first.</li>
 * <li><b>Disengage</b>: away from a flame in the prey's hand, or out of sight to come back another
 * way. Nothing hurts it, so a drawn bow doesn't.</li>
 * </ul>
 *
 * Drives shape all of it (6.2): hunger rises over time and makes it bolder; caution rises when it
 * is watched and makes it hide more.
 */
public final class RaukBrain extends Goal {
	public enum Intent {
		REST, PATROL, INVESTIGATE, TRACK, SEARCH, STALK, AMBUSH, PROBE, COMMIT, STRIKE, CHASE, INTERCEPT, DISENGAGE,
		GRAB, FLANK, REACH, FEED,
		/** Stood still at the edge of the music, listening, for as long as it plays (hotel design 4.3). */
		LISTEN
	}

	private static final double CREEP = 1.4, STALK_SPEED = 2.2, PROWL = 3.0, HURRY = 5.0, GALLOP = 12.0;

	private final RaukEntity rauk;
	public final Lights lights;
	private Intent intent = Intent.PATROL;
	private long intentStart;
	private boolean done;
	@Nullable
	private UUID target;

	/** Drives (design doc 6.2), 0 to 1. */
	public double hunger = 0.55, caution = 0.1;

	private long probeReady, strikeReady, ambushReady, nextLights, chaseEnded = -100_000, reachReady;

	/** How it is going about this prey: hunting it down now, or playing with it first. */
	public enum Mood { HUNT, PLAY }

	private Mood mood = Mood.HUNT;
	/** Whether, this encounter, it cuts the prey off when it can (mostly), or simply runs it down. */
	private boolean cutsOff = true;
	/** Whom the mood is for, until when it plays, and when the prey was last in sight. */
	@Nullable
	private UUID moodFor;
	private long playUntil, lastSeenTime = -100_000, hauntChecked = -100_000;
	/** The prey as last seen, to learn where it bolts to when it gets out of sight. */
	private boolean wasSeen;
	private Vec3 lastSeenAt = Vec3.ZERO, lastSeenVelocity = Vec3.ZERO;
	/** Where it has worked out the prey is going, for an intercept. */
	@Nullable
	private Prediction.Guess interceptAt;
	/** Rooms it uses as larders (design doc 6.7), up to three. */
	private final List<BlockPos> larders = new ArrayList<>();
	private int noticesSeen = Integer.MIN_VALUE, freeze;

	// The current intent's working state.
	@Nullable
	private Vec3 spot;
	@Nullable
	private Direction spotSurface;
	private String spotLabel = "";
	private int phase, timer;
	@Nullable
	private BlockPos errand;
	private boolean errandOn;
	private final List<BlockPos> doors = new ArrayList<>();
	private int doorIndex;
	private double probeStart;
	private long lastPlan;
	@Nullable
	private Vec3 planned;
	private long lastTap = -1000;
	private boolean tapped;
	@Nullable
	private BlockPos flushLever;

	public RaukBrain(RaukEntity rauk) {
		this.rauk = rauk;
		this.lights = new Lights(rauk);
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return rauk.brainEnabled();
	}

	@Override
	public boolean canContinueToUse() {
		return rauk.brainEnabled();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	/** Switched on: it starts from a clean slate and decides afresh. */
	@Override
	public void start() {
		long now = rauk.level().getGameTime();
		if (now - intentStart > 5) {
			// Unless it was just told what to do (a test command), it decides afresh.
			intent = Intent.PATROL;
			done = true;
		}
		noticesSeen = Integer.MIN_VALUE;
		caution = 0.1;
	}

	@Override
	public void stop() {
		rauk.setRisen(false);
		rauk.setLookPoint(null);
		rauk.mover().setPaused(false);
		rauk.mind().setListening(false);
		rauk.stopWalking();
		rauk.mind().tacticSpot = null;
	}

	public Intent intent() {
		return intent;
	}

	/**
	 * Struck (it does nothing to it, design doc 6.6). It turns on whoever did it and grows warier,
	 * and remembers the place. Fire makes it back off; struck again and again by something it can't
	 * get at (an archer up on a gallery), it takes cover rather than stand there.
	 */
	public void struck(DamageSource source) {
		long now = rauk.level().getGameTime();
		caution = Math.min(1, caution + 0.04);
		Entity attacker = source.getEntity();
		if (attacker instanceof LivingEntity living && Prey.is(living)) {
			target = attacker.getUUID();
			rauk.mind().belief(target).observe(attacker.position(), null, 1.5, 0.8, Belief.Sense.TOUCH, now);
		}
		rauk.mind().dangerAt(rauk.position(), 10);
		// Whatever game it was playing is over.
		mood = Mood.HUNT;
		if (rauk.combat().holding() || intent == Intent.GRAB) {
			return;
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			begin(Intent.DISENGAGE, now);
			return;
		}
		if (attacker instanceof LivingEntity living && !rauk.combat().reach(living)) {
			unanswered += 10;
			if (unanswered > 40 && intent != Intent.DISENGAGE) {
				unanswered = 0;
				strikeReady = now + 200;
				begin(Intent.DISENGAGE, now);
			}
		}
	}

	/** Blows from something it couldn't reach, fading over a few seconds. */
	private double unanswered;

	/** Forgets what it was doing and its timers (test command), keeping its drives and lights. */
	public void reset() {
		probeReady = strikeReady = ambushReady = nextLights = reachReady = 0;
		larders.clear();
		chaseEnded = -100_000;
		frozen = 0;
		target = null;
		moodFor = null;
		lastSeenTime = hauntChecked = -100_000;
		interceptAt = null;
		intent = Intent.PATROL;
		done = true;
	}

	// --- every tick ---------------------------------------------------------------------------

	@Override
	public void tick() {
		if (!rauk.brainEnabled()) {
			return;
		}
		ServerLevel level = (ServerLevel) rauk.level();
		long now = level.getGameTime();
		RaukMind mind = rauk.mind();
		hunger = Math.min(1, hunger + 1 / 24000.0);
		caution = Math.max(0.05, caution - 0.0002);
		unanswered *= 0.995;
		lights.tick(level, now);

		// Detect: something new makes it freeze a moment while it takes it in.
		int notices = rauk.noticeCount();
		if (notices != noticesSeen) {
			if (noticesSeen != Integer.MIN_VALUE && calm()) {
				freeze = 14;
			}
			noticesSeen = notices;
		}
		watchPrey(level);
		if (rauk.tickCount % 20 == 0) {
			// Looked into: the heat map knows it was here (design doc 6.5).
			HotelHeat.get(level).checked(HotelHeat.area(level, rauk.position()), now);
		}
		if (rauk.tickCount % 10 == 0 || done) {
			choose(level, now);
		}
		if (freeze > 0) {
			freeze--;
			rauk.mover().setPaused(true);
			status(level, now);
			return;
		}
		rauk.mover().setPaused(false);
		run(level, now);
		watchdog(level, now);
		status(level, now);
	}

	/** When it last asked to go somewhere, and where and since when it has been standing. */
	private long lastGo = -100, stillSince;
	private Vec3 stillAt = Vec3.ZERO;

	/**
	 * Wanting to go somewhere and getting nowhere for four seconds (no way there, or no way it can
	 * find, after the mover's own tries at another way): it gives that up and does something else,
	 * rather than stand there.
	 */
	private void watchdog(ServerLevel level, long now) {
		boolean wants = lastGo >= now - 2 && !rauk.mover().isLeaping();
		if (!wants || rauk.position().distanceTo(stillAt) > 0.6) {
			stillAt = rauk.position();
			stillSince = now;
			return;
		}
		if (now - stillSince < 80) {
			return;
		}
		stillSince = now;
		if (DEBUG) {
			Rauk.LOGGER.info("watchdog: {} going nowhere at {} (spot {})", intent, rauk.blockPosition().toShortString(), spot);
		}
		Belief b = target == null ? null : rauk.mind().beliefs().get(target);
		switch (intent) {
			case STALK -> {
				if (spotSurface == Direction.UP && !noCeiling) {
					noCeiling = true;
				} else {
					if (b != null) {
						b.confidence *= 0.6;
					}
					done = true;
				}
			}
			case PATROL -> spot = null;
			case INVESTIGATE, TRACK -> {
				if (spot != null) {
					rauk.mind().forgetNoisesNear(spot, 4);
				}
				if (b != null) {
					b.confidence *= 0.6;
				}
				done = true;
			}
			case SEARCH -> {
				doorIndex++;
				phase = 0;
				timer = 0;
			}
			case INTERCEPT -> ambushAt(rauk.position(), now);
			case AMBUSH -> {
				if (errand != null) {
					errand = null;
				} else if (phase == 1) {
					phase = 2;
					timer = 0;
				}
			}
			case FEED -> timer = Math.max(timer, 1201);
			case PROBE -> timer = Math.max(timer, 301);
			case REACH -> {
				if (spot != null) {
					tried.add(BlockPos.containing(spot));
				}
				spot = null;
			}
			default -> {
				if (errand != null) {
					errand = null;
				} else {
					done = true;
				}
			}
		}
	}

	/** Sets how it goes about its prey (test command). */
	public void setMood(Mood mood) {
		this.mood = mood;
		moodSet = true;
		// Told how to go about it, it does it the surest way: cutting off whenever it can.
		cutsOff = true;
		playUntil = rauk.level().getGameTime() + 20 * 70;
	}

	/** Told how to go about the next prey (test command), instead of deciding for itself. */
	private boolean moodSet;

	/**
	 * Keeps track of the prey in sight; when it ran and got out of sight, the place it was making
	 * for is a bolt-hole it remembers (design doc 6.5).
	 */
	private void watchPrey(ServerLevel level) {
		LivingEntity t = targetEntity(level);
		boolean seen = t != null && rauk.mind().sees(target);
		if (seen) {
			lastSeenAt = t.position();
			lastSeenVelocity = rauk.mind().velocity(t).scale(20);
		} else if (wasSeen && target != null && lastSeenVelocity.horizontalDistance() > 3
				&& lastSeenVelocity.dot(lastSeenAt.subtract(rauk.position())) > 0) {
			rauk.mind().profile(target).fledTo(BlockPos.containing(lastSeenAt.add(lastSeenVelocity.scale(1.2))));
		}
		wasSeen = seen;
	}

	/**
	 * A new encounter (a different prey, or the same after a minute out of sight): hunt it now, or
	 * play with it first. Hungrier, it plays less; with prey that is alone or weak, less; with prey
	 * that has got away from it before, never. A game lasts about half a minute to a minute and a
	 * half, then it hunts.
	 */
	private void decideMood(PreyRead read, long now) {
		if (LockdownRelease.alarmed(rauk.level())) {
			// The lockdown lifted, the alarm ringing, the doors about to open: no games now (hotel design 4.6).
			mood = Mood.HUNT;
			moodFor = target;
			return;
		}
		if (moodSet) {
			moodSet = false;
			moodFor = target;
		} else if (!target.equals(moodFor) || now - lastSeenTime > 1200) {
			// Mostly it hunts, fed or not (playtest, 2026-10-06: it was too cautious); now and then,
			// when it isn't hungry, it plays first, briefly.
			moodFor = target;
			double kill = 0.72 + 0.4 * hunger + (read.vulnerable() ? 0.1 : 0) + (read.isolated() ? 0.05 : 0)
					+ (rauk.mind().profile(target).escapes() > 0 ? 1 : 0);
			mood = rauk.getRandom().nextDouble() < kill ? Mood.HUNT : Mood.PLAY;
			playUntil = now + 20 * (20 + rauk.getRandom().nextInt(25));
			cutsOff = rauk.getRandom().nextDouble() < 0.85;
		}
		lastSeenTime = now;
		if (mood == Mood.PLAY && (now > playUntil || hunger > 0.85)) {
			mood = Mood.HUNT;
		}
	}

	/** Hunting, how far away it simply goes for the prey: further when hungrier. Leaps carry it twelve. */
	private double killRange() {
		return 10 + 4 * hunger;
	}

	/** Playing, whether the prey has given it an opening: its back turned within a leap, or busy close by. */
	private boolean opening(PreyRead read, double d) {
		return (!read.lookingAt() && d < 8) || (read.busy() && d < commitRange() + 2);
	}

	/** How fast it gets about on the way to cut someone off, on average, where there is no hotel map. */
	private static final double RAUK_PACE = 8.0;

	/**
	 * Running prey: hunting, it cuts it off if it can get where the prey is going first, rather
	 * than run behind it ({@link CutOff}): in the hotel it reckons the prey's way on foot (the
	 * stairs, the doors) against its own by any way it can climb, never through the space the prey
	 * is in. Someone running up a stairwell it beats to the top by the shaft next door, and is
	 * waiting by the door when they come out. Playing, it stalks instead; and now and then, hunting,
	 * it simply runs them down.
	 */
	private boolean cutOff(ServerLevel level, LivingEntity t) {
		if (mood != Mood.HUNT || !cutsOff) {
			return false;
		}
		Vec3 v = rauk.mind().velocity(t).scale(20);
		CutOff.Plan p = CutOff.plan(level, rauk, target, t.position(), v, 0);
		if (p != null) {
			if (DEBUG) {
				Rauk.LOGGER.info("cut off at {} {}: theirs {}s, mine {}s", p.why(), BlockPos.containing(p.at()).toShortString(),
						String.format("%.1f", p.theirs()), String.format("%.1f", p.mine()));
			}
			interceptAt = new Prediction.Guess(p.at(), p.why(), p.score(), p.shun());
			interceptShun = p.shun();
			return true;
		}
		if (HotelGraph.get(level) != null) {
			return false;
		}
		// No hotel here: straight lines will do.
		Prediction.Guess g = Prediction.destination(level, rauk, target, t.position(), v);
		if (g == null) {
			return false;
		}
		double distMine = rauk.position().distanceTo(g.at()), distTheirs = t.position().distanceTo(g.at());
		if (distTheirs > 6 && distMine / RAUK_PACE < distTheirs / Math.max(3.5, v.length())) {
			interceptAt = g;
			interceptShun = Set.of();
			return true;
		}
		return false;
	}

	/**
	 * The prey ran and got out of sight: where it is making for, worked out from where and how it
	 * was last seen and how long ago, if it can be there first. Vanishing through a stairwell's door,
	 * it knows where that comes out, and goes round the other way.
	 */
	private boolean stairsAhead(ServerLevel level, Belief b) {
		double lost = (level.getGameTime() - b.lastSeen) / 20.0;
		CutOff.Plan p = CutOff.plan(level, rauk, target, b.seenAt, b.seenVelocity, lost);
		if (p != null) {
			interceptAt = new Prediction.Guess(p.at(), p.why(), p.score(), p.shun());
			interceptShun = p.shun();
			if (DEBUG) {
				Rauk.LOGGER.info("lost them running: cut off at {} {}", p.why(), BlockPos.containing(p.at()).toShortString());
			}
			return true;
		}
		Prediction.Guess g = Prediction.destination(level, rauk, target, b.seenAt, b.seenVelocity);
		if (g == null || g.through().isEmpty()) {
			g = Prediction.throughStairwell(level, b.seenAt, b.seenAt, 3);
		}
		if (g == null || g.through().isEmpty()) {
			return false;
		}
		interceptAt = g;
		interceptShun = shunFor(level, g, b.seenAt);
		if (DEBUG) {
			Rauk.LOGGER.info("gone up the stairs: cut off at {} {}", g.why(), BlockPos.containing(g.at()).toShortString());
		}
		return true;
	}

	/** Cutting off: when it last checked the prey was still making for its spot, and how far from it they were. */
	private long spotChecked;
	private double preyToSpot = -1;

	/** Cutting off, the openings of the space the prey was in when it decided: its way keeps out of there. */
	private Set<Vec3> interceptShun = Set.of();

	/**
	 * Where its way to a cut-off mustn't go: through the stairwell the prey is taking, or the space
	 * the prey is in now (unless it is in it too).
	 */
	private Set<Vec3> shunFor(ServerLevel level, Prediction.Guess g, Vec3 prey) {
		Set<Vec3> shun = new HashSet<>(g.through());
		HotelGraph graph = HotelGraph.get(level);
		int theirs = graph == null ? -1 : graph.partAt(prey);
		if (theirs >= 0 && theirs != graph.partAt(rauk.position())) {
			shun.add(prey);
		}
		return shun;
	}

	private boolean calm() {
		return switch (intent) {
			case REST, PATROL, INVESTIGATE, TRACK, SEARCH -> true;
			default -> false;
		};
	}

	// --- choosing -----------------------------------------------------------------------------

	private boolean locked(long now) {
		if (done) {
			return false;
		}
		return switch (intent) {
			case GRAB, FEED -> true;
			case COMMIT, STRIKE, DISENGAGE, FLANK, REACH -> true;
			case CHASE -> true;
			case PROBE -> phase < 3;
			case AMBUSH -> now - intentStart < ambushFor;
			default -> false;
		};
	}

	private void choose(ServerLevel level, long now) {
		RaukMind mind = rauk.mind();
		if (rauk.combat().holding() || rauk.combat().pinning()) {
			if (intent != Intent.GRAB) {
				begin(Intent.GRAB, now);
			}
			return;
		}
		pickTarget(level, now);
		if (rauk.carrying() && intent != Intent.FEED && !locked(now)) {
			// Still has a kill in its jaws (after a retreat, or the world was saved with it): it goes and feeds.
			begin(Intent.FEED, now);
			return;
		}
		Belief b = target == null ? null : mind.beliefs().get(target);
		LivingEntity t = targetEntity(level);
		boolean seen = t != null && mind.sees(target);
		if (locked(now)) {
			// Even locked, an ambush or an intercept springs when the prey comes close and isn't looking.
			if (intent == Intent.AMBUSH && seen) {
				PreyRead read = PreyRead.of(t, rauk, mind.velocity(t), mind.preys());
				decideMood(read, now);
				if (doorAmbush) {
					// Beside a door it waits for the prey to come within reach (see ambush()).
					if (read.running() && read.distance() < 16 && !rauk.combat().reach(t)) {
						begin(Intent.CHASE, now);
					}
				} else if (now >= strikeReady && (mood == Mood.HUNT ? read.distance() < killRange() : opening(read, read.distance()))) {
					begin(Intent.COMMIT, now);
				} else if (read.running() && read.distance() < 16) {
					begin(Intent.CHASE, now);
				}
			}
			return;
		}
		Intent next;
		if (seen) {
			PreyRead read = PreyRead.of(t, rauk, mind.velocity(t), mind.preys());
			double d = read.distance();
			decideMood(read, now);
			boolean hunting = mood == Mood.HUNT;
			if (read.fire() && d < 7) {
				// A flame in the prey's hand keeps it just out of reach, circling, waiting for it to go.
				next = Intent.DISENGAGE;
			} else if (d < 8 && now >= reachReady && !rauk.combat().reach(t) && (cantFollow(level, t)
					|| (rauk.mover().unreachable() && headingFor(t)) || (!rauk.mover().isMoving() && d < 6 && now - intentStart > 30))) {
				// Somewhere it can't follow (a vent, up on a gallery), or got as close as it can and
				// still can't touch it: one arm, through the gap.
				next = Intent.REACH;
			} else if (intent == Intent.INTERCEPT && d > 5 && !done) {
				// Already on its way round to cut it off: it doesn't turn back for a glimpse, only for
				// prey right in front of it; but seeing them turn off the way it reckoned (getting no
				// nearer the door it is making for), it thinks again from where they're going now.
				next = Intent.INTERCEPT;
				if (spot != null && now - spotChecked >= 20) {
					spotChecked = now;
					double toSpot = t.position().distanceTo(spot);
					boolean turned = preyToSpot >= 0 && toSpot > preyToSpot + 0.8;
					preyToSpot = toSpot;
					if (turned) {
						if (DEBUG) {
							Rauk.LOGGER.info("they turned off the way to {}: thinks again", spotLabel);
						}
						if (cutOff(level, t)) {
							begin(Intent.INTERCEPT, now);
							return;
						}
						next = Intent.CHASE;
					}
				}
			} else if (read.running() && d < 28 && !rauk.panting()) {
				// It doesn't run behind you if it knows where you're going: it gets there first.
				next = cutOff(level, t) ? Intent.INTERCEPT : Intent.CHASE;
			} else if (hunting && d > killRange() && mind.velocity(t).horizontalDistance() > 0.15 && cutOff(level, t)) {
				// Going somewhere in a hurry, not away from it: it knows where, and is there first.
				next = Intent.INTERCEPT;
			} else if (now >= strikeReady && (hunting ? d < killRange() : opening(read, d))) {
				// Hunting, close enough is enough: watched or not, it goes.
				next = Intent.COMMIT;
			} else if (!hunting && d < 9 && read.lookingAt()) {
				// Too close for the game, face to face: it melts away, to turn up where it isn't expected.
				next = Intent.DISENGAGE;
			} else if (!hunting && now >= probeReady && d > 9 && d < 26) {
				// Risen in a big room; in a corridor only its head comes round a corner. It doesn't
				// probe twice in a row: probeReady is pushed back after each one.
				next = Intent.PROBE;
			} else {
				next = Intent.STALK;
			}
		} else if (spellbound(now)) {
			// The waltz: come to the music and finding no one it can see, it stands at the edge of it
			// and listens, for as long as it plays. Not every time.
			next = Intent.LISTEN;
		} else if (intent == Intent.INTERCEPT && !done && b != null && b.secondsSince(now) < 12) {
			// On its way round to where they'll come out: it keeps going, whatever it hears or smells
			// on the way, until it gets there (and lies in wait) or sees them.
			next = Intent.INTERCEPT;
		} else if (t != null && b != null && b.confidence > 0.3 && b.secondsSince(now) < 6 && now >= reachReady
				&& rauk.position().distanceTo(b.position) < 8 && b.position.distanceTo(t.position()) < 2
				&& (cantFollow(level, t) || rauk.mover().unreachable() && headingFor(t))) {
			// It knows something is in there, close, where it can't follow: the arm goes in to feel for it.
			next = Intent.REACH;
		} else if (intent != Intent.INTERCEPT && b != null && now - b.lastSeen < 80 && b.seenVelocity.length() > 2 && stairsAhead(level, b)) {
			// Gone through a stairwell's door in a hurry: it knows where that comes out, and goes there
			// first, another way.
			next = Intent.INTERCEPT;
		} else if (b != null && b.confidence > 0.4 && b.secondsSince(now) < 12 && rauk.position().distanceTo(b.position) > 3) {
			// On its way to cut the prey off, it keeps going. (Right where it thinks the prey is and not
			// seeing it, it searches round instead.)
			next = now - chaseEnded < 300 || intent == Intent.INTERCEPT ? Intent.INTERCEPT : Intent.STALK;
		} else if (b != null && b.secondsSince(now) > 8 && b.uncertainty > 5 && now - hauntChecked > 1200 && hunger > 0.4
				&& lieInWait(level, b)) {
			// It has lost them: rather than search at random, it goes where they usually are and waits.
			hauntChecked = now;
			next = Intent.INTERCEPT;
		} else if (b != null && b.confidence > 0.12 && b.secondsSince(now) < 120) {
			if (b.uncertainty > 3 && !doorsNear(b).isEmpty()) {
				next = Intent.SEARCH;
			} else if (freshScent(now)) {
				next = Intent.TRACK;
			} else {
				next = Intent.INVESTIGATE;
			}
		} else if (mind.lastStrangeNoise(20) != null || mind.territory.next() != null) {
			next = Intent.INVESTIGATE;
		} else if (freshScent(now) && hunger > 0.25) {
			// Fed, it doesn't go looking for more.
			next = Intent.TRACK;
		} else if (hunger > 0.6 && now >= ambushReady && knownRoom() != null) {
			next = Intent.AMBUSH;
		} else {
			next = hunger < 0.3 ? Intent.REST : Intent.PATROL;
		}
		// It goes where it first meant to (design doc 4.7): on its way somewhere to look, it doesn't
		// turn aside for every new idea, only for prey it can see or a loud new noise; there it
		// decides what next, by what it finds.
		if (!seen && !done && next != intent && purposeful(intent) && purposeful(next) && now - intentStart < 600 && !startledSince(now)) {
			next = intent;
		}
		if (next != intent || done) {
			begin(next, now);
		}
	}

	/** Going somewhere to look, with a reason: what it keeps to until it gets there. */
	private static boolean purposeful(Intent i) {
		return i == Intent.INVESTIGATE || i == Intent.TRACK || i == Intent.SEARCH;
	}

	/** Whether something loud and new has been heard since it set out. */
	private boolean startledSince(long now) {
		RaukMind.Noise n = rauk.mind().lastStrangeNoise(3);
		return n != null && n.time() > intentStart && n.loudness() > 0.6;
	}

	/** Where the prey usually is, near where it was lost, set up for an intercept; false if it knows nowhere. */
	private boolean lieInWait(ServerLevel level, Belief b) {
		Prediction.Guess g = Prediction.destination(level, rauk, target, b.position, Vec3.ZERO);
		if (g == null || g.at().distanceTo(rauk.position()) > 48) {
			return false;
		}
		interceptAt = new Prediction.Guess(g.at(), "lie in wait at " + g.why(), g.score());
		return true;
	}

	private double commitRange() {
		return 4.5 + 2 * hunger - 1.5 * caution;
	}

	private static final boolean DEBUG = Boolean.getBoolean("rauk.devshot");

	private void begin(Intent next, long now) {
		RaukMind mind = rauk.mind();
		if (DEBUG && next != intent) {
			Belief b = target == null ? null : mind.beliefs().get(target);
			Rauk.LOGGER.info("intent {} -> {} at {} (belief {})", intent, next, rauk.blockPosition().toShortString(),
					b == null ? "none" : String.format("%.2f +-%.1f %s %.1fs ago at %s", b.confidence, b.uncertainty, b.lastSense,
							b.secondsSince(now), BlockPos.containing(b.position).toShortString()));
		}
		if (intent == Intent.CHASE && next != Intent.CHASE) {
			chaseEnded = now;
		}
		if (intent == Intent.FEED && next != Intent.FEED && rauk.carrying() && rauk.level() instanceof ServerLevel level) {
			// Interrupted: the kill is left where it is.
			leaveRemains(level, carcassPoint());
		}
		int action = rauk.action();
		if (action == RaukMind.ACTION_PEEK || action == RaukMind.ACTION_FEED || action == RaukMind.ACTION_REACH
				|| action == RaukMind.ACTION_LUNGE || action == RaukMind.ACTION_HOOK) {
			rauk.setAction(RaukMind.ACTION_NONE, rauk.position());
		}
		intent = next;
		intentStart = now;
		done = false;
		phase = 0;
		timer = 0;
		spot = null;
		spotSurface = null;
		spotLabel = "";
		errand = null;
		planned = null;
		tapped = false;
		flushLever = null;
		rauk.mover().setPaused(false);
		mind.setListening(false);
		if (next != Intent.PROBE) {
			rauk.setRisen(false);
		}
		followed = false;
		leaptStrike = false;
		if (next != Intent.INTERCEPT) {
			interceptAt = null;
		}
		misses = 0;
		tried.clear();
		peekHead = null;
		peekDoor = null;
		doorAmbush = false;
		noCeiling = false;
		waited = 0;
		ambushFor = 1800;
		if (next != Intent.INTERCEPT) {
			interceptShun = Set.of();
			rauk.mover().shun(Set.of());
		}
		preyToSpot = -1;
		if (next == Intent.SEARCH) {
			doors.clear();
			doorIndex = 0;
		}
	}

	private void pickTarget(ServerLevel level, long now) {
		RaukMind mind = rauk.mind();
		UUID best = null;
		double bestScore = -1e9;
		for (Map.Entry<UUID, Belief> e : mind.beliefs().entrySet()) {
			Belief b = e.getValue();
			if (b.confidence < 0.05) {
				continue;
			}
			Entity entity = level.getEntity(e.getKey());
			if (entity != null && !Prey.is(entity)) {
				continue;
			}
			double score = 2 * b.confidence - b.position.distanceTo(rauk.position()) / 60 + (e.getKey().equals(target) ? 0.3 : 0);
			if (entity instanceof LivingEntity living) {
				// It goes for whoever is alone, and whoever is weak.
				PreyRead read = PreyRead.of(living, rauk, mind.velocity(living), mind.preys());
				score += (read.isolated() ? 0.6 : 0) + (read.vulnerable() ? 0.4 : 0);
			}
			if (score > bestScore) {
				bestScore = score;
				best = e.getKey();
			}
		}
		target = best;
	}

	@Nullable
	private LivingEntity targetEntity(ServerLevel level) {
		if (target == null) {
			return null;
		}
		return level.getEntity(target) instanceof LivingEntity l && Prey.is(l) ? l : null;
	}

	// --- doing --------------------------------------------------------------------------------

	private void run(ServerLevel level, long now) {
		timer++;
		RaukMind mind = rauk.mind();
		mind.tacticSpot = spot;
		mind.tacticLabel = spotLabel;
		switch (intent) {
			case REST -> rest(level);
			case PATROL -> patrol(level, now);
			case INVESTIGATE -> investigate(level, now);
			case TRACK -> track(level, now);
			case SEARCH -> search(level, now);
			case STALK -> stalk(level, now);
			case AMBUSH -> ambush(level, now);
			case PROBE -> probe(level, now);
			case COMMIT -> commit(level, now);
			case STRIKE -> strike(level, now);
			case CHASE -> chase(level, now);
			case INTERCEPT -> intercept(level, now);
			case DISENGAGE -> disengage(level, now);
			case GRAB -> grab(level, now);
			case FLANK -> flank(level, now);
			case REACH -> reachIn(level, now);
			case FEED -> feed(level, now);
			case LISTEN -> listen();
		}
		Vec3 lookAt = rauk.lookPoint();
		Entity gaze = rauk.gazeTarget();
		if (gaze != null) {
			rauk.getLookControl().setLookAt(gaze, 30, 30);
		} else if (lookAt != null) {
			rauk.getLookControl().setLookAt(lookAt.x, lookAt.y, lookAt.z, 20, 20);
		}
	}

	/**
	 * Heads for a place, and keeps to the way it worked out (design doc 4.7): a place that stays put
	 * is worked out once; one that moves (the prey's last known place) is caught up with only every
	 * so often, the replan interval, or at once if it jumps a long way. The way there is traced back
	 * from the place itself, so it is always the fastest from wherever it is.
	 */
	private void go(Vec3 to, @Nullable Direction surface, double speed, RaukPathfinder.Costs costs, int replan, long now) {
		lastGo = now;
		double moved = planned == null ? Double.MAX_VALUE : planned.distanceTo(to);
		if (!rauk.mover().isMoving() || moved > 8 || moved > 1.5 && now - lastPlan >= replan) {
			planned = to;
			lastPlan = now;
			rauk.mover().moveTo(to, surface, speed, costs);
		}
	}

	private boolean arrived(Vec3 to, double radius) {
		return rauk.position().distanceTo(to) < radius || (!rauk.mover().isMoving() && planned != null && rauk.position().distanceTo(to) < radius * 2.5);
	}

	private void watch(@Nullable LivingEntity prey, @Nullable Vec3 point, float attention) {
		rauk.setGazeTarget(prey);
		rauk.setLookPoint(prey == null ? point : null);
		rauk.setAttention(attention);
	}

	/** Which time of the music it has made up its mind about, and whether it stops for it this time. */
	private int musicSession = -1;
	private boolean musicSpell;

	/**
	 * Whether the music has it (hotel design 4.3): playing now, close (it has come to it, or was
	 * there), and this time, of all the times it's been played, it stops to listen.
	 */
	private boolean spellbound(long now) {
		if (!LastWaltz.playing(now)) {
			return false;
		}
		int session = LastWaltz.session();
		if (session != musicSession) {
			musicSession = session;
			musicSpell = rauk.getRandom().nextDouble() < LastWaltz.spell;
		}
		double d = rauk.position().distanceTo(LastWaltz.where());
		return musicSpell && (d < 22 || intent == Intent.LISTEN && d < 30);
	}

	/** Listening: still, upright if there's room, its head turned to the music. */
	private void listen() {
		rauk.stopWalking();
		rauk.setFloorStyle(RaukPosture.UPRIGHT);
		watch(null, LastWaltz.where(), 0.8f);
		rauk.setTension(0.35f);
	}

	private void rest(ServerLevel level) {
		BlockPos home = rauk.home();
		Vec3 den = Vec3.atBottomCenterOf(home);
		spot = den;
		spotLabel = "den";
		watch(null, null, 0);
		rauk.setTension(0.05f);
		rauk.setFloorStyle(RaukPosture.QUADRUPED);
		if (arrived(den, 1.5)) {
			rauk.stopWalking();
		} else {
			go(den, Direction.DOWN, PROWL, HuntCosts.quiet(level), 200, level.getGameTime());
		}
	}

	private void patrol(ServerLevel level, long now) {
		watch(null, null, 0);
		rauk.setTension(0.15f);
		// It goes where it first meant to look in (design doc 4.7), however far, unless there turns
		// out to be no way there; then the next place.
		if (spot == null || arrived(spot, 1.5) || timer > 1800 || rauk.mover().unreachable() && timer > 20) {
			timer = 0;
			pickPatrolSpot(level);
		}
		if (spot != null) {
			go(spot, spotSurface, PROWL, HuntCosts.quiet(level), 200, now);
		}
	}

	/**
	 * Where it looks in next: in the hotel, a space by the heat map ({@link #heatSpot}); elsewhere
	 * somewhere it knows, a door, a lever, a light. Now and then along the ceiling to get there.
	 */
	private void pickPatrolSpot(ServerLevel level) {
		var random = rauk.getRandom();
		Vec3 warm = heatSpot(level);
		if (warm != null) {
			spot = warm;
			spotSurface = null;
			spotLabel = "patrol: " + patrolName;
			rauk.setFloorStyle(random.nextFloat() < 0.7f ? RaukPosture.QUADRUPED : RaukPosture.UPRIGHT);
			if (random.nextFloat() < 0.25f) {
				Vec3 ceiling = ceilingAbove(new RaukPathfinder(level), warm, 6);
				if (ceiling != null) {
					spot = ceiling;
					spotSurface = Direction.UP;
					spotLabel += " (ceiling)";
				}
			}
			return;
		}
		List<BlockPos> places = rauk.mind().territory.known(rauk.blockPosition(), 40,
				s -> (s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)
						|| s.getBlock() instanceof LeverBlock || Lights.isLight(s));
		places.removeIf(p -> Vec3.atCenterOf(p).distanceTo(rauk.position()) < 8);
		RaukPathfinder finder = new RaukPathfinder(level);
		Vec3 to = null;
		if (!places.isEmpty()) {
			BlockPos p = places.get(random.nextInt(places.size()));
			SurfaceNode node = floorNear(finder, p, 3);
			if (node != null) {
				to = node.position();
			}
		}
		if (to == null) {
			to = DefaultRandomPos.getPos(rauk, 14, 4);
		}
		spot = to;
		spotSurface = null;
		spotLabel = "patrol";
		rauk.setFloorStyle(random.nextFloat() < 0.7f ? RaukPosture.QUADRUPED : RaukPosture.UPRIGHT);
		if (to != null && random.nextFloat() < 0.3f) {
			Vec3 ceiling = ceilingAbove(finder, to, 6);
			if (ceiling != null) {
				spot = ceiling;
				spotSurface = Direction.UP;
				spotLabel = "patrol (ceiling)";
			}
		}
	}

	private String patrolName = "";

	/**
	 * A space of the hotel to look in on (design doc 6.5, the heat map): the ones the players have
	 * spent their time in lately count most, and those it hasn't looked into for a while more than
	 * those it just has; near before far. Chosen at random by that, so it isn't predictable. Null
	 * without a hotel map.
	 */
	@Nullable
	private Vec3 heatSpot(ServerLevel level) {
		HotelGraph graph = HotelGraph.get(level);
		if (graph == null) {
			return null;
		}
		HotelHeat heat = HotelHeat.get(level);
		long now = level.getGameTime();
		int here = graph.partAt(rauk.position());
		List<double[]> options = new ArrayList<>();
		double total = 0;
		for (int p = 0; p < graph.sections(); p++) {
			if (p == here || graph.size(p) < 30) {
				continue;
			}
			rauk.com.hotel.HotelPlan.Area a = graph.plan.areas.get(graph.area(p));
			switch (a.kind()) {
				case LIFT, CRAWL, TUNNEL -> {
					continue;
				}
				default -> {
				}
			}
			BlockPos c = graph.frame.toWorld((int) Math.floor(a.centerX()), a.y0(), (int) Math.floor(a.centerZ()));
			double d = Vec3.atCenterOf(c).distanceTo(rauk.position());
			if (d > 90) {
				continue;
			}
			double warm = heat.heat(graph.area(p), now);
			double unseen = Math.min(1, heat.sinceChecked(graph.area(p), now) / (20.0 * 60 * 4));
			double w = (0.15 + Math.min(6, warm / 20)) * (0.15 + unseen) / (1 + d / 40);
			options.add(new double[] {p, w});
		}
		if (options.isEmpty()) {
			return null;
		}
		// Chosen among the six likeliest, by how likely: warm places it hasn't looked in on lately
		// come up most, and the rest now and then, so it isn't predictable.
		options.sort((a, b) -> Double.compare(b[1], a[1]));
		if (options.size() > 6) {
			options = new ArrayList<>(options.subList(0, 6));
		}
		for (double[] o : options) {
			total += o[1];
		}
		double pick = rauk.getRandom().nextDouble() * total;
		int part = (int) options.get(options.size() - 1)[0];
		for (double[] o : options) {
			pick -= o[1];
			if (pick <= 0) {
				part = (int) o[0];
				break;
			}
		}
		int centre = graph.centre(part);
		if (centre < 0) {
			return null;
		}
		patrolName = graph.plan.areas.get(graph.area(part)).name();
		if (DEBUG) {
			Rauk.LOGGER.info("patrol: looks in on {} (warm {}, last looked {}s ago)", patrolName, Math.round(heat.heat(graph.area(part), now)),
					heat.sinceChecked(graph.area(part), now) / 20);
		}
		return graph.node(centre).position();
	}

	private void investigate(ServerLevel level, long now) {
		RaukMind mind = rauk.mind();
		Belief b = target == null ? null : mind.beliefs().get(target);
		rauk.setFloorStyle(RaukPosture.QUADRUPED);
		if (b != null && b.confidence > 0.12) {
			rauk.setTension(0.45f);
			if (lookAt(level, b.position, "where it thinks prey is", true, now)) {
				done = true;
			}
			return;
		}
		RaukMind.Noise noise = mind.lastStrangeNoise(20);
		if (noise != null) {
			rauk.setTension(0.35f);
			if (lookAt(level, noise.pos(), "noise: " + noise.label(), false, now)) {
				mind.forgetNoisesNear(noise.pos(), 4);
				done = true;
			}
			return;
		}
		Territory.Anomaly anomaly = mind.territory.next();
		if (anomaly != null) {
			rauk.setTension(0.3f);
			if (lookAt(level, Vec3.atCenterOf(anomaly.pos()), anomaly.kind().name().toLowerCase().replace('_', ' '), false, now)) {
				mind.territory.investigated(anomaly.pos());
				done = true;
			}
			return;
		}
		done = true;
	}

	/**
	 * Goes to look at a place; true once it has had a look round. Looking for prey, it knocks and
	 * listens there instead, and a knock that gives nobody away lowers its hopes.
	 */
	private boolean lookAt(ServerLevel level, Vec3 at, String what, boolean forPrey, long now) {
		RaukMind mind = rauk.mind();
		spot = at;
		spotLabel = what;
		watch(null, at.add(0, 1, 0), 0.5f);
		if (rauk.position().distanceTo(at) > 3 && !(rauk.mover().unreachable() && timer > 40)) {
			phase = 0;
			go(at, null, HURRY - 1, HuntCosts.quiet(level), 60, now);
			return false;
		}
		rauk.stopWalking();
		if (!forPrey) {
			return ++phase > 40;
		}
		if (mind.busy()) {
			return false;
		}
		if (tapped) {
			tapped = false;
			if (!mind.tapHeard()) {
				Belief b = target == null ? null : mind.beliefs().get(target);
				if (b != null) {
					b.confidence *= 0.3;
				}
				return true;
			}
			return false;
		}
		if (now - lastTap > 200) {
			lastTap = now;
			tapped = true;
			BlockPos knock = somethingToKnockOn(level);
			mind.tap(knock, face(knock));
		}
		return false;
	}

	private void track(ServerLevel level, long now) {
		RaukMind mind = rauk.mind();
		ScentData.Hit scent = mind.scent();
		if (scent == null || scent.age() > 6000) {
			done = true;
			return;
		}
		ScentData.Hit next = ScentData.get(level).follow(scent, 6, now);
		if (next == null) {
			done = true;
			return;
		}
		rauk.setTension(0.4f);
		rauk.setFloorStyle(RaukPosture.QUADRUPED);
		spot = next.position();
		spotLabel = "scent " + (scent.age() / 20) + "s old";
		watch(null, next.position(), 0.6f);
		if (rauk.mover().unreachable() && timer > 10) {
			// The trail goes where it can't follow (up on a desk, along a beam): it leaves it a while.
			trackBlockedUntil = now + 200;
			done = true;
			return;
		}
		go(next.position(), null, PROWL, HuntCosts.quiet(level), 40, now);
	}

	/** Until when it leaves a scent trail it couldn't follow. */
	private long trackBlockedUntil;

	// --- searching rooms ----------------------------------------------------------------------

	/**
	 * The few doors nearest where it thinks the prey is, on its floor before any other; the doors
	 * into the rooms the prey has spent its time in lately before the rest (the heat map), and those
	 * it has just looked into after them.
	 */
	private List<BlockPos> doorsNear(Belief b) {
		ServerLevel level = (ServerLevel) rauk.level();
		long now = level.getGameTime();
		HotelHeat heat = HotelHeat.get(level);
		double reach = Math.min(b.uncertainty + 6, b.secondsSince(now) > 10 ? 24 : 16);
		List<BlockPos> out = rauk.mind().territory.known(BlockPos.containing(b.position), reach,
				s -> s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER);
		out.removeIf(p -> Vec3.atCenterOf(p).distanceTo(rauk.position()) > 48);
		int mine = HotelHeat.area(level, rauk.position());
		java.util.Map<BlockPos, Double> score = new java.util.HashMap<>();
		for (BlockPos p : out) {
			double s = Math.hypot(p.getX() + 0.5 - b.position.x, p.getZ() + 0.5 - b.position.z) + 3 * Math.abs(p.getY() - b.position.y);
			BlockState state = level.getBlockState(p);
			if (state.getBlock() instanceof DoorBlock) {
				Direction f = state.getValue(DoorBlock.FACING);
				double warm = 0;
				boolean looked = false;
				for (Direction side : new Direction[] {f, f.getOpposite()}) {
					int area = HotelHeat.area(level, Vec3.atBottomCenterOf(p.relative(side, 2)));
					if (area >= 0) {
						warm = Math.max(warm, heat.heat(b.prey, area, now));
						// Looked into is the room through the door, not the corridor it is standing in.
						looked |= area != mine && heat.sinceChecked(area, now) < 600;
					}
				}
				s += -8 * Math.min(1, warm / 60) + (looked ? 6 : 0);
			}
			score.put(p, s);
		}
		out.sort(Comparator.comparingDouble(score::get));
		return out.size() > 5 ? new ArrayList<>(out.subList(0, 5)) : out;
	}

	/**
	 * Door to door: it goes to each door near where it thinks the prey is, presses its head to it
	 * and listens (and sniffs the gap: its nose works through a closed door), then knocks, or flicks
	 * the room's lights off and on, and listens again. If that gives the prey away, it stalks in.
	 */
	private void search(ServerLevel level, long now) {
		RaukMind mind = rauk.mind();
		Belief b = target == null ? null : mind.beliefs().get(target);
		if (b == null) {
			done = true;
			return;
		}
		if (doors.isEmpty() && doorIndex == 0) {
			doors.addAll(doorsNear(b));
			if (doors.isEmpty()) {
				done = true;
				return;
			}
		}
		if (doorIndex >= doors.size()) {
			b.confidence *= 0.4;
			mind.setListening(false);
			done = true;
			return;
		}
		BlockPos door = doors.get(doorIndex);
		rauk.setTension(0.5f);
		rauk.setFloorStyle(RaukPosture.QUADRUPED);
		if (phase == 0) {
			Vec3 outside = outsideOf(level, door);
			spot = outside;
			spotLabel = "door " + (doorIndex + 1) + "/" + doors.size();
			watch(null, Vec3.atCenterOf(door), 0.5f);
			if (outside == null || (rauk.mover().unreachable() && timer > 40)) {
				doorIndex++;
				timer = 0;
				return;
			}
			if (arrived(outside, 1.3)) {
				rauk.stopWalking();
				phase = 1;
				timer = 0;
				flushLever = leverByDoor(door);
			} else {
				go(outside, Direction.DOWN, PROWL, HuntCosts.quiet(level), 80, now);
			}
			return;
		}
		// Listening at the door.
		watch(null, Vec3.atCenterOf(door).add(0, 0.5, 0), 1);
		mind.setListening(true);
		if (timer == 12) {
			if (flushLever != null) {
				lights.pull(level, flushLever);
				rauk.setAction(RaukMind.ACTION_REACH, Vec3.atCenterOf(flushLever));
			} else if (rauk.getRandom().nextFloat() < 0.6f) {
				mind.tap(door.above(), face(door.above()));
			}
		}
		if (timer == 26 && flushLever != null) {
			lights.pull(level, flushLever);
		}
		if (timer == 30 && flushLever != null) {
			rauk.setAction(RaukMind.ACTION_NONE, Vec3.atCenterOf(flushLever));
		}
		if (timer > 60 && !mind.busy()) {
			mind.setListening(false);
			if (b.confidence > 0.45 && b.secondsSince(now) < 4) {
				// Found: the next choice will stalk in.
				done = true;
				return;
			}
			doorIndex++;
			phase = 0;
			timer = 0;
		}
	}

	/** The floor in front of the door on Rauk's side. */
	@Nullable
	private Vec3 outsideOf(ServerLevel level, BlockPos door) {
		BlockState state = level.getBlockState(door);
		RaukPathfinder finder = new RaukPathfinder(level);
		Direction facing = state.getBlock() instanceof DoorBlock ? state.getValue(DoorBlock.FACING) : Direction.NORTH;
		Vec3 best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Direction d : new Direction[] {facing, facing.getOpposite()}) {
			SurfaceNode node = floorNear(finder, door.relative(d, 2), 1);
			if (node != null) {
				double distance = node.position().distanceTo(rauk.position());
				if (distance < bestDistance) {
					bestDistance = distance;
					best = node.position();
				}
			}
		}
		return best;
	}

	@Nullable
	private BlockPos leverByDoor(BlockPos door) {
		for (BlockPos lever : rauk.mind().territory.known(door, 4, s -> s.getBlock() instanceof LeverBlock)) {
			if (lights.worksLightsNear(lever, Vec3.atCenterOf(door), 8)) {
				return lever;
			}
		}
		return null;
	}

	// --- stalking -----------------------------------------------------------------------------

	private void stalk(ServerLevel level, long now) {
		RaukMind mind = rauk.mind();
		LivingEntity t = targetEntity(level);
		Belief b = target == null ? null : mind.beliefs().get(target);
		if (b == null) {
			done = true;
			return;
		}
		boolean seen = t != null && mind.sees(target);
		Vec3 prey = seen ? t.position() : b.position;
		Vec3 preyEye = seen ? t.getEyePosition() : b.position.add(0, 1.6, 0);
		Vec3 look = seen ? t.getViewVector(1f) : null;
		Vec3 velocity = seen ? mind.velocity(t).scale(20) : b.velocity;
		double d = rauk.position().distanceTo(prey);
		boolean watched = seen && PreyRead.lookingAt(t, rauk);
		if (watched) {
			caution = Math.min(1, caution + 0.002);
		}
		if (!seen && rauk.position().distanceTo(prey) < 3) {
			// Where it thought the prey was, and nothing there: it was wrong, and looks about.
			b.confidence *= 0.5;
			done = true;
			return;
		}
		rauk.setFloorStyle(RaukPosture.QUADRUPED);
		rauk.setTension((float) Math.min(0.85, 0.55 + 0.3 * (1 - Math.min(d, 20) / 20)));
		watch(seen ? t : null, prey.add(0, 1, 0), seen ? 1 : 0.5f);
		boolean hunting = mood == Mood.HUNT;

		// Lights off along its way in.
		if (errand == null && now >= nextLights) {
			nextLights = now + 200;
			BlockPos lever = lights.leverFor(level, prey.lerp(rauk.position(), 0.5), d / 2 + 6, false, 14);
			if (lever != null && !(seen && HuntCosts.visibleFrom(level, preyEye, Vec3.atCenterOf(lever)) && watched)) {
				errand = lever;
				errandOn = false;
			}
		}
		if (doErrand(level, now, preyEye, look)) {
			return;
		}
		// Lanterns and candles near its hands come down.
		BlockPos lamp = Lights.lampWithinReach(level, mind.eye());
		if (lamp != null && d < 24) {
			level.destroyBlock(lamp, true, rauk);
			mind.territory.expect(lamp, level.getBlockState(lamp));
		}
		// Hunting it never stops. Playing, watched from afar it holds dead still; stared at too long,
		// it melts away and comes round another way. Close enough, it waits at the edge of the light
		// for an opening.
		if (!hunting && watched && d > 9) {
			frozen++;
			if (frozen > 60) {
				frozen = 0;
				begin(Intent.DISENGAGE, now);
				return;
			}
			rauk.mover().setPaused(true);
			spotLabel = "stalk: still, being watched";
			return;
		}
		frozen = 0;
		if (!hunting && watched && d < 13) {
			// At the edge of the light it waits for an opening, but not for long: then it comes.
			if (++waited > 50) {
				waited = 0;
				mood = Mood.HUNT;
			} else {
				rauk.stopWalking();
				spotLabel = "stalk: waiting for an opening";
				return;
			}
		} else {
			waited = 0;
		}
		// Above where the prey is heading, if there is a ceiling; otherwise just close.
		RaukPathfinder finder = new RaukPathfinder(level);
		Vec3 ahead = prey.add(velocity.scale(1.5));
		Vec3 ceiling = d < 16 && !noCeiling ? ceilingAbove(finder, ahead, 6) : null;
		if (ceiling != null) {
			spot = ceiling;
			spotSurface = Direction.UP;
			spotLabel = "stalk: ceiling above";
		} else {
			spot = prey;
			spotSurface = null;
			spotLabel = "stalk";
		}
		// Hunting it comes on fast, faster than you walk; playing, it creeps.
		double speed = hunting ? (d > 12 ? HURRY + 1.5 : HURRY + 0.5) : d < 8 ? CREEP : STALK_SPEED;
		// Close and in sight it keeps right on it; far or out of sight it goes for where the prey
		// was last known to be, caught up with every two seconds.
		go(spot, spotSurface, speed, HuntCosts.stalk(level, preyEye, look, hunting ? 0 : caution), seen && d < 16 ? 10 : 40, now);
		if (spotSurface == Direction.UP && rauk.mover().unreachable()) {
			// No way onto that ceiling: along the floor, then.
			noCeiling = true;
		}
	}

	/** How long, playing, it has waited at the edge of the light for an opening. */
	private int waited;
	/** Stalking, the ceilings here turned out to be out of its reach. */
	private boolean noCeiling;

	/** Going to a lever on the way: true while it is busy with it. */
	private boolean doErrand(ServerLevel level, long now, @Nullable Vec3 preyEye, @Nullable Vec3 look) {
		if (errand == null) {
			return false;
		}
		RaukMind mind = rauk.mind();
		Vec3 lever = Vec3.atCenterOf(errand);
		spot = lever;
		spotLabel = errandOn ? "lights on" : "lights off";
		if (mind.eye().distanceTo(lever) < 3.2 || rauk.getBoundingBox().getCenter().distanceTo(lever) < 3) {
			rauk.stopWalking();
			if (phaseErrand == 0) {
				rauk.setAction(RaukMind.ACTION_REACH, lever);
				phaseErrand = 1;
				errandTick = now;
			} else if (now - errandTick >= 8) {
				if (level.getBlockState(errand).getBlock() instanceof LeverBlock) {
					lights.pull(level, errand);
				}
				rauk.setAction(RaukMind.ACTION_NONE, lever);
				errand = null;
				phaseErrand = 0;
			}
			return true;
		}
		if (rauk.mover().unreachable() && now - lastPlan > 20) {
			errand = null;
			return false;
		}
		RaukPathfinder.Costs costs = preyEye == null ? HuntCosts.quiet(level) : HuntCosts.stalk(level, preyEye, look, caution);
		RaukPathfinder finder = new RaukPathfinder(level);
		SurfaceNode node = floorNear(finder, errand, 2);
		go(node != null ? node.position() : lever, Direction.DOWN, STALK_SPEED + 0.8, costs, 40, now);
		return true;
	}

	private int phaseErrand, frozen;
	private long errandTick;

	// --- ambush, intercept --------------------------------------------------------------------

	@Nullable
	private BlockPos knownRoom() {
		if (target != null) {
			PlayerProfile p = rauk.mind().profile(target);
			if (p.room() != null) {
				return p.room();
			}
		}
		for (Map.Entry<UUID, Belief> e : rauk.mind().beliefs().entrySet()) {
			BlockPos room = rauk.mind().profile(e.getKey()).room();
			if (room != null) {
				return room;
			}
		}
		return null;
	}

	/** Sets where it will wait: on the ceiling over a door near the place, or over the place itself. */
	public void ambushAt(Vec3 place, long now) {
		begin(Intent.AMBUSH, now);
		spot = place;
		phase = 0;
	}

	/** Sets it to wait beside a door, on the room side, for whoever comes through (test command). */
	public void ambushDoor(BlockPos door, Vec3 room, long now) {
		begin(Intent.AMBUSH, now);
		SurfaceNode beside = besideDoor((ServerLevel) rauk.level(), door, room);
		if (beside != null) {
			spot = beside.position();
			spotSurface = beside.surface();
			spotLabel = "ambush: beside the door";
			doorAmbush = true;
			phase = 1;
		} else {
			spot = Vec3.atBottomCenterOf(door);
			phase = 0;
		}
	}

	private void ambush(ServerLevel level, long now) {
		RaukMind mind = rauk.mind();
		RaukPathfinder finder = new RaukPathfinder(level);
		if (phase == 0) {
			Vec3 place = spot;
			if (place == null) {
				BlockPos room = knownRoom();
				if (room == null) {
					done = true;
					return;
				}
				place = Vec3.atBottomCenterOf(room);
				List<BlockPos> roomDoors = mind.territory.known(room, 8,
						s -> s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER);
				if (!roomDoors.isEmpty()) {
					// Flat against the wall just inside the door, to take whoever comes through it.
					SurfaceNode beside = besideDoor(level, roomDoors.get(0), Vec3.atCenterOf(room));
					if (beside != null) {
						spot = beside.position();
						spotSurface = beside.surface();
						spotLabel = "ambush: beside the door";
						doorAmbush = true;
						phase = 1;
						return;
					}
					place = Vec3.atBottomCenterOf(roomDoors.get(0));
				}
			}
			Vec3 ceiling = ceilingAbove(finder, place, 7);
			spot = ceiling != null ? ceiling : place;
			spotSurface = ceiling != null ? Direction.UP : null;
			spotLabel = "ambush";
			// Lights on where it waits: prey goes toward the light.
			BlockPos lever = lights.leverFor(level, place, 7, true, 16);
			if (lever != null) {
				errand = lever;
				errandOn = true;
			}
			phase = 1;
			return;
		}
		if (doErrand(level, now, null, null)) {
			return;
		}
		Belief b = target == null ? null : mind.beliefs().get(target);
		LivingEntity t = targetEntity(level);
		boolean seen = t != null && mind.sees(target);
		watch(seen ? t : null, spot.add(0, -2, 0), seen ? 1 : 0.4f);
		if (phase == 1) {
			rauk.setTension(0.5f);
			rauk.setFloorStyle(RaukPosture.QUADRUPED);
			RaukPathfinder.Costs costs = b != null ? HuntCosts.stalk(level, b.position.add(0, 1.6, 0), null, caution) : HuntCosts.quiet(level);
			if (arrived(spot, 1.2) || (rauk.mover().unreachable() && timer > 60)) {
				phase = 2;
				timer = 0;
			} else {
				go(spot, spotSurface, HURRY - 1.5, costs, 80, now);
			}
			return;
		}
		// Waiting, still, listening. Whatever comes within reach (through the door beside it, under
		// the ceiling it hangs from) it grabs at once.
		rauk.stopWalking();
		rauk.setTension(0.6f);
		mind.setListening(true);
		spotLabel = "ambush: waiting";
		if (seen && now >= strikeReady && rauk.combat().reach(t)) {
			mind.setListening(false);
			engage(t, now);
			return;
		}
		if (now - intentStart > ambushFor) {
			ambushReady = now + 2400;
			mind.setListening(false);
			done = true;
		}
	}

	/**
	 * Beside a door on the room side: clinging to the wall next to the frame, or failing that flat on
	 * the floor beside it, out of sight of anyone coming through until they are past it.
	 */
	@Nullable
	private SurfaceNode besideDoor(ServerLevel level, BlockPos door, Vec3 room) {
		BlockState state = level.getBlockState(door);
		if (!(state.getBlock() instanceof DoorBlock)) {
			return null;
		}
		Direction facing = state.getValue(DoorBlock.FACING);
		Vec3 axis = new Vec3(facing.getStepX(), 0, facing.getStepZ());
		Direction in = room.subtract(Vec3.atCenterOf(door)).dot(axis) >= 0 ? facing : facing.getOpposite();
		RaukPathfinder finder = new RaukPathfinder(level);
		Direction[] sides = {facing.getClockWise(), facing.getCounterClockWise()};
		for (Direction side : sides) {
			for (int k = 2; k <= 3; k++) {
				for (int dy = 1; dy <= 2; dy++) {
					SurfaceNode node = finder.node(door.relative(side, k).relative(in).above(dy), in.getOpposite());
					if (node != null) {
						return node;
					}
				}
			}
		}
		for (Direction side : sides) {
			SurfaceNode node = floorNear(finder, door.relative(side, 2).relative(in), 0);
			if (node != null) {
				return node;
			}
		}
		return null;
	}

	private void intercept(ServerLevel level, long now) {
		RaukMind mind = rauk.mind();
		Belief b = target == null ? null : mind.beliefs().get(target);
		if (b == null) {
			done = true;
			return;
		}
		if (spot == null) {
			// Where the prey is going: worked out from what it knows of it, or failing that straight on.
			Prediction.Guess g = interceptAt != null ? interceptAt
					: Prediction.destination(level, rauk, target, b.position, b.velocity);
			if (interceptAt == null && g != null) {
				// Gone out of sight: wherever it worked out they're going, not by the way they went.
				interceptShun = shunFor(level, g, b.position);
			}
			interceptAt = null;
			Vec3 dest;
			if (g != null) {
				dest = g.at();
				spotLabel = "intercept: " + g.why();
			} else {
				Vec3 heading = b.velocity.horizontalDistance() > 0.5 ? b.velocity.normalize() : Vec3.ZERO;
				dest = b.position.add(heading.scale(10));
				spotLabel = "intercept";
			}
			RaukPathfinder finder = new RaukPathfinder(level);
			SurfaceNode node = floorNear(finder, BlockPos.containing(dest), 3);
			spot = node != null ? node.position() : dest;
		}
		rauk.setTension(0.7f);
		rauk.setFloorStyle(RaukPosture.QUADRUPED);
		watch(null, spot.add(0, 1, 0), 0.6f);
		// Hunting, it gets there at a gallop while it has the breath; out of sight of the prey either
		// way, and not through the space the prey is in.
		rauk.mover().shun(interceptShun);
		double speed = mood == Mood.HUNT && !rauk.panting() && spot.distanceTo(rauk.position()) > 8 ? GALLOP : HURRY;
		go(spot, null, speed, HuntCosts.stalk(level, b.position.add(0, 1.6, 0), null, mood == Mood.HUNT ? 0 : caution), 60, now);
		if (arrived(spot, 2.5) || timer > 400) {
			Vec3 place = spot;
			BlockPos door = doorNear(level, place, 3);
			if (door != null) {
				// The way out of where the prey is: flat against the wall beside the door, on this side.
				ambushDoor(door, place, now);
				ambushFor = 500;
				spotLabel = "cut off: waiting by the door";
			} else {
				ambushAt(place, now);
				ambushFor = 500;
			}
		}
	}

	/** A door (its lower half) within a few blocks of a place, nearest first, or null. */
	@Nullable
	private static BlockPos doorNear(ServerLevel level, Vec3 at, int reach) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.withinManhattan(BlockPos.containing(at), reach, 2, reach)) {
			BlockState state = level.getBlockState(p);
			if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
				double d = Vec3.atBottomCenterOf(p).distanceTo(at);
				if (d < bestDistance) {
					bestDistance = d;
					best = p.immutable();
				}
			}
		}
		return best;
	}

	/** How long it lies in wait this time before giving it up: a cut-off only a little while. */
	private int ambushFor = 1800;

	// --- probing ------------------------------------------------------------------------------

	private void probe(ServerLevel level, long now) {
		RaukMind mind = rauk.mind();
		LivingEntity t = targetEntity(level);
		if (t == null) {
			phase = 3;
			done = true;
			return;
		}
		PreyRead read = PreyRead.of(t, rauk, mind.velocity(t), mind.preys());
		watch(t, null, 1);
		if (phase == 0) {
			// In a big room it shows its whole self; in a corridor, only its head round a corner.
			Vec3 at = null;
			if (headroom(level, t.blockPosition()) >= 6) {
				at = probeSpot(level, t);
			} else {
				Vec3[] corner = peekSpot(level, t);
				if (corner != null) {
					at = corner[0];
					peekHead = corner[1];
				}
			}
			if (at == null) {
				phase = 3;
				done = true;
				probeReady = now + 600;
				return;
			}
			spot = at;
			spotLabel = peekHead != null ? "probe: to the corner" : "probe: show itself";
			phase = 1;
			timer = 0;
		}
		if (phase == 1) {
			rauk.setTension(0.45f);
			boolean tall = headroom(level, BlockPos.containing(spot)) >= 9;
			rauk.setFloorStyle(tall && peekHead == null ? RaukPosture.UPRIGHT : RaukPosture.QUADRUPED);
			if (arrived(spot, 1.5) || timer > 300) {
				phase = 2;
				timer = 0;
				probeStart = read.distance();
				rauk.stopWalking();
			} else {
				go(spot, Direction.DOWN, PROWL, RaukPathfinder.Costs.NONE, 60, now);
			}
			return;
		}
		// Showing itself, watching what the prey does.
		rauk.stopWalking();
		rauk.setTension(0.5f);
		spotLabel = "probe: watching";
		if (peekHead != null) {
			// Level, still, watching: the head out round the corner, the body behind it.
			spotLabel = "probe: peeking";
			if (peekDoor != null && level.getBlockState(peekDoor).getBlock() instanceof DoorBlock door
					&& !door.isOpen(level.getBlockState(peekDoor))) {
				// One long finger hooks the edge and draws it open, slowly: the hotel's doors only a
				// crack, enough to look through.
				if (!HotelDoorBlock.peek(level, peekDoor, rauk)) {
					door.setOpen(rauk, level, level.getBlockState(peekDoor), peekDoor, true);
				}
				rauk.mind().territory.expect(peekDoor, level.getBlockState(peekDoor));
			}
			rauk.setAction(RaukMind.ACTION_PEEK, peekHead);
			Vec3 to = peekHead.subtract(rauk.position());
			float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
			rauk.setYRot(yaw);
			rauk.yBodyRot = yaw;
		}
		if (timer == 25 && rauk.posture() == RaukPosture.UPRIGHT && headroom(level, rauk.blockPosition()) >= 9) {
			rauk.setRisen(true);
		}
		if (read.running()) {
			probeReady = now + 1200;
			phase = 3;
			begin(Intent.CHASE, now);
		} else if (read.distance() < probeStart - 3 || (read.rangedAimed() && read.lookingAt())) {
			// Coming at it: it circles away and comes back another way.
			probeReady = now + 1200;
			phase = 3;
			begin(Intent.DISENGAGE, now);
		} else if (timer > 130) {
			probeReady = now + 1200;
			phase = 3;
			begin(Intent.DISENGAGE, now);
		}
	}

	/** A floor spot 11 to 15 blocks from the prey, where the prey can see it, as dark as possible. */
	@Nullable
	private Vec3 probeSpot(ServerLevel level, LivingEntity prey) {
		RaukPathfinder finder = new RaukPathfinder(level);
		Vec3 eye = prey.getEyePosition();
		Vec3 best = null;
		double bestScore = Double.MAX_VALUE;
		for (double r : new double[] {13, 11, 15}) {
			for (int a = 0; a < 16; a++) {
				double angle = a * Math.PI / 8;
				BlockPos cell = BlockPos.containing(prey.getX() + Math.cos(angle) * r, prey.getY(), prey.getZ() + Math.sin(angle) * r);
				SurfaceNode node = floorNear(finder, cell, 2);
				if (node == null || node.posture() == RaukPosture.SQUEEZE) {
					continue;
				}
				Vec3 body = node.position().add(0, 2, 0);
				if (!HuntCosts.visibleFrom(level, eye, body)) {
					continue;
				}
				double score = level.getMaxLocalRawBrightness(node.cell()) + node.position().distanceTo(rauk.position()) * 0.2;
				if (score < bestScore) {
					bestScore = score;
					best = node.position();
				}
			}
			if (best != null) {
				break;
			}
		}
		return best;
	}

	/** Where the head goes when it peeks: out round the corner. */
	@Nullable
	private Vec3 peekHead;

	/**
	 * A corner six to twelve blocks from the prey: a floor spot the prey can't see, with a clear
	 * line for the neck from there to a point about three blocks on that the prey can see. The body
	 * waits behind the corner and only the head comes out.
	 */
	@Nullable
	private Vec3[] peekSpot(ServerLevel level, LivingEntity prey) {
		Vec3[] door = doorPeek(level, prey);
		if (door != null) {
			return door;
		}
		RaukPathfinder finder = new RaukPathfinder(level);
		Vec3 eye = prey.getEyePosition();
		Vec3[] best = null;
		double bestScore = Double.MAX_VALUE;
		java.util.Set<BlockPos> tried = new java.util.HashSet<>();
		for (double r : new double[] {6, 8, 5, 10, 12}) {
			for (int a = 0; a < 32; a++) {
				double angle = a * Math.PI / 16;
				BlockPos cell = BlockPos.containing(prey.getX() + Math.cos(angle) * r, prey.getY(), prey.getZ() + Math.sin(angle) * r);
				SurfaceNode node = floorNear(finder, cell, 1);
				if (node == null || node.posture() == RaukPosture.SQUEEZE || !tried.add(node.cell())) {
					continue;
				}
				Vec3 p = node.position();
				if (HuntCosts.visibleFrom(level, eye, p.add(0, 1.0, 0)) || HuntCosts.visibleFrom(level, eye, p.add(0, 2.0, 0))) {
					continue;
				}
				// The neck is straight from the shoulders: try it at a few angles off the line to the
				// prey and a few lengths, for one that clears the corner or the door frame.
				Vec3 toward = eye.subtract(p).multiply(1, 0, 1).normalize();
				Vec3 neckBase = p.add(0, 1.3, 0);
				search:
				for (double turn : new double[] {0, 0.3, -0.3, 0.6, -0.6, 0.9, -0.9, 1.2, -1.2}) {
					for (double length : new double[] {2.8, 2.2, 3.4}) {
						Vec3 head = neckBase.add(toward.yRot((float) turn).scale(length));
						var neck = level.clip(new ClipContext(neckBase, head, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rauk));
						if (neck.getType() != HitResult.Type.MISS || !HuntCosts.visibleFrom(level, eye, head)) {
							continue;
						}
						double score = level.getMaxLocalRawBrightness(node.cell()) + p.distanceTo(rauk.position()) * 0.15 + Math.abs(turn);
						if (score < bestScore) {
							bestScore = score;
							best = new Vec3[] {p, head};
						}
						break search;
					}
				}
			}
			if (best != null) {
				break;
			}
		}
		return best;
	}

	/**
	 * Out of a doorway: the body inside the room, off to one side of the door where the prey can't
	 * see it, the head out just past the frame.
	 */
	@Nullable
	private Vec3[] doorPeek(ServerLevel level, LivingEntity prey) {
		RaukPathfinder finder = new RaukPathfinder(level);
		Vec3 eye = prey.getEyePosition();
		Vec3[] best = null;
		double bestScore = Double.MAX_VALUE;
		for (BlockPos door : rauk.mind().territory.known(prey.blockPosition(), 14,
				s -> s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)) {
			BlockState state = level.getBlockState(door);
			if (!(state.getBlock() instanceof DoorBlock) || Vec3.atCenterOf(door).distanceTo(eye) < 4) {
				continue;
			}
			Direction facing = state.getValue(DoorBlock.FACING);
			Vec3 axis = new Vec3(facing.getStepX(), 0, facing.getStepZ());
			// The room is the side away from the prey.
			Direction in = prey.position().subtract(Vec3.atCenterOf(door)).dot(axis) > 0 ? facing.getOpposite() : facing;
			Vec3 head = Vec3.atBottomCenterOf(door).add(0, 1.2, 0).add(in.getStepX() * -0.9, 0, in.getStepZ() * -0.9);
			if (!HuntCosts.visibleFrom(level, eye, head)) {
				if (DEBUG) {
					Rauk.LOGGER.info("peek: door {} head {} not visible", door.toShortString(), head);
				}
				continue;
			}
			for (int back = 2; back <= 3; back++) {
				for (int s : new int[] {1, -1, 0}) {
					SurfaceNode node = floorNear(finder, door.relative(in, back).relative(facing.getClockWise(), s), 0);
					if (node == null || node.posture() == RaukPosture.SQUEEZE) {
						continue;
					}
					Vec3 p = node.position();
					if (HuntCosts.visibleFrom(level, eye, p.add(0, 1.0, 0)) || HuntCosts.visibleFrom(level, eye, p.add(0, 2.0, 0))) {
						if (DEBUG) {
							Rauk.LOGGER.info("peek: door {} body {} seen", door.toShortString(), p);
						}
						continue;
					}
					Vec3 neckBase = p.add(0, 1.3, 0);
					var neck = level.clip(new ClipContext(neckBase, head, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rauk));
					// The door itself doesn't count: it opens it to look out.
					boolean doorOnly = neck.getType() != HitResult.Type.MISS
							&& (neck.getBlockPos().equals(door) || neck.getBlockPos().equals(door.above()));
					if (neckBase.distanceTo(head) > 3.8 || neck.getType() != HitResult.Type.MISS && !doorOnly) {
						if (DEBUG) {
							Rauk.LOGGER.info("peek: door {} body {} neck blocked at {} ({} long)", door.toShortString(), p,
									neck.getLocation(), String.format("%.1f", neckBase.distanceTo(head)));
						}
						continue;
					}
					double score = level.getMaxLocalRawBrightness(node.cell()) + p.distanceTo(rauk.position()) * 0.15;
					if (score < bestScore) {
						bestScore = score;
						best = new Vec3[] {p, head};
						peekDoor = door.immutable();
					}
				}
			}
		}
		return best;
	}

	/** The door it peeks out of, which it opens first if it is shut. */
	@Nullable
	private BlockPos peekDoor;

	private static int headroom(ServerLevel level, BlockPos floor) {
		RaukPathfinder finder = new RaukPathfinder(level);
		int h = 0;
		while (h < 10 && !finder.blocked(floor.above(h))) {
			h++;
		}
		return h;
	}

	// --- committing, striking, chasing, getting away ------------------------------------------

	private void commit(ServerLevel level, long now) {
		LivingEntity t = targetEntity(level);
		if (t == null) {
			done = true;
			return;
		}
		// It holds its breath, folds flat, body coils: silence, then it goes. Seen, hunting, it
		// doesn't wait.
		rauk.mover().setPaused(true);
		rauk.setTension(1);
		watch(t, null, 1);
		spot = t.position();
		spotLabel = "commit";
		boolean quick = mood == Mood.HUNT && PreyRead.lookingAt(t, rauk);
		if (timer >= (quick ? 6 : 14)) {
			begin(Intent.STRIKE, now);
		}
	}

	private void strike(ServerLevel level, long now) {
		RaukMind mind = rauk.mind();
		LivingEntity t = targetEntity(level);
		if (t == null) {
			done = true;
			return;
		}
		rauk.setTension(1);
		watch(t, null, 1);
		spot = t.position();
		spotLabel = "strike";
		if (timer == 1) {
			PreyRead read = PreyRead.of(t, rauk, mind.velocity(t), mind.preys());
			if (read.lookingAt() && read.armed() && t.getHealth() > t.getMaxHealth() * 0.4f && hunger < 0.8) {
				// Armed and facing it: it goes round first, for a moment.
				begin(Intent.FLANK, now);
				return;
			}
			double d = rauk.position().distanceTo(t.position());
			boolean leapt = false;
			if (rauk.surface() != Direction.DOWN || (d > 4 && d < 12)) {
				RaukPathfinder finder = new RaukPathfinder(level);
				SurfaceNode node = floorNear(finder, t.blockPosition(), 2);
				if (node != null) {
					leapt = rauk.mover().leap(node.position(), Direction.DOWN);
				}
			}
			if (!leapt) {
				rauk.setFloorStyle(RaukPosture.QUADRUPED);
				rauk.mover().follow(t, GALLOP, 1.4);
			}
			leaptStrike = leapt;
		} else if (!rauk.mover().isMoving() && !rauk.mover().isLeaping()) {
			rauk.mover().follow(t, GALLOP, 1.4);
		}
		// Landed from a leap or a ceiling drop on top of the prey: pinned under it, then grabbed.
		if (leaptStrike && !rauk.mover().isLeaping() && timer > 3) {
			leaptStrike = false;
			if (rauk.position().distanceTo(t.position()) < 3 && rauk.combat().reach(t)) {
				rauk.combat().pin(t);
				begin(Intent.GRAB, now);
				return;
			}
		}
		if (!rauk.mover().isLeaping() && rauk.combat().reach(t)) {
			engage(t, now);
		} else if (!rauk.mover().isLeaping() && timer > 10 && t.distanceTo(rauk) < 8 && now >= reachReady
				&& (rauk.mover().unreachable() || (!rauk.mover().isMoving() && t.distanceTo(rauk) < 6))) {
			begin(Intent.REACH, now);
		} else if (timer > 80) {
			if (mood == Mood.HUNT) {
				// Missed: straight again.
				strikeReady = now + 20;
				done = true;
			} else {
				strikeReady = now + 200;
				begin(Intent.DISENGAGE, now);
			}
		}
	}

	/**
	 * Within reach of its prey: it hooks a raised shield away; armed prey that faces it and is still
	 * strong it circles round to take from the side or behind; anything else it grabs. After a hook
	 * it keeps coming.
	 */
	private void engage(LivingEntity t, long now) {
		PreyRead read = PreyRead.of(t, rauk, rauk.mind().velocity(t), rauk.mind().preys());
		switch (rauk.combat().engage(t, read.lookingAt() && read.armed())) {
			case GRABBED, PINNED -> begin(Intent.GRAB, now);
			case FACED -> {
				if (intent != Intent.FLANK) {
					begin(Intent.FLANK, now);
				}
			}
			case HOOKED -> {
				if (intent == Intent.STRIKE) {
					timer = 2;
				}
			}
			case NONE -> {
			}
		}
	}

	/** Whether the prey is somewhere its body can't fit at all, folded as small as it goes: a vent, a gap. */
	private static boolean cantFollow(ServerLevel level, LivingEntity t) {
		return floorNear(new RaukPathfinder(level), t.blockPosition(), 0) == null;
	}

	/** Whether the mover's goal is the prey (or right next to it), so an unreachable goal means the prey is. */
	private boolean headingFor(LivingEntity t) {
		Vec3 goal = rauk.mover().goal();
		return goal != null && goal.distanceTo(t.position()) < 3.5;
	}

	/**
	 * Faced by armed prey (design doc 7.4: "face it, it prefers your back"): it circles at a run to
	 * the prey's side, then its back, and grabs as soon as the prey isn't facing it. If the prey
	 * keeps turning to face it, after a couple of seconds it stops respecting the standoff: nothing
	 * the prey holds can hurt it.
	 */
	private void flank(ServerLevel level, long now) {
		LivingEntity t = targetEntity(level);
		if (t == null) {
			done = true;
			return;
		}
		RaukMind mind = rauk.mind();
		PreyRead read = PreyRead.of(t, rauk, mind.velocity(t), mind.preys());
		watch(t, null, 1);
		rauk.setTension(0.9f);
		rauk.setFloorStyle(RaukPosture.QUADRUPED);
		if (read.fire()) {
			strikeReady = now + 60;
			begin(Intent.DISENGAGE, now);
			return;
		}
		if (rauk.combat().reach(t) && (!(read.lookingAt() && read.armed()) || timer > 50)) {
			if (timer > 50) {
				// Done going round: it takes it head on.
				rauk.combat().grab(t);
				begin(Intent.GRAB, now);
			} else {
				engage(t, now);
			}
			return;
		}
		if (timer > 50) {
			// Straight in.
			spotLabel = "flank: straight in";
			if (timer % 6 == 1) {
				rauk.mover().follow(t, GALLOP, 1.4);
			}
			return;
		}
		if (timer % 6 == 1 || !rauk.mover().isMoving()) {
			Vec3 look = t.getViewVector(1f).multiply(1, 0, 1).normalize();
			Vec3 from = rauk.position().subtract(t.position()).multiply(1, 0, 1).normalize();
			// Round on whichever side it is already, wide while in front of the prey, close behind it.
			double side = look.z * from.x - look.x * from.z >= 0 ? 1 : -1;
			boolean inFront = look.dot(from) > Math.cos(Math.toRadians(80));
			double angle = Math.toRadians(inFront ? 100 : 155) * side;
			Vec3 aim = t.position().add(look.yRot((float) angle).scale(inFront ? 3.4 : 2.2));
			SurfaceNode node = floorNear(new RaukPathfinder(level), BlockPos.containing(aim), 2);
			spot = node != null ? node.position() : aim;
			rauk.mover().moveTo(spot, null, HURRY + 3, RaukPathfinder.Costs.NONE);
		}
		spotLabel = "flank";
	}

	/** Waiting beside a door rather than on a ceiling: it grabs as soon as anyone comes within reach. */
	private boolean doorAmbush;

	/** Spots tried for a reach-in that it couldn't get to, and how many times the arm came back empty. */
	private final List<BlockPos> tried = new ArrayList<>();
	private int misses;

	/**
	 * Prey it can't get to but can nearly touch: in a vent, a gap, a window, up on a gallery or a
	 * landing (design doc 7.1). It gets as close as it can, puts one long arm in, and if it catches
	 * the prey, drags it out into the grab.
	 */
	private void reachIn(ServerLevel level, long now) {
		LivingEntity t = targetEntity(level);
		if (t == null) {
			done = true;
			return;
		}
		RaukCombat combat = rauk.combat();
		watch(t, null, 1);
		rauk.setTension(0.85f);
		rauk.setFloorStyle(RaukPosture.QUADRUPED);
		if (phase == 1) {
			// The arm is in, feeling for it.
			rauk.stopWalking();
			rauk.mover().setPaused(true);
			rauk.setAction(RaukMind.ACTION_REACH, t.getBoundingBox().getCenter());
			spotLabel = "reach in";
			if (timer >= 10) {
				rauk.setAction(RaukMind.ACTION_NONE, t.position());
				if (combat.reachIn(t)) {
					combat.grab(t);
					begin(Intent.GRAB, now);
				} else {
					phase = 0;
					timer = 0;
					misses++;
				}
			}
			return;
		}
		if (combat.reachIn(t)) {
			phase = 1;
			timer = 0;
			rauk.stopWalking();
			return;
		}
		if (spot != null && rauk.mover().unreachable() && now - lastPlan > 10) {
			tried.add(BlockPos.containing(spot));
			spot = null;
		}
		if (spot == null) {
			spot = reachSpot(level, t);
		}
		if (spot == null && misses == 0 && timer < 5) {
			// Nowhere to reach in from: it isn't that sort of place after all. It thinks again,
			// without backing off.
			reachReady = now + 100;
			done = true;
			return;
		}
		if (spot == null || timer > 300 || misses >= 3) {
			// It can't get at it. It goes, and comes back when the prey has to come out.
			reachReady = now + 600;
			strikeReady = now + 60;
			begin(Intent.DISENGAGE, now);
			return;
		}
		spotLabel = "get at it";
		go(spot, null, HURRY, RaukPathfinder.Costs.NONE, 40, now);
	}

	/** The floor nearest the prey from which its eyes are within arm's reach of it, with nothing in the way. */
	@Nullable
	private Vec3 reachSpot(ServerLevel level, LivingEntity prey) {
		RaukPathfinder finder = new RaukPathfinder(level);
		Vec3 c = prey.getBoundingBox().getCenter();
		BlockPos base = prey.blockPosition();
		Vec3 best = null;
		double bestScore = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(base.offset(-4, -5, -4), base.offset(4, 1, 4))) {
			if (tried.contains(p)) {
				continue;
			}
			SurfaceNode node = finder.node(p, Direction.DOWN);
			if (node == null) {
				continue;
			}
			Vec3 eye = node.position().add(0, Math.min(2.2, node.posture().eyeHeight), 0);
			double d = eye.distanceTo(c);
			if (d > 3.6) {
				continue;
			}
			// The arm goes in from the shoulder, which can be lower than the eyes.
			Vec3 shoulder = node.position().add(0, Math.min(1.4, node.posture().height / 2), 0);
			if (level.clip(new ClipContext(eye, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rauk)).getType() != HitResult.Type.MISS
					&& level.clip(new ClipContext(shoulder, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rauk))
							.getType() != HitResult.Type.MISS) {
				continue;
			}
			double score = d + node.position().distanceTo(rauk.position()) * 0.05;
			if (score < bestScore) {
				bestScore = score;
				best = node.position();
			}
		}
		return best;
	}

	private long reachUntil;
	private boolean leaptStrike, followed;

	private void chase(ServerLevel level, long now) {
		RaukMind mind = rauk.mind();
		LivingEntity t = targetEntity(level);
		if (t == null || rauk.panting() || (!mind.sees(target) && timer > 40)) {
			// The burst is spent, or it has lost sight: it simply stops.
			chaseEnded = now;
			rauk.stopWalking();
			done = true;
			return;
		}
		rauk.setFloorStyle(RaukPosture.QUADRUPED);
		rauk.setTension(1);
		watch(t, null, 1);
		// Leading the prey: heading for where it will be, not where it is.
		double d = rauk.position().distanceTo(t.position());
		Vec3 v = mind.velocity(t).scale(20);
		Vec3 aim = t.position().add(v.scale(Math.min(d / GALLOP, 1.2)));
		spot = aim;
		spotLabel = "chase (stamina " + Math.round(rauk.stamina() * 100) + "%)";
		if (timer % 6 == 1) {
			rauk.mover().moveTo(aim, null, GALLOP);
		}
		if (rauk.combat().reach(t)) {
			engage(t, now);
		} else if (timer > 10 && rauk.mover().unreachable() && d < 8 && now >= reachReady) {
			begin(Intent.REACH, now);
		}
	}

	private void disengage(ServerLevel level, long now) {
		RaukMind mind = rauk.mind();
		LivingEntity t = targetEntity(level);
		if (reachUntil > 0 && now >= reachUntil) {
			rauk.setAction(RaukMind.ACTION_NONE, rauk.position());
			reachUntil = 0;
		}
		Belief b = target == null ? null : mind.beliefs().get(target);
		Vec3 preyEye = t != null ? t.getEyePosition() : b != null ? b.position.add(0, 1.6, 0) : null;
		if (preyEye == null) {
			done = true;
			return;
		}
		if (spot == null) {
			hideSpot(level, preyEye);
			if (spot == null) {
				done = true;
				return;
			}
		}
		rauk.setTension(0.7f);
		rauk.setFloorStyle(RaukPosture.QUADRUPED);
		watch(t, null, 0.8f);
		go(spot, spotSurface, HURRY, HuntCosts.retreat(level, preyEye, t != null ? t.getViewVector(1f) : null)
				.avoiding(mind::danger), 60, now);
		// Followed while it is still healthy: this was never a retreat. Out of sight, it waits for
		// whoever follows to come round the corner (design doc 6.6, the fake retreat).
		if (t != null && mind.sees(target) && rauk.getHealth() > rauk.getMaxHealth() * 0.6f) {
			Vec3 toward = rauk.position().subtract(t.position());
			if (toward.length() < 12 && mind.velocity(t).dot(toward.normalize()) > 0.08) {
				followed = true;
			}
		}
		// Hunting, this only ever lasts a moment (keeping off a flame); playing, it gets right away.
		int lasts = mood == Mood.HUNT ? 50 : 200;
		if (arrived(spot, 1.5) || timer > lasts || (rauk.mover().unreachable() && timer > 30)) {
			if (followed && spot != null) {
				ambushAt(spot, now);
				spotLabel = "fake retreat: waiting";
				return;
			}
			if (mood == Mood.PLAY && target != null) {
				// Gone from where it was seen: it turns up where the prey is going, and waits.
				Belief seenAt = mind.beliefs().get(target);
				Vec3 from = t != null ? t.position() : seenAt != null ? seenAt.position : null;
				Vec3 velocity = t != null ? mind.velocity(t).scale(20) : seenAt != null ? seenAt.velocity : Vec3.ZERO;
				Prediction.Guess g = from == null ? null : Prediction.destination(level, rauk, target, from, velocity);
				if (g != null && g.at().distanceTo(from) > 6) {
					ambushAt(g.at(), now);
					spotLabel = "turns up at " + g.why();
					return;
				}
			}
			done = true;
		}
	}

	/** Somewhere out of the prey's sight and at least 9 blocks from it, preferably up and dark. */
	private void hideSpot(ServerLevel level, Vec3 preyEye) {
		RaukPathfinder finder = new RaukPathfinder(level);
		double bestScore = Double.MAX_VALUE;
		BlockPos base = rauk.blockPosition();
		for (int r = 5; r <= 14; r += 3) {
			for (int a = 0; a < 16; a++) {
				double angle = a * Math.PI / 8;
				BlockPos cell = base.offset((int) Math.round(Math.cos(angle) * r), 0, (int) Math.round(Math.sin(angle) * r));
				SurfaceNode floor = floorNear(finder, cell, 2);
				if (floor == null) {
					continue;
				}
				for (SurfaceNode node : new SurfaceNode[] {floor, ceilingNode(finder, floor.position(), 7)}) {
					if (node == null || node.position().distanceTo(preyEye) < 9) {
						continue;
					}
					Vec3 body = node.position().add(0, node.posture().height / 2, 0);
					if (HuntCosts.visibleFrom(level, preyEye, body)) {
						continue;
					}
					double score = node.position().distanceTo(rauk.position()) * 0.5 + level.getMaxLocalRawBrightness(node.cell()) * 0.4
							- (node.surface() == Direction.UP ? 2 : 0);
					if (score < bestScore) {
						bestScore = score;
						spot = node.position();
						spotSurface = node.surface();
					}
				}
			}
		}
		spotLabel = "get out of sight";
	}

	// --- grabbing -----------------------------------------------------------------------------

	/** Holding prey up at its face while it struggles, then the bite; or it got away. */
	private void grab(ServerLevel level, long now) {
		RaukCombat combat = rauk.combat();
		rauk.mover().setPaused(true);
		rauk.setTension(1);
		if (combat.holding() || combat.pinning()) {
			phase = 1;
			spotLabel = switch (combat.phase()) {
				case RaukCombat.PHASE_LIFT -> "grab: lifting";
				case RaukCombat.PHASE_STRUGGLE -> "grab: struggle";
				case RaukCombat.PHASE_BITE -> "grab: bite";
				default -> "grab: pinned";
			};
			return;
		}
		if (phase == 1) {
			phase = 2;
			timer = 0;
		}
		if (combat.lastKilled()) {
			// The kill: it takes it to a larder to feed.
			spotLabel = "kill";
			if (timer > 20) {
				if (rauk.carrying()) {
					begin(Intent.FEED, now);
				} else {
					hunger = 0.05;
					done = true;
				}
			}
		} else {
			// Got away: its head jerks back, then it comes again, and it doesn't play any more.
			spotLabel = "grab: got away";
			mood = Mood.HUNT;
			if (timer > 20) {
				strikeReady = now + 30;
				done = true;
			}
		}
	}

	// --- feeding ------------------------------------------------------------------------------

	/** Where it feeds: the kill on the floor in front of it. */
	private Vec3 feedAt = Vec3.ZERO;
	private Vec3 lastSmear = Vec3.ZERO;

	/**
	 * A kill (design doc 6.7): dragged in its jaws to a larder, leaving a smear along the floor; fed
	 * on there, tearing and chewing; then the remains are left (bones, torn leather, meat) in a pool
	 * of blood, and it rests. Prey close by interrupts it: the kill is dropped where it is.
	 */
	private void feed(ServerLevel level, long now) {
		RaukMind mind = rauk.mind();
		if (!rauk.carrying()) {
			done = true;
			return;
		}
		// Anyone it sees coming, or hears close, or who walks right up to it: the kill is dropped and it
		// goes for them, at once and without playing.
		LivingEntity intruder = intruder(level);
		if (intruder != null) {
			mind.setListening(false);
			if (phase == 2) {
				hunger = Math.max(0.1, hunger - timer / 260.0 * 0.8);
			}
			leaveRemains(level, phase == 2 ? feedAt : carcassPoint());
			target = intruder.getUUID();
			moodFor = target;
			mood = Mood.HUNT;
			lastSeenTime = now;
			strikeReady = now;
			rauk.notice(intruder.getEyePosition());
			done = true;
			return;
		}
		if (phase == 0) {
			spot = larder(level);
			spotLabel = "drag to larder";
			lastSmear = rauk.position();
			phase = 1;
			timer = 0;
		}
		if (phase == 1) {
			rauk.setTension(0.35f);
			rauk.setFloorStyle(RaukPosture.QUADRUPED);
			watch(null, spot.add(0, 0.5, 0), 0.3f);
			go(spot, Direction.DOWN, 2.6, HuntCosts.quiet(level), 80, now);
			Vec3 drag = carcassPoint();
			if (drag.distanceTo(lastSmear) > 1.1) {
				Vec3 way = drag.subtract(lastSmear);
				BloodBlock.stain(level, drag, BloodBlock.Shape.SMEAR, Direction.getNearest(way.x, 0, way.z));
				lastSmear = drag;
			}
			if (arrived(spot, 1.6) || (rauk.mover().unreachable() && timer > 60) || timer > 1200) {
				rauk.stopWalking();
				Vec3 fwd = Vec3.directionFromRotation(0, rauk.yBodyRot);
				feedAt = rauk.position().add(fwd.scale(2.4)).add(0, 0.1, 0);
				BlockPos pool = BloodBlock.stain(level, feedAt.add(0, 0.4, 0), BloodBlock.Shape.POOL, rauk.getDirection());
				if (pool == null) {
					feedAt = rauk.position().add(0, 0.1, 0);
					BloodBlock.stain(level, feedAt.add(0, 0.4, 0), BloodBlock.Shape.POOL, rauk.getDirection());
				} else {
					feedAt = new Vec3(feedAt.x, pool.getY() + 0.05, feedAt.z);
				}
				phase = 2;
				timer = 0;
			}
			return;
		}
		if (phase == 2) {
			// Head down, tearing at it, chewing, listening between mouthfuls.
			rauk.stopWalking();
			rauk.setTension(0.3f);
			mind.setListening(true);
			spotLabel = "feeding";
			int glance = timer % 90;
			if (glance >= 60 && glance < 78) {
				// Between mouthfuls the head comes up and it looks round, one way then the other.
				Vec3 round = Vec3.directionFromRotation(0, rauk.yBodyRot + (timer / 90 % 2 == 0 ? 50 : -50));
				watch(null, mind.eye().add(round.scale(8)), 0.7f);
				rauk.setAction(RaukMind.ACTION_NONE, feedAt);
				spotLabel = "feeding: looks round";
				return;
			}
			watch(null, feedAt, 0.4f);
			rauk.setAction(RaukMind.ACTION_FEED, feedAt);
			int beat = timer % 40;
			if (beat == 8) {
				RaukSounds.event(rauk, RaukSounds.EV_TEAR);
				level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
						net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState()), feedAt.x, feedAt.y + 0.4, feedAt.z,
						14, 0.3, 0.2, 0.3, 0.15);
				if (rauk.getRandom().nextFloat() < 0.4f) {
					BloodBlock.stain(level, feedAt.add((rauk.getRandom().nextDouble() - 0.5) * 2.5, 0.4,
							(rauk.getRandom().nextDouble() - 0.5) * 2.5), BloodBlock.Shape.DROPS, rauk.getDirection());
				}
			} else if (beat == 20 || beat == 30) {
				RaukSounds.event(rauk, RaukSounds.EV_CHEW);
			}
			if (timer > 260) {
				mind.setListening(false);
				leaveRemains(level, feedAt);
				hunger = 0.05;
				phase = 3;
				timer = 0;
			}
			return;
		}
		done = true;
	}

	/**
	 * Prey that has come to it while it feeds: seen within sixteen blocks, heard within eight in the
	 * last second, or (sneaking or not) right up beside it.
	 */
	@Nullable
	private LivingEntity intruder(ServerLevel level) {
		RaukMind mind = rauk.mind();
		long now = level.getGameTime();
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity p : mind.preys()) {
			double d = p.position().distanceTo(rauk.position());
			Belief b = mind.beliefs().get(p.getUUID());
			boolean sensed = d < 4.5 || (mind.sees(p.getUUID()) && d < 16)
					|| (b != null && now - b.lastSensed < 20 && d < 8);
			if (sensed && d < bestDistance) {
				best = p;
				bestDistance = d;
			}
		}
		if (best != null && !mind.sees(best.getUUID())) {
			mind.belief(best.getUUID()).observe(best.position(), null, 1.0, 0.9, Belief.Sense.TOUCH, now);
		}
		return best;
	}

	/** Drops whatever it carries where it is, as remains (it died with it in its jaws). */
	public void dropKill(ServerLevel level) {
		if (rauk.carrying()) {
			leaveRemains(level, carcassPoint());
		}
	}

	/** The carried kill: hanging from its jaws, its lower end dragging on the floor. */
	private Vec3 carcassPoint() {
		Vec3 eye = rauk.mind().eye();
		Vec3 fwd = Vec3.directionFromRotation(0, rauk.yBodyRot);
		return new Vec3(eye.x, rauk.getY() + 0.3, eye.z).add(fwd.scale(0.4));
	}

	/**
	 * What is left of a kill: bones, torn leather (the clothes), meat gone bad, in a pool of blood.
	 * They never despawn: finding a larder is one of the worst things that can happen to you.
	 */
	private void leaveRemains(ServerLevel level, Vec3 at) {
		rauk.setCarcass(null);
		rauk.setAction(RaukMind.ACTION_NONE, at);
		BloodBlock.stain(level, at.add(0, 0.4, 0), BloodBlock.Shape.POOL, rauk.getDirection());
		var random = rauk.getRandom();
		List<net.minecraft.world.item.ItemStack> remains = new ArrayList<>();
		remains.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE, 2 + random.nextInt(3)));
		remains.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH, 1 + random.nextInt(2)));
		remains.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER, 1));
		if (random.nextFloat() < 0.5f) {
			remains.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE_MEAL, 1 + random.nextInt(2)));
		}
		for (var stack : remains) {
			var item = new net.minecraft.world.entity.item.ItemEntity(level, at.x + (random.nextDouble() - 0.5) * 1.2, at.y + 0.2,
					at.z + (random.nextDouble() - 0.5) * 1.2, stack);
			item.setDeltaMovement(Vec3.ZERO);
			item.setUnlimitedLifetime();
			level.addFreshEntity(item);
			rauk.mind().territory.ownItem(item);
		}
	}

	/**
	 * Where it takes a kill: a larder it already uses, if one is near; otherwise a new one, the
	 * darkest place just inside a door it knows, or by its den.
	 */
	private Vec3 larder(ServerLevel level) {
		RaukPathfinder finder = new RaukPathfinder(level);
		for (BlockPos l : larders) {
			if (Vec3.atBottomCenterOf(l).distanceTo(rauk.position()) < 64) {
				SurfaceNode node = floorNear(finder, l, 1);
				if (node != null) {
					return node.position();
				}
			}
		}
		Vec3 best = null;
		double bestScore = Double.MAX_VALUE;
		for (BlockPos door : rauk.mind().territory.known(rauk.blockPosition(), 40,
				s -> s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)) {
			BlockState state = level.getBlockState(door);
			if (!(state.getBlock() instanceof DoorBlock)) {
				continue;
			}
			Direction facing = state.getValue(DoorBlock.FACING);
			Vec3 axis = new Vec3(facing.getStepX(), 0, facing.getStepZ());
			double mySide = Math.signum(rauk.position().subtract(Vec3.atCenterOf(door)).dot(axis));
			for (Direction d : new Direction[] {facing, facing.getOpposite()}) {
				SurfaceNode node = floorNear(finder, door.relative(d, 3), 1);
				if (node == null) {
					continue;
				}
				// Through the door, into the room beyond it, rather than out where it made the kill.
				boolean through = Math.signum(d.getStepX() * axis.x + d.getStepZ() * axis.z) != mySide;
				double score = level.getMaxLocalRawBrightness(node.cell()) + node.position().distanceTo(rauk.position()) * 0.08
						+ node.position().distanceTo(Vec3.atCenterOf(rauk.home())) * 0.04 + (through ? 0 : 8);
				if (score < bestScore) {
					bestScore = score;
					best = node.position();
				}
			}
		}
		if (best == null) {
			SurfaceNode node = floorNear(finder, rauk.home().offset(2, 0, 2), 2);
			best = node != null ? node.position() : Vec3.atBottomCenterOf(rauk.home());
		}
		larders.add(BlockPos.containing(best));
		if (larders.size() > 3) {
			larders.remove(0);
		}
		return best;
	}

	// --- places -------------------------------------------------------------------------------

	/** A floor node at or near a block, searching a little up and down and around. */
	@Nullable
	private static SurfaceNode floorNear(RaukPathfinder finder, BlockPos at, int spread) {
		for (BlockPos p : BlockPos.withinManhattan(at, spread, 2, spread)) {
			SurfaceNode node = finder.node(p, Direction.DOWN);
			if (node != null) {
				return node;
			}
		}
		return null;
	}

	@Nullable
	private static SurfaceNode ceilingNode(RaukPathfinder finder, Vec3 floor, int maxUp) {
		BlockPos base = BlockPos.containing(floor);
		for (int dy = 1; dy <= maxUp; dy++) {
			if (finder.blocked(base.above(dy))) {
				return finder.node(base.above(dy - 1), Direction.UP);
			}
		}
		return null;
	}

	/** The ceiling right above a point, if there is one it can hang from within reach. */
	@Nullable
	private static Vec3 ceilingAbove(RaukPathfinder finder, Vec3 at, int maxUp) {
		SurfaceNode node = ceilingNode(finder, at, maxUp);
		return node == null ? null : node.position();
	}

	private boolean freshScent(long now) {
		ScentData.Hit scent = rauk.mind().scent();
		return scent != null && scent.age() < 6000 && now >= trackBlockedUntil;
	}

	private BlockPos somethingToKnockOn(ServerLevel level) {
		BlockPos center = BlockPos.containing(rauk.mind().eye());
		BlockPos wall = null;
		for (BlockPos p : BlockPos.withinManhattan(center, 3, 2, 3)) {
			BlockState state = level.getBlockState(p);
			if (state.getBlock() instanceof DoorBlock) {
				return p.immutable();
			}
			if (wall == null && state.isCollisionShapeFullBlock(level, p)) {
				wall = p.immutable();
			}
		}
		return wall != null ? wall : rauk.blockPosition().below();
	}

	private Vec3 face(BlockPos block) {
		Vec3 eye = rauk.mind().eye();
		Vec3 c = Vec3.atCenterOf(block);
		Direction toward = Direction.getNearest(eye.x - c.x, eye.y - c.y, eye.z - c.z);
		return c.add(toward.getStepX() * 0.5, toward.getStepY() * 0.5, toward.getStepZ() * 0.5);
	}

	private void status(ServerLevel level, long now) {
		LivingEntity t = targetEntity(level);
		String who = t != null ? " " + t.getName().getString() : "";
		rauk.mind().status = intent.name().toLowerCase() + who + (spotLabel.isEmpty() ? "" : " (" + spotLabel + ")")
				+ String.format(" | %s | hunger %.2f caution %.2f stamina %d%%%s", mood == Mood.HUNT ? "hunting" : "playing", hunger,
						caution, Math.round(rauk.stamina() * 100), rauk.panting() ? " PANTING" : "");
	}

	// --- saving ---------------------------------------------------------------------------------

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putDouble("hunger", hunger);
		tag.putDouble("caution", caution);
		tag.put("lights", lights.save());
		tag.putLongArray("larders", larders.stream().mapToLong(BlockPos::asLong).toArray());
		return tag;
	}

	public void load(CompoundTag tag) {
		if (tag.contains("hunger")) {
			hunger = tag.getDouble("hunger");
			caution = tag.getDouble("caution");
		}
		lights.load(tag.getCompound("lights"));
		larders.clear();
		for (long l : tag.getLongArray("larders")) {
			larders.add(BlockPos.of(l));
		}
	}

	/** The larders it uses (test command and debug). */
	public List<BlockPos> larders() {
		return larders;
	}
}

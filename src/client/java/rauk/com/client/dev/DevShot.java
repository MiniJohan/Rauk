package rauk.com.client.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import rauk.com.Rauk;
import rauk.com.client.anim.RaukAnimator;
import rauk.com.client.combat.RaukCombatClient;
import rauk.com.entity.RaukEntity;

/**
 * Development harness, enabled by {@code -Drauk.devshot=true} (the {@code runDevShot} Gradle
 * task): opens a flat test world, summons Rauk and runs a script of commands, camera moves and
 * screenshots into {@code run/screenshots/}, then quits. Bursts of frames show it moving. Lets
 * the creature be reviewed in the game itself without anyone at the keyboard.
 */
public final class DevShot {
	private enum Stage { WAIT_FOR_MENU, LOADING, SETUP, RUNNING, DONE }

	private sealed interface Step permits Cmd, Wait, Camera, Grab, Burst, Orbit, Follow, Walk, Run, CamFollow, PreyWalk, Struggle, Arrive, Hunt {
	}

	/**
	 * Waits for Rauk to get within reach of a place (relative to the anchor), or for the timeout,
	 * taking a frame every interval ticks if named.
	 */
	private record Arrive(String name, Vec3 target, int timeout, int interval) implements Step {
	}

	/** A command; {x} {y} {z} are replaced by the anchor offset by dx, dy, dz. */
	private record Cmd(String command, double dx, double dy, double dz) implements Step {
	}

	private record Wait(int ticks) implements Step {
	}

	/** Moves the camera: eye and look target relative to the anchor. */
	private record Camera(Vec3 eye, Vec3 target) implements Step {
	}

	private record Grab(String name) implements Step {
	}

	private record Burst(String name, int frames, int interval) implements Step {
	}

	/**
	 * Moves the player (the camera) from one point to another at a speed in blocks per second,
	 * looking at a target (relative to the anchor; null looks at Rauk), taking a frame every
	 * interval ticks if named. Rauk senses the player moving the way it would a real one.
	 */
	private record Walk(String name, Vec3 from, Vec3 to, double speed, Vec3 look, int interval) implements Step {
		int ticks() {
			return Math.max(1, (int) Math.round(from.distanceTo(to) / speed * 20));
		}
	}

	/** From now on the camera keeps this offset from Rauk, looking at it (null stops following). */
	private record CamFollow(Vec3 offset) implements Step {
	}

	/**
	 * Moves the stand-in prey (the villager tagged rauk_prey) from one point to another at a speed,
	 * facing a fixed yaw or (if null) the way it walks, taking a frame every interval ticks if named.
	 */
	private record PreyWalk(String name, Vec3 from, Vec3 to, double speed, Float yaw, int interval) implements Step {
		int ticks() {
			return Math.max(1, (int) Math.round(from.distanceTo(to) / speed * 20));
		}
	}

	/**
	 * The camera player as prey: waits to be grabbed, then fights back, swinging at whatever is in
	 * front of it every so many ticks, taking a frame every other tick until well after it is let go
	 * (or the timeout).
	 */
	private record Struggle(String name, int timeout, int attackEvery) implements Step {
	}

	/**
	 * Lets Rauk hunt the stand-in prey until it has it (or the timeout), logging every second what
	 * it is doing and where (from the integrated server), and calling out any stretch of three
	 * seconds or more it spends standing still while not grabbing, feeding or lying in wait.
	 */
	private record Hunt(String name, int timeout, int interval) implements Step {
	}

	/** Runs code on the client, such as starting to fly. */
	private record Run(java.util.function.Consumer<Minecraft> action) implements Step {
	}

	/** Frames taken with the camera keeping a fixed offset from Rauk as it moves (world axes). */
	private record Follow(String name, Vec3 eye, Vec3 target, int frames, int interval) implements Step {
	}

	/** Frames taken while the camera circles a point (relative to the anchor), from one angle to another. */
	private record Orbit(String name, Vec3 center, double radius, double height, double fromDeg, double toDeg, int frames,
			int interval) implements Step {
		Camera camera(int i) {
			double a = Math.toRadians(fromDeg + (toDeg - fromDeg) * i / Math.max(1, frames - 1));
			return new Camera(center.add(Math.sin(a) * radius, height, -Math.cos(a) * radius), center);
		}
	}

	/**
	 * RAUK_WORLD=normal films in an ordinary generated world instead of the flat one: for the hotel
	 * as world generation makes it.
	 */
	private static final boolean NORMAL_WORLD = "normal".equals(System.getenv("RAUK_WORLD"));
	/**
	 * Any other RAUK_WORLD names a save to look into as it is (a copy of a playtest world): nothing
	 * in it is killed or summoned, and the script only finds its hotel and watches its Rauk.
	 */
	private static final boolean INSPECT = !NORMAL_WORLD && System.getenv("RAUK_WORLD") != null && !System.getenv("RAUK_WORLD").isBlank();
	private static final String WORLD = NORMAL_WORLD ? "rauk_dev_normal"
			: System.getenv("RAUK_WORLD") != null && !System.getenv("RAUK_WORLD").isBlank() ? System.getenv("RAUK_WORLD") : "rauk_dev";

	private static Stage stage = Stage.WAIT_FOR_MENU;
	private static int ticks;
	private static Vec3 anchor;
	private static final List<Step> script = new ArrayList<>();
	private static int step, frame, wait;

	private DevShot() {
	}

	public static void init() {
		if (!Boolean.getBoolean("rauk.devshot")) {
			return;
		}
		Rauk.LOGGER.info("Dev screenshot harness active");
		ClientTickEvents.END_CLIENT_TICK.register(DevShot::tick);
	}

	/** Whether a scene is in this run: all of them, or the ones named in RAUK_SCENES (comma separated). */
	private static boolean scene(String name) {
		String only = System.getenv("RAUK_SCENES");
		return only == null || only.isBlank() || List.of(only.split(",")).contains(name);
	}

	/** Starts counting the frames in which each part of the body is inside a block. */
	private static Run auditOn() {
		return new Run(c -> {
			RaukAnimator.AUDIT.clear();
			RaukAnimator.AUDIT_PARTS.clear();
			RaukAnimator.AUDIT_SEEN.clear();
			RaukAnimator.audit = true;
		});
	}

	/** Stops counting and logs it: per part of the body, the frames it was inside a block, the parts most often in, and where. */
	private static Run auditReport(String shot) {
		return new Run(c -> {
			RaukAnimator.audit = false;
			StringBuilder b = new StringBuilder();
			RaukAnimator.AUDIT.forEach((g, n) -> b.append(g).append(' ').append(n[0]).append('/').append(n[1]).append("  "));
			Rauk.LOGGER.info("Clip audit {}: {}", shot, b);
			// The parts most often in, for each part of the body.
			for (String g : RaukAnimator.AUDIT.keySet()) {
				StringBuilder top = new StringBuilder();
				RaukAnimator.AUDIT_PARTS.entrySet().stream().filter(en -> g.equals(RaukAnimator.auditGroupOf(en.getKey())))
						.sorted((x, y) -> y.getValue() - x.getValue()).limit(5)
						.forEach(en -> top.append(' ').append(en.getKey()).append('=').append(en.getValue()));
				if (!top.isEmpty()) {
					Rauk.LOGGER.info("Clip audit {}:   {}:{}", shot, g, top);
				}
			}
			RaukAnimator.AUDIT_SEEN.forEach(line -> Rauk.LOGGER.info("Clip audit {}:   {}", shot, line));
			RaukAnimator.AUDIT_PARTS.clear();
			RaukAnimator.AUDIT_SEEN.clear();
		});
	}

	/** Puts Rauk back at a start point, standing still and calm, facing the given yaw. */
	private static void reset(double dx, double dz, int yaw, String posture) {
		cmd("rauk stop");
		cmd("rauk forget");
		cmd("rauk rise off");
		cmd("rauk gaze none");
		cmd("rauk attention 0");
		cmd("rauk tension 0.3");
		cmd("rauk posture " + posture);
		cmd("tp @e[type=rauk:rauk,limit=1] {x} {y} {z} " + yaw + " 0", dx, 0, dz);
	}

	/** Absolute block coordinates for an offset from the anchor. */
	private static String at(int dx, int dy, int dz) {
		return (int) Math.floor(anchor.x + dx) + " " + (int) Math.floor(anchor.y + dy) + " " + (int) Math.floor(anchor.z + dz);
	}

	/**
	 * The surface test course, north and east and south of the anchor: a tall pavilion (a roof 8
	 * up, a back wall to climb, lit underneath), a long wall with a single wooden door through it,
	 * and a long wall with a 3-wide, 2-high tunnel through it.
	 */
	private static void course() {
		String stone = "minecraft:stone_bricks";
		// Clear the ground first (a fill may only touch 32768 blocks at once).
		for (int x = -40; x < 40; x += 20) {
			cmd("fill " + at(x, 0, -45) + " " + at(x + 19, 9, 25) + " minecraft:air");
		}
		// Pavilion.
		cmd("fill " + at(-9, 0, -19) + " " + at(9, 8, -2) + " minecraft:air");
		cmd("fill " + at(-9, 8, -19) + " " + at(9, 8, -2) + " " + stone);
		cmd("fill " + at(-9, 0, -19) + " " + at(9, 7, -19) + " " + stone);
		cmd("fill " + at(-9, 0, -2) + " " + at(-9, 7, -2) + " " + stone);
		cmd("fill " + at(9, 0, -2) + " " + at(9, 7, -2) + " " + stone);
		cmd("fill " + at(-8, 5, -18) + " " + at(8, 7, -3) + " minecraft:light[level=15] replace minecraft:air");
		// The doorway wall, west of the pavilion, long enough that the door is the way through.
		cmd("fill " + at(-14, 0, -40) + " " + at(-14, 5, 20) + " " + stone);
		cmd("setblock " + at(-14, 0, -9) + " minecraft:oak_door[facing=east,half=lower,open=false]");
		cmd("setblock " + at(-14, 1, -9) + " minecraft:oak_door[facing=east,half=upper,open=false]");
		// The crawl tunnel, through a tall wall.
		cmd("fill " + at(-13, 0, 8) + " " + at(30, 6, 8) + " " + stone);
		cmd("fill " + at(-2, 0, 6) + " " + at(2, 2, 14) + " " + stone);
		cmd("fill " + at(-1, 0, 6) + " " + at(1, 1, 14) + " minecraft:air");
	}

	private static void fly(boolean flying) {
		script.add(new Run(c -> {
			c.player.getAbilities().flying = flying;
			c.player.onUpdateAbilities();
		}));
	}

	/** The camera player becomes visible prey (creative, so nothing hurts it) with the overlay on. */
	private static void beginSenses(boolean flying) {
		cmd("gamemode creative");
		fly(flying);
		cmd("rauk debug");
	}

	private static void endSenses() {
		cmd("rauk brain off");
		cmd("rauk debug");
		cmd("effect clear @s");
		cmd("gamemode spectator");
	}

	/**
	 * A small test hotel floor east of the course: a 3-high carpeted corridor (open at the west end,
	 * a glass south wall to film through), four rooms with plank floors and wooden doors on its
	 * north side, and a lamp in the wall beside each door, switched by a lever in the corridor.
	 */
	private static void hotel() {
		String sb = "minecraft:stone_bricks";
		cmd("fill " + at(40, 0, -10) + " " + at(90, 6, 14) + " minecraft:air");
		cmd("fill " + at(45, -1, -1) + " " + at(85, -1, 9) + " " + sb);
		cmd("fill " + at(47, -1, 4) + " " + at(81, -1, 8) + " minecraft:oak_planks");
		cmd("fill " + at(45, 3, -1) + " " + at(85, 3, 9) + " " + sb);
		cmd("fill " + at(45, 0, -1) + " " + at(85, 2, -1) + " minecraft:glass");
		cmd("fill " + at(45, 0, 3) + " " + at(85, 2, 3) + " " + sb);
		cmd("fill " + at(85, 0, -1) + " " + at(85, 2, 9) + " " + sb);
		cmd("fill " + at(45, 0, 9) + " " + at(85, 2, 9) + " " + sb);
		cmd("fill " + at(45, 0, 4) + " " + at(46, 2, 8) + " " + sb);
		for (int x : new int[] {55, 64, 73}) {
			cmd("fill " + at(x, 0, 4) + " " + at(x, 2, 8) + " " + sb);
		}
		cmd("fill " + at(82, 0, 4) + " " + at(84, 2, 8) + " " + sb);
		cmd("fill " + at(45, 0, 0) + " " + at(84, 0, 2) + " minecraft:red_carpet");
		for (int x : new int[] {50, 59, 68, 77}) {
			cmd("setblock " + at(x, 0, 3) + " minecraft:oak_door[facing=south,half=lower,open=false]");
			cmd("setblock " + at(x, 1, 3) + " minecraft:oak_door[facing=south,half=upper,open=false]");
			cmd("setblock " + at(x + 2, 2, 3) + " minecraft:redstone_lamp");
			cmd("setblock " + at(x + 2, 2, 2) + " minecraft:lever[face=wall,facing=south,powered=true]");
		}
		// Rebuilding knocks carpets and doors loose; it would take the items for things left behind.
		cmd("kill @e[type=item]");
	}

	private static void spawnPrey(int dx, int dz, float yaw) {
		cmd("kill @e[tag=rauk_prey]");
		cmd("summon minecraft:villager {x} {y} {z} {NoAI:1b,Invulnerable:1b,Silent:1b,PersistenceRequired:1b,"
				+ "Tags:[\"rauk_prey\"],Rotation:[" + yaw + "f,0f]}", dx + 0.5, 0, dz + 0.5);
	}

	private static void night(boolean on) {
		cmd(on ? "time set 18000" : "time set 6000");
		cmd(on ? "effect give @s minecraft:night_vision infinite 0 true" : "effect clear @s");
	}

	private static void endHunt() {
		cmd("rauk brain off");
		cmd("rauk debug");
		cmd("kill @e[tag=rauk_prey]");
		night(false);
	}

	/** Whether Rauk has hold of anything: a walking stand-in stops being walked once it is grabbed. */
	private static boolean grabbedPrey(Minecraft client) {
		for (net.minecraft.world.entity.Entity e : client.level.entitiesForRendering()) {
			if (e instanceof RaukEntity rauk && rauk.grabbed() != null) {
				return true;
			}
		}
		return false;
	}

	private static boolean struggled;
	private static int after;

	private static void armedPrey(double dx, double dz, float yaw, boolean invulnerable) {
		cmd("kill @e[tag=rauk_prey]");
		cmd("summon minecraft:villager {x} {y} {z} {NoAI:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:" + (invulnerable ? 1 : 0)
				+ "b,Tags:[\"rauk_prey\"],Rotation:[" + yaw + "f,0f],HandItems:[{id:\"minecraft:iron_sword\",count:1},{}]}", dx, 0, dz);
	}

	/** The hotel with a glass roof, to film from above what happens in the rooms. */
	private static void glassHotel() {
		hotel();
		cmd("fill " + at(45, 3, -1) + " " + at(85, 3, 9) + " minecraft:glass");
	}

	private static void openDoors() {
		for (int x : new int[] {50, 59, 68, 77}) {
			cmd("setblock " + at(x, 0, 3) + " minecraft:oak_door[facing=south,half=lower,open=true]");
			cmd("setblock " + at(x, 1, 3) + " minecraft:oak_door[facing=south,half=upper,open=true]");
		}
	}

	private static void prey(String type, double dx, double dy, double dz, float yaw, boolean invulnerable) {
		cmd("kill @e[tag=rauk_prey]");
		cmd("summon minecraft:" + type + " {x} {y} {z} {NoAI:1b,Silent:1b,PersistenceRequired:1b,Invulnerable:" + (invulnerable ? 1 : 0)
				+ "b,Tags:[\"rauk_prey\"],Rotation:[" + yaw + "f,0f]}", dx, dy, dz);
	}

	/** Step 7: the blood on it, its eyes, and that nothing hurts it. */
	private static void goreScenes() {
		if (scene("bloody")) {
			// Daylight, all the way round: it is never clean.
			reset(0, -34, 90, "quad");
			script.add(new Wait(30));
			script.add(new Orbit("bloody", new Vec3(0, 1.8, -34), 5.5, 1.5, -60, 240, 50, 2));
		}
		if (scene("unhurt")) {
			// Arrows shot into it glance off; it only startles and turns to where they came from.
			reset(0, -34, 90, "quad");
			script.add(new Camera(new Vec3(-6.5, 3.0, -29), new Vec3(0, 1.8, -34)));
			script.add(new Wait(20));
			for (double[] a : new double[][] {{-8, 2.5, -31, 1.4, 0, -0.6}, {-8, 1.5, -36, 1.4, 0.1, 0.4}, {-7, 3.5, -34, 1.4, -0.2, 0}}) {
				cmd("summon minecraft:arrow {x} {y} {z} {Motion:[" + a[3] + "d," + a[4] + "d," + a[5] + "d],pickup:0b}", a[0], a[1], a[2]);
				script.add(new Burst("unhurt_" + (int) a[0] + "_" + (int) a[2], 6, 1));
			}
			script.add(new Burst("unhurt_after", 30, 2));
			cmd("kill @e[type=arrow]");
		}
		if (scene("eyeshine")) {
			// Night, a lamp near its head: its eyes throw back a cold silver-green when it faces the
			// camera, nothing when it looks away, and blink now and then.
			night(true);
			cmd("effect clear @s");
			reset(0.5, -34.5, 0, "quad");
			cmd("setblock {bx} {by} {bz} minecraft:light[level=12]", 0, 3, -30);
			// The camera carries a torch: what lights its eyes is the light you bring.
			cmd("item replace entity @s weapon.mainhand with minecraft:torch");
			script.add(new Camera(new Vec3(0.5, 2.3, -27), new Vec3(0.5, 2.1, -31)));
			script.add(new Wait(30));
			cmd("rauk gaze @s");
			cmd("rauk attention 0.6");
			script.add(new Burst("eyeshine_look", 60, 2));
			cmd("rauk gaze none");
			cmd("rauk attention 0");
			script.add(new Burst("eyeshine_away", 30, 2));
			cmd("setblock {bx} {by} {bz} minecraft:air", 0, 3, -30);
			cmd("item replace entity @s weapon.mainhand with minecraft:air");
			night(false);
		}
		if (scene("combat_bitten")) {
			// The camera is the prey, facing away, and doesn't fight back: it comes up behind, lifts the
			// camera to its face, and bites. The red flash. (Creative: the bite is shown, nobody dies.)
			reset(34.5, -31.5, 0, "quad");
			cmd("kill @e[tag=rauk_prey]");
			cmd("rauk prey creative on");
			cmd("gamemode creative");
			fly(false);
			script.add(new Camera(new Vec3(34.5, 1.62, -24.5), new Vec3(34.5, 1.62, -10)));
			cmd("rauk hunger 1");
			cmd("rauk believe @s 1");
			cmd("rauk brain on");
			script.add(new Struggle("bitten", 400, 100000));
			cmd("rauk brain off");
			cmd("gamemode spectator");
		}
	}

	/** Where the shell scenes build the hotel: its plan origin, from the anchor. */
	private static final Vec3 HOTEL = new Vec3(150, 15, -24);

	/** A plan cell's middle (at its floor), from the anchor. */
	private static Vec3 h(double x, double y, double z) {
		return HOTEL.add(x, y, z);
	}

	private static void cmdAt(String command, Vec3 at) {
		cmd(command, at.x, at.y, at.z);
	}

	/**
	 * The puzzles' groundwork (hotel design 4.8): the front doors behind their shutters; the
	 * flashlight's beam down a dark corridor, on Rauk, on the dormitory table where one lies, and
	 * picked up; a locked staff door, then its key; the service lift without its crank and without
	 * the penthouse key, then with them.
	 */
	private static void groundwork() {
		script.add(new Camera(h(39.5, 2.5, 131), h(39.5, 2, 117)));
		script.add(new Wait(30));
		script.add(new Grab("gw_front"));
		script.add(new Camera(h(39.5, 2.2, 109), h(39.5, 1.5, 118)));
		script.add(new Wait(25));
		script.add(new Grab("gw_front_inside"));
		// Night, the power off, and only the flashlight.
		cmd("effect clear @s");
		cmd("time set midnight");
		cmd("rauk hotel power off");
		cmd("gamemode creative");
		script.add(new Run(c -> {
			c.player.getAbilities().flying = true;
			c.player.onUpdateAbilities();
		}));
		cmd("item replace entity @s weapon.mainhand with rauk:flashlight[rauk:lit=false]");
		script.add(new Camera(h(12, 8.6, 80), h(28, 8.6, 96)));
		script.add(new Wait(30));
		script.add(new Grab("gw_dark"));
		cmd("item replace entity @s weapon.mainhand with rauk:flashlight[rauk:lit=true]");
		script.add(new Wait(40));
		script.add(new Grab("gw_beam"));
		cmdAt("tp @e[type=rauk:rauk] {x} {y} {z} 225 0", h(21, 7, 89));
		script.add(new Camera(h(12, 8.6, 80), h(21, 8.4, 89)));
		script.add(new Wait(40));
		script.add(new Grab("gw_beam_rauk"));
		cmdAt("tp @e[type=rauk:rauk] {x} {y} {z}", h(40, 0, 27));
		// The first flashlight, on the dormitory table by the candle; then picked up.
		script.add(new Camera(h(15, -5.3, 31.5), h(16, -6.4, 35)));
		script.add(new Wait(40));
		script.add(new Grab("gw_dorm"));
		click(16, -6, 35, Direction.UP);
		script.add(new Wait(10));
		script.add(new Run(c -> Rauk.LOGGER.info("Groundwork: flashlights carried after the pick-up: {}",
				c.player.getInventory().countItem(rauk.com.registry.RaukItems.FLASHLIGHT))));
		script.add(new Grab("gw_dorm_taken"));
		// The laundry's door: locked; the keyring; open.
		script.add(new Camera(h(29, -5.4, 21.5), h(29, -6, 24)));
		script.add(new Wait(20));
		click(29, -7, 24, Direction.NORTH);
		script.add(new Wait(5));
		script.add(new Grab("gw_locked"));
		cmd("item replace entity @s weapon.mainhand with rauk:staff_keyring");
		click(29, -7, 24, Direction.NORTH);
		script.add(new Wait(5));
		script.add(new Grab("gw_unlocked"));
		click(29, -7, 24, Direction.NORTH);
		script.add(new Wait(30));
		script.add(new Grab("gw_door_open"));
		cmd("rauk hotel progress");
		// The service lift: no crank, then the crank; floor 4 without the key, then with it.
		cmd("rauk hotel power on");
		cmd("rauk hotel lift service 1");
		cmd("rauk hotel progress set " + rauk.com.hotel.HotelProgress.CRANK + " on");
		cmd("rauk hotel lift service 1");
		script.add(new Wait(200));
		cmd("rauk hotel lift service 4");
		cmd("rauk hotel progress set " + rauk.com.hotel.HotelProgress.PENTHOUSE_KEY + " on");
		cmd("rauk hotel lift service 4");
		script.add(new Wait(200));
		cmd("rauk hotel progress");
		cmd("gamemode spectator");
	}

	/**
	 * Puzzle 1, the Night Ledger (hotel design 4.1): the reception desk and its safe; the ledger
	 * read page by page; the safe dialled to this world's figures, opened, and emptied.
	 */
	private static void nightLedger() {
		cmd("rauk hotel power on");
		cmd("gamemode creative");
		script.add(new Run(c -> {
			c.player.getAbilities().flying = true;
			c.player.onUpdateAbilities();
		}));
		cmd("clear @s");
		script.add(new Camera(h(35.5, 2.3, 109.9), h(31.5, 0.45, 110)));
		script.add(new Wait(40));
		script.add(new Grab("ledger_desk"));
		// The ledger, as the server has it for this world, into the log; then read on the desk.
		script.add(new Run(c -> {
			var level = c.getSingleplayerServer().overworld();
			var book = rauk.com.hotel.NightLedger.ledger(level);
			var pages = book.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT).pages();
			for (int i = 0; i < pages.size(); i++) {
				Rauk.LOGGER.info("Ledger page {}: {}", i + 1, pages.get(i).raw().getString().replace("\n", " / "));
			}
			Rauk.LOGGER.info("Ledger combination: {}", java.util.Arrays.toString(rauk.com.hotel.NightLedger.combination(level)));
		}));
		click(33, 1, 110, Direction.UP);
		script.add(new Wait(10));
		for (int page = 0; page < 9; page++) {
			int at = page;
			script.add(new Run(c -> {
				if (c.screen instanceof net.minecraft.client.gui.screens.inventory.BookViewScreen book) {
					book.setPage(at);
				}
			}));
			script.add(new Wait(3));
			script.add(new Grab("ledger_page_" + page));
		}
		script.add(new Run(c -> c.player.closeContainer()));
		// Close up over the desk. The safe: each dial turned to its figure, a click at a time.
		script.add(new Camera(h(34.2, 1.8, 109.9), h(31.9, 0.4, 109.9)));
		script.add(new Wait(20));
		script.add(new Grab("ledger_safe_shut"));
		for (int[] dials : new int[][] {{0, 1}, {2, 3}}) {
			script.add(new Run(c -> {
				int[] figures = rauk.com.hotel.NightLedger.combination(c.getSingleplayerServer().overworld());
				BlockPos origin = BlockPos.containing(anchor.add(h(31, 0, 109)));
				for (int i : dials) {
					double across = rauk.com.hotel.HotelFurniture.SAFE_DIALS[i] / 16;
					BlockPos part = across < 1 ? origin : origin.south();
					Vec3 at = new Vec3(origin.getX() + 1.0, origin.getY() + 7 / 16.0, origin.getZ() + across);
					for (int k = 0; k < figures[i]; k++) {
						c.gameMode.useItemOn(c.player, net.minecraft.world.InteractionHand.MAIN_HAND,
								new net.minecraft.world.phys.BlockHitResult(at, Direction.EAST, part, false));
					}
				}
			}));
			script.add(new Wait(30));
			script.add(new Grab(dials[0] == 0 ? "ledger_safe_half" : "ledger_safe_open"));
		}
		click(31, 0, 109, Direction.EAST);
		script.add(new Wait(20));
		script.add(new Grab("ledger_safe_empty"));
		script.add(new Run(c -> {
			StringBuilder carried = new StringBuilder();
			for (var stack : c.getSingleplayerServer().getPlayerList().getPlayers().get(0).getInventory().items) {
				if (!stack.isEmpty()) {
					carried.append(stack.getHoverName().getString()).append(" x").append(stack.getCount()).append("; ");
				}
			}
			Rauk.LOGGER.info("Ledger: carrying {}", carried);
		}));
		cmd("rauk hotel progress");
		cmd("gamemode spectator");
	}

	/**
	 * Puzzle 2, the Generator (hotel design 4.2): the fuse from the bar's till, the cold room (its
	 * door unlocked with the keyring) and the laundry's tank; the board filled; a start botched, then
	 * done right; the lights.
	 */
	private static void generatorPuzzle() {
		cmd("rauk hotel power off");
		cmd("gamemode creative");
		script.add(new Run(c -> {
			c.player.getAbilities().flying = true;
			c.player.onUpdateAbilities();
		}));
		cmd("clear @s");
		script.add(new Run(c -> {
			var log = rauk.com.hotel.Generator.log().get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT).pages();
			for (int i = 0; i < log.size(); i++) {
				Rauk.LOGGER.info("Engine log page {}: {}", i + 1, log.get(i).raw().getString().replace("\n", " / "));
			}
		}));
		cmd("item replace entity @s weapon.mainhand with rauk:staff_keyring");
		// The bar's till.
		script.add(new Camera(h(52, 1.8, 37), h(55, 1.2, 37)));
		script.add(new Wait(30));
		script.add(new Grab("gen_bar"));
		click(55, 1, 37, Direction.WEST);
		script.add(new Wait(15));
		script.add(new Wait(5));
		script.add(new Run(c -> Rauk.LOGGER.info("Generator: fuses after the till: {}",
				c.getSingleplayerServer().getPlayerList().getPlayers().get(0).getInventory().countItem(rauk.com.registry.RaukItems.FUSE))));
		script.add(new Grab("gen_till_open"));
		// The cold room: the keyring (in hand since the start), the door, the larder.
		script.add(new Camera(h(5, -5.4, 22), h(5, -6, 20)));
		script.add(new Wait(20));
		click(5, -7, 20, Direction.SOUTH);
		script.add(new Wait(5));
		click(5, -7, 20, Direction.SOUTH);
		script.add(new Wait(20));
		script.add(new Camera(h(5, -5.4, 7.5), h(5, -7, 3.5)));
		script.add(new Wait(30));
		script.add(new Grab("gen_larder"));
		click(6, -7, 3, Direction.UP);
		script.add(new Wait(5));
		script.add(new Run(c -> Rauk.LOGGER.info("Generator: fuses after the larder: {}",
				c.getSingleplayerServer().getPlayerList().getPlayers().get(0).getInventory().countItem(rauk.com.registry.RaukItems.FUSE))));
		// The laundry: the door, the tank.
		script.add(new Camera(h(29, -5.4, 22), h(29, -6, 24)));
		script.add(new Wait(20));
		click(29, -7, 24, Direction.NORTH);
		script.add(new Wait(5));
		click(29, -7, 24, Direction.NORTH);
		script.add(new Wait(20));
		script.add(new Camera(h(24, -5.5, 31), h(24, -10, 34)));
		script.add(new Wait(30));
		script.add(new Grab("gen_tank"));
		click(24, -10, 34, Direction.UP);
		script.add(new Wait(10));
		script.add(new Run(c -> Rauk.LOGGER.info("Generator: fuses carried {}",
				c.getSingleplayerServer().getPlayerList().getPlayers().get(0).getInventory().countItem(rauk.com.registry.RaukItems.FUSE))));
		// The fuse board.
		script.add(new Camera(h(51.5, -12.4, 16), h(54, -12.6, 16)));
		script.add(new Wait(30));
		script.add(new Grab("gen_board_empty"));
		// The three found, into the hand.
		cmd("clear @s rauk:fuse");
		cmd("item replace entity @s weapon.mainhand with rauk:fuse 3");
		for (int i = 0; i < 3; i++) {
			click(54, -13, 16, Direction.WEST);
			script.add(new Wait(4));
		}
		script.add(new Wait(10));
		script.add(new Grab("gen_board_full"));
		// The engine: cranked unprimed (a cough), then started as the log says.
		cmd("item replace entity @s weapon.mainhand with minecraft:air");
		script.add(new Camera(h(37, -12.2, 21.5), h(37, -13.2, 17)));
		script.add(new Wait(30));
		script.add(new Grab("gen_engine_cold"));
		click(38, -13, 18, Direction.SOUTH);
		script.add(new Wait(10));
		script.add(new Grab("gen_cough"));
		for (int i = 0; i < 3; i++) {
			click(36, -13, 18, Direction.SOUTH);
			script.add(new Wait(4));
		}
		click(37, -13, 18, Direction.SOUTH);
		script.add(new Wait(4));
		for (int i = 0; i < 3; i++) {
			click(38, -13, 18, Direction.SOUTH);
			script.add(new Wait(6));
		}
		script.add(new Wait(10));
		click(37, -13, 18, Direction.SOUTH);
		script.add(new Wait(40));
		script.add(new Grab("gen_running"));
		cmd("rauk hotel progress");
		// Upstairs: the lights.
		cmd("effect clear @s");
		script.add(new Camera(h(10.5, 9.2, 18.5), h(10.5, 8, 6)));
		script.add(new Wait(60));
		script.add(new Grab("gen_lights"));
		cmd("gamemode spectator");
	}

	/**
	 * Puzzle 3, the Last Waltz (hotel design 4.3): the letter and the music, into the log; the piano
	 * and the bandstand's drawer; the waltz played key by key with Rauk loose in the ballroom (made
	 * to stop and listen, for the test); the drawer open, the key taken; Rauk once the music stops.
	 */
	private static void lastWaltz() {
		cmd("rauk hotel power on");
		cmd("gamemode creative");
		script.add(new Run(c -> {
			c.player.getAbilities().flying = true;
			c.player.onUpdateAbilities();
		}));
		cmd("clear @s");
		script.add(new Run(c -> {
			for (var book : new net.minecraft.world.item.ItemStack[] {rauk.com.hotel.LastWaltz.letter(), rauk.com.hotel.LastWaltz.music()}) {
				var pages = book.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT).pages();
				for (int i = 0; i < pages.size(); i++) {
					Rauk.LOGGER.info("Waltz page {}: {}", i + 1, pages.get(i).raw().getString().replace("\n", " / "));
				}
			}
		}));
		script.add(new Camera(h(73, 2.7, 11), h(75.5, 1.6, 11)));
		script.add(new Wait(30));
		script.add(new Grab("waltz_piano"));
		script.add(new Camera(h(71, 1.6, 7), h(74, 0.5, 7)));
		script.add(new Wait(20));
		script.add(new Grab("waltz_drawer_shut"));
		// Rauk loose at the far end of the room, and certain to stop and listen.
		script.add(new Run(c -> rauk.com.hotel.LastWaltz.spell = 1));
		cmdAt("tp @e[type=rauk:rauk] {x} {y} {z} 270 0", h(57, 0, 10));
		cmd("rauk brain on");
		script.add(new Camera(h(73.4, 2.7, 11), h(56, 2.5, 10)));
		script.add(new Wait(20));
		int[] waltz = {3, 5, 5, 2, 4, 4, 1, 3, 5, 8, 5, 3};
		for (int n = 0; n < waltz.length; n++) {
			int key = waltz[n];
			script.add(new Run(c -> {
				// The piano's front at x 75, the keys across z from 12 (the pianist's right) to 10.
				double across = 0.5 + (8 - key + 0.5) * 31 / 8.0;
				Vec3 at = anchor.add(h(75, 1, 11)).add(-0.5, 10.5 / 16, 0.5 - across / 16);
				BlockPos pos = BlockPos.containing(at.add(0.01, 0, 0));
				c.gameMode.useItemOn(c.player, net.minecraft.world.InteractionHand.MAIN_HAND,
						new net.minecraft.world.phys.BlockHitResult(at, Direction.WEST, pos, false));
			}));
			script.add(new Wait(10));
			if (n == 5) {
				reportIntent("halfway through the waltz");
				script.add(new Grab("waltz_playing"));
			}
		}
		script.add(new Wait(10));
		reportIntent("as the waltz ends");
		script.add(new Grab("waltz_listening"));
		script.add(new Camera(h(71, 1.6, 7), h(74, 0.5, 7)));
		script.add(new Wait(15));
		script.add(new Grab("waltz_drawer_open"));
		click(74, 0, 7, Direction.WEST);
		script.add(new Wait(10));
		script.add(new Run(c -> Rauk.LOGGER.info("Waltz: study keys carried {}",
				c.getSingleplayerServer().getPlayerList().getPlayers().get(0).getInventory().countItem(rauk.com.registry.RaukItems.STUDY_KEY))));
		script.add(new Grab("waltz_drawer_empty"));
		script.add(new Wait(100));
		reportIntent("after the music stopped");
		cmd("rauk brain off");
		script.add(new Run(c -> rauk.com.hotel.LastWaltz.spell = 0.35));
		cmd("rauk hotel progress");
		cmd("gamemode spectator");
	}

	/**
	 * Puzzle 4, the Collector's Shelves (hotel design 4.4): the catalogue read; a volume picked up
	 * off a table; all six shelved in their numbers; the section of shelves open; the hidden study,
	 * its book, its card and its key.
	 */
	private static void collectorsShelves() {
		cmd("rauk hotel power on");
		cmd("gamemode creative");
		script.add(new Run(c -> {
			c.player.getAbilities().flying = true;
			c.player.onUpdateAbilities();
		}));
		cmd("clear @s");
		script.add(new Run(c -> {
			var level = c.getSingleplayerServer().overworld();
			for (var book : new net.minecraft.world.item.ItemStack[] {rauk.com.hotel.CollectorsShelves.catalogue(level),
					rauk.com.hotel.CollectorsShelves.noSeven(), rauk.com.hotel.CollectorsShelves.codeCard(level)}) {
				var pages = book.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT).pages();
				for (int i = 0; i < pages.size(); i++) {
					Rauk.LOGGER.info("Shelves page {}: {}", i + 1, pages.get(i).raw().getString().replace("\n", " / "));
				}
			}
		}));
		// The wall with the catalogue shelf, and a volume off the table.
		script.add(new Camera(h(16, 8.8, 5), h(20, 8, 5)));
		script.add(new Wait(30));
		script.add(new Grab("shelves_wall"));
		script.add(new Camera(h(5, 9.2, 11.5), h(5, 8, 14)));
		script.add(new Wait(20));
		script.add(new Grab("shelves_volume"));
		click(5, 8, 14, Direction.UP);
		script.add(new Wait(10));
		script.add(new Run(c -> Rauk.LOGGER.info("Shelves: picked up {}", c.getSingleplayerServer().getPlayerList().getPlayers().get(0)
				.getInventory().items.stream().filter(s -> !s.isEmpty()).map(s -> s.getHoverName().getString()).toList())));
		// All six into the shelf, in their numbers, each from the hand.
		script.add(new Camera(h(17.5, 8.8, 6), h(20, 8.5, 6)));
		script.add(new Wait(20));
		for (int slot = 0; slot < 6; slot++) {
			int s = slot;
			script.add(new Run(c -> {
				var server = c.getSingleplayerServer();
				var player = server.getPlayerList().getPlayers().get(0);
				var order = rauk.com.hotel.CollectorsShelves.numberedNames(server.overworld().getSeed());
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, rauk.com.hotel.CollectorsShelves.volume(order[s]));
			}));
			script.add(new Wait(3));
			script.add(new Run(c -> {
				BlockPos shelf = BlockPos.containing(anchor.add(h(20, 8, 6)));
				Vec3 at = new Vec3(shelf.getX(), shelf.getY() + (s < 3 ? 0.75 : 0.25), shelf.getZ() + 0.18 + (s % 3) * 0.32);
				c.gameMode.useItemOn(c.player, net.minecraft.world.InteractionHand.MAIN_HAND,
						new net.minecraft.world.phys.BlockHitResult(at, Direction.WEST, shelf, false));
			}));
			script.add(new Wait(4));
		}
		script.add(new Wait(50));
		cmd("rauk hotel progress");
		script.add(new Camera(h(16, 8.8, 5), h(20, 8, 4)));
		script.add(new Wait(30));
		script.add(new Grab("shelves_open"));
		script.add(new Camera(h(25, 8.8, 6.5), h(25, 7.6, 1)));
		script.add(new Wait(30));
		script.add(new Grab("shelves_study"));
		click(27, 8, 1, Direction.UP);
		script.add(new Wait(10));
		script.add(new Run(c -> Rauk.LOGGER.info("Shelves: penthouse keys {}", c.getSingleplayerServer().getPlayerList().getPlayers().get(0)
				.getInventory().countItem(rauk.com.registry.RaukItems.PENTHOUSE_KEY))));
		cmd("gamemode spectator");
	}

	/**
	 * Puzzle 5, the Stopped Clock (hotel design 4.5): the journal read; the clock in Wennberg's study
	 * set from twenty past six to 11:47, the hour hand sneaking; the clock aside, the control room
	 * open; the lockdown key taken.
	 */
	private static void stoppedClock() {
		cmd("rauk hotel power on");
		cmd("gamemode creative");
		script.add(new Run(c -> {
			c.player.getAbilities().flying = true;
			c.player.onUpdateAbilities();
		}));
		cmd("clear @s");
		script.add(new Run(c -> {
			var level = c.getSingleplayerServer().overworld();
			for (var book : new net.minecraft.world.item.ItemStack[] {rauk.com.hotel.StoppedClock.journal(), rauk.com.hotel.StoppedClock.lastPages(),
					rauk.com.hotel.StoppedClock.codeCard(level)}) {
				var pages = book.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT).pages();
				for (int i = 0; i < pages.size(); i++) {
					Rauk.LOGGER.info("Clock page {}: {}", i + 1, pages.get(i).raw().getString().replace("\n", " / "));
				}
			}
		}));
		script.add(new Camera(h(47, 22.7, 3), h(50, 23.4, 3)));
		script.add(new Wait(30));
		script.add(new Grab("clock_shut"));
		// The hour hand on five, sneaking; then the minutes on twenty-seven.
		script.add(new Run(c -> c.options.keyShift.setDown(true)));
		script.add(new Wait(4));
		for (int i = 0; i < 5; i++) {
			click(50, 23, 3, Direction.WEST);
			script.add(new Wait(3));
		}
		script.add(new Run(c -> c.options.keyShift.setDown(false)));
		script.add(new Wait(4));
		script.add(new Camera(h(47, 22.7, 3), h(50, 23.4, 3)));
		script.add(new Wait(6));
		script.add(new Grab("clock_hour"));
		for (int i = 0; i < 27; i++) {
			click(50, 23, 3, Direction.WEST);
			script.add(new Wait(2));
		}
		script.add(new Wait(30));
		cmd("rauk hotel progress");
		script.add(new Camera(h(47, 22.7, 3.5), h(51, 22, 3)));
		script.add(new Wait(20));
		script.add(new Grab("clock_open"));
		script.add(new Camera(h(52.5, 22.7, 3), h(55, 21.5, 3)));
		script.add(new Wait(20));
		script.add(new Grab("control_room"));
		script.add(new Run(c -> {
			var plan = rauk.com.hotel.HotelBuilder.plan();
			for (int y = 20; y < 24; y++) {
				for (int z = 1; z <= 5; z++) {
					for (int x = 52; x <= 56; x++) {
						if (plan.get(x, y, z).startsWith("rauk:lockdown_key_lying")) {
							BlockPos pos = BlockPos.containing(anchor.add(h(x, y, z)));
							c.gameMode.useItemOn(c.player, net.minecraft.world.InteractionHand.MAIN_HAND,
									new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
						}
					}
				}
			}
		}));
		script.add(new Wait(10));
		script.add(new Run(c -> Rauk.LOGGER.info("Clock: lockdown keys {}", c.getSingleplayerServer().getPlayerList().getPlayers().get(0)
				.getInventory().countItem(rauk.com.registry.RaukItems.LOCKDOWN_KEY))));
		cmd("gamemode spectator");
	}

	/**
	 * The finale (hotel design 4.6), ten times as fast: the lockdown key into the release panel, the
	 * code keyed in, the lever; the shutters rising; the front doors open; out, and dawn.
	 */
	private static void finale() {
		cmd("rauk hotel power on");
		cmd("gamemode creative");
		script.add(new Run(c -> {
			c.player.getAbilities().flying = true;
			c.player.onUpdateAbilities();
			rauk.com.hotel.LockdownRelease.speed = 10;
		}));
		cmd("clear @s");
		cmd("item replace entity @s weapon.mainhand with rauk:lockdown_key");
		var mark = rauk.com.hotel.HotelBuilder.plan().marks.get("release_panel");
		int px = mark.x(), py = mark.y(), pz = mark.z();
		script.add(new Camera(h(px - 2.5, py + 1.2, pz + 0.5), h(px, py + 0.4, pz + 0.5)));
		script.add(new Wait(30));
		script.add(new Grab("finale_panel"));
		click(px, py, pz, Direction.WEST);
		script.add(new Wait(5));
		// The code, key by key: the panel faces west, its keys across z (its own left at high x of the piece).
		double[] cols = {11, 8, 5}, rows = {9.9, 7.9, 5.9, 3.9};
		script.add(new Run(c -> {
			int[] code = rauk.com.hotel.LockdownCode.of(c.getSingleplayerServer().overworld().getSeed());
			Rauk.LOGGER.info("Finale: the code {}", java.util.Arrays.toString(code));
			BlockPos pos = BlockPos.containing(anchor.add(h(px, py, pz)));
			for (int d : code) {
				int row = d == 0 ? 3 : (d - 1) / 3, col = d == 0 ? 1 : (d - 1) % 3;
				Vec3 at = new Vec3(pos.getX() + 14.3 / 16, pos.getY() + rows[row] / 16, pos.getZ() + 1 - cols[col] / 16);
				c.gameMode.useItemOn(c.player, net.minecraft.world.InteractionHand.MAIN_HAND,
						new net.minecraft.world.phys.BlockHitResult(at, Direction.WEST, pos, false));
			}
		}));
		script.add(new Wait(10));
		script.add(new Grab("finale_code"));
		cmd("rauk hotel progress");
		click(px, py, pz + 1, Direction.WEST);
		script.add(new Wait(10));
		cmd("rauk hotel progress");
		script.add(new Grab("finale_lever"));
		// The windows of the garden hall's court side, rising.
		script.add(new Camera(h(39.5, 2.5, 62), h(39.5, 4, 49)));
		script.add(new Wait(60));
		script.add(new Grab("finale_court"));
		script.add(new Wait(60));
		script.add(new Camera(h(39.5, 2.2, 109), h(39.5, 1.5, 118)));
		script.add(new Wait(30));
		script.add(new Grab("finale_front_doors"));
		script.add(new Camera(h(39.5, 3, 131), h(39.5, 2, 117)));
		script.add(new Wait(40));
		cmd("rauk hotel progress");
		script.add(new Grab("finale_out"));
		script.add(new Run(c -> rauk.com.hotel.LockdownRelease.speed = 1));
		cmd("gamemode spectator");
	}

	/** What Rauk is about, into the log. */
	private static void reportIntent(String when) {
		script.add(new Run(c -> {
			for (var e : c.getSingleplayerServer().overworld().getEntities(rauk.com.registry.RaukEntities.RAUK, r -> true)) {
				Rauk.LOGGER.info("Waltz: {}, Rauk {} at {} ({})", when, e.brain().intent(), e.blockPosition().toShortString(), e.posture());
			}
		}));
	}

	/** A click on a block of the hotel (a plan cell), from one side, with whatever is in the main hand. */
	private static void click(int x, int y, int z, Direction side) {
		script.add(new Run(c -> {
			BlockPos pos = BlockPos.containing(anchor.add(h(x, y, z)));
			c.gameMode.useItemOn(c.player, net.minecraft.world.InteractionHand.MAIN_HAND,
					new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos), side, pos, false));
		}));
	}

	/** Rauk put somewhere in the hotel, calm and still. */
	private static void resetIn(Vec3 at, int yaw, String posture) {
		cmd("rauk stop");
		cmd("rauk forget");
		cmd("rauk rise off");
		cmd("rauk gaze none");
		cmd("rauk attention 0");
		cmd("rauk tension 0.3");
		cmd("rauk posture " + posture);
		cmdAt("tp @e[type=rauk:rauk,limit=1] {x} {y} {z} " + yaw + " 0", at);
	}

	/** Writes where Rauk is in the hotel's plan to the log, to check it got where it was sent. */
	private static void reportRauk(String label) {
		script.add(new Run(c -> {
			for (var e : c.level.entitiesForRendering()) {
				if (e instanceof RaukEntity rauk) {
					Vec3 p = rauk.position().subtract(anchor.add(HOTEL).subtract(0.5, 0, 0.5));
					Rauk.LOGGER.info("Hotel walk {}: Rauk at plan {} {} {} ({} {})", label, fmt(p.x), fmt(p.y), fmt(p.z), rauk.posture(),
							rauk.surface());
				}
			}
		}));
	}

	/**
	 * Hotel step 2, the shell: the hotel built in the test world on a plateau of earth up to its
	 * lobby floor, seen from outside, cut open level by level from above, walked through, and Rauk
	 * sent round it.
	 */
	private static void shellScenes() {
		if (!(scene("shell_exterior") || scene("shell_cutaway") || scene("shell_tour") || scene("shell_moves")
				|| scene("shell_corridor") || scene("shell_lift") || scene("shell_tour2") || scene("dressed") || scene("dressed_lit")
				|| scene("power") || scene("doors") || scene("lifts") || scene("hunt_hotel") || scene("multiplayer") || scene("climbs") || scene("atmosphere") || scene("light_survey") || scene("snake_hotel") || scene("snake_corner") || scene("reshaped") || scene("wing_moves") || scene("groundwork") || scene("ledger") || scene("generator") || scene("waltz") || scene("shelves") || scene("clock") || scene("finale") || scene("solutions") || scene("nav_moves") || scene("heat_patrol") || scene("nav_film") || scene("anim_ledge") || scene("col_stairwell"))) {
			return;
		}
		script.add(new Camera(h(40, 45, 70), h(40, 0, 24)));
		script.add(new Wait(100));
		cmd("rauk hotel clear");
		for (int x = -12; x < 92; x += 26) {
			cmd("fill " + at(150 + x, 0, -36) + " " + at(150 + Math.min(x + 25, 91), 13, 35) + " minecraft:dirt");
			cmd("fill " + at(150 + x, 0, 36) + " " + at(150 + Math.min(x + 25, 91), 13, 115) + " minecraft:dirt");
		}
		cmd("fill " + at(138, 14, -36) + " " + at(241, 14, 115) + " minecraft:grass_block");
		cmd("rauk hotel build {bx} {by} {bz}", HOTEL.x, HOTEL.y, HOTEL.z);
		cmd("kill @e[type=item]");
		// Only one Rauk, and it does as it's told: any left in the hotel from an earlier run go.
		cmd("tp @e[type=rauk:rauk] ~ -400 ~");
		cmd("kill @e[type=rauk:rauk]");
		cmdAt("summon rauk:rauk {x} {y} {z} {Rotation:[180f,0f],PersistenceRequired:1b}", h(40, 0, 27));
		script.add(new Wait(5));
		cmd("rauk brain off");
		script.add(new Wait(60));
		if (scene("shell_exterior")) {
			script.add(new Orbit("shell_exterior", h(40, 6, 24), 82, 26, 150, 510, 72, 2));
		}
		if (scene("shell_cutaway")) {
			script.add(new Camera(h(40, 78, 52), h(40, -4, 22)));
			script.add(new Wait(40));
			script.add(new Grab("shell_cut_0"));
			String[] levels = {"4", "3", "2", "1", "B1", "B2"};
			for (int i = 0; i < levels.length; i++) {
				cmd("rauk hotel cut " + levels[i]);
				script.add(new Wait(50));
				script.add(new Grab("shell_cut_" + (i + 1)));
			}
			cmd("rauk hotel restore");
			script.add(new Wait(40));
		}
		cmd("effect give @s minecraft:night_vision infinite 0 true");
		if (scene("shell_tour")) {
			// In through the entrance hall, across the atrium, up the grand staircase to the gallery,
			// round it and off down the west corridor.
			script.add(new Walk("shell_tour", h(39.5, 1.6, 45), h(39.5, 1.6, 30), 3.5, h(39.5, 9, 8), 3));
			script.add(new Walk("shell_tour_b", h(39.5, 1.6, 30), h(39.5, 8.7, 15.5), 3.0, h(39.5, 12, 4), 3));
			script.add(new Walk("shell_tour_c", h(39.5, 8.7, 14.5), h(31.5, 8.7, 14.5), 3.0, h(20, 4, 30), 3));
			script.add(new Walk("shell_tour_d", h(31.5, 8.7, 14.5), h(31.5, 8.7, 22.5), 3.0, h(10, 8.7, 22.5), 3));
			script.add(new Walk("shell_tour_e", h(31.5, 8.7, 22.5), h(8, 8.7, 22.5), 4.0, h(0, 8.7, 22.5), 3));
		}
		if (scene("shell_tour2")) {
			// The ballroom, the bar, the kitchen, the library on 2, the attic.
			script.add(new Walk("shell_ballroom", h(55, 2.2, 17), h(74, 2.2, 5), 3.0, h(80, 4, -2), 3));
			script.add(new Walk("shell_bar", h(48, 1.6, 35), h(57, 1.6, 44), 2.5, h(60, 1.5, 48), 3));
			script.add(new Walk("shell_kitchen", h(7, 1.6, 26), h(7, 1.6, 44), 3.0, h(7, 1.0, 50), 3));
			script.add(new Walk("shell_library", h(2.5, 8.7, 2.5), h(18, 8.7, 17), 2.5, h(22, 9, 22), 3));
			script.add(new Walk("shell_attic", h(3, 22.6, 22.5), h(26, 22.6, 22.5), 3.5, h(40, 22.6, 22.5), 3));
		}
		if (scene("dressed")) {
			dressedTour("dressed");
		}
		if (scene("power")) {
			// The library and the corridor outside as the power comes on, and goes off again.
			cmd("effect clear @s");
			for (Object[] v : new Object[][] {{"power_library", h(10.5, 9.2, 18.5), h(10.5, 8, 6)}, {"power_corridor", h(4.5, 8.8, 35.5), h(30, 8.5, 35.5)}}) {
				script.add(new Camera((Vec3) v[1], (Vec3) v[2]));
				script.add(new Wait(30));
				script.add(new Burst(v[0] + "_off", 6, 3));
				cmd("rauk hotel power on");
				script.add(new Burst(v[0] + "_on", 14, 3));
				cmd("rauk hotel power off");
				script.add(new Burst(v[0] + "_out", 6, 3));
			}
			cmd("effect give @s minecraft:night_vision infinite 0 true");
		}
		if (scene("doors")) {
			doorScenes();
		}
		if (scene("lifts")) {
			liftScenes();
		}
		if (scene("hunt_hotel")) {
			huntHotelScenes();
		}
		if (scene("multiplayer")) {
			multiplayerScenes();
		}
		if (scene("atmosphere")) {
			atmosphereScenes();
		}
		if (scene("light_survey")) {
			// How light the hotel is with the power on, space by space, against hotel design 6.3.
			cmd("rauk hotel power on");
			script.add(new Wait(80));
			script.add(new Run(c -> {
				var level = c.getSingleplayerServer().overworld();
				var frame = rauk.com.hotel.HotelData.get(level).frame();
				for (int[] f : new int[][] {{56, 2, 10}, {56, 4, 10}, {56, 1, 10}, {39, 2, 25}}) {
					var pos = frame.toWorld(f[0], f[1], f[2]);
					Rauk.LOGGER.info("Light probe {} {} {}: {} block light {}", f[0], f[1], f[2], level.getBlockState(pos),
							level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos));
				}
				for (var a : rauk.com.hotel.HotelBuilder.plan().areas) {
					if (a.kind() == rauk.com.hotel.HotelPlan.Kind.CRAWL || a.kind() == rauk.com.hotel.HotelPlan.Kind.OUTSIDE
							|| a.kind() == rauk.com.hotel.HotelPlan.Kind.LIFT || a.kind() == rauk.com.hotel.HotelPlan.Kind.TUNNEL) {
						continue;
					}
					int n = 0, sum = 0, max = 0, bright = 0;
					for (int x = a.x0(); x <= a.x1(); x++) {
						for (int z = a.z0(); z <= a.z1(); z++) {
							var pos = frame.toWorld(x, a.y0(), z).above();
							if (!level.getBlockState(pos).isAir()) {
								continue;
							}
							int l = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos);
							n++;
							sum += l;
							max = Math.max(max, l);
							bright += l >= 8 ? 1 : 0;
						}
					}
					if (n > 0) {
						Rauk.LOGGER.info("Light {} | {} {} {} | avg {} max {} lit8+ {}%", a.name(), a.level(), a.kind(), a.style(),
								String.format("%.1f", sum / (double) n), max, 100 * bright / n);
					}
				}
			}));
			cmd("rauk hotel power off");
		}
		if (scene("climbs")) {
			// Second playtest: the climb up the service stairwell where the flight from 1 to 2 is gone
			// (up the wall, under the landing and round its edge), and out of the spa's pool.
			Object[][] legs = {{"stairwell 1 to 2", h(33, 0, 7), h(33, 7, 8), "st12"},
					{"stairwell 1 to 2, put down half in the wall", h(33, 0, 8), h(33, 7, 8), "st12b"},
					{"passage on 1 to the landing on 3", h(32, 0, 11), h(33, 14, 8), "st13"},
					{"out of the pool", h(65, -10, 35), h(55, -7, 27), "pool"},
					{"out of the pool to the vestibule", h(68, -10, 38), h(54, -7, 22), "pool2"}};
			script.add(new CamFollow(new Vec3(-4, 5, 5)));
			String onlyLegs = System.getenv("RAUK_LEGS");
			for (Object[] leg : legs) {
				if (onlyLegs != null && !onlyLegs.isBlank() && !List.of(onlyLegs.split(",")).contains((String) leg[3])) {
					continue;
				}
				resetIn((Vec3) leg[1], 180, "quad");
				script.add(new Wait(10));
				cmdAt("rauk walk {x} {y} {z} 5", (Vec3) leg[2]);
				script.add(new Arrive("climb_" + leg[3], (Vec3) leg[2], 900, 4));
				reportRauk((String) leg[0]);
			}
			script.add(new CamFollow(null));
		}
		if (scene("col_stairwell")) {
			// The climbs under a stair (design doc 5.12): up the service stairwell where the flight
			// from 1 to 2 is gone, up the wall, under the landing and round its edge; counted.
			Object[][] legs = {{"col_st12", h(33, 0, 7), h(33, 7, 8)}, {"col_st13", h(32, 0, 11), h(33, 14, 8)}};
			script.add(new CamFollow(new Vec3(-4, 5, 5)));
			for (Object[] leg : legs) {
				resetIn((Vec3) leg[1], 180, "quad");
				script.add(new Wait(10));
				script.add(auditOn());
				cmdAt("rauk walk {x} {y} {z} 5", (Vec3) leg[2]);
				script.add(new Arrive((String) leg[0], (Vec3) leg[2], 900, 4));
				script.add(auditReport((String) leg[0]));
			}
			script.add(new CamFollow(null));
		}
		if (scene("snake_hotel")) {
			// Round the corridors on 2 (west, down the west stair corridor, along the south one),
			// cut open above and filmed from over it: 3 wide and turning twice.
			cmd("rauk hotel cut 2");
			script.add(new Wait(40));
			for (boolean on : new boolean[] {false, true}) {
				String shot = "snake_hotel" + (on ? "" : "_old");
				script.add(new Run(c -> RaukAnimator.pathBody = on));
				resetIn(h(20, 7, 35), 90, "quad");
				script.add(new CamFollow(new Vec3(0, 12, 0.01)));
				script.add(new Wait(30));
				script.add(new Run(c -> {
					RaukAnimator.AUDIT.clear();
					RaukAnimator.audit = true;
				}));
				cmdAt("rauk walk {x} {y} {z} 4", h(2, 7, 28));
				script.add(new Arrive(shot + "_a", h(2, 7, 28), 400, 3));
				cmdAt("rauk walk {x} {y} {z} 4", h(20, 7, 22));
				script.add(new Arrive(shot + "_b", h(20, 7, 22), 400, 3));
				script.add(new Run(c -> {
					RaukAnimator.audit = false;
					StringBuilder b = new StringBuilder();
					RaukAnimator.AUDIT.forEach((g, n) -> b.append(g).append(' ').append(n[0]).append('/').append(n[1]).append("  "));
					Rauk.LOGGER.info("Clip audit {}: {}", shot, b);
					RaukAnimator.AUDIT_PARTS.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(10)
							.forEach(en -> Rauk.LOGGER.info("Clip audit {}:   {} {}", shot, en.getKey(), en.getValue()));
					RaukAnimator.AUDIT_SEEN.forEach(line -> Rauk.LOGGER.info("Clip audit {}:   {}", shot, line));
					RaukAnimator.AUDIT_PARTS.clear();
					RaukAnimator.AUDIT_SEEN.clear();
				}));
				script.add(new CamFollow(null));
			}
			script.add(new Run(c -> RaukAnimator.pathBody = true));
			reportRauk("snake");
			cmd("rauk hotel restore");
			script.add(new Wait(40));
		}
		if (scene("reshaped")) {
			// The reshaped hotel (hotel design 14): outside by day, then inside.
			Object[][] outside = {{"front_far", h(39.5, 9, 152), h(39.5, 13, 112)}, {"front_near", h(39.5, 2.5, 131), h(39.5, 6, 117)},
					{"aerial", h(-35, 62, 152), h(40, 5, 62)}, {"side_west", h(-30, 10, 66), h(10, 9, 78)},
					{"court", h(26, 3.5, 53), h(40, 7, 96)}, {"court_back", h(52, 4, 82), h(40, 6, 50)}};
			for (Object[] v : outside) {
				script.add(new Camera((Vec3) v[1], (Vec3) v[2]));
				script.add(new Wait(30));
				script.add(new Grab("reshaped_" + v[0]));
			}
			cmd("effect give @s minecraft:night_vision infinite 0 true");
			Object[][] inside = {{"rotunda", h(34, 2.4, 114), h(41, 5, 103)}, {"rotunda_gallery", h(45, 9.4, 113), h(36, 8, 103)},
					{"palm_court", h(39.5, 2.5, 86), h(39.5, 4, 96)}, {"arcade", h(12, 1.6, 80), h(28, 1.6, 96)},
					{"wing_corridor", h(12, 8.6, 80), h(28, 8.6, 96)}, {"arm_corridor", h(9, 8.6, 40), h(9, 8, 70)},
					{"cinema", h(70.5, 3.5, 65), h(70.5, 2, 50)}, {"wing_room", h(14.5, 8.6, 88), h(16, 8, 95)},
					{"stair_tower", h(14, 9.5, 69), h(14, 6, 74)}, {"lantern_room", h(34, 22, 112), h(41, 22, 104)}};
			for (Object[] v : inside) {
				script.add(new Camera((Vec3) v[1], (Vec3) v[2]));
				script.add(new Wait(25));
				script.add(new Grab("reshaped_" + v[0]));
			}
		}
		if (scene("groundwork")) {
			groundwork();
		}
		if (scene("ledger")) {
			nightLedger();
		}
		if (scene("generator")) {
			generatorPuzzle();
		}
		if (scene("waltz")) {
			lastWaltz();
		}
		if (scene("shelves")) {
			collectorsShelves();
		}
		if (scene("clock")) {
			stoppedClock();
		}
		if (scene("finale")) {
			finale();
		}
		if (scene("solutions")) {
			cmd("rauk hotel solutions");
			script.add(new Wait(20));
		}
		if (scene("snake_corner")) {
			// Up the west stair corridor on 2 and round the corner into the west corridor, toward the
			// camera along it: the way a player sees it come.
			for (boolean on : new boolean[] {false, true}) {
				String shot = "snake_corner" + (on ? "" : "_old");
				script.add(new Run(c -> RaukAnimator.pathBody = on));
				resetIn(h(2, 7, 32), 180, "quad");
				script.add(new Camera(h(9.5, 8.4, 22), h(2, 7.6, 25.5)));
				script.add(new Wait(40));
				cmdAt("rauk walk {x} {y} {z} 3", h(7, 7, 22));
				script.add(new Burst(shot, 60, 3));
			}
			script.add(new Run(c -> RaukAnimator.pathBody = true));
		}
		if (scene("dressed_lit")) {
			// The same rooms as they really look: the power on, no night vision.
			cmd("effect clear @s");
			cmd("rauk hotel power on");
			script.add(new Wait(40));
			dressedTour("lit");
			cmd("rauk hotel power off");
			cmd("effect give @s minecraft:night_vision infinite 0 true");
		}
		if (scene("shell_corridor")) {
			// Galloping the length of the south corridor on 2, seen from behind.
			resetIn(h(5, 7, 35), -90, "quad");
			script.add(new CamFollow(new Vec3(-8, 2.2, 0)));
			script.add(new Wait(20));
			cmdAt("rauk walk {x} {y} {z} 9", h(46, 7, 35));
			script.add(new Burst("shell_corridor", 50, 2));
			reportRauk("corridor");
		}
		if (scene("shell_lift")) {
			// The service lift shaft on its own: from the lobby on 2 up to 3, down to B1, up to 4.
			resetIn(h(40, 7, 6), 180, "quad");
			script.add(new CamFollow(new Vec3(-4, 5, 5)));
			Object[][] legs = {{"lift up to 3", h(40, 14, 7), "lift3"}, {"lift down to B1", h(40, -7, 7), "liftB1"},
					{"lift up to the penthouse", h(40, 21, 9), "lift4"}};
			for (Object[] leg : legs) {
				Vec3 to = (Vec3) leg[1];
				cmdAt("rauk walk {x} {y} {z} 5", to);
				script.add(new Arrive("shell_leg_" + leg[2], to, 1600, 6));
				reportRauk((String) leg[0]);
				script.add(new Run(c -> {
					for (int fy : new int[] {-7, 14}) {
						BlockPos at = BlockPos.containing(anchor.add(h(40, fy, 4)));
						var door = rauk.com.block.hotel.HotelDoorBlock.entity(c.level, at);
						Rauk.LOGGER.info("Gate at {}: {}", fy, door == null ? c.level.getBlockState(at) : door.target() + " / " + door.angle(1)
								+ " " + c.level.getBlockState(at));
					}
				}));
			}
			script.add(new CamFollow(null));
		}
		if (scene("nav_moves")) {
			// Rauk's ways round the reshaped hotel (design doc 4.7): its map, a few routes, then every
			// test leg of the wings and the old building, from one to the next.
			resetIn(h(40, 0, 27), 180, "quad");
			cmd("rauk nav");
			for (Vec3 to : new Vec3[] {h(40, 0, 113), h(36, 14, 114), h(9, -7, 60), h(65, -7, 12), h(40, 21, 9)}) {
				cmdAt("rauk nav route {x} {y} {z}", to);
			}
			script.add(new CamFollow(new Vec3(-4, 6, 5)));
			Object[][] legs = {{"atrium to the rotunda", h(40, 0, 113), "rotunda"}, {"up to the rotunda gallery on 3", h(36, 14, 114), "gallery3"},
					{"west wing corridor on 2", h(20, 7, 89), "wing2"}, {"across to the east wing on 2", h(59, 7, 89), "east2"},
					{"down to the west servants' tunnel", h(9, -7, 60), "tunnel"}, {"up to the attic's west wing", h(20, 21, 89), "attic"},
					{"down to the cinema", h(70, 0, 58), "cinema"}, {"out into the courtyard", h(30, 0, 66), "court"},
					{"into the palm court", h(39, 0, 88), "palm"}, {"up the east tower to 3", h(70, 14, 60), "tower"},
					{"gallery to west corridor", h(10, 7, 22), "west"}, {"into the crawlspace", h(15, 11, 22), "crawl"},
					{"up to the lift lobby on 3", h(40, 14, 7), "up3"}, {"down to B1", h(40, -7, 7), "downB1"},
					{"to the Cabinet", h(65, -7, 12), "den"}, {"down the Cabinet drain", h(53.5, -14, 8), "drain"},
					{"up the lift shaft to the penthouse", h(40, 21, 9), "penthouse"}};
			String only = System.getenv("RAUK_LEGS");
			for (Object[] leg : legs) {
				if (only != null && !only.isBlank() && !List.of(only.split(",")).contains((String) leg[2])) {
					continue;
				}
				Vec3 to = (Vec3) leg[1];
				cmdAt("rauk walk {x} {y} {z} 5", to);
				script.add(new Arrive(System.getenv("RAUK_FILM") != null ? "nav_" + leg[2] : null, to, 1600, 4));
				reportRauk((String) leg[0]);
			}
			script.add(new CamFollow(null));
		}
		animScenes();
		if (scene("nav_film")) {
			// Rauk's way, seen from above with its line drawn (design doc 4.7): the hotel cut open over
			// the lobby floor, and Rauk sent from the atrium to the rotunda, through the west arm and
			// down the slanted wing.
			cmd("rauk hotel cut 1");
			cmd("rauk hotel power on");
			cmd("effect give @s minecraft:night_vision infinite 0 true");
			script.add(new Wait(40));
			resetIn(h(40, 0, 27), 180, "quad");
			cmd("rauk debug");
			script.add(new CamFollow(new Vec3(0.01, 19, 4)));
			script.add(new Wait(20));
			cmdAt("rauk walk {x} {y} {z} 6", h(40, 0, 113));
			script.add(new Arrive("nav_film", h(40, 0, 113), 1600, 3));
			cmd("rauk debug");
			script.add(new CamFollow(null));
			cmd("rauk hotel power off");
			cmd("rauk hotel restore");
			script.add(new Wait(40));
		}
		if (scene("heat_patrol")) {
			// The heat map (design doc 6.5): the library on 2 made warm, as if someone had spent a quarter
			// of an hour in it; with nobody about, Rauk patrols, and should look in on it more than anywhere.
			resetIn(h(40, 0, 27), 180, "quad");
			cmdAt("rauk heat warm {x} {y} {z} 900", h(10, 7, 10));
			cmd("rauk hunger 0.5");
			cmd("rauk brain on");
			script.add(new CamFollow(new Vec3(-4, 6, 5)));
			script.add(new Wait(3600));
			cmd("rauk heat");
			cmd("rauk brain off");
			script.add(new CamFollow(null));
		}
		if (scene("wing_moves")) {
			// The reshaped hotel (hotel design 14): Rauk's ways through the new wings, on every floor.
			resetIn(h(40, 0, 27), 180, "quad");
			script.add(new CamFollow(new Vec3(-4, 6, 5)));
			Object[][] legs = {{"atrium to the rotunda", h(40, 0, 113), "rotunda"}, {"up to the rotunda gallery on 3", h(36, 14, 114), "gallery3"},
					{"west wing corridor on 2", h(20, 7, 89), "wing2"}, {"across to the east wing on 2", h(59, 7, 89), "east2"},
					{"down to the west servants' tunnel", h(9, -7, 60), "tunnel"}, {"up to the attic's west wing", h(20, 21, 89), "attic"},
					{"down to the cinema", h(70, 0, 58), "cinema"}, {"out into the courtyard", h(30, 0, 66), "court"},
					{"into the palm court", h(39, 0, 90), "palm"}, {"up the east tower to 3", h(70, 14, 60), "tower"}};
			for (Object[] leg : legs) {
				Vec3 to = (Vec3) leg[1];
				cmdAt("rauk walk {x} {y} {z} 5", to);
				script.add(new Arrive("wing_leg_" + leg[2], to, 1600, 8));
				reportRauk((String) leg[0]);
			}
			script.add(new CamFollow(null));
		}
		if (scene("shell_moves")) {
			// Up the grand staircase, along the gallery, into the west corridor and up into its
			// crawlspace; then across to the service stairs and up to 3, down to B1 past the missing
			// flight, through the spa to the Cabinet, and down its drain.
			resetIn(h(40, 0, 27), 180, "quad");
			script.add(new Camera(h(47, 4, 31), h(40, 4, 19)));
			script.add(new Wait(20));
			cmdAt("rauk walk {x} {y} {z} 4", h(40, 7, 14));
			script.add(new Burst("shell_move_stair", 50, 2));
			script.add(new Arrive(null, h(40, 7, 14), 400, 1));
			reportRauk("grand staircase");
			// The rest followed from above and behind, seeing through the floors (a spectator's view).
			Object[][] legs = {{"gallery to west corridor", h(10, 7, 22), "west"}, {"into the crawlspace", h(15, 11, 22), "crawl"},
					{"up to the lift lobby on 3", h(40, 14, 7), "up3"}, {"down to B1", h(40, -7, 7), "downB1"},
					{"to the Cabinet", h(65, -7, 12), "den"}, {"down the Cabinet drain", h(53.5, -14, 8), "drain"},
					{"up the lift shaft to the penthouse", h(40, 21, 9), "penthouse"}};
			script.add(new CamFollow(new Vec3(-4, 5, 5)));
			for (Object[] leg : legs) {
				Vec3 to = (Vec3) leg[1];
				cmdAt("rauk walk {x} {y} {z} 5", to);
				script.add(new Arrive("shell_leg_" + leg[2], to, 1600, 6));
				reportRauk((String) leg[0]);
			}
			script.add(new CamFollow(null));
		}
		cmd("effect clear @s");
	}

	/**
	 * The procedural body at the places it went wrong (design doc 5.12): out of the drain hole up
	 * into the Cabinet, a U-turn in the ballroom, up and down the grand staircase, through a shut
	 * door. Each filmed in bursts; the lights on and night vision, to see it.
	 */
	private static void animScenes() {
		String tag = System.getenv("RAUK_TAG") == null ? "" : "_" + System.getenv("RAUK_TAG");
		if (scene("anim_ledge")) {
			script.add(new Run(c -> RaukAnimator.logLimbs = true));
			cmd("rauk hotel power on");
			cmd("effect give @s minecraft:night_vision infinite 0 true");
			// Up the drain from the east drain tunnel on B2 into the Cabinet and out over its east lip
			// along the north wall, seen side on from the south.
			resetIn(h(53.5, -14, 9), 180, "quad");
			script.add(new Camera(h(57.0, -5.5, 8.0), h(55.0, -7.0, 3.0)));
			script.add(new Wait(40));
			cmdAt("rauk walk {x} {y} {z} 3", h(60.5, -7, 3.5));
			script.add(auditOn());
			script.add(new Burst("anim_ledge" + tag, 90, 2));
			script.add(auditReport("anim_ledge"));
			script.add(new Run(c -> RaukAnimator.logLimbs = false));
			reportRauk("ledge");
		}
	}

	/**
	 * The procedural body on open ground in daylight (design doc 5.12), on a pad south of the course:
	 * a U-turn seen from above; up and down a staircase two blocks wide with nothing under it, seen
	 * from the side; through a shut hotel door in a wall; into a dead-end corridor three wide, a
	 * wait at its end, turning round in it and back out, seen from above. Each counts the frames
	 * in which any part of the body is inside a block.
	 */
	private static void animPadScenes() {
		if (!(scene("anim_turn") || scene("anim_stairs") || scene("anim_door") || scene("col_corridor") || scene("col_climb"))) {
			return;
		}
		String tag = System.getenv("RAUK_TAG") == null ? "" : "_" + System.getenv("RAUK_TAG");
		cmd("time set 6000");
		cmd("fill " + at(8, 0, -60) + " " + at(39, 8, -24) + " minecraft:air");
		cmd("fill " + at(8, -1, -60) + " " + at(39, -1, -24) + " minecraft:stone");
		// The dead-end corridor: three wide inside, four high, open to the west and to the sky.
		cmd("fill " + at(14, 0, -57) + " " + at(32, 3, -57) + " minecraft:stone_bricks");
		cmd("fill " + at(14, 0, -53) + " " + at(32, 3, -53) + " minecraft:stone_bricks");
		cmd("fill " + at(33, 0, -57) + " " + at(33, 3, -53) + " minecraft:stone_bricks");
		// A landing sticking out from a wall four blocks up, with nothing under it: the only way onto
		// it is up the wall under it, along its underside and round its edge (the hotel's service
		// stairwell, where the flight is gone).
		cmd("fill " + at(34, 0, -50) + " " + at(38, 7, -50) + " minecraft:stone_bricks");
		cmd("fill " + at(34, 4, -49) + " " + at(38, 4, -47) + " minecraft:stone_bricks");
		// The staircase: four steps up east, then a landing, all two wide, open underneath.
		for (int k = 0; k < 4; k++) {
			cmd("fill " + at(15 + k, k, -29) + " " + at(15 + k, k, -28) + " minecraft:stone_brick_stairs[facing=east]");
		}
		cmd("fill " + at(19, 3, -29) + " " + at(24, 3, -28) + " minecraft:stone_bricks");
		// The wall with the door.
		cmd("fill " + at(25, 0, -34) + " " + at(35, 3, -34) + " minecraft:stone_bricks");
		cmd("setblock " + at(30, 0, -34) + " rauk:hotel_door[facing=south,half=lower,hinge=left,open=false]");
		cmd("setblock " + at(30, 1, -34) + " rauk:hotel_door[facing=south,half=upper,hinge=left,open=false]");
		cmd("kill @e[type=item]");
		// Only one Rauk, the one these scenes move (the rauk commands go to the one nearest the
		// camera): any others left in the world go.
		script.add(new Camera(new Vec3(25, 11, -39.9), new Vec3(25, 0, -40)));
		cmd("tp @e[type=rauk:rauk] ~ -400 ~");
		cmd("kill @e[type=rauk:rauk]");
		cmd("summon rauk:rauk " + at(20, 0, -40) + " {Rotation:[-90f,0f],PersistenceRequired:1b}");
		script.add(new Wait(5));
		cmd("rauk brain off");
		script.add(new Wait(20));
		if (scene("anim_turn")) {
			reset(20, -40, -90, "quad");
			script.add(new Camera(new Vec3(25, 11, -39.9), new Vec3(25, 0, -40)));
			script.add(new Wait(30));
			script.add(auditOn());
			cmd("rauk walk {x} {y} {z} 4", 31, 0, -40);
			script.add(new Wait(30));
			cmd("rauk walk {x} {y} {z} 4", 16, 0, -40);
			script.add(new Burst("anim_turn" + tag, 70, 2));
			script.add(auditReport("anim_turn"));
		}
		if (scene("anim_stairs")) {
			reset(9, -28.5, -90, "quad");
			script.add(new Camera(new Vec3(17, 3.5, -20.5), new Vec3(17, 2, -28.5)));
			script.add(new Wait(30));
			script.add(new Run(c -> RaukAnimator.logLimbs = true));
			script.add(auditOn());
			cmd("rauk walk {x} {y} {z} 3", 22, 4, -28.5);
			script.add(new Burst("anim_stairs_up" + tag, 50, 3));
			script.add(auditReport("anim_stairs_up"));
			script.add(auditOn());
			cmd("rauk walk {x} {y} {z} 3", 8, 0, -28.5);
			script.add(new Burst("anim_stairs_down" + tag, 50, 3));
			script.add(auditReport("anim_stairs_down"));
			script.add(new Run(c -> RaukAnimator.logLimbs = false));
		}
		if (scene("anim_door")) {
			reset(30, -29, 180, "quad");
			script.add(new Camera(new Vec3(35.5, 2.6, -30.5), new Vec3(30, 1.4, -34)));
			script.add(new Wait(30));
			script.add(auditOn());
			cmd("rauk walk {x} {y} {z} 3", 30, 0, -41);
			script.add(new Burst("anim_door" + tag, 60, 2));
			script.add(auditReport("anim_door"));
		}
		if (scene("col_climb")) {
			reset(36.5, -42.5, 180, "quad");
			script.add(new Camera(new Vec3(44.5, 4.5, -45.5), new Vec3(36, 3, -48)));
			script.add(new Wait(30));
			script.add(auditOn());
			cmd("rauk walk {x} {y} {z} 3", 36.5, 5, -48);
			script.add(new Burst("col_climb" + tag, 100, 3));
			script.add(auditReport("col_climb"));
		}
		if (scene("col_corridor")) {
			// In to the end, a wait there, a startle from behind it (it turns round where it stands:
			// there is no room to go round), and back out.
			reset(10.5, -55, -90, "quad");
			script.add(new Camera(new Vec3(23, 16, -54.9), new Vec3(23, 0, -55)));
			script.add(new Wait(30));
			script.add(auditOn());
			cmd("rauk walk {x} {y} {z} 3", 31, 0, -55);
			script.add(new Burst("col_corridor_in" + tag, 70, 2));
			cmd("rauk startle {x} {y} {z}", 14, 1.5, -55);
			script.add(new Burst("col_corridor_turn" + tag, 30, 2));
			cmd("rauk walk {x} {y} {z} 3", 9, 0, -55);
			script.add(new Burst("col_corridor_out" + tag, 70, 2));
			script.add(auditReport("col_corridor"));
		}
		cmd("rauk stop");
	}

	/**
	 * The doors (hotel design 4b): a guest room door on 2 swung every way a door can be (a crack,
	 * the rest of the way slowly, shut, flung open, slammed), seen from the corridor; then Rauk
	 * creeping through one door and charging through another.
	 */
	private static void doorScenes() {
		// First by hand, as a player does it: room 204's door clicked open and shut, then with sneak
		// held (a crack, the rest of the way, eased shut). The door's swing is written to the log.
		Vec3 hand = h(55, 7, 24);
		cmd("gamemode creative");
		// Standing on the corridor floor, not flying: sneaking while flying would sink the camera.
		script.add(new Camera(h(55.5, 8.62, 22.2), h(55.5, 8.2, 24.5)));
		script.add(new Run(c -> {
			c.player.getAbilities().flying = false;
			c.player.onUpdateAbilities();
		}));
		script.add(new Wait(20));
		boolean[] sneaks = {false, false, true, true, true};
		for (int i = 0; i < sneaks.length; i++) {
			boolean sneak = sneaks[i];
			int n = i;
			script.add(new Run(c -> c.options.keyShift.setDown(sneak)));
			script.add(new Wait(3));
			script.add(new Run(c -> {
				BlockPos at = BlockPos.containing(anchor.add(hand));
				var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(at), net.minecraft.core.Direction.NORTH, at, false);
				c.gameMode.useItemOn(c.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
			}));
			script.add(new Burst("door_hand_" + n + (sneak ? "_sneak" : ""), 15, 2));
			script.add(new Run(c -> {
				BlockPos at = BlockPos.containing(anchor.add(hand));
				var door = rauk.com.block.hotel.HotelDoorBlock.entity(c.level, at);
				Rauk.LOGGER.info("Door by hand {}{}: going to {} degrees, now at {}", n, sneak ? " (sneaking)" : "",
						door == null ? "?" : door.target(), door == null ? "?" : door.angle(1));
			}));
		}
		script.add(new Run(c -> c.options.keyShift.setDown(false)));
		cmd("gamemode spectator");
		// Room 202's door, seen from inside the room: it opens inwards, towards whoever is in there.
		Vec3 door = h(17, 7, 24);
		script.add(new Camera(h(16.4, 8.7, 30.5), h(17.5, 8.1, 24.5)));
		script.add(new Wait(30));
		String[] hows = {"ajar", "creep", "close", "slam", "shut"};
		int[] waits = {40, 50, 25, 20, 20};
		for (int i = 0; i < hows.length; i++) {
			cmdAt("rauk hotel door {bx} {by} {bz} " + hows[i], door);
			script.add(new Burst("door_" + i + "_" + hows[i], waits[i] / 2, 2));
		}
		// The double doors of the library, swung as a pair.
		script.add(new Camera(h(11.0, 8.9, 14.5), h(11.0, 8.2, 20.5)));
		script.add(new Wait(20));
		cmdAt("rauk hotel door {bx} {by} {bz} open", h(10, 7, 20));
		script.add(new Burst("door_5_pair", 12, 2));
		cmdAt("rauk hotel door {bx} {by} {bz} close", h(10, 7, 20));
		script.add(new Burst("door_6_pair_close", 12, 2));
		// Rauk stalking slowly into room 203 through its shut door, then galloping out of room 201's.
		resetIn(h(25, 7, 22.5), 0, "quad");
		script.add(new Camera(h(24.0, 9.0, 31.4), h(25.5, 8.1, 24.5)));
		script.add(new Wait(20));
		cmdAt("rauk walk {x} {y} {z} 1.4", h(25.5, 7, 28));
		script.add(new Burst("door_7_rauk_creep", 60, 2));
		reportRauk("crept into room 203");
		resetIn(h(8.5, 7, 29), 180, "quad");
		script.add(new Wait(5));
		reportRauk("put in room 201");
		cmdAt("rauk hotel door {bx} {by} {bz} close", h(8, 7, 24));
		script.add(new Camera(h(8.5, 9.2, 21.2), h(8.5, 7.9, 25.5)));
		script.add(new Wait(30));
		cmdAt("rauk walk {x} {y} {z} 12", h(8.5, 7, 21.5));
		script.add(new Burst("door_8_rauk_charge", 50, 1));
		reportRauk("charged out of room 201");
	}

	/**
	 * Playtest fixes (2026-10-06): Rauk hunting a stand-in in the real hotel, everywhere it was
	 * caught standing about or stuck: up the service stairs after it, at it from the flight below,
	 * a still figure in the lit atrium it has to see for itself, up on a gallery, and someone
	 * walking up to it while it has a kill.
	 */
	private static void huntHotelScenes() {
		String only = System.getenv("RAUK_HUNTS");
		java.util.function.Predicate<String> trial = n -> only == null || only.isBlank() || List.of(only.split(",")).contains(n);
		cmd("rauk hunger 0.55");
		if (trial.test("stairs")) {
			// Up the service stairs from 2 to 3, with Rauk after it from the lift lobby on 2.
			resetIn(h(40, 7, 6), 180, "quad");
			prey("villager", h(32, 7, 10).x, h(32, 7, 10).y, h(32, 7, 10).z, 0, false);
			cmd("rauk believe @e[tag=rauk_prey,limit=1] 1");
			script.add(new CamFollow(new Vec3(-5, 6, 6)));
			cmd("rauk brain on");
			Vec3[] way = {h(32, 7, 10), h(32, 7, 7), h(31, 10, 4), h(31, 10, 2), h(35, 10, 2), h(35, 10, 3),
					h(35, 14, 7), h(33, 14, 7)};
			for (int i = 0; i + 1 < way.length; i++) {
				script.add(new PreyWalk(i == 0 ? "hh_stairs_walk" : null, way[i], way[i + 1], 3.0, null, 4));
			}
			script.add(new Hunt("hh_stairs", 900, 5));
			endHuntHotel();
		}
		if (trial.test("below")) {
			// Standing still on the upper flight from 2 to 3; Rauk comes in at the foot of the stairs on 2,
			// under it. It must go up and round, not reach up through the steps.
			resetIn(h(32, 7, 11), 180, "quad");
			prey("villager", h(35, 12, 5).x, h(35, 12, 5).y, h(35, 12, 5).z, 0, false);
			cmd("rauk believe @e[tag=rauk_prey,limit=1] 0");
			script.add(new CamFollow(new Vec3(-5, 6, 6)));
			cmd("rauk brain on");
			script.add(new Hunt("hh_below", 900, 5));
			endHuntHotel();
		}
		if (trial.test("atrium")) {
			// The lights on; a figure standing still in the atrium ten blocks in front of it. It sees it
			// for itself (nothing told it), and comes.
			cmd("rauk hotel power on");
			resetIn(h(36, 0, 31), 180, "quad");
			prey("villager", h(35, 0, 21).x, h(35, 0, 21).y, h(35, 0, 21).z, 0, false);
			script.add(new Camera(h(45, 4, 31), h(36, 1, 24)));
			cmd("rauk brain on");
			script.add(new Hunt("hh_atrium", 600, 4));
			// It has a kill now; while it drags it off, someone else comes walking up to it.
			script.add(new Wait(120));
			script.add(new Run(c -> {
				RaukEntity r = serverRauk(c);
				if (r == null) {
					return;
				}
				// On the open atrium floor, well clear of the stairs and the lift.
				Vec3 at = anchor.add(h(35, 0, 28));
				Rauk.LOGGER.info("Intruder at {}: Rauk carrying {}, {}", at, r.carrying(), r.mind().status);
				send(c, String.format(Locale.ROOT, "summon minecraft:villager %.2f %.2f %.2f {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"rauk_prey\",\"intruder\"]}",
						at.x, at.y, at.z));
				if (true) {
					return;
				}
				Rauk.LOGGER.info("Intruder: nowhere to put one");
			}));
			// It walks up to it, at a walk, for up to five seconds.
			for (int i = 0; i < 100; i++) {
				script.add(new Run(c -> {
					RaukEntity r = serverRauk(c);
					if (r == null || r.combat().holding()) {
						return;
					}
					for (var e : c.getSingleplayerServer().overworld().getAllEntities()) {
						if (e.getTags().contains("intruder") && e.isAlive() && e.distanceTo(r) > 2) {
							// Along the floor only: never into a block (a stair, a table).
							Vec3 to = r.position().subtract(e.position()).multiply(1, 0, 1).normalize().scale(0.2);
							Vec3 next = e.position().add(to);
							var lvl = c.getSingleplayerServer().overworld();
							BlockPos cell = BlockPos.containing(next);
							if (lvl.getBlockState(cell).getCollisionShape(lvl, cell).isEmpty()
									&& lvl.getBlockState(cell.above()).getCollisionShape(lvl, cell.above()).isEmpty()) {
								send(c, String.format(Locale.ROOT, "tp @e[tag=intruder,limit=1] %.3f %.3f %.3f", next.x, e.getY(), next.z));
							}
						}
					}
				}));
				script.add(new Wait(1));
			}
			script.add(new Hunt("hh_intruder", 400, 4));
			cmd("rauk hotel power off");
			endHuntHotel();
		}
		if (trial.test("cutoff")) {
			// Up the service stairs from 2 to 3 at a run, Rauk in the passage by the lift lobby: it
			// should go up the lift shaft instead and be waiting by the door at the top.
			// RAUK_FILM: the hotel cut open above the 3rd floor and a camera over it, to see it all.
			boolean film = System.getenv("RAUK_FILM") != null;
			if (film) {
				cmd("rauk hotel cut 3");
				script.add(new Wait(20));
			}
			resetIn(h(49, 7, 10), 90, "quad");
			cmd("rauk survey");
			cmd("rauk hunger 0");
			prey("villager", h(34, 7, 10).x, h(34, 7, 10).y, h(34, 7, 10).z, 0, false);
			if (film) {
				script.add(new Camera(h(38, 27, 16), h(37, 12, 6)));
			} else {
				script.add(new CamFollow(new Vec3(-5, 6, 6)));
			}
			script.add(new Wait(10));
			cmd("rauk believe @e[tag=rauk_prey,limit=1] 0");
			cmd("rauk brain on");
			cmd("rauk mood hunt");
			Vec3[] way = {h(34, 7, 10), h(32, 7, 10), h(32, 7, 7), h(31, 10, 4), h(31, 10, 2), h(35, 10, 2),
					h(35, 10, 3), h(35, 14, 7), h(32, 14, 7), h(32, 14, 10), h(30, 14, 11.0)};
			for (int i = 0; i + 1 < way.length; i++) {
				script.add(new PreyWalk(film ? "hh_cutoff_run" + i : i == 0 ? "hh_cutoff_run" : null, way[i], way[i + 1], 4.6, null, film ? 2 : 4));
			}
			script.add(new Hunt("hh_cutoff", film ? 300 : 400, film ? 2 : 4));
			if (film) {
				script.add(new Burst("hh_cutoff_after", 30, 2));
			}
			endHuntHotel();
			if (film) {
				cmd("rauk hotel restore");
				script.add(new Wait(20));
			}
		}
		if (trial.test("gallery")) {
			// Up on the gallery on 2 by the railing, still; Rauk on the atrium floor below.
			resetIn(h(36, 0, 24), 0, "quad");
			prey("villager", h(31, 7, 22).x, h(31, 7, 22).y, h(31, 7, 22).z, 90, false);
			cmd("rauk believe @e[tag=rauk_prey,limit=1] 0");
			script.add(new Camera(h(46, 10, 30), h(34, 4, 22)));
			cmd("rauk brain on");
			script.add(new Hunt("hh_gallery", 900, 5));
			endHuntHotel();
		}
		script.add(new CamFollow(null));
	}

	/**
	 * Playing with friends: in through the coal store (the chute falls in behind), then from outside
	 * onto the rubble as a late arrival would, and taken inside: where each comes back.
	 */
	private static void multiplayerScenes() {
		cmd("rauk hotel restore");
		script.add(new Wait(20));
		cmd("gamemode survival");
		cmd("effect give @s minecraft:resistance infinite 4 true");
		stand(h(4, -7, 29), 90, 0);
		cmdAt("tp @s {x} {y} {z}", h(20, -7, 22));
		script.add(new Wait(30));
		reportMp("inside");
		// Out and up onto the fallen-in chute, like someone arriving late.
		cmdAt("tp @s {x} {y} {z}", h(-3.5, 0.2, 29));
		script.add(new Wait(30));
		reportMp("after stepping on the rubble");
		script.add(new Grab("mp_rubble_in"));
		// Taken in the hotel: where they wake.
		cmd("effect clear @s");
		cmd("kill @s");
		script.add(new Wait(30));
		script.add(new Run(c -> {
			if (c.player != null) {
				c.player.respawn();
				c.setScreen(null);
			}
		}));
		script.add(new Wait(40));
		reportMp("after dying");
		script.add(new Grab("mp_woke"));
		cmd("gamemode spectator");
	}

	private static void reportMp(String label) {
		script.add(new Run(c -> {
			var server = c.getSingleplayerServer();
			var sp = server.getPlayerList().getPlayer(c.player.getUUID());
			var frame = rauk.com.hotel.HotelData.get(server.overworld()).frame();
			int[] at = frame.toPlan(sp.blockPosition());
			var area = rauk.com.hotel.HotelBuilder.plan().owner(at[0], at[1], at[2]);
			BlockPos wake = sp.getRespawnPosition();
			int[] w = wake == null ? null : frame.toPlan(wake);
			var wakeArea = w == null ? null : rauk.com.hotel.HotelBuilder.plan().owner(w[0], w[1], w[2]);
			Rauk.LOGGER.info("Multiplayer {}: player at plan {} {} {} ({}), {}, chute down {}, wakes at {}", label, at[0], at[1], at[2],
					area == null ? "outside" : area.name(), sp.gameMode.getGameModeForPlayer(), rauk.com.hotel.HotelData.get(server.overworld()).chuteDown(),
					w == null ? "spawn" : w[0] + " " + w[1] + " " + w[2] + " (" + (wakeArea == null ? "-" : wakeArea.name()) + ")");
		}));
	}

	/**
	 * The hotel's atmosphere (hotel design 6.3, 6.4): each place shot twice from the same spot at
	 * night, first as plain Minecraft with the brightness turned right up, then as the hotel has it
	 * (the brightness ignored, its haze), with the power off or on.
	 */
	private static void atmosphereScenes() {
		cmd("effect clear @s");
		cmd("time set 18000");
		script.add(new Run(c -> c.options.gamma().set(1.0)));
		Object[][] shots = {
				{"atmo_entrance", h(39, 1.6, 45), h(39, 2, 33), false},
				{"atmo_atrium", h(34, 1.6, 31), h(42, 5, 18), false},
				{"atmo_atrium_lit", h(34, 1.6, 31), h(42, 5, 18), true},
				{"atmo_corridor_lit", h(5, 8.6, 35), h(40, 8.6, 35), true},
				{"atmo_library_lit", h(10, 8.6, 18), h(10, 8, 4), true},
				{"atmo_burned", h(55, 15.6, 25), h(55, 14.5, 33), false},
				{"atmo_spa", h(60, -5.4, 27), h(66, -7, 38), false},
				{"atmo_boiler", h(24, -12.4, 28), h(40, -13, 16), false},
				{"atmo_crawlspace", h(6, 11.6, 22), h(20, 11.3, 22), false}};
		for (Object[] shot : shots) {
			cmd("rauk hotel power " + ((Boolean) shot[3] ? "on" : "off"));
			script.add(new Camera((Vec3) shot[1], (Vec3) shot[2]));
			script.add(new Run(c -> rauk.com.client.fx.HotelAtmosphere.setEnabled(false)));
			script.add(new Wait(40));
			script.add(new Grab(shot[0] + "_off"));
			script.add(new Run(c -> rauk.com.client.fx.HotelAtmosphere.setEnabled(true)));
			script.add(new Wait(70));
			script.add(new Grab(shot[0] + "_on"));
		}
		// Walking into the burned wing on 3, the ash thickening.
		cmd("rauk hotel power off");
		script.add(new Walk("atmo_walk_burned", h(40, 15.6, 22), h(55, 15.6, 22), 2.5, h(70, 15, 22), 3));
		cmd("rauk hotel power off");
		script.add(new Run(c -> c.options.gamma().set(0.5)));
		cmd("time set 6000");
	}

	private static void endHuntHotel() {
		cmd("rauk brain off");
		cmd("kill @e[tag=rauk_prey]");
		// Killed in its grip, the prey becomes a kill in its jaws: let that happen, then forget it.
		script.add(new Wait(10));
		cmd("rauk forget");
		cmd("kill @e[type=item]");
		script.add(new CamFollow(null));
		script.add(new Wait(10));
	}

	/** The test player clicks a block (a lever, a button, a gate), with sneak held or not. */
	private static void click(Vec3 cell, boolean sneak) {
		script.add(new Run(c -> c.options.keyShift.setDown(sneak)));
		script.add(new Wait(3));
		script.add(new Run(c -> {
			BlockPos at = BlockPos.containing(anchor.add(cell));
			var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(at), net.minecraft.core.Direction.NORTH, at, false);
			c.gameMode.useItemOn(c.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
		}));
		script.add(new Wait(2));
		script.add(new Run(c -> c.options.keyShift.setDown(false)));
	}

	/** Stands the test player at a place, looking a way, on its feet (not flying). */
	private static void stand(Vec3 at, int yaw, int pitch) {
		cmdAt("tp @s {x} {y} {z} " + yaw + " " + pitch, at);
		script.add(new Run(c -> {
			c.player.getAbilities().flying = false;
			c.player.onUpdateAbilities();
		}));
		script.add(new Wait(10));
	}

	/**
	 * The lifts (hotel design 3.2), with the power on: riding the guest lift up through the atrium,
	 * the cage's bands going by; riding the service lift up from the pit; the guest car called down
	 * from the atrium floor and seen coming; and the lever in it worked.
	 */
	private static void liftScenes() {
		cmd("gamemode creative");
		cmd("effect clear @s");
		cmd("effect give @s minecraft:night_vision infinite 0 true");
		cmd("rauk hotel power on");
		script.add(new Wait(20));
		// In the guest car at 1, facing out through its gate onto the atrium; up to 3.
		stand(h(44.5, 0, 22.5), -90, 5);
		cmd("rauk hotel lift guest 3");
		script.add(new Burst("lift_guest_up", 58, 2));
		reportPlayer("guest lift up");
		// In the service car in the pit at B2, facing its gate; up to 2.
		stand(h(40.5, -14, 2.2), 0, 5);
		cmd("rauk hotel lift service 2");
		script.add(new Burst("lift_service_up", 52, 3));
		reportPlayer("service lift up");
		// On the atrium floor by the cage: the call button brings the guest car down from 3.
		stand(h(49.5, 0, 24.5), 135, -12);
		click(h(47, 1, 21), false);
		script.add(new Burst("lift_guest_called", 50, 2));
		// Into the car, and the lever: up a floor.
		stand(h(44.5, 0, 22.5), -90, 5);
		click(h(43, 1, 22), false);
		script.add(new Burst("lift_lever_up", 30, 3));
		reportPlayer("lever up");
		cmd("rauk hotel power off");
		cmd("gamemode spectator");
	}

	private static void reportPlayer(String label) {
		script.add(new Run(c -> {
			Vec3 p = c.player.position().subtract(anchor).subtract(HOTEL);
			Rauk.LOGGER.info("Lift test {}: player at plan {} {} {}", label, String.format("%.2f", p.x), String.format("%.2f", p.y),
					String.format("%.2f", p.z));
		}));
	}

	/** A walk through the furnished hotel, room by room, frames named after the room. */
	private static void dressedTour(String prefix) {
		Object[][] walks = {
				{"entrance", h(39.5, 1.6, 46), h(39.5, 1.6, 36), h(39.5, 3, 20)},
				{"atrium", h(34.5, 1.6, 31), h(34.5, 1.6, 19), h(42, 7, 23)},
				{"reception", h(31.5, 1.6, 45), h(29.5, 1.6, 36), h(22, 1.5, 40)},
				{"bar", h(48.5, 1.6, 45.5), h(49.5, 1.6, 34.5), h(57, 1.5, 40)},
				{"dining", h(10.5, 1.6, 18.5), h(10.5, 1.6, 3.5), h(10.5, 1, 0)},
				{"ballroom", h(54.5, 2.2, 10.5), h(70.5, 2.2, 10.5), h(79, 3, 10.5)},
				{"kitchen", h(9.5, 1.6, 26.5), h(9.5, 1.6, 44.5), h(6, 0.5, 47)},
				{"smoking", h(61.5, 1.6, 35.5), h(74.5, 1.6, 43.5), h(79, 0.5, 47)},
				{"library", h(10.5, 8.6, 18.5), h(10.5, 8.6, 4.5), h(10.5, 8.5, 0)},
				{"reading", h(25.5, 8.6, 18.5), h(25.5, 8.6, 14.5), h(25.5, 8, 10)},
				{"room201", h(8.5, 8.6, 25.5), h(8.5, 8.6, 28.5), h(8.5, 7.5, 33)},
				{"corridor2", h(4.5, 8.6, 35.5), h(30.5, 8.6, 35.5), h(60, 8.6, 35.5)},
				{"burned", h(55.5, 15.6, 25.5), h(55.5, 15.6, 29.5), h(55.5, 14.5, 33)},
				{"nursery", h(39.5, 22.6, 38.5), h(40.5, 22.6, 42.5), h(40, 21.5, 47)},
				{"dormitory", h(15.5, -5.4, 26.5), h(15.5, -5.4, 40.5), h(15.5, -6, 47)},
				{"drawing", h(35.5, 22.6, 8.5), h(28.5, 22.6, 6.5), h(22, 22, 5)}};
		for (Object[] w : walks) {
			script.add(new Camera((Vec3) w[1], (Vec3) w[3]));
			script.add(new Wait(10));
			script.add(new Walk(prefix + "_" + w[0], (Vec3) w[1], (Vec3) w[2], 2.2, (Vec3) w[3], 3));
		}
	}

	/** Where the furniture showroom is built, from the anchor. */
	private static final Vec3 SHOWROOM = new Vec3(-70, 0, 40);

	/**
	 * The furniture showroom (/rauk hotel showroom): every piece in its bay, photographed one by
	 * one by day, then the lights again by night.
	 */
	private static void furnitureScenes() {
		if (!scene("furniture")) {
			return;
		}
		cmd("rauk hotel showroom {bx} {by} {bz}", SHOWROOM.x, SHOWROOM.y, SHOWROOM.z);
		cmd("kill @e[type=item]");
		cmd("time set 6000");
		script.add(new Wait(60));
		for (int night = 0; night < 2; night++) {
			if (night == 1) {
				cmd("time set 18000");
				script.add(new Wait(40));
			}
			for (var bay : rauk.com.hotel.HotelShowroom.bays()) {
				var piece = bay.piece();
				if (night == 1 && (piece == null || piece.light == rauk.com.hotel.HotelFurniture.Light.NONE)) {
					continue;
				}
				// Framed on the piece: its middle, and far enough back to see all of it.
				var form = piece == null ? null : piece.form;
				int wide = form == rauk.com.hotel.HotelFurniture.Form.RUN || form == rauk.com.hotel.HotelFurniture.Form.TABLE ? 3
						: piece == null ? 1 : piece.w;
				int deep = form == rauk.com.hotel.HotelFurniture.Form.TABLE ? 2 : piece == null ? 1 : piece.d;
				int tall = form == rauk.com.hotel.HotelFurniture.Form.STACK ? 3 : piece == null ? 2 : piece.h;
				var mount = piece == null ? rauk.com.hotel.HotelFurniture.Mount.WALL : piece.mount;
				double cx = SHOWROOM.x + bay.x() + 1 + wide / 2.0;
				Vec3 mid = switch (mount) {
					case WALL -> new Vec3(cx, SHOWROOM.y + tall / 2.0 + 0.3, SHOWROOM.z + 5.5);
					case CEILING -> new Vec3(cx, SHOWROOM.y + 4.4, SHOWROOM.z + 3.5);
					default -> new Vec3(cx, SHOWROOM.y + tall * 0.4, SHOWROOM.z + 2 + deep / 2.0);
				};
				double back = 0.7 + Math.max(Math.max(wide, deep), tall) * 0.62;
				Vec3 eye = mount == rauk.com.hotel.HotelFurniture.Mount.CEILING ? mid.add(0.7, -1.9, -1.3)
						: mid.add(back * 0.3, 0.5 + back * 0.32, -back);
				script.add(new Camera(eye, mid));
				script.add(new Wait(8));
				script.add(new Grab("furn_" + (night == 1 ? "night_" : "") + bay.id()));
			}
		}
		script.add(new Camera(new Vec3(SHOWROOM.x - 4, SHOWROOM.y + 3, SHOWROOM.z - 6), new Vec3(SHOWROOM.x + 20, SHOWROOM.y + 1, SHOWROOM.z + 4)));
		script.add(new Wait(20));
		script.add(new Grab("furn_overview_night"));
		cmd("time set 6000");
		script.add(new Wait(20));
		script.add(new Grab("furn_overview"));
	}

	/**
	 * A playtest world as it was left: its hotel found, its Rauk looked at where it is (in which
	 * room, doing what), then watched for a minute going about its business.
	 */
	private static void inspectScenes() {
		script.add(new Run(c -> {
			var frame = rauk.com.hotel.HotelData.get(c.getSingleplayerServer().overworld()).frame();
			if (frame == null) {
				Rauk.LOGGER.info("Inspect: no hotel");
				return;
			}
			BlockPos mid = frame.center();
			anchor = new Vec3(mid.getX() + 0.5, mid.getY(), mid.getZ() + 0.5).subtract(h(40, 0, 24));
			Rauk.LOGGER.info("Inspect: hotel middle {} turned {}", mid.toShortString(), frame.rotation());
		}));
		cmd("rauk hotel tp lobby");
		cmd("effect give @s minecraft:night_vision infinite 0 true");
		script.add(new Wait(120));
		script.add(new Run(c -> {
			RaukEntity r = serverRauk(c);
			var server = c.getSingleplayerServer().overworld();
			var frame = rauk.com.hotel.HotelData.get(server).frame();
			if (r == null || frame == null) {
				Rauk.LOGGER.info("Inspect: no Rauk loaded");
				return;
			}
			int[] plan = frame.toPlan(r.blockPosition());
			var area = rauk.com.hotel.HotelBuilder.plan().owner(plan[0], plan[1], plan[2]);
			Rauk.LOGGER.info("Inspect: Rauk at {} = plan {} {} {} in {}; {} {}; carrying {}; home {}; {}", r.blockPosition().toShortString(), plan[0],
					plan[1], plan[2], area == null ? "nowhere" : area.name(), r.posture(), r.surface(), r.carrying(), r.home().toShortString(),
					r.mind().status);
			send(c, String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", r.getX() + 3, r.getY() + 4, r.getZ() + 3));
		}));
		script.add(new Wait(40));
		script.add(new CamFollow(new Vec3(-4, 5, 5)));
		script.add(new Burst("inspect", 6, 10));
		script.add(new Hunt("inspect_watch", 1200, 40));
		script.add(new CamFollow(null));
	}

	/**
	 * The hotel as world generation makes it, in a normal world (RAUK_WORLD=normal): found with
	 * /locate, gone to, seen all round and from above, and its Rauk looked for in the Cabinet.
	 */
	private static void worldgenScenes() {
		if (!scene("shell_worldgen")) {
			return;
		}
		cmd("locate structure rauk:raukvik_grand");
		cmd("rauk hotel info");
		cmd("rauk hotel tp front");
		script.add(new Wait(400));
		// From here on the anchor is set so that h() gives places in this hotel by its plan.
		script.add(new Run(c -> {
			var frame = rauk.com.hotel.HotelData.get(c.getSingleplayerServer().overworld()).frame();
			if (frame == null) {
				Rauk.LOGGER.info("Worldgen: no hotel found");
				return;
			}
			BlockPos mid = frame.center();
			anchor = new Vec3(mid.getX() + 0.5, mid.getY(), mid.getZ() + 0.5).subtract(h(40, 0, 24));
			Rauk.LOGGER.info("Worldgen: hotel middle {} turned {}", mid.toShortString(), frame.rotation());
		}));
		script.add(new Camera(h(40, 30, 110), h(40, 0, 24)));
		script.add(new Wait(200));
		script.add(new Orbit("shell_worldgen", h(40, 4, 24), 95, 34, 0, 360, 72, 3));
		script.add(new Camera(h(40, 140, 24.2), h(40, 0, 24)));
		script.add(new Wait(100));
		script.add(new Grab("shell_worldgen_above"));
		// Inside, furnished and turned with the building.
		cmd("effect give @s minecraft:night_vision infinite 0 true");
		for (String place : new String[] {"lobby", "2", "dorm"}) {
			cmd("rauk hotel tp " + place);
			script.add(new Wait(60));
			script.add(new Grab("shell_worldgen_in_" + place));
		}
		// The puzzles' blocks, with what they keep, as generation made them.
		script.add(new Run(c -> {
			var level = c.getSingleplayerServer().overworld();
			var frame = rauk.com.hotel.HotelData.get(level).frame();
			if (frame == null) {
				return;
			}
			int[][] cells = {{31, 0, 109}, {50, 21, 3}, {20, 8, 6}};
			String[] names = {"reception safe", "study clock", "catalogue shelf"};
			// (On the server's own thread: it hands out its block entities to no other.)
			c.getSingleplayerServer().execute(() -> {
				for (int i = 0; i < cells.length; i++) {
					BlockPos at = frame.toWorld(cells[i][0], cells[i][1], cells[i][2]);
					var be = level.getBlockEntity(at);
					Rauk.LOGGER.info("Worldgen: {} at {}: {} {}", names[i], at.toShortString(), level.getBlockState(at).getBlock(),
							be == null ? "no block entity" : be.getClass().getSimpleName());
				}
			});
		}));
		script.add(new Run(c -> {
			int n = 0;
			for (var e : c.level.entitiesForRendering()) {
				if (e instanceof RaukEntity) {
					n++;
					Rauk.LOGGER.info("Worldgen: a Rauk at {}", e.blockPosition().toShortString());
				}
			}
			Rauk.LOGGER.info("Worldgen: {} Rauks in view", n);
		}));
	}

	/** Review fixes: the back under low ceilings, the arm in the grab, the tail dragging behind. */
	private static void fixScenes() {
		if (scene("corridor_walk")) {
			// On all fours down the 3-high corridor, seen through the glass wall: the back stays under
			// the ceiling, and the tail drags along behind.
			hotel();
			reset(48, 1, -90, "quad");
			script.add(new CamFollow(new Vec3(1, 1.0, -5.5)));
			script.add(new Wait(30));
			cmd("rauk walk {x} {y} {z} 3", 80, 0, 1.5);
			script.add(new Burst("corridor", 70, 2));
		}
		if (scene("grab_arm")) {
			// Side on, close: the grab, the prey held up under its face, the arm bent.
			reset(30.5, -36.5, 0, "quad");
			prey("villager", 30.5, 0, -32.5, 180, true);
			script.add(new Camera(new Vec3(23.5, 3.2, -32), new Vec3(30.5, 2.6, -32)));
			script.add(new Wait(30));
			cmd("rauk grab @e[tag=rauk_prey,limit=1]");
			script.add(new Burst("grab_arm", 45, 2));
			cmd("kill @e[tag=rauk_prey]");
		}
	}

	/**
	 * The body following its path (fix pass, 2026-10-12): a cramped Z of thin blue glass walls, 3
	 * wide and open above, filmed from straight above with north up, so anything of it inside or
	 * through a wall shows. At a walk, at a gallop, and close with the camera over it.
	 */
	private static void snakeScenes() {
		if (!(scene("snake") || scene("snake_fast") || scene("snake_close") || scene("snake_s"))) {
			return;
		}
		String wall = "minecraft:light_blue_stained_glass";
		script.add(new Camera(new Vec3(-25, 22, 69.01), new Vec3(-25, 0, 69)));
		cmd("fill " + at(-48, 0, 55) + " " + at(-4, 9, 96) + " minecraft:air");
		cmd("fill " + at(-48, -1, 55) + " " + at(-4, -1, 96) + " minecraft:grass_block");
		cmd("fill " + at(-42, 0, 61) + " " + at(-8, 3, 77) + " " + wall);
		cmd("fill " + at(-42, 0, 62) + " " + at(-26, 3, 64) + " minecraft:air");
		cmd("fill " + at(-28, 0, 62) + " " + at(-26, 3, 76) + " minecraft:air");
		cmd("fill " + at(-28, 0, 74) + " " + at(-8, 3, 76) + " minecraft:air");
		cmd("fill " + at(-24, 0, 61) + " " + at(-8, 3, 72) + " minecraft:air");
		cmd("fill " + at(-42, 0, 66) + " " + at(-30, 3, 77) + " minecraft:air");
		// South of it a tight S: a jog of four blocks, its two turns closer together than Rauk is long.
		cmd("fill " + at(-43, 0, 85) + " " + at(-13, 3, 93) + " " + wall);
		cmd("fill " + at(-43, 0, 86) + " " + at(-30, 3, 88) + " minecraft:air");
		cmd("fill " + at(-32, 0, 86) + " " + at(-30, 3, 92) + " minecraft:air");
		cmd("fill " + at(-32, 0, 90) + " " + at(-13, 3, 92) + " minecraft:air");
		cmd("fill " + at(-28, 0, 85) + " " + at(-13, 3, 88) + " minecraft:air");
		cmd("fill " + at(-43, 0, 90) + " " + at(-34, 3, 93) + " minecraft:air");
		// One Rauk only, here: any left about the world from earlier runs would take the orders too.
		cmd("tp @e[type=rauk:rauk] ~ -400 ~");
		cmd("kill @e[type=rauk:rauk]");
		cmd("summon rauk:rauk {x} {y} {z} {Rotation:[-90f,0f],PersistenceRequired:1b}", -38, 0, 63);
		// The old ones gone for good before a camera goes looking for one to follow.
		script.add(new Wait(40));
		cmd("rauk brain off");
		// Each run twice: first the way it was (the body turning as one piece), then following its path,
		// counting the frames any part of it is inside a block.
		Object[][] runs = {{"snake", 3.0, 100, 3, "quad"}, {"snake_fast", 8.0, 50, 2, "quad"}, {"snake_upright", 3.0, 100, 3, "upright"},
				{"snake_close", 3.0, 100, 3, "quad"}, {"snake_turn", 3.0, 0, 3, "quad"}, {"snake_s", 3.0, 80, 3, "quad"}};
		for (Object[] run : runs) {
			String name = (String) run[0];
			if (!scene(name) && !((name.equals("snake_upright") || name.equals("snake_turn")) && scene("snake"))) {
				continue;
			}
			for (boolean on : new boolean[] {false, true}) {
				String shot = name + (on ? "" : "_old");
				script.add(new Run(c -> RaukAnimator.pathBody = on));
				boolean jog = name.equals("snake_s");
				if (jog) {
					reset(-40, 87, -90, (String) run[4]);
				} else {
					reset(-38, 63, -90, (String) run[4]);
				}
				if (jog) {
					script.add(new Run(c -> c.options.fov().set(30)));
					script.add(new Camera(new Vec3(-28, 38, 89.01), new Vec3(-28, 0, 89)));
				} else if (name.equals("snake_close")) {
					script.add(new CamFollow(new Vec3(0, 11, 0.01)));
				} else {
					// From high up through a narrow lens, so walls and body are seen nearly flat.
					script.add(new Run(c -> c.options.fov().set(30)));
					script.add(new Camera(new Vec3(-25, 50, 69.01), new Vec3(-25, 0, 69)));
				}
				script.add(new Wait(40));
				script.add(new Run(c -> {
					RaukAnimator.AUDIT.clear();
					RaukAnimator.audit = true;
				}));
				if (name.equals("snake_turn")) {
					// Turning back in the corridor: along it, then straight back the way it came.
					cmd("rauk walk {x} {y} {z} 3", -30, 0, 63);
					script.add(new Arrive(shot + "_a", new Vec3(-30, 0, 63), 160, 3));
					cmd("rauk walk {x} {y} {z} 3", -40, 0, 63);
					script.add(new Arrive(shot + "_b", new Vec3(-40, 0, 63), 220, 3));
				} else if (jog) {
					cmd("rauk walk {x} {y} {z} " + run[1], -16, 0, 91);
					script.add(new Burst(shot, (Integer) run[2], (Integer) run[3]));
				} else {
					cmd("rauk walk {x} {y} {z} " + run[1], -11, 0, 75);
					script.add(new Burst(shot, (Integer) run[2], (Integer) run[3]));
				}
				script.add(new Run(c -> {
					RaukAnimator.audit = false;
					StringBuilder b = new StringBuilder();
					RaukAnimator.AUDIT.forEach((g, n) -> b.append(g).append(' ').append(n[0]).append('/').append(n[1]).append("  "));
					Rauk.LOGGER.info("Clip audit {}: {}", shot, b);
					RaukAnimator.AUDIT_PARTS.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(10)
							.forEach(en -> Rauk.LOGGER.info("Clip audit {}:   {} {}", shot, en.getKey(), en.getValue()));
					RaukAnimator.AUDIT_SEEN.forEach(line -> Rauk.LOGGER.info("Clip audit {}:   {}", shot, line));
					RaukAnimator.AUDIT_PARTS.clear();
					RaukAnimator.AUDIT_SEEN.clear();
					c.options.fov().set(70);
				}));
				script.add(new CamFollow(null));
			}
		}
		script.add(new Run(c -> RaukAnimator.pathBody = true));
	}

	/**
	 * The slanted wall pieces (hotel design 14): a wall at 45 degrees, two thick, the pieces set in
	 * its steps on both sides, filmed from either side and from above.
	 */
	private static void slantTest() {
		if (!scene("slant_test")) {
			return;
		}
		script.add(new Camera(new Vec3(-58, 3, 118), new Vec3(-50, 1.5, 110)));
		script.add(new Wait(40));
		cmd("fill " + at(-60, 0, 100) + " " + at(-40, 6, 120) + " minecraft:air");
		for (int i = 0; i < 10; i++) {
			int x = -55 + i, z = 105 + i;
			cmd("fill " + at(x, 0, z) + " " + at(x, 3, z) + " rauk:ashlar");
			cmd("fill " + at(x + 1, 0, z) + " " + at(x + 1, 3, z) + " rauk:ashlar");
			// South-west side: walls to its north and east, so its hidden half is the north-east one.
			cmd("fill " + at(x, 0, z + 1) + " " + at(x, 3, z + 1) + " rauk:slant_ashlar[facing=west]");
			// North-east side: walls to its south and west.
			cmd("fill " + at(x + 2, 0, z) + " " + at(x + 2, 3, z) + " rauk:slant_damask_green[facing=east]");
		}
		cmd("setblock " + at(-50, 4, 110) + " rauk:brass_letter[facing=south,letter=r]");
		cmd("setblock " + at(-49, 4, 110) + " rauk:brass_letter[facing=south,letter=a]");
		cmd("setblock " + at(-48, 4, 110) + " rauk:festoon[axis=x,lit=true]");
		script.add(new Camera(new Vec3(-58, 3, 118), new Vec3(-50, 1.5, 110)));
		script.add(new Wait(40));
		script.add(new Grab("slant_sw"));
		script.add(new Camera(new Vec3(-42, 3, 102), new Vec3(-50, 1.5, 110)));
		script.add(new Wait(20));
		script.add(new Grab("slant_ne"));
		script.add(new Camera(new Vec3(-50, 14, 110.1), new Vec3(-50, 0, 110)));
		script.add(new Wait(20));
		script.add(new Grab("slant_top"));
		script.add(new Camera(new Vec3(-49.5, 5, 114), new Vec3(-49.5, 4.5, 110)));
		script.add(new Wait(20));
		script.add(new Grab("slant_letters"));
	}

	/** The predator: hunting without stopping, cutting the prey off, and playing with it first. */
	private static void predatorScenes() {
		if (scene("hunt_relentless")) {
			// Hunting, it doesn't stop and stare: an armed villager facing it all the way, and it comes
			// straight on, goes round it for a moment, then takes it head on.
			reset(30.5, -44.5, 0, "quad");
			armedPrey(30.5, -24.5, 180, false);
			cmd("rauk hunger 0.55");
			cmd("rauk debug");
			cmd("rauk believe @e[tag=rauk_prey,limit=1] 1");
			script.add(new Camera(new Vec3(21, 4, -37), new Vec3(30.5, 1.5, -31)));
			cmd("rauk brain on");
			script.add(new Wait(2));
			cmd("rauk mood hunt");
			script.add(new Burst("relentless", 110, 3));
			endHunt();
			cmd("kill @e[type=item]");
		}
		if (scene("hunt_cutoff")) {
			// It has found this villager by the pavilion wall often enough to know it's its place. The
			// villager hurries north past it; rather than give chase it gallops to that place first and
			// is waiting when the villager gets there.
			reset(17.5, 42.5, 90, "quad");
			prey("villager", 5.0, 0, 30.0, 0, false);
			cmd("rauk hunger 0.55");
			cmd("rauk debug");
			cmd("rauk haunt @e[tag=rauk_prey,limit=1] {bx} {by} {bz} 400", 17, 0, 58);
			cmd("rauk believe @e[tag=rauk_prey,limit=1] 1");
			script.add(new Camera(new Vec3(-4, 16, 38), new Vec3(12, 0, 46)));
			cmd("rauk brain on");
			script.add(new Wait(2));
			cmd("rauk mood hunt");
			script.add(new PreyWalk("cutoff_a", new Vec3(5.0, 0, 30.0), new Vec3(5.0, 0, 52.0), 5.0, null, 3));
			script.add(new PreyWalk("cutoff_b", new Vec3(5.0, 0, 52.0), new Vec3(17.0, 0, 58.0), 5.0, null, 3));
			script.add(new Burst("cutoff_after", 50, 3));
			endHunt();
			cmd("kill @e[type=item]");
		}
		if (scene("hunt_play")) {
			// Playing: down the corridor it keeps its distance and holds still while the villager faces
			// it, peeks out of a doorway, melts away when it comes closer; the moment the villager turns
			// its back close by, it strikes.
			hotel();
			openDoors();
			night(true);
			reset(49, 1, -90, "quad");
			cmd("rauk survey");
			cmd("rauk hunger 0.45");
			prey("villager", 75.5, 0, 1.5, 90, false);
			cmd("rauk debug");
			cmd("rauk believe @e[tag=rauk_prey,limit=1] 1");
			script.add(new Camera(new Vec3(84, 2.3, 0.6), new Vec3(56, 1.0, 2)));
			cmd("rauk brain on");
			script.add(new Wait(2));
			cmd("rauk mood play");
			// It watches down the corridor, then walks toward whatever is there, then turns and goes.
			script.add(new PreyWalk("play_a", new Vec3(75.5, 0, 1.5), new Vec3(70.5, 0, 1.5), 1.0, 90f, 4));
			script.add(new Burst("play_b", 50, 4));
			script.add(new PreyWalk("play_c", new Vec3(70.5, 0, 1.5), new Vec3(63.5, 0, 1.5), 1.0, 90f, 4));
			script.add(new Burst("play_d", 25, 4));
			script.add(new PreyWalk("play_e", new Vec3(63.5, 0, 1.5), new Vec3(80.5, 0, 1.5), 1.0, -90f, 4));
			script.add(new Burst("play_f", 40, 3));
			endHunt();
			cmd("kill @e[type=item]");
		}
	}

	/** Step 7: the finished creature's behavior. */
	private static void polishScenes() {
		if (scene("combat_feed")) {
			// It kills a villager in the corridor, drags it in its jaws through a door into a dark room
			// (a smear along the floor), feeds there and leaves the remains in a pool of blood.
			glassHotel();
			night(true);
			reset(49, 1, -90, "quad");
			cmd("rauk survey");
			cmd("rauk home {bx} {by} {bz}", 47, 0, 1);
			cmd("rauk hunger 1");
			prey("villager", 57.5, 0, 1.5, -90, false);
			cmd("rauk debug");
			cmd("rauk believe @e[tag=rauk_prey,limit=1] 1");
			script.add(new CamFollow(new Vec3(-2, 10, -4)));
			cmd("rauk brain on");
			script.add(new Burst("feed", 55, 4));
			// Inside the room it takes the kill to, under the glass: the feeding, and what it leaves.
			script.add(new Camera(new Vec3(53.5, 2.5, 7.8), new Vec3(49.5, 0.2, 5)));
			script.add(new Burst("feed_room", 90, 4));
			endHunt();
			cmd("kill @e[type=item]");
		}
		if (scene("combat_reach")) {
			// A pig hiding in a vent at waist height in a thick wall: it can't follow, so one arm goes in
			// and drags it out into the grab.
			String sb = "minecraft:stone_bricks";
			cmd("fill " + at(20, 0, 18) + " " + at(30, 3, 23) + " " + sb);
			cmd("fill " + at(25, 1, 18) + " " + at(25, 1, 22) + " minecraft:air");
			cmd("setblock " + at(25, 1, 21) + " minecraft:light[level=11]");
			reset(25.5, 16.5, 0, "quad");
			cmd("rauk home {bx} {by} {bz}", 25, 0, 4);
			cmd("rauk hunger 0.6");
			prey("pig", 25.0, 1, 22.0, 0, false);
			cmd("rauk debug");
			script.add(new Camera(new Vec3(31, 3.5, 13), new Vec3(25.5, 1.5, 18)));
			// It knows the pig went in there.
			cmd("rauk believe @e[tag=rauk_prey,limit=1] 0");
			cmd("rauk brain on");
			script.add(new Burst("reach", 90, 3));
			endHunt();
			cmd("fill " + at(20, 0, 15) + " " + at(30, 3, 23) + " minecraft:air");
			cmd("kill @e[type=item]");
		}
		if (scene("hunt_doorway")) {
			// It waits flat against the wall beside a door, inside the room; the prey walks in and is
			// taken as it comes through.
			hotel();
			night(true);
			cmd("setblock " + at(59, 0, 3) + " minecraft:oak_door[facing=south,half=lower,open=true]");
			cmd("setblock " + at(59, 1, 3) + " minecraft:oak_door[facing=south,half=upper,open=true]");
			reset(61.5, 6.5, 180, "quad");
			cmd("rauk survey");
			cmd("rauk home {bx} {by} {bz}", 62, 0, 7);
			cmd("rauk hunger 1");
			prey("villager", 50.5, 0, 1.5, -90, true);
			cmd("rauk debug");
			cmd("rauk believe @e[tag=rauk_prey,limit=1] 0.5");
			script.add(new Camera(new Vec3(62.5, 2.4, 7.8), new Vec3(59.5, 1.0, 3.5)));
			cmd("rauk brain on");
			script.add(new Wait(2));
			cmd("rauk ambush door " + at(59, 0, 3) + " " + at(59, 0, 7));
			script.add(new Burst("doorway_wait", 20, 4));
			script.add(new PreyWalk("doorway_walk", new Vec3(50.5, 0, 1.5), new Vec3(59.5, 0, 1.5), 2.0, -90f, 4));
			script.add(new PreyWalk("doorway_in", new Vec3(59.5, 0, 1.5), new Vec3(59.5, 0, 7.5), 2.0, 0f, 2));
			script.add(new Burst("doorway_after", 40, 3));
			endHunt();
		}
		if (scene("hunt_peek")) {
			// In the corridor it doesn't show itself: from inside a room only its head comes out of the
			// doorway, level and still, watching.
			hotel();
			openDoors();
			night(true);
			reset(49, 1, -90, "quad");
			cmd("rauk survey");
			cmd("rauk hunger 0.5");
			prey("villager", 71.5, 0, 1.5, 90, true);
			cmd("rauk debug");
			cmd("rauk believe @e[tag=rauk_prey,limit=1] 1");
			script.add(new Camera(new Vec3(65.5, 1.9, 0.8), new Vec3(59.5, 1.0, 3.0)));
			cmd("rauk brain on");
			// Moving, it is seen from down the corridor.
			script.add(new PreyWalk("peek_walk", new Vec3(71.5, 0, 1.5), new Vec3(69.5, 0, 1.5), 1.0, 90f, 4));
			script.add(new Burst("peek", 110, 4));
			endHunt();
		}
		if (scene("others")) {
			// Cows keep away from it once they see it; a husk standing in its way it kills without a sound.
			reset(0, -36, -90, "quad");
			// Peaceful would remove the husk at once.
			cmd("difficulty easy");
			cmd("kill @e[type=cow]");
			cmd("kill @e[type=husk]");
			for (int i = 0; i < 3; i++) {
				cmd("summon minecraft:cow {x} {y} {z} {PersistenceRequired:1b}", 14 + i * 1.5, 0, -38 + i * 2);
			}
			cmd("summon minecraft:husk {x} {y} {z} {PersistenceRequired:1b,NoAI:1b,Rotation:[90f,0f]}", 9.0, 0, -35.5);
			script.add(new Camera(new Vec3(8, 9, -24), new Vec3(10, 0, -36)));
			script.add(new Wait(30));
			cmd("rauk walk {x} {y} {z} 3", 20, 0, -36);
			script.add(new Burst("others", 80, 3));
			cmd("kill @e[type=cow]");
			cmd("kill @e[type=husk]");
			cmd("kill @e[type=item]");
			cmd("difficulty peaceful");
		}
	}

	private static void combatScenes() {
		if (scene("combat_kill")) {
			// An armed villager faces it: it won't come at it head on, so it circles round behind it and
			// grabs it from there, lifts it to its face and bites.
			reset(30.5, -32.5, 0, "quad");
			armedPrey(30.5, -25.5, 180, false);
			cmd("rauk hunger 1");
			cmd("rauk debug");
			cmd("rauk believe @e[tag=rauk_prey,limit=1] 1");
			script.add(new Camera(new Vec3(21, 3, -29), new Vec3(30, 2.4, -29)));
			cmd("rauk brain on");
			script.add(new Burst("kill", 110, 3));
			endHunt();
		}
		if (scene("combat_escape")) {
			// The camera is the prey, facing away. It comes up behind, grabs, and lifts the camera to its
			// face; the camera swings at its face, and it bites all the same (it never lets go).
			reset(34.5, -27.5, 0, "quad");
			cmd("kill @e[tag=rauk_prey]");
			cmd("rauk prey creative off");
			cmd("gamemode survival");
			cmd("give @s minecraft:iron_sword");
			fly(false);
			script.add(new Camera(new Vec3(34.5, 1.62, -24.5), new Vec3(34.5, 1.62, -10)));
			cmd("rauk hunger 1");
			script.add(new Wait(20));
			cmd("rauk brain on");
			cmd("rauk grab @s");
			script.add(new Struggle("escape", 200, 10));
			cmd("rauk brain off");
			cmd("gamemode spectator");
			cmd("rauk prey creative on");
			cmd("clear @s");
		}
	}

	private static void huntScenes() {
		if (scene("hunt_stalk")) {
			// It sees prey down the dark corridor: stalks it along the ceiling, switching the lights off
			// on its way, freezes while the prey looks at it, then commits and strikes.
			hotel();
			night(true);
			reset(49, 1, -90, "quad");
			cmd("rauk survey");
			cmd("rauk hunger 0.7");
			spawnPrey(74, 1, -90);
			cmd("rauk debug");
			script.add(new CamFollow(new Vec3(0, 1.2, -6.5)));
			script.add(new Wait(20));
			// It has just heard something down the corridor.
			cmd("rauk believe @e[tag=rauk_prey,limit=1] 2");
			cmd("rauk brain on");
			script.add(new PreyWalk("stalk_a", new Vec3(74.5, 0, 1.5), new Vec3(77.5, 0, 1.5), 1.0, -90f, 4));
			script.add(new PreyWalk("stalk_b", new Vec3(77.5, 0, 1.5), new Vec3(74.5, 0, 1.5), 1.0, -90f, 4));
			script.add(new Burst("stalk_c", 60, 4));
			cmd("execute as @e[tag=rauk_prey] at @s run tp @s ~ ~ ~ facing entity @e[type=rauk:rauk,limit=1] eyes");
			script.add(new Burst("stalk_d", 25, 4));
			cmd("execute as @e[tag=rauk_prey] at @s run tp @s ~ ~ ~ -90 0");
			script.add(new Burst("stalk_e", 90, 4));
			endHunt();
		}
		if (scene("hunt_search")) {
			// It has a rough idea someone is on this floor: it goes door to door, listening and sniffing,
			// and smells the prey hiding just inside the third room.
			hotel();
			night(true);
			reset(47, 1, -90, "quad");
			cmd("rauk survey");
			spawnPrey(68, 5, 0);
			cmd("rauk debug");
			cmd("rauk believe @e[tag=rauk_prey,limit=1] 9");
			script.add(new CamFollow(new Vec3(0, 1.2, -7)));
			cmd("rauk brain on");
			script.add(new Burst("search", 170, 4));
			endHunt();
		}
		if (scene("hunt_ambush")) {
			// It waits on the ceiling halfway down the corridor, having put the light on there. The prey
			// walks under it, and it drops.
			hotel();
			night(true);
			reset(47, 1, -90, "quad");
			cmd("rauk survey");
			cmd("setblock {bx} {by} {bz} minecraft:lever[face=wall,facing=south,powered=false]", 61, 2, 2);
			spawnPrey(83, 1, 90);
			cmd("rauk debug");
			script.add(new Camera(new Vec3(60, 1.6, -7), new Vec3(60, 1.2, 1.5)));
			cmd("rauk brain on");
			script.add(new Wait(5));
			cmd("rauk ambush {x} {y} {z}", 61.5, 0, 1.5);
			script.add(new Burst("ambush_wait", 50, 4));
			script.add(new PreyWalk("ambush_walk", new Vec3(83.5, 0, 1.5), new Vec3(48.5, 0, 1.5), 1.6, 90f, 4));
			script.add(new Burst("ambush_after", 20, 4));
			endHunt();
		}
		if (scene("hunt_chase")) {
			// Out in the open the prey runs: it gallops after it, leading it, until its burst is spent;
			// then it stops, panting, and goes to cut the prey off.
			reset(36, -24, 0, "quad");
			cmd("rauk hunger 0.8");
			spawnPrey(36, -6, 0);
			cmd("rauk debug");
			script.add(new CamFollow(new Vec3(-10, 4, 0)));
			cmd("rauk brain on");
			script.add(new Wait(20));
			script.add(new PreyWalk("chase_run", new Vec3(36.5, 0, -5.5), new Vec3(36.5, 0, 24.5), 9.0, 0f, 2));
			script.add(new PreyWalk("chase_turn", new Vec3(36.5, 0, 24.5), new Vec3(70.5, 0, 24.5), 9.0, -90f, 2));
			script.add(new Burst("chase_after", 60, 3));
			endHunt();
		}
		if (scene("hunt_probe")) {
			// In the tall pavilion it shows itself to the prey at the edge of the light, watches what it
			// does, and when it does nothing withdraws out of sight.
			reset(-6, -4, 180, "upright");
			spawnPrey(3, -16, 0);
			cmd("rauk debug");
			script.add(new Camera(new Vec3(8, 4.5, -4), new Vec3(-2, 3, -12)));
			cmd("rauk brain on");
			script.add(new PreyWalk("probe_a", new Vec3(0.5, 0, -14.5), new Vec3(3.5, 0, -14.5), 1.2, 0f, 4));
			script.add(new Burst("probe_b", 130, 4));
			endHunt();
		}
	}

	private static void senseScenes() {
		if (scene("sight")) {
			// Daylight. Standing still 18 blocks away it can't see you; move and it does; stop, and it
			// loses you and comes to where it last saw you, until it is close enough to see you again.
			// Out in the open, east of the test course.
			reset(27, -32, 0, "upright");
			beginSenses(true);
			script.add(new Camera(new Vec3(27, 3.5, -14), new Vec3(27, 3, -32)));
			cmd("rauk brain on");
			script.add(new Wait(60));
			script.add(new Grab("sight_still"));
			script.add(new Walk("sight_move", new Vec3(27, 3.5, -14), new Vec3(34, 3.5, -14), 3, new Vec3(27, 3, -32), 5));
			script.add(new Burst("sight_lost", 70, 5));
			endSenses();
		}
		if (scene("hear")) {
			// Out of sight behind the doorway wall a block breaks: it hears it through the wall, its
			// head snaps round, and it goes to look, through the door.
			reset(-19, -9, 90, "quad");
			cmd("setblock {bx} {by} {bz} minecraft:stone", -8, 0, -5);
			cmd("rauk debug");
			script.add(new Camera(new Vec3(-7, 7, -17), new Vec3(-15, 0, -8)));
			script.add(new Wait(40));
			cmd("rauk brain on");
			cmd("setblock {bx} {by} {bz} minecraft:air destroy", -8, 0, -5);
			script.add(new Burst("hear", 90, 3));
			endSenses();
		}
		if (scene("tap")) {
			// It knocks on the door and listens. On the other side you shift your feet, and it hears you.
			reset(-16, -9, -90, "quad");
			beginSenses(false);
			script.add(new Camera(new Vec3(-9, 1.62, -12), new Vec3(-14, 1.5, -9)));
			script.add(new Wait(40));
			cmd("rauk tap {bx} {by} {bz}", -14, 0, -9);
			script.add(new Burst("tap", 30, 2));
			script.add(new Walk("tap_move", new Vec3(-9, 1.62, -12), new Vec3(-9, 1.62, -11), 0.6, new Vec3(-14, 1.5, -9), 4));
			cmd("rauk brain on");
			script.add(new Burst("tap_after", 60, 3));
			endSenses();
		}
		if (scene("smell")) {
			// You walk a route and leave by air. It finds your trail and follows it, head low, toward
			// where you went.
			reset(0, 60, 0, "quad");
			beginSenses(false);
			cmd("effect give @s minecraft:invisibility infinite 0 true");
			script.add(new Walk(null, new Vec3(-10, 1.62, 28), new Vec3(10, 1.62, 28), 4, new Vec3(10, 1.62, 28), 1));
			script.add(new Walk(null, new Vec3(10, 1.62, 28), new Vec3(10, 1.62, 40), 4, new Vec3(10, 1.62, 40), 1));
			script.add(new Walk(null, new Vec3(10, 1.62, 40), new Vec3(-6, 1.62, 42), 4, new Vec3(-6, 1.62, 42), 1));
			fly(true);
			script.add(new Camera(new Vec3(0, 14, 18), new Vec3(0, 0, 34)));
			cmd("tp @e[type=rauk:rauk,limit=1] {x} {y} {z} -90 0", -12, 0, 28);
			script.add(new Wait(10));
			cmd("rauk brain on");
			script.add(new Burst("smell", 100, 4));
			endSenses();
		}
		if (scene("territory")) {
			// It learns the pavilion. Then a torch appears and a bone is dropped where it can see them,
			// and it goes to look at each.
			reset(0, -10, 180, "upright");
			cmd("rauk debug");
			script.add(new Camera(new Vec3(8, 4, -3), new Vec3(0, 1, -12)));
			script.add(new Wait(440));
			cmd("setblock {bx} {by} {bz} minecraft:torch", 5, 0, -15);
			cmd("summon minecraft:item {x} {y} {z} {Item:{id:'minecraft:bone',count:1}}", -5, 0.5, -15);
			cmd("rauk brain on");
			script.add(new Burst("territory", 90, 3));
			endSenses();
		}
	}

	private static void script() {
		if (INSPECT) {
			inspectScenes();
			return;
		}
		cmd("rauk brain off");
		if (scene("climb") || scene("ceiling") || scene("leap") || scene("door") || scene("crawl") || scene("hear")
				|| scene("tap") || scene("territory") || scene("hunt_probe")) {
			course();
			script.add(new Wait(20));
		}
		cmd("rauk prey creative on");
		senseScenes();
		huntScenes();
		combatScenes();
		polishScenes();
		goreScenes();
		predatorScenes();
		fixScenes();
		snakeScenes();
		animPadScenes();
		slantTest();
		shellScenes();
		worldgenScenes();
		furnitureScenes();
		if (scene("probe")) {
			for (int y = 0; y <= 7; y++) {
				cmd("rauk probe {x} {y} {z} north", 0, y, -18);
			}
			cmd("rauk probe {x} {y} {z} floor", 0, 0, -18);
			cmd("rauk probe {x} {y} {z} ceiling", 0, 7, -18);
			cmd("rauk probe {x} {y} {z} ceiling", 0, 7, -10);
			script.add(new Wait(20));
		}
		if (scene("climb")) {
			// Across the floor to the back wall, up it and out along the ceiling.
			reset(0, -13, 180, "quad");
			script.add(new Camera(new Vec3(7, 3.0, -1), new Vec3(0, 4.5, -15)));
			script.add(new Wait(50));
			cmd("rauk climb {x} {y} {z} ceiling 3.5", 0, 7, -10);
			script.add(new Burst("climb", 90, 2));
		}
		if (scene("ceiling")) {
			// Hanging from the ceiling, along it, then letting go to drop to the floor.
			script.add(new Camera(new Vec3(16, 3.5, -7), new Vec3(2, 4, -8)));
			cmd("rauk climb {x} {y} {z} ceiling 3", 5, 7, -6);
			script.add(new Burst("ceiling", 50, 2));
			cmd("rauk walk {x} {y} {z} 3", 5, 0, -5);
			script.add(new Burst("drop", 30, 2));
		}
		if (scene("leap")) {
			// From the floor up to the ceiling in one leap, and back down.
			reset(0, -5, 180, "quad");
			script.add(new Camera(new Vec3(16, 4, -9), new Vec3(0, 4, -9)));
			script.add(new Wait(50));
			cmd("rauk leap {x} {y} {z}", 0, 7, -12);
			script.add(new Burst("leap_up", 40, 1));
			script.add(new Wait(20));
			cmd("rauk leap {x} {y} {z}", 4, 0, -5);
			script.add(new Burst("leap_down", 40, 1));
		}
		if (scene("door")) {
			// Through a closed wooden door in a wall: it opens it and squeezes through.
			reset(-9, -9, 90, "quad");
			script.add(new Camera(new Vec3(-8, 3.5, -15), new Vec3(-14.5, 1, -9)));
			script.add(new Wait(50));
			cmd("rauk walk {x} {y} {z} 2.5", -19, 0, -9);
			script.add(new Burst("door", 100, 2));
		}
		if (scene("crawl")) {
			// Through the 2-high tunnel in the wall.
			reset(0, 0, 0, "quad");
			script.add(new Camera(new Vec3(5, 3, 1), new Vec3(0, 1, 6.5)));
			script.add(new Wait(50));
			cmd("rauk walk {x} {y} {z} 2.5", 0, 0, 19);
			script.add(new Burst("crawl", 50, 2));
			script.add(new Camera(new Vec3(5, 3, 19.5), new Vec3(0, 1, 14)));
			script.add(new Burst("crawl_out", 60, 2));
		}

		if (scene("walk")) {
			// Upright walk past the camera, right to left, at 2.5 blocks a second.
			reset(7, 0, 90, "upright");
			script.add(new Camera(new Vec3(0, 3.2, 13), new Vec3(0, 3.0, 0)));
			script.add(new Wait(50));
			cmd("rauk walk {x} {y} {z} 2.5", -8, 0, 0);
			script.add(new Burst("walk", 44, 2));
		}
		if (scene("prowl")) {
			// Down onto all fours, prowl back across, and stand up again.
			reset(7, 0, 90, "upright");
			script.add(new Camera(new Vec3(0, 3.2, 13), new Vec3(0, 3.0, 0)));
			script.add(new Wait(40));
			cmd("rauk posture quad");
			script.add(new Burst("to_quad", 24, 2));
			cmd("rauk walk {x} {y} {z} 3", -8, 0, 0);
			script.add(new Burst("prowl", 44, 2));
			cmd("rauk stop");
			cmd("rauk posture upright");
			script.add(new Burst("to_upright", 24, 2));
		}
		if (scene("gallop")) {
			// A chase: galloping across at 12 blocks a second and pulling up hard at the end.
			reset(16, 0, 90, "quad");
			script.add(new Camera(new Vec3(0, 3.0, 19), new Vec3(0, 2.5, 0)));
			script.add(new Wait(60));
			cmd("rauk walk {x} {y} {z} 12", -14, 0, 0);
			script.add(new Burst("gallop", 70, 1));
			// The same again with the camera running alongside.
			reset(16, 0, 90, "quad");
			script.add(new Camera(new Vec3(16, 2.6, 10), new Vec3(16, 2.2, 0)));
			script.add(new Wait(60));
			cmd("rauk walk {x} {y} {z} 12", -24, 0, 0);
			script.add(new Follow("gallop_side", new Vec3(-1, 2.6, 10), new Vec3(-1, 2.2, 0), 60, 1));
		}
		if (scene("run")) {
			// Upright, long fast strides at 5.5 blocks a second.
			reset(13, 0, 90, "upright");
			script.add(new Camera(new Vec3(0, 3.2, 17), new Vec3(0, 3.0, 0)));
			script.add(new Wait(50));
			cmd("rauk walk {x} {y} {z} 5.5", -14, 0, 0);
			script.add(new Burst("run", 50, 2));
		}
		if (scene("rise")) {
			// Watching the camera, it slowly rises to its full height, then lowers itself again.
			reset(0, 0, 180, "upright");
			cmd("rauk gaze @s");
			cmd("rauk attention 1");
			cmd("rauk tension 0.5");
			script.add(new Camera(new Vec3(-4, 4.5, -14), new Vec3(0, 4.6, -1)));
			script.add(new Wait(60));
			cmd("rauk rise on");
			script.add(new Burst("rise", 70, 2));
			cmd("rauk rise off");
			script.add(new Burst("lower", 60, 2));
		}
		if (scene("startle")) {
			// Calm and looking about, then something behind and to its right startles it.
			reset(0, 0, 180, "upright");
			script.add(new Camera(new Vec3(-10, 3.5, -2), new Vec3(0, 3.6, -1.5)));
			script.add(new Wait(70));
			cmd("rauk startle {x} {y} {z}", 6, 2, 5);
			script.add(new Burst("startle", 40, 1));
		}
		if (scene("toes")) {
			// The feet close up on a slow walk, the camera alongside: toes curl as each foot lifts.
			reset(4, 0, 90, "upright");
			script.add(new Camera(new Vec3(4, 0.5, 3.4), new Vec3(4, 0.4, 0)));
			script.add(new Wait(50));
			cmd("rauk walk {x} {y} {z} 1.5", -8, 0, 0);
			script.add(new Follow("toes", new Vec3(-0.3, 0.5, 3.0), new Vec3(-0.3, 0.45, 0), 40, 2));
		}
		if (scene("orbit")) {
			// Standing still, locked on the camera as it circles round from the front to behind.
			reset(0, 0, 180, "upright");
			cmd("rauk gaze @s");
			cmd("rauk attention 1");
			cmd("rauk tension 0.6");
			script.add(new Camera(new Vec3(0, 4, -10), new Vec3(0, 3.5, -1.5)));
			script.add(new Wait(50));
			script.add(new Orbit("orbit", new Vec3(0, 3.5, -1.5), 8.5, 0.8, -20, 200, 60, 2));
		}
	}

	private static void cmd(String command) {
		script.add(new Cmd(command, 0, 0, 0));
	}

	private static void cmd(String command, double dx, double dy, double dz) {
		script.add(new Cmd(command, dx, dy, dz));
	}

	private static void tick(Minecraft client) {
		ticks++;
		if (client.player != null && client.screen instanceof net.minecraft.client.gui.screens.DeathScreen) {
			// The camera got killed (a grab test), or the world was left on the death screen: back up
			// at once, or nothing after it is filmed.
			client.player.respawn();
			client.setScreen(null);
		}
		switch (stage) {
			case WAIT_FOR_MENU -> {
				// Recording keeps going even if the window loses focus, and without the music.
				client.options.pauseOnLostFocus = false;
				if (client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MUSIC).get() > 0) {
					client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MUSIC).set(0.0);
					client.options.save();
					client.getMusicManager().stopPlaying();
				}
				if (client.screen instanceof AccessibilityOnboardingScreen) {
					client.options.onboardAccessibility = false;
					client.options.save();
					client.setScreen(new TitleScreen(true));
				} else if (client.screen instanceof TitleScreen && client.isGameLoadFinished()) {
					openWorld(client);
					stage = Stage.LOADING;
				}
			}
			case LOADING -> {
				if (client.player != null && client.level != null && client.screen == null) {
					stage = Stage.SETUP;
					ticks = 0;
				}
			}
			case SETUP -> {
				if (ticks == 20) {
					client.options.hideGui = true;
					// On the ground at the world spawn, so every run builds and films in the same place.
					// The flat preset's grass is always the fourth layer, whatever was built on it before.
					var at = client.level.getSharedSpawnPos();
					int ground = client.level.getMinBuildHeight() + 4;
					anchor = new Vec3(at.getX() + 0.5, ground, at.getZ() + 0.5);
					send(client, "gamemode spectator");
					send(client, "gamerule doDaylightCycle false");
					send(client, "gamerule doWeatherCycle false");
					send(client, "gamerule doMobSpawning false");
					send(client, "time set 6000");
					send(client, "weather clear");
					if (!INSPECT) {
						// Old Rauks go out of the world rather than lie there dying in the next shot.
						send(client, "tp @e[type=rauk:rauk] ~ -400 ~");
						send(client, "kill @e[type=!player]");
					}
				} else if (ticks == 30 && !INSPECT) {
					send(client, String.format(Locale.ROOT, "summon rauk:rauk %.2f %.2f %.2f {Rotation:[180f,0f],PersistenceRequired:1b}",
							anchor.x, anchor.y, anchor.z));
				} else if (ticks == 40) {
					script.clear();
					script();
					step = 0;
					stage = Stage.RUNNING;
				}
			}
			case RUNNING -> run(client);
			case DONE -> {
			}
		}
	}

	private static Vec3 followCam;
	/** Where Rauk last made real progress during an Arrive, and when. */
	private static Vec3 stuckAt;
	private static int stuckSince;
	/** How much Rauk has turned and how far it has gone on this walk, and its last heading and place: how straight it walks. */
	private static double turned, travelled;
	private static float lastYaw = Float.NaN;
	private static Vec3 lastAt;

	private static void run(Minecraft client) {
		if (client.level == null) {
			return;
		}
		if (followCam != null) {
			for (var entity : client.level.entitiesForRendering()) {
				if (entity instanceof RaukEntity rauk) {
					Vec3 at = rauk.position().subtract(anchor);
					place(client, new Camera(at.add(followCam), at.add(0, 1.5, 0)));
				}
			}
		}
		while (step < script.size()) {
			Step s = script.get(step);
			switch (s) {
				case CamFollow f -> {
					followCam = f.offset();
					step++;
				}
				case Arrive a -> {
					if (client.level == null) {
						return;
					}
					Vec3 where = null;
					for (var e : client.level.entitiesForRendering()) {
						if (e instanceof RaukEntity rauk) {
							where = rauk.position();
							if (!Float.isNaN(lastYaw) && lastAt != null) {
								turned += Math.abs(net.minecraft.util.Mth.wrapDegrees(rauk.yBodyRot - lastYaw));
								travelled += Math.hypot(where.x - lastAt.x, where.z - lastAt.z);
							}
							lastYaw = rauk.yBodyRot;
							lastAt = where;
						}
					}
					if (a.name() != null && wait % a.interval() == 0) {
						grab(client, String.format(Locale.ROOT, "rauk_%s_%03d.png", a.name(), frame++));
					}
					// No real progress for 15 seconds: it's stuck, so say so and move on.
					if (where != null && (stuckAt == null || where.distanceTo(stuckAt) > 0.75)) {
						stuckAt = where;
						stuckSince = wait;
					}
					boolean stuck = wait - stuckSince > 300;
					if (++wait > a.timeout() || stuck || where != null && where.distanceTo(anchor.add(a.target())) < 1.6) {
						boolean there = where != null && where.distanceTo(anchor.add(a.target())) < 1.6;
						Rauk.LOGGER.info("Hotel walk took {} ticks{}{}; {} blocks walked, turning {} degrees a block", wait, stuck ? ", STUCK" : "",
								there ? "" : ", NOT THERE", String.format(Locale.ROOT, "%.1f", travelled),
								String.format(Locale.ROOT, "%.1f", travelled > 0.5 ? turned / travelled : 0));
						turned = 0;
						travelled = 0;
						lastYaw = Float.NaN;
						lastAt = null;
						stuckAt = null;
						stuckSince = 0;
						wait = 0;
						frame = 0;
						step++;
						break;
					}
					return;
				}
				case Struggle st -> {
					RaukEntity holder = RaukCombatClient.holder(client, client.player);
					wait++;
					if (holder != null) {
						struggled = true;
						if (wait % st.attackEvery() == 0) {
							RaukCombatClient.attack(client, client.player);
						}
					}
					if (struggled && wait % 2 == 0) {
						grab(client, String.format(Locale.ROOT, "rauk_%s_%02d.png", st.name(), frame++));
					}
					if ((struggled && holder == null && ++after > 40) || wait > st.timeout()) {
						wait = 0;
						frame = 0;
						after = 0;
						struggled = false;
						step++;
						break;
					}
					return;
				}
				case PreyWalk w -> {
					int n = w.ticks();
					if (wait > n || grabbedPrey(client)) {
						wait = 0;
						frame = 0;
						step++;
						break;
					}
					Vec3 at = anchor.add(w.from().lerp(w.to(), wait / (double) n));
					Vec3 dir = w.to().subtract(w.from());
					float yaw = w.yaw() != null ? w.yaw() : (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
					send(client, String.format(Locale.ROOT, "tp @e[tag=rauk_prey,limit=1] %.3f %.3f %.3f %.1f 0", at.x, at.y, at.z, yaw));
					if (w.name() != null && wait % w.interval() == 0 && wait > 0) {
						grab(client, String.format(Locale.ROOT, "rauk_%s_%02d.png", w.name(), frame++));
					}
					wait++;
					return;
				}
				case Hunt h -> {
					RaukEntity server = serverRauk(client);
					boolean has = grabbedPrey(client) || server != null && server.combat().holding();
					if (server != null && wait % 20 == 0) {
						Vec3 p = server.position().subtract(anchor.add(HOTEL).subtract(0.5, 0, 0.5));
						if (huntStill == null || server.position().distanceTo(huntStill) > 0.5) {
							if (wait - huntStillSince >= 60) {
								huntStalls++;
								Rauk.LOGGER.info("Hunt {}: STALLED {}s, ended", h.name(), (wait - huntStillSince) / 20);
							}
							huntStill = server.position();
							huntStillSince = wait;
						}
						var frame = rauk.com.hotel.HotelData.get((net.minecraft.server.level.ServerLevel) server.level()).frame();
						int[] cell = frame == null ? null : frame.toPlan(server.blockPosition());
						var area = cell == null ? null : rauk.com.hotel.HotelBuilder.plan().owner(cell[0], cell[1], cell[2]);
						String where = cell == null ? String.format(Locale.ROOT, "%s %s %s", fmt(p.x), fmt(p.y), fmt(p.z))
								: cell[0] + " " + cell[1] + " " + cell[2] + " (" + (area == null ? "-" : area.name()) + ")";
						Rauk.LOGGER.info("Hunt {} {}s: plan {} {} {} | {}", h.name(), wait / 20, where, server.posture(), server.surface(),
								server.mind().status);
					}
					if (h.name() != null && wait % h.interval() == 0) {
						grab(client, String.format(Locale.ROOT, "rauk_%s_%03d.png", h.name(), frame++));
					}
					if (has || ++wait > h.timeout()) {
						if (wait - huntStillSince >= 60) {
							huntStalls++;
						}
						Rauk.LOGGER.info("Hunt {} over after {} ticks: {}, {} stalls", h.name(), wait, has ? "caught" : "TIMED OUT", huntStalls);
						huntStill = null;
						huntStillSince = 0;
						huntStalls = 0;
						wait = 0;
						frame = 0;
						step++;
						break;
					}
					return;
				}
				case Cmd c -> {
					Vec3 at = anchor.add(c.dx(), c.dy(), c.dz());
					BlockPos block = BlockPos.containing(at);
					send(client, c.command().replace("{x}", fmt(at.x)).replace("{y}", fmt(at.y)).replace("{z}", fmt(at.z))
							.replace("{bx}", Integer.toString(block.getX())).replace("{by}", Integer.toString(block.getY()))
							.replace("{bz}", Integer.toString(block.getZ())));
					step++;
				}
				case Camera c -> {
					followCam = null;
					place(client, c);
					step++;
				}
				case Grab g -> {
					grab(client, "rauk_" + g.name() + ".png");
					step++;
				}
				case Wait w -> {
					if (++wait < w.ticks()) {
						return;
					}
					wait = 0;
					step++;
				}
				case Orbit o -> {
					if (wait++ % o.interval() != 0) {
						return;
					}
					if (frame > 0) {
						grab(client, String.format(Locale.ROOT, "rauk_%s_%02d.png", o.name(), frame - 1));
					}
					if (frame < o.frames()) {
						place(client, o.camera(frame++));
						return;
					}
					frame = 0;
					wait = 0;
					step++;
				}
				case Follow f -> {
					if (wait++ % f.interval() != 0) {
						return;
					}
					if (frame > 0) {
						grab(client, String.format(Locale.ROOT, "rauk_%s_%02d.png", f.name(), frame - 1));
					}
					if (frame < f.frames()) {
						frame++;
						follow(client, f);
						return;
					}
					frame = 0;
					wait = 0;
					step++;
				}
				case Run r -> {
					r.action().accept(client);
					step++;
				}
				case Walk w -> {
					int n = w.ticks();
					if (wait > n) {
						wait = 0;
						frame = 0;
						step++;
						break;
					}
					Vec3 at = w.from().lerp(w.to(), wait / (double) n);
					Vec3 target = w.look();
					if (target == null) {
						for (var entity : client.level.entitiesForRendering()) {
							if (entity instanceof RaukEntity rauk) {
								target = rauk.position().add(0, 2, 0).subtract(anchor);
							}
						}
					}
					place(client, new Camera(at, target == null ? at.add(0, 0, -1) : target));
					if (w.name() != null && wait % w.interval() == 0 && wait > 0) {
						grab(client, String.format(Locale.ROOT, "rauk_%s_%02d.png", w.name(), frame++));
					}
					wait++;
					return;
				}
				case Burst b -> {
					if (wait++ % b.interval() != 0) {
						return;
					}
					grab(client, String.format(Locale.ROOT, "rauk_%s_%02d.png", b.name(), frame));
					if (++frame < b.frames()) {
						return;
					}
					frame = 0;
					wait = 0;
					step++;
				}
			}
			if (s instanceof Wait || s instanceof Burst || s instanceof Orbit || s instanceof Follow || s instanceof PreyWalk
					|| s instanceof Struggle) {
				return;
			}
		}
		stage = Stage.DONE;
		client.stop();
	}

	private static String fmt(double v) {
		return String.format(Locale.ROOT, "%.3f", v);
	}

	private static Vec3 huntStill;
	private static int huntStillSince, huntStalls;

	/** Rauk as the integrated server has it, for what only the server knows (its mind, its plans). */
	private static RaukEntity serverRauk(Minecraft client) {
		var server = client.getSingleplayerServer();
		if (server == null) {
			return null;
		}
		// The one nearest the camera, if there is more than one loaded.
		RaukEntity best = null;
		for (var e : server.overworld().getAllEntities()) {
			if (e instanceof RaukEntity rauk && rauk.isAlive()
					&& (best == null || client.player != null && rauk.distanceToSqr(client.player) < best.distanceToSqr(client.player))) {
				best = rauk;
			}
		}
		return best;
	}

	private static void grab(Minecraft client, String name) {
		Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(),
				message -> Rauk.LOGGER.info("Dev screenshot: {}", message.getString()));
	}

	private static void openWorld(Minecraft client) {
		if (client.getLevelSource().levelExists(WORLD)) {
			client.createWorldOpenFlows().openWorld(WORLD, () -> client.setScreen(new TitleScreen()));
			return;
		}
		LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(),
				WorldDataConfiguration.DEFAULT);
		client.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261004L, NORMAL_WORLD, false),
				registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(NORMAL_WORLD ? WorldPresets.NORMAL : WorldPresets.FLAT).value()
						.createWorldDimensions(),
				new TitleScreen());
	}

	/** Puts the camera at the follow offset from wherever Rauk is now. */
	private static void follow(Minecraft client, Follow f) {
		for (var entity : client.level.entitiesForRendering()) {
			if (entity instanceof RaukEntity rauk) {
				Vec3 at = rauk.position().subtract(anchor);
				place(client, new Camera(at.add(f.eye()), at.add(f.target())));
				return;
			}
		}
	}

	private static void place(Minecraft client, Camera c) {
		Vec3 eye = anchor.add(c.eye());
		Vec3 look = anchor.add(c.target()).subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
		send(client, String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f %.2f %.2f", eye.x, eye.y - 1.62, eye.z, yaw, pitch));
	}

	private static void send(Minecraft client, String command) {
		client.player.connection.sendCommand(command);
	}
}

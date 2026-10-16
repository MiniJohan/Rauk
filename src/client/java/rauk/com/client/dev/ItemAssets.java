package rauk.com.client.dev;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Paints the things to carry (hotel design 8, step 4d) and writes their models and names: the
 * flashlight (off and on), the service lift's crank, the staff keyring, a fuse, and the three keys.
 * Each is a 16 by 16 sprite drawn in code from a few shapes laid along a slant, lit from the top
 * left, with a dark edge round it the way the game's own items have; and a sheet of them all in
 * docs/hotel for review.
 */
public final class ItemAssets {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** Each item: its id, its name, the line under its name, and whether it's held like a tool. */
	static final String[][] ITEMS = {
			{"flashlight", "Flashlight", "Nickel-plated, the switch on its side. Click to turn it on or off.", "handheld"},
			{"lift_crank", "Service Lift Crank", "The handle off the service lift's winding gear.", "handheld"},
			{"staff_keyring", "Staff Keyring", "The night porter's keys: every staff-only door.", "generated"},
			{"fuse", "Fuse", "A cartridge fuse for the generator's board.", "generated"},
			{"study_key", "Study Key", "Brass, engraved W. Wennberg's study.", "generated"},
			{"penthouse_key", "Penthouse Lift Key", "On a brass tag stamped 4.", "generated"},
			{"lockdown_key", "Lockdown Key", "Black iron, heavy and cold.", "generated"}};

	/** What the hotel says when its doors and the service lift meet the things to carry. */
	static final String[][] MESSAGES = {{"itemGroup.rauk.carried", "The Raukvik Grand: Things to Carry"},
			{"block.rauk.hotel_door.locked.staff", "Locked. A brass plate: STAFF ONLY."},
			{"block.rauk.hotel_door.locked.study", "Locked. A brass W on the door."},
			{"block.rauk.hotel_door.unlocked.staff", "One of the porter's keys turns the lock."},
			{"block.rauk.hotel_door.unlocked.study", "The brass key turns. The study is open."},
			{"hotel.rauk.lift.no_crank", "The winding gear has no crank. The car won't move."},
			{"hotel.rauk.lift.crank_fitted", "The crank fits the winding gear."},
			{"hotel.rauk.lift.no_key", "Floor 4 is locked off. It needs a key."},
			{"hotel.rauk.lift.key_turned", "The key turns. The car will go up to 4 now."},
			{"block.rauk.reception_safe.opened", "The tumblers drop. The safe swings open."},
			{"block.rauk.reception_safe.taken", "A crank, a ring of keys, and a page in the clerk's hand."},
			{"block.rauk.reception_safe.empty", "Empty."},
			{"hotel.rauk.generator.primer", "The pump draws."},
			{"hotel.rauk.generator.primer_full", "The pump draws. It's stiff now: primed."},
			{"hotel.rauk.generator.primer_over", "It won't take any more."},
			{"hotel.rauk.generator.primer_running", "She's running. The pump stays still."},
			{"hotel.rauk.generator.choke_open", "Choke open."},
			{"hotel.rauk.generator.choke_shut", "Choke shut."},
			{"hotel.rauk.generator.running", "She runs, steady, shaking the floor."},
			{"hotel.rauk.generator.catching", "She's catching..."},
			{"hotel.rauk.generator.cranks", "The flywheel turns over. Again."},
			{"hotel.rauk.generator.catches", "She catches! Coughing, spluttering..."},
			{"hotel.rauk.generator.no_spark", "The flywheel turns over. Not a spark: the fuse board."},
			{"hotel.rauk.generator.cough", "She coughs like a gunshot and dies."},
			{"hotel.rauk.generator.raced", "She races, roars, and dies."},
			{"hotel.rauk.generator.started", "She roars into life. Above you, through every floor, the lights come on."},
			{"hotel.rauk.generator.cold", "A gas engine and its dynamo, cold."},
			{"hotel.rauk.generator.cold_no_spark", "A gas engine and its dynamo, cold. No spark: the fuse board is empty."},
			{"hotel.rauk.generator.board_0", "Three empty fuse holders."},
			{"hotel.rauk.generator.board_1", "One fuse in. Two holders empty."},
			{"hotel.rauk.generator.board_2", "Two fuses in. One holder empty."},
			{"hotel.rauk.generator.board_3", "The board is full."},
			{"hotel.rauk.generator.board_full", "The board is full. Somewhere, a relay clicks."},
			{"hotel.rauk.generator.till", "The drawer rings out. Among the coins, a fuse."},
			{"hotel.rauk.generator.till_empty", "Only coins."},
			{"subtitles.rauk.generator", "Generator runs"},
			{"hotel.rauk.waltz.drawer_opens", "Under the bandstand, a catch lets go."},
			{"hotel.rauk.waltz.drawer_0", "A drawer in the front of the bandstand. No handle, no keyhole. It doesn't move."},
			{"hotel.rauk.waltz.drawer_1", "A key, brass, engraved W."},
			{"hotel.rauk.waltz.drawer_2", "Empty."},
			{"hotel.rauk.shelves.open", "Somewhere in the wall, a catch gives. A section of the shelves swings open."},
			{"hotel.rauk.clock.minute", "The minute hand moves on. (Sneaking: the hour hand.)"},
			{"hotel.rauk.clock.hour", "The hour hand moves on."},
			{"hotel.rauk.clock.opens", "The clock strikes, once, deep. It slides aside on the wall behind it."},
			{"hotel.rauk.lockdown.dead", "The panel is dead. No power."},
			{"hotel.rauk.lockdown.no_key", "A keyhole, and a keypad. The keys won't go down."},
			{"hotel.rauk.lockdown.key_in", "The key turns. The panel hums."},
			{"hotel.rauk.lockdown.cleared", "Cleared."},
			{"hotel.rauk.lockdown.accepted", "The code is taken. A lamp lights behind the glass."},
			{"hotel.rauk.lockdown.wrong", "A buzz. Wrong."},
			{"hotel.rauk.lockdown.lever_stuck", "The lever won't move. The panel isn't ready."},
			{"hotel.rauk.lockdown.pulled", "The lever comes down. Below you, all through the building, an alarm begins to ring."},
			{"hotel.rauk.lockdown.released", "The lockdown is lifted."},
			{"hotel.rauk.ending.title", "Dawn"},
			{"hotel.rauk.ending.subtitle", "You got out of the Raukvik Grand."},
			{"hotel.rauk.ending.line", "Behind you the shutters stand open on the dark. It is still in there."}};

	// The colours: nickel, iron, brass, wood, porcelain, glass, the lockdown key's red tag.
	private static final int[] NICKEL = {0xFFE4E7EA, 0xFFA9AFB4, 0xFF666C72}, IRON = {0xFF7A7470, 0xFF4E4945, 0xFF2B2826},
			BRASS = {0xFFF2D27A, 0xFFC99A3E, 0xFF84601F}, WOOD = {0xFFA8673A, 0xFF7A4423, 0xFF4E2914}, PORCELAIN = {0xFFFAF4E6, 0xFFDCD2BE, 0xFFA89E8A},
			BLACK = {0xFF5A5654, 0xFF34302E, 0xFF1A1817}, RED = {0xFFC0443A, 0xFF8E2A24, 0xFF5C1714}, LENS_OFF = {0xFFC6D4DA, 0xFF8EA0A8, 0xFF5F6E75},
			LENS_ON = {0xFFFFFFF0, 0xFFFFF0B0, 0xFFF5C860};

	private final Path assets;

	private ItemAssets(Path resources) {
		assets = resources.resolve("assets/rauk");
	}

	public static void write(Path resources) throws IOException {
		new ItemAssets(resources).writeAll(resources.resolve("../../../docs/hotel/items.png").normalize());
	}

	private void writeAll(Path sheet) throws IOException {
		Map<String, Sprite> sprites = new LinkedHashMap<>();
		sprites.put("flashlight", flashlight(false));
		sprites.put("flashlight_on", flashlight(true));
		sprites.put("lift_crank", crank());
		sprites.put("staff_keyring", keyring());
		sprites.put("fuse", fuse());
		sprites.put("study_key", key(BRASS, 2.6, false, null));
		sprites.put("penthouse_key", key(NICKEL, 2.1, false, BRASS));
		sprites.put("lockdown_key", key(BLACK, 3.0, true, RED));
		Path tex = assets.resolve("textures/item");
		Files.createDirectories(tex);
		for (var e : sprites.entrySet()) {
			ImageIO.write(e.getValue().image(), "png", tex.resolve(e.getKey() + ".png").toFile());
		}
		JsonObject lang = JsonParser.parseString(Files.readString(assets.resolve("lang/en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
		for (String[] i : ITEMS) {
			String model = "{\"parent\": \"minecraft:item/" + i[3] + "\", \"textures\": {\"layer0\": \"rauk:item/" + i[0] + "\"}";
			if (i[0].equals("flashlight")) {
				model += ", \"overrides\": [{\"predicate\": {\"rauk:lit\": 1}, \"model\": \"rauk:item/flashlight_on\"}]";
				write("models/item/flashlight_on.json", "{\"parent\": \"minecraft:item/handheld\", \"textures\": {\"layer0\": \"rauk:item/flashlight_on\"}}");
			}
			write("models/item/" + i[0] + ".json", model + "}");
			lang.addProperty("item.rauk." + i[0], i[1]);
			lang.addProperty("item.rauk." + i[0] + ".desc", i[2]);
		}
		for (String[] m : MESSAGES) {
			lang.addProperty(m[0], m[1]);
		}
		Files.writeString(assets.resolve("lang/en_us.json"), GSON.toJson(lang) + "\n", StandardCharsets.UTF_8);
		drawSheet(sprites, sheet);
	}

	private void write(String path, String json) throws IOException {
		Path p = assets.resolve(path);
		Files.createDirectories(p.getParent());
		Files.writeString(p, GSON.toJson(JsonParser.parseString(json)) + "\n", StandardCharsets.UTF_8);
	}

	// --- the sprites -----------------------------------------------------------------------------

	/** The flashlight on the slant, head up and to the right: the tube, the flared head, its bezel and lens, the brass switch. */
	private static Sprite flashlight(boolean on) {
		Sprite s = new Sprite();
		double[] a = {2, 14}, b = {12.6, 3.4};
		s.capsule(a, b, 0, 0.6, 1.35, NICKEL, true);
		s.capsule(a, b, 0.6, 0.7, 1.85, NICKEL, false);
		s.capsule(a, b, 0.7, 0.9, 2.55, NICKEL, false);
		s.capsule(a, b, 0.9, 1, 2.55, on ? LENS_ON : LENS_OFF, false);
		s.capsule(a, b, 0.32, 0.44, 0.55, BRASS, false, 1.2);
		s.outline();
		if (on) {
			// The glare off the lens.
			for (int[] g : new int[][] {{14, 1, 0xD0}, {15, 0, 0x90}, {13, 0, 0x70}, {15, 2, 0x70}, {12, 0, 0x40}, {15, 3, 0x40}}) {
				s.set(g[0], g[1], g[2] << 24 | 0xFFF6C8);
			}
		}
		return s;
	}

	/** The crank: a square socket for the winding gear's shaft, the iron arm, the wooden grip across its end. */
	private static Sprite crank() {
		Sprite s = new Sprite();
		s.capsule(new double[] {3.5, 12.5}, new double[] {11, 5}, 0, 1, 1.0, IRON, true);
		s.capsule(new double[] {10.5, 4.5}, new double[] {13.5, 7.5}, 0, 1, 1.25, WOOD, true);
		s.box(1.5, 10.5, 5.5, 14.5, IRON);
		s.set(3, 12, IRON[2]);
		s.set(4, 12, IRON[2]);
		s.set(3, 13, IRON[2]);
		s.set(4, 13, IRON[2]);
		s.outline();
		return s;
	}

	/** An iron ring with three keys hanging off it, fanned out. */
	private static Sprite keyring() {
		Sprite s = new Sprite();
		s.ring(4.8, 4.8, 3.8, 2.5, IRON);
		keyOn(s, new double[] {6.4, 9.6}, new double[] {6.4, 15.4}, 1.5, 0.6, 0.55, BRASS);
		keyOn(s, new double[] {9.6, 6.4}, new double[] {15.4, 6.4}, 1.5, 0.6, 0.55, IRON);
		keyOn(s, new double[] {9.4, 9.4}, new double[] {14.2, 14.2}, 1.5, 0.6, 0.55, BRASS);
		s.outline();
		return s;
	}

	/** A key from its bow to its tip: a ring with a hole, the shank, and two teeth off the lower side near the tip. */
	private static void keyOn(Sprite s, double[] bow, double[] tip, double bowR, double hole, double shank, int[] c) {
		double dx = tip[0] - bow[0], dy = tip[1] - bow[1], len = Math.hypot(dx, dy);
		double ux = dx / len, uy = dy / len;
		// Across the shank, to its lower right.
		double nx = -uy, ny = ux;
		if (nx + ny < 0) {
			nx = -nx;
			ny = -ny;
		}
		s.capsule(new double[] {bow[0] + ux * bowR * 0.8, bow[1] + uy * bowR * 0.8}, tip, 0, 1, shank, c, false);
		for (double t : new double[] {len - 0.9, len - 2.6}) {
			double[] root = {bow[0] + ux * t, bow[1] + uy * t};
			s.capsule(root, new double[] {root[0] + nx * 1.7, root[1] + ny * 1.7}, 0, 1, 0.5, c, false);
		}
		s.ring(bow[0], bow[1], bowR, hole, c);
	}

	/** A cartridge fuse: a porcelain barrel with brass caps on both ends. */
	private static Sprite fuse() {
		Sprite s = new Sprite();
		double[] a = {3, 13}, b = {13, 3};
		s.capsule(a, b, 0.2, 0.8, 2.1, PORCELAIN, false);
		s.capsule(a, b, 0.05, 0.22, 2.4, BRASS, false);
		s.capsule(a, b, 0.78, 0.95, 2.4, BRASS, false);
		s.capsule(a, b, 0, 0.06, 1.2, BRASS, false);
		s.capsule(a, b, 0.94, 1, 1.2, BRASS, false);
		s.outline();
		return s;
	}

	/** A key on the slant, bow at the top left, and maybe a tag tied through the bow, hanging below it. */
	private static Sprite key(int[] metal, double bow, boolean heavy, int[] tag) {
		Sprite s = new Sprite();
		if (tag != null) {
			// The string, then the tag: a round brass one, or a cloth one.
			s.set(3, 8, 0xFFB8A888);
			s.set(3, 9, 0xFFB8A888);
			if (heavy) {
				s.box(1, 10, 5, 15, tag);
			} else {
				s.ring(3.5, 12, 2.4, 0, tag);
				s.set(3, 11, BRASS[2]);
				s.set(4, 11, BRASS[2]);
				s.set(4, 12, BRASS[2]);
				s.set(4, 13, BRASS[2]);
			}
		}
		keyOn(s, new double[] {4.4, 4.4}, new double[] {14, 14}, bow, heavy ? 1.7 : 1.0, heavy ? 0.85 : 0.65, metal);
		s.outline();
		return s;
	}

	// --- drawing ----------------------------------------------------------------------------------

	/** A 16 by 16 sprite, drawn with shapes shaded from the top left. */
	static final class Sprite {
		private final int[][] px = new int[16][16];

		void set(int x, int y, int argb) {
			if (x >= 0 && y >= 0 && x < 16 && y < 16) {
				px[y][x] = argb;
			}
		}

		/** A rod from a to b (a stretch of it, by fraction), so wide each side, lit on its upper left. */
		void capsule(double[] a, double[] b, double t0, double t1, double r, int[] c, boolean round) {
			capsule(a, b, t0, t1, r, c, round, 0);
		}

		/** The same, its middle moved off the line by {@code off} (up and left when negative). */
		void capsule(double[] a, double[] b, double t0, double t1, double r, int[] c, boolean round, double off) {
			double dx = b[0] - a[0], dy = b[1] - a[1], len = Math.hypot(dx, dy);
			double ux = dx / len, uy = dy / len, nx = uy, ny = -ux;
			// The normal pointing up and left is the lit side.
			if (nx + ny > 0) {
				nx = -nx;
				ny = -ny;
			}
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					double px0 = x + 0.5 - a[0], py0 = y + 0.5 - a[1];
					double u = (px0 * ux + py0 * uy) / len, v = px0 * nx + py0 * ny + off;
					double over = u < t0 ? (t0 - u) * len : u > t1 ? (u - t1) * len : 0;
					if (!round && over > 0.01 || Math.hypot(over, v) > r) {
						continue;
					}
					set(x, y, v > r * 0.35 ? c[0] : v < -r * 0.35 ? c[2] : c[1]);
				}
			}
		}

		/** A ring about (cx, cy), from radius {@code inner} to {@code outer}, lit on its upper left. */
		void ring(double cx, double cy, double outer, double inner, int[] c) {
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					double dx = x + 0.5 - cx, dy = y + 0.5 - cy, d = Math.hypot(dx, dy);
					if (d <= outer && d >= inner) {
						double lit = -(dx + dy) / Math.max(d, 0.01);
						set(x, y, lit > 0.4 ? c[0] : lit < -0.4 ? c[2] : c[1]);
					}
				}
			}
		}

		/** A square from (x0, y0) to (x1, y1). */
		void box(double x0, double y0, double x1, double y1, int[] c) {
			for (int y = (int) Math.floor(y0); y < Math.ceil(y1); y++) {
				for (int x = (int) Math.floor(x0); x < Math.ceil(x1); x++) {
					set(x, y, x == (int) Math.floor(x0) || y == (int) Math.floor(y0) ? c[0] : c[1]);
				}
			}
		}

		/** A dark edge round everything drawn: each empty pixel beside a drawn one, in that one's colour darkened. */
		void outline() {
			int[][] edge = new int[16][16];
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					if (px[y][x] != 0) {
						continue;
					}
					for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
						int nx = x + d[0], ny = y + d[1];
						if (nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && (px[ny][nx] >>> 24) == 0xFF) {
							edge[y][x] = darken(px[ny][nx], 0.38);
							break;
						}
					}
				}
			}
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					if (edge[y][x] != 0) {
						px[y][x] = edge[y][x];
					}
				}
			}
		}

		private static int darken(int argb, double f) {
			int r = (int) ((argb >> 16 & 0xFF) * f), g = (int) ((argb >> 8 & 0xFF) * f), b = (int) ((argb & 0xFF) * f);
			return 0xFF000000 | r << 16 | g << 8 | b;
		}

		BufferedImage image() {
			BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					img.setRGB(x, y, px[y][x]);
				}
			}
			return img;
		}
	}

	/** All of them on one sheet, big, on a dark ground and a light one. */
	private static void drawSheet(Map<String, Sprite> sprites, Path path) throws IOException {
		int scale = 8, pad = 8, cell = 16 * scale + pad;
		BufferedImage sheet = new BufferedImage(cell * sprites.size() + pad, cell * 2 + pad, BufferedImage.TYPE_INT_ARGB);
		var g = sheet.createGraphics();
		g.setColor(new java.awt.Color(0x1E1B1A));
		g.fillRect(0, 0, sheet.getWidth(), cell + pad / 2);
		g.setColor(new java.awt.Color(0x8B8B8B));
		g.fillRect(0, cell + pad / 2, sheet.getWidth(), sheet.getHeight());
		int i = 0;
		for (Sprite s : sprites.values()) {
			for (int row = 0; row < 2; row++) {
				g.drawImage(s.image(), pad + i * cell, pad + row * cell, 16 * scale, 16 * scale, null);
			}
			i++;
		}
		g.dispose();
		Files.createDirectories(path.getParent());
		ImageIO.write(sheet, "png", path.toFile());
	}
}

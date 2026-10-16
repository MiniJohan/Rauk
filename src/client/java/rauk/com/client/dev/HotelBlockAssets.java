package rauk.com.client.dev;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rauk.com.hotel.HotelMaterials;
import rauk.com.hotel.HotelMaterials.Material;

/**
 * Writes the hotel blocks' assets from {@link HotelMaterials}: block states, block and item
 * models, their names, and the unclimbable tag (run with the texture painter). Stairs, slabs and
 * the iron railings follow the game's own oak stairs, oak slab and iron bars.
 */
public final class HotelBlockAssets {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path assets, data;

	private HotelBlockAssets(Path resources) {
		this.assets = resources.resolve("assets/rauk");
		this.data = resources.resolve("data/rauk");
	}

	public static void write(Path resources) throws IOException {
		new HotelBlockAssets(resources).writeAll();
		FurnitureAssets.write(resources);
		DoorAssets.write(resources);
		ItemAssets.write(resources);
	}

	private void writeAll() throws IOException {
		for (Material m : HotelMaterials.ALL) {
			switch (m.shape()) {
				case CUBE -> cube(m);
				case FACING -> facing(m);
				case COLUMN -> column(m);
				case PILASTER -> pilaster(m);
				case CORNICE -> cornice(m);
				case BALUSTRADE -> balustrade(m);
				case RAILING -> railing(m);
				case RUNNER -> runner(m);
				case STAIRS -> stairs(m, tex(m.other()), tex(m.other()));
				case CARPET_STAIRS -> stairs(m, tex("runner_center"), tex(m.other()));
				case SLAB -> slab(m);
				case PIPE -> pipe(m);
				case FINIAL -> finial(m);
				case SLANT -> slant(m);
				case LETTER -> letter(m);
				case FESTOON -> festoon(m);
				case POST -> post(m);
			}
		}
		names();
		unclimbable();
	}

	private static String tex(String name) {
		return name.contains(":") ? name : "rauk:block/" + name;
	}

	private void model(String name, String json) throws IOException {
		Path p = assets.resolve("models/block/" + name + ".json");
		Files.createDirectories(p.getParent());
		Files.writeString(p, json, StandardCharsets.UTF_8);
	}

	private void state(String id, String json) throws IOException {
		Path p = assets.resolve("blockstates/" + id + ".json");
		Files.createDirectories(p.getParent());
		Files.writeString(p, json, StandardCharsets.UTF_8);
	}

	private void item(String id, String parent) throws IOException {
		Path p = assets.resolve("models/item/" + id + ".json");
		Files.createDirectories(p.getParent());
		Files.writeString(p, "{\n  \"parent\": \"" + parent + "\"\n}\n", StandardCharsets.UTF_8);
	}

	private static String parented(String parent, Map<String, String> textures) {
		JsonObject o = new JsonObject();
		o.addProperty("parent", parent);
		JsonObject t = new JsonObject();
		textures.forEach(t::addProperty);
		o.add("textures", t);
		return GSON.toJson(o) + "\n";
	}

	private static final String[] FACINGS = {"north", "east", "south", "west"};

	private static int turn(String facing) {
		return switch (facing) {
			case "east" -> 90;
			case "south" -> 180;
			case "west" -> 270;
			default -> 0;
		};
	}

	private static String variant(String model, int y) {
		return "{\"model\": \"" + model + "\"" + (y != 0 ? ", \"y\": " + y : "") + "}";
	}

	private void cube(Material m) throws IOException {
		List<String> looks = HotelMaterials.looks(m.id(), m.variants());
		StringBuilder list = new StringBuilder();
		boolean floor = m.id().startsWith("marble_cream") || m.id().startsWith("marble_black");
		for (String look : looks) {
			model(look, floor ? parented("minecraft:block/cube_bottom_top", Map.of("top", tex(look), "side", tex(look),
					"bottom", tex("plaster_ceiling"))) : parented("minecraft:block/cube_all", Map.of("all", tex(look))));
			list.append(list.isEmpty() ? "" : ", ").append(variant("rauk:block/" + look, 0));
		}
		state(m.id(), "{\n  \"variants\": {\n    \"\": " + (looks.size() == 1 ? list : "[" + list + "]") + "\n  }\n}\n");
		item(m.id(), "rauk:block/" + m.id());
	}

	private void facing(Material m) throws IOException {
		String other = tex(m.other());
		List<String> looks = HotelMaterials.looks(m.id(), m.variants());
		for (String look : looks) {
			Map<String, String> t = new LinkedHashMap<>();
			t.put("particle", tex(look));
			t.put("north", tex(look));
			for (String side : new String[] {"south", "east", "west", "up", "down"}) {
				t.put(side, other);
			}
			model(look, parented("minecraft:block/cube", t));
		}
		facingState(m.id(), looks);
		item(m.id(), "rauk:block/" + m.id());
	}

	private void facingState(String id, String model) throws IOException {
		facingState(id, List.of(model.substring(model.indexOf('/') + 1)));
	}

	/** Turned to each facing; with several looks, one picked at random block by block. */
	private void facingState(String id, List<String> looks) throws IOException {
		StringBuilder s = new StringBuilder("{\n  \"variants\": {\n");
		for (int i = 0; i < 4; i++) {
			StringBuilder list = new StringBuilder();
			for (String look : looks) {
				list.append(list.isEmpty() ? "" : ", ").append(variant("rauk:block/" + look, turn(FACINGS[i])));
			}
			s.append("    \"facing=").append(FACINGS[i]).append("\": ").append(looks.size() == 1 ? list : "[" + list + "]")
					.append(i < 3 ? ",\n" : "\n");
		}
		state(id, s.append("  }\n}\n").toString());
	}

	private void column(Material m) throws IOException {
		StringBuilder s = new StringBuilder("{\n  \"variants\": {\n");
		String[] parts = {"base", "shaft", "capital"};
		for (int i = 0; i < 3; i++) {
			String name = m.id() + "_" + parts[i];
			model(name, parented("minecraft:block/cube_column", Map.of("end", tex(m.other()), "side", tex(name))));
			s.append("    \"part=").append(parts[i]).append("\": ").append(variant("rauk:block/" + name, 0)).append(i < 2 ? ",\n" : "\n");
		}
		state(m.id(), s.append("  }\n}\n").toString());
		item(m.id(), "rauk:block/" + m.id() + "_shaft");
	}

	/** A box element with every face textured, front and sides from the face texture by position. */
	private static String box(double x0, double y0, double z0, double x1, double y1, double z1, String face, String end) {
		String v = (16 - y1) + ", " + (16 - y0);
		return "    {\"from\": [" + x0 + ", " + y0 + ", " + z0 + "], \"to\": [" + x1 + ", " + y1 + ", " + z1 + "], \"faces\": {\n"
				+ "      \"north\": {\"uv\": [" + x0 + ", " + v.split(", ")[0] + ", " + x1 + ", " + v.split(", ")[1] + "], \"texture\": \"" + face + "\"},\n"
				+ "      \"south\": {\"uv\": [" + (16 - x1) + ", " + v.split(", ")[0] + ", " + (16 - x0) + ", " + v.split(", ")[1] + "], \"texture\": \"" + face + "\"},\n"
				+ "      \"east\": {\"uv\": [" + (16 - z1) + ", " + v.split(", ")[0] + ", " + (16 - z0) + ", " + v.split(", ")[1] + "], \"texture\": \"" + face + "\"},\n"
				+ "      \"west\": {\"uv\": [" + z0 + ", " + v.split(", ")[0] + ", " + z1 + ", " + v.split(", ")[1] + "], \"texture\": \"" + face + "\"},\n"
				+ "      \"up\": {\"uv\": [" + x0 + ", " + z0 + ", " + x1 + ", " + z1 + "], \"texture\": \"" + end + "\"},\n"
				+ "      \"down\": {\"uv\": [" + x0 + ", " + (16 - z1) + ", " + x1 + ", " + (16 - z0) + "], \"texture\": \"" + end + "\"}\n"
				+ "    }}";
	}

	private static String elements(String particle, String face, String end, String... boxes) {
		return "{\n  \"textures\": {\"particle\": \"" + particle + "\", \"face\": \"" + face + "\", \"end\": \"" + end + "\"},\n"
				+ "  \"elements\": [\n" + String.join(",\n", boxes) + "\n  ]\n}\n";
	}

	private void pilaster(Material m) throws IOException {
		StringBuilder s = new StringBuilder("{\n  \"variants\": {\n");
		String[] parts = {"base", "shaft", "capital"};
		int n = 0;
		for (String part : parts) {
			String name = m.id() + "_" + part;
			String b = part.equals("shaft") ? box(3, 0, 11, 13, 16, 16, "#face", "#end") : box(1, 0, 10, 15, 16, 16, "#face", "#end");
			model(name, elements(tex(name), tex(name), tex(m.other()), b));
			for (String f : FACINGS) {
				s.append(n++ == 0 ? "" : ",\n").append("    \"facing=").append(f).append(",part=").append(part).append("\": ")
						.append(variant("rauk:block/" + name, turn(f)));
			}
		}
		state(m.id(), s.append("\n  }\n}\n").toString());
		item(m.id(), "rauk:block/" + m.id() + "_shaft");
	}

	private void cornice(Material m) throws IOException {
		model(m.id(), elements(tex(m.id()), tex(m.id()), tex(m.other()), box(0, 0, 10, 16, 5, 16, "#face", "#end"),
				box(0, 5, 5, 16, 11, 16, "#face", "#end"), box(0, 11, 0, 16, 16, 16, "#face", "#end")));
		facingState(m.id(), "rauk:block/" + m.id());
		item(m.id(), "rauk:block/" + m.id());
	}

	private void balustrade(Material m) throws IOException {
		String t = tex(m.other());
		model(m.id(), elements(t, t, t, box(0, 0, 3, 16, 3, 13, "#face", "#end"), box(0, 13, 2, 16, 16, 14, "#face", "#end"),
				box(1.5, 3, 6, 4.5, 13, 10, "#face", "#end"), box(6.5, 3, 6, 9.5, 13, 10, "#face", "#end"),
				box(11.5, 3, 6, 14.5, 13, 10, "#face", "#end")));
		state(m.id(), "{\n  \"variants\": {\n    \"axis=x\": " + variant("rauk:block/" + m.id(), 0) + ",\n    \"axis=z\": "
				+ variant("rauk:block/" + m.id(), 90) + "\n  }\n}\n");
		item(m.id(), "rauk:block/" + m.id());
	}

	private void railing(Material m) throws IOException {
		for (String part : new String[] {"post_ends", "post", "cap", "cap_alt", "side", "side_alt"}) {
			model(m.id() + "_" + part, parented("minecraft:block/iron_bars_" + part,
					Map.of("particle", tex(m.id()), "bars", tex(m.id()), "edge", tex(m.id() + "_edge"))));
		}
		String bars = vanilla("blockstates/iron_bars.json").replace("minecraft:block/iron_bars_", "rauk:block/" + m.id() + "_");
		state(m.id(), bars);
		Path p = assets.resolve("models/item/" + m.id() + ".json");
		Files.writeString(p, parented("minecraft:item/generated", Map.of("layer0", tex(m.id()))), StandardCharsets.UTF_8);
	}

	private void runner(Material m) throws IOException {
		model("runner_center", parented("minecraft:block/carpet", Map.of("wool", tex("runner_center"))));
		model("runner_edge", parented("minecraft:block/carpet", Map.of("wool", tex("runner_edge"))));
		StringBuilder s = new StringBuilder("{\n  \"variants\": {\n");
		int n = 0;
		for (String part : new String[] {"center", "edge"}) {
			for (String f : FACINGS) {
				s.append(n++ == 0 ? "" : ",\n").append("    \"facing=").append(f).append(",part=").append(part).append("\": ")
						.append(variant("rauk:block/runner_" + part, turn(f)));
			}
		}
		state(m.id(), s.append("\n  }\n}\n").toString());
		item(m.id(), "rauk:block/runner_center");
	}

	private void stairs(Material m, String top, String side) throws IOException {
		Map<String, String> t = Map.of("bottom", side, "top", top, "side", side);
		model(m.id(), parented("minecraft:block/stairs", t));
		model(m.id() + "_inner", parented("minecraft:block/inner_stairs", t));
		model(m.id() + "_outer", parented("minecraft:block/outer_stairs", t));
		state(m.id(), vanilla("blockstates/oak_stairs.json").replace("minecraft:block/oak_stairs", "rauk:block/" + m.id()));
		item(m.id(), "rauk:block/" + m.id());
	}

	private void slab(Material m) throws IOException {
		String t = tex(m.other());
		Map<String, String> tx = Map.of("bottom", t, "top", t, "side", t);
		model(m.id(), parented("minecraft:block/slab", tx));
		model(m.id() + "_top", parented("minecraft:block/slab_top", tx));
		state(m.id(), vanilla("blockstates/oak_slab.json").replace("minecraft:block/oak_planks", "rauk:block/" + m.other())
				.replace("minecraft:block/oak_slab", "rauk:block/" + m.id()));
		item(m.id(), "rauk:block/" + m.id());
	}

	private void pipe(Material m) throws IOException {
		String t = tex(m.id());
		model(m.id(), elements(t, t, t, box(5, 0, 5, 11, 16, 11, "#face", "#end")));
		state(m.id(), "{\n  \"variants\": {\n    \"axis=y\": {\"model\": \"rauk:block/" + m.id() + "\"},\n"
				+ "    \"axis=x\": {\"model\": \"rauk:block/" + m.id() + "\", \"x\": 90, \"y\": 90},\n"
				+ "    \"axis=z\": {\"model\": \"rauk:block/" + m.id() + "\", \"x\": 90}\n  }\n}\n");
		item(m.id(), "rauk:block/" + m.id());
	}

	private void finial(Material m) throws IOException {
		String t = tex(m.other());
		model(m.id(), elements(t, t, t, box(4, 0, 4, 12, 3, 12, "#face", "#end"), box(6, 3, 6, 10, 9, 10, "#face", "#end"),
				box(5, 9, 5, 11, 13, 11, "#face", "#end"), box(7, 13, 7, 9, 16, 9, "#face", "#end")));
		state(m.id(), "{\n  \"variants\": {\n    \"\": {\"model\": \"rauk:block/" + m.id() + "\"}\n  }\n}\n");
		item(m.id(), "rauk:block/" + m.id());
	}

	/**
	 * A slanted piece (hotel design 14): a thin face turned 45 degrees across the block from its
	 * north-east corner to its south-west one, stretched to reach them, and three steps filling the
	 * half behind it (so its top and bottom, where they show, read as stone). Facing north it looks
	 * north-west; the block state turns it.
	 */
	private void slant(Material m) throws IOException {
		String t = tex(m.other());
		String face = "    {\"from\": [0, 0, 7.9], \"to\": [16, 16, 8.1], \"rotation\": {\"origin\": [8, 8, 8], \"axis\": \"y\", "
				+ "\"angle\": 45, \"rescale\": true}, \"faces\": {\n"
				+ "      \"north\": {\"uv\": [0, 0, 16, 16], \"texture\": \"#face\"},\n"
				+ "      \"south\": {\"uv\": [0, 0, 16, 16], \"texture\": \"#face\"}\n    }}";
		model(m.id(), elements(t, t, t, face, box(4, 0, 12, 8, 16, 16, "#face", "#end"), box(8, 0, 8, 12, 16, 16, "#face", "#end"),
				box(12, 0, 4, 16, 16, 16, "#face", "#end")));
		facingState(m.id(), "rauk:block/" + m.id());
		item(m.id(), "rauk:block/" + m.id());
	}

	/** The letters, five pixels by seven, each pixel two model units square. */
	private static final Map<String, String[]> FONT = Map.of(
			"a", new String[] {"01110", "10001", "10001", "11111", "10001", "10001", "10001"},
			"d", new String[] {"11110", "10001", "10001", "10001", "10001", "10001", "11110"},
			"g", new String[] {"01110", "10001", "10000", "10111", "10001", "10001", "01110"},
			"i", new String[] {"01110", "00100", "00100", "00100", "00100", "00100", "01110"},
			"k", new String[] {"10001", "10010", "10100", "11000", "10100", "10010", "10001"},
			"n", new String[] {"10001", "11001", "10101", "10011", "10001", "10001", "10001"},
			"r", new String[] {"11110", "10001", "10001", "11110", "10100", "10010", "10001"},
			"u", new String[] {"10001", "10001", "10001", "10001", "10001", "10001", "01110"},
			"v", new String[] {"10001", "10001", "10001", "10001", "10001", "01010", "00100"});

	/** A brass letter: its strokes as boxes two units deep in the middle of the block, facing south. */
	private void letter(Material m) throws IOException {
		String t = tex(m.other());
		StringBuilder s = new StringBuilder("{\n  \"variants\": {\n");
		int n = 0;
		for (var e : FONT.entrySet()) {
			List<String> boxes = new java.util.ArrayList<>();
			String[] rows = e.getValue();
			for (int r = 0; r < rows.length; r++) {
				int y0 = 1 + (rows.length - 1 - r) * 2;
				for (int c = 0; c < 5; c++) {
					if (rows[r].charAt(c) != '1' || c > 0 && rows[r].charAt(c - 1) == '1') {
						continue;
					}
					int end = c;
					while (end + 1 < 5 && rows[r].charAt(end + 1) == '1') {
						end++;
					}
					// Drawn facing south: the letter reads left to right seen from the south.
					double x0 = 3 + c * 2, x1 = 3 + (end + 1) * 2;
					boxes.add(box(x0, y0, 7, x1, y0 + 2, 9, "#face", "#end"));
				}
			}
			String name = m.id() + "_" + e.getKey();
			model(name, elements(t, t, t, boxes.toArray(new String[0])));
			for (String f : FACINGS) {
				s.append(n++ == 0 ? "" : ",\n").append("    \"facing=").append(f).append(",letter=").append(e.getKey()).append("\": ")
						.append(variant("rauk:block/" + name, turn(f) + 180 >= 360 ? turn(f) - 180 : turn(f) + 180));
			}
		}
		state(m.id(), s.append("\n  }\n}\n").toString());
		item(m.id(), "rauk:block/" + m.id() + "_r");
	}

	/**
	 * A slender post: a square shaft a quarter of a block across, a broader foot, and at the top four
	 * arms branching out at 45 degrees, up and out to hold up what's on it.
	 */
	private void post(Material m) throws IOException {
		String t = tex(m.other());
		String shaft = box(6, 0, 6, 10, 16, 10, "#face", "#end");
		model(m.id() + "_shaft", elements(t, t, t, shaft));
		model(m.id() + "_base", elements(t, t, t, box(4, 0, 4, 12, 3, 12, "#face", "#end"), box(5, 3, 5, 11, 4, 11, "#face", "#end"), shaft));
		String[] arms = new String[5];
		arms[0] = box(6, 0, 6, 10, 10, 10, "#face", "#end");
		String[][] turns = {{"z", "45"}, {"z", "-45"}, {"x", "45"}, {"x", "-45"}};
		for (int i = 0; i < 4; i++) {
			String arm = box(7.25, 9, 7.25, 8.75, 18, 8.75, "#face", "#end");
			arms[i + 1] = arm.replaceFirst("\\], \"faces\"", "], \"rotation\": {\"origin\": [8, 9, 8], \"axis\": \"" + turns[i][0]
					+ "\", \"angle\": " + turns[i][1] + "}, \"faces\"");
		}
		model(m.id() + "_capital", elements(t, t, t, arms));
		StringBuilder s = new StringBuilder("{\n  \"variants\": {\n");
		String[] parts = {"base", "shaft", "capital"};
		for (int i = 0; i < 3; i++) {
			s.append("    \"part=").append(parts[i]).append("\": ").append(variant("rauk:block/" + m.id() + "_" + parts[i], 0)).append(i < 2 ? ",\n" : "\n");
		}
		state(m.id(), s.append("  }\n}\n").toString());
		item(m.id(), "rauk:block/" + m.id() + "_shaft");
	}

	/** A wire of bulbs: the wire along the top, a bulb hanging from its middle, dead or lit. */
	private void festoon(Material m) throws IOException {
		String wire = tex("f_chain");
		for (boolean lit : new boolean[] {false, true}) {
			String bulb = tex(lit ? "f_bulb_lit" : "f_bulb");
			String name = m.id() + (lit ? "_lit" : "");
			model(name, "{\n  \"textures\": {\"particle\": \"" + bulb + "\", \"wire\": \"" + wire + "\", \"bulb\": \"" + bulb + "\"},\n"
					+ "  \"elements\": [\n" + box(0, 14.5, 7.5, 16, 15.5, 8.5, "#wire", "#wire") + ",\n"
					+ box(7.5, 13, 7.5, 8.5, 14.5, 8.5, "#wire", "#wire") + ",\n" + box(6.5, 10.5, 6.5, 9.5, 13, 9.5, "#bulb", "#bulb")
					+ "\n  ]\n}\n");
		}
		StringBuilder s = new StringBuilder("{\n  \"variants\": {\n");
		int n = 0;
		for (String axis : new String[] {"x", "z"}) {
			for (boolean lit : new boolean[] {false, true}) {
				s.append(n++ == 0 ? "" : ",\n").append("    \"axis=").append(axis).append(",lit=").append(lit).append("\": ")
						.append(variant("rauk:block/" + m.id() + (lit ? "_lit" : ""), axis.equals("z") ? 90 : 0));
			}
		}
		state(m.id(), s.append("\n  }\n}\n").toString());
		item(m.id(), "rauk:block/" + m.id());
	}

	private static String vanilla(String path) throws IOException {
		try (InputStream in = HotelBlockAssets.class.getClassLoader().getResourceAsStream("assets/minecraft/" + path)) {
			if (in == null) {
				throw new IOException("No vanilla " + path);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/** Each block's name in the English names, and the creative tab's. */
	private void names() throws IOException {
		Path p = assets.resolve("lang/en_us.json");
		JsonObject lang = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
		lang.addProperty("itemGroup.rauk.hotel", "The Raukvik Grand");
		for (Material m : HotelMaterials.ALL) {
			lang.addProperty("block.rauk." + m.id(), m.name());
		}
		Files.writeString(p, GSON.toJson(lang) + "\n", StandardCharsets.UTF_8);
	}

	/** The hotel blocks Rauk can't climb (design 3.4): marble, tiles, metal. */
	private void unclimbable() throws IOException {
		Path p = data.resolve("tags/block/unclimbable.json");
		JsonObject tag = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
		JsonArray values = tag.getAsJsonArray("values");
		for (Material m : HotelMaterials.ALL) {
			String id = "rauk:" + m.id();
			boolean listed = false;
			for (var v : values) {
				listed |= v.isJsonPrimitive() && v.getAsString().equals(id);
			}
			if (!m.climbable() && !listed) {
				values.add(id);
			}
		}
		Files.writeString(p, GSON.toJson(tag) + "\n", StandardCharsets.UTF_8);
	}
}

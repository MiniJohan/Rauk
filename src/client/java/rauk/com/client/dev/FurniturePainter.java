package rauk.com.client.dev;

import static rauk.com.client.dev.HotelTexturePainter.BRASS;
import static rauk.com.client.dev.HotelTexturePainter.BRASS_DARK;
import static rauk.com.client.dev.HotelTexturePainter.BRASS_HI;
import static rauk.com.client.dev.HotelTexturePainter.GRIME;
import static rauk.com.client.dev.HotelTexturePainter.MAH_DARK;
import static rauk.com.client.dev.HotelTexturePainter.MAH_HI;
import static rauk.com.client.dev.HotelTexturePainter.MAH_MID;
import static rauk.com.client.dev.HotelTexturePainter.MOLD;
import static rauk.com.client.dev.HotelTexturePainter.PATINA;
import static rauk.com.client.dev.HotelTexturePainter.RUST;
import static rauk.com.client.dev.HotelTexturePainter.mix;
import static rauk.com.client.dev.HotelTexturePainter.rgb;
import static rauk.com.client.dev.HotelTexturePainter.shade;

import java.util.Map;
import java.util.function.Consumer;

import rauk.com.client.dev.HotelTexturePainter.Tex;

/**
 * Paints what the hotel's furniture is made of (hotel design 12): polished mahogany and its
 * panels and drawers, buttoned leather, velvet, linen and wool, gilt and brass, black iron and
 * enamel, frosted glass and silk that glow when their lamp is lit, candle wax, fire, the pictures
 * on the walls, the rugs on the floors, and the ash, rubble and broken glass of the wrecked rooms.
 * Registered with the hotel's texture painter, which writes them all; their names start {@code f_}.
 * Textures laid once over a face (doors, drawers, pictures) are drawn to fill the whole tile.
 */
final class FurniturePainter {
	private FurniturePainter() {
	}

	static final int OXBLOOD = rgb(104, 28, 24), LINEN = rgb(222, 216, 200), VELVET = rgb(112, 18, 24), GILT = rgb(186, 146, 62),
			GILT_HI = rgb(234, 200, 112), GILT_DARK = rgb(104, 74, 30), IRON_BLACK = rgb(38, 37, 40), PINE = rgb(176, 140, 96),
			OAK = rgb(146, 106, 62), PAPER = rgb(214, 202, 168), INK = rgb(46, 40, 52);

	/** The textures drawn as frames of an animation, with ticks per frame. */
	static final Map<String, Integer> ANIMATED = Map.of("f_fire_lit", 2);

	static void register(Map<String, Consumer<Tex>> p, Map<String, int[]> sizes) {
		// Wood.
		p.put("f_mahogany", t -> wood(t, MAH_MID, MAH_DARK, MAH_HI));
		p.put("f_mahogany_dark", t -> wood(t, shade(MAH_MID, 0.62), shade(MAH_DARK, 0.7), MAH_MID));
		p.put("f_mahogany_worn", t -> worn(t, MAH_MID));
		p.put("f_mahogany_panel", t -> panel(t, MAH_MID, MAH_DARK, MAH_HI));
		p.put("f_charred_wood", FurniturePainter::charred);
		p.put("f_pine", t -> {
			wood(t, PINE, rgb(128, 96, 62), rgb(206, 176, 130));
			grime(t, 0.5);
		});
		p.put("f_oak", t -> wood(t, OAK, rgb(98, 70, 40), rgb(186, 150, 96)));
		p.put("f_drawers", t -> drawers(t, 3));
		p.put("f_filing_front", t -> filing(t));
		p.put("f_desk_top", FurniturePainter::deskTop);
		p.put("f_bar_panel", t -> {
			panel(t, MAH_MID, MAH_DARK, MAH_HI);
			for (int y = 5; y <= 10; y++) {
				t.mix(5, y, BRASS, 0.35);
				t.mix(10, y, BRASS, 0.35);
			}
		});
		p.put("f_desk_diamond", FurniturePainter::diamondPanel);
		p.put("f_icebox_door", t -> {
			panel(t, OAK, rgb(98, 70, 40), rgb(196, 160, 104));
			for (int y : new int[] {3, 4, 11, 12}) {
				t.set(1, y, BRASS);
			}
		});
		p.put("f_wardrobe_door", FurniturePainter::wardrobeDoor);
		sizes.put("f_wardrobe_door", new int[] {16, 32});
		p.put("f_clock_trunk", FurniturePainter::clockTrunk);
		sizes.put("f_clock_trunk", new int[] {16, 32});
		p.put("f_white_paint", FurniturePainter::whitePaint);
		p.put("f_wicker", FurniturePainter::wicker);
		p.put("f_barber_stripes", t -> {
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					int band = Math.floorMod(x + y, 8);
					t.set(x, y, band < 3 ? rgb(150, 30, 34) : band < 4 ? rgb(40, 50, 120) : rgb(226, 220, 204));
				}
			}
			t.noise(0.06);
		});
		// Upholstery and cloth.
		p.put("f_leather_oxblood", t -> leather(t, OXBLOOD, false));
		p.put("f_leather_oxblood_tufted", t -> tufted(t, OXBLOOD));
		p.put("f_leather_oxblood_torn", t -> leather(t, OXBLOOD, true));
		p.put("f_leather_green", t -> leather(t, rgb(38, 70, 46), false));
		p.put("f_leather_green_tufted", t -> tufted(t, rgb(38, 70, 46)));
		p.put("f_leather_brown", t -> leather(t, rgb(96, 60, 36), false));
		p.put("f_leather_tan", t -> leather(t, rgb(150, 100, 58), false));
		p.put("f_velvet_red", t -> velvet(t, VELVET));
		p.put("f_curtain_red", t -> velvetFolds(t, VELVET, false));
		p.put("f_curtain_green", t -> velvetFolds(t, rgb(34, 66, 42), false));
		p.put("f_curtain_torn", t -> velvetFolds(t, shade(VELVET, 0.85), true));
		p.put("f_gold_fringe", t -> fringe(t, false));
		p.put("f_gold_fringe_torn", t -> fringe(t, true));
		p.put("f_linen", t -> linen(t, LINEN, false));
		p.put("f_linen_dirty", t -> {
			linen(t, rgb(206, 196, 168), false);
			stains(t, rgb(150, 120, 70), 3);
		});
		p.put("f_tablecloth", t -> linen(t, rgb(232, 228, 216), true));
		p.put("f_pillow", FurniturePainter::pillow);
		p.put("f_mattress", FurniturePainter::mattress);
		p.put("f_blanket_red", t -> blanket(t, rgb(118, 24, 28), rgb(196, 152, 64)));
		p.put("f_blanket_grey", t -> blanket(t, rgb(104, 102, 98), rgb(60, 58, 56)));
		p.put("f_blanket_pink", t -> blanket(t, rgb(186, 146, 146), rgb(226, 214, 200)));
		p.put("f_dust_sheet", FurniturePainter::dustSheet);
		p.put("f_coat", t -> {
			t.fill(rgb(40, 38, 44));
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					t.shade(x, y, 0.85 + 0.25 * (0.5 + 0.5 * Math.sin(x * 0.9)));
				}
			}
			t.noise(0.05);
		});
		p.put("f_hatbox", t -> {
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					t.set(x, y, x % 4 < 2 ? rgb(196, 160, 150) : rgb(222, 206, 186));
				}
			}
			grime(t, 0.6);
		});
		p.put("f_trunk", FurniturePainter::trunk);
		p.put("f_burlap", FurniturePainter::burlap);
		p.put("f_horsehair", t -> {
			t.fill(rgb(40, 28, 20));
			t.grain(0, 0, 15, 15, false, 0.2);
		});
		p.put("f_dapple", FurniturePainter::dapple);
		p.put("f_doll_dress", t -> {
			linen(t, rgb(214, 186, 184), true);
			grime(t, 0.8);
		});
		p.put("f_doll_face", FurniturePainter::dollFace);
		p.put("f_baize", t -> baize(t, false));
		p.put("f_baize_torn", t -> baize(t, true));
		p.put("f_pocket", t -> {
			t.fill(rgb(28, 20, 16));
			t.noise(0.2);
		});
		// Metal, china, glass.
		p.put("f_iron", t -> iron(t, IRON_BLACK));
		p.put("f_iron_rust", t -> {
			iron(t, rgb(70, 52, 44));
			for (int k = 0; k < 6; k++) {
				t.blob(t.r.nextInt(16), t.r.nextInt(16), 3, RUST, 0.6);
			}
		});
		p.put("f_iron_painted", t -> painted(t, rgb(196, 186, 160)));
		p.put("f_locker_steel", t -> painted(t, rgb(92, 104, 88)));
		// The reception safe: iron enamelled the darkest green, nearly black (puzzle 1).
		p.put("f_safe", t -> painted(t, rgb(34, 46, 38)));
		p.put("f_locker_front", FurniturePainter::lockerFront);
		sizes.put("f_locker_front", new int[] {16, 32});
		p.put("f_zinc", FurniturePainter::zinc);
		p.put("f_silver", t -> metal(t, rgb(176, 176, 170), rgb(90, 86, 80)));
		p.put("f_copper", t -> {
			metal(t, rgb(172, 98, 56), rgb(110, 58, 30));
			for (int k = 0; k < 3; k++) {
				t.blob(t.r.nextInt(16), t.r.nextInt(16), 2.5, PATINA, 0.5);
			}
		});
		p.put("f_enamel", t -> enamel(t, rgb(232, 230, 222), 0));
		p.put("f_enamel_grimy", t -> enamel(t, rgb(220, 214, 196), 2));
		p.put("f_enamel_outside", t -> enamel(t, rgb(46, 66, 52), 1));
		p.put("f_enamel_green", t -> enamel(t, rgb(54, 84, 66), 1));
		p.put("f_porcelain", t -> {
			t.fill(rgb(236, 234, 226));
			t.blob(5, 5, 6, rgb(255, 255, 250), 0.5);
			for (int x = 0; x < 16; x++) {
				t.mix(x, 15, rgb(196, 160, 80), 0.5);
			}
			t.noise(0.012);
		});
		p.put("f_pearl", t -> {
			t.fill(rgb(232, 228, 216));
			t.blob(6, 6, 5, rgb(214, 226, 236), 0.4);
		});
		p.put("f_bakelite", t -> {
			t.fill(rgb(22, 20, 20));
			t.blob(5, 4, 4, rgb(80, 76, 72), 0.5);
			t.noise(0.08);
		});
		p.put("f_chain", t -> iron(t, rgb(60, 58, 56)));
		p.put("f_boiler", FurniturePainter::boilerPlate);
		p.put("f_boiler_door", FurniturePainter::boilerDoor);
		p.put("f_brick_soot", t -> {
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					boolean joint = y % 4 == 3 || (x + (y / 4 % 2) * 4) % 8 == 7;
					t.set(x, y, joint ? rgb(34, 30, 28) : rgb(86, 44, 34));
				}
			}
			for (int i = 0; i < 256; i++) {
				t.mix(i % 16, i / 16, rgb(20, 18, 18), 0.25 + (i / 16) / 40.0);
			}
			t.noise(0.08);
		});
		p.put("f_range_front", FurniturePainter::rangeFront);
		p.put("f_range_top", FurniturePainter::rangeTop);
		p.put("f_cash_register", FurniturePainter::cashRegister);
		p.put("f_register_sign", t -> {
			t.fill(BRASS_DARK);
			t.rect(2, 4, 13, 11, rgb(232, 224, 200));
			for (int x : new int[] {4, 6, 9, 11}) {
				t.set(x, 7, INK);
				t.set(x, 8, INK);
			}
			t.set(8, 9, INK);
		});
		p.put("f_typewriter_keys", t -> {
			t.fill(rgb(20, 20, 22));
			for (int y = 1; y < 16; y += 4) {
				for (int x = (y / 4) % 2; x < 16; x += 3) {
					t.set(x, y, rgb(214, 208, 190));
					t.set(x + 1, y, rgb(214, 208, 190));
					t.set(x, y + 1, rgb(170, 164, 150));
					t.set(x + 1, y + 1, rgb(170, 164, 150));
				}
			}
		});
		p.put("f_glassware", t -> {
			t.fill(rgb(196, 212, 216));
			for (int y = 0; y < 16; y++) {
				t.set(3, y, rgb(240, 248, 250));
				t.set(11, y, rgb(150, 166, 172));
			}
		});
		p.put("f_bottle_green", t -> bottle(t, rgb(30, 56, 32)));
		p.put("f_bottle_amber", t -> bottle(t, rgb(110, 60, 18)));
		p.put("f_water", t -> {
			t.fill(rgb(84, 92, 80));
			t.blob(6, 6, 6, rgb(120, 130, 120), 0.4);
			t.noise(0.04);
		});
		p.put("f_ink", t -> {
			t.fill(rgb(16, 14, 20));
			t.blob(4, 4, 3, rgb(90, 90, 110), 0.5);
		});
		// Light.
		p.put("f_glass_tulip", t -> tulip(t, false));
		p.put("f_glass_tulip_lit", t -> tulip(t, true));
		p.put("f_bulb", t -> bulb(t, false));
		p.put("f_bulb_lit", t -> bulb(t, true));
		p.put("f_crystal", FurniturePainter::crystal);
		p.put("f_lantern_glass", t -> lanternGlass(t, false));
		p.put("f_lantern_glass_lit", t -> lanternGlass(t, true));
		p.put("f_shade_green", t -> greenShade(t, false));
		p.put("f_shade_green_lit", t -> greenShade(t, true));
		p.put("f_shade_silk", t -> silk(t, false));
		p.put("f_shade_silk_lit", t -> silk(t, true));
		p.put("f_chimney", t -> chimney(t, false));
		p.put("f_chimney_lit", t -> chimney(t, true));
		p.put("f_wax", FurniturePainter::wax);
		p.put("f_wick", t -> t.fill(rgb(34, 26, 20)).noise(0.2));
		p.put("f_soot", t -> {
			t.fill(rgb(26, 22, 20));
			t.noise(0.18);
			for (int y = 10; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					t.mix(x, y, rgb(60, 40, 30), (y - 10) / 14.0);
				}
			}
		});
		p.put("f_log", t -> log(t, false));
		p.put("f_log_lit", t -> log(t, true));
		p.put("f_fire_lit", FurniturePainter::fire);
		sizes.put("f_fire_lit", new int[] {16, 16 * 8});
		p.put("f_ember", t -> {
			t.fill(rgb(30, 26, 26));
			for (int x = 1; x < 16; x += 3) {
				for (int y = 2; y < 14; y++) {
					t.set(x, y, rgb(90, 40, 24));
				}
			}
		});
		p.put("f_gilt", FurniturePainter::gilt);
		// Pictures, papers, books, things.
		for (int i = 0; i < 4; i++) {
			int look = i;
			p.put("f_portrait_" + i, t -> portrait(t, look));
			sizes.put("f_portrait_" + i, new int[] {16, 32});
		}
		p.put("f_seascape", FurniturePainter::seascape);
		sizes.put("f_seascape", new int[] {32, 16});
		p.put("f_mirror", FurniturePainter::mirror);
		sizes.put("f_mirror", new int[] {16, 32});
		p.put("f_clock_face", FurniturePainter::clockFace);
		p.put("f_clock_face_bare", FurniturePainter::clockFaceBare);
		p.put("f_cork", FurniturePainter::cork);
		p.put("f_paper", t -> paper(t, 0));
		p.put("f_pages", t -> {
			t.fill(PAPER);
			for (int y = 0; y < 16; y += 2) {
				for (int x = 0; x < 16; x++) {
					t.shade(x, y, 0.88);
				}
			}
		});
		p.put("f_pages_left", t -> paper(t, 1));
		p.put("f_pages_right", t -> paper(t, 2));
		p.put("f_sheet_music", t -> paper(t, 3));
		p.put("f_books", FurniturePainter::bookSpines);
		p.put("f_record", t -> {
			t.fill(rgb(18, 16, 18));
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					double d = Math.hypot(x - 7.5, y - 7.5);
					if (d < 2.6) {
						t.set(x, y, rgb(140, 30, 30));
					} else if ((int) d % 2 == 0) {
						t.set(x, y, rgb(34, 32, 34));
					}
				}
			}
		});
		p.put("f_globe", FurniturePainter::globe);
		p.put("f_dead_flowers", FurniturePainter::deadFlowers);
		p.put("f_palm_dead", FurniturePainter::palmFrond);
		p.put("f_palm_trunk", t -> {
			t.fill(rgb(92, 70, 46));
			for (int y = 0; y < 16; y += 3) {
				for (int x = 0; x < 16; x++) {
					t.set(x, y, rgb(60, 44, 30));
				}
			}
			t.noise(0.1);
		});
		p.put("f_soil", t -> {
			t.fill(rgb(70, 54, 40));
			t.noise(0.2);
		});
		p.put("f_piano_black", FurniturePainter::lacquer);
		p.put("f_piano_keys", FurniturePainter::pianoKeys);
		sizes.put("f_piano_keys", new int[] {32, 16});
		// The ballroom grand's eight broad keys, C to C (puzzle 3). The texture runs from the
		// pianist's right (its u = 0) to the left, so the black keys fall the right way round for
		// someone sitting at it: between B and A, A and G, G and F, E and D, D and C.
		p.put("f_piano_keys8", t -> {
			for (int x = 0; x < 32; x++) {
				for (int y = 0; y < 16; y++) {
					t.set(x, y, x % 4 == 3 ? rgb(150, 144, 130) : rgb(226, 218, 196));
				}
			}
			for (int b : new int[] {8, 12, 16, 24, 28}) {
				for (int x = b - 1; x <= b; x++) {
					for (int y = 7; y < 16; y++) {
						t.set(x, y, rgb(20, 18, 18));
					}
				}
			}
			grime(t, 0.4);
		});
		sizes.put("f_piano_keys8", new int[] {32, 16});
		// Wreckage.
		p.put("f_ash", t -> {
			t.fill(rgb(118, 114, 108));
			t.noise(0.18);
			for (int k = 0; k < 12; k++) {
				t.set(t.r.nextInt(16), t.r.nextInt(16), rgb(30, 28, 26));
			}
			for (int k = 0; k < 6; k++) {
				t.set(t.r.nextInt(16), t.r.nextInt(16), rgb(196, 192, 184));
			}
		});
		// The porter's remains in the cold room (puzzle 2): old bone, and blood dried brown, in a stain
		// that never fades (Rauk's own stains do).
		p.put("f_bone", t -> {
			t.fill(rgb(206, 194, 162));
			t.grain(0, 0, 15, 15, true, 0.06);
			t.noise(0.05);
			for (int k = 0; k < 5; k++) {
				t.set(t.r.nextInt(16), t.r.nextInt(16), rgb(150, 128, 96));
			}
		});
		p.put("f_old_blood", t -> {
			// Dried blood that runs on from block to block into one pool, the floor showing through
			// where it cracked and flaked.
			t.fill(rgb(70, 20, 16));
			for (int k = 0; k < 10; k++) {
				t.blob(t.r.nextInt(16), t.r.nextInt(16), 2.5, rgb(46, 12, 10), 0.6);
			}
			for (int k = 0; k < 6; k++) {
				t.blob(t.r.nextInt(16), t.r.nextInt(16), 1.5, rgb(104, 32, 24), 0.5);
			}
			for (int k = 0; k < 14; k++) {
				t.clear(t.r.nextInt(16), t.r.nextInt(16));
			}
		});
		p.put("f_rubble", FurniturePainter::rubble);
		p.put("f_glass_shards", FurniturePainter::shards);
		p.put("f_coal", t -> {
			t.fill(rgb(24, 24, 26));
			for (int k = 0; k < 14; k++) {
				t.blob(t.r.nextInt(16), t.r.nextInt(16), 2, rgb(54, 54, 58), 0.6);
			}
			for (int k = 0; k < 6; k++) {
				t.set(t.r.nextInt(16), t.r.nextInt(16), rgb(120, 120, 126));
			}
		});
		// Doors, 16 by 32: their faces.
		p.put("f_door_hotel_front", t -> hotelDoor(t, true));
		p.put("f_door_hotel_back", t -> hotelDoor(t, false));
		p.put("f_door_service", FurniturePainter::serviceDoor);
		p.put("f_door_glass", FurniturePainter::glassDoor);
		p.put("f_lift_gate", FurniturePainter::liftGate);
		p.put("f_lift_dial", FurniturePainter::liftDial);
		p.put("lift_floor", FurniturePainter::treadPlate);
		p.put("lift_roof", t -> {
			boilerPlate(t);
			grime(t, 0.5);
		});
		for (String d : new String[] {"f_door_hotel_front", "f_door_hotel_back", "f_door_service", "f_door_glass", "f_lift_gate"}) {
			sizes.put(d, new int[] {16, 32});
		}
		// Fittings that fill a block: their fronts.
		p.put("bookcase", t -> shelves(t, 0, false));
		p.put("bookcase_worn", t -> shelves(t, 0, true));
		p.put("pantry_shelves", t -> shelves(t, 1, true));
		p.put("linen_shelves", t -> shelves(t, 2, true));
		p.put("bottle_shelves", t -> shelves(t, 3, false));
		p.put("pigeonholes", FurniturePainter::pigeonholes);
		p.put("wine_rack", FurniturePainter::wineRack);
		p.put("bar_cabinet", t -> {
			wood(t, MAH_MID, MAH_DARK, MAH_HI);
			raised(t, 1, 1, 7, 14, MAH_DARK, MAH_HI);
			raised(t, 8, 1, 14, 14, MAH_DARK, MAH_HI);
			t.set(6, 8, BRASS_HI);
			t.set(9, 8, BRASS_HI);
			for (int x = 0; x < 16; x++) {
				t.set(x, 0, MAH_HI);
				t.set(x, 15, MAH_DARK);
			}
		});
		p.put("crate", FurniturePainter::crate);
		// Rugs: a field, and a border round all four edges of its own tile.
		p.put("f_rug_red_field", t -> rugField(t, rgb(122, 26, 30), rgb(24, 30, 64), rgb(200, 160, 90), false));
		p.put("f_rug_red", t -> rugBorder(t, rgb(122, 26, 30), rgb(24, 30, 64), rgb(200, 160, 90), false));
		p.put("f_rug_green_field", t -> rugField(t, rgb(34, 62, 44), rgb(110, 30, 30), rgb(196, 170, 110), false));
		p.put("f_rug_green", t -> rugBorder(t, rgb(34, 62, 44), rgb(110, 30, 30), rgb(196, 170, 110), false));
		p.put("f_rug_worn_field", t -> rugField(t, rgb(130, 70, 60), rgb(70, 66, 76), rgb(180, 160, 120), true));
		p.put("f_rug_worn", t -> rugBorder(t, rgb(130, 70, 60), rgb(70, 66, 76), rgb(180, 160, 120), true));
	}

	// --- small helpers ---------------------------------------------------------------------------

	static void line(Tex t, int x0, int y0, int x1, int y1, int c, double a) {
		int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
		for (int i = 0; i <= n; i++) {
			double f = n == 0 ? 0 : (double) i / n;
			t.mix((int) Math.round(x0 + (x1 - x0) * f), (int) Math.round(y0 + (y1 - y0) * f), c, a);
		}
	}

	static void disc(Tex t, double cx, double cy, double r, int c) {
		for (int y = 0; y < t.h; y++) {
			for (int x = 0; x < t.w; x++) {
				if (Math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r) {
					t.set(x, y, c);
				}
			}
		}
	}

	/** Dirt settled over a texture, thickest at the bottom. */
	static void grime(Tex t, double amount) {
		for (int y = 0; y < t.h; y++) {
			for (int x = 0; x < t.w; x++) {
				t.mix(x, y, GRIME, amount * (0.08 + 0.14 * y / t.h) * (0.6 + t.r.nextDouble() * 0.8));
			}
		}
	}

	static void stains(Tex t, int c, int n) {
		for (int k = 0; k < n; k++) {
			t.blob(t.r.nextInt(t.w), t.r.nextInt(t.h), 2 + t.r.nextInt(4), c, 0.35);
		}
	}

	/** A raised panel inside a rectangle: dark groove, lit bevel top and left, shadow bottom and right. */
	static void raised(Tex t, int x0, int y0, int x1, int y1, int dark, int light) {
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				boolean groove = x == x0 || x == x1 || y == y0 || y == y1;
				if (groove) {
					t.mix(x, y, dark, 0.7);
				} else if (x == x0 + 1 || y == y0 + 1) {
					t.mix(x, y, light, 0.4);
				} else if (x == x1 - 1 || y == y1 - 1) {
					t.mix(x, y, dark, 0.45);
				} else {
					t.shade(x, y, 1.05);
				}
			}
		}
	}

	static void pull(Tex t, int x, int y) {
		t.set(x - 1, y, BRASS_DARK);
		t.set(x, y, BRASS_HI);
		t.set(x + 1, y, BRASS);
	}

	// --- wood ------------------------------------------------------------------------------------

	/** Polished wood: fine grain along it, a few darker waves of figure, a soft sheen. */
	static void wood(Tex t, int base, int dark, int light) {
		t.fill(base);
		t.grain(0, 0, t.w - 1, t.h - 1, true, 0.09);
		for (int k = 0; k < 4; k++) {
			int y = t.r.nextInt(t.h);
			double ph = t.r.nextDouble() * 6;
			for (int x = 0; x < t.w; x++) {
				t.mix(x, y + (int) Math.round(Math.sin(ph + x * 0.45) * 1.2), dark, 0.32);
			}
		}
		for (int x = 0; x < t.w; x++) {
			for (int y = 5; y < 8; y++) {
				t.mix(x, y, light, 0.1);
			}
		}
		t.noise(0.025);
	}

	/** Mahogany gone dull: dusty, scratched pale, a ring where a glass stood. */
	static void worn(Tex t, int base) {
		wood(t, base, MAH_DARK, MAH_HI);
		for (int i = 0; i < 256; i++) {
			t.mix(i % 16, i / 16, rgb(120, 104, 90), 0.2);
		}
		for (int k = 0; k < 4; k++) {
			line(t, t.r.nextInt(16), t.r.nextInt(16), t.r.nextInt(16), t.r.nextInt(16), rgb(170, 130, 100), 0.4);
		}
		for (int a = 0; a < 24; a++) {
			double ang = a * Math.PI / 12;
			t.mix(9 + (int) Math.round(Math.cos(ang) * 3), 9 + (int) Math.round(Math.sin(ang) * 3), rgb(160, 130, 104), 0.35);
		}
	}

	/** A raised panel in its frame, laid once over a face (a door, a headboard). */
	static void panel(Tex t, int base, int dark, int light) {
		wood(t, base, dark, light);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (x < 2 || x > 13 || y < 2 || y > 13) {
					t.shade(x, y, 0.9);
				}
			}
		}
		raised(t, 2, 2, 13, 13, dark, light);
		for (int i = 0; i < 16; i++) {
			t.mix(i, 0, light, 0.25);
			t.mix(0, i, light, 0.15);
			t.mix(i, 15, dark, 0.4);
			t.mix(15, i, dark, 0.3);
		}
	}

	/** Wood burned to char: alligatored into blisters with deep black cracks, a grey sheen on them. */
	static void charred(Tex t) {
		t.fill(rgb(34, 28, 24));
		int row = 0;
		for (int y = 0; y < 16; y += 3 + row % 2) {
			int x = t.r.nextInt(4);
			while (x < 16) {
				int w = 3 + t.r.nextInt(4);
				for (int i = 0; i < w; i++) {
					t.set(x + i, y, rgb(8, 6, 6));
					if (t.r.nextInt(3) == 0) {
						t.set(x + i, y + 1, rgb(14, 12, 10));
					}
				}
				for (int j = 1; j < 3; j++) {
					t.set(x + w, y + j, rgb(8, 6, 6));
					t.mix(x + 1, y + j, rgb(96, 90, 86), 0.35);
				}
				x += w + 1;
			}
			row++;
		}
		for (int k = 0; k < 3; k++) {
			t.set(t.r.nextInt(16), t.r.nextInt(16), rgb(110, 50, 30));
		}
		t.noise(0.08);
	}

	/** Drawer fronts laid over a face: so many drawers, each a panel with a brass pull. */
	static void drawers(Tex t, int n) {
		wood(t, MAH_MID, MAH_DARK, MAH_HI);
		int h = 16 / n;
		for (int i = 0; i < n; i++) {
			int y0 = i * h, y1 = i == n - 1 ? 15 : y0 + h - 1;
			raised(t, 1, y0 + 1, 14, y1 - 1, MAH_DARK, MAH_HI);
			for (int x = 0; x < 16; x++) {
				t.set(x, y0, shade(MAH_DARK, 0.8));
			}
			pull(t, 7, (y0 + y1) / 2);
			t.set(8, (y0 + y1) / 2, BRASS);
		}
	}

	static void filing(Tex t) {
		wood(t, OAK, rgb(98, 70, 40), rgb(186, 150, 96));
		for (int i = 0; i < 4; i++) {
			int y0 = i * 4;
			for (int x = 0; x < 16; x++) {
				t.set(x, y0, rgb(70, 50, 30));
			}
			t.rect(6, y0 + 1, 9, y0 + 1, BRASS);
			t.rect(7, y0 + 1, 8, y0 + 1, rgb(220, 212, 190));
			t.rect(6, y0 + 2, 9, y0 + 2, BRASS_DARK);
		}
	}

	/** A desk's top: green leather inside a tooled gold line, a mahogany edge. */
	static void deskTop(Tex t) {
		wood(t, MAH_MID, MAH_DARK, MAH_HI);
		for (int y = 1; y < 15; y++) {
			for (int x = 1; x < 15; x++) {
				t.set(x, y, shade(rgb(40, 74, 50), 0.9 + t.r.nextDouble() * 0.15));
			}
		}
		for (int i = 2; i < 14; i++) {
			t.set(i, 2, GILT);
			t.set(i, 13, GILT);
			t.set(2, i, GILT);
			t.set(13, i, GILT);
		}
		t.blob(10, 6, 3, rgb(70, 60, 40), 0.3);
	}

	/** The reception desk's panels (after the reference): nested diamonds of light and dark veneer. */
	static void diamondPanel(Tex t) {
		wood(t, MAH_MID, MAH_DARK, MAH_HI);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int d = (int) (Math.abs(x - 7.5) + Math.abs(y - 7.5));
				if (x < 1 || x > 14 || y < 1 || y > 14) {
					t.shade(x, y, 0.82);
				} else if (d <= 7) {
					int band = d / 2;
					t.set(x, y, band % 2 == 0 ? shade(MAH_HI, 0.85 + 0.05 * band) : shade(MAH_DARK, 1.1));
					t.mix(x, y, x < 8 ? MAH_HI : MAH_DARK, 0.15);
				}
			}
		}
		t.set(7, 7, BRASS);
		t.set(8, 8, BRASS_DARK);
		t.noise(0.03);
	}

	/** A tall wardrobe door, 16 by 32: a long upper panel over a short one. */
	static void wardrobeDoor(Tex t) {
		wood(t, MAH_MID, MAH_DARK, MAH_HI);
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 16; x++) {
				if (x < 2 || x > 13 || y < 2 || y > 29 || (y > 19 && y < 22)) {
					t.shade(x, y, 0.9);
				}
			}
		}
		raised(t, 2, 2, 13, 19, MAH_DARK, MAH_HI);
		raised(t, 2, 22, 13, 29, MAH_DARK, MAH_HI);
		for (int i = 0; i < 32; i++) {
			t.mix(0, i, MAH_HI, 0.2);
			t.mix(15, i, MAH_DARK, 0.4);
		}
		t.set(1, 6, BRASS);
		t.set(1, 25, BRASS);
	}

	/** A grandfather clock's trunk door, 16 by 32: glass showing the still brass pendulum. */
	static void clockTrunk(Tex t) {
		wood(t, MAH_MID, MAH_DARK, MAH_HI);
		for (int y = 4; y < 28; y++) {
			for (int x = 4; x < 12; x++) {
				t.set(x, y, mix(rgb(30, 20, 14), rgb(70, 60, 50), (x - 4) / 16.0));
			}
		}
		for (int y = 5; y < 22; y++) {
			t.set(8, y, BRASS_DARK);
		}
		disc(t, 8.5, 23.5, 2.6, BRASS);
		t.set(7, 22, BRASS_HI);
		raised(t, 3, 3, 12, 28, MAH_DARK, MAH_HI);
		for (int y = 5; y < 27; y += 3) {
			t.mix(5, y, rgb(200, 210, 210), 0.25);
		}
	}

	static void whitePaint(Tex t) {
		t.fill(rgb(222, 218, 206));
		t.noise(0.02);
		for (int k = 0; k < 7; k++) {
			int x = t.r.nextInt(16), y = t.r.nextInt(16);
			t.set(x, y, rgb(140, 104, 70));
			if (t.r.nextBoolean()) {
				t.set(x + 1, y, rgb(160, 120, 84));
			}
		}
		grime(t, 0.6);
	}

	static void wicker(Tex t) {
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				boolean over = ((x / 2) + (y / 2)) % 2 == 0;
				t.set(x, y, over ? rgb(182, 146, 92) : rgb(130, 98, 58));
			}
		}
		t.noise(0.07);
	}

	// --- upholstery and cloth ----------------------------------------------------------------------

	/** Smooth old leather: crazed with fine cracks, a sheen on it; torn, the stuffing showing. */
	static void leather(Tex t, int base, boolean torn) {
		t.fill(base);
		t.noise(0.05);
		t.blob(5, 4, 6, shade(base, 1.35), 0.3);
		for (int k = 0; k < 7; k++) {
			t.walk(t.r.nextInt(16), t.r.nextInt(16), 5, shade(base, 0.6), 0.5, t.r.nextBoolean() ? 1 : -1, 1);
		}
		for (int k = 0; k < 3; k++) {
			t.blob(t.r.nextInt(16), t.r.nextInt(16), 2.5, shade(base, 1.25), 0.25);
		}
		if (torn) {
			// One ragged rip, the horsehair stuffing bulging out of it, and the leather scuffed pale.
			int stuffing = rgb(186, 166, 120);
			int x = 2 + t.r.nextInt(6), y = 3 + t.r.nextInt(8);
			for (int i = 0; i < 8; i++) {
				int open = i == 0 || i == 7 ? 1 : 1 + t.r.nextInt(2);
				for (int j = 0; j < open; j++) {
					t.set(x + i, y + j, j == 0 ? stuffing : shade(stuffing, 0.82));
				}
				t.set(x + i, y - 1, shade(base, 0.4));
				t.set(x + i, y + open, shade(base, 0.6));
				y += t.r.nextInt(3) - 1;
			}
			for (int k = 0; k < 4; k++) {
				t.blob(t.r.nextInt(16), t.r.nextInt(16), 2, rgb(170, 130, 110), 0.3);
			}
			t.blob(t.r.nextInt(16), t.r.nextInt(16), 3, MOLD, 0.35);
			for (int i = 0; i < 256; i++) {
				t.mix(i % 16, i / 16, rgb(110, 90, 80), 0.15);
			}
		}
	}

	/** Deep-buttoned leather: puffs between the buttons, creases running diagonally between them. */
	static void tufted(Tex t, int base) {
		int[][] buttons = {{2, 2}, {10, 2}, {6, 6}, {14, 6}, {2, 10}, {10, 10}, {6, 14}, {14, 14}};
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double d = 99;
				for (int[] b : buttons) {
					int dx = Math.abs(x - b[0]), dy = Math.abs(y - b[1]);
					d = Math.min(d, Math.hypot(Math.min(dx, 16 - dx), Math.min(dy, 16 - dy)));
				}
				double s = Math.min(d / 4.0, 1);
				double f = 0.72 + 0.33 * (3 * s * s - 2 * s * s * s);
				int c = shade(base, f);
				if (d > 1.2 && (Math.floorMod(x - y, 8) == 0 || Math.floorMod(x + y, 8) == 4)) {
					c = shade(c, 0.86);
				}
				t.set(x, y, c);
			}
		}
		for (int[] b : buttons) {
			t.set(b[0], b[1], shade(base, 0.5));
			t.mix(b[0] + 2, b[1] - 2, shade(base, 1.6), 0.22);
		}
		t.noise(0.03);
	}

	/** Velvet: a short deep pile, lighter where it catches the light. */
	static void velvet(Tex t, int base) {
		t.fill(base);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				t.shade(x, y, 0.9 + 0.2 * (0.5 + 0.5 * Math.sin((x + y) * 0.35)));
			}
		}
		t.noise(0.06);
	}

	/** Heavy velvet in vertical folds: lit along the crests, deep in the hollows. Torn: holes, faded. */
	static void velvetFolds(Tex t, int base, boolean torn) {
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double f = 0.7 + 0.32 * (0.5 + 0.5 * Math.sin(x * Math.PI / 4 + 0.6));
				t.set(x, y, shade(base, f));
			}
		}
		for (int k = 0; k < 5; k++) {
			int x = t.r.nextInt(16);
			for (int y = 0; y < 16; y++) {
				t.mix(x, y, shade(base, 1.4), 0.12);
			}
		}
		t.noise(0.05);
		if (torn) {
			for (int i = 0; i < 256; i++) {
				t.mix(i % 16, i / 16, rgb(120, 100, 90), 0.2);
			}
			grime(t, 0.8);
			for (int k = 0; k < 4; k++) {
				int cx = t.r.nextInt(16), cy = t.r.nextInt(16);
				double r = 0.8 + t.r.nextDouble() * 1.6;
				for (int y = 0; y < 16; y++) {
					for (int x = 0; x < 16; x++) {
						double d = Math.hypot(x - cx, (y - cy) * 0.6);
						if (d < r) {
							t.clear(x, y);
						} else if (d < r + 1) {
							t.shade(x, y, 0.6);
						}
					}
				}
			}
		}
	}

	/** A gold fringe: a braided band along the top, threads hanging from it, gaps between. */
	static void fringe(Tex t, boolean torn) {
		int gilt = torn ? mix(GILT, rgb(90, 80, 60), 0.4) : GILT, hi = torn ? mix(GILT_HI, rgb(110, 100, 80), 0.4) : GILT_HI;
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (y < 5) {
					t.set(x, y, Math.floorMod(x + y, 4) < 2 ? hi : gilt);
				} else {
					t.clear(x, y);
				}
			}
		}
		for (int x = 0; x < 16; x += 2) {
			if (torn && t.r.nextInt(3) == 0) {
				continue;
			}
			int end = torn ? 7 + t.r.nextInt(9) : 11 + t.r.nextInt(5);
			for (int y = 5; y <= end; y++) {
				t.set(x, y, y == end ? hi : y % 3 == 0 ? GILT_DARK : gilt);
			}
		}
		t.noise(0.05);
	}

	static void linen(Tex t, int base, boolean pattern) {
		t.fill(base);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double f = ((x + y) % 2 == 0 ? 1.015 : 0.985) + Math.sin(x * Math.PI / 8 + 0.7) * 0.025;
				if (pattern && (Math.floorMod(x - y, 8) == 0 || Math.floorMod(x + y, 8) == 0)) {
					f *= 0.965;
				}
				t.shade(x, y, f);
			}
		}
		t.noise(0.018);
	}

	static void pillow(Tex t) {
		t.fill(rgb(232, 228, 216));
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double d = Math.max(Math.abs(x - 7.5), Math.abs(y - 7.5)) / 8;
				t.shade(x, y, 1.03 - d * d * 0.16);
			}
		}
		t.noise(0.02);
	}

	/** Ticking: blue-grey stripes on off-white, and what ninety years did to it. */
	static void mattress(Tex t) {
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				t.set(x, y, x % 4 == 0 ? rgb(96, 104, 120) : x % 4 == 1 ? rgb(150, 156, 164) : rgb(214, 206, 186));
			}
		}
		t.noise(0.03);
		stains(t, rgb(150, 112, 60), 3);
		t.blob(t.r.nextInt(16), t.r.nextInt(16), 3, rgb(90, 60, 40), 0.4);
	}

	/** Wool: a close knit, a band across it near one end. */
	static void blanket(Tex t, int base, int band) {
		t.fill(base);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				t.shade(x, y, (x + (y % 2)) % 2 == 0 ? 1.04 : 0.95);
			}
		}
		for (int x = 0; x < 16; x++) {
			t.set(x, 12, shade(base, 0.7));
			t.set(x, 13, mix(band, base, 0.3));
			t.set(x, 14, shade(base, 0.7));
		}
		t.noise(0.04);
	}

	/** A dust sheet: heavy off-white cotton, soft folds, grey with dust where it lies flat. */
	static void dustSheet(Tex t) {
		t.fill(rgb(206, 200, 184));
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				t.shade(x, y, 0.92 + 0.1 * Math.sin(x * 0.7 + y * 0.25) + ((x + y) % 2 == 0 ? 0.01 : -0.01));
			}
		}
		for (int k = 0; k < 3; k++) {
			t.blob(t.r.nextInt(16), t.r.nextInt(16), 4, rgb(150, 146, 136), 0.3);
		}
		stains(t, rgb(170, 150, 110), 1);
		t.noise(0.02);
	}

	static void trunk(Tex t) {
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				t.set(x, y, x % 3 == 0 ? rgb(70, 60, 40) : rgb(96, 84, 58));
			}
		}
		for (int y : new int[] {4, 11}) {
			for (int x = 0; x < 16; x++) {
				t.set(x, y, PINE);
				t.set(x, y + 1, shade(PINE, 0.75));
			}
		}
		grime(t, 0.6);
		t.noise(0.04);
	}

	static void burlap(Tex t) {
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				t.set(x, y, (x % 2 == 0) ^ (y % 2 == 0) ? rgb(156, 128, 86) : rgb(124, 100, 64));
			}
		}
		t.noise(0.06);
		stains(t, rgb(220, 214, 196), 1);
	}

	static void dapple(Tex t) {
		t.fill(rgb(222, 216, 204));
		for (int k = 0; k < 6; k++) {
			t.blob(t.r.nextInt(16), t.r.nextInt(16), 1.6, rgb(120, 116, 112), 0.6);
		}
		grime(t, 0.5);
	}

	/** A doll's porcelain face, laid over its front: black eyes, rosy cheeks, a crack across it. */
	static void dollFace(Tex t) {
		t.fill(rgb(234, 222, 208));
		t.blob(4, 10, 2.5, rgb(220, 150, 150), 0.4);
		t.blob(12, 10, 2.5, rgb(220, 150, 150), 0.4);
		t.rect(4, 6, 5, 8, rgb(14, 12, 12));
		t.rect(10, 6, 11, 8, rgb(14, 12, 12));
		t.set(4, 6, rgb(80, 80, 90));
		t.set(10, 6, rgb(80, 80, 90));
		t.rect(7, 12, 8, 12, rgb(150, 40, 46));
		line(t, 2, 2, 9, 15, rgb(60, 50, 44), 0.7);
		grime(t, 0.4);
	}

	static void baize(Tex t, boolean torn) {
		t.fill(rgb(30, 96, 54));
		t.noise(0.06);
		if (torn) {
			for (int i = 0; i < 256; i++) {
				t.mix(i % 16, i / 16, rgb(90, 90, 70), 0.25);
			}
			for (int k = 0; k < 2; k++) {
				int x = t.r.nextInt(12), y = t.r.nextInt(16), len = 4 + t.r.nextInt(6);
				for (int i = 0; i < len; i++) {
					t.set(x + i, y, rgb(60, 44, 30));
					t.set(x + i, y + 1, rgb(26, 50, 30));
				}
			}
			stains(t, rgb(60, 30, 26), 2);
		}
	}

	// --- metal, china, glass ------------------------------------------------------------------------

	static void iron(Tex t, int base) {
		t.fill(base);
		t.noise(0.08);
		t.blob(4, 4, 5, shade(base, 1.5), 0.25);
		for (int k = 0; k < 4; k++) {
			t.set(t.r.nextInt(16), t.r.nextInt(16), mix(base, RUST, 0.6));
		}
	}

	static void metal(Tex t, int base, int tarnish) {
		t.fill(base);
		t.grain(0, 0, 15, 15, true, 0.05);
		t.blob(5, 5, 5, shade(base, 1.25), 0.4);
		for (int k = 0; k < 3; k++) {
			t.blob(t.r.nextInt(16), t.r.nextInt(16), 3, tarnish, 0.45);
		}
	}

	/** Paint over iron (a radiator, a locker), chipped through in places, grimy. */
	static void painted(Tex t, int paint) {
		t.fill(paint);
		t.noise(0.04);
		for (int k = 0; k < 6; k++) {
			int x = t.r.nextInt(16), y = t.r.nextInt(16);
			t.set(x, y, IRON_BLACK);
			if (t.r.nextBoolean()) {
				t.set(x + 1, y, mix(IRON_BLACK, RUST, 0.5));
			}
		}
		grime(t, 0.6);
	}

	/** A staff locker's door, 16 by 32: louvres at the top, a handle, a brass number plate, rust. */
	static void lockerFront(Tex t) {
		painted(t, rgb(92, 104, 88));
		for (int y = 3; y < 10; y += 2) {
			t.rect(4, y, 11, y, rgb(30, 34, 30));
			t.rect(4, y + 1, 11, y + 1, rgb(130, 140, 124));
		}
		t.rect(12, 15, 12, 19, rgb(160, 162, 156));
		t.rect(12, 15, 12, 15, rgb(200, 200, 196));
		t.rect(6, 24, 9, 26, BRASS);
		t.set(7, 25, BRASS_DARK);
		t.set(8, 25, BRASS_DARK);
		for (int x = 0; x < 16; x++) {
			t.set(x, 0, rgb(60, 70, 58));
			t.set(x, 31, rgb(50, 56, 48));
		}
		for (int k = 0; k < 3; k++) {
			int x = t.r.nextInt(16), y = 10 + t.r.nextInt(16);
			for (int i = 0; i < 5; i++) {
				t.mix(x, y + i, RUST, 0.5 - i * 0.08);
			}
		}
	}

	static void zinc(Tex t) {
		t.fill(rgb(148, 150, 148));
		t.noise(0.05);
		for (int k = 0; k < 4; k++) {
			t.blob(t.r.nextInt(16), t.r.nextInt(16), 3, rgb(110, 110, 104), 0.35);
		}
		for (int k = 0; k < 5; k++) {
			line(t, t.r.nextInt(16), t.r.nextInt(16), t.r.nextInt(16), t.r.nextInt(16), rgb(186, 188, 186), 0.4);
		}
		stains(t, rgb(100, 70, 50), 2);
	}

	/** Enamel: glossy, chipped to black iron; grimy (1, a bit; 2, a lot, rust under the taps). */
	static void enamel(Tex t, int base, int dirt) {
		t.fill(base);
		t.blob(5, 5, 6, shade(base, 1.08), 0.5);
		t.noise(0.015);
		for (int k = 0; k < 3; k++) {
			t.set(t.r.nextInt(16), t.r.nextInt(16), IRON_BLACK);
		}
		if (dirt > 0) {
			grime(t, dirt == 2 ? 1.6 : 0.5);
		}
		if (dirt == 2) {
			for (int k = 0; k < 3; k++) {
				int x = t.r.nextInt(16);
				for (int y = 0; y < 8; y++) {
					t.mix(x, y, RUST, 0.45 - y * 0.05);
				}
			}
		}
	}

	static void boilerPlate(Tex t) {
		iron(t, rgb(56, 54, 54));
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (y % 8 == 0) {
					t.set(x, y, rgb(30, 28, 28));
				}
				if (y % 8 == 1 && x % 3 == 1 || x % 8 == 1 && y % 3 == 2) {
					t.set(x, y, rgb(110, 106, 100));
				}
			}
		}
		for (int k = 0; k < 3; k++) {
			int x = t.r.nextInt(16), y = t.r.nextInt(8);
			for (int i = 0; i < 7; i++) {
				t.mix(x, y + i, RUST, 0.55 - i * 0.06);
			}
		}
	}

	/** The boiler's front: two round fire doors, latched, rivets all round. */
	static void boilerDoor(Tex t) {
		boilerPlate(t);
		for (int[] c : new int[][] {{5, 10}, {11, 10}}) {
			disc(t, c[0] + 0.5, c[1] + 0.5, 3, rgb(32, 30, 30));
			disc(t, c[0] + 0.5, c[1] + 0.5, 2, rgb(66, 62, 60));
			t.set(c[0] + 2, c[1], BRASS_DARK);
		}
		disc(t, 8.5, 4, 1.6, rgb(220, 214, 196));
		t.set(8, 4, INK);
	}

	/** A range's front, laid once over it: two oven doors with brass handles, an ash drawer below. */
	static void rangeFront(Tex t) {
		iron(t, IRON_BLACK);
		for (int[] d : new int[][] {{1, 2, 7, 10}, {9, 2, 14, 10}}) {
			t.rect(d[0], d[1], d[2], d[3], rgb(52, 50, 54));
			for (int x = d[0]; x <= d[2]; x++) {
				t.set(x, d[1], rgb(84, 82, 86));
				t.set(x, d[3], rgb(22, 20, 22));
			}
			t.rect(d[0] + 1, d[1] + 2, d[2] - 1, d[1] + 2, BRASS);
			t.set(d[0] + 1, d[1] + 2, BRASS_HI);
		}
		t.rect(3, 12, 12, 14, rgb(30, 28, 30));
		t.rect(6, 13, 9, 13, BRASS_DARK);
		disc(t, 8, 6, 1, rgb(196, 190, 170));
		grime(t, 0.8);
	}

	static void rangeTop(Tex t) {
		iron(t, rgb(30, 30, 32));
		for (int[] c : new int[][] {{4, 4}, {11, 4}, {4, 11}, {11, 11}}) {
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					double d = Math.hypot(x - c[0], y - c[1]);
					if (d < 3.2 && (int) (d * 1.3) % 2 == 0) {
						t.set(x, y, rgb(62, 60, 60));
					}
				}
			}
		}
		stains(t, rgb(70, 50, 34), 2);
	}

	/** An ornate brass till, laid over its front: embossed scrolls, rows of round white keys. */
	static void cashRegister(Tex t) {
		metal(t, BRASS, BRASS_DARK);
		for (int y = 1; y < 7; y++) {
			for (int x = 1; x < 15; x++) {
				if ((int) (Math.sin(x * 0.9) * 2 + Math.cos(y * 1.3) * 2) % 2 == 0) {
					t.mix(x, y, BRASS_HI, 0.35);
				}
			}
		}
		for (int y = 9; y < 16; y += 2) {
			for (int x = 1; x < 15; x += 2) {
				t.set(x, y, rgb(232, 228, 216));
				t.set(x, y + 1 > 15 ? y : y + 1, BRASS_DARK);
			}
		}
	}

	static void bottle(Tex t, int glass) {
		t.fill(glass);
		for (int y = 0; y < 16; y++) {
			t.mix(3, y, rgb(200, 220, 200), 0.3);
		}
		t.rect(0, 6, 15, 10, rgb(214, 200, 162));
		t.rect(0, 7, 15, 7, rgb(150, 40, 40));
		t.set(6, 9, INK);
		t.set(8, 9, INK);
		grime(t, 0.6);
	}

	// --- light --------------------------------------------------------------------------------------

	/** A frosted tulip shade, fluted; lit, it glows warm, brightest at its heart. */
	static void tulip(Tex t, boolean lit) {
		int base = lit ? rgb(255, 226, 160) : rgb(206, 196, 172);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double f = x % 2 == 0 ? 1.0 : 0.93;
				if (lit) {
					f *= 1.0 - Math.abs(y - 7.5) / 40;
				}
				t.set(x, y, shade(base, f));
			}
		}
		if (lit) {
			for (int x = 0; x < 16; x++) {
				t.mix(x, 7, rgb(255, 250, 230), 0.5);
				t.mix(x, 8, rgb(255, 250, 230), 0.5);
			}
		} else {
			for (int x = 0; x < 16; x++) {
				t.mix(x, 15, rgb(110, 96, 70), 0.3);
			}
		}
		t.noise(0.015);
	}

	static void bulb(Tex t, boolean lit) {
		t.fill(lit ? rgb(255, 232, 172) : rgb(180, 176, 164));
		t.blob(6, 6, 5, lit ? rgb(255, 252, 236) : rgb(220, 218, 210), 0.7);
		t.noise(0.02);
	}

	/** Cut crystal: pale, faceted, a glint here and there. */
	static void crystal(Tex t) {
		t.fill(rgb(210, 218, 224));
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				t.shade(x, y, Math.floorMod(x + y, 4) < 2 ? 1.06 : 0.9);
			}
		}
		for (int k = 0; k < 6; k++) {
			t.set(t.r.nextInt(16), t.r.nextInt(16), rgb(255, 255, 255));
		}
	}

	/** Amber glass panes in a brass frame; lit, the amber glows. */
	static void lanternGlass(Tex t, boolean lit) {
		t.fill(lit ? rgb(255, 196, 110) : rgb(150, 110, 60));
		t.blob(8, 8, 7, lit ? rgb(255, 236, 180) : rgb(176, 136, 80), 0.6);
		for (int i = 0; i < 16; i++) {
			t.set(i, 0, BRASS);
			t.set(0, i, BRASS);
			t.set(i, 15, BRASS_DARK);
			t.set(15, i, BRASS_DARK);
			t.set(7, i, BRASS);
		}
	}

	/** The banker's lamp's green glass: dark emerald with a light band; lit, it glows green. */
	static void greenShade(Tex t, boolean lit) {
		int base = lit ? rgb(110, 210, 120) : rgb(24, 84, 46);
		t.fill(base);
		for (int x = 0; x < 16; x++) {
			for (int y = 0; y < 16; y++) {
				t.shade(x, y, 0.85 + 0.25 * Math.sin(y * Math.PI / 16));
			}
			t.mix(x, 4, lit ? rgb(220, 255, 200) : rgb(110, 170, 120), 0.45);
		}
		if (lit) {
			for (int x = 0; x < 16; x++) {
				t.mix(x, 15, rgb(255, 250, 210), 0.6);
				t.mix(x, 14, rgb(255, 250, 210), 0.3);
			}
		}
	}

	/** Pleated cream silk; lit, warm and bright through it. */
	static void silk(Tex t, boolean lit) {
		int base = lit ? rgb(255, 220, 160) : rgb(212, 196, 160);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				t.set(x, y, shade(base, x % 2 == 0 ? 1.0 : 0.9));
			}
		}
		if (lit) {
			t.blob(8, 8, 8, rgb(255, 244, 214), 0.5);
		} else {
			grime(t, 0.4);
		}
	}

	/** An oil lamp's glass chimney; lit, the flame's glow fills it. */
	static void chimney(Tex t, boolean lit) {
		t.fill(lit ? rgb(255, 200, 120) : rgb(196, 200, 196));
		if (lit) {
			disc(t, 8, 9, 4, rgb(255, 236, 170));
			disc(t, 8, 10, 2, rgb(255, 255, 230));
		} else {
			for (int y = 0; y < 16; y++) {
				t.set(4, y, rgb(240, 244, 240));
			}
			for (int x = 0; x < 16; x++) {
				t.mix(x, 1, rgb(40, 36, 34), 0.4);
			}
		}
	}

	/** Old candle wax, ivory, run down in drips. */
	static void wax(Tex t) {
		t.fill(rgb(230, 220, 192));
		for (int k = 0; k < 5; k++) {
			int x = t.r.nextInt(16), len = 3 + t.r.nextInt(8), y0 = t.r.nextInt(6);
			for (int y = y0; y < y0 + len; y++) {
				t.mix(x, y, rgb(214, 196, 150), 0.5);
			}
		}
		t.noise(0.03);
	}

	/** Split logs in the grate; lit, glowing cracks of ember. */
	static void log(Tex t, boolean lit) {
		t.fill(rgb(60, 44, 30));
		t.grain(0, 0, 15, 15, true, 0.15);
		for (int y = 0; y < 16; y += 5) {
			for (int x = 0; x < 16; x++) {
				t.set(x, y, rgb(30, 22, 16));
			}
		}
		if (lit) {
			for (int k = 0; k < 14; k++) {
				int x = t.r.nextInt(16), y = t.r.nextInt(16);
				t.set(x, y, rgb(255, 150, 50));
				t.mix(x + 1, y, rgb(230, 90, 30), 0.7);
			}
			for (int y = 0; y < 16; y += 5) {
				for (int x = 0; x < 16; x++) {
					t.mix(x, y, rgb(255, 120, 40), 0.6);
				}
			}
		}
	}

	/** Flames, eight frames of them licking up, clear between: drawn on crossed planes in the grate. */
	static void fire(Tex t) {
		for (int frame = 0; frame < 8; frame++) {
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					double tongue = 0;
					for (int k = 0; k < 3; k++) {
						double cx = 3 + k * 5 + Math.sin(frame * 0.8 + k * 2.1) * 1.2;
						double height = 9 + 5 * (0.5 + 0.5 * Math.sin(frame * 0.9 + k * 1.7));
						double up = (15 - y) / height;
						double width = 2.6 * (1 - up);
						if (up < 1 && Math.abs(x - cx) < width) {
							tongue = Math.max(tongue, 1 - up);
						}
					}
					int py = frame * 16 + y;
					if (tongue <= 0.02) {
						t.clear(x, py);
					} else {
						int c = tongue > 0.7 ? rgb(255, 240, 170) : tongue > 0.4 ? rgb(255, 180, 60) : rgb(230, 90, 30);
						t.set(x, py, c);
					}
				}
			}
		}
	}

	/** Gilt: carved beads in gold leaf, rubbed through to the red ground in places. */
	static void gilt(Tex t) {
		t.fill(GILT);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int bx = Math.floorMod(x, 4), by = Math.floorMod(y, 4);
				if (bx == 0 && by == 0) {
					t.set(x, y, GILT_HI);
				} else if (bx == 2 || by == 2) {
					t.set(x, y, mix(GILT, GILT_DARK, 0.45));
				}
			}
		}
		for (int k = 0; k < 4; k++) {
			t.mix(t.r.nextInt(16), t.r.nextInt(16), rgb(120, 52, 36), 0.6);
		}
		t.noise(0.05);
	}

	// --- pictures and papers -------------------------------------------------------------------------

	/** A gilt frame drawn round the edge of a picture. */
	static void frame(Tex t) {
		for (int y = 0; y < t.h; y++) {
			for (int x = 0; x < t.w; x++) {
				int edge = Math.min(Math.min(x, t.w - 1 - x), Math.min(y, t.h - 1 - y));
				if (edge == 0) {
					t.set(x, y, x == 0 || y == 0 ? GILT_HI : GILT_DARK);
				} else if (edge == 1) {
					t.set(x, y, Math.floorMod(x + y, 3) == 0 ? GILT_HI : GILT);
				} else if (edge == 2) {
					t.mix(x, y, GILT_DARK, 0.55);
				}
			}
		}
	}

	/** Old varnish over a painting: yellowed, crazed. */
	static void varnish(Tex t) {
		for (int y = 0; y < t.h; y++) {
			for (int x = 0; x < t.w; x++) {
				t.mix(x, y, rgb(60, 46, 18), 0.14);
			}
		}
		for (int k = 0; k < 5; k++) {
			t.walk(2 + t.r.nextInt(t.w - 4), 2 + t.r.nextInt(t.h - 4), 5, rgb(16, 12, 8), 0.35, t.r.nextBoolean() ? 1 : -1, 1);
		}
		t.noise(0.03);
	}

	/**
	 * A portrait in oils, 16 by 32, in its gilt frame, gone dark under old varnish: Wennberg
	 * himself (0), a lady in red (1), a young officer (2), and a girl in white whose eyes someone
	 * has scratched out (3).
	 */
	static void portrait(Tex t, int look) {
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 16; x++) {
				double d = Math.hypot((x - 7.5) / 8, (y - 12) / 16);
				t.set(x, y, mix(rgb(70, 52, 34), rgb(22, 16, 12), Math.min(1, d)));
			}
		}
		int skin = rgb(200, 164, 132), skinDark = rgb(146, 108, 84), black = rgb(26, 20, 18);
		int coat = switch (look) {
			case 1 -> rgb(122, 24, 32);
			case 2 -> rgb(44, 50, 62);
			case 3 -> rgb(214, 208, 192);
			default -> rgb(30, 26, 26);
		};
		for (int y = 15; y < 30; y++) {
			int half = Math.min(6, 2 + (y - 15) / 2 + (y > 17 ? 1 : 0));
			for (int x = 8 - half; x < 8 + half; x++) {
				t.set(x, y, shade(coat, x > 8 ? 0.82 : 1));
			}
		}
		int[][] face = {{6, 9}, {5, 10}, {5, 10}, {5, 10}, {5, 10}, {5, 10}, {6, 9}};
		for (int i = 0; i < face.length; i++) {
			for (int x = face[i][0]; x <= face[i][1]; x++) {
				t.set(x, 7 + i, x >= 9 ? skinDark : skin);
			}
		}
		t.set(7, 14, skinDark);
		t.set(8, 14, skinDark);
		t.set(6, 9, black);
		t.set(9, 9, black);
		t.set(8, 10, skinDark);
		t.set(8, 11, skinDark);
		t.set(7, 12, rgb(110, 60, 50));
		t.set(8, 12, rgb(110, 60, 50));
		switch (look) {
			case 0 -> {
				t.mix(7, 7, rgb(240, 214, 190), 0.6);
				for (int y = 9; y <= 13; y++) {
					t.set(5, y, rgb(150, 146, 138));
					t.set(10, y, rgb(120, 116, 110));
				}
				t.set(6, 8, rgb(150, 146, 138));
				t.set(9, 8, rgb(150, 146, 138));
				for (int x = 6; x <= 9; x++) {
					t.set(x, 15, rgb(220, 214, 200));
				}
				t.set(7, 16, rgb(110, 24, 28));
				t.set(8, 16, rgb(110, 24, 28));
				for (int x = 5; x <= 10; x++) {
					t.set(x, 23 + Math.abs(x - 7) / 2, BRASS_HI);
				}
			}
			case 1 -> {
				for (int y = 3; y <= 8; y++) {
					for (int x = 4; x <= 11; x++) {
						if (Math.hypot(x - 7.5, y - 6.5) < 3.8 && (y < 8 || x < 6 || x > 9)) {
							t.set(x, y, rgb(48, 30, 22));
						}
					}
				}
				t.set(4, 9, rgb(48, 30, 22));
				t.set(11, 9, rgb(48, 30, 22));
				for (int y = 15; y <= 17; y++) {
					for (int x = 4; x <= 11; x++) {
						t.set(x, y, x >= 9 ? skinDark : skin);
					}
				}
				for (int x = 5; x <= 10; x++) {
					t.set(x, 16 + (x == 5 || x == 10 ? 0 : 1), rgb(236, 232, 220));
				}
				t.set(7, 12, rgb(150, 40, 44));
				t.set(8, 12, rgb(150, 40, 44));
			}
			case 2 -> {
				for (int x = 5; x <= 10; x++) {
					t.set(x, 7, rgb(56, 40, 28));
					t.set(x, 6, rgb(56, 40, 28));
				}
				t.set(5, 8, rgb(56, 40, 28));
				t.set(7, 11, rgb(56, 40, 28));
				t.set(8, 11, rgb(56, 40, 28));
				t.set(9, 11, rgb(56, 40, 28));
				for (int x = 6; x <= 9; x++) {
					t.set(x, 15, rgb(200, 190, 160));
				}
				t.set(5, 19, BRASS_HI);
				t.set(6, 19, rgb(160, 30, 30));
				t.set(5, 20, BRASS);
				for (int x = 3; x <= 12; x++) {
					t.set(x, 17, BRASS_DARK);
				}
			}
			default -> {
				for (int y = 5; y <= 18; y++) {
					for (int x = 3; x <= 12; x++) {
						boolean hair = y <= 7 && Math.hypot(x - 7.5, y - 8) < 4.2
								|| y > 7 && (x == 4 || x == 11 || x == 3 && y > 12 || x == 12 && y > 12);
						if (hair) {
							t.set(x, y, rgb(120, 92, 54));
						}
					}
				}
				t.set(10, 4, rgb(236, 232, 224));
				t.set(11, 5, rgb(236, 232, 224));
				t.set(9, 5, rgb(236, 232, 224));
				for (int x = 4; x <= 11; x++) {
					t.mix(x, 9, rgb(214, 200, 176), 0.8);
					t.mix(x + (x % 2), 8 + (x % 3) / 2, rgb(190, 176, 150), 0.5);
				}
				t.set(6, 9, rgb(240, 230, 210));
				t.set(9, 9, rgb(240, 230, 210));
			}
		}
		varnish(t);
		frame(t);
	}

	/** The bay at dusk, 32 by 16: a grey-gold sky, dark water, the pale sea stacks the hotel is named for. */
	static void seascape(Tex t) {
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 32; x++) {
				int c = y < 9 ? mix(rgb(120, 110, 96), rgb(200, 150, 90), y / 9.0) : mix(rgb(40, 56, 66), rgb(22, 30, 38), (y - 9) / 7.0);
				t.set(x, y, c);
			}
		}
		for (int x = 0; x < 32; x++) {
			t.mix(x, 9, rgb(220, 180, 110), 0.5);
		}
		int[][] stacks = {{7, 4, 2}, {11, 6, 1}, {21, 3, 3}, {26, 6, 1}};
		for (int[] s : stacks) {
			for (int y = s[1]; y < 11; y++) {
				for (int x = s[0] - s[2]; x <= s[0] + s[2] - (y == s[1] ? 1 : 0); x++) {
					t.set(x, y, x > s[0] ? rgb(150, 144, 130) : rgb(196, 190, 172));
				}
			}
		}
		for (int k = 0; k < 6; k++) {
			t.set(3 + t.r.nextInt(26), 11 + t.r.nextInt(4), rgb(90, 104, 110));
		}
		varnish(t);
		frame(t);
	}

	/** A mirror, 16 by 32, gone dim: the silvering black at the edges, foxed, a crack. */
	static void mirror(Tex t) {
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 16; x++) {
				t.set(x, y, mix(rgb(150, 160, 162), rgb(70, 76, 80), y / 40.0 + Math.abs(x - 7.5) / 30));
			}
		}
		for (int x = 0; x < 16; x++) {
			t.mix(x, 6 + x / 2, rgb(220, 226, 226), 0.3);
		}
		for (int k = 0; k < 10; k++) {
			int x = t.r.nextBoolean() ? 3 + t.r.nextInt(2) : 11 + t.r.nextInt(2);
			t.blob(x, t.r.nextInt(32), 1.5, rgb(30, 30, 30), 0.6);
		}
		for (int k = 0; k < 8; k++) {
			t.set(3 + t.r.nextInt(10), 3 + t.r.nextInt(26), rgb(110, 96, 70));
		}
		line(t, 4, 20, 12, 27, rgb(230, 236, 236), 0.6);
		frame(t);
	}

	/** A clock face: white enamel, its hours marked, stopped at a quarter to twelve. */
	static void clockFace(Tex t) {
		t.fill(MAH_MID);
		disc(t, 8, 8, 7.6, BRASS);
		disc(t, 8, 8, 6.6, rgb(226, 220, 204));
		for (int h = 0; h < 12; h++) {
			double a = h * Math.PI / 6;
			t.set((int) Math.round(7.5 + Math.sin(a) * 5.4), (int) Math.round(7.5 - Math.cos(a) * 5.4), INK);
		}
		line(t, 8, 8, 8, 4, INK, 1);
		line(t, 8, 8, 3, 7, INK, 1);
		t.set(8, 8, BRASS_DARK);
		grime(t, 0.5);
	}

	/** The study clock's face (puzzle 5): no hands painted on, its renderer draws them where they're set. */
	static void clockFaceBare(Tex t) {
		t.fill(MAH_MID);
		disc(t, 8, 8, 7.6, BRASS);
		disc(t, 8, 8, 6.6, rgb(226, 220, 204));
		for (int h = 0; h < 12; h++) {
			double a = h * Math.PI / 6;
			t.set((int) Math.round(7.5 + Math.sin(a) * 5.4), (int) Math.round(7.5 - Math.cos(a) * 5.4), INK);
		}
		grime(t, 0.5);
	}

	static void cork(Tex t) {
		t.fill(rgb(160, 120, 76));
		t.noise(0.12);
		for (int i = 0; i < 16; i++) {
			t.set(i, 0, MAH_DARK);
			t.set(0, i, MAH_DARK);
			t.set(i, 15, MAH_DARK);
			t.set(15, i, MAH_DARK);
		}
		int[][] notes = {{2, 2, 7, 8}, {9, 3, 13, 7}, {4, 9, 11, 13}};
		for (int[] n : notes) {
			t.rect(n[0], n[1], n[2], n[3], PAPER);
			for (int y = n[1] + 1; y < n[3]; y += 2) {
				t.rect(n[0] + 1, y, n[2] - 1 - t.r.nextInt(2), y, rgb(110, 100, 90));
			}
			t.set((n[0] + n[2]) / 2, n[1], rgb(170, 40, 40));
		}
	}

	/** A sheet of paper: a letter (0), a ledger's left and right pages (1, 2), sheet music (3). */
	static void paper(Tex t, int kind) {
		t.fill(PAPER);
		t.noise(0.03);
		switch (kind) {
			case 1, 2 -> {
				for (int y = 1; y < 16; y += 2) {
					for (int x = 0; x < 16; x++) {
						t.mix(x, y, rgb(150, 160, 190), 0.35);
					}
					int len = 4 + t.r.nextInt(8);
					t.rect(kind == 1 ? 2 : 1, y - 1, Math.min(14, (kind == 1 ? 2 : 1) + len), y - 1, INK);
				}
				for (int y = 0; y < 16; y++) {
					t.mix(kind == 1 ? 1 : 12, y, rgb(190, 60, 60), 0.4);
					t.mix(kind == 1 ? 15 : 0, y, rgb(120, 100, 70), 0.4);
				}
			}
			case 3 -> {
				for (int s = 0; s < 3; s++) {
					for (int l = 0; l < 4; l++) {
						t.rect(1, 2 + s * 5 + l, 14, 2 + s * 5 + l, rgb(120, 110, 100));
					}
					for (int x = 2; x < 14; x += 3) {
						t.set(x, 2 + s * 5 + t.r.nextInt(4), INK);
					}
				}
			}
			default -> {
				for (int y = 2; y < 15; y += 2) {
					int len = 6 + t.r.nextInt(7);
					for (int x = 2; x < 2 + len; x++) {
						if (t.r.nextInt(5) > 0) {
							t.set(x, y, INK);
						}
					}
				}
			}
		}
		t.blob(t.r.nextInt(16), t.r.nextInt(16), 3, rgb(170, 140, 90), 0.3);
	}

	static void bookSpines(Tex t) {
		int[] colors = {rgb(110, 28, 30), rgb(36, 60, 40), rgb(40, 40, 70), rgb(96, 70, 40), rgb(70, 30, 50)};
		for (int x = 0; x < 16; x++) {
			int c = colors[(x / 3 + x / 7) % colors.length];
			for (int y = 0; y < 16; y++) {
				t.set(x, y, shade(c, x % 3 == 0 ? 0.75 : 1));
			}
		}
		for (int x = 0; x < 16; x++) {
			t.set(x, 3, GILT);
			t.set(x, 12, GILT);
		}
		grime(t, 0.4);
	}

	static void globe(Tex t) {
		t.fill(rgb(150, 136, 98));
		for (int k = 0; k < 5; k++) {
			t.blob(t.r.nextInt(16), t.r.nextInt(16), 3, rgb(196, 170, 120), 0.8);
		}
		for (int y = 0; y < 16; y += 4) {
			for (int x = 0; x < 16; x++) {
				t.mix(x, y, rgb(90, 70, 40), 0.3);
			}
		}
		grime(t, 0.5);
	}

	/** Dried flowers, drooping: stems and heads, clear between (drawn on crossed planes). */
	static void deadFlowers(Tex t) {
		for (int i = 0; i < 256; i++) {
			t.clear(i % 16, i / 16);
		}
		for (int s = 0; s < 4; s++) {
			int x = 3 + s * 3, top = 2 + t.r.nextInt(5);
			for (int y = top; y < 16; y++) {
				t.set(x + (y < top + 3 ? (s % 2 == 0 ? -1 : 1) : 0), y, rgb(88, 78, 46));
			}
			int hx = x + (s % 2 == 0 ? -2 : 2);
			t.set(hx, top + 1, rgb(110, 56, 40));
			t.set(hx, top + 2, rgb(130, 70, 48));
			t.set(hx + 1, top + 2, rgb(96, 46, 34));
			t.set(hx, top + 3, rgb(110, 56, 40));
		}
	}

	/** A dead palm frond: a midrib down it and leaflets hanging from it, brown, clear between. */
	static void palmFrond(Tex t) {
		for (int i = 0; i < 256; i++) {
			t.clear(i % 16, i / 16);
		}
		for (int y = 0; y < 16; y++) {
			int x = 8 + (int) Math.round((y - 8) * 0.15);
			t.set(x, y, rgb(100, 82, 50));
			if (y % 2 == 0) {
				for (int k = 1; k < 7 - Math.abs(y - 7) / 3; k++) {
					int c = mix(rgb(120, 100, 54), rgb(88, 72, 40), t.r.nextDouble());
					t.set(x - k, y + k / 2, c);
					t.set(x + k, y + k / 2, c);
				}
			}
		}
	}

	/** Black lacquer with long soft reflections, and dust. */
	static void lacquer(Tex t) {
		t.fill(rgb(14, 12, 14));
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				t.mix(x, y, rgb(90, 86, 84), 0.15 * (0.5 + 0.5 * Math.sin(x * 0.4 + y * 0.1)));
			}
		}
		grime(t, 0.3);
	}

	/** A keyboard, 32 by 16: white keys, the black ones in twos and threes over their far ends. */
	static void pianoKeys(Tex t) {
		for (int x = 0; x < 32; x++) {
			for (int y = 0; y < 16; y++) {
				t.set(x, y, x % 2 == 1 ? rgb(150, 144, 130) : rgb(226, 218, 196));
			}
		}
		int[] black = {1, 3, 7, 9, 11};
		for (int o = 0; o < 32; o += 14) {
			for (int b : black) {
				int x = o + b;
				if (x < 32) {
					for (int y = 0; y < 10; y++) {
						t.set(x, y, rgb(20, 18, 18));
					}
				}
			}
		}
		grime(t, 0.4);
	}

	// --- doors --------------------------------------------------------------------------------------

	/** The frame of a door: stiles down its sides, rails across, lit along the top and left. */
	static void doorFrame(Tex t, int dark, int light) {
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 16; x++) {
				if (x < 2 || x > 13 || y < 2 || y > 29 || y == 15 || y == 16) {
					t.shade(x, y, 0.92);
				}
			}
		}
		for (int i = 0; i < 32; i++) {
			t.mix(0, i, light, 0.25);
			t.mix(15, i, dark, 0.45);
		}
		for (int i = 0; i < 16; i++) {
			t.mix(i, 0, light, 0.3);
			t.mix(i, 31, dark, 0.5);
		}
	}

	/** A guest room's door: four raised mahogany panels; on the corridor side, its brass number. */
	static void hotelDoor(Tex t, boolean front) {
		wood(t, MAH_MID, MAH_DARK, MAH_HI);
		doorFrame(t, MAH_DARK, MAH_HI);
		raised(t, 2, 3, 7, 14, MAH_DARK, MAH_HI);
		raised(t, 8, 3, 13, 14, MAH_DARK, MAH_HI);
		raised(t, 2, 17, 7, 29, MAH_DARK, MAH_HI);
		raised(t, 8, 17, 13, 29, MAH_DARK, MAH_HI);
		if (front) {
			t.rect(6, 1, 9, 2, BRASS);
			t.set(6, 1, BRASS_HI);
			t.set(7, 2, INK);
			t.set(8, 2, INK);
		}
		grime(t, 0.25);
	}

	/** A staff door: painted dark green over pine, chipped, grimy at the foot, two plain panels. */
	static void serviceDoor(Tex t) {
		t.fill(rgb(58, 74, 60));
		t.noise(0.05);
		doorFrame(t, rgb(30, 40, 32), rgb(96, 112, 96));
		raised(t, 3, 3, 12, 14, rgb(30, 40, 32), rgb(96, 112, 96));
		raised(t, 3, 17, 12, 28, rgb(30, 40, 32), rgb(96, 112, 96));
		for (int k = 0; k < 9; k++) {
			int x = t.r.nextInt(16), y = t.r.nextInt(32);
			t.set(x, y, PINE);
			if (t.r.nextBoolean()) {
				t.set(x + 1, y, shade(PINE, 0.85));
			}
		}
		for (int y = 22; y < 32; y++) {
			for (int x = 0; x < 16; x++) {
				t.mix(x, y, GRIME, (y - 22) / 18.0);
			}
		}
	}

	/** The spa's doors: a mahogany frame, frosted glass in the top with an etched border, a panel below. */
	static void glassDoor(Tex t) {
		wood(t, MAH_MID, MAH_DARK, MAH_HI);
		doorFrame(t, MAH_DARK, MAH_HI);
		for (int y = 3; y <= 14; y++) {
			for (int x = 3; x <= 12; x++) {
				boolean etch = x == 4 || x == 11 || y == 4 || y == 13;
				t.set(x, y, shade(rgb(196, 210, 206), etch ? 1.08 : 0.95 + t.r.nextDouble() * 0.05));
			}
		}
		for (int i = 3; i <= 12; i++) {
			t.set(i, 3, MAH_DARK);
			t.set(i, 14, MAH_HI);
		}
		raised(t, 3, 18, 12, 28, MAH_DARK, MAH_HI);
		grime(t, 0.3);
	}

	/** A lift's gate, 16 by 32: a brass frame round a lattice of diagonal bars, clear between them. */
	static void liftGate(Tex t) {
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 16; x++) {
				boolean frame = x == 0 || x == 15 || y == 0 || y == 31 || y == 15 || y == 16;
				boolean bar = Math.floorMod(x + y, 6) == 0 || Math.floorMod(x - y, 6) == 0;
				if (frame) {
					t.set(x, y, x == 0 || y == 0 || y == 15 ? BRASS_HI : BRASS_DARK);
				} else if (bar) {
					t.set(x, y, Math.floorMod(x + y, 6) == 0 && Math.floorMod(x - y, 6) == 0 ? BRASS_HI : BRASS);
				} else {
					t.clear(x, y);
				}
			}
		}
	}

	/** The lever's dial: the floors marked round a half circle in black on cream, a brass needle. */
	static void liftDial(Tex t) {
		t.fill(BRASS_DARK);
		for (int y = 2; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double d = Math.hypot(x - 7.5, y - 15.5);
				if (d < 7.5) {
					t.set(x, y, rgb(226, 216, 190));
				}
				if (d >= 6.2 && d < 7.2 && Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(15.5 - y, x - 7.5))), 30) < 8) {
					t.set(x, y, INK);
				}
			}
		}
		line(t, 8, 15, 4, 9, BRASS_HI, 1);
		grime(t, 0.4);
	}

	/** Iron tread plate: raised diamonds in rows, worn bright where feet go. */
	static void treadPlate(Tex t) {
		t.fill(rgb(70, 70, 72));
		t.noise(0.06);
		for (int y = 0; y < 16; y += 4) {
			for (int x = (y / 4) % 2 * 2; x < 16; x += 4) {
				t.set(x, y, rgb(120, 120, 122));
				t.set(x + 1, y + 1, rgb(120, 120, 122));
				t.set(x + 1, y, rgb(40, 40, 42));
			}
		}
		t.blob(8, 8, 6, rgb(110, 108, 104), 0.3);
		grime(t, 0.4);
	}

	// --- fittings ------------------------------------------------------------------------------------

	/**
	 * Shelves seen from the front, three rows of them in a frame: books (0), jars and tins (1),
	 * folded linen (2), or bottles before a mirror (3). Worn: dusty, gaps, a cobweb in a corner.
	 */
	static void shelves(Tex t, int kind, boolean worn) {
		boolean pine = kind == 1 || kind == 2;
		int wood = pine ? PINE : MAH_MID, dark = pine ? rgb(110, 84, 54) : MAH_DARK;
		t.fill(kind == 3 ? rgb(120, 128, 130) : shade(dark, 0.45));
		if (kind == 3) {
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					t.mix(x, y, rgb(60, 64, 66), (x + y) / 40.0);
				}
			}
		}
		int[] colors = {rgb(110, 28, 30), rgb(36, 60, 40), rgb(40, 40, 70), rgb(96, 70, 40), rgb(70, 30, 50), rgb(120, 96, 50)};
		for (int row = 0; row < 3; row++) {
			int floor = row * 5 + 4;
			int x = 1;
			while (x < 15) {
				int w = 1 + t.r.nextInt(2), h = 2 + t.r.nextInt(2);
				if (worn && t.r.nextInt(4) == 0) {
					x += 2;
					continue;
				}
				for (int i = 0; i < w && x + i < 15; i++) {
					for (int y = floor - h; y < floor; y++) {
						int c = switch (kind) {
							case 1 -> i == 0 && y == floor - h ? rgb(180, 170, 150) : t.r.nextBoolean() ? rgb(150, 110, 60) : rgb(120, 130, 120);
							case 2 -> (y - floor) % 2 == 0 ? rgb(214, 208, 194) : rgb(186, 180, 166);
							case 3 -> y < floor - 2 ? rgb(40, 70, 40) : colors[(x + row) % 3 == 0 ? 3 : 1];
							default -> shade(colors[(x * 7 + row * 3) % colors.length], i == 0 ? 0.8 : 1);
						};
						t.set(x + i, y, c);
					}
					if (kind == 0) {
						t.set(x + i, floor - h + 1, GILT_DARK);
					}
				}
				x += w + (kind == 0 ? 0 : 1);
			}
			for (int xx = 0; xx < 16; xx++) {
				t.set(xx, floor, kind == 3 ? BRASS : wood);
			}
		}
		for (int y = 0; y < 16; y++) {
			t.set(0, y, wood);
			t.set(15, y, shade(wood, 0.8));
		}
		for (int x = 0; x < 16; x++) {
			t.set(x, 15, shade(wood, 0.75));
		}
		if (worn) {
			grime(t, 0.7);
			line(t, 1, 0, 6, 5, rgb(214, 214, 210), 0.4);
			line(t, 1, 3, 4, 0, rgb(214, 214, 210), 0.3);
		}
	}

	/** Behind the front desk: a grid of pigeonholes, a key hanging below some, letters in others. */
	static void pigeonholes(Tex t) {
		wood(t, MAH_MID, MAH_DARK, MAH_HI);
		for (int cy = 0; cy < 4; cy++) {
			for (int cx = 0; cx < 4; cx++) {
				int x0 = cx * 4, y0 = cy * 4;
				t.rect(x0 + 1, y0 + 1, x0 + 3, y0 + 3, rgb(26, 16, 10));
				int what = (cx * 3 + cy * 5) % 4;
				if (what == 0) {
					t.set(x0 + 2, y0 + 2, BRASS_HI);
					t.set(x0 + 2, y0 + 3, BRASS);
				} else if (what == 1) {
					t.rect(x0 + 1, y0 + 3, x0 + 3, y0 + 3, PAPER);
				}
			}
		}
	}

	static void wineRack(Tex t) {
		t.fill(rgb(20, 16, 12));
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (Math.floorMod(x - y, 8) == 0 || Math.floorMod(x + y, 8) == 0) {
					t.set(x, y, OAK);
				}
			}
		}
		for (int[] c : new int[][] {{4, 0}, {0, 4}, {8, 4}, {4, 8}, {12, 8}, {8, 12}, {0, 12}, {12, 0}}) {
			if (t.r.nextInt(4) > 0) {
				disc(t, c[0] + 0.5, c[1] + 0.5, 1.5, rgb(26, 44, 26));
				t.set(c[0], c[1], rgb(120, 30, 30));
			}
		}
		grime(t, 0.6);
	}

	static void crate(Tex t) {
		t.fill(PINE);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (y % 5 == 4) {
					t.set(x, y, rgb(96, 72, 46));
				}
				if (x < 2 || x > 13) {
					t.set(x, y, shade(PINE, 0.85));
				}
			}
		}
		t.grain(2, 0, 13, 15, true, 0.12);
		for (int y : new int[] {1, 6, 11}) {
			t.set(1, y, IRON_BLACK);
			t.set(14, y, IRON_BLACK);
		}
		if (t.look == 1) {
			for (int x = 5; x < 11; x++) {
				t.set(x, 7, rgb(60, 50, 40));
			}
			t.set(5, 6, rgb(60, 50, 40));
			t.set(10, 8, rgb(60, 50, 40));
		}
		grime(t, 0.8);
	}

	// --- wreckage and rugs --------------------------------------------------------------------------

	static void rubble(Tex t) {
		t.fill(rgb(186, 176, 156));
		for (int k = 0; k < 8; k++) {
			t.blob(t.r.nextInt(16), t.r.nextInt(16), 2.2, rgb(140, 130, 112), 0.5);
		}
		for (int k = 0; k < 4; k++) {
			int x = t.r.nextInt(14), y = t.r.nextInt(16);
			t.rect(x, y, x + 2, y, rgb(120, 90, 58));
		}
		t.noise(0.08);
	}

	/** Broken glass on the floor: pale shards scattered, clear between. */
	static void shards(Tex t) {
		for (int i = 0; i < 256; i++) {
			t.clear(i % 16, i / 16);
		}
		for (int k = 0; k < 9; k++) {
			int x = t.r.nextInt(16), y = t.r.nextInt(16), len = 1 + t.r.nextInt(3);
			for (int i = 0; i < len; i++) {
				t.set(x + i, y + (i == len - 1 ? 1 : 0), i == 0 ? rgb(236, 244, 246) : rgb(170, 186, 190));
			}
		}
	}

	/** A Persian rug's field: a lattice of small medallions on the ground colour. Worn: faded, bare patches. */
	static void rugField(Tex t, int ground, int accent, int gold, boolean worn) {
		// A dark lattice of diamonds, a small medallion in each, all low in contrast: an old wool pile.
		int dim = mix(gold, ground, 0.55), deep = shade(ground, 0.78);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int d = Math.abs(Math.floorMod(x, 8) - 4) + Math.abs(Math.floorMod(y, 8) - 4);
				int c = d == 4 ? mix(accent, ground, 0.35) : d == 1 ? dim : d == 0 ? mix(accent, ground, 0.2) : d == 2 ? deep : ground;
				t.set(x, y, c);
			}
		}
		t.noise(0.07);
		if (worn) {
			for (int i = 0; i < 256; i++) {
				t.mix(i % 16, i / 16, rgb(150, 130, 110), 0.3);
			}
			for (int k = 0; k < 3; k++) {
				t.blob(t.r.nextInt(16), t.r.nextInt(16), 3, rgb(176, 160, 130), 0.5);
			}
			grime(t, 0.8);
		}
	}

	/** The same rug's border, round all four edges of the tile so each strip of it takes its own edge. */
	static void rugBorder(Tex t, int ground, int accent, int gold, boolean worn) {
		rugField(t, ground, accent, gold, worn);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int edge = Math.min(Math.min(x, 15 - x), Math.min(y, 15 - y));
				if (edge == 0) {
					t.set(x, y, shade(accent, 0.7));
				} else if (edge == 1) {
					int along = (x == 1 || x == 14) ? y : x;
					t.set(x, y, along % 3 == 0 ? gold : accent);
				} else if (edge == 2) {
					t.set(x, y, gold);
				}
			}
		}
		if (worn) {
			grime(t, 0.6);
		}
	}
}

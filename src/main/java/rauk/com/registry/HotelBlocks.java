package rauk.com.registry;

import java.util.LinkedHashMap;
import java.util.Map;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import rauk.com.Rauk;
import rauk.com.block.hotel.FurnitureBlock;
import rauk.com.block.hotel.HotelDoorBlock;
import rauk.com.block.hotel.HotelDoorEntity;
import rauk.com.block.hotel.ClockBlock;
import rauk.com.block.hotel.ClockEntity;
import rauk.com.block.hotel.SafeBlock;
import rauk.com.block.hotel.SafeEntity;
import rauk.com.block.hotel.HotelSwitch;
import rauk.com.block.hotel.LiftControlBlock;
import rauk.com.block.hotel.HotelShapes;
import rauk.com.hotel.HotelFurniture;
import rauk.com.hotel.HotelFurniture.Piece;
import rauk.com.hotel.HotelMaterials;
import rauk.com.hotel.HotelMaterials.Feel;
import rauk.com.hotel.HotelMaterials.Material;

/**
 * Registers the hotel's own blocks (hotel design 11.3) from {@link HotelMaterials}, and its
 * furniture (design 12) from {@link HotelFurniture}, each with a block item, in creative tabs of
 * their own.
 */
public final class HotelBlocks {
	public static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
	public static final Map<String, FurnitureBlock> FURNITURE = new LinkedHashMap<>();
	public static HotelSwitch lightSwitch;
	/** The doors (hotel design 4b): the guest rooms' mahogany, the staff's painted pine, the spa's glass. */
	public static final Map<String, HotelDoorBlock> DOORS = new LinkedHashMap<>();
	public static final String[][] DOOR_TYPES = {{"hotel_door", "Mahogany Door"}, {"service_door", "Service Door"},
			{"glass_door", "Glazed Door"}, {"lift_door", "Lift Gate"}, {"bookcase_door", "Bookcase"}};
	/** The lifts' operator levers and call buttons (hotel design 3.2). */
	public static LiftControlBlock liftControl, liftButton;

	private HotelBlocks() {
	}

	public static Block get(String id) {
		Block b = BLOCKS.get(id);
		if (b == null) {
			throw new IllegalArgumentException("No hotel block " + id);
		}
		return b;
	}

	public static void init() {
		for (Material m : HotelMaterials.ALL) {
			Block block = Registry.register(BuiltInRegistries.BLOCK, Rauk.id(m.id()), create(m));
			BLOCKS.put(m.id(), block);
			Registry.register(BuiltInRegistries.ITEM, Rauk.id(m.id()), new BlockItem(block, new Item.Properties()));
		}
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Rauk.id("hotel"), FabricItemGroup.builder()
				.title(Component.translatable("itemGroup.rauk.hotel"))
				.icon(() -> new ItemStack(BLOCKS.get("mahogany_wainscot")))
				.displayItems((params, out) -> BLOCKS.values().forEach(out::accept))
				.build());
		for (Piece piece : HotelFurniture.all()) {
			if (piece.kind.equals("lift_control") || piece.kind.equals("lift_button")) {
				LiftControlBlock b = Registry.register(BuiltInRegistries.BLOCK, Rauk.id(piece.id),
						new LiftControlBlock(piece.kind.equals("lift_control"), properties(Feel.METAL).noOcclusion().noCollission().strength(0.5f)));
				Registry.register(BuiltInRegistries.ITEM, Rauk.id(piece.id), new BlockItem(b, new Item.Properties()));
				if (piece.kind.equals("lift_control")) {
					liftControl = b;
				} else {
					liftButton = b;
				}
				continue;
			}
			if (piece.kind.equals("clock")) {
				ClockBlock clock = Registry.register(BuiltInRegistries.BLOCK, Rauk.id(piece.id),
						ClockBlock.create(piece, properties(piece.feel).noOcclusion().strength(-1, 3600000)));
				FURNITURE.put(piece.id, clock);
				Registry.register(BuiltInRegistries.ITEM, Rauk.id(piece.id), new BlockItem(clock, new Item.Properties()));
				ClockBlock.entityType = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Rauk.id(piece.id),
						BlockEntityType.Builder.of(ClockEntity::new, clock).build(null));
				continue;
			}
			if (piece.kind.equals("safe")) {
				SafeBlock safe = Registry.register(BuiltInRegistries.BLOCK, Rauk.id(piece.id),
						SafeBlock.create(piece, properties(piece.feel).noOcclusion().strength(-1, 3600000)));
				FURNITURE.put(piece.id, safe);
				Registry.register(BuiltInRegistries.ITEM, Rauk.id(piece.id), new BlockItem(safe, new Item.Properties()));
				SafeBlock.entityType = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Rauk.id(piece.id),
						BlockEntityType.Builder.of(SafeEntity::new, safe).build(null));
				continue;
			}
			if (piece.kind.equals("switch")) {
				lightSwitch = Registry.register(BuiltInRegistries.BLOCK, Rauk.id(piece.id),
						new HotelSwitch(properties(piece.feel).noOcclusion().noCollission().strength(0.5f)));
				Registry.register(BuiltInRegistries.ITEM, Rauk.id(piece.id), new BlockItem(lightSwitch, new Item.Properties()));
				continue;
			}
			BlockBehaviour.Properties p = properties(piece.feel).noOcclusion().lightLevel(s -> FurnitureBlock.light(piece, s));
			if (!piece.solid) {
				p = p.noCollission();
			}
			FurnitureBlock block = Registry.register(BuiltInRegistries.BLOCK, Rauk.id(piece.id), FurnitureBlock.create(piece, p));
			FURNITURE.put(piece.id, block);
			Registry.register(BuiltInRegistries.ITEM, Rauk.id(piece.id), new BlockItem(block, new Item.Properties()));
		}
		for (String[] d : DOOR_TYPES) {
			BlockSetType type = switch (d[0]) {
				case "hotel_door" -> BlockSetType.DARK_OAK;
				case "service_door" -> BlockSetType.SPRUCE;
				case "lift_door" -> BlockSetType.COPPER;
				default -> BlockSetType.OAK;
			};
			boolean gate = d[0].equals("lift_door");
			HotelDoorBlock door = Registry.register(BuiltInRegistries.BLOCK, Rauk.id(d[0]), new HotelDoorBlock(type, gate,
					properties(gate ? Feel.METAL : Feel.WOOD).strength(3.0f).noOcclusion().pushReaction(PushReaction.DESTROY)));
			DOORS.put(d[0], door);
			Registry.register(BuiltInRegistries.ITEM, Rauk.id(d[0]), new DoubleHighBlockItem(door, new Item.Properties()));
		}
		HotelDoorBlock.entityType = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Rauk.id("hotel_door"),
				BlockEntityType.Builder.of(HotelDoorEntity::new, DOORS.values().toArray(new Block[0])).build(null));
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Rauk.id("furniture"), FabricItemGroup.builder()
				.title(Component.translatable("itemGroup.rauk.furniture"))
				.icon(() -> new ItemStack(FURNITURE.get("club_chair")))
				.displayItems((params, out) -> {
					FURNITURE.values().forEach(out::accept);
					out.accept(lightSwitch);
					out.accept(liftControl);
					out.accept(liftButton);
					DOORS.values().forEach(out::accept);
				})
				.build());
	}

	private static Block create(Material m) {
		BlockBehaviour.Properties p = properties(m.feel());
		return switch (m.shape()) {
			case CUBE -> new Block(p);
			case FACING -> new HotelShapes.Facing(p);
			case COLUMN -> new HotelShapes.Column(p);
			case PILASTER -> new HotelShapes.Pilaster(p.noOcclusion());
			case CORNICE -> new HotelShapes.Cornice(p.noOcclusion());
			case BALUSTRADE -> new HotelShapes.Balustrade(p.noOcclusion());
			case RAILING -> new HotelShapes.Railing(p.noOcclusion());
			case RUNNER -> new HotelShapes.Runner(p);
			case STAIRS, CARPET_STAIRS -> new StairBlock(BLOCKS.get(m.other()).defaultBlockState(), p);
			case SLAB -> new SlabBlock(p);
			case PIPE -> new HotelShapes.Pipe(p.noOcclusion());
			case FINIAL -> new HotelShapes.Finial(p.noOcclusion());
			case SLANT -> new HotelShapes.Slant(p.noOcclusion());
			case POST -> new HotelShapes.Post(p.noOcclusion());
			case LETTER -> new HotelShapes.LetterBlock(p.noOcclusion().noCollission());
			case FESTOON -> new HotelShapes.Festoon(p.noOcclusion().noCollission().strength(0.3f)
					.lightLevel(state -> state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) ? 6 : 0));
		};
	}

	private static BlockBehaviour.Properties properties(Feel feel) {
		BlockBehaviour.Properties p = BlockBehaviour.Properties.of();
		return switch (feel) {
			case WOOD -> p.mapColor(MapColor.COLOR_BROWN).strength(2.0f, 3.0f).sound(SoundType.WOOD);
			case STONE -> p.mapColor(MapColor.STONE).strength(1.5f, 6.0f).sound(SoundType.STONE);
			case METAL -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL);
			case CLOTH -> p.mapColor(MapColor.COLOR_RED).strength(0.1f).sound(SoundType.WOOL);
			case TILE -> p.mapColor(MapColor.QUARTZ).strength(1.5f, 6.0f).sound(SoundType.DEEPSLATE_TILES);
			case PLASTER -> p.mapColor(MapColor.SAND).strength(1.0f, 3.0f).sound(SoundType.CALCITE);
			case GLASS -> p.mapColor(MapColor.NONE).strength(0.3f).sound(SoundType.GLASS);
		};
	}
}

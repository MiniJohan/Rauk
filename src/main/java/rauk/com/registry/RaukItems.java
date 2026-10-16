package rauk.com.registry;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import rauk.com.Rauk;
import rauk.com.item.CarriedItem;
import rauk.com.item.FlashlightItem;

public final class RaukItems {
	/** Whether a flashlight is switched on. */
	public static final DataComponentType<Boolean> LIT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Rauk.id("lit"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

	public static final Item RAUK_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, Rauk.id("rauk_spawn_egg"),
			new SpawnEggItem(RaukEntities.RAUK, 0xD8D2C6, 0x3A2E2A, new Item.Properties()));

	/** The things to carry (hotel design 4 and 8, step 4d), by id, in the order the puzzles want them. */
	public static final Map<String, Item> CARRIED = new LinkedHashMap<>();

	public static final Item FLASHLIGHT = carried("flashlight", new FlashlightItem(new Item.Properties()));
	/** Puzzle 1: the service lift won't run without it. */
	public static final Item LIFT_CRANK = carried("lift_crank", new CarriedItem(new Item.Properties().stacksTo(1)));
	/** Puzzle 1: opens the staff-only doors. */
	public static final Item STAFF_KEYRING = carried("staff_keyring", new CarriedItem(new Item.Properties().stacksTo(1)));
	/** Puzzle 2: three, for the generator's fuse board. */
	public static final Item FUSE = carried("fuse", new CarriedItem(new Item.Properties().stacksTo(3)));
	/** Puzzle 3: Wennberg's study, in the penthouse. */
	public static final Item STUDY_KEY = carried("study_key", new CarriedItem(new Item.Properties().stacksTo(1)));
	/** Puzzle 4: lets the service lift go up to 4. */
	public static final Item PENTHOUSE_KEY = carried("penthouse_key", new CarriedItem(new Item.Properties().stacksTo(1)));
	/** Puzzle 5: the lockdown panel's key. */
	public static final Item LOCKDOWN_KEY = carried("lockdown_key", new CarriedItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

	private RaukItems() {
	}

	private static Item carried(String id, Item item) {
		Registry.register(BuiltInRegistries.ITEM, Rauk.id(id), item);
		CARRIED.put(id, item);
		return item;
	}

	/** Whether a stack is one of the things to carry, which the hotel never lets go to waste. */
	public static boolean carried(ItemStack stack) {
		return CARRIED.containsValue(stack.getItem());
	}

	public static void init() {
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> entries.accept(RAUK_SPAWN_EGG));
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Rauk.id("carried"), FabricItemGroup.builder()
				.title(Component.translatable("itemGroup.rauk.carried"))
				.icon(() -> new ItemStack(FLASHLIGHT))
				.displayItems((params, out) -> CARRIED.values().forEach(out::accept))
				.build());
	}
}

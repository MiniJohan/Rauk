package rauk.com.entity.hunt;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import rauk.com.entity.RaukEntity;
import rauk.com.entity.mind.RaukMind;

/**
 * How Rauk reads a prey it can see (design doc 6.4): whether it is looking at Rauk, what it is
 * holding, whether it is busy, how strong it is, whether it is running away, and whether it is
 * alone.
 */
public record PreyRead(boolean lookingAt, boolean rangedAimed, boolean fire, boolean shield, boolean busy,
		boolean vulnerable, boolean running, boolean isolated, boolean armed, double distance) {

	public static PreyRead of(LivingEntity prey, RaukEntity rauk, Vec3 velocity, List<LivingEntity> others) {
		double distance = prey.distanceTo(rauk);
		return new PreyRead(lookingAt(prey, rauk), rangedAimed(prey), fire(prey), prey.isBlocking(), busy(prey),
				vulnerable(prey), running(prey, rauk, velocity), isolated(prey, others), armed(prey), distance);
	}

	/** Holding something that can hurt it: a blade, an axe, a trident, a mace or a bow. */
	private static boolean armed(LivingEntity prey) {
		for (ItemStack stack : new ItemStack[] {prey.getMainHandItem(), prey.getOffhandItem()}) {
			var item = stack.getItem();
			if (item instanceof net.minecraft.world.item.SwordItem || item instanceof net.minecraft.world.item.AxeItem
					|| item instanceof TridentItem || item instanceof BowItem || item instanceof CrossbowItem
					|| item instanceof net.minecraft.world.item.MaceItem) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether the prey could see Rauk right now: Rauk is in front of its face, nothing is in the
	 * way, and there is light enough to see by (or Rauk is close).
	 */
	public static boolean lookingAt(LivingEntity prey, RaukEntity rauk) {
		Vec3 eye = prey.getEyePosition();
		Vec3 view = prey.getViewVector(1f);
		double cone = Math.cos(Math.toRadians(40));
		Vec3[] points = {rauk.getBoundingBox().getCenter(), rauk.mind().eye()};
		BlockPos at = rauk.blockPosition();
		int light = rauk.level().getMaxLocalRawBrightness(at);
		boolean dark = light <= 3 && !RaukMind.carriesLight(prey);
		for (Vec3 p : points) {
			Vec3 to = p.subtract(eye);
			double d = to.length();
			if (d < 1e-3 || view.dot(to.scale(1 / d)) < cone || (dark && d > 6)) {
				continue;
			}
			var hit = rauk.level().clip(new ClipContext(eye, p, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, prey));
			if (hit.getType() == HitResult.Type.MISS) {
				return true;
			}
		}
		return false;
	}

	/** A drawn bow, a raised trident or a loaded crossbow. */
	private static boolean rangedAimed(LivingEntity prey) {
		if (prey.isUsingItem()) {
			var item = prey.getUseItem().getItem();
			if (item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem) {
				return true;
			}
		}
		for (ItemStack stack : new ItemStack[] {prey.getMainHandItem(), prey.getOffhandItem()}) {
			if (stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
				return true;
			}
		}
		return false;
	}

	/** Fire in hand: flint and steel, a fire charge, or a fire-enchanted weapon. It keeps away. */
	private static boolean fire(LivingEntity prey) {
		for (ItemStack stack : new ItemStack[] {prey.getMainHandItem(), prey.getOffhandItem()}) {
			if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)) {
				return true;
			}
			for (var e : stack.getEnchantments().keySet()) {
				if (e.is(Enchantments.FIRE_ASPECT) || e.is(Enchantments.FLAME)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Eating, drinking, at a chest or table, mining, or facing a wall: openings. */
	private static boolean busy(LivingEntity prey) {
		if (prey.isUsingItem() && !rangedAimed(prey)) {
			return true;
		}
		if (prey instanceof Player p && p.hasContainerOpen()) {
			return true;
		}
		if (prey.swinging) {
			return true;
		}
		Vec3 eye = prey.getEyePosition();
		var hit = prey.level().clip(new ClipContext(eye, eye.add(prey.getViewVector(1f).scale(1.6)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, prey));
		return hit.getType() != HitResult.Type.MISS;
	}

	/** Badly hurt, too hungry to sprint, or unarmored. */
	private static boolean vulnerable(LivingEntity prey) {
		if (prey.getHealth() < prey.getMaxHealth() * 0.5f || prey.getArmorValue() < 6) {
			return true;
		}
		return prey instanceof Player p && p.getFoodData().getFoodLevel() <= 6;
	}

	/** Moving fast and away from Rauk. */
	private static boolean running(LivingEntity prey, RaukEntity rauk, Vec3 velocity) {
		Vec3 away = prey.position().subtract(rauk.position());
		return velocity.horizontalDistance() > 0.17 && velocity.dot(away) > 0;
	}

	/** No other prey within 12 blocks. */
	private static boolean isolated(LivingEntity prey, List<LivingEntity> others) {
		for (LivingEntity o : others) {
			if (o != prey && o.distanceTo(prey) < 12) {
				return false;
			}
		}
		return true;
	}
}

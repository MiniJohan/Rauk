package rauk.com.mixin;

import java.util.Optional;
import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import rauk.com.entity.RaukEntity;

/**
 * Rauk's skull is carried well ahead of its collision box on its long neck. Projectiles that pass
 * through the skull hit it, in the head (design doc 7.3: head shots do half again as much).
 */
@Mixin(ProjectileUtil.class)
public abstract class ProjectileUtilMixin {
	@Inject(method = "getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;",
			at = @At("RETURN"), cancellable = true)
	private static void rauk$hitTheSkull(Level level, Entity projectile, Vec3 from, Vec3 to, AABB area, Predicate<Entity> filter,
			float margin, CallbackInfoReturnable<EntityHitResult> cir) {
		EntityHitResult result = cir.getReturnValue();
		double best = result == null ? Double.MAX_VALUE : from.distanceToSqr(result.getLocation());
		for (RaukEntity rauk : level.getEntitiesOfClass(RaukEntity.class, area.inflate(6), filter)) {
			Vec3 head = rauk.mind().eye();
			Optional<Vec3> hit = new AABB(head, head).inflate(0.5 + margin).clip(from, to);
			if (hit.isPresent() && from.distanceToSqr(hit.get()) < best) {
				best = from.distanceToSqr(hit.get());
				cir.setReturnValue(new EntityHitResult(rauk, hit.get()));
			}
		}
	}
}

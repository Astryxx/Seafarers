package dev.seafarers.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.seafarers.Seafarers;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;

/**
 * One advancement trigger for everything a sailor does: {@code "trigger": "seafarers:feat", "conditions":
 * {"event": "launch"}}. The events are listed in {@link dev.seafarers.ship.Feats}.
 */
public final class ModTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, Seafarers.MODID);

    public static final DeferredHolder<CriterionTrigger<?>, FeatTrigger> FEAT = TRIGGERS.register("feat", FeatTrigger::new);

    public static final class FeatTrigger extends SimpleCriterionTrigger<FeatTrigger.Instance> {
        @Override
        public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player, String event) {
            this.trigger(player, i -> i.event().equals(event));
        }

        public record Instance(Optional<ContextAwarePredicate> player, String event) implements SimpleCriterionTrigger.SimpleInstance {
            public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    Codec.STRING.fieldOf("event").forGetter(Instance::event)
            ).apply(i, Instance::new));
        }
    }

    private ModTriggers() {}
}

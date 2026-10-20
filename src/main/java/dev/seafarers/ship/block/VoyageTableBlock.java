package dev.seafarers.ship.block;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.seafarers.Seafarers;
import dev.seafarers.registry.ModBlockEntities;
import dev.seafarers.ship.Crews;
import dev.seafarers.ship.SeafarersTags;
import dev.seafarers.ship.Ships;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The voyage table, run the Sea of Thieves way: anyone puts a voyage down on it, everyone aboard votes, and when
 * the whole crew agrees the voyage starts and everyone gets a copy. A voyage is any treasure or explorer map
 * (vanilla's or another mod's: a map with marks on it), or any item in the seafarers:voyages tag.
 * Use it with a voyage to put it down (one each; yours comes back if you swap); use it empty handed to vote,
 * cycling through what's on the table; sneak-use to take yours back.
 */
public class VoyageTableBlock extends MultiDecorBlock implements EntityBlock {
    /** Two blocks long, like the Sloop's quest table against the cabin's front wall. */
    public static final int[][] PARTS = {{0, 0, 0}, {1, 0, 0}};

    public static final int MAX_VOYAGES = 4;

    public VoyageTableBlock(Properties props) {
        super(props, Block.box(1, 0, 1, 15, 14, 15), PARTS);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(VoyageTableBlock::new);
    }

    public static boolean isVoyage(ItemStack stack) {
        if (stack.is(SeafarersTags.VOYAGES)) {
            return true;
        }
        MapDecorations marks = stack.get(DataComponents.MAP_DECORATIONS);
        return stack.has(DataComponents.MAP_ID) && marks != null && !marks.decorations().isEmpty();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!isVoyage(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        pos = this.origin(state, pos);
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof Table table) {
            if (table.propose(server, sp, stack.copyWithCount(1))) {
                stack.consume(1, player);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        pos = this.origin(state, pos);
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof Table table) {
            if (player.isShiftKeyDown()) {
                table.withdraw(server, sp);
            } else {
                table.open(server, sp);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && isMain(state) && level.getBlockEntity(pos) instanceof Table table) {
            for (Proposal p : table.proposals) {
                Block.popResource(level, pos, p.voyage);
            }
            table.proposals.clear();
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (!isMain(state)) {
            return null;
        }
        return new Table(pos, state);
    }

    public record Proposal(UUID by, ItemStack voyage) {}

    public static class Table extends BlockEntity {
        private final List<Proposal> proposals = new ArrayList<>();
        /** Who votes for which proposal (index into proposals). */
        private final Map<UUID, Integer> votes = new HashMap<>();

        public Table(BlockPos pos, BlockState state) {
            super(ModBlockEntities.VOYAGE_TABLE.get(), pos, state);
        }

        public List<Proposal> proposals() {
            return this.proposals;
        }

        boolean propose(ServerLevel level, ServerPlayer player, ItemStack voyage) {
            Proposal mine = this.find(player.getUUID());
            if (mine == null && this.proposals.size() >= MAX_VOYAGES) {
                dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.voyage_table_full"));
                return false;
            }
            if (mine != null) {
                this.give(level, player.getUUID(), mine.voyage);
                this.proposals.set(this.proposals.indexOf(mine), new Proposal(player.getUUID(), voyage));
            } else {
                this.proposals.add(new Proposal(player.getUUID(), voyage));
            }
            // whoever puts a voyage down votes for it
            this.votes.put(player.getUUID(), this.proposals.indexOf(this.find(player.getUUID())));
            level.playSound(null, this.getBlockPos(), SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1f, 1f);
            Seafarers.LOGGER.info("PROBE voyage proposed {} ({} on the table)", voyage.getHoverName().getString(), this.proposals.size());
            this.changed();
            this.tally(level, player);
            return true;
        }

        void withdraw(ServerLevel level, ServerPlayer player) {
            Proposal mine = this.find(player.getUUID());
            if (mine == null) {
                dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.voyage_hint"));
                return;
            }
            int i = this.proposals.indexOf(mine);
            this.proposals.remove(i);
            // votes for later proposals shift down; votes for this one are gone
            this.votes.replaceAll((who, v) -> v > i ? v - 1 : v);
            this.votes.values().removeIf(v -> v == i);
            this.give(level, player.getUUID(), mine.voyage);
            this.changed();
        }

        void vote(ServerLevel level, ServerPlayer player) {
            if (this.proposals.isEmpty()) {
                dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.voyage_hint"));
                return;
            }
            this.vote(level, player, (this.votes.getOrDefault(player.getUUID(), -1) + 1) % this.proposals.size());
        }

        /** Votes for one of the voyages on the table (by its place in the list). */
        public void vote(ServerLevel level, ServerPlayer player, int index) {
            if (index < 0 || index >= this.proposals.size()) {
                return;
            }
            this.votes.put(player.getUUID(), index);
            level.playSound(null, this.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4f, 1.2f);
            this.changed();
            this.tally(level, player);
        }

        /** Shows the vote, and starts the voyage once everyone who has a say agrees. */
        private void tally(ServerLevel level, ServerPlayer who) {
            Set<UUID> crew = this.crew(level);
            crew.add(who.getUUID());
            Integer choice = this.votes.get(who.getUUID());
            if (choice == null || choice >= this.proposals.size()) {
                return;
            }
            long agree = crew.stream().filter(id -> choice.equals(this.votes.get(id))).count();
            Proposal p = this.proposals.get(choice);
            Component name = p.voyage.getHoverName();
            for (UUID id : crew) {
                ServerPlayer member = level.getServer().getPlayerList().getPlayer(id);
                if (member != null) {
                    dev.seafarers.ship.Notify.show(member, Component.translatable("message.seafarers.voyage_votes", name, agree, crew.size())
                            .withStyle(ChatFormatting.GOLD));
                }
            }
            Seafarers.LOGGER.info("PROBE voyage vote {} {}/{}", name.getString(), agree, crew.size());
            if (agree < crew.size()) {
                return;
            }
            // agreed: every voter gets a copy (maps share their map data, so everyone's copy shows the same marks)
            for (UUID id : crew) {
                if (id.equals(who.getUUID())) {
                    if (!who.addItem(p.voyage.copy())) {
                        who.drop(p.voyage.copy(), false);
                    }
                } else {
                    this.give(level, id, p.voyage.copy());
                }
                ServerPlayer member = level.getServer().getPlayerList().getPlayer(id);
                if (member != null) {
                    dev.seafarers.ship.Feats.award(member, "voyage");
                    member.sendSystemMessage(Component.translatable("message.seafarers.voyage_started", name).withStyle(ChatFormatting.GOLD));
                }
            }
            for (Proposal other : this.proposals) {
                if (other != p) {
                    this.give(level, other.by, other.voyage);
                }
            }
            this.proposals.clear();
            this.votes.clear();
            level.playSound(null, this.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 0.6f, 1f);
            Seafarers.LOGGER.info("PROBE voyage started {} for {} crew", name.getString(), crew.size());
            this.changed();
        }

        // ---------------------------------------------------------------- the screen

        /** Opens the voyage table's screen for a player. */
        public void open(ServerLevel level, ServerPlayer player) {
            this.sendState(level, player);
            level.playSound(null, this.getBlockPos(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8f, 1f);
        }

        private void sendState(ServerLevel level, ServerPlayer player) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new dev.seafarers.network.VoyagePayloads.State(this.getBlockPos(), this.state(level, player)));
        }

        /** A button on the screen. Every action sends the screen fresh state afterwards. */
        public void act(ServerLevel level, ServerPlayer player, int action, int index, String text) {
            switch (action) {
                case dev.seafarers.network.VoyagePayloads.Action.VOTE -> this.vote(level, player, index);
                case dev.seafarers.network.VoyagePayloads.Action.WITHDRAW -> this.withdraw(level, player);
                case dev.seafarers.network.VoyagePayloads.Action.PROPOSE_HELD -> {
                    ItemStack held = player.getMainHandItem();
                    if (isVoyage(held)) {
                        if (this.propose(level, player, held.copyWithCount(1))) {
                            held.shrink(1);
                        }
                    } else {
                        dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.voyage_hold_one"));
                    }
                }
                case dev.seafarers.network.VoyagePayloads.Action.RENAME -> this.rename(level, player, text);
                case dev.seafarers.network.VoyagePayloads.Action.LEAVE_CREW -> dev.seafarers.ship.Captaincy.leave(player, dev.seafarers.ship.Reply.to(player));
                case dev.seafarers.network.VoyagePayloads.Action.KICK -> {
                    UUID who = parse(text);
                    if (who != null) {
                        dev.seafarers.ship.Captaincy.kick(player, who, dev.seafarers.ship.Reply.to(player));
                    }
                }
                case dev.seafarers.network.VoyagePayloads.Action.FREE -> dev.seafarers.ship.Refloat.free(player, dev.seafarers.ship.Reply.to(player));
                case dev.seafarers.network.VoyagePayloads.Action.BRIG -> {
                    UUID who = parse(text);
                    if (who != null) {
                        dev.seafarers.ship.Brig.vote(player, who, !Crews.get(level).inBrig(who), dev.seafarers.ship.Reply.to(player));
                    }
                }
                case dev.seafarers.network.VoyagePayloads.Action.PROMOTE -> {
                    UUID who = parse(text);
                    if (who != null) {
                        dev.seafarers.ship.Captaincy.promote(player, who, dev.seafarers.ship.Reply.to(player));
                    }
                }
                case dev.seafarers.network.VoyagePayloads.Action.INVITE -> {
                    UUID who = parse(text);
                    ServerPlayer target = who == null ? null : level.getServer().getPlayerList().getPlayer(who);
                    if (target != null && target.level() == level
                            && target.position().distanceTo(Ships.toWorld(level, this.getBlockPos())) < NEARBY) {
                        dev.seafarers.ship.Captaincy.invite(player, target, dev.seafarers.ship.Reply.to(player));
                    }
                }
                case dev.seafarers.network.VoyagePayloads.Action.LOCK -> dev.seafarers.ship.Captaincy.lock(player, index == 1, dev.seafarers.ship.Reply.to(player));
                case dev.seafarers.network.VoyagePayloads.Action.SWAP -> {
                    SubLevel ship = Ships.shipAt(level, this.getBlockPos());
                    if (ship != null && ship.getUniqueId().equals(Crews.get(level).captainedBy(player.getUUID()))) {
                        dev.seafarers.ship.deed.ShipChoice.open(player, dev.seafarers.network.ChoicePayloads.SWAP);
                        return; // the choice screen takes over from the table's
                    }
                }
                default -> {
                }
            }
            this.sendState(level, player);
        }

        /** How near the table (blocks) a player must be to be offered a place in the crew. */
        private static final double NEARBY = 16;

        @Nullable
        private static UUID parse(String text) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        /** Renames the ship: her crew can, anyone can if she has no crew. */
        private void rename(ServerLevel level, ServerPlayer player, String name) {
            SubLevel ship = Ships.shipAt(level, this.getBlockPos());
            String clean = name.strip();
            if (ship == null || clean.isEmpty() || clean.length() > 32) {
                return;
            }
            var crew = Crews.get(level).crewOf(ship.getUniqueId());
            if (!crew.isEmpty() && !crew.contains(player.getUUID())) {
                dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.not_your_ship"));
                return;
            }
            ((dev.ryanhcode.sable.sublevel.ServerSubLevel) ship).setName(clean);
            Seafarers.LOGGER.info("PROBE ship renamed {}", clean);
        }

        /** Everything the screen shows, for one player. */
        public CompoundTag state(ServerLevel level, ServerPlayer player) {
            CompoundTag out = new CompoundTag();
            Set<UUID> say = this.crew(level);
            say.add(player.getUUID());
            ListTag props = new ListTag();
            for (int i = 0; i < this.proposals.size(); i++) {
                Proposal p = this.proposals.get(i);
                CompoundTag t = new CompoundTag();
                t.putString("name", p.voyage.getHoverName().getString());
                t.put("item", p.voyage.save(level.registryAccess()));
                ServerPlayer by = level.getServer().getPlayerList().getPlayer(p.by);
                t.putString("by", by != null ? by.getGameProfile().getName() : "?");
                final int index = i;
                t.putInt("votes", (int) say.stream().filter(id -> Integer.valueOf(index).equals(this.votes.get(id))).count());
                t.putBoolean("mine", p.by.equals(player.getUUID()));
                t.putBoolean("myVote", Integer.valueOf(i).equals(this.votes.get(player.getUUID())));
                props.add(t);
            }
            out.put("proposals", props);
            out.putInt("say", say.size());

            SubLevel ship = Ships.shipAt(level, this.getBlockPos());
            ListTag crew = new ListTag();
            CompoundTag info = new CompoundTag();
            if (ship != null) {
                var helm = Ships.helmOf(ship);
                var aboard = level.getEntitiesOfClass(ServerPlayer.class, ship.boundingBox().toMojang().inflate(3));
                Crews crews = Crews.get(level);
                UUID captain = crews.captainOf(ship.getUniqueId());
                java.util.List<UUID> members = crews.crewOf(ship.getUniqueId());
                for (UUID id : members) {
                    ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                    CompoundTag t = new CompoundTag();
                    t.putString("name", p != null ? p.getGameProfile().getName() : id.toString().substring(0, 8));
                    t.putBoolean("online", p != null);
                    t.putBoolean("aboard", p != null && aboard.contains(p));
                    t.putBoolean("you", id.equals(player.getUUID()));
                    t.putString("uuid", id.toString());
                    t.putBoolean("captain", id.equals(captain));
                    t.putBoolean("brig", crews.inBrig(id));
                    t.putInt("brigVotes", dev.seafarers.ship.Brig.tally(level.getServer(), crews, ship.getUniqueId(), id));
                    t.putBoolean("myBrigVote", dev.seafarers.ship.Brig.voted(ship.getUniqueId(), id, player.getUUID()));
                    crew.add(t);
                }
                boolean youCaptain = player.getUUID().equals(captain);
                info.putString("captain", captain == null ? "" : dev.seafarers.ship.Captaincy.name(level.getServer(), captain));
                info.putBoolean("youCaptain", youCaptain);
                info.putBoolean("locked", crews.locked(ship.getUniqueId()));
                info.putBoolean("canLock", youCaptain && dev.seafarers.SeafarersConfig.ALLOW_LOCKING.get());
                info.putBoolean("canSwap", youCaptain && dev.seafarers.SeafarersConfig.ALLOW_SWAP.get());
                info.putInt("swapWait", youCaptain ? dev.seafarers.ship.deed.ShipChoice.waitSeconds(player) : 0);
                boolean canInvite = members.contains(player.getUUID())
                        && (youCaptain || !dev.seafarers.SeafarersConfig.ONLY_CAPTAIN_INVITES.get());
                out.putBoolean("canInvite", canInvite);
                ListTag nearby = new ListTag();
                Vec3 here = Ships.toWorld(level, this.getBlockPos());
                for (ServerPlayer p : level.players()) {
                    if (!members.contains(p.getUUID()) && p.position().distanceTo(here) < NEARBY && !p.isSpectator()) {
                        CompoundTag t = new CompoundTag();
                        t.putString("name", p.getGameProfile().getName());
                        t.putString("uuid", p.getUUID().toString());
                        nearby.add(t);
                    }
                }
                out.put("nearby", nearby);
                info.putString("name", ship.getName() == null ? "" : ship.getName());
                if (helm != null) {
                    info.putString("layout", helm.layout());
                    info.putFloat("water", helm.flood());
                    info.putInt("holes", helm.holeCount());
                    info.putBoolean("anchored", helm.anchored());
                    info.putFloat("speed", (float) helm.speed());
                    info.putInt("crewMax", helm.maxCrew());
                    info.putBoolean("canFree", members.contains(player.getUUID()) && dev.seafarers.SeafarersConfig.ALLOW_FREEING.get()
                            && !helm.rowboat());
                    info.putBoolean("hasBrig", helm.hasBrig() && dev.seafarers.SeafarersConfig.ALLOW_BRIG.get()
                            && members.contains(player.getUUID()));
                    info.putInt("brigNeed", dev.seafarers.ship.Brig.needed(level.getServer(), crews, ship.getUniqueId()));
                    int[] masts = new int[helm.masts()];
                    for (int m = 0; m < masts.length; m++) {
                        masts[m] = helm.sail(m).health();
                    }
                    info.putIntArray("masts", masts);
                    info.putInt("mastMax", helm.mastHealthMax());
                }
            }
            out.put("crew", crew);
            out.put("ship", info);
            return out;
        }

        /**
         * Who has a say: the ship's crew who are online and near the table; on a ship without a crew (or a table
         * ashore), whoever is near the table.
         */
        private Set<UUID> crew(ServerLevel level) {
            Set<UUID> out = new HashSet<>();
            Vec3 here = Ships.toWorld(level, this.getBlockPos());
            SubLevel ship = Ships.shipAt(level, this.getBlockPos());
            if (ship != null) {
                for (UUID id : Crews.get(level).crewOf(ship.getUniqueId())) {
                    ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
                    if (p != null && p.level() == level && p.position().distanceTo(here) < 64) {
                        out.add(id);
                    }
                }
            }
            if (out.isEmpty()) {
                for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(here, here).inflate(16))) {
                    out.add(p.getUUID());
                }
            }
            return out;
        }

        @Nullable
        private Proposal find(UUID who) {
            for (Proposal p : this.proposals) {
                if (p.by.equals(who)) {
                    return p;
                }
            }
            return null;
        }

        private void give(ServerLevel level, UUID who, ItemStack stack) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(who);
            if (p != null && p.addItem(stack)) {
                return;
            }
            Vec3 at = Ships.toWorld(level, this.getBlockPos()).add(0, 1, 0);
            if (p != null) {
                p.drop(stack, false);
            } else {
                net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y, at.z, stack);
                level.addFreshEntity(item);
            }
        }

        private void changed() {
            this.setChanged();
            if (this.level != null) {
                this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
            }
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            ListTag list = new ListTag();
            for (Proposal p : this.proposals) {
                CompoundTag t = new CompoundTag();
                t.putUUID("by", p.by);
                t.put("voyage", p.voyage.save(registries));
                list.add(t);
            }
            tag.put("proposals", list);
            CompoundTag votes = new CompoundTag();
            this.votes.forEach((who, v) -> votes.putInt(who.toString(), v));
            tag.put("votes", votes);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            this.proposals.clear();
            for (Tag t : tag.getList("proposals", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                ItemStack voyage = ItemStack.parseOptional(registries, c.getCompound("voyage"));
                if (!voyage.isEmpty()) {
                    this.proposals.add(new Proposal(c.getUUID("by"), voyage));
                }
            }
            this.votes.clear();
            CompoundTag votes = tag.getCompound("votes");
            for (String k : votes.getAllKeys()) {
                this.votes.put(UUID.fromString(k), votes.getInt(k));
            }
        }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            return this.saveWithoutMetadata(registries);
        }

        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }
    }
}

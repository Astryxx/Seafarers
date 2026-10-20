package dev.seafarers.ship.deed;

import dev.seafarers.Seafarers;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What each player has done about a ship of their own: whether they've chosen yet (on the ship choice screen),
 * the one deed of theirs that is valid and unused, when they last got a free ship, and deeds held for them
 * while they're dead.
 */
public final class Sailors extends SavedData {
    private static final String NAME = Seafarers.MODID + "_sailors";

    public enum Choice { NONE, SHIP, CREW }

    private static final class Entry {
        Choice choice = Choice.NONE;
        @Nullable UUID deed;
        String deedLayout = "";
        @Nullable UUID deedReplaces;
        /** Game time the player last got a deed; -1 if never (the first ship is never held up). */
        long lastShip = -1;
        final List<CompoundTag> held = new ArrayList<>();
    }

    private final Map<UUID, Entry> players = new HashMap<>();

    public static Sailors get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Sailors::new, Sailors::load, null), NAME);
    }

    private Entry entry(UUID player) {
        return this.players.computeIfAbsent(player, k -> new Entry());
    }

    public Choice choice(UUID player) {
        Entry e = this.players.get(player);
        return e == null ? Choice.NONE : e.choice;
    }

    public void setChoice(UUID player, Choice choice) {
        this.entry(player).choice = choice;
        this.setDirty();
    }

    /** The id of the player's valid, unused deed, or null. */
    @Nullable
    public UUID deed(UUID player) {
        Entry e = this.players.get(player);
        return e == null ? null : e.deed;
    }

    public String deedLayout(UUID player) {
        Entry e = this.players.get(player);
        return e == null ? "" : e.deedLayout;
    }

    @Nullable
    public UUID deedReplaces(UUID player) {
        Entry e = this.players.get(player);
        return e == null ? null : e.deedReplaces;
    }

    public boolean valid(Deed deed) {
        return deed.id().equals(this.deed(deed.owner()));
    }

    /** Records a new deed as the player's one valid deed (any older one stops working). */
    public void issue(UUID player, UUID id, String layout, @Nullable UUID replaces, long now, boolean startsCooldown) {
        Entry e = this.entry(player);
        e.deed = id;
        e.deedLayout = layout;
        e.deedReplaces = replaces;
        if (startsCooldown) {
            e.lastShip = now;
        }
        this.setDirty();
    }

    public void used(Deed deed) {
        Entry e = this.players.get(deed.owner());
        if (e != null && deed.id().equals(e.deed)) {
            e.deed = null;
            e.deedReplaces = null;
            this.setDirty();
        }
    }

    public long lastShip(UUID player) {
        Entry e = this.players.get(player);
        return e == null ? -1 : e.lastShip;
    }

    public void clearCooldown(UUID player) {
        Entry e = this.players.get(player);
        if (e != null && e.lastShip >= 0) {
            e.lastShip = -1;
            this.setDirty();
        }
    }

    /** Forgets everything about a player: they choose again as if new (an operator's /crew reset). */
    public void reset(UUID player) {
        if (this.players.remove(player) != null) {
            this.setDirty();
        }
    }

    /** Keeps a deed for a player who died with it; given back when they respawn. */
    public void hold(UUID player, ItemStack stack, HolderLookup.Provider registries) {
        this.entry(player).held.add((CompoundTag) stack.save(registries));
        this.setDirty();
    }

    public List<ItemStack> takeHeld(UUID player, HolderLookup.Provider registries) {
        Entry e = this.players.get(player);
        if (e == null || e.held.isEmpty()) {
            return List.of();
        }
        List<ItemStack> out = new ArrayList<>();
        for (CompoundTag t : e.held) {
            ItemStack.parse(registries, t).ifPresent(out::add);
        }
        e.held.clear();
        this.setDirty();
        return out;
    }

    private static Sailors load(CompoundTag tag, HolderLookup.Provider registries) {
        Sailors s = new Sailors();
        for (Tag t : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) t;
            Entry e = new Entry();
            try {
                e.choice = Choice.valueOf(p.getString("choice"));
            } catch (IllegalArgumentException ignored) {
                e.choice = Choice.NONE;
            }
            if (p.hasUUID("deed")) {
                e.deed = p.getUUID("deed");
            }
            e.deedLayout = p.getString("deedLayout");
            if (p.hasUUID("replaces")) {
                e.deedReplaces = p.getUUID("replaces");
            }
            e.lastShip = p.contains("lastShip") ? p.getLong("lastShip") : -1;
            for (Tag h : p.getList("held", Tag.TAG_COMPOUND)) {
                e.held.add((CompoundTag) h);
            }
            s.players.put(NbtUtils.loadUUID(p.get("player")), e);
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        this.players.forEach((id, e) -> {
            CompoundTag p = new CompoundTag();
            p.put("player", NbtUtils.createUUID(id));
            p.putString("choice", e.choice.name());
            if (e.deed != null) {
                p.putUUID("deed", e.deed);
            }
            p.putString("deedLayout", e.deedLayout);
            if (e.deedReplaces != null) {
                p.putUUID("replaces", e.deedReplaces);
            }
            p.putLong("lastShip", e.lastShip);
            ListTag held = new ListTag();
            held.addAll(e.held);
            p.put("held", held);
            list.add(p);
        });
        tag.put("players", list);
        return tag;
    }
}

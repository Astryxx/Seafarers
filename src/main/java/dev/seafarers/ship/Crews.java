package dev.seafarers.ship;

import dev.seafarers.Seafarers;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Which players crew which ship (by Sable sub-level id), who captains each ship and whether she's locked. Crew
 * respawn aboard their ship and get a new one when it sinks. Invites are kept in memory only.
 */
public final class Crews extends SavedData {
    private static final String NAME = Seafarers.MODID + "_crews";

    private final Map<UUID, UUID> shipOf = new HashMap<>();
    private final Map<UUID, UUID> invites = new HashMap<>();
    /** The mattress each player last chose (plot position on their ship). */
    private final Map<UUID, Long> beds = new HashMap<>();
    /** Players who have since set their spawn ashore: they respawn there instead. */
    private final java.util.Set<UUID> ashore = new java.util.HashSet<>();
    /** Ship to her captain: whoever launched her, or was handed the ship since. */
    private final Map<UUID, UUID> captains = new HashMap<>();
    /** Ships whose captain has locked them: only her crew can use her (see ShipLocks). */
    private final java.util.Set<UUID> locked = new java.util.HashSet<>();
    /** Crew locked in their ship's brig, and the game time they were put there (see Brig). */
    private final Map<UUID, Long> brig = new HashMap<>();

    public static Crews get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Crews::new, Crews::load, null), NAME);
    }

    @Nullable
    public UUID shipOf(UUID player) {
        return this.shipOf.get(player);
    }

    public List<UUID> crewOf(UUID ship) {
        List<UUID> out = new ArrayList<>();
        this.shipOf.forEach((player, s) -> {
            if (s.equals(ship)) {
                out.add(player);
            }
        });
        return out;
    }

    /** @return false if the crew is already full */
    public boolean join(ServerPlayer player, UUID ship, int maxCrew) {
        if (!ship.equals(this.shipOf.get(player.getUUID())) && this.crewOf(ship).size() >= maxCrew) {
            return false;
        }
        UUID before = this.shipOf.get(player.getUUID());
        if (before != null && !before.equals(ship)) {
            this.leave(player.getUUID()); // a captain joining another crew hands her old ship on
        }
        this.shipOf.put(player.getUUID(), ship);
        this.setDirty();
        return true;
    }

    /** Remembers the mattress a player chose aboard their ship; they respawn on it from now on. */
    public void setBed(UUID player, net.minecraft.core.BlockPos plotPos) {
        this.beds.put(player, plotPos.asLong());
        this.ashore.remove(player);
        this.setDirty();
    }

    @Nullable
    public net.minecraft.core.BlockPos bedOf(UUID player) {
        Long l = this.beds.get(player);
        return l == null ? null : net.minecraft.core.BlockPos.of(l);
    }

    public void setAshore(UUID player, boolean ashore) {
        if (ashore ? this.ashore.add(player) : this.ashore.remove(player)) {
            this.setDirty();
        }
    }

    public boolean ashore(UUID player) {
        return this.ashore.contains(player);
    }

    /**
     * Takes a player off their crew. If they were the captain, the ship passes to another of her crew;
     * with nobody left aboard she's nobody's (and unlocked).
     * @return who captains the ship now, if the captaincy changed hands; else null
     */
    @Nullable
    public UUID leave(UUID player) {
        this.beds.remove(player);
        this.brig.remove(player);   // off the crew, out of her brig
        UUID ship = this.shipOf.remove(player);
        if (ship == null) {
            return null;
        }
        this.setDirty();
        if (!player.equals(this.captains.get(ship))) {
            return null;
        }
        List<UUID> rest = this.crewOf(ship);
        if (rest.isEmpty()) {
            this.captains.remove(ship);
            this.locked.remove(ship);
            return null;
        }
        this.captains.put(ship, rest.get(0));
        return rest.get(0);
    }

    @Nullable
    public UUID captainOf(UUID ship) {
        return this.captains.get(ship);
    }

    public void setCaptain(UUID ship, UUID player) {
        this.captains.put(ship, player);
        this.setDirty();
    }

    /** The ship this player captains, or null. */
    @Nullable
    public UUID captainedBy(UUID player) {
        for (Map.Entry<UUID, UUID> e : this.captains.entrySet()) {
            if (e.getValue().equals(player)) {
                return e.getKey();
            }
        }
        return null;
    }

    /** How many ships have a captain: the player ships on the server. */
    public int captainCount() {
        return this.captains.size();
    }

    public boolean locked(UUID ship) {
        return this.locked.contains(ship);
    }

    public void setLocked(UUID ship, boolean lock) {
        if (lock ? this.locked.add(ship) : this.locked.remove(ship)) {
            this.setDirty();
        }
    }

    /** Forgets a ship that is gone for good: her crew are nobody's crew, she has no captain. */
    public void forgetShip(UUID ship) {
        for (UUID player : this.crewOf(ship)) {
            this.shipOf.remove(player);
            this.beds.remove(player);
            this.brig.remove(player);
        }
        this.captains.remove(ship);
        this.locked.remove(ship);
        this.setDirty();
    }

    /** Ends a ship's captaincy (she's being swapped away); her crew stay listed so they follow the captain to the new ship. */
    public void dropCaptaincy(UUID ship) {
        this.captains.remove(ship);
        this.locked.remove(ship);
        this.setDirty();
    }

    /** Moves a whole crew to a new ship (after their old one sank). */
    public void reassign(UUID oldShip, UUID newShip) {
        this.shipOf.replaceAll((player, ship) -> ship.equals(oldShip) ? newShip : ship);
        UUID captain = this.captains.remove(oldShip);
        if (captain != null) {
            this.captains.put(newShip, captain);
        }
        if (this.locked.remove(oldShip)) {
            this.locked.add(newShip);
        }
        this.shipOf.forEach((player, ship) -> {
            if (ship.equals(newShip)) {
                this.beds.remove(player); // the new ship's bunks are elsewhere in her plot
            }
        });
        this.setDirty();
    }

    /** Whether this player is locked in their ship's brig. */
    public boolean inBrig(UUID player) {
        return this.brig.containsKey(player);
    }

    /** Game time this player was locked up, or -1. */
    public long brigSince(UUID player) {
        return this.brig.getOrDefault(player, -1L);
    }

    public void setBrig(UUID player, long since) {
        if (since < 0 ? this.brig.remove(player) != null : !Long.valueOf(since).equals(this.brig.put(player, since))) {
            this.setDirty();
        }
    }

    public java.util.Set<UUID> prisoners() {
        return java.util.Collections.unmodifiableSet(this.brig.keySet());
    }

    public void invite(UUID player, UUID ship) {
        this.invites.put(player, ship);
    }

    @Nullable
    public UUID takeInvite(UUID player) {
        return this.invites.remove(player);
    }

    private static Crews load(CompoundTag tag, HolderLookup.Provider registries) {
        Crews crews = new Crews();
        for (Tag t : tag.getList("members", Tag.TAG_COMPOUND)) {
            CompoundTag m = (CompoundTag) t;
            UUID player = NbtUtils.loadUUID(m.get("player"));
            crews.shipOf.put(player, NbtUtils.loadUUID(m.get("ship")));
            if (m.contains("bed")) {
                crews.beds.put(player, m.getLong("bed"));
            }
            if (m.getBoolean("ashore")) {
                crews.ashore.add(player);
            }
            if (m.contains("brig")) {
                crews.brig.put(player, m.getLong("brig"));
            }
        }
        for (Tag t : tag.getList("captains", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            UUID ship = NbtUtils.loadUUID(c.get("ship"));
            crews.captains.put(ship, NbtUtils.loadUUID(c.get("player")));
            if (c.getBoolean("locked")) {
                crews.locked.add(ship);
            }
        }
        return crews;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag members = new ListTag();
        this.shipOf.forEach((player, ship) -> {
            CompoundTag m = new CompoundTag();
            m.put("player", NbtUtils.createUUID(player));
            m.put("ship", NbtUtils.createUUID(ship));
            if (this.beds.containsKey(player)) {
                m.putLong("bed", this.beds.get(player));
            }
            m.putBoolean("ashore", this.ashore.contains(player));
            if (this.brig.containsKey(player)) {
                m.putLong("brig", this.brig.get(player));
            }
            members.add(m);
        });
        tag.put("members", members);
        ListTag captains = new ListTag();
        this.captains.forEach((ship, player) -> {
            CompoundTag c = new CompoundTag();
            c.put("ship", NbtUtils.createUUID(ship));
            c.put("player", NbtUtils.createUUID(player));
            c.putBoolean("locked", this.locked.contains(ship));
            captains.add(c);
        });
        tag.put("captains", captains);
        return tag;
    }
}

package world.bentobox.border.listeners;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;

import world.bentobox.bentobox.api.events.island.IslandProtectionRangeChangeEvent;
import world.bentobox.bentobox.api.metadata.MetaDataValue;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.util.Util;
import world.bentobox.border.Border;

/**
 * Displays a border to a player
 * @author tastybento
 *
 */
public class PlayerBorder implements Listener {

    public static final String BORDER_STATE_META_DATA = "Border_state";
    private static final BlockData BLOCK = Material.BARRIER.createBlockData();
    private final Border addon;
    private static final Particle PARTICLE = Particle.REDSTONE;
    private static final Particle.DustOptions PARTICLE_DUST_RED = new Particle.DustOptions(Color.RED, 1.0F);
    private static final Particle.DustOptions PARTICLE_DUST_BLUE = new Particle.DustOptions(Color.BLUE, 1.0F);
    private static final int BARRIER_RADIUS = 5;
    private final Map<UUID, Set<BarrierBlock>> barrierBlocks = new HashMap<>();


    /**
     * @param addon
     */
    public PlayerBorder(Border addon) {
        super();
        this.addon = addon;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent e) {
        // Remove head movement
        if (!e.getFrom().toVector().equals(e.getTo().toVector())) {
            addon.getIslands().getIslandAt(e.getPlayer().getLocation()).ifPresent(i -> showBarrier(e.getPlayer(), i));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onVehicleMove(VehicleMoveEvent e) {
        // Remove head movement
        if (!e.getFrom().toVector().equals(e.getTo().toVector())) {
            e.getVehicle().getPassengers().stream().filter(en -> en instanceof Player).map(en -> (Player)en).forEach(p ->
            addon.getIslands().getIslandAt(p.getLocation()).ifPresent(i -> showBarrier(p, i)));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onProtectionRangeChange(IslandProtectionRangeChangeEvent e) {
        // Cleans up barrier blocks that were on old range
        e.getIsland().getPlayersOnIsland().forEach(player -> hideBarrier(User.getInstance(player)));
    }

    /**
     * Show the barrier to the player on an island
     * @param player - player to show
     * @param island - island
     */
    public void showBarrier(Player player, Island island) {

        if (addon.getSettings().getDisabledGameModes().contains(island.getGameMode()))
            return;

        if (!User.getInstance(player).getMetaData(BORDER_STATE_META_DATA).map(MetaDataValue::asBoolean).orElse(addon.getSettings().isShowByDefault())) {
            return;
        }

        // Get the locations to show
        Location loc = player.getLocation();
        int xMin = island.getMinProtectedX();
        int xMax = island.getMaxProtectedX();
        int zMin = island.getMinProtectedZ();
        int zMax = island.getMaxProtectedZ();
        int radius = BARRIER_RADIUS;
        if (loc.getBlockX() - xMin < radius) {
            // Close to min x
            for (int z = Math.max(loc.getBlockZ() - radius, zMin); z < loc.getBlockZ() + radius && z < zMax; z++) {
                for (int y = -radius; y < radius; y++) {
                    showPlayer(player, xMin-1, loc.getBlockY() + y, z);
                }
            }
        }
        if (loc.getBlockZ() - zMin < radius) {
            // Close to min z
            for (int x = Math.max(loc.getBlockX() - radius, xMin); x < loc.getBlockX() + radius && x < xMax; x++) {
                for (int y = -radius; y < radius; y++) {
                    showPlayer(player, x, loc.getBlockY() + y, zMin-1);
                }
            }
        }
        if (xMax - loc.getBlockX() < radius) {
            // Close to max x
            for (int z = Math.max(loc.getBlockZ() - radius, zMin); z < loc.getBlockZ() + radius && z < zMax; z++) {
                for (int y = -radius; y < radius; y++) {
                    showPlayer(player, xMax, loc.getBlockY() + y, z); // not xMax+1, that's outside the region
                }
            }
        }
        if (zMax - loc.getBlockZ() < radius) {
            // Close to max z
            for (int x = Math.max(loc.getBlockX() - radius, xMin); x < loc.getBlockX() + radius && x < xMax; x++) {
                for (int y = -radius; y < radius; y++) {
                    showPlayer(player, x, loc.getBlockY() + y, zMax); // not zMax+1, that's outside the region
                }
            }
        }
    }

    private void showPlayer(Player player, int i, int j, int k) {
        // Get if on or in border
        if (addon.getSettings().isUseBarrierBlocks()
                && player.getLocation().getBlockX() == i
                && player.getLocation().getBlockZ() == k) {
            teleportPlayer(player);
            return;
        }
        Location l = new Location(player.getWorld(), i, j, k);
        Util.getChunkAtAsync(l).thenAccept(c -> {
            if (j < 0 || j > player.getWorld().getMaxHeight()) {
                User.getInstance(player).spawnParticle(PARTICLE, PARTICLE_DUST_RED, i + 0.5D, j + 0.0D, k + 0.5D);
            } else {
                User.getInstance(player).spawnParticle(PARTICLE, PARTICLE_DUST_BLUE, i + 0.5D, j + 0.0D, k + 0.5D);
            }
            if (addon.getSettings().isUseBarrierBlocks() && (l.getBlock().isEmpty() || l.getBlock().isLiquid())) {
                player.sendBlockChange(l, BLOCK);
                barrierBlocks.computeIfAbsent(player.getUniqueId(), u -> new HashSet<>()).add(new BarrierBlock(l, l.getBlock().getBlockData()));
            }
        });
    }

    private void teleportPlayer(Player p) {
        addon.getIslands().getIslandAt(p.getLocation()).ifPresent(i -> {
            Vector unitVector = i.getCenter().toVector().subtract(p.getLocation().toVector()).normalize()
                    .multiply(new Vector(1,0,1));
            p.setVelocity(new Vector (0,0,0));
            // Get distance from border

            Location to = p.getLocation().toVector().add(unitVector).toLocation(p.getWorld());
            to.setPitch(p.getLocation().getPitch());
            to.setYaw(p.getLocation().getYaw());
            Util.teleportAsync(p, to, TeleportCause.PLUGIN);
        });
    }

    /**
     * Hide the barrier
     * @param user - user
     */
    public void hideBarrier(User user) {
        if (barrierBlocks.containsKey(user.getUniqueId())) {
            barrierBlocks.get(user.getUniqueId()).stream()
            .filter(v -> v.l.getWorld().equals(user.getWorld()))
            .forEach(v -> {
                user.getPlayer().sendBlockChange(v.l, v.oldBlockData);
            });
            // Clean up
            clearUser(user);
        }
    }

    /**
     * Removes any cached barrier blocks
     * @param user - user
     */
    public void clearUser(User user) {
        barrierBlocks.remove(user.getUniqueId());
    }

    private class BarrierBlock {
        Location l;
        BlockData oldBlockData;
        public BarrierBlock(Location l, BlockData oldBlockData) {
            super();
            this.l = l;
            this.oldBlockData = oldBlockData;
        }

    }

}

package glizz.archer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class Archer extends JavaPlugin implements Listener, TabExecutor {

    private Map<UUID, Long> cooldowns = new HashMap<>();
    private WorldGuardPlugin worldGuard;
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MiPlugin habilitado!");

        worldGuard = (WorldGuardPlugin) getServer().getPluginManager().getPlugin("WorldGuard");
        PluginCommand bardCommand = getCommand("bard");
        if (bardCommand != null) {
            bardCommand.setExecutor(this);
            bardCommand.setTabCompleter(this);
        }

        PluginCommand giveBardCommand = getCommand("givebard");
        if (giveBardCommand != null) {
            giveBardCommand.setExecutor(this);
            giveBardCommand.setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("MiPlugin deshabilitado.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("bard")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Uso incorrecto. /bard <jugador> <cantidad>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);

            if (target == null) {
                sender.sendMessage(ChatColor.RED + "El jugador especificado no está en línea.");
                return true;
            }

            // Verificar si el jugador tiene el permiso para usar el comando /bard
            if (sender instanceof Player && !sender.hasPermission("miplugin.use.bard")) {
                sender.sendMessage(ChatColor.RED + "No tienes permiso para usar este comando.");
                return true;
            }

            Player player = (Player) sender;

            int cantidad;
            try {
                cantidad = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "La cantidad debe ser un número válido.");
                return true;
            }

            // Crear el item personalizado
            ItemStack bardItem = createBardsSword();

            // Verificar si el jugador tiene la armadura de cuero completa
            if (!hasFullLeatherArmor(player)) {
                player.sendMessage(ChatColor.RED + "Debes tener un conjunto completo de armadura de cuero para usar este item.");
                return true;
            }

            // Entregar el item al jugador especificado, uno por uno
            for (int i = 0; i < cantidad; i++) {
                target.getInventory().addItem(bardItem.clone());
            }

            sender.sendMessage(ChatColor.GREEN + "Has entregado " + cantidad + " Bard's Sword(s) a " + target.getName());
            return true;
        } else if (cmd.getName().equalsIgnoreCase("givebard")) {
            if (args.length < 1) {
                sender.sendMessage(ChatColor.RED + "Uso incorrecto. /givebard <jugador> [cantidad]");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);

            if (target == null) {
                sender.sendMessage(ChatColor.RED + "El jugador especificado no está en línea.");
                return true;
            }

            // Verificar si el jugador tiene el permiso para usar el comando /givebard
            if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission("miplugin.use.givebard")) {
                sender.sendMessage(ChatColor.RED + "No tienes permiso para usar este comando.");
                return true;
            }

            int cantidad = 1; // Cantidad predeterminada es 1

            if (args.length > 1) {
                try {
                    cantidad = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "La cantidad debe ser un número válido.");
                    return true;
                }
            }

            // Crear el item personalizado
            ItemStack bardItem = createBardsSword();

            // Entregar el item al jugador especificado, uno por uno
            for (int i = 0; i < cantidad; i++) {
                target.getInventory().addItem(bardItem.clone());
            }

            sender.sendMessage(ChatColor.GREEN + "Has entregado " + cantidad + " Bard's Sword(s) a " + target.getName());
            return true;
        }
        return false;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack clickedItem = event.getItem();

        if (clickedItem != null && clickedItem.getType() == Material.SUGAR && clickedItem.hasItemMeta()) {
            ItemMeta itemMeta = clickedItem.getItemMeta();
            if (itemMeta.getDisplayName().equals(ChatColor.GREEN + "Impulso mágico")) {
                List<String> expectedLores = Arrays.asList(
                        ChatColor.GRAY + "Este item es solo para los archers,",
                        ChatColor.GRAY + "tiempo de espera de 20 Segundos.",
                        ChatColor.BLUE + "Ve por ell@s y ",
                        ChatColor.RED + "no te rindas"
                );
                if (isInProtectedRegion(player)) {
                    player.sendMessage(ChatColor.RED + "No puedes usar este item en esta región.");
                    event.setCancelled(true);
                } else {
                    // Comprobar el cooldown, la armadura, aplicar efectos, etc.

                    // Comprobar si el jugador está en cooldown
                    if (isOnCooldown(player)) {
                        long segundosRestantes = calcularSegundosRestantes(player);
                        player.sendMessage(ChatColor.RED + "Debes esperar " + segundosRestantes + " segundos antes de usar este item nuevamente.");
                        event.setCancelled(true); // Cancelar el uso del ítem.
                        return; // Detener la ejecución del método aquí
                    }

                    // Verificar si el jugador tiene la armadura de cuero completa
                    if (!hasFullLeatherArmor(player)) {
                        player.sendMessage(ChatColor.RED + "Debes tener un conjunto completo de armadura de cuero para usar este item.");
                        event.setCancelled(true); // Cancelar el uso del ítem.
                        return; // Detener la ejecución del método aquí
                    }

                    // Aplicar el efecto de velocidad 3 durante 5 segundos al hacer clic derecho
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 5, 2));

                    // Consumir uno de los ítems en la mano
                    if (clickedItem.getAmount() > 1) {
                        clickedItem.setAmount(clickedItem.getAmount() - 1);
                    } else {
                        player.getInventory().remove(clickedItem);
                    }

                    // Establecer el cooldown después de usar el item
                    setCooldown(player, 20); // 60 segundos de cooldown
                }
            }
        }
    }
    private long calcularSegundosRestantes(Player player) {
        UUID playerUUID = player.getUniqueId();
        if (cooldowns.containsKey(playerUUID)) {
            long cooldownEndTime = cooldowns.get(playerUUID);
            long segundosRestantes = (cooldownEndTime - System.currentTimeMillis()) / 1000L;
            return Math.max(segundosRestantes, 0);
        }
        return 0;
    }
    private boolean isInProtectedRegion(Player player) {
        if (worldGuard == null || player == null || player.getWorld() == null) {
            return false;
        }

        ApplicableRegionSet regions = worldGuard.getRegionManager(player.getWorld()).getApplicableRegions(player.getLocation());

        for (ProtectedRegion region : regions) {
            // Puedes agregar más nombres de regiones a la lista según sea necesario
            if (region.getId().equalsIgnoreCase("spawn-nopvp")) {
                return true;
            }
        }

        return false;
    }
    private ItemStack createBardsSword() {
        // Crear un item personalizado (espada) con nombre y lore personalizados
        ItemStack bardItem = new ItemStack(Material.SUGAR);
        ItemMeta itemMeta = bardItem.getItemMeta();
        itemMeta.setDisplayName(ChatColor.GREEN + "Impulso mágico");

        // Crear una lista de lores adicionales
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Este item es solo para los archers,");
        lore.add(ChatColor.GRAY + "tiempo de espera de 20 Segundos.");

        // Agregar líneas adicionales al lore
        lore.add(ChatColor.BLUE + "Ve por ell@s y");
        lore.add(ChatColor.RED + "no te rindas");

        // Establecer el nuevo lore en el ítem
        itemMeta.setLore(lore);

        bardItem.setItemMeta(itemMeta);

        return bardItem;
    }

    private boolean isOnCooldown(Player player) {
        UUID playerUUID = player.getUniqueId();
        if (cooldowns.containsKey(playerUUID)) {
            long cooldownEndTime = cooldowns.get(playerUUID);
            return System.currentTimeMillis() < cooldownEndTime;
        }
        return false;
    }

    private void setCooldown(Player player, int seconds) {
        UUID playerUUID = player.getUniqueId();
        long cooldownEndTime = System.currentTimeMillis() + (seconds * 1000L);
        cooldowns.put(playerUUID, cooldownEndTime);
    }

    private boolean hasFullLeatherArmor(Player player) {
        ItemStack[] armorContents = player.getInventory().getArmorContents();

        // Verificar si hay cuatro piezas de armadura de cuero en el inventario del jugador.
        int leatherArmorCount = 0;
        for (ItemStack armorPiece : armorContents) {
            if (armorPiece != null && armorPiece.getType() == Material.LEATHER_HELMET
                    && armorPiece.getEnchantments().containsKey(Enchantment.PROTECTION_ENVIRONMENTAL)
                    && armorPiece.getEnchantmentLevel(Enchantment.PROTECTION_ENVIRONMENTAL) >= 1
                    && armorPiece.getEnchantments().containsKey(Enchantment.DURABILITY)
                    && armorPiece.getEnchantmentLevel(Enchantment.DURABILITY) >= 1) {
                leatherArmorCount++;
            }
            if (armorPiece != null && armorPiece.getType() == Material.LEATHER_CHESTPLATE
                    && armorPiece.getEnchantments().containsKey(Enchantment.PROTECTION_ENVIRONMENTAL)
                    && armorPiece.getEnchantmentLevel(Enchantment.PROTECTION_ENVIRONMENTAL) >= 1
                    && armorPiece.getEnchantments().containsKey(Enchantment.DURABILITY)
                    && armorPiece.getEnchantmentLevel(Enchantment.DURABILITY) >= 1) {
                leatherArmorCount++;
            }
            if (armorPiece != null && armorPiece.getType() == Material.LEATHER_LEGGINGS
                    && armorPiece.getEnchantments().containsKey(Enchantment.PROTECTION_ENVIRONMENTAL)
                    && armorPiece.getEnchantmentLevel(Enchantment.PROTECTION_ENVIRONMENTAL) >= 1
                    && armorPiece.getEnchantments().containsKey(Enchantment.DURABILITY)
                    && armorPiece.getEnchantmentLevel(Enchantment.DURABILITY) >= 1) {
                leatherArmorCount++;
            }
            if (armorPiece != null && armorPiece.getType() == Material.LEATHER_BOOTS
                    && armorPiece.getEnchantments().containsKey(Enchantment.PROTECTION_ENVIRONMENTAL)
                    && armorPiece.getEnchantmentLevel(Enchantment.PROTECTION_ENVIRONMENTAL) >= 1
                    && armorPiece.getEnchantments().containsKey(Enchantment.DURABILITY)
                    && armorPiece.getEnchantmentLevel(Enchantment.DURABILITY) >= 1) {
                leatherArmorCount++;
            }
        }

        return leatherArmorCount >= 4; // El jugador debe tener al menos cuatro piezas de armadura de cuero.
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (cmd.getName().equalsIgnoreCase("bard") && args.length == 1 && sender instanceof ConsoleCommandSender) {
            // Si el primer argumento está vacío y el comando se ejecuta desde la consola,
            // proporciona una lista de jugadores en línea como sugerencia.
            return getPlayerNames();
        }
        return null;
    }

    private List<String> getPlayerNames() {
        List<String> playerNames = new java.util.ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerNames.add(player.getName());
        }
        return playerNames;
    }
}
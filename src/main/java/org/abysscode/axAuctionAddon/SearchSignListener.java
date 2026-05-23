package org.abysscode.axAuctionAddon;

import de.rapha149.signgui.SignGUI;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;

public class SearchSignListener implements Listener {

    private final AxAuctionAddon plugin;
    private boolean debugMode;

    public SearchSignListener(AxAuctionAddon plugin) {
        this.plugin = plugin;
        this.debugMode = plugin.getConfig().getBoolean("debug", true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        // Check if player clicked in an inventory
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        // Check if item exists
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // Check if item has meta
        if (!clickedItem.hasItemMeta()) {
            return;
        }

        ItemMeta meta = clickedItem.getItemMeta();
        if (!meta.hasDisplayName()) {
            return;
        }

        // Get clean display name (without color codes and emoji)
        String cleanDisplayName = stripColorCodes(meta.getDisplayName());

        // Get inventory title
        String inventoryTitle = event.getView().getTitle();
        String cleanInventoryTitle = stripColorCodes(inventoryTitle);

        // Debug logging
        if (debugMode) {
            plugin.getLogger().info("=== Click Debug ===");
            plugin.getLogger().info("Inventory (raw): " + inventoryTitle);
            plugin.getLogger().info("Inventory (clean): " + cleanInventoryTitle);
            plugin.getLogger().info("Material: " + clickedItem.getType());
            plugin.getLogger().info("Display Name (raw): " + meta.getDisplayName());
            plugin.getLogger().info("Display Name (clean): " + cleanDisplayName);
            plugin.getLogger().info("Slot: " + event.getSlot());
        }

        // Check all configured search triggers
        ConfigurationSection triggers = plugin.getConfig().getConfigurationSection("search-triggers");
        if (triggers == null) {
            if (debugMode) {
                plugin.getLogger().warning("No search-triggers configured!");
            }
            return;
        }

        // Iterate through all configured systems
        for (String systemName : triggers.getKeys(false)) {
            ConfigurationSection system = triggers.getConfigurationSection(systemName);
            if (system == null || !system.getBoolean("enabled", false)) {
                continue;
            }

            // Get configuration
            String configItemType = system.getString("item-type", "OAK_SIGN").toUpperCase();
            String configDisplayName = stripColorCodes(system.getString("display-name", ""));
            String configInventoryTitle = stripColorCodes(system.getString("inventory-title", ""));
            String command = system.getString("command", "");

            // Check if inventory title matches (if configured)
            boolean inventoryMatches = true; // Default: don't check
            if (!configInventoryTitle.isEmpty()) {
                inventoryMatches = cleanInventoryTitle.toLowerCase().contains(configInventoryTitle.toLowerCase());
            }

            // Check if material matches
            boolean materialMatches = clickedItem.getType().name().equals(configItemType);

            // Check if display name CONTAINS the configured text (case-insensitive)
            // This allows partial matching, e.g. "Wyszukaj" will match "🔍 Wyszukaj przedmiot"
            boolean nameMatches = cleanDisplayName.toLowerCase().contains(configDisplayName.toLowerCase());

            if (debugMode) {
                plugin.getLogger().info("--- Checking system: " + systemName + " ---");
                if (!configInventoryTitle.isEmpty()) {
                    plugin.getLogger().info("  Expected Inventory Title: " + configInventoryTitle);
                    plugin.getLogger().info("  Actual Inventory Title: " + cleanInventoryTitle);
                    plugin.getLogger().info("  Inventory Match (contains): " + inventoryMatches);
                }
                plugin.getLogger().info("  Expected Material: " + configItemType);
                plugin.getLogger().info("  Actual Material: " + clickedItem.getType().name());
                plugin.getLogger().info("  Material Match: " + materialMatches);
                plugin.getLogger().info("  Expected Name: " + configDisplayName);
                plugin.getLogger().info("  Actual Name: " + cleanDisplayName);
                plugin.getLogger().info("  Name Match (contains): " + nameMatches);
                plugin.getLogger().info("  Command: " + command);
            }

            // If all conditions match, trigger SignGUI
            if (inventoryMatches && materialMatches && nameMatches) {
                if (debugMode) {
                    plugin.getLogger().info("✓ MATCH FOUND! Opening SignGUI for system: " + systemName);
                }

                // Cancel the default action
                event.setCancelled(true);

                // Close the current inventory
                player.closeInventory();

                // Open SignGUI with a small delay
                String finalCommand = command;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    openSearchSignGUI(player, finalCommand);
                }, 3L);

                // Only trigger once
                return;
            }
        }

        if (debugMode) {
            plugin.getLogger().info("✗ No matching system found");
        }
    }

    private String stripColorCodes(String text) {
        if (text == null) return "";
        // Remove § color codes, & color codes, hex colors, and emoji/special chars
        return text.replaceAll("§[0-9a-fk-or]", "")
                   .replaceAll("&[0-9a-fk-or]", "")
                   .replaceAll("§x(§[0-9a-f]){6}", "")
                   .replaceAll("&x(&[0-9a-f]){6}", "")
                   .replaceAll("[^\\p{L}\\p{N}\\s]", "") // Remove emoji and special characters
                   .trim();
    }

    public void openSearchSignGUI(Player player, String command) {
        // Get lines from config with color code translation
        String line1 = translateColors(plugin.getConfig().getString("sign-gui.line-1", "§6Wpisz nazwe"));
        String line2 = translateColors(plugin.getConfig().getString("sign-gui.line-2", "§7przedmiotu"));
        String line3 = translateColors(plugin.getConfig().getString("sign-gui.line-3", ""));
        String line4 = translateColors(plugin.getConfig().getString("sign-gui.line-4", ""));

        try {
            SignGUI gui = SignGUI.builder()
                    .setType(Material.OAK_SIGN)
                    .setLine(0, line1)
                    .setLine(1, line2)
                    .setLine(2, line3)
                    .setLine(3, line4)
                    .setHandler((p, result) -> {
                        String searchText = result.getLine(0);

                        // Check if text is empty or just contains placeholder text
                        if (searchText != null && !searchText.trim().isEmpty()) {
                            // Clean the text
                            searchText = stripColorCodes(searchText)
                                    .replace("Wpisz nazwe", "")
                                    .replace("przedmiotu", "")
                                    .trim();

                            if (!searchText.isEmpty()) {
                                // Execute the search command
                                plugin.getLogger().info("Player " + p.getName() + " searching for: " + searchText);

                                // Replace %query% placeholder with actual search text
                                final String finalCommand = command.replace("%query%", searchText);

                                // Schedule command execution to avoid timing issues
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    p.performCommand(finalCommand);
                                });

                                // Send message from config
                                String message = translateColors(
                                        plugin.getConfig().getString("messages.searching", "&aWyszukiwanie: &e%query%")
                                                .replace("%query%", searchText)
                                );
                                p.sendMessage(message);
                            } else {
                                // Empty text after cleaning - cancel search
                                String cancelMsg = translateColors(
                                        plugin.getConfig().getString("messages.cancelled", "&7Anulowano wyszukiwanie.")
                                );
                                p.sendMessage(cancelMsg);
                            }
                        } else {
                            // No text entered - cancel search
                            String cancelMsg = translateColors(
                                    plugin.getConfig().getString("messages.cancelled", "&7Anulowano wyszukiwanie.")
                            );
                            p.sendMessage(cancelMsg);
                        }

                        return Collections.emptyList();
                    })
                    .build();

            gui.open(player);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to open SignGUI: " + e.getMessage());
            player.sendMessage("§cBłąd: SignGUI nie wspiera tej wersji Minecraft!");
            player.sendMessage("§7Skontaktuj się z administratorem.");
        }
    }

    private String translateColors(String text) {
        if (text == null) return "";
        return text.replace("&", "§");
    }

    public void setDebugMode(boolean enabled) {
        this.debugMode = enabled;
    }

    public boolean isDebugEnabled() {
        return this.debugMode;
    }
}


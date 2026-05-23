package org.abysscode.axAuctionAddon;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

public final class AxAuctionAddon extends JavaPlugin {

    private SearchSignListener searchSignListener;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Initialize and register listener
        searchSignListener = new SearchSignListener(this);
        getServer().getPluginManager().registerEvents(searchSignListener, this);

        // Set debug mode from config
        searchSignListener.setDebugMode(getConfig().getBoolean("debug", true));

        // Plugin startup logic
        getLogger().info("AxAuctionAddon has been enabled!");
        getLogger().info("SignGUI integration active.");
        getLogger().info("Search sign listener registered - click search signs in AxAuctions GUI!");
        getLogger().info("Debug mode: " + (searchSignListener.isDebugEnabled() ? "ENABLED" : "DISABLED"));
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("AxAuctionAddon has been disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("axsearch")) {
            // Handle debug subcommand
            if (args.length > 0 && args[0].equalsIgnoreCase("debug")) {
                if (!sender.hasPermission("axauctionaddon.debug")) {
                    sender.sendMessage("§cBrak uprawnień!");
                    return true;
                }

                boolean newState = !searchSignListener.isDebugEnabled();
                searchSignListener.setDebugMode(newState);
                sender.sendMessage("§aDebug mode: §e" + (newState ? "ENABLED" : "DISABLED"));
                sender.sendMessage("§7Kliknij tabliczkę w GUI aby zobaczyć logi w konsoli");
                return true;
            }

            // Check if sender is a player
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cThis command can only be used by players!");
                return true;
            }

            Player player = (Player) sender;

            // Get first enabled system command
            String defaultCommand = getFirstEnabledSystemCommand();
            if (defaultCommand == null) {
                player.sendMessage("§cNo search system is enabled in config.yml!");
                return true;
            }

            // Use the listener's method to open the sign GUI
            searchSignListener.openSearchSignGUI(player, defaultCommand);

            return true;
        }

        return false;
    }

    private String getFirstEnabledSystemCommand() {
        var triggers = getConfig().getConfigurationSection("search-triggers");
        if (triggers == null) return null;

        for (String systemName : triggers.getKeys(false)) {
            var system = triggers.getConfigurationSection(systemName);
            if (system != null && system.getBoolean("enabled", false)) {
                return system.getString("command", "ah search %query%");
            }
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("axsearch")) {
            if (args.length == 1 && sender.hasPermission("axauctionaddon.debug")) {
                return Collections.singletonList("debug");
            }
            return Collections.emptyList();
        }
        return null;
    }
}

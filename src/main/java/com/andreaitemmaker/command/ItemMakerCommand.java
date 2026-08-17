package com.andreaitemmaker.command;

import com.andreaitemmaker.AndreaitemmakerPlugin;
import com.andreaitemmaker.api.CustomItem;
import com.andreaitemmaker.util.Chat;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /andreaitemmaker (aliases: aitem, itemmaker) — admin commands. */
public final class ItemMakerCommand implements CommandExecutor, TabCompleter {

    private final AndreaitemmakerPlugin plugin;

    public ItemMakerCommand(AndreaitemmakerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> help(sender, label);
            case "give" -> give(sender, args);
            case "list" -> list(sender, args);
            case "info" -> info(sender, args);
            case "reload" -> reload(sender);
            case "pack" -> pack(sender, args);
            default -> help(sender, label);
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("andreaitemmaker.give") && !sender.hasPermission("andreaitemmaker.admin")) {
            sender.sendMessage(Chat.color("&cYou don't have permission to do that."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Chat.color("&cUsage: /andreaitemmaker give <id> [amount] [player]"));
            return;
        }
        CustomItem item = plugin.getContentRegistry().getItem(args[1]);
        if (item == null) {
            sender.sendMessage(Chat.color("&cUnknown item '&f" + args[1] + "&c'."));
            return;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException e) {
                sender.sendMessage(Chat.color("&cInvalid amount."));
                return;
            }
        }
        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                sender.sendMessage(Chat.color("&cPlayer '&f" + args[3] + "&c' is not online."));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(Chat.color("&cSpecify a player when running from the console."));
            return;
        }
        target.getInventory().addItem(plugin.getItemFactory().build(item, amount));
        target.sendMessage(Chat.color("&aReceived &f" + item.getDisplayName() + "&a x" + amount + "."));
        if (!target.equals(sender)) {
            sender.sendMessage(Chat.color("&aGave &f" + amount + "x " + item.getId() + " &ato " + target.getName() + "."));
        }
    }

    private void list(CommandSender sender, String[] args) {
        String type = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "all";
        List<CustomItem> items = switch (type) {
            case "items", "item" -> plugin.getContentRegistry().getItems();
            case "weapons", "weapon" -> plugin.getContentRegistry().getWeapons();
            case "armor" -> plugin.getContentRegistry().getArmor();
            case "food" -> plugin.getContentRegistry().getFood();
            case "blocks", "block" -> plugin.getContentRegistry().getBlocks().stream()
                    .map(c -> (CustomItem) c).toList();
            case "furniture" -> plugin.getContentRegistry().getFurnitures().stream()
                    .map(c -> (CustomItem) c).toList();
            default -> plugin.getContentRegistry().getAll().stream().toList();
        };
        if (items.isEmpty()) {
            sender.sendMessage(Chat.color("&eNo content of that type."));
            return;
        }
        sender.sendMessage(Chat.color("&b" + items.size() + " &fcontent entries (" + type + "):"));
        for (CustomItem item : items) {
            sender.sendMessage(Chat.color(" &8- &f" + item.getId() + " &8(" + item.getType().name().toLowerCase()
                    + ", " + item.getMaterial().name().toLowerCase() + ")"));
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Chat.color("&cUsage: /andreaitemmaker info <id>"));
            return;
        }
        CustomItem item = plugin.getContentRegistry().getItem(args[1]);
        if (item == null) {
            sender.sendMessage(Chat.color("&cUnknown item '&f" + args[1] + "&c'."));
            return;
        }
        sender.sendMessage(Chat.color("&b" + item.getId() + "&f (" + item.getType().name().toLowerCase() + ")"));
        sender.sendMessage(Chat.color(" &8- &fmaterial: &7" + item.getMaterial().name().toLowerCase()));
        sender.sendMessage(Chat.color(" &8- &fdisplay name: &7" + item.getDisplayName()));
        if (!item.getAttributes().isEmpty()) {
            sender.sendMessage(Chat.color(" &8- &fattributes: &7" + item.getAttributes()));
        }
        if (!item.getMechanics().isEmpty()) {
            sender.sendMessage(Chat.color(" &8- &fmechanics: &7" + String.join(", ", item.getMechanics().keySet())));
        }
        sender.sendMessage(Chat.color(" &8- &fpack: &7" + (plugin.getPackManager().isGenerated()
                ? "format " + plugin.getPackManager().getFormat() : "not generated")));
    }

    private void reload(CommandSender sender) {
        sender.sendMessage(Chat.color("&eReloading Andreaitemmaker..."));
        try {
            plugin.reloadAll();
            sender.sendMessage(Chat.color("&aReload complete. &f" + plugin.getContentRegistry().getAll().size()
                    + " content entries, pack " + (plugin.getPackManager().isGenerated() ? "generated" : "FAILED") + "."));
        } catch (Exception e) {
            plugin.getLogger().severe("Reload failed: " + e);
            sender.sendMessage(Chat.color("&cReload failed, see console."));
        }
    }

    private void pack(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Chat.color("&cUsage: /andreaitemmaker pack <send [player|all]|url|regenerate>"));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "url" -> {
                sender.sendMessage(Chat.color("&fPack URL: &b" + plugin.getPackManager().getUrl()));
                sender.sendMessage(Chat.color("&fUnzipped pack folder: &7" + plugin.getPackManager().getPackFolder().getPath()
                        + " (host it anywhere and set pack.public-url)"));
            }
            case "regenerate" -> {
                boolean ok = plugin.getPackManager().generate();
                sender.sendMessage(Chat.color(ok ? "&aPack regenerated." : "&cPack generation failed, see console."));
                if (ok) {
                    plugin.getPackManager().sendToAll();
                }
            }
            case "send" -> {
                if (args.length >= 3 && !args[2].equalsIgnoreCase("all")) {
                    Player player = Bukkit.getPlayerExact(args[2]);
                    if (player == null) {
                        sender.sendMessage(Chat.color("&cPlayer not online."));
                        return;
                    }
                    plugin.getPackManager().sendTo(player);
                    sender.sendMessage(Chat.color("&aPack sent to " + player.getName() + "."));
                } else {
                    plugin.getPackManager().sendToAll();
                    sender.sendMessage(Chat.color("&aPack sent to all online players."));
                }
            }
            default -> sender.sendMessage(Chat.color("&cUnknown pack subcommand."));
        }
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(Chat.color("&bAndreaitemmaker commands:"));
        sender.sendMessage(Chat.color(" &f/" + label + " give <id> [amount] [player] &8- give a custom item"));
        sender.sendMessage(Chat.color(" &f/" + label + " list [items|weapons|armor|food|blocks|furniture]"));
        sender.sendMessage(Chat.color(" &f/" + label + " info <id>"));
        sender.sendMessage(Chat.color(" &f/" + label + " pack send [player|all] &8- send the resource pack"));
        sender.sendMessage(Chat.color(" &f/" + label + " pack url &8- show the pack download URL + folder path"));
        sender.sendMessage(Chat.color(" &f/" + label + " pack regenerate"));
        sender.sendMessage(Chat.color(" &f/" + label + " reload"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : new String[]{"give", "list", "info", "pack", "reload", "help"}) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("info"))) {
            for (CustomItem item : plugin.getContentRegistry().getAll()) {
                if (item.getId().startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(item.getId());
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            for (String s : new String[]{"items", "weapons", "armor", "food", "blocks", "furniture"}) {
                if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("pack")) {
            for (String s : new String[]{"send", "url", "regenerate"}) {
                if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("pack") && args[1].equalsIgnoreCase("send")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().startsWith(args[2])) {
                    out.add(p.getName());
                }
            }
            out.add("all");
        }
        return out;
    }
}

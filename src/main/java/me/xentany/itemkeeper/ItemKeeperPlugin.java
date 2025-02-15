package me.xentany.itemkeeper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.commons.lang.math.NumberUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public final class ItemKeeperPlugin extends JavaPlugin {

  private static ItemKeeperPlugin instance;

  private Map<String, ItemStack> items;
  private Path itemsPath;
  private FileConfiguration itemsConfig;

  @Override
  public void onEnable() {
    instance = this;

    this.items = new HashMap<>();
    this.itemsPath = this.getDataFolder().toPath().resolve("items.yml");

    try {
      Files.createDirectories(this.itemsPath.getParent());

      if (Files.notExists(this.itemsPath)) {
        Files.createFile(this.itemsPath);
      }
    } catch (final IOException e) {
      this.getLogger().severe("Failed to create items.yml: " + e.getMessage());
      return;
    }

    this.itemsConfig = YamlConfiguration.loadConfiguration(this.itemsPath.toFile());
    this.itemsConfig.getKeys(false).forEach(key -> {
      var itemStack = this.itemsConfig.getItemStack(key);

      if (itemStack != null) {
        this.items.put(key, itemStack);
      }
    });
  }

  @Override
  public boolean onCommand(final @NotNull CommandSender sender,
                           final @NotNull Command command,
                           final @NotNull String label,
                           final String @NotNull [] arguments) {
    if (arguments.length < 2) {
      sender.sendMessage(Component
              .text(String.format("Используйте: /%s (save/give/delete) (название)", label))
              .color(NamedTextColor.YELLOW));
      return true;
    }

    var action = arguments[0].toLowerCase(Locale.ROOT);
    var itemName = arguments[1];

    switch (action) {
      case "delete" -> {
        if (this.items.remove(itemName) != null) {
          this.saveItems();

          sender.sendMessage(Component
                  .text("Предмет был успешно выгружен и удалён!")
                  .color(NamedTextColor.GREEN));
        } else {
          sender.sendMessage(Component
                  .text("Предмет не был найден.")
                  .color(NamedTextColor.RED));
        }
      }

      case "give" -> {
        if (arguments.length < 3) {
          sender.sendMessage(Component
                  .text(String.format("Используйте: /%s give (название) [количество]", label))
                  .color(NamedTextColor.YELLOW));
          return true;
        }

        var targetPlayerName = arguments[2];
        int amount = NumberUtils.toInt(arguments.length > 3 ? arguments[3] : "1", 1);

        if (!this.items.containsKey(itemName)) {
          sender.sendMessage(Component
                  .text("Предмет не был найден.")
                  .color(NamedTextColor.RED));
          return true;
        }

        var targetPlayer = Bukkit.getPlayer(targetPlayerName);

        if (targetPlayer == null) {
          sender.sendMessage(Component
                  .text("Игрок не был найден.")
                  .color(NamedTextColor.RED));
          return true;
        }

        var itemToGive = this.items.get(itemName).clone();

        itemToGive.setAmount(amount);

        var leftovers = targetPlayer.getInventory().addItem(itemToGive);
        int givenAmount = amount - (leftovers.isEmpty() ? 0 : leftovers.values().iterator().next().getAmount());

        sender.sendMessage(Component
                .text(String.format("Предмет %s был выдан игроку %s в количестве %d%s",
                        itemName,
                        targetPlayerName,
                        givenAmount,
                        leftovers.isEmpty() ? "" : String.format(", выпало %d", amount - givenAmount)
                ))
                .color(NamedTextColor.GREEN));

        if (leftovers.isEmpty()) {
          return true;
        }

        leftovers.values().forEach(leftover ->
                targetPlayer.getWorld().dropItem(targetPlayer.getLocation(), leftover)
        );
      }

      case "save" -> {
        if (!(sender instanceof final Player player)) {
          return true;
        }

        var itemInMainHand = player.getInventory().getItemInMainHand().clone();

        if (itemInMainHand.getType().isAir()) {
          sender.sendMessage(Component
                  .text("Предмет не может быть воздухом.")
                  .color(NamedTextColor.YELLOW));
          return true;
        }

        if (this.items.get(itemName) != null) {
          sender.sendMessage(Component
                  .text("Предмет с таким названием уже существует.")
                  .color(NamedTextColor.RED));
          return true;
        }

        itemInMainHand.setAmount(1);

        this.items.put(itemName, itemInMainHand);
        this.saveItems();

        sender.sendMessage(Component
                .text("Предмет был успешно загружен и сохранён!")
                .color(NamedTextColor.GREEN));
      }

      default -> sender.sendMessage(Component
              .text("Неизвестная подкоманда.")
              .color(NamedTextColor.RED));
    }

    return true;
  }

  @Override
  public @NotNull List<String> onTabComplete(final @NotNull CommandSender sender,
                                             final @NotNull Command command,
                                             final @NotNull String alias,
                                             final @NotNull String @NotNull [] arguments) {
    return arguments.length == 1
            ? StringUtil.copyPartialMatches(arguments[0], List.of("save", "give", "delete"), new ArrayList<>())
            : arguments.length == 2 && arguments[0].equalsIgnoreCase("save")
            ? StringUtil.copyPartialMatches(arguments[1], List.of("<название>"), new ArrayList<>())
            : arguments.length == 2 && (arguments[0].equalsIgnoreCase("give") || arguments[0].equalsIgnoreCase("delete"))
            ? StringUtil.copyPartialMatches(arguments[1], new ArrayList<>(this.items.keySet()), new ArrayList<>())
            : arguments.length == 3 && arguments[0].equalsIgnoreCase("give")
            ? StringUtil.copyPartialMatches(arguments[2], Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .toList(), new ArrayList<>())
            : arguments.length == 4 && arguments[0].equalsIgnoreCase("give")
            ? StringUtil.copyPartialMatches(arguments[3], List.of("<количество>"), new ArrayList<>())
            : List.of();
  }

  private void saveItems() {
    var newItemsConfig = new YamlConfiguration();

    this.items.forEach(newItemsConfig::set);

    try {
      Files.createDirectories(this.itemsPath.getParent());
      Files.writeString(this.itemsPath, newItemsConfig.saveToString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    } catch (final IOException e) {
      this.getLogger().severe("Failed to save items.yml: " + e.getMessage());
    }
  }

  public static ItemKeeperPlugin getInstance() {
    return instance;
  }
}
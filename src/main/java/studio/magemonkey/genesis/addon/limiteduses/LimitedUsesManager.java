package studio.magemonkey.genesis.addon.limiteduses;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import studio.magemonkey.genesis.Genesis;
import studio.magemonkey.genesis.api.GenesisAddonStorage;
import studio.magemonkey.genesis.core.GenesisBuy;
import studio.magemonkey.genesis.core.GenesisShop;
import studio.magemonkey.genesis.managers.ClassManager;

import java.util.*;

public class LimitedUsesManager {
    private final LimitedUses                 plugin;
    private final GenesisAddonStorage         storage;
    private final HashMap<UUID, List<String>> uses      = new HashMap<>(); //includes cooldowns
    private final HashMap<UUID, List<String>> cooldowns = new HashMap<>();

    public LimitedUsesManager(LimitedUses plugin) {
        this.plugin = plugin;
        storage = plugin.createStorage(plugin, "used");
        if (storage.containsPath("players")) {
            Genesis.log("[LimitedUses] Seems like you are using an old storage type. Quickly converting your file!");
            for (String key : storage.listKeys("players", true)) { //Convert storage
                List<String> uses_list = storage.getStringList("players." + key);
                if (uses_list != null) {
                    List<String> new_uses_list = new ArrayList<>();
                    for (String entry : uses_list) {
                        new_uses_list.add(entry.replace("-", ":"));
                    }

                    storage.set("uses." + key, new_uses_list);
                }
            }
            storage.deleteAll("players");
            storage.save();
            Genesis.log("[LimitedUses] Finished converting!");
        }
    }


    public void save() {
        storage.save();
    }


    public void resetAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            savePlayer(p, false);
        }
        GenesisAddonStorage backup =
                plugin.createStorage(plugin, "used_backup_" + new Date().toString().replaceAll(":", "_"));
        backup.pasteContentFrom(storage);
        backup.save();
        uses.clear();
        cooldowns.clear();
        storage.deleteAll("uses");
        storage.deleteAll("cooldowns");
        storage.save();
    }

    public void resetPlayer(Player p) {
        boolean b = false;
        if (uses.containsKey(p.getUniqueId())) {
            storage.deleteAll("uses." + p.getUniqueId());
            uses.remove(p.getUniqueId());
            b = true;
        }
        if (cooldowns.containsKey(p.getUniqueId())) {
            storage.deleteAll("cooldowns." + p.getUniqueId());
            cooldowns.remove(p.getUniqueId());
            b = true;
        }

        if (b) {
            storage.save();
        }
    }

    public void resetShopItem(OfflinePlayer p, GenesisShop shop, GenesisBuy buy) {
        resetValue(p, shop, buy, cooldowns);
        resetValue(p, shop, buy, uses);
    }


    public void loadPlayer(OfflinePlayer player) {
        loadPlayer(player,
                storage.getStringList("uses." + player.getUniqueId()),
                storage.getStringList("cooldowns." + player.getUniqueId()));
    }

    private void loadPlayer(OfflinePlayer player, List<String> uses, List<String> cooldowns) {
        if (uses != null & !uses.isEmpty()) {
            this.uses.put(player.getUniqueId(), uses);
        }
        if (cooldowns != null & !cooldowns.isEmpty()) {
            this.cooldowns.put(player.getUniqueId(), cooldowns);
        }
    }

    public void savePlayer(OfflinePlayer player, boolean saveStorage) {
        boolean b = false;

        if (uses.containsKey(player.getUniqueId())) {
            storage.set("uses." + player.getUniqueId(), uses.get(player.getUniqueId()));
            b = true;
        }
        if (cooldowns.containsKey(player.getUniqueId())) {
            storage.set("cooldowns." + player.getUniqueId(), cooldowns.get(player.getUniqueId()));
            b = true;
        }

        if (b && saveStorage) {
            storage.save();
        }
    }

    public void unloadPlayer(OfflinePlayer player, boolean savePlayer, boolean saveStorage) {
        if (savePlayer) {
            savePlayer(player, saveStorage);
        }
        uses.remove(player.getUniqueId());
        cooldowns.remove(player.getUniqueId());
    }


    public void unloadAll() {
        uses.clear();
        cooldowns.clear();
    }


    public long detectUsedAmount(OfflinePlayer p, String tag) {
        return detectValue(p, tag, uses, 0);
    }

    public long detectUsedAmount(OfflinePlayer p, GenesisShop shop, GenesisBuy buy) { //in Uses
        return detectValue(p, shop, buy, uses, 0);
    }

    public long detectLastUseDelay(OfflinePlayer p, String tag) { //in MS
        long value = detectLastUseTime(p, tag);
        return System.currentTimeMillis() - value;
    }

    public long detectLastUseDelay(OfflinePlayer p, GenesisShop shop, GenesisBuy buy) { //in MS
        long value = detectLastUseTime(p, shop, buy);
        return System.currentTimeMillis() - value;
    }

    public long detectLastUseTime(OfflinePlayer p, String tag) { //in MS
        return detectValue(p, tag, cooldowns, -1);
    }

    public long detectLastUseTime(OfflinePlayer p, GenesisShop shop, GenesisBuy buy) { //in MS
        return detectValue(p, shop, buy, cooldowns, -1);
    }

    public long detectValue(OfflinePlayer p,
                            GenesisShop shop,
                            GenesisBuy buy,
                            HashMap<UUID, List<String>> map,
                            long def) {
        return detectValue(p, createTag(shop, buy), map, def);
    }

    public long detectValue(OfflinePlayer p, String tag, HashMap<UUID, List<String>> map, long def) {
        if (map.containsKey(p.getUniqueId())) {
            List<String> used = map.get(p.getUniqueId());
            for (String entry : used) {
                if (entry.startsWith(tag)) {
                    try {
                        String value = entry.replace(tag + ":", "");
                        return Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        return def;
                    }
                }
            }
        }

        return def;
    }

    public boolean resetValue(OfflinePlayer p, GenesisShop shop, GenesisBuy buy, HashMap<UUID, List<String>> map) {
        if (map.containsKey(p.getUniqueId())) {
            String       tag  = createTag(shop, buy);
            List<String> used = map.get(p.getUniqueId());

            for (String entry : used) {
                if (entry.startsWith(tag)) {
                    progressValue(p, shop, buy, map, 0);
                    return true;
                }
            }

        }
        return false;
    }

    public void progressUse(OfflinePlayer p, GenesisShop shop, GenesisBuy buy) {
        long value = detectUsedAmount(p, shop, buy);
        progressValue(p, shop, buy, uses, value + 1);
    }

    public void progressCooldown(OfflinePlayer p, GenesisShop shop, GenesisBuy buy) {
        progressValue(p, shop, buy, cooldowns, System.currentTimeMillis());
    }

    public void setUses(OfflinePlayer p, GenesisShop shop, GenesisBuy buy, long value) {
        progressValue(p, shop, buy, uses, value);
    }


    public void progressValue(OfflinePlayer p,
                              GenesisShop shop,
                              GenesisBuy buy,
                              HashMap<UUID, List<String>> map,
                              long value) {
        if (!map.containsKey(p.getUniqueId())) {
            map.put(p.getUniqueId(), new ArrayList<>());
        }

        String       tag       = createTag(shop, buy);
        List<String> list      = map.get(p.getUniqueId());
        String       to_remove = null;
        String       to_add    = tag + ":" + value;
        for (String entry : list) {
            if (entry.startsWith(tag)) {
                to_remove = entry;
                break;
            }
        }
        if (to_remove != null) {
            list.remove(to_remove);
        }
        list.add(to_add);
    }

    private String createTag(GenesisShop shop, GenesisBuy item) {
        return shop.getShopName() + ":" + item.getName();
    }

    public GenesisBuy getShopItem(String tag) {
        if (tag != null) {
            String[] parts = tag.split(":", 2);
            if (parts.length == 2) {
                if (ClassManager.manager.getShops() != null) {
                    GenesisShop shop = ClassManager.manager.getShops().getShop(parts[0].trim().toLowerCase());
                    if (shop != null) {
                        return shop.getItem(parts[1].trim());
                    }
                }
            }
        }
        return null;
    }


}

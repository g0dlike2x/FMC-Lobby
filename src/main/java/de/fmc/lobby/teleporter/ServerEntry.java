package de.fmc.lobby.teleporter;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Ein Teleporter-Eintrag aus der config.yml.
 *
 * @param id        Key unter teleporter.servers
 * @param slot      Slot im GUI
 * @param proxyName Servername im Proxy
 * @param host      Host für den Status-Ping
 * @param port      Port für den Status-Ping
 * @param baseItem  vorgebautes Item ohne Lore (wird pro Anzeige geklont)
 * @param lore      übersetzte Lore-Vorlage mit %status%, %online%, %max%, %server%
 * @param plainName Name ohne Farbcodes (für Nachrichten)
 */
public record ServerEntry(String id, int slot, String proxyName, String host, int port,
                          ItemStack baseItem, List<String> lore, String plainName) {
}

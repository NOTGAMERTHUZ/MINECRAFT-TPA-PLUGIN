# GamerthuzTPA

An advanced teleport-request plugin for **Paper 26.2** with a full GUI, by **Gamerthuz**.

## Features
- **Player picker GUI** with player heads, world, distance, ping and request status, with pages
  - Left-click = teleport to them · Right-click = bring them to you
- **Incoming requests GUI**: accept / deny / deny all
- **Popup Accept / Deny / Block menu** when someone sends you a request (optional, per player)
- **Settings GUI**: toggle requests, toggle popup, manage blocked players
- Clickable `[ACCEPT]` / `[DENY]` buttons in chat
- Warmup countdown with title, cancel on move or damage, cooldown, request expiry
- Optional **cost** per request through Vault
- Disabled-worlds list, teleport effects, all messages editable in `config.yml`
- Maharashtra tricolor GUI theme

## World groups (Multiverse)
Requests only work **inside one world group** (for example Survival + Survival_nether + Survival_the_end) and never between two groups
(Survival -> Lifesteal is blocked). The player list only shows players from your own group. Configure groups in `config.yml`.

## Requirements
- Paper 26.2 and Java 25
- [Vault](https://www.spigotmc.org/resources/vault.34315/) + an economy plugin (optional, only for `cost`)

## Commands
| Command | Description |
|---|---|
| `/tpa` | Open the GUI |
| `/tpa <player>` | Request to teleport to a player |
| `/tpahere <player>` | Ask a player to teleport to you |
| `/tpaccept [player]` | Accept a request |
| `/tpdeny [player]` | Deny a request |
| `/tpcancel` | Cancel your request |
| `/tpatoggle` | Turn incoming requests on/off |
| `/tpa block\|unblock <player>` | Manage your block list |
| `/tpa requests\|settings` | Open those menus |
| `/tpa reload` | Reload config (admin) |

If EssentialsX is installed, its own `/tpa` may take over. Remove those commands from Essentials, or use `/gamerthuztpa:tpa`.

## Permissions
`gamerthuz.tpa.use` (default: everyone) · `gamerthuz.tpa.bypass.cooldown` · `gamerthuz.tpa.bypass.warmup` · `gamerthuz.tpa.bypass.cost` · `gamerthuz.tpa.admin`

## Build
```
mvn package
```

---
Developed by **Gamerthuz** · Licensed under MIT

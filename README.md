# Hub Health Monitor & Auto Reboot

A Hubitat app that watches your hub's health and reboots it automatically when free memory runs low, when a critical hub event occurs, or on a regular schedule.

**Current version:** 1.4.2

## Features

- **Low-memory reboots.** Reboots when free memory falls below a threshold you set, but only within a time window you choose.
- **Hub event monitoring.** Reboots on `zigbeeOff`, `zwaveCrashed` or `severeLoad` events.
- **Periodic reboots.** Weekly, fortnightly or monthly, on a chosen day and time, with an optional fixed schedule (see below).
- **Optional database rebuild.** Applies to every reboot type.
- **Notifications.** Sends alerts to any notification device, such as the Hubitat mobile app, before a reboot and when a reboot fails.
- **Status at a glance.** Hub model, memory, uptime and reboot statistics on the main page.
- **Built-in safeguards.** Low-memory and event reboots are ignored for the first 5 minutes after startup, and won't fire within 1 minute of the last attempt, to prevent reboot loops.
- **Test buttons.** Test Reboot and Send Test Notification.

## Requirements

- Hubitat Elevation firmware 2.5.0 or later.
- Works on all hub models. The C-8 Pro is detected as having 2 GB RAM; other models are treated as 1 GB.

## Installation

### Hubitat Package Manager (recommended)

Search for **Hub Health Monitor & Auto Reboot**, or install from the package manifest:

```
https://raw.githubusercontent.com/dJOS1475/Hubitat-Memory-Monitor-Auto-Reboot/refs/heads/main/packageManifest.json
```

### Manual

1. In Hubitat, go to **Apps Code → New App → Import**.
2. Paste this URL and import:
   ```
   https://raw.githubusercontent.com/dJOS1475/Hubitat-Memory-Monitor-Auto-Reboot/refs/heads/main/memory-monitor-reboot.groovy
   ```
3. Save, then go to **Apps → Add User App → Hub Health Monitor & Auto Reboot**.

When updating, paste in the new code and press **Done** in the app. Your settings carry over.

## Configuration

The main page shows hub status and statistics, plus links to each settings page:

| Page | What it covers |
|---|---|
| **Reboot Settings** | Free memory threshold (default 200 MB), Enable Automatic Reboot, Rebuild Database on Reboot, and the reboot time window. The window can span midnight. |
| **Hub Event Monitoring** | Turn on event monitoring and pick which events trigger a reboot. |
| **Periodic Reboot Schedule** | Frequency, day of week, time, and Disable Smart Reboot. |
| **Notification Settings** | Message template, notification devices, which reboot types to notify on, and a test button. |
| **Advanced** | Test Reboot, Log Warning Before Reboot, and Enable Debug Logging. |

The memory check interval (1 minute to 1 hour, default 15 minutes) is on the main page.

### Periodic reboots: Smart vs fixed

The hub is checked once a week on the chosen day and time.

- **Smart Reboot (default).** The hub reboots only once its uptime reaches the minimum for the chosen frequency: 4.9 days for weekly, 9.8 days for fortnightly, 22 days for monthly. A manual, low-memory or event reboot in between pushes the next scheduled reboot back.
- **Disable Smart Reboot.** The hub reboots every 1, 2 or 4 weeks, counted from the last periodic reboot, whatever other reboots happened in between.

### Notification placeholders

| Placeholder | Replaced with |
|---|---|
| `%hub%` | Hub name |
| `%reason%` | Why the hub is rebooting |
| `%free%` | Current free memory (MB) |
| `%uptime%` | Hub uptime |
| `%now%` | Current date and time |

Default message: `%hub% is rebooting - %reason%`

## Recent changes

- **1.4.2.** Statistics column tidied so it lines up with the hub status column.
- **1.4.1.** Added Disable Smart Reboot. Settings split into their own pages, plus an Advanced page. Hub status and statistics shown side by side. Fixed a crash on clean installs.
- **1.4.0.** Added notifications.
- **1.3.3.** Startup wait and cooldown for low-memory reboots. Live free-memory reading. Reboot stats only recorded on success.
- **1.3.2.** Fixed Rebuild Database on Reboot failing with HTTP 500 on firmware 2.5.0+.
- **1.3.1.** Rewrote the periodic reboot scheduler as a weekly cron job.

The full changelog is in the header of [memory-monitor-reboot.groovy](memory-monitor-reboot.groovy).

## Support

Questions and feedback: [Hubitat Community thread](https://community.hubitat.com/t/release-memory-monitor-auto-reboot-application/160272)

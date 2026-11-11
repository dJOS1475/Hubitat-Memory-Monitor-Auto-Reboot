/**
 *  Hub Health Monitor & Auto Reboot
 *
 *  Description:
 *  Monitors hub health (memory usage and critical events) and automatically reboots
 *  when free memory falls below a configured threshold or critical events are detected.
 *
 *  Features:
 *  - Configurable minimum free memory threshold
 *  - Configurable reboot time window
 *  - Manual test reboot function
 *  - Optional database rebuild on reboot
 *  - Periodic scheduled reboots (weekly, fortnightly, monthly)
 *  - Hub event monitoring (zigbeeOff, zwaveCrashed, severeLoad)
 *  - Hub uptime display
 *  - Detailed logging
 *  - Memory status tracking
 *  - Push notifications on reboot (separate Notifications page)
 *
 *  Version: 1.4.2
 *  Author: Derek Osborn
 *  Date: 2026-11-11
 *
 *  v1.4.2 - Tidied the Statistics column so it lines up with the hub status column:
 *           grouped lines, shorter dates, event type shown with the last event reboot,
 *           and reboot totals on one line.
 *  v1.4.1 - Added "Disable Smart Reboot" (off by default) for periodic reboots on a fixed
 *           frequency, counted from the last periodic reboot and ignoring other reboots.
 *           Moved Reboot Settings + Time Window, Hub Event Monitoring and Periodic Reboot
 *           Schedule onto their own pages, linked from the main page with a summary of each.
 *           Moved Test Reboot and Logging onto a new Advanced page.
 *           Memory Monitoring now shows hub status and statistics side by side.
 *           Fixed a StackOverflowError on the main page for clean installs.
 *  v1.4.0 - Added a Notifications page: send reboot alerts to notification devices (e.g. the
 *           Hubitat mobile app), with a message template, per-reboot-type toggles,
 *           failed-reboot alerts and a Send Test Notification button.
 *  v1.3.3 - Low-memory reboots now wait 5 minutes after startup and use the same 1-minute
 *           cooldown as event reboots (prevents reboot loops). Low-memory reboot stats are
 *           only recorded when the reboot command succeeds. Free memory is read from the
 *           live /hub/advanced/freeOSMemory endpoint instead of the 5-minute history.
 *           Old-schedule cleanup now runs even when the memory reading fails.
 *  v1.3.2 - Fixed "Rebuild Database on Reboot" failing with HTTP 500 on firmware 2.5.0+,
 *           which now expects a JSON body for /hub/reboot.
 *  v1.3.1 - Rewrote periodic reboot scheduler. It now uses a weekly cron schedule on the
 *           chosen day/time (survives reboots, can't break the chain or go stale) and decides
 *           at each firing whether enough uptime has accrued for the chosen frequency.
 *           "Enable Debug Logging" now only affects logging - it no longer switches the
 *           scheduler into a daily "debug mode" (which caused daily reboots).
 *           Monthly now requires 22+ days uptime so it can't fire at week 3.
 *           Existing schedules from older versions migrate automatically.
 *  v1.3.0 - Code quality improvements: extracted reboot helper, eliminated duplicated
 *           frequency/uptime logic, added event reboot cooldown, fixed state updates
 *           on failed reboots, added unsubscribe to uninstalled(), replaced deprecated
 *           BigDecimal.ROUND_HALF_UP, centralised version constant, added HR helper.
 *  v1.2.3 - Added hub model detection using getHubVersion and display in info panel. Total RAM now determined by hub model.
 *  v1.2.2 - Improved debug mode for periodic reboots - uses exact reboot time on current day,
 *           bypassing day-of-week and uptime checks. Removed hourly option.
 *  v1.2.1 - Fixed BigDecimal.round() compatibility issue for Hubitat
 *  v1.2.0 - Renamed to "Hub Health Monitor & Auto Reboot" and added hub event monitoring
 *           for zigbeeOff, zwaveCrashed, and severeLoad events with 5-minute startup grace period
 *  v1.1.5 - Updated default memory threshold to 200 MB and minor improvements to scheduler logic
 *  v1.1.4 - Added uptime check during polling - reschedules reboot if uptime < 70% of interval
 *  v1.1.3 - Finally got the Rebuild Database on Reboot function working
 *  v1.1.2 - Fixed periodic reboot scheduling, reverted to /hub/rebuildDatabaseAndReboot endpoint
 *  v1.1.1 - Fixed periodic reboot scheduling to properly calculate next occurrence
 *  v1.1.0 - Simplified memory detection to use actual total RAM from hub data
 *  v1.0.9 - Updated memory detection - only C-8 Pro has 2GB RAM
 *  v1.0.8 - Fixed uptime parsing to correctly handle CSV format from memory history
 *  v1.0.7 - Fixed uptime calculation to use memory history endpoint
 *  v1.0.6 - Fixed namespace and improved uptime display
 *  v1.0.5 - Added hub uptime display and periodic scheduled reboot feature
 *  v1.0.4 - Added import url and updated endpoint for reboot with db rebuild
 *  v1.0.2 - Added option to rebuild the Database on reboot
 *  v1.0.1 - Removed Hub Security as no longer required
 *  v1.0.0 - First public release
 */

import groovy.transform.Field

@Field static final String VERSION = "1.4.2"
@Field static final int STARTUP_GRACE_SECONDS = 300  // 5 minutes
@Field static final long EVENT_REBOOT_COOLDOWN_MS = 60000  // 1 minute cooldown between event reboots
@Field static final String DEFAULT_NOTIFY_MESSAGE = "%hub% is rebooting - %reason%"

definition(
    name: "Hub Health Monitor & Auto Reboot",
    namespace: "dJOS",
    author: "Derek Osborn",
    description: "Monitors hub health (memory and critical events) and automatically reboots when thresholds are exceeded or critical events detected",
    category: "Utility",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    importUrl: "https://raw.githubusercontent.com/dJOS1475/Hubitat-Memory-Monitor-Auto-Reboot/refs/heads/main/memory-monitor-reboot.groovy"
)

preferences {
    page(name: "mainPage")
    page(name: "rebootSettingsPage")
    page(name: "eventMonitoringPage")
    page(name: "periodicRebootPage")
    page(name: "notificationPage")
    page(name: "advancedPage")
}

// ──────────────────────────────────────────────
//  UI
// ──────────────────────────────────────────────

def mainPage() {
    dynamicPage(name: "mainPage", title: "Hub Health Monitor & Auto Reboot", install: true, uninstall: true) {
        sectionHR()

        section("<b>Memory Monitoring</b>") {
            paragraph "<div style='display:flex; flex-wrap:wrap; gap:16px 32px;'>" +
                      "<div style='flex:1 1 260px;'>${hubStatusHtml()}</div>" +
                      "<div style='flex:1 1 260px;'>${statisticsHtml()}</div>" +
                      "</div>"
        }

        sectionHR()

        section("<b>Settings</b>") {
            href "rebootSettingsPage", title: "Reboot Settings", description: rebootSettingsSummary()
            href "eventMonitoringPage", title: "Hub Event Monitoring", description: eventMonitoringSummary()
            href "periodicRebootPage", title: "Periodic Reboot Schedule", description: periodicRebootSummary()
            def notifyDesc = notifyDevices ? "Sending to: ${notifyDevices.join(', ')}" : "Not configured - tap to set up"
            href "notificationPage", title: "Notification Settings", description: notifyDesc
            href "advancedPage", title: "Advanced", description: "Test reboot and logging (debug logging: ${enableDebug ? 'on' : 'off'})"
        }

        sectionHR()

        section("<b>Monitoring Schedule</b>") {
            input "checkInterval", "enum",
                title: "Memory Check Interval",
                description: "How often to check memory usage",
                options: [
                    "1": "Every 1 minute",
                    "5": "Every 5 minutes",
                    "10": "Every 10 minutes",
                    "15": "Every 15 minutes",
                    "30": "Every 30 minutes",
                    "60": "Every 1 hour"
                ],
                defaultValue: "15",
                required: true
        }
    }
}

/** Left column of the Memory Monitoring section: version, hub model, memory and uptime. */
private String hubStatusHtml() {
    def lines = ["<b>Version:</b> ${VERSION}",
                 "<b>Hub Model:</b> ${getHubModel() ?: 'Unknown'}",
                 ""]
    def memInfo = getMemoryInfo()
    if (memInfo) {
        lines << "<b>Free Memory:</b> ${memInfo.free} MB"
        lines << "<b>Total Memory:</b> ${memInfo.total} MB"
        lines << "<b>Used Memory:</b> ${memInfo.used} MB"
        lines << "<b>Usage:</b> ${memInfo.percentUsed}%"
    } else {
        lines << "Unable to retrieve memory information"
    }
    def uptime = getHubUptime()
    if (uptime) {
        lines << ""
        lines << "<b>Hub Uptime:</b> ${uptime}"
    }
    return lines.join("<br>")
}

/**
 * Right column of the Memory Monitoring section: check and reboot statistics.
 * Laid out in three groups that line up with the left column's groups.
 */
private String statisticsHtml() {
    def fmt = { Long ms -> new Date(ms).format('yyyy-MM-dd HH:mm', location.timeZone) }

    // Group 1: checks and the next scheduled reboot (beside Version / Hub Model)
    def checks = []
    checks << "<b>Last Check:</b> ${state.lastCheck ? fmt(state.lastCheck as Long) : 'Not yet'}"
    if (enablePeriodicReboot && periodicDayOfWeek && periodicRebootTime) {
        def nextReboot = getNextPeriodicReboot()
        if (nextReboot) {
            state.nextPeriodicReboot = nextReboot.time
            checks << "<b>Next Scheduled Reboot:</b> ${fmt(nextReboot.time)}" +
                      (disableSmartReboot ? "" : " <i>(if no other reboot first)</i>")
        }
    }

    // Group 2: most recent reboot of each type (beside the memory figures)
    def recent = []
    if (state.lastReboot) recent << "<b>Last Low-Memory Reboot:</b> ${fmt(state.lastReboot as Long)}"
    if (state.lastEventReboot) {
        recent << "<b>Last Event Reboot:</b> ${fmt(state.lastEventReboot as Long)}" +
                  (state.lastEventType ? " (${state.lastEventType})" : "")
    }
    if (state.lastPeriodicReboot) recent << "<b>Last Periodic Reboot:</b> ${fmt(state.lastPeriodicReboot as Long)}"
    if (!recent) recent << "<b>Last Reboot:</b> None recorded yet"

    // Group 3: reboot totals on one line (beside Hub Uptime)
    def totals = "<b>Total Reboots:</b> ${state.rebootCount ?: 0} low-memory, " +
                 "${state.eventRebootCount ?: 0} event, ${state.periodicRebootCount ?: 0} periodic"

    return [checks.join("<br>"), recent.join("<br>"), totals].join("<br><br>")
}

def rebootSettingsPage() {
    dynamicPage(name: "rebootSettingsPage", title: "Reboot Settings") {
        section("<b>Reboot Settings</b>") {
            input "memoryThreshold", "number",
                title: "Minimum Free Memory Threshold (MB)",
                description: "Reboot when free memory falls below this value",
                required: true,
                defaultValue: 200,
                range: "10..500"

            input "enableAutoReboot", "bool",
                title: "Enable Automatic Reboot",
                description: "Allow app to automatically reboot hub when threshold is reached",
                defaultValue: false,
                submitOnChange: true

            input "rebuildDatabase", "bool",
                title: "Rebuild Database on Reboot",
                description: "Perform database rebuild when rebooting (may take longer). Applies to all reboot types.",
                defaultValue: false
        }

        if (enableAutoReboot) {
            section("<b>Reboot Time Window</b>") {
                paragraph "The hub will only reboot within this time window when the memory threshold is reached"

                input "rebootStartTime", "time",
                    title: "Window Start Time",
                    description: "Start of allowed reboot window",
                    required: true

                input "rebootEndTime", "time",
                    title: "Window End Time",
                    description: "End of allowed reboot window",
                    required: true
            }
        }
    }
}

def eventMonitoringPage() {
    dynamicPage(name: "eventMonitoringPage", title: "Hub Event Monitoring") {
        section("<b>Hub Event Monitoring</b>") {
            paragraph "Monitor for critical hub events and automatically reboot when detected<br><i>(Critical events are ignored for the first ${STARTUP_GRACE_SECONDS / 60 as int} minutes after hub startup)</i>"

            input "enableEventMonitoring", "bool",
                title: "Enable Hub Event Monitoring",
                description: "Monitor for critical hub events and reboot when detected",
                defaultValue: false,
                submitOnChange: true

            if (enableEventMonitoring) {
                input "monitorZigbeeOff", "bool",
                    title: "Monitor for zigbeeOff Event",
                    description: "Reboot when Zigbee radio goes offline",
                    defaultValue: true

                input "monitorZwaveCrashed", "bool",
                    title: "Monitor for zwaveCrashed Event",
                    description: "Reboot when Z-Wave radio crashes",
                    defaultValue: true

                input "monitorSevereLoad", "bool",
                    title: "Monitor for severeLoad Event",
                    description: "Reboot when hub experiences severe load",
                    defaultValue: true
            }
        }
    }
}

def periodicRebootPage() {
    dynamicPage(name: "periodicRebootPage", title: "Periodic Reboot Schedule") {
        section("<b>Periodic Reboot Schedule</b>") {
            input "enablePeriodicReboot", "bool",
                title: "Enable Periodic Scheduled Reboot",
                description: "Reboot hub on a regular schedule",
                defaultValue: false,
                submitOnChange: true

            if (enablePeriodicReboot) {
                input "periodicFrequency", "enum",
                    title: "Reboot Frequency",
                    description: "How often to perform scheduled reboot",
                    options: [
                        "weekly": "Weekly",
                        "fortnightly": "Fortnightly (Every 2 weeks)",
                        "monthly": "Monthly (Every 4 weeks)"
                    ],
                    required: true,
                    defaultValue: "weekly"

                input "periodicDayOfWeek", "enum",
                    title: "Day of Week",
                    description: "Which day to perform the reboot",
                    options: [
                        "SUN": "Sunday",
                        "MON": "Monday",
                        "TUE": "Tuesday",
                        "WED": "Wednesday",
                        "THU": "Thursday",
                        "FRI": "Friday",
                        "SAT": "Saturday"
                    ],
                    required: true,
                    defaultValue: "SUN"

                input "periodicRebootTime", "time",
                    title: "Reboot Time",
                    description: "Time to perform the scheduled reboot",
                    required: true

                input "disableSmartReboot", "bool",
                    title: "Disable Smart Reboot",
                    description: "Reboot on the chosen frequency no matter how recently the hub last rebooted",
                    defaultValue: false,
                    submitOnChange: true

                if (disableSmartReboot) {
                    paragraph "<i>Smart Reboot is OFF: the hub reboots every week, every 2 weeks or every 4 weeks " +
                              "(counted from the last periodic reboot) on the chosen day and time, even if it rebooted " +
                              "for another reason in between.</i>"
                } else {
                    paragraph "<i>Smart Reboot is ON: the hub is checked every week on the chosen day and time, and only reboots once its uptime " +
                              "has reached the minimum for the chosen frequency (weekly: 4.9 days, fortnightly: 9.8 days, " +
                              "monthly: 22 days). A manual or memory-triggered reboot therefore pushes the next scheduled reboot back automatically.</i>"
                }
                paragraph "<i>Note: Periodic reboots use the database rebuild setting on the Reboot Settings page.</i>"
            }
        }
    }
}

/** One-line summaries shown under each settings link on the main page. */
private String rebootSettingsSummary() {
    def parts = ["Threshold: ${thresholdMB()} MB"]
    if (enableAutoReboot) {
        def window = (rebootStartTime && rebootEndTime) ? " (${formatTimeSetting(rebootStartTime)}-${formatTimeSetting(rebootEndTime)})" : ""
        parts << "Auto reboot: on${window}"
    } else {
        parts << "Auto reboot: off"
    }
    parts << "DB rebuild: ${rebuildDatabase ? 'on' : 'off'}"
    return parts.join(" | ")
}

private String eventMonitoringSummary() {
    if (!enableEventMonitoring) return "Off"
    def events = []
    if (monitorZigbeeOff != false) events << "zigbeeOff"
    if (monitorZwaveCrashed != false) events << "zwaveCrashed"
    if (monitorSevereLoad != false) events << "severeLoad"
    return events ? "On: ${events.join(', ')}" : "On, but no events selected"
}

private String periodicRebootSummary() {
    if (!enablePeriodicReboot) return "Off"
    def freqNames = [weekly: "Weekly", fortnightly: "Fortnightly", monthly: "Monthly"]
    def desc = "${freqNames[periodicFrequency ?: 'weekly']} on ${getDayName(periodicDayOfWeek ?: 'SUN')}"
    if (periodicRebootTime) desc += " at ${formatTimeSetting(periodicRebootTime)}"
    return desc + (disableSmartReboot ? " (fixed schedule)" : " (smart)")
}

/** Formats a "time" input setting as HH:mm in the hub's time zone. */
private String formatTimeSetting(String timeSetting) {
    try {
        return timeToday(timeSetting, location.timeZone).format("HH:mm", location.timeZone)
    } catch (Exception e) {
        return "?"
    }
}

/** Memory threshold in MB, falling back to the default if the setting was never saved. */
private int thresholdMB() {
    return (settings.memoryThreshold ?: 200) as int
}

def advancedPage() {
    dynamicPage(name: "advancedPage", title: "Advanced") {
        section("<b>Test Reboot</b>") {
            paragraph "<b>Warning:</b> This will immediately reboot your hub!"
            input "testReboot", "button", title: "Test Reboot Now"
        }

        section("<b>Logging</b>") {
            input "notifyBeforeReboot", "bool",
                title: "Log Warning Before Reboot",
                description: "Log a warning message before initiating reboot",
                defaultValue: true

            input "enableDebug", "bool",
                title: "Enable Debug Logging",
                defaultValue: false
        }
    }
}

def notificationPage() {
    dynamicPage(name: "notificationPage", title: "Notifications") {
        section("<b>Message to Send</b>") {
            input "notifyMessage", "textarea",
                title: "Message",
                defaultValue: DEFAULT_NOTIFY_MESSAGE,
                required: false
            paragraph "Use %hub% to include the hub name, %reason% for why the hub is rebooting, " +
                      "%free% for current free memory (MB), %uptime% for hub uptime, and %now% for the current time."
        }

        section("<b>Use these notification devices</b>") {
            input "notifyDevices", "capability.notification",
                title: "On these notification devices",
                multiple: true,
                required: false
        }

        section("<b>Notify on</b>") {
            input "notifyOnMemory", "bool", title: "Low-memory reboots", defaultValue: true
            input "notifyOnEvent", "bool", title: "Hub event reboots (zigbeeOff, zwaveCrashed, severeLoad)", defaultValue: true
            input "notifyOnPeriodic", "bool", title: "Periodic scheduled reboots", defaultValue: true
            input "notifyOnTest", "bool", title: "Test reboots", defaultValue: true
            input "notifyOnFailure", "bool", title: "Failed reboot attempts", defaultValue: true
        }

        section("<b>Test</b>") {
            paragraph "Sends the message above to the selected devices without rebooting. Press Done on the main page first if you just picked devices."
            input "testNotification", "button", title: "Send Test Notification"
        }
    }
}

/** Renders a styled horizontal rule inside its own section. */
private sectionHR() {
    section() {
        paragraph "<hr style='background-color:#1A77C9; height: 1px; border: 0;'>"
    }
}

// ──────────────────────────────────────────────
//  Lifecycle
// ──────────────────────────────────────────────

def installed() {
    log.info "Hub Health Monitor & Auto Reboot installed"
    initialize()
}

def updated() {
    log.info "Hub Health Monitor & Auto Reboot updated"
    unsubscribe()
    unschedule()
    initialize()
}

def uninstalled() {
    log.info "Hub Health Monitor & Auto Reboot uninstalled"
    unsubscribe()
    unschedule()
}

def initialize() {
    state.rebootCount = state.rebootCount ?: 0
    state.periodicRebootCount = state.periodicRebootCount ?: 0
    state.eventRebootCount = state.eventRebootCount ?: 0

    // Subscribe to hub events if monitoring is enabled
    if (enableEventMonitoring) {
        if (monitorZigbeeOff) {
            subscribe(location, "zigbeeOff", hubEventHandler)
            log.info "Subscribed to zigbeeOff events"
        }
        if (monitorZwaveCrashed) {
            subscribe(location, "zwaveCrashed", hubEventHandler)
            log.info "Subscribed to zwaveCrashed events"
        }
        if (monitorSevereLoad) {
            subscribe(location, "severeLoad", hubEventHandler)
            log.info "Subscribed to severeLoad events"
        }
    }

    // Schedule memory checks based on configured interval
    def interval = (checkInterval ?: "15").toInteger()
    
    switch(interval) {
        case 1:
            runEvery1Minute(checkMemory)
            break
        case 5:
            runEvery5Minutes(checkMemory)
            break
        case 10:
            runEvery10Minutes(checkMemory)
            break
        case 15:
            runEvery15Minutes(checkMemory)
            break
        case 30:
            runEvery30Minutes(checkMemory)
            break
        case 60:
            runEvery1Hour(checkMemory)
            break
        default:
            runEvery15Minutes(checkMemory)
    }
    
    log.info "Memory monitoring initialized - checking every ${interval} minute(s)"
    log.info "Threshold: ${thresholdMB()} MB free memory"
    
    if (enableAutoReboot) {
        log.info "Auto-reboot enabled for time window: ${rebootStartTime} to ${rebootEndTime}"
    } else {
        log.info "Auto-reboot is DISABLED"
    }
    
    // Schedule (or clear) the weekly periodic reboot check
    schedulePeriodicReboot()
    
    // Do an initial check
    runIn(5, checkMemory)
}

// ──────────────────────────────────────────────
//  Shared helpers
// ──────────────────────────────────────────────

/**
 * Returns the number of days for a given periodic frequency string.
 * Defaults to 7 (weekly) if the value is null or unrecognised.
 */
private int getFrequencyDays(String freq = null) {
    switch(freq ?: periodicFrequency ?: "weekly") {
        case "weekly":      return 7
        case "fortnightly": return 14
        case "monthly":     return 28
        default:            return 7
    }
}

/**
 * Returns the uptime (seconds) the hub must have before a periodic reboot is performed.
 * Normally 70% of the interval, but never less than (interval - 6 days) so that a
 * weekly check can't trigger a monthly reboot at week 3.
 *   weekly 4.9d, fortnightly 9.8d, monthly 22d
 */
private long getRequiredUptimeSeconds(String freq = null) {
    long intervalSecs = getFrequencyDays(freq) * 86400L
    long seventyPct = (long)(intervalSecs * 0.7)
    long floorSecs = intervalSecs - (6L * 86400L)
    return Math.max(seventyPct, floorSecs)
}

/** Current hub uptime in seconds (0 if unavailable). */
private long getUptimeSeconds() {
    try {
        return (location.hub.uptime ?: 0) as long
    } catch (Exception e) {
        return 0L
    }
}

/**
 * Formats a seconds value as a day count with one decimal place,
 * avoiding the deprecated BigDecimal.ROUND_HALF_UP constant.
 */
private String formatDays(Number seconds) {
    return String.valueOf(Math.round(seconds / 8640.0) / 10.0)
}

/**
 * Sends a reboot notification to the selected devices if notifications are enabled
 * for this kind of reboot. type: memory, event, periodic, test or failure.
 * Toggles that were never saved count as on.
 */
private void sendRebootNotification(String type, String reason) {
    if (!notifyDevices) return
    def toggle = [memory: "notifyOnMemory", event: "notifyOnEvent", periodic: "notifyOnPeriodic",
                  test: "notifyOnTest", failure: "notifyOnFailure"][type]
    if (toggle && settings[toggle] == false) return

    String msg
    if (type == "failure") {
        msg = "${location.hub.name}: reboot FAILED (${reason}) - you may need to reboot manually"
    } else {
        msg = buildNotifyMessage(reason)
    }

    try {
        notifyDevices.each { it.deviceNotification(msg) }
        logDebug "Notification sent: ${msg}"
    } catch (Exception e) {
        log.error "Error sending notification: ${e.message}"
    }
}

/** Fills in the placeholders in the user's message template. */
private String buildNotifyMessage(String reason) {
    String msg = notifyMessage ?: DEFAULT_NOTIFY_MESSAGE
    if (msg.contains("%free%")) {
        msg = msg.replace("%free%", "${getMemoryInfo()?.free ?: '?'}")
    }
    return msg.replace("%hub%", "${location.hub.name}")
              .replace("%reason%", reason ?: "")
              .replace("%uptime%", getHubUptime() ?: "unknown")
              .replace("%now%", new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone))
}

/**
 * True if the hub firmware is at least minVer (e.g. "2.5.0.0").
 * Assumes current firmware if the version can't be read.
 */
private boolean firmwareAtLeast(String minVer) {
    try {
        def current = (location.hub.firmwareVersionString ?: "").tokenize('.').collect { it.toInteger() }
        if (!current) return true
        def wanted = minVer.tokenize('.').collect { it.toInteger() }
        for (int i = 0; i < wanted.size(); i++) {
            int c = i < current.size() ? current[i] : 0
            if (c != wanted[i]) return c > wanted[i]
        }
        return true
    } catch (Exception e) {
        return true
    }
}

/**
 * Sends the reboot (or rebuild-and-reboot) HTTP command to the hub.
 * Returns true if the command was sent successfully, false otherwise.
 */
private boolean executeReboot() {
    try {
        def params = [
            uri: "http://127.0.0.1:8080",
            path: "/hub/reboot"
        ]
        if (rebuildDatabase) {
            // Firmware 2.5.0+ expects a JSON body; older firmware expects a form field
            if (firmwareAtLeast("2.5.0.0")) {
                params.headers = ["Content-Type": "application/json"]
                params.body = '{"rebuildDatabase":true,"purgeLogs":false}'
            } else {
                params.headers = ["Content-Type": "application/x-www-form-urlencoded"]
                params.body = [rebuildDatabase: "true"]
            }
        }
        logDebug "Reboot request: ${params}"
        httpPost(params) { resp -> }
        log.info "Reboot command sent successfully${rebuildDatabase ? ' (with DB rebuild)' : ''}"
        return true
    } catch (Exception e) {
        log.error "Error sending reboot command: ${e.message}"
        return false
    }
}

// ──────────────────────────────────────────────
//  Button handler
// ──────────────────────────────────────────────

def appButtonHandler(btn) {
    switch(btn) {
        case "testReboot":
            log.warn "TEST REBOOT button pressed - rebooting hub NOW"
            performReboot(true)
            break
        case "testNotification":
            if (!notifyDevices) {
                log.warn "Test notification: no notification devices selected"
            } else {
                String msg = buildNotifyMessage("Test notification")
                notifyDevices.each { it.deviceNotification(msg) }
                log.info "Test notification sent: ${msg}"
            }
            break
    }
}

// ──────────────────────────────────────────────
//  Hub event monitoring
// ──────────────────────────────────────────────

def hubEventHandler(evt) {
    def eventName = evt.name
    def eventValue = evt.value

    log.warn "═══════════════════════════════════════"
    log.warn "CRITICAL HUB EVENT DETECTED: ${eventName}"
    log.warn "Event Value: ${eventValue}"
    log.warn "═══════════════════════════════════════"

    // Ignore all critical events if hub has been online for less than the grace period
    long hubUptimeSeconds = getUptimeSeconds()
    if (hubUptimeSeconds < STARTUP_GRACE_SECONDS) {
        def uptimeMinutes = Math.round(hubUptimeSeconds / 60)
        log.info "Ignoring ${eventName} event - hub uptime is only ${uptimeMinutes} minute(s) (waiting ${STARTUP_GRACE_SECONDS / 60 as int} minutes)"
        return
    }

    // Cooldown: prevent duplicate reboots if multiple events fire in quick succession
    def lastAttempt = state.lastRebootAttempt ?: 0
    if ((now() - lastAttempt) < EVENT_REBOOT_COOLDOWN_MS) {
        log.warn "Reboot already in progress or recently attempted, ignoring duplicate ${eventName} event"
        return
    }
    state.lastRebootAttempt = now()

    // Perform reboot due to hub event
    def dbAction = rebuildDatabase ? " with Database Rebuild" : ""
    log.warn "Initiating hub reboot${dbAction} due to ${eventName} event"
    sendRebootNotification("event", "${eventName} event")

    // Pause briefly to ensure log messages and notifications go out
    pauseExecution(2000)

    if (executeReboot()) {
        // Only record the reboot in state if the command was actually sent
        state.lastEventReboot = now()
        state.lastEventType = eventName
        state.eventRebootCount = (state.eventRebootCount ?: 0) + 1
    } else {
        log.error "You may need to reboot manually from Settings > Reboot"
        sendRebootNotification("failure", "${eventName} event")
    }
}

// ──────────────────────────────────────────────
//  Memory monitoring
// ──────────────────────────────────────────────

def checkMemory() {
    state.lastCheck = now()

    // Self-migrate: if the code was updated without pressing Done, older versions'
    // one-shot schedules may still be pending. Rebuild the schedule once per version.
    if (state.scheduleVersion != VERSION) {
        log.info "Scheduler version changed (${state.scheduleVersion ?: 'none'} -> ${VERSION}) - rebuilding periodic reboot schedule"
        schedulePeriodicReboot()
    }

    def memInfo = getMemoryInfo()

    if (!memInfo) {
        log.error "Unable to retrieve memory information"
        return
    }

    logDebug "Memory check - Free: ${memInfo.free} MB, Used: ${memInfo.used} MB (${memInfo.percentUsed}%)"

    // Check if we're below threshold
    if (memInfo.free < thresholdMB()) {
        log.warn "Free memory (${memInfo.free} MB) is below threshold (${thresholdMB()} MB)"

        if (enableAutoReboot) {
            long uptimeSecs = getUptimeSeconds()
            long sinceLastAttempt = now() - (state.lastRebootAttempt ?: 0)
            if (uptimeSecs < STARTUP_GRACE_SECONDS) {
                log.info "Below threshold but hub uptime is only ${Math.round(uptimeSecs / 60)} minute(s) - " +
                         "waiting ${STARTUP_GRACE_SECONDS / 60 as int} minutes after startup before rebooting"
            } else if (sinceLastAttempt < EVENT_REBOOT_COOLDOWN_MS) {
                log.warn "Below threshold but a reboot was attempted ${Math.round(sinceLastAttempt / 1000)} second(s) ago - skipping"
            } else if (isWithinRebootWindow()) {
                log.warn "Within reboot time window - initiating reboot"
                performReboot(false)
            } else {
                log.info "Below threshold but outside reboot time window - will reboot when window opens"
            }
        } else {
            log.info "Auto-reboot is disabled - no action taken"
        }
    } else {
        logDebug "Memory levels OK - ${memInfo.free} MB free (threshold: ${thresholdMB()} MB)"
    }
}

// ──────────────────────────────────────────────
//  Hub info helpers
// ──────────────────────────────────────────────

def getHubModel() {
    try {
        // getHubVersion() is a built-in Hubitat method that returns the hub model
        // Returns strings like "C-7", "C-8", "C-8 Pro", etc.
        return getHubVersion()
    } catch (Exception e) {
        log.error "Error getting hub model: ${e.message}"
        return null
    }
}

def getTotalMemoryForModel(String model) {
    // C-8 Pro and higher models have 2GB RAM
    // All other models (C-1 through C-8) have 1GB RAM
    if (model != null) {
        def modelUpper = model.toUpperCase()
        if (modelUpper.contains("PRO")) {
            return 2048 // 2GB for C-8 Pro and higher
        }
    }
    return 1024 // 1GB for all other hubs (C-1 through C-8)
}

def getMemoryInfo() {
    try {
        // Current free OS memory in KB, returned as a plain number
        // (the history endpoint is only sampled every 5 minutes)
        def params = [
            uri: "http://127.0.0.1:8080",
            path: "/hub/advanced/freeOSMemory",
            textParser: true,
            timeout: 5
        ]

        def freeMemKB = null

        httpGet(params) { resp ->
            if (resp.success) {
                def raw = resp.data
                String text = (raw instanceof String) ? raw : raw?.text
                if (text?.trim()?.isNumber()) {
                    freeMemKB = text.trim().toBigDecimal().toLong()
                }
            }
        }

        if (freeMemKB != null) {
            // Convert KB to MB
            def freeMemMB = Math.round(freeMemKB / 1024)

            // Determine total OS RAM based on hub model
            def hubModel = getHubModel()
            def totalMemMB = getTotalMemoryForModel(hubModel)

            def usedMemMB = totalMemMB - freeMemMB
            def percentUsed = Math.round((usedMemMB / totalMemMB) * 100)

            return [
                free: freeMemMB,
                total: totalMemMB,
                used: usedMemMB,
                percentUsed: percentUsed
            ]
        }
    } catch (Exception e) {
        log.error "Error getting memory stats: ${e.message}"
        logDebug "Memory error details: ${e}"
    }

    return null
}

def getHubUptime() {
    try {
        def uptimeSeconds = location.hub.uptime
        if (uptimeSeconds == null || uptimeSeconds < 0) return null

        def days = (uptimeSeconds / 86400) as int
        def hours = ((uptimeSeconds % 86400) / 3600) as int
        def minutes = ((uptimeSeconds % 3600) / 60) as int

        def uptimeStr = ""
        if (days > 0) {
            uptimeStr += "${days} day${days != 1 ? 's' : ''}, "
        }
        if (hours > 0 || days > 0) {
            uptimeStr += "${hours} hour${hours != 1 ? 's' : ''}, "
        }
        uptimeStr += "${minutes} minute${minutes != 1 ? 's' : ''}"

        return uptimeStr
    } catch (Exception e) {
        log.error "Error getting hub uptime: ${e.message}"
        logDebug "Uptime error details: ${e}"
    }

    return null
}

// ──────────────────────────────────────────────
//  Reboot window & execution
// ──────────────────────────────────────────────

def isWithinRebootWindow() {
    if (!rebootStartTime || !rebootEndTime) {
        return false
    }
    
    def now = new Date()
    def start = timeToday(rebootStartTime, location.timeZone)
    def end = timeToday(rebootEndTime, location.timeZone)
    
    // Handle time window spanning midnight
    if (end < start) {
        return (now >= start || now <= end)
    } else {
        return (now >= start && now <= end)
    }
}

def performReboot(isTest) {
    def memInfo = getMemoryInfo()

    if (notifyBeforeReboot || isTest) {
        def reason = isTest ? "TEST REBOOT" : "Low Memory (${memInfo?.free} MB free)"
        def dbAction = rebuildDatabase ? " with Database Rebuild" : ""
        log.warn "═══════════════════════════════════════"
        log.warn "REBOOTING HUB${dbAction} - Reason: ${reason}"
        log.warn "═══════════════════════════════════════"
    }

    state.lastRebootAttempt = now()

    def notifyReason = isTest ? "Test reboot" : "Low memory (${memInfo?.free} MB free)"
    sendRebootNotification(isTest ? "test" : "memory", notifyReason)

    // Pause briefly to ensure log messages and notifications go out
    pauseExecution(2000)

    if (executeReboot()) {
        // Only record the reboot in state if the command was actually sent
        if (!isTest) {
            state.lastReboot = now()
            state.rebootCount = (state.rebootCount ?: 0) + 1
        }
    } else {
        log.error "You may need to reboot manually from Settings > Reboot"
        sendRebootNotification("failure", notifyReason)
    }
}

// ──────────────────────────────────────────────
//  Periodic reboot scheduling
// ──────────────────────────────────────────────

/**
 * Sets up the periodic reboot check as a weekly cron job on the chosen day and time.
 * A cron schedule persists across hub reboots and never needs re-arming, so it
 * can't be lost or go stale the way a chain of runOnce() calls can.
 */
def schedulePeriodicReboot() {
    // Clear any schedule from this or older versions (older versions used runOnce)
    unschedule("periodicRebootCheck")
    unschedule("performPeriodicReboot")
    state.scheduleVersion = VERSION

    if (!(enablePeriodicReboot && periodicDayOfWeek && periodicRebootTime)) {
        state.remove("nextPeriodicReboot")
        return
    }

    def rebootTime = timeToday(periodicRebootTime, location.timeZone)
    def hour = rebootTime.format("H", location.timeZone)
    def minute = rebootTime.format("m", location.timeZone)
    def cron = "0 ${minute} ${hour} ? * ${periodicDayOfWeek}"

    schedule(cron, "periodicRebootCheck")

    def nextReboot = getNextPeriodicReboot()
    state.nextPeriodicReboot = nextReboot?.time

    log.info "Periodic reboot check scheduled every ${getDayName(periodicDayOfWeek)} at ${rebootTime.format('HH:mm', location.timeZone)} " +
             "(cron: ${cron}); frequency ${periodicFrequency ?: 'weekly'}, " +
             (disableSmartReboot ? "fixed schedule (Smart Reboot disabled)" : "minimum uptime ${formatDays(getRequiredUptimeSeconds())} days")
    if (nextReboot) {
        log.info "Next periodic reboot expected ${nextReboot.format('yyyy-MM-dd HH:mm', location.timeZone)}"
    }
}

/**
 * Projects the next date the weekly check will actually reboot the hub, assuming
 * no other reboot happens first: the first chosen-weekday occurrence at which
 * projected uptime meets the requirement for the chosen frequency.
 */
Date getNextPeriodicReboot() {
    if (!(periodicDayOfWeek && periodicRebootTime)) return null

    def nowDate = new Date()
    def calendar = Calendar.getInstance(location.timeZone)
    calendar.setTime(timeToday(periodicRebootTime, location.timeZone))

    int daysToAdd = getDayOfWeekNumber(periodicDayOfWeek) - calendar.get(Calendar.DAY_OF_WEEK)
    if (daysToAdd < 0) daysToAdd += 7
    calendar.add(Calendar.DAY_OF_MONTH, daysToAdd)
    if (!calendar.time.after(nowDate)) calendar.add(Calendar.DAY_OF_MONTH, 7)

    // At most 5 weekly occurrences are ever needed (monthly = 4 weeks)
    for (int i = 0; i < 6; i++) {
        if (isPeriodicRebootDue(calendar.time.time)) return calendar.time
        calendar.add(Calendar.DAY_OF_MONTH, 7)
    }
    return calendar.time
}

/**
 * Whether a weekly check at the given time (epoch ms) would reboot the hub.
 * Smart Reboot (default): projected uptime must reach the minimum for the frequency.
 * Smart Reboot disabled: a fixed cadence counted from the last periodic reboot; other
 * reboots are ignored. One day of slack absorbs DST shifts and late-running jobs.
 */
private boolean isPeriodicRebootDue(long atMillis) {
    if (disableSmartReboot) {
        Long last = state.lastPeriodicReboot as Long
        if (!last) return true
        long minGapMs = (getFrequencyDays() - 1) * 86400000L
        return (atMillis - last) >= minGapMs
    }
    long secsUntil = Math.max(0L, (atMillis - now()).intdiv(1000L) as long)
    return getUptimeSeconds() + secsUntil >= getRequiredUptimeSeconds()
}

/**
 * Cron handler - runs every week on the chosen day/time and reboots only if the hub
 * has been up long enough for the chosen frequency.
 */
def periodicRebootCheck() {
    if (!enablePeriodicReboot) {
        logDebug "Periodic reboot check fired but periodic reboot is disabled - ignoring"
        return
    }

    // Guard: only act on the configured weekday
    def todayNum = Calendar.getInstance(location.timeZone).get(Calendar.DAY_OF_WEEK)
    if (todayNum != getDayOfWeekNumber(periodicDayOfWeek)) {
        log.info "Periodic reboot check fired on a day other than ${getDayName(periodicDayOfWeek)} (old schedule) - ignoring and rebuilding schedule"
        schedulePeriodicReboot()
        return
    }

    long uptimeSecs = getUptimeSeconds()
    long requiredSecs = getRequiredUptimeSeconds()
    def freq = periodicFrequency ?: "weekly"

    if (disableSmartReboot) {
        if (!isPeriodicRebootDue(now())) {
            log.info "Periodic reboot check: skipping - fixed ${freq} schedule, last periodic reboot was " +
                     "${formatDays((now() - (state.lastPeriodicReboot as Long)).intdiv(1000L))} days ago"
            state.lastPeriodicSkip = now()
            def nextReboot = getNextPeriodicReboot()
            state.nextPeriodicReboot = nextReboot?.time
            if (nextReboot) log.info "Next periodic reboot expected ${nextReboot.format('yyyy-MM-dd HH:mm', location.timeZone)}"
            return
        }
    } else if (uptimeSecs < requiredSecs) {
        log.info "Periodic reboot check: skipping - hub uptime ${formatDays(uptimeSecs)} days is below the ${formatDays(requiredSecs)} days required for ${freq} reboots"
        state.lastPeriodicSkip = now()
        def nextReboot = getNextPeriodicReboot()
        state.nextPeriodicReboot = nextReboot?.time
        if (nextReboot) log.info "Next periodic reboot expected ${nextReboot.format('yyyy-MM-dd HH:mm', location.timeZone)}"
        return
    }

    log.warn "═══════════════════════════════════════"
    log.warn "PERFORMING PERIODIC SCHEDULED REBOOT${rebuildDatabase ? ' with Database Rebuild' : ''}"
    log.warn "Frequency: ${freq}, Day: ${periodicDayOfWeek}, Hub uptime: ${formatDays(uptimeSecs)} days"
    log.warn "═══════════════════════════════════════"

    def notifyReason = "Periodic ${freq} reboot"
    sendRebootNotification("periodic", notifyReason)

    // Pause briefly to ensure log messages and notifications go out
    pauseExecution(3000)

    if (executeReboot()) {
        state.lastPeriodicReboot = now()
        state.periodicRebootCount = (state.periodicRebootCount ?: 0) + 1
    } else {
        log.error "Periodic reboot command failed - you may need to reboot manually"
        sendRebootNotification("failure", notifyReason)
    }
}

/**
 * Legacy handler name used by v1.3.0 and earlier (runOnce chain). If an old pending
 * schedule fires after upgrading, convert to the new cron schedule and run the
 * normal check (which verifies day and uptime before rebooting).
 */
def performPeriodicReboot() {
    log.info "Legacy periodic reboot schedule fired - migrating to weekly schedule"
    schedulePeriodicReboot()
    periodicRebootCheck()
}

// ──────────────────────────────────────────────
//  Utility
// ──────────────────────────────────────────────

def getDayOfWeekNumber(dayCode) {
    switch(dayCode) {
        case "SUN": return Calendar.SUNDAY
        case "MON": return Calendar.MONDAY
        case "TUE": return Calendar.TUESDAY
        case "WED": return Calendar.WEDNESDAY
        case "THU": return Calendar.THURSDAY
        case "FRI": return Calendar.FRIDAY
        case "SAT": return Calendar.SATURDAY
        default: return Calendar.SUNDAY
    }
}

def getDayName(dayCode) {
    def names = [SUN: "Sunday", MON: "Monday", TUE: "Tuesday", WED: "Wednesday",
                 THU: "Thursday", FRI: "Friday", SAT: "Saturday"]
    return names[dayCode] ?: dayCode
}

def logDebug(msg) {
    if (enableDebug) {
        log.debug msg
    }
}

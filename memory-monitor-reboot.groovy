/**
 *  Memory Monitor & Auto Reboot
 *
 *  Description:
 *  Monitors hub memory usage and automatically reboots when free memory falls below
 *  a configured threshold during a specified time window.
 *
 *  Features:
 *  - Configurable minimum free memory threshold
 *  - Configurable reboot time window
 *  - Manual test reboot function
 *  - Optional database rebuild on reboot
 *  - Periodic scheduled reboots (weekly, fortnightly, monthly)
 *  - Hub uptime display
 *  - Detailed logging
 *  - Memory status tracking
 *
 *  Version: 1.1.0
 *  Author: Derek Osborn
 *  Date: 2026-01-02
 * 
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

definition(
    name: "Memory Monitor & Auto Reboot",
    namespace: "dJOS",
    author: "Derek Osborn",
    description: "Monitors hub memory usage and automatically reboots when free memory falls below threshold during configured time window",
    category: "Utility",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    importUrl: "https://raw.githubusercontent.com/dJOS1475/Hubitat-Memory-Monitor-Auto-Reboot/refs/heads/main/memory-monitor-reboot.groovy"
)

preferences {
    page(name: "mainPage")
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Memory Monitor & Auto Reboot", install: true, uninstall: true) {
        section("Memory Monitoring") {
            paragraph "<b>Version:</b> 1.1.0"
            paragraph "Current Hub Memory Status:"
            def memInfo = getMemoryInfo()
            if (memInfo) {
                paragraph "<b>Free Memory:</b> ${memInfo.free} MB<br>" +
                         "<b>Total Memory:</b> ${memInfo.total} MB<br>" +
                         "<b>Used Memory:</b> ${memInfo.used} MB<br>" +
                         "<b>Usage:</b> ${memInfo.percentUsed}%"
            } else {
                paragraph "Unable to retrieve memory information"
            }
            
            def uptime = getHubUptime()
            if (uptime) {
                paragraph "<br><b>Hub Uptime:</b> ${uptime}"
            }
        }
        
        section("Reboot Settings") {
            input "memoryThreshold", "number", 
                title: "Minimum Free Memory Threshold (MB)", 
                description: "Reboot when free memory falls below this value",
                required: true, 
                defaultValue: 50,
                range: "10..500"
            
            input "enableAutoReboot", "bool",
                title: "Enable Automatic Reboot",
                description: "Allow app to automatically reboot hub when threshold is reached",
                defaultValue: false,
                submitOnChange: true
            
            input "rebuildDatabase", "bool",
                title: "Rebuild Database on Reboot",
                description: "Perform database rebuild when rebooting (may take longer)",
                defaultValue: false
        }
        
        if (enableAutoReboot) {
            section("Reboot Time Window") {
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
        
        section("Periodic Reboot Schedule") {
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
                        "monthly": "Monthly"
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
                
                paragraph "<i>Note: Periodic reboots will use the database rebuild setting configured above.</i>"
            }
        }
        
        section("Monitoring Schedule") {
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
        
        section("Notifications") {
            input "notifyBeforeReboot", "bool",
                title: "Log Warning Before Reboot",
                description: "Log a warning message before initiating reboot",
                defaultValue: true
        }
        
        section("Test Reboot") {
            paragraph "<b>Warning:</b> This will immediately reboot your hub!"
            input "testReboot", "button", title: "Test Reboot Now"
        }
        
        section("Logging") {
            input "enableDebug", "bool",
                title: "Enable Debug Logging",
                defaultValue: false
        }
        
        section("Statistics") {
            if (state.lastCheck) {
                paragraph "<b>Last Check:</b> ${new Date(state.lastCheck).format('yyyy-MM-dd HH:mm:ss')}"
            }
            if (state.lastReboot) {
                paragraph "<b>Last Auto Reboot:</b> ${new Date(state.lastReboot).format('yyyy-MM-dd HH:mm:ss')}"
            }
            if (state.lastPeriodicReboot) {
                paragraph "<b>Last Periodic Reboot:</b> ${new Date(state.lastPeriodicReboot).format('yyyy-MM-dd HH:mm:ss')}"
            }
            if (state.nextPeriodicReboot && enablePeriodicReboot) {
                paragraph "<b>Next Scheduled Reboot:</b> ${new Date(state.nextPeriodicReboot).format('yyyy-MM-dd HH:mm:ss')}"
            }
            if (state.rebootCount) {
                paragraph "<b>Total Auto Reboots:</b> ${state.rebootCount}"
            }
            if (state.periodicRebootCount) {
                paragraph "<b>Total Periodic Reboots:</b> ${state.periodicRebootCount}"
            }
        }
    }
}

def installed() {
    log.info "Memory Monitor & Auto Reboot installed"
    initialize()
}

def updated() {
    log.info "Memory Monitor & Auto Reboot updated"
    unsubscribe()
    unschedule()
    initialize()
}

def uninstalled() {
    log.info "Memory Monitor & Auto Reboot uninstalled"
    unschedule()
}

def initialize() {
    state.rebootCount = state.rebootCount ?: 0
    state.periodicRebootCount = state.periodicRebootCount ?: 0
    
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
    log.info "Threshold: ${memoryThreshold} MB free memory"
    
    if (enableAutoReboot) {
        log.info "Auto-reboot enabled for time window: ${rebootStartTime} to ${rebootEndTime}"
    } else {
        log.info "Auto-reboot is DISABLED"
    }
    
    // Schedule periodic reboots
    if (enablePeriodicReboot && periodicDayOfWeek && periodicRebootTime) {
        schedulePeriodicReboot()
    }
    
    // Do an initial check
    runIn(5, checkMemory)
}

def appButtonHandler(btn) {
    switch(btn) {
        case "testReboot":
            log.warn "TEST REBOOT button pressed - rebooting hub NOW"
            performReboot(true)
            break
    }
}

def checkMemory() {
    state.lastCheck = now()
    
    def memInfo = getMemoryInfo()
    
    if (!memInfo) {
        log.error "Unable to retrieve memory information"
        return
    }
    
    logDebug "Memory check - Free: ${memInfo.free} MB, Used: ${memInfo.used} MB (${memInfo.percentUsed}%)"
    
    // Check if we're below threshold
    if (memInfo.free < memoryThreshold) {
        log.warn "Free memory (${memInfo.free} MB) is below threshold (${memoryThreshold} MB)"
        
        if (enableAutoReboot) {
            if (isWithinRebootWindow()) {
                log.warn "Within reboot time window - initiating reboot"
                performReboot(false)
            } else {
                log.info "Below threshold but outside reboot time window - will reboot when window opens"
            }
        } else {
            log.info "Auto-reboot is disabled - no action taken"
        }
    } else {
        logDebug "Memory levels OK - ${memInfo.free} MB free (threshold: ${memoryThreshold} MB)"
    }
}

def getMemoryInfo() {
    try {
        // Get free OS memory
        def params = [
            uri: "http://127.0.0.1:8080",
            path: "/hub/advanced/freeOSMemory",
            timeout: 5
        ]
        
        def freeMemKB = null
        
        httpGet(params) { resp ->
            if (resp.success) {
                freeMemKB = resp.data.toString().toLong()
            }
        }
        
        if (freeMemKB != null) {
            // Convert KB to MB
            def freeMemMB = Math.round(freeMemKB / 1024)
            
            // Determine total RAM based on free memory
            // All hubs (C-4, C-5, C-7, C-8) have 1GB RAM
            // Only C-8 Pro has 2GB RAM
            // If free > 1000MB = 2GB hub (C-8 Pro)
            // Otherwise = 1GB hub (all other models)
            def totalMemMB
            if (freeMemMB > 1000) {
                totalMemMB = 2048 // 2GB (C-8 Pro)
            } else {
                totalMemMB = 1024 // 1GB (C-4, C-5, C-7, C-8)
            }
            
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
        def params = [
            uri: "http://127.0.0.1:8080",
            path: "/hub/advanced/freeOSMemoryHistory",
            timeout: 5
        ]
        
        def historyData = null
        
        httpGet(params) { resp ->
            if (resp.success) {
                historyData = resp.data.text
            }
        }
        
        if (historyData != null) {
            // Parse CSV data - skip header line and count data lines
            def lines = historyData.split('\n')
            def dataLines = lines.findAll { line -> 
                line.trim() && !line.startsWith('Date/time')
            }
            
            if (dataLines.size() > 0) {
                // Each line represents a 5-minute sample
                def uptimeMinutes = dataLines.size() * 5
                
                def days = (uptimeMinutes / 1440) as int
                def hours = ((uptimeMinutes % 1440) / 60) as int
                def minutes = (uptimeMinutes % 60) as int
                
                def uptimeStr = ""
                if (days > 0) {
                    uptimeStr += "${days} day${days != 1 ? 's' : ''}, "
                }
                if (hours > 0 || days > 0) {
                    uptimeStr += "${hours} hour${hours != 1 ? 's' : ''}, "
                }
                uptimeStr += "${minutes} minute${minutes != 1 ? 's' : ''}"
                
                return uptimeStr
            }
        }
    } catch (Exception e) {
        log.error "Error getting uptime from memory history: ${e.message}"
        logDebug "Uptime error details: ${e}"
    }
    
    return null
}

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
    
    if (!isTest) {
        state.lastReboot = now()
        state.rebootCount = (state.rebootCount ?: 0) + 1
    }
    
    // Pause briefly to ensure log message is written
    pauseExecution(2000)
    
    // Reboot the hub using the local API
    // Requests from 127.0.0.1 bypass Hub Security authentication
    try {
        // Determine reboot path based on database rebuild setting
        def rebootPath = rebuildDatabase ? "/hub/rebuildDatabaseAndReboot" : "/hub/reboot"
        
        def params = [
            uri: "http://127.0.0.1:8080",
            path: rebootPath,
            timeout: 5
        ]
        
        httpPost(params) { resp ->
            if (rebuildDatabase) {
                log.info "Database rebuild and reboot command sent successfully"
            } else {
                log.info "Reboot command sent successfully"
            }
        }
    } catch (Exception e) {
        log.error "Error sending reboot command: ${e.message}"
        log.error "You may need to reboot manually from Settings > Reboot"
    }
}

def schedulePeriodicReboot() {
    // Calculate next reboot time
    def rebootTime = timeToday(periodicRebootTime, location.timeZone)
    def now = new Date()
    def nextReboot = rebootTime
    
    // If the reboot time has already passed today, schedule for next occurrence
    if (nextReboot <= now) {
        use(groovy.time.TimeCategory) {
            nextReboot = nextReboot + 1.day
        }
    }
    
    // Adjust for day of week
    def calendar = Calendar.getInstance(location.timeZone)
    calendar.setTime(nextReboot)
    
    def targetDayOfWeek = getDayOfWeekNumber(periodicDayOfWeek)
    def currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    
    def daysToAdd = targetDayOfWeek - currentDayOfWeek
    if (daysToAdd < 0) {
        daysToAdd += 7
    }
    
    calendar.add(Calendar.DAY_OF_MONTH, daysToAdd)
    nextReboot = calendar.time
    
    // Store next reboot time
    state.nextPeriodicReboot = nextReboot.time
    
    // Schedule the reboot
    runOnce(nextReboot, performPeriodicReboot)
    
    log.info "Periodic reboot scheduled for ${nextReboot.format('yyyy-MM-dd HH:mm:ss')} (${periodicFrequency})"
}

def performPeriodicReboot() {
    log.warn "═══════════════════════════════════════"
    log.warn "PERFORMING PERIODIC SCHEDULED REBOOT"
    log.warn "Frequency: ${periodicFrequency}"
    log.warn "═══════════════════════════════════════"
    
    state.lastPeriodicReboot = now()
    state.periodicRebootCount = (state.periodicRebootCount ?: 0) + 1
    
    // Pause briefly to ensure log message is written
    pauseExecution(2000)
    
    // Reboot the hub
    try {
        def rebootPath = rebuildDatabase ? "/hub/rebuildDatabaseAndReboot" : "/hub/reboot"
        
        def params = [
            uri: "http://127.0.0.1:8080",
            path: rebootPath,
            timeout: 5
        ]
        
        httpPost(params) { resp ->
            if (rebuildDatabase) {
                log.info "Database rebuild and reboot command sent successfully"
            } else {
                log.info "Reboot command sent successfully"
            }
        }
    } catch (Exception e) {
        log.error "Error sending periodic reboot command: ${e.message}"
    }
    
    // Schedule next periodic reboot based on frequency
    def daysUntilNext = 7 // weekly by default
    
    switch(periodicFrequency) {
        case "fortnightly":
            daysUntilNext = 14
            break
        case "monthly":
            daysUntilNext = 28 // Approximate month
            break
        default:
            daysUntilNext = 7
    }
    
    def nextReboot = new Date(state.nextPeriodicReboot + (daysUntilNext * 24 * 60 * 60 * 1000))
    state.nextPeriodicReboot = nextReboot.time
    runOnce(nextReboot, performPeriodicReboot)
    
    log.info "Next periodic reboot scheduled for ${nextReboot.format('yyyy-MM-dd HH:mm:ss')}"
}

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

def logDebug(msg) {
    if (enableDebug) {
        log.debug msg
    }
}

/**
 *	TotalConnect Device API (Async)
 *
 *	Copyright 2017 Oendaril (adapted from Brian Wilson)
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 *	This Device is a based on work by @mhatrey (https://github.com/mhatrey/TotalConnect/blob/master/TotalConnect.groovy)
 *	Brian Wilson (bdwilson/SmartThings-TotalConnect-Device/blob/master/SmartThings-TotalConnect-Device.groovy)
 *	and Jeremy Stroebel (https://github.com/jhstroebel/SmartThings-TCv2)
 *	The goal of this is to expose the TotalConnect Alarm to be used in other routines and in modes.	 To do this, I setup
 *	both lock and switch capabilities for it. Switch On = Armed Stay, Lock On = Armed Away, Switch/Lock Off = Disarm. 
 *	There are no tiles because I don't need them, but feel free to add them.  Also, you'll have to use @mhatrey's tester
 *	tool to get your deviceId and locationId.  See his thread for more info: 
 *	 https://community.smartthings.com/t/new-app-integration-with-honeywell-totalconnect-alarm-monitoring-system/
 *
 */
 include 'asynchttp_v1'
 
preferences {
	// See above ST thread above on how to configure the user/password.	 Make sure the usercode is configured
	// for whatever account you setup. That way, arming/disarming/etc can be done without passing a user code.
	input("userName", "text", title: "Username", description: "Your username for TotalConnect")
	input("password", "password", title: "Password", description: "Your Password for TotalConnect")
	// get this info by using https://github.com/mhatrey/TotalConnect/blob/master/TotalConnectTester.groovy 
	input("deviceId", "text", title: "Device ID - You'll have to look up", description: "Device ID")
	// get this info by using https://github.com/mhatrey/TotalConnect/blob/master/TotalConnectTester.groovy 
	input("locationId", "text", title: "Location ID - You'll have to look up", description: "Location ID")
	input("applicationId", "text", title: "Application ID - It is '14588' currently", description: "Application ID")
	input("applicationVersion", "text", title: "Application Version - use '3.0.32'", description: "Application Version")
}
metadata {
	definition (name: "TotalConnect Device (async)", namespace: "Oendaril", author: "Oendaril") {
        capability "Lock"
        capability "Refresh"
        capability "Switch"
        attribute "status", "string"
	}

    simulator {
        // TODO: define status and reply messages here
    }

    tiles {
		standardTile("toggle", "device.status", width: 2, height: 2) {
			state("unknown", label:'${name}', action:"device.refresh", icon:"st.Office.office9", backgroundColor:"#ffa81e")
			state("Armed Stay", label:'${name}', action:"switch.off", icon:"st.Home.home4", backgroundColor:"#79b821", nextState:"Disarmed")
			state("Disarmed", label:'${name}', action:"lock.lock", icon:"st.Home.home2", backgroundColor:"#a8a8a8", nextState:"Armed Away")
			state("Armed Away", label:'${name}', action:"switch.off", icon:"st.Home.home3", backgroundColor:"#79b821", nextState:"Disarmed")
            state("Arming", label:'${name}', icon:"st.Home.home4", backgroundColor:"#ffa81e")
			state("Disarming", label:'${name}', icon:"st.Home.home2", backgroundColor:"#ffa81e")
		}
		standardTile("statusstay", "device.status", inactiveLabel: false, decoration: "flat") {
			state "default", label:'Arm Stay', action:"switch.on", icon:"st.Home.home4"
		}
		standardTile("statusaway", "device.status", inactiveLabel: false, decoration: "flat") {
			state "default", label:'Arm Away', action:"lock.lock", icon:"st.Home.home3"
		}
		standardTile("statusdisarm", "device.status", inactiveLabel: false, decoration: "flat") {
			state "default", label:'Disarm', action:"switch.off", icon:"st.Home.home2"
		}
		standardTile("refresh", "device.status", inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		main "toggle"
		details(["toggle", "statusaway", "statusstay", "statusdisarm", "refresh"])
	}
}

def tcCommandAsync(path, body, retry, callback) {
	String stringBody = ""
    
    body.each { k, v ->
    	if(!(stringBody == "")) {
        	stringBody += "&" }            
        stringBody += "${k}=${v}"
    }//convert Map to String

	//log.debug "stringBody: ${stringBody}"

    def params = [
		uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/",	
		path: path,
    	body: stringBody,
        requestContentType: "application/x-www-form-urlencoded",
        contentType: "application/xml"
    ]
    
    def handler
        
    switch(path) {
    	case "GetPanelMetaDataAndFullStatusEx":
        	handler = "panel"
            break
        case "GetZonesListInStateEx":
        	handler = "zone"
            break
        case "GetAllAutomationDeviceStatusEx":
        	handler = "automation"
            break
        case "AuthenticateUserLogin":
        	handler = "login"
            break
        case "ArmSecuritySystem":
        case "DisarmSecuritySystem":
        	handler = "refresh"
            break
        default:
        	handler = "none"
            break
    }//define handler based on method called
    
    def data = [
    	path: path,
        body: stringBody,
        handler: handler,
        callback: callback,
        retry: retry
    ] //Data for Async Command.  Params to retry, handler to handle, and retry count if needed
    
    try {
    	asynchttp_v1.post('asyncResponse', params, data)
        //log.debug "Sent asynchhttp_v1.post(responseHandler, ${params}, ${data})"
    } catch (e) {
    	log.error "Something unexpected went wrong in tcCommandAsync: ${e}"
	}//try / catch for asynchttpPost
}//async post command

def asyncResponse(response, data) {    
    if (response.hasError()) {
        log.debug "error response data: ${response.errorData}"
        try {
            // exception thrown if xml cannot be parsed from response
            log.debug "error response xml: ${response.errorXml}"
        } catch (e) {
            log.warn "error parsing xml: ${e}"
        }
        try {
            // exception thrown if json cannot be parsed from response
            log.debug "error response json: ${response.errorJson}"
        } catch (e) {
            log.warn "error parsing json: ${e}"
        }
    }
    
	//log.debug "response received: ${response}"
    response = response.getXml()
 	//log.debug "data:  ${data}"
    try {    
    	def handler = data.get('handler')
        def callback = data.get('callback')        
        
        if(handler == "login") {            
            loginResponse(response.SessionID, callback)
        }
        else {
            //validate response
            def resultCode = response.ResultCode
            def resultData = response.ResultData

            switch(resultCode) {
                case "0": //Successful Command
                case "4500": //Successful Command for Arm Action
                    state.tokenRefresh = now() //we ran a successful command, that will keep the token alive

                    //log.debug "Handler: ${data.get('handler')}"
                    switch(handler) {
                        //update cases
                        case "panel":
                            updateAlarmStatus(getAlarmStatus(response))
                            break
                        //case "zone":
                        //    state.zoneStatus = getZoneStatus(response)
                        //    updateZoneStatuses()
                        //    break
                        //case "automation":
                        //    state.switchStatus = getAutomationDeviceStatus(response)
                        //    updateSwitchStatuses()
                        //    break   
                        case "refresh":
                            refresh()
                            break
                        //case "keepAlive":
                        default:
                            //if its not an update method or keepAlive we don't return anything
                            return
                            break
                    }//switch(data)
                    break
                case "-102":
                    //this means the Session ID is invalid, needs to login and try again
                    log.error "Command Type: ${data} failed with ResultCode: ${resultCode} and ResultData: ${resultData}"
                    log.debug "Attempting to refresh token and try again for method ${callback}"
                    state.token = null
                    login(callback)
                    //login(data.get('callback'))
                    //settings.token = login().toString()
                    //pause(1000) //pause should allow login to complete before trying again.
                    //tcCommandAsync(data.get('path'), data.get('body')) //we don't send retry as 1 since it was a login failure
                    break
                case "4101": //We are unable to connect to the security panel. Please try again later or contact support
                case "4108": //Panel not connected with Virtual Keypad. Check Power/Communication failure
                case "-4002": //The specified location is not valid
                case "-4108": //Cannot establish a connection at this time. Please contact your Security Professional if the problem persists.
                default: //Other Errors 
                    log.error "Command Type: ${data} failed with ResultCode: ${resultCode} and ResultData: ${resultData}"
                    /* Retry causes goofy issues...		
                        if(retry == 0) {
                            pause(2000) //pause 2 seconds (otherwise this hits our rate limit)
                            retry += 1
                            tcCommandAsync(data.get('path'), data.get('body'), retry)
                        }//retry after 3 seconds if we haven't retried before
                    */      
                    break
            }//switch
        }
	} catch (SocketTimeoutException e) {
        //identify a timeout and retry?
		log.error "Timeout Error: $e"
	/* Retry causes goofy issues...		
        if(retry == 0) {
        	pause(2000) //pause 2 seconds (otherwise this hits our rate limit)
           	retry += 1
            tcCommandAsync(data.get('path'), data.get('body'), retry)
		}//retry after 5 seconds if we haven't retried before
	*/
    } catch (e) {
    	log.error "Something unexpected went wrong in asyncResponse: $e"
	}//try / catch for httpPost
}//asyncResponse

def updateAlarmStatus(alarmCode) {
	//log.debug "current status " + state.alarmCode + ", new status " + alarmCode  
    if(state.alarmCode != alarmCode) {
        if (alarmCode == "10200") {
            log.debug "Status is: Disarmed"
            sendEvent(name: "lock", value: "unlocked", displayed: "true", description: "Disarming") 
            sendEvent(name: "switch", value: "off", displayed: "true", description: "Disarming") 
            sendEvent(name: "status", value: "Disarmed", displayed: "true", description: "Refresh: Alarm is Disarmed") 
        } else if (alarmCode == "10203") {
            log.debug "Status is: Armed Stay"
            sendEvent(name: "status", value: "Armed Stay", displayed: "true", description: "Refresh: Alarm is Armed Stay") 
            sendEvent(name: "switch", value: "on", displayed: "true", description: "Arming Stay") 
        } else if (alarmCode =="10201") {
            log.debug "Status is: Armed Away"
            sendEvent(name: "lock", value: "locked", displayed: "true", description: "Arming Away") 
            sendEvent(name: "status", value: "Armed Away", displayed: "true", description: "Refresh: Alarm is Armed Away")
        }
    }
	//logout(token)
	sendEvent(name: "refresh", value: "true", displayed: "true", description: "Refresh Successful") 
    state.alarmCode = alarmCode
}

// Gets Panel Metadata.
def getAlarmStatus(response) {
	String alarmCode
   
	alarmCode = response.PanelMetadataAndStatus.Partitions.PartitionInfo.ArmingState

	state.alarmStatusRefresh = now()
	return alarmCode
} //returns alarmCode

/*
Map getZoneStatus(response) {
    String zoneID
    String zoneStatus
    def zoneMap = [:]
	try {
        response?.ZoneStatus.Zones.ZoneStatusInfoEx.each
        {
            ZoneStatusInfoEx ->
                zoneID = ZoneStatusInfoEx.'@ZoneID'
                zoneStatus = ZoneStatusInfoEx.'@ZoneStatus'
                //bypassable = ZoneStatusInfoEx.'@CanBeBypassed' //0 means no, 1 means yes
                zoneMap.put(zoneID, zoneStatus)
        }//each Zone 

        //log.debug "ZoneNumber: ZoneStatus " + zoneMap
	} catch (e) {
      	log.error("Error Occurred Updating Zones: " + e)
	}// try/catch block
	
    if(zoneMap) {
    	state.zoneStatusRefresh = now()
    	return zoneMap
    } else {
    	return state.zoneStatus
    }//if zoneMap is empty, return current state as a failsafe and don't update zoneStatusRefresh
} //Should return zone information

/*
// Gets Automation Device Status
Map getAutomationDeviceStatus(response) {
	String switchID
	String switchState
    String switchType
    String switchLevel
    Map automationMap = [:]

	try {
        response.AutomationData.AutomationSwitch.SwitchInfo.each
        {
            SwitchInfo ->
                switchID = SwitchInfo.SwitchID
                switchState = SwitchInfo.SwitchState
                //switchType = SwitchInfo.SwitchType
                //switchLevel = SwitchInfo.SwitchLevel
                automationMap.put(switchID,switchState)
*/
            /* Future format to store state information	(maybe store by TC-deviceId-switchId for ease of retrevial?)
                if(switchType == "2") {
                	automationMap[SwitchInfo.SwitchID] = [id: "${SwitchInfo.SwitchID}", switchType: "${SwitchInfo.SwitchType}", switchState: "${SwitchInfo.SwitchState}", switchLevel: "${SwitchInfo.SwitchLevel}"]
                } else {
                	automationMap[SwitchInfo.SwitchID] = [id: "${SwitchInfo.SwitchID}", switchType: "${SwitchInfo.SwitchType}", switchState: "${SwitchInfo.SwitchState}"]
			*//*
        }//SwitchInfo.each

        //log.debug "SwitchID: SwitchState " + automationMap
/*		
		response.data.AutomationData.AutomationThermostat.ThermostatInfo.each
        {
            ThermostatInfo ->
                automationMap[ThermostatInfo.ThermostatID] = [
                    thermostatId: ThermostatInfo.ThermostatID,
                    currentOpMode: ThermostatInfo.CurrentOpMode,
                    thermostatMode: ThermostatInfo.ThermostatMode,
                    thermostatFanMode: ThermostatInfo.ThermostatFanMode,
                    heatSetPoint: ThermostatInfo.HeatSetPoint,
                    coolSetPoint: ThermostatInfo.CoolSetPoint,
                    energySaveHeatSetPoint: ThermostatInfo.EnergySaveHeatSetPoint,
                    energySaveCoolSetPoint: ThermostatInfo.EnergySaveCoolSetPoint,
                    temperatureScale: ThermostatInfo.TemperatureScale,
                    currentTemperture: ThermostatInfo.CurrentTemperture,
                    batteryState: ThermostatInfo.BatteryState]
        }//ThermostatInfo.each
*/
    
/*		
		response.data.AutomationData.AutomationLock.LockInfo_Transitional.each
        {
            LockInfo_Transitional ->
                automationMap[LockInfo_Transitional.LockID] = [
                    lockID: LockInfo_Transitional.LockID,
                    lockState: LockInfo_Transitional.LockState,
                    batteryState: LockInfo_Transitional.BatteryState]                    ]
        }//LockInfo_Transitional.each
*/
/*
	} catch (e) {
      	log.error("Error Occurred Updating Automation Devices: " + e)
	}// try/catch block
	
    if(automationMap) {
    	state.automationStatusRefresh = now()
    	return automationMap
    } else {
    	return state.automationStatus
    }//if automationMap is empty, return current state as a failsafe and don't update automationStatusRefresh
} //Should return switch state information for all SwitchIDs
*/
def isTokenValid() {
	//return false if token doesn't exist
    if(state.token == null) {
    	return false 
    }
    
    Long timeSinceRefresh = now() - state.tokenRefresh
    
    //return false if time since refresh is over 4 minutes (likely timeout)       
    if(timeSinceRefresh > 240000) {
    	state.token = null
    	return false 
    }
    
    return true
} // This is a logical check only, assuming known timeout values and clearing token on loggout.  This method does no testing of the actua


// Login Function. Returns SessionID for rest of the functions
def login(callback) {
	//log.debug "Executed login"    
	//def paramsLogin = [
	//	uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/AuthenticateUserLogin",
	//	body: [userName: settings.userName , password: settings.password, ApplicationID: settings.applicationId, ApplicationVersion: settings.applicationVersion]
	//]
	//httpPost(paramsLogin) { responseLogin ->
	//	token = responseLogin.data.SessionID 
	//}
    tcCommandAsync("AuthenticateUserLogin",  [userName: settings.userName , password: settings.password, ApplicationID: settings.applicationId, ApplicationVersion: settings.applicationVersion], 0, callback)
}

def loginResponse(token, callback) {                                  
    if(token != null) {
    	//log.debug "new token is ${token}"
        state.token = "${token}"
    }
    
    switch(callback) {
        case "refresh":
        	refresh()
        	break
        case "refreshAuthenticated":
        	refreshAuthenticated()
        	break
        case "armAway":
        	armAway()
        	break
        case "armAwayAuthenticated":
        	armAwayAuthenticated()
        	break
        case "armStay":
        	armStay()
        	break
        case "armStayAuthenticated":
        	armStayAuthenticated()
        	break
        case "getPanelMetadata":
        	getPanelMetadata()
        	break
        case "disarm":
        	disarm()
        	break
        default:
            return	
        break
    }
}

// Logout Function. Called after every mutational command. Ensures the current user is always logged Out.
def logout(token) {
	//log.debug "During logout - ${token}"
	//def paramsLogout = [
	//	uri: "https://rs.alarmnet.com/TC21API/TC2.asmx/Logout",
	//	body: [SessionID: token]
	//]
	//httpPost(paramsLogout) { responseLogout ->
	//	log.debug "Smart Things has successfully logged out"
	//}  
}

// Gets Panel Metadata. Takes token & location ID as an argument
def getPanelMetadata() {
	tcCommandAsync("GetPanelMetaDataAndFullStatusEx", [SessionID: state.token, LocationID: settings.locationId, LastSequenceNumber: 0, LastUpdatedTimestampTicks: 0, PartitionID: 1], 0, "getPanelMetadata") //This updates panel status
}

// Arm Function. Performs arming function
def armAway() {		   
	if(isTokenValid())
    	armAwayAuthenticated()
    else {
		login(armAwayAuthenticated)
    }
}

def armAwayAuthenticated() {
	tcCommandAsync("ArmSecuritySystem", [SessionID: state.token, LocationID: settings.locationId, DeviceID: settings.deviceId, ArmType: 0, UserCode: '-1'], 0 , "armAway")	
}

def armStay() {		   
	if(isTokenValid())
    	armStayAuthenticated()
    else {
		login(armStayAuthenticated)
    }
}

def armStayAuthenticated() {		
	tcCommandAsync("ArmSecuritySystem", [SessionID: state.token, LocationID: settings.locationId, DeviceID: settings.deviceId, ArmType: 1, UserCode: '-1'], 0, "armStay")
}

def disarm() {		   
	if(isTokenValid())
    	disarmAuthenticated()
    else {
		login(disarmAuthenticated)
    }
}

def disarmAuthenticated() {
	tcCommandAsync("DisarmSecuritySystem", [SessionID: state.token, LocationID: settings.locationId, DeviceID: settings.deviceId, UserCode: '-1'], 0, "disarm")
}

def refresh() {		   
	//log.debug "is token not null? ${state.token != null} and is token here? ${state.token}"
	if(isTokenValid()) {
    	refreshAuthenticated()
    }
    else {
    	login(refreshAuthenticated)
    }
}

def refreshAuthenticated() {
	//log.debug "Doing refresh"
	//httpPost(paramsArm) // Arming function in stay mode
	getPanelMetadata() // Gets AlarmCode
}

// handle commands
def lock() {
	//log.debug "Executing 'Arm Away'"
	armAway()
	sendEvent(name: "lock", value: "locked", displayed: "true", description: "Arming Away") 
	sendEvent(name: "status", value: "Arming", displayed: "true", description: "Updating Status: Arming System")
	//runIn(15,refresh)
}

def unlock() {
	//log.debug "Executing 'Disarm'"
	disarm()
	sendEvent(name: "lock", value: "unlocked", displayed: "true", description: "Disarming") 
	sendEvent(name: "status", value: "Disarming", displayed: "true", description: "Updating Status: Disarming System") 
	//runIn(15,refresh)
}

def on() {
	//log.debug "Executing 'Arm Stay'"
	armStay()
	sendEvent(name: "switch", value: "on", displayed: "true", description: "Arming Stay") 
	sendEvent(name: "status", value: "Arming", displayed: "true", description: "Updating Status: Arming System") 
	//runIn(15,refresh)
}

def off() {
	//log.debug "Executing 'Disarm'"
	disarm()
	sendEvent(name: "switch", value: "off", displayed: "true", description: "Disarming") 
	sendEvent(name: "status", value: "Disarmed", displayed: "true", description: "Updating Status: Disarming System") 
	//runIn(15,refresh)
}

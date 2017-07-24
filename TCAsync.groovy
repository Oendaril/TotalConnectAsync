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

def tcCommandAsync(path, body, retry, source, callback) {
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
    
    def data = [
    	path: path,
        body: stringBody,
        source: source,
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
        def source = data.get('source')
        def callback = data.get('callback')
        def retry = data.get('retry')
        def path = data.get('path')
        
        def resultCode = response.ResultCode
        def resultData = response.ResultData
        
        if(source == "login") {
            if(resultCode == "0") {
            	loginResponse(response.SessionID, callback)
            }
            else {
            	//Don't retry logins, as that just means something is bad with the credentials itself
                log.error "Command Type: ${data} failed with ResultCode: ${resultCode} and ResultData: ${resultData}"
            }
        }
        else {
            //validate response
            switch(resultCode) {
                case "0": //Successful Command
                case "4500": //Successful Command for Arm Action
                    state.tokenRefresh = now() //we ran a successful command, that will keep the token alive
                	//update cases, this is used for synchronous parsing of response data only. Async callbacks are used in callback param
                    switch(source) {
                        case "getPanelMetadata":
                            updateAlarmStatus(getAlarmStatus(response))
                            break
                        default:
                            //if its not an update method or keepAlive we don't return anything
                            return
                            break
                    }//switch(data)
                    
                    if(callback != null) {
                    	handleCallback(callback)
                    }
                    break
                case "-102":
                    //this means the Session ID is invalid, needs to login and try again
                    log.error "Command Type: ${data} failed with ResultCode: ${resultCode} and ResultData: ${resultData}"
                    log.debug "Attempting to refresh token and try again for method ${callback}"
                    if(retry == 0) {                        
                        state.token = null
                        retry+=1
                        login(source, retry)
                    }
                    break
                case "4101": //We are unable to connect to the security panel. Please try again later or contact support
                case "4108": //Panel not connected with Virtual Keypad. Check Power/Communication failure
                case "-4002": //The specified location is not valid
                case "-4108": //Cannot establish a connection at this time. Please contact your Security Professional if the problem persists.
                default: //Other Errors 
                    log.error "Command Type: ${data} failed with ResultCode: ${resultCode} and ResultData: ${resultData}"
                    break
            }//switch
        }
	} catch (SocketTimeoutException e) {
        //identify a timeout and retry?
		log.error "Timeout Error: $e"
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

def isTokenValid() {
	def isValid = true
    if(state.token == null) {
    	isValid = false 
    }
    else {    
        Long timeSinceRefresh = now() - (state.tokenRefresh != null ? state.tokenRefresh : 0)

        //return false if time since refresh is over 4 minutes (likely timeout)       
        if(timeSinceRefresh > 240000) {
            state.token = null
            isValid = false 
        }
    }
    
    return isValid
} // This is a logical check only, assuming known timeout values and clearing token on loggout.  This method does no testing of the actua


def login(callback, retry) {
	//log.debug "Executed login"
    tcCommandAsync("AuthenticateUserLogin",  [userName: settings.userName , password: settings.password, ApplicationID: settings.applicationId, ApplicationVersion: settings.applicationVersion], retry != null ? retry : 0, "login", callback)
}

def loginResponse(token, callback) {                                  
    if(token != null) {
    	//log.debug "new token is ${token}"
        state.token = "${token}"
        state.tokenRefresh = now()
    }
    
    handleCallback(callback); 
}

def logout() {
    tcCommandAsync("Logout",  [SessionID: state.token], 0, "logout", null)
}

// Gets Panel Metadata. Takes token & location ID as an argument
def getPanelMetadata() {
	tcCommandAsync("GetPanelMetaDataAndFullStatusEx", [SessionID: state.token, LocationID: settings.locationId, LastSequenceNumber: 0, LastUpdatedTimestampTicks: 0, PartitionID: 1], 0, "getPanelMetadata", null) //This updates panel status
}

// Arm Function. Performs arming function
def armAway() {		   
	if(isTokenValid())
    	armAwayAuthenticated()
    else {
		login(armAwayAuthenticated, 0)
    }
}

def armAwayAuthenticated() {
	tcCommandAsync("ArmSecuritySystem", [SessionID: state.token, LocationID: settings.locationId, DeviceID: settings.deviceId, ArmType: 0, UserCode: '-1'], 0 , "armAway", null)	
}

def armStay() {		   
	if(isTokenValid())
    	armStayAuthenticated()
    else {
		login(armStayAuthenticated, 0)
    }
}

def armStayAuthenticated() {		
	tcCommandAsync("ArmSecuritySystem", [SessionID: state.token, LocationID: settings.locationId, DeviceID: settings.deviceId, ArmType: 1, UserCode: '-1'], 0, "armStay", null)
}

def disarm() {		   
	if(isTokenValid())
    	disarmAuthenticated()
    else {
		login(disarmAuthenticated, 0)
    }
}

def disarmAuthenticated() {
	tcCommandAsync("DisarmSecuritySystem", [SessionID: state.token, LocationID: settings.locationId, DeviceID: settings.deviceId, UserCode: '-1'], 0, "disarm", null)
}

def refresh() {		   
	//log.debug "is token not null? ${state.token != null} and is token here? ${state.token}"
	if(isTokenValid()) {
    	getPanelMetadata()
    }
    else {
    	login(getPanelMetadata, 0)
    }
}

def handleCallback(callback) {
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
        case "disarmAuthenticated":
        	disarmAuthenticated()
            break
        default:
            return	
        break
    }
}

// handle commands
def lock() {
	//log.debug "Executing 'Arm Away'"
	armAway()
	sendEvent(name: "lock", value: "locked", displayed: "true", description: "Arming Away") 
	sendEvent(name: "status", value: "Arming", displayed: "true", description: "Updating Status: Arming System")
	runIn(60,refresh)
}

def unlock() {
	//log.debug "Executing 'Disarm'"
	disarm()
	sendEvent(name: "lock", value: "unlocked", displayed: "true", description: "Disarming") 
	sendEvent(name: "status", value: "Disarming", displayed: "true", description: "Updating Status: Disarming System") 
	runIn(60,refresh)
}

def on() {
	//log.debug "Executing 'Arm Stay'"
	armStay()
	sendEvent(name: "switch", value: "on", displayed: "true", description: "Arming Stay") 
	sendEvent(name: "status", value: "Arming", displayed: "true", description: "Updating Status: Arming System") 
	runIn(60,refresh)
}

def off() {
	//log.debug "Executing 'Disarm'"
	disarm()
	sendEvent(name: "switch", value: "off", displayed: "true", description: "Disarming") 
	sendEvent(name: "status", value: "Disarmed", displayed: "true", description: "Updating Status: Disarming System") 
	runIn(60,refresh)
}

/**
 *  TotalConnect Location and Device Details (async)
 *
 *  Copyright 2017 Yogesh Mhatre, Brian Wilson, Oendaril
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

/** 
 * Most of this is borrowed from: https://github.com/mhatrey/TotalConnect/blob/master/TotalConnectTester.groovy
 * Goal if this is to return your Location ID and Device ID to use with my Total Connect Device located here:
 *  https://github.com/bdwilson/SmartThings-TotalConnect-Device
 *
 * To install, go to the IDE: https://graph.api.smartthings.com/ide/app/create,
 * Create a new SmartApp from Code, Save, Publish, Install at your location and
 * enter your credentials for your TotalConnect account.  The outputs will be emitted
 * to your live logging area with the locationid and deviceid.
 */
 include 'asynchttp_v1'

definition(
   	 	name: "TotalConnect Location and Device Details (async)",
    	namespace: "Oendaril",
    	author: "Oendaril",
    	description: "Total Connect App to show you your Location and Device ID's for use with Total Connect Device (async)",
    	category: "My Apps",
   	 	iconUrl: "https://s3.amazonaws.com/yogi/TotalConnect/150.png",
    	iconX2Url: "https://s3.amazonaws.com/yogi/TotalConnect/300.png"
)

preferences {
        section ("User Credentials"){
    		input("userName", "text", title: "Username", description: "Your username for TotalConnect")
    		input("password", "password", title: "Password", description: "Your Password for TotalConnect")
	}
}

// End of Page Functions

def installed(){
	getDetails()
}
def updated(){
	unsubscribe()
    getDetails()
}

// Login Function. Returns SessionID for rest of the functions
def login(callback) {
	def applicationId="14588"
	def applicationVersion="1.0.34"
	//log.debug "Executed login"    
    tcCommandAsync("AuthenticateUserLogin",  [userName: settings.userName , password: settings.password, ApplicationID: applicationId, ApplicationVersion: applicationVersion], 0, callback)
}

def loginResponse(token, callback) {                                  
    if(token != null) {
    	//log.debug "new token is ${token}"
        state.token = "${token}"
    }
    
    switch(callback) {
        case "getSessionDetails":
        	getSessionDetails()
            break
        default:
            return	
        break
    }
}

def logout() {
    tcCommandAsync("Logout",  [SessionID: state.token], 0, "logout")
} //Takes token as argument

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
        case "AuthenticateUserLogin":
        	handler = "login"
            break
        case "GetSessionDetails":
        	handler = "details"
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
                        case "details":           
                            def locationId
                            def deviceId
                            def locationName
                            Map locationMap = [:]
                            Map deviceMap = [:]	
                            response.Locations.LocationInfoBasic.each
                            {
                                LocationInfoBasic ->
                                locationName = LocationInfoBasic.LocationName
                                locationId = LocationInfoBasic.LocationID
                                deviceId = LocationInfoBasic.DeviceList.DeviceInfoBasic.DeviceID
                                locationMap["${locationName}"] = "${locationId}"
                                deviceMap["${locationName}"] = "${deviceId}"
                            }
							log.debug "Location ID map is " + locationMap + " & Device ID map is " + deviceMap + " (typically only use the last 6 numbers)"
                        	break
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

def getSessionDetails() {
	def applicationId="14588"
	def applicationVersion="1.0.34"
	tcCommandAsync("GetSessionDetails", [SessionID: state.token, ApplicationID: applicationId, ApplicationVersion: applicationVersion], 0, "getSessionDetails") //This updates panel status
}

def getDetails() {
	login(getSessionDetails)
}

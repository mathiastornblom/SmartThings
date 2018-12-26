/**
 *
 *  NEXA AN-179 In-Wall relay
 *
 *  github: Mathias Törnblom (mathiastornblom)
 *  Date: 2018-12-25
 *  Copyright Mathias Törnblom
 *
 *  Includes all configuration parameters and ease of advanced configuration.
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

metadata {
	definition (name: "NEXA AN-179 In-Wall relay", namespace: "mathiastornblom", author: "Mathias Törnblom", runLocally: false, minHubCoreVersion: '000.017.0012', executeCommandsLocally: false)
	{
		capability "Actuator"
		capability "Switch"
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
		capability "Health Check"
		capability "Relay Switch"
		
        fingerprint mfr: "0060", prod: "0004", model: "0008"
		fingerprint type: "1001", cc: "5E,86,72,5A,85,59,73,20,71,70,25,27,7A"
		
    }

	// simulator metadata
	simulator {
		status "on":  "command: 2003, payload: FF"
		status "off": "command: 2003, payload: 00"

		// reply messages
		reply "2001FF,delay 100,2502": "command: 2503, payload: FF"
		reply "200100,delay 100,2502": "command: 2503, payload: 00"
	}

    preferences {
        input description: "Once you change values on this page, the corner of the \"configuration\" icon will change orange until all configuration parameters are updated.", title: "Settings", displayDuringSetup: false, type: "paragraph", element: "paragraph"
        generate_preferences(configuration_model())
    }

    tiles {
        multiAttributeTile(name:"switch", type: "generic", width: 6, height: 4, canChangeIcon: true) {
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#00a0dc", nextState:"turningOff"
                attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#00a0dc", nextState:"turningOff"
            }
            tileAttribute ("statusText", key: "SECONDARY_CONTROL") {
                attributeState "statusText", label:'${currentValue}'
            }
        }
        standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        standardTile("configure", "device.needUpdate", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "NO" , label:'', action:"configuration.configure", icon:"st.secondary.configure"
            state "YES", label:'', action:"configuration.configure", icon:"https://github.com/mathiastornblom/SmartThings/raw/master/devicetypes/nexa/nexa_an-179.src/configure%402x.png"
        }
    }
}

def installed() {
    logging("installed()", 1)
    command(zwave.manufacturerSpecificV1.manufacturerSpecificGet())
}

def parse(String description) {
    def result = []
    def cmd = zwave.parse(description, [0x20: 1, 0x70: 1])
    if (cmd) {
        result += zwaveEvent(cmd)
		logging("Parsed ${cmd} to ${result.inspect()}", 1)
    }
    if (result?.name == 'hail' && hubFirmwareLessThan("000.011.00602")) {
        result = [result, response(zwave.basicV1.basicGet())]
        logging("Was hailed: requesting state update", 2)
    } else {
        logging("Parse returned ${result?.descriptionText}", 1)
    }
    return result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
    logging("BasicReport ${cmd}", 2)
    createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "physical")
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    logging("SwitchBinaryReport ${cmd}", 2)
    createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "digital")
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
    logging("ManufacturerSpecificReport ${cmd}", 2)
    if (state.manufacturer != cmd.manufacturerName) {
        updateDataValue("manufacturer", cmd.manufacturerName)
    }

    createEvent(name: "manufacturer", value: cmd.manufacturerName)
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
    update_current_properties(cmd)
    logging("${device.displayName} parameter '${cmd.parameterNumber}' with a byte size of '${cmd.size}' is set to '${cmd2Integer(cmd.configurationValue)}'", 2)
}

def zwaveEvent(physicalgraph.zwave.commands.hailv1.Hail cmd) {
	[name: "hail", value: "hail", descriptionText: "Switch button was pressed", displayed: false]
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    // Handles all Z-Wave commands we aren't interested in
    logging("$device.displayName: Unhandled: $cmd", 2)
    [:]
}

def on() {
    logging("on()", 1)
    commands([
        zwave.basicV1.basicSet(value: 0xFF),
        zwave.switchBinaryV1.switchBinaryGet()
    ])
}

def off() {
    logging("off()", 1)
    commands([
        zwave.basicV1.basicSet(value: 0x00),
        zwave.switchBinaryV1.switchBinaryGet()
    ])
}

def poll() {
    logging("poll()", 1)
    command(zwave.switchBinaryV1.switchBinaryGet())
}

def ping() {
    logging("ping()", 1)
    refresh()
}

def refresh() {
    logging("refresh()", 1)
    commands([
        zwave.switchBinaryV1.switchBinaryGet(),
        zwave.manufacturerSpecificV1.manufacturerSpecificGet()
    ])
}

def configure() {
    logging("configure()", 1)
    def cmds = []
    cmds = update_needed_settings()
    if (cmds != []) commands(cmds)
}

def updated() {
    logging("updated()", 1)
    def cmds = []
    cmds = update_needed_settings()
    sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
    sendEvent(name:"needUpdate", value: device.currentValue("needUpdate"), displayed:false, isStateChange: true)
    if (cmds != []) response(commands(cmds))
}

def generate_preferences(configuration_model) {
    def configuration = parseXml(configuration_model)

    configuration.Value.each {
        if(it.@hidden != "true" && it.@disabled != "true") {
            switch(it.@type) {
                case ["number"]:
                    input "${it.@index}", "number",
                        title:"${it.@label}\n" + "${it.Help}",
                        range: "${it.@min}..${it.@max}",
                        defaultValue: "${it.@value}",
                        displayDuringSetup: "${it.@displayDuringSetup}"
                    break
                case "list":
                    def items = []
                    it.Item.each { items << ["${it.@value}":"${it.@label}"] }
                    input "${it.@index}", "enum",
                        title:"${it.@label}\n" + "${it.Help}",
                        defaultValue: "${it.@value}",
                        displayDuringSetup: "${it.@displayDuringSetup}",
                        options: items
                    break
                case "decimal":
                    input "${it.@index}", "decimal",
                        title:"${it.@label}\n" + "${it.Help}",
                        range: "${it.@min}..${it.@max}",
                        defaultValue: "${it.@value}",
                        displayDuringSetup: "${it.@displayDuringSetup}"
                    break
                case "boolean":
                    input "${it.@index}", "boolean",
                        title:"${it.@label}\n" + "${it.Help}",
                        defaultValue: "${it.@value}",
                        displayDuringSetup: "${it.@displayDuringSetup}"
                    break
            }
        }
    }
}

/*  Code has elements from other community source @CyrilPeponnet (Z-Wave Parameter Sync). */

def update_current_properties(cmd) {
    def currentProperties = state.currentProperties ?: [:]

    currentProperties."${cmd.parameterNumber}" = cmd.configurationValue

    def parameterSettings = parseXml(configuration_model()).Value.find{it.@index == "${cmd.parameterNumber}"}

    if (settings."${cmd.parameterNumber}" != null || parameterSettings.@hidden == "true") {
        if (convertParam(cmd.parameterNumber, parameterSettings.@hidden != "true"? settings."${cmd.parameterNumber}" : parameterSettings.@value) == cmd2Integer(cmd.configurationValue)) {
            sendEvent(name:"needUpdate", value:"NO", displayed:false, isStateChange: true)
        } else {
            sendEvent(name:"needUpdate", value:"YES", displayed:false, isStateChange: true)
        }
    }

    state.currentProperties = currentProperties
}

def update_needed_settings() {
    def cmds = []
    def currentProperties = state.currentProperties ?: [:]

    def configuration = parseXml(configuration_model())
    def isUpdateNeeded = "NO"

    configuration.Value.each {
        if ("${it.@setting_type}" == "zwave" && it.@disabled != "true") {
            if (currentProperties."${it.@index}" == null) {
                if (it.@setonly == "true") {
                    logging("Parameter ${it.@index} will be updated to " + convertParam(it.@index.toInteger(), settings."${it.@index}"? settings."${it.@index}" : "${it.@value}"), 2)
                    def convertedConfigurationValue = convertParam(it.@index.toInteger(), settings."${it.@index}"? settings."${it.@index}" : "${it.@value}")
                    cmds << zwave.configurationV1.configurationSet(configurationValue: integer2Cmd(convertedConfigurationValue, it.@byteSize.toInteger()), parameterNumber: it.@index.toInteger(), size: it.@byteSize.toInteger())
                } else {
                    isUpdateNeeded = "YES"
                    logging("Current value of parameter ${it.@index} is unknown", 2)
                    cmds << zwave.configurationV1.configurationGet(parameterNumber: it.@index.toInteger())
                }
            } else if ((settings."${it.@index}" != null || "${it.@hidden}" == "true") && cmd2Integer(currentProperties."${it.@index}") != convertParam(it.@index.toInteger(), "${it.@hidden}" != "true"? settings."${it.@index}" : "${it.@value}")) {
                isUpdateNeeded = "YES"
                logging("Parameter ${it.@index} will be updated to " + convertParam(it.@index.toInteger(), "${it.@hidden}" != "true"? settings."${it.@index}" : "${it.@value}"), 2)
                def convertedConfigurationValue = convertParam(it.@index.toInteger(), "${it.@hidden}" != "true"? settings."${it.@index}" : "${it.@value}")
                cmds << zwave.configurationV1.configurationSet(configurationValue: integer2Cmd(convertedConfigurationValue, it.@byteSize.toInteger()), parameterNumber: it.@index.toInteger(), size: it.@byteSize.toInteger())
                cmds << zwave.configurationV1.configurationGet(parameterNumber: it.@index.toInteger())
            }
        }
    }

    sendEvent(name:"needUpdate", value: isUpdateNeeded, displayed:false, isStateChange: true)
    return cmds
}

def convertParam(number, value) {
    def parValue
    switch (number) {
        case 110:
            if (value < 0)
                parValue = value * -1 + 1000
            else
                parValue = value
            break
        default:
            parValue = value
            break
    }
    return parValue.toInteger()
}

private def logging(message, level) {
    if (logLevel != "0") {
        switch (logLevel) {
            case "1":
                if (level > 1)
                    log.debug "$message"
                break
            case "99":
                log.debug "$message"
                break
        }
    }
}

/**
* Convert 1 and 2 bytes values to integer
*/
def cmd2Integer(array) {
    switch(array.size()) {
        case 1:
            array[0]
            break
        case 2:
            ((array[0] & 0xFF) << 8) | (array[1] & 0xFF)
            break
        case 3:
            ((array[0] & 0xFF) << 16) | ((array[1] & 0xFF) << 8) | (array[2] & 0xFF)
            break
        case 4:
            ((array[0] & 0xFF) << 24) | ((array[1] & 0xFF) << 16) | ((array[2] & 0xFF) << 8) | (array[3] & 0xFF)
            break
    }
}

def integer2Cmd(value, size) {
    switch(size) {
        case 1:
            [value]
            break
        case 2:
            def short value1 = value & 0xFF
            def short value2 = (value >> 8) & 0xFF
            [value2, value1]
            break
        case 3:
            def short value1 = value & 0xFF
            def short value2 = (value >> 8) & 0xFF
            def short value3 = (value >> 16) & 0xFF
            [value3, value2, value1]
            break
        case 4:
            def short value1 = value & 0xFF
            def short value2 = (value >> 8) & 0xFF
            def short value3 = (value >> 16) & 0xFF
            def short value4 = (value >> 24) & 0xFF
            [value4, value3, value2, value1]
            break
    }
}

private command(physicalgraph.zwave.Command cmd) {
    if (state.sec) {
        zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
    } else {
        cmd.format()
    }
}

private commands(commands, delay=500) {
    delayBetween(commands.collect{ command(it) }, delay)
}

private channelNumber(String dni) {
    dni.split("-i")[-1] as Integer
}


def configuration_model() {
'''
<configuration>
<Value type="list" byteSize="1" index="4" label="Switch 1 switching type" min="0" max="1" value="1" setting_type="zwave" fw="">
 <Help>
Range: 0 to 1
Default: Toggle switch
</Help>
        <Item label="Momentary switch (Push button)" value="0" />
        <Item label="Toggle switch (SPDT)" value="1" />
</Value>
<Value type="list" byteSize="1" index="2" label="Remember the last status" min="0" max="1" value="0" setting_type="zwave" fw="">
 <Help>
Range: 0 to 1
Default: 0 (Do not remember)
</Help>
        <Item label="Do not remember" value="0" />
        <Item label="Remember" value="1" />
</Value>
<Value type="number" byteSize="1" index="3" label="The delaying time to report to Group 1" min="3" max="25" value="3" setting_type="zwave" fw="">
 <Help>
Range: 2 to 25
Default: 3 (seconds)
</Help>
</Value>
</configuration>
'''
}

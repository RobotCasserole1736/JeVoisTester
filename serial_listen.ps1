#########################################################################################
# Copyright (C) 2017 FRC Team 1736 Robot Casserole - www.robotcasserole.org
#########################################################################################
# Non-legally-binding statement from Team 1736:
#  Thank you for taking the time to read through our software! We hope you
#   find it educational and informative! 
#  Please feel free to snag our software for your own use in whatever project
#   you have going on right now! We'd love to be able to help out! Shoot us 
#   any questions you may have, all our contact info should be on our website
#   (listed above).
#  If you happen to end up using our software to make money, that is wonderful!
#   Robot Casserole is always looking for more sponsors, so we'd be very appreciative
#   if you would consider donating to our club to help further STEM education.
#########################################################################################
#
# serial_listen.ps1 - Opens the JeVois serial port and dumps to console
#
# Algorithm:
# 1) Search all COM ports for JeVois (unopened and responds correctly to ping)
# 2) Dump all output to the console
#########################################################################################


#########################################################################################
$jevois_port_name = ""


# Get all port names present on the PC
$port_name_list = [System.IO.Ports.SerialPort]::getportnames()


#Strategy: for each port on the system, attempt to open it and send "ping".
# if it responds with ALIVE, we assume we have found the JeVois.
foreach($port_name in $port_name_list)
{
    echo "[serial_listen] Checking $port_name"
    try {
        #Try to open & configure the port
        $port= new-Object System.IO.Ports.SerialPort $port_name,115200,None,8,one
        $port.Open()
        $port.ReadTimeout = 500
    }
    catch {
        echo "[serial_listen] Failed to open and configure $port_name"
        if($port.IsOpen){
            $port.Close()
        }
        continue
    }

    try {
        #Assuming the port open works, send ping.
        # Note if we send this to a poorly-implemented device that is not a JeVois, 
        # we may cause it to have bad behavior. Hopefully that will not be the case. 
        # If it is for you, just kill off part 1 of this code and hardcode the
        # JeVois COM port.
        $port.WriteLine("ping")
        $response = $port.ReadLine()
        if(($response -like "ALIVE*") -or ($response -like "{*}*")-or ($response -like "OK*")) 
        {
            #Expect that a real jevois will respond with ALIVE, OK, or a packet.
            $jevois_port_name = $port_name
            echo "[serial_listen] Found JeVois on $jevois_port_name"
            break
        }
        else {
            echo "[serial_listen] Incorrect Response from $port_name"
        }
        $port.Close();
    } catch {
        echo "[serial_listen] Failed to get proper response on $port_name"
        if($port.IsOpen){
            $port.Close()
        }
        continue
    }
}

if($jevois_port_name -eq ""){
    Write-Error "[serial_listen] Failed to open JeVois serial port - is it plugged in and not already open?"
    exit -1
}

echo "[serial_listen] Jevois Location complete"

#Configure Jevois

#force all log messages to USB
$port.WriteLine("setpar serlog USB")

#Port will already be open from above code
#adjust the read timeout to be nicer, since non-ping commands might take longer to execute.
$port.ReadTimeout = 10

echo "[serial_listen] Starting serial listening. Press Q to quit."

while($true){
    try{
        echo $port.ReadLine()
    } catch {
        Start-Sleep -m 50
    }

    # Check if ctrl+C was pressed and quit if so.
    if ([console]::KeyAvailable) {
        $key = [system.console]::readkey($true)
        if (($key.key -eq "Q")) {
            echo "Quitting, user pressed Q..."
            break
        }
    }
    
}




if($port.IsOpen){
    echo "[serial_listen] Closing port..."
    $port.Close()
}
pause
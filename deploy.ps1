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
# deploy.ps1 - sends locallly developed script to a connected JeVois camera
#
# Algorithm:
# 0) Verify python script syntax (compile with a local version of python)
# 1) Search all COM ports for JeVois (unopened and responds correctly to ping)
# 2) Send commands to put the JeVois in USB file transfer mode
# 3) Copy the local development file over to the JeVois
# 4) Eject the USB drive and close the 
#########################################################################################


#########################################################################################
# Configuration - Edit these values to taste for your team.

#Local file - the one you edit on your PC
$py_source_file = ".\moduleSrc\CasseroleVision.py"

#Module Destination folder - the place you want your module to show up on the JeVois filesystem
# The JeVois drive letter will be automatically prepended to this path, no need to hard code it.
$mod_dest_path = "modules\JeVois\CasseroleVision"

#Configuration source files - These probably won't ever be changed
$init_cfg_source_file = ".\moduleSrc\initscript.cfg"
$params_cfg_source_file = ".\moduleSrc\params.cfg"
$vidmap_cfg_source_file = ".\moduleSrc\videomappings.cfg"

#Configuration Destination folder - the place you want the cfg files to show up on the JeVois filesystem
# The JeVois drive letter will be automatically prepended to this path, no need to hard code it.
$cfg_dest_path = "config"

#Python things. If your computers have a nice version of python on the system path,
# these defaults should be fine. If not, feel free to change.
# They're only used to verify script syntax before deploying (development velocity!)
# so don't feel too bad if you just set them to bogus values and ignore the warnings.
$python_exe = "python"
$python_compile = "py_compile"
#########################################################################################



$jevois_port_name = ""

echo "========================================================================"
echo "Verifying python script syntax..."

Remove-Item "__pycache__" -Recurse -ErrorAction Ignore
#Assemble a command to validate the script syntax via the "One True Way"
#see https://stackoverflow.com/questions/4284313/how-can-i-check-the-syntax-of-python-script-without-executing-it
$python_cmd = $python_exe + " -m " + $python_compile + " " + $py_source_file 
#run said command
Invoke-Expression $python_cmd
$ret_val = $LASTEXITCODE
Remove-Item "__pycache__" -Recurse -ErrorAction Ignore

if($ret_val)
{
    Write-Warning "Warning: Problems verifying python syntax. "
    Write-Warning ".....But, We'll deploy anyway."
}

echo "Verification stage complete"

echo "========================================================================"
echo "Attempting to find attached JeVois..."


# Get all port names present on the PC
$port_name_list = [System.IO.Ports.SerialPort]::getportnames()


#Strategy: for each port on the system, attempt to open it and send "ping".
# if it responds with ALIVE, we assume we have found the JeVois.
foreach($port_name in $port_name_list)
{
    echo "Checking $port_name"
    try {
        #Try to open & configure the port
        $port= new-Object System.IO.Ports.SerialPort $port_name,115200,None,8,one
        $port.Open()
        $port.ReadTimeout = 500
    }
    catch {
        echo "Failed to open and configure $port_name"
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
        if($response -like "*ALIVE*")
        {
            #Expect that a real jevois will respond with al ive
            $jevois_port_name = $port_name
            echo "Found JeVois on $jevois_port_name"
            break
        }
        else {
            echo "Incorrect Response from $port_name"
        }
        $port.Close();
    } catch {
        echo "Failed to get proper response on $port_name"
        if($port.IsOpen){
            $port.Close()
        }
        continue
    }
}

if($jevois_port_name -eq ""){
    Write-Error "Failed to open JeVois serial port - is it plugged in and not already open?"
    exit -1
}

echo "Jevois Location complete"


echo "========================================================================"
echo "Accessing JeVois USB drive"

#Port will already be open from above code
#adjust the read timeout to be nicer, since non-ping commands might take longer to execute.
$port.ReadTimeout = 2000

#Kick the JeVois out of streaming and into file transfer mode
$port.WriteLine("streamoff")
$port.WriteLine("usbsd")

#Give the JeVois and windows a bit of time to actually connect
# I'm not actually sure what this time needs to be, but giving a bit after
# connecting seems to help it be much more robust.
Start-Sleep -m 2000

#Go find the Jevois USB drive, by searching for a USB storage device with JeVois in the Model name.
# Presumably this was just created by the usbsd command to the Jevois
# Thanks to https://stackoverflow.com/questions/10634396/how-do-i-get-the-drive-letter-of-a-usb-drive-in-powershell, we have this glorious command
# Abandon hope all ye who enter here.
$drive_letter = gwmi win32_diskdrive | ?{$_.Model -like "*JeVois*"} | %{gwmi -Query "ASSOCIATORS OF {Win32_DiskDrive.DeviceID=`"$($_.DeviceID.replace('\','\\'))`"} WHERE AssocClass = Win32_DiskDriveToDiskPartition"} |  %{gwmi -Query "ASSOCIATORS OF {Win32_DiskPartition.DeviceID=`"$($_.DeviceID)`"} WHERE AssocClass = Win32_LogicalDiskToPartition"} | %{$_.deviceid}

if(!$drive_letter){
    Write-Error "No JeVois filesystem found"
    exit -1
}

echo "JeVois filesystem found at $drive_letter"

echo "========================================================================"
echo "Deploying user code files..."

#Generate file paths and actually copy the user code files
$py_output_location = Join-Path $drive_letter $mod_dest_path

echo "Copying $py_source_file to $py_output_location "
cp $py_source_file $py_output_location 

$cfg_output_location = Join-Path $drive_letter $cfg_dest_path

echo "Copying $init_cfg_source_file to $cfg_output_location "
cp $init_cfg_source_file $cfg_output_location 

echo "Copying $params_cfg_source_file to $cfg_output_location "
cp $params_cfg_source_file $cfg_output_location 

echo "Copying $vidmap_cfg_source_file to $cfg_output_location "
cp $vidmap_cfg_source_file $cfg_output_location 

#Seems like we need to wait a bit here too.
# Same as above, not sure how long we'd actually need to wait.
# At least long enough for the cp command to actually finish 
# writing bits, otherwise windows _sometimes_ throws a 
# "drive in use can't do the thing" error.
Start-Sleep -m 2000

echo "========================================================================"
echo "Cleaning Up"

#Eject Drive
# See https://community.spiceworks.com/topic/417345-powershell-script-to-eject-usb-device
$driveEject = New-Object -comObject Shell.Application
$driveEject.Namespace(17).ParseName($drive_letter).InvokeVerb("Eject")

# Close serial port
$port.Close();

echo "========================================================================"
echo "Deploy complete"

pause
################################################################################
# Copyright 2017 FRC Team 1736 Robot Casserole
################################################################################
# deploy.ps1 - sends locallly developed script to a connected JeVois camera
#
#
################################################################################
# Configuration

$source_file=".\moduleSrc\CasseroleVision.py"
$dest_path="modules\JeVois\CasseroleVision"

################################################################################
$jevois_port_name = ""

echo "Attempting to find attached JeVois..."

$port_name_list = [System.IO.Ports.SerialPort]::getportnames()

foreach($port_name in $port_name_list)
{
    echo "Checking $port_name"
    try {
        #Try to open the port
        $port= new-Object System.IO.Ports.SerialPort $port_name,115200,None,8,one
        $port.Open()
        $port.ReadTimeout = 1000

        #Assuming the port open works, send ping
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
    }
    catch {
        echo "Failed to open $port_name"
    }
}

if(!$port.IsOpen){
    echo "Error opening JeVois serial port - is it plugged in and not already open?"
    exit -1
}

echo "Jevois Location complete"

#Port will already be open from above code
#adjust the read timeout to be nicer
$port.ReadTimeout = 2000

#Kick the JeVois out of streaming and into file transfer mode
$port.WriteLine("streamoff")
$port.WriteLine("usbsd")

#Give the JeVois and windows a bit of time to actually connect
Start-Sleep -m 3000

#Go find the Jevois USB drive
# Thanks to https://stackoverflow.com/questions/10634396/how-do-i-get-the-drive-letter-of-a-usb-drive-in-powershell, we have this glorious command
# abandon hope all ye who enter here.
$drive_letter = gwmi win32_diskdrive | ?{$_.interfacetype -eq "USB"} | %{gwmi -Query "ASSOCIATORS OF {Win32_DiskDrive.DeviceID=`"$($_.DeviceID.replace('\','\\'))`"} WHERE AssocClass = Win32_DiskDriveToDiskPartition"} |  %{gwmi -Query "ASSOCIATORS OF {Win32_DiskPartition.DeviceID=`"$($_.DeviceID)`"} WHERE AssocClass = Win32_LogicalDiskToPartition"} | %{$_.deviceid}

echo "JeVois filesystem found at $drive_letter"


$output_location = Join-Path $drive_letter $dest_path
echo "Deploying $source_file to $output_location "

cp $source_file $output_location 

#Seems like we need to wait a bit here too.
Start-Sleep -m 1000

#Eject Drive
$driveEject = New-Object -comObject Shell.Application
$driveEject.Namespace(17).ParseName($drive_letter).InvokeVerb("Eject")

$port.Close();
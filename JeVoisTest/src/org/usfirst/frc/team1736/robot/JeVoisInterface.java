package org.usfirst.frc.team1736.robot;

import edu.wpi.cscore.MjpegServer;
import edu.wpi.cscore.UsbCamera;
import edu.wpi.first.wpilibj.SerialPort;

public class JeVoisInterface {
	static final int BAUD_RATE = 115200;
	static final int MJPG_STREAM_PORT = 1180;
	
	SerialPort visionPort = null;
	UsbCamera visionCam = null;
	MjpegServer camServer = null;
	
	boolean camStreamRunning = false;
	
	int loopCount = 0;
	

	public JeVoisInterface() {
		try {
			System.out.print("Creating JeVois SerialPort...");
			visionPort = new SerialPort(BAUD_RATE,SerialPort.Port.kUSB);
			System.out.println("SUCCESS!!");
		} catch (Exception e) {
			System.out.println("FAILED!!");
            e.printStackTrace();
		}
		
		doInitConfig();
		
		
	} 
	
    public void doInitConfig() {
        if (visionPort != null){
            System.out.println("configuring JeVois");
            stopCameraStream(); //Stop broadcasting camera stream
            sendCmd("streamoff"); //Turn off video stream from JeVois
            sendCmd("setmapping 4"); //Select the sample passthrough program
            sendCmd("streamon");  //Resume video streaming
            startCameraStream(); //start broadcasting camera stream
		
        }
    }
    
	public void startCameraStream(){
		try{
			System.out.print("Starting JeVois Cam Stream...");
			visionCam = new UsbCamera("VisionProcCam", 0);
			camServer = new MjpegServer("VisionCamServer", MJPG_STREAM_PORT);
			camServer.setSource(visionCam);
			camStreamRunning = true;
		} catch (Exception e) {
			System.out.println("FAILED!!");
            e.printStackTrace();
		}
	}
	
	public void stopCameraStream(){
		if(camStreamRunning){
			camServer.free();
			visionCam.free();
			camStreamRunning = false;
		}
	}
	

    public void sendPing() {
        if (visionPort != null){
            System.out.println("pinging JeVois");
            String cmd = "ping\n";
            int bytes = visionPort.writeString(cmd);
            System.out.println("wrote " +  bytes + "/" + cmd.length() + " bytes, cmd: " + cmd);
        }
    }
	
    public void setCamVisionProcMode() {
        if (visionPort != null){
            System.out.println("configuring JeVois");
            sendCmd("setcam autoexp 1"); //Disable auto exposure
            sendCmd("setcam absexp 50"); //Force exposure to a low value for vision processing
        }
    }
	
    public void setCamHumanDriverMode() {
        if (visionPort != null){
            System.out.println("configuring JeVois");
            sendCmd("setcam autoexp 0"); //Enable AutoExposure
        }
    }
	
	
    private int sendCmd(String cmd){
	    int bytes;
            bytes = visionPort.writeString(cmd + "\n");
            System.out.println("wrote " +  bytes + "/" + cmd.length() + " bytes, cmd: " + cmd);
	    return bytes;
    };


    public void getBytesPeriodic() {
        if (visionPort != null){
            if (visionPort.getBytesReceived() > 0) {
                System.out.println("Waited: " + loopCount + " loops, Rcv'd: " + visionPort.readString());
                loopCount = 0;
            } else {
                ++loopCount;
            }
        }
    }
}

//Other Resources: 
// https://github.com/nyholku/purejavacomm
// https://www.systutorials.com/docs/linux/man/8-setserial/
// 

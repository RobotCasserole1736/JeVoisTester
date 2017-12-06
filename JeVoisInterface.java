public class JeVoisInterface {
	static final int BAUD_RATE = 115200;
	
	SerialPort visionPort = null;
	int loopCount = 0;
	

	public JeVoisInterface() {
		try {
			System.out.print("Creating JeVois SerialPort...");
			visionPort = new SerialPort(BAUD_RATE,SerialPort.Port.kUSB);
			System.out.println("SUCCESS!!");
		} catch (Exception e) {
			System.out.println("FAILED!!  Fix and then restart code...");
                        e.printStackTrace();
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
	
    public void doInitConfig() {
        if (visionPort != null){
            System.out.println("configuring JeVois");
            sendCmd("streamoff"); //Turn off video stream
            sendCmd("setmapping 4"); //Run the sample passthrough program
	    sendCmd("streamon");  //Resume video streaming
		
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

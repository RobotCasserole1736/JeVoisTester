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
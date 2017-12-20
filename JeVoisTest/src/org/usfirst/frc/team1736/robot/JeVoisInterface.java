package org.usfirst.frc.team1736.robot;

import edu.wpi.cscore.MjpegServer;
import edu.wpi.cscore.UsbCamera;
import edu.wpi.cscore.VideoMode.PixelFormat;
import edu.wpi.first.wpilibj.SerialPort;
import edu.wpi.first.wpilibj.Timer;

public class JeVoisInterface {
	static final int BAUD_RATE = 115200;
	static final int MJPG_STREAM_PORT = 1180;
	static final String PACKET_START_CHAR = "{";
	static final String PACKET_END_CHAR = "}";
	static final String PACKET_DILEM_CHAR = ",";
	
	
	//Confgure the camera to stream debug images or not.
	static final boolean BROADCAST_USB_CAM = false;
	
	//When not streaming, use this mapping
	static final int NO_STREAM_MAPPING = 2;
	
	//When streaming, use this set of configuration
	
	SerialPort visionPort = null;
	UsbCamera visionCam = null;
	MjpegServer camServer = null;
	
	boolean camStreamRunning = false;
    
    String packetBuffer = "";
    
    boolean visionOnline = false;

	

	public JeVoisInterface() {
		int retry_counter = 0;
		
		//Retry strategy to get this serial port open.
		//I have yet to see a single retry used assuming the camera is plugged in
		// but you never know.
		while(visionPort == null && retry_counter++ < 10){
			try {
				System.out.print("Creating JeVois SerialPort...");
				visionPort = new SerialPort(BAUD_RATE,SerialPort.Port.kUSB);
				System.out.println("SUCCESS!!");
			} catch (Exception e) {
				System.out.println("FAILED!!");
	            e.printStackTrace();
	            sleep(500);
	            System.out.println("Retry " + Integer.toString(retry_counter));
			}
		}
		
		//Report an error if we didn't get to open the serial port
		if(visionPort == null){
			System.out.println("Error, Cannot open serial port to JeVois. Not starting vision system.");
			return;
		}
		
		//Test to make sure we are actually talking to the JeVois
		if(sendPing() != 0){
			System.out.println("Error, JeVois ping test failed. Not starting vision system.");
			return;
		}
			
		//We've got a connected JeVois, go ahead and run the initilization
		sendCmdAndCheck("setpar serout USB"); //Force serial output to USB
		
		if(BROADCAST_USB_CAM){
			//Start streaming the JeVois
	        startCameraStream(); 
		} else {
			sendCmdAndCheck("setmapping " + Integer.toString(NO_STREAM_MAPPING));
			sendCmdAndCheck("streamon ");
		}



        
        //Start listening for packets
        cameraListener.start();
        
	} 
    
    /**
     * Open an Mjpeg streamer from the JeVois camera
     */
	public void startCameraStream(){
		try{
			System.out.print("Starting JeVois Cam Stream...");
			visionCam = new UsbCamera("VisionProcCam", 0);
			visionCam.setVideoMode(PixelFormat.kBGR, 320, 240, 8); //This is our mapping
			camServer = new MjpegServer("VisionCamServer", MJPG_STREAM_PORT);
			camServer.setSource(visionCam);
			camStreamRunning = true;
			System.out.println("SUCCESS!!");
			
			//debug, temporary only
			sleep(500);
			System.out.println(visionCam.getDescription());
			System.out.println("isConnected: " + Boolean.toString(visionCam.isConnected()));
			System.out.println(visionCam.getVideoMode().toString());
			System.out.println(camServer.getDescription());
			System.out.println(camServer.getSource());
			
		} catch (Exception e) {
			System.out.println("FAILED!!");
            e.printStackTrace();
		}
	}
	
	/**
	 * Cease the oepration of the camera stream. Unknown if needed.
	 */
	public void stopCameraStream(){
		if(camStreamRunning){
			camServer.free();
			visionCam.free();
			camStreamRunning = false;
		}
	}
	
	/**
	 * Send the ping command to the JeVois to verify it is connected
	 * @return 0 on success, -1 on unexpected response, -2 on timeout
	 */
    public int sendPing() {
    	int retval = -1;
        if (visionPort != null){
            System.out.println("pinging JeVois...");
            retval = sendCmdAndCheck("ping");
        }
        
        if(retval==0){
        	System.out.println("success!");
        }
        
        return retval;
    }
	
    /**
     * Send commands to the JeVois to configure it for image-processing friendly parameters
     */
    public void setCamVisionProcMode() {
        if (visionPort != null){
            System.out.println("configuring JeVois");
            sendCmdAndCheck("setcam autoexp 1"); //Disable auto exposure
            sendCmdAndCheck("setcam absexp 50"); //Force exposure to a low value for vision processing
        }
    }
	
    /**
     * Send parameters to the camera to configure it for a human-readable image
     */
    public void setCamHumanDriverMode() {
        if (visionPort != null){
            System.out.println("configuring JeVois");
            sendCmdAndCheck("setcam autoexp 0"); //Enable AutoExposure
        }
    }
	
	/**
	 * Sends a command over serial to JeVois and returns immedeately.
     * @param cmd String of the command to send (ex: "ping")
	 * @return number of bytes written
	 */
    private int sendCmd(String cmd){
	    int bytes;
        bytes = visionPort.writeString(cmd + "\n");
        System.out.println("wrote " +  bytes + "/" + (cmd.length()+1) + " bytes, cmd: " + cmd);
	    return bytes;
    };
    
    /**
     * Sends a command over serial to the JeVois, waits for a response, and checks that response
     * Automatically ends the line termination character.
     * @param cmd String of the command to send (ex: "ping")
     * @return 0 if OK detected, -1 if ERR detected, -2 if timeout waiting for response
     */
    public int sendCmdAndCheck(String cmd){
    	int retval = 0;
	    sendCmd(cmd);
	    retval = blockAndCheckForOK(1.0);
	    if(retval == -1){
	    	System.out.println(cmd + " Produced an error");
	    } else if (retval == -2) {
	    	System.out.println(cmd + " timed out");
	    }
	    return retval;
    };

    //Persistant but "local" variables for getBytesPeriodic()
    private String getBytesWork = "";
	private int loopCount = 0;
    /**
     * Read bytes from the serial port in a non-blocking fashion
     * Will return the whole thing once the first "OK" or "ERR" is seen in the stream.
     * Returns null if no string read back yet.
     */
    public String getCmdResponseNonBlock() {
    	String retval =  null;
        if (visionPort != null){
            if (visionPort.getBytesReceived() > 0) {
            	String rxString = visionPort.readString();
                System.out.println("Waited: " + loopCount + " loops, Rcv'd: " + rxString);
                getBytesWork += rxString;
                if(getBytesWork.contains("OK") || getBytesWork.contains("ERR")){
                	retval = getBytesWork;
                	getBytesWork = "";
                	System.out.println(retval);
                }
                loopCount = 0;
            } else {
                ++loopCount;
            }
        }
		return retval;
    }
    
    /** 
     * Blocks thread execution till we get a response from the serial line
     * or timeout. 
     * Return values:
     *  0 = OK in response
     * -1 = ERR in response
     * -2 = No token found before timeout_s
     */
    public int blockAndCheckForOK(double timeout_s){
    	int retval = -2;
    	double startTime = Timer.getFPGATimestamp();
    	String testStr = "";
        if (visionPort != null){
            while(Timer.getFPGATimestamp() - startTime < timeout_s){
                if (visionPort.getBytesReceived() > 0) {
                	testStr += visionPort.readString();
                	if(testStr.contains("OK")){
                		retval = 0;
                		System.out.println(testStr);
                		break;
                	}else if(testStr.contains("ERR")){
                		retval = -1;
                		System.out.println(testStr);
                		break;
                	}

                } else {
                	sleep(10);
                }
            }
        }
        return retval;
    }
	
    /** 
     * Blocks thread execution till we get a valid packet from the serial line
     * or timeout. 
     * Return values:
     *  String = the packet 
     *  null = No full packet found before timeout_s
     */
    public String blockAndGetPacket(double timeout_s){
    	String retval = null;
    	double startTime = Timer.getFPGATimestamp();
        if (visionPort != null){
            while(Timer.getFPGATimestamp() - startTime < timeout_s){
                //Keep trying to get bytes from the serial port until the timeout expires.
                
                if (visionPort.getBytesReceived() > 0) {
                    //If there are any bytes available, read them in and 
                    // append them to the buffer.
                	packetBuffer += visionPort.readString();
                    
                    //Attempt to detect if the buffer currently contains a complete packet
                	if(packetBuffer.contains(PACKET_START_CHAR)){
                        //Buffer contains at least one start character. 
				        if(packetBuffer.contains(PACKET_END_CHAR)){
                            //Buffer also contains at least one end character. 
                            //Get the most-recent packet end character's index
                            int endIdx = packetBuffer.lastIndexOf(PACKET_END_CHAR);
                            
                            // Look for the index of the start character for the packet
                            //  described by endIdx. Note this line of code assumes the 
                            //  start character for the packet must come _before_ the
                            //  end character.
                            int startIdx = packetBuffer.lastIndexOf(PACKET_START_CHAR, endIdx);
                            
                            if(startIdx == -1){
                                // If there was no start character before the end character,
                                //  we can assume that we have something a bit wacky in our
                                //  buffer. For example: ",abc}garbage{1,2".
                                // Since we've started to recieve a good packet, discard 
                                //  everything prior to the start character.
                                startIdx = packetBuffer.lastIndexOf(PACKET_START_CHAR);
                                packetBuffer = packetBuffer.substring(startIdx);
                            } else {
                                // Buffer contains a full packet. Extract it.
                                retval = packetBuffer.substring(startIdx+1, endIdx-1);
                                // We no longer need this packet in the buffer.
                                packetBuffer = packetBuffer.substring(endIdx+1);
                                //We have a packet, jump out of the while() loop!
                                break;
                            } 
                        } else {
                          //In this case, we have a start character, but no end to the buffer yet. 
                          // Do nothing, just wait for more characters to come in.
                        }
                    } else {
                        //Buffer contains no start characters. None of the current buffer contents can 
                        // be meaningful. Discard the whole thing.
                        packetBuffer = "";
                    }
                } else {
                	sleep(10);
                }
            }
        }
        return retval;
    }
    
    private void sleep(int time_ms){
    	try {
			Thread.sleep(time_ms);
		} catch (InterruptedException e) {
			System.out.println("DO NOT WAKE THE SLEEPY BEAST");
			e.printStackTrace();
		}
    }
    
    public void blockAndPrintAllSerial(){
        if (visionPort != null){
            while(true){
                if (visionPort.getBytesReceived() > 0) {
                	System.out.print(visionPort.readString());
            	} else {
            		System.out.println("Nothing Rx'ed");
                	sleep(100);
                }
            }
        }

    }
    
    /**
     * Parse individual numbers from a packet
     * @param pkt
     */
    public void parsePacket(String pkt){
    	
    }
    
    /**
     * This thread runs a periodic task in the background to listen for vision camera packets.
     */
    Thread cameraListener = new Thread(new Runnable(){
    	public void run(){
    		blockAndPrintAllSerial();
    		//String packet;
    		//packet = blockAndGetPacket(10);
    	}
    });
    
}

//Other Resources: 
// https://github.com/nyholku/purejavacomm
// https://www.systutorials.com/docs/linux/man/8-setserial/
// 

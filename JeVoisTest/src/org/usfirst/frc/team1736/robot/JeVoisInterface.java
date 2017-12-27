package org.usfirst.frc.team1736.robot;

import edu.wpi.cscore.MjpegServer;
import edu.wpi.cscore.UsbCamera;
import edu.wpi.cscore.VideoMode.PixelFormat;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.SerialPort;
import edu.wpi.first.wpilibj.Timer;

public class JeVoisInterface {
    
    // Serial Port Constants 
    private static final int BAUD_RATE = 115200;
    
    // MJPG Streaming Constants 
    private static final int MJPG_STREAM_PORT = 1180;
    
    // Packet format constants 
    private static final String PACKET_START_CHAR = "{";
    private static final String PACKET_END_CHAR = "}";
    private static final String PACKET_DILEM_CHAR = ",";
    private static final int PACKET_NUM_EXPECTED_FIELDS = 3;
    
    
    // Confgure the camera to stream debug images or not.
    private boolean broadcastUSBCam = false;
    
    // When not streaming, use this mapping
    private static final int NO_STREAM_MAPPING = 2;
    
    // When streaming, use this set of configuration
    private static final int STREAM_WIDTH_PX = 352;
    private static final int STREAM_HEIGHT_PX = 288;
    private static final int STREAM_RATE_FPS = 15;
    
    // Serial port used for getting target data from JeVois 
    private SerialPort visionPort = null;
    
    // USBCam and server used for broadcasting a webstream of what is seen 
    private UsbCamera visionCam = null;
    private MjpegServer camServer = null;
    
    // Status variables 
    private boolean dataStreamRunning = false;
    private boolean camStreamRunning = false;
    private boolean visionOnline = false;

    // Packet rate performace tracking
    private double packetRxTime = 0;
    private double prevPacketRxTime = 0;
    private double packetRate_PPS = 0;

    // Most recently seen target information
    private boolean tgtVisible = false;
    private double  tgtAngleDeg = 0;
    private double  tgtRange = 0;
    private double  tgtTime = 0;
    
    // Info about the JeVois performace & status
    private double jeVoisCpuTempC = 0;
    private double jeVoisCpuLoadPct = 0;
    private double jeVoisFramerateFPS = 0;
    private double packetRxRatePPS = 0;
    
    
    //=======================================================
    //== BEGIN PUBLIC INTERFACE
    //=======================================================

    /**
     * Constructor. Opens a USB serial port to the JeVois camera, sends a few test commands checking for error,
     * then fires up the user's program and begins listening for target info packets in the background
     */
    public JeVoisInterface() {
        JeVoisInterface(false); //Default - stream disabled, just run serial.
    }

    /**
     * Constructor. Opens a USB serial port to the JeVois camera, sends a few test commands checking for error,
     * then fires up the user's program and begins listening for target info packets in the background.
     * Pass TRUE to additionaly enable a USB camera stream of what the vision camera is seeing.
     */
    public JeVoisInterface(boolean useUSBStream) {
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
            DriverStation.reportError("Cannot open serial port to JeVois. Not starting vision system.", false);
            return;
        }
        
        //Test to make sure we are actually talking to the JeVois
        if(sendPing() != 0){
            DriverStation.reportError("JeVois ping test failed. Not starting vision system.", false);
            return;
        }

        setCameraStreamActive(useUSBStream);
        start();

        //Start listening for packets
        packetListenerThread.setDaemon(true);
        packetListenerThread.start();

    } 

    public void start(){
        if(broadcastUSBCam){
            //Start streaming the JeVois via webcam
            //This auto-starts the serial stream
            startCameraStream(); 
        } else {
            startDataOnlyStream();
        }
    }

    public void stop(){
        if(broadcastUSBCam){
            //Start streaming the JeVois via webcam
            //This auto-starts the serial stream
            stopCameraStream(); 
        } else {
            stopDataOnlyStream();
        }
    }
    
    /**
     * Send commands to the JeVois to configure it for image-processing friendly parameters
     */
    public void setCamVisionProcMode() {
        if (visionPort != null){
            sendCmdAndCheck("setcam autoexp 1"); //Disable auto exposure
            sendCmdAndCheck("setcam absexp 75"); //Force exposure to a low value for vision processing
        }
    }
    
    /**
     * Send parameters to the camera to configure it for a human-readable image
     */
    public void setCamHumanDriverMode() {
        if (visionPort != null){
            sendCmdAndCheck("setcam autoexp 0"); //Enable AutoExposure
        }
    }

    /*
     * Main getters/setters
     */

    /**
     * Set to true to enable the camera stream, or set to false to stream serial-packets only.
     * Note this cannot be changed at runtime due to jevois constraints. You must stop whatatever processing
     * is going on first.
     */
    public void setCameraStreamActive(boolean active){
        if(dataStreamRunning == false){
            broadcastUSBCam = active;
        } else {
            DriverStation.reportError("Attempt to change cal stream mode while JeVois is still running. This is disallowed.", false);
        }
        

    }

    /**
     * Returns the most recently seen target's angle relative to the camera in degrees
     * Positive means to the Right of center, negative means to the left
     */
    public double getTgtAngle_Deg() {
        return tgtAngleDeg;
    }

    /**
     * Returns the most recently seen target's range from the camera in inches
     * Range means distance along the ground from camera mount point to observed target
     * Return values should only be positive
     */
    public double getTgtRange_in() {
        return tgtRange;
    }
    
    /**
     * Get the estimated timestamp of the most recent target observation.
     * This is calculated based on the FPGA timestamp at packet RX time, minus the reportetd vision pipeline delay.
     * It will not currently account for serial hardware or other delays.
     */
    public double getTgtTime() {
        return tgtTime;
    }
    
    /**
     * Returns true when the roboRIO is recieving packets from the JeVois, false if no packets have been recieved.
     * Other modules should not use the vision processing results if this returns false.
     */
    public boolean isVisionOnline() {
        return visionOnline;
    }
    
    /**
     * Returns the JeVois's most recently reported CPU Temperature in deg C
     */
    public double getJeVoisCPUTemp_C(){
        return jeVoisCpuTempC;
    }

    /**
     * Returns the JeVois's most recently reported CPU Load in percent of max
     */
    public double getJeVoisCpuLoad_pct(){
        return jeVoisCpuLoadPct;
    }

    /**
     * Returns the JeVois's most recently reported pipline framerate in Frames per second
     */
    public double getJeVoisFramerate_FPS(){
        return jeVoisFramerateFPS;
    }

    /**
     * Returns the roboRIO measured serial packet recieve rate in packets per second
     */
    public double getPacketRxRate_PPS(){
        return packetRxRatePPS;
    }

    //=======================================================
    //== END PUBLIC INTERFACE
    //=======================================================

    
    /**
     * This is the main perodic update function for the Listener. It is intended
     * to be run in a background task, as it will block until it gets packets. 
     */
    private void backgroundUpdate(){
        
        // Grab packets and parse them.
        String packet;
        
        prevPacketRxTime = packetRxTime;
        packet = blockAndGetPacket(10);
        
        
        if(packet != null){
            packetRxTime = Timer.getFPGATimestamp();
            if( parsePacket(packet, packetRxTime) == 0){
                visionOnline = true;
                packetRxRatePPS = 1.0/(packetRxTime - prevPacketRxTime);
            } else {
                visionOnline = false;
            }
            
        } else {
            visionOnline = false;
            DriverStation.reportWarning("Cannot get packet from JeVois Vision Processor", false);
        }
        
    }

    /**
     * Send the ping command to the JeVois to verify it is connected
     * @return 0 on success, -1 on unexpected response, -2 on timeout
     */
    private int sendPing() {
        int retval = -1;
        if (visionPort != null){
            retval = sendCmdAndCheck("ping");
        }
        return retval;
    }

    private void startDataOnlyStream(){
        //Send serial commands to start the streaming of target info
        sendCmdAndCheck("setmapping " + Integer.toString(NO_STREAM_MAPPING));
        sendCmdAndCheck("streamon");
        dataStreamRunning = true;
    }

    private void stopDataOnlyStream(){
        //Send serial commands to stop the streaming of target info
        sendCmdAndCheck("streamoff");
        dataStreamRunning = false;
    }
    

    /**
     * Open an Mjpeg streamer from the JeVois camera
     */
    private void startCameraStream(){
        try{
            System.out.print("Starting JeVois Cam Stream...");
            visionCam = new UsbCamera("VisionProcCam", 0);
            visionCam.setVideoMode(PixelFormat.kBGR, STREAM_WIDTH_PX, STREAM_HEIGHT_PX, STREAM_RATE_FPS);
            camServer = new MjpegServer("VisionCamServer", MJPG_STREAM_PORT);
            camServer.setSource(visionCam);
            camStreamRunning = true;
            dataStreamRunning = true;
            System.out.println("SUCCESS!!");
        } catch (Exception e) {
            DriverStation.reportError("Cannot start camera stream from JeVois", false);
            e.printStackTrace();
        }
    }
    
    /**
     * Cease the operation of the camera stream. Unknown if needed.
     */
    private void stopCameraStream(){
        if(camStreamRunning){
            camServer.free();
            visionCam.free();
            camStreamRunning = false;
            dataStreamRunning = false;
        }
    }
    
    /**
     * Sends a command over serial to JeVois and returns immediately.
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

    //Persistent but "local" variables for getBytesPeriodic()
    private String getBytesWork = "";
    private int loopCount = 0;
    /**
     * Read bytes from the serial port in a non-blocking fashion
     * Will return the whole thing once the first "OK" or "ERR" is seen in the stream.
     * Returns null if no string read back yet.
     */
    private String getCmdResponseNonBlock() {
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
    private int blockAndCheckForOK(double timeout_s){
        int retval = -2;
        double startTime = Timer.getFPGATimestamp();
        String testStr = "";
        if (visionPort != null){
            while(Timer.getFPGATimestamp() - startTime < timeout_s){
                if (visionPort.getBytesReceived() > 0) {
                    testStr += visionPort.readString();
                    if(testStr.contains("OK")){
                        retval = 0;
                        break;
                    }else if(testStr.contains("ERR")){
                        retval = -1;
                        break;
                    }

                } else {
                    sleep(10);
                }
            }
        }
        return retval;
    }
    
    
    // buffer to contain data from the port while we gather full packets 
    private String packetBuffer = "";
    /** 
     * Blocks thread execution till we get a valid packet from the serial line
     * or timeout. 
     * Return values:
     *  String = the packet 
     *  null = No full packet found before timeout_s
     */
    private String blockAndGetPacket(double timeout_s){
        String retval = null;
        double startTime = Timer.getFPGATimestamp();
        if (visionPort != null){
            while(Timer.getFPGATimestamp() - startTime < timeout_s){
                // Keep trying to get bytes from the serial port until the timeout expires.
                
                if (visionPort.getBytesReceived() > 0) {
                    // If there are any bytes available, read them in and 
                    //  append them to the buffer.
                    packetBuffer += visionPort.readString();
                    
                    // Attempt to detect if the buffer currently contains a complete packet
                    if(packetBuffer.contains(PACKET_START_CHAR)){
                        if(packetBuffer.contains(PACKET_END_CHAR)){
                            // Buffer also contains at least one start & end character.
                            // But we don't know if they're in the right order yet.
                            // Start by getting the most-recent packet end character's index
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
                                // Since we've started to receive a good packet, discard 
                                //  everything prior to the start character.
                                startIdx = packetBuffer.lastIndexOf(PACKET_START_CHAR);
                                packetBuffer = packetBuffer.substring(startIdx);
                            } else {
                                // Buffer contains a full packet. Extract it and clean up buffer
                                retval = packetBuffer.substring(startIdx+1, endIdx-1);
                                packetBuffer = packetBuffer.substring(endIdx+1);
                                break;
                            } 
                        } else {
                          // In this case, we have a start character, but no end to the buffer yet. 
                          //  Do nothing, just wait for more characters to come in.
                        }
                    } else {
                        // Buffer contains no start characters. None of the current buffer contents can 
                        //  be meaningful. Discard the whole thing.
                        packetBuffer = "";
                    }
                } else {
                    sleep(10);
                }
            }
        }
        return retval;
    }
    
    /**
     * Private wrapper around the Thread.sleep method, to catch that interrupted error.
     * @param time_ms
     */
    private void sleep(int time_ms){
        try {
            Thread.sleep(time_ms);
        } catch (InterruptedException e) {
            System.out.println("DO NOT WAKE THE SLEEPY BEAST");
            e.printStackTrace();
        }
    }
    
    /**
     * Mostly for debugging. Blocks execution forever and just prints all serial 
     * characters to the console. It might print a different message too if nothing
     * comes in.
     */
    public void blockAndPrintAllSerial(){
        if (visionPort != null){
            while(!Thread.interrupted()){
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
    public int parsePacket(String pkt, double rx_Time){
        //Parsing constants
        final int NUM_EXPECTED_TOKENS = 8;
        final int TGT_VISIBLE_TOKEN_IDX = 1;
        final int TGT_ANGLE_TOKEN_IDX = 2;
        final int TGT_RANGE_TOKEN_IDX = 3;
        final int JV_FRMRT_TOKEN_IDX = 4;
        final int JV_CPULOAD_TOKEN_IDX = 5;
        final int JV_CPUTEMP_TOKEN_IDX = 6;
        final int JV_PIPLINE_DELAY_TOKEN_IDX = 7;

        //TODO - populate the following based on packet contents & rxTime:
        // AngleDeg, tgtRange, tgtTime, jeVoisCpuTempC, jeVoisCpuLoadPct, jeVoisFramerateFPS
        String[] tokens = pkt.split(",");

        if(tokens.length < NUM_EXPECTED_TOKENS){
            DriverStation.reportError("Got malformed vision packet. Expected 8 tokens, but only found " + Integer.toString(tokens.length) + ". Packet Contents: " + pkt);
            return -1;
        }

        try {
            
            if(tokens[TGT_VISIBLE_TOKEN_IDX].equals("F")){
                tgtVisible = false;
            } else if (tokens[TGT_VISIBLE_TOKEN_IDX].equals("T")) {
                tgtVisible = true;
            } else {
                DriverStation.reportError("Got malformed vision packet. Expected only T or F in " + Integer.toString(TGT_VISIBLE_TOKEN_IDX) + ", but got " + tokens[TGT_VISIBLE_TOKEN_IDX]);
                return -1;
            }

            tgtAngleDeg = Double.parseDouble(tokens[TGT_ANGLE_TOKEN_IDX]);
            tgtRange    = Double.parseDouble(tokens[TGT_RANGE_TOKEN_IDX]);
            tgtTime  = rx_Time - Double.parseDouble(tokens[JV_PIPLINE_DELAY_TOKEN_IDX])/1000000.0;
            jeVoisCpuTempC   = Double.parseDouble(tokens[JV_CPUTEMP_TOKEN_IDX]);
            jeVoisCpuLoadPct = Double.parseDouble(tokens[JV_CPULOAD_TOKEN_IDX]);

        } catch (Exception e) {
            DriverStation.reportError("Unhandled exception while parsing Vision packet: " + e.getMessage() + "\n" + e.getStackTrace());
            return -1;
        }

        return 0;

        
    }
    
    
    /**
     * This thread runs a periodic task in the background to listen for vision camera packets.
     */
    Thread packetListenerThread = new Thread(new Runnable(){
        public void run(){
            backgroundUpdate();        
        }
    });
    
}

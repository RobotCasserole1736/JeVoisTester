import libjevois as jevois
import time
import re
import numpy as np
import cv2
####################################################################################################
## Copyright 2017 FRC Team 1736 Robot Casserole
####################################################################################################
## Casserole Vision - Vision Processing test for the 2018 Game 
####################################################################################################
class CasseroleVision:
    ####################################################################################################
    ## Constructor
    def __init__(self):
        jevois.LINFO("CasseroleVision Constructor...")
        
        #Frame Index
        self.frame = 0 

        #USB send frame decimation
        #Reduces send rate by this factor to limit USB bandwidth at high process rates
        self.frame_dec_factor = 4

        #Processing tune constants
        self.hsv_thres_lower = np.array([0,0,220])
        self.hsv_thres_upper = np.array([255,255,255])

        #Target Information
        self.tgtAngle = "0.0"
        self.tgtRange = "0.0"
        self.tgtAvailable = "f"

        #Timer and Variables to track statistics we care about
        self.timer = jevois.Timer("sandbox", 25, jevois.LOG_DEBUG)

        #regex for parsing info out of the status returned from the Timer class 
        self.pattern = re.compile('([0-9]*\.[0-9]+|[0-9]+) fps, ([0-9]*\.[0-9]+|[0-9]+)% CPU, ([0-9]*\.[0-9]+|[0-9]+)C,')

        #Tracked stats
        self.fps = "0"
        self.CPULoad = "0"
        self.CPUTemp = "0"
        jevois.LINFO("CasseroleVision construction Finished")
        

    # ###################################################################################################
    ## Process function with USB output
    def process(self, inframe, outframe = None):
        
        # Start measuring image processing time (NOTE: does not account for input conversion time):
        self.timer.start()
        
        #Capture image from camera
        inimg = inframe.getCvBGR()
        self.frame += 1

        ###############################################
        ## Start Image Processing Pipeline
        ###############################################
        # Move the image to HSV color space
        hsv = cv2.cvtColor(inimg, cv2.COLOR_BGR2HSV)

        #Create a mask of only pixells which match the HSV color space thresholds we've determined
        hsv_mask = cv2.inRange(hsv,self.hsv_thres_lower, self.hsv_thres_upper)

        #Find all countours of the outline of shapes in that mask
        _, contours, _ = cv2.findContours(hsv_mask, cv2.RETR_LIST, cv2.CHAIN_APPROX_TC89_KCOS)

        #debug - report the number of available contours
        self.tgtAvailable = str(len(contours))

        #Generate a debug image of the input image, masking non-detected pixels
        outimg = cv2.bitwise_and(inimg, inimg, mask = hsv_mask)
        ###############################################
        ## End Image Processing Pipeline
        ###############################################
        
        # Send processed data about target location and current status
        jevois.sendSerial("{{{},{},{},{},{},{},{}}}\n".format(self.frame,self.tgtAvailable,self.tgtAngle, self.tgtRange,self.fps,self.CPULoad,self.CPUTemp))
        

        # Broadcast the frame if we have an output sink available
        if(outframe != None):
            #Even if we're connected, don't send every frame we process. This will
            # help keep our USB bandwidth usage down.
            if(self.frame  % self.frame_dec_factor == 0):
                # We are done with the output, ready to send it to host over USB:
                outframe.sendCvBGR(outimg)

        # Track Processor Statistics
        results = self.pattern.match(self.timer.stop())
        if(results is not None):
            self.fps = results.group(1)
            self.CPULoad = results.group(2)
            self.CPUTemp = results.group(3)

    # ###################################################################################################
    ## Parse a serial command forwarded to us by the JeVois Engine, return a string
    def parseSerial(self, str):
        jevois.LINFO("parseserial received command [{}]".format(str))
        if str == "hello":
            return self.hello()
        elif str == "Geevoooice":
            return self.hi()
        return "ERR: Unsupported command"
    
    # ###################################################################################################
    ## Return a string that describes the custom commands we support, for the JeVois help message
    def supportedCommands(self):
        # use \n seperator if your module supports several commands
        return "hello - print hello using python"

    # ###################################################################################################
    ## Internal method that gets invoked as a custom command
    def hello(self):
        return "Hello from python!"
    
    def hi(self):
        return "Hi from python!"
        

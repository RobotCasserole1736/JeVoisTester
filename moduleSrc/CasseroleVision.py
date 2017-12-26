import libjevois as jevois
import time
import re
import numpy as np
import cv2
from datetime import datetime
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
        #TODO - Pick better constants
        self.hsv_thres_lower = np.array([0,0,220])
        self.hsv_thres_upper = np.array([255,255,255])

        #Target Information
        self.tgtAngle = "0.0"
        self.tgtRange = "0.0"
        self.tgtAvailable = "f"

        #Timer and Variables to track statistics we care about
        self.timer = jevois.Timer("CasseroleVisionStats", 25, jevois.LOG_DEBUG)

        #regex for parsing info out of the status returned from the jevois Timer class 
        self.pattern = re.compile('([0-9]*\.[0-9]+|[0-9]+) fps, ([0-9]*\.[0-9]+|[0-9]+)% CPU, ([0-9]*\.[0-9]+|[0-9]+)C,')

        #Tracked stats
        self.framerate_fps = "0"
        self.CPULoad_pct = "0"
        self.CPUTemp_C = "0"
        self.pipelineDelay_us = "0"

        #data structure object to hold info about the present data processed from the image fram
        self.curTargets = []

        jevois.LINFO("CasseroleVision construction Finished")
        

    # ###################################################################################################
    ## Process function with USB output
    def process(self, inframe, outframe = None):
        
        # Start measuring image processing time:
        self.timer.start()

        #No targets found yet
        self.tgtAvailable = False
        self.curTargets = []
        
        #Capture image from camera
        inimg = inframe.getCvBGR()
        self.frame += 1

        #Mark start of pipeline time
        pipline_start_time = datetime.now()

        ###############################################
        ## Start Image Processing Pipeline
        ###############################################
        # Move the image to HSV color space
        hsv = cv2.cvtColor(inimg, cv2.COLOR_BGR2HSV)

        #Create a mask of only pixells which match the HSV color space thresholds we've determined
        hsv_mask = cv2.inRange(hsv,self.hsv_thres_lower, self.hsv_thres_upper)

        # Erode image to remove noise if necessary.
        hsv_mask = cv2.erode(hsv_mask, None, iterations = 3)
        #Dilate image to fill in gaps
        hsv_mask = cv2.dilate(hsv_mask, None, iterations = 3)

        #Find all countours of the outline of shapes in that mask
        _, contours, _ = cv2.findContours(hsv_mask, cv2.RETR_LIST, cv2.CHAIN_APPROX_TC89_KCOS)

        #Extract Pertenant params from contours
        for c in contours:
            #Calcualte unrotated bounding rectangle (top left corner x/y, plus width and height)
            br_x, br_y, w, h = cv2.boundingRect(c)
            #minimal amount of qualification on targets
            if(w > 5 and h > 5): 
                moments = cv2.moments(c)
                if(moments['m00'] != 0):
                    #Calculate total filled in area
                    area = cv2.contourArea(c)
                    #Calculate centroid X and Y
                    c_x = int(moments['m10']/moments['m00'])
                    c_y = int(moments['m01']/moments['m00'])
                    self.curTargets.append(TargetObservation(c_x, c_y, area, w, h)) 

        #If we have some contours, figure out which is the target.
        if(len(self.curTargets) > 0):
            self.tgtAvailable = True
            #Find the best contour, which we will call the target
            best_target = self.curTargets[0] #Start presuming the first is the best
            for tgt in self.curTargets[1:]:
                #Super-simple algorithm: biggest target wins
                #TODO - make this better
                if(tgt.boundedArea > best_target.boundedArea):
                    best_target = tgt 


        # Calculate target physical location and populate output
        if(self.tgtAvailable == True):
            #TODO: Actual math to make this right
            self.tgtAngle = best_target.X
            self.tgtRange = best_target.boundedArea
        else:
            self.tgtAngle = 0
            self.tgtRange = 0

        ###############################################
        ## End Image Processing Pipeline
        ###############################################

        #Mark end of pipline
        # For accuracy, Must be done as close to sending the serial data as possible 
        pipeline_end_time = datetime.now() - pipline_start_time
        self.pipelineDelay_us = pipeline_end_time.microseconds
        
        # Send processed data about target location and current status
        # Note the order and number of params here must match with the roboRIO code.
        jevois.sendSerial("{{{},{},{},{},{},{},{},{}}}\n".format(self.frame,("T" if self.tgtAvailable else "F"),self.tgtAngle, self.tgtRange,self.framerate_fps,self.CPULoad_pct,self.CPUTemp_C,self.pipelineDelay_us))
        

        # Broadcast the frame if we have an output sink available
        if(outframe != None):
            #Even if we're connected, don't send every frame we process. This will
            # help keep our USB bandwidth usage down.
            if(self.frame  % self.frame_dec_factor == 0):
                #Generate a debug image of the input image, masking non-detected pixels
                outimg = cv2.bitwise_and(inimg, inimg, mask = hsv_mask)

                #Overlay target info if found
                if(self.tgtAvailable):
                    top    = int(best_target.Y - best_target.height/2)
                    bottom = int(best_target.Y + best_target.height/2)
                    left   = int(best_target.X - best_target.width/2)
                    right  = int(best_target.X + best_target.width/2)
                    cv2.rectangle(outimg, (right,top),(left,bottom),(0,255,0), 2,cv2.LINE_4)

                # We are done with the output, ready to send it to host over USB:
                outframe.sendCvBGR(outimg)

        # Track Processor Statistics
        results = self.pattern.match(self.timer.stop())
        if(results is not None):
            self.framerate_fps = results.group(1)
            self.CPULoad_pct = results.group(2)
            self.CPUTemp_C = results.group(3)

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
        


class TargetObservation(object):
    def __init__(self, X_in, Y_in, area_in, width_in, height_in):
        self.X = (X_in)
        self.Y = (Y_in)
        self.boundedArea = (area_in)
        self.width = (width_in)
        self.height = (height_in)
        




import libjevois as jevois
import time
## Simple test of programming JeVois modules in Python
#
# This module by default simply draws a cricle and a text message onto the grabbed video frames.
#
# Feel free to edit it and try something else. Note that this module does not import OpenCV, see the PythonOpenCV for a
# minimal JeVois module written in Python that uses OpenCV.
#
# @author Laurent Itti
# 
# @videomapping YUYV 640 480 15.0 YUYV 640 480 15.0 JeVois PythonTest
# @email itti\@usc.edu
# @address University of Southern California, HNB-07A, 3641 Watt Way, Los Angeles, CA 90089-2520, USA
# @copyright Copyright (C) 2017 by Laurent Itti, iLab and the University of Southern California
# @mainurl http://jevois.org
# @supporturl http://jevois.org/doc
# @otherurl http://iLab.usc.edu
# @license GPL v3
# @distribution Unrestricted
# @restrictions None
# @ingroup modules
class PythonTest:
    ####################################################################################################
    ## Constructor
    def __init__(self):
        jevois.LINFO("PythonTest Constructor...")
        
        self.frame = 0 # a simple frame counter used to demonstrate sendSerial()
        
        jevois.LINFO("PythonTest construction Finished")

    ###################################################################################################
    # Process function with no USB output
    def process(self, inframe):
        jevois.LINFO("{-35,20,105,234}")
        jevois.sendSerial("test3\n")

    # ###################################################################################################
    ## Process function with USB output
    def process(self, inframe, outframe):
        
        
        
        #jevois.drawRect(inframe,50,50,50,50,5,0)
        
        inimg = inframe.getCvBGR()
        
        self.frame += 1
        
        # Send processed data
        jevois.LINFO("{-35,20,105,234}")
        jevois.sendSerial("DONE frame {} \n".format(self.frame));
        
        # We are done with the output, ready to send it to host over USB:
        outframe.sendCvBGR(inimg)

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
        

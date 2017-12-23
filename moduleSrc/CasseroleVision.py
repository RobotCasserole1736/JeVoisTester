import libjevois as jevois
import time
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
        
        self.frame = 0 # a simple frame counter used to demonstrate sendSerial()
        
        jevois.LINFO("CasseroleVision construction Finished")

    # ###################################################################################################
    ## Process function with USB output
    def process(self, inframe, outframe = None):
        
        
        
        #jevois.drawRect(inframe,50,50,50,50,5,0)
        
        inimg = inframe.getCvBGR()
        
        self.frame += 1
        
        # Send processed data
        jevois.LINFO("{-35,20,105,234}")
        jevois.sendSerial("DONE frame {} \n".format(self.frame));
        
        if(outframe != None):
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
        

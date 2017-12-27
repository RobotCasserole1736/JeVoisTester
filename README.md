# JeVoisTester
Testing for the Jevois Interface roboRIO classes and custom Python code for a [JeVois Vision Processing camera](http://jevois.org/doc/).

# Contents

## JeVoisTest

Sample FRC Java source code designed to interact with the vision processing algorithm on the JeVois Camera

## moduleSource

Python source files & configuration filels for our JeVois camera custom processing module.

## Old JeVois Files

Historical experiments. May or may not work.

## camera.html

Super-de-duper simple webpage to display a streamed image from the roboRIO

## deploy.ps1

Powershell script to take the python files from moduleSource, do some basic verification, and deploy them to a connected JeVois camera.

## serial_listen.ps1

Powershell script to find & open the JeVois serial port, and then just listen for whatever packets are received. These are printed to console. Press 'q' to quit. 

## run_no_camera.ps1

Powershell script which works the same way as serial_listen.ps1, but additionally sends the setmapping & streamon commands to get the vision algorithm to run without a webcam hooked up.

# Note on powershell permissions

To allow execution of powershell scripts, you may have to enable it on your windows machines. To do this across the board, start up a powershell command window as an administrator (right-click, "run as administrator"), and run the following command:

    Set-ExecutionPolicy Unrestricted 


This should be a one-time operation. See [here](https://technet.microsoft.com/en-us/library/ee176961.aspx) for more info.

Note this will allow _any_ powershell script to run, so be careful if you download scripts from untrusted sources or something like that. Talk to your mentors and/or sysadmins if concerned.

Powershell was chosen since all the operations (serial port interaction, file system operations, USB disk search & eject) could be done without additional software installs for windows computers (looking at you python). Not saying it's the best solution. It's just _a_ solution with minimal setup.
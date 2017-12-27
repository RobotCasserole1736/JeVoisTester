package org.usfirst.frc.team1736.robot;

import org.usfirst.frc.team1736.lib.LoadMon.CasseroleRIOLoadMonitor;

import edu.wpi.first.wpilibj.IterativeRobot;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

/**
 * The VM is configured to automatically run this class, and to call the
 * functions corresponding to each mode, as described in the IterativeRobot
 * documentation. If you change the name of this class or the package after
 * creating this project, you must also update the manifest file in the resource
 * directory.
 */
public class Robot extends IterativeRobot {

    JeVoisInterface testCam;
    CasseroleRIOLoadMonitor loadMon;
    
    /**
     * This function is run when the robot is first started up and should be
     * used for any initialization code.
     */
    @Override
    public void robotInit() {
        testCam = new JeVoisInterface(true);
        loadMon = new CasseroleRIOLoadMonitor();
    }
    
    /**
     * This function is called at the start of disabled
     */
    @Override
    public void disabledInit() {

    }
    
    /**
     * This function is called periodically during disabled mode
     */
    @Override
    public void disabledPeriodic() {
        System.out.print("s");
        System.out.println(testCam.getPacketRxRate_PPS());
        System.out.print("r");
        System.out.println(loadMon.getCPULoadPct());

    }
    
    
    /**
     * This function is called at the start of autonomous
     */
    @Override
    public void autonomousInit() {

    }

    /**
     * This function is called periodically during autonomous
     */
    @Override
    public void autonomousPeriodic() {
        
    }
    
    /**
     * This function is called at the start of teleop
     */
    @Override
    public void teleopInit() {

    }
    
    /**
     * This function is called periodically during operator control
     */
    @Override
    public void teleopPeriodic() {
        System.out.println("==============+++==============");
        System.out.print("Vision Online: ");
        System.out.println(testCam.isVisionOnline());
        System.out.print("Target Visible: ");
        System.out.println(testCam.isTgtVisible());
        System.out.print("Target Angle: ");
        System.out.println(testCam.getTgtAngle_Deg());
        System.out.print("Target Range:");
        System.out.println(testCam.getTgtRange_in());
        System.out.print("Serial Packet RX Rate: ");
        System.out.println(testCam.getPacketRxRate_PPS());
        System.out.print("JeVois Framerate: ");
        System.out.println(testCam.getJeVoisFramerate_FPS());
        System.out.print("JeVois CPU Load: ");
        System.out.println(testCam.getJeVoisCpuLoad_pct());
        System.out.print("RIO CPU Load: ");
        System.out.println(loadMon.getCPULoadPct());
        System.out.print("RIO MEM Load: ");
        System.out.println(loadMon.getMemLoadPct());
        System.out.println("===============================\n\n\n");
        
    }

    /**
     * This function is called periodically during test mode
     */
    @Override
    public void testPeriodic() {
    }
}


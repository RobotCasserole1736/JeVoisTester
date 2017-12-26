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
        testCam = new JeVoisInterface();
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
        System.out.println(loadMon.getCPULoadPct());
        System.out.println(loadMon.getMemLoadPct());
        System.out.println("----------");
        
    }

    /**
     * This function is called periodically during test mode
     */
    @Override
    public void testPeriodic() {
    }
}


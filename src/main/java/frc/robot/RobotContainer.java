// In Java We Trust

// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.auto.NamedCommands;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.PrintCommand;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import frc.robot.commands.IntakeCommands;
import frc.robot.commands.SwerveCommands;
import frc.robot.subsystems.drive.Drive;
import frc.robot.subsystems.drive.GyroIO;
import frc.robot.subsystems.drive.GyroIOPigeon2;
import frc.robot.subsystems.drive.ModuleIO;
import frc.robot.subsystems.drive.ModuleIOSim;
import frc.robot.subsystems.drive.ModuleIOSparkMax;
import frc.robot.subsystems.intake.Intake;
import frc.robot.subsystems.intake.IntakeIO;
import frc.robot.subsystems.intake.IntakeIOSim;
import frc.robot.subsystems.intake.IntakeIOSparkMax;
import frc.robot.subsystems.shooter.ProjectileTrajectoryGenerator;
import org.littletonrobotics.junction.networktables.LoggedDashboardChooser;

public class RobotContainer {
  private Drive robotDrive;
  private Intake robotIntake;

  private CommandXboxController pilotController = new CommandXboxController(0);

  private LoggedDashboardChooser<Command> autoChooser;

  public RobotContainer() {
    initializeSubsystems();

    // NamedCommands MUST be registered before building the auto chooser
    configureAutonomous();

    // AutoBuilder is configured when Drive is initialized, thus chooser must be instantiated after
    // initializeSubsystems()
    autoChooser =
        new LoggedDashboardChooser<>("Autonomous Selector", AutoBuilder.buildAutoChooser());
    autoChooser.addDefaultOption("Print Hello", new PrintCommand("Hello"));

    configureButtonBindings();

    // TODO Remove
    ProjectileTrajectoryGenerator.displayDebuggingInformation();
  }

  /** Instantiate subsystems */
  private void initializeSubsystems() {
    switch (Constants.currentMode) {
      case REAL:
        robotDrive =
            new Drive(
                new ModuleIOSparkMax(0),
                new ModuleIOSparkMax(1),
                new ModuleIOSparkMax(2),
                new ModuleIOSparkMax(3),
                new GyroIOPigeon2(false));
        robotIntake = new Intake(new IntakeIOSparkMax());
        break;
      case SIM:
        robotDrive =
            new Drive(
                new ModuleIOSim(0),
                new ModuleIOSim(1),
                new ModuleIOSim(2),
                new ModuleIOSim(3),
                new GyroIO() {});
        robotIntake = new Intake(new IntakeIOSim());
        break;
      default:
        robotDrive =
            new Drive(
                new ModuleIO() {},
                new ModuleIO() {},
                new ModuleIO() {},
                new ModuleIO() {},
                new GyroIO() {});
        robotIntake = new Intake(new IntakeIO() {});
        break;
    }
  }

  /** Register commands with PathPlanner and add default autos to chooser */
  private void configureAutonomous() {
    // Register commands with PathPlanner's AutoBuilder so it can call them
    NamedCommands.registerCommand(
        "Print Pose", Commands.print("Pose: " + robotDrive.getPosition()));
    NamedCommands.registerCommand("Intake", IntakeCommands.intakePiece(robotIntake, 12.0));
    NamedCommands.registerCommand("Stop Intake", IntakeCommands.stopIntake(robotIntake));
  }

  /** Configure controllers */
  private void configureButtonBindings() {
    /* Drive with joysticks */
    robotDrive.setDefaultCommand(
        SwerveCommands.swerveDrive(
            robotDrive,
            () -> -pilotController.getLeftY(),
            () -> -pilotController.getLeftX(),
            () -> -pilotController.getRightX()));

    /* Reset drive heading | Debugging */
    pilotController
        .y()
        .onTrue(
            Commands.runOnce(
                    () ->
                        robotDrive.setPose(
                            new Pose2d(
                                robotDrive.getPosition().getTranslation(), new Rotation2d())),
                    robotDrive)
                .ignoringDisable(true)); // Reset even when disabled

    /* Reset drive pose | Debugging */
    pilotController.a().onTrue(Commands.runOnce(robotDrive::resetPose, robotDrive));
  }

  /** Returns the selected autonomous */
  public Command getAutonomousCommand() {
    return autoChooser.get();
  }
}

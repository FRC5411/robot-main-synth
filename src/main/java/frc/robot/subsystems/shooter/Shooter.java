// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems.shooter;

import edu.wpi.first.math.controller.ArmFeedforward;
import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.trajectory.TrapezoidProfile;
import edu.wpi.first.math.trajectory.TrapezoidProfile.State;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.subsystems.shooter.angler.AnglerIO;
import frc.robot.subsystems.shooter.angler.AnglerIOInputsAutoLogged;
import frc.robot.subsystems.shooter.launcher.LauncherIO;
import frc.robot.subsystems.shooter.launcher.LauncherIOInputsAutoLogged;
import frc.robot.utils.debugging.LoggedTunableNumber;
import org.littletonrobotics.junction.AutoLogOutput;
import org.littletonrobotics.junction.Logger;

/** Shooter subsystem */
public class Shooter extends SubsystemBase {
  private AnglerIO anglerIO;
  private AnglerIOInputsAutoLogged anglerIOInputs = new AnglerIOInputsAutoLogged();
  private LauncherIO launcherIO;
  private LauncherIOInputsAutoLogged launcherIOInputs = new LauncherIOInputsAutoLogged();

  private ProfiledPIDController anglerFeedback =
      new ProfiledPIDController(1.45, 0.0, 0.0, new TrapezoidProfile.Constraints(600.0, 300.0));
  private ArmFeedforward anglerFeedforward = new ArmFeedforward(0.0, 0.25, 0.0);

  private LoggedTunableNumber anglerFeedbackP =
      new LoggedTunableNumber("Shooter/Angler/Feedback/P", anglerFeedback.getP());
  private LoggedTunableNumber anglerFeedbackI =
      new LoggedTunableNumber("Shooter/Angler/Feedback/I", anglerFeedback.getI());
  private LoggedTunableNumber anglerFeedbackD =
      new LoggedTunableNumber("Shooter/Angler/Feedback/D", anglerFeedback.getD());
  private LoggedTunableNumber anglerFeedbackV =
      new LoggedTunableNumber(
          "Shooter/Angler/Feedback/V", anglerFeedback.getConstraints().maxVelocity);
  private LoggedTunableNumber anglerFeedbackA =
      new LoggedTunableNumber(
          "Shooter/Angler/Feedback/A", anglerFeedback.getConstraints().maxAcceleration);

  private LoggedTunableNumber anglerFeedforwardS =
      new LoggedTunableNumber("Shooter/Angler/Feedforward/S", anglerFeedforward.ks);
  private LoggedTunableNumber anglerFeedforwardG =
      new LoggedTunableNumber("Shooter/Angler/Feedforward/G", anglerFeedforward.kg);
  private LoggedTunableNumber anglerFeedforwardV =
      new LoggedTunableNumber("Shooter/Angler/Feedforward/V", anglerFeedforward.kv);

  private Rotation2d anglerSetpoint = null;
  private Double launcherSetpointMPS = null;

  /** Creates a new Shooter. */
  public Shooter(AnglerIO anglerIO, LauncherIO launcherIO) {
    this.anglerIO = anglerIO;
    this.launcherIO = launcherIO;

    resetAnglerFeedback();
  }

  @Override
  public void periodic() {
    anglerIO.updateInputs(anglerIOInputs);
    Logger.processInputs("Shooter/Angler/Inputs", anglerIOInputs);
    launcherIO.updateInputs(launcherIOInputs);
    Logger.processInputs("Shooter/Launcher/Inputs", launcherIOInputs);

    if (DriverStation.isDisabled()) {
      stopMotors(true, true);
    }

    if (anglerSetpoint != null) {
      double anglerFeedbackOutput =
          anglerFeedback.calculate(
              anglerIOInputs.anglerPosition.getDegrees(), anglerSetpoint.getDegrees());
      double anglerFeedforwardOutput =
          anglerFeedforward.calculate(
              anglerFeedback.getSetpoint().position, anglerFeedback.getSetpoint().velocity);

      double anglerCombinedOutput = (anglerFeedbackOutput + anglerFeedforwardOutput) / 12.0;

      anglerIO.setVolts(anglerCombinedOutput);

      Logger.recordOutput("Shooter/Angler/Feedback/Output", anglerFeedbackOutput);
      Logger.recordOutput("Shooter/Angler/Feedforward/Output", anglerFeedforwardOutput);
      Logger.recordOutput("Shooter/Angler/CombinedOutput", anglerCombinedOutput);
    }

    if (launcherSetpointMPS != null) {
      launcherIO.setTopVelocity(launcherSetpointMPS);
      launcherIO.setBottomVelocity(launcherSetpointMPS);
    }

    updateTunableNumbers();
  }

  /** Checks if tunable numbers have changed, if so update controllers */
  private void updateTunableNumbers() {
    if (anglerFeedbackP.hasChanged(hashCode())
        || anglerFeedbackI.hasChanged(hashCode())
        || anglerFeedbackD.hasChanged(hashCode())
        || anglerFeedbackV.hasChanged(hashCode())
        || anglerFeedbackA.hasChanged(hashCode())) {
      anglerFeedback.setP(anglerFeedbackP.get());
      anglerFeedback.setI(anglerFeedbackI.get());
      anglerFeedback.setD(anglerFeedbackD.get());

      anglerFeedback.setConstraints(
          new TrapezoidProfile.Constraints(anglerFeedbackV.get(), anglerFeedbackA.get()));
    }
    if (anglerFeedforwardS.hasChanged(hashCode())
        || anglerFeedforwardG.hasChanged(hashCode())
        || anglerFeedforwardV.hasChanged(hashCode())) {
      anglerFeedforward =
          new ArmFeedforward(
              anglerFeedforwardS.get(), anglerFeedforwardG.get(), anglerFeedforwardG.get());
    }
  }

  /** Stop specified motors and set their setpoints to null */
  public void stopMotors(boolean stopAngler, boolean stopLaunchers) {
    if (stopAngler) {
      anglerSetpoint = null;
      anglerIO.setVolts(0.0);
    }
    if (stopLaunchers) {
      launcherSetpointMPS = null;
      launcherIO.setTopVolts(0.0);
      launcherIO.setBottomVolts(0.0);
    }
  }

  /** Reset the angler controller profile */
  public void resetAnglerFeedback() {
    anglerFeedback.reset(anglerIOInputs.anglerPosition.getDegrees(), anglerIOInputs.anglerVelocityRadiansPerSecond);
  }

  /** Set the voltage of the angler motor */
  public void setAnglerVolts(double volts) {
    anglerIO.setVolts(volts);
  }

  /** Set the voltage of the launcher motors */
  public void setLauncherVolts(double topFlywheelVolts, double bottomFlywheelVolts) {
    launcherIO.setTopVolts(topFlywheelVolts);
    launcherIO.setBottomVolts(bottomFlywheelVolts);
  }

  /** Set the position setpoint of the angler mechanism */
  public void setAnglerPosition(Rotation2d position) {
    anglerSetpoint = position;
  }

  /** Set the velocity setpoint of the launcher flywheels */
  public void setLauncherVelocityMPS(double velocityMPS) {
    launcherSetpointMPS = velocityMPS;
  }

  /** Set all of the motors to a desired state */
  public void setAllMotors(Rotation2d anglerPosition, double launcherVelocityMPS) {
    setAnglerPosition(anglerPosition);
    setLauncherVelocityMPS(launcherVelocityMPS);
  }

  /** Returns the angle of the pivot */
  @AutoLogOutput(key = "Shooter/Angler/Position")
  public Rotation2d getAnglerPosition() {
    return anglerIOInputs.anglerPosition;
  }

  /** Returns the setpoint state of the angler feedback */
  @AutoLogOutput(key = "Shooter/Angler/Feedback/Setpoint")
  public State getAnglerSetpoint() {
    return anglerFeedback.getSetpoint();
  }

  /** Returns the position error of the angler feedback */
  @AutoLogOutput(key = "Shooter/Angler/Feedback/PositionError")
  public double getAnglerPositionError() {
    return anglerFeedback.getPositionError();
  }

  /** Returns the velocity error of the angler feedback */
  @AutoLogOutput(key = "Shooter/Angler/Feedback/VelocityError")
  public double getAnglerVelocityError() {
    return anglerFeedback.getVelocityError();
  }

  /** Returns the velocity of the top launcher flywheel */
  @AutoLogOutput(key = "Shooter/TopLauncher/VelocityMPS")
  public double getTopLauncherVelocityMPS() {
    return launcherIOInputs.topFlywheelVelocityMPS;
  }

  /** Returns the velocity of the bottom launcher flyhweel */
  @AutoLogOutput(key = "Shooter/BottomLauncher/VelocityMPS")
  public double getBottomLauncherVelocityMPS() {
    return launcherIOInputs.bottomFlywheelVelocityMPS;
  }
}

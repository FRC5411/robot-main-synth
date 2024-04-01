package frc.robot.subsystems.shooter.angler;

import java.util.function.DoubleSupplier;

import org.littletonrobotics.junction.AutoLogOutput;
import org.littletonrobotics.junction.Logger;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.trajectory.TrapezoidProfile;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.FunctionalCommand;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants;
import frc.robot.Constants.Robot;
import frc.robot.subsystems.shooter.Shooter.AnglerSetpoints;
import frc.robot.subsystems.shooter.ShooterVisualizer;
import frc.robot.utils.debugging.LoggedTunableNumber;
import frc.robot.utils.math.ScrewArmFeedforward;

public class Angler extends SubsystemBase {
  private static Rotation2d anglerPosition = null;

  public static Rotation2d getLastAnglerPosition() {
    return anglerPosition;
  }

  private AnglerIO anglerIO;
  private AnglerIOInputsAutoLogged anglerIOInputs = new AnglerIOInputsAutoLogged();

  private final Rotation2d minAngle = Rotation2d.fromDegrees(26.5);
  private final Rotation2d maxAngle = Rotation2d.fromDegrees(57.0);

  // Default to real values
  private ProfiledPIDController anglerFeedback =
      new ProfiledPIDController(0.49, 2.0, 0.018, new TrapezoidProfile.Constraints(1000.0, 1000.0));
  private ScrewArmFeedforward anglerFeedforward = new ScrewArmFeedforward(0.2, 0.0);

  private LoggedTunableNumber anglerFeedbackP;
  private LoggedTunableNumber anglerFeedbackI;
  private LoggedTunableNumber anglerFeedbackD;
  private LoggedTunableNumber anglerFeedbackV;
  private LoggedTunableNumber anglerFeedbackA;

  private LoggedTunableNumber anglerFeedforwardU;
  private LoggedTunableNumber anglerFeedforwardL;

  private ShooterVisualizer anglerVisualizer = new ShooterVisualizer(new Rotation2d());

  private AnglerSetpoints anglerSetpoint = null;


  private boolean angleEncoderCalibrated = false;
  private Rotation2d angleOffset = new Rotation2d();
  private Rotation2d currentAngle = new Rotation2d();

  public Angler(AnglerIO anglerIO) {
        this.anglerIO = anglerIO;
        this.anglerIO = anglerIO;

        if (Constants.currentRobot == Robot.SYNTH) {
        switch (Constants.currentMode) {
        case REAL:
          anglerFeedback.setP(0.49);
          anglerFeedback.setI(2.0);
          anglerFeedback.setD(0.018);
          anglerFeedback.setConstraints(new TrapezoidProfile.Constraints(1000.0, 1000.0));

          anglerFeedforward.updateU(0.2);
          anglerFeedforward.updateL(0.0);
          break;
        case SIM:
          anglerFeedback.setP(0.1);
          anglerFeedback.setI(0.0);
          anglerFeedback.setD(0.5);
          anglerFeedback.setConstraints(new TrapezoidProfile.Constraints(50.0, 50.0));

          anglerFeedforward.updateU(0.0);
          anglerFeedforward.updateL(0.0);
          break;
        default:
          anglerFeedback.setP(0.0);
          anglerFeedback.setI(0.0);
          anglerFeedback.setD(0.0);
          anglerFeedback.setConstraints(new TrapezoidProfile.Constraints(0.0, 0.0));

          anglerFeedforward.updateU(0.0);
          anglerFeedforward.updateL(0.0);
          break;
      }
    }

    anglerFeedbackP = new LoggedTunableNumber("Shooter/Angler/Feedback/P", anglerFeedback.getP());
    anglerFeedbackI = new LoggedTunableNumber("Shooter/Angler/Feedback/I", anglerFeedback.getI());
    anglerFeedbackD = new LoggedTunableNumber("Shooter/Angler/Feedback/D", anglerFeedback.getD());
    anglerFeedbackV =
        new LoggedTunableNumber(
            "Shooter/Angler/Feedback/V", anglerFeedback.getConstraints().maxVelocity);
    anglerFeedbackA =
        new LoggedTunableNumber(
            "Shooter/Angler/Feedback/A", anglerFeedback.getConstraints().maxAcceleration);
    anglerFeedforwardU =
        new LoggedTunableNumber("Shooter/Angler/Feedforward/U", anglerFeedforward.getU());
    anglerFeedforwardL =
        new LoggedTunableNumber("Shooter/Angler/Feedforward/L", anglerFeedforward.getL());

    resetAnglerFeedback();
    anglerFeedback.setTolerance(0.1);
    anglerFeedback.setIZone(20.0);
    anglerFeedback.setIntegratorRange(-0.5, 0.5);
  }

  @Override
  public void periodic() {
    anglerIO.updateInputs(anglerIOInputs);
    Logger.processInputs("Shooter/Angler/Inputs", anglerIOInputs);

    if (DriverStation.isDisabled()) {
      stopMotors();
    }

    if (!angleEncoderCalibrated) {
      for (int i = 0; i < 100; i++) {
        if (anglerIOInputs.anglerDutyCycleFrequency == 955) {
          angleOffset =
              Rotation2d.fromRadians(
                      MathUtil.inputModulus(
                          anglerIOInputs.anglerAbsolutePosition.getRadians(), 0, 2.0 * Math.PI))
                  .minus(anglerIOInputs.anglerRelativePosition);
          angleEncoderCalibrated = true;
          break;
        }
        if (i == 99) {
          angleOffset =
              Rotation2d.fromRadians(
                      MathUtil.inputModulus(
                          anglerIOInputs.anglerAbsolutePosition.getRadians(), 0, 2.0 * Math.PI))
                  .minus(anglerIOInputs.anglerRelativePosition);
          angleEncoderCalibrated = true;
          break;
        }
        anglerVisualizer =
            new ShooterVisualizer(anglerIOInputs.anglerRelativePosition.plus(angleOffset));
      }
      currentAngle = anglerIOInputs.anglerRelativePosition.plus(angleOffset);

      resetAnglerFeedback();
    }

    currentAngle = anglerIOInputs.anglerRelativePosition.plus(angleOffset);

    if (anglerSetpoint != null) {
      Rotation2d angleSetpoint =
          Rotation2d.fromDegrees(
              MathUtil.clamp(
                  anglerSetpoint.getAngle().get().getDegrees(),
                  minAngle.getDegrees(),
                  maxAngle.getDegrees()));

      double anglerFeedbackOutput =
          anglerFeedback.calculate(currentAngle.getDegrees(), angleSetpoint.getDegrees());
      double anglerFeedforwardOutput = anglerFeedforward.calculate(currentAngle, angleSetpoint);

      double anglerCombinedOutput = (anglerFeedbackOutput + anglerFeedforwardOutput);

      if(anglerFeedback.atGoal()) {
        anglerCombinedOutput *= 0.0;
      };      

      anglerIO.setVolts(anglerCombinedOutput);

      Logger.recordOutput("Shooter/Angler/Feedback/Output", anglerFeedbackOutput);
      Logger.recordOutput("Shooter/Angler/Feedforward/Output", anglerFeedforwardOutput);
      Logger.recordOutput("Shooter/Angler/CombinedOutput", anglerCombinedOutput);
    }

    anglerVisualizer.updateShooterAngle(currentAngle);

    if (Constants.tuningMode) {
      updateTunableNumbers();
    }
  }

  public Command setAnglerManualVolts(double volts) {
    return Commands.runOnce(
        () -> {
          setAnglerPosition(null);
          setAnglerVolts(volts);
        },
        this);
  }

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
    if (anglerFeedforwardU.hasChanged(hashCode()) || anglerFeedforwardL.hasChanged(hashCode())) {
        anglerFeedforward.updateU(anglerFeedforwardU.get());
        anglerFeedforward.updateL(anglerFeedforwardL.get());
    }
  }

    public void setAnglerPosition(AnglerSetpoints position) {
        anglerPosition = currentAngle;
        anglerSetpoint = position;

        if (anglerSetpoint != null) {
            DoubleSupplier errorDegrees =
            () -> Math.abs(anglerSetpoint.getAngle().get().minus(currentAngle).getDegrees());
        Logger.recordOutput("Shooter/ErrorDegrees", errorDegrees.getAsDouble());

        if ((anglerSetpoint != null && anglerSetpoint != AnglerSetpoints.IDLE) && errorDegrees.getAsDouble() > 2.0) {
            resetAnglerFeedback();
        }
      }
    }

    // TODO Remove, this is for debugging only
  public Command setAnglerCommandWithoutEnd(AnglerSetpoints setpoint) {
    return new FunctionalCommand(
      () -> {
        setAnglerPosition(setpoint);

        DoubleSupplier errorDegrees = () -> Math.abs(anglerSetpoint.getAngle().get().minus(currentAngle).getDegrees());

        if (anglerSetpoint != null && errorDegrees.getAsDouble() > 2.0) {
          resetAnglerFeedback();
        }
      }, 
      () -> {
        if(isAnglerAtGoal()) {
          anglerIO.setVolts(0.0);
        }
      }, 
      (interrupted) -> {
        anglerSetpoint = null;
      },
      () -> false, 
      this);
  }

  // TODO Remove, this is for debugging only
  public Command setAnglerCommand(AnglerSetpoints setpoint) {
    return new FunctionalCommand(
      () -> {
        anglerSetpoint = setpoint;

        DoubleSupplier errorDegrees = () -> Math.abs(anglerSetpoint.getAngle().get().minus(currentAngle).getDegrees());

        if (anglerSetpoint != null && errorDegrees.getAsDouble() > 2.0) {
          resetAnglerFeedback();
        }
      }, 
      () -> {}, 
      (interrupted) -> {
        anglerSetpoint = null;
      }, 
      () -> isAnglerAtGoal(), 
      this);
  }

  public Command setAnglerCommandInstant(AnglerSetpoints setpoint) {
    return new InstantCommand(() -> {
      setAnglerPosition(setpoint);
    }, this);
  }

  public void resetAnglerFeedback() {
    anglerFeedback.reset(currentAngle.getDegrees(), 0.0);
  }

  public void setAnglerVolts(double volts) {
    anglerIO.setVolts(volts);
  }

  public void stopMotors() {
      anglerSetpoint = null;
      anglerIO.setVolts(0.0);
  }

  public AnglerSetpoints getCurrentSetpoint() {
    return anglerSetpoint;
  }

  @AutoLogOutput(key = "Shooter/Angler/Position")
  public Rotation2d getAnglerPosition() {
    return currentAngle;
  }

  @AutoLogOutput(key = "Shooter/Angler/PositionDegrees")
  public double getAnglerDegrees() {
    return currentAngle.getDegrees();
  }

  @AutoLogOutput(key = "Shooter/Angler/AtAllSetpoints")
  public boolean atAnglerSetpoints() {
    return getAnglerPositionError() < 0.4;
  }

  @AutoLogOutput(key = "Shooter/Angler/Feedback/SetpointPosition")
  public Rotation2d getAnglerSetpointPosition() {
    return Rotation2d.fromDegrees(anglerFeedback.getSetpoint().position);
  }

  @AutoLogOutput(key = "Shooter/Angler/Feedback/SetpointVelocity")
  public double getAnglerSetpointVelocity() {
    return anglerFeedback.getSetpoint().velocity;
  }

  @AutoLogOutput(key = "Shooter/Angler/Feedback/GoalPosition")
  public double getAnglerGoalPosition() {
    return anglerFeedback.getGoal().position;
  }

  @AutoLogOutput(key = "Shooter/Angler/Feedback/PositionError")
  public double getAnglerPositionError() {
    return anglerFeedback.getPositionError();
  }

  @AutoLogOutput(key = "Shooter/Angler/Feedback/VelocityError")
  public double getAnglerVelocityError() {
    return anglerFeedback.getVelocityError();
  }

  @AutoLogOutput(key = "Shooter/Angler/Feedback/AtGoal")
  public boolean isAnglerAtGoal() {
    return anglerFeedback.atGoal();
  }
}

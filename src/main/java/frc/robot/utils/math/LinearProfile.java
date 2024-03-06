package frc.robot.utils.math;

import lombok.Getter;
import lombok.Setter;

/** Ramps up and down to setpoint for velocity closed loop control */
public class LinearProfile {
  private double dv;
  @Getter private final double period;
  @Getter private double currentSetpoint = 0;
  @Getter @Setter private double goal = 0;

  /**
   * Creates a new LinearProfile
   *
   * @param maxAcceleration The max ramp rate in velocity in rpm/sec
   * @param period Period of control loop (0.02)
   */
  public LinearProfile(double maxAcceleration, double period) {
    this.period = period;
    setMaxAcceleration(maxAcceleration);
  }

  /** Set the max acceleration constraint in rpm/sec */
  public void setMaxAcceleration(double maxAcceleration) {
    dv = maxAcceleration * period;
  }

  public double getPeriod() {
    return period;
  }

  /**
   * Sets the target setpoint, starting from the current speed
   *
   * @param goal Target setpoint
   * @param currentSpeed Current speed, to be used as the starting setpoint
   */
  public void setGoal(double goal, double currentSpeed) {
    this.goal = goal;
    currentSetpoint = currentSpeed;
  }

  /** Resets target setpoint and current setpoint */
  public void reset() {
    currentSetpoint = 0;
    goal = 0;
  }

  /**
   * Returns the current setpoint to send to motors
   *
   * @return Setpoint to send to motors
   */
  public double calculateSetpoint() {
    if (EqualsUtil.epsilonEquals(goal, currentSetpoint)) {
      return currentSetpoint;
    }
    if (goal > currentSetpoint) {
      currentSetpoint += dv;
      if (currentSetpoint > goal) {
        currentSetpoint = goal;
      }
    } else if (goal < currentSetpoint) {
      currentSetpoint -= dv;
      if (currentSetpoint < goal) {
        currentSetpoint = goal;
      }
    }
    return currentSetpoint;
  }
}

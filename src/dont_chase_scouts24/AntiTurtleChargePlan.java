package dont_chase_scouts24;

import battlecode.common.*;

public class AntiTurtleChargePlan {
	public MapLocation chargeCenter;
	public int chargeRound;

	public AntiTurtleChargePlan(MapLocation chargeCenter, int chargeRound) {
		this.chargeCenter = chargeCenter;
		this.chargeRound = chargeRound;
	}
}

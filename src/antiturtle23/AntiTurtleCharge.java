package antiturtle23;

import battlecode.common.*;

public class AntiTurtleCharge extends Globals {
	public static MapLocation chargeCenter = null;
	
	public static int gatherRound = 0; // what round to gather for the charge
    public static int chargeRound = 0; // what round to begin the charge
    public static int endRound = 0; // what round to end the charge
	public static int lastProposalRound = 0; // time last proposal was made
    
	// how many turns after the proposal we will gather
	public static final int VETO_WAIT_ROUNDS = 50;

	// how long we will spend gathering after the veto wait period is over
	public static final int GATHER_WAIT_ROUNDS = 100;
	
	// how long the charge itself will last once it begins
	public static final int CHARGE_LENGTH = 200;
	
	private static void scheduleCharge() {
		gatherRound = rc.getRoundNum() + VETO_WAIT_ROUNDS;
		chargeRound = gatherRound + GATHER_WAIT_ROUNDS;
		endRound = chargeRound + CHARGE_LENGTH;
	}

	public static void processAntiTurtleChargeMessage(int[] data) {
		// ignore proposals and vetos sent during a valid charge.
		// probably these are from ignorant newborns
		if (chargeCenter != null && rc.getRoundNum() >= gatherRound && rc.getRoundNum() <= endRound) {
			return;
		}
		
		if (Messages.parseAntiTurtleChargeVeto(data)) {
			Debug.indicate("charge", 0, "received a charge veto!!");
			chargeCenter = null;
		} else {
			chargeCenter = Messages.parseAntiTurtleChargeCenter(data);
			scheduleCharge();
			Debug.indicate("charge", 0, "received a charge proposal! center = " + chargeCenter + ", gatherRound = " + gatherRound + ", chargeRound = " + chargeRound);
		}
	}
	
	public static void proposeCharge(MapLocation proposalCenter) throws GameActionException {
		Messages.proposeAntiTurtleCharge(proposalCenter, MapEdges.maxBroadcastDistSq());
		chargeCenter = proposalCenter;
		scheduleCharge();
	}
	
	public static void vetoCharge() throws GameActionException {
		Messages.vetoAntiTurtleCharge(MapEdges.maxBroadcastDistSq());
	}
}

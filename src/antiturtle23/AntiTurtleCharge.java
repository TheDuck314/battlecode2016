package antiturtle23;

import battlecode.common.*;

public class AntiTurtleCharge extends Globals {
	public static MapLocation chargeCenter = null;
	
	public static int gatherRound = 0; // what round to gather for the charge
    public static int chargeRound = 0; // what round to begin the charge
	public static int lastProposalRound = 0; // time last proposal was made
    
	// how many turns after the proposal we will gather
	public static final int ANTI_TURTLE_CHARGE_VETO_WAIT_ROUNDS = 50;

	// how many turns after a charge proposal we will charge
	public static final int ANTI_TURTLE_CHARGE_ROUND_DELAY = 150;
	

	public static void processAntiTurtleChargeMessage(int[] data) {
		if (Messages.parseAntiTurtleChargeVeto(data)) {
			chargeCenter = null;
		} else {
			chargeCenter = Messages.parseAntiTurtleChargeCenter(data);
			gatherRound = rc.getRoundNum() + ANTI_TURTLE_CHARGE_VETO_WAIT_ROUNDS;
			chargeRound = rc.getRoundNum() + ANTI_TURTLE_CHARGE_ROUND_DELAY;
		}
	}
	
	public static void proposeCharge(MapLocation chargeCenter) {
		
	}
}

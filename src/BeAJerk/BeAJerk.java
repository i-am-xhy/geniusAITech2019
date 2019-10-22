package BeAJerk;

import java.nio.channels.AcceptPendingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;

/**
 * This is your negotiation party.
 */
public class BeAJerk extends AbstractNegotiationParty {
	private double phase1Fraction = 0.6;
	private double phase2Fraction = 0.3;

	// only accept/send offers above this threshold in phase1
	double phase1UtilityThreshold = 0.9;
	double phase2UtilityThreshold;
	// what absolute fraction should the utility decrease over phase 2 compared to phase 1
	double phase2UtilityDegradation = 0.4;
	// what is the minimum threshold for phase 3
	double phase3DesperationThreshold = 0.5;

	private Bid lastReceivedBid = null;
	// all bids up untill now
	private ArrayList<Bid> bids = new ArrayList<Bid>();

	private Map<genius.core.issue.Value, Integer> histogram = new HashMap<>();


	ArrayList<Bid> phase3Bids = new ArrayList<Bid>();

	double randomThresholdDecay = 0.1;
	int randomThresholdDecaryN = 1000;

	@Override
	public void init(NegotiationInfo info) {

		super.init(info);

		System.out.println("Discount Factor is " + getUtilitySpace().getDiscountFactor());
		System.out.println("Reservation Value is " + getUtilitySpace().getReservationValueUndiscounted());

		// if you need to initialize some variables, please initialize them
		// below

	}

	@Override
	public Action chooseAction(List<Class<? extends Action>> validActions) {

		// with 50% chance, counter offer
		// if we are the first party, also offer.
//		if (lastReceivedBid == null || !validActions.contains(Accept.class) || Math.random() > 0.5) {
//			return new Offer(getPartyId(), generateRandomBid());
//		} else {
//			return new Accept(getPartyId(), lastReceivedBid);
//		}



		// first determine if in phase 1,2 or 3
		int phase = getPhase();
		if(phase == 1){
			// if in phase one, return a random bid that has a high utility for us
			return phase1Action();
		} else if (phase == 2){
			// if in phase two, do bram using knowledge gathered in previous phase
			return phase2Action();
		} else{
			// if in phase three, send the best offer received
			return phase3Action();
		}


	}

	@Override
	public void receiveMessage(AgentID sender, Action action) {
		super.receiveMessage(sender, action);
		if (action instanceof Offer) {
			Bid bid = ((Offer) action).getBid();
			lastReceivedBid = bid;
			bids.add(bid);
		}

		// first determine if in phase 1,2 or 3

		// if in phase one or two, only accept if above the current utility threshold
		// if in phase three accept pretty much anything. (perhaps with some threshold)
	}


	private int getPhase() {
		//todo verify that getTime is the function that returns normalized (i.e. [0,1]) timeline that tells waht fraction of negotation has passed

		double fraction_passed = this.getTimeLine().getTime();
		if(fraction_passed<phase1Fraction){
			return 1;
		} else if(fraction_passed<phase2Fraction){
			return 2;
		} else {
			return 3;
		}
	}

	private Action phase1Action(){
		// todo verify getUtility is in range [0,1]
		// see if received bid is above threshold, and accept if so.
		if(lastReceivedBid!= null){
			double utility = this.getUtility(lastReceivedBid);
			if(utility>phase1UtilityThreshold){
				return new Accept(getPartyId(), lastReceivedBid);
			}
		}
		// generate random bid above threshold
		Bid validBid = generateBidAboveThreshold(phase1UtilityThreshold);
		return new Offer(getPartyId(), validBid);
	}

	private Action phase2Action() {
		//todo find agreeable modification to BRAM
		double time = this.getTimeLine().getTime();
		double a = phase2UtilityDegradation / phase2Fraction;
		double b = phase1UtilityThreshold - a * phase1Fraction;
		phase2UtilityThreshold = a * time + b;

		int n = this.utilitySpace.getDomain().getIssues().size();

		if (this.getUtility(lastReceivedBid) > phase2UtilityThreshold) {
			return new Accept(getPartyId(), lastReceivedBid);
		} else {
			Bid maxBid = null;
			double maxUtility = 0;
			for (int i = 0; i < 100; i++) {
				Bid generatedBid = generateBidAboveThreshold(phase2UtilityThreshold);
				double opponentUtility = computeOpponentUtility(generatedBid);
				if (opponentUtility > maxUtility) {
					maxUtility = opponentUtility;
					maxBid = generatedBid;
				}
			}
			return new Accept(getPartyId(), maxBid);
		}
	}




	private Double computeOpponentUtility(Bid bid) {
		return 0.0;
	}

	private void updateHistogram() {
		//todo
	}

	private Action phase3Action(){
		if(lastReceivedBid!=null){
			double utility = this.getUtility(lastReceivedBid);
			if(utility>phase3DesperationThreshold){
				return new Accept(getPartyId(), lastReceivedBid);
			}
		}

		// check old bids for best bid above old threshold, that hasn't been sent in phase 3 yet
		Bid bestOldBid = null;
		for(Bid bid: bids) {
			double utility = this.getUtility(bid);
			if(utility > phase3DesperationThreshold && !phase3Bids.contains(bid)) {
				if(bestOldBid== null || this.getUtility(bestOldBid) < this.getUtility(bid)) {
					bestOldBid = bid;
				}
			}
		}

		if(bestOldBid!=null) {
			phase3Bids.add(bestOldBid);
			return new Offer(getPartyId(), bestOldBid);
		}

		// if no such offer is found, generate something random.

		Bid validBid = generateBidAboveThreshold(phase3DesperationThreshold);
		return new Offer(getPartyId(), validBid);
	}

	private Bid generateBidAboveThreshold(Double threshold){
		Bid randomBid = generateRandomBid();
		int i = 0;
		while(this.getUtility(randomBid)< threshold) {
			// to prevent infinite loops, lower threshold after a sufficiently large amount of tries.
			if(i%randomThresholdDecaryN==0){
				threshold-=randomThresholdDecay;
			}
			randomBid = generateRandomBid();
			i++;
		}
		return randomBid;
	}

	@Override
	public String getDescription() {
		return "This is a annoying yet cooperative negotiator";
	}

}

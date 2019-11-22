package BeAJerk;

import java.io.IOException;
import java.nio.channels.AcceptPendingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.Map;


import genius.core.AgentID;
import genius.core.Bid;
import genius.core.Domain;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.uncertainty.AdditiveUtilitySpaceFactory;
import genius.core.uncertainty.BidRanking;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;

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

	ArrayList<Bid> phase3Bids = new ArrayList<Bid>();

	double randomThresholdDecay = 0.1;
	int randomThresholdDecaryN = 1000;

	// Opponent model parameters	
	private Map<Integer, Map<ValueDiscrete, Integer> > HistCounts = new HashMap<>(); // HistCounts
	private Map<Integer, Map<ValueDiscrete, Double> > HistUtils = new HashMap<>(); // HistUtils
	private Map<Integer, Double> IssueWeights = new HashMap<>(); // IssueWeights map	
	private int histogramWindown = 10; // last n bids

	Logger logger = null;


	@Override
	public void init(NegotiationInfo info) {

		super.init(info);

		System.out.println("Discount Factor is " + getUtilitySpace().getDiscountFactor());
		System.out.println("Reservation Value is " + getUtilitySpace().getReservationValueUndiscounted());

		// if you need to initialize some variables, please initialize them
		// below

		initOpponentModel();
		
		logger = Logger.getLogger("JerkLog");
		FileHandler fh;

		try {

			// This block configure the logger with handler and formatter
			fh = new FileHandler("logs/jerk_log.log");
			logger.addHandler(fh);
			SimpleFormatter formatter = new SimpleFormatter();
			fh.setFormatter(formatter);

			// the following statement is used to log any messages
			logger.info("Initializing new Jerk agent");
			logger.info("max utiltiy is " + this.getUtility(info.getUtilitySpace().getMaxUtilityBid()));

		} catch (Exception e ) {
			e.printStackTrace();
		}

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
    public AbstractUtilitySpace estimateUtilitySpace(){
	    Domain domain = getDomain();
            AdditiveUtilitySpaceFactory factory = new AdditiveUtilitySpaceFactory(domain);
			List<IssueDiscrete> issues = factory.getIssues();
			//Iterate over all issues and values
			for(IssueDiscrete issue: issues){
				factory.setWeight(issue,1 / issues.size());

				List<ValueDiscrete> values = issue.getValues();
				for(ValueDiscrete value: values){
					//get bidranking
					int count = 0;
					int counter = 0;
					List<Bid> bidRanking = this.userModel.getBidRanking().getBidOrder();
					for(Bid bid: bidRanking){
						counter++;
						//If the bid equals the right value in the bid order, set counter to 1
						if (bid.containsValue(issue, value)) {
							//Count the values, depending on its place in the list
							count = count + counter;
						}
					}
					System.out.println("Issue: " + issue + " Value: " + value + " Count: " + count);
					factory.setUtility(issue,value,(double)count);
				}

			}
            //estimate utility space, scale the values
           	factory.scaleAllValuesFrom0To1();
            return factory.getUtilitySpace();
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

		double fraction_passed = this.getTimeLine().getTime();
		logger.info("the fraction that has passed is: " + fraction_passed);
		if(fraction_passed<phase1Fraction){
			return 1;
		} else if(fraction_passed<phase2Fraction){
			return 2;
		} else {
			return 3;
		}
	}

	private Action phase1Action(){
		// see if received bid is above threshold, and accept if so.
		if(lastReceivedBid!= null){
			double utility = this.getUtility(lastReceivedBid);
			logger.info("the utility of the last bid is: "+utility);
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

			if(bids.size() >= histogramWindown)
			{
				updateOpponentModel(); // Update opponent model based on last n bids received
			}

			for (int i = 0; i < 100; i++) {
				Bid generatedBid = generateBidAboveThreshold(phase2UtilityThreshold);
				double opponentUtility = computeOpponentUtility(generatedBid);
				if (opponentUtility >= maxUtility) {
					maxUtility = opponentUtility;
					maxBid = generatedBid;
				}
			}

			// this safeguard is here in case the oponnent model doesnt generate sufficiently high utility offers.
			if(maxBid==null){
				maxBid = generateBidAboveThreshold(phase2UtilityThreshold);
			}

			return new Offer(getPartyId(), maxBid);
		}
	}

	private void initOpponentModel()
	{
		// Create Histogram and initialize opponent's issue weights
		for (Issue currentIssue : getUtilitySpace().getDomain().getIssues())
		{	
			Map<ValueDiscrete, Integer> ValCount = new HashMap<>(); // New value count map for this issue
			Map<ValueDiscrete, Double> ValUtil = new HashMap<>(); // New value utility map for this issue
			
			IssueDiscrete discreteIssue = (IssueDiscrete) currentIssue; // To get values, use discrete class			
			for (ValueDiscrete currentValue : discreteIssue.getValues())
			{								
				ValCount.put(currentValue, 0); // Initialize value count to zero
				ValUtil.put(currentValue, 0.0); // Initialize value utility to zero
			}
			
			// Add all value counts and utilities for this issue
			HistCounts.put(currentIssue.getNumber(), ValCount);
			HistUtils.put(currentIssue.getNumber(), ValUtil);
			
			// Initialize opponent's issue weights
			IssueWeights.put(currentIssue.getNumber(), 0.0);
		}		
		
	}	
	
	private void updateOpponentModel()
	{
		// Reset previous histogram and issue weights (model)
		resetHistogramAndWeights();
		
		// Compute histogram value counts using last n bids
		computeHistogram();
		
		// Compute issue weights and value utilities
		int currCount = 0, maxCount = 0, sumCount = 0;
		double util = 0.0, meanCount = 0.0, variance = 0.0;
		for (Issue currentIssue : getUtilitySpace().getDomain().getIssues())
		{
			IssueDiscrete discreteIssue = (IssueDiscrete) currentIssue; // To get values, use discrete class
			// First, find max and mean of value counts
			for (ValueDiscrete currentValue : discreteIssue.getValues())
			{
				currCount = HistCounts.get(currentIssue.getNumber()).get(currentValue); // get count
				if (currCount > maxCount)
				{
					maxCount = currCount; // Get max count for normalization of utils
				}
				sumCount += currCount;
			}
			
			meanCount = sumCount/discreteIssue.getValues().size();
			variance = 0.0;
			
			// Set value utilities and compute weight of current issue
			for (ValueDiscrete currentValue : discreteIssue.getValues())
			{
				currCount = HistCounts.get(currentIssue.getNumber()).get(currentValue); // get count
				util = (double) currCount/maxCount;
				HistUtils.get(currentIssue.getNumber()).put(currentValue, util); // Set value utilities
				
				variance += Math.pow(currCount - meanCount, 2);
			}
			
			IssueWeights.put(currentIssue.getNumber(), Math.sqrt(variance/discreteIssue.getValues().size()));
		}
		
		// Normalize weights
		normalizeWeights();		
		
	}
	
	private void resetHistogramAndWeights()
	{
		// Reset Histogram and initialize opponent's issue weights
		for (Issue currentIssue : getUtilitySpace().getDomain().getIssues())
		{	
			IssueDiscrete discreteIssue = (IssueDiscrete) currentIssue; // To get values, use discrete class			
			for (ValueDiscrete currentValue : discreteIssue.getValues())
			{								
				HistCounts.get(currentIssue.getNumber()).put(currentValue, 0); // Reset value count to zero
				HistUtils.get(currentIssue.getNumber()).put(currentValue, 0.0); // Reset value utilities to zero								
			}

			// Reset opponent's issue weights
			IssueWeights.put(currentIssue.getNumber(), 0.0);
		}		
		
	}
	
	private void computeHistogram()
	{
		int i, count;
		for (i = bids.size()-1; i > bids.size()-1-histogramWindown; i--)
		{
			Bid currentBid = bids.get(i);
			for (Issue currentIssue : getUtilitySpace().getDomain().getIssues())
			{
				// Update count
				count = HistCounts.get(currentIssue.getNumber()).get((ValueDiscrete)currentBid.getValue(currentIssue.getNumber()));
				HistCounts.get(currentIssue.getNumber()).put((ValueDiscrete)currentBid.getValue(currentIssue.getNumber()), count + 1);
			}
		}
	}
	
	private void normalizeWeights()
	{
		double weight = 0, weightSum = 0.0;
		// Sum the weights
		for (Issue currentIssue : getUtilitySpace().getDomain().getIssues())
		{
			weightSum += IssueWeights.get(currentIssue.getNumber());		
		}
		
		// Normalize
		for (Issue currentIssue : getUtilitySpace().getDomain().getIssues())
		{
			weight = IssueWeights.get(currentIssue.getNumber());
			IssueWeights.put(currentIssue.getNumber(), weight/weightSum);
		}
	}
	
	private Double computeOpponentUtility(Bid bid) {
		Double oppUtility = 0.0;
		int issueNumber;
		for (Issue currentIssue : getUtilitySpace().getDomain().getIssues())
		{
			issueNumber = currentIssue.getNumber();
			oppUtility += IssueWeights.get(issueNumber) * HistUtils.get(issueNumber).get((ValueDiscrete)bid.getValue(issueNumber));			
		}
		return oppUtility;
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

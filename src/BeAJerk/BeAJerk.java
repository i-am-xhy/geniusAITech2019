package BeAJerk;

import java.util.List;

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

	private Bid lastReceivedBid = null;

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
		if (lastReceivedBid == null || !validActions.contains(Accept.class) || Math.random() > 0.5) {
			return new Offer(getPartyId(), generateRandomBid());
		} else {
			return new Accept(getPartyId(), lastReceivedBid);
		}

		// first determine if in phase 1,2 or 3

		// if in phase one, return a random bid that has a high utility for us
		// if in phase two, do bram using knowledge gathered in previous phase
		// if in phase three, send the best offer received
	}

	@Override
	public void receiveMessage(AgentID sender, Action action) {
		super.receiveMessage(sender, action);
		if (action instanceof Offer) {
			lastReceivedBid = ((Offer) action).getBid();
		}

		// first determine if in phase 1,2 or 3

		// if in phase one or two, only accept if above the current utility threshold
		// if in phase three accept pretty much anything. (perhaps with some threshold)
	}

	@Override
	public String getDescription() {
		return "This is a annoying yet cooperative negotiator";
	}

}

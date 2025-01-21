package projectAgents;

import java.util.ArrayList;
import java.util.List;

import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class BazaarAgent extends Agent {

    // ask for the object the player will use in that round
    // update scoreboard and next round prices

    // ?? example of a round
    // 1. Market update
    //  prices are announced (with associated coin value)
    //  events might have a probability of happening
    // 2. Exchanges and negotiations 
    // between merchants
    // 3. Sale to the Market
    // agents decide so sell or hold
    // 4. Next round

    // merchants
    // arrive to market with limited stocks of spices
    // alert to profit opportunities and sabotagin rivals

    // prices of spices fluctuate based on demand and unexpected events
    // constant interaction, temporary alliances, betrayals

    private List<AID> activeParticipants;
    private static final int TOTAL_ROUNDS = 15;

    @Override
    protected void setup() {
        System.out.println("BazzarAgent" + getLocalName() + " started");
        activeParticipants = new ArrayList<>();

        AID[] participantAgents = findAgentsByService("market");
        if (participantAgents != null && participantAgents.length > 0) {
            for (AID participant : participantAgents) {
                activeParticipants.add(participant);
                System.out.println(participant.getName());
            }

            for (int i = 1; i <= TOTAL_ROUNDS; i++) {
                System.out.println(getLocalName() + " - Starting round" + i);

                // implementar ronda

            }
        }
    }

    private AID[] findAgentsByService(String serviceType) {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType(serviceType);
            dfd.addServices(sd);

            DFAgentDescription[] result = DFService.search(this, dfd);
            AID[] agentIDs = new AID[result.length];
            for (int i = 0; i < result.length; i++) {
                agentIDs[i] = result[i].getName();
            }
            return agentIDs;
        } catch (FIPAException e) {
            e.printStackTrace();
            return null;
        }
    }

}

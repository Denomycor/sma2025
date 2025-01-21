package projectAgents;

import java.util.ArrayList;
import java.util.List;

import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.HashMap;
import java.util.Map;

import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.lang.acl.ACLMessage;

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

public class BazaarAgent extends Agent {

    // private static final int TOTAL_ROUNDS = 10;
    // private List<String> events = new ArrayList<>();

    private List<AID> activeParticipants;
    private Map<String, Integer> spicePrices = new HashMap<>();

    @Override
    protected void setup() {
        System.out.println("BazzarAgent" + getLocalName() + " started");
        iniciatePrices();
        activeParticipants = new ArrayList<>();

        AID[] participantAgents = findAgentsByService("market");
        if (participantAgents != null && participantAgents.length > 0) {
            for (AID participant : participantAgents) {
                activeParticipants.add(participant);
                System.out.println(participant.getName());
            }

            SequentialBehaviour behaviour = new SequentialBehaviour(this);
            behaviour.addSubBehaviour(new GameStartBehaviour());
            behaviour.addSubBehaviour(new RoundBehaviour());
            addBehaviour(behaviour);

        } else {
            System.out.println("No participant agents found.");
            doDelete();
        }
    }

    private void iniciatePrices() {
        spicePrices.put("Cravinho", 20);
        spicePrices.put("Cinnamon", 5);
        spicePrices.put("Nutmeg", 15);
        spicePrices.put("Cardamom", 10);
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

    private class GameStartBehaviour extends OneShotBehaviour {
        public void action() {
            // game start implementation
            System.out.println(getLocalName() + " - game is starting");
        }
    }

    private class RoundBehaviour extends SimpleBehaviour {

        int round_counter = 0;
        int round_max = 10;

        public void action() {
            int cravinho_stock = 0;
            int cinnamon_stock = 0;
            int nutmeg_stock = 0;
            int cardamom_stock = 0;

            // 1. Ask for stock to all participants
            ACLMessage requestStock = new ACLMessage(ACLMessage.REQUEST);
            requestStock.setContent("STOCK");
            for (AID participant : activeParticipants) {
                requestStock.addReceiver(participant);
            }
            myAgent.send(requestStock);

            // 2. Receive answers of stock from all participants
            int responsesReceived = 0;
            while (responsesReceived < activeParticipants.size()) {
                ACLMessage reply = myAgent.blockingReceive();
                if (reply != null && reply.getPerformative() == ACLMessage.INFORM) {
                    responsesReceived++;
                    String content = reply.getContent();
                    String[] stocks = content.split(",");

                    cravinho_stock += Integer.parseInt(stocks[0]);
                    cinnamon_stock += Integer.parseInt(stocks[1]);
                    nutmeg_stock += Integer.parseInt(stocks[2]);
                    cardamom_stock += Integer.parseInt(stocks[3]);
                }
            }

            System.out.println("Cravinho: "+ cravinho_stock);
            System.out.println("Cinnamon: "+ cinnamon_stock);
            System.out.println("Nutmeg: "+ nutmeg_stock);
            System.out.println("Cardamom: "+ cardamom_stock);

            adjustPrices(cravinho_stock, cinnamon_stock, nutmeg_stock, cardamom_stock);

            broadcastPrices();


            round_counter ++;
        }

        public boolean done() {
            return round_counter < round_max;
        }
    }

    private void adjustPrices(int cravinhoStock, int cinnamonStock, int nutmegStock, int cardamomStock) {
        // cravinho rare and valuable
        if (cravinhoStock < 10) {
            spicePrices.put("Cravinho", spicePrices.get("Cravinho") + 10);
        } else if (cravinhoStock > 30) {
            spicePrices.put("Cravinho", Math.max(20, spicePrices.get("Cravinho") - 5));
        }

        // cinnamon stable
        if (cinnamonStock > 40) {
            spicePrices.put("Cinnamon", Math.max(3, spicePrices.get("Cinnamon") - 1));
        } else if (cinnamonStock < 20) {
            spicePrices.put("Cinnamon", spicePrices.get("Cinnamon") + 2);
        }

        // nutmeg sensitive to demand
        if (nutmegStock < 15) {
            spicePrices.put("Nutmeg", spicePrices.get("Nutmeg") + 10);
        } else if (nutmegStock > 35) {
            spicePrices.put("Nutmeg", Math.max(10, spicePrices.get("Nutmeg") - 5));
        }

        // cardamom volatile
        if (cardamomStock < 10) {
            spicePrices.put("Cardamom", spicePrices.get("Cardamom") + 5);
        } else if (cardamomStock > 40) {
            spicePrices.put("Cardamom", Math.max(5, spicePrices.get("Cardamom") - 3));
        }

        // for debug
        System.out.println("Updated spice prices: " + spicePrices);
    }

    private void broadcastPrices() {
        ACLMessage priceMessage = new ACLMessage(ACLMessage.INFORM);
        StringBuilder priceInfo = new StringBuilder("Updated Prices: ");
        int count = 0;
        for (Map.Entry<String, Integer> entry : spicePrices.entrySet()) {
            priceInfo.append(entry.getKey()).append("=").append(entry.getValue());
            if (++count < spicePrices.size()) {
                priceInfo.append(", ");
            }
        }
        priceMessage.setContent(priceInfo.toString());
        for (AID participant : activeParticipants) {
            priceMessage.addReceiver(participant);
        }
        send(priceMessage);
    
        System.out.println("Broadcasted prices to participants: " + spicePrices);
    }

}

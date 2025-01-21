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

    private List<AID> activeParticipants;
    private static final int TOTAL_ROUNDS = 10;
    private Map<String, Integer> currentPrices = new HashMap<>();
    private Map<String, Integer> stock = new HashMap<>();

    @Override
    protected void setup() {
        System.out.println("BazzarAgent" + getLocalName() + " started");
        iniciatePrices();
        initializeStock();
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
        currentPrices.put("Cravinho", 20);
        currentPrices.put("Cinnamon", 5);
        currentPrices.put("Nutmeg", 15);
        currentPrices.put("Cardamom", 10);
    }

    private void initializeStock() {
        stock.put("Cravinho", 0);
        stock.put("Cinnamon", 0);
        stock.put("Nutmeg", 0);
        stock.put("Cardamom", 0);
    }

    private void resetStock() {
        stock.put("Cravinho", 0);
        stock.put("Cinnamon", 0);
        stock.put("Nutmeg", 0);
        stock.put("Cardamom", 0);
    }

    private String getPricesAsCommaSeparatedString() {
        return  currentPrices.get("Cravinho") + "," +
                currentPrices.get("Cinnamon") + "," +
                currentPrices.get("Nutmeg") + "," +
                currentPrices.get("Cardamom");
    }

    private class GameStartBehaviour extends OneShotBehaviour {
        public void action() {
            // game start implementation
            System.out.println(getLocalName() + " - game is starting");
        }
    }

    private class RoundBehaviour extends SimpleBehaviour {

        int round_counter = 0;

        public void action() {
            resetStock();

            // Get Stock

            // 1. Ask for stock from all participants
            ACLMessage requestStock = new ACLMessage(ACLMessage.REQUEST);
            requestStock.setContent("STOCK");
            for (AID participant : activeParticipants) {
                requestStock.addReceiver(participant);
            }
            myAgent.send(requestStock);

            // 2. Receive stock responses and update centralized stock
            int responsesReceived = 0;
            while (responsesReceived < activeParticipants.size()) {
                ACLMessage reply = myAgent.blockingReceive();
                if (reply != null && reply.getPerformative() == ACLMessage.INFORM) {
                    responsesReceived++;
                    String content = reply.getContent();
                    String[] stocks = content.split(",");

                    stock.put("Cravinho", stock.get("Cravinho") + Integer.parseInt(stocks[0]));
                    stock.put("Cinnamon", stock.get("Cinnamon") + Integer.parseInt(stocks[1]));
                    stock.put("Nutmeg", stock.get("Nutmeg") + Integer.parseInt(stocks[2]));
                    stock.put("Cardamom", stock.get("Cardamom") + Integer.parseInt(stocks[3]));
                }
            }

            System.out.println("Updated stock: " + stock);


            // Update Prices

            adjustPrices();
            

            // Broadcast Prices

            ACLMessage priceMessage = new ACLMessage(ACLMessage.INFORM);
            String priceInfo = getPricesAsCommaSeparatedString();
            priceMessage.setContent(priceInfo);
            for (AID participant : activeParticipants) {
                priceMessage.addReceiver(participant);
            }
            send(priceMessage);
            System.out.println("Broadcasted prices to participants: " + priceInfo);

            round_counter++;
        }

        public boolean done() {
            return round_counter >= TOTAL_ROUNDS;
        }
    }

    private void adjustPrices() {
        // Access centralized stock directly
        int cravinhoStock = stock.get("Cravinho");
        int cinnamonStock = stock.get("Cinnamon");
        int nutmegStock = stock.get("Nutmeg");
        int cardamomStock = stock.get("Cardamom");
    
        // cravinho rare and valuable
        if (cravinhoStock < 10) {
            currentPrices.put("Cravinho", currentPrices.get("Cravinho") + 10);
        } else if (cravinhoStock > 30) {
            currentPrices.put("Cravinho", Math.max(20, currentPrices.get("Cravinho") - 5));
        }
    
        // cinnamon stable
        if (cinnamonStock > 40) {
            currentPrices.put("Cinnamon", Math.max(3, currentPrices.get("Cinnamon") - 1));
        } else if (cinnamonStock < 20) {
            currentPrices.put("Cinnamon", currentPrices.get("Cinnamon") + 2);
        }
    
        // nutmeg sensitive to demand
        if (nutmegStock < 15) {
            currentPrices.put("Nutmeg", currentPrices.get("Nutmeg") + 10);
        } else if (nutmegStock > 35) {
            currentPrices.put("Nutmeg", Math.max(10, currentPrices.get("Nutmeg") - 5));
        }
    
        // cardamom volatile
        if (cardamomStock < 10) {
            currentPrices.put("Cardamom", currentPrices.get("Cardamom") + 5);
        } else if (cardamomStock > 40) {
            currentPrices.put("Cardamom", Math.max(5, currentPrices.get("Cardamom") - 3));
        }
    
        // for debug
        System.out.println("Updated spice prices: " + currentPrices);
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

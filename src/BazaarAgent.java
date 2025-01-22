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
    private Map<String, Integer> prices = new HashMap<>();
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
                System.out.println("Participant agents:");
                System.out.println(participant.getLocalName());
            }

            SequentialBehaviour behaviour = new SequentialBehaviour(this);
            behaviour.addSubBehaviour(new GameStartBehaviour());
            behaviour.addSubBehaviour(new RoundBehaviour());
            addBehaviour(behaviour);

        } else {
            System.out.println("No participant agents found");
            doDelete();
        }
    }

    private void iniciatePrices() {
        prices.put("Cravinho", 20);
        prices.put("Cinnamon", 5);
        prices.put("Nutmeg", 15);
        prices.put("Cardamom", 10);
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
        return  prices.get("Cravinho") + "," +
                prices.get("Cinnamon") + "," +
                prices.get("Nutmeg") + "," +
                prices.get("Cardamom");
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

            handleStockRequestsAndResponses(5000); // 5 seconds

            System.out.println(getLocalName() + " - Updated stock: " + stock);

            // Update Prices
            adjustPrices();
            
            // Broadcast Prices
            broadcastPrices();

            round_counter++;
        }

        private void handleStockRequestsAndResponses(long timeoutDuration) {
            // 1. Send stock request to all participants
            ACLMessage requestStock = new ACLMessage(ACLMessage.REQUEST);
            requestStock.setContent("STOCK");
            for (AID participant : activeParticipants) {
                requestStock.addReceiver(participant);
            }
            send(requestStock);
            System.out.println(getLocalName() + " - Sent stock requests to all participants.");

            // 2. Process responses with a timeout
            List<AID> responsiveParticipants = new ArrayList<>();
            long timeout = System.currentTimeMillis() + timeoutDuration;

            while (System.currentTimeMillis() < timeout && responsiveParticipants.size() < activeParticipants.size()) {
                ACLMessage reply = receive();
                if (reply != null && reply.getPerformative() == ACLMessage.INFORM) {

                    // 2.1 On reply update stocks
                    responsiveParticipants.add(reply.getSender());
                    updateStock(reply);
                } else if (reply == null) {
                    block(100);
                }
            }

            // 2.2 Remove unresponsive participants
            for (AID participant : new ArrayList<>(activeParticipants)) {
                if (!responsiveParticipants.contains(participant)) {
                    System.out.println(getLocalName() + " - Removing unresponsive participant: " + participant.getLocalName());
                    activeParticipants.remove(participant);
                }
            }
        }

        private void updateStock(ACLMessage reply) {
            String content = reply.getContent();
            String[] stocks = content.split(",");
    
            stock.put("Cravinho", stock.get("Cravinho") + Integer.parseInt(stocks[0]));
            stock.put("Cinnamon", stock.get("Cinnamon") + Integer.parseInt(stocks[1]));
            stock.put("Nutmeg", stock.get("Nutmeg") + Integer.parseInt(stocks[2]));
            stock.put("Cardamom", stock.get("Cardamom") + Integer.parseInt(stocks[3]));
    
            System.out.println(getLocalName() + " - Received stock from " + reply.getSender().getLocalName() + ": " + content);
        }

        private void broadcastPrices() {
            ACLMessage priceMessage = new ACLMessage(ACLMessage.INFORM);
            String priceInfo = getPricesAsCommaSeparatedString();
            priceMessage.setContent(priceInfo);
            for (AID participant : activeParticipants) {
                priceMessage.addReceiver(participant);
            }
            send(priceMessage);
            System.out.println(getLocalName() + " - Broadcasted prices to participants: " + priceInfo);
        }

        public boolean done() {
            return round_counter >= TOTAL_ROUNDS;
        }
    }

    private void adjustPrices() {
        int cravinhoStock = stock.get("Cravinho");
        int cinnamonStock = stock.get("Cinnamon");
        int nutmegStock = stock.get("Nutmeg");
        int cardamomStock = stock.get("Cardamom");
    
        // cravinho rare and valuable
        if (cravinhoStock < 10) {
            prices.put("Cravinho", prices.get("Cravinho") + 10);
        } else if (cravinhoStock > 30) {
            prices.put("Cravinho", Math.max(20, prices.get("Cravinho") - 5));
        }
    
        // cinnamon stable
        if (cinnamonStock > 40) {
            prices.put("Cinnamon", Math.max(3, prices.get("Cinnamon") - 1));
        } else if (cinnamonStock < 20) {
            prices.put("Cinnamon", prices.get("Cinnamon") + 2);
        }
    
        // nutmeg sensitive to demand
        if (nutmegStock < 15) {
            prices.put("Nutmeg", prices.get("Nutmeg") + 10);
        } else if (nutmegStock > 35) {
            prices.put("Nutmeg", Math.max(10, prices.get("Nutmeg") - 5));
        }
    
        // cardamom volatile
        if (cardamomStock < 10) {
            prices.put("Cardamom", prices.get("Cardamom") + 5);
        } else if (cardamomStock > 40) {
            prices.put("Cardamom", Math.max(5, prices.get("Cardamom") - 3));
        }
    
        // for debug
        System.out.println("Updated spice prices: " + prices);
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

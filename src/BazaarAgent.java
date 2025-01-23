package projectAgents;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.SimpleBehaviour;
import jade.lang.acl.ACLMessage;

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
                System.out.println("Participant agent: " + participant.getLocalName() + " joined the game");
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

    private class GameStartBehaviour extends OneShotBehaviour {
        public void action() {
            System.out.println(getLocalName() + " - game is starting");
        }
    }

    private class RoundBehaviour extends SimpleBehaviour {

        int round_counter = 0;
        private String nextRoundEventType = null;
        private String nextRoundTarget = null;

        public void action() {
            System.out.println("ROUND " + round_counter);

            resetStock();

            getStockFromMerchants();

            System.out.println(getLocalName() + " - updated stock: " + stock.toString());

            adjustPrices();

            determineNextRoundEvent();

            broadcastPricesAndEvent();

            round_counter++;
        }

        private void resetStock() {
            stock.put("Cravinho", 0);
            stock.put("Cinnamon", 0);
            stock.put("Nutmeg", 0);
            stock.put("Cardamom", 0);
            System.out.println(getLocalName() + " - Stock has been reset for the new round.");
        }

        private void getStockFromMerchants() {
            ACLMessage requestStock = new ACLMessage(ACLMessage.REQUEST);
            requestStock.setContent("STOCK");
            for (AID participant : activeParticipants) {
                requestStock.addReceiver(participant);
            }
            send(requestStock);
            System.out.println(getLocalName() + " - Sent stock requests to all participants.");

            int responsesReceived = 0;

            while (responsesReceived < activeParticipants.size()) {
                ACLMessage reply = blockingReceive(); // wait for responses
                if (reply != null && reply.getPerformative() == ACLMessage.INFORM) {
                    responsesReceived++;
                    updateStock(reply);
                }
            }
        }

        // Adjust prices based on current stock levels and market size
        private void adjustPrices() {
            int marketFactor = activeParticipants.size();

            int cravinhoStock = stock.get("Cravinho");
            int cinnamonStock = stock.get("Cinnamon");
            int nutmegStock = stock.get("Nutmeg");
            int cardamomStock = stock.get("Cardamom");

            // Cravinho - rare and valuable
            if (cravinhoStock < 10 * marketFactor) {
                prices.put("Cravinho", prices.get("Cravinho") + (10 / marketFactor));
            } else if (cravinhoStock > 30 * marketFactor) {
                prices.put("Cravinho", Math.max(20, prices.get("Cravinho") - (5 / marketFactor)));
            }

            // Cinnamon - stable
            if (cinnamonStock > 40 * marketFactor) {
                prices.put("Cinnamon", Math.max(3, prices.get("Cinnamon") - (1 / marketFactor)));
            } else if (cinnamonStock < 20 * marketFactor) {
                prices.put("Cinnamon", prices.get("Cinnamon") + (2 / marketFactor));
            }

            // Nutmeg - sensitive to demand
            if (nutmegStock < 15 * marketFactor) {
                prices.put("Nutmeg", prices.get("Nutmeg") + (10 / marketFactor));
            } else if (nutmegStock > 35 * marketFactor) {
                prices.put("Nutmeg", Math.max(10, prices.get("Nutmeg") - (5 / marketFactor)));
            }

            // Cardamom - volatile
            if (cardamomStock < 10 * marketFactor) {
                prices.put("Cardamom", prices.get("Cardamom") + (5 / marketFactor));
            } else if (cardamomStock > 40 * marketFactor) {
                prices.put("Cardamom", Math.max(5, prices.get("Cardamom") - (3 / marketFactor)));
            }

            // Adjust prices based on event
            if (nextRoundEventType != null) {
                switch (nextRoundEventType) {
                    case "SULTAN_TAX":
                        // Increase all prices due to Sultan's tax
                        for (Map.Entry<String, Integer> entry : prices.entrySet()) {
                            prices.put(entry.getKey(), (int) (entry.getValue() * 1.1)); // 10% increase
                        }
                        System.out.println(getLocalName() + " - Sultan's Tax: Increased all prices by 10%.");
                        break;

                    case "TRADE_ROUTE":
                        // Decrease price of the affected spice due to a new trade route
                        if (nextRoundTarget != null) {
                            int currentPrice = prices.get(nextRoundTarget);
                            prices.put(nextRoundTarget, (int) (currentPrice * 0.8)); // 20% decrease
                            System.out.println(getLocalName() + " - Trade Route: Decreased price of " + nextRoundTarget
                                    + " by 20%.");
                        }
                        break;

                    default:
                        break;
                }
            }

            System.out.println(
                    getLocalName() + " - Adjusted prices considering market size (" + marketFactor + "): " + prices);
        }

        private void updateStock(ACLMessage reply) {
            String content = reply.getContent();
            String[] stocks = content.split(",");

            stock.put("Cravinho", stock.get("Cravinho") + Integer.parseInt(stocks[0]));
            stock.put("Cinnamon", stock.get("Cinnamon") + Integer.parseInt(stocks[1]));
            stock.put("Nutmeg", stock.get("Nutmeg") + Integer.parseInt(stocks[2]));
            stock.put("Cardamom", stock.get("Cardamom") + Integer.parseInt(stocks[3]));
        }

        private void broadcastPricesAndEvent() {
            StringBuilder messageContent = new StringBuilder();
            messageContent.append("PRICES,").append(getPricesAsCommaSeparatedString());

            messageContent.append("|EVENT,");
            if (nextRoundEventType == null) {
                messageContent.append("No significant events.");
            } else {
                switch (nextRoundEventType) {
                    case "STORM":
                        messageContent.append("A storm destroyed " + nextRoundTarget
                                + " plantations: the price will increase in the next round.");
                        break;
                    case "SULTAN_TAX":
                        messageContent.append(
                                "The Sultan has imposed a new tax: all prices will increase in the next round.");
                        break;
                    case "TRADE_ROUTE":
                        messageContent.append("A new trade route has been discovered for " + nextRoundTarget
                                + ": the price will decrease in the next round.");
                        break;
                }
            }

            // Send the message to all active participants
            ACLMessage broadcastMessage = new ACLMessage(ACLMessage.INFORM);
            broadcastMessage.setContent(messageContent.toString());
            for (AID participant : activeParticipants) {
                broadcastMessage.addReceiver(participant);
            }
            send(broadcastMessage);
            System.out.println(getLocalName() + " - Broadcasted prices and event to participants: " + messageContent);
        }

        private void determineNextRoundEvent() {
            double eventProbability = Math.random();
            if (eventProbability < 0.15) {
                nextRoundEventType = "STORM";
                nextRoundTarget = pickRandomSpice();
            } else if (eventProbability < 0.30) {
                nextRoundEventType = "SULTAN_TAX";
            } else if (eventProbability < 0.45) {
                nextRoundEventType = "TRADE_ROUTE";
                nextRoundTarget = pickRandomSpice();
            } else {
                nextRoundEventType = null;
                nextRoundTarget = null;
            }
        }

        private String pickRandomSpice() {
            String[] spices = { "Cravinho", "Cinnamon", "Nutmeg", "Cardamom" };
            return spices[(int) (Math.random() * spices.length)];
        }

        private String getPricesAsCommaSeparatedString() {
            return prices.get("Cravinho") + "," + prices.get("Cinnamon") + "," + prices.get("Nutmeg") + ","
                    + prices.get("Cardamom");
        }

        public boolean done() {
            return round_counter >= TOTAL_ROUNDS;
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

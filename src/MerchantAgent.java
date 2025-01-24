package projectAgents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

import java.util.HashMap;
import java.util.Map;

public class MerchantAgent extends Agent {

    private Map<String, Integer> stock;
    private Map<String, Integer> prices;
    private String stormAffectedSpice = null;
    private int totalRounds = 0;
    private int currentRound = 0;

    protected void setup() {
        System.out.println("MerchantAgent " + getLocalName() + " started");

        initializeStock();
        initializePrices();

        registerInDF("market", "market-service");

        addBehaviour(new MessageHandler());
    }

    private void initializeStock() {
        stock = new HashMap<>();
        stock.put("Cravinho", 10);
        stock.put("Cinnamon", 15);
        stock.put("Nutmeg", 20);
        stock.put("Cardamom", 12);
    }

    private void initializePrices() {
        prices = new HashMap<>();
        prices.put("Cravinho", 0);
        prices.put("Cinnamon", 0);
        prices.put("Nutmeg", 0);
        prices.put("Cardamom", 0);
    }

    private String getStockAsCommaSeparatedString() {
        return stock.get("Cravinho") + "," +
                stock.get("Cinnamon") + "," +
                stock.get("Nutmeg") + "," +
                stock.get("Cardamom");
    }

    private void applyStormImpact() {
        if (stormAffectedSpice != null) {
            int currentStock = stock.get(stormAffectedSpice);
            int reducedStock = Math.max(0, currentStock / 2); // Reduce stock by 50%
            stock.put(stormAffectedSpice, reducedStock);
            System.out.println(getLocalName() + " - Storm reduced " + stormAffectedSpice + " stock to " + reducedStock);
            stormAffectedSpice = null; // Reset the flag
        }
    }


    private class MessageHandler extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg != null) {
                if (msg.getPerformative() == ACLMessage.REQUEST && "STOCK".equals(msg.getContent())) {
                    // Apply storm
                    applyStormImpact();

                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent(getStockAsCommaSeparatedString());
                    myAgent.send(reply);
                    System.out.println(getLocalName() + " - Sent stock details: " + getStockAsCommaSeparatedString());
                } else if (msg.getPerformative() == ACLMessage.INFORM) {
                    if (msg.getContent().startsWith("PRICES,")) {
                        // Process Broadcast
                        processBroadcast(msg);
                    } else if (msg.getContent().startsWith("TOTAL_ROUNDS")) {
                        totalRounds = Integer.parseInt(msg.getContent().split("=")[1]);
    
                        ACLMessage ack = msg.createReply();
                        ack.setPerformative(ACLMessage.CONFIRM);
                        ack.setContent("ACK");
                        myAgent.send(ack);
                    } else if (msg.getContent().startsWith("CURRENT_ROUND")) {
                        currentRound = Integer.parseInt(msg.getContent().split("=")[1]);
    
                        ACLMessage ack = msg.createReply();
                        ack.setPerformative(ACLMessage.CONFIRM);
                        ack.setContent("ACK");
                        myAgent.send(ack);
                    }
                }
            } else {
                block();
            }
        }

        private void processBroadcast(ACLMessage msg) {
            String[] parts = msg.getContent().split("\\|");
            String pricesPart = parts[0].replace("PRICES,", "").trim();
            String eventPart = parts[1].replace("EVENT,", "").trim();

            // Update local prices
            String[] receivedPrices = pricesPart.split(",");
            prices.put("Cravinho", Integer.parseInt(receivedPrices[0].trim()));
            prices.put("Cinnamon", Integer.parseInt(receivedPrices[1].trim()));
            prices.put("Nutmeg", Integer.parseInt(receivedPrices[2].trim()));
            prices.put("Cardamom", Integer.parseInt(receivedPrices[3].trim()));

            // Check for a storm event and update affected spice
            if (eventPart.startsWith("A storm destroyed")) {
                String[] eventDetails = eventPart.split(" ");
                stormAffectedSpice = eventDetails[3];
                System.out.println(getLocalName() + " - Storm will impact " + stormAffectedSpice + " stock in the next round.");
            }

            ACLMessage ack = msg.createReply();
            ack.setPerformative(ACLMessage.CONFIRM);
            ack.setContent("ACK");
            myAgent.send(ack);
        }
    }

    private void registerInDF(String serviceType, String serviceName) {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(serviceType);
        sd.setName(serviceName);
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println(getLocalName() + " - registered with the DF as a " + serviceType + " agent.");
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println(getLocalName() + " - terminating.");
    }
}
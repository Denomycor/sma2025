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

    private String nextRoundEvent = null;
    private String nextRoundTarget = null;

    private int totalRounds = 0;
    private int currentRound = 0;

    // agents with diffrent risk factors (0.1, 0.5, 0.9)
    private double riskFactor = 1.0;
    // riskFactor closer to 0, it values immediate returns over potential future
    // gains
    // riskFactor closer to 1, it values potential future gains over immediate
    // returns

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
        if ("STORM".equals(nextRoundEvent) && nextRoundTarget != null) {
            int currentStock = stock.get(nextRoundTarget);
            int reducedStock = Math.max(0, currentStock / 2); // Reduce stock by 50%
            stock.put(nextRoundTarget, reducedStock);
            System.out.println(getLocalName() + " - Storm reduced " + nextRoundTarget + " stock to " + reducedStock);
            nextRoundEvent = null;
            nextRoundTarget = null;
        }
    }

    private double calculateRawUtilitySell(String spice) {
        int currentPrice = prices.get(spice);
        double expectedPrice = predictExpectedPrice(spice);
        double roundWeight = (double) currentRound / totalRounds;

        return (currentPrice * (1 - riskFactor)) + (roundWeight * expectedPrice * (1 - riskFactor));
    }

    private double calculateRawUtilityHold(String spice) {
        double expectedPrice = predictExpectedPrice(spice);

        return expectedPrice * (1 + riskFactor);
    }

    private double predictExpectedPrice(String spice) {
        int currentPrice = prices.get(spice);

        switch (nextRoundEvent) {
            case "STORM":
                if (nextRoundTarget != null && nextRoundTarget.equals(spice)) {

                    // Introduce a trade-off: assume stock loss will dampen price benefits.
                    double adjustedPrice = currentPrice * 1.5; // Price increase due to storm
                    double stockLossFactor = 0.5; // Losing 50% of stock
                    return adjustedPrice * stockLossFactor;
                }
                break;

            case "TRADE_ROUTE":
                if (nextRoundTarget != null && nextRoundTarget.equals(spice)) {
                    // Decreasing price by 20%.
                    return currentPrice * 0.8;
                }
                break;

            case "SULTAN_TAX":
                // Reducing prices by 10%.
                return currentPrice * 0.9;

            default:
                // Return the current price since roundWeight is already taken into account
                return currentPrice;
        }

        // Return the current price since roundWeight is already taken into account
        return currentPrice;
    }

    private double normalizeUtilitySell(String spice) {
        double utilitySell = calculateRawUtilitySell(spice);
        double utilityHold = calculateRawUtilityHold(spice);
        return utilitySell / (utilitySell + utilityHold);
    }

    private double normalizeUtilityHold(String spice) {
        double utilitySell = calculateRawUtilitySell(spice);
        double utilityHold = calculateRawUtilityHold(spice);
        return utilityHold / (utilitySell + utilityHold);
    }

    private int decideQuantityToSell(String spice) {
        double normalizedSell = normalizeUtilitySell(spice);
        return (int) Math.round(normalizedSell * stock.get(spice));
    }

    private int decideQuantityToHold(String spice) {
        double normalizedHold = normalizeUtilityHold(spice);
        return (int) Math.round(normalizedHold * stock.get(spice));
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

            // Determine nextRoundEvent and nextRoundTarget
            if (eventPart.startsWith("A storm destroyed")) {
                nextRoundEvent = "STORM";
                nextRoundTarget = eventPart.split(" ")[3];
                System.out.println(getLocalName() + " - Next round event: STORM on " + nextRoundTarget);
            } else if (eventPart.startsWith("The Sultan has imposed a new tax")) {
                nextRoundEvent = "SULTAN_TAX";
                nextRoundTarget = null;
                System.out.println(getLocalName() + " - Next round event: SULTAN_TAX");
            } else if (eventPart.startsWith("A new trade route")) {
                nextRoundEvent = "TRADE_ROUTE";
                nextRoundTarget = eventPart.split(" ")[8];
                System.out.println(getLocalName() + " - Next round event: TRADE_ROUTE for " + nextRoundTarget);
            } else {
                nextRoundEvent = null;
                nextRoundTarget = null;
                System.out.println(getLocalName() + " - Next round event: No significant event.");
            }

            // Send ACK back
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
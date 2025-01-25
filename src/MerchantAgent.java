package projectAgents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

import java.util.HashMap;
import java.util.Map;

// to simplify the negotiations:
// limit negotiations to one proposal per round per agent
// propose simple spice per spice trades
// accept or reject with no counter-proposals
// the merchant will broadcast his proposal to all other merchants
// if multiple accept he will choose one randomly to trade with
// if one accepts trade with that one
// if no one accepts the trade fails

public class MerchantAgent extends Agent {

    private Map<String, Integer> stock;
    private Map<String, Integer> prices;

    private String nextRoundEvent = null;
    private String nextRoundTarget = null;

    private int totalRounds = 0;
    private int currentRound = 0;

    // agents with diffrent risk factors (0.1, 0.5, 0.9)
    private double riskFactor = 0.5; // default risk factor

    // riskFactor closer to 0:
    // values immediate returns over potential future gains
    // propose safer trades that guarantee immediate returns
    // accept trades that immediately improve their utility with little risk

    // riskFactor closer to 1:
    // values potential future gains over immediate returns
    // propose trades that are more beneficial in the long term, even if they seem
    // risky in the short term
    // accept trades with higher potential future gains but also higher uncertainty

    protected void setup() {
        System.out.println("MerchantAgent " + getLocalName() + " started");

        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            try {
                riskFactor = Double.parseDouble(args[0].toString());
                System.out.println(getLocalName() + " - Risk factor set to: " + riskFactor);
            } catch (NumberFormatException e) {
                System.err.println(getLocalName() + " - Invalid risk factor argument. Using default: " + riskFactor);
            }
        }

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

        if (nextRoundEvent == null) {
            // Return the current price since roundWeight is already taken into account
            return currentPrice;
        }

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
                break;
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

    private void proposeTrade() {
        String spiceToSell = chooseSpiceToSell();
        String spiceToBuy = chooseSpiceToBuy(spiceToSell);

        ACLMessage proposal = new ACLMessage(ACLMessage.PROPOSE);
        AID[] merchants = findAgentsByService("market");

        boolean tradeProposed = false;

        if (spiceToSell == null || spiceToBuy == null) {
            proposal.setContent("NO_TRADE");
            if (merchants != null) {
                for (AID merchant : merchants) {
                    proposal.addReceiver(merchant);
                }
            }
            send(proposal);
            System.out.println(getLocalName() + " - No valid trade proposal can be made. Sent NO_TRADE");
        } else {
            int quantityToSell = decideQuantityToSell(spiceToSell);
            int quantityToBuy = (int) Math.round(quantityToSell * adjustTradeRatio(spiceToSell, spiceToBuy));

            String tradeProposal = spiceToSell + "," + quantityToSell + "," + spiceToBuy + "," + quantityToBuy;
            proposal.setContent(tradeProposal);

            if (merchants != null) {
                for (AID merchant : merchants) {
                    proposal.addReceiver(merchant);
                }
            }
            send(proposal);
            tradeProposed = true;
            System.out.println(getLocalName() + " - Sent trade proposal: " + tradeProposal);
        }

        int proposalsReceived = 0;
        int numberOfMerchants = merchants != null ? merchants.length : 0; // Include all merchants
        while (proposalsReceived < numberOfMerchants) {
            ACLMessage reply = blockingReceive();
            if (reply != null && reply.getPerformative() == ACLMessage.PROPOSE) {
                handleTradeProposal(reply);
                proposalsReceived++;
            }
        }

        if (tradeProposed) {
            ACLMessage reply = blockingReceive();
            if (reply != null) {
                if (reply.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                    System.out.println(
                            getLocalName() + " - Trade proposal accepted by " + reply.getSender().getLocalName());
                    finalizeTrade(reply);
                } else if (reply.getPerformative() == ACLMessage.REJECT_PROPOSAL) {
                    System.out.println(
                            getLocalName() + " - Trade proposal rejected by " + reply.getSender().getLocalName());
                }
            } else {
                System.out.println(getLocalName() + " - No response received for the trade proposal.");
            }
        }
    }

    private void finalizeTrade(ACLMessage msg) {
        String proposalContent = msg.getContent();

        System.out.println(getLocalName() + " - Finalizing trade: " + proposalContent);

        String[] tradeDetails = proposalContent.split(",");
        String spiceToSell = tradeDetails[0];
        int quantityToSell = Integer.parseInt(tradeDetails[1]);
        String spiceToBuy = tradeDetails[2];
        int quantityToBuy = Integer.parseInt(tradeDetails[3]);

        // Update stock based on the trade
        stock.put(spiceToSell, stock.get(spiceToSell) - quantityToSell);
        stock.put(spiceToBuy, stock.get(spiceToBuy) + quantityToBuy);

        System.out.println(getLocalName() + " - Trade finalized. Updated stock: " + stock);
    }

    private boolean evaluateTradeProposal(String spiceOffered, int quantityOffered, String spiceRequested,
            int quantityRequested) {
        double utilityGain = calculateRawUtilityHold(spiceOffered) * quantityOffered;
        double utilityLoss = calculateRawUtilitySell(spiceRequested) * quantityRequested;

        return riskFactor < 0.5 ? utilityGain > utilityLoss : utilityGain * (1 + riskFactor) > utilityLoss;
    }

    private String chooseSpiceToSell() {
        return stock.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .max((entry1, entry2) -> Double.compare(normalizeUtilitySell(entry1.getKey()),
                        normalizeUtilitySell(entry2.getKey())))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String chooseSpiceToBuy(String spiceToSell) {
        return stock.keySet().stream()
                .filter(spice -> !spice.equals(spiceToSell))
                .max((spice1, spice2) -> Double.compare(normalizeUtilityHold(spice1), normalizeUtilityHold(spice2)))
                .orElse(null);
    }

    private double adjustTradeRatio(String spiceToSell, String spiceToBuy) {
        double expectedPriceSell = predictExpectedPrice(spiceToSell);
        double expectedPriceBuy = predictExpectedPrice(spiceToBuy);

        double baseRatio = expectedPriceSell / expectedPriceBuy;
        return riskFactor < 0.5 ? baseRatio * (1 - riskFactor) : baseRatio * (1 + riskFactor);
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
                    System.out.println(getLocalName() + " - Sent stock details: " + stock);
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
            } else if (eventPart.startsWith("The Sultan has imposed a new tax")) {
                nextRoundEvent = "SULTAN_TAX";
                nextRoundTarget = null;
                System.out.println(getLocalName() + " - Next round event: SULTAN_TAX");
            } else if (eventPart.startsWith("A new trade route")) {
                nextRoundEvent = "TRADE_ROUTE";
                nextRoundTarget = eventPart.split(" ")[8];
            } else {
                nextRoundEvent = null;
                nextRoundTarget = null;
            }

            // propose trade
            proposeTrade();

            // decide what to sell
            String saleDecision = decideMarketSale();

            ACLMessage reply = msg.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            reply.setContent(saleDecision);
            send(reply);

            System.out.println(getLocalName() + " - Sent market sale decision: " + saleDecision);
        }

        private String decideMarketSale() {
            StringBuilder saleDecision = new StringBuilder();
            boolean isSelling = false;

            for (String spice : stock.keySet()) {
                int quantityToSell = decideQuantityToSell(spice);
                if (quantityToSell > 0) {
                    isSelling = true;
                    int pricePerUnit = prices.get(spice);
                    int totalValue = quantityToSell * pricePerUnit;

                    saleDecision.append(spice)
                            .append(",")
                            .append(quantityToSell)
                            .append(",")
                            .append(totalValue)
                            .append(";");

                    stock.put(spice, stock.get(spice) - quantityToSell);
                }
            }

            if (!isSelling) {
                return "HOLD"; // no sale
            }

            // remove semicolon
            if (saleDecision.length() > 0 && saleDecision.charAt(saleDecision.length() - 1) == ';') {
                saleDecision.deleteCharAt(saleDecision.length() - 1);
            }

            return saleDecision.toString();
        }

    }

    private void handleTradeProposal(ACLMessage msg) {

        String proposalContent = msg.getContent();
        ACLMessage reply = msg.createReply();

        System.out.println(getLocalName() + " - Received trade proposal: " + proposalContent);

        if ("NO_TRADE".equals(proposalContent)) {
            System.out.println(getLocalName() + " - No trade proposal from " + msg.getSender().getLocalName());
            return;
        }

        String[] tradeDetails = proposalContent.split(",");
        String spiceToSell = tradeDetails[0];
        int quantityToSell = Integer.parseInt(tradeDetails[1]);
        String spiceToBuy = tradeDetails[2];
        int quantityToBuy = Integer.parseInt(tradeDetails[3]);

        boolean isAcceptable = evaluateTradeProposal(spiceToBuy, quantityToBuy, spiceToSell, quantityToSell);
        if (isAcceptable) {
            reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
            reply.setContent(proposalContent);
            System.out.println(getLocalName() + " - Accepted trade proposal: " + proposalContent);
        } else {
            reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
            System.out.println(getLocalName() + " - Rejected trade proposal: " + proposalContent);
        }
        send(reply);
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

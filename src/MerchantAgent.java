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

    protected void setup() {
        System.out.println("MerchantAgent" + getLocalName() + " started");

        initializeStock();
        initializePrices();

        registerInDF("market", "market-service");

        addBehaviour(new StockRequestHandler());
        addBehaviour(new PriceUpdateHandler());
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

    private class StockRequestHandler extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg != null) {
                if (msg.getPerformative() == ACLMessage.REQUEST && "STOCK".equals(msg.getContent())) {
                    System.out
                            .println(getLocalName() + " received stock request from " + msg.getSender().getLocalName());

                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent(getStockAsCommaSeparatedString());
                    myAgent.send(reply);

                    System.out.println(getLocalName() + " sent stock details: " + getStockAsCommaSeparatedString());
                }
            } else {
                block();
            }
        }
    }

    private class PriceUpdateHandler extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();
            if (msg != null) {
                if (msg.getPerformative() == ACLMessage.INFORM) {
                    System.out
                            .println(getLocalName() + " received price update from " + msg.getSender().getLocalName());

                    String[] receivedPrices = msg.getContent().split(",");
                    prices.put("Cravinho", Integer.parseInt(receivedPrices[0].trim()));
                    prices.put("Cinnamon", Integer.parseInt(receivedPrices[1].trim()));
                    prices.put("Nutmeg", Integer.parseInt(receivedPrices[2].trim()));
                    prices.put("Cardamom", Integer.parseInt(receivedPrices[3].trim()));

                    System.out.println(getLocalName() + " updated prices: " + prices);
                }
            } else {
                block();
            }
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
            System.out.println(getLocalName() + "- registered with the DF as a " + serviceType + " agent.");
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

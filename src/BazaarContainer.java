package projectAgents;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

public class BazaarContainer {

    public static void main(String[] args) {
        Runtime runtime = Runtime.instance();

        ProfileImpl profile = new ProfileImpl();
        profile.setParameter(Profile.CONTAINER_NAME, "BazaarContainer");
        AgentContainer container = runtime.createAgentContainer(profile);

        try {

            AgentController merchantAgent1 = container.createNewAgent("m1", "projectAgents.MerchantAgent", new Object[]{0.1});
            merchantAgent1.start();

            AgentController merchantAgent2 = container.createNewAgent("m2", "projectAgents.MerchantAgent", new Object[]{0.5});
            merchantAgent2.start();

            AgentController merchantAgent3 = container.createNewAgent("m3", "projectAgents.MerchantAgent", new Object[]{0.9});
            merchantAgent3.start();

            Thread.sleep(1000);

            AgentController bazzarAgent = container.createNewAgent("baz", "projectAgents.BazaarAgent", null);
            bazzarAgent.start();

            // ensure proper termination
            java.lang.Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    System.out.println("Shutting down agents...");
                    container.kill();
                } catch (StaleProxyException e) {
                    e.printStackTrace();
                }
            }));
        } catch (StaleProxyException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}

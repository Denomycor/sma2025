# sma2025

Afonso Esteves 54394

Gonçalo Correia 56316

João Ascenso 56939



How to run a the game:

1. run the app:
java -classpath lib/jade.jar:src/ jade.Boot -gui

2. run the container:
java -classpath lib/jade.jar:src/ projectAgents.BazaarContainer


You can compile and run each individual MerchantAgent by adjusting the riskFactor (e.g., 0.5) passed as a double argument. 
Afterward, you can run the BazaarAgent.

Alternatively, you can run the container, which will automatically start three MerchantAgents with predefined riskFactors (0.1, 0.5, and 0.9) along with the BazaarAgent.
You also need to compile the BazaarContainer

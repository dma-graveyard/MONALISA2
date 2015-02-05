package dk.dma.epd;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import eu.monalisaproject.TacticalVoyagePlan;
import eu.monalisaproject.TacticalVoyagePlanEndpoint;
import eu.monalisaproject.TacticalVoyagePlanResponse;
import net.maritimecloud.core.id.MaritimeId;
import net.maritimecloud.net.EndpointInvocationFuture;
import net.maritimecloud.net.mms.MmsClient;
import net.maritimecloud.net.mms.MmsClientConfiguration;
import net.maritimecloud.util.geometry.PositionReader;
import net.maritimecloud.util.geometry.PositionTime;

public class Main {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        System.out.println("Hello Maritime World");
        MmsClientConfiguration conf = MmsClientConfiguration.create();

        MaritimeId id = MaritimeId.create("mmsi:123454321");
        conf.setId(id);

        conf.properties().setName("MobyDick");
        conf.properties().setOrganization("Danish Maritime Authority");
        conf.properties().setDescription("A fictiv vessel used in the Mona Lisa 2 project");

        conf.setPositionReader(new PositionReader() {
          public PositionTime getCurrentPosition() {
            return PositionTime.create(10, 20, System.currentTimeMillis());
          }
        });

        conf.setHost("mms03.maritimecloud.net");

        MmsClient client = conf.build();

        // Wait for a connection for 10 seconds
        if (client.connection().awaitConnected(10, TimeUnit.SECONDS)) {
          System.out.println("Succesfully connected to the MaritimeCloud test server");
        } else {
          System.out.println("Sorry, could not connect to the MaritimeCloud test server");
          System.exit(1);
        }
        
        // Now find the ship
        String mmsi = (args.length > 0) ? args[0] : "414000285";
        
        TacticalVoyagePlanEndpoint endpoint = client.endpointCreate(MaritimeId.create("mmsi:" + mmsi), TacticalVoyagePlanEndpoint.class);
        
        EndpointInvocationFuture<TacticalVoyagePlanResponse> response = endpoint.requestTacticalVoyagePlan(5 * 60); // 5 hours ahead

        
        String msg = response.get().getTextMessage();
        TacticalVoyagePlan plan = response.get().getPlan();
        
        System.out.println("Ship responded with '" + msg + "'");
        if (plan != null) {
            System.out.println("Route is called " + plan.getRouteName());
        }
        
        //Disconnect
        client.close();
        client.awaitTermination(10, TimeUnit.SECONDS);
    }
}

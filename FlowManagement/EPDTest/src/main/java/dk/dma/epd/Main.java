package dk.dma.epd;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import net.maritimecloud.core.id.MaritimeId;
import net.maritimecloud.net.EndpointInvocationFuture;
import net.maritimecloud.net.MessageHeader;
import net.maritimecloud.net.mms.MmsClient;
import net.maritimecloud.net.mms.MmsClientConfiguration;
import net.maritimecloud.util.Timestamp;
import net.maritimecloud.util.geometry.PositionReader;
import net.maritimecloud.util.geometry.PositionTime;
import eu.monalisaproject.AbstractFlowManagementResponseEndpoint;
import eu.monalisaproject.FlowManagementSuggestionEndpoint;
import eu.monalisaproject.FlowManagementSuggestionRequest;
import eu.monalisaproject.FlowManagementSuggestionResponse;
import eu.monalisaproject.Schedule;
import eu.monalisaproject.TacticalVoyagePlan;
import eu.monalisaproject.TacticalVoyagePlanEndpoint;
import eu.monalisaproject.TacticalVoyagePlanResponse;
import eu.monalisaproject.Waypoint;

public class Main {
    private static AtomicBoolean received = new AtomicBoolean(false);
    private static Thread mainThread;

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        mainThread = Thread.currentThread();
        System.out.println("Hello Maritime World");
        MmsClient client = connect2Cloud();

        if (client == null) {
            System.err.println("No cloud, all sunny today..");
            System.exit(1);
        }
        
        try {
            withMMSClient(client, args);
        }catch (InterruptedException e) {
        } finally {
            System.out.println("Closing the cloud connection");
            // Disconnect
            client.close();
            client.awaitTermination(10, TimeUnit.SECONDS);

        }
        
    }
            
    public static void withMMSClient(MmsClient client, String[] args) throws InterruptedException, ExecutionException {
     
        // Now find the ship
        String mmsi = (args.length > 0) ? args[0] : "414000285";
        int hours = (args.length > 1) ? Integer.parseInt(args[1]) : 10; // 10 hours ahead

        // Step 1 - Tactical Voyage Plan Exchange

        TacticalVoyagePlan plan = tacticalVoyagePlanExchange(client, mmsi, hours);
        printVoyagePlan(plan);

        if (plan != null) {

            // Step 2 - Flow Management Suggestion
            plan = modifyPlan(plan);

            registerForSuggestionResponse(client);

            // Suggest this new plan..
            flowManagementSuggestion(client, mmsi, plan);

            // Wait for response..
            wait20mins();

        }

    }

    private static MmsClient connect2Cloud() throws InterruptedException {
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
        }
        return client;
    }

    private static TacticalVoyagePlan modifyPlan(TacticalVoyagePlan plan) {

        List<Schedule> schedules = plan.getSchedules();
        if (schedules.isEmpty()) {
            System.err.println("ERROR: No schedule to modify...");
            return plan;
        }
        // Modify the last schedule segment by an hour and reduce the speed somewhat..
        Schedule lastSchedule = schedules.get(schedules.size() - 1);
        Timestamp eta = lastSchedule.getEta();

        Timestamp newEta = Timestamp.create(eta.getTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS));

        System.out.println(new Date(eta.getTime()) + " vs " + new Date(newEta.getTime()));

        lastSchedule.setEta(newEta);
        lastSchedule.setSpeed(lastSchedule.getSpeed() * .95);
        System.out.println("Reducing last segment to ETA " + new Date(newEta.getTime()));
        return plan;
    }

    private static void registerForSuggestionResponse(MmsClient client) throws InterruptedException {
        client.endpointRegister(new AbstractFlowManagementResponseEndpoint() {
            protected void postResponse(MessageHeader header, FlowManagementSuggestionResponse reply) {
                System.out.println("Received a reply from" + header.getSender());
                System.out.println("Regarding transaction " + reply.getRequestId());
                System.out.println("Response is " + reply.getStatus() + " - " + reply.getReplyText());
                
                received.set(true);
                mainThread.interrupt();
            }
        }).awaitRegistered(5, TimeUnit.SECONDS);
    }

    private static TacticalVoyagePlan tacticalVoyagePlanExchange(MmsClient client, String mmsi, int hours)
            throws InterruptedException, ExecutionException {

        TacticalVoyagePlanEndpoint endpoint = client.endpointCreate(MaritimeId.create("mmsi:" + mmsi),
                TacticalVoyagePlanEndpoint.class);

        EndpointInvocationFuture<TacticalVoyagePlanResponse> response = endpoint.requestTacticalVoyagePlan(hours * 60); // ? hours
                                                                                                                        // ahead

        String msg = response.get().getTextMessage();
        TacticalVoyagePlan plan = response.get().getPlan();

        System.out.println("Ship responded with '" + msg + "'");
        return plan;
    }

    private static void flowManagementSuggestion(MmsClient client, String mmsi, TacticalVoyagePlan plan) {

        FlowManagementSuggestionEndpoint endpoint = client.endpointCreate(MaritimeId.create("mmsi:" + mmsi),
                FlowManagementSuggestionEndpoint.class);
        FlowManagementSuggestionRequest request = new FlowManagementSuggestionRequest();
        request.setRequestId(19L);
        request.setSuggestedVoyagePlan(plan);
        request.setTextMessage("Please slow down on last leg");
        endpoint.submitSuggestion(request);

        System.out.println("Request submitted");
    }

    private static void wait20mins() throws InterruptedException {
        System.out.println("Hanging in here just to see if we get a response..");
        int turns = 20;
        while (turns > 0 && !received.get()) {
            Thread.sleep(60 * 1000); // one minute
            turns--;
            if (turns > 0) {
                System.out.println(turns + " minutes left before I give up..");
            }
        }

        System.out.println("I give up..");
    }

    private static void printVoyagePlan(TacticalVoyagePlan plan) {
        if (plan != null) {
            System.out.println("Route is called " + plan.getRouteName());
            List<Waypoint> waypoints = plan.getWaypoints();
            List<Schedule> schedules = plan.getSchedules();

            // All but one..
            for (int i = 0; i < waypoints.size() - 1; i++) {
                Waypoint waypoint = waypoints.get(i);
                Schedule schedule = schedules.get(i);

                // Sanity
                if (schedule.getWaypointFrom() != waypoint.getWaypointId()) {
                    System.err.println("ERROR::WAYPOINT #" + waypoint.getWaypointId() + " does not match schedule from #"
                            + schedule.getWaypointFrom());
                }

                System.out.println("Waypoint #" + waypoint.getWaypointId() + " at " + waypoint.getWaypointPosition());
                System.out.println(" -Departing " + new Date(schedule.getEtd().getTime()));
                System.out.println(" -Speed " + schedule.getSpeed());
                System.out.println(" - Arriving " + new Date(schedule.getEta().getTime()));
            }
            // Last point
            Waypoint waypoint = waypoints.get(waypoints.size() - 1);
            System.out.println("Waypoint #" + waypoint.getWaypointId() + " at " + waypoint.getWaypointPosition());
        }
    }
}

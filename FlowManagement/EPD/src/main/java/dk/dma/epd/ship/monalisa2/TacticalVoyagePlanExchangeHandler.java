package dk.dma.epd.ship.monalisa2;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.maritimecloud.core.id.MaritimeId;
import net.maritimecloud.net.MessageHeader;
import net.maritimecloud.net.mms.MmsClient;
import net.maritimecloud.util.Timestamp;
import net.maritimecloud.util.geometry.Position;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.openmap.MapHandlerChild;

import dk.dma.epd.common.Heading;
import dk.dma.epd.common.prototype.model.route.ActiveRoute;
import dk.dma.epd.common.prototype.model.route.RouteWaypoint;
import dk.dma.epd.common.prototype.service.MaritimeCloudService;
import dk.dma.epd.common.prototype.service.MaritimeCloudService.IMaritimeCloudListener;
import dk.dma.epd.common.prototype.status.CloudStatus;
import dk.dma.epd.ship.route.RouteManager;
import eu.monalisaproject.AbstractTacticalVoyagePlanEndpoint;
import eu.monalisaproject.GeometryType;
import eu.monalisaproject.Leg;
import eu.monalisaproject.Schedule;
import eu.monalisaproject.ScheduleType;
import eu.monalisaproject.TacticalVoyagePlan;
import eu.monalisaproject.TacticalVoyagePlanResponse;
import eu.monalisaproject.Waypoint;

// @See EnavServiceHandler -> ChantServiceHandler for initial inspiration..
// @See also the IntendedRouteHandler for getting the active route..
public class TacticalVoyagePlanExchangeHandler extends MapHandlerChild implements IMaritimeCloudListener {

    private static final Logger LOG = LoggerFactory.getLogger(TacticalVoyagePlanExchangeHandler.class);

    protected MaritimeCloudService maritimeCloudService;
    private RouteManager routeManager;

    // -- Generic Lifecycle methods ---------------------------------------------
    public void shutdown() {
    }

    // -- Maritime Cloud listener methods ----------------------------------------

    @Override
    public void cloudConnected(MmsClient connection) {
        // Register a cloud service
        try {
            getMmsClient().endpointRegister(new AbstractTacticalVoyagePlanEndpoint() {
                protected TacticalVoyagePlanResponse requestTacticalVoyagePlan(MessageHeader header, Integer timeWindow) {
                    return tvpResponse(header, timeWindow);
                }
            }).awaitRegistered(4, TimeUnit.SECONDS);
            LOG.info("Registered the Tactical Voyage Plan Endpoint (MONALISA 2.0) in the EPD ship");
        } catch (InterruptedException e) {
            LOG.error("Error hooking up services", e);
        }
    }

    @Override
    public void cloudDisconnected() {
        // Nothing to do here

    }

    @Override
    public void cloudError(String error) {
        // Nothing to do here

    }

    private TacticalVoyagePlanResponse tvpResponse(MessageHeader header, Integer timeWindow) {
        // TODO: Check who wants to know first...
        MaritimeId sender = header.getSender();
        LOG.info("Tactical Voyage Plan requested from " + sender);
        
        String responseText = "";
        TacticalVoyagePlan plan = getTVPFromActiveRoute(timeWindow);
        if (plan == null) {
            responseText = "No active plan";
        }
        
        // Translate into response message
        TacticalVoyagePlanResponse response = new TacticalVoyagePlanResponse();
        response.setTextMessage(responseText);
        response.setPlan(plan);
        
        return response;
    }

    // The EPD does not have a dynamic voyage plan, so we deduce it from the ActiveRoute
    private TacticalVoyagePlan getTVPFromActiveRoute(Integer timeWindow) {
        ActiveRoute activeRoute = routeManager.getActiveRoute();
        if (activeRoute == null) {
            // No voyage plan
            return null;
        }
        Date startDate = activeRoute.getActiveWaypointEta();
        if (startDate == null) {
            // No active way point, no voyage plan
            return null;
        }
        
        Date endDate = new Date(startDate.getTime() + timeWindow * 1000L * 60L);
        
        List<Waypoint> tvpWaypoints = new ArrayList<>();
        List<Schedule> tvpSchedules = new ArrayList<>();

        List<Date> etas = activeRoute.getEtas();
        LinkedList<RouteWaypoint> waypoints = activeRoute.getWaypoints();
        int activeWaypointIndex = activeRoute.getActiveWaypointIndex();
        Schedule schedule = new Schedule();
        
        for (int i = activeWaypointIndex, index = 0 ; i < waypoints.size(); i++, index++) {
            RouteWaypoint currentWaypoint = waypoints.get(i);
            Date currentWaypointEta = etas.get(i);
            if (currentWaypointEta.after(endDate)) {
                // Ignore the rest..
                continue;
            }
            // Translate to monalisa waypoint
            Waypoint waypoint = new Waypoint();
            waypoint.setWaypointId(i);
            waypoint.setTurnRadius(currentWaypoint.getTurnRad());
            Position position = Position.create(currentWaypoint.getPos().getLatitude(), currentWaypoint.getPos().getLongitude());
            waypoint.setWaypointPosition(position);
            if (index > 0 && currentWaypoint.getInLeg() != null) {
                Leg inLeg = new Leg();
                inLeg.setXtdPort(currentWaypoint.getInLeg().getXtdPort());
                inLeg.setXtdStarboard(currentWaypoint.getInLeg().getXtdStarboard());
                inLeg.setGeometryType((currentWaypoint.getInLeg().getHeading() == Heading.GC) ? GeometryType.ORTHODROME : GeometryType.LOXODROME);
            }
            
            tvpWaypoints.add(waypoint);
            
            // Now set the schedule when not first...
            if (index > 0) {
                schedule.setWaypointTo(i);
                schedule.setEta(Timestamp.create(currentWaypointEta.getTime()));
                schedule.setScheduleType(ScheduleType.CALCULATED);
                Double speed = (currentWaypoint.getInLeg() != null) ? currentWaypoint.getInLeg().getSpeed() : 0;
                schedule.setSpeed(speed);
                tvpSchedules.add(schedule);
            }
            schedule = new Schedule();
            schedule.setWaypointFrom(i);
            schedule.setEtd(Timestamp.create(currentWaypointEta.getTime()));
        }
        
        // Create the voyage plan
        TacticalVoyagePlan tvp = new TacticalVoyagePlan();
        tvp.addAllWaypoints(tvpWaypoints);
        tvp.addAllSchedules(tvpSchedules);
        tvp.setRouteName(activeRoute.getName());

        return tvp;
    }

    // -- MapHandler context methods --------------------------------------------------
    /**
     * {@inheritDoc}
     */
    @Override
    public void findAndInit(Object obj) {
        super.findAndInit(obj);

        if (obj instanceof MaritimeCloudService) {
            maritimeCloudService = (MaritimeCloudService) obj;
            maritimeCloudService.addListener(this);
            if (maritimeCloudService.isConnected()) {
                cloudConnected(maritimeCloudService.getConnection());
            }
        }

        if (obj instanceof RouteManager) {
            routeManager = (RouteManager) obj;
            // NBN 2301 Should be no reason to listen to changes..
            // routeManager.addListener(this);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void findAndUndo(Object obj) {

        if (obj instanceof MaritimeCloudService) {
            maritimeCloudService.removeListener(this);
            maritimeCloudService = null;
        }

        if (obj instanceof RouteManager) {
            // NBN 2301 Should be no reason to listen to changes..
            // routeManager.removeListener(this);
            routeManager = null;
        }

        super.findAndUndo(obj);
    }

    // -- Service methods ------------------------------------------------------------

    /**
     * Returns a reference to the {@linkplain MaritimeCloudService}
     * 
     * @return a reference to the {@linkplain MaritimeCloudService}
     */
    public synchronized MaritimeCloudService getMaritimeCloudService() {
        return maritimeCloudService;
    }

    /**
     * Returns a reference to the cloud client connection
     * 
     * @return a reference to the cloud client connection
     */
    public synchronized MmsClient getMmsClient() {
        return (maritimeCloudService == null) ? null : maritimeCloudService.getConnection();
    }

    /**
     * Returns a reference to the cloud status
     * 
     * @return a reference to the cloud status
     */
    public synchronized CloudStatus getStatus() {
        return (maritimeCloudService == null) ? null : maritimeCloudService.getStatus();
    }

    /**
     * Returns if there is a live connection to the Maritime Cloud
     * 
     * @return if there is a live connection to the Maritime Cloud
     */
    public synchronized boolean isConnected() {
        // Consider using the isClosed()/isConnected methods of the connection
        return maritimeCloudService != null && maritimeCloudService.isConnected();
    }

}

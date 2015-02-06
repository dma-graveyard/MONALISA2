package dk.dma.epd.ship.monalisa2;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.maritimecloud.core.id.MaritimeId;
import net.maritimecloud.net.MessageHeader;
import net.maritimecloud.net.mms.MmsClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.openmap.MapHandlerChild;

import dk.dma.epd.common.prototype.EPD;
import dk.dma.epd.common.prototype.model.route.RouteSuggestionData;
import dk.dma.epd.common.prototype.service.MaritimeCloudService;
import dk.dma.epd.common.prototype.service.MaritimeCloudService.IMaritimeCloudListener;
import dk.dma.epd.common.prototype.service.MaritimeCloudUtils;
import dk.dma.epd.common.prototype.service.RouteSuggestionHandlerCommon;
import dk.dma.epd.common.prototype.status.CloudStatus;
import eu.monalisaproject.AbstractFlowManagementSuggestionEndpoint;
import eu.monalisaproject.FlowManagementResponseEndpoint;
import eu.monalisaproject.FlowManagementSuggestionRequest;
import eu.monalisaproject.FlowManagementSuggestionResponse;
import eu.monalisaproject.FlowManagementSuggestionStatus;

public class FlowManagementSuggestionHandler extends MapHandlerChild implements IMaritimeCloudListener {

    private static final Logger LOG = LoggerFactory.getLogger(FlowManagementSuggestionHandler.class);

    protected MaritimeCloudService maritimeCloudService;

    // -- Maritime Cloud listener methods ----------------------------------------

    @Override
    public void cloudConnected(MmsClient connection) {
        // Register a cloud service
        try {
            getMmsClient().endpointRegister(new AbstractFlowManagementSuggestionEndpoint() {
                protected void submitSuggestion(MessageHeader header, FlowManagementSuggestionRequest request) {
                    handleFlowRequest(header, request);
                }
            }).awaitRegistered(5, TimeUnit.SECONDS);
            LOG.info("Registered the Flow Management Suggestion Endpoint (MONALISA 2.0) in the EPD ship");
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

    // -- The real handler ------------------------------------------------------------
    protected void handleFlowRequest(MessageHeader header, FlowManagementSuggestionRequest request) {

        // // Who did it..
        MaritimeId sender = header.getSender();
        long mmsi = MaritimeCloudUtils.toMmsi(sender);
        Long requestId = request.getRequestId();

        LOG.info("Received a flow request #{} from sender {}", requestId, sender);

        // At first... Respond with a status=pending..
        respond(sender, requestId, FlowManagementSuggestionStatus.PENDING);

        // Convert to EPD suggestion type..
        RouteSuggestionData routeData = convertToRouteSuggestion(mmsi, request);

        if (routeData != null) {
            routeData.setAcknowleged(false);

            // NBN 06 FEB 2015: I know......
            // Extremely HACKY way of pushing my data into the EPD system..
            // But I cannot right now do significant changes to EPD, so I
            // needed to do this to establish a proof of concept...

            RouteSuggestionHandlerCommon routeSuggestionHandler = EPD.getInstance().getRouteSuggestionHandler();
            Map<Long, RouteSuggestionData> existingRouteSuggestions = routeSuggestionHandler.getRouteSuggestions();
            existingRouteSuggestions.put(requestId, routeData);
            routeSuggestionHandler.setRouteSuggestions(existingRouteSuggestions);
            routeSuggestionHandler.notifyRouteSuggestionListeners();
        }

    }

    private RouteSuggestionData convertToRouteSuggestion(long mmsi, FlowManagementSuggestionRequest request) {
        // TODO Auto-generated method stub
        // See dk.dma.epd.ship.service.RouteSuggestionHandler in the EPD for inspiration
        return null;
    }

    private void respond(MaritimeId maritimeId, Long requestId, FlowManagementSuggestionStatus status) {
        LOG.info("Responding to {} request #{} with status {}", maritimeId, requestId, status);
        FlowManagementResponseEndpoint responseEndpoint = getMmsClient().endpointCreate(maritimeId, FlowManagementResponseEndpoint.class);
        FlowManagementSuggestionResponse response = new FlowManagementSuggestionResponse();
        response.setRequestId(requestId);
        response.setStatus(status);
        response.setReplyText("Response");
        responseEndpoint.postResponse(response);
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

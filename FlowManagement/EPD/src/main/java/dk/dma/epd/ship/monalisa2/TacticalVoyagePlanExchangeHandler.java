package dk.dma.epd.ship.monalisa2;

import net.maritimecloud.net.mms.MmsClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bbn.openmap.MapHandlerChild;

import dk.dma.epd.common.prototype.service.MaritimeCloudService;
import dk.dma.epd.common.prototype.service.MaritimeCloudService.IMaritimeCloudListener;
import dk.dma.epd.common.prototype.status.CloudStatus;
import dk.dma.epd.ship.route.RouteManager;


// @See EnavServiceHandler -> ChantServiceHandler for initial inspiration..
// @See also the IntendedRouteHandler for getting the active route..
public class TacticalVoyagePlanExchangeHandler extends MapHandlerChild
		implements IMaritimeCloudListener {
	
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
        	Thread.sleep(500); // Until I have the endpoint in there..
//            getMmsClient().endpointRegister(new AbstractMaritimeTextingService() {
//                @Override
//                protected void sendMessage(MessageHeader header, MaritimeText msg) {
//                    receiveChatMessage(header.getSender(), msg, header.getSenderTime());
//                }
//            }).awaitRegistered(4, TimeUnit.SECONDS);

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
	
	/*
	private void respond() {
	    // Check who wants to know first... 
		ActiveRoute activeRoute = routeManager.getActiveRoute();
		// Translate into response message
		// Return response..
	}
	*/
	
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
            //routeManager.addListener(this);
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

package net.floodlightcontroller.displaylldp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManager;
import net.floodlightcontroller.linkdiscovery.internal.Qos;
import net.floodlightcontroller.restserver.IRestApiService;

public class DisplayLLDP implements IFloodlightModule,DisplayLLDPService, IOFMessageListener {
	
	protected IFloodlightProviderService floodlightProvider;
	protected HashMap<String ,Qos> qosmap;
	protected IRestApiService restApi;
	protected static Logger logger;

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "DisplayLLDP";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg,
			FloodlightContext cntx) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(DisplayLLDPService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
	    m.put(DisplayLLDPService.class, this);
	    return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
	    l.add(IFloodlightProviderService.class);
	    l.add(IRestApiService.class);
	    return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		restApi = context.getServiceImpl(IRestApiService.class);
		logger = LoggerFactory.getLogger(DisplayLLDP.class);
		qosmap = new HashMap<String,Qos>();
		qosmap = (HashMap<String, Qos>) LinkDiscoveryManager.qosdatabase;
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
//		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		restApi.addRestletRoutable(new DisplayLLDPWebRoutable());
	}

	@Override
	public HashMap<String, Qos> getqos() {
		// TODO Auto-generated method stub
		return qosmap;
	}

}

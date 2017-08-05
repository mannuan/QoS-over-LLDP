package net.floodlightcontroller.displaylldp;

import java.util.HashMap;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.linkdiscovery.internal.Qos;

public interface DisplayLLDPService extends IFloodlightService {
	public HashMap<String,Qos> getqos();
}

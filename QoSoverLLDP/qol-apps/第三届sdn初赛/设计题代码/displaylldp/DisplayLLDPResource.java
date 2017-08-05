package net.floodlightcontroller.displaylldp;

import java.util.Collection;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import net.floodlightcontroller.linkdiscovery.internal.Qos;

public class DisplayLLDPResource extends ServerResource {
	@Get("json")
    public String retrieve() {
        DisplayLLDPService lldp = (DisplayLLDPService)getContext().getAttributes().get(DisplayLLDPService.class.getCanonicalName());
        Collection<Qos> qosvalues = (Collection<Qos>) lldp.getqos().values();
		String strqosvalues = new String();
		for( Qos q : qosvalues){
			strqosvalues += q.toString();
		}
    	strqosvalues = "\n当前网络的各个接口qos的信息如下:\n"+strqosvalues;
        return strqosvalues;
    }
}

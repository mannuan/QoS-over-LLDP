package net.floodlightcontroller.linkdiscovery.internal;

public class Qos {
	protected String chassisid;
	protected int portid;
	protected int bandwidth;
	protected int delay;
	protected int jitter;
	protected int loss;
	
	@Override
	public String toString() {
		// TODO Auto-generated method stub
		String qos = "";
		qos += "{ 机箱的ID:"+chassisid+",";
		qos += "端口的ID:"+portid+",";
		qos += "带宽:"+bandwidth+" Mb,";
		qos += "延迟:"+delay+" s,";
		qos += "抖动:"+jitter+" s^2,";
		qos += "丢包率:"+loss+"% }\n";
		return qos;
	}
}

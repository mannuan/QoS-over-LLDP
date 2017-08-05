/**
 * Copyright (c) 2016 - 2017 SWUNIX Lab, swunix.com, Inc.
 *
 *  You may obtain a copy of the License at:
 *
 *       http://www.swunix.com
 *
 *       vlog默认的输出文件是/var/log/openvswitch/ovs-vswitchd.log
 *       在floodlight上面设置qostlv的时候要注意了,不要把qostlv的位置放在timestamp的后面，否则就会影响floodlight的功能
 *       tc查看命令：带宽 tc class show dev s1-eth1, 延迟、抖动、丢包率 tc qdisc show dev s1-eth1
 *       使用tc命令把qos设置为0，是无法设置的，所以在判断0的时候无需考虑太多
 *       带宽和丢包率这两个qos值是独立的，不受其他qos信息的影响,但是延迟和抖动是相互影响的，唯有设置了延迟才可以设置抖动，
 *       因此，在openvswitch设置jitter的时候应该先判断有无delay,否则不需要判断
 *       注意：linux 的tc 支持带宽设置的范围为:8bit~1Gbit;(而且只能8bit的设置)单位有:bit,Kbit,Mbit,Gbit
 *                   delay延迟的范围(0,274877906]us,mininet设置的单位默认是us,也就是延迟最大是247.9s，然后tc设置时间的时候只保留到小数点后一位
 *                   单位有:us,ms,s
 *                   抖动(jitter)的范围(0,274877906]        单位:us 注意:jitter是依赖于delay的,也就是说没有delay就没有jitter,有delay但是可以没有jitter
 *                   丢包率(delay)的范围[0,100]
 *       在floodlight中加入qostlv时千万不可以改变chassisid和portidtlv的位置,其他随便,否则qos over lldp项目无法实现,但是不会影响交换机的正常工作
 *       在mininet中设置0.000008Mbit(8bit)的时候，在终端可能显示0.00Mbit,但是确实设置进去了,如下:
 *       class htb 5:1 root leaf 10: prio 0 rate 8bit ceil 8bit burst 1b cburst 225b
 *       把实现qostlv的功能加在了net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManager.generateLLDPMessage方法里面
 *       只需要一行代码:lldp.getOptionalTLVList().add(qosTLV);(放这行代码要注意:不要把qostlv的位置放在timestamp的后面)
 *       另外由于要改lldp发送的时间间隔,所以要改net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManager中的LLDP_TO_ALL_INTERVAL参数,
 *       把protected final int LLDP_TO_ALL_INTERVAL = 15;把protected final修饰符改为public static修饰符
 *       还有要/wm/qosoverlldp/proactive/json立刻生效就要修改lldpClock的属性把protected final 改为public static
 *       把protected final long lldpClock = 0;把protected final修饰符改为public static修饰符
 *       把对LLDP进行QoS信息的填写的功能加在packet_out解码的函数里面，具体位置是ofp-util.c的文件的ofputil_decode_packet_out函数里面，大概在文件的第4190行
 *		 填写的位置如下所示：
 *
 *		 struct ofpbuf b = ofpbuf_const_initializer(oh, ntohs(oh->length));
 *
 *		 add_qos_to_lldp(&b);
 *
 *		 也就是放在ofpbuf下面，因为我们的主程序要调用它
 *
 *		 在进行floodlight编程的时候，一定要注意：千万不要乱导包，导入的外部包类名如果与原来的包冲突，就会影响整个floodlight的正常运行
 *		 为了使QoSoverLLDP模块生效，要在如下两个配置文件里面添加net.floodlightcontroller.qosoverlldp.internal.QoSoverLLDPManager
 *		 qol-fl/floodlight-v5.0/src/main/resources/META-INF/services/net.floodlightcontroller.core.module.IFloodlightModule
 *		 qol-fl/floodlight-v5.0/src/main/resources/floodlightdefault.properties
 *		 以上两个文件的添加方式是不同的，第一个配置只需要加在文件末尾即可
 *		 但是第二个配置文件，必须考虑清楚，应该如下：
 *		 net.floodlightcontroller.statistics.StatisticsCollector,\
 *       net.floodlightcontroller.qosoverlldp.internal.QoSoverLLDPManager
 *       也就是说上面要加的那句话应该放在net.floodlightcontroller这类模块的最后，并与上一个模块用逗号隔开
 */

package net.floodlightcontroller.qosoverlldp.internal;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class DeviceCurrentQoS {

	private long time;/**记录收到qos信息的时间，单位:ms*/
	private String visualtime;/**格式：年月日 时分秒*/
	private String switchid;/**存放交换机编号的值*/
	private long portid;/**存放端口的值*/
	private long bandwidth;/**存放带宽的值*/
	private long delay;/**存放时延的值*/
	private long jitter;/**存放抖动的值*/
	private double loss;/**存放丢包率的值*/
	private long latency;/*存放交互延迟的值,单位默认微秒*/

	protected final int ACCURACY = 3;//精确到小数点后几位
	//bandwidth unit constant related
	public static final String BW_bUNIT = "bit";
	protected final String BW_KUNIT = "Kbit";
	protected final String BW_MUNIT = "Mbit";
	protected final String BW_GUNIT = "Gbit";
	//delay or jitter unit constant related
	public static final String uUNIT = "us";
	protected final String mUNIT = "ms";
	protected final String sUNIT = "s";


	protected boolean setTime(){
		this.time = System.currentTimeMillis();
		this.visualtime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
		return true;
	}
	protected boolean setSwitchid(String switchid){
		this.switchid = switchid;
		return true;
	}
	protected boolean setPortid(long portid){
		this.portid = portid;
		return true;
	}
	protected boolean setBandwidth(long bandwidth){
		this.bandwidth = bandwidth;
		return true;
	}
	protected boolean setDelay(long delay){
		this.delay = delay;
		return true;
	}
	protected boolean setJitter(long jitter){
		this.jitter = jitter;
		return true;
	}
	protected boolean setLoss(double loss){
		this.loss = loss;
		return true;
	}
	protected boolean setLatency(long latency){
		this.latency = latency;
		return true;
	}

	public long getTime(){
		return this.time;
	}
	public String getVisualTime(){
		return this.visualtime;
	}
	public String getDeviceName(){
		return "s"+Long.valueOf(this.switchid.replace(":", ""),16)+"-eth"+this.portid;
	}
	public long getDigitalSwicthID(){
		return Long.valueOf(this.switchid.replaceAll(":",""),16);
	}
	public String getSwitchid(){
		return this.switchid;
	}

	public long getPortid(){
		return this.portid;
	}

	public long getDigitalBandwidth(){
		return this.bandwidth;
	}
	public long getDigitalDelay(){
		return this.delay;
	}
	public long getDigitalJitter(){
		return this.jitter;
	}
	public double getDigitalLoss(){
		return this.loss;
	}

	/**
	 * @param unit
	 * @param unitarr
	 * @param value
	 * @return
	 * qos可视化格式化
	 * */
	protected String VisualFormat(String value,String unit,String[] unitarr){
		double dvalue = Double.parseDouble(value.replaceAll(unit,""));
		for(int i=0;dvalue>=1000;){
			dvalue /= 1000;
			unit = unitarr[++i];
		}
		return new BigDecimal(dvalue).setScale(this.ACCURACY,BigDecimal.ROUND_HALF_UP).doubleValue()+unit;
	}
	
	public String getBandwidth(){
		return this.bandwidth+"bit";
	}

	public String getvisualBandwidth(){
		return VisualFormat(getBandwidth(),this.BW_bUNIT,new String[]{this.BW_bUNIT,this.BW_KUNIT,this.BW_MUNIT,this.BW_GUNIT});
	}

	public String getLatency(){
		return this.latency*1000+"us";//由于latency的单位默认是ms,所以要乘以1000
	}

	public String getvisualLatency(){
		return VisualFormat(getLatency(),this.uUNIT,new String[]{this.uUNIT,this.mUNIT,this.sUNIT});
	}

	public String getDelay(){
		return this.delay+"us";
	}

	public String getvisualDelay(){
		return VisualFormat(getDelay(),this.uUNIT,new String[]{this.uUNIT,this.mUNIT,this.sUNIT});
	}

	public String getJitter(){
		return this.jitter+"us";
	}

	public String getvisualJitter(){
		return VisualFormat(getJitter(),this.uUNIT,new String[]{this.uUNIT,this.mUNIT,this.sUNIT});
	}

	public String getLoss(){
		return loss+"%";
	}

	public HashMap<String,String> toMap(){
		HashMap<String, String> mapqos = new HashMap<String,String>();
		mapqos.put("switchid", this.switchid);
		mapqos.put("portid", this.portid+"");
		mapqos.put("bandwidth",getBandwidth());
		mapqos.put("delay", getDelay());
		mapqos.put("jitter", getJitter());
		mapqos.put("loss", getLoss());
		mapqos.put("latency",getLatency());
		return mapqos;
	}
	@Override
	public String toString() {
		// TODO Auto-generated method stub
		String qos = "";
		qos += "{\"switchid\":"+"\""+this.switchid+"\""+",";
		qos += "\"portid\":"+this.portid+",";
		qos += "\"bandwidth\":"+this.getvisualBandwidth();
		qos += "\"delay\":"+this.getvisualDelay();
		qos += "\"jitter\":"+this.getvisualJitter();
		qos += "\"loss\":"+this.getLoss()+"}";
		return qos;
	}
	
}
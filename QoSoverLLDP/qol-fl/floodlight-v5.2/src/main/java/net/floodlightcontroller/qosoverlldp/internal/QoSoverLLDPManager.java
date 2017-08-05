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

import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.core.*;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.packet.BSN;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.LLDP;
import net.floodlightcontroller.packet.LLDPTLV;
import net.floodlightcontroller.qosoverlldp.IQoSoverLLDPService;
import net.floodlightcontroller.qosoverlldp.web.QoSoverLLDPWebRoutable;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.midi.SysexMessage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class QoSoverLLDPManager implements IOFMessageListener, IOFSwitchListener,IQoSoverLLDPService,
		IFloodlightModule{
	protected static final Logger log = LoggerFactory.getLogger(QoSoverLLDPManager.class);
	protected final String MODULE_NAME = "QoSoverLLDP";
	protected IFloodlightProviderService floodlightProviderService;
	protected IOFSwitchService switchService;
	protected IRestApiService restApi;

	protected final int STANFORD_TLV_TYPE = 127;

	//设备当前服务质量数据库
	protected DeviceCurrentQoSDB devicecurrentqosdb;
	//设备历史服务质量里表数据库
	protected DeviceHistoryQoSListDB devicehistoryqoslistdb;
	//设备历史服务质量里表数据库的大小
	protected int DEVICE_HISTORY_QOS_LIST_DB_SIZE = 40;

	/**
	 * 关于QoS每个字段范围的规定:
	 * 带宽(bandwidth)的范围(8bit,1Gbit) mininet设置带宽默认的单位是:MB
	 * 当前设置的最大值只能为1Gbit,用32位表示,
	 * 为什么要分配这么多空间，表示带宽呢？
	 * 因为不知道带宽的单位究竟是什么，因为如:1024000bit就不能用16位表示
	 * 时延(delay)的范围(0,274877906]     单位:us
	 * 当前设置的最大值只能为274877906us
	 * 抖动(jitter)的范围(0,274877906]        单位:us 注意:jitter是依赖于delay的,也就是说没有delay就没有jitter,有delay但是可以没有jitter
	 * 丢包率(loss)的范围(0,100)          单位:%
	 * 顾名思义是个百分数,所以最大只能为100,只需要用7为表示,但是为了类型转化的方便,浪费一位也变得无所谓了
	 * 默认情况下，从控制器发出去的包
	 * 都把发出LLDP包里面的带宽、时延、抖动的初始值设置为65536，丢包率设置为256,表示的意思都是初始值
	 */
	//下面这里的每个字节不要轻易修改要不然会发生不可预期的错误
	protected List<Integer> type_subtype_bytelist = new ArrayList<Integer>(){{
		add(0xab);add(0xcd);add(0xef);add(0x66);/*前3个字节:类型字段，表示斯坦福TLV格式,最后1个:子类型字段,表示QoS的字段的类型标识*/
	}};
	protected List<Integer> bandwidth_bytelist = new ArrayList<Integer>(){{
		add(0x7f);add(0xff);add(0xff);add(0xff);add(0xff);add(0xff);add(0xff);add(0xff);//7fffffffffffffff是long类型可以取到的最大值，而带宽不可能取到
	}};/*8个字节存放long型带宽数据,lldp传输过来的带宽单位默认为bit,lldp默认发出去的带宽的值为0,也就是说带宽在没有设置或者超出范围，带宽显示的值都为0bit*/
	protected List<Integer> delay_bytelist = new ArrayList<Integer>(){{
		add(0x7f);add(0xff);add(0xff);add(0xff);add(0xff);add(0xff);add(0xff);add(0xff);//7fffffffffffffff是long类型可以取到的最大值，而时延不可能取到
	}};/*8个字节存放long型delay数据,lldp传输过来的时延单位默认为us*/
	protected List<Integer> jitter_bytelist = new ArrayList<Integer>(){{
		add(0x7f);add(0xff);add(0xff);add(0xff);add(0xff);add(0xff);add(0xff);add(0xff);//7fffffffffffffff是long类型可以取到的最大值，而抖动不可能取到
	}};/*8个字节存放long型jitter数据,lldp传输过来的抖动单位默认为us*/
	protected List<Integer> loss_bytelist = new ArrayList<Integer>(){{
		add(0x7f);add(0xff);add(0xff);add(0xff);add(0xff);add(0xff);add(0xff);add(0xff);//7fffffffffff这个值抖动也不可能取到
	}};/*8个字节，用于存放丢包率的值，loss设置的精度很高，可以到小数点后5位*/
	protected ArrayList<Integer> qostlv_bytelist = new ArrayList<Integer>();
	public static LLDPTLV qosTLV;//直接在LinkDiscoveryManager的generateLLDPMessage()中调用，所以要是public static
	//用于开启一个新的任务
	protected SingletonTask StochasticTask;//统计式的任务
	protected IThreadPoolService threadPoolService;

	public static final int Max_Device_Num = 1000;//主机的最大数量,deviceresource里面用到
	//下面三个分别是qos更新的三种方式
	public static final String PERIODICMODE = "periodic";//周期式
	public static final String PROACTIVEMODE = "proactive";//主动式
	public static final String STOCHASTICMODE = "stochastic";//统计式
	//下面两个用于控制qos更新处于哪种模式
	protected static boolean isPeriodicMode = true;
	protected static boolean isStochasticMode = false;

	protected final int STOCHASTIC_TIME = 300;//统计模式分析服务质量历史数据的时间间隔，单位:s
	protected int DEVICE_HISTORY_QOS_LIST_DISPLAY_SIZE = 10;//android端显示的历史数据列表的长度
	public static ArrayList<QoSoverLLDPAPI> QoSoverLLDPAPIList = new ArrayList<QoSoverLLDPAPI>();//qosoverlldp所有api列表

	//*********************
	// IQoSoverLLDPService
	//*********************

	@Override
	public DeviceCurrentQoSDB getDeviceCurrentQoSDB() {
		// TODO Auto-generated method stub
		return devicecurrentqosdb;
	}
	
	@Override
	public DeviceHistoryQoSListDB getDeviceHistoryQoSListDB() {
		// TODO Auto-generated method stub
		return devicehistoryqoslistdb;
	}

	public int getDeviceHistoryQoSListDBSize(){
		return DEVICE_HISTORY_QOS_LIST_DB_SIZE;
	}


	@Override
	public int getDeviceHistoryQoSListDispalySize(){
		return DEVICE_HISTORY_QOS_LIST_DISPLAY_SIZE;
	}

	@Override
	public boolean setDeviceHistoryQoSListDBSize(int dbsize){
		if(dbsize<DEVICE_HISTORY_QOS_LIST_DISPLAY_SIZE){
			return false;
		}else{
			DEVICE_HISTORY_QOS_LIST_DB_SIZE = dbsize;
			return true;
		}
	}

	@Override
	public boolean setDeviceHistoryQoSListDispalySize(int displaysize){
		if(displaysize>DEVICE_HISTORY_QOS_LIST_DB_SIZE){
			return false;
		}else if(displaysize<1){
			return false;
		} else{
			DEVICE_HISTORY_QOS_LIST_DISPLAY_SIZE = displaysize;
			return true;
		}
	}

	@Override
	public boolean setDeviceQoSUpdateMode(String mode){
		switch(mode){
			case PERIODICMODE:
				isPeriodicMode = true;
				isStochasticMode = false;
				return true;
			case STOCHASTICMODE:
				isPeriodicMode = false;
				isStochasticMode = true;
				return true;
			default:
				return false;
		}
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return MODULE_NAME;
	}

	//***********************************
	// Implements Methods - other Related
	//***********************************

	/**
	 * long 转换成 ip
	 * @param i
	 * @return
	 */
	@Override
	public String longToIp(long i) {
		return ((i >> 24) & 0xFF) +
				"." + ((i >> 16) & 0xFF) +
				"." + ((i >> 8) & 0xFF) +
				"." + (i & 0xFF);
	}

	//***********************************
	// Implements Methods - HTTP Related
	//***********************************

	/**
	 * @return
	 * @throws Exception
	 */
	public String doGet(String url) throws Exception {

		HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(url).openConnection();

		httpURLConnection.setRequestProperty("Accept-Charset", "utf-8");
		httpURLConnection.setRequestProperty("Content-Type", "application/json");
		httpURLConnection.setRequestProperty("Accept", "application/json");

		InputStream inputStream = null;
		InputStreamReader inputStreamReader = null;
		BufferedReader reader = null;
		StringBuffer resultBuffer = new StringBuffer();
		String tempLine = null;
		//响应失败
		if(httpURLConnection.getResponseCode() >= 300) {
			throw new Exception("HTTP Request is not success, Response code is " + httpURLConnection.getResponseCode());
		}
		try{
			inputStream = httpURLConnection.getInputStream();
			inputStreamReader = new InputStreamReader(inputStream);
			reader = new BufferedReader(inputStreamReader);
			while((tempLine = reader.readLine()) != null){
				resultBuffer.append(tempLine);
			}
		}finally {
			if(reader != null){
				reader.close();
			}
			if(inputStreamReader != null){
				inputStreamReader.close();
			}
			if(inputStream != null){
				inputStream.close();
			}
		}
		return resultBuffer.toString();
	}

	/**
	 * @param body
	 * @return
	 * @throws Exception
	 */
	public String doPost(String url,Object body) throws Exception {

		ObjectMapper mapper = new ObjectMapper();
		String json = mapper.writeValueAsString(body);

		HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(url).openConnection();

		httpURLConnection.setDoOutput(true);
		httpURLConnection.setRequestMethod("POST");
		httpURLConnection.setRequestProperty("Content-Length", String.valueOf(json.length()));
		httpURLConnection.setRequestProperty("Content-Type", "application/json");
		httpURLConnection.setRequestProperty("Accept", "application/json");

		OutputStream outputStream = null;
		OutputStreamWriter outputStreamWriter = null;
		InputStream inputStream = null;
		InputStreamReader inputStreamReader = null;
		BufferedReader reader = null;
		StringBuffer resultBuffer = new StringBuffer();
		String tempLine = null;

		try{
			outputStream = httpURLConnection.getOutputStream();
			outputStreamWriter = new OutputStreamWriter(outputStream);

			outputStreamWriter.write(json);
			outputStreamWriter.flush();
			//响应失败
			if(httpURLConnection.getResponseCode() >= 300){
				throw new Exception("HTTP Request is not success, Response code is " + httpURLConnection.getResponseCode());
			}
			//接收响应流
			inputStream = httpURLConnection.getInputStream();
			inputStreamReader = new InputStreamReader(inputStream);
			reader = new BufferedReader(inputStreamReader);
			while((tempLine = reader.readLine()) != null){
				resultBuffer.append(tempLine);
			}
		}finally{
			if(outputStreamWriter != null){
				outputStreamWriter.close();
			}
			if(outputStream != null){
				outputStream.close();
			}
			if(reader != null) {
				reader.close();
			}
			if(inputStreamReader != null) {
				inputStreamReader.close();
			}
			if(inputStream != null) {
				inputStream.close();
			}
		}
		return resultBuffer.toString();
	}

	/**
	 * @param body
	 * @return
	 * @throws Exception
	 */
	public String doDelete(String url,Object body) throws Exception {

		ObjectMapper mapper = new ObjectMapper();
		String json = mapper.writeValueAsString(body);

		HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(url).openConnection();

		httpURLConnection.setDoOutput(true);
		httpURLConnection.setRequestMethod("DELETE");
		httpURLConnection.setRequestProperty("Content-Length", String.valueOf(json.length()));
		httpURLConnection.setRequestProperty("Content-Type", "application/json");
		httpURLConnection.setRequestProperty("Accept", "application/json");

		OutputStream outputStream = null;
		OutputStreamWriter outputStreamWriter = null;
		InputStream inputStream = null;
		InputStreamReader inputStreamReader = null;
		BufferedReader reader = null;
		StringBuffer resultBuffer = new StringBuffer();
		String tempLine = null;

		try{
			outputStream = httpURLConnection.getOutputStream();
			outputStreamWriter = new OutputStreamWriter(outputStream);

			outputStreamWriter.write(json);
			outputStreamWriter.flush();
			//响应失败
			if(httpURLConnection.getResponseCode() >= 300){
				throw new Exception("HTTP Request is not success, Response code is " + httpURLConnection.getResponseCode());
			}
			//接收响应流
			inputStream = httpURLConnection.getInputStream();
			inputStreamReader = new InputStreamReader(inputStream);
			reader = new BufferedReader(inputStreamReader);
			while((tempLine = reader.readLine()) != null){
				resultBuffer.append(tempLine);
			}
		}finally{
			if(outputStreamWriter != null){
				outputStreamWriter.close();
			}
			if(outputStream != null){
				outputStream.close();
			}
			if(reader != null) {
				reader.close();
			}
			if(inputStreamReader != null) {
				inputStreamReader.close();
			}
			if(inputStream != null) {
				inputStream.close();
			}
		}
		return resultBuffer.toString();
	}

	//*********************
	//   OFMessage Listener
	//*********************
    
	@Override
	public Command receive(IOFSwitch sw, OFMessage msg,
                           FloodlightContext cntx) {
		// TODO Auto-generated method stub
		switch (msg.getType()) {
			case PACKET_IN:
				 /* Retrieve the deserialized packet in message */
				Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,
						IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
				if (eth.getPayload() instanceof BSN) {
					BSN bsn = (BSN) eth.getPayload();
					if (bsn != null && bsn.getPayload() != null && bsn.getPayload() instanceof LLDP != false){
						FromLLDPGetQoS((LLDP) bsn.getPayload(), sw.getId());
					}
				}else if (eth.getPayload() instanceof LLDP){
					FromLLDPGetQoS((LLDP) eth.getPayload(), sw.getId());
				}
				break;
			default:
				break;
		}
		return Command.CONTINUE;
	}

		@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {//name模块在这个模块之后
		// TODO Auto-generated method stub
		return false;
	}
	
	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {//name模块在这个模块之后
		// TODO Auto-generated method stub qosoverlldp必须允许在linkdiscovery之前，应为linkdiscovery会把处理过后的lldp消息直接command.stop掉
		return "linkdiscovery".equals(name);//linkdiscovery在qosoverlldp之后,注意这句话超级重要否则接收不到qos信息
	}

	//***********************************
	//  Internal Methods - Generate QoSTLV Processing Related
	//***********************************
	protected LLDPTLV qosTLV(){
		byte[] qostlv_bytearray = new byte[qostlv_bytelist.size()];
		for(int i=0;i<qostlv_bytelist.size();i++) qostlv_bytearray[i] = qostlv_bytelist.get(i).byteValue();
		LLDPTLV qostlv = new LLDPTLV().setType((byte) STANFORD_TLV_TYPE)
				.setLength((short) qostlv_bytearray.length)
				.setValue(qostlv_bytearray);
		return qostlv;
	}
	
	//***********************************
	//  Internal Methods - From LLDP get QoS Processing Related
	//***********************************
	
    /**
     *@param arr
	 * @return long
     */
	protected long byteArrayToLong(byte[] arr) {
		long result = (((long) arr[0] & 0xff) << ((arr.length-1)*8));
		for(int i=1;i<arr.length;i++){
			result |= (((long) arr[i] & 0xff) << ((arr.length-i-1)*8));
		}
		return result;
	}

	/**
	 *@param arr
	 * @return double
	 */
	protected double byteArrayToDouble(byte[] arr) {
		return Double.longBitsToDouble(byteArrayToLong(arr));
	}

	protected String getSwitchID(byte[] chassisid){
		String strchassisid = new String();
		String front,behind;
		byte num = 15;
		String[ ] arr=new String[]{"0","1","2","3","4","5","6","7","8","9","a","b","c","d","e","f"};
		for(int i = 1;i<chassisid.length-1;i++){//注意：这里从1开始的原因是:chassisid的前面1个字节描述的是tlv的类型和tlv的长度,并且chassisid的字节数组的长度为7
			behind = arr[chassisid[i]&num];//一个字节后4位(从左到右)如:0xab
			front = arr[(chassisid[i]>>4&num)];//一个字节的前4位(从左到右)
			strchassisid += front+behind+":";
		}
		behind = arr[chassisid[chassisid.length-1]&num];
		front = arr[(chassisid[chassisid.length-1]>>4&num)];
		strchassisid += front+behind;
		String switchid = "00:00:"+strchassisid;//这里加上00:00:是因为在floodlight中switchid等同于chassisid，但是switchid比chasssisid多了00:00:
		return switchid;
	}

	protected void saveQoS(DeviceCurrentQoS qos){
		//服务质量数据库添加数据
		devicecurrentqosdb.updateDeviceQoS(qos.getDeviceName(), qos);
		//往服务质量历史列表数据库添加数据
		String devicename = qos.getDeviceName();
		devicehistoryqoslistdb.updateDeviceHistoryQoSList(devicename,
				DeviceHistoryQoSListDB.BANDWIDTH,qos.getBandwidth(),DEVICE_HISTORY_QOS_LIST_DB_SIZE);
		devicehistoryqoslistdb.updateDeviceHistoryQoSList(devicename,
				DeviceHistoryQoSListDB.DELAY,qos.getDelay(),DEVICE_HISTORY_QOS_LIST_DB_SIZE);
		devicehistoryqoslistdb.updateDeviceHistoryQoSList(devicename,
				DeviceHistoryQoSListDB.JITTER,qos.getJitter(),DEVICE_HISTORY_QOS_LIST_DB_SIZE);
		devicehistoryqoslistdb.updateDeviceHistoryQoSList(devicename,
				DeviceHistoryQoSListDB.LOSS,qos.getLoss(),DEVICE_HISTORY_QOS_LIST_DB_SIZE);
		devicehistoryqoslistdb.updateDeviceHistoryQoSList(devicename,
				DeviceHistoryQoSListDB.LATENCY,qos.getLatency(),DEVICE_HISTORY_QOS_LIST_DB_SIZE);
		devicehistoryqoslistdb.updateDeviceHistoryQoSList(devicename,
				DeviceHistoryQoSListDB.TIME,qos.getTime(),DEVICE_HISTORY_QOS_LIST_DB_SIZE);
		devicehistoryqoslistdb.updateDeviceHistoryQoSList(devicename,
				DeviceHistoryQoSListDB.VISUALTIME,qos.getVisualTime(),DEVICE_HISTORY_QOS_LIST_DB_SIZE);
		devicehistoryqoslistdb.updateDeviceHistoryQoSList(devicename,
				DeviceHistoryQoSListDB.VISUALBANDWIDTH,qos.getvisualBandwidth(),DEVICE_HISTORY_QOS_LIST_DB_SIZE);
		devicehistoryqoslistdb.updateDeviceHistoryQoSList(devicename,
				DeviceHistoryQoSListDB.VISUALDELAY,qos.getvisualDelay(),DEVICE_HISTORY_QOS_LIST_DB_SIZE);
		devicehistoryqoslistdb.updateDeviceHistoryQoSList(devicename,
				DeviceHistoryQoSListDB.VISUALJITTER,qos.getvisualJitter(),DEVICE_HISTORY_QOS_LIST_DB_SIZE);
		devicehistoryqoslistdb.updateDeviceHistoryQoSList(devicename,
				DeviceHistoryQoSListDB.VISUALLATENCY,qos.getvisualLatency(),DEVICE_HISTORY_QOS_LIST_DB_SIZE);
	}

	protected void FromLLDPGetQoS(LLDP lldp, DatapathId sw){
		IOFSwitch iofSwitch = switchService.getSwitch(sw);
		DeviceCurrentQoS qos = new DeviceCurrentQoS();
		qos.setTime();
		//取出机箱的ID以String的形式存放
		byte[] chassisid = lldp.getChassisId().getValue();
		qos.setSwitchid(getSwitchID(chassisid));
//		log.info("机箱的ID:"+qos.chassisid);
		//取出对应机箱的端口的ID
		qos.setPortid(byteArrayToLong(new byte[]{lldp.getPortId().getValue()[1],lldp.getPortId().getValue()[2]}));//不选0是因为0记录的是类型和长度
//		log.info("端口的ID:"+qos.portid);
		for (LLDPTLV lldptlv : lldp.getOptionalTLVList()) {
			if(lldptlv.getLength() == qostlv_bytelist.size()&&
			    lldptlv.getType() == STANFORD_TLV_TYPE&&
				lldptlv.getValue()[0] == qostlv_bytelist.get(0).byteValue()&&
				lldptlv.getValue()[1] == qostlv_bytelist.get(1).byteValue()&&
				lldptlv.getValue()[2] == qostlv_bytelist.get(2).byteValue()&&
				lldptlv.getValue()[3] == qostlv_bytelist.get(3).byteValue()){
				int p = type_subtype_bytelist.size();
				byte[] bandwidth = new byte[bandwidth_bytelist.size()];
				for(int i=0;i<bandwidth_bytelist.size();i++,p++){
					bandwidth[i] = lldptlv.getValue()[p];
				}
				qos.setBandwidth(byteArrayToLong(bandwidth));
				byte[] delay = new byte[delay_bytelist.size()];
				for(int i=0;i<delay_bytelist.size();i++,p++){
					delay[i] = lldptlv.getValue()[p];
				}
				qos.setDelay(byteArrayToLong(delay));
				byte[] jitter = new byte[jitter_bytelist.size()];
				for(int i=0;i<jitter_bytelist.size();i++,p++){
					jitter[i] = lldptlv.getValue()[p];
				}
				qos.setJitter(byteArrayToLong(jitter));
				byte[] loss = new byte[loss_bytelist.size()];
				for(int i=0;i<loss_bytelist.size();i++,p++){
					loss[i] = lldptlv.getValue()[p];
				}
				qos.setLoss(byteArrayToDouble(loss));
			}else if (lldptlv.getType() == 127 && lldptlv.getLength() == 12
					&& lldptlv.getValue()[0] == 0x00
					&& lldptlv.getValue()[1] == 0x26
					&& lldptlv.getValue()[2] == (byte) 0xe1
					&& lldptlv.getValue()[3] == 0x01) { /* 0x01 for timestamp */
				ByteBuffer tsBB = ByteBuffer.wrap(lldptlv.getValue()); /* skip OpenFlow OUI (4 bytes above) */
				long swLatency = iofSwitch.getLatency().getValue();
				long timestamp = tsBB.getLong(4); /* include the RX switch latency to "subtract" it */
				timestamp = timestamp + swLatency;
				// Store the time of update to this link, and push it out to
				// routingEngine
				long time = System.currentTimeMillis();//单位ms
				long latency = time - timestamp;
				qos.setLatency(latency);/*服务器与交换机的交互延迟*/
			}
		}
		//qos信息初始化,防止不运行qosoverlldp这套机制出现的错误
		if(qos.getDigitalBandwidth() < Long.MAX_VALUE && qos.getDigitalDelay() < Long.MAX_VALUE &&
				qos.getDigitalJitter() < Long.MAX_VALUE && qos.getDigitalLoss() != Double.NaN){//如果带宽不为0，就把qos信息加入到数据库里面(使用带宽来判断的原因是带宽一定不会为0)
			saveQoS(qos);
		}

	}

    // ***************
    // Getters/Setters
    // ***************

    public IOFSwitchService getSwitchService() {
        return this.switchService;
    }
	
    public void setSwitchService(IOFSwitchService switchService) {
        this.switchService = switchService;
     }
    
	//***************
	// IOFSwitchListener
	//***************

	@Override
	public void switchAdded(DatapathId switchId) {
		// TODO Auto-generated method stub
			
	}
		
	@Override
	public void switchRemoved(DatapathId switchId) {
		// TODO Auto-generated method stub
			
	}
		
	@Override
	public void switchActivated(DatapathId switchId) {
		// TODO Auto-generated method stub
			
	}
	
	/**
	 * 这个函数的目的就是在删除设备的时候，可以同时把qosdatabase里面的数据删除掉，
	 * 否则当floodlight没有重启，再次创建网络的时候就会出现数据的重叠错误
	 */
	@Override
	public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {
		// TODO Auto-generated method stub
		switch (type) {
		case UP:
			break;
		case DELETE: case DOWN:
			/**
			 * 这里的switchid由16个16进制组成,switchid即chassisid
			 * 但是，实际在lldp里面chassisid是由12个16进制组成,
			 * 因此,switchid要转化成chassisid要把前面的00:00:去掉
			 * port.getName()形如s8-eth4
			 */
			String portName = String.format("%s", port.getName());
 			devicecurrentqosdb.remove(portName);
 			devicehistoryqoslistdb.remove(portName);
			break;
		case OTHER_UPDATE: case ADD:
			// This is something other than port add or delete.
			// Topology does not worry about this.
			// If for some reason the port features change, which
			// we may have to react.
			break;
		}
	}
		
	@Override
	public void switchChanged(DatapathId switchId) {
		// TODO Auto-generated method stub
			
	}

	@Override
	public void switchDeactivated(DatapathId switchId) {

	}

	//***************
	// IFloodlightModule
	//***************
	
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IQoSoverLLDPService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<>();
	    m.put(IQoSoverLLDPService.class, this);
	    return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<>();
	    l.add(IFloodlightProviderService.class);
		l.add(IThreadPoolService.class);
	    l.add(IRestApiService.class);
	    return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		threadPoolService = context.getServiceImpl(IThreadPoolService.class);
		restApi = context.getServiceImpl(IRestApiService.class);
		//init qosdatabase
		devicecurrentqosdb = new DeviceCurrentQoSDB();
		//初始化服务质量历史列表数据库
		devicehistoryqoslistdb = new DeviceHistoryQoSListDB();
		//init qostlv
		qostlv_bytelist.addAll(type_subtype_bytelist);
		qostlv_bytelist.addAll(bandwidth_bytelist);
		qostlv_bytelist.addAll(delay_bytelist);
		qostlv_bytelist.addAll(jitter_bytelist);
		qostlv_bytelist.addAll(loss_bytelist);
		qosTLV = qosTLV();
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		// Initialize role to floodlight provider role.
		floodlightProviderService.addOFMessageListener(OFType.PACKET_IN, this);
		restApi.addRestletRoutable(new QoSoverLLDPWebRoutable());
		// Register for switch updates
		switchService.addOFSwitchListener(this);
		ScheduledExecutorService ses = threadPoolService.getScheduledExecutor();
		StochasticTask = new SingletonTask(ses, new Runnable(){

			@Override
			public void run() {
				try{

				}catch (Exception e) {
					log.error("Exception in qosoverlldp timer.", e);
				}finally{
					StochasticTask.reschedule(STOCHASTIC_TIME, TimeUnit.SECONDS);
				}
			}
		});
		StochasticTask.reschedule(STOCHASTIC_TIME, TimeUnit.SECONDS);

	}

}

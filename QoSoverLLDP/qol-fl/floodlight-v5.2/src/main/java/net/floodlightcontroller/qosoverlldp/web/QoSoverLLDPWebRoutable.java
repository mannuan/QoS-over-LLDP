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
 *		 为了使QoSoverLLDP模块生效，要在如下两个配置文件里面添加net.floodlightcontroller.qosoverlldp.internal.QoSoverLLDPManager
 *		 qol-fl/floodlight-v5.0/src/main/resources/META-INF/services/net.floodlightcontroller.core.module.IFloodlightModule
 *		 qol-fl/floodlight-v5.0/src/main/resources/floodlightdefault.properties
 *		 以上两个文件的添加方式是不同的，第一个配置只需要加在文件末尾即可
 *		 但是第二个配置文件，必须考虑清楚，应该如下：
 *		 net.floodlightcontroller.statistics.StatisticsCollector,\
 *       net.floodlightcontroller.qosoverlldp.internal.QoSoverLLDPManager
 *       也就是说上面要加的那句话应该放在net.floodlightcontroller这类模块的最后，并与上一个模块用逗号隔开
 */

package net.floodlightcontroller.qosoverlldp.web;

import net.floodlightcontroller.qosoverlldp.internal.QoSoverLLDPAPI;
import net.floodlightcontroller.restserver.RestletRoutable;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;
import static net.floodlightcontroller.qosoverlldp.internal.QoSoverLLDPManager.QoSoverLLDPAPIList;

import java.util.ArrayList;

public class QoSoverLLDPWebRoutable implements RestletRoutable {

	@Override
	public Restlet getRestlet(Context context) {
		// TODO Auto-generated method stub
		QoSoverLLDPAPI api_1 = new QoSoverLLDPAPI(DeviceCurrentQoSDBResource.class,"devicecurrentqosdb","json");
		api_1.setDescription(1,"显示所有设备实时的QoS信息。");
		QoSoverLLDPAPIList.add(api_1);

		QoSoverLLDPAPI api_2 = new QoSoverLLDPAPI(DeviceHistoryQoSListDBResource.class,"devicehistoryqoslistdb","json");
		api_2.setDescription(2,"显示所有设备的历史QoS信息。");
		QoSoverLLDPAPIList.add(api_2);

		QoSoverLLDPAPI api_3 = new QoSoverLLDPAPI(DeviceCurrentQoSUpdateModeSelectResource.class,"qos/all","json");
		api_3.setDescription(3,"根据QoS的更新模式，返回QoS信息，目前有三个参数：periodic（周期式）、proactive（主动式）、stochastic（统计式）。");
		api_3.setParameter("{qos-update-mode}",new ArrayList<String>(){{add("periodic");add("proactive");add("stochastic");}});
		QoSoverLLDPAPIList.add(api_3);

		QoSoverLLDPAPI api_4 = new QoSoverLLDPAPI(ShortestRouteResource.class,"shortestroute","json");
		api_4.setDescription(4,"输入源主机与目的主机（可以输入交换机但必须是s加一个整数），返回最短路径，输入格式：'h'加整数或s加整数。");
		api_4.setParameter("{src-host}","{dst-host}");
		QoSoverLLDPAPIList.add(api_4);

		QoSoverLLDPAPI api_5 = new QoSoverLLDPAPI(AllShortestRoutesResource.class,"shortestroute/all","json");
		api_5.setDescription(5,"返回主机之间所有可能的最短路径。");
		QoSoverLLDPAPIList.add(api_5);

		QoSoverLLDPAPI api_6 = new QoSoverLLDPAPI(GenerateShortestRouteTimeResource.class,"shortestroutewithtime","json");
		api_6.setDescription(6,"返回一条最短路径，末尾有迪杰斯特拉算法与全路径算法寻找最短路径的时间（可以输入交换机但必须是s加一个整数），输入格式：'h'加整数或‘s’加整数。");
		api_6.setParameter("{src-host}","{dst-host}");
		QoSoverLLDPAPIList.add(api_6);

		ArrayList<String> optimal_type = new ArrayList<String>(){{
			add("bandwidth");add("delay");add("jitter");add("loss");add("latency");add("total");
		}};
		QoSoverLLDPAPI api_7 = new QoSoverLLDPAPI(OptimalRouteResource.class,"optimalroute","json");
		api_7.setDescription(7,"输入优化的模式、源主机和目的主机，返回一条最优路径。优化模式目前有：bandwidth、delay、jitter、loss、latency、total，输入格式：'h'加整数或's'加整数。");
		api_7.setParameter("{optimal-type}",optimal_type,"{src-host}","{dst-host}");
		QoSoverLLDPAPIList.add(api_7);

		QoSoverLLDPAPI api_8 = new QoSoverLLDPAPI(OptimalRouteWithQoSResource.class,"optimalroutewithqos","json");
		api_8.setDescription(8,"输入优化的模式、源主机和目的主机，返回一条最优路径并附加每个端口的QoS信息。优化模式目前有：bandwidth、delay、jitter、loss、latency、total，输入格式：'h'加整数或's'加整数。");
		api_8.setParameter("{optimal-type}",optimal_type,"{src-host}","{dst-host}");
		QoSoverLLDPAPIList.add(api_8);

		QoSoverLLDPAPI api_9 = new QoSoverLLDPAPI(OptimalRouteWithAggregateValueResource.class,"optimalroutewithaggregatevalue","json");
		api_9.setDescription(9,"输入优化的模式、源主机和目的主机，返回一条最优路径并附加整条路的QoS聚合值的集合。优化模式目前有：bandwidth、delay、jitter、loss、latency、total，输入格式：'h'加整数或's'加整数。");
		api_9.setParameter("{optimal-type}",optimal_type,"{src-host}","{dst-host}");
		QoSoverLLDPAPIList.add(api_9);

		QoSoverLLDPAPI api_10 = new QoSoverLLDPAPI(LLDPToAllIntervalResource.class,"lldptoallinterval","json");
		api_10.setDescription(10,"修改链路发现的更新时间，输入的格式：不小于1的整数，单位：s。");
		api_10.setParameter("{lldp-to-all-interval}");
		QoSoverLLDPAPIList.add(api_10);

		QoSoverLLDPAPI api_11 = new QoSoverLLDPAPI(DeviceHistoryQoSListDBSizeResource.class,"devicehistoryqoslistdbsize","json");
		api_11.setDescription(11,"修改历史数据库可以存储QoS信息的数量，默认是10，输入格式：不小于1整数。");
		api_11.setParameter("{device-history-qos-list-db-size}");
		QoSoverLLDPAPIList.add(api_11);

		QoSoverLLDPAPI api_12 = new QoSoverLLDPAPI(StaticEntryPusherResource.class,"staticentrypusher","json");
		api_12.setDescription(12,"根据优化的模式选择的最优路径推送静态流表，以控制数据的流向，使用这个api不需要担心流表覆盖问题，因为每次添加，都会自动移除冲突的流表。");
		api_12.setParameter("{optimal-type}",optimal_type,"{src-host}","{dst-host}");
		QoSoverLLDPAPIList.add(api_12);

		QoSoverLLDPAPI api_13 = new QoSoverLLDPAPI(DevicesResource.class,"device/all","json");
		api_13.setDescription(13,"返回网络中所有主机的信息，重写了/wm/device/和/wm/device/all/json，过滤了无效主机，返回所有主机的信息。");
		QoSoverLLDPAPIList.add(api_13);

		QoSoverLLDPAPI api_14 = new QoSoverLLDPAPI(DeviceHistoryQoSListDisplaySizeResource.class,"devicehistoryqoslistdisplaysize","json");
		api_14.setDescription(14,"用来修改显示的历史服务质量信息的数量，输入格式：不小于1的整数，并且必须小于服务质量历史数据库的大小");
		api_14.setParameter("{device-history-qos-list-display-size}");
		QoSoverLLDPAPIList.add(api_14);

		QoSoverLLDPAPI api_15 = new QoSoverLLDPAPI(SwitchPortQoSListforAndroidResource.class,"android/switchportqoslist","json");
		api_15.setDescription(15,"安卓端专用，用来获取每个交换机的服务质量信息，输入格式：从0开始的整数，交换机1相当于整数0。");
		api_15.setParameter("{switchid}");
		QoSoverLLDPAPIList.add(api_15);

		QoSoverLLDPAPI api_16 = new QoSoverLLDPAPI(SwitchQoSVarietyforAndroidResource.class,"android/switchqosvariety","json");
		api_16.setDescription(16,"安卓端专用，用来获取交换机的历史服务质量信息，输入格式：从0开始的整数，交换机1相当于整数0。");
		api_16.setParameter("{switchid}");
		QoSoverLLDPAPIList.add(api_16);

		QoSoverLLDPAPI api_17 = new QoSoverLLDPAPI(QueryQoSforAndroidResource.class,"android/queryqos","json");
		api_17.setDescription(17,"安卓端专用，用来查询某个交换机的服务质量信息，输入格式如：s1-eth1。");
		api_17.setParameter("{device}");
		QoSoverLLDPAPIList.add(api_17);

		QoSoverLLDPAPI api_18 = new QoSoverLLDPAPI(QoSoverLLDPAPIDisplayResource.class,"api/all","html");
		api_18.setDescription(18,"用html显示所有api。");
		QoSoverLLDPAPIList.add(api_18);

		QoSoverLLDPAPI api_19 = new QoSoverLLDPAPI(DeviceQoSHistoryListStatisticsforWebGUIResource.class,"webgui/statistics","json");
		api_19.setDescription(19,"webgui专用api，用来根据交换机端口来返回交换机历史服务质量的数据统计信息，有两个参数分别是：percentagelist(百分比列表)、varietyproportion（变化比重），输入格式如：s1-eth1。");
		api_19.setParameter("{data-format}",new ArrayList<String>(){{add("percentagelist");add("varietyproportion");}},"{switch-port}");
		QoSoverLLDPAPIList.add(api_19);

		QoSoverLLDPAPI api_20 = new QoSoverLLDPAPI(DeviceQoSHistoryListforWebGUIResource.class,"webgui/deviceqoshistorylist","json");
		api_20.setDescription(20,"webgui专用api，用来根据交换机端口来返回交换机历史服务质量列表，输入格式如：s1-eth1。");
		api_20.setParameter("{switch-port}");
		QoSoverLLDPAPIList.add(api_20);

		QoSoverLLDPAPI api_21 = new QoSoverLLDPAPI(GetPortidlistBySwicthidforWebGUIResource.class,"webgui/getportidlistbyswicthid","json");
		api_21.setDescription(21,"webgui专用api，用来根据交换机的编号返回端口号的列表，输入格式：整数。");
		api_21.setParameter("{switchid}");
		QoSoverLLDPAPIList.add(api_21);

		QoSoverLLDPAPI api_22 = new QoSoverLLDPAPI(GetSwitchidPortidListMapforWebGUIResource.class,"webgui/getswitchidportidlistmap","json");
		api_22.setDescription(22,"webgui专用api，获取交换机与端口列表之间的哈希表。");
		QoSoverLLDPAPIList.add(api_22);

		QoSoverLLDPAPI api_23 = new QoSoverLLDPAPI(DeviceQoSMapListforWebGUIResource.class,"webgui/deviceqosmaplist","json");
		api_23.setDescription(23,"webgui专用api，获取设备名称和QoS信息的哈希的列表。");
		QoSoverLLDPAPIList.add(api_23);

		QoSoverLLDPAPI api_24 = new QoSoverLLDPAPI(GetD3v4TopologyDBContainOptimalpathResource.class,"webgui/d3v4topologydbcontainoptimalpath","json");
		api_24.setDescription(24,"webgui专用api，获取包含最优路径的d3的拓扑数据库。");
		api_24.setParameter("{optimal-type}",optimal_type,"{src-host}","{dst-host}");
		QoSoverLLDPAPIList.add(api_24);

		QoSoverLLDPAPI api_25 = new QoSoverLLDPAPI(HistoryQoSListStatisticsResource.class,"webgui/historyqosliststatistics","json");
		api_25.setDescription(25,"webgui专用api，获取历史服务质量");
		api_25.setParameter("{device}");
		QoSoverLLDPAPIList.add(api_25);

		Router router = new Router(context);
		for(QoSoverLLDPAPI api:QoSoverLLDPAPIList){
			router.attach(api.getPath(),api.get_Class());
		}
		//返回显示所有api的一个html界面
		router.attach("/api/index.html",QoSoverLLDPAPIDisplayResource.class);
		router.attach("/floodlight-api.html",FloodlightAPIDisplayResource.class);
        return router;
	}

	@Override
	public String basePath() {
		// TODO Auto-generated method stub
		return "/wm/qosoverlldp";
	}

}

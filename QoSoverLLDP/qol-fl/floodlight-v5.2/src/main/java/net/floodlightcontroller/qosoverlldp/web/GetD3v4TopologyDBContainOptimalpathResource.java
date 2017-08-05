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

package net.floodlightcontroller.qosoverlldp.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.floodlightcontroller.qosoverlldp.IQoSoverLLDPService;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class GetD3v4TopologyDBContainOptimalpathResource extends ServerResource {

    public class Node extends HashMap<String,String>{
        public Node(String name){
            put("name",name);
        }
        public String getName(){
            return get("name");
        }
    }
    public class Link extends HashMap<String,String>{
        public Link(Node source,Node target){
            put("source",source.get("name"));
            put("target",target.get("name"));
        }
        public void setColor(String color){
            put("color",color);
        }
        public String getTarget(){
            return get("target");
        }
        public String getSource(){
            return get("source");
        }
    }

    @Get("json")
    public Object retrieve(){
        IQoSoverLLDPService qosoverlldp = (IQoSoverLLDPService)getContext().getAttributes().get(IQoSoverLLDPService.class.getCanonicalName());
        String optimaltype = (String)getRequestAttributes().get("optimal-type");//优化的类型
        String src_host = (String)getRequestAttributes().get("src-host");
        String dst_host = (String)getRequestAttributes().get("dst-host");
        ObjectMapper mapper = new ObjectMapper();
        ArrayList<String> optimalroute;
        ArrayList<Link> optimallinks = new ArrayList<>();
        try{//获取主机最优路径
            String optimalroutestr = qosoverlldp.doGet(String.format("http://127.0.0.1:8080/wm/qosoverlldp/optimalroute/%s/%s/%s/json",optimaltype,src_host,dst_host));
            optimalroute = mapper.readValue(optimalroutestr,ArrayList.class);
            for(int i=0;i<optimalroute.size()-1;i+=2){
                String src = optimalroute.get(i);
                if(src.contains("-eth")) src = src.substring(0,src.indexOf("-eth"));
                String dst = optimalroute.get(i+1);
                if(dst.contains("-eth")) dst = dst.substring(0,dst.indexOf("-eth"));
                optimallinks.add(new Link(new Node(src),new Node(dst)));
                optimallinks.add(new Link(new Node(dst),new Node(src)));
            }
        }catch(Exception e){
            return e.toString();
        }
        ArrayList<Link> links = new ArrayList<>();
        Set<Node> nodes = new HashSet<>();
        try{//获取主机与交换机的连接信息
            String deviceallstr = qosoverlldp.doGet("http://127.0.0.1:8080/wm/qosoverlldp/device/all/json");
            ArrayList<Object> list = (ArrayList<Object>) mapper.readValue(deviceallstr, HashMap.class).get("devices");
            for(Object object:list){
                HashMap<String,Object> map = (HashMap<String,Object>)object;
                ArrayList<String> maclist = (ArrayList<String>)map.get("mac");
                ArrayList<Node> hosts = new ArrayList<>();
                for(String mac:maclist) hosts.add(new Node("h"+Long.valueOf(mac.replace(":", ""), 16)));
                nodes.addAll(hosts);
                ArrayList<Node> switches = new ArrayList<>();
                ArrayList<Object> attachmentPointlist = (ArrayList<Object>)map.get("attachmentPoint");
                for(Object object2:attachmentPointlist){
                    HashMap<String,String> map2 = (HashMap<String, String>) object2;
                    switches.add(new Node("s"+Long.valueOf(map2.get("switch").replaceAll(":", ""),16)));
                }
                nodes.addAll(switches);
                for(Node node1:hosts){
                    for(Node node2:switches){
                        Link link = new Link(node1,node2);
                        if(optimallinks.contains(link)){
                            link.setColor("red");
                        }else{
                            link.setColor("black");
                        }
                        links.add(link);
                    }
                }
            }
        }catch(Exception e){
            return e.toString();
        }
        try{//获取交换机之间的链路信息
            String linkstr = qosoverlldp.doGet("http://127.0.0.1:8080/wm/topology/links/json");
            ArrayList<HashMap<String,Object>> topologylinks = mapper.readValue(linkstr,ArrayList.class);
            for(HashMap<String,Object> tl:topologylinks){
                Node src = new Node("s"+Long.valueOf(tl.get("src-switch").toString().replaceAll(":", ""), 16));
                Node dst = new Node("s"+Long.valueOf(tl.get("dst-switch").toString().replaceAll(":", ""), 16));
                nodes.add(src);
                nodes.add(dst);
                Link link = new Link(src,dst);
                if(optimallinks.contains(link)){
                    link.setColor("red");
                }else{
                    link.setColor("black");
                }
                links.add(link);
            }
            HashMap<String,Object> d3topologydb = new HashMap<String,Object>(){{
                put("nodes",nodes);
                put("links",links);
            }};
        }catch(Exception e){
            return e.toString();
        }
        HashMap<String,Object> d3topologydb = new HashMap<String,Object>(){{
            put("nodes",nodes);
            put("links",links);
        }};
        return d3topologydb;
    }

}

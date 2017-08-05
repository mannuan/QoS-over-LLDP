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

public class StaticEntryPusherResource extends ServerResource {

    public ObjectMapper mapper = new ObjectMapper();

    public enum URLFields{
        DEFAULT,LIST_ALL,CLEAR_ALL
    }

    public String getURLFieldsName(URLFields uf){
        switch(uf){
            case LIST_ALL:
                return "list/all/";
            case CLEAR_ALL:
                return "clear/all/";
            default:
                return "";
        }
    }

    public String base_url = "http://localhost:8080/wm/staticentrypusher/%sjson";

    /**
     * 根据源ip和目的ip,检查流表是否冲突,如果冲突则删除指定流表
     * @param src_host
     * @param dst_host
     * @throws Exception
     */
    public boolean Checkout(String src_host,String dst_host) {

        IQoSoverLLDPService qosoverlldp = (IQoSoverLLDPService)getContext().getAttributes().get(IQoSoverLLDPService.class.getCanonicalName());
        try{
            //获取源主机和目的主机的ip
            String ipv4_src = qosoverlldp.longToIp(Long.parseLong(src_host.replaceAll("h","")));
            ipv4_src = "10"+ipv4_src.substring(1,ipv4_src.length());
            String ipv4_dst = qosoverlldp.longToIp(Long.parseLong(dst_host.replaceAll("h","")));
            ipv4_dst = "10"+ipv4_dst.substring(1,ipv4_dst.length());
            //获取名称中包含源主机和目的主机ip的流表名称
            String url = String.format(base_url, getURLFieldsName(URLFields.LIST_ALL));
            HashMap<String,ArrayList<HashMap<String, Object>>> listall = mapper.readValue(qosoverlldp.doGet(url), HashMap.class);
            ArrayList<String> entrynamelist = new ArrayList<>();
            for(ArrayList<HashMap<String, Object>> sw :listall.values()){
                for(HashMap<String, Object> flow :sw){
                    for(String key:flow.keySet()){
                        if(key.contains(ipv4_src) && key.contains(ipv4_dst)) entrynamelist.add(key);
                    }
                }
            }
            //根据流表的名称删除流表
            url = String.format(base_url, getURLFieldsName(URLFields.DEFAULT));
            for(String entryname: entrynamelist){
                qosoverlldp.doDelete(url, new HashMap<String,String>(){{
                    put("name",entryname);
                }});
            }
            System.out.println(true);
            return true;
        }catch(Exception e){
            System.out.println(false);
            return false;
        }

    }

    public HashMap<String,String> StaticEntry(String dpid,String in_port,String output,String ipv4_src,String ipv4_dst){
        return new HashMap<String,String>(){{
            put("switch",dpid);
            put("name",dpid+"-"+in_port+"-"+output+"-"+ipv4_src+"-"+ipv4_dst);
            put("cookie","0");
            put("priority","32768");
            put("in_port",in_port);
            put("eth_type","0x0800");
            put("active","true");
            put("ipv4_src",ipv4_src+"/32");
            put("ipv4_dst",ipv4_dst+"/32");
            put("instruction_apply_actions","output="+output);
        }};
    }

    public Object StaticEntryPusher(String optimal_type,String src_host,String dst_host){

        IQoSoverLLDPService qosoverlldp = (IQoSoverLLDPService)getContext().getAttributes().get(IQoSoverLLDPService.class.getCanonicalName());
        ArrayList<Object> result = new ArrayList<>();

        try{
            //获取最优路径进行推送流表
            String url = String.format("http://localhost:8080/wm/qosoverlldp/optimalroute/%s/%s/%s/json",optimal_type,src_host,dst_host);
            ArrayList<String> optimalpath = mapper.readValue(qosoverlldp.doGet(url), ArrayList.class);
            //从optimalpath里面移除源主机和目的主机
            optimalpath.remove(0);
            optimalpath.remove(optimalpath.size()-1);
            ArrayList<ArrayList<String>> dpid_in_port_output = new ArrayList<>();
            for(int i=0;i<optimalpath.size();i+=2){
                ArrayList<String> l = new ArrayList<>();
                String dpid_tmp = String.format("%0"+16+"x",Long.parseLong(optimalpath.get(i).replaceAll("s","").split("-eth")[0]));
                String dpid = new String();
                for(int j=0;j<dpid_tmp.length();j+=2){
                    dpid+=dpid_tmp.substring(j, j+2)+":";
                }
                dpid = dpid.substring(0,dpid.length()-1);
                l.add(dpid);
                l.add(optimalpath.get(i).split("-eth")[1]);
                l.add(optimalpath.get(i+1).split("-eth")[1]);
                dpid_in_port_output.add(l);
            }
            //获取源主机和目的主机的ip
            String ipv4_src = qosoverlldp.longToIp(Long.parseLong(src_host.replaceAll("h","")));
            ipv4_src = "10"+ipv4_src.substring(1,ipv4_src.length());
            String ipv4_dst = qosoverlldp.longToIp(Long.parseLong(dst_host.replaceAll("h","")));
            ipv4_dst = "10"+ipv4_dst.substring(1,ipv4_dst.length());
            url = String.format(base_url, getURLFieldsName(URLFields.DEFAULT));
            for(ArrayList<String> dio :dpid_in_port_output){
                HashMap<String,String> entry = StaticEntry(dio.get(0),dio.get(1),dio.get(2),ipv4_src,ipv4_dst);
                result.add(entry);
                result.add(qosoverlldp.doPost(url, entry));
                entry = StaticEntry(dio.get(0),dio.get(2),dio.get(1),ipv4_dst,ipv4_src);
                result.add(entry);
                result.add(qosoverlldp.doPost(url, entry));
            }
        }catch(Exception e){
            result.add(e.toString());
        }
        return result;
    }

    @Get("json")
    public Object retrieve(){
        String optimaltype = (String)getRequestAttributes().get("optimal-type");//优化的类型
        String src_host = (String)getRequestAttributes().get("src-host");
        String dst_host = (String)getRequestAttributes().get("dst-host");
        if(Checkout(src_host,dst_host)){
            return StaticEntryPusher(optimaltype,src_host,dst_host);
        }else{
            return new ArrayList<Object>(){{add("unexcept error");}};
        }
    }

}
